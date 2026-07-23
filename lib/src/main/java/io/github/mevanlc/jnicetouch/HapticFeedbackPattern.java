package io.github.mevanlc.jnicetouch;

/** A public AppKit haptic feedback pattern, expressed without Cocoa types. */
public enum HapticFeedbackPattern {
    /** General-purpose feedback. */
    GENERIC,
    /** Feedback for an alignment action. */
    ALIGNMENT,
    /** Feedback for crossing a discrete level. */
    LEVEL_CHANGE
}
