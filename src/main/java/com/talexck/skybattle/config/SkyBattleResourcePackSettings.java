package com.talexck.skybattle.config;

/**
 * @param enabled send the bundled pack through MinigameLib's HTTP server (or {@code url}).
 * @param required kick players that decline the pack.
 * @param prompt text shown in the client's pack prompt.
 * @param publicUrlBase public base URL when the pack is served by a proxy/CDN in front of
 *     MinigameLib; blank uses MinigameLib's built-in server address.
 */
public record SkyBattleResourcePackSettings(boolean enabled, boolean required, String prompt,
    String publicUrlBase) {
}
