package io.github.mevanlc.jnicetouch.internal;

import io.github.mevanlc.jnicetouch.Capability;
import java.util.EnumSet;
import java.util.Set;

final class NativeConstants {
    static final int ABI_VERSION = 1;
    static final long EVENT_STRUCT_SIZE = 128;

    static final int STATUS_OK = 0;
    static final int STATUS_INVALID_ARGUMENT = -1;
    static final int STATUS_UNSUPPORTED = -2;
    static final int STATUS_ALREADY_INSTALLED = -3;
    static final int STATUS_NATIVE_ERROR = -4;

    static final long CAP_PRECISE_SCROLL = 1L << 0;
    static final long CAP_MAGNIFY = 1L << 1;
    static final long CAP_ROTATE = 1L << 2;
    static final long CAP_SWIPE = 1L << 3;
    static final long CAP_SMART_MAGNIFY = 1L << 4;
    static final long CAP_PRESSURE = 1L << 5;
    static final long CAP_QUICK_LOOK = 1L << 6;
    static final long CAP_HAPTIC_FEEDBACK = 1L << 7;

    private NativeConstants() {}

    static long capabilityMask(Set<Capability> capabilities) {
        long result = 0;
        for (Capability capability : capabilities) {
            result |= switch (capability) {
                case PRECISE_SCROLL -> CAP_PRECISE_SCROLL;
                case MAGNIFY -> CAP_MAGNIFY;
                case ROTATE -> CAP_ROTATE;
                case SWIPE -> CAP_SWIPE;
                case SMART_MAGNIFY -> CAP_SMART_MAGNIFY;
                case PRESSURE -> CAP_PRESSURE;
                case QUICK_LOOK -> CAP_QUICK_LOOK;
                case HAPTIC_FEEDBACK -> CAP_HAPTIC_FEEDBACK;
            };
        }
        return result;
    }

    static Set<Capability> capabilities(long mask) {
        EnumSet<Capability> result = EnumSet.noneOf(Capability.class);
        addIfSet(result, Capability.PRECISE_SCROLL, mask, CAP_PRECISE_SCROLL);
        addIfSet(result, Capability.MAGNIFY, mask, CAP_MAGNIFY);
        addIfSet(result, Capability.ROTATE, mask, CAP_ROTATE);
        addIfSet(result, Capability.SWIPE, mask, CAP_SWIPE);
        addIfSet(result, Capability.SMART_MAGNIFY, mask, CAP_SMART_MAGNIFY);
        addIfSet(result, Capability.PRESSURE, mask, CAP_PRESSURE);
        addIfSet(result, Capability.QUICK_LOOK, mask, CAP_QUICK_LOOK);
        addIfSet(result, Capability.HAPTIC_FEEDBACK, mask, CAP_HAPTIC_FEEDBACK);
        return Set.copyOf(result);
    }

    private static void addIfSet(
            EnumSet<Capability> destination,
            Capability capability,
            long mask,
            long bit) {
        if ((mask & bit) != 0) {
            destination.add(capability);
        }
    }
}
