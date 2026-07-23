package io.github.mevanlc.jnicetouch;

import io.github.mevanlc.jnicetouch.internal.FfmMacTrackpadProvider;
import java.util.EnumSet;
import java.util.Set;

/** Entry point for the JDK 22+ Foreign Function and Memory provider. */
public final class JNiceTouch {
    private JNiceTouch() {}

    /**
     * Returns whether this runtime can load the macOS FFM provider.
     *
     * @return whether the OS and Java feature version are supported
     */
    public static boolean isSupported() {
        return System.getProperty("os.name").equals("Mac OS X")
                && Runtime.version().feature() >= 22;
    }

    /**
     * Opens a provider capturing every supported input capability.
     *
     * @return the process-wide provider
     * @throws JNiceTouchException if the runtime or native bridge is unavailable
     */
    public static MacTrackpadProvider open() {
        return open(EnumSet.complementOf(EnumSet.of(Capability.HAPTIC_FEEDBACK)));
    }

    /**
     * Opens a provider capturing the requested event capabilities.
     *
     * @param requestedCapabilities event capabilities to monitor
     * @return the process-wide provider
     * @throws JNiceTouchException if the runtime or native bridge is unavailable
     * @throws IllegalArgumentException if no event capability is requested
     */
    public static MacTrackpadProvider open(Set<Capability> requestedCapabilities) {
        if (!isSupported()) {
            throw new JNiceTouchException("JNiceTouch requires macOS and JDK 22 or newer");
        }
        return FfmMacTrackpadProvider.open(requestedCapabilities);
    }
}
