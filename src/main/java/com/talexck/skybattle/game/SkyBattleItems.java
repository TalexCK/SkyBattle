package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.minigamelib.api.arena.ArenaItemEnchantment;
import com.talexck.minigamelib.api.arena.ArenaItemFactory;
import com.talexck.minigamelib.api.arena.ArenaItemMode;
import com.talexck.minigamelib.api.arena.ArenaPotionItemConfig;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;

public final class SkyBattleItems {

  private SkyBattleItems() {
  }

  public static List<ArenaItemEntry> beginningItems() {
    return List.of(
        item("无限方块", Material.WHITE_CONCRETE, 64, ArenaItemMode.INFINITE),
        item("铁胸甲", Material.IRON_CHESTPLATE, 1),
        item("皮革裤子", Material.LEATHER_LEGGINGS, 1),
        item("皮革靴子", Material.LEATHER_BOOTS, 1),
        item("石剑", Material.STONE_SWORD, 1),
        item("弓", Material.BOW, 1),
        item("箭", Material.ARROW, 2),
        enchanted("效率 III 铁镐", Material.IRON_PICKAXE, 1, "efficiency", 3),
        item("牛排", Material.COOKED_BEEF, 8));
  }

  public static ArenaItemEntry item(String name, Material material, int amount) {
    return item(name, material, amount, ArenaItemMode.DEFAULT);
  }

  public static ArenaItemEntry item(String name, Material material, int amount,
      ArenaItemMode mode) {
    return ArenaItemFactory.item(name, material, amount, mode);
  }

  public static ArenaItemEntry enchanted(String name, Material material, int amount,
      String enchantment, int level) {
    return ArenaItemFactory.item(name, material, amount,
        List.of(new ArenaItemEnchantment(enchantment, level)));
  }

  public static ArenaItemEntry harmingOrb(String name, int amount, boolean quick) {
    return potion(name, Material.FIRE_CHARGE, amount, PotionEffectType.INSTANT_DAMAGE, 0,
        Duration.ofSeconds(10), Duration.ofSeconds(1), 3.0);
  }

  public static ArenaItemEntry poisonOrb(String name, int amount, boolean quick) {
    return potion(name, Material.SLIME_BALL, amount, PotionEffectType.POISON, 0,
        Duration.ofSeconds(10), Duration.ofSeconds(4), 3.0);
  }

  public static ArenaItemEntry cleansingOrb(String name, int amount) {
    return potion(name, Material.SNOWBALL, amount, PotionEffectType.REGENERATION, 0,
        Duration.ofSeconds(3), Duration.ofSeconds(3), 3.0);
  }

  public static ArenaItemEntry levitationSpark(String name, int amount) {
    return selfPotion(name, Material.FEATHER, amount, PotionEffectType.LEVITATION, 0,
        Duration.ofSeconds(10));
  }

  public static ArenaItemEntry regenerationSpark(String name, int amount) {
    return selfPotion(name, Material.BLAZE_POWDER, amount, PotionEffectType.REGENERATION, 1,
        Duration.ofSeconds(4));
  }

  private static ArenaItemEntry potion(String name, Material material, int amount,
      PotionEffectType effect, int amplifier, Duration duration, Duration effectDuration,
      double radius) {
    ArenaPotionItemConfig config =
        new ArenaPotionItemConfig(radius, duration, effect, amplifier, effectDuration);
    return new ArenaItemEntry(name, new ItemStack(material), amount, ArenaItemMode.POTION, config);
  }

  private static ArenaItemEntry selfPotion(String name, Material material, int amount,
      PotionEffectType effect, int amplifier, Duration duration) {
    ArenaPotionItemConfig config =
        new ArenaPotionItemConfig(1.0, Duration.ZERO, effect, amplifier, duration);
    return new ArenaItemEntry(name, new ItemStack(material), amount, ArenaItemMode.SELF_POTION,
        config);
  }
}
