package com.talexck.skybattle;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.lobby.LobbyService;
import com.talexck.minigamelib.api.lobby.LobbySettings;
import com.talexck.minigamelib.api.queue.QueueJoinResult;
import com.talexck.minigamelib.api.queue.QueueService;
import com.talexck.minigamelib.api.queue.QueueSettings;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.minigamelib.api.stats.LeaderboardType;
import com.talexck.minigamelib.api.stats.StatsBoard;
import com.talexck.minigamelib.api.stats.StatsService;
import com.talexck.skybattle.config.SkyBattleArenaConfig;
import com.talexck.skybattle.config.SkyBattleConfigException;
import com.talexck.skybattle.config.SkyBattleConfigLoader;
import com.talexck.skybattle.config.SkyBattleGlobalConfig;
import com.talexck.skybattle.config.SkyBattleLanguage;
import com.talexck.skybattle.config.SkyBattleLoadedConfig;
import com.talexck.skybattle.config.SkyBattleModeSettings;
import com.talexck.skybattle.game.SkyBattleArenaFactory;
import com.talexck.skybattle.game.SkyBattleItems;
import com.talexck.skybattle.game.SkyBattleLootTableLoader;
import com.talexck.skybattle.game.SkyBattleMode;
import com.talexck.skybattle.lobby.SkyBattleMenu;
import com.talexck.skybattle.setup.SkyBattleSetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public final class SkyBattlePlugin extends JavaPlugin {

  private static final String ADMIN_PERMISSION = "skybattle.admin";
  private static final String PERMISSION_PREFIX = "skybattle.command.";
  /** Sub commands every player may use. */
  private static final List<String> PLAYER_SUBCOMMANDS =
      List.of("help", "join", "leave", "menu", "stats");
  private static final List<String> ADMIN_SUBCOMMANDS = List.of("reload", "list", "start",
      "forcestart", "stop", "destroy", "setup", "setupaction", "spawn", "board");

  private ArenaService arenas;
  private SetupService setup;
  private LobbyService lobby;
  private StatsService stats;
  private QueueService queues;
  private SkyBattleSetupManager setupManager;
  private SkyBattleMenu menu;
  private SkyBattleLanguage language;
  private SkyBattleLoadedConfig loadedConfig;
  private boolean forcingStart;

  @Override
  public void onEnable() {
    this.language = new SkyBattleLanguage(this);
    registerPermissions();
    registerRuntimeCommand();
    MinigameLibrary library = Bukkit.getServicesManager().load(MinigameLibrary.class);
    if (library == null) {
      getLogger().severe(language.text("plugin.missing-minigamelib"));
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    this.arenas = library.arenas();
    this.setup = library.setup();
    this.lobby = library.lobby();
    this.stats = library.stats();
    this.queues = library.queues();
    this.setupManager = new SkyBattleSetupManager(this, setup, language);
    this.menu = new SkyBattleMenu(this, language, queues, this::availableModes,
        mode -> arenas.arenas().stream()
            .filter(handle -> SkyBattleMenu.isLive(handle.status()))
            .filter(handle -> modeOfTemplate(handle.templateId()) == mode).count());
    exportResourcePack();
    try {
      reloadSkyBattle();
    } catch (RuntimeException exception) {
      getLogger().severe(language.text("command.reload-failed", "{error}",
          exception.getMessage()));
    }
    getLogger().info(language.text("plugin.enabled"));
  }

  @Override
  public void onDisable() {
    if (queues != null) {
      for (SkyBattleMode mode : SkyBattleMode.values()) {
        queues.unregister(mode.queueId());
      }
    }
    if (setupManager != null) {
      setupManager.shutdown();
    }
    if (menu != null) {
      menu.shutdown();
    }
    getLogger().info(language == null ? "SkyBattle disabled." : language.text("plugin.disabled"));
  }

  // ---- command dispatch ----------------------------------------------------

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, String @NotNull [] args) {
    if (args.length == 0) {
      if (sender instanceof Player player && loadedConfig != null
          && hasCommandPermission(sender, "menu")) {
        menu.open(player);
      } else {
        sendHelp(sender, label);
      }
      return true;
    }
    String subcommand = args[0].toLowerCase(Locale.ROOT);
    if (!PLAYER_SUBCOMMANDS.contains(subcommand) && !ADMIN_SUBCOMMANDS.contains(subcommand)) {
      sendHelp(sender, label);
      return true;
    }
    String permissionNode = subcommand.equals("setupaction") ? "setup" : subcommand;
    if (!hasCommandPermission(sender, permissionNode)) {
      send(sender, language.text("command.no-permission"));
      return true;
    }
    if (loadedConfig == null && !subcommand.equals("reload") && !subcommand.equals("help")) {
      send(sender, language.text("command.not-loaded"));
      return true;
    }
    switch (subcommand) {
      case "help" -> sendHelp(sender, label);
      case "join" -> handleJoin(sender, args);
      case "leave" -> handleLeave(sender);
      case "menu" -> handleMenu(sender);
      case "stats" -> handleStats(sender, args);
      case "reload" -> handleReload(sender);
      case "list" -> handleList(sender);
      case "start" -> handleStart(sender, args);
      case "forcestart" -> handleForceStart(sender, args);
      case "stop" -> handleStop(sender, args);
      case "destroy" -> handleDestroy(sender, args);
      case "setup" -> handleSetup(sender, args);
      case "setupaction" -> handleSetupAction(sender, args);
      case "spawn" -> handleSpawn(sender);
      case "board" -> handleBoard(sender, args);
      default -> sendHelp(sender, label);
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, String @NotNull [] args) {
    if (args.length == 1) {
      return filterPrefix(allowedSubcommands(sender), args[0]);
    }
    String subcommand = args[0].toLowerCase(Locale.ROOT);
    String permissionNode = subcommand.equals("setupaction") ? "setup" : subcommand;
    if (!hasCommandPermission(sender, permissionNode)) {
      return List.of();
    }
    List<String> modeKeys = Arrays.stream(SkyBattleMode.values()).map(SkyBattleMode::key).toList();
    return switch (subcommand) {
      case "join", "forcestart" -> args.length == 2 ? filterPrefix(modeKeys, args[1]) : List.of();
      case "start" -> args.length == 2 ? filterPrefix(Stream.concat(modeKeys.stream(),
          templateIds().stream()).toList(), args[1]) : List.of();
      case "stop", "destroy" -> args.length == 2 ? filterPrefix(runningArenaIds(), args[1])
          : List.of();
      case "setup" -> completeSetup(args, modeKeys);
      case "setupaction" -> args.length == 2
          ? filterPrefix(SkyBattleSetupManager.ACTIONS, args[1]) : List.of();
      case "board" -> completeBoard(args);
      case "stats" -> args.length == 2 ? filterPrefix(Bukkit.getOnlinePlayers().stream()
          .map(Player::getName).toList(), args[1]) : List.of();
      default -> List.of();
    };
  }

  // ---- loading ---------------------------------------------------------------

  private void reloadSkyBattle() {
    SkyBattleItems items = new SkyBattleItems(language);
    SkyBattleLoadedConfig config = new SkyBattleConfigLoader(this, items).load();
    SkyBattleArenaFactory factory = new SkyBattleArenaFactory(this, language, config.global(),
        new SkyBattleLootTableLoader(this, items).load());

    if (stats != null) {
      stats.configure(config.global().statsSettings());
    }
    if (loadedConfig != null) {
      loadedConfig.arenas().forEach(arena -> arenas.unregisterTemplate(arena.id()));
    }
    List<SkyBattleArenaConfig> registered = new ArrayList<>();
    for (SkyBattleArenaConfig arena : config.arenas()) {
      arenas.unregisterTemplate(arena.id());
      try {
        arenas.registerTemplate(factory.createTemplate(arena));
        registered.add(arena);
      } catch (RuntimeException exception) {
        getLogger().warning("Arena " + arena.id() + " skipped: " + exception.getMessage());
      }
    }
    this.loadedConfig = new SkyBattleLoadedConfig(config.global(), registered, config.problems());
    configureLobby(config.global());
    registerQueues(config.global());
    getLogger().info(language.text("plugin.templates-registered", "{count}", registered.size()));
    config.problems().forEach(problem -> getLogger().warning(problem));
  }

  private void registerQueues(SkyBattleGlobalConfig global) {
    for (SkyBattleMode mode : SkyBattleMode.values()) {
      SkyBattleModeSettings settings = global.mode(mode);
      List<SkyBattleArenaConfig> maps = arenasOf(mode);
      if (settings == null || !settings.enabled() || maps.isEmpty()) {
        queues.unregister(mode.queueId());
        continue;
      }
      int capacity = maps.stream().mapToInt(map -> map.capacity(settings.teamSize())).max()
          .orElse(settings.maxPlayers());
      int maxPlayers = Math.max(1, Math.min(settings.maxPlayers(), capacity));
      queues.register(new QueueSettings(mode.queueId(),
          language.text("queue-name." + mode.key()),
          Math.min(settings.minPlayers(), maxPlayers), maxPlayers,
          settings.queueCountdownSeconds(), settings.queueFullCountdownSeconds()),
          (queue, players) -> startGame(mode, players.stream().map(Player::getName).toList(),
              forcingStart, null));
    }
  }

  private void configureLobby(SkyBattleGlobalConfig global) {
    if (lobby == null) {
      return;
    }
    String title = getConfig().getString("lobby.scoreboard.title",
        language.text("lobby.scoreboard-title"));
    List<String> lines = getConfig().isList("lobby.scoreboard.lines")
        ? getConfig().getStringList("lobby.scoreboard.lines")
        : language.list("lobby.scoreboard-lines");
    Map<Integer, org.bukkit.inventory.ItemStack> hotbar =
        global.lobbyItems() ? Map.of(4, menu.selectorItem()) : Map.of();
    lobby.configure(new LobbySettings(global.lobbyWorldName(), global.lobbySpawnPoint(), title,
        lines, hotbar, this::lobbyPlaceholders));
  }

  private String lobbyPlaceholders(Player player, String text) {
    QueueSettings queue = queues.queueOf(player).orElse(null);
    String result = text
        .replace("{queue}", queue == null ? language.text("lobby.no-queue") : queue.displayName())
        .replace("{queue_count}", queue == null ? "-"
            : queues.members(queue.id()).size() + "/" + queue.maxPlayers());
    for (SkyBattleMode mode : SkyBattleMode.values()) {
      result = result.replace("{" + mode.key() + "_queue}",
          Integer.toString(queues.members(mode.queueId()).size()));
    }
    long games = arenas.arenas().stream().filter(handle -> SkyBattleMenu.isLive(handle.status()))
        .count();
    return result.replace("{games}", Long.toString(games));
  }

  /** Copies the bundled resource pack next to the config so it can be hosted anywhere. */
  private void exportResourcePack() {
    try (InputStream input = getResource(SkyBattleArenaFactory.RESOURCE_PACK_PATH)) {
      if (input == null) {
        return;
      }
      File target = new File(getDataFolder(), "skybattle-resourcepack.zip");
      Files.createDirectories(getDataFolder().toPath());
      Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException exception) {
      getLogger().warning("Could not export resource pack: " + exception.getMessage());
    }
  }

  // ---- player commands -----------------------------------------------------

  private void handleJoin(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      send(sender, language.text("command.player-only"));
      return;
    }
    if (setupManager.isInSetup(player)) {
      send(sender, language.text("command.in-setup"));
      return;
    }
    Optional<SkyBattleMode> mode = args.length >= 2 ? SkyBattleMode.fromKey(args[1])
        : Optional.of(availableModes().isEmpty() ? SkyBattleMode.QUADS
            : availableModes().getFirst());
    if (mode.isEmpty()) {
      send(sender, language.text("command.unknown-mode", "{mode}", args[1]));
      return;
    }
    QueueJoinResult result = queues.join(player, mode.get().queueId());
    String key = switch (result) {
      case JOINED, SWITCHED -> "command.join-success";
      case ALREADY_QUEUED -> "command.join-already";
      case FULL -> "command.join-full";
      case IN_GAME -> "command.join-in-game";
      case UNKNOWN_QUEUE -> "command.join-unavailable";
    };
    send(sender, language.text(key, "{mode}", language.text("mode." + mode.get().key())));
  }

  private void handleLeave(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      send(sender, language.text("command.player-only"));
      return;
    }
    send(sender, language.text(queues.leave(player) ? "command.leave-success"
        : "command.leave-not-queued"));
  }

  private void handleMenu(CommandSender sender) {
    if (sender instanceof Player player) {
      menu.open(player);
    } else {
      send(sender, language.text("command.player-only"));
    }
  }

  private void handleStats(CommandSender sender, String[] args) {
    if (stats == null || !stats.isAvailable()) {
      send(sender, language.text("command.board-stats-unavailable"));
      return;
    }
    UUID target;
    String name;
    if (args.length >= 2) {
      OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[1]);
      if (offline == null) {
        send(sender, language.text("command.stats-unknown", "{player}", args[1]));
        return;
      }
      target = offline.getUniqueId();
      name = offline.getName() == null ? args[1] : offline.getName();
    } else if (sender instanceof Player player) {
      target = player.getUniqueId();
      name = player.getName();
    } else {
      send(sender, language.text("command.player-only"));
      return;
    }
    stats.playerStats(target).thenAccept(result -> runSync(() -> {
      send(sender, language.text("command.stats-header", "{player}", name));
      send(sender, language.text("command.stats-line",
          "{kills}", result.map(value -> value.kills()).orElse(0),
          "{wins}", result.map(value -> value.wins()).orElse(0),
          "{experience}", result.map(value -> value.experience()).orElse(0)));
    })).exceptionally(exception -> {
      runSync(() -> send(sender, language.text("command.board-list-failed", "{error}",
          rootMessage(exception))));
      return null;
    });
  }

  // ---- admin commands ------------------------------------------------------

  private void handleReload(CommandSender sender) {
    try {
      reloadConfig();
      language.reload();
      reloadSkyBattle();
      send(sender, language.text("command.reload-success", "{count}",
          loadedConfig.arenas().size()));
      for (String problem : loadedConfig.problems()) {
        send(sender, language.text("command.reload-problem", "{problem}", problem));
      }
    } catch (SkyBattleConfigException | IllegalArgumentException exception) {
      send(sender, language.text("command.reload-failed", "{error}", exception.getMessage()));
    }
  }

  private void handleList(CommandSender sender) {
    send(sender, language.text("command.templates-header", "{count}",
        loadedConfig.arenas().size()));
    loadedConfig.arenas().stream().sorted(Comparator.comparing(SkyBattleArenaConfig::id))
        .forEach(arena -> {
          SkyBattleModeSettings mode = loadedConfig.global().mode(arena.mode());
          sender.sendMessage(color(language.text("command.template-line",
              "{id}", arena.id(), "{mode}", language.text("mode." + arena.mode().key()),
              "{world}", arena.templateWorldName(),
              "{teams}", arena.teamSpawns().size(),
              "{capacity}", arena.capacity(mode == null ? 1 : mode.teamSize()),
              "{chests}", arena.allChestPoints().size()))
              .clickEvent(ClickEvent.suggestCommand("/skb start " + arena.id()))
              .hoverEvent(HoverEvent.showText(color(language.text("command.template-hover")))));
        });
    List<ArenaHandle> running = arenas.arenas().stream()
        .filter(handle -> SkyBattleMenu.isLive(handle.status())
            || handle.status() == com.talexck.minigamelib.api.arena.ArenaStatus.STOPPING)
        .toList();
    send(sender, language.text("command.running-header", "{count}", running.size()));
    for (ArenaHandle handle : running) {
      sender.sendMessage(color(language.text("command.running-line", "{id}", handle.arenaId(),
          "{status}", handle.status().name(), "{players}", handle.playerNames().size()))
          .clickEvent(ClickEvent.suggestCommand("/skb stop " + handle.arenaId())));
    }
    for (SkyBattleMode mode : SkyBattleMode.values()) {
      send(sender, language.text("command.queue-line", "{mode}",
          language.text("mode." + mode.key()), "{count}",
          queues.members(mode.queueId()).size(), "{seconds}",
          Math.max(0, queues.secondsLeft(mode.queueId()))));
    }
  }

  /** {@code /skb start [arena|mode]}: starts immediately with the players in your world. */
  private void handleStart(CommandSender sender, String[] args) {
    SkyBattleArenaConfig arena;
    if (args.length >= 2 && SkyBattleMode.fromKey(args[1]).isPresent()) {
      arena = randomArena(SkyBattleMode.fromKey(args[1]).get(), 0).orElse(null);
    } else if (args.length >= 2) {
      arena = loadedConfig.arenas().stream().filter(candidate -> candidate.id().equals(args[1]))
          .findFirst().orElse(null);
      if (arena == null) {
        send(sender, language.text("command.unknown-arena", "{arena}", args[1]));
        return;
      }
    } else {
      List<SkyBattleArenaConfig> all = loadedConfig.arenas();
      arena = all.isEmpty() ? null : all.get(ThreadLocalRandom.current().nextInt(all.size()));
    }
    if (arena == null) {
      send(sender, language.text("command.no-templates"));
      return;
    }
    World world = sender instanceof Player player ? player.getWorld()
        : Bukkit.getWorld(loadedConfig.global().lobbyWorldName());
    if (world == null) {
      send(sender, language.text("command.no-world-players"));
      return;
    }
    SkyBattleModeSettings mode = loadedConfig.global().mode(arena.mode());
    int capacity = Math.min(mode.maxPlayers(), arena.capacity(mode.teamSize()));
    List<String> players = world.getPlayers().stream()
        .filter(player -> !arenas.isPlaying(player) && !setupManager.isInSetup(player))
        .map(Player::getName).limit(capacity).toList();
    if (players.isEmpty()) {
      send(sender, language.text("command.no-world-players"));
      return;
    }
    boolean allowSinglePlayer = sender.hasPermission(ADMIN_PERMISSION);
    if (players.size() < 2 && !allowSinglePlayer) {
      send(sender, language.text("command.not-enough-players"));
      return;
    }
    players.forEach(name -> Optional.ofNullable(Bukkit.getPlayerExact(name))
        .ifPresent(queues::leave));
    startGame(arena.mode(), players, allowSinglePlayer, arena, sender);
  }

  private void handleForceStart(CommandSender sender, String[] args) {
    Optional<SkyBattleMode> mode = args.length >= 2 ? SkyBattleMode.fromKey(args[1])
        : Optional.empty();
    if (mode.isEmpty()) {
      send(sender, language.text("command.usage-forcestart"));
      return;
    }
    forcingStart = true;
    try {
      if (!queues.forceStart(mode.get().queueId())) {
        send(sender, language.text("command.forcestart-empty"));
        return;
      }
    } finally {
      forcingStart = false;
    }
    send(sender, language.text("command.forcestart-success", "{mode}",
        language.text("mode." + mode.get().key())));
  }

  private void startGame(SkyBattleMode mode, List<String> players, boolean allowSinglePlayer,
      SkyBattleArenaConfig chosen) {
    startGame(mode, players, allowSinglePlayer, chosen, null);
  }

  private void startGame(SkyBattleMode mode, List<String> players, boolean allowSinglePlayer,
      SkyBattleArenaConfig chosen, CommandSender sender) {
    SkyBattleArenaConfig arena = chosen != null ? chosen
        : randomArena(mode, players.size()).orElse(null);
    if (arena == null) {
      notifyPlayers(players, sender, language.text("command.no-templates"));
      return;
    }
    String arenaId = arena.id() + "_" + UUID.randomUUID().toString().substring(0, 8);
    ArenaCreateRequest request = new ArenaCreateRequest(arenaId, arena.id(),
        "skybattle_" + arenaId.toLowerCase(Locale.ROOT), null, null, players, null,
        allowSinglePlayer);
    notifyPlayers(players, null, language.text("command.game-found", "{map}", arena.displayName(),
        "{mode}", language.text("mode." + mode.key())));
    arenas.createArena(request)
        .thenCompose(handle -> arenas.startArena(handle.arenaId()))
        .thenRun(() -> runSync(() -> {
          if (sender != null) {
            send(sender, language.text("command.start-success", "{arenaId}", arenaId,
                "{players}", players.size()));
          }
        }))
        .exceptionally(exception -> {
          runSync(() -> notifyPlayers(players, sender, language.text("command.start-failed",
              "{error}", rootMessage(exception))));
          return null;
        });
  }

  private void notifyPlayers(List<String> players, CommandSender sender, String message) {
    for (String name : players) {
      Player player = Bukkit.getPlayerExact(name);
      if (player != null && player != sender) {
        send(player, message);
      }
    }
    if (sender != null) {
      send(sender, message);
    }
  }

  /** A random map of the mode that fits the players (or the biggest one if none does). */
  private Optional<SkyBattleArenaConfig> randomArena(SkyBattleMode mode, int players) {
    SkyBattleModeSettings settings = loadedConfig.global().mode(mode);
    List<SkyBattleArenaConfig> maps = arenasOf(mode);
    if (maps.isEmpty()) {
      return Optional.empty();
    }
    int teamSize = settings == null ? mode.defaultTeamSize() : settings.teamSize();
    List<SkyBattleArenaConfig> fitting =
        maps.stream().filter(map -> map.capacity(teamSize) >= players).toList();
    if (fitting.isEmpty()) {
      return maps.stream().max(Comparator.comparingInt(map -> map.capacity(teamSize)));
    }
    return Optional.of(fitting.get(ThreadLocalRandom.current().nextInt(fitting.size())));
  }

  private void handleStop(CommandSender sender, String[] args) {
    if (args.length < 2) {
      send(sender, language.text("command.usage-stop"));
      return;
    }
    try {
      arenas.stopArena(args[1], ArenaStopReason.FORCE)
          .thenRun(() -> runSync(() -> send(sender, language.text("command.stop-success",
              "{arenaId}", args[1]))))
          .exceptionally(exception -> {
            runSync(() -> send(sender, language.text("command.stop-failed", "{error}",
                rootMessage(exception))));
            return null;
          });
    } catch (IllegalArgumentException exception) {
      send(sender, language.text("command.stop-failed", "{error}", exception.getMessage()));
    }
  }

  private void handleDestroy(CommandSender sender, String[] args) {
    if (args.length < 2) {
      send(sender, language.text("command.usage-destroy"));
      return;
    }
    try {
      arenas.destroyArena(args[1])
          .thenRun(() -> runSync(() -> send(sender, language.text("command.destroy-success",
              "{arenaId}", args[1]))))
          .exceptionally(exception -> {
            runSync(() -> send(sender, language.text("command.destroy-failed", "{error}",
                rootMessage(exception))));
            return null;
          });
    } catch (IllegalArgumentException exception) {
      send(sender, language.text("command.destroy-failed", "{error}", exception.getMessage()));
    }
  }

  private void handleSetup(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      send(sender, language.text("command.player-only"));
      return;
    }
    if (args.length < 3) {
      send(sender, language.text("command.usage-setup"));
      return;
    }
    if (arenas.isPlaying(player)) {
      send(sender, language.text("command.join-in-game"));
      return;
    }
    SkyBattleMode mode = SkyBattleMode.QUADS;
    if (args.length >= 4) {
      Optional<SkyBattleMode> parsed = SkyBattleMode.fromKey(args[3]);
      if (parsed.isEmpty()) {
        send(sender, language.text("command.unknown-mode", "{mode}", args[3]));
        return;
      }
      mode = parsed.get();
    }
    queues.leave(player);
    setupManager.startSetup(player, args[1], args[2], mode);
  }

  private void handleSetupAction(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player) || args.length < 2) {
      return;
    }
    setupManager.handleAction(player, args[1]);
  }

  private void handleSpawn(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      send(sender, language.text("command.player-only"));
      return;
    }
    Location location = player.getLocation();
    ArenaPoint point = new ArenaPoint(location.getX(), location.getY(), location.getZ(),
        location.getYaw(), location.getPitch());
    getConfig().set("lobby.world", location.getWorld().getName());
    setConfigPoint("lobby.spawn", point);
    getConfig().set("return.world", location.getWorld().getName());
    setConfigPoint("return.point", point);
    saveConfig();
    handleReload(sender);
    send(sender, language.text("command.spawn-success"));
  }

  private void handleBoard(CommandSender sender, String[] args) {
    if (args.length < 3) {
      send(sender, language.text("command.usage-board"));
      return;
    }
    LeaderboardType type = LeaderboardType.fromKey(args[1]).orElse(null);
    if (type == null) {
      send(sender, language.text("command.board-type-invalid", "{type}", args[1]));
      return;
    }
    if (stats == null || !stats.isAvailable()) {
      send(sender, language.text("command.board-stats-unavailable"));
      return;
    }
    switch (args[2].toLowerCase(Locale.ROOT)) {
      case "create" -> handleBoardCreate(sender, args, type);
      case "list" -> handleBoardList(sender, type);
      case "delete" -> handleBoardDelete(sender, args, type);
      default -> send(sender, language.text("command.usage-board"));
    }
  }

  private void handleBoardCreate(CommandSender sender, String[] args, LeaderboardType type) {
    if (!(sender instanceof Player player)) {
      send(sender, language.text("command.player-only"));
      return;
    }
    if (args.length < 4) {
      send(sender, language.text("command.usage-board"));
      return;
    }
    try {
      stats.createBoard(type, args[3], player.getLocation()).thenAccept(board -> runSync(() ->
          send(sender, language.text("command.board-create-success", "{type}",
              type.key(), "{id}", board.id())))).exceptionally(exception -> {
        runSync(() -> send(sender, language.text("command.board-create-failed", "{error}",
            rootMessage(exception))));
        return null;
      });
    } catch (IllegalArgumentException exception) {
      send(sender, language.text("command.board-create-failed", "{error}",
          exception.getMessage()));
    }
  }

  private void handleBoardList(CommandSender sender, LeaderboardType type) {
    stats.boards(type).thenAccept(boards -> runSync(() -> {
      send(sender, language.text("command.board-list-header", "{type}", type.key()));
      if (boards.isEmpty()) {
        send(sender, language.text("command.board-list-empty"));
        return;
      }
      for (StatsBoard board : boards) {
        send(sender, language.text("command.board-list-line",
            "{id}", board.id(),
            "{world}", board.worldName(),
            "{x}", formatCoordinate(board.x()),
            "{y}", formatCoordinate(board.y()),
            "{z}", formatCoordinate(board.z())));
      }
    })).exceptionally(exception -> {
      runSync(() -> send(sender, language.text("command.board-list-failed", "{error}",
          rootMessage(exception))));
      return null;
    });
  }

  private void handleBoardDelete(CommandSender sender, String[] args, LeaderboardType type) {
    if (args.length < 4) {
      send(sender, language.text("command.usage-board"));
      return;
    }
    String id = args[3];
    try {
      stats.deleteBoard(type, id).thenAccept(deleted -> runSync(() -> send(sender,
          deleted ? language.text("command.board-delete-success", "{type}", type.key(), "{id}", id)
              : language.text("command.board-delete-missing", "{id}", id))))
          .exceptionally(exception -> {
            runSync(() -> send(sender, language.text("command.board-delete-failed", "{error}",
                rootMessage(exception))));
            return null;
          });
    } catch (IllegalArgumentException exception) {
      send(sender, language.text("command.board-delete-failed", "{error}",
          exception.getMessage()));
    }
  }

  // ---- help / helpers ------------------------------------------------------

  private void sendHelp(CommandSender sender, String label) {
    send(sender, language.text("command.help-header"));
    for (String subcommand : allowedSubcommands(sender)) {
      String usage = language.text("command.help." + subcommand).replace("{label}", label);
      sender.sendMessage(color(usage).clickEvent(ClickEvent.suggestCommand("/" + label + " "
          + subcommand + " ")));
    }
  }

  private void setConfigPoint(String path, ArenaPoint point) {
    getConfig().set(path + ".x", point.x());
    getConfig().set(path + ".y", point.y());
    getConfig().set(path + ".z", point.z());
    getConfig().set(path + ".yaw", point.yaw());
    getConfig().set(path + ".pitch", point.pitch());
  }

  private boolean hasCommandPermission(CommandSender sender, String subcommand) {
    return sender.hasPermission(ADMIN_PERMISSION)
        || sender.hasPermission(PERMISSION_PREFIX + subcommand);
  }

  private List<String> allowedSubcommands(CommandSender sender) {
    return Stream.concat(PLAYER_SUBCOMMANDS.stream(), ADMIN_SUBCOMMANDS.stream())
        .filter(subcommand -> !subcommand.equals("setupaction"))
        .filter(subcommand -> hasCommandPermission(sender, subcommand)).toList();
  }

  private List<SkyBattleMode> availableModes() {
    return Arrays.stream(SkyBattleMode.values())
        .filter(mode -> queues.queues().stream().anyMatch(queue -> queue.id()
            .equals(mode.queueId())))
        .toList();
  }

  private List<SkyBattleArenaConfig> arenasOf(SkyBattleMode mode) {
    return loadedConfig == null ? List.of()
        : loadedConfig.arenas().stream().filter(arena -> arena.mode() == mode).toList();
  }

  private SkyBattleMode modeOfTemplate(String templateId) {
    return loadedConfig == null ? null : loadedConfig.arenas().stream()
        .filter(arena -> arena.id().equals(templateId)).map(SkyBattleArenaConfig::mode)
        .findFirst().orElse(null);
  }

  private List<String> templateIds() {
    return loadedConfig == null ? List.of()
        : loadedConfig.arenas().stream().map(SkyBattleArenaConfig::id).sorted().toList();
  }

  private List<String> runningArenaIds() {
    return arenas == null ? List.of() : arenas.arenas().stream()
        .filter(handle -> SkyBattleMenu.isLive(handle.status()))
        .map(ArenaHandle::arenaId).sorted().toList();
  }

  private List<String> completeSetup(String[] args, List<String> modeKeys) {
    return switch (args.length) {
      case 2 -> filterPrefix(templateIds(), args[1]);
      case 3 -> filterPrefix(setupWorldNames(), args[2]);
      case 4 -> filterPrefix(modeKeys, args[3]);
      default -> List.of();
    };
  }

  private List<String> completeBoard(String[] args) {
    if (args.length == 2) {
      return filterPrefix(List.of("kills", "wins", "experience"), args[1]);
    }
    if (args.length == 3) {
      return filterPrefix(List.of("create", "list", "delete"), args[2]);
    }
    return List.of();
  }

  private List<String> setupWorldNames() {
    File[] files = new File(getServer().getWorldContainer(), "arena")
        .listFiles(File::isDirectory);
    return files == null ? List.of() : Arrays.stream(files).map(File::getName).sorted().toList();
  }

  private List<String> filterPrefix(List<String> values, String prefix) {
    String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
    return values.stream()
        .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)).toList();
  }

  private void send(CommandSender sender, String message) {
    sender.sendMessage(color(message));
  }

  private static Component color(String message) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(message.replace('§', '&'));
  }

  private void runSync(Runnable task) {
    if (isEnabled()) {
      Bukkit.getScheduler().runTask(this, task);
    }
  }

  private String formatCoordinate(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName()
        : current.getMessage();
  }

  private void registerRuntimeCommand() {
    CommandMap commandMap = Bukkit.getCommandMap();
    if (commandMap.getCommand("skb") != null) {
      return;
    }
    commandMap.register("skybattle", new Command("skb", "SkyBattle", "/skb",
        List.of("skybattle")) {
      @Override
      public boolean execute(@NotNull CommandSender sender, @NotNull String label,
          String @NotNull [] args) {
        return SkyBattlePlugin.this.onCommand(sender, this, label, args);
      }

      @Override
      public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
          @NotNull String alias, String @NotNull [] args) {
        return SkyBattlePlugin.this.onTabComplete(sender, this, alias, args);
      }
    });
  }

  private void registerPermissions() {
    registerPermission(ADMIN_PERMISSION, PermissionDefault.OP);
    PLAYER_SUBCOMMANDS.forEach(subcommand ->
        registerPermission(PERMISSION_PREFIX + subcommand, PermissionDefault.TRUE));
    ADMIN_SUBCOMMANDS.forEach(subcommand ->
        registerPermission(PERMISSION_PREFIX + subcommand, PermissionDefault.OP));
  }

  private void registerPermission(String name, PermissionDefault defaultValue) {
    if (Bukkit.getPluginManager().getPermission(name) != null) {
      return;
    }
    Bukkit.getPluginManager().addPermission(new Permission(name, defaultValue));
  }
}
