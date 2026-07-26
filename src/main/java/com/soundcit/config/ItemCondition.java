package com.soundcit.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * An extra requirement on the item beyond its name — enchantments, wear, stack size, components,
 * tags. This is what takes rules past "match the name" and towards what OptiFine CIT could express.
 *
 * <p>Every condition needs the real {@link ItemStack}. Some sounds are resolved from something less
 * than that (a flying projectile is identified by name alone), and in that case a rule carrying
 * conditions is skipped rather than guessed at.</p>
 */
public abstract class ItemCondition {

    public abstract boolean test(ItemStack stack);

    /** Parses the optional condition fields of a rule. */
    public static List<ItemCondition> parseAll(JsonObject json) {
        List<ItemCondition> conditions = new ArrayList<>();

        if (json.has("enchantments")) {
            JsonObject enchantments = GsonHelper.getAsJsonObject(json, "enchantments");
            for (Map.Entry<String, JsonElement> entry : enchantments.entrySet()) {
                Identifier id = Identifier.parse(entry.getKey());
                Range range = Range.parse(GsonHelper.convertToString(entry.getValue(), entry.getKey()));
                conditions.add(new EnchantmentCondition(id, range));
            }
        }
        if (json.has("damage")) {
            conditions.add(new DamageCondition(Range.parse(GsonHelper.getAsString(json, "damage")), false));
        }
        if (json.has("damage_percent")) {
            conditions.add(new DamageCondition(Range.parse(GsonHelper.getAsString(json, "damage_percent")), true));
        }
        if (json.has("count")) {
            conditions.add(new CountCondition(Range.parse(GsonHelper.getAsString(json, "count"))));
        }
        if (json.has("tag")) {
            conditions.add(new TagCondition(GsonHelper.getAsString(json, "tag")));
        }
        if (json.has("components")) {
            for (JsonElement el : GsonHelper.getAsJsonArray(json, "components")) {
                conditions.add(new ComponentCondition(
                        Identifier.parse(GsonHelper.convertToString(el, "components")), true));
            }
        }
        if (json.has("without_components")) {
            for (JsonElement el : GsonHelper.getAsJsonArray(json, "without_components")) {
                conditions.add(new ComponentCondition(
                        Identifier.parse(GsonHelper.convertToString(el, "without_components")), false));
            }
        }
        return conditions;
    }

    /**
     * A numeric requirement written the way CIT packs write them: {@code "4"}, {@code ">=4"},
     * {@code "<50"}, {@code "1-3"}.
     */
    public record Range(int min, int max) {
        public static Range parse(String spec) {
            String text = spec.trim();
            try {
                if (text.startsWith(">=")) {
                    return new Range(Integer.parseInt(text.substring(2).trim()), Integer.MAX_VALUE);
                }
                if (text.startsWith("<=")) {
                    return new Range(Integer.MIN_VALUE, Integer.parseInt(text.substring(2).trim()));
                }
                if (text.startsWith(">")) {
                    return new Range(Integer.parseInt(text.substring(1).trim()) + 1, Integer.MAX_VALUE);
                }
                if (text.startsWith("<")) {
                    return new Range(Integer.MIN_VALUE, Integer.parseInt(text.substring(1).trim()) - 1);
                }
                int dash = text.indexOf('-', 1);
                if (dash > 0) {
                    return new Range(Integer.parseInt(text.substring(0, dash).trim()),
                            Integer.parseInt(text.substring(dash + 1).trim()));
                }
                int exact = Integer.parseInt(text);
                return new Range(exact, exact);
            } catch (NumberFormatException e) {
                throw new JsonSyntaxException("Cannot read '" + spec + "' as a number or range."
                        + " Use forms like 4, >=4, <50 or 1-3");
            }
        }

        public boolean contains(int value) {
            return value >= min && value <= max;
        }
    }

    private static final class EnchantmentCondition extends ItemCondition {
        private final Identifier id;
        private final Range level;

        EnchantmentCondition(Identifier id, Range level) {
            this.id = id;
            this.level = level;
        }

        @Override
        public boolean test(ItemStack stack) {
            var lookup = net.minecraft.client.Minecraft.getInstance().level;
            if (lookup == null) {
                return false;
            }
            TagKey<Enchantment> unused = null; // registry access needs a level; enchantments are dynamic
            var registry = lookup.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var holder = registry.get(net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id));
            if (holder.isEmpty()) {
                return false;
            }
            return level.contains(net.minecraft.world.item.enchantment.EnchantmentHelper
                    .getItemEnchantmentLevel(holder.get(), stack));
        }
    }

    private static final class DamageCondition extends ItemCondition {
        private final Range range;
        private final boolean percent;

        DamageCondition(Range range, boolean percent) {
            this.range = range;
            this.percent = percent;
        }

        @Override
        public boolean test(ItemStack stack) {
            if (!stack.isDamageableItem()) {
                return false;
            }
            int damage = stack.getDamageValue();
            return range.contains(percent ? damage * 100 / Math.max(1, stack.getMaxDamage()) : damage);
        }
    }

    private static final class CountCondition extends ItemCondition {
        private final Range range;

        CountCondition(Range range) {
            this.range = range;
        }

        @Override
        public boolean test(ItemStack stack) {
            return range.contains(stack.getCount());
        }
    }

    private static final class TagCondition extends ItemCondition {
        private final TagKey<net.minecraft.world.item.Item> tag;

        TagCondition(String spec) {
            String id = spec.startsWith("#") ? spec.substring(1) : spec;
            this.tag = ItemTags.create(Identifier.parse(id));
        }

        @Override
        public boolean test(ItemStack stack) {
            return stack.is(tag);
        }
    }

    private static final class ComponentCondition extends ItemCondition {
        private final Identifier id;
        private final boolean expected;

        ComponentCondition(Identifier id, boolean expected) {
            this.id = id;
            this.expected = expected;
        }

        @Override
        public boolean test(ItemStack stack) {
            // 26.2 registries hand back an Optional<Reference<…>> rather than the value itself.
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(id).orElse(null);
            if (type == null) {
                // An unknown component id cannot be present, so "without_components" is satisfied
                // and "components" is not. Naming a component no version has is a pack mistake, but
                // it must not throw during a sound event.
                return !expected;
            }
            return stack.has(type.value()) == expected;
        }
    }
}
