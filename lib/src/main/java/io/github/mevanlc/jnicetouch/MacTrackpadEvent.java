package io.github.mevanlc.jnicetouch;

import java.util.Objects;
import java.util.Set;

/**
 * An immutable Java representation of one high-level AppKit trackpad event.
 * Coordinates use AppKit screen coordinates and deltas already honor the
 * user's natural-scrolling preference.
 *
 * @param kind high-level event kind
 * @param phases normalized physical gesture phases
 * @param momentumPhases normalized momentum phases
 * @param nativePhaseBits unmodified public {@code NSEventPhase} bits
 * @param nativeMomentumPhaseBits unmodified momentum phase bits
 * @param sequenceId native gesture-sequence identifier
 * @param timestampNanos monotonic nanoseconds since system startup
 * @param modifiers unmodified public AppKit modifier flags
 * @param nativeWindowNumber AppKit window number, or {@code -1} when absent
 * @param screenX AppKit global screen X coordinate
 * @param screenY AppKit global screen Y coordinate
 * @param deltaX horizontal scroll or swipe delta
 * @param deltaY vertical scroll or swipe delta
 * @param magnification incremental magnification delta
 * @param rotationDegrees incremental rotation in degrees
 * @param pressure normalized pressure
 * @param stageTransition pressure-stage transition value
 * @param pressureStage pressure stage
 * @param pressureBehavior normalized pressure behavior
 * @param nativePressureBehavior unmodified AppKit pressure behavior value
 * @param nativeFlags unmodified v1 event flag bits
 * @param flags normalized event flags
 */
public record MacTrackpadEvent(
        EventKind kind,
        Set<EventPhase> phases,
        Set<EventPhase> momentumPhases,
        int nativePhaseBits,
        int nativeMomentumPhaseBits,
        long sequenceId,
        long timestampNanos,
        long modifiers,
        long nativeWindowNumber,
        double screenX,
        double screenY,
        double deltaX,
        double deltaY,
        double magnification,
        double rotationDegrees,
        double pressure,
        double stageTransition,
        int pressureStage,
        PressureBehavior pressureBehavior,
        int nativePressureBehavior,
        int nativeFlags,
        Set<EventFlag> flags) {

    /** Validates and defensively copies the record's collection values. */
    public MacTrackpadEvent {
        Objects.requireNonNull(kind, "kind");
        phases = Set.copyOf(phases);
        momentumPhases = Set.copyOf(momentumPhases);
        Objects.requireNonNull(pressureBehavior, "pressureBehavior");
        flags = Set.copyOf(flags);
    }

    /**
     * Returns whether this record terminates its pinned gesture target.
     *
     * @return whether the pinned target should be released
     */
    public boolean isTerminal() {
        if (kind == EventKind.CANCEL
                || phases.contains(EventPhase.CANCELLED)
                || momentumPhases.contains(EventPhase.CANCELLED)) {
            return true;
        }
        if (kind == EventKind.SCROLL) {
            // Physical scrolling may end immediately before momentum begins.
            return momentumPhases.contains(EventPhase.ENDED);
        }
        return phases.contains(EventPhase.ENDED)
                || momentumPhases.contains(EventPhase.ENDED);
    }

    /**
     * Returns whether AppKit supplied no lifecycle phase for this event.
     *
     * @return whether the event is discrete
     */
    public boolean isDiscrete() {
        return nativePhaseBits == 0 && nativeMomentumPhaseBits == 0;
    }
}
