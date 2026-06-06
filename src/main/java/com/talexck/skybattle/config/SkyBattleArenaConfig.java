package com.talexck.skybattle.config;

import com.talexck.minigamelib.api.arena.ArenaBoundaryStage;
import com.talexck.minigamelib.api.arena.ArenaBoundaryWall;
import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.arena.ArenaTeamSpawn;
import com.talexck.minigamelib.api.arena.ArenaVerticalBoundary;

import java.util.List;

public record SkyBattleArenaConfig(
    String id,
    String templateWorldName,
    ArenaPoint center,
    double initialBorderRadius,
    ArenaBoundaryWall initialBoundaryWall,
    ArenaVerticalBoundary verticalBoundary,
    List<ArenaBoundaryStage> boundaryStages,
    List<ArenaTeamSpawn> teamSpawns,
    List<ArenaPoint> commonChests,
    List<ArenaPoint> uncommonChests,
    List<ArenaPoint> rareChests,
    List<ArenaPoint> epicChests,
    List<ArenaPoint> legendaryChests) {

  public List<ArenaPoint> allChestPoints() {
    return java.util.stream.Stream.of(
            commonChests,
            uncommonChests,
            rareChests,
            epicChests,
            legendaryChests)
        .flatMap(List::stream)
        .toList();
  }
}
