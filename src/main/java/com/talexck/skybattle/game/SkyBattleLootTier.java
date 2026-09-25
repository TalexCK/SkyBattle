package com.talexck.skybattle.game;

import org.bukkit.Color;
import org.bukkit.Material;

/** Colour-coded loot chest tiers. Chest blocks use vanilla (copper) chest looks. */
public enum SkyBattleLootTier {
  COMMON("common", Material.CHEST, "&f", Color.WHITE),
  UNCOMMON("uncommon", Material.WAXED_COPPER_CHEST, "&a", Color.LIME),
  RARE("rare", Material.WAXED_EXPOSED_COPPER_CHEST, "&9", Color.fromRGB(0x5555FF)),
  EPIC("epic", Material.WAXED_WEATHERED_COPPER_CHEST, "&5", Color.PURPLE),
  LEGENDARY("legendary", Material.WAXED_OXIDIZED_COPPER_CHEST, "&6", Color.ORANGE);

  private final String fileName;
  private final Material chestMaterial;
  private final String legacyColor;
  private final Color markerColor;

  SkyBattleLootTier(String fileName, Material chestMaterial, String legacyColor,
      Color markerColor) {
    this.fileName = fileName;
    this.chestMaterial = chestMaterial;
    this.legacyColor = legacyColor;
    this.markerColor = markerColor;
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

  public String legacyColor() {
    return legacyColor;
  }

  public Color markerColor() {
    return markerColor;
  }

  /** Epic and legendary chests lay every item out in its own slot. */
  public boolean splitStacks() {
    return this == EPIC || this == LEGENDARY;
  }
}
