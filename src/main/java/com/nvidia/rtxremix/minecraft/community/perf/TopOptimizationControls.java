/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.community.perf;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.nvidia.rtxremix.minecraft.RemixRenderer;
import com.nvidia.rtxremix.minecraft.RtxRemixMod;
import com.nvidia.rtxremix.minecraft.community.perf.TopOptimization.Technique;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The in-game controls for {@link TopOptimization}: the {@code /rtx topopt}
 * command, its live frame-rate readout, and the A/B run that measures the
 * speedup in the player's own scene.
 *
 * <p>{@code on}, {@code off} and {@code <technique> on|off} change the same
 * persisted settings as the menu. {@code ab} toggles the master switch through
 * a session override only and restores the player's choice when it ends.
 *
 * <p>The readout follows gameplay frames only, frames with no screen or Remix
 * menu open, so typing the command in chat does not skew it. Render thread
 * only; the per-frame hook allocates nothing outside an A/B block change.
 */
public final class TopOptimizationControls {

    private static final Logger LOGGER = LoggerFactory.getLogger(TopOptimizationControls.class);
    private static final String KEY = "rtxremix.topopt.";
    private static final double READOUT_WEIGHT = 0.05;
    /** How long a requested A/B run waits for the chat screen to close before giving up. */
    private static final long START_TIMEOUT_NANOS = 3_000_000_000L;

    private static final TopOptimizationAb AB = new TopOptimizationAb();
    private static boolean abPending;
    private static long abRequestedNanos;
    private static ClientLevel abWorld;

    private static double readoutFrameNanos;
    private static double readoutPresentNanos;
    private static PresentWaitPolicy.Mode readoutMode = PresentWaitPolicy.Mode.OFF;

    private TopOptimizationControls() {}

    /** The {@code topopt} branch of the {@code /rtx} command tree. */
    public static LiteralArgumentBuilder<FabricClientCommandSource> command() {
        return ClientCommands.literal("topopt")
                .executes(context -> status(context.getSource()))
                .then(ClientCommands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(ClientCommands.literal("on")
                        .executes(context -> setMaster(context.getSource(), true)))
                .then(ClientCommands.literal("off")
                        .executes(context -> setMaster(context.getSource(), false)))
                .then(ClientCommands.literal("ab")
                        .executes(context -> requestAb(context.getSource())))
                .then(ClientCommands.argument("technique", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (Technique technique : Technique.values()) {
                                builder.suggest(technique.id());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> describeTechnique(context.getSource(),
                                StringArgumentType.getString(context, "technique")))
                        .then(ClientCommands.literal("on")
                                .executes(context -> setTechnique(context.getSource(),
                                        StringArgumentType.getString(context, "technique"), true)))
                        .then(ClientCommands.literal("off")
                                .executes(context -> setTechnique(context.getSource(),
                                        StringArgumentType.getString(context, "technique"), false))));
    }

    /**
     * Called at the end of every Minecraft frame with that frame's wall-clock
     * period (0 unless it and the previous frame were world frames), the time
     * Present took, and the present mode the frame used.
     */
    public static void onFrameEnd(long nowNanos, long periodNanos, long presentNanos,
                                  PresentWaitPolicy.Mode mode) {
        Minecraft client = Minecraft.getInstance();
        boolean gameplay = client.screen == null && !RtxRemixMod.isRemixMenuOpen();
        if (periodNanos > 0 && gameplay) {
            readoutFrameNanos = readoutFrameNanos == 0.0
                    ? periodNanos : readoutFrameNanos + READOUT_WEIGHT * (periodNanos - readoutFrameNanos);
            readoutPresentNanos = readoutPresentNanos == 0.0
                    ? presentNanos : readoutPresentNanos + READOUT_WEIGHT * (presentNanos - readoutPresentNanos);
            readoutMode = mode;
        }

        if (abPending) {
            if (gameplay && client.level != null) {
                abPending = false;
                abWorld = client.level;
                AB.start(nowNanos);
                applyArm(client);
            } else if (nowNanos - abRequestedNanos > START_TIMEOUT_NANOS) {
                abPending = false;
                send(client, Component.translatable(KEY + "ab.aborted",
                        Component.translatable(KEY + "ab.abort.chat")).withStyle(ChatFormatting.YELLOW));
            }
            return;
        }
        if (!AB.isRunning()) {
            return;
        }
        String abort = abortReason(client);
        if (abort != null) {
            AB.abort(abort);
            finishAb(client);
            return;
        }
        if (AB.onFrame(nowNanos, periodNanos)) {
            applyArm(client);
        } else if (AB.status() == TopOptimizationAb.Status.FINISHED) {
            finishAb(client);
        }
    }

    private static int status(FabricClientCommandSource source) {
        TopOptimization topOpt = TopOptimization.get();
        source.sendFeedback(masterLine(topOpt));
        for (Technique technique : Technique.values()) {
            source.sendFeedback(techniqueLine(topOpt, technique));
        }
        if (readoutFrameNanos > 0.0) {
            source.sendFeedback(Component.translatable(KEY + "readout",
                    format(1.0e9 / readoutFrameNanos, 1),
                    format(readoutFrameNanos / 1.0e6, 2),
                    format(readoutPresentNanos / 1.0e6, 2),
                    Component.translatable(KEY + "mode." + readoutMode.name().toLowerCase(Locale.ROOT))));
        } else {
            source.sendFeedback(Component.translatable(KEY + "readout.none"));
        }
        return 1;
    }

    private static int setMaster(FabricClientCommandSource source, boolean on) {
        TopOptimization topOpt = TopOptimization.get();
        topOpt.setEnabled(on);
        RtxRemixMod.saveRtxSettings();
        source.sendFeedback(masterLine(topOpt));
        return 1;
    }

    private static int setTechnique(FabricClientCommandSource source, String id, boolean on) {
        Optional<Technique> technique = Technique.byId(id);
        if (technique.isEmpty()) {
            source.sendError(unknownTechnique(id));
            return 0;
        }
        TopOptimization topOpt = TopOptimization.get();
        topOpt.setWanted(technique.get(), on);
        RtxRemixMod.saveRtxSettings();
        source.sendFeedback(techniqueLine(topOpt, technique.get()));
        if (on && !topOpt.isMasterActive()) {
            source.sendFeedback(Component.translatable(KEY + "masterOff").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int describeTechnique(FabricClientCommandSource source, String id) {
        Optional<Technique> technique = Technique.byId(id);
        if (technique.isEmpty()) {
            source.sendError(unknownTechnique(id));
            return 0;
        }
        source.sendFeedback(techniqueLine(TopOptimization.get(), technique.get()));
        return 1;
    }

    private static int requestAb(FabricClientCommandSource source) {
        TopOptimization topOpt = TopOptimization.get();
        if (abPending || AB.isRunning()) {
            source.sendError(Component.translatable(KEY + "ab.running"));
            return 0;
        }
        if (topOpt.isMasterForced()) {
            source.sendError(Component.translatable(KEY + "ab.forced", topOpt.masterBlockedReason()));
            return 0;
        }
        Minecraft client = Minecraft.getInstance();
        RemixRenderer renderer = RtxRemixMod.renderer;
        if (renderer == null || !renderer.isInitialized() || client.level == null
                || !RtxRemixMod.isRtxEnabled()) {
            source.sendError(Component.translatable(KEY + "ab.unavailable"));
            return 0;
        }
        abPending = true;
        abRequestedNanos = System.nanoTime();
        long seconds = Math.round(TopOptimizationAb.BLOCKS * TopOptimizationAb.BLOCK_NANOS / 1.0e9);
        source.sendFeedback(Component.translatable(KEY + "ab.start", TopOptimizationAb.BLOCKS, seconds));
        return 1;
    }

    /** The translation-key suffix of why a running A/B must stop, or null to keep going. */
    private static String abortReason(Minecraft client) {
        if (client.level != abWorld) {
            return "world";
        }
        if (client.screen != null) {
            return "screen";
        }
        if (RtxRemixMod.isRemixMenuOpen()) {
            return "menu";
        }
        if (!client.isWindowActive()) {
            return "focus";
        }
        if (!RtxRemixMod.isRtxEnabled()) {
            return "rtx";
        }
        return null;
    }

    private static void applyArm(Minecraft client) {
        boolean on = AB.currentArmOn();
        TopOptimization.get().setSessionOverride(on);
        if (client.player != null) {
            client.player.sendOverlayMessage(Component.translatable(KEY + "ab.block",
                    AB.block() + 1, TopOptimizationAb.BLOCKS, onOff(on)));
        }
    }

    private static void finishAb(Minecraft client) {
        TopOptimization.get().setSessionOverride(null);
        abWorld = null;
        if (AB.status() == TopOptimizationAb.Status.FINISHED) {
            TopOptimizationAb.Result result = AB.result();
            send(client, Component.translatable(KEY + "ab.result",
                    format(result.offFps(), 1), format(result.offOnePercentLowFps(), 1),
                    format(result.onFps(), 1), format(result.onOnePercentLowFps(), 1)));
            if (result.complete()) {
                send(client, Component.translatable(KEY + "ab.speedup",
                        format(result.speedup(), 2), format(result.minPairRatio(), 2),
                        format(result.maxPairRatio(), 2)).withStyle(ChatFormatting.GREEN));
            } else {
                send(client, Component.translatable(KEY + "ab.incomplete").withStyle(ChatFormatting.YELLOW));
            }
            LOGGER.info("Top Optimization A/B: off {} fps (1% low {}, {} frames), on {} fps (1% low {}, {} frames),"
                            + " speedup x{} (pairs x{} to x{}); techniques: {}",
                    format(result.offFps(), 1), format(result.offOnePercentLowFps(), 1), result.offFrameCount(),
                    format(result.onFps(), 1), format(result.onOnePercentLowFps(), 1), result.onFrameCount(),
                    format(result.speedup(), 3), format(result.minPairRatio(), 3),
                    format(result.maxPairRatio(), 3), TopOptimization.get().describe());
        } else if (AB.status() == TopOptimizationAb.Status.ABORTED) {
            send(client, Component.translatable(KEY + "ab.aborted",
                    Component.translatable(KEY + "ab.abort." + AB.abortReason())).withStyle(ChatFormatting.YELLOW));
            LOGGER.info("Top Optimization A/B aborted: {}", AB.abortReason());
        }
    }

    private static MutableComponent masterLine(TopOptimization topOpt) {
        MutableComponent line = Component.translatable(KEY + "state", onOff(topOpt.isMasterActive()));
        String forced = topOpt.masterBlockedReason();
        if (forced != null) {
            line.append(" ").append(Component.translatable(KEY + "forced", forced).withStyle(ChatFormatting.YELLOW));
        }
        return line;
    }

    private static MutableComponent techniqueLine(TopOptimization topOpt, Technique technique) {
        MutableComponent line = Component.literal("  ")
                .append(Component.translatable("rtxremix.setting." + technique.settingId()))
                .append(": ")
                .append(onOff(topOpt.active(technique)));
        String blocked = topOpt.blockedReason(technique);
        if (blocked != null) {
            line.append(" ").append(Component.translatable("rtxremix.settings.tooltip.blocked", blocked)
                    .withStyle(ChatFormatting.RED));
        } else if (!topOpt.isWanted(technique)) {
            line.append(" ").append(Component.translatable(KEY + "notWanted").withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    private static Component unknownTechnique(String id) {
        StringBuilder ids = new StringBuilder();
        for (Technique technique : Technique.values()) {
            if (ids.length() > 0) {
                ids.append(", ");
            }
            ids.append(technique.id());
        }
        return Component.translatable(KEY + "unknown", id, ids.toString());
    }

    private static Component onOff(boolean on) {
        return on ? CommonComponents.OPTION_ON.copy().withStyle(ChatFormatting.GREEN)
                : CommonComponents.OPTION_OFF.copy().withStyle(ChatFormatting.GRAY);
    }

    private static void send(Minecraft client, Component text) {
        if (client.player != null) {
            client.player.sendSystemMessage(text);
        }
    }

    private static String format(double value, int decimals) {
        if (Double.isNaN(value)) {
            return "-";
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }
}
