package com.talexck.skybattle.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class SkyBattleLanguage {

  private static final String DEFAULT_LANGUAGE = "zh_cn";

  private final JavaPlugin plugin;
  private YamlConfiguration messages;

  public SkyBattleLanguage(JavaPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    plugin.saveDefaultConfig();
    String language = plugin.getConfig().getString("language", DEFAULT_LANGUAGE);
    String resourcePath = "lang/" + language + ".yml";
    plugin.saveResource(resourcePath, false);
    this.messages = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), resourcePath));
  }

  public String text(String key) {
    return messages.getString(key, key);
  }

  public List<String> list(String key) {
    List<String> values = messages.getStringList(key);
    return values.isEmpty() ? List.of(key) : values;
  }

  public String text(String key, Object... replacements) {
    String value = text(key);
    for (int index = 0; index + 1 < replacements.length; index += 2) {
      value = value.replace(String.valueOf(replacements[index]), String.valueOf(replacements[index + 1]));
    }
    return value;
  }
}
