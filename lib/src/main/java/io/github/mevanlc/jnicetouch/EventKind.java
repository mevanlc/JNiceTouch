package io.github.mevanlc.jnicetouch;

/** The high-level AppKit event represented by a {@link MacTrackpadEvent}. */
public enum EventKind {
    /** Scroll-wheel or trackpad scrolling. */
    SCROLL,
    /** Pinch magnification. */
    MAGNIFY,
    /** Two-finger rotation. */
    ROTATE,
    /** A discrete swipe. */
    SWIPE,
    /** A smart-magnify gesture. */
    SMART_MAGNIFY,
    /** Pressure or Force Touch input. */
    PRESSURE,
    /** A Quick Look gesture. */
    QUICK_LOOK,
    /** Cancellation synthesized after lifecycle or queue state loss. */
    CANCEL
}
