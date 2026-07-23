package io.github.mevanlc.jnicetouch.internal;

import io.github.mevanlc.jnicetouch.Capability;
import io.github.mevanlc.jnicetouch.HapticFeedbackPattern;
import io.github.mevanlc.jnicetouch.HapticPerformanceTime;
import io.github.mevanlc.jnicetouch.JNiceTouchException;
import io.github.mevanlc.jnicetouch.MacTrackpadEvent;
import io.github.mevanlc.jnicetouch.MacTrackpadListener;
import io.github.mevanlc.jnicetouch.MacTrackpadProvider;
import io.github.mevanlc.jnicetouch.Subscription;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** The JDK 22+ FFM implementation behind {@code JNiceTouch.open()}. */
public final class FfmMacTrackpadProvider implements MacTrackpadProvider {
    private static final int BATCH_CAPACITY = 64;
    private static final int WAIT_TIMEOUT_MILLIS = 100;
    private static final Duration CLOSE_WAIT = Duration.ofSeconds(2);

    private final NativeBridge bridge;
    private final Set<Capability> capabilities;
    private final ComponentEventRouter router = new ComponentEventRouter();
    private final AtomicBoolean open = new AtomicBoolean(true);
    private final AtomicReference<Throwable> pumpFailure = new AtomicReference<>();
    private final Thread pumpThread;

    private FfmMacTrackpadProvider(
            NativeBridge bridge,
            Set<Capability> requestedCapabilities) {
        this.bridge = bridge;
        verifyAbi(bridge);

        long nativeCapabilities = bridge.capabilities();
        capabilities = NativeConstants.capabilities(nativeCapabilities);
        long requestedMask = NativeConstants.capabilityMask(requestedCapabilities)
                & ~NativeConstants.CAP_HAPTIC_FEEDBACK;
        if (requestedMask == 0) {
            throw new IllegalArgumentException(
                    "At least one event capability must be requested");
        }
        long unsupported = requestedMask & ~nativeCapabilities;
        if (unsupported != 0) {
            throw new JNiceTouchException(
                    "The native bridge does not support requested capability mask 0x"
                            + Long.toHexString(unsupported));
        }

        checkStatus("install", bridge.install(requestedMask));
        pumpThread = Thread.ofPlatform()
                .daemon(true)
                .name("JNiceTouch event pump")
                .start(this::pumpEvents);
    }

    /**
     * Opens the internal FFM provider used by the public factory.
     *
     * @param requestedCapabilities event capabilities to monitor
     * @return a running provider
     */
    public static FfmMacTrackpadProvider open(Set<Capability> requestedCapabilities) {
        Objects.requireNonNull(requestedCapabilities, "requestedCapabilities");
        if (GraphicsEnvironment.isHeadless()) {
            throw new JNiceTouchException("JNiceTouch requires a graphical environment");
        }
        // Ensure the JVM's AppKit event loop exists before installing its monitor.
        Toolkit.getDefaultToolkit();
        EnumSet<Capability> requested = EnumSet.noneOf(Capability.class);
        requested.addAll(requestedCapabilities);
        return new FfmMacTrackpadProvider(FfmNativeBridge.open(), requested);
    }

    @Override
    public Set<Capability> capabilities() {
        return capabilities;
    }

    @Override
    public Subscription listen(JComponent root, MacTrackpadListener listener) {
        ensureOpen();
        return router.listen(root, listener);
    }

    @Override
    public void performHapticFeedback(
            HapticFeedbackPattern pattern,
            HapticPerformanceTime performanceTime) {
        ensureOpen();
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(performanceTime, "performanceTime");
        if (!capabilities.contains(Capability.HAPTIC_FEEDBACK)) {
            throw new JNiceTouchException("Haptic feedback is not supported");
        }
        checkStatus(
                "perform haptic feedback",
                bridge.performHapticFeedback(
                        nativeHapticPattern(pattern),
                        nativeHapticPerformanceTime(performanceTime)));
    }

    @Override
    public long droppedNativeEventCount() {
        ensureOpen();
        return bridge.droppedEventCount();
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }

    @Override
    public void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        router.deactivateAll();
        if (SwingUtilities.isEventDispatchThread()) {
            router.clearPinnedSequences();
        } else {
            EventQueue.invokeLater(router::clearPinnedSequences);
            waitForPump();
        }
    }

    private void pumpEvents() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment batch = arena.allocate(
                    Math.multiplyExact(NativeConstants.EVENT_STRUCT_SIZE, BATCH_CAPACITY),
                    8);
            while (open.get()) {
                long count = bridge.waitAndDrain(
                        batch, BATCH_CAPACITY, WAIT_TIMEOUT_MILLIS);
                if (count < 0 || count > BATCH_CAPACITY) {
                    throw new JNiceTouchException(
                            "Native drain returned an invalid record count: " + count);
                }
                if (count == 0) {
                    continue;
                }

                List<MacTrackpadEvent> events = new ArrayList<>((int) count);
                for (long index = 0; index < count; ++index) {
                    events.add(NativeEventDecoder.decode(batch, index));
                }
                List<MacTrackpadEvent> immutableBatch = List.copyOf(events);
                EventQueue.invokeLater(() -> router.dispatch(immutableBatch));
            }
        } catch (Throwable failure) {
            pumpFailure.compareAndSet(null, failure);
            open.set(false);
            router.deactivateAll();
            EventQueue.invokeLater(router::clearPinnedSequences);
        } finally {
            bridge.uninstall();
        }
    }

    private void waitForPump() {
        if (Thread.currentThread() == pumpThread) {
            return;
        }
        try {
            pumpThread.join(CLOSE_WAIT.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureOpen() {
        Throwable failure = pumpFailure.get();
        if (failure != null) {
            throw new JNiceTouchException("The native event pump failed", failure);
        }
        if (!open.get()) {
            throw new IllegalStateException("The JNiceTouch provider is closed");
        }
    }

    private static void verifyAbi(NativeBridge bridge) {
        int version = bridge.abiVersion();
        long size = bridge.eventStructSize(NativeConstants.ABI_VERSION);
        if (version < NativeConstants.ABI_VERSION
                || size != NativeConstants.EVENT_STRUCT_SIZE) {
            throw new JNiceTouchException(
                    "Incompatible native ABI: version " + version + ", event size " + size);
        }
    }

    private static void checkStatus(String operation, int status) {
        if (status == NativeConstants.STATUS_OK) {
            return;
        }
        String detail = switch (status) {
            case NativeConstants.STATUS_INVALID_ARGUMENT -> "invalid argument";
            case NativeConstants.STATUS_UNSUPPORTED -> "unsupported capability";
            case NativeConstants.STATUS_ALREADY_INSTALLED -> "provider already installed";
            case NativeConstants.STATUS_NATIVE_ERROR -> "native error";
            default -> "status " + status;
        };
        throw new JNiceTouchException("Unable to " + operation + ": " + detail);
    }

    private static int nativeHapticPattern(HapticFeedbackPattern pattern) {
        return switch (pattern) {
            case GENERIC -> 0;
            case ALIGNMENT -> 1;
            case LEVEL_CHANGE -> 2;
        };
    }

    private static int nativeHapticPerformanceTime(HapticPerformanceTime performanceTime) {
        return switch (performanceTime) {
            case DEFAULT -> 0;
            case NOW -> 1;
            case DRAW_COMPLETED -> 2;
        };
    }
}
