package com.talexck.skybattle.setup;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.skybattle.config.SkyBattleLanguage;
import com.talexck.skybattle.game.SkyBattleLootTier;
import com.talexck.skybattle.game.SkyBattleMode;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Guided in-game arena editor. The admin works in a temporary copy of the template world and is
 * walked through seven steps with chat prompts, clickable buttons, a progress boss bar and glowing
 * markers on everything already placed. {@code undo} reverts the last mark at any time.
 */
public final class SkyBattleSetupManager implements Listener {

  public static final List<String> ACTIONS =
      List.of("done", "skip", "undo", "cancel", "here", "default", "status");

  private static final List<ArenaTeamColor> TEAM_COLORS =
      List.of(ArenaTeamColor.RED, ArenaTeamColor.YELLOW, ArenaTeamColor.GREEN, ArenaTeamColor.BLUE,
          ArenaTeamColor.ORANGE, ArenaTeamColor.PURPLE, ArenaTeamColor.WHITE, ArenaTeamColor.PINK);
  private static final int STEP_COUNT = SetupStep.values().length;

  private final JavaPlugin plugin;
  private final SetupService setup;
  private final SkyBattleLanguage language;
  private final Map<UUID, SetupSession> sessions = new ConcurrentHashMap<>();
  private final Set<UUID> pendingSetups = ConcurrentHashMap.newKeySet();
  private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(task -> {
    Thread thread = new Thread(task, "skybattle-setup-io");
    thread.setDaemon(true);
    return thread;
  });

  public SkyBattleSetupManager(JavaPlugin plugin, SetupService setup, SkyBattleLanguage language) {
    this.plugin = plugin;
    this.setup = setup;
    this.language = language;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  public boolean isInSetup(Player player) {
    return sessions.containsKey(player.getUniqueId());
  }

  public void startSetup(Player player, String arenaId, String templateWorldName,
      SkyBattleMode mode) {
    UUID playerId = player.getUniqueId();
    if (sessions.containsKey(playerId) || pendingSetups.contains(playerId)) {
      send(player, language.text("setup.already-running"));
      return;
    }
    if (!arenaId.matches("[A-Za-z0-9_-]{1,32}")) {
      send(player, language.text("setup.invalid-arena-id"));
      return;
    }
    File source =
        new File(new File(plugin.getServer().getWorldContainer(), "arena"), templateWorldName);
    if (!source.isDirectory()) {
      send(player, language.text("setup.template-world-not-found", "{worldName}",
          templateWorldName));
      return;
    }
    if (new File(new File(plugin.getDataFolder(), "arena"), arenaId + ".yml").isFile()) {
      send(player, language.text("setup.overwrite-warning", "{arena}", arenaId));
    }

    String setupWorldName = "skybattle_setup_" + arenaId.toLowerCase(Locale.ROOT) + "_"
        + UUID.randomUUID().toString().substring(0, 8);
    File target = new File(plugin.getServer().getWorldContainer(), setupWorldName);
    // Reserve the slot synchronously so a second /skb setup can't race the async copy.
    pendingSetups.add(playerId);
    send(player, language.text("setup.copying", "{worldName}", templateWorldName));

    ioExecutor.execute(() -> {
      try {
        copyWorld(source.toPath(), target.toPath());
      } catch (IOException exception) {
        pendingSetups.remove(playerId);
        deleteDirectoryQuietly(target.toPath());
        runOnMain(() -> {
          Player online = Bukkit.getPlayer(playerId);
          if (online != null) {
            send(online, language.text("setup.copy-world-failed", "{error}",
                exception.getMessage()));
          }
        });
        return;
      }
      runOnMain(() -> finishStartSetup(playerId, arenaId, templateWorldName, setupWorldName,
          target.toPath(), mode));
    });
  }

  private void finishStartSetup(UUID playerId, String arenaId, String templateWorldName,
      String setupWorldName, Path target, SkyBattleMode mode) {
    if (!pendingSetups.remove(playerId)) {
      deleteDirectoryAsync(target);
      return;
    }
    Player player = Bukkit.getPlayer(playerId);
    if (player == null) {
      deleteDirectoryAsync(target);
      return;
    }
    World world = Bukkit.createWorld(new WorldCreator(setupWorldName));
    if (world == null) {
      deleteDirectoryAsync(target);
      send(player, language.text("setup.load-world-failed"));
      return;
    }
    Location returnLocation = player.getLocation();
    GameMode returnGameMode = player.getGameMode();
    player.teleport(world.getSpawnLocation());
    player.setGameMode(GameMode.CREATIVE);
    SetupSession session = new SetupSession(playerId, arenaId, templateWorldName, setupWorldName,
        target, world.getWorldFolder().toPath(), returnLocation, returnGameMode, mode);
    session.bossBar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_6);
    session.bossBar.addPlayer(player);
    sessions.put(playerId, session);
    setup.startBlockMarker(player, mark -> handleMark(session, mark.block()));
    send(player, language.text("setup.started", "{arena}", arenaId, "{mode}",
        language.text("mode." + mode.key())));
    prompt(player, session);
  }

  private void runOnMain(Runnable runnable) {
    if (!plugin.isEnabled()) {
      return;
    }
    Bukkit.getScheduler().runTask(plugin, runnable);
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
    pendingSetups.clear();
    for (SetupSession session : List.copyOf(sessions.values())) {
      cleanup(session, Bukkit.getPlayer(session.playerId), true);
    }
    sessions.clear();
    ioExecutor.shutdown();
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    pendingSetups.remove(event.getPlayer().getUniqueId());
    SetupSession session = sessions.remove(event.getPlayer().getUniqueId());
    if (session != null) {
      cleanup(session, event.getPlayer(), true);
    }
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void onChat(AsyncChatEvent event) {
    SetupSession session = sessions.get(event.getPlayer().getUniqueId());
    if (session == null) {
      return;
    }
    event.setCancelled(true);
    String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
    Bukkit.getScheduler().runTask(plugin, () -> handleInput(event.getPlayer(), session, message));
  }

  /** Entry point for {@code /skb setupaction <action>} (the clickable chat buttons). */
  public void handleAction(Player player, String action) {
    SetupSession session = sessions.get(player.getUniqueId());
    if (session == null) {
      send(player, language.text("setup.not-running"));
      return;
    }
    handleInput(player, session, action);
  }

  private void handleInput(Player player, SetupSession session, String message) {
    String lower = message.toLowerCase(Locale.ROOT);
    switch (lower) {
      case "cancel" -> {
        sessions.remove(player.getUniqueId());
        cleanup(session, player, true);
        send(player, language.text("setup.cancelled"));
        return;
      }
      case "undo" -> {
        undo(player, session);
        return;
      }
      case "status" -> {
        sendStatus(player, session);
        return;
      }
      default -> {
      }
    }
    switch (session.step) {
      case CENTER -> {
        if (lower.equals("here")) {
          Block block = player.getLocation().getBlock().getRelative(0, -1, 0);
          handleMark(session, block);
          return;
        }
        send(player, language.text("setup.marker-needed"));
      }
      case RADIUS -> {
        Double radius = parsePositive(message);
        if (radius == null) {
          send(player, language.text("setup.radius-invalid"));
          return;
        }
        double previous = session.initialBorderRadius;
        session.initialBorderRadius = radius;
        session.undo.push(() -> {
          session.initialBorderRadius = previous;
          session.step = SetupStep.RADIUS;
        });
        advance(player, session, SetupStep.BOUNDARY_WALL);
      }
      case BOUNDARY_WALL -> {
        if (lower.equals("skip") || lower.equals("done")) {
          advance(player, session, SetupStep.VERTICAL_BOUNDARY);
          return;
        }
        double[] values = parseDoubles(message, 4);
        if (values == null) {
          send(player, language.text("setup.boundary-wall-invalid"));
          return;
        }
        session.boundaryWall = normalizeBoundaryWall(values);
        session.undo.push(() -> {
          session.boundaryWall = null;
          session.step = SetupStep.BOUNDARY_WALL;
        });
        advance(player, session, SetupStep.VERTICAL_BOUNDARY);
      }
      case VERTICAL_BOUNDARY -> {
        if (lower.equals("skip") || lower.equals("done")) {
          session.verticalBoundary = new double[] {-1.0, -1.0};
          advance(player, session, SetupStep.TEAM_SPAWNS);
          return;
        }
        double[] values = parseDoubles(message, 2);
        if (values == null || !validVerticalBoundary(values)) {
          send(player, language.text("setup.vertical-boundary-invalid"));
          return;
        }
        session.verticalBoundary = values;
        session.undo.push(() -> {
          session.verticalBoundary = new double[] {-1.0, -1.0};
          session.step = SetupStep.VERTICAL_BOUNDARY;
        });
        advance(player, session, SetupStep.TEAM_SPAWNS);
      }
      case TEAM_SPAWNS -> {
        if (lower.equals("done") || lower.equals("skip")) {
          finishSpawns(player, session);
          return;
        }
        send(player, language.text("setup.marker-needed"));
      }
      case LOOT_CHESTS -> {
        if (!lower.equals("done") && !lower.equals("skip")) {
          send(player, language.text("setup.loot-chat-hint"));
          return;
        }
        if (session.advanceLootTier()) {
          prompt(player, session);
          return;
        }
        advance(player, session, SetupStep.BOUNDARY_STAGES);
      }
      case BOUNDARY_STAGES -> {
        if (lower.equals("done") || lower.equals("skip")) {
          finish(player, session);
          return;
        }
        if (lower.equals("default")) {
          session.boundaryStages.clear();
          session.boundaryStages.addAll(defaultStages(session.initialBorderRadius));
          send(player, language.text("setup.boundary-stage-defaults", "{count}",
              session.boundaryStages.size()));
          sendStages(player, session);
          prompt(player, session);
          return;
        }
        double[] values = parseBoundaryStage(message);
        if (values == null || !validVerticalBoundary(new double[] {values[2], values[3]})) {
          send(player, language.text("setup.boundary-stage-invalid"));
          return;
        }
        session.boundaryStages.add(values);
        session.undo.push(() -> session.boundaryStages.removeLast());
        send(player, language.text("setup.boundary-stage-added", "{count}",
            session.boundaryStages.size()));
        prompt(player, session);
      }
    }
  }

  private void handleMark(SetupSession session, Block block) {
    Player player = Bukkit.getPlayer(session.playerId);
    if (player == null) {
      return;
    }
    ArenaPoint point = new ArenaPoint(block.getX(), block.getY(), block.getZ(), 0f, 0f);
    switch (session.step) {
      case CENTER -> {
        session.center = point;
        Location location = block.getLocation();
        setup.showMarker(player, location, language.text("setup.marker-center"), Color.WHITE);
        session.undo.push(() -> {
          setup.removeMarker(player, location);
          session.center = null;
          session.step = SetupStep.CENTER;
        });
        advance(player, session, SetupStep.RADIUS);
      }
      case TEAM_SPAWNS -> markSpawn(player, session, block);
      case LOOT_CHESTS -> markChest(player, session, block, point);
      default -> send(player, language.text("setup.marker-not-needed"));
    }
  }

  private void markSpawn(Player player, SetupSession session, Block block) {
    ArenaTeamColor color = session.currentTeamColor();
    List<ArenaPoint> spawns = session.currentTeamSpawns();
    ArenaPoint spawn = spawnPointFromFootBlock(block);
    if (indexOfSameBlock(spawns, spawn) >= 0) {
      send(player, language.text("setup.spawn-duplicate"));
      return;
    }
    spawns.add(spawn);
    Location location = block.getLocation();
    setup.showMarker(player, location, teamLabel(color) + " &7#" + spawns.size(),
        teamColor(color));
    int teamIndex = session.teamIndex;
    session.undo.push(() -> {
      session.step = SetupStep.TEAM_SPAWNS;
      session.teamIndex = teamIndex;
      List<ArenaPoint> list = session.teamSpawns.get(color);
      if (!list.isEmpty()) {
        list.removeLast();
      }
      setup.removeMarker(player, location);
    });
    ding(player);
    if (spawns.size() < session.mode.spawnsPerTeam()) {
      prompt(player, session);
      return;
    }
    if (session.mode == SkyBattleMode.QUADS && !validTeamSpawnCorners(spawns)) {
      for (ArenaPoint existing : spawns) {
        setup.removeMarker(player, new Location(block.getWorld(), existing.x(), existing.y() - 1,
            existing.z()));
      }
      spawns.clear();
      send(player, language.text("setup.team-spawns-invalid"));
      prompt(player, session);
      return;
    }
    session.teamIndex++;
    if (session.teamIndex >= TEAM_COLORS.size()) {
      advance(player, session, SetupStep.LOOT_CHESTS);
      return;
    }
    prompt(player, session);
  }

  private void finishSpawns(Player player, SetupSession session) {
    if (!session.currentTeamSpawns().isEmpty()) {
      send(player, language.text("setup.spawns-incomplete", "{team}",
          teamLabel(session.currentTeamColor())));
      return;
    }
    if (session.teamIndex < 2) {
      send(player, language.text("setup.spawns-too-few"));
      return;
    }
    int teamIndex = session.teamIndex;
    session.undo.push(() -> {
      session.step = SetupStep.TEAM_SPAWNS;
      session.teamIndex = teamIndex;
    });
    advance(player, session, SetupStep.LOOT_CHESTS);
  }

  private void markChest(Player player, SetupSession session, Block block, ArenaPoint point) {
    SkyBattleLootTier tier = session.currentLootTier();
    for (SkyBattleLootTier other : SkyBattleLootTier.values()) {
      if (other != tier && indexOfSameBlock(session.lootPoints.get(other), point) >= 0) {
        send(player, language.text("setup.loot-other-tier", "{tier}", tierLabel(other)));
        return;
      }
    }
    List<ArenaPoint> points = session.lootPoints.get(tier);
    int existingIndex = indexOfSameBlock(points, point);
    Location location = block.getLocation();
    if (existingIndex >= 0) {
      points.remove(existingIndex);
      setup.removeMarker(player, location);
      send(player, language.text("setup.loot-removed", "{tier}", tierLabel(tier),
          "{point}", format(point), "{count}", points.size()));
      return;
    }
    points.add(point);
    markLootChestBlock(block, tier);
    setup.showMarker(player, location, tierLabel(tier), tier.markerColor());
    session.undo.push(() -> {
      session.step = SetupStep.LOOT_CHESTS;
      session.lootTierIndex = tier.ordinal();
      int index = indexOfSameBlock(session.lootPoints.get(tier), point);
      if (index >= 0) {
        session.lootPoints.get(tier).remove(index);
      }
      setup.removeMarker(player, location);
    });
    ding(player);
    player.sendActionBar(color(language.text("setup.loot-recorded", "{tier}", tierLabel(tier),
        "{point}", format(point), "{count}", points.size())));
    updateBossBar(player, session);
  }

  private void undo(Player player, SetupSession session) {
    Runnable action = session.undo.poll();
    if (action == null) {
      send(player, language.text("setup.nothing-to-undo"));
      return;
    }
    action.run();
    send(player, language.text("setup.undone"));
    prompt(player, session);
  }

  private void advance(Player player, SetupSession session, SetupStep next) {
    session.step = next;
    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
    prompt(player, session);
  }

  private void ding(Player player) {
    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.8f);
  }

  private void prompt(Player player, SetupSession session) {
    updateBossBar(player, session);
    String header = language.text("setup.step-header", "{step}", session.step.ordinal() + 1,
        "{total}", STEP_COUNT);
    String body;
    List<String> actions;
    switch (session.step) {
      case CENTER -> {
        body = language.text("setup.prompt-center");
        actions = List.of("here", "cancel");
      }
      case RADIUS -> {
        body = language.text("setup.prompt-radius");
        actions = List.of("undo", "cancel");
      }
      case BOUNDARY_WALL -> {
        body = language.text("setup.prompt-boundary-wall");
        actions = List.of("skip", "undo", "cancel");
      }
      case VERTICAL_BOUNDARY -> {
        body = language.text("setup.prompt-vertical-boundary");
        actions = List.of("skip", "undo", "cancel");
      }
      case TEAM_SPAWNS -> {
        String key = session.mode == SkyBattleMode.SOLO ? "setup.prompt-spawn-solo"
            : "setup.prompt-team-spawns";
        body = language.text(key, "{team}", teamLabel(session.currentTeamColor()),
            "{count}", session.currentTeamSpawns().size(),
            "{needed}", session.mode.spawnsPerTeam(),
            "{index}", session.teamIndex + 1, "{max}", TEAM_COLORS.size());
        actions = session.teamIndex >= 2 && session.currentTeamSpawns().isEmpty()
            ? List.of("done", "undo", "cancel") : List.of("undo", "cancel");
      }
      case LOOT_CHESTS -> {
        SkyBattleLootTier tier = session.currentLootTier();
        body = language.text("setup.prompt-loot-chests", "{tier}", tierLabel(tier),
            "{count}", session.lootPoints.get(tier).size());
        actions = List.of("done", "undo", "cancel");
      }
      default -> {
        body = language.text("setup.prompt-boundary-stages", "{count}",
            session.boundaryStages.size());
        actions = List.of("default", "done", "undo", "cancel");
      }
    }
    player.sendMessage(Component.empty());
    send(player, header);
    send(player, body);
    player.sendMessage(buttons(actions));
  }

  private Component buttons(List<String> actions) {
    Component line = Component.text(" ");
    for (String action : actions) {
      Component button = color(language.text("setup.button." + action))
          .clickEvent(ClickEvent.runCommand("/skb setupaction " + action))
          .hoverEvent(HoverEvent.showText(color(language.text("setup.button-hover." + action))));
      line = line.append(button).append(Component.text(" "));
    }
    return line;
  }

  private void updateBossBar(Player player, SetupSession session) {
    if (session.bossBar == null) {
      return;
    }
    String detail = switch (session.step) {
      case TEAM_SPAWNS -> teamLabel(session.currentTeamColor()) + " "
          + session.currentTeamSpawns().size() + "/" + session.mode.spawnsPerTeam();
      case LOOT_CHESTS -> tierLabel(session.currentLootTier()) + " "
          + session.lootPoints.get(session.currentLootTier()).size();
      default -> language.text("setup.step-name." + session.step.name().toLowerCase(Locale.ROOT));
    };
    session.bossBar.setTitle(LegacyComponentSerializer.legacySection().serialize(color(
        language.text("setup.bossbar", "{arena}", session.arenaId, "{step}",
            session.step.ordinal() + 1, "{total}", STEP_COUNT, "{detail}", detail))));
    session.bossBar.setProgress(Math.min(1.0, (session.step.ordinal() + 1.0) / STEP_COUNT));
  }

  private void sendStatus(Player player, SetupSession session) {
    send(player, language.text("setup.status-header", "{arena}", session.arenaId, "{mode}",
        language.text("mode." + session.mode.key())));
    send(player, language.text("setup.status-center", "{value}",
        session.center == null ? "-" : format(session.center), "{radius}",
        trim(session.initialBorderRadius)));
    long teams = TEAM_COLORS.stream().filter(color -> !session.teamSpawns.get(color).isEmpty())
        .count();
    send(player, language.text("setup.status-spawns", "{teams}", teams));
    for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
      send(player, language.text("setup.status-chests", "{tier}", tierLabel(tier), "{count}",
          session.lootPoints.get(tier).size()));
    }
    sendStages(player, session);
  }

  private void sendStages(Player player, SetupSession session) {
    int index = 1;
    for (double[] stage : session.boundaryStages) {
      send(player, language.text("setup.stage-line", "{index}", index++,
          "{x}", trim(stage[0]), "{z}", trim(stage[1]), "{lower}", trim(stage[2]),
          "{upper}", trim(stage[3]), "{delay}", trim(stage[4]), "{duration}", trim(stage[5])));
    }
  }

  /** Three MCC-like shrinks over a five minute round: 60% -> 30% -> a small final circle. */
  private List<double[]> defaultStages(double radius) {
    double second = Math.max(8.0, Math.round(radius * 0.6));
    double third = Math.max(6.0, Math.round(radius * 0.3));
    double last = Math.max(4.0, Math.min(10.0, Math.round(radius * 0.1)));
    return new ArrayList<>(List.of(
        new double[] {second, second, -1.0, -1.0, 60, 45},
        new double[] {third, third, -1.0, -1.0, 30, 45},
        new double[] {last, last, -1.0, -1.0, 20, 40}));
  }

  private void finish(Player player, SetupSession session) {
    if (session.center == null) {
      send(player, language.text("setup.missing-center"));
      return;
    }
    try {
      saveArena(session);
    } catch (IOException exception) {
      send(player, language.text("setup.save-failed", "{error}", exception.getMessage()));
      return;
    }
    sendStatus(player, session);
    sessions.remove(player.getUniqueId());
    cleanup(session, player, true);
    send(player, language.text("setup.saved", "{arena}", session.arenaId));
    player.sendMessage(color(language.text("setup.reload-button"))
        .clickEvent(ClickEvent.runCommand("/skb reload")));
  }

  private void saveArena(SetupSession session) throws IOException {
    File folder = new File(plugin.getDataFolder(), "arena");
    if (!folder.isDirectory() && !folder.mkdirs()) {
      throw new IOException("cannot create " + folder);
    }
    File file = new File(folder, session.arenaId + ".yml");
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("id", session.arenaId);
    yaml.set("display-name", session.arenaId);
    yaml.set("mode", session.mode.key());
    yaml.set("template-world", session.templateWorldName);
    setPoint(yaml, "center", session.center);
    yaml.set("initial-border-radius", session.initialBorderRadius);
    if (session.boundaryWall != null) {
      yaml.set("initial-boundary-wall.x1", session.boundaryWall[0]);
      yaml.set("initial-boundary-wall.x2", session.boundaryWall[1]);
      yaml.set("initial-boundary-wall.z1", session.boundaryWall[2]);
      yaml.set("initial-boundary-wall.z2", session.boundaryWall[3]);
    }
    yaml.set("vertical-boundary.lower-y", session.verticalBoundary[0]);
    yaml.set("vertical-boundary.upper-y", session.verticalBoundary[1]);
    List<Map<String, Object>> stages = new ArrayList<>();
    for (double[] stage : session.boundaryStages) {
      stages.add(Map.of("x-distance-from-center", stage[0], "z-distance-from-center", stage[1],
          "lower-y", stage[2], "upper-y", stage[3], "delay-seconds", (long) stage[4],
          "duration-seconds", (long) stage[5]));
    }
    yaml.set("boundary-stages", stages);
    List<Map<String, Object>> spawns = new ArrayList<>();
    for (ArenaTeamColor color : TEAM_COLORS) {
      List<ArenaPoint> points = session.teamSpawns.get(color);
      if (!points.isEmpty()) {
        spawns.add(Map.of("color", color.name(), "spawns",
            points.stream().map(this::format).toList()));
      }
    }
    yaml.set("team-spawns", spawns);
    for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
      yaml.set(tier.chestKey(), session.lootPoints.get(tier).stream().map(this::format).toList());
    }
    yaml.save(file);
  }

  private void setPoint(YamlConfiguration yaml, String path, ArenaPoint point) {
    yaml.set(path + ".x", point.x());
    yaml.set(path + ".y", point.y());
    yaml.set(path + ".z", point.z());
    yaml.set(path + ".yaw", point.yaw());
    yaml.set(path + ".pitch", point.pitch());
  }

  private void markLootChestBlock(Block block, SkyBattleLootTier tier) {
    block.setType(tier.chestMaterial());
    if (block.getState() instanceof Chest chest) {
      chest.customName(color(language.text("chest." + tier.fileName())));
      chest.update(true);
    }
  }

  private int indexOfSameBlock(List<ArenaPoint> points, ArenaPoint point) {
    for (int index = 0; index < points.size(); index++) {
      ArenaPoint existing = points.get(index);
      if (blockCoordinate(existing.x()) == blockCoordinate(point.x())
          && blockCoordinate(existing.y()) == blockCoordinate(point.y())
          && blockCoordinate(existing.z()) == blockCoordinate(point.z())) {
        return index;
      }
    }
    return -1;
  }

  private int blockCoordinate(double value) {
    return (int) Math.floor(value);
  }

  private ArenaPoint spawnPointFromFootBlock(Block block) {
    return new ArenaPoint(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5, 0f, 0f);
  }

  private boolean validTeamSpawnCorners(List<ArenaPoint> points) {
    if (points.size() != 4) {
      return false;
    }
    int y = blockCoordinate(points.getFirst().y());
    int minX = points.stream().mapToInt(point -> blockCoordinate(point.x())).min().orElse(0);
    int maxX = points.stream().mapToInt(point -> blockCoordinate(point.x())).max().orElse(0);
    int minZ = points.stream().mapToInt(point -> blockCoordinate(point.z())).min().orElse(0);
    int maxZ = points.stream().mapToInt(point -> blockCoordinate(point.z())).max().orElse(0);
    if (maxX - minX != 2 || maxZ - minZ != 2) {
      return false;
    }
    Set<String> corners = new HashSet<>();
    for (ArenaPoint point : points) {
      int x = blockCoordinate(point.x());
      int pointY = blockCoordinate(point.y());
      int z = blockCoordinate(point.z());
      if (pointY != y || (x != minX && x != maxX) || (z != minZ && z != maxZ)) {
        return false;
      }
      corners.add(x + ":" + z);
    }
    return corners.size() == 4;
  }

  private void cleanup(SetupSession session, Player player, boolean teleportBack) {
    if (session.bossBar != null) {
      session.bossBar.removeAll();
    }
    if (player != null) {
      setup.stopBlockMarker(player);
      if (teleportBack && player.getWorld().getName().equals(session.setupWorldName)) {
        player.teleport(session.returnLocation);
        player.setGameMode(session.returnGameMode);
      }
    }
    World world = Bukkit.getWorld(session.setupWorldName);
    if (world != null) {
      for (Player other : world.getPlayers()) {
        other.teleport(session.returnLocation);
      }
      Bukkit.unloadWorld(world, false);
    }
    deleteDirectoryAsync(session.setupWorldPath);
    if (!session.loadedWorldPath.equals(session.setupWorldPath)
        && session.loadedWorldPath.getFileName() != null
        && session.loadedWorldPath.getFileName().toString()
            .equalsIgnoreCase(session.setupWorldName)) {
      // 26.1+ migrates legacy world folders on load; remove the migrated copy as well.
      deleteDirectoryAsync(session.loadedWorldPath);
    }
  }

  /** Deletes a world directory off the main thread to avoid blocking the server on large worlds. */
  private void deleteDirectoryAsync(Path path) {
    if (ioExecutor.isShutdown()) {
      deleteDirectoryQuietly(path);
      return;
    }
    ioExecutor.execute(() -> deleteDirectoryQuietly(path));
  }

  private void copyWorld(Path source, Path target) throws IOException {
    try (java.util.stream.Stream<Path> stream = Files.walk(source)) {
      for (Path path : stream.toList()) {
        Path relative = source.relativize(path);
        String fileName = relative.getFileName() == null ? "" : relative.getFileName().toString();
        if (fileName.equals("uid.dat") || fileName.equals("session.lock")) {
          continue;
        }
        Path destination = target.resolve(relative);
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(path, destination);
        }
      }
    }
  }

  private void deleteDirectoryQuietly(Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
      stream.sorted(Comparator.reverseOrder()).forEach(entry -> {
        try {
          Files.deleteIfExists(entry);
        } catch (IOException ignored) {
          // Best effort cleanup.
        }
      });
    } catch (IOException ignored) {
      // Best effort cleanup.
    }
  }

  private Double parsePositive(String message) {
    try {
      double value = Double.parseDouble(message.trim());
      return value > 0 && Double.isFinite(value) ? value : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private double[] parseDoubles(String message, int count) {
    String[] parts = message.trim().split("\\s+");
    if (parts.length != count) {
      return null;
    }
    double[] values = new double[count];
    try {
      for (int index = 0; index < count; index++) {
        values[index] = Double.parseDouble(parts[index]);
      }
      return values;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private double[] parseBoundaryStage(String message) {
    String[] parts = message.trim().split("\\s+");
    if (parts.length != 4 && parts.length != 6) {
      return null;
    }
    double[] values = new double[parts.length];
    try {
      for (int index = 0; index < parts.length; index++) {
        values[index] = Double.parseDouble(parts[index]);
      }
    } catch (NumberFormatException exception) {
      return null;
    }
    double[] stage = parts.length == 4
        ? new double[] {values[0], values[1], -1.0, -1.0, values[2], values[3]} : values;
    if (stage[0] <= 0 || stage[1] <= 0 || stage[4] < 0 || stage[5] < 0) {
      return null;
    }
    return stage;
  }

  private double[] normalizeBoundaryWall(double[] values) {
    return new double[] {
        Math.min(values[0], values[1]),
        Math.max(values[0], values[1]),
        Math.min(values[2], values[3]),
        Math.max(values[2], values[3])
    };
  }

  private boolean validVerticalBoundary(double[] values) {
    double lower = values[0];
    double upper = values[1];
    return lower == -1.0 || upper == -1.0 || lower <= upper;
  }

  private String format(ArenaPoint point) {
    return trim(point.x()) + "," + trim(point.y()) + "," + trim(point.z()) + "," + trim(point.yaw())
        + "," + trim(point.pitch());
  }

  private String trim(double value) {
    if (value == Math.rint(value)) {
      return Long.toString((long) value);
    }
    return Double.toString(value);
  }

  private String teamLabel(ArenaTeamColor color) {
    return language.text("team-color." + color.name().toLowerCase(Locale.ROOT));
  }

  private String tierLabel(SkyBattleLootTier tier) {
    return tier.legacyColor() + language.text("tier." + tier.fileName());
  }

  private Color teamColor(ArenaTeamColor color) {
    return switch (color) {
      case RED -> Color.RED;
      case YELLOW -> Color.YELLOW;
      case GREEN -> Color.GREEN;
      case BLUE -> Color.BLUE;
      case ORANGE -> Color.ORANGE;
      case PURPLE -> Color.PURPLE;
      case WHITE -> Color.WHITE;
      case PINK -> Color.FUCHSIA;
      case GRAY -> Color.GRAY;
      case CYAN -> Color.AQUA;
    };
  }

  private void send(Player player, String message) {
    player.sendMessage(color(message));
  }

  private static Component color(String text) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
  }

  private enum SetupStep {
    CENTER, RADIUS, BOUNDARY_WALL, VERTICAL_BOUNDARY, TEAM_SPAWNS, LOOT_CHESTS, BOUNDARY_STAGES
  }

  private static final class SetupSession {
    private final UUID playerId;
    private final String arenaId;
    private final String templateWorldName;
    private final String setupWorldName;
    private final Path setupWorldPath;
    private final Path loadedWorldPath;
    private final Location returnLocation;
    private final GameMode returnGameMode;
    private final SkyBattleMode mode;
    private final Map<ArenaTeamColor, List<ArenaPoint>> teamSpawns =
        new EnumMap<>(ArenaTeamColor.class);
    private final Map<SkyBattleLootTier, List<ArenaPoint>> lootPoints =
        new EnumMap<>(SkyBattleLootTier.class);
    private final List<double[]> boundaryStages = new ArrayList<>();
    private final Deque<Runnable> undo = new ArrayDeque<>();
    private SetupStep step = SetupStep.CENTER;
    private ArenaPoint center;
    private double initialBorderRadius = 120.0;
    private double[] boundaryWall;
    private double[] verticalBoundary = new double[] {-1.0, -1.0};
    private int teamIndex;
    private int lootTierIndex;
    private BossBar bossBar;

    private SetupSession(UUID playerId, String arenaId, String templateWorldName,
        String setupWorldName, Path setupWorldPath, Path loadedWorldPath, Location returnLocation,
        GameMode returnGameMode, SkyBattleMode mode) {
      this.playerId = playerId;
      this.arenaId = arenaId;
      this.templateWorldName = templateWorldName;
      this.setupWorldName = setupWorldName;
      this.setupWorldPath = setupWorldPath.toAbsolutePath().normalize();
      this.loadedWorldPath = loadedWorldPath.toAbsolutePath().normalize();
      this.returnLocation = returnLocation;
      this.returnGameMode = returnGameMode;
      this.mode = mode;
      TEAM_COLORS.forEach(color -> teamSpawns.put(color, new ArrayList<>()));
      for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
        lootPoints.put(tier, new ArrayList<>());
      }
    }

    private ArenaTeamColor currentTeamColor() {
      return TEAM_COLORS.get(Math.min(teamIndex, TEAM_COLORS.size() - 1));
    }

    private List<ArenaPoint> currentTeamSpawns() {
      return teamSpawns.get(currentTeamColor());
    }

    private SkyBattleLootTier currentLootTier() {
      return SkyBattleLootTier.values()[lootTierIndex];
    }

    private boolean advanceLootTier() {
      if (lootTierIndex + 1 >= SkyBattleLootTier.values().length) {
        return false;
      }
      int previous = lootTierIndex;
      lootTierIndex++;
      undo.push(() -> {
        step = SetupStep.LOOT_CHESTS;
        lootTierIndex = previous;
      });
      return true;
    }
  }
}
