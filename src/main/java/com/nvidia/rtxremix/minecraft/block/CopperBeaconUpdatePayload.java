/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.block;

import com.nvidia.rtxremix.minecraft.RtxRemixCommonMod;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/** One validated control edit from an open Copper Beacon screen. */
public record CopperBeaconUpdatePayload(int syncId, Field field, float value) {

    public static final Identifier ID =
            new Identifier(RtxRemixCommonMod.MOD_ID, "copper_beacon_update");

    public static CopperBeaconUpdatePayload read(PacketByteBuf buffer) {
        return new CopperBeaconUpdatePayload(
                buffer.readVarInt(),
                Field.byId(buffer.readVarInt()),
                buffer.readFloat());
    }

    /** This edit as the buffer {@link #read} decodes. */
    public PacketByteBuf toBuffer() {
        PacketByteBuf buffer = PacketByteBufs.create();
        buffer.writeVarInt(syncId);
        buffer.writeVarInt(field.ordinal());
        buffer.writeFloat(value);
        return buffer;
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, networkHandler, buffer, responseSender) -> {
            // Decoded here, on the network thread: the buffer is released once this returns.
            CopperBeaconUpdatePayload payload = read(buffer);
            server.execute(() -> {
                if (player.currentScreenHandler
                                instanceof CopperBeaconScreenHandler handler
                        && handler.syncId == payload.syncId
                        && handler.canUse(player)) {
                    handler.applyUpdate(payload.field, payload.value);
                }
            });
        });
    }

    public enum Field {
        INTENSITY,
        CONE_ANGLE,
        REDSTONE_CONTROLLED,
        YAW_POSITION,
        YAW_MODE,
        YAW_ROTATION_SPEED,
        YAW_MINIMUM_OFFSET,
        YAW_MAXIMUM_OFFSET,
        YAW_CYCLE_SECONDS,
        YAW_PATROL_SECONDS,
        PITCH_POSITION,
        PITCH_MODE,
        PITCH_ROTATION_SPEED,
        PITCH_MINIMUM_OFFSET,
        PITCH_MAXIMUM_OFFSET,
        PITCH_CYCLE_SECONDS,
        PITCH_PATROL_SECONDS,
        SATURATION;

        private static Field byId(int id) {
            Field[] values = values();
            return id >= 0 && id < values.length ? values[id] : INTENSITY;
        }
    }
}
