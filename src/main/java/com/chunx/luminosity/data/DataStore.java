package com.chunx.luminosity.data;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Flat-file persistence for every player's luminosity record. */
public final class DataStore {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public DataStore(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public PlayerData get(UUID uuid) {
        return cache.computeIfAbsent(uuid, PlayerData::new);
    }

    public Collection<PlayerData> all() {
        return cache.values();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed player key in players.yml: " + key);
                continue;
            }
            ConfigurationSection sec = root.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            PlayerData data = new PlayerData(uuid);
            data.luminosity(sec.getInt("luminosity", 0));
            data.eclipse(sec.getBoolean("eclipse", false));
            data.voidLocked(sec.getBoolean("void-locked", false));
            data.pendingReturn(sec.getBoolean("pending-return", false));
            cache.put(uuid, data);
        }
        plugin.getLogger().info("Loaded " + cache.size() + " luminosity records.");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerData data : cache.values()) {
            String base = "players." + data.uuid();
            yaml.set(base + ".luminosity", data.luminosity());
            yaml.set(base + ".eclipse", data.eclipse());
            yaml.set(base + ".void-locked", data.voidLocked());
            yaml.set(base + ".pending-return", data.pendingReturn());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create data folder " + parent);
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save players.yml", ex);
        }
    }
}
