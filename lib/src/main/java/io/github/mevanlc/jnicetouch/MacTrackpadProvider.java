package io.github.mevanlc.jnicetouch;

import java.util.Set;
import javax.swing.JComponent;

/**
 * Application-local macOS trackpad input. Listener callbacks always run on
 * the Swing event-dispatch thread.
 */
public interface MacTrackpadProvider extends AutoCloseable {
    /**
     * Returns every capability implemented by the loaded native bridge.
     *
     * @return an immutable capability set
     */
    Set<Capability> capabilities();

    /**
     * Registers an EDT listener for events initially routed inside {@code root}.
     *
     * @param root component subtree to observe
     * @param listener callback invoked on the EDT
     * @return an idempotently closeable registration
     */
    Subscription listen(JComponent root, MacTrackpadListener listener);

    /**
     * Requests public AppKit haptic feedback asynchronously.
     *
     * @param pattern semantic feedback pattern
     * @param performanceTime display synchronization policy
     */
    void performHapticFeedback(
            HapticFeedbackPattern pattern,
            HapticPerformanceTime performanceTime);

    /**
     * Returns the number of native records lost to queue pressure.
     *
     * @return cumulative dropped record count
     */
    long droppedNativeEventCount();

    /**
     * Returns whether the provider is accepting work.
     *
     * @return whether the provider is open
     */
    boolean isOpen();

    @Override
    void close();
}
