package com.chunx.luminosity.core;

/**
 * The seven discrete power stages. Luminosity only ever moves in steps of 20,
 * so every reachable value maps cleanly onto one of these bands.
 */
public enum Stage {
    VOID_3(PathType.VOID, 3, "Void Revenant"),
    VOID_2(PathType.VOID, 2, "Void Phantom"),
    VOID_1(PathType.VOID, 1, "Shadowbound"),
    NEUTRAL(PathType.NEUTRAL, 0, "Neutral"),
    LIGHT_1(PathType.LIGHT, 1, "Sunbound"),
    LIGHT_2(PathType.LIGHT, 2, "Radiant Guardian"),
    LIGHT_3(PathType.LIGHT, 3, "Solar Deity");

    private final PathType path;
    private final int tier;
    private final String title;

    Stage(PathType path, int tier, String title) {
        this.path = path;
        this.tier = tier;
        this.title = title;
    }

    public static Stage of(int luminosity) {
        if (luminosity <= -100) return VOID_3;
        if (luminosity <= -60) return VOID_2;
        if (luminosity <= -20) return VOID_1;
        if (luminosity >= 100) return LIGHT_3;
        if (luminosity >= 60) return LIGHT_2;
        if (luminosity >= 20) return LIGHT_1;
        return NEUTRAL;
    }

    public PathType path() {
        return path;
    }

    /** 0 for neutral, otherwise 1-3 within the path. */
    public int tier() {
        return tier;
    }

    public String title() {
        return title;
    }

    public boolean isVoid() {
        return path == PathType.VOID;
    }

    public boolean isLight() {
        return path == PathType.LIGHT;
    }

    /** True if this stage is at or beyond {@code t} on the Void path. */
    public boolean voidAtLeast(int t) {
        return isVoid() && tier >= t;
    }

    /** True if this stage is at or beyond {@code t} on the Light path. */
    public boolean lightAtLeast(int t) {
        return isLight() && tier >= t;
    }
}
