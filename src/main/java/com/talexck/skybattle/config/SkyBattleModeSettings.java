package com.talexck.skybattle.config;

import com.talexck.minigamelib.api.arena.ArenaItemEntry;
import com.talexck.skybattle.game.SkyBattleMode;

import java.time.Duration;
import java.util.List;

/** Per-mode rules from {@code config.yml -> modes.<mode>}. */
public record SkyBattleModeSettings(
    SkyBattleMode mode,
    boolean enabled,
    int teamSize,
    int minPlayers,
    int maxPlayers,
    int countdownSeconds,
    int queueCountdownSeconds,
    int queueFullCountdownSeconds,
    Duration timeLimit,
    int killScore,
    int outliveScore,
    List<Integer> placementScores,
    double borderDamagePerSecond,
    List<ArenaItemEntry> kit) {

  public SkyBattleModeSettings {
    placementScores = List.copyOf(placementScores);
    kit = List.copyOf(kit);
  }
}
