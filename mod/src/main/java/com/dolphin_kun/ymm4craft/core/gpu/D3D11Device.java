package com.dolphin_kun.ymm4craft.core.gpu;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

final class D3D11Device implements AutoCloseable {
    private static final int DXGI_FACTORY1_ENUM_ADAPTERS1 = 12;
    private static final int DXGI_ADAPTER1_GET_DESC1 = 10;
    private static final int D3D11_DEVICE_OPEN_SHARED_RESOURCE = 28;
    private static final int UNKNOWN_QUERY_INTERFACE = 0;
    private static final int KEYED_MUTEX_ACQUIRE_SYNC = 8;
    private static final int KEYED_MUTEX_RELEASE_SYNC = 9;

    private static final String IID_IDXGI_FACTORY1 = "770aae78-f26f-4dba-a829-253c83d1b387";
    private static final String IID_ID3D11_TEXTURE2D = "6f15aaf2-d208-4e89-9ab4-489535d34f9c";
    private static final String IID_IDXGI_KEYED_MUTEX = "9d8e1289-d7b3-465f-8126-250e349af85d";

    private static final int DXGI_ERROR_NOT_FOUND = 0x887A0002;
    private static final int D3D_DRIVER_TYPE_UNKNOWN = 0;
    private static final int D3D11_SDK_VERSION = 7;
    private static final int ADAPTER_DESC1_SIZE = 312;
    private static final int ADAPTER_DESC1_LUID_OFFSET = 296;

    static final int WAIT_TIMEOUT = 0x102;

    final MemorySegment device;
    private final MemorySegment context;
    final String adapterName;

    private D3D11Device(MemorySegment device, MemorySegment context, String adapterName) {
        this.device = device;
        this.context = context;
        this.adapterName = adapterName;
    }

    static D3D11Device create(long luidLow, int luidHigh) {
        SymbolLookup dxgi = Com.library("dxgi.dll");
        SymbolLookup d3d11 = Com.library("d3d11.dll");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment factoryOut = arena.allocate(ADDRESS);
            int hr = (int) Com.invoke(dxgi.find("CreateDXGIFactory1").orElseThrow(),
                    FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS), Com.guid(arena, IID_IDXGI_FACTORY1), factoryOut);
            Com.check(hr, "CreateDXGIFactory1");
            MemorySegment factory = factoryOut.get(ADDRESS, 0);
            try {
                MemorySegment adapter = null;
                String name = null;
                for (int i = 0; ; i++) {
                    MemorySegment adapterOut = arena.allocate(ADDRESS);
                    hr = (int) Com.call(factory, DXGI_FACTORY1_ENUM_ADAPTERS1, FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS), i, adapterOut);
                    if (hr == DXGI_ERROR_NOT_FOUND) break;
                    Com.check(hr, "IDXGIFactory1::EnumAdapters1");
                    MemorySegment candidate = adapterOut.get(ADDRESS, 0);
                    MemorySegment desc = arena.allocate(ADAPTER_DESC1_SIZE, 8);
                    Com.check((int) Com.call(candidate, DXGI_ADAPTER1_GET_DESC1, FunctionDescriptor.of(JAVA_INT, ADDRESS), desc),
                            "IDXGIAdapter1::GetDesc1");
                    long low = Integer.toUnsignedLong(desc.get(JAVA_INT, ADAPTER_DESC1_LUID_OFFSET));
                    int high = desc.get(JAVA_INT, ADAPTER_DESC1_LUID_OFFSET + 4);
                    if (low == luidLow && high == luidHigh) {
                        adapter = candidate;
                        name = readWideString(desc, 128);
                        break;
                    }
                    Com.release(candidate);
                }
                if (adapter == null) throw new IllegalStateException("YMM4と同じGPUが見つかりません");

                try {
                    MemorySegment deviceOut = arena.allocate(ADDRESS);
                    MemorySegment contextOut = arena.allocate(ADDRESS);
                    hr = (int) Com.invoke(d3d11.find("D3D11CreateDevice").orElseThrow(),
                            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
                            adapter, D3D_DRIVER_TYPE_UNKNOWN, MemorySegment.NULL, 0, MemorySegment.NULL, 0, D3D11_SDK_VERSION,
                            deviceOut, MemorySegment.NULL, contextOut);
                    Com.check(hr, "D3D11CreateDevice");
                    return new D3D11Device(deviceOut.get(ADDRESS, 0), contextOut.get(ADDRESS, 0), name);
                } finally {
                    Com.release(adapter);
                }
            } finally {
                Com.release(factory);
            }
        }
    }

    SharedTexture openShared(long handle) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment textureOut = arena.allocate(ADDRESS);
            Com.check((int) Com.call(device, D3D11_DEVICE_OPEN_SHARED_RESOURCE, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS),
                    MemorySegment.ofAddress(handle), Com.guid(arena, IID_ID3D11_TEXTURE2D), textureOut), "ID3D11Device::OpenSharedResource");
            MemorySegment texture = textureOut.get(ADDRESS, 0);
            MemorySegment mutexOut = arena.allocate(ADDRESS);
            int hr = (int) Com.call(texture, UNKNOWN_QUERY_INTERFACE, FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS),
                    Com.guid(arena, IID_IDXGI_KEYED_MUTEX), mutexOut);
            if (hr < 0) {
                Com.release(texture);
                Com.check(hr, "IDXGIKeyedMutex の取得");
            }
            return new SharedTexture(texture, mutexOut.get(ADDRESS, 0));
        }
    }

    record SharedTexture(MemorySegment texture, MemorySegment keyedMutex) implements AutoCloseable {
        boolean acquire(long key, int milliseconds) {
            int hr = (int) Com.call(keyedMutex, KEYED_MUTEX_ACQUIRE_SYNC, FunctionDescriptor.of(JAVA_INT, JAVA_LONG, JAVA_INT), key, milliseconds);
            if (hr == WAIT_TIMEOUT) return false;
            Com.check(hr, "IDXGIKeyedMutex::AcquireSync");
            return true;
        }

        void release(long key) {
            Com.check((int) Com.call(keyedMutex, KEYED_MUTEX_RELEASE_SYNC, FunctionDescriptor.of(JAVA_INT, JAVA_LONG), key),
                    "IDXGIKeyedMutex::ReleaseSync");
        }

        @Override
        public void close() {
            Com.release(keyedMutex);
            Com.release(texture);
        }
    }

    private static String readWideString(MemorySegment segment, int maxChars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxChars; i++) {
            char c = segment.get(ValueLayout.JAVA_CHAR_UNALIGNED, i * 2L);
            if (c == 0) break;
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public void close() {
        Com.release(context);
        Com.release(device);
    }
}
