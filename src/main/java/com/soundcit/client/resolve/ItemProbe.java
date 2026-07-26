package com.soundcit.client.resolve;

import com.soundcit.config.RuleManager;
import com.soundcit.trigger.TriggerType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Given the entity a sound came from, works out which of its items caused it.
 *
 * <p>Which slot to look in depends on the action: an eating sound comes from the item being used,
 * an equip sound from an armour slot, a totem sound from either hand. Guessing "whatever is in the
 * main hand" would attribute a shield block to the sword the player is holding.</p>
 */
public final class ItemProbe {

    private ItemProbe() {}

    /**
     * @param entity  the entity the sound was bound to
     * @param trigger semantic trigger of the sound, or null if unknown
     * @return the named item responsible, or null if nothing there matches a rule
     */
    @Nullable
    public static ResolvedItem probe(Entity entity, @Nullable TriggerType trigger, ResourceLocation soundId) {
        // Projectiles carry their own identity: the stack itself is not synced to the client, but
        // the item's custom name was copied onto the entity when it was created.
        ResolvedItem projectile = probeProjectile(entity);
        if (projectile != null) {
            return withTrigger(projectile, trigger);
        }
        if (!(entity instanceof LivingEntity living)) {
            return null;
        }

        if (trigger == null) {
            return firstNamed(living, null, ItemProbe::handsThenArmour);
        }
        return switch (trigger.slot()) {
            case USE_ITEM -> {
                // The item being used, falling back to the hands: some "use" sounds arrive a tick
                // after the use finished, when getUseItem() is already empty again.
                ResolvedItem inUse = named(living.getUseItem(), trigger, ResolvedItem.LAYER_ENTITY);
                yield inUse != null ? inUse : firstNamed(living, trigger, ItemProbe::handsOnly);
            }
            case MAIN_HAND -> named(living.getMainHandItem(), trigger, ResolvedItem.LAYER_ENTITY);
            case HANDS -> firstNamed(living, trigger, ItemProbe::handsOnly);
            case ARMOUR -> firstNamed(living, trigger, ItemProbe::armourOnly);
            // PROJECTILE sounds are answered by probeProjectile above; reaching here means the
            // sound was bound to the shooter instead, so look at what they are holding.
            case PROJECTILE -> firstNamed(living, trigger, ItemProbe::handsOnly);
            case ANY -> firstNamed(living, trigger, ItemProbe::handsThenArmour);
        };
    }

    /** A thrown trident/arrow or a snowball-like projectile identified by its own data. */
    @Nullable
    private static ResolvedItem probeProjectile(Entity entity) {
        if (entity instanceof ThrowableItemProjectile thrown) {
            // Fully synced to the client, custom name included.
            return named(thrown.getItem(), null, ResolvedItem.LAYER_ENTITY);
        }
        if (entity instanceof AbstractArrow arrow) {
            // Nothing about the arrow's item reaches the client: pickupItemStack is server-only, and
            // unlike 1.21.x the entity no longer inherits the item's custom name. The only remaining
            // client-side route is what ProjectileTracker remembered when the shot was predicted.
            ResolvedItem remembered = ProjectileTracker.get(arrow.getId());
            if (remembered != null) {
                return remembered;
            }
            // A custom name may still be set explicitly (by a datapack or /summon).
            if (arrow.getCustomName() == null) {
                return null;
            }
            ResourceLocation itemId = RuleManager.idOf(
                    arrow instanceof ThrownTrident ? Items.TRIDENT : Items.ARROW);
            return new ResolvedItem(itemId, arrow.getCustomName().getString(), null, ResolvedItem.LAYER_ENTITY);
        }
        return null;
    }

    private interface SlotSet {
        ItemStack[] of(LivingEntity entity);
    }

    private static ItemStack[] handsOnly(LivingEntity entity) {
        return new ItemStack[] {entity.getMainHandItem(), entity.getOffhandItem()};
    }

    private static ItemStack[] armourOnly(LivingEntity entity) {
        return new ItemStack[] {
                entity.getItemBySlot(EquipmentSlot.HEAD),
                entity.getItemBySlot(EquipmentSlot.CHEST),
                entity.getItemBySlot(EquipmentSlot.LEGS),
                entity.getItemBySlot(EquipmentSlot.FEET)};
    }

    private static ItemStack[] handsThenArmour(LivingEntity entity) {
        ItemStack[] hands = handsOnly(entity);
        ItemStack[] armour = armourOnly(entity);
        ItemStack[] all = new ItemStack[hands.length + armour.length + 1];
        System.arraycopy(hands, 0, all, 0, hands.length);
        System.arraycopy(armour, 0, all, hands.length, armour.length);
        all[all.length - 1] = entity.getUseItem();
        return all;
    }

    @Nullable
    private static ResolvedItem firstNamed(LivingEntity entity, @Nullable TriggerType trigger, SlotSet slots) {
        for (ItemStack stack : slots.of(entity)) {
            ResolvedItem resolved = named(stack, trigger, ResolvedItem.LAYER_ENTITY);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    @Nullable
    private static ResolvedItem named(ItemStack stack, @Nullable TriggerType trigger, String layer) {
        String name = RuleManager.customNameOf(stack);
        if (name == null) {
            return null;
        }
        return new ResolvedItem(RuleManager.idOf(stack.getItem()), name, trigger, layer);
    }

    private static ResolvedItem withTrigger(ResolvedItem item, @Nullable TriggerType trigger) {
        return item.trigger() == trigger ? item : new ResolvedItem(item.itemId(), item.customName(), trigger, item.layer());
    }
}
