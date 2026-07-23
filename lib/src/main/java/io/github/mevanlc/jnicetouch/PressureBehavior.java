package io.github.mevanlc.jnicetouch;

/** A normalized public AppKit pressure behavior. */
public enum PressureBehavior {
    /** AppKit does not know the pressure behavior. */
    UNKNOWN,
    /** AppKit's default primary behavior. */
    PRIMARY_DEFAULT,
    /** A one-stage click. */
    PRIMARY_CLICK,
    /** General-purpose pressure with a broad dynamic range. */
    PRIMARY_GENERIC,
    /** Pressure controls acceleration. */
    PRIMARY_ACCELERATOR,
    /** A two-stage Force Click. */
    PRIMARY_DEEP_CLICK,
    /** A two-stage Force Drag. */
    PRIMARY_DEEP_DRAG,
    /** A future native value not known to this Java API. */
    UNRECOGNIZED
}
