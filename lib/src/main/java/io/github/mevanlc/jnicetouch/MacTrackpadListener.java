package io.github.mevanlc.jnicetouch;

import java.awt.Component;

/** Receives trackpad events on the Swing event-dispatch thread. */
@FunctionalInterface
public interface MacTrackpadListener {
    /**
     * Receives an event and the deepest Swing/AWT component under the initial
     * event location. The component remains pinned through the entire gesture,
     * including scroll momentum.
     *
     * @param target pinned component target
     * @param event immutable trackpad event
     */
    void onTrackpadEvent(Component target, MacTrackpadEvent event);
}
