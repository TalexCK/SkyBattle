package com.talexck.skybattle.game;

import java.util.Locale;
import org.bukkit.Material;

public enum SkyBattleLootTier {
  COMMON("common", Material.CHEST),
  UNCOMMON("uncommon", Material.WAXED_COPPER_CHEST),
  RARE("rare", Material.WAXED_EXPOSED_COPPER_CHEST),
  EPIC("epic", Material.WAXED_WEATHERED_COPPER_CHEST),
  LEGENDARY("legendary", Material.WAXED_OXIDIZED_COPPER_CHEST);

  private final String fileName;
  private final Material chestMaterial;

  SkyBattleLootTier(String fileName, Material chestMaterial) {
    this.fileName = fileName;
    this.chestMaterial = chestMaterial;
  }

  public String fileName() {
    return fileName;
  }

  public String chestKey() {
    return fileName + "chest";
  }

  public Material chestMaterial() {
    return chestMaterial;
  }

  public String chestDisplayName() {
    return switch (this) {
      case COMMON -> "SkyBattle Common Chest";
      case UNCOMMON -> "SkyBattle Uncommon Chest";
      case RARE -> "SkyBattle Rare Chest";
      case EPIC -> "SkyBattle Epic Chest";
      case LEGENDARY -> "SkyBattle Legendary Chest";
    };
  }

  public boolean splitStacks() {
    return this == EPIC || this == LEGENDARY;
  }

  public static SkyBattleLootTier fromFileName(String name) {
    return valueOf(name.toUpperCase(Locale.ROOT));
  }
}
