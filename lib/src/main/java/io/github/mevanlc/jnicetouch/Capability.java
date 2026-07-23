package io.github.mevanlc.jnicetouch;

/** A feature implemented by the native JNiceTouch bridge. */
public enum Capability {
    /** Two-dimensional scrolling with AppKit phase metadata. */
    PRECISE_SCROLL,
    /** Pinch magnification. */
    MAGNIFY,
    /** Two-finger rotation. */
    ROTATE,
    /** Discrete swipe gestures. */
    SWIPE,
    /** AppKit smart magnification. */
    SMART_MAGNIFY,
    /** Pressure and Force Touch stages. */
    PRESSURE,
    /** Quick Look gestures. */
    QUICK_LOOK,
    /** AppKit haptic feedback output. */
    HAPTIC_FEEDBACK
}
