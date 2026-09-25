package com.talexck.skybattle.game;

import java.util.Locale;
import java.util.Optional;

/**
 * MCC Island style Sky Battle modes. Each mode has its own kit, loot tables, team size, scoring
 * and queue; an arena map is built for exactly one mode.
 */
public enum SkyBattleMode {
  /** Every player for themselves, 8 islands with one spawn each. */
  SOLO("solo", 1, 8),
  /** Eight teams of four, each spawning on the corners of a 3x3 platform. */
  QUADS("quads", 4, 8);

  private final String key;
  private final int defaultTeamSize;
  private final int islands;

  SkyBattleMode(String key, int defaultTeamSize, int islands) {
    this.key = key;
    this.defaultTeamSize = defaultTeamSize;
    this.islands = islands;
  }

  public String key() {
    return key;
  }

  public int defaultTeamSize() {
    return defaultTeamSize;
  }

  /** Spawn points marked per team during setup. */
  public int spawnsPerTeam() {
    return defaultTeamSize;
  }

  /** Maximum number of islands / teams a map can have. */
  public int islands() {
    return islands;
  }

  public String queueId() {
    return "skybattle-" + key;
  }

  public static Optional<SkyBattleMode> fromKey(String key) {
    if (key == null) {
      return Optional.empty();
    }
    return switch (key.toLowerCase(Locale.ROOT)) {
      case "solo", "solos", "single" -> Optional.of(SOLO);
      case "quads", "quad", "team", "teams", "squads" -> Optional.of(QUADS);
      default -> Optional.empty();
    };
  }
}
