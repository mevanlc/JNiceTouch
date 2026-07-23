package io.github.mevanlc.jnicetouch.internal;

import java.lang.foreign.MemorySegment;

interface NativeBridge {
    int abiVersion();

    long eventStructSize(int abiVersion);

    long capabilities();

    int install(long requestedCapabilities);

    long waitAndDrain(MemorySegment destination, long capacity, int timeoutMillis);

    void uninstall();

    int performHapticFeedback(int pattern, int performanceTime);

    long droppedEventCount();
}
