package com.soundcit.context;

import com.soundcit.trigger.TriggerType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Maps vanilla sound ids to the semantic {@link TriggerType} they belong to, so a sound is only
 * ever attributed to an action it could actually have come from (an ATTACK context must not steal
 * the victim's hurt sound playing at the same instant).
 *
 * <p>All ids verified against 1.21.1 {@code SoundEvents}. Entries are either exact paths in the
 * {@code minecraft} namespace or {@link SoundFamily} patterns for groups too large to enumerate
 * (every block's break sound, every armour material's equip sound).</p>
 */
public final class TriggerSounds {

    /** A prefix/suffix pattern, used where enumerating every id would be unmaintainable. */
    public record SoundFamily(String prefix, String suffix) {
        public boolean matches(ResourceLocation soundId) {
            String path = soundId.getPath();
            return path.startsWith(prefix) && path.endsWith(suffix);
        }
    }

    private static final Map<TriggerType, Set<String>> EXACT = new EnumMap<>(TriggerType.class);
    private static final Map<TriggerType, List<SoundFamily>> FAMILIES = new EnumMap<>(TriggerType.class);

    private static void exact(TriggerType trigger, String... paths) {
        EXACT.put(trigger, Set.of(paths));
    }

    private static void family(TriggerType trigger, String prefix, String suffix) {
        FAMILIES.computeIfAbsent(trigger, t -> new ArrayList<>()).add(new SoundFamily(prefix, suffix));
    }

    static {
        // --- melee ---
        exact(TriggerType.ATTACK,
                "entity.player.attack.weak",
                "entity.player.attack.nodamage",
                "entity.player.attack.sweep");
        exact(TriggerType.HIT,
                "entity.player.attack.strong",
                "entity.player.attack.crit",
                "entity.player.attack.knockback",
                "item.mace.smash_ground",
                "item.mace.smash_ground_heavy",
                "item.mace.smash_air");

        // --- eating and drinking ---
        exact(TriggerType.EAT,
                "entity.generic.eat",
                "entity.player.burp");
        exact(TriggerType.DRINK,
                "entity.generic.drink",
                "item.honey_bottle.drink");
        exact(TriggerType.CHORUS_TELEPORT,
                "item.chorus_fruit.teleport");

        // --- mining and placing ---
        family(TriggerType.MINE, "block.", ".hit");
        family(TriggerType.BREAK, "block.", ".break");
        family(TriggerType.PLACE, "block.", ".place");

        // --- shooting and throwing ---
        exact(TriggerType.SHOOT,
                "entity.arrow.shoot",
                "item.crossbow.shoot");
        exact(TriggerType.CROSSBOW_LOAD,
                "item.crossbow.loading_start",
                "item.crossbow.loading_middle",
                "item.crossbow.quick_charge_1",
                "item.crossbow.quick_charge_2",
                "item.crossbow.quick_charge_3");
        exact(TriggerType.CROSSBOW_LOAD_END,
                "item.crossbow.loading_end");
        exact(TriggerType.ARROW_HIT,
                "entity.arrow.hit",
                "entity.arrow.hit_player",
                "item.crossbow.hit");
        exact(TriggerType.THROW,
                "entity.snowball.throw",
                "entity.egg.throw",
                "entity.ender_pearl.throw",
                "entity.experience_bottle.throw",
                "entity.splash_potion.throw",
                "entity.lingering_potion.throw",
                "entity.wind_charge.throw",
                "entity.ender_eye.launch");

        // --- trident ---
        exact(TriggerType.TRIDENT_THROW, "item.trident.throw");
        exact(TriggerType.TRIDENT_RETURN, "item.trident.return");
        exact(TriggerType.TRIDENT_HIT, "item.trident.hit");
        exact(TriggerType.TRIDENT_HIT_GROUND, "item.trident.hit_ground");
        exact(TriggerType.RIPTIDE,
                "item.trident.riptide_1",
                "item.trident.riptide_2",
                "item.trident.riptide_3");
        exact(TriggerType.THUNDER, "item.trident.thunder");

        // --- defence and survival ---
        exact(TriggerType.SHIELD_BLOCK, "item.shield.block");
        exact(TriggerType.SHIELD_BREAK, "item.shield.break");
        exact(TriggerType.TOTEM_USE, "item.totem.use");

        // --- item state ---
        family(TriggerType.EQUIP, "item.armor.equip", "");
        exact(TriggerType.ITEM_BREAK, "entity.item.break", "item.wolf_armor.break");
        exact(TriggerType.ELYTRA, "item.elytra.flying");

        // --- fishing ---
        exact(TriggerType.FISH_CAST, "entity.fishing_bobber.throw");
        exact(TriggerType.FISH_RETRIEVE, "entity.fishing_bobber.retrieve");
        exact(TriggerType.FISH_SPLASH, "entity.fishing_bobber.splash");

        // --- containers and fluids ---
        family(TriggerType.BUCKET_FILL, "item.bucket.fill", "");
        family(TriggerType.BUCKET_EMPTY, "item.bucket.empty", "");
        exact(TriggerType.BOTTLE_FILL, "item.bottle.fill", "item.bottle.fill_dragonbreath");
        exact(TriggerType.BOTTLE_EMPTY, "item.bottle.empty");

        // --- tools used on blocks ---
        exact(TriggerType.TILL, "item.hoe.till");
        exact(TriggerType.STRIP, "item.axe.strip");
        exact(TriggerType.SCRAPE, "item.axe.scrape");
        exact(TriggerType.WAX_ON, "item.honeycomb.wax_on");
        exact(TriggerType.WAX_OFF, "item.axe.wax_off");
        exact(TriggerType.FLATTEN, "item.shovel.flatten");
        family(TriggerType.BRUSH, "item.brush.brushing", "");
        exact(TriggerType.IGNITE, "item.flintandsteel.use", "item.firecharge.use");
        exact(TriggerType.SHEAR, "entity.sheep.shear", "block.beehive.shear", "entity.snow_golem.shear",
                "entity.mooshroom.shear", "entity.bogged.shear");
        exact(TriggerType.BONE_MEAL, "item.bone_meal.use");
        exact(TriggerType.DYE, "item.dye.use", "item.ink_sac.use", "item.glow_ink_sac.use");

        // --- misc items ---
        exact(TriggerType.SPYGLASS, "item.spyglass.use", "item.spyglass.stop_using");
        // Instruments became data-driven: the ids moved from item.goat_horn.play* to
        // item.goat_horn.sound.N, declared in data/minecraft/instrument/*.json.
        family(TriggerType.INSTRUMENT, "item.goat_horn.", "");
        exact(TriggerType.BUNDLE, "item.bundle.insert", "item.bundle.remove_one", "item.bundle.drop_contents");

        // --- workstations ---
        exact(TriggerType.ANVIL, "block.anvil.use", "block.anvil.destroy");
        exact(TriggerType.GRINDSTONE, "block.grindstone.use");
        exact(TriggerType.SMITHING, "block.smithing_table.use");
        exact(TriggerType.ENCHANT, "block.enchantment_table.use");

        // USE stays deliberately narrow: a loose "any item.* sound" rule would steal chest opens,
        // note blocks and every bucket sound within the context TTL.
        exact(TriggerType.USE,
                "item.lodestone_compass.lock",
                "entity.armor_stand.place",
                "item.ominous_bottle.dispose");
    }

    private TriggerSounds() {}

    public static Set<String> exactSounds(TriggerType trigger) {
        return EXACT.getOrDefault(trigger, Set.of());
    }

    public static List<SoundFamily> families(TriggerType trigger) {
        return FAMILIES.getOrDefault(trigger, List.of());
    }

    /** Every trigger whose sound set contains this id — the reverse of {@link #matches}. */
    public static List<TriggerType> triggersFor(ResourceLocation soundId) {
        List<TriggerType> found = new ArrayList<>(2);
        for (TriggerType trigger : TriggerType.values()) {
            if (matches(trigger, soundId)) {
                found.add(trigger);
            }
        }
        return found;
    }

    public static boolean matches(TriggerType trigger, ResourceLocation soundId) {
        if (soundId.getNamespace().equals("minecraft") && exactSounds(trigger).contains(soundId.getPath())) {
            return true;
        }
        for (SoundFamily family : families(trigger)) {
            if (family.matches(soundId)) {
                return true;
            }
        }
        return false;
    }
}
