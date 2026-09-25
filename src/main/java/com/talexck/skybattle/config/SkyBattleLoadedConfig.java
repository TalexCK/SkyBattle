package com.talexck.skybattle.config;

import java.util.List;

/**
 * @param problems arena files that were skipped or have suspicious data, shown on reload.
 */
public record SkyBattleLoadedConfig(
    SkyBattleGlobalConfig global,
    List<SkyBattleArenaConfig> arenas,
    List<String> problems) {

  public SkyBattleLoadedConfig {
    arenas = List.copyOf(arenas);
    problems = List.copyOf(problems);
  }
}
