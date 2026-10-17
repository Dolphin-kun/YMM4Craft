package com.dolphin_kun.ymm4craft.core.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.ADDRESS;

final class Com {
    static final Linker LINKER = Linker.nativeLinker();
    private static final Map<FunctionDescriptor, MethodHandle> HANDLES = new ConcurrentHashMap<>();

    private Com() {
    }

    static SymbolLookup library(String name) {
        return SymbolLookup.libraryLookup(name, Arena.global());
    }

    static Object invoke(MemorySegment function, FunctionDescriptor descriptor, Object... args) {
        MethodHandle handle = HANDLES.computeIfAbsent(descriptor, LINKER::downcallHandle);
        Object[] all = new Object[args.length + 1];
        all[0] = function;
        System.arraycopy(args, 0, all, 1, args.length);
        try {
            return handle.invokeWithArguments(all);
        } catch (Throwable t) {
            throw new IllegalStateException("ネイティブ関数の呼び出しに失敗しました", t);
        }
    }

    static Object call(MemorySegment object, int index, FunctionDescriptor descriptor, Object... args) {
        MemorySegment vtable = object.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0)
                .reinterpret(ADDRESS.byteSize() * (index + 1L));
        MemorySegment function = vtable.getAtIndex(ADDRESS, index);
        Object[] all = new Object[args.length + 1];
        all[0] = object;
        System.arraycopy(args, 0, all, 1, args.length);
        return invoke(function, descriptor.insertArgumentLayouts(0, ADDRESS), all);
    }

    static void check(int hresult, String what) {
        if (hresult < 0) throw new IllegalStateException(what + " に失敗しました（HRESULT 0x" + Integer.toHexString(hresult) + "）");
    }

    static void release(MemorySegment object) {
        if (object == null || object.address() == 0) return;
        call(object, 2, FunctionDescriptor.of(ValueLayout.JAVA_INT));
    }

    static MemorySegment guid(Arena arena, String text) {
        UUID uuid = UUID.fromString(text);
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        MemorySegment g = arena.allocate(16);
        g.set(ValueLayout.JAVA_INT_UNALIGNED, 0, (int) (msb >>> 32));
        g.set(ValueLayout.JAVA_SHORT_UNALIGNED, 4, (short) (msb >>> 16));
        g.set(ValueLayout.JAVA_SHORT_UNALIGNED, 6, (short) msb);
        for (int i = 0; i < 8; i++) g.set(ValueLayout.JAVA_BYTE, 8 + i, (byte) (lsb >>> (56 - 8 * i)));
        return g;
    }
}
