package com.soundcit.server;

import com.soundcit.trigger.TriggerType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingUseTotemEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-side hooks that record which item is about to cause a sound.
 *
 * <p>These cover the cases the client genuinely cannot resolve on its own: another player's totem,
 * a shield block, armour equipped through an inventory, the bow an arrow was fired from. On the
 * server the item is known exactly rather than predicted.</p>
 */
public final class ServerActionHooks {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerCauseTracker.tick(event.getServer().overworld().getGameTime());
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(TriggerType.HIT, player.getMainHandItem(), player);
    }

    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(triggerFor(event.getItem()), event.getItem(), event.getEntity());
    }

    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(triggerFor(event.getItem()), event.getItem(), event.getEntity());
    }

    @SubscribeEvent
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null || event.getLevel().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(TriggerType.USE, event.getItemStack(), player);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(TriggerType.USE, event.getItemStack(), event.getEntity());
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(TriggerType.BREAK, event.getItemStack(), event.getEntity());
    }

    /** Totem of undying — server-only event, and the totem is consumed before the client sees it. */
    @SubscribeEvent
    public static void onUseTotem(LivingUseTotemEvent event) {
        ServerCauseTracker.record(TriggerType.TOTEM_USE, event.getTotem(), event.getEntity());
    }

    /** Shield block — the client never predicts this for other entities. */
    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        LivingEntity entity = event.getEntity();
        ServerCauseTracker.record(TriggerType.SHIELD_BLOCK, entity.getUseItem(), entity);
    }

    /** Armour equipped from an inventory: the equip sound is server-side only. */
    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!event.getTo().isEmpty()) {
            ServerCauseTracker.record(TriggerType.EQUIP, event.getTo(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(TriggerType.SHOOT, event.getBow(), event.getEntity());
    }

    @SubscribeEvent
    public static void onItemFished(ItemFishedEvent event) {
        ServerCauseTracker.record(TriggerType.FISH_RETRIEVE,
                event.getEntity().getMainHandItem(), event.getEntity());
    }

    @SubscribeEvent
    public static void onDestroyItem(PlayerDestroyItemEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        ServerCauseTracker.record(TriggerType.ITEM_BREAK, event.getOriginal(), event.getEntity());
    }

    private static TriggerType triggerFor(ItemStack stack) {
        return switch (stack.getUseAnimation()) {
            case EAT -> TriggerType.EAT;
            case DRINK -> TriggerType.DRINK;
            default -> TriggerType.USE;
        };
    }

    private ServerActionHooks() {}
}
