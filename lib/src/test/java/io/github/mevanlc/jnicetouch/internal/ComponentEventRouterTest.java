package io.github.mevanlc.jnicetouch.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import io.github.mevanlc.jnicetouch.EventKind;
import io.github.mevanlc.jnicetouch.EventPhase;
import io.github.mevanlc.jnicetouch.MacTrackpadEvent;
import io.github.mevanlc.jnicetouch.PressureBehavior;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class ComponentEventRouterTest {
    @Test
    public void convertsAppKitCoordinatesUsingPrimaryScreenHeight() {
        Point result = ComponentEventRouter.toAwtScreenPoint(
                -150.75, 900.25, new Rectangle(0, 0, 1920, 1080));

        assertEquals(result, new Point(-151, 179));
    }

    @Test
    public void pinsInitialDeepestComponentUntilGestureEnds() throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            throw new SkipException("component routing requires a graphics environment");
        }

        List<Component> targets = new ArrayList<>();
        List<MacTrackpadEvent> received = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            ComponentEventRouter router = new ComponentEventRouter();
            ShowingPanel root = new ShowingPanel(new Point(100, 200));
            root.setLayout(null);
            root.setSize(300, 200);
            JPanel child = new JPanel();
            child.setBounds(20, 30, 100, 80);
            root.add(child);
            router.listen(root, (target, event) -> {
                targets.add(target);
                received.add(event);
            });

            Rectangle primary = GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .getBounds();
            double initialAppKitY = primary.getY() + primary.getHeight() - 250;
            router.dispatch(List.of(event(
                    7, 130, initialAppKitY, Set.of(EventPhase.BEGAN), Set.of(), 1, 0)));
            router.dispatch(List.of(event(
                    7, -10_000, -10_000, Set.of(EventPhase.ENDED), Set.of(), 8, 0)));
            router.dispatch(List.of(event(
                    7, -10_000, -10_000, Set.of(), Set.of(EventPhase.BEGAN), 0, 1)));
            router.dispatch(List.of(event(
                    7, -10_000, -10_000, Set.of(), Set.of(EventPhase.ENDED), 0, 8)));
            router.dispatch(List.of(event(
                    7, -10_000, -10_000, Set.of(EventPhase.CHANGED), Set.of(), 4, 0)));
        });

        assertEquals(received.size(), 4);
        assertSame(targets.get(0), targets.get(1));
        assertSame(targets.get(1), targets.get(2));
        assertSame(targets.get(2), targets.get(3));
    }

    private static MacTrackpadEvent event(
            long sequence,
            double screenX,
            double screenY,
            Set<EventPhase> phases,
            Set<EventPhase> momentumPhases,
            int nativePhase,
            int nativeMomentumPhase) {
        return new MacTrackpadEvent(
                EventKind.SCROLL,
                phases,
                momentumPhases,
                nativePhase,
                nativeMomentumPhase,
                sequence,
                1,
                0,
                1,
                screenX,
                screenY,
                0,
                1,
                0,
                0,
                0,
                0,
                0,
                PressureBehavior.UNKNOWN,
                -1,
                0,
                Set.of());
    }

    private static final class ShowingPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final Point screenLocation;

        private ShowingPanel(Point screenLocation) {
            this.screenLocation = screenLocation;
        }

        @Override
        public boolean isShowing() {
            return true;
        }

        @Override
        public Point getLocationOnScreen() {
            return new Point(screenLocation);
        }
    }
}
