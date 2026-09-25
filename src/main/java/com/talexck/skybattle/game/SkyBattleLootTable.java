package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaLootEntry;

import java.util.List;

/**
 * @param rolls how many different variants one chest of this tier receives.
 */
public record SkyBattleLootTable(List<ArenaLootEntry> entries, int rolls) {

  public SkyBattleLootTable {
    entries = List.copyOf(entries);
    rolls = Math.max(1, rolls);
  }
}
