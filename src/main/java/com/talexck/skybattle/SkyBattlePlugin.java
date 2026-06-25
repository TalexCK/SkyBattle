package com.talexck.skybattle;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.lobby.LobbyService;
import com.talexck.minigamelib.api.lobby.LobbySettings;
import com.talexck.minigamelib.api.setup.SetupService;
import com.talexck.minigamelib.api.stats.LeaderboardType;
import com.talexck.minigamelib.api.stats.StatsBoard;
import com.talexck.minigamelib.api.stats.StatsService;
import com.talexck.skybattle.config.SkyBattleArenaConfig;
import com.talexck.skybattle.config.SkyBattleConfigException;
import com.talexck.skybattle.config.SkyBattleConfigLoader;
import com.talexck.skybattle.config.SkyBattleLanguage;
import com.talexck.skybattle.config.SkyBattleLoadedConfig;
import com.talexck.skybattle.game.SkyBattleArenaFactory;
import com.talexck.skybattle.game.SkyBattleLootTableLoader;
import com.talexck.skybattle.setup.SkyBattleSetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class SkyBattlePlugin extends JavaPlugin {

  private static final String ADMIN_PERMISSION = "skybattle.admin";
  private static final String PERMISSION_PREFIX = "skybattle.command.";
  private static final List<String> SUBCOMMANDS =
      List.of("reload", "list", "start", "stop", "destroy", "setup", "spawn", "board");

  private ArenaService arenas;
  private SetupService setup;
  private LobbyService lobby;
  private StatsService stats;
  private SkyBattleSetupManager setupManager;
  private SkyBattleLanguage language;
  private SkyBattleLoadedConfig loadedConfig;

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
    this.setupManager = new SkyBattleSetupManager(this, setup, language);
    reloadSkyBattle();
    getLogger().info(language.text("plugin.enabled"));
  }

  @Override
  public void onDisable() {
    if (setupManager != null) {
      setupManager.shutdown();
    }
    getLogger().info(language == null ? "SkyBattle Plugin disabled." : language.text("plugin.disabled"));
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, String @NotNull [] args) {
    if (!command.getName().equalsIgnoreCase("skb")
        && !command.getName().equalsIgnoreCase("skybattle")) {
      return false;
    }

    if (args.length == 0) {
      sendHelp(sender, label);
      return true;
    }

    String subcommand = args[0].toLowerCase(Locale.ROOT);
    if (!hasCommandPermission(sender, subcommand)) {
      error(sender, language.text("command.no-permission"));
      return true;
    }

    switch (subcommand) {
      case "reload" -> handleReload(sender);
      case "list" -> handleList(sender);
      case "start" -> handleStart(sender, args);
      case "stop" -> handleStop(sender, args);
      case "destroy" -> handleDestroy(sender, args);
      case "setup" -> handleSetup(sender, args);
      case "spawn" -> handleSpawn(sender);
      case "board" -> handleBoard(sender, args);
      default -> sendHelp(sender, label);
    }
    return true;
  }

  @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
      @NotNull Command command, @NotNull String label, String @NotNull [] args) {
    if (!command.getName().equalsIgnoreCase("skb")
        && !command.getName().equalsIgnoreCase("skybattle")) {
      return List.of();
    }
    if (args.length == 1) {
      return filterPrefix(allowedSubcommands(sender), args[0]);
    }

    String subcommand = args[0].toLowerCase(Locale.ROOT);
    if (!hasCommandPermission(sender, subcommand)) {
      return List.of();
    }

    return switch (subcommand) {
      case "start" -> args.length == 2 ? filterPrefix(templateIds(), args[1]) : List.of();
      case "stop", "destroy" -> args.length == 2 ? filterPrefix(runningArenaIds(), args[1])
          : List.of();
      case "setup" -> completeSetup(args);
      case "board" -> completeBoard(args);
      default -> List.of();
    };
  }

  private void reloadSkyBattle() {
    SkyBattleConfigLoader configLoader = new SkyBattleConfigLoader(this);
    SkyBattleLoadedConfig config = configLoader.load();
    SkyBattleArenaFactory factory =
        new SkyBattleArenaFactory(language, config.global(), new SkyBattleLootTableLoader(this).load());

    if (stats != null) {
      stats.configure(config.global().statsSettings());
    }

    for (SkyBattleArenaConfig arena : config.arenas()) {
      arenas.unregisterTemplate(arena.id());
      arenas.registerTemplate(factory.createTemplate(arena));
    }
    this.loadedConfig = config;
    configureLobby(config.global());
    getLogger().info(language.text("plugin.templates-registered", "{count}", config.arenas().size()));
  }

  private void configureLobby(com.talexck.skybattle.config.SkyBattleGlobalConfig global) {
    if (lobby != null) {
      lobby.configure(new LobbySettings(global.lobbyWorldName(), global.lobbySpawnPoint(),
          global.lobbyScoreboardTitle(), global.lobbyScoreboardLines()));
    }
  }

  private void handleReload(CommandSender sender) {
    try {
      reloadConfig();
      language.reload();
      reloadSkyBattle();
      success(sender, language.text("command.reload-success"));
    } catch (SkyBattleConfigException | IllegalArgumentException exception) {
      error(sender, language.text("command.reload-failed", "{error}", exception.getMessage()));
    }
  }

  private void handleList(CommandSender sender) {
    success(sender, language.text("command.templates-header"));
    loadedConfig.arenas().stream().sorted(Comparator.comparing(SkyBattleArenaConfig::id))
        .forEach(arena -> sender.sendMessage(Component
            .text("- " + arena.id() + " -> " + arena.templateWorldName(), NamedTextColor.GRAY)));
    List<ArenaHandle> runningArenas = visibleRunningArenas();
    success(sender, language.text("command.running-header", "{count}", runningArenas.size()));
    for (ArenaHandle handle : runningArenas) {
      sender.sendMessage(Component.text("- " + handle.arenaId() + " [" + handle.status() + "] "
          + handle.playerNames().size() + " 人", NamedTextColor.GRAY));
    }
  }

  private void handleStart(CommandSender sender, String[] args) {
    String templateId = resolveStartTemplateId(args);
    if (templateId == null) {
      error(sender, language.text("command.no-templates"));
      return;
    }
    String arenaId = generateArenaId(templateId);
    List<String> players = playersInCommandWorld(sender);
    if (players.isEmpty()) {
      error(sender, language.text("command.no-world-players"));
      return;
    }
    if (players.size() > loadedConfig.global().maxPlayers()) {
      error(sender, language.text("command.max-players-exceeded", "{max}", loadedConfig.global().maxPlayers()));
      return;
    }
    boolean allowSinglePlayer = sender.hasPermission(ADMIN_PERMISSION);
    if (players.size() < 2 && !allowSinglePlayer) {
      error(sender, language.text("command.not-enough-players"));
      return;
    }

    ArenaCreateRequest request = new ArenaCreateRequest(arenaId, templateId, "skybattle_" + arenaId,
        null, null, players, null, allowSinglePlayer);
    arenas.createArena(request).thenAccept(handle -> {
      arenas.startArena(handle.arenaId()).thenRun(
          () -> success(sender, language.text("command.start-success", "{arenaId}",
              handle.arenaId(), "{players}", players.size()))).exceptionally(exception -> {
        error(sender, language.text("command.start-failed", "{error}", exception.getMessage()));
        return null;
      });
    }).exceptionally(exception -> {
      error(sender, language.text("command.create-failed", "{error}", exception.getMessage()));
      return null;
    });
  }

  private List<String> playersInCommandWorld(CommandSender sender) {
    World world = commandWorld(sender);
    if (world == null) {
      return List.of();
    }
    return world.getPlayers().stream().map(Player::getName).toList();
  }

  private World commandWorld(CommandSender sender) {
    if (sender instanceof Player player) {
      return player.getWorld();
    }
    return Bukkit.getWorld(loadedConfig.global().returnWorldName());
  }

  private String generateArenaId(String templateId) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    return templateId + "_" + suffix;
  }

  private String resolveStartTemplateId(String[] args) {
    if (args.length >= 2 && !args[1].isBlank()) {
      return args[1];
    }
    List<String> templates = templateIds();
    if (templates.isEmpty()) {
      return null;
    }
    return templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
  }

  private void handleStop(CommandSender sender, String[] args) {
    if (args.length < 2) {
      error(sender, language.text("command.usage-stop"));
      return;
    }
    arenas.stopArena(args[1], ArenaStopReason.FORCE)
        .thenRun(() -> success(sender, language.text("command.stop-success", "{arenaId}", args[1]))).exceptionally(exception -> {
          error(sender, language.text("command.stop-failed", "{error}", exception.getMessage()));
          return null;
        });
  }

  private void handleDestroy(CommandSender sender, String[] args) {
    if (args.length < 2) {
      error(sender, language.text("command.usage-destroy"));
      return;
    }
    arenas.destroyArena(args[1]).thenRun(() -> {
      success(sender, language.text("command.destroy-success", "{arenaId}", args[1]));
    }).exceptionally(exception -> {
      error(sender, language.text("command.destroy-failed", "{error}", exception.getMessage()));
      return null;
    });
  }

  private void handleSetup(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      error(sender, language.text("command.setup-player-only"));
      return;
    }
    if (args.length < 3) {
      error(sender, language.text("command.usage-setup"));
      return;
    }
    setupManager.startSetup(player, args[1], args[2]);
  }

  private void handleSpawn(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      error(sender, language.text("command.spawn-player-only"));
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
    reloadConfig();
    if (lobby != null) {
      List<String> lines = getConfig().getStringList("lobby.scoreboard.lines");
      lobby.configure(new LobbySettings(location.getWorld().getName(), point,
          getConfig().getString("lobby.scoreboard.title", ""), lines));
    }
    success(sender, language.text("command.spawn-success"));
  }

  private void handleBoard(CommandSender sender, String[] args) {
    if (args.length < 3) {
      error(sender, language.text("command.usage-board"));
      return;
    }
    LeaderboardType type = LeaderboardType.fromKey(args[1]).orElse(null);
    if (type == null) {
      error(sender, language.text("command.board-type-invalid", "{type}", args[1]));
      return;
    }
    if (stats == null || !stats.isAvailable()) {
      error(sender, language.text("command.board-stats-unavailable"));
      return;
    }
    String action = args[2].toLowerCase(Locale.ROOT);
    switch (action) {
      case "create" -> handleBoardCreate(sender, args, type);
      case "list" -> handleBoardList(sender, type);
      case "delete" -> handleBoardDelete(sender, args, type);
      default -> error(sender, language.text("command.usage-board"));
    }
  }

  private void handleBoardCreate(CommandSender sender, String[] args, LeaderboardType type) {
    if (!(sender instanceof Player player)) {
      error(sender, language.text("command.board-player-only"));
      return;
    }
    if (args.length < 4) {
      error(sender, language.text("command.usage-board"));
      return;
    }
    String id = args[3];
    stats.createBoard(type, id, player.getLocation()).thenAccept(board -> runSync(() ->
        success(sender, language.text("command.board-create-success", "{type}",
            type.displayName(), "{id}", board.id())))).exceptionally(exception -> {
      runSync(() -> error(sender, language.text("command.board-create-failed", "{error}",
          rootMessage(exception))));
      return null;
    });
  }

  private void handleBoardList(CommandSender sender, LeaderboardType type) {
    stats.boards(type).thenAccept(boards -> runSync(() -> {
      success(sender, language.text("command.board-list-header", "{type}", type.displayName()));
      if (boards.isEmpty()) {
        sender.sendMessage(colored(language.text("command.board-list-empty"),
            NamedTextColor.GRAY));
        return;
      }
      for (StatsBoard board : boards) {
        sender.sendMessage(colored(language.text("command.board-list-line",
            "{id}", board.id(),
            "{world}", board.worldName(),
            "{x}", formatCoordinate(board.x()),
            "{y}", formatCoordinate(board.y()),
            "{z}", formatCoordinate(board.z())), NamedTextColor.GRAY));
      }
    })).exceptionally(exception -> {
      runSync(() -> error(sender, language.text("command.board-list-failed", "{error}",
          rootMessage(exception))));
      return null;
    });
  }

  private void handleBoardDelete(CommandSender sender, String[] args, LeaderboardType type) {
    if (args.length < 4) {
      error(sender, language.text("command.usage-board"));
      return;
    }
    String id = args[3];
    stats.deleteBoard(type, id).thenAccept(deleted -> runSync(() -> {
      if (deleted) {
        success(sender, language.text("command.board-delete-success", "{type}",
            type.displayName(), "{id}", id));
      } else {
        error(sender, language.text("command.board-delete-missing", "{id}", id));
      }
    })).exceptionally(exception -> {
      runSync(() -> error(sender, language.text("command.board-delete-failed", "{error}",
          rootMessage(exception))));
      return null;
    });
  }

  private void setConfigPoint(String path, ArenaPoint point) {
    getConfig().set(path + ".x", point.x());
    getConfig().set(path + ".y", point.y());
    getConfig().set(path + ".z", point.z());
    getConfig().set(path + ".yaw", point.yaw());
    getConfig().set(path + ".pitch", point.pitch());
  }

  private void sendHelp(CommandSender sender, String label) {
    sender.sendMessage(Component.text(language.text("command.help-header"), NamedTextColor.GOLD));
    if (hasCommandPermission(sender, "reload")) {
      sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "list")) {
      sender.sendMessage(Component.text("/" + label + " list", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "start")) {
      sender.sendMessage(Component.text("/" + label + " start [arena]", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "stop")) {
      sender.sendMessage(Component.text("/" + label + " stop <arenaId>", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "destroy")) {
      sender.sendMessage(Component.text("/" + label + " destroy <arenaId>", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "setup")) {
      sender.sendMessage(Component.text("/" + label + " setup <arena> <worldName>",
          NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "spawn")) {
      sender.sendMessage(Component.text("/" + label + " spawn", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "board")) {
      sender.sendMessage(Component.text("/" + label
          + " board <kills|wins|experience> <create|list|delete> [id]",
          NamedTextColor.GRAY));
    }
  }

  private boolean hasCommandPermission(CommandSender sender, String subcommand) {
    return sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(PERMISSION_PREFIX + subcommand);
  }

  private List<String> allowedSubcommands(CommandSender sender) {
    return SUBCOMMANDS.stream().filter(subcommand -> hasCommandPermission(sender, subcommand))
        .toList();
  }

  private List<String> templateIds() {
    if (loadedConfig == null) {
      return List.of();
    }
    return loadedConfig.arenas().stream().map(SkyBattleArenaConfig::id).sorted().toList();
  }

  private List<String> runningArenaIds() {
    if (arenas == null) {
      return List.of();
    }
    return visibleRunningArenas().stream().map(ArenaHandle::arenaId).sorted().toList();
  }

  private List<ArenaHandle> visibleRunningArenas() {
    return arenas.arenas().stream()
        .filter(handle -> handle.status() == ArenaStatus.CREATED
            || handle.status() == ArenaStatus.COUNTDOWN
            || handle.status() == ArenaStatus.RUNNING
            || handle.status() == ArenaStatus.STOPPING)
        .toList();
  }

  private List<String> completeSetup(String[] args) {
    if (args.length == 2) {
      return filterPrefix(templateIds(), args[1]);
    }
    if (args.length == 3) {
      return filterPrefix(setupWorldNames(), args[2]);
    }
    return List.of();
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
    java.io.File folder = new java.io.File(getServer().getWorldContainer(), "arena");
    java.io.File[] files = folder.listFiles(java.io.File::isDirectory);
    if (files == null) {
      return List.of();
    }
    return java.util.Arrays.stream(files)
        .map(java.io.File::getName)
        .sorted()
        .toList();
  }

  private List<String> filterPrefix(List<String> values, String prefix) {
    String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
    return values.stream()
        .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalizedPrefix)).toList();
  }

  private void success(CommandSender sender, String message) {
    sender.sendMessage(colored(message, NamedTextColor.GREEN));
  }

  private void error(CommandSender sender, String message) {
    sender.sendMessage(colored(message, NamedTextColor.RED));
  }

  private Component colored(String message, NamedTextColor fallbackColor) {
    if (message.indexOf('&') >= 0 || message.indexOf('§') >= 0) {
      return LegacyComponentSerializer.legacyAmpersand().deserialize(message.replace('§', '&'));
    }
    return Component.text(message, fallbackColor);
  }

  private void runSync(Runnable task) {
    Bukkit.getScheduler().runTask(this, task);
  }

  private String formatCoordinate(double value) {
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private String rootMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage();
  }

  private void registerRuntimeCommand() {
    CommandMap commandMap = Bukkit.getCommandMap();
    if (commandMap.getCommand("skb") != null) {
      return;
    }
    commandMap.register("skybattle", new Command("skb", "管理 SkyBattle arena。",
        "/skb", List.of("skybattle")) {
      @Override
      public boolean execute(@NotNull CommandSender sender, @NotNull String label,
          String @NotNull [] args) {
        return SkyBattlePlugin.this.onCommand(sender, this, label, args);
      }

      @Override
      public @NotNull List<String> tabComplete(@NotNull CommandSender sender,
          @NotNull String alias, String @NotNull [] args) {
        List<String> completions =
            SkyBattlePlugin.this.onTabComplete(sender, this, alias, args);
        return completions == null ? List.of() : completions;
      }
    });
  }

  private void registerPermissions() {
    registerPermission(ADMIN_PERMISSION);
    SUBCOMMANDS.forEach(subcommand -> registerPermission(PERMISSION_PREFIX + subcommand));
  }

  private void registerPermission(String name) {
    if (Bukkit.getPluginManager().getPermission(name) != null) {
      return;
    }
    Bukkit.getPluginManager().addPermission(new Permission(name, PermissionDefault.OP));
  }
}
