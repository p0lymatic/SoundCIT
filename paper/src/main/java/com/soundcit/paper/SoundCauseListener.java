package com.soundcit.paper;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * The actions worth telling clients about: the ones that make a sound whose cause a client cannot
 * work out for itself, above all when another player did them.
 *
 * <p>Everything runs at {@link EventPriority#MONITOR} and never changes the event — the plugin only
 * observes.</p>
 */
public final class SoundCauseListener implements Listener {
    private final SoundCITPlugin plugin;

    public SoundCauseListener(SoundCITPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            plugin.sendCause(player, "hit", player.getInventory().getItemInMainHand());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() == null) {
            return;
        }
        ItemStack stack = event.getHand() == EquipmentSlot.OFF_HAND
                ? event.getPlayer().getInventory().getItemInOffHand()
                : event.getPlayer().getInventory().getItemInMainHand();
        plugin.sendCause(event.getPlayer(), "use", stack);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        plugin.sendCause(event.getPlayer(), "eat", event.getItem());
        plugin.sendCause(event.getPlayer(), "drink", event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakBlock(BlockBreakEvent event) {
        plugin.sendCause(event.getPlayer(), "break", event.getPlayer().getInventory().getItemInMainHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceBlock(BlockPlaceEvent event) {
        plugin.sendCause(event.getPlayer(), "place", event.getItemInHand());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) {
            plugin.sendCause(player, "shoot", event.getBow());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            plugin.sendCause(player, "throw", player.getInventory().getItemInMainHand());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        plugin.sendCause(event.getPlayer(), "fish_cast", event.getPlayer().getInventory().getItemInMainHand());
        plugin.sendCause(event.getPlayer(), "fish_retrieve", event.getPlayer().getInventory().getItemInMainHand());
    }

    /**
     * A totem firing is the clearest case for this plugin: the item is consumed before the client
     * is told anything, so without the server the sound cannot be attributed at all.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack totem = player.getInventory().getItemInOffHand();
            if (totem.getType().isAir()) {
                totem = player.getInventory().getItemInMainHand();
            }
            plugin.sendCause(player, "totem_use", totem);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onItemBreak(PlayerItemBreakEvent event) {
        plugin.sendCause(event.getPlayer(), "item_break", event.getBrokenItem());
    }
}
