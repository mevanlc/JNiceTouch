package io.github.mevanlc.jnicetouch.internal;

import io.github.mevanlc.jnicetouch.EventKind;
import io.github.mevanlc.jnicetouch.EventPhase;
import io.github.mevanlc.jnicetouch.MacTrackpadEvent;
import io.github.mevanlc.jnicetouch.MacTrackpadListener;
import io.github.mevanlc.jnicetouch.Subscription;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

final class ComponentEventRouter {
    private final CopyOnWriteArrayList<Registration> registrations =
            new CopyOnWriteArrayList<>();
    private final Map<Long, PinnedRoute> pinnedSequences = new HashMap<>();

    Subscription listen(JComponent root, MacTrackpadListener listener) {
        Registration registration = new Registration(root, listener);
        registrations.add(registration);
        return registration;
    }

    void dispatch(List<MacTrackpadEvent> batch) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Trackpad events must be routed on the EDT");
        }
        for (MacTrackpadEvent event : batch) {
            dispatch(event);
        }
    }

    void deactivateAll() {
        for (Registration registration : registrations) {
            registration.close();
        }
    }

    void clearPinnedSequences() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Pinned routes must be cleared on the EDT");
        }
        pinnedSequences.clear();
    }

    private void dispatch(MacTrackpadEvent event) {
        if (event.kind() == EventKind.CANCEL) {
            List<Delivery> deliveries = event.sequenceId() == 0
                    ? allPinnedDeliveries()
                    : deliveries(pinnedSequences.remove(event.sequenceId()));
            deliver(deliveries, event);
            if (event.sequenceId() == 0) {
                pinnedSequences.clear();
            }
            return;
        }

        if (event.phases().contains(EventPhase.BEGAN)
                || event.phases().contains(EventPhase.MAY_BEGIN)) {
            pinnedSequences.entrySet().removeIf(entry ->
                    entry.getKey() != event.sequenceId()
                            && entry.getValue().kind() == event.kind());
        }

        PinnedRoute pinned = pinnedSequences.get(event.sequenceId());
        List<Delivery> deliveries = deliveries(pinned);
        if (deliveries == null) {
            deliveries = routeInitialEvent(event);
            if (!deliveries.isEmpty() && !event.isDiscrete() && !event.isTerminal()) {
                pinnedSequences.put(
                        event.sequenceId(), new PinnedRoute(event.kind(), deliveries));
            }
        }
        deliver(deliveries, event);
        if (event.isTerminal()) {
            pinnedSequences.remove(event.sequenceId());
        }
    }

    private List<Delivery> routeInitialEvent(MacTrackpadEvent event) {
        Point awtPoint = toAwtScreenPoint(event.screenX(), event.screenY());
        List<Delivery> result = new ArrayList<>();
        for (Registration registration : registrations) {
            if (!registration.isActive() || !registration.root.isShowing()) {
                continue;
            }
            try {
                Point rootLocation = registration.root.getLocationOnScreen();
                Rectangle rootBounds = new Rectangle(
                        rootLocation, registration.root.getSize());
                if (!rootBounds.contains(awtPoint)) {
                    continue;
                }
                Point pointInRoot = new Point(awtPoint);
                SwingUtilities.convertPointFromScreen(pointInRoot, registration.root);
                Component target = SwingUtilities.getDeepestComponentAt(
                        registration.root, pointInRoot.x, pointInRoot.y);
                result.add(new Delivery(
                        registration,
                        target == null ? registration.root : target));
            } catch (IllegalComponentStateException ignored) {
                // The component stopped showing between the checks above.
            }
        }
        return List.copyOf(result);
    }

    private List<Delivery> allPinnedDeliveries() {
        LinkedHashSet<Delivery> result = new LinkedHashSet<>();
        for (PinnedRoute route : pinnedSequences.values()) {
            result.addAll(route.deliveries());
        }
        return List.copyOf(result);
    }

    private static List<Delivery> deliveries(PinnedRoute route) {
        return route == null ? null : route.deliveries();
    }

    private static void deliver(List<Delivery> deliveries, MacTrackpadEvent event) {
        if (deliveries == null) {
            return;
        }
        for (Delivery delivery : deliveries) {
            if (!delivery.registration.isActive()) {
                continue;
            }
            try {
                delivery.registration.listener.onTrackpadEvent(delivery.target, event);
            } catch (RuntimeException exception) {
                Thread thread = Thread.currentThread();
                thread.getUncaughtExceptionHandler().uncaughtException(thread, exception);
            }
        }
    }

    static Point toAwtScreenPoint(double appKitX, double appKitY) {
        Rectangle primaryBounds = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration()
                .getBounds();
        return toAwtScreenPoint(appKitX, appKitY, primaryBounds);
    }

    static Point toAwtScreenPoint(
            double appKitX,
            double appKitY,
            Rectangle primaryBounds) {
        double awtY = primaryBounds.getY() + primaryBounds.getHeight() - appKitY;
        return new Point((int) Math.floor(appKitX), (int) Math.floor(awtY));
    }

    private record Delivery(Registration registration, Component target) {}

    private record PinnedRoute(EventKind kind, List<Delivery> deliveries) {}

    private final class Registration implements Subscription {
        private final JComponent root;
        private final MacTrackpadListener listener;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Registration(JComponent root, MacTrackpadListener listener) {
            this.root = Objects.requireNonNull(root, "root");
            this.listener = Objects.requireNonNull(listener, "listener");
        }

        @Override
        public boolean isActive() {
            return active.get();
        }

        @Override
        public void close() {
            if (active.compareAndSet(true, false)) {
                registrations.remove(this);
            }
        }
    }
}
