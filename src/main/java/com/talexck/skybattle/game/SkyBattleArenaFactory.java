package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaActionBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBoundaryShape;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLootChest;
import com.talexck.minigamelib.api.arena.ArenaLootEntry;
import com.talexck.minigamelib.api.arena.ArenaLootPlacementMode;
import com.talexck.minigamelib.api.arena.ArenaMessages;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaPresentation;
import com.talexck.minigamelib.api.arena.ArenaResourcePackConfig;
import com.talexck.minigamelib.api.arena.ArenaRules;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaSoundConfig;
import com.talexck.minigamelib.api.arena.ArenaTeamFillMode;
import com.talexck.minigamelib.api.arena.ArenaTemplate;
import com.talexck.minigamelib.api.arena.ArenaTitleConfig;
import com.talexck.minigamelib.api.arena.ArenaTitleFrame;
import com.talexck.minigamelib.api.arena.ArenaVictoryCondition;
import com.talexck.skybattle.config.SkyBattleArenaConfig;
import com.talexck.skybattle.config.SkyBattleGlobalConfig;
import com.talexck.skybattle.config.SkyBattleLanguage;
import com.talexck.skybattle.config.SkyBattleModeSettings;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns a Sky Battle arena config into a MinigameLib template for its mode. */
public final class SkyBattleArenaFactory {

  public static final String RESOURCE_PACK_PATH = "resourcepack/skybattle-resourcepack.zip";

  private final JavaPlugin plugin;
  private final SkyBattleGlobalConfig global;
  private final Map<SkyBattleMode, Map<SkyBattleLootTier, SkyBattleLootTable>> lootTables;
  private final SkyBattleLanguage language;

  public SkyBattleArenaFactory(JavaPlugin plugin, SkyBattleLanguage language,
      SkyBattleGlobalConfig global,
      Map<SkyBattleMode, Map<SkyBattleLootTier, SkyBattleLootTable>> lootTables) {
    this.plugin = plugin;
    this.language = language;
    this.global = global;
    this.lootTables = lootTables;
  }

  public ArenaTemplate createTemplate(SkyBattleArenaConfig arena) {
    SkyBattleModeSettings mode = global.mode(arena.mode());
    ArenaLayout layout = new ArenaLayout(
        arena.teamSpawns().stream().flatMap(spawn -> spawn.spawnPoints().stream()).toList(),
        arena.teamSpawns(),
        arena.allChestPoints(),
        arena.center(),
        arena.initialBorderRadius());

    ArenaSettings settings = new ArenaSettings(
        mode.countdownSeconds(),
        mode.teamSize(),
        global.returnWorldName(),
        global.returnPoint(),
        global.saveWorldOnUnload(),
        scoreboard(arena.mode()),
        bossBar(),
        ArenaActionBarConfig.disabled(),
        titles(),
        ArenaSoundConfig.disabled(),
        resourcePack(),
        mode.kit(),
        lootChests(arena),
        arena.initialBoundaryWall(),
        arena.verticalBoundary(),
        arena.boundaryStages(),
        ArenaVictoryCondition.OTHER_TEAMS_ALL_FAILED,
        messages(),
        rules(mode, arena),
        presentation(arena));

    return new ArenaTemplate(arena.id(), arena.templateWorldName(), layout, settings, null);
  }

  private ArenaRules rules(SkyBattleModeSettings mode, SkyBattleArenaConfig arena) {
    // A configured rectangular wall keeps the old square border; otherwise MCC's round border.
    ArenaBoundaryShape shape = arena.initialBoundaryWall() == null ? ArenaBoundaryShape.CIRCLE
        : ArenaBoundaryShape.RECTANGLE;
    return new ArenaRules(mode.timeLimit(), mode.killScore(), mode.outliveScore(),
        mode.placementScores(), ArenaTeamFillMode.FILL, shape, mode.borderDamagePerSecond(), 10);
  }

  private ArenaPresentation presentation(SkyBattleArenaConfig arena) {
    return new ArenaPresentation(
        language.text("game-name"),
        language.text("mode." + arena.mode().key()),
        arena.displayName(),
        language.text("arena.feedback.eliminated-title"),
        language.text("arena.feedback.eliminated-subtitle"),
        language.text("arena.feedback.kill-actionbar"),
        language.text("arena.feedback.team-eliminated"),
        language.text("arena.feedback.victory-title"),
        language.text("arena.feedback.victory-subtitle"),
        language.text("arena.feedback.defeat-title"),
        language.text("arena.feedback.defeat-subtitle"),
        language.text("arena.bossbar-running"),
        language.text("arena.feedback.border-shrink"),
        language.text("arena.feedback.out-of-bounds"),
        language.text("arena.feedback.time-up"));
  }

  private List<ArenaLootChest> lootChests(SkyBattleArenaConfig arena) {
    List<ArenaLootChest> chests = new ArrayList<>();
    Map<SkyBattleLootTier, SkyBattleLootTable> tables = lootTables.get(arena.mode());
    addChests(chests, tables, arena.commonChests(), SkyBattleLootTier.COMMON);
    addChests(chests, tables, arena.uncommonChests(), SkyBattleLootTier.UNCOMMON);
    addChests(chests, tables, arena.rareChests(), SkyBattleLootTier.RARE);
    addChests(chests, tables, arena.epicChests(), SkyBattleLootTier.EPIC);
    addChests(chests, tables, arena.legendaryChests(), SkyBattleLootTier.LEGENDARY);
    return List.copyOf(chests);
  }

  private void addChests(List<ArenaLootChest> chests,
      Map<SkyBattleLootTier, SkyBattleLootTable> tables, List<ArenaPoint> points,
      SkyBattleLootTier tier) {
    SkyBattleLootTable table = tables == null ? null : tables.get(tier);
    List<ArenaLootEntry> entries = table == null ? List.of() : table.entries();
    int rolls = table == null ? 1 : table.rolls();
    String title = language.text("chest." + tier.fileName());
    for (ArenaPoint point : points) {
      chests.add(new ArenaLootChest(point, entries, ArenaLootPlacementMode.AUTO,
          false, false, 0L, 0L, rolls, rolls, title, tier.splitStacks(), tier.chestMaterial()));
    }
  }

  private ArenaResourcePackConfig resourcePack() {
    if (!global.resourcePack().enabled() || plugin.getResource(RESOURCE_PACK_PATH) == null) {
      return ArenaResourcePackConfig.disabled();
    }
    return new ArenaResourcePackConfig(true, plugin, RESOURCE_PACK_PATH,
        global.resourcePack().required(), global.resourcePack().prompt(),
        global.resourcePack().publicUrlBase());
  }

  private ArenaScoreboardConfig scoreboard(SkyBattleMode mode) {
    return new ArenaScoreboardConfig(
        true,
        language.text("arena.scoreboard-title"),
        language.list("arena.scoreboard-lines." + mode.key()));
  }

  private ArenaBossBarConfig bossBar() {
    // Countdown progress while caged, then the round timer while the game runs.
    return new ArenaBossBarConfig(true, language.text("arena.bossbar"), BarColor.BLUE,
        BarStyle.SEGMENTED_10, 1.0, true);
  }

  private ArenaTitleConfig titles() {
    ArenaTitleFrame teleport = new ArenaTitleFrame(language.text("arena.title.teleport"),
        language.text("arena.title.teleport-sub"), Duration.ofMillis(200), Duration.ofMillis(2200),
        Duration.ofMillis(300));
    ArenaTitleFrame started = new ArenaTitleFrame(language.text("arena.title.started"),
        language.text("arena.title.started-sub"), Duration.ZERO, Duration.ofMillis(1400),
        Duration.ofMillis(400));
    ArenaTitleFrame stopped = new ArenaTitleFrame(language.text("arena.title.stopped"),
        language.text("arena.title.stopped-sub"), Duration.ofMillis(200), Duration.ofMillis(1600),
        Duration.ofMillis(400));
    return new ArenaTitleConfig(true, teleport, ArenaTitleFrame.empty(), started, stopped);
  }

  private ArenaMessages messages() {
    return new ArenaMessages(
        language.lines("arena.messages.created"),
        language.lines("arena.messages.teleport"),
        language.text("arena.messages.countdown"),
        language.lines("arena.messages.started"),
        language.lines("arena.messages.ended"),
        language.lines("arena.messages.destroyed"),
        language.text("arena.messages.death-generic"),
        language.text("arena.messages.death-player"),
        language.text("arena.messages.death-tnt"),
        language.text("arena.messages.death-creeper"),
        language.text("arena.messages.death-potion"));
  }
}
