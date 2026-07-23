package io.github.mevanlc.jnicetouch;

/** When AppKit should perform requested haptic feedback. */
public enum HapticPerformanceTime {
    /** Let AppKit choose the appropriate synchronization point. */
    DEFAULT,
    /** Perform immediately without display synchronization. */
    NOW,
    /** Synchronize with completion of the next drawing pass. */
    DRAW_COMPLETED
}
