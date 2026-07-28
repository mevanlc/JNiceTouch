package io.github.mevanlc.jnicetouch;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

public class ScrollDeltaMappingTest {
    @Test
    public void mapsCanvasPanDeltas() {
        assertEquals(ScrollDeltaMapping.CANVAS.map(12.5, -7.25), new ScrollDelta(12.5, 7.25));
    }

    @Test
    public void reversesBothAppKitDeltas() {
        assertEquals(
                ScrollDeltaMapping.REVERSED.map(12.5, -7.25),
                new ScrollDelta(-12.5, 7.25));
    }

    @Test
    public void preservesRawAppKitDeltas() {
        assertEquals(
                ScrollDeltaMapping.RAW_APPKIT.map(12.5, -7.25),
                new ScrollDelta(12.5, -7.25));
    }
}
