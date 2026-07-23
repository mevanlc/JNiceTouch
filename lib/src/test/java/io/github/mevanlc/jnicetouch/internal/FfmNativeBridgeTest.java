package io.github.mevanlc.jnicetouch.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.lang.foreign.MemorySegment;
import org.testng.annotations.Test;

public class FfmNativeBridgeTest {
    @Test
    public void callsVersionedNativeDiscoveryAbi() {
        NativeBridge bridge = FfmNativeBridge.open();

        assertEquals(bridge.abiVersion(), NativeConstants.ABI_VERSION);
        assertEquals(
                bridge.eventStructSize(NativeConstants.ABI_VERSION),
                NativeConstants.EVENT_STRUCT_SIZE);
        assertEquals(bridge.eventStructSize(999), 0L);
        assertTrue((bridge.capabilities() & NativeConstants.CAP_PRECISE_SCROLL) != 0);
        assertTrue((bridge.capabilities() & NativeConstants.CAP_HAPTIC_FEEDBACK) != 0);
        assertEquals(bridge.install(1L << 63), NativeConstants.STATUS_UNSUPPORTED);
        assertEquals(bridge.waitAndDrain(MemorySegment.NULL, 1, 0), 0L);
        assertEquals(
                bridge.performHapticFeedback(99, 0),
                NativeConstants.STATUS_INVALID_ARGUMENT);
        assertTrue(bridge.droppedEventCount() >= 0);
    }
}
