package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaItemEnchantment;
import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.minigamelib.api.arena.ArenaItemFactory;
import com.talexck.minigamelib.api.arena.ArenaItemMode;
import com.talexck.minigamelib.api.arena.ArenaPotionItemConfig;
import com.talexck.skybattle.config.SkyBattleConfigException;
import com.talexck.skybattle.config.SkyBattleLanguage;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds Sky Battle items from YAML maps (kits and loot tables). An item is either a plain
 * {@code material} or one of the special {@code alias} items below, whose names/lore come from the
 * language file ({@code items.<alias>}).
 *
 * <pre>
 * team_blocks, team_helmet, team_chestplate, team_leggings, team_boots,
 * timed_orb_of_harming, orb_of_poison, orb_of_slowness, orb_of_cleansing,
 * spark_of_levitation, spark_of_regeneration, spark_of_speed
 * </pre>
 */
public final class SkyBattleItems {

  public static final List<String> ALIASES = List.of("team_blocks", "team_helmet",
      "team_chestplate", "team_leggings", "team_boots", "timed_orb_of_harming", "orb_of_poison",
      "orb_of_slowness", "orb_of_cleansing", "spark_of_levitation", "spark_of_regeneration",
      "spark_of_speed");

  private final SkyBattleLanguage language;

  public SkyBattleItems(SkyBattleLanguage language) {
    this.language = language;
  }

  public ArenaItemEntry parse(Map<?, ?> section) {
    String alias = string(section, "alias", "").toLowerCase(Locale.ROOT);
    if (alias.equals("quick_timed_orb_of_poison")) {
      alias = "orb_of_poison";
    }
    int amount = integer(section, "amount", 1);
    if (amount <= 0) {
      throw new SkyBattleConfigException("item amount must be positive: " + section);
    }
    if (!alias.isBlank()) {
      String name = string(section, "name", language.text("items." + alias + ".name"));
      ArenaItemEntry entry = alias(alias, name, amount);
      if (entry == null) {
        throw new SkyBattleConfigException("unknown item alias: " + alias);
      }
      return entry;
    }
    Material material = Material.matchMaterial(string(section, "material", ""));
    if (material == null || !material.isItem()) {
      throw new SkyBattleConfigException("unknown item material: " + section.get("material"));
    }
    ArenaItemMode mode;
    try {
      mode = ArenaItemMode.valueOf(
          string(section, "mode", ArenaItemMode.DEFAULT.name()).toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new SkyBattleConfigException("unknown item mode: " + section.get("mode"), exception);
    }
    List<ArenaItemEnchantment> enchantments = new ArrayList<>();
    if (section.get("enchantments") instanceof Map<?, ?> enchantmentMap) {
      for (Map.Entry<?, ?> entry : enchantmentMap.entrySet()) {
        enchantments.add(new ArenaItemEnchantment(String.valueOf(entry.getKey()),
            integerValue(entry.getValue(), 1)));
      }
    }
    List<String> lore = new ArrayList<>();
    if (section.get("lore") instanceof List<?> loreList) {
      loreList.forEach(line -> lore.add(String.valueOf(line)));
    }
    try {
      return ArenaItemFactory.item(string(section, "name", ""), material, amount, mode,
          enchantments, material == Material.TNT, splitInLoot(material), lore);
    } catch (IllegalArgumentException exception) {
      throw new SkyBattleConfigException(exception.getMessage(), exception);
    }
  }

  private ArenaItemEntry alias(String alias, String name, int amount) {
    List<String> lore = language.lines("items." + alias + ".lore");
    return switch (alias) {
      case "team_blocks" -> ArenaItemFactory.infiniteOffhandBlock(name, Material.WHITE_CONCRETE,
          64).withLore(lore);
      case "team_helmet" -> ArenaItemFactory.teamLeatherHelmet(name);
      case "team_chestplate" -> ArenaItemFactory.teamLeatherChestplate(name);
      case "team_leggings" -> ArenaItemFactory.teamLeatherLeggings(name);
      case "team_boots" -> ArenaItemFactory.teamLeatherBoots(name);
      // Thrown orbs: burst where they land or when the fuse runs out.
      case "timed_orb_of_harming" -> ArenaItemFactory.orb(name, Material.FIRE_CHARGE, amount,
          orb(PotionEffectType.INSTANT_DAMAGE, 0, 3.0, Duration.ZERO, Duration.ofSeconds(1),
              Duration.ofMillis(1500), false, 1001, "skybattle:timed_orb_of_harming"), lore);
      case "orb_of_poison" -> ArenaItemFactory.orb(name, Material.SLIME_BALL, amount,
          orb(PotionEffectType.POISON, 1, 3.0, Duration.ofSeconds(3), Duration.ofSeconds(4),
              Duration.ofMillis(1000), false, 1002, "skybattle:orb_of_poison"), lore);
      case "orb_of_slowness" -> ArenaItemFactory.orb(name, Material.PRISMARINE_CRYSTALS, amount,
          orb(PotionEffectType.SLOWNESS, 1, 3.5, Duration.ofSeconds(3), Duration.ofSeconds(4),
              Duration.ofMillis(1000), false, 1006, "skybattle:orb_of_slowness"), lore);
      case "orb_of_cleansing" -> ArenaItemFactory.orb(name, Material.SNOWBALL, amount,
          orb(null, 0, 3.5, Duration.ZERO, Duration.ZERO, Duration.ZERO, true, 1003,
              "skybattle:orb_of_cleansing"), lore);
      // Sparks: instant self buffs.
      case "spark_of_levitation" -> ArenaItemFactory.spark(name, Material.FEATHER, amount,
          spark(PotionEffectType.LEVITATION, 4, Duration.ofMillis(2500), 1004,
              "skybattle:spark_of_levitation"), lore);
      case "spark_of_regeneration" -> ArenaItemFactory.spark(name, Material.BLAZE_POWDER, amount,
          spark(PotionEffectType.REGENERATION, 1, Duration.ofSeconds(5), 1005,
              "skybattle:spark_of_regeneration"), lore);
      case "spark_of_speed" -> ArenaItemFactory.spark(name, Material.SUGAR, amount,
          spark(PotionEffectType.SPEED, 1, Duration.ofSeconds(8), 1007,
              "skybattle:spark_of_speed"), lore);
      default -> null;
    };
  }

  private static ArenaPotionItemConfig orb(PotionEffectType effect, int amplifier, double radius,
      Duration cloud, Duration effectDuration, Duration fuse, boolean cleanse, int modelData,
      String model) {
    return new ArenaPotionItemConfig(radius, cloud, effect, amplifier, effectDuration, modelData,
        model, cleanse, true, fuse);
  }

  private static ArenaPotionItemConfig spark(PotionEffectType effect, int amplifier,
      Duration duration, int modelData, String model) {
    return new ArenaPotionItemConfig(1.0, Duration.ZERO, effect, amplifier, duration, modelData,
        model, false, false, Duration.ZERO);
  }

  private static boolean splitInLoot(Material material) {
    return switch (material) {
      case TNT, ENDER_PEARL, CREEPER_SPAWN_EGG, GOLDEN_APPLE, WIND_CHARGE, COBWEB -> true;
      default -> material.getMaxStackSize() == 1;
    };
  }

  private static String string(Map<?, ?> map, String key, String fallback) {
    Object value = map.get(key);
    return value == null ? fallback : String.valueOf(value);
  }

  static int integer(Map<?, ?> map, String key, int fallback) {
    Object value = map.get(key);
    return value == null ? fallback : integerValue(value, fallback);
  }

  static int integerValue(Object value, int fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException exception) {
      throw new SkyBattleConfigException("expected an integer but got: " + value, exception);
    }
  }
}
