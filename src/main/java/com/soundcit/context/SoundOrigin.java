package com.soundcit.context;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * What the client knew about a sound at the moment it handed it to the sound engine: the exact
 * (un-quantised) position, the entity it was bound to, the seed, and — for {@code levelEvent} or
 * entity-event sounds — the identifiers those carry instead.
 *
 * <p>{@code SoundInstance} alone loses all of this: the entity is private, the position has already
 * been rounded to 1/8 of a block by the network packet, and the seed is gone. Mixins on the few
 * methods every sound funnels through capture it just before playback.</p>
 */
public record SoundOrigin(
        @Nullable Entity entity,
        double x, double y, double z,
        float volume, float pitch,
        long seed,
        int levelEventType,
        @Nullable BlockPos levelEventPos,
        int entityEventId,
        int tick) {

    public static SoundOrigin atPosition(double x, double y, double z, float volume, float pitch, long seed) {
        return new SoundOrigin(null, x, y, z, volume, pitch, seed, 0, null, 0, SoundContextTracker.currentTick());
    }

    public static SoundOrigin atEntity(Entity entity, float volume, float pitch, long seed) {
        return new SoundOrigin(entity, entity.getX(), entity.getY(), entity.getZ(), volume, pitch, seed,
                0, null, 0, SoundContextTracker.currentTick());
    }

    /**
     * A sound the client produces in response to an entity event (totem use, shield block, a tool
     * breaking). These carry no seed, so they are matched to a server hint by entity and event id.
     */
    public static SoundOrigin atEntityEvent(Entity entity, int eventId) {
        return new SoundOrigin(entity, entity.getX(), entity.getY(), entity.getZ(), 1.0F, 1.0F, 0L,
                0, null, eventId, SoundContextTracker.currentTick());
    }

    public SoundOrigin withLevelEvent(int type, BlockPos pos) {
        return new SoundOrigin(entity, x, y, z, volume, pitch, seed, type, pos, entityEventId, tick);
    }

    public boolean isLevelEvent() {
        return levelEventPos != null;
    }

    public static int tickOf(@Nullable SoundOrigin origin) {
        return origin != null ? origin.tick() : SoundContextTracker.currentTick();
    }

    /**
     * Join key for a seeded sound. The low bit separates the two key spaces so an entity-event key
     * can never collide with a seed that happens to have the same numeric value.
     */
    public static long seedKey(long seed) {
        return seed << 1;
    }

    public static long entityEventKey(int entityId, int eventId) {
        return ((((long) entityId << 8) | (eventId & 0xFF)) << 1) | 1L;
    }

    /**
     * Join key for a {@code levelEvent} sound (anvil, grindstone, bone meal, wax). These carry
     * neither seed nor entity — only an event type and a block position, which is exactly what
     * both sides can agree on.
     */
    public static long levelEventKey(int type, BlockPos pos) {
        return ((((long) type << 40) ^ pos.asLong()) << 1) | 1L;
    }

    /** The key a server hint for this sound would be filed under, or 0 if it cannot be matched. */
    public long hintKey() {
        if (levelEventPos != null) {
            return levelEventKey(levelEventType, levelEventPos);
        }
        if (entityEventId != 0 && entity != null) {
            return entityEventKey(entity.getId(), entityEventId);
        }
        return seed != 0L ? seedKey(seed) : 0L;
    }
}
