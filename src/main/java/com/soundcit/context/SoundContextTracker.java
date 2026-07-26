package com.soundcit.context;

import com.soundcit.SoundCIT;
import com.soundcit.config.RuleManager;
import com.soundcit.trigger.TriggerType;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Short-lived "this entity's item X is about to cause a sound" records.
 *
 * <p>Game-action hooks (attack, use, mine, ...) push a context here right before the game — usually
 * the server, after a round-trip — plays the corresponding sound. When a sound is about to play,
 * the resolver asks this tracker whether an active context claims it.</p>
 *
 * <p>Contexts are keyed by <em>entity id and trigger</em>, not kept in one global list: without that
 * a mob eating nearby and the player swinging a weapon end up competing for the same sound. They
 * expire after a TTL counted in client ticks (wall-clock time makes lag spikes and headless test
 * runs flaky).</p>
 */
public final class SoundContextTracker {
    /** Covers the client→server→client round-trip plus modest ping. */
    private static final int TTL_TICKS = 25;
    /** Entity positions on the client lag behind the server by a tick or two, so this is generous. */
    private static final double MAX_DISTANCE = 6.0;

    /** What an action hook recorded. Only the item identity matters to rules, so no stack is kept. */
    public record Context(ResourceLocation itemId, String customName, Vec3 pos, int expiresAtTick) {}

    private static final Map<Integer, EnumMap<TriggerType, Context>> BY_ENTITY = new HashMap<>();
    private static int currentTick;

    private SoundContextTracker() {}

    public static void tick() {
        currentTick++;
        if ((currentTick & 0x1F) == 0) {
            expireStale();
        }
    }

    public static int currentTick() {
        return currentTick;
    }

    /**
     * Records that {@code stack} held by {@code owner} just triggered {@code trigger}, so vanilla
     * sounds of that action arriving shortly should be replaced. No-op when no rule matches the
     * stack, which keeps the hot path (every eat tick, every mining tick) allocation-free.
     */
    public static void push(TriggerType trigger, ItemStack stack, Entity owner) {
        String name = RuleManager.customNameOf(stack);
        if (name == null) {
            return;
        }
        ResourceLocation itemId = RuleManager.idOf(stack.getItem());
        if (!RuleManager.hasAnyRuleFor(itemId, name)) {
            return;
        }
        BY_ENTITY.computeIfAbsent(owner.getId(), id -> new EnumMap<>(TriggerType.class))
                .put(trigger, new Context(itemId, name, owner.position(), currentTick + TTL_TICKS));
        if (SoundCIT.debug()) {
            SoundCIT.LOGGER.info("[SoundCIT] context {} for {} on entity {}", trigger, name, owner.getId());
        }
    }

    /** Context recorded for a specific entity, or null if none is active for this trigger. */
    @Nullable
    public static Context get(int entityId, TriggerType trigger) {
        EnumMap<TriggerType, Context> perEntity = BY_ENTITY.get(entityId);
        if (perEntity == null) {
            return null;
        }
        Context context = perEntity.get(trigger);
        if (context == null) {
            return null;
        }
        if (currentTick > context.expiresAtTick()) {
            perEntity.remove(trigger);
            return null;
        }
        return context;
    }

    /** Any context of this trigger recorded close to the given position (no entity known). */
    @Nullable
    public static Context findNear(TriggerType trigger, double x, double y, double z) {
        Context best = null;
        double bestDistance = MAX_DISTANCE * MAX_DISTANCE;
        for (EnumMap<TriggerType, Context> perEntity : BY_ENTITY.values()) {
            Context context = perEntity.get(trigger);
            if (context == null || currentTick > context.expiresAtTick()) {
                continue;
            }
            double distance = context.pos().distanceToSqr(x, y, z);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = context;
            }
        }
        return best;
    }

    private static void expireStale() {
        for (Iterator<Map.Entry<Integer, EnumMap<TriggerType, Context>>> it = BY_ENTITY.entrySet().iterator(); it.hasNext();) {
            EnumMap<TriggerType, Context> perEntity = it.next().getValue();
            perEntity.values().removeIf(c -> currentTick > c.expiresAtTick());
            if (perEntity.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Called when leaving a world — entity ids are only meaningful within one connection. */
    public static void clear() {
        BY_ENTITY.clear();
    }
}
