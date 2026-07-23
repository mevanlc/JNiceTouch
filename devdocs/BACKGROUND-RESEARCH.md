# Recommendation

**Build a small Objective‑C++ AppKit shim with a flat, versioned C ABI.** Treat `java.lang.foreign`, JNI, and JNA as interchangeable transports across that ABI—not as the architecture.

```text
AppKit / Objective-C++
        ↓
versioned C ABI; no NSObject pointers
        ↓
FFM on JDK 22+  |  JNI on older supported JDKs
        ↓
Java trackpad event model
        ↓
Swing EDT and component routing
```

## Implementation in this repository

The JDK 22+ path recommended below is implemented in this repository:

* [`JNiceTouch.mm`](../native/src/main/objective-cpp/JNiceTouch.mm) installs the
  application-local AppKit monitor, copies scalar event fields, and feeds a
  bounded queue without calling Java.
* [`jnicetouch.h`](../native/include/jnicetouch.h) defines the versioned C ABI.
  No Objective-C or Cocoa pointer type crosses it.
* [`FfmNativeBridge.java`](../lib/src/main/java/io/github/mevanlc/jnicetouch/internal/FfmNativeBridge.java)
  binds that ABI with the final JDK 22 Foreign Function and Memory API.
* [`FfmMacTrackpadProvider.java`](../lib/src/main/java/io/github/mevanlc/jnicetouch/internal/FfmMacTrackpadProvider.java)
  drains batches on a dedicated platform thread and schedules one EDT task per
  batch. Component routing pins the initial deepest component through gesture
  completion and scroll momentum.

The ABI details, threading contract, and 128-byte v1 record layout are in
[`C-ABI.md`](C-ABI.md). Automated tests cover native layout and discovery,
queue coalescing and overflow cancellation, scroll/momentum sequence identity,
FFM discovery downcalls, record decoding, and EDT component routing. Physical
trackpad and multi-display behavior still require interactive hardware testing.

The optional older-JDK JNI adapter and raw per-finger contacts discussed below
are not part of the current JDK 22+ implementation scope.

My concrete choices:

| Option                                       | Verdict                                                                                 |
| -------------------------------------------- | --------------------------------------------------------------------------------------- |
| Objective‑C++ shim + C ABI                   | **Yes—core architecture**                                                               |
| `java.lang.foreign` / FFM                    | **Preferred** when you ship and control a JDK 22+ runtime                               |
| JNI                                          | **Preferred compatibility path** for JDK 17/21 or varied runtimes                       |
| JNA                                          | Acceptable for a prototype or existing dependency, but only against your C ABI          |
| Rococoa                                      | Do not use for the input hot path                                                       |
| OpenJDK’s hidden gesture API                 | Borrow for an MVP/fallback, not as the final contract                                   |
| JAWT                                         | Not applicable                                                                          |
| OpenJDK/JBR fork                             | Only when raw per-finger contacts or exact AWT native-view integration are requirements |
| Global `CGEventTap`                          | Avoid                                                                                   |
| Private multitouch frameworks / IOHID tricks | Do not ship them                                                                        |

FFM became final in JDK 22 and is well suited to calling a small C interface. Current JDKs require explicit native access, such as `--enable-native-access=your.module` or `ALL-UNNAMED`. JNI remains the conservative choice when older JDK baselines matter. JNA itself uses a small JNI native component and is convenient, but it does not eliminate native machinery. Rococoa is a general Java-to-Objective-C object binding; that is considerably more surface area than this subsystem needs. ([OpenJDK][1])

## First, split “touchpad support” into three levels

### 1. Ordinary pointer and scrolling

Leave these with AWT:

* pointer motion
* click, secondary click, and drag
* ordinary wheel scrolling
* standard `JScrollPane`, text component, and list/table behavior

Current OpenJDK already reads macOS’s precise X and Y scrolling deltas. However, its public Java representation is lossy: `MouseWheelEvent` exposes a scalar precise rotation, and the macOS implementation represents horizontal scrolling as a Shift-modified wheel event. It does not expose the original two-dimensional vector, gesture phase, or momentum phase publicly. ([GitHub][2])

That means stock Swing is generally sufficient for conventional scrolling, but not for a complete trackpad event API.

### 2. High-level AppKit gestures and metadata

Your native provider should add:

* precise two-dimensional scroll deltas
* scroll phase
* momentum phase
* magnify/pinch
* rotate
* discrete swipe
* smart magnify
* Quick Look, where useful
* pressure and Force Touch stages
* timestamps and modifiers
* event/window location
* natural-scrolling metadata
* optional haptic feedback

Apple’s public event model already provides these concepts. Scroll events have separate `phase` and `momentumPhase` values, and momentum events stay targeted at the view where momentum began. Magnification and rotation values are incremental deltas rather than absolute values. ([Apple Developer][3])

Pressure events expose pressure, stage, stage transition, behavior, and phase. AppKit also has a public haptic-feedback interface for Force Touch trackpads, so haptic output can live behind the same shim rather than another bridge. ([Apple Developer][4])

### 3. Raw per-finger contacts

This is materially harder. Raw support means:

* stable touch identity
* normalized position
* individual began/moved/stationary/ended/cancelled phases
* resting touches
* device dimensions
* simultaneous contacts

Apple explicitly says touches queried from a gesture event may be unreliable; reliable contact data comes through the view’s touch-responder methods. The native view must opt into indirect touch types. Apple also recommends using recognized gestures instead of raw contacts unless the extra data is genuinely needed. ([Apple Developer][3])

Because Swing’s native `AWTView` is owned by the JDK, there is no supported Java hook for turning that view into a raw-touch responder. That is the point at which a custom OpenJDK/JBR runtime patch becomes the clean solution.

## What you can borrow from OpenJDK immediately

OpenJDK currently contains a macOS-specific Swing gesture package:

```java
com.apple.eawt.event.GestureUtilities
```

It can attach a gesture listener to a `JComponent`; attaching to a root pane effectively handles a top-level window. Its gesture callbacks are delivered on the Swing EDT. The current native implementation forwards gesture boundaries, magnification, rotation, and swipe events. ([GitHub][5])

This is useful as:

* an MVP implementation
* a fallback when the native extension fails to load
* a behavioral test oracle
* a way to ship pinch/rotate/swipe before completing the native layer

It is not sufficient for the final implementation because it lacks:

* precise 2D scroll as a first-class event
* scroll and momentum phases
* smart magnify
* pressure
* raw contacts
* a supported public compatibility contract

It is also strongly encapsulated on modern JDKs, so you will need something equivalent to:

```text
--add-exports=java.desktop/com.apple.eawt.event=your.module
```

or, on the class path:

```text
--add-exports=java.desktop/com.apple.eawt.event=ALL-UNNAMED
```

OpenJDK documents the encapsulation of the old Apple extension packages beginning with the modular JDK. ([Oracle Documentation][6])

There is an additional forward-compatibility concern: the current macOS 27 SDK documentation marks the separate begin-gesture event mask as no longer delivered and directs applications toward each event’s `phase` or a gesture recognizer’s phase. OpenJDK’s current native implementation still has explicit `beginGestureWithEvent:` and `endGestureWithEvent:` paths. That does not prove the hidden Java API has failed, but it makes it a feature that deserves dedicated macOS 27 regression testing rather than a foundation you control. ([Apple Developer][7])

## The native architecture I would ship

### Capture with an application-local `NSEvent` monitor

Use:

```objc
[NSEvent addLocalMonitorForEventsMatchingMask:... handler:...]
```

with masks for the events you support, including:

```objc
NSEventMaskScrollWheel
NSEventMaskMagnify
NSEventMaskRotate
NSEventMaskSwipe
NSEventMaskSmartMagnify
NSEventMaskPressure
NSEventMaskQuickLook       // optional
```

A local monitor sees events for the current application, runs on the AppKit main thread, and can return the original event, a replacement, or `nil`. Return the original event by default so ordinary AWT processing continues unchanged. Install and remove the monitor on the AppKit main thread. ([Apple Developer][8])

Do **not** use a global `CGEventTap` for this. It has the wrong scope, adds permission and lifecycle complications, and encourages you to reconstruct application-level AppKit semantics from a lower-level event source.

### Never call Java directly from the monitor

The monitor runs on the AppKit main thread, which is not the Swing EDT. Its handler should:

1. Copy relevant fields into a plain native record.
2. Append that record to a bounded queue.
3. Signal a condition variable or semaphore.
4. Return the original `NSEvent`.

A dedicated Java **platform thread** should block in a native `wait-and-drain` call, receive events in batches, and schedule one EDT task for the batch.

Avoid:

* JNI callbacks from the AppKit thread
* FFM upcalls from the AppKit thread
* waiting synchronously for the EDT
* calling AppKit synchronously from the EDT
* allocating Java objects per native event before batching

The native call overhead of FFM versus JNI versus JNA becomes nearly irrelevant when one call drains a batch. The reliability win from avoiding cross-runtime callbacks is much larger.

### Expose a small C ABI

For example:

```c
#pragma once
#include <stddef.h>
#include <stdint.h>

enum mt_event_kind {
    MT_EVENT_SCROLL        = 1,
    MT_EVENT_MAGNIFY       = 2,
    MT_EVENT_ROTATE        = 3,
    MT_EVENT_SWIPE         = 4,
    MT_EVENT_SMART_MAGNIFY = 5,
    MT_EVENT_PRESSURE      = 6,
    MT_EVENT_QUICK_LOOK    = 7,
    MT_EVENT_CANCEL        = 8
};

enum mt_event_flags {
    MT_FLAG_PRECISE_SCROLL          = 1u << 0,
    MT_FLAG_DIRECTION_INVERTED      = 1u << 1,
    MT_FLAG_QUEUE_OVERFLOW_OCCURRED = 1u << 2
};

typedef struct mt_event_v1 {
    uint32_t struct_size;
    uint16_t abi_version;
    uint16_t kind;

    uint32_t flags;
    uint32_t phase;           /* Preserve native NSEventPhase bits. */
    uint32_t momentum_phase;
    uint32_t reserved_0;

    uint64_t sequence_id;
    uint64_t timestamp_ns;
    uint64_t modifier_flags;
    int64_t  window_number;

    double screen_x;
    double screen_y;

    double delta_x;
    double delta_y;
    double magnification;
    double rotation_degrees;

    double pressure;
    double stage_transition;
    int32_t pressure_stage;
    int32_t reserved_1;
} mt_event_v1;

uint64_t mt_capabilities(void);

int mt_install(uint64_t requested_capabilities);

/*
 * Blocks up to timeout_ms and writes at most capacity records.
 * Called from one dedicated Java-side consumer thread.
 */
size_t mt_wait_and_drain(
    mt_event_v1 *destination,
    size_t capacity,
    int timeout_ms);

void mt_uninstall(void);

int mt_perform_haptic_feedback(
    uint32_t pattern,
    uint32_t performance_time);
```

Important ABI rules:

* Put `struct_size` and `abi_version` in every record.
* Preserve native phase bit values even if Java also normalizes them into enums.
* Never return `NSObject *`, `NSWindow *`, Blocks, or retained Cocoa objects.
* Do not expose Objective-C ownership semantics to Java.
* Treat pressure, raw touches, and haptics as capabilities.
* Use monotonic timestamps and an explicit sequence number.
* Keep raw-touch contacts in a separate ABI structure rather than continually expanding the high-level event record.

For FFM, this becomes four or five downcall method handles. For JNI, the wrapper can consist of four native methods. JNA could call exactly the same ABI during prototyping.

## Phase and momentum handling

Do not build your lifecycle around separate “begin gesture” and “end gesture” event types.

Instead:

* derive gesture state from `event.phase`
* derive inertial scrolling from `event.momentumPhase`
* accept old explicit begin/end callbacks only as compatibility inputs
* end active sequences on `.ended` or `.cancelled`
* synthesize cancellation when the app deactivates, a target window closes, or the native queue loses state

Apple specifically documents separate normal and momentum phases, including the fact that momentum remains attached to the original target. ([Apple Developer][3])

For natural scrolling, pass through AppKit’s `scrollingDeltaX` and `scrollingDeltaY`. AppKit already applies the user’s scrolling-direction preference. Expose `isDirectionInvertedFromDevice` as metadata; do not invert the deltas a second time. ([Apple Developer][9])

## Queue policy

Use a bounded single-producer/single-consumer queue:

* AppKit main thread is the producer.
* Java pump thread is the consumer.
* Never block the producer.

Under pressure:

* coalesce consecutive `.changed` scroll, magnify, and rotate deltas
* never silently discard `.began`, `.ended`, or `.cancelled`
* preserve transitions into and out of momentum
* emit an overflow flag/counter
* cancel the affected sequence if state can no longer be reconstructed

After draining, post the entire batch with one `EventQueue.invokeLater`.

## Java-side event model

Do not try to squeeze everything into subclasses of `MouseWheelEvent`. Use a separate API:

```java
public interface MacTrackpadProvider extends AutoCloseable {
    EnumSet<Capability> capabilities();

    Subscription listen(
        JComponent root,
        MacTrackpadListener listener);

    @Override
    void close();
}
```

A representative event might carry:

```java
public record MacTrackpadEvent(
    Kind kind,
    Phase phase,
    Phase momentumPhase,
    long sequenceId,
    long timestampNanos,
    long modifiers,
    long nativeWindowNumber,
    Point2D screenLocation,
    Point2D delta,
    double magnification,
    double rotationDegrees,
    double pressure,
    int pressureStage,
    double stageTransition,
    Set<Flag> flags
) {}
```

Document that listeners always run on the EDT.

## Component targeting and duplicate delivery

This is where most otherwise-working bridges become subtly wrong.

### Routing

The native event should contain:

* the native window number
* the pointer location in screen coordinates
* a sequence ID

On the EDT:

1. Map the native window to a Java `Window`.
2. Convert the screen point to window coordinates.
3. Resolve the initial component with `SwingUtilities.getDeepestComponentAt`.
4. Pin that component for the entire gesture sequence, including momentum.
5. Release it on ended/cancelled.

A public-API-only implementation can approximate the top-level window using visible Java window bounds, focus state, and the screen point. That is usually adequate for a single-window application, but it is not exact when native windows overlap, an inactive window scrolls under the pointer, or heavyweight popups are involved.

For exact multi-window routing, isolate a small, version-specific adapter that obtains the native `NSWindow` identity from the macOS LWAWT peer. That requires JDK internals and corresponding `--add-exports`/`--add-opens`. Keep it in one replaceable module. If exact native mapping must be guaranteed across arbitrary JDK updates without relying on internals, a small runtime patch is cleaner.

### Event ownership

Keep two explicit modes:

**Standard Swing controls**

* Let AWT handle scrolling.
* Use the native provider for gestures, pressure, and metadata.
* Do not apply the native scroll delta again.

**Custom canvas or viewport**

* Let the native provider own precise scrolling.
* Install a Swing wheel listener that consumes the corresponding `MouseWheelEvent`.
* Do not ask the EDT synchronously from the AppKit monitor whether an event should be suppressed.

The local monitor should still normally return the original native event. Returning `nil` is only safe when the native side already has a synchronous, pre-registered suppression policy—for example, a known native window and region. It must never block the AppKit main thread waiting for Java.

Also, do not activate both OpenJDK’s hidden gesture listener and your native listener for the same gesture types.

## Raw contacts: where a JDK fork becomes justified

For raw `NSTouch` contacts, I would patch the JDK rather than swizzle `AWTView` at runtime.

The patch would:

1. Configure the JDK’s native AWT view for indirect touch types using the current `allowedTouchTypes` API.
2. Implement the native touch responder methods.
3. Copy touch identity, normalized position, phase, resting state, and device size into plain records.
4. Forward those records through the same queue/C ABI machinery.
5. Route them using the JDK’s already-known Java platform window.

This is less fragile than:

* Objective-C method swizzling on an internal JDK class
* inserting a transparent overlay view above AWT
* reaching into undocumented AWTView ivars
* using private `MultitouchSupport.framework`
* consuming IOHID events and reconstructing AppKit behavior

**JAWT does not solve this.** Current macOS JAWT exposes a CALayer-oriented drawing surface protocol; it is not an `NSView` or `NSWindow` escape hatch. ([GitHub][10])

Therefore:

* recognized gestures only: no JDK fork needed
* high-resolution scroll, momentum, pressure, haptics: no JDK fork needed
* reliable per-finger contacts: fork or patch the runtime
* exact native target integration without JDK internals: fork or patch the runtime

## Third-party code: borrow, but do not inherit blindly

`MultiTouch-Gestures-Java` and its forks are useful archaeological references. They demonstrate several relevant events and smooth/momentum scrolling. I would not ship the original implementation unchanged. Its native code stores a `JNIEnv *` and Java class reference globally and then uses them in a `CGEventTap` callback path. `JNIEnv` is thread-specific; a native callback thread must attach to the JVM and obtain its own environment, and retained Java references must be global references. ([GitHub][11])

Rococoa is maintained enough to be a plausible general Cocoa bridge, but it is the wrong abstraction here. A direct binding makes Java responsible for:

* Objective-C message signatures
* Blocks
* ARC/autorelease behavior
* selector availability
* Cocoa object lifetimes
* AppKit main-thread rules
* callback threading and JVM attachment

All of those disappear behind a twenty-function C shim. Direct FFM or JNA calls to `objc_msgSend` have the same architectural problem, with additional ABI hazards around variant return conventions, structures, and Blocks.

My search did not surface a maintained commercial, drop-in Swing SDK covering this entire feature set. The sensible “buy” option is to contract or support the Objective‑C++ shim and its macOS/JDK compatibility testing while retaining ownership of the C and Java APIs.

## Rollout sequence

1. **Define the Java provider and event model first.** Keep all macOS dependencies behind it.
2. **Use stock AWT for ordinary scrolling.**
3. **Add the hidden OpenJDK gesture provider** as an optional MVP for magnify, rotate, and swipe.
4. **Implement the Objective‑C++ local-monitor shim** and the batched C ABI.
5. **Use FFM when the bundled runtime is JDK 22+; otherwise add the tiny JNI adapter.**
6. **Add exact native-window mapping only if the public approximation proves inadequate.**
7. **Add a runtime patch for raw contacts only when a concrete UX requires contact identities or positions.**

Your compatibility matrix should include built-in trackpads, Magic Trackpad, Magic Mouse, conventional wheel mice, natural scrolling both ways, pressure-capable and non-pressure hardware, multiple Retina displays with negative screen origins, nested scroll panes, popups and modal sheets, inactive-window scrolling, full screen, interrupted momentum, app deactivation, EDT stalls, queue overflow, and both macOS 26 and the current macOS 27 development releases.

Finally, every critical action still needs a mouse, keyboard, or menu path. Apple explicitly cautions against making gestures the sole means of invoking critical functionality because users may not have a trackpad. ([Apple Developer][3])

**The choice I would ship is Objective‑C++ + a stable C ABI + FFM, with a JNI adapter when older JDKs matter. Borrow OpenJDK’s hidden gestures for an interim implementation; fork the runtime only for reliable raw contacts. Do not make Rococoa, direct `objc_msgSend`, JAWT, or an old CGEventTap/JNI project the foundation.**

[1]: https://openjdk.org/jeps/454?utm_source=chatgpt.com "JEP 454: Foreign Function & Memory API"
[2]: https://github.com/openjdk/jdk/blob/master/src/java.desktop/macosx/native/libawt_lwawt/awt/AWTView.m "jdk/src/java.desktop/macosx/native/libawt_lwawt/awt/AWTView.m at master · openjdk/jdk · GitHub"
[3]: https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/EventOverview/HandlingTouchEvents/HandlingTouchEvents.html "Handling Trackpad Events"
[4]: https://developer.apple.com/documentation/appkit/nsevent/pressure?utm_source=chatgpt.com "pressure | Apple Developer Documentation"
[5]: https://github.com/openjdk/jdk/blob/master/src/java.desktop/macosx/classes/com/apple/eawt/event/GestureUtilities.java "jdk/src/java.desktop/macosx/classes/com/apple/eawt/event/GestureUtilities.java at master · openjdk/jdk · GitHub"
[6]: https://docs.oracle.com/en/java/javase/24/migrate/migrating-jdk-8-later-jdk-releases.html?embed=1 "https://docs.oracle.com/en/java/javase/24/migrate/migrating-jdk-8-later-jdk-releases.html?embed=1"
[7]: https://developer.apple.com/documentation/appkit/nsevent/eventtypemask/begingesture?changes=_1 "https://developer.apple.com/documentation/appkit/nsevent/eventtypemask/begingesture?changes=_1"
[8]: https://developer.apple.com/library/archive/documentation/Cocoa/Conceptual/EventOverview/MonitoringEvents/MonitoringEvents.html "Monitoring Events"
[9]: https://developer.apple.com/documentation/appkit/nsevent/isdirectioninvertedfromdevice?language=objc "https://developer.apple.com/documentation/appkit/nsevent/isdirectioninvertedfromdevice?language=objc"
[10]: https://github.com/openjdk/jdk/blob/master/src/java.desktop/macosx/native/include/jawt_md.h "jdk/src/java.desktop/macosx/native/include/jawt_md.h at master · openjdk/jdk · GitHub"
[11]: https://github.com/mcourteaux/MultiTouch-Gestures-Java "GitHub - mcourteaux/MultiTouch-Gestures-Java: MultiTouch Gesture support for Java on OS X. · GitHub"
