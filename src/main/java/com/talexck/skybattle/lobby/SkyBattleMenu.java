package com.talexck.skybattle.lobby;

import com.talexck.minigamelib.api.arena.ArenaStatus;
import com.talexck.minigamelib.api.queue.QueueService;
import com.talexck.minigamelib.api.queue.QueueSettings;
import com.talexck.skybattle.config.SkyBattleLanguage;
import com.talexck.skybattle.game.SkyBattleMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Lobby game selector: a compass in the hotbar opens a small menu with one button per mode. The
 * buttons show queue size and running games, and join/leave the mode's queue on click.
 */
public final class SkyBattleMenu implements Listener {

  private static final int[] MODE_SLOTS = {11, 15};
  private static final int LEAVE_SLOT = 22;

  private final SkyBattleLanguage language;
  private final QueueService queues;
  private final Supplier<List<SkyBattleMode>> availableModes;
  private final Function<SkyBattleMode, Long> runningGames;
  private final NamespacedKey selectorKey;

  public SkyBattleMenu(JavaPlugin plugin, SkyBattleLanguage language, QueueService queues,
      Supplier<List<SkyBattleMode>> availableModes, Function<SkyBattleMode, Long> runningGames) {
    this.language = language;
    this.queues = queues;
    this.availableModes = availableModes;
    this.runningGames = runningGames;
    this.selectorKey = new NamespacedKey(plugin, "game_selector");
    Bukkit.getPluginManager().registerEvents(this, plugin);
  }

  /** The hotbar compass that opens the menu. */
  public ItemStack selectorItem() {
    return tagged(new ItemStack(Material.COMPASS), language.text("menu.selector-name"),
        language.lines("menu.selector-lore"), selectorKey);
  }

  public void open(Player player) {
    Holder holder = new Holder();
    Inventory inventory = Bukkit.createInventory(holder, 27, color(language.text("menu.title")));
    holder.inventory = inventory;
    ItemStack filler = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", List.of());
    for (int slot = 0; slot < inventory.getSize(); slot++) {
      inventory.setItem(slot, filler);
    }
    List<SkyBattleMode> modes = availableModes.get();
    QueueSettings current = queues.queueOf(player).orElse(null);
    for (int index = 0; index < SkyBattleMode.values().length && index < MODE_SLOTS.length;
        index++) {
      SkyBattleMode mode = SkyBattleMode.values()[index];
      inventory.setItem(MODE_SLOTS[index], modeButton(mode, modes.contains(mode),
          current != null && current.id().equals(mode.queueId())));
      holder.modes[index] = mode;
    }
    if (current != null) {
      inventory.setItem(LEAVE_SLOT, named(new ItemStack(Material.BARRIER),
          language.text("menu.leave-name"), language.lines("menu.leave-lore")));
    }
    player.openInventory(inventory);
    player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
  }

  private ItemStack modeButton(SkyBattleMode mode, boolean available, boolean queued) {
    Material icon = mode == SkyBattleMode.SOLO ? Material.IRON_SWORD : Material.DIAMOND_SWORD;
    int waiting = queues.members(mode.queueId()).size();
    int max = queues.queues().stream().filter(queue -> queue.id().equals(mode.queueId()))
        .findFirst().map(QueueSettings::maxPlayers).orElse(0);
    int seconds = queues.secondsLeft(mode.queueId());
    List<String> lore = new ArrayList<>();
    for (String line : language.lines("menu.mode-lore." + mode.key())) {
      lore.add(line);
    }
    lore.add("");
    lore.add(language.text("menu.queue-line", "{count}", waiting, "{max}", max));
    lore.add(language.text("menu.games-line", "{count}", runningGames.apply(mode)));
    if (seconds > 0) {
      lore.add(language.text("menu.starting-line", "{seconds}", seconds));
    }
    lore.add("");
    lore.add(language.text(!available ? "menu.click-unavailable"
        : queued ? "menu.click-leave" : "menu.click-join"));
    ItemStack stack = named(new ItemStack(icon),
        language.text("menu.mode-name", "{mode}", language.text("mode." + mode.key())), lore);
    if (queued) {
      ItemMeta meta = stack.getItemMeta();
      meta.setEnchantmentGlintOverride(true);
      stack.setItemMeta(meta);
    }
    return stack;
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof Holder holder)) {
      return;
    }
    event.setCancelled(true);
    if (!(event.getWhoClicked() instanceof Player player)
        || event.getClickedInventory() != event.getView().getTopInventory()) {
      return;
    }
    int slot = event.getRawSlot();
    if (slot == LEAVE_SLOT && queues.queueOf(player).isPresent()) {
      player.closeInventory();
      player.performCommand("skb leave");
      return;
    }
    for (int index = 0; index < MODE_SLOTS.length; index++) {
      if (MODE_SLOTS[index] == slot && holder.modes[index] != null) {
        SkyBattleMode mode = holder.modes[index];
        player.closeInventory();
        boolean queued = queues.queueOf(player).map(queue -> queue.id().equals(mode.queueId()))
            .orElse(false);
        player.performCommand(queued ? "skb leave" : "skb join " + mode.key());
        return;
      }
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (event.getInventory().getHolder() instanceof Holder) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onUseItem(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND || (event.getAction() != Action.RIGHT_CLICK_AIR
        && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
      return;
    }
    ItemStack item = event.getItem();
    if (hasTag(item, selectorKey)) {
      event.setCancelled(true);
      open(event.getPlayer());
    }
  }

  private boolean hasTag(ItemStack item, NamespacedKey key) {
    if (item == null || !item.hasItemMeta()) {
      return false;
    }
    return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
  }

  private ItemStack tagged(ItemStack stack, String name, List<String> lore, NamespacedKey key) {
    ItemStack named = named(stack, name, lore);
    ItemMeta meta = named.getItemMeta();
    meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
    named.setItemMeta(meta);
    return named;
  }

  private ItemStack named(ItemStack stack, String name, List<String> lore) {
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(color(name).decorationIfAbsent(TextDecoration.ITALIC,
        TextDecoration.State.FALSE));
    meta.lore(lore.stream().map(line -> color(line).decorationIfAbsent(TextDecoration.ITALIC,
        TextDecoration.State.FALSE)).toList());
    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
    stack.setItemMeta(meta);
    return stack;
  }

  private static Component color(String text) {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
  }

  public void shutdown() {
    HandlerList.unregisterAll(this);
  }

  /** Counts running games of a mode from arena handles. */
  public static boolean isLive(ArenaStatus status) {
    return status == ArenaStatus.CREATED || status == ArenaStatus.COUNTDOWN
        || status == ArenaStatus.RUNNING;
  }

  private static final class Holder implements InventoryHolder {
    private final SkyBattleMode[] modes = new SkyBattleMode[MODE_SLOTS.length];
    private Inventory inventory;

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }
}
