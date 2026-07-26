package com.soundcit.config;

import com.soundcit.context.TriggerSounds;
import com.soundcit.trigger.TriggerType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Precomputed set of vanilla sounds any loaded rule could possibly claim.
 *
 * <p>{@code PlaySoundEvent} fires for every footstep, water drip and music note, so the handler
 * needs an O(1) rejection for the overwhelming majority of sounds. This index is rebuilt once per
 * resource reload and answers "could any rule care about this sound id?" with a single hash lookup,
 * falling back to a short list of pattern families ({@code block.*.break} and friends) only when
 * the exact lookup misses.</p>
 */
public final class RuleIndex {
    public static final RuleIndex EMPTY = new RuleIndex(Set.of(), List.of());

    private final Set<ResourceLocation> exact;
    private final List<TriggerSounds.SoundFamily> families;

    private RuleIndex(Set<ResourceLocation> exact, List<TriggerSounds.SoundFamily> families) {
        this.exact = exact;
        this.families = families;
    }

    public static RuleIndex build(List<SoundRule> rules) {
        if (rules.isEmpty()) {
            return EMPTY;
        }
        Set<ResourceLocation> exact = new HashSet<>();
        List<TriggerSounds.SoundFamily> families = new ArrayList<>();
        for (SoundRule rule : rules) {
            exact.addAll(rule.directOverrides.keySet());
            for (TriggerType trigger : rule.triggerSounds.keySet()) {
                for (String path : TriggerSounds.exactSounds(trigger)) {
                    exact.add(ResourceLocation.fromNamespaceAndPath("minecraft", path));
                }
                for (TriggerSounds.SoundFamily family : TriggerSounds.families(trigger)) {
                    if (!families.contains(family)) {
                        families.add(family);
                    }
                }
            }
        }
        return new RuleIndex(exact, families);
    }

    public boolean isEmpty() {
        return exact.isEmpty() && families.isEmpty();
    }

    /** Fast pre-filter: false means no rule can possibly replace this sound. */
    public boolean isInteresting(ResourceLocation soundId) {
        if (exact.contains(soundId)) {
            return true;
        }
        for (TriggerSounds.SoundFamily family : families) {
            if (family.matches(soundId)) {
                return true;
            }
        }
        return false;
    }
}
