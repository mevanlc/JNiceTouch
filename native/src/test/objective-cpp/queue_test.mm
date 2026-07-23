#include <cassert>

/* Exercise the exact implementation while keeping test hooks out of the ABI. */
#include "../../main/objective-cpp/JNiceTouch.mm"

namespace {

void testCoalescing() {
    g_event_queue.clearAndWake();
    auto first = baseEvent(JNT_EVENT_SCROLL);
    first.phase = JNT_PHASE_CHANGED;
    first.sequence_id = 42;
    first.delta_x = 1.25;
    first.delta_y = -2.0;
    g_event_queue.enqueue(first);

    auto second = first;
    second.delta_x = 0.75;
    second.delta_y = -3.0;
    g_event_queue.enqueue(second);

    jnt_event_v1 drained[2]{};
    assert(g_event_queue.waitAndDrain(drained, 2, 0) == 1);
    assert(drained[0].delta_x == 2.0);
    assert(drained[0].delta_y == -5.0);
}

void testOverflowCancelsLostState() {
    g_event_queue.clearAndWake();
    const uint64_t droppedBefore = g_dropped_event_count.load();
    for (std::size_t index = 0; index < kQueueCapacity + 1; ++index) {
        auto event = baseEvent(JNT_EVENT_PRESSURE);
        event.sequence_id = index + 1;
        event.phase = JNT_PHASE_CHANGED;
        g_event_queue.enqueue(event);
    }

    jnt_event_v1 drained[2]{};
    assert(g_event_queue.waitAndDrain(drained, 2, 0) == 2);
    assert(drained[0].kind == JNT_EVENT_CANCEL);
    assert((drained[0].flags & JNT_FLAG_QUEUE_OVERFLOW_OCCURRED) != 0);
    assert(drained[1].sequence_id == kQueueCapacity + 1);
    assert(g_dropped_event_count.load() - droppedBefore == kQueueCapacity);
}

void testScrollMomentumKeepsSequence() {
    GestureSequences sequences;
    const uint64_t began = sequences.sequenceFor(
            JNT_EVENT_SCROLL, JNT_PHASE_BEGAN, JNT_PHASE_NONE);
    const uint64_t physicalEnd = sequences.sequenceFor(
            JNT_EVENT_SCROLL, JNT_PHASE_ENDED, JNT_PHASE_NONE);
    const uint64_t momentumBegin = sequences.sequenceFor(
            JNT_EVENT_SCROLL, JNT_PHASE_NONE, JNT_PHASE_BEGAN);
    const uint64_t momentumEnd = sequences.sequenceFor(
            JNT_EVENT_SCROLL, JNT_PHASE_NONE, JNT_PHASE_ENDED);
    const uint64_t next = sequences.sequenceFor(
            JNT_EVENT_SCROLL, JNT_PHASE_CHANGED, JNT_PHASE_NONE);

    assert(physicalEnd == began);
    assert(momentumBegin == began);
    assert(momentumEnd == began);
    assert(next != began);
}

}  // namespace

int main() {
    testCoalescing();
    testOverflowCancelsLostState();
    testScrollMomentumKeepsSequence();
    return 0;
}
