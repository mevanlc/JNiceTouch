#import <AppKit/AppKit.h>
#import <Foundation/Foundation.h>

#include "jnicetouch.h"

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <deque>
#include <mutex>

namespace {

constexpr std::size_t kQueueCapacity = 1024;
constexpr uint64_t kEventCapabilities =
        JNT_CAP_PRECISE_SCROLL | JNT_CAP_MAGNIFY | JNT_CAP_ROTATE |
        JNT_CAP_SWIPE | JNT_CAP_SMART_MAGNIFY | JNT_CAP_PRESSURE |
        JNT_CAP_QUICK_LOOK;
constexpr uint64_t kAllCapabilities =
        kEventCapabilities | JNT_CAP_HAPTIC_FEEDBACK;

std::atomic<bool> g_installed{false};
std::atomic<uint64_t> g_install_generation{0};
std::atomic<uint64_t> g_requested_capabilities{0};
std::atomic<uint64_t> g_dropped_event_count{0};
std::atomic<uint64_t> g_next_sequence_id{1};

/* AppKit-owned tokens are confined to the AppKit main thread. */
id g_monitor_token = nil;
id g_deactivate_observer = nil;
id g_window_close_observer = nil;

uint64_t monotonicTimestampNanos() {
    const auto now = std::chrono::steady_clock::now().time_since_epoch();
    return static_cast<uint64_t>(
            std::chrono::duration_cast<std::chrono::nanoseconds>(now).count());
}

jnt_event_v1 baseEvent(uint16_t kind) {
    jnt_event_v1 event{};
    event.struct_size = sizeof(jnt_event_v1);
    event.abi_version = JNT_ABI_VERSION_1;
    event.kind = kind;
    event.timestamp_ns = monotonicTimestampNanos();
    event.window_number = -1;
    event.pressure_behavior = JNT_PRESSURE_BEHAVIOR_UNKNOWN;
    return event;
}

jnt_event_v1 cancellationEvent(int64_t windowNumber, bool overflow) {
    auto event = baseEvent(JNT_EVENT_CANCEL);
    event.window_number = windowNumber;
    event.phase = JNT_PHASE_CANCELLED;
    if (overflow) {
        event.flags |= JNT_FLAG_QUEUE_OVERFLOW_OCCURRED;
    }
    return event;
}

bool isCoalescible(const jnt_event_v1 &event) {
    if ((event.phase & JNT_PHASE_CHANGED) == 0 ||
        (event.phase & (JNT_PHASE_BEGAN | JNT_PHASE_ENDED |
                        JNT_PHASE_CANCELLED)) != 0) {
        return false;
    }
    return event.kind == JNT_EVENT_SCROLL || event.kind == JNT_EVENT_MAGNIFY ||
           event.kind == JNT_EVENT_ROTATE;
}

bool coalesceInto(jnt_event_v1 &previous, const jnt_event_v1 &current) {
    if (!isCoalescible(current) || previous.kind != current.kind ||
        previous.sequence_id != current.sequence_id ||
        previous.phase != current.phase ||
        previous.momentum_phase != current.momentum_phase) {
        return false;
    }

    previous.flags |= current.flags;
    previous.timestamp_ns = current.timestamp_ns;
    previous.modifier_flags = current.modifier_flags;
    previous.screen_x = current.screen_x;
    previous.screen_y = current.screen_y;
    previous.delta_x += current.delta_x;
    previous.delta_y += current.delta_y;
    previous.magnification += current.magnification;
    previous.rotation_degrees += current.rotation_degrees;
    return true;
}

class EventQueue {
public:
    void enqueue(const jnt_event_v1 &event) {
        std::unique_lock lock(mutex_, std::try_to_lock);
        if (!lock.owns_lock()) {
            noteOverflow();
            condition_.notify_one();
            return;
        }

        recoverFromOverflowLocked(event.window_number);
        if (!events_.empty() && coalesceInto(events_.back(), event)) {
            condition_.notify_one();
            return;
        }
        if (events_.size() == kQueueCapacity) {
            g_dropped_event_count.fetch_add(events_.size());
            events_.clear();
            events_.push_back(cancellationEvent(
                    event.window_number, /* overflow */ true));
        }
        events_.push_back(event);
        condition_.notify_one();
    }

    std::size_t waitAndDrain(
            jnt_event_v1 *destination,
            std::size_t capacity,
            int32_t timeoutMs) {
        if (destination == nullptr || capacity == 0) {
            return 0;
        }

        std::unique_lock lock(mutex_);
        const auto ready = [this] {
            return !events_.empty() || overflow_pending_.load() ||
                   !g_installed.load();
        };
        if (timeoutMs < 0) {
            condition_.wait(lock, ready);
        } else if (timeoutMs > 0) {
            condition_.wait_for(
                    lock, std::chrono::milliseconds(timeoutMs), ready);
        }

        recoverFromOverflowLocked(-1);
        const std::size_t count = std::min(capacity, events_.size());
        for (std::size_t index = 0; index < count; ++index) {
            destination[index] = events_.front();
            events_.pop_front();
        }
        return count;
    }

    void clearAndWake() {
        std::lock_guard lock(mutex_);
        events_.clear();
        overflow_pending_.store(false);
        condition_.notify_all();
    }

private:
    void noteOverflow() {
        ++g_dropped_event_count;
        overflow_pending_.store(true);
    }

    void recoverFromOverflowLocked(int64_t windowNumber) {
        if (!overflow_pending_.exchange(false)) {
            return;
        }
        g_dropped_event_count.fetch_add(events_.size());
        events_.clear();
        events_.push_back(cancellationEvent(
                windowNumber, /* overflow */ true));
    }

    std::mutex mutex_;
    std::condition_variable condition_;
    std::deque<jnt_event_v1> events_;
    std::atomic<bool> overflow_pending_{false};
};

EventQueue g_event_queue;

struct GestureSequences {
    uint64_t by_kind[JNT_EVENT_CANCEL + 1]{};

    uint64_t sequenceFor(
            uint16_t kind,
            uint32_t phase,
            uint32_t momentumPhase) {
        uint64_t &active = by_kind[kind];
        const bool phaseBegins =
                (phase & (JNT_PHASE_BEGAN | JNT_PHASE_MAY_BEGIN)) != 0;
        const bool discrete = phase == JNT_PHASE_NONE &&
                              momentumPhase == JNT_PHASE_NONE;
        if (active == 0 || phaseBegins || discrete) {
            active = g_next_sequence_id.fetch_add(1);
        }
        const uint64_t result = active;

        const bool cancelled =
                ((phase | momentumPhase) & JNT_PHASE_CANCELLED) != 0;
        const bool momentumEnded =
                (momentumPhase & JNT_PHASE_ENDED) != 0;
        const bool ordinaryEnded =
                (phase & JNT_PHASE_ENDED) != 0 &&
                momentumPhase == JNT_PHASE_NONE && kind != JNT_EVENT_SCROLL;
        if (cancelled || momentumEnded || ordinaryEnded || discrete) {
            active = 0;
        }
        return result;
    }

    void clear() {
        std::memset(by_kind, 0, sizeof(by_kind));
    }
};

GestureSequences g_sequences;

uint64_t capabilityForEventType(NSEventType type) {
    switch (type) {
        case NSEventTypeScrollWheel:
            return JNT_CAP_PRECISE_SCROLL;
        case NSEventTypeMagnify:
            return JNT_CAP_MAGNIFY;
        case NSEventTypeRotate:
            return JNT_CAP_ROTATE;
        case NSEventTypeSwipe:
            return JNT_CAP_SWIPE;
        case NSEventTypeSmartMagnify:
            return JNT_CAP_SMART_MAGNIFY;
        case NSEventTypePressure:
            return JNT_CAP_PRESSURE;
        case NSEventTypeQuickLook:
            return JNT_CAP_QUICK_LOOK;
        default:
            return 0;
    }
}

uint16_t kindForEventType(NSEventType type) {
    switch (type) {
        case NSEventTypeScrollWheel:
            return JNT_EVENT_SCROLL;
        case NSEventTypeMagnify:
            return JNT_EVENT_MAGNIFY;
        case NSEventTypeRotate:
            return JNT_EVENT_ROTATE;
        case NSEventTypeSwipe:
            return JNT_EVENT_SWIPE;
        case NSEventTypeSmartMagnify:
            return JNT_EVENT_SMART_MAGNIFY;
        case NSEventTypePressure:
            return JNT_EVENT_PRESSURE;
        case NSEventTypeQuickLook:
            return JNT_EVENT_QUICK_LOOK;
        default:
            return 0;
    }
}

jnt_event_v1 copyEvent(NSEvent *event) {
    const uint16_t kind = kindForEventType(event.type);
    auto record = baseEvent(kind);
    record.phase = static_cast<uint32_t>(event.phase);
    record.momentum_phase = static_cast<uint32_t>(event.momentumPhase);
    record.sequence_id = g_sequences.sequenceFor(
            kind, record.phase, record.momentum_phase);
    record.timestamp_ns = static_cast<uint64_t>(
            event.timestamp * 1'000'000'000.0);
    record.modifier_flags = static_cast<uint64_t>(event.modifierFlags);
    record.window_number = event.windowNumber;

    NSPoint screenPoint;
    if (event.window != nil) {
        screenPoint = [event.window convertPointToScreen:event.locationInWindow];
    } else {
        screenPoint = NSEvent.mouseLocation;
    }
    record.screen_x = screenPoint.x;
    record.screen_y = screenPoint.y;

    switch (event.type) {
        case NSEventTypeScrollWheel:
            record.delta_x = event.scrollingDeltaX;
            record.delta_y = event.scrollingDeltaY;
            if (event.hasPreciseScrollingDeltas) {
                record.flags |= JNT_FLAG_PRECISE_SCROLL;
            }
            if (event.isDirectionInvertedFromDevice) {
                record.flags |= JNT_FLAG_DIRECTION_INVERTED;
            }
            break;
        case NSEventTypeMagnify:
            record.magnification = event.magnification;
            break;
        case NSEventTypeRotate:
            record.rotation_degrees = event.rotation;
            break;
        case NSEventTypeSwipe:
            record.delta_x = event.deltaX;
            record.delta_y = event.deltaY;
            break;
        case NSEventTypePressure:
            record.pressure = event.pressure;
            record.stage_transition = event.stageTransition;
            record.pressure_stage = static_cast<int32_t>(event.stage);
            record.pressure_behavior =
                    static_cast<int32_t>(event.pressureBehavior);
            break;
        default:
            break;
    }
    return record;
}

NSEventMask maskForCapabilities(uint64_t capabilities) {
    NSEventMask mask = 0;
    if ((capabilities & JNT_CAP_PRECISE_SCROLL) != 0) {
        mask |= NSEventMaskScrollWheel;
    }
    if ((capabilities & JNT_CAP_MAGNIFY) != 0) {
        mask |= NSEventMaskMagnify;
    }
    if ((capabilities & JNT_CAP_ROTATE) != 0) {
        mask |= NSEventMaskRotate;
    }
    if ((capabilities & JNT_CAP_SWIPE) != 0) {
        mask |= NSEventMaskSwipe;
    }
    if ((capabilities & JNT_CAP_SMART_MAGNIFY) != 0) {
        mask |= NSEventMaskSmartMagnify;
    }
    if ((capabilities & JNT_CAP_PRESSURE) != 0) {
        mask |= NSEventMaskPressure;
    }
    if ((capabilities & JNT_CAP_QUICK_LOOK) != 0) {
        mask |= NSEventMaskFromType(NSEventTypeQuickLook);
    }
    return mask;
}

void enqueueCancellation(NSWindow *window) {
    g_sequences.clear();
    g_event_queue.enqueue(cancellationEvent(
            window == nil ? -1 : window.windowNumber, /* overflow */ false));
}

void installOnAppKitThread(uint64_t capabilities, uint64_t generation) {
    if (!g_installed.load() || g_install_generation.load() != generation) {
        return;
    }

    const NSEventMask mask = maskForCapabilities(capabilities);
    g_monitor_token = [NSEvent
            addLocalMonitorForEventsMatchingMask:mask
                                         handler:^NSEvent *(NSEvent *event) {
        if (!g_installed.load() ||
            (g_requested_capabilities.load() &
             capabilityForEventType(event.type)) == 0) {
            return event;
        }
        g_event_queue.enqueue(copyEvent(event));
        return event;
    }];

    NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
    g_deactivate_observer = [center
            addObserverForName:NSApplicationDidResignActiveNotification
                        object:nil
                         queue:nil
                    usingBlock:^(NSNotification *) {
        enqueueCancellation(nil);
    }];
    g_window_close_observer = [center
            addObserverForName:NSWindowWillCloseNotification
                        object:nil
                         queue:nil
                    usingBlock:^(NSNotification *notification) {
        enqueueCancellation((NSWindow *) notification.object);
    }];
}

void removeFromAppKitThread() {
    if (g_monitor_token != nil) {
        [NSEvent removeMonitor:g_monitor_token];
        g_monitor_token = nil;
    }
    NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
    if (g_deactivate_observer != nil) {
        [center removeObserver:g_deactivate_observer];
        g_deactivate_observer = nil;
    }
    if (g_window_close_observer != nil) {
        [center removeObserver:g_window_close_observer];
        g_window_close_observer = nil;
    }
    g_sequences.clear();
}

}  // namespace

extern "C" {

uint32_t jnt_abi_version(void) {
    return JNT_ABI_VERSION_1;
}

size_t jnt_event_struct_size(uint32_t abiVersion) {
    return abiVersion == JNT_ABI_VERSION_1 ? sizeof(jnt_event_v1) : 0;
}

uint64_t jnt_capabilities_v1(void) {
    return kAllCapabilities;
}

int32_t jnt_install_v1(uint64_t requestedCapabilities) {
    if ((requestedCapabilities & ~kAllCapabilities) != 0) {
        return JNT_STATUS_UNSUPPORTED;
    }

    bool expected = false;
    if (!g_installed.compare_exchange_strong(expected, true)) {
        return JNT_STATUS_ALREADY_INSTALLED;
    }

    uint64_t eventCapabilities = requestedCapabilities & kEventCapabilities;
    if (requestedCapabilities == 0) {
        eventCapabilities = kEventCapabilities;
    }
    if (eventCapabilities == 0) {
        g_installed.store(false);
        return JNT_STATUS_INVALID_ARGUMENT;
    }

    g_event_queue.clearAndWake();
    g_requested_capabilities.store(eventCapabilities);
    const uint64_t generation = g_install_generation.fetch_add(1) + 1;
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            installOnAppKitThread(eventCapabilities, generation);
        }
    });
    return JNT_STATUS_OK;
}

size_t jnt_wait_and_drain_v1(
        jnt_event_v1 *destination,
        size_t capacity,
        int32_t timeoutMs) {
    return g_event_queue.waitAndDrain(destination, capacity, timeoutMs);
}

void jnt_uninstall_v1(void) {
    if (!g_installed.exchange(false)) {
        return;
    }
    g_install_generation.fetch_add(1);
    g_requested_capabilities.store(0);
    g_event_queue.clearAndWake();
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            removeFromAppKitThread();
        }
    });
}

int32_t jnt_perform_haptic_feedback_v1(
        uint32_t pattern,
        uint32_t performanceTime) {
    if (pattern > static_cast<uint32_t>(NSHapticFeedbackPatternLevelChange) ||
        performanceTime >
                static_cast<uint32_t>(NSHapticFeedbackPerformanceTimeDrawCompleted)) {
        return JNT_STATUS_INVALID_ARGUMENT;
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            [NSHapticFeedbackManager.defaultPerformer
                    performFeedbackPattern:
                            static_cast<NSHapticFeedbackPattern>(pattern)
                         performanceTime:
                            static_cast<NSHapticFeedbackPerformanceTime>(
                                    performanceTime)];
        }
    });
    return JNT_STATUS_OK;
}

uint64_t jnt_dropped_event_count_v1(void) {
    return g_dropped_event_count.load();
}

}  // extern "C"
