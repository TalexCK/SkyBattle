package com.talexck.skybattle.setup;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaTeamColor;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.skybattle.config.SkyBattleLanguage;
import com.talexck.skybattle.game.SkyBattleLootTier;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkyBattleSetupManager implements Listener {

  private static final List<ArenaTeamColor> TEAM_COLORS =
      List.of(ArenaTeamColor.RED, ArenaTeamColor.YELLOW, ArenaTeamColor.GREEN, ArenaTeamColor.BLUE,
          ArenaTeamColor.ORANGE, ArenaTeamColor.PURPLE, ArenaTeamColor.WHITE, ArenaTeamColor.PINK);

  private final JavaPlugin plugin;
  private final SetupService setup;
  private final SkyBattleLanguage language;
  private final Map<UUID, SetupSession> sessions = new java.util.HashMap<>();

  public SkyBattleSetupManager(JavaPlugin plugin, SetupService setup, SkyBattleLanguage language) {
    this.plugin = plugin;
    this.setup = setup;
    this.language = language;
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  public void startSetup(Player player, String arenaId, String templateWorldName) {
    if (sessions.containsKey(player.getUniqueId())) {
      player.sendMessage(Component.text(language.text("setup.already-running")));
      return;
    }
    File source =
        new File(new File(plugin.getServer().getWorldContainer(), "arena"), templateWorldName);
    if (!source.isDirectory()) {
      player.sendMessage(Component
          .text(language.text("setup.template-world-not-found", "{worldName}", templateWorldName)));
      return;
    }

    String setupWorldName =
        "skybattle_setup_" + arenaId + "_" + UUID.randomUUID().toString().substring(0, 8);
    File target = new File(plugin.getServer().getWorldContainer(), setupWorldName);
    try {
      copyWorld(source.toPath(), target.toPath());
    } catch (IOException exception) {
      player.sendMessage(Component
          .text(language.text("setup.copy-world-failed", "{error}", exception.getMessage())));
      return;
    }

    World world = Bukkit.createWorld(new WorldCreator(setupWorldName));
    if (world == null) {
      deleteDirectoryQuietly(target.toPath());
      player.sendMessage(Component.text(language.text("setup.load-world-failed")));
      return;
    }

    Location returnLocation = player.getLocation();
    player.teleport(world.getSpawnLocation());
    SetupSession session = new SetupSession(player.getUniqueId(), arenaId, templateWorldName,
        setupWorldName, target.toPath(), returnLocation);
    sessions.put(player.getUniqueId(), session);
    setup.startBlockMarker(player, mark -> handleMark(session, mark.block()));
    prompt(player, session);
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
    for (SetupSession session : List.copyOf(sessions.values())) {
      Player player = Bukkit.getPlayer(session.playerId());
      cleanup(session, player, false);
    }
    sessions.clear();
  }

  @EventHandler
  public void onChat(AsyncChatEvent event) {
    SetupSession session = sessions.get(event.getPlayer().getUniqueId());
    if (session == null) {
      return;
    }
    event.setCancelled(true);
    String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
    Bukkit.getScheduler().runTask(plugin, () -> handleChat(event.getPlayer(), session, message));
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    SetupSession session = sessions.remove(event.getPlayer().getUniqueId());
    if (session != null) {
      cleanup(session, event.getPlayer(), false);
    }
  }

  private void handleChat(Player player, SetupSession session, String message) {
    if (message.equalsIgnoreCase("cancel")) {
      sessions.remove(player.getUniqueId());
      cleanup(session, player, true);
      player.sendMessage(Component.text(language.text("setup.cancelled")));
      return;
    }
    switch (session.step()) {
      case RADIUS -> {
        try {
          session.initialBorderRadius(Double.parseDouble(message));
          session.step(SetupStep.BOUNDARY_WALL);
          prompt(player, session);
        } catch (NumberFormatException exception) {
          player.sendMessage(Component.text(language.text("setup.radius-invalid")));
        }
      }
      case BOUNDARY_WALL -> {
        if (message.equalsIgnoreCase("skip")) {
          session.step(SetupStep.TEAM_SPAWNS);
          prompt(player, session);
          return;
        }
        double[] values = parseDoubles(message, 4);
        if (values == null) {
          player.sendMessage(Component.text(language.text("setup.boundary-wall-invalid")));
          return;
        }
        session.boundaryWall(values);
        session.step(SetupStep.TEAM_SPAWNS);
        prompt(player, session);
      }
      case LOOT_CHESTS -> {
        if (!message.equalsIgnoreCase("done")) {
          player.sendMessage(Component.text(language.text("setup.loot-chat-hint")));
          return;
        }
        if (session.advanceLootTier()) {
          prompt(player, session);
          return;
        }
        session.step(SetupStep.BOUNDARY_STAGES);
        prompt(player, session);
      }
      case BOUNDARY_STAGES -> {
        if (message.equalsIgnoreCase("done")) {
          finish(player, session);
          return;
        }
        double[] values = parseDoubles(message, 4);
        if (values == null) {
          player.sendMessage(Component.text(language.text("setup.boundary-stage-invalid")));
          return;
        }
        session.boundaryStages().add(values);
        player.sendMessage(Component.text(language.text("setup.boundary-stage-added")));
      }
      default -> player.sendMessage(Component.text(language.text("setup.marker-needed")));
    }
  }

  private void handleMark(SetupSession session, Block block) {
    Player player = Bukkit.getPlayer(session.playerId());
    if (player == null) {
      return;
    }
    ArenaPoint point = new ArenaPoint(block.getX(), block.getY(), block.getZ(), 0f, 0f);
    switch (session.step()) {
      case CENTER -> {
        session.center(point);
        session.step(SetupStep.RADIUS);
        prompt(player, session);
      }
      case TEAM_SPAWNS -> {
        session.currentTeamSpawns().add(point);
        if (session.currentTeamSpawns().size() >= 4) {
          session.advanceTeam();
        }
        prompt(player, session);
      }
      case LOOT_CHESTS -> {
        session.currentLootPoints().add(point);
        markLootChestBlock(block, session.currentLootTier());
        player.sendMessage(Component.text(language.text("setup.loot-recorded", "{tier}",
            session.currentLootTier().fileName(), "{point}", format(point))));
      }
      default -> player.sendMessage(Component.text(language.text("setup.marker-not-needed")));
    }
  }

  private void prompt(Player player, SetupSession session) {
    switch (session.step()) {
      case CENTER -> player.sendMessage(Component.text(language.text("setup.prompt-center")));
      case RADIUS -> player.sendMessage(Component.text(language.text("setup.prompt-radius")));
      case BOUNDARY_WALL -> player
          .sendMessage(Component.text(language.text("setup.prompt-boundary-wall")));
      case TEAM_SPAWNS -> player
          .sendMessage(Component.text(language.text("setup.prompt-team-spawns", "{team}",
              session.currentTeamColor(), "{count}", session.currentTeamSpawns().size())));
      case LOOT_CHESTS -> player.sendMessage(Component.text(language
          .text("setup.prompt-loot-chests", "{tier}", session.currentLootTier().fileName())));
      case BOUNDARY_STAGES -> player
          .sendMessage(Component.text(language.text("setup.prompt-boundary-stages")));
    }
  }

  private void finish(Player player, SetupSession session) {
    try {
      saveArena(session);
      sessions.remove(player.getUniqueId());
      cleanup(session, player, true);
      player
          .sendMessage(Component.text(language.text("setup.saved", "{arena}", session.arenaId())));
    } catch (IOException exception) {
      player.sendMessage(
          Component.text(language.text("setup.save-failed", "{error}", exception.getMessage())));
    }
  }

  private void saveArena(SetupSession session) throws IOException {
    File file = new File(new File(plugin.getDataFolder(), "arena"), session.arenaId() + ".yml");
    YamlConfiguration yaml = new YamlConfiguration();
    yaml.set("id", session.arenaId());
    yaml.set("template-world", session.templateWorldName());
    setPoint(yaml, "center", session.center());
    yaml.set("initial-border-radius", session.initialBorderRadius());
    if (session.boundaryWall() != null) {
      yaml.set("initial-boundary-wall.x1", session.boundaryWall()[0]);
      yaml.set("initial-boundary-wall.x2", session.boundaryWall()[1]);
      yaml.set("initial-boundary-wall.z1", session.boundaryWall()[2]);
      yaml.set("initial-boundary-wall.z2", session.boundaryWall()[3]);
    }
    List<Map<String, Object>> stages = new ArrayList<>();
    for (double[] stage : session.boundaryStages()) {
      stages.add(Map.of("x-distance-from-center", stage[0], "z-distance-from-center", stage[1],
          "delay-seconds", (long) stage[2], "duration-seconds", (long) stage[3]));
    }
    yaml.set("boundary-stages", stages);
    yaml.set("team-spawns", teamSpawnMaps(session));
    for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
      yaml.set(tier.chestKey(), session.lootPoints(tier).stream().map(this::format).toList());
    }
    yaml.save(file);
  }

  private List<Map<String, Object>> teamSpawnMaps(SetupSession session) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (ArenaTeamColor color : TEAM_COLORS) {
      result.add(Map.of("color", color.name(), "spawns",
          session.teamSpawns(color).stream().map(this::format).toList()));
    }
    return result;
  }

  private void setPoint(YamlConfiguration yaml, String path, ArenaPoint point) {
    yaml.set(path + ".x", point.x());
    yaml.set(path + ".y", point.y());
    yaml.set(path + ".z", point.z());
    yaml.set(path + ".yaw", point.yaw());
    yaml.set(path + ".pitch", point.pitch());
  }

  private void markLootChestBlock(Block block, SkyBattleLootTier tier) {
    block.setType(Material.CHEST);
    if (block.getState() instanceof Chest chest) {
      chest.customName(Component.text(tier.chestDisplayName()));
      chest.update(true);
    }
  }

  private void cleanup(SetupSession session, Player player, boolean teleportBack) {
    if (player != null) {
      setup.stopBlockMarker(player);
      if (teleportBack) {
        player.teleport(session.returnLocation());
      }
    }
    World world = Bukkit.getWorld(session.setupWorldName());
    if (world != null) {
      Bukkit.unloadWorld(world, false);
    }
    deleteDirectoryQuietly(session.setupWorldPath());
  }

  private void copyWorld(Path source, Path target) throws IOException {
    try (java.util.stream.Stream<Path> stream = Files.walk(source)) {
      for (Path path : stream.toList()) {
        Path relative = source.relativize(path);
        if (relative.toString().equals("uid.dat") || relative.toString().equals("session.lock")) {
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
    try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
      stream.sorted(Comparator.reverseOrder()).forEach(entry -> {
        try {
          Files.deleteIfExists(entry);
        } catch (IOException ignored) {
        }
      });
    } catch (IOException ignored) {
    }
  }

  private double[] parseDoubles(String message, int count) {
    String[] parts = message.split("\\s+");
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

  private enum SetupStep {
    CENTER, RADIUS, BOUNDARY_WALL, TEAM_SPAWNS, LOOT_CHESTS, BOUNDARY_STAGES
  }

  private static final class SetupSession {
    private final UUID playerId;
    private final String arenaId;
    private final String templateWorldName;
    private final String setupWorldName;
    private final Path setupWorldPath;
    private final Location returnLocation;
    private final Map<ArenaTeamColor, List<ArenaPoint>> teamSpawns =
        new EnumMap<>(ArenaTeamColor.class);
    private final Map<SkyBattleLootTier, List<ArenaPoint>> lootPoints =
        new EnumMap<>(SkyBattleLootTier.class);
    private final List<double[]> boundaryStages = new ArrayList<>();
    private SetupStep step = SetupStep.CENTER;
    private ArenaPoint center;
    private double initialBorderRadius = 120.0;
    private double[] boundaryWall;
    private int teamIndex;
    private int lootTierIndex;

    private SetupSession(UUID playerId, String arenaId, String templateWorldName,
        String setupWorldName, Path setupWorldPath, Location returnLocation) {
      this.playerId = playerId;
      this.arenaId = arenaId;
      this.templateWorldName = templateWorldName;
      this.setupWorldName = setupWorldName;
      this.setupWorldPath = setupWorldPath;
      this.returnLocation = returnLocation;
      TEAM_COLORS.forEach(color -> teamSpawns.put(color, new ArrayList<>()));
      for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
        lootPoints.put(tier, new ArrayList<>());
      }
    }

    private UUID playerId() {
      return playerId;
    }

    private String arenaId() {
      return arenaId;
    }

    private String templateWorldName() {
      return templateWorldName;
    }

    private String setupWorldName() {
      return setupWorldName;
    }

    private Path setupWorldPath() {
      return setupWorldPath;
    }

    private Location returnLocation() {
      return returnLocation;
    }

    private SetupStep step() {
      return step;
    }

    private void step(SetupStep step) {
      this.step = step;
    }

    private ArenaPoint center() {
      return center;
    }

    private void center(ArenaPoint center) {
      this.center = center;
    }

    private double initialBorderRadius() {
      return initialBorderRadius;
    }

    private void initialBorderRadius(double radius) {
      this.initialBorderRadius = radius;
    }

    private double[] boundaryWall() {
      return boundaryWall;
    }

    private void boundaryWall(double[] boundaryWall) {
      this.boundaryWall = boundaryWall;
    }

    private List<double[]> boundaryStages() {
      return boundaryStages;
    }

    private ArenaTeamColor currentTeamColor() {
      return TEAM_COLORS.get(teamIndex);
    }

    private List<ArenaPoint> currentTeamSpawns() {
      return teamSpawns(currentTeamColor());
    }

    private List<ArenaPoint> teamSpawns(ArenaTeamColor color) {
      return teamSpawns.get(color);
    }

    private void advanceTeam() {
      teamIndex++;
      if (teamIndex >= TEAM_COLORS.size()) {
        step = SetupStep.LOOT_CHESTS;
      }
    }

    private SkyBattleLootTier currentLootTier() {
      return SkyBattleLootTier.values()[lootTierIndex];
    }

    private List<ArenaPoint> currentLootPoints() {
      return lootPoints(currentLootTier());
    }

    private List<ArenaPoint> lootPoints(SkyBattleLootTier tier) {
      return lootPoints.get(tier);
    }

    private boolean advanceLootTier() {
      lootTierIndex++;
      return lootTierIndex < SkyBattleLootTier.values().length;
    }
  }
}
