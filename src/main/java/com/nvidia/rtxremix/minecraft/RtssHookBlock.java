/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Keeps RivaTuner Statistics Server's hook DLL out of this Minecraft process while RTSS runs.
 *
 * <p>RTSS reaches a game two ways. Its implicit Vulkan layer is kept out of the Remix runtime
 * by {@link RtssVulkanLayerPolicy}. Independently, {@code RTSSHooksLoader64} installs a global
 * window hook that maps {@code RTSSHooks64.dll} into every GUI process, and the Global profile's
 * {@code [Hooking] HookVulkan=1} then hooks Vulkan presentation directly, a configurable delay
 * after start (15 s by default). With DLSS frame generation on, presents through that hook
 * fault the GPU within seconds ({@code VK_ERROR_DEVICE_LOST}) even with the layer disabled.
 *
 * <p>Windows' extension-point mitigation ({@code ProcessExtensionPointDisablePolicy}) makes
 * the loader refuse global-hook, AppInit and legacy-IME DLLs for the process that sets it, and
 * only for that process. Set at pre-launch, before Minecraft creates its window, it keeps
 * RTSS out of this game alone: other games and other Java programs keep the RTSS overlay, and
 * no RTSS profile is written. It applies only when RTSS is running at launch, found through
 * the shared memory RTSS publishes, so a player without RTSS keeps every extension point.
 * The policy cannot be lifted once set; a DLL already mapped stays, which is logged.
 *
 * <p>{@code -D}{@value #ALLOW_PROPERTY}{@code =true} leaves the process open to RTSS.
 */
public final class RtssHookBlock implements PreLaunchEntrypoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(RtssHookBlock.class);

    public static final String ALLOW_PROPERTY = "rtxremix.allowRtssHooks";
    /** The DLL RTSS's global hook maps into GUI processes on 64-bit Windows. */
    static final String HOOK_DLL = "RTSSHooks64.dll";
    /** The file mapping RTSS publishes while it runs (RTSS SDK, RTSSSharedMemory sample). */
    static final String SHARED_MEMORY = "RTSSSharedMemoryV2";

    /** What pre-launch does about RTSS. */
    enum Decision {
        /** The player allowed RTSS hooks. */
        ALLOWED,
        /** RTSS is not running, so every extension point stays available. */
        NOT_RUNNING,
        /** Set the extension-point mitigation for this process. */
        BLOCK
    }

    static Decision decide(String allowProperty, boolean rtssRunning) {
        if (Boolean.parseBoolean(allowProperty)) {
            return Decision.ALLOWED;
        }
        return rtssRunning ? Decision.BLOCK : Decision.NOT_RUNNING;
    }

    @Override
    public void onPreLaunch() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
            return;
        }
        try {
            Decision decision = decide(System.getProperty(ALLOW_PROPERTY), Win32.sharedMemoryExists(SHARED_MEMORY));
            switch (decision) {
                case ALLOWED -> LOGGER.info("RTSS hooks allowed into this process (-D{}=true)", ALLOW_PROPERTY);
                case NOT_RUNNING -> LOGGER.info("RTSS is not running; extension points left open");
                case BLOCK -> block();
            }
        } catch (Throwable t) {
            // Never stop the game over this: without the block, RTSS only risks frame generation.
            LOGGER.warn("Could not keep RTSS hooks out of this process; with RTSS running, DLSS frame"
                    + " generation may lose the GPU device", t);
        }
    }

    private static void block() throws Throwable {
        boolean alreadyMapped = Win32.moduleLoaded(HOOK_DLL);
        int error = Win32.disableExtensionPoints();
        if (error != 0) {
            LOGGER.warn("RTSS is running but SetProcessMitigationPolicy(ExtensionPointDisable) failed"
                    + " (error {}); DLSS frame generation may lose the GPU device while RTSS runs", error);
            return;
        }
        if (alreadyMapped) {
            LOGGER.warn("RTSS hooks were already in this process before pre-launch; the block cannot"
                    + " remove them, so DLSS frame generation may still lose the GPU device");
            return;
        }
        LOGGER.info("RTSS is running: its hook DLL is kept out of this game process only, so DLSS frame"
                + " generation works and other games keep the RTSS overlay (-D{}=true lets it in)",
                ALLOW_PROPERTY);
    }

    /** Whether RTSS's hook DLL is mapped into this process now; false off Windows or on error. */
    public static boolean hookDllLoaded() {
        try {
            return Win32.moduleLoaded(HOOK_DLL);
        } catch (Throwable t) {
            return false;
        }
    }

    /** kernel32 through the foreign-function API; handles bind on first use. */
    static final class Win32 {
        private static final int PROCESS_EXTENSION_POINT_DISABLE_POLICY = 6;
        private static final int FILE_MAP_READ = 0x0004;
        private static final int DISABLE_EXTENSION_POINTS = 0x1;

        private static final Linker LINKER = Linker.nativeLinker();
        private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
        private static final StructLayout CALL_STATE = Linker.Option.captureStateLayout();
        private static final VarHandle LAST_ERROR =
                CALL_STATE.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

        private static final MethodHandle GET_MODULE_HANDLE = bind("GetModuleHandleW",
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        private static final MethodHandle OPEN_FILE_MAPPING = bind("OpenFileMappingW",
                FunctionDescriptor.of(ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        private static final MethodHandle CLOSE_HANDLE = bind("CloseHandle",
                FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        private static final MethodHandle SET_MITIGATION_POLICY = LINKER.downcallHandle(
                KERNEL32.findOrThrow("SetProcessMitigationPolicy"),
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG),
                Linker.Option.captureCallState("GetLastError"));
        private static final MethodHandle GET_MITIGATION_POLICY = bind("GetProcessMitigationPolicy",
                FunctionDescriptor.of(ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        private static final MethodHandle GET_CURRENT_PROCESS = bind("GetCurrentProcess",
                FunctionDescriptor.of(ValueLayout.ADDRESS));

        private Win32() {}

        private static MethodHandle bind(String name, FunctionDescriptor descriptor) {
            return LINKER.downcallHandle(KERNEL32.findOrThrow(name), descriptor);
        }

        private static MemorySegment wide(Arena arena, String text) {
            return arena.allocateFrom(text, StandardCharsets.UTF_16LE);
        }

        static boolean moduleLoaded(String name) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment module = (MemorySegment) GET_MODULE_HANDLE.invoke(wide(arena, name));
                return module.address() != 0;
            }
        }

        static boolean sharedMemoryExists(String name) throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment mapping = (MemorySegment) OPEN_FILE_MAPPING.invoke(
                        FILE_MAP_READ, 0, wide(arena, name));
                if (mapping.address() == 0) {
                    return false;
                }
                int ignored = (int) CLOSE_HANDLE.invoke(mapping);
                return true;
            }
        }

        /** Whether this process already refuses extension-point DLLs. */
        static boolean extensionPointsDisabled() throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment policy = arena.allocate(ValueLayout.JAVA_INT);
                int ok = (int) GET_MITIGATION_POLICY.invoke(
                        (MemorySegment) GET_CURRENT_PROCESS.invoke(),
                        PROCESS_EXTENSION_POINT_DISABLE_POLICY, policy, 4L);
                return ok != 0 && (policy.get(ValueLayout.JAVA_INT, 0) & DISABLE_EXTENSION_POINTS) != 0;
            }
        }

        /**
         * Sets {@code ProcessExtensionPointDisablePolicy} for this process and confirms it.
         *
         * @return 0 on success, otherwise the Win32 error (or -1 when the call reported success
         *         but the policy does not read back as set)
         */
        static int disableExtensionPoints() throws Throwable {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment state = arena.allocate(CALL_STATE);
                MemorySegment policy = arena.allocate(ValueLayout.JAVA_INT);
                policy.set(ValueLayout.JAVA_INT, 0, DISABLE_EXTENSION_POINTS);
                int ok = (int) SET_MITIGATION_POLICY.invoke(
                        state, PROCESS_EXTENSION_POINT_DISABLE_POLICY, policy, 4L);
                if (ok == 0) {
                    int error = (int) LAST_ERROR.get(state, 0L);
                    return error != 0 ? error : -1;
                }
            }
            return extensionPointsDisabled() ? 0 : -1;
        }
    }
}
