package com.talexck.skybattle.config;

import com.talexck.minigamelib.api.arena.ArenaPoint;

import java.util.List;

public record SkyBattleGlobalConfig(
    int countdownSeconds,
    String lobbyWorldName,
    ArenaPoint lobbySpawnPoint,
    String lobbyScoreboardTitle,
    List<String> lobbyScoreboardLines,
    String returnWorldName,
    ArenaPoint returnPoint,
    int maxPlayers,
    int teamSize,
    double defaultInitialBorderRadius,
    boolean saveWorldOnUnload) {

  public SkyBattleGlobalConfig {
    lobbyScoreboardTitle = lobbyScoreboardTitle == null ? "" : lobbyScoreboardTitle;
    lobbyScoreboardLines =
        lobbyScoreboardLines == null ? List.of() : List.copyOf(lobbyScoreboardLines);
  }
}
