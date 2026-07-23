package io.github.mevanlc.jnicetouch;

/** A normalized view of one public {@code NSEventPhase} bit. */
public enum EventPhase {
    /** A sequence began. */
    BEGAN,
    /** The sequence is stationary. */
    STATIONARY,
    /** The sequence changed. */
    CHANGED,
    /** The sequence ended normally. */
    ENDED,
    /** The sequence was cancelled. */
    CANCELLED,
    /** The sequence may begin. */
    MAY_BEGIN
}
