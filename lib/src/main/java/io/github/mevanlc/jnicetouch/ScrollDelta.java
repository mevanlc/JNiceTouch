package io.github.mevanlc.jnicetouch;

/**
 * A two-dimensional scroll delta produced by a {@link ScrollDeltaMapping}.
 *
 * @param x mapped horizontal delta
 * @param y mapped vertical delta
 */
public record ScrollDelta(double x, double y) {}
