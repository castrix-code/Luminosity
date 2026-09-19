package com.chunx.luminosity.listeners;

import com.chunx.luminosity.abilities.Cooldowns;
import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.core.VoidBans;
import com.chunx.luminosity.data.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/** Join, quit, death, respawn: the Kill Transfer Rule and the Void Ban Threshold. */
public final class LifecycleListener implements Listener {

    private final Plugin plugin;
    private final LuminosityService service;
    private final Cooldowns cooldowns;

    public LifecycleListener(Plugin plugin, LuminosityService service, Cooldowns cooldowns) {
        this.plugin = plugin;
        this.service = service;
        this.cooldowns = cooldowns;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        PlayerData data = service.data(victim);
        // The Void Ban Threshold is judged on the luminosity the victim died holding,
        // so read it before the kill transfer moves anything.
        boolean doomed = data.atVoidThreshold();

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            service.transferOnKill(killer, victim);
        }

        // Lock last. The transfer above re-applies traits, so locking first would let
        // the victim's bonus hearts be handed straight back to them.
        if (doomed) {
            voidBan(victim);
        }
    }

    /** Bans a Void Revenant who died at -100 until a Revival Ritual frees them. */
    private void voidBan(Player player) {
        PlayerData data = service.data(player);
        data.voidLocked(true);
        service.traits().clear(player);
        cooldowns.clear(player);

        Bukkit.broadcast(Component.text(player.getName() + " has been claimed by the Void.",
                NamedTextColor.DARK_PURPLE));
        Bukkit.broadcast(Component.text("Only a Charged Core can bring them back.",
                NamedTextColor.DARK_GRAY));
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 0.6f);

        // Disconnecting a player from inside their own death event can interrupt the
        // death being processed (drops, stats), so the ban lands on the next tick.
        Bukkit.getScheduler().runTask(plugin, () -> VoidBans.ban(player.getUniqueId()));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> service.traits().apply(player));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = service.data(player);

        // A void-banned player can only get this far if their ban was lifted outside the
        // plugin, e.g. a vanilla /pardon. Treat that as the admin reviving them.
        if (data.voidLocked()) {
            service.revive(player.getUniqueId());
            return;
        }
        if (data.pendingReturn()) {
            service.returnToWorld(player);
            return;
        }
        service.traits().apply(player);
        player.sendMessage(Component.text("Luminosity " + data.luminosity()
                + " — " + data.stage().title(), data.stage().path().color()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.clear(event.getPlayer());
    }
}
