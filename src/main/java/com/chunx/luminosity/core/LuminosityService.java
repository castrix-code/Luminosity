package com.chunx.luminosity.core;

import com.chunx.luminosity.data.DataStore;
import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.traits.TraitApplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * The single entry point for reading and changing luminosity. Everything that moves
 * the number funnels through here so traits are always re-applied exactly once.
 */
public final class LuminosityService {

    private final DataStore store;
    private final TraitApplier traits;

    public LuminosityService(DataStore store, TraitApplier traits) {
        this.store = store;
        this.traits = traits;
    }

    public DataStore store() {
        return store;
    }

    public TraitApplier traits() {
        return traits;
    }

    public PlayerData data(Player player) {
        return store.get(player.getUniqueId());
    }

    public int get(Player player) {
        return data(player).luminosity();
    }

    /** Sets an absolute value, refreshes traits, and tells the player what changed. */
    public void set(Player player, int value, String reason) {
        PlayerData data = data(player);
        Stage before = data.stage();
        int old = data.luminosity();
        data.luminosity(value);
        if (data.luminosity() == old) {
            return;
        }
        traits.apply(player);
        announce(player, old, data.luminosity(), before, data.stage(), reason);
    }

    public void add(Player player, int delta, String reason) {
        set(player, data(player).luminosity() + delta, reason);
    }

    /** Applies the Kill Transfer Rule: {@link Luminosity#STEP} moves from victim to killer. */
    public void transferOnKill(Player killer, Player victim) {
        add(victim, -Luminosity.STEP, "slain by " + killer.getName());
        add(killer, Luminosity.STEP, "slew " + victim.getName());
    }

    /**
     * Frees a player the Void claimed: lifts the ban and resets them to
     * {@link Luminosity#REVIVAL_LUMINOSITY}. They are banned and so almost always offline,
     * which is why the actual return is deferred to {@link #returnToWorld} on next login.
     *
     * @return false if the player was not void-banned in the first place
     */
    public boolean revive(UUID uuid) {
        PlayerData data = store.get(uuid);
        if (!data.voidLocked()) {
            return false;
        }
        data.voidLocked(false);
        data.eclipse(false);
        data.luminosity(Luminosity.REVIVAL_LUMINOSITY);
        data.pendingReturn(true);
        VoidBans.pardon(uuid);

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            returnToWorld(online);
        }
        return true;
    }

    /** Completes a revival once the player is actually in the world. */
    public void returnToWorld(Player player) {
        data(player).pendingReturn(false);
        // Players locked by the old spectator-limbo version come back in spectator.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        traits.apply(player);
        player.sendMessage(Component.text("You are pulled back from the Void at "
                + Luminosity.REVIVAL_LUMINOSITY + " Luminosity.", NamedTextColor.LIGHT_PURPLE));
    }

    /** Offline-safe absolute set; traits apply when the player next joins. */
    public void setOffline(UUID uuid, int value) {
        store.get(uuid).luminosity(value);
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            traits.apply(online);
        }
    }

    private void announce(Player player, int old, int now, Stage before, Stage after, String reason) {
        int delta = now - old;
        NamedTextColor color = delta > 0 ? NamedTextColor.GOLD : NamedTextColor.DARK_PURPLE;
        player.sendMessage(Component.text("Luminosity ", NamedTextColor.GRAY)
                .append(Component.text((delta > 0 ? "+" : "") + delta, color))
                .append(Component.text(" → " + now + " (" + reason + ")", NamedTextColor.GRAY)));

        if (before != after) {
            player.sendMessage(Component.text("You are now " + after.title(), after.path().color()));
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(after.title(), after.path().color()),
                    Component.text("Luminosity " + now, NamedTextColor.GRAY)));
        }
    }
}
