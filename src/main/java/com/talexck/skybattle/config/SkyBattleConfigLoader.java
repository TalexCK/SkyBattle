package com.talexck.skybattle.config;

import com.talexck.minigamelib.api.arena.ArenaBoundaryStage;
import com.talexck.minigamelib.api.arena.ArenaBoundaryWall;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.arena.ArenaTeamSpawn;
import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;
import com.talexck.minigamelib.api.stats.StatsSettings;
import com.talexck.minigamelib.api.stats.StatsStorageConfig;
import com.talexck.minigamelib.api.stats.StatsStorageType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.skybattle.game.SkyBattleItems;
import com.talexck.skybattle.game.SkyBattleMode;

public final class SkyBattleConfigLoader {

  private final JavaPlugin plugin;
  private final SkyBattleItems items;

  public SkyBattleConfigLoader(JavaPlugin plugin, SkyBattleItems items) {
    this.plugin = plugin;
    this.items = items;
  }

  public SkyBattleLoadedConfig load() {
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    SkyBattleGlobalConfig global = loadGlobal(plugin.getConfig());
    List<String> problems = new ArrayList<>();
    List<SkyBattleArenaConfig> arenas = loadArenas(plugin.getDataFolder(), global, problems);
    return new SkyBattleLoadedConfig(global, arenas, problems);
  }

  private SkyBattleGlobalConfig loadGlobal(ConfigurationSection config) {
    String fallbackWorld = requireString(config, "return.world");
    ArenaPoint fallbackPoint = point(requireSection(config, "return.point"), "return.point");
    ConfigurationSection lobby = config.getConfigurationSection("lobby");
    String lobbyWorld = lobby == null ? fallbackWorld : lobby.getString("world", fallbackWorld);
    ArenaPoint lobbyPoint = lobby == null || lobby.getConfigurationSection("spawn") == null
        ? fallbackPoint
        : point(lobby.getConfigurationSection("spawn"), "lobby.spawn");
    boolean lobbyItems = lobby == null || lobby.getBoolean("give-items", true);
    Map<SkyBattleMode, SkyBattleModeSettings> modes = new EnumMap<>(SkyBattleMode.class);
    for (SkyBattleMode mode : SkyBattleMode.values()) {
      modes.put(mode, modeSettings(mode, config.getConfigurationSection("modes." + mode.key()),
          config));
    }
    ConfigurationSection pack = config.getConfigurationSection("resource-pack");
    SkyBattleResourcePackSettings resourcePack = new SkyBattleResourcePackSettings(
        pack != null && pack.getBoolean("enabled", false),
        pack != null && pack.getBoolean("required", false),
        pack == null ? "" : pack.getString("prompt", ""),
        pack == null ? "" : pack.getString("public-url-base", ""));
    return new SkyBattleGlobalConfig(
        lobbyWorld,
        lobbyPoint,
        lobbyItems,
        fallbackWorld,
        fallbackPoint,
        config.getDouble("default-initial-border-radius", 120.0),
        config.getBoolean("save-world-on-unload", false),
        statsSettings(config.getConfigurationSection("stats")),
        modes,
        resourcePack);
  }

  private SkyBattleModeSettings modeSettings(SkyBattleMode mode, ConfigurationSection section,
      ConfigurationSection root) {
    boolean solo = mode == SkyBattleMode.SOLO;
    int teamSize = section == null ? (solo ? 1 : root.getInt("team-size", 4))
        : section.getInt("team-size", mode.defaultTeamSize());
    int maxPlayers = section == null ? (solo ? 8 : root.getInt("max-players", 32))
        : section.getInt("max-players", teamSize * mode.islands());
    int minPlayers = section == null ? 2 : section.getInt("min-players", solo ? 2 : 4);
    List<Integer> placement = section == null || !section.isList("placement-score")
        ? (solo ? List.of(100, 80, 65, 50, 40, 30, 20, 10)
            : List.of(160, 130, 105, 85, 65, 50, 35, 20))
        : section.getIntegerList("placement-score");
    List<ArenaItemEntry> kit = new ArrayList<>();
    List<Map<?, ?>> kitEntries = section == null ? List.of() : section.getMapList("kit");
    if (kitEntries.isEmpty()) {
      kitEntries = defaultKit(solo);
    }
    for (Map<?, ?> entry : kitEntries) {
      kit.add(items.parse(entry));
    }
    return new SkyBattleModeSettings(
        mode,
        section == null || section.getBoolean("enabled", true),
        Math.max(1, teamSize),
        Math.max(1, Math.min(minPlayers, maxPlayers)),
        Math.max(1, maxPlayers),
        Math.max(0, section == null ? root.getInt("countdown-seconds", 10)
            : section.getInt("countdown-seconds", 10)),
        section == null ? 30 : section.getInt("queue-countdown-seconds", 30),
        section == null ? 10 : section.getInt("queue-full-countdown-seconds", 10),
        Duration.ofSeconds(Math.max(0, section == null ? 300
            : section.getInt("time-limit-seconds", 300))),
        Math.max(0, section == null ? (solo ? 25 : 20) : section.getInt("kill-score", 20)),
        Math.max(0, section == null ? (solo ? 5 : 2) : section.getInt("outlive-score", 2)),
        placement,
        Math.max(0.0, section == null ? 2.0 : section.getDouble("border-damage-per-second", 2.0)),
        kit);
  }

  /** MCC Island kit: stone sword, bow with 2 (quads) or 4 (solo) arrows, pickaxe, blocks. */
  private List<Map<?, ?>> defaultKit(boolean solo) {
    return List.of(
        Map.of("alias", "team_blocks"),
        Map.of("alias", "team_chestplate"),
        Map.of("alias", "team_leggings"),
        Map.of("alias", "team_boots"),
        Map.of("material", "STONE_SWORD"),
        Map.of("material", "BOW"),
        Map.of("material", "IRON_PICKAXE", "enchantments", Map.of("efficiency", 2)),
        Map.of("material", "ARROW", "amount", solo ? 4 : 2),
        Map.of("material", "COOKED_BEEF", "amount", 4));
  }

  private StatsSettings statsSettings(ConfigurationSection section) {
    ConfigurationSection storageSection =
        section == null ? null : section.getConfigurationSection("storage");
    String typeKey = storageSection == null ? "sqlite" : storageSection.getString("type", "sqlite");
    StatsStorageType type = StatsStorageType.fromKey(typeKey).orElse(StatsStorageType.SQLITE);
    String sqliteFile = storageSection == null
        ? "stats.db"
        : storageSection.getString("sqlite-file", "stats.db");
    if (type == StatsStorageType.SQLITE) {
      File file = new File(sqliteFile);
      if (!file.isAbsolute()) {
        file = new File(plugin.getDataFolder(), sqliteFile);
      }
      sqliteFile = file.getAbsolutePath();
    }
    String jdbcUrl = storageSection == null ? "" : storageSection.getString("jdbc-url", "");
    String username = storageSection == null ? "" : storageSection.getString("username", "");
    String password = storageSection == null ? "" : storageSection.getString("password", "");
    ConfigurationSection rewards =
        section == null ? null : section.getConfigurationSection("rewards");
    int killExperience = rewards == null ? 10 : rewards.getInt("kill-experience", 10);
    int winExperience = rewards == null ? 50 : rewards.getInt("win-experience", 50);
    return new StatsSettings(
        new StatsStorageConfig(type, sqliteFile, jdbcUrl, username, password),
        killExperience,
        winExperience);
  }

  private List<SkyBattleArenaConfig> loadArenas(File dataFolder, SkyBattleGlobalConfig global,
      List<String> problems) {
    File arenaFolder = new File(dataFolder, "arena");
    File[] files = arenaFolder.listFiles(file -> file.isFile() && file.getName().endsWith(".yml"));
    if (files == null) {
      return List.of();
    }

    List<File> sortedFiles = new ArrayList<>(List.of(files));
    sortedFiles.sort(Comparator.comparing(File::getName));
    List<SkyBattleArenaConfig> arenas = new ArrayList<>();
    java.util.Set<String> ids = new java.util.HashSet<>();
    for (File file : sortedFiles) {
      try {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ensureVerticalBoundaryDefaults(file, yaml);
        SkyBattleArenaConfig arena = loadArena(file, yaml, global);
        if (!ids.add(arena.id())) {
          throw new SkyBattleConfigException("duplicate arena id " + arena.id());
        }
        validate(arena, problems);
        arenas.add(arena);
      } catch (RuntimeException exception) {
        String problem = file.getName() + ": " + exception.getMessage();
        problems.add(problem);
        plugin.getLogger().warning("Skipping arena " + problem);
      }
    }
    return List.copyOf(arenas);
  }

  /** Non fatal checks: reported to admins on reload but the arena stays playable. */
  private void validate(SkyBattleArenaConfig arena, List<String> problems) {
    if (arena.teamSpawns().size() < 2) {
      throw new SkyBattleConfigException("needs spawns for at least 2 teams/players");
    }
    for (ArenaTeamSpawn spawn : arena.teamSpawns()) {
      if (spawn.spawnPoints().isEmpty()) {
        throw new SkyBattleConfigException("team " + spawn.color() + " has no spawn points");
      }
      if (arena.mode() == SkyBattleMode.QUADS && spawn.spawnPoints().size() != 4) {
        problems.add(arena.id() + ": team " + spawn.color() + " has "
            + spawn.spawnPoints().size() + " spawns (quads expects 4)");
      }
    }
    if (arena.allChestPoints().isEmpty()) {
      problems.add(arena.id() + ": has no loot chests");
    }
    if (!new File(new File(plugin.getServer().getWorldContainer(), "arena"),
        arena.templateWorldName()).isDirectory()) {
      problems.add(arena.id() + ": template world arena/" + arena.templateWorldName()
          + " not found");
    }
  }

  private SkyBattleArenaConfig loadArena(File file, ConfigurationSection config,
      SkyBattleGlobalConfig global) {
    String fallbackId = file.getName().replaceFirst("\\.yml$", "");
    String id = config.getString("id", fallbackId);
    String templateWorld = requireString(config, "template-world");
    ArenaPoint center = point(requireSection(config, "center"), "center");
    double radius = config.getDouble("initial-border-radius", global.defaultInitialBorderRadius());
    String modeKey = config.getString("mode", "quads");
    SkyBattleMode mode = SkyBattleMode.fromKey(modeKey)
        .orElseThrow(() -> new SkyBattleConfigException("unknown mode " + modeKey));

    return new SkyBattleArenaConfig(
        id,
        config.getString("display-name", id),
        mode,
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
      if (entry.get("spawns") instanceof List<?> list && list.isEmpty()) {
        continue;
      }
      String colorName = String.valueOf(entry.get("color")).toUpperCase(Locale.ROOT);
      ArenaTeamColor color;
      try {
        color = ArenaTeamColor.valueOf(colorName);
      } catch (IllegalArgumentException exception) {
        throw new SkyBattleConfigException("未知队伍颜色: " + colorName, exception);
      }
      if (!(entry.get("spawns") instanceof List<?> rawSpawns)) {
        throw new SkyBattleConfigException("team " + colorName + " is missing its spawns list");
      }
      spawns.add(new ArenaTeamSpawn(color, points(rawSpawns, "team-spawns")));
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
    try {
      return Double.parseDouble(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw new SkyBattleConfigException(
          "配置项 " + key + " 需要数字，实际为: " + value, exception);
    }
  }

  private long longNumber(java.util.Map<?, ?> map, String key, long fallback) {
    Object value = map.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.longValue();
    }
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (NumberFormatException exception) {
      throw new SkyBattleConfigException(
          "配置项 " + key + " 需要整数，实际为: " + value, exception);
    }
  }
}
