package com.talexck.skybattle;

import com.talexck.minigamelib.api.MinigameLibrary;
import com.talexck.minigamelib.api.arena.ArenaCreateRequest;
import com.talexck.minigamelib.api.arena.ArenaHandle;
import com.talexck.minigamelib.api.arena.ArenaService;
import com.talexck.minigamelib.api.arena.ArenaStopReason;
import com.talexck.skybattle.config.SkyBattleArenaConfig;
import com.talexck.skybattle.config.SkyBattleConfigException;
import com.talexck.skybattle.config.SkyBattleConfigLoader;
import com.talexck.skybattle.config.SkyBattleLoadedConfig;
import com.talexck.skybattle.game.SkyBattleArenaFactory;
import com.talexck.skybattle.game.SkyBattleLootTableLoader;
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
      List.of("reload", "list", "start", "stop", "destroy");

  private ArenaService arenas;
  private SkyBattleLoadedConfig loadedConfig;
  private final Map<String, String> runningArenaTemplates = new HashMap<>();

  @Override
  public void onEnable() {
    MinigameLibrary library = Bukkit.getServicesManager().load(MinigameLibrary.class);
    if (library == null) {
      getLogger().severe("未找到 MinigameLib, SkyBattle 无法启动。");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }
    this.arenas = library.arenas();
    reloadSkyBattle();
    getLogger().info("SkyBattle Plugin enabled.");
  }

  @Override
  public void onDisable() {
    getLogger().info("SkyBattle Plugin disabled.");
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
      error(sender, "你没有权限执行这个命令。");
      return true;
    }

    switch (subcommand) {
      case "reload" -> handleReload(sender);
      case "list" -> handleList(sender);
      case "start" -> handleStart(sender, args);
      case "stop" -> handleStop(sender, args);
      case "destroy" -> handleDestroy(sender, args);
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
      default -> List.of();
    };
  }

  private void reloadSkyBattle() {
    SkyBattleConfigLoader configLoader = new SkyBattleConfigLoader(this);
    SkyBattleLoadedConfig config = configLoader.load();
    SkyBattleArenaFactory factory =
        new SkyBattleArenaFactory(config.global(), new SkyBattleLootTableLoader(this).load());

    for (SkyBattleArenaConfig arena : config.arenas()) {
      arenas.unregisterTemplate(arena.id());
      arenas.registerTemplate(factory.createTemplate(arena));
    }
    this.loadedConfig = config;
    getLogger().info("已注册 " + config.arenas().size() + " 个 SkyBattle arena 模板。");
  }

  private void handleReload(CommandSender sender) {
    try {
      reloadConfig();
      reloadSkyBattle();
      success(sender, "SkyBattle 配置已重载。");
    } catch (SkyBattleConfigException | IllegalArgumentException exception) {
      error(sender, "配置重载失败：" + exception.getMessage());
    }
  }

  private void handleList(CommandSender sender) {
    success(sender, "Arena 模板：");
    loadedConfig.arenas().stream().sorted(Comparator.comparing(SkyBattleArenaConfig::id))
        .forEach(arena -> sender.sendMessage(Component
            .text("- " + arena.id() + " -> " + arena.templateWorldName(), NamedTextColor.GRAY)));
    success(sender, "运行中 Arena：" + arenas.arenas().size());
    for (ArenaHandle handle : arenas.arenas()) {
      sender.sendMessage(Component.text("- " + handle.arenaId() + " [" + handle.status() + "] "
          + handle.playerNames().size() + " 人", NamedTextColor.GRAY));
    }
  }

  private void handleStart(CommandSender sender, String[] args) {
    if (args.length < 2) {
      error(sender, "用法：/skybattle start <arena>");
      return;
    }
    String templateId = args[1];
    String arenaId = generateArenaId(templateId);
    List<String> players = playersInCommandWorld(sender);
    if (players.isEmpty()) {
      error(sender, "当前世界没有可加入的玩家。");
      return;
    }
    if (players.size() > loadedConfig.global().maxPlayers()) {
      error(sender, "玩家数量超过上限：" + loadedConfig.global().maxPlayers());
      return;
    }

    ArenaCreateRequest request = new ArenaCreateRequest(arenaId, templateId, "skybattle_" + arenaId,
        null, null, players, null);
    arenas.createArena(request).thenAccept(handle -> {
      runningArenaTemplates.put(handle.arenaId(), templateId);
      arenas.startArena(handle.arenaId()).thenRun(
          () -> success(sender, "SkyBattle 已启动：" + handle.arenaId() + "，玩家 "
              + players.size() + " 人。")).exceptionally(exception -> {
        error(sender, "Arena 启动失败：" + exception.getMessage());
        return null;
      });
    }).exceptionally(exception -> {
      error(sender, "Arena 创建失败：" + exception.getMessage());
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
      error(sender, "用法：/skybattle stop <arenaId>");
      return;
    }
    arenas.stopArena(args[1], ArenaStopReason.FORCE)
        .thenRun(() -> success(sender, "Arena 已停止：" + args[1])).exceptionally(exception -> {
          error(sender, "Arena 停止失败：" + exception.getMessage());
          return null;
        });
  }

  private void handleDestroy(CommandSender sender, String[] args) {
    if (args.length < 2) {
      error(sender, "用法：/skybattle destroy <arenaId>");
      return;
    }
    arenas.destroyArena(args[1]).thenRun(() -> {
      runningArenaTemplates.remove(args[1]);
      success(sender, "Arena 已销毁：" + args[1]);
    }).exceptionally(exception -> {
      error(sender, "Arena 销毁失败：" + exception.getMessage());
      return null;
    });
  }

  private void sendHelp(CommandSender sender, String label) {
    sender.sendMessage(Component.text("SkyBattle 命令：", NamedTextColor.GOLD));
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
