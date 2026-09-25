package com.talexck.skybattle.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * SkyBattle texts from {@code plugins/SkyBattle/lang/<language>.yml}. Keys missing from the file on
 * disk fall back to the bundled copy, so updating the plugin never shows raw keys.
 */
public final class SkyBattleLanguage {

  private static final String DEFAULT_LANGUAGE = "zh_cn";

  private final JavaPlugin plugin;
  private YamlConfiguration messages;
  private String language = DEFAULT_LANGUAGE;

  public SkyBattleLanguage(JavaPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    plugin.saveDefaultConfig();
    String configured = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
    String resourcePath = "lang/" + configured + ".yml";
    File file = new File(plugin.getDataFolder(), resourcePath);
    if (plugin.getResource(resourcePath) == null && !file.isFile()) {
      plugin.getLogger().warning("Unknown language " + configured + ", using " + DEFAULT_LANGUAGE);
      configured = DEFAULT_LANGUAGE;
      resourcePath = "lang/" + configured + ".yml";
      file = new File(plugin.getDataFolder(), resourcePath);
    }
    if (!file.isFile() && plugin.getResource(resourcePath) != null) {
      plugin.saveResource(resourcePath, false);
    }
    YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
    InputStream bundled = plugin.getResource(resourcePath);
    if (bundled != null) {
      try (InputStreamReader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
        loaded.setDefaults(YamlConfiguration.loadConfiguration(reader));
      } catch (java.io.IOException ignored) {
        // Defaults are a convenience only.
      }
    }
    this.messages = loaded;
    this.language = configured;
  }

  public String language() {
    return language;
  }

  public String text(String key) {
    return messages.getString(key, key);
  }

  /** A list of lines, or {@code [key]} when the key is missing. */
  public List<String> list(String key) {
    List<String> values = messages.getStringList(key);
    return values.isEmpty() ? List.of(key) : values;
  }

  /** A list of lines, empty when the key is missing. */
  public List<String> lines(String key) {
    return messages.getStringList(key);
  }

  public String text(String key, Object... replacements) {
    String value = text(key);
    for (int index = 0; index + 1 < replacements.length; index += 2) {
      value = value.replace(String.valueOf(replacements[index]),
          String.valueOf(replacements[index + 1]));
    }
    return value;
  }
}
