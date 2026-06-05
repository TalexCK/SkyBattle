package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.minigamelib.api.arena.ArenaItemEnchantment;
import com.talexck.minigamelib.api.arena.ArenaItemFactory;
import com.talexck.minigamelib.api.arena.ArenaItemMode;
import com.talexck.minigamelib.api.arena.ArenaLootEntry;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SkyBattleLootTableLoader {

  private final JavaPlugin plugin;

  public SkyBattleLootTableLoader(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public Map<SkyBattleLootTier, SkyBattleLootTable> load() {
    saveDefaultLootFiles();
    Map<SkyBattleLootTier, SkyBattleLootTable> tables =
        new EnumMap<>(SkyBattleLootTier.class);
    for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
      File file = new File(plugin.getDataFolder(), "loot/" + tier.fileName() + ".yml");
      tables.put(tier, loadTier(file));
    }
    return Map.copyOf(tables);
  }

  private void saveDefaultLootFiles() {
    for (SkyBattleLootTier tier : SkyBattleLootTier.values()) {
      plugin.saveResource("loot/" + tier.fileName() + ".yml", false);
    }
  }

  private SkyBattleLootTable loadTier(File file) {
    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
    List<Map<?, ?>> entries = yaml.getMapList("variants");
    List<ArenaLootEntry> loot = new ArrayList<>();
    for (Map<?, ?> entry : entries) {
      loot.add(toLootEntry(entry));
    }
    return new SkyBattleLootTable(loot);
  }

  private ArenaLootEntry toLootEntry(Map<?, ?> variant) {
    List<ArenaItemEntry> items = new ArrayList<>();
    Object rawItems = variant.get("items");
    if (!(rawItems instanceof List<?> itemList)) {
      throw new IllegalArgumentException("loot variant 缺少 items 列表");
    }
    for (Object rawItem : itemList) {
      if (!(rawItem instanceof Map<?, ?> itemSection)) {
        throw new IllegalArgumentException("loot variant item 必须是对象");
      }
      items.add(toItem(itemSection));
    }
    return new ArenaLootEntry(
        items,
        number(variant, "weight", 1.0),
        integer(variant, "earliest-generation-round", 0));
  }

  private ArenaItemEntry toItem(Map<?, ?> section) {
    String alias = string(section, "alias", "");
    int amount = integer(section, "amount", 1);
    String name = string(section, "name", alias.isBlank() ? "" : defaultAliasName(alias));
    return alias.isBlank()
        ? materialItem(section, name, amount)
        : aliasItem(alias, name, amount, section);
  }

  private ArenaItemEntry aliasItem(String alias, String name, int amount, Map<?, ?> section) {
    return switch (alias.toLowerCase(Locale.ROOT)) {
      case "timed_orb_of_harming" -> SkyBattleItems.harmingOrb(name, amount, false);
      case "quick_timed_orb_of_poison" -> SkyBattleItems.poisonOrb(name, amount, true);
      case "orb_of_cleansing" -> SkyBattleItems.cleansingOrb(name, amount);
      case "spark_of_levitation" -> SkyBattleItems.levitationSpark(name, amount);
      case "spark_of_regeneration" -> SkyBattleItems.regenerationSpark(name, amount);
      default -> materialItem(section, name, amount);
    };
  }

  private String defaultAliasName(String alias) {
    return switch (alias.toLowerCase(Locale.ROOT)) {
      case "timed_orb_of_harming" -> "瞬间伤害球";
      case "quick_timed_orb_of_poison" -> "快速中毒球";
      case "orb_of_cleansing" -> "净化宝珠";
      case "spark_of_levitation" -> "飘浮火花";
      case "spark_of_regeneration" -> "生命恢复火花";
      default -> alias;
    };
  }

  private ArenaItemEntry materialItem(Map<?, ?> section, String name, int amount) {
    Material material = Material.matchMaterial(string(section, "material", ""));
    if (material == null) {
      throw new IllegalArgumentException("未知物品材质: " + section.get("material"));
    }
    ArenaItemMode mode = ArenaItemMode.valueOf(
        string(section, "mode", ArenaItemMode.DEFAULT.name()).toUpperCase(Locale.ROOT));
    List<ArenaItemEnchantment> enchantments = new ArrayList<>();
    Object rawEnchantments = section.get("enchantments");
    if (rawEnchantments instanceof Map<?, ?> enchantmentMap) {
      for (Map.Entry<?, ?> entry : enchantmentMap.entrySet()) {
        enchantments.add(new ArenaItemEnchantment(String.valueOf(entry.getKey()),
            integerValue(entry.getValue(), 1)));
      }
    }
    return ArenaItemFactory.item(name, material, amount, mode, enchantments, material == Material.TNT);
  }

  private String string(Map<?, ?> map, String key, String fallback) {
    Object value = map.get(key);
    return value == null ? fallback : String.valueOf(value);
  }

  private int integer(Map<?, ?> map, String key, int fallback) {
    Object value = map.get(key);
    return value == null ? fallback : integerValue(value, fallback);
  }

  private int integerValue(Object value, int fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private double number(Map<?, ?> map, String key, double fallback) {
    Object value = map.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return Double.parseDouble(String.valueOf(value));
  }
}
