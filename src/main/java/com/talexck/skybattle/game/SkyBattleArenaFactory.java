package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaActionBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLootChest;
import com.talexck.minigamelib.api.arena.ArenaLootEntry;
import com.talexck.minigamelib.api.arena.ArenaLootPlacementMode;
import com.talexck.minigamelib.api.arena.ArenaMessages;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaSoundConfig;
import com.talexck.minigamelib.api.arena.ArenaTemplate;
import com.talexck.minigamelib.api.arena.ArenaTitleConfig;
import com.talexck.minigamelib.api.arena.ArenaVictoryCondition;
import com.talexck.skybattle.config.SkyBattleArenaConfig;
import com.talexck.skybattle.config.SkyBattleGlobalConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SkyBattleArenaFactory {

  private final SkyBattleGlobalConfig global;
  private final Map<SkyBattleLootTier, SkyBattleLootTable> lootTables;

  public SkyBattleArenaFactory(SkyBattleGlobalConfig global,
      Map<SkyBattleLootTier, SkyBattleLootTable> lootTables) {
    this.global = global;
    this.lootTables = lootTables;
  }

  public ArenaTemplate createTemplate(SkyBattleArenaConfig arena) {
    ArenaLayout layout = new ArenaLayout(
        arena.teamSpawns().stream().flatMap(spawn -> spawn.spawnPoints().stream()).toList(),
        arena.teamSpawns(),
        arena.allChestPoints(),
        arena.center(),
        arena.initialBorderRadius());

    ArenaSettings settings = new ArenaSettings(
        global.countdownSeconds(),
        global.returnWorldName(),
        global.returnPoint(),
        global.saveWorldOnUnload(),
        scoreboard(),
        ArenaBossBarConfig.disabled(),
        ArenaActionBarConfig.disabled(),
        ArenaTitleConfig.disabled(),
        ArenaSoundConfig.disabled(),
        null,
        SkyBattleItems.beginningItems(),
        lootChests(arena),
        arena.initialBoundaryWall(),
        arena.boundaryStages(),
        ArenaVictoryCondition.OTHER_TEAMS_ALL_FAILED,
        messages());

    return new ArenaTemplate(arena.id(), arena.templateWorldName(), layout, settings, null);
  }

  private List<ArenaLootChest> lootChests(SkyBattleArenaConfig arena) {
    List<ArenaLootChest> chests = new ArrayList<>();
    addChests(chests, arena.commonChests(), SkyBattleLootTier.COMMON);
    addChests(chests, arena.uncommonChests(), SkyBattleLootTier.UNCOMMON);
    addChests(chests, arena.rareChests(), SkyBattleLootTier.RARE);
    addChests(chests, arena.epicChests(), SkyBattleLootTier.EPIC);
    addChests(chests, arena.legendaryChests(), SkyBattleLootTier.LEGENDARY);
    return List.copyOf(chests);
  }

  private void addChests(List<ArenaLootChest> chests,
      List<com.talexck.minigamelib.api.arena.ArenaPoint> points, SkyBattleLootTier tier) {
    SkyBattleLootTable table = lootTables.get(tier);
    List<ArenaLootEntry> entries = table == null ? List.of() : table.entries();
    for (com.talexck.minigamelib.api.arena.ArenaPoint point : points) {
      chests.add(new ArenaLootChest(point, entries, ArenaLootPlacementMode.AUTO,
          false, false, 0L, 0L, 1, 1));
    }
  }

  private ArenaScoreboardConfig scoreboard() {
    return new ArenaScoreboardConfig(true, "Sky Battle", List.of(
        "目标：成为最后存活的队伍",
        "队伍：8 x 4",
        "时长：5 分钟"));
  }

  private ArenaMessages messages() {
    return new ArenaMessages(
        List.of("Sky Battle arena 已创建。"),
        List.of("正在传送到 Sky Battle arena。"),
        "Sky Battle 将在 %seconds% 秒后开始。",
        List.of("Sky Battle 开始！"),
        List.of("Sky Battle 已结束。"),
        List.of("Sky Battle arena 已销毁。"));
  }
}
