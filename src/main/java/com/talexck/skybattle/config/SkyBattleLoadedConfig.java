package com.talexck.skybattle.config;

import java.util.List;

public record SkyBattleLoadedConfig(
    SkyBattleGlobalConfig global,
    List<SkyBattleArenaConfig> arenas) {
}
