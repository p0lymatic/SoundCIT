package com.soundcit.server;

import com.soundcit.trigger.TriggerType;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side record of "this entity is about to cause a sound with this item".
 *
 * <p>Unlike the client tracker, this one has the authoritative item: no prediction, no guessing
 * which slot. It only holds causes for the current tick, since a sound follows its action within
 * the same tick on the server.</p>
 */
public final class ServerCauseTracker {
    /** How close a sound must be to the acting entity to be attributed to it. */
    private static final double MAX_DISTANCE = 8.0;

    public record Cause(TriggerType trigger, Identifier itemId, Component customName, Vec3 pos, long tick) {}

    private static final Map<Integer, Cause> BY_ENTITY = new HashMap<>();
    private static long currentTick;

    private ServerCauseTracker() {}

    public static void tick(long gameTime) {
        currentTick = gameTime;
        for (Iterator<Map.Entry<Integer, Cause>> it = BY_ENTITY.entrySet().iterator(); it.hasNext();) {
            if (currentTick - it.next().getValue().tick() > 2) {
                it.remove();
            }
        }
    }

    /** Records a cause; ignored unless the item carries a custom name, which is all rules match on. */
    public static void record(TriggerType trigger, ItemStack stack, Entity owner) {
        if (stack.isEmpty()) {
            return;
        }
        Component name = stack.get(DataComponents.CUSTOM_NAME);
        if (name == null) {
            return;
        }
        BY_ENTITY.put(owner.getId(), new Cause(trigger, BuiltInRegistries.ITEM.getKey(stack.getItem()),
                name, owner.position(), currentTick));
    }

    /**
     * The cause that best explains a sound at this position: recorded this tick or the last, and
     * close enough to the acting entity.
     */
    @Nullable
    public static Cause find(double x, double y, double z) {
        Cause best = null;
        double bestDistance = MAX_DISTANCE * MAX_DISTANCE;
        for (Cause cause : BY_ENTITY.values()) {
            if (currentTick - cause.tick() > 2) {
                continue;
            }
            double distance = cause.pos().distanceToSqr(x, y, z);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = cause;
            }
        }
        return best;
    }

    public static boolean isEmpty() {
        return BY_ENTITY.isEmpty();
    }

    public static void clear() {
        BY_ENTITY.clear();
    }
}
