package com.soundcit.trigger;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Semantic sound-trigger categories usable as keys in the "sounds" object of a rule.
 * Raw sound ids (keys containing ':') are handled separately as direct overrides.
 *
 * <p>Names are matched case-insensitively against the JSON keys, so {@code "trident_throw"} maps to
 * {@link #TRIDENT_THROW}.</p>
 */
public enum TriggerType {
    // --- melee ---
    /** Swing/whoosh when attacking, regardless of whether the hit landed. */
    ATTACK(Slot.MAIN_HAND),
    /** A successful melee hit with the item (strong/crit/knockback, mace smash, etc). */
    HIT(Slot.MAIN_HAND),

    // --- generic use ---
    /** Generic right-click use of the item. */
    USE(Slot.USE_ITEM),
    /** Eating food. */
    EAT(Slot.USE_ITEM),
    /** Drinking (potions, milk, honey). */
    DRINK(Slot.USE_ITEM),

    // --- mining and placing ---
    /** Block-breaking progress ticks while mining with the item. */
    MINE(Slot.MAIN_HAND),
    /** The block-broken sound for the block destroyed with the item. */
    BREAK(Slot.MAIN_HAND),
    /** Placing a block from a BlockItem. */
    PLACE(Slot.USE_ITEM),

    // --- shooting and throwing ---
    /** Bow/crossbow/generic shooting. */
    SHOOT(Slot.HANDS),
    /** Throwing a snowball, egg, ender pearl, potion, wind charge. */
    THROW(Slot.HANDS),
    /** Crossbow charging stages (loading start/middle, quick charge). */
    CROSSBOW_LOAD(Slot.USE_ITEM),
    /** Crossbow finished charging. */
    CROSSBOW_LOAD_END(Slot.USE_ITEM),
    /** An arrow hitting a block or entity. */
    ARROW_HIT(Slot.PROJECTILE),

    // --- trident ---
    TRIDENT_THROW(Slot.PROJECTILE),
    TRIDENT_RETURN(Slot.PROJECTILE),
    TRIDENT_HIT(Slot.PROJECTILE),
    TRIDENT_HIT_GROUND(Slot.PROJECTILE),
    /** Riptide launch — the sound is bound to the player, not the projectile. */
    RIPTIDE(Slot.HANDS),
    /** Channeling lightning. */
    THUNDER(Slot.HANDS),

    // --- defence and survival ---
    SHIELD_BLOCK(Slot.USE_ITEM),
    SHIELD_BREAK(Slot.HANDS),
    TOTEM_USE(Slot.HANDS),

    // --- item state ---
    /** Armour/elytra equip sound. */
    EQUIP(Slot.ARMOUR),
    /** A tool or piece of armour breaking from durability loss. */
    ITEM_BREAK(Slot.ANY),
    /** The looping elytra flight sound. */
    ELYTRA(Slot.ARMOUR),

    // --- fishing ---
    FISH_CAST(Slot.HANDS),
    FISH_RETRIEVE(Slot.HANDS),
    FISH_SPLASH(Slot.HANDS),

    // --- containers and fluids ---
    BUCKET_FILL(Slot.USE_ITEM),
    BUCKET_EMPTY(Slot.USE_ITEM),
    BOTTLE_FILL(Slot.USE_ITEM),
    BOTTLE_EMPTY(Slot.USE_ITEM),

    // --- tools used on blocks ---
    TILL(Slot.USE_ITEM),
    STRIP(Slot.USE_ITEM),
    SCRAPE(Slot.USE_ITEM),
    WAX_ON(Slot.USE_ITEM),
    WAX_OFF(Slot.USE_ITEM),
    FLATTEN(Slot.USE_ITEM),
    BRUSH(Slot.USE_ITEM),
    IGNITE(Slot.USE_ITEM),
    SHEAR(Slot.HANDS),
    BONE_MEAL(Slot.USE_ITEM),
    DYE(Slot.USE_ITEM),

    // --- misc items ---
    SPYGLASS(Slot.USE_ITEM),
    INSTRUMENT(Slot.USE_ITEM),
    BUNDLE(Slot.ANY),
    CHORUS_TELEPORT(Slot.USE_ITEM),

    // --- workstations (position-matched, least reliable) ---
    ANVIL(Slot.ANY),
    GRINDSTONE(Slot.ANY),
    SMITHING(Slot.ANY),
    ENCHANT(Slot.ANY);

    /** Where to look for the item responsible for a sound of this kind. */
    public enum Slot { MAIN_HAND, HANDS, USE_ITEM, ARMOUR, PROJECTILE, ANY }

    private final Slot slot;

    TriggerType(Slot slot) {
        this.slot = slot;
    }

    public Slot slot() {
        return slot;
    }

    @Nullable
    public static TriggerType byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
