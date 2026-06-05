package com.talexck.skybattle.game;

import com.talexck.minigamelib.api.arena.ArenaLootEntry;

import java.util.List;

public record SkyBattleLootTable(
    List<ArenaLootEntry> entries) {

  public SkyBattleLootTable {
    entries = List.copyOf(entries);
  }
}
