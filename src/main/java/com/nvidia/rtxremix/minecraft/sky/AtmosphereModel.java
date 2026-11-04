/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 RTX Remix for Minecraft Community Edition contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nvidia.rtxremix.minecraft.sky;

import java.util.Locale;

/**
 * The one physical atmosphere that both the sky dome and the sun and moon
 * lights are computed from.
 *
 * <p>The parameters are the Earth-like set of Hillaire 2020 and Bruneton
 * 2017: Rayleigh and Mie scattering with exponential density, Mie absorption,
 * and an ozone layer that absorbs in the Chappuis band. Ozone is what turns a
 * cyan-green Rayleigh sky into a deep blue one, and its absence was why the
 * old sky's horizon read green.
 *
 * <p>{@code sky_atmosphere.comp} receives these values as {@code #define}s
 * through {@link #shaderDefines()}, so the GPU sky and the CPU light
 * transmittance cannot drift apart again, as the old airmass formula and the
 * old shader did: the sun lost about three times more light on its way down
 * than the sky it lit implied.
 *
 * <p>Units: metres for lengths and inverse metres for coefficients. A Remix
 * distant light of radiance {@code R} gives an irradiance of {@code pi * R}
 * (its shader divides the radiance by the squared sine of the half angle and
 * samples the cone uniformly), so the sky is lit by {@code pi * R} as well.
 */
public final class AtmosphereModel {

    public static final double PLANET_RADIUS = 6360.0e3;
    public static final double TOP_RADIUS = 6460.0e3;
    /** Height of the viewer above the planet surface, enough to keep the horizon row off the ground. */
    public static final double VIEW_HEIGHT = 100.0;

    static final double[] RAYLEIGH_SCATTERING = {5.802e-6, 13.558e-6, 33.1e-6};
    static final double RAYLEIGH_SCALE_HEIGHT = 8000.0;
    static final double MIE_SCATTERING = 3.996e-6;
    static final double MIE_EXTINCTION = 4.440e-6;
    static final double MIE_SCALE_HEIGHT = 1200.0;
    static final double MIE_ANISOTROPY = 0.8;
    static final double[] OZONE_ABSORPTION = {0.650e-6, 1.881e-6, 0.085e-6};
    static final double OZONE_CENTER_HEIGHT = 25.0e3;
    static final double OZONE_HALF_WIDTH = 15.0e3;
    static final double GROUND_ALBEDO = 0.3;
    /**
     * Night-sky floor in dome radiance units: airglow and integrated starlight,
     * so a moonless sky is a very dark blue rather than black. About a tenth of
     * the full-moon zenith.
     */
    static final double[] AIRGLOW = {0.00022, 0.00028, 0.00040};

    /** Irradiance of a distant light per unit of its Remix radiance. */
    public static final float DISTANT_LIGHT_IRRADIANCE_PER_RADIANCE = (float) Math.PI;

    static final int TRANSMITTANCE_STEPS = 48;

    private AtmosphereModel() {}

    /**
     * Transmittance of the atmosphere from the viewer toward a direction, per RGB channel.
     *
     * @param cosZenith cosine of the angle between the direction and straight up
     * @param out three floats, zero when the ray hits the planet
     */
    public static void transmittance(double cosZenith, float[] out) {
        double r = PLANET_RADIUS + VIEW_HEIGHT;
        double mu = Math.max(-1.0, Math.min(1.0, cosZenith));
        if (hitsGround(r, mu)) {
            out[0] = 0.0f;
            out[1] = 0.0f;
            out[2] = 0.0f;
            return;
        }
        double distance = distanceToTop(r, mu);
        double tauR = 0.0;
        double tauG = 0.0;
        double tauB = 0.0;
        double previous = 0.0;
        for (int step = 1; step <= TRANSMITTANCE_STEPS; step++) {
            double amount = (double) step / TRANSMITTANCE_STEPS;
            double next = distance * amount * amount;
            double length = next - previous;
            double t = previous + 0.5 * length;
            double height = Math.sqrt(r * r + t * t + 2.0 * r * t * mu) - PLANET_RADIUS;
            height = Math.max(0.0, height);
            double rayleigh = Math.exp(-height / RAYLEIGH_SCALE_HEIGHT);
            double mie = Math.exp(-height / MIE_SCALE_HEIGHT);
            double ozone = ozoneDensity(height);
            tauR += (RAYLEIGH_SCATTERING[0] * rayleigh + MIE_EXTINCTION * mie + OZONE_ABSORPTION[0] * ozone) * length;
            tauG += (RAYLEIGH_SCATTERING[1] * rayleigh + MIE_EXTINCTION * mie + OZONE_ABSORPTION[1] * ozone) * length;
            tauB += (RAYLEIGH_SCATTERING[2] * rayleigh + MIE_EXTINCTION * mie + OZONE_ABSORPTION[2] * ozone) * length;
            previous = next;
        }
        out[0] = (float) Math.exp(-tauR);
        out[1] = (float) Math.exp(-tauG);
        out[2] = (float) Math.exp(-tauB);
    }

    /**
     * Transmittance toward a sun or moon disc, faded by the part of the disc
     * still above the horizon so it sets smoothly instead of switching off.
     *
     * @param height y component of the unit direction toward the body
     * @param angularRadius the disc's angular radius in radians
     * @param out three floats
     */
    public static void discTransmittance(float height, float angularRadius, float[] out) {
        double elevation = Math.asin(Math.max(-1.0f, Math.min(1.0f, height)));
        double dip = horizonDip();
        double aboveHorizon = elevation + dip;
        double visible = smoothstep(-angularRadius, angularRadius, aboveHorizon);
        if (visible <= 0.0) {
            out[0] = 0.0f;
            out[1] = 0.0f;
            out[2] = 0.0f;
            return;
        }
        // Sample the transmittance at the visible part of the disc: just above the apparent horizon.
        double sampleElevation = Math.max(elevation, -dip + 0.25 * angularRadius);
        transmittance(Math.sin(sampleElevation), out);
        out[0] *= (float) visible;
        out[1] *= (float) visible;
        out[2] *= (float) visible;
    }

    /** Angle by which the geometric horizon sits below level for the viewer, in radians. */
    static double horizonDip() {
        return Math.acos(PLANET_RADIUS / (PLANET_RADIUS + VIEW_HEIGHT));
    }

    static double ozoneDensity(double height) {
        return Math.max(0.0, 1.0 - Math.abs(height - OZONE_CENTER_HEIGHT) / OZONE_HALF_WIDTH);
    }

    static boolean hitsGround(double r, double mu) {
        return mu < 0.0 && r * r * (1.0 - mu * mu) < PLANET_RADIUS * PLANET_RADIUS;
    }

    static double distanceToTop(double r, double mu) {
        double discriminant = r * r * (mu * mu - 1.0) + TOP_RADIUS * TOP_RADIUS;
        return Math.max(0.0, -r * mu + Math.sqrt(Math.max(0.0, discriminant)));
    }

    /** {@code #define} lines that give the shader this exact atmosphere. */
    static String shaderDefines() {
        StringBuilder defines = new StringBuilder();
        define(defines, "PHYS_PLANET_RADIUS", PLANET_RADIUS);
        define(defines, "PHYS_TOP_RADIUS", TOP_RADIUS);
        define(defines, "PHYS_VIEW_HEIGHT", VIEW_HEIGHT);
        define(defines, "PHYS_RAYLEIGH_SCATTERING", RAYLEIGH_SCATTERING);
        define(defines, "PHYS_RAYLEIGH_SCALE_HEIGHT", RAYLEIGH_SCALE_HEIGHT);
        define(defines, "PHYS_MIE_SCATTERING", MIE_SCATTERING);
        define(defines, "PHYS_MIE_EXTINCTION", MIE_EXTINCTION);
        define(defines, "PHYS_MIE_SCALE_HEIGHT", MIE_SCALE_HEIGHT);
        define(defines, "PHYS_MIE_ANISOTROPY", MIE_ANISOTROPY);
        define(defines, "PHYS_OZONE_ABSORPTION", OZONE_ABSORPTION);
        define(defines, "PHYS_OZONE_CENTER_HEIGHT", OZONE_CENTER_HEIGHT);
        define(defines, "PHYS_OZONE_HALF_WIDTH", OZONE_HALF_WIDTH);
        define(defines, "PHYS_GROUND_ALBEDO", GROUND_ALBEDO);
        define(defines, "PHYS_AIRGLOW", AIRGLOW);
        defines.append("#define PHYS_TRANSMITTANCE_STEPS ").append(TRANSMITTANCE_STEPS).append('\n');
        return defines.toString();
    }

    private static void define(StringBuilder out, String name, double value) {
        out.append("#define ").append(name).append(' ').append(glslFloat(value)).append('\n');
    }

    private static void define(StringBuilder out, String name, double[] value) {
        out.append("#define ").append(name).append(" vec3(")
                .append(glslFloat(value[0])).append(", ")
                .append(glslFloat(value[1])).append(", ")
                .append(glslFloat(value[2])).append(")\n");
    }

    private static String glslFloat(double value) {
        return String.format(Locale.ROOT, "%.9e", value);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }
}
