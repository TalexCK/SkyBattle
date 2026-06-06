package com.talexck.skybattle.config;

import com.talexck.minigamelib.api.arena.ArenaBoundaryStage;
import com.talexck.minigamelib.api.arena.ArenaBoundaryWall;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTeamSpawn;
import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SkyBattleConfigLoader {

  private final JavaPlugin plugin;

  public SkyBattleConfigLoader(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public SkyBattleLoadedConfig load() {
    plugin.saveDefaultConfig();

    SkyBattleGlobalConfig global = loadGlobal(plugin.getConfig());
    List<SkyBattleArenaConfig> arenas = loadArenas(plugin.getDataFolder(), global);
    return new SkyBattleLoadedConfig(global, arenas);
  }

  private SkyBattleGlobalConfig loadGlobal(ConfigurationSection config) {
    String fallbackWorld = requireString(config, "return.world");
    ArenaPoint fallbackPoint = point(requireSection(config, "return.point"), "return.point");
    ConfigurationSection lobby = config.getConfigurationSection("lobby");
    String lobbyWorld = lobby == null ? fallbackWorld : lobby.getString("world", fallbackWorld);
    ArenaPoint lobbyPoint = lobby == null || lobby.getConfigurationSection("spawn") == null
        ? fallbackPoint
        : point(lobby.getConfigurationSection("spawn"), "lobby.spawn");
    ConfigurationSection lobbyScoreboard =
        lobby == null ? null : lobby.getConfigurationSection("scoreboard");
    String lobbyScoreboardTitle = lobbyScoreboard == null
        ? ""
        : lobbyScoreboard.getString("title", "");
    List<String> lobbyScoreboardLines = lobbyScoreboard == null
        ? List.of()
        : lobbyScoreboard.getStringList("lines");
    return new SkyBattleGlobalConfig(
        config.getInt("countdown-seconds", 10),
        lobbyWorld,
        lobbyPoint,
        lobbyScoreboardTitle,
        lobbyScoreboardLines,
        fallbackWorld,
        fallbackPoint,
        config.getInt("max-players", 32),
        config.getInt("team-size", 4),
        config.getDouble("default-initial-border-radius", 120.0),
        config.getBoolean("save-world-on-unload", false));
  }

  private List<SkyBattleArenaConfig> loadArenas(File dataFolder, SkyBattleGlobalConfig global) {
    File arenaFolder = new File(dataFolder, "arena");
    File[] files = arenaFolder.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
    if (files == null) {
      return List.of();
    }

    List<File> sortedFiles = new ArrayList<>(List.of(files));
    sortedFiles.sort(Comparator.comparing(File::getName));
    List<SkyBattleArenaConfig> arenas = new ArrayList<>();
    for (File file : sortedFiles) {
      YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
      ensureVerticalBoundaryDefaults(file, yaml);
      arenas.add(loadArena(file, yaml, global));
    }
    return List.copyOf(arenas);
  }

  private SkyBattleArenaConfig loadArena(File file, ConfigurationSection config,
      SkyBattleGlobalConfig global) {
    String fallbackId = file.getName().replaceFirst("\\.yml$", "");
    String id = config.getString("id", fallbackId);
    String templateWorld = requireString(config, "template-world");
    ArenaPoint center = point(requireSection(config, "center"), "center");
    double radius = config.getDouble("initial-border-radius", global.defaultInitialBorderRadius());

    return new SkyBattleArenaConfig(
        id,
        templateWorld,
        center,
        radius,
        optionalBoundaryWall(config.getConfigurationSection("initial-boundary-wall")),
        verticalBoundary(config.getConfigurationSection("vertical-boundary")),
        boundaryStages(config.getMapList("boundary-stages")),
        teamSpawns(config.getMapList("team-spawns")),
        points(config.getList("commonchest"), "commonchest"),
        points(config.getList("uncommonchest"), "uncommonchest"),
        points(config.getList("rarechest"), "rarechest"),
        points(config.getList("epicchest"), "epicchest"),
        points(config.getList("legendarychest"), "legendarychest"));
  }

  private List<ArenaBoundaryStage> boundaryStages(List<java.util.Map<?, ?>> entries) {
    List<ArenaBoundaryStage> stages = new ArrayList<>();
    for (java.util.Map<?, ?> entry : entries) {
      double x = number(entry, "x-distance-from-center", 0.0);
      double z = number(entry, "z-distance-from-center", x);
      double lowerY = number(entry, "lower-y", ArenaVerticalBoundary.DISABLED);
      double upperY = number(entry, "upper-y", ArenaVerticalBoundary.DISABLED);
      long delay = longNumber(entry, "delay-seconds", 0L);
      long duration = longNumber(entry, "duration-seconds", 1L);
      stages.add(new ArenaBoundaryStage(x, z, lowerY, upperY, Duration.ofSeconds(delay),
          Duration.ofSeconds(duration)));
    }
    return List.copyOf(stages);
  }

  private void ensureVerticalBoundaryDefaults(File file, YamlConfiguration yaml) {
    boolean changed = false;
    if (!yaml.isSet("vertical-boundary.lower-y")) {
      yaml.set("vertical-boundary.lower-y", ArenaVerticalBoundary.DISABLED);
      changed = true;
    }
    if (!yaml.isSet("vertical-boundary.upper-y")) {
      yaml.set("vertical-boundary.upper-y", ArenaVerticalBoundary.DISABLED);
      changed = true;
    }
    if (!changed) {
      return;
    }
    try {
      yaml.save(file);
    } catch (IOException exception) {
      throw new SkyBattleConfigException("写入上下边界默认值失败: " + file.getName(), exception);
    }
  }

  private ArenaVerticalBoundary verticalBoundary(ConfigurationSection section) {
    if (section == null) {
      return new ArenaVerticalBoundary(ArenaVerticalBoundary.DISABLED,
          ArenaVerticalBoundary.DISABLED);
    }
    return new ArenaVerticalBoundary(
        section.getDouble("lower-y", ArenaVerticalBoundary.DISABLED),
        section.getDouble("upper-y", ArenaVerticalBoundary.DISABLED));
  }

  private List<ArenaTeamSpawn> teamSpawns(List<java.util.Map<?, ?>> entries) {
    List<ArenaTeamSpawn> spawns = new ArrayList<>();
    for (java.util.Map<?, ?> entry : entries) {
      String colorName = String.valueOf(entry.get("color")).toUpperCase(Locale.ROOT);
      ArenaTeamColor color;
      try {
        color = ArenaTeamColor.valueOf(colorName);
      } catch (IllegalArgumentException exception) {
        throw new SkyBattleConfigException("未知队伍颜色: " + colorName, exception);
      }
      spawns.add(new ArenaTeamSpawn(color, points((List<?>) entry.get("spawns"), "team-spawns")));
    }
    return List.copyOf(spawns);
  }

  private ArenaBoundaryWall optionalBoundaryWall(ConfigurationSection section) {
    if (section == null) {
      return null;
    }
    double x1 = section.getDouble("x1");
    double x2 = section.getDouble("x2");
    double z1 = section.getDouble("z1");
    double z2 = section.getDouble("z2");
    return new ArenaBoundaryWall(
        Math.min(x1, x2),
        Math.max(x1, x2),
        Math.min(z1, z2),
        Math.max(z1, z2));
  }

  private List<ArenaPoint> points(List<?> entries, String path) {
    if (entries == null) {
      return List.of();
    }
    List<ArenaPoint> points = new ArrayList<>();
    for (Object entry : entries) {
      points.add(point(entry, path));
    }
    return List.copyOf(points);
  }

  private ArenaPoint point(Object entry, String path) {
    if (entry instanceof ConfigurationSection section) {
      return point(section, path);
    }
    if (entry instanceof java.util.Map<?, ?> map) {
      return new ArenaPoint(
          number(map, "x", 0.0),
          number(map, "y", 0.0),
          number(map, "z", 0.0),
          (float) number(map, "yaw", 0.0),
          (float) number(map, "pitch", 0.0));
    }
    if (entry instanceof String text) {
      String[] parts = text.split(",");
      if (parts.length < 3 || parts.length > 5) {
        throw new SkyBattleConfigException(path + " 坐标格式应为 x,y,z[,yaw,pitch]");
      }
      try {
        return new ArenaPoint(
            Double.parseDouble(parts[0].trim()),
            Double.parseDouble(parts[1].trim()),
            Double.parseDouble(parts[2].trim()),
            parts.length >= 4 ? Float.parseFloat(parts[3].trim()) : 0f,
            parts.length >= 5 ? Float.parseFloat(parts[4].trim()) : 0f);
      } catch (NumberFormatException exception) {
        throw new SkyBattleConfigException(path + " 坐标包含非数字内容: " + text, exception);
      }
    }
    throw new SkyBattleConfigException(path + " 坐标必须是字符串或对象");
  }

  private ArenaPoint point(ConfigurationSection section, String path) {
    if (section == null) {
      throw new SkyBattleConfigException("缺少配置节点: " + path);
    }
    return new ArenaPoint(
        section.getDouble("x"),
        section.getDouble("y"),
        section.getDouble("z"),
        (float) section.getDouble("yaw", 0.0),
        (float) section.getDouble("pitch", 0.0));
  }

  private ConfigurationSection requireSection(ConfigurationSection config, String path) {
    ConfigurationSection section = config.getConfigurationSection(path);
    if (section == null) {
      throw new SkyBattleConfigException("缺少配置节点: " + path);
    }
    return section;
  }

  private String requireString(ConfigurationSection config, String path) {
    String value = config.getString(path);
    if (value == null || value.isBlank()) {
      throw new SkyBattleConfigException("缺少配置项: " + path);
    }
    return value;
  }

  private double number(java.util.Map<?, ?> map, String key, double fallback) {
    Object value = map.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return Double.parseDouble(String.valueOf(value));
  }

  private long longNumber(java.util.Map<?, ?> map, String key, long fallback) {
    Object value = map.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(String.valueOf(value));
  }
}
