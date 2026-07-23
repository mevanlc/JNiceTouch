# JNiceTouch C ABI v1

The public native contract is
[`native/include/jnicetouch.h`](../native/include/jnicetouch.h). The header is
valid C11 and does not include AppKit, Foundation, Objective-C syntax, or Cocoa
pointer types.

## Versioning

`jnt_abi_version()` and `jnt_event_struct_size(version)` are stable discovery
symbols. Operations and record types carry a version suffix:

- `jnt_capabilities_v1`
- `jnt_install_v1`
- `jnt_wait_and_drain_v1`
- `jnt_uninstall_v1`
- `jnt_perform_haptic_feedback_v1`
- `jnt_dropped_event_count_v1`
- `jnt_event_v1`

Every record also begins with its byte size and ABI version. A future ABI can
add suffixed functions and a new record without changing v1. Existing field
offsets, constants, and function signatures must never be repurposed.

## `jnt_event_v1` layout

The record is 128 bytes and has 8-byte alignment on supported 64-bit macOS
targets.

| Offset | C type | Field |
| ---: | --- | --- |
| 0 | `uint32_t` | `struct_size` |
| 4 | `uint16_t` | `abi_version` |
| 6 | `uint16_t` | `kind` |
| 8 | `uint32_t` | `flags` |
| 12 | `uint32_t` | `phase` |
| 16 | `uint32_t` | `momentum_phase` |
| 20 | `uint32_t` | `reserved_0` |
| 24 | `uint64_t` | `sequence_id` |
| 32 | `uint64_t` | `timestamp_ns` |
| 40 | `uint64_t` | `modifier_flags` |
| 48 | `int64_t` | `window_number` |
| 56 | `double` | `screen_x` |
| 64 | `double` | `screen_y` |
| 72 | `double` | `delta_x` |
| 80 | `double` | `delta_y` |
| 88 | `double` | `magnification` |
| 96 | `double` | `rotation_degrees` |
| 104 | `double` | `pressure` |
| 112 | `double` | `stage_transition` |
| 120 | `int32_t` | `pressure_stage` |
| 124 | `int32_t` | `pressure_behavior` |

Phase and momentum values preserve the public `NSEventPhase` bits. Modifier
flags and pressure-behavior values likewise preserve their public AppKit
numeric values. Java keeps the native integers in addition to normalized enum
sets so unknown future bits are not silently lost.

`sequence_id` identifies a gesture rather than one event. In particular, a
scroll's physical phase and subsequent momentum phase use the same ID.
`timestamp_ns` is monotonic nanoseconds since system startup. Coordinates remain
in AppKit's global screen coordinate system, including negative display
origins. AppKit's scrolling deltas already include the user's natural-scrolling
preference and must not be inverted again.

## Threading and ownership

Installation and removal enqueue their AppKit work on the macOS main dispatch
queue. The local monitor copies scalar fields into `jnt_event_v1`, enqueues the
record, and immediately returns the original `NSEvent`, preserving ordinary AWT
processing.

The queue has one AppKit producer and one Java consumer. The producer uses a
nonblocking lock attempt and never waits for Java or the EDT. Consecutive
changed scroll, magnify, and rotate records for the same sequence are
coalesced. When state is lost because the queue is full or briefly contended,
the next drain begins with `JNT_EVENT_CANCEL` and
`JNT_FLAG_QUEUE_OVERFLOW_OCCURRED`; `jnt_dropped_event_count_v1()` reports the
lost records.

One dedicated Java platform thread calls `jnt_wait_and_drain_v1`. It copies a
batch from caller-owned native memory and schedules one EDT task for that batch.
There are no native-to-Java callbacks, retained Java references, `JNIEnv`
values, FFM upcalls, or Java-visible native objects.

## Lifecycle cancellation

The native bridge synthesizes cancellation when the application resigns active,
a window closes, or queue overflow makes sequence state unreliable. Java drops
the pinned component for cancelled sequences. Ordinary ended/cancelled phases
terminate other gestures; a physical scroll end remains pinned until momentum
ends or a later scroll sequence begins.
