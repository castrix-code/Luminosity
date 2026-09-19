package com.chunx.luminosity.data;

import com.chunx.luminosity.core.Luminosity;
import com.chunx.luminosity.core.Stage;

import java.util.UUID;

public final class PlayerData {

    private final UUID uuid;
    private int luminosity;
    private boolean eclipse;
    private boolean voidLocked;
    private boolean pendingReturn;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public int luminosity() {
        return luminosity;
    }

    public void luminosity(int value) {
        this.luminosity = Luminosity.clamp(value);
    }

    /** Has consumed the Dragon Egg at +100: keeps Light buffs, gains all Void Stage 3 powers. */
    public boolean eclipse() {
        return eclipse;
    }

    public void eclipse(boolean eclipse) {
        this.eclipse = eclipse;
    }

    /** Died at -100 and is banned from the server until a Revival Ritual frees them. */
    public boolean voidLocked() {
        return voidLocked;
    }

    public void voidLocked(boolean voidLocked) {
        this.voidLocked = voidLocked;
    }

    /**
     * Revived while offline (a banned player always is), so their return to the world
     * is applied on their next login instead of at the moment of the ritual.
     */
    public boolean pendingReturn() {
        return pendingReturn;
    }

    public void pendingReturn(boolean pendingReturn) {
        this.pendingReturn = pendingReturn;
    }

    public Stage stage() {
        return Stage.of(luminosity);
    }

    /** Eclipse players count as Void Revenant for every Void trait check. */
    public boolean hasVoidTier(int tier) {
        return eclipse ? tier <= 3 : stage().voidAtLeast(tier);
    }

    public boolean hasLightTier(int tier) {
        return stage().lightAtLeast(tier);
    }

    /** True when a death here means permanent removal from the world. */
    public boolean atVoidThreshold() {
        return luminosity <= Luminosity.MIN && !eclipse;
    }
}
