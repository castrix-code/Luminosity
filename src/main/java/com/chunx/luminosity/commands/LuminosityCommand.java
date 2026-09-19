package com.chunx.luminosity.commands;

import com.chunx.luminosity.core.Luminosity;
import com.chunx.luminosity.core.LuminosityService;
import com.chunx.luminosity.data.PlayerData;
import com.chunx.luminosity.items.LuminousCore;
import com.chunx.luminosity.items.LuminousSigil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** {@code /luminosity} - status readout for everyone, tuning knobs for staff. */
public final class LuminosityCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "luminosity.admin";
    private static final List<String> ADMIN_SUBS =
            List.of("set", "add", "get", "revive", "eclipse", "core", "sigil");

    private final LuminosityService service;
    private final LuminousCore core;
    private final LuminousSigil sigil;

    public LuminosityCommand(LuminosityService service, LuminousCore core, LuminousSigil sigil) {
        this.service = service;
        this.core = core;
        this.sigil = sigil;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return status(sender);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("get")) {
            return get(sender, args);
        }
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }

        return switch (sub) {
            case "set" -> setOrAdd(sender, args, false);
            case "add" -> setOrAdd(sender, args, true);
            case "revive" -> revive(sender, args);
            case "eclipse" -> eclipse(sender, args);
            case "core" -> giveCore(sender, args);
            case "sigil" -> giveSigil(sender, args);
            default -> usage(sender);
        };
    }

    private boolean status(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return usage(sender);
        }
        report(sender, player.getName(), service.data(player));
        return true;
    }

    private boolean get(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        report(sender, target.getName(), service.data(target));
        return true;
    }

    private void report(CommandSender sender, String name, PlayerData data) {
        sender.sendMessage(Component.text(name + ": ", NamedTextColor.GRAY)
                .append(Component.text(data.luminosity() + " ", data.stage().path().color()))
                .append(Component.text("(" + data.stage().title() + ")", NamedTextColor.WHITE)));
        if (data.eclipse()) {
            sender.sendMessage(Component.text("  Eclipse: active", NamedTextColor.DARK_PURPLE));
        }
        if (data.voidLocked()) {
            sender.sendMessage(Component.text("  Banned by the Void, awaiting revival.", NamedTextColor.DARK_RED));
        }
    }

    private boolean setOrAdd(CommandSender sender, String[] args, boolean relative) {
        if (args.length < 3) {
            return usage(sender);
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        int value;
        try {
            value = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("'" + args[2] + "' is not a number.", NamedTextColor.RED));
            return true;
        }
        if (relative) {
            service.add(target, value, "adjusted by " + sender.getName());
        } else {
            service.set(target, value, "set by " + sender.getName());
        }
        report(sender, target.getName(), service.data(target));
        return true;
    }

    private boolean revive(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return usage(sender);
        }
        // Bukkit.getOfflinePlayer(String) blocks on a Mojang API call for an uncached
        // name, which would freeze the whole server. Resolve without leaving the box:
        // anyone eligible for revival has played here, so they are in the profile cache.
        UUID uuid;
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online != null) {
            uuid = online.getUniqueId();
        } else {
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (cached == null) {
                sender.sendMessage(Component.text("No player named '" + args[1]
                        + "' has played on this server.", NamedTextColor.RED));
                return true;
            }
            uuid = cached.getUniqueId();
        }

        if (!service.revive(uuid)) {
            sender.sendMessage(Component.text("That player was not claimed by the Void.", NamedTextColor.RED));
            return true;
        }
        sender.sendMessage(Component.text("Unbanned " + args[1] + "; they return at "
                + Luminosity.REVIVAL_LUMINOSITY + " Luminosity.", NamedTextColor.GREEN));
        return true;
    }

    private boolean eclipse(CommandSender sender, String[] args) {
        if (args.length < 3) {
            return usage(sender);
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        boolean on = Boolean.parseBoolean(args[2]);
        service.data(target).eclipse(on);
        service.traits().apply(target);
        sender.sendMessage(Component.text("Eclipse " + (on ? "granted to " : "removed from ")
                + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }

    private boolean giveCore(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        target.getInventory().addItem(core.create(Luminosity.STEP));
        sender.sendMessage(Component.text("Gave a Luminous Core to " + target.getName() + ".",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean giveSigil(CommandSender sender, String[] args) {
        Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
            return true;
        }
        target.getInventory().addItem(sigil.create());
        sender.sendMessage(Component.text("Gave a Luminous Sigil to " + target.getName() + ".",
                NamedTextColor.GREEN));
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(Component.text("/luminosity [get <player>]", NamedTextColor.GRAY));
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage(Component.text(
                    "/luminosity set|add <player> <value> | revive <player> | "
                            + "eclipse <player> <true|false> | core [player] | sigil [player]",
                    NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("get"));
            if (sender.hasPermission(ADMIN)) {
                subs.addAll(ADMIN_SUBS);
            }
            return prefixed(subs, args[0]);
        }
        if (args.length == 2) {
            return prefixed(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("eclipse")) {
            return prefixed(List.of("true", "false"), args[2]);
        }
        if (args.length == 3 && Arrays.asList("set", "add").contains(args[0].toLowerCase(Locale.ROOT))) {
            return prefixed(List.of("-100", "-40", "0", "40", "100"), args[2]);
        }
        return List.of();
    }

    private List<String> prefixed(List<String> options, String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
