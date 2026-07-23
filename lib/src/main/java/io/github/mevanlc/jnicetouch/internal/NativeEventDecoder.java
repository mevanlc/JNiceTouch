package io.github.mevanlc.jnicetouch.internal;

import io.github.mevanlc.jnicetouch.EventFlag;
import io.github.mevanlc.jnicetouch.EventKind;
import io.github.mevanlc.jnicetouch.EventPhase;
import io.github.mevanlc.jnicetouch.JNiceTouchException;
import io.github.mevanlc.jnicetouch.MacTrackpadEvent;
import io.github.mevanlc.jnicetouch.PressureBehavior;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.EnumSet;

final class NativeEventDecoder {
    private static final long OFFSET_STRUCT_SIZE = 0;
    private static final long OFFSET_ABI_VERSION = 4;
    private static final long OFFSET_KIND = 6;
    private static final long OFFSET_FLAGS = 8;
    private static final long OFFSET_PHASE = 12;
    private static final long OFFSET_MOMENTUM_PHASE = 16;
    private static final long OFFSET_SEQUENCE_ID = 24;
    private static final long OFFSET_TIMESTAMP_NS = 32;
    private static final long OFFSET_MODIFIER_FLAGS = 40;
    private static final long OFFSET_WINDOW_NUMBER = 48;
    private static final long OFFSET_SCREEN_X = 56;
    private static final long OFFSET_SCREEN_Y = 64;
    private static final long OFFSET_DELTA_X = 72;
    private static final long OFFSET_DELTA_Y = 80;
    private static final long OFFSET_MAGNIFICATION = 88;
    private static final long OFFSET_ROTATION_DEGREES = 96;
    private static final long OFFSET_PRESSURE = 104;
    private static final long OFFSET_STAGE_TRANSITION = 112;
    private static final long OFFSET_PRESSURE_STAGE = 120;
    private static final long OFFSET_PRESSURE_BEHAVIOR = 124;

    private NativeEventDecoder() {}

    static MacTrackpadEvent decode(MemorySegment batch, long index) {
        long base = Math.multiplyExact(index, NativeConstants.EVENT_STRUCT_SIZE);
        int structSize = batch.get(ValueLayout.JAVA_INT, base + OFFSET_STRUCT_SIZE);
        int abiVersion = Short.toUnsignedInt(
                batch.get(ValueLayout.JAVA_SHORT, base + OFFSET_ABI_VERSION));
        if (structSize < NativeConstants.EVENT_STRUCT_SIZE
                || abiVersion != NativeConstants.ABI_VERSION) {
            throw new JNiceTouchException(
                    "Incompatible native event record: ABI " + abiVersion
                            + ", size " + structSize);
        }

        int kind = Short.toUnsignedInt(batch.get(ValueLayout.JAVA_SHORT, base + OFFSET_KIND));
        int flags = batch.get(ValueLayout.JAVA_INT, base + OFFSET_FLAGS);
        int phase = batch.get(ValueLayout.JAVA_INT, base + OFFSET_PHASE);
        int momentumPhase = batch.get(ValueLayout.JAVA_INT, base + OFFSET_MOMENTUM_PHASE);
        int nativePressureBehavior =
                batch.get(ValueLayout.JAVA_INT, base + OFFSET_PRESSURE_BEHAVIOR);

        return new MacTrackpadEvent(
                eventKind(kind),
                phases(phase),
                phases(momentumPhase),
                phase,
                momentumPhase,
                batch.get(ValueLayout.JAVA_LONG, base + OFFSET_SEQUENCE_ID),
                batch.get(ValueLayout.JAVA_LONG, base + OFFSET_TIMESTAMP_NS),
                batch.get(ValueLayout.JAVA_LONG, base + OFFSET_MODIFIER_FLAGS),
                batch.get(ValueLayout.JAVA_LONG, base + OFFSET_WINDOW_NUMBER),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_SCREEN_X),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_SCREEN_Y),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_DELTA_X),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_DELTA_Y),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_MAGNIFICATION),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_ROTATION_DEGREES),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_PRESSURE),
                batch.get(ValueLayout.JAVA_DOUBLE, base + OFFSET_STAGE_TRANSITION),
                batch.get(ValueLayout.JAVA_INT, base + OFFSET_PRESSURE_STAGE),
                pressureBehavior(nativePressureBehavior),
                nativePressureBehavior,
                flags,
                flags(flags));
    }

    private static EventKind eventKind(int value) {
        return switch (value) {
            case 1 -> EventKind.SCROLL;
            case 2 -> EventKind.MAGNIFY;
            case 3 -> EventKind.ROTATE;
            case 4 -> EventKind.SWIPE;
            case 5 -> EventKind.SMART_MAGNIFY;
            case 6 -> EventKind.PRESSURE;
            case 7 -> EventKind.QUICK_LOOK;
            case 8 -> EventKind.CANCEL;
            default -> throw new JNiceTouchException("Unknown native event kind: " + value);
        };
    }

    private static EnumSet<EventPhase> phases(int bits) {
        EnumSet<EventPhase> result = EnumSet.noneOf(EventPhase.class);
        addPhase(result, bits, 1 << 0, EventPhase.BEGAN);
        addPhase(result, bits, 1 << 1, EventPhase.STATIONARY);
        addPhase(result, bits, 1 << 2, EventPhase.CHANGED);
        addPhase(result, bits, 1 << 3, EventPhase.ENDED);
        addPhase(result, bits, 1 << 4, EventPhase.CANCELLED);
        addPhase(result, bits, 1 << 5, EventPhase.MAY_BEGIN);
        return result;
    }

    private static void addPhase(
            EnumSet<EventPhase> result,
            int bits,
            int bit,
            EventPhase phase) {
        if ((bits & bit) != 0) {
            result.add(phase);
        }
    }

    private static EnumSet<EventFlag> flags(int bits) {
        EnumSet<EventFlag> result = EnumSet.noneOf(EventFlag.class);
        if ((bits & (1 << 0)) != 0) {
            result.add(EventFlag.PRECISE_SCROLL);
        }
        if ((bits & (1 << 1)) != 0) {
            result.add(EventFlag.DIRECTION_INVERTED_FROM_DEVICE);
        }
        if ((bits & (1 << 2)) != 0) {
            result.add(EventFlag.QUEUE_OVERFLOW_OCCURRED);
        }
        return result;
    }

    private static PressureBehavior pressureBehavior(int value) {
        return switch (value) {
            case -1 -> PressureBehavior.UNKNOWN;
            case 0 -> PressureBehavior.PRIMARY_DEFAULT;
            case 1 -> PressureBehavior.PRIMARY_CLICK;
            case 2 -> PressureBehavior.PRIMARY_GENERIC;
            case 3 -> PressureBehavior.PRIMARY_ACCELERATOR;
            case 5 -> PressureBehavior.PRIMARY_DEEP_CLICK;
            case 6 -> PressureBehavior.PRIMARY_DEEP_DRAG;
            default -> PressureBehavior.UNRECOGNIZED;
        };
    }
}
