package com.talexck.skybattle.game;

import java.util.Locale;

public enum SkyBattleLootTier {
  COMMON("common"),
  UNCOMMON("uncommon"),
  RARE("rare"),
  EPIC("epic"),
  LEGENDARY("legendary");

  private final String fileName;

  SkyBattleLootTier(String fileName) {
    this.fileName = fileName;
  }

  public String fileName() {
    return fileName;
  }

  public String chestKey() {
    return fileName + "chest";
  }

  public static SkyBattleLootTier fromFileName(String name) {
    return valueOf(name.toUpperCase(Locale.ROOT));
  }
}
