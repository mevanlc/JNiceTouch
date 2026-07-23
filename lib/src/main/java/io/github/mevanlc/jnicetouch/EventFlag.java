package io.github.mevanlc.jnicetouch;

/** Metadata attached to a {@link MacTrackpadEvent}. */
public enum EventFlag {
    /** AppKit reported high-resolution scrolling deltas. */
    PRECISE_SCROLL,
    /** The user's scrolling direction differs from the device direction. */
    DIRECTION_INVERTED_FROM_DEVICE,
    /** Native queue loss forced sequence cancellation. */
    QUEUE_OVERFLOW_OCCURRED
}
