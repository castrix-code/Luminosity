package com.chunx.luminosity.core;

import net.kyori.adventure.text.format.NamedTextColor;

public enum PathType {
    VOID("Void Path", NamedTextColor.DARK_PURPLE),
    NEUTRAL("Neutral", NamedTextColor.GRAY),
    LIGHT("Light Path", NamedTextColor.GOLD);

    private final String display;
    private final NamedTextColor color;

    PathType(String display, NamedTextColor color) {
        this.display = display;
        this.color = color;
    }

    public String display() {
        return display;
    }

    public NamedTextColor color() {
        return color;
    }
}
