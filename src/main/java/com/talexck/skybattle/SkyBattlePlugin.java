package com.talexck.skybattle;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.minigamelib.api.setup.SetupService;
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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

public final class SkyBattlePlugin extends JavaPlugin {

  private static final String PERMISSION_PREFIX = "skybattle.command.";
  private static final List<String> SUBCOMMANDS =
      List.of("reload", "list", "start", "stop", "destroy", "setup");

  private ArenaService arenas;
  private SetupService setup;
  private SkyBattleSetupManager setupManager;
  private SkyBattleLanguage language;
  private SkyBattleLoadedConfig loadedConfig;
  private final Map<String, String> runningArenaTemplates = new HashMap<>();

  @Override
  public void onEnable() {
    this.language = new SkyBattleLanguage(this);
    MinigameLibrary library = Bukkit.getServicesManager().load(MinigameLibrary.class);
    if (library == null) {
      getLogger().severe(language.text("plugin.missing-minigamelib"));
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    this.arenas = library.arenas();
    this.setup = library.setup();
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
    if (!command.getName().equalsIgnoreCase("skybattle")) {
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
      default -> sendHelp(sender, label);
    }
    return true;
  }

  @Override
  public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
      @NotNull Command command, @NotNull String label, String @NotNull [] args) {
    if (!command.getName().equalsIgnoreCase("skybattle")) {
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
      default -> List.of();
    };
  }

  private void reloadSkyBattle() {
    SkyBattleConfigLoader configLoader = new SkyBattleConfigLoader(this);
    SkyBattleLoadedConfig config = configLoader.load();
    SkyBattleArenaFactory factory =
        new SkyBattleArenaFactory(this, language, config.global(), new SkyBattleLootTableLoader(this).load());

    for (SkyBattleArenaConfig arena : config.arenas()) {
      arenas.unregisterTemplate(arena.id());
      arenas.registerTemplate(factory.createTemplate(arena));
    }
    this.loadedConfig = config;
    getLogger().info(language.text("plugin.templates-registered", "{count}", config.arenas().size()));
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
    success(sender, language.text("command.running-header", "{count}", arenas.arenas().size()));
    for (ArenaHandle handle : arenas.arenas()) {
      sender.sendMessage(Component.text("- " + handle.arenaId() + " [" + handle.status() + "] "
          + handle.playerNames().size() + " 人", NamedTextColor.GRAY));
    }
  }

  private void handleStart(CommandSender sender, String[] args) {
    if (args.length < 2) {
      error(sender, language.text("command.usage-start"));
      return;
    }
    String templateId = args[1];
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

    ArenaCreateRequest request = new ArenaCreateRequest(arenaId, templateId, "skybattle_" + arenaId,
        null, null, players, null);
    arenas.createArena(request).thenAccept(handle -> {
      runningArenaTemplates.put(handle.arenaId(), templateId);
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
      runningArenaTemplates.remove(args[1]);
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

  private void sendHelp(CommandSender sender, String label) {
    sender.sendMessage(Component.text(language.text("command.help-header"), NamedTextColor.GOLD));
    if (hasCommandPermission(sender, "reload")) {
      sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "list")) {
      sender.sendMessage(Component.text("/" + label + " list", NamedTextColor.GRAY));
    }
    if (hasCommandPermission(sender, "start")) {
      sender.sendMessage(Component.text("/" + label + " start <arena>", NamedTextColor.GRAY));
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
  }

  private boolean hasCommandPermission(CommandSender sender, String subcommand) {
    return sender.hasPermission(PERMISSION_PREFIX + subcommand);
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
    return arenas.arenas().stream().map(ArenaHandle::arenaId).sorted().toList();
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
    sender.sendMessage(Component.text(message, NamedTextColor.GREEN));
  }

  private void error(CommandSender sender, String message) {
    sender.sendMessage(Component.text(message, NamedTextColor.RED));
  }
}
