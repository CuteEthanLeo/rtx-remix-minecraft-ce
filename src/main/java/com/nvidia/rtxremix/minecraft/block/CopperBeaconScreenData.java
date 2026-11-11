/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 NVIDIA CORPORATION & AFFILIATES. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.block;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.BlockPos;

/** Additional data needed to bind the client-side handler to its block entity. */
public record CopperBeaconScreenData(BlockPos pos) {
    public static CopperBeaconScreenData read(PacketByteBuf buffer) {
        return new CopperBeaconScreenData(buffer.readBlockPos());
    }

    public void write(PacketByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }
}
