/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.sky;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtmosphereModelTest {

    private static final float SUN_RADIUS = (float) Math.toRadians(0.265);

    @Test
    void zenithTransmittanceMatchesTheAnalyticOpticalDepth() {
        float[] transmittance = new float[3];
        AtmosphereModel.transmittance(1.0, transmittance);
        double h0 = AtmosphereModel.VIEW_HEIGHT;
        for (int channel = 0; channel < 3; channel++) {
            double tau = AtmosphereModel.RAYLEIGH_SCATTERING[channel]
                    * AtmosphereModel.RAYLEIGH_SCALE_HEIGHT * Math.exp(-h0 / AtmosphereModel.RAYLEIGH_SCALE_HEIGHT)
                    + AtmosphereModel.MIE_EXTINCTION
                    * AtmosphereModel.MIE_SCALE_HEIGHT * Math.exp(-h0 / AtmosphereModel.MIE_SCALE_HEIGHT)
                    // The ozone tent integrates to its half width.
                    + AtmosphereModel.OZONE_ABSORPTION[channel] * AtmosphereModel.OZONE_HALF_WIDTH;
            assertEquals(Math.exp(-tau), transmittance[channel], 0.005, "channel " + channel);
        }
    }

    @Test
    void noonSunlightIsNearlyWhiteAndALowSunIsOrange() {
        float[] noon = new float[3];
        AtmosphereModel.discTransmittance((float) Math.sin(Math.toRadians(60.0)), SUN_RADIUS, noon);
        // The old airmass formula gave about (1, 0.82, 0.65) even at the zenith.
        assertTrue(noon[1] / noon[0] > 0.88f, "green/red " + noon[1] / noon[0]);
        assertTrue(noon[2] / noon[0] > 0.75f, "blue/red " + noon[2] / noon[0]);

        float[] low = new float[3];
        AtmosphereModel.discTransmittance((float) Math.sin(Math.toRadians(5.0)), SUN_RADIUS, low);
        assertTrue(low[2] / low[0] < 0.25f, "a 5 degree sun is orange, blue/red " + low[2] / low[0]);
        assertTrue(low[0] < noon[0]);
    }

    @Test
    void theDiscSetsSmoothlyAndIsGoneBelowTheHorizon() {
        float[] previous = new float[3];
        AtmosphereModel.discTransmittance(-0.02f, SUN_RADIUS, previous);
        assertEquals(0.0f, previous[0], 0.0f);
        for (int tenth = -60; tenth <= 100; tenth++) {
            float elevation = (float) Math.toRadians(tenth * 0.1);
            float[] current = new float[3];
            AtmosphereModel.discTransmittance((float) Math.sin(elevation), SUN_RADIUS, current);
            assertTrue(current[0] >= previous[0] - 1.0e-6f, "monotonic at " + tenth * 0.1 + " degrees");
            assertTrue(current[0] - previous[0] < 0.05f, "no jump at " + tenth * 0.1 + " degrees");
            previous = current;
        }
    }

    @Test
    void theShaderGetsEveryPhysicalConstantFromJava() throws IOException {
        String shader;
        try (InputStream in = AtmosphereModelTest.class.getResourceAsStream(
                "/assets/rtx-remix-renderer/shaders/sky_atmosphere.comp")) {
            assertNotNull(in);
            shader = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> used = new TreeSet<>();
        Matcher matcher = Pattern.compile("\\bPHYS_[A-Z_]+\\b").matcher(shader);
        while (matcher.find()) {
            used.add(matcher.group());
        }
        // Names the shader derives itself rather than receiving.
        used.remove("PHYS_ATMOSPHERE_HEIGHT");
        used.remove("PHYS_VIEW_STEPS");
        String defines = AtmosphereModel.shaderDefines();
        for (String name : used) {
            assertTrue(defines.contains("#define " + name + " "), name + " is not supplied by AtmosphereModel");
        }
    }
}
