package com.chunx.luminosity.core;

/** Shared constants and clamping for the luminosity scale. */
public final class Luminosity {

    public static final int MIN = -100;
    public static final int MAX = 100;
    /** Amount moved by a single kill, and the size of one "pour" into a Luminous Core. */
    public static final int STEP = 20;
    /** Charge a Luminous Core must reach before it can perform the Revival Ritual. */
    public static final int CORE_CHARGED_AT = 100;
    /** Luminosity a revived player is returned to the world with. */
    public static final int REVIVAL_LUMINOSITY = -40;

    private Luminosity() {
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
