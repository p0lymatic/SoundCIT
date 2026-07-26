package com.soundcit.config;

import com.google.gson.JsonElement;
import com.soundcit.SoundCIT;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads {@code assets/<namespace>/soundcit/*.json} rule files from all active resource packs.
 * Registered as a client reload listener, so F3+T re-reads the rules without restarting.
 *
 * <p>The base class became codec-driven in the 2026 releases; rules are still parsed by hand from
 * raw JSON, because the error messages a hand-written parser can give a pack author ("unknown
 * trigger 'attackk'") are far more useful than a codec's.</p>
 */
public class SoundRuleLoader extends SimpleJsonResourceReloadListener<JsonElement> {

    public SoundRuleLoader() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("soundcit"));
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<SoundRule> rules = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<Identifier, JsonElement> entry : files.entrySet()) {
            try {
                rules.add(SoundRule.parse(entry.getKey(), entry.getValue().getAsJsonObject()));
            } catch (Exception e) {
                problems.add(entry.getKey() + " — " + e.getMessage());
                SoundCIT.LOGGER.error("SoundCIT: failed to parse rule {}: {}", entry.getKey(), e.getMessage());
            }
        }
        RuleManager.setProblems(problems);
        // The incoming map iterates in hash order, so without an explicit sort two rules matching
        // the same item would win non-deterministically between runs.
        rules.sort(Comparator.comparingInt((SoundRule r) -> -r.priority)
                .thenComparing(r -> r.source.toString()));
        RuleManager.setProblems(problems);
        RuleManager.setRules(rules);
    }
}
