package dev.nighter.celestCombat.listeners;

import dev.nighter.celestCombat.CelestCombat;
import dev.nighter.celestCombat.combat.CombatManager;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ItemRestrictionListener implements Listener {
    private final CelestCombat plugin;
    private final CombatManager combatManager;

    public static String formatItemName(Material material) {
        if (material == null) {
            return "Unknown Item";
        }

        // Convert from UPPERCASE_WITH_UNDERSCORES to Title Case
        String[] words = material.name().split("_");
        StringBuilder formattedName = new StringBuilder();

        for (String word : words) {
            // Capitalize first letter, rest lowercase
            formattedName
                    .append(word.substring(0, 1).toUpperCase())
                    .append(word.substring(1).toLowerCase())
                    .append(" ");
        }

        return formattedName.toString().trim();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (combatManager.isInCombat(player)) {
            List<String> disabledItems = plugin.getConfig().getStringList("combat.disabled_items");

            // Check if the consumed item is in the disabled items list
            if (isItemDisabled(item.getType(), disabledItems)) {
                event.setCancelled(true);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("item", formatItemName(item.getType()));
                placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
                plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (combatManager.isInCombat(player)) {
            List<String> disabledItems = plugin.getConfig().getStringList("combat.disabled_items");

            if (disabledItems.contains("ELYTRA") && player.isGliding()) {
                player.setGliding(false);

                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("player", player.getName());
                placeholders.put("item", "Elytra");
                placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
                plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClickElytra(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        Player player = (Player) event.getWhoClicked();

        if (combatManager.isInCombat(player)) {
            List<String> disabledItems = plugin.getConfig().getStringList("combat.disabled_items");

            if (disabledItems.contains("ELYTRA")) {
                ItemStack clickedItem = event.getCurrentItem();

                // Check if the clicked item is an Elytra or if it's being equipped to the chestplate slot
                if (clickedItem != null && clickedItem.getType() == Material.ELYTRA ||
                        (event.getSlot() == 38 && clickedItem != null)) { // 38 is the chestplate slot

                    event.setCancelled(true);

                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", player.getName());
                    placeholders.put("item", "Elytra");
                    placeholders.put("time", String.valueOf(combatManager.getRemainingCombatTime(player)));
                    plugin.getMessageService().sendMessage(player, "item_use_blocked_in_combat", placeholders);
                }
            }
        }
    }

    private boolean isItemDisabled(Material itemType, List<String> disabledItems) {
        return disabledItems.stream()
                .anyMatch(disabledItem ->
                        itemType.name().equalsIgnoreCase(disabledItem) ||
                                itemType.name().contains(disabledItem)
                );
    }
}