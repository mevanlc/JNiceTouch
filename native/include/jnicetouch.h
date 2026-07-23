#ifndef JNICETOUCH_H
#define JNICETOUCH_H

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define JNT_API __declspec(dllexport)
#else
#define JNT_API __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define JNT_ABI_VERSION_1 1u

enum jnt_status {
    JNT_STATUS_OK = 0,
    JNT_STATUS_INVALID_ARGUMENT = -1,
    JNT_STATUS_UNSUPPORTED = -2,
    JNT_STATUS_ALREADY_INSTALLED = -3,
    JNT_STATUS_NATIVE_ERROR = -4
};

enum jnt_capability {
    JNT_CAP_PRECISE_SCROLL = UINT64_C(1) << 0,
    JNT_CAP_MAGNIFY = UINT64_C(1) << 1,
    JNT_CAP_ROTATE = UINT64_C(1) << 2,
    JNT_CAP_SWIPE = UINT64_C(1) << 3,
    JNT_CAP_SMART_MAGNIFY = UINT64_C(1) << 4,
    JNT_CAP_PRESSURE = UINT64_C(1) << 5,
    JNT_CAP_QUICK_LOOK = UINT64_C(1) << 6,
    JNT_CAP_HAPTIC_FEEDBACK = UINT64_C(1) << 7
};

enum jnt_event_kind {
    JNT_EVENT_SCROLL = 1,
    JNT_EVENT_MAGNIFY = 2,
    JNT_EVENT_ROTATE = 3,
    JNT_EVENT_SWIPE = 4,
    JNT_EVENT_SMART_MAGNIFY = 5,
    JNT_EVENT_PRESSURE = 6,
    JNT_EVENT_QUICK_LOOK = 7,
    JNT_EVENT_CANCEL = 8
};

enum jnt_event_flag {
    JNT_FLAG_PRECISE_SCROLL = 1u << 0,
    JNT_FLAG_DIRECTION_INVERTED = 1u << 1,
    JNT_FLAG_QUEUE_OVERFLOW_OCCURRED = 1u << 2
};

/* These values deliberately match the public NSEventPhase bit values. */
enum jnt_event_phase {
    JNT_PHASE_NONE = 0,
    JNT_PHASE_BEGAN = 1u << 0,
    JNT_PHASE_STATIONARY = 1u << 1,
    JNT_PHASE_CHANGED = 1u << 2,
    JNT_PHASE_ENDED = 1u << 3,
    JNT_PHASE_CANCELLED = 1u << 4,
    JNT_PHASE_MAY_BEGIN = 1u << 5
};

/* Values match NSPressureBehavior without exposing an AppKit type. */
enum jnt_pressure_behavior {
    JNT_PRESSURE_BEHAVIOR_UNKNOWN = -1,
    JNT_PRESSURE_BEHAVIOR_PRIMARY_DEFAULT = 0,
    JNT_PRESSURE_BEHAVIOR_PRIMARY_CLICK = 1,
    JNT_PRESSURE_BEHAVIOR_PRIMARY_GENERIC = 2,
    JNT_PRESSURE_BEHAVIOR_PRIMARY_ACCELERATOR = 3,
    JNT_PRESSURE_BEHAVIOR_PRIMARY_DEEP_CLICK = 5,
    JNT_PRESSURE_BEHAVIOR_PRIMARY_DEEP_DRAG = 6
};

typedef struct jnt_event_v1 {
    uint32_t struct_size;
    uint16_t abi_version;
    uint16_t kind;

    uint32_t flags;
    uint32_t phase;
    uint32_t momentum_phase;
    uint32_t reserved_0;

    /* Identifies one gesture sequence, not an individual native event. */
    uint64_t sequence_id;
    /* Monotonic nanoseconds since system startup. */
    uint64_t timestamp_ns;
    uint64_t modifier_flags;
    int64_t window_number;

    double screen_x;
    double screen_y;

    double delta_x;
    double delta_y;
    double magnification;
    double rotation_degrees;

    double pressure;
    double stage_transition;
    int32_t pressure_stage;
    int32_t pressure_behavior;
} jnt_event_v1;

/* Stable discovery entry points for the versioned ABI below. */
JNT_API uint32_t jnt_abi_version(void);
JNT_API size_t jnt_event_struct_size(uint32_t abi_version);

JNT_API uint64_t jnt_capabilities_v1(void);

/*
 * Starts application-local capture. A zero mask requests every event
 * capability. Haptic feedback does not require capture to be installed.
 */
JNT_API int32_t jnt_install_v1(uint64_t requested_capabilities);

/*
 * Blocks for at most timeout_ms and writes up to capacity complete records.
 * A negative timeout waits indefinitely; zero performs a nonblocking drain.
 * Exactly one consumer thread may call this function at a time.
 */
JNT_API size_t jnt_wait_and_drain_v1(
        jnt_event_v1 *destination,
        size_t capacity,
        int32_t timeout_ms);

JNT_API void jnt_uninstall_v1(void);

JNT_API int32_t jnt_perform_haptic_feedback_v1(
        uint32_t pattern,
        uint32_t performance_time);

JNT_API uint64_t jnt_dropped_event_count_v1(void);

#ifdef __cplusplus
}
#endif

#endif
