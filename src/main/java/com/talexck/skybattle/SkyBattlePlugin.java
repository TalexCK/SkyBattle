package com.talexck.skybattle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public final class SkyBattlePlugin extends JavaPlugin {
  @Override
  public void onEnable() {
    getLogger().info("SkyBattle Plugin enabled.");
  }

  @Override
  public void onDisable() {
    getLogger().info("SkyBattle Plugin disabled.");
  }

  @Override
  public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
      @NotNull String label, String @NotNull [] args) {
    if (!command.getName().equalsIgnoreCase("skybattle")) {
      return false;
    }

    sender.sendMessage(Component.text("SkyBattle Plugin loaded.", NamedTextColor.GREEN));
    return true;
  }
}
