package dev.nighter.celesCombat.combat;

import dev.nighter.celesCombat.CelesCombat;
import dev.nighter.celesCombat.Scheduler;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatManager {
    private final CelesCombat plugin;
    @Getter private final Map<UUID, Long> playersInCombat;
    private final Map<UUID, Scheduler.Task> combatTasks;
    private final Map<UUID, Scheduler.Task> countdownTasks;
    private final Map<UUID, UUID> combatOpponents;

    // Ender pearl cooldown map
    @Getter private final Map<UUID, Long> enderPearlCooldowns;

    public CombatManager(CelesCombat plugin) {
        this.plugin = plugin;
        this.playersInCombat = new ConcurrentHashMap<>();
        this.combatTasks = new ConcurrentHashMap<>();
        this.countdownTasks = new ConcurrentHashMap<>();
        this.combatOpponents = new ConcurrentHashMap<>();
        this.enderPearlCooldowns = new ConcurrentHashMap<>();
    }

    public void tagPlayer(Player player, Player attacker) {
        if (player == null || attacker == null) return;

        UUID playerUUID = player.getUniqueId();
        int combatTime = plugin.getConfig().getInt("combat.duration", 20);

        boolean alreadyInCombatWithAttacker =
                isInCombat(player) &&
                        attacker.getUniqueId().equals(combatOpponents.get(playerUUID));

        if (alreadyInCombatWithAttacker) {
            long currentEndTime = playersInCombat.get(playerUUID);
            long newEndTime = System.currentTimeMillis() + (combatTime * 1000L);

            if (newEndTime <= currentEndTime) {
                return;
            }
        }

        combatOpponents.put(playerUUID, attacker.getUniqueId());

        if (isInCombat(player)) {
            Scheduler.Task existingTask = combatTasks.get(playerUUID);
            if (existingTask != null) {
                existingTask.cancel();
            }

            Scheduler.Task existingCountdownTask = countdownTasks.get(playerUUID);
            if (existingCountdownTask != null) {
                existingCountdownTask.cancel();
            }
        }

        playersInCombat.put(playerUUID, System.currentTimeMillis() + (combatTime * 1000L));

        // Use the entity's scheduler for player-specific tasks
        Scheduler.Task task = Scheduler.runEntityTaskLater(player, () -> {
            removeFromCombat(player);
        }, combatTime * 20L);

        combatTasks.put(playerUUID, task);
        startCountdownTimer(player);
    }

    private void startCountdownTimer(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        Scheduler.Task existingTask = countdownTasks.get(playerUUID);
        if (existingTask != null) {
            existingTask.cancel();
        }

        Scheduler.Task countdownTask = Scheduler.runEntityTaskTimer(player, () -> {
            if (player.isOnline() && isInCombat(player)) {
                int remainingTime = getRemainingCombatTime(player);

                // Only show countdown if time is > 0 to avoid showing 0
                if (remainingTime > 0) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("player", player.getName());
                    placeholders.put("time", String.valueOf(remainingTime));
                    plugin.getMessageService().sendMessage(player, "combat_countdown", placeholders);
                }
            } else {
                Scheduler.Task task = countdownTasks.get(playerUUID);
                if (task != null) {
                    task.cancel();
                    countdownTasks.remove(playerUUID);
                }
            }
        }, 0L, 20L); // Start immediately (0L) and run every second (20L)

        countdownTasks.put(playerUUID, countdownTask);
    }

    public void punishCombatLogout(Player player) {
        if (player == null || !player.isOnline()) return;

        // Store player location for effects before killing them
        final Location location = player.getLocation().clone();

        // Schedule the kill in the player's thread
        Scheduler.runEntityTask(player, () -> {
            // Kill the player
            player.setHealth(0);

            // Schedule effects in the location's thread
            applyLogoutEffects(location);

            // Remove from combat in the player's thread
            Scheduler.runEntityTaskLater(player, () -> {
                removeFromCombat(player);

                // Schedule respawn in the player's thread
                Scheduler.runEntityTaskLater(player, () -> {
                    if (player.isOnline()) {
                        player.spigot().respawn();
                    }
                }, 1L); // Slight delay to ensure death processing is complete
            }, 1L); // Slight delay to ensure combat state is properly cleared
        });
    }

    private void applyLogoutEffects(Location location) {
        if (location == null) return;

        // Schedule location-based effects with the location scheduler
        Scheduler.runLocationTask(location, () -> {
            if (plugin.getConfig().getBoolean("combat.logout_effects.lightning", true)) {
                location.getWorld().strikeLightningEffect(location);
            }

            String soundName = plugin.getConfig().getString("combat.logout_effects.sound", "ENTITY_LIGHTNING_BOLT_THUNDER");
            // Only play sound if not set to "NONE"
            if (soundName != null && !soundName.isEmpty() && !soundName.equalsIgnoreCase("NONE")) {
                try {
                    Sound sound = Sound.valueOf(soundName);
                    location.getWorld().playSound(location, sound, 1.0F, 1.0F);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid sound effect in config: " + soundName);
                }
            }
        });
    }

    public void removeFromCombat(Player player) {
        if (player == null) return;

        UUID playerUUID = player.getUniqueId();

        playersInCombat.remove(playerUUID);
        combatOpponents.remove(playerUUID);

        Scheduler.Task task = combatTasks.remove(playerUUID);
        if (task != null) {
            task.cancel();
        }

        Scheduler.Task countdownTask = countdownTasks.remove(playerUUID);
        if (countdownTask != null) {
            countdownTask.cancel();
        }
    }

    public Player getCombatOpponent(Player player) {
        if (player == null || !isInCombat(player)) return null;

        UUID opponentUUID = combatOpponents.get(player.getUniqueId());
        if (opponentUUID == null) return null;

        return Bukkit.getPlayer(opponentUUID);
    }

    public boolean isInCombat(Player player) {
        if (player == null) return false;

        UUID playerUUID = player.getUniqueId();

        if (!playersInCombat.containsKey(playerUUID)) {
            return false;
        }

        long combatEndTime = playersInCombat.get(playerUUID);

        if (System.currentTimeMillis() > combatEndTime) {
            removeFromCombat(player);
            return false;
        }

        return true;
    }

    public int getRemainingCombatTime(Player player) {
        if (!isInCombat(player)) return 0;

        long endTime = playersInCombat.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();

        // Math.ceil for the countdown to show 20, 19, ... 2, 1 instead of 19, 18, ... 1, 0
        return (int) Math.ceil(Math.max(0, (endTime - currentTime) / 1000.0));
    }

    public void updateMutualCombat(Player player1, Player player2) {
        if (player1 != null && player1.isOnline() && player2 != null && player2.isOnline()) {
            tagPlayer(player1, player2);
            tagPlayer(player2, player1);
        }
    }

    // Ender pearl cooldown methods
    public void setEnderPearlCooldown(Player player) {
        if (player == null) return;

        // Only set cooldown if enabled in config
        if (!plugin.getConfig().getBoolean("enderpearl_cooldown.enabled", true)) {
            return;
        }

        int cooldownTime = plugin.getConfig().getInt("enderpearl_cooldown.duration", 10);
        enderPearlCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (cooldownTime * 1000L));
    }

    public boolean isEnderPearlOnCooldown(Player player) {
        if (player == null) return false;

        // If ender pearl cooldowns are disabled in config, always return false
        if (!plugin.getConfig().getBoolean("enderpearl_cooldown.enabled", true)) {
            return false;
        }

        UUID playerUUID = player.getUniqueId();
        if (!enderPearlCooldowns.containsKey(playerUUID)) {
            return false;
        }

        long cooldownEndTime = enderPearlCooldowns.get(playerUUID);
        if (System.currentTimeMillis() > cooldownEndTime) {
            enderPearlCooldowns.remove(playerUUID);
            return false;
        }

        return true;
    }

    public int getRemainingEnderPearlCooldown(Player player) {
        if (player == null || !isEnderPearlOnCooldown(player)) return 0;

        long endTime = enderPearlCooldowns.get(player.getUniqueId());
        long currentTime = System.currentTimeMillis();

        return (int) Math.ceil(Math.max(0, (endTime - currentTime) / 1000.0));
    }

    public void shutdown() {
        combatTasks.values().forEach(Scheduler.Task::cancel);
        combatTasks.clear();

        countdownTasks.values().forEach(Scheduler.Task::cancel);
        countdownTasks.clear();

        playersInCombat.clear();
        combatOpponents.clear();
        enderPearlCooldowns.clear();
    }
}