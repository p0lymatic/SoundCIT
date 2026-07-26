package com.soundcit.client.resolve;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Remembers which item a flying projectile came from.
 *
 * <p>An arrow or trident tells the client nothing about the item it was: {@code pickupItemStack} is
 * server-only, and since the 2026 releases the projectile no longer inherits the item's custom name
 * either (it did in 1.21.x, which is what the old resolver relied on). So the association has to be
 * made at the moment of the shot, while the item is still in hand, and kept for as long as the
 * projectile lives.</p>
 *
 * <p>Entries are dropped when the entity disappears or the map fills up — a trident can fly for a
 * while, so this is deliberately longer-lived than the tick-based action contexts.</p>
 */
public final class ProjectileTracker {
    private static final int CAPACITY = 64;

    private static final Map<Integer, ResolvedItem> BY_ENTITY = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, ResolvedItem> eldest) {
            return size() > CAPACITY;
        }
    };

    private ProjectileTracker() {}

    /** Associates a just-spawned projectile with the item it was fired or thrown from. */
    public static void remember(Entity projectile, Identifier itemId, String customName) {
        BY_ENTITY.put(projectile.getId(), new ResolvedItem(itemId, customName, null, ResolvedItem.LAYER_CONTEXT));
    }

    @Nullable
    public static ResolvedItem get(int entityId) {
        return BY_ENTITY.get(entityId);
    }

    public static void forget(int entityId) {
        BY_ENTITY.remove(entityId);
    }

    public static void clear() {
        BY_ENTITY.clear();
    }
}
