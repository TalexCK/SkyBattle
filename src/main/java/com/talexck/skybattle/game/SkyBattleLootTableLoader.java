package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.minigamelib.api.arena.ArenaLootEntry;
import com.talexck.skybattle.config.SkyBattleConfigException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code plugins/SkyBattle/loot/<mode>/<tier>.yml}. Each chest rolls a number of variants
 * ({@code rolls}, default 1) from {@code variants}; a variant can hold several items.
 */
public final class SkyBattleLootTableLoader {

  private final JavaPlugin plugin;
  private final SkyBattleItems items;

  public SkyBattleLootTableLoader(JavaPlugin plugin, SkyBattleItems items) {
    this.plugin = plugin;
    this.items = items;
  }

  public Map<SkyBattleMode, Map<SkyBattleLootTier, SkyBattleLootTable>> load() {
    Map<SkyBattleMode, Map<SkyBattleLootTier, SkyBattleLootTable>> result =
        new EnumMap<>(SkyBattleMode.class);
    for (SkyBattleMode mode : SkyBattleMode.values()) {
      Map<SkyBattleLootTier, SkyBattleLootTable> tables = new EnumMap<>(SkyBattleLootTier.class);
      for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
        String path = "loot/" + mode.key() + "/" + tier.fileName() + ".yml";
        File file = new File(plugin.getDataFolder(), path);
        if (!file.isFile()) {
          plugin.saveResource(path, false);
        }
        try {
          tables.put(tier, loadTier(file));
        } catch (RuntimeException exception) {
          throw new SkyBattleConfigException(path + ": " + exception.getMessage(), exception);
        }
      }
      result.put(mode, Map.copyOf(tables));
    }
    return Map.copyOf(result);
  }

  private SkyBattleLootTable loadTier(File file) {
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    List<ArenaLootEntry> loot = new ArrayList<>();
    for (Map<?, ?> entry : yaml.getMapList("variants")) {
      loot.add(toLootEntry(entry));
    }
    int rolls = Math.max(1, yaml.getInt("rolls", 1));
    return new SkyBattleLootTable(loot, rolls);
  }

  private ArenaLootEntry toLootEntry(Map<?, ?> variant) {
    if (!(variant.get("items") instanceof List<?> itemList)) {
      throw new SkyBattleConfigException("loot variant is missing its items list");
    }
    List<ArenaItemEntry> entries = new ArrayList<>();
    for (Object rawItem : itemList) {
      if (!(rawItem instanceof Map<?, ?> itemSection)) {
        throw new SkyBattleConfigException("loot items must be maps");
      }
      entries.add(items.parse(itemSection));
    }
    Object weight = variant.get("weight");
    double parsedWeight = weight instanceof Number number ? number.doubleValue()
        : weight == null ? 1.0 : Double.parseDouble(String.valueOf(weight));
    return new ArenaLootEntry(entries, parsedWeight,
        SkyBattleItems.integer(variant, "earliest-generation-round", 0));
  }
}
