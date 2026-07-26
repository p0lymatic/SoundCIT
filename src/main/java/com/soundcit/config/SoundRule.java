package com.soundcit.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.soundcit.trigger.TriggerType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One parsed rule file from {@code assets/<ns>/soundcit/<name>.json}.
 *
 * <pre>{@code
 * {
 *   "item": "minecraft:mace",            // optional; string or array; omit = any item
 *   "match": "custom_name",              // optional; only "custom_name" is supported
 *   "pattern": "Frying Pan",             // required; see NameMatcher for syntax
 *   "sounds": {
 *     "hit": "mypack:frying_pan_hit",    // semantic trigger key -> replacement sound id
 *     "attack": "mypack:frying_pan_swing",
 *     "minecraft:item.mace.smash_ground": "mypack:frying_pan_hit"  // direct override by sound id
 *   }
 * }
 * }</pre>
 */
public final class SoundRule {
    /** Written as {@code "none"} in a rule: play nothing at all instead of the vanilla sound. */
    public static final Identifier SILENCE =
            Identifier.fromNamespaceAndPath("soundcit", "silence");

    /** Source file, for logging. */
    public final Identifier source;
    /** Item ids this rule applies to; empty = any item. */
    public final Set<Identifier> items;
    public final NameMatcher nameMatcher;
    /** Semantic trigger -> replacement sound id. */
    public final Map<TriggerType, Identifier> triggerSounds;
    /** Vanilla sound id -> replacement sound id (keys of "sounds" containing ':'). */
    public final Map<Identifier, Identifier> directOverrides;
    /** Higher wins when several rules match the same item; ties break on source path. */
    public final int priority;
    /** Extra requirements on the item itself; empty for a plain name rule. */
    public final List<ItemCondition> conditions;

    private SoundRule(Identifier source, Set<Identifier> items, NameMatcher nameMatcher,
            Map<TriggerType, Identifier> triggerSounds, Map<Identifier, Identifier> directOverrides,
            int priority, List<ItemCondition> conditions) {
        this.source = source;
        this.items = items;
        this.nameMatcher = nameMatcher;
        this.triggerSounds = triggerSounds;
        this.directOverrides = directOverrides;
        this.priority = priority;
        this.conditions = conditions;
    }

    /** @throws JsonSyntaxException if the file is malformed */
    public static SoundRule parse(Identifier source, JsonObject json) {
        Set<Identifier> items = new HashSet<>();
        if (json.has("item")) {
            JsonElement itemEl = json.get("item");
            if (itemEl.isJsonArray()) {
                for (JsonElement el : itemEl.getAsJsonArray()) {
                    items.add(Identifier.parse(GsonHelper.convertToString(el, "item")));
                }
            } else {
                items.add(Identifier.parse(GsonHelper.convertToString(itemEl, "item")));
            }
        }

        String match = GsonHelper.getAsString(json, "match", "custom_name");
        if (!"custom_name".equals(match)) {
            throw new JsonSyntaxException("Unsupported match type '" + match + "', only 'custom_name' is supported");
        }

        List<ItemCondition> conditions = ItemCondition.parseAll(json);
        // A rule with conditions may legitimately have no name requirement at all.
        NameMatcher matcher = json.has("pattern")
                ? NameMatcher.parse(GsonHelper.getAsString(json, "pattern"))
                : NameMatcher.matchAny();
        if (!json.has("pattern") && conditions.isEmpty() && items.isEmpty()) {
            throw new JsonSyntaxException("Rule matches everything: give it a \"pattern\","
                    + " an \"item\", or a condition such as \"enchantments\"");
        }

        Map<TriggerType, Identifier> triggerSounds = new HashMap<>();
        Map<Identifier, Identifier> directOverrides = new HashMap<>();
        JsonObject sounds = GsonHelper.getAsJsonObject(json, "sounds");
        for (Map.Entry<String, JsonElement> entry : sounds.entrySet()) {
            String value = GsonHelper.convertToString(entry.getValue(), entry.getKey());
            Identifier replacement = "none".equalsIgnoreCase(value) ? SILENCE : Identifier.parse(value);
            if (entry.getKey().contains(":")) {
                directOverrides.put(Identifier.parse(entry.getKey()), replacement);
            } else {
                TriggerType trigger = TriggerType.byName(entry.getKey());
                if (trigger == null) {
                    throw new JsonSyntaxException("Unknown sound trigger '" + entry.getKey()
                            + "'. Known triggers: attack, hit, use, eat, drink, mine, break, equip, shoot, throw, or a full sound id like 'minecraft:item.mace.smash_ground'");
                }
                triggerSounds.put(trigger, replacement);
            }
        }
        if (triggerSounds.isEmpty() && directOverrides.isEmpty()) {
            throw new JsonSyntaxException("Rule has an empty \"sounds\" object");
        }

        return new SoundRule(source, items, matcher, triggerSounds, directOverrides,
                GsonHelper.getAsInt(json, "priority", 0), conditions);
    }

    public boolean appliesTo(Identifier itemId, String customName) {
        return (items.isEmpty() || items.contains(itemId)) && nameMatcher.matches(customName);
    }

    /**
     * Whether this rule accepts the item, conditions included.
     *
     * <p>Some sounds are resolved from an item id and a name alone — a projectile in flight carries
     * nothing else — and a rule with conditions cannot be judged from that. Rather than guess, such
     * a rule simply declines: a wrong sound is worse than a missing one.</p>
     */
    public boolean appliesTo(Identifier itemId, String customName, @Nullable ItemStack stack) {
        if (!appliesTo(itemId, customName)) {
            return false;
        }
        if (conditions.isEmpty()) {
            return true;
        }
        if (stack == null) {
            return false;
        }
        for (ItemCondition condition : conditions) {
            if (!condition.test(stack)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public Identifier replacementFor(@Nullable TriggerType trigger, Identifier originalSound) {
        Identifier direct = directOverrides.get(originalSound);
        if (direct != null) {
            return direct;
        }
        return trigger != null ? triggerSounds.get(trigger) : null;
    }
}
