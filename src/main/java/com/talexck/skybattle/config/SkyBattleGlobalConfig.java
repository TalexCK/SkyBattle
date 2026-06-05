package com.talexck.skybattle.config;

import com.talexck.minigamelib.api.arena.ArenaPoint;

public record SkyBattleGlobalConfig(
    int countdownSeconds,
    String returnWorldName,
    ArenaPoint returnPoint,
    int maxPlayers,
    int teamSize,
    double defaultInitialBorderRadius,
    boolean saveWorldOnUnload) {
}
