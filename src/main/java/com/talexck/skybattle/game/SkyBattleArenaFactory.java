package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaActionBarConfig;
import com.talexck.minigamelib.api.arena.ArenaBossBarConfig;
import com.talexck.minigamelib.api.arena.ArenaLayout;
import com.talexck.minigamelib.api.arena.ArenaLootChest;
import com.talexck.minigamelib.api.arena.ArenaLootEntry;
import com.talexck.minigamelib.api.arena.ArenaLootPlacementMode;
import com.talexck.minigamelib.api.arena.ArenaMessages;
import com.talexck.minigamelib.api.arena.ArenaResourcePackConfig;
import com.talexck.minigamelib.api.arena.ArenaScoreboardConfig;
import com.talexck.minigamelib.api.arena.ArenaSettings;
import com.talexck.minigamelib.api.arena.ArenaSoundConfig;
import com.talexck.minigamelib.api.arena.ArenaTemplate;
import com.talexck.minigamelib.api.arena.ArenaTitleConfig;
import com.talexck.minigamelib.api.arena.ArenaVictoryCondition;
import com.talexck.skybattle.config.SkyBattleArenaConfig;
import com.talexck.skybattle.config.SkyBattleGlobalConfig;
import com.talexck.skybattle.config.SkyBattleLanguage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SkyBattleArenaFactory {

  private final SkyBattleGlobalConfig global;
  private final Map<SkyBattleLootTier, SkyBattleLootTable> lootTables;
  private final JavaPlugin plugin;
  private final SkyBattleLanguage language;

  public SkyBattleArenaFactory(JavaPlugin plugin, SkyBattleLanguage language, SkyBattleGlobalConfig global,
      Map<SkyBattleLootTier, SkyBattleLootTable> lootTables) {
    this.plugin = plugin;
    this.language = language;
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
        global.lobbyWorldName(),
        global.lobbySpawnPoint(),
        global.saveWorldOnUnload(),
        scoreboard(),
        ArenaBossBarConfig.disabled(),
        ArenaActionBarConfig.disabled(),
        ArenaTitleConfig.disabled(),
        ArenaSoundConfig.disabled(),
        resourcePack(),
        SkyBattleItems.beginningItems(),
        lootChests(arena),
        arena.initialBoundaryWall(),
        arena.verticalBoundary(),
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
          false, false, 0L, 0L, 1, 1, tier.chestDisplayName(), tier.splitStacks(),
          tier.chestMaterial()));
    }
  }

  private ArenaResourcePackConfig resourcePack() {
    String resourcePath = "resourcepacks/skybattle-items.zip";
    if (plugin.getResource(resourcePath) == null) {
      return ArenaResourcePackConfig.disabled();
    }
    return new ArenaResourcePackConfig(
        true,
        plugin,
        resourcePath,
        plugin.getConfig().getBoolean("resource-pack.required", true),
        language.text("resource-pack.prompt"),
        plugin.getConfig().getString("resource-pack.public-url-base", ""));
  }

  private ArenaScoreboardConfig scoreboard() {
    return new ArenaScoreboardConfig(
        true,
        language.text("arena.scoreboard-title"),
        language.list("arena.scoreboard-lines"));
  }

  private ArenaMessages messages() {
    return new ArenaMessages(
        language.list("arena.messages.created"),
        language.list("arena.messages.teleport"),
        language.text("arena.messages.countdown"),
        language.list("arena.messages.started"),
        language.list("arena.messages.ended"),
        language.list("arena.messages.destroyed"),
        language.text("arena.messages.death-generic"),
        language.text("arena.messages.death-player"),
        language.text("arena.messages.death-tnt"),
        language.text("arena.messages.death-creeper"),
        language.text("arena.messages.death-potion"));
  }
}
