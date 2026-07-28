# JNiceTouch

JNiceTouch is a macOS trackpad-input bridge for Swing applications running on
JDK 22 or newer.

```text
AppKit local event monitor (Objective-C++)
                  ↓
       versioned, flat C ABI
                  ↓
     java.lang.foreign downcalls
                  ↓
 dedicated platform thread → one EDT task per batch
```

The native boundary contains only fixed-width numbers and caller-owned record
buffers. It never passes `NSObject`, `NSWindow`, blocks, or other Cocoa objects
to Java, and it never calls Java from the AppKit thread.

## Features

- precise two-dimensional scrolling, physical and momentum phases, and natural
  scrolling metadata
- magnify, rotate, swipe, smart-magnify, pressure, and Quick Look events
- public haptic-feedback patterns
- bounded, nonblocking native capture with changed-event coalescing and explicit
  cancellation after queue overflow
- monotonic timestamps, gesture sequence IDs, AppKit modifier flags, native
  window numbers, and screen locations
- Swing listeners delivered on the EDT, with the initial deepest component
  pinned through the gesture and scroll momentum
- a universal arm64/x86_64 dylib embedded in the library JAR

Ordinary Swing controls should continue to let AWT own wheel scrolling. A
JNiceTouch scroll event can supply phase and metadata, but applying its delta to
a standard control would scroll twice. A custom canvas that lets JNiceTouch own
scrolling should consume the corresponding AWT wheel event separately.

## Use

Applications must explicitly grant native access to the module containing the
FFM calls. Use one of:

```text
--enable-native-access=ALL-UNNAMED
--enable-native-access=io.github.mevanlc.jnicetouch
```

The first form applies when JNiceTouch is on the class path; the second applies
when its named module is on the module path.

```java
MacTrackpadProvider provider = JNiceTouch.open();
Subscription subscription = provider.listen(canvas, (target, event) -> {
    // Always the Swing EDT. "target" remains pinned for the sequence.
    switch (event.kind()) {
        case SCROLL -> {
            ScrollDelta delta = ScrollDeltaMapping.CANVAS.map(
                    event.deltaX(), event.deltaY());
            panX += delta.x();
            panY += delta.y();
        }
        case MAGNIFY -> zoomBy(event.magnification());
        case ROTATE -> rotateBy(event.rotationDegrees());
        default -> { }
    }
});

// During application shutdown:
subscription.close();
provider.close();
```

`ScrollDeltaMapping.CANVAS` is intended for pan state rendered as
`(screenX + panX, screenY - panY)`. `REVERSED` negates both AppKit deltas, while
`RAW_APPKIT` preserves them. These mappings operate on values that already honor
the user's macOS Natural Scrolling preference.

Only one provider can own the process-wide AppKit monitor at a time. The
overload `JNiceTouch.open(Set<Capability>)` can reduce the monitored event mask.
The optional `jnicetouch.native.library` system property selects an unpackaged
dylib during native development.

## Build and test

The Gradle build provisions a JDK 22 toolchain. On macOS with Xcode Command Line
Tools installed, run:

```text
./gradlew test
```

This builds both slices of the dylib, runs the C layout/discovery test, runs the
Objective-C++ queue and sequence-policy tests, then runs the Java tests. The
Java suite includes a real FFM downcall into the packaged dylib.

The ABI contract and evolution rules are documented in
[`devdocs/C-ABI.md`](devdocs/C-ABI.md). The architectural investigation and
scope decisions are in
[`devdocs/BACKGROUND-RESEARCH.md`](devdocs/BACKGROUND-RESEARCH.md).

## Scope

The current implementation is the JDK 22+ FFM path. It does not expose raw
per-finger contacts, use a global event tap, patch AWT, or include the optional
JNI compatibility adapter for older JDKs. Component routing uses the public
screen-bounds approximation described in the background research; exact
native-window matching would require an isolated JDK-specific adapter.
