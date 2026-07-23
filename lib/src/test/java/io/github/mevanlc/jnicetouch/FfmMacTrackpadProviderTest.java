package io.github.mevanlc.jnicetouch;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.awt.GraphicsEnvironment;
import java.util.Set;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class FfmMacTrackpadProviderTest {
    @Test(timeOut = 5_000)
    public void installsPumpsAndUninstallsThroughFfm() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new SkipException("native provider requires a graphics environment");
        }

        MacTrackpadProvider provider = JNiceTouch.open(Set.of(Capability.MAGNIFY));
        assertTrue(provider.isOpen());
        assertTrue(provider.capabilities().contains(Capability.MAGNIFY));

        provider.close();

        assertFalse(provider.isOpen());
    }
}
