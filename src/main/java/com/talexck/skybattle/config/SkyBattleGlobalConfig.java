package com.talexck.skybattle.config;

import com.talexck.minigamelib.api.arena.ArenaPoint;
import com.talexck.minigamelib.api.stats.StatsSettings;
import com.talexck.skybattle.game.SkyBattleMode;

import java.util.List;
import java.util.Map;

public record SkyBattleGlobalConfig(
    String lobbyWorldName,
    ArenaPoint lobbySpawnPoint,
    boolean lobbyItems,
    String returnWorldName,
    ArenaPoint returnPoint,
    double defaultInitialBorderRadius,
    boolean saveWorldOnUnload,
    StatsSettings statsSettings,
    Map<SkyBattleMode, SkyBattleModeSettings> modes,
    SkyBattleResourcePackSettings resourcePack) {

  public SkyBattleGlobalConfig {
    modes = Map.copyOf(modes);
  }

  public SkyBattleModeSettings mode(SkyBattleMode mode) {
    return modes.get(mode);
  }

  public List<SkyBattleModeSettings> enabledModes() {
    return java.util.Arrays.stream(SkyBattleMode.values()).map(modes::get)
        .filter(settings -> settings != null && settings.enabled()).toList();
  }
}
