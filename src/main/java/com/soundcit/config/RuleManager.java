package com.soundcit.config;

import com.soundcit.SoundCIT;
import com.soundcit.trigger.TriggerType;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the currently loaded set of sound rules and answers lookup queries.
 * Rules are (re)loaded by {@link SoundRuleLoader} on resource reload (F3+T).
 */
public final class RuleManager {
    private static volatile List<SoundRule> rules = List.of();
    private static volatile RuleIndex index = RuleIndex.EMPTY;
    /** Rule files that failed to parse on the last reload, for reporting to the player. */
    private static volatile List<String> problems = List.of();
    /** Bumped on every reload so the client can report the result once it has a player to tell. */
    private static volatile int generation;

    private RuleManager() {}

    public static void setRules(List<SoundRule> newRules) {
        rules = List.copyOf(newRules);
        index = RuleIndex.build(rules);
        generation++;
        SoundCIT.LOGGER.info("SoundCIT: loaded {} sound rule(s)", newRules.size());
    }

    public static void setProblems(List<String> newProblems) {
        problems = List.copyOf(newProblems);
    }

    public static List<String> getProblems() {
        return problems;
    }

    public static int generation() {
        return generation;
    }

    public static List<SoundRule> getRules() {
        return rules;
    }

    public static RuleIndex index() {
        return index;
    }

    public static boolean isEmpty() {
        return index.isEmpty();
    }

    /**
     * Finds a replacement sound id for a named item.
     *
     * @param itemId        registry id of the item the sound originates from
     * @param customName    the item's custom name, already flattened to a plain string
     * @param trigger       semantic trigger, or null if only direct sound-id overrides should apply
     * @param originalSound id of the vanilla sound about to play
     * @return replacement sound id, or null if no rule matches
     */
    @Nullable
    public static ResourceLocation findReplacement(ResourceLocation itemId, String customName,
            @Nullable TriggerType trigger, ResourceLocation originalSound) {
        return findReplacement(itemId, customName, trigger, originalSound, null);
    }

    public static ResourceLocation findReplacement(ResourceLocation itemId, String customName,
            @Nullable TriggerType trigger, ResourceLocation originalSound, @Nullable ItemStack stack) {
        for (SoundRule rule : rules) {
            if (rule.appliesTo(itemId, customName, stack)) {
                ResourceLocation replacement = rule.replacementFor(trigger, originalSound);
                if (replacement != null) {
                    return replacement;
                }
            }
        }
        return null;
    }

    @Nullable
    public static ResourceLocation findReplacement(ItemStack stack, @Nullable TriggerType trigger,
            ResourceLocation originalSound) {
        String name = customNameOf(stack);
        if (name == null) {
            return null;
        }
        return findReplacement(BuiltInRegistries.ITEM.getKey(stack.getItem()), name, trigger, originalSound, stack);
    }

    /** The item's custom name as a plain string, or null if it has none. */
    @Nullable
    public static String customNameOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        return customName == null ? null : customName.getString();
    }

    /** True if the stack has a custom name matching any rule (cheap pre-filter for context capture). */
    public static boolean hasAnyRuleFor(ItemStack stack) {
        String name = customNameOf(stack);
        return name != null && hasAnyRuleFor(BuiltInRegistries.ITEM.getKey(stack.getItem()), name);
    }

    public static boolean hasAnyRuleFor(ResourceLocation itemId, String customName) {
        for (SoundRule rule : rules) {
            if (rule.appliesTo(itemId, customName)) {
                return true;
            }
        }
        return false;
    }

    public static ResourceLocation idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }
}
