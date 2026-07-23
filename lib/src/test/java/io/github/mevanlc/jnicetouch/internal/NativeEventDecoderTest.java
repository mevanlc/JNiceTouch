package io.github.mevanlc.jnicetouch.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import io.github.mevanlc.jnicetouch.EventFlag;
import io.github.mevanlc.jnicetouch.EventKind;
import io.github.mevanlc.jnicetouch.EventPhase;
import io.github.mevanlc.jnicetouch.JNiceTouchException;
import io.github.mevanlc.jnicetouch.MacTrackpadEvent;
import io.github.mevanlc.jnicetouch.PressureBehavior;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Set;
import org.testng.annotations.Test;

public class NativeEventDecoderTest {
    @Test
    public void decodesEveryV1FieldAndNormalizedValue() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = validRecord(arena);
            record.set(ValueLayout.JAVA_SHORT, 6, (short) 1);
            record.set(ValueLayout.JAVA_INT, 8, 0b111);
            record.set(ValueLayout.JAVA_INT, 12, (1 << 0) | (1 << 2));
            record.set(ValueLayout.JAVA_INT, 16, 1 << 3);
            record.set(ValueLayout.JAVA_LONG, 24, 41L);
            record.set(ValueLayout.JAVA_LONG, 32, 1_234_567L);
            record.set(ValueLayout.JAVA_LONG, 40, 0x1_0000_0000L);
            record.set(ValueLayout.JAVA_LONG, 48, -17L);
            record.set(ValueLayout.JAVA_DOUBLE, 56, -100.25);
            record.set(ValueLayout.JAVA_DOUBLE, 64, 800.5);
            record.set(ValueLayout.JAVA_DOUBLE, 72, 1.25);
            record.set(ValueLayout.JAVA_DOUBLE, 80, -2.5);
            record.set(ValueLayout.JAVA_DOUBLE, 88, 0.125);
            record.set(ValueLayout.JAVA_DOUBLE, 96, 12.0);
            record.set(ValueLayout.JAVA_DOUBLE, 104, 0.75);
            record.set(ValueLayout.JAVA_DOUBLE, 112, 0.5);
            record.set(ValueLayout.JAVA_INT, 120, 2);
            record.set(ValueLayout.JAVA_INT, 124, 5);

            MacTrackpadEvent event = NativeEventDecoder.decode(record, 0);

            assertEquals(event.kind(), EventKind.SCROLL);
            assertEquals(event.phases().size(), 2);
            assertTrue(event.phases().contains(EventPhase.BEGAN));
            assertTrue(event.phases().contains(EventPhase.CHANGED));
            assertEquals(event.momentumPhases(), Set.of(EventPhase.ENDED));
            assertEquals(event.nativePhaseBits(), 0b0101);
            assertEquals(event.sequenceId(), 41L);
            assertEquals(event.timestampNanos(), 1_234_567L);
            assertEquals(event.modifiers(), 0x1_0000_0000L);
            assertEquals(event.nativeWindowNumber(), -17L);
            assertEquals(event.screenX(), -100.25);
            assertEquals(event.screenY(), 800.5);
            assertEquals(event.deltaX(), 1.25);
            assertEquals(event.deltaY(), -2.5);
            assertEquals(event.magnification(), 0.125);
            assertEquals(event.rotationDegrees(), 12.0);
            assertEquals(event.pressure(), 0.75);
            assertEquals(event.stageTransition(), 0.5);
            assertEquals(event.pressureStage(), 2);
            assertEquals(event.pressureBehavior(), PressureBehavior.PRIMARY_DEEP_CLICK);
            assertEquals(event.nativePressureBehavior(), 5);
            assertEquals(event.nativeFlags(), 0b111);
            assertEquals(event.flags().size(), 3);
            assertTrue(event.flags().contains(EventFlag.PRECISE_SCROLL));
            assertTrue(event.flags().contains(EventFlag.DIRECTION_INVERTED_FROM_DEVICE));
            assertTrue(event.flags().contains(EventFlag.QUEUE_OVERFLOW_OCCURRED));
            assertTrue(event.isTerminal());
        }
    }

    @Test(expectedExceptions = JNiceTouchException.class)
    public void rejectsTooSmallRecord() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = validRecord(arena);
            record.set(ValueLayout.JAVA_INT, 0, 64);
            NativeEventDecoder.decode(record, 0);
        }
    }

    @Test(expectedExceptions = JNiceTouchException.class)
    public void rejectsUnknownEventKind() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment record = validRecord(arena);
            record.set(ValueLayout.JAVA_SHORT, 6, (short) 99);
            NativeEventDecoder.decode(record, 0);
        }
    }

    private static MemorySegment validRecord(Arena arena) {
        MemorySegment record = arena.allocate(NativeConstants.EVENT_STRUCT_SIZE, 8);
        record.set(ValueLayout.JAVA_INT, 0, (int) NativeConstants.EVENT_STRUCT_SIZE);
        record.set(ValueLayout.JAVA_SHORT, 4, (short) NativeConstants.ABI_VERSION);
        record.set(ValueLayout.JAVA_SHORT, 6, (short) 8);
        record.set(ValueLayout.JAVA_INT, 124, -1);
        return record;
    }
}
