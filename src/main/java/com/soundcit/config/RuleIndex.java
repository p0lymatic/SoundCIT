package com.soundcit.config;

import com.soundcit.context.TriggerSounds;
import com.soundcit.trigger.TriggerType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Precomputed set of sounds any loaded rule could possibly claim.
 *
 * <p>{@code PlaySoundEvent} fires for every footstep, water drip and music note — in a big modpack
 * that is thousands of calls a minute — so the handler needs an O(1) rejection for the overwhelming
 * majority of them. This index is rebuilt once per resource reload and answers "could any rule care
 * about this sound?" with a hash lookup, falling back to a short list of pattern families
 * ({@code block.*.break} and friends) only when both exact lookups miss.</p>
 */
public final class RuleIndex {
    public static final RuleIndex EMPTY = new RuleIndex(Set.of(), Set.of(), List.of());

    /** Full ids from direct overrides — there the namespace was written out and does matter. */
    private final Set<Identifier> exactIds;
    /** Paths from semantic triggers, matched regardless of namespace so modded sounds count too. */
    private final Set<String> exactPaths;
    private final List<TriggerSounds.SoundFamily> families;

    private RuleIndex(Set<Identifier> exactIds, Set<String> exactPaths,
            List<TriggerSounds.SoundFamily> families) {
        this.exactIds = exactIds;
        this.exactPaths = exactPaths;
        this.families = families;
    }

    public static RuleIndex build(List<SoundRule> rules) {
        if (rules.isEmpty()) {
            return EMPTY;
        }
        Set<Identifier> exactIds = new HashSet<>();
        Set<String> exactPaths = new HashSet<>();
        List<TriggerSounds.SoundFamily> families = new ArrayList<>();
        for (SoundRule rule : rules) {
            exactIds.addAll(rule.directOverrides.keySet());
            for (TriggerType trigger : rule.triggerSounds.keySet()) {
                exactPaths.addAll(TriggerSounds.exactSounds(trigger));
                for (TriggerSounds.SoundFamily family : TriggerSounds.families(trigger)) {
                    if (!families.contains(family)) {
                        families.add(family);
                    }
                }
            }
        }
        return new RuleIndex(exactIds, exactPaths, families);
    }

    public boolean isEmpty() {
        return exactIds.isEmpty() && exactPaths.isEmpty() && families.isEmpty();
    }

    /** Fast pre-filter: false means no rule can possibly replace this sound. */
    public boolean isInteresting(Identifier soundId) {
        if (exactIds.contains(soundId) || exactPaths.contains(soundId.getPath())) {
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
