package com.soundcit.client.resolve;

import com.soundcit.config.RuleManager;
import com.soundcit.config.SoundCITConfig;
import com.soundcit.context.SoundContextTracker;
import com.soundcit.context.SoundOrigin;
import com.soundcit.context.TriggerSounds;
import com.soundcit.trigger.TriggerType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Works out which item is responsible for a sound, trying the sources in order of how much they can
 * be trusted:
 *
 * <ol>
 *   <li>the entity the sound was bound to — a fact, captured by the emitter mixins;</li>
 *   <li>a context recorded by a client action hook for that same entity — a prediction;</li>
 *   <li>the nearest entity to the sound holding a matching item — a guess, only for sounds whose
 *       semantics are known, and only when exactly one candidate matches.</li>
 * </ol>
 */
public final class ItemResolver {
    /** Client entity positions lag behind the server, so this is deliberately loose. */


    private ItemResolver() {}

    /**
     * Every item that could plausibly have caused this sound, most trustworthy first.
     *
     * <p>Returns candidates rather than one answer on purpose: a more trusted layer can still be
     * wrong. The server attributes a sound to the nearest recent action, so a trident landing next
     * to a player who just swung a sword gets the sword — and that attribution matches no rule.
     * The caller keeps walking the list until one candidate actually yields a replacement, which
     * lets a lower layer correct a higher one instead of being shadowed by it.</p>
     */
    public static List<ResolvedItem> resolve(ResourceLocation soundId, SoundInstance sound, @Nullable SoundOrigin origin) {
        List<ResolvedItem> candidates = new ArrayList<>(3);
        List<TriggerType> triggers = TriggerSounds.triggersFor(soundId);

        SoundCITConfig config = SoundCITConfig.get();
        if (config.useServerHints && origin != null) {
            long key = origin.hintKey();
            if (key != 0L) {
                addIfPresent(candidates, ServerHintStore.take(key));
            }
        }

        Entity entity = config.useEntityResolution && origin != null ? origin.entity() : null;
        if (entity != null) {
            addIfPresent(candidates, probeWithTriggers(entity, triggers, soundId));
            addIfPresent(candidates, fromContext(entity.getId(), triggers, soundId));
        }

        double x = origin != null ? origin.x() : sound.getX();
        double y = origin != null ? origin.y() : sound.getY();
        double z = origin != null ? origin.z() : sound.getZ();

        for (TriggerType trigger : config.usePredictedContexts ? triggers : List.<TriggerType>of()) {
            SoundContextTracker.Context context = SoundContextTracker.findNear(trigger, x, y, z);
            if (context != null) {
                candidates.add(new ResolvedItem(context.itemId(), context.customName(), trigger,
                        ResolvedItem.LAYER_CONTEXT));
                break;
            }
        }

        if (config.useProximityGuessing) {
            addIfPresent(candidates, byProximity(triggers, soundId, x, y, z));
        }
        return attachStacks(candidates, entity);
    }

    /**
     * Server hints and predicted contexts carry only an item id and a name, which is not enough to
     * judge a rule's conditions. Where the item is still on an entity we can see, attach it.
     */
    private static List<ResolvedItem> attachStacks(List<ResolvedItem> candidates, @Nullable Entity origin) {
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < candidates.size(); i++) {
            ResolvedItem candidate = candidates.get(i);
            if (candidate.stack() != null) {
                continue;
            }
            ItemStack found = null;
            if (origin != null) {
                found = ItemProbe.findStack(origin, candidate.itemId(), candidate.customName());
            }
            if (found == null && minecraft.player != null) {
                found = ItemProbe.findStack(minecraft.player, candidate.itemId(), candidate.customName());
            }
            candidates.set(i, candidate.withStack(found));
        }
        return candidates;
    }

    private static void addIfPresent(List<ResolvedItem> candidates, @Nullable ResolvedItem item) {
        if (item != null) {
            candidates.add(item);
        }
    }

    @Nullable
    private static ResolvedItem probeWithTriggers(Entity entity, List<TriggerType> triggers, ResourceLocation soundId) {
        for (TriggerType trigger : triggers) {
            ResolvedItem resolved = ItemProbe.probe(entity, trigger, soundId);
            if (resolved != null) {
                return resolved;
            }
        }
        return triggers.isEmpty() ? ItemProbe.probe(entity, null, soundId) : null;
    }

    @Nullable
    private static ResolvedItem fromContext(int entityId, List<TriggerType> triggers, ResourceLocation soundId) {
        for (TriggerType trigger : triggers) {
            SoundContextTracker.Context context = SoundContextTracker.get(entityId, trigger);
            if (context != null) {
                return new ResolvedItem(context.itemId(), context.customName(), trigger, ResolvedItem.LAYER_CONTEXT);
            }
        }
        return null;
    }

    /**
     * Last resort: look for entities around the sound. Ambiguity is resolved conservatively — if two
     * candidates would give different results we replace nothing, since stealing someone else's
     * sound is worse than missing one.
     */
    @Nullable
    private static ResolvedItem byProximity(List<TriggerType> triggers, ResourceLocation soundId,
            double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }
        double radius = SoundCITConfig.get().proximityRadius;
        AABB box = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
        ResolvedItem found = null;
        // Not just living entities: projectiles play their own sounds positionally (a trident
        // sticking into the ground goes through Entity#playSound, which carries no entity), and
        // the flying trident is the only thing that knows it is a "Mjolnir".
        var candidates = minecraft.level.getEntitiesOfClass(Entity.class, box);
        if (com.soundcit.SoundCIT.debug()) {
            com.soundcit.SoundCIT.LOGGER.info("[SoundCIT]   proximity for {}: {} candidate(s) {}",
                    soundId, candidates.size(), candidates.stream().map(e -> e.getType().toString()).toList());
        }
        for (Entity candidate : candidates) {
            ResolvedItem resolved = triggers.isEmpty()
                    ? ItemProbe.probe(candidate, null, soundId)
                    : probeWithTriggers(candidate, triggers, soundId);
            if (resolved == null) {
                continue;
            }
            ResolvedItem asProximity = new ResolvedItem(
                    resolved.itemId(), resolved.customName(), resolved.trigger(), ResolvedItem.LAYER_PROXIMITY);
            if (found == null) {
                found = asProximity;
            } else if (!sameOutcome(found, asProximity, soundId)) {
                return null; // ambiguous — leave the vanilla sound alone
            }
        }
        return found;
    }

    private static boolean sameOutcome(ResolvedItem a, ResolvedItem b, ResourceLocation soundId) {
        ResourceLocation first = RuleManager.findReplacement(a.itemId(), a.customName(), a.trigger(), soundId);
        ResourceLocation second = RuleManager.findReplacement(b.itemId(), b.customName(), b.trigger(), soundId);
        return first != null && first.equals(second);
    }
}
