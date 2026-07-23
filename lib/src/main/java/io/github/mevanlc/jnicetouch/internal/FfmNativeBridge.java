package io.github.mevanlc.jnicetouch.internal;

import io.github.mevanlc.jnicetouch.JNiceTouchException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

final class FfmNativeBridge implements NativeBridge {
    private final MethodHandle abiVersion;
    private final MethodHandle eventStructSize;
    private final MethodHandle capabilities;
    private final MethodHandle install;
    private final MethodHandle waitAndDrain;
    private final MethodHandle uninstall;
    private final MethodHandle performHapticFeedback;
    private final MethodHandle droppedEventCount;

    static FfmNativeBridge open() {
        NativeLibraryLoader.load();
        return new FfmNativeBridge();
    }

    private FfmNativeBridge() {
        Linker linker = Linker.nativeLinker();
        SymbolLookup symbols = SymbolLookup.loaderLookup();
        abiVersion = bind(
                linker, symbols, "jnt_abi_version",
                FunctionDescriptor.of(ValueLayout.JAVA_INT));
        eventStructSize = bind(
                linker, symbols, "jnt_event_struct_size",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        capabilities = bind(
                linker, symbols, "jnt_capabilities_v1",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        install = bind(
                linker, symbols, "jnt_install_v1",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
        waitAndDrain = bind(
                linker, symbols, "jnt_wait_and_drain_v1",
                FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT));
        uninstall = bind(
                linker, symbols, "jnt_uninstall_v1",
                FunctionDescriptor.ofVoid());
        performHapticFeedback = bind(
                linker, symbols, "jnt_perform_haptic_feedback_v1",
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT));
        droppedEventCount = bind(
                linker, symbols, "jnt_dropped_event_count_v1",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    }

    @SuppressWarnings("restricted")
    private static MethodHandle bind(
            Linker linker,
            SymbolLookup symbols,
            String name,
            FunctionDescriptor descriptor) {
        MemorySegment symbol = symbols.find(name).orElseThrow(
                () -> new JNiceTouchException("Native symbol is missing: " + name));
        return linker.downcallHandle(symbol, descriptor);
    }

    @Override
    public int abiVersion() {
        try {
            return (int) abiVersion.invokeExact();
        } catch (Throwable throwable) {
            throw callFailed("jnt_abi_version", throwable);
        }
    }

    @Override
    public long eventStructSize(int version) {
        try {
            return (long) eventStructSize.invokeExact(version);
        } catch (Throwable throwable) {
            throw callFailed("jnt_event_struct_size", throwable);
        }
    }

    @Override
    public long capabilities() {
        try {
            return (long) capabilities.invokeExact();
        } catch (Throwable throwable) {
            throw callFailed("jnt_capabilities_v1", throwable);
        }
    }

    @Override
    public int install(long requestedCapabilities) {
        try {
            return (int) install.invokeExact(requestedCapabilities);
        } catch (Throwable throwable) {
            throw callFailed("jnt_install_v1", throwable);
        }
    }

    @Override
    public long waitAndDrain(
            MemorySegment destination,
            long capacity,
            int timeoutMillis) {
        try {
            return (long) waitAndDrain.invokeExact(destination, capacity, timeoutMillis);
        } catch (Throwable throwable) {
            throw callFailed("jnt_wait_and_drain_v1", throwable);
        }
    }

    @Override
    public void uninstall() {
        try {
            uninstall.invokeExact();
        } catch (Throwable throwable) {
            throw callFailed("jnt_uninstall_v1", throwable);
        }
    }

    @Override
    public int performHapticFeedback(int pattern, int performanceTime) {
        try {
            return (int) performHapticFeedback.invokeExact(pattern, performanceTime);
        } catch (Throwable throwable) {
            throw callFailed("jnt_perform_haptic_feedback_v1", throwable);
        }
    }

    @Override
    public long droppedEventCount() {
        try {
            return (long) droppedEventCount.invokeExact();
        } catch (Throwable throwable) {
            throw callFailed("jnt_dropped_event_count_v1", throwable);
        }
    }

    private static JNiceTouchException callFailed(String function, Throwable cause) {
        return new JNiceTouchException("Native call failed: " + function, cause);
    }
}
