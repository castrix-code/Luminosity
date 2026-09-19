package com.chunx.luminosity.core;

import io.papermc.paper.ban.BanListType;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.ban.ProfileBanList;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;

/**
 * Real server bans for the Void Ban Threshold, written to the vanilla ban list so they
 * show up in {@code /banlist} and survive a plugin removal.
 *
 * <p>Every ban carries {@link #SOURCE}, and only bans with that source are ever lifted.
 * A player an admin banned for something else stays banned even if someone spends a
 * Charged Core on them.
 */
public final class VoidBans {

    public static final String SOURCE = "Luminosity";
    public static final String REASON = "Claimed by the Void. Only a Charged Core can bring you back.";

    private VoidBans() {
    }

    /** Bans and immediately disconnects an online player. */
    public static void ban(Player player) {
        player.ban(REASON, (Instant) null, SOURCE, true);
    }

    /** Bans a player who may be offline, unless an existing ban already covers them. */
    public static void ban(UUID uuid) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
        if (target.isBanned()) {
            return;
        }
        Player online = target.getPlayer();
        if (online != null) {
            ban(online);
            return;
        }
        // OfflinePlayer#ban uses a UUID-only profile, which lists as a blank name in
        // /banlist. Attach the cached name so admins can tell who the entry is for.
        String name = target.getName();
        ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
        if (name == null) {
            target.ban(REASON, (Instant) null, SOURCE);
        } else {
            bans.addBan(Bukkit.createProfile(uuid, name), REASON, (Instant) null, SOURCE);
        }
    }

    /** Lifts the ban, but only if this plugin is the one that issued it. */
    public static void pardon(UUID uuid) {
        ProfileBanList bans = Bukkit.getBanList(BanListType.PROFILE);
        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
        BanEntry<?> entry = bans.getBanEntry(target.getPlayerProfile());
        if (entry != null && SOURCE.equals(entry.getSource())) {
            bans.pardon(target.getPlayerProfile());
        }
    }
}
