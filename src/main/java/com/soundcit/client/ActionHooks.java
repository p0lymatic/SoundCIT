package com.soundcit.client;

import com.soundcit.context.SoundContextTracker;
import com.soundcit.trigger.TriggerType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import com.soundcit.client.resolve.ProjectileTracker;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.ArrowNockEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent;

/**
 * Client-side hooks at the moments a player action is about to produce item sounds.
 *
 * <p>These record a short-lived context in {@link SoundContextTracker} for actions whose sound is
 * played by the server a few ticks later, or where the item is no longer in hand once the sound
 * arrives. Sounds the client plays itself are usually resolved straight from the entity, so these
 * hooks are the fallback rather than the primary path.</p>
 */
public final class ActionHooks {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        SoundContextTracker.tick();
    }

    /** Melee attack: swing/miss sounds (ATTACK) and landed-hit sounds (HIT), incl. mace smash. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        SoundContextTracker.push(TriggerType.ATTACK, weapon, player);
        SoundContextTracker.push(TriggerType.HIT, weapon, player);
    }

    /** Triggers a right-click in the air can produce. */
    private static final TriggerType[] RIGHT_CLICK_TRIGGERS = {
            TriggerType.USE, TriggerType.THROW, TriggerType.SHOOT,
            TriggerType.FISH_CAST, TriggerType.FISH_RETRIEVE,
            TriggerType.BUCKET_FILL, TriggerType.BOTTLE_FILL, TriggerType.SPYGLASS,
            TriggerType.INSTRUMENT, TriggerType.CHORUS_TELEPORT};

    /** Triggers using an item on a block can produce. */
    private static final TriggerType[] USE_ON_BLOCK_TRIGGERS = {
            TriggerType.USE, TriggerType.PLACE, TriggerType.TILL, TriggerType.STRIP, TriggerType.SCRAPE,
            TriggerType.WAX_ON, TriggerType.WAX_OFF, TriggerType.FLATTEN, TriggerType.IGNITE,
            TriggerType.BONE_MEAL, TriggerType.DYE, TriggerType.BUCKET_EMPTY, TriggerType.BOTTLE_EMPTY,
            TriggerType.BRUSH};

    /** Right-click use: generic use sounds, throws, fishing, buckets, spyglass, goat horn. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        pushAll(RIGHT_CLICK_TRIGGERS, event.getItemStack(), event.getEntity());
    }

    /** Using an item on a block: hoe till, axe strip, shovel flatten, flint and steel, buckets, wax. */
    @SubscribeEvent
    public static void onUseItemOnBlock(UseItemOnBlockEvent event) {
        Player player = event.getPlayer();
        if (player == null || !event.getLevel().isClientSide()) {
            return;
        }
        pushAll(USE_ON_BLOCK_TRIGGERS, event.getItemStack(), player);
    }

    /** Interacting with an entity: shears on a sheep, dye, saddle — the sound plays on the target. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        SoundContextTracker.push(TriggerType.SHEAR, event.getItemStack(), player);
        SoundContextTracker.push(TriggerType.DYE, event.getItemStack(), player);
        // Bound to the target entity, which holds nothing itself — record it there too.
        SoundContextTracker.push(TriggerType.SHEAR, event.getItemStack(), event.getTarget());
        SoundContextTracker.push(TriggerType.DYE, event.getItemStack(), event.getTarget());
    }

    private static void pushAll(TriggerType[] triggers, net.minecraft.world.item.ItemStack stack,
            net.minecraft.world.entity.Entity owner) {
        for (TriggerType trigger : triggers) {
            SoundContextTracker.push(trigger, stack, owner);
        }
    }

    /**
     * Continuous use (eating, drinking, blocking, charging a crossbow): refresh the context every
     * use tick. The crossbow charging sounds are played server-side, so the client has no other way
     * to know which crossbow they belong to.
     */
    @SubscribeEvent
    public static void onUseItemTick(LivingEntityUseItemEvent.Tick event) {
        pushUseContext(event.getEntity(), event.getItem());
        SoundContextTracker.push(TriggerType.CROSSBOW_LOAD, event.getItem(), event.getEntity());
        SoundContextTracker.push(TriggerType.CROSSBOW_LOAD_END, event.getItem(), event.getEntity());
        SoundContextTracker.push(TriggerType.SHIELD_BLOCK, event.getItem(), event.getEntity());
    }

    /**
     * Ties a freshly spawned projectile to the item it came from.
     *
     * <p>An arrow or trident carries nothing about its item to the client, so this association has
     * to be made while the shooter still holds it. Looked up by the shooter's own contexts, which
     * the shoot/throw hooks recorded a moment earlier.</p>
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile projectile)) {
            return;
        }
        Entity owner = projectile.getOwner();
        if (owner == null) {
            return;
        }
        for (TriggerType trigger : new TriggerType[] {TriggerType.SHOOT, TriggerType.THROW}) {
            SoundContextTracker.Context context = SoundContextTracker.get(owner.getId(), trigger);
            if (context != null) {
                ProjectileTracker.remember(projectile, context.itemId(), context.customName());
                return;
            }
        }
    }

    /** A tool or armour piece breaking: the stack is gone from the hand once the sound plays. */
    @SubscribeEvent
    public static void onDestroyItem(PlayerDestroyItemEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide()) {
            return;
        }
        SoundContextTracker.push(TriggerType.ITEM_BREAK, event.getOriginal(), player);
    }

    /** Nocking a bow — covers the shot sound that follows on release. */
    @SubscribeEvent
    public static void onArrowNock(ArrowNockEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        SoundContextTracker.push(TriggerType.SHOOT, event.getBow(), event.getEntity());
    }

    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        SoundContextTracker.push(TriggerType.SHOOT, event.getBow(), event.getEntity());
        SoundContextTracker.push(TriggerType.ARROW_HIT, event.getBow(), event.getEntity());
    }

    /** Finishing use (the burp after eating, potion finished, ...). */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        pushUseContext(event.getEntity(), event.getItem());
    }

    /** Releasing use early — bow shots, trident throws. */
    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        LivingEntity entity = event.getEntity();
        if (!entity.level().isClientSide()) {
            return;
        }
        SoundContextTracker.push(TriggerType.SHOOT, event.getItem(), entity);
        SoundContextTracker.push(TriggerType.THROW, event.getItem(), entity);
    }

    /** Mining: block-hit progress sounds and the final break sound of the mined block. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide()) {
            return;
        }
        Player player = event.getEntity();
        SoundContextTracker.push(TriggerType.MINE, event.getItemStack(), player);
        SoundContextTracker.push(TriggerType.BREAK, event.getItemStack(), player);
    }

    private static void pushUseContext(LivingEntity entity, ItemStack stack) {
        if (!entity.level().isClientSide() || stack.isEmpty()) {
            return;
        }
        TriggerType trigger = switch (stack.getUseAnimation()) {
            case EAT -> TriggerType.EAT;
            case DRINK -> TriggerType.DRINK;
            default -> TriggerType.USE;
        };
        SoundContextTracker.push(trigger, stack, entity);
    }

    /** Entity ids only mean something within one connection. */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().level == null) {
            SoundContextTracker.clear();
            com.soundcit.client.resolve.ServerHintStore.clear();
        }
    }

    private ActionHooks() {}
}
