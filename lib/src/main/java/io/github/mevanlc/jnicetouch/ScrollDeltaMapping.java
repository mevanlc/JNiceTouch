package io.github.mevanlc.jnicetouch;

/**
 * Common translations of preference-adjusted AppKit scrolling deltas.
 *
 * <p>These mappings do not undo the user's Natural Scrolling preference. AppKit
 * has already applied that preference before JNiceTouch receives the deltas.
 */
public enum ScrollDeltaMapping {
    /**
     * Maps {@code (x, y)} to {@code (x, -y)} for a canvas whose view transform
     * renders its pan state as {@code (screenX + panX, screenY - panY)}.
     */
    CANVAS(1.0, -1.0),

    /** Maps {@code (x, y)} to {@code (-x, -y)}. */
    REVERSED(-1.0, -1.0),

    /** Preserves the preference-adjusted AppKit values as {@code (x, y)}. */
    RAW_APPKIT(1.0, 1.0);

    private final double xMultiplier;
    private final double yMultiplier;

    ScrollDeltaMapping(double xMultiplier, double yMultiplier) {
        this.xMultiplier = xMultiplier;
        this.yMultiplier = yMultiplier;
    }

    /**
     * Maps one pair of AppKit scrolling deltas.
     *
     * @param deltaX preference-adjusted AppKit horizontal delta
     * @param deltaY preference-adjusted AppKit vertical delta
     * @return the mapped delta pair
     */
    public ScrollDelta map(double deltaX, double deltaY) {
        return new ScrollDelta(xMultiplier * deltaX, yMultiplier * deltaY);
    }
}
