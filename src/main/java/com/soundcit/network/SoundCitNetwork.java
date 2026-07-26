package com.soundcit.network;

import com.soundcit.SoundCIT;
import com.soundcit.context.SoundOrigin;
import com.soundcit.server.ServerCauseTracker;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

/**
 * Optional server-assisted mode. When SoundCIT is installed on the server it tells clients exactly
 * which item caused each sound, which removes all the client-side guesswork — including cases the
 * client simply cannot work out, such as an arrow hit needing to know which bow fired it (the
 * shooter's weapon is never synced to clients).
 *
 * <p>The channel is registered as {@code optional()}, so a vanilla client can still join a server
 * with the mod and a client with the mod can still join a vanilla server; in both cases the mod
 * silently falls back to client-side resolution.</p>
 */
public final class SoundCitNetwork {
    private static final String VERSION = "2";

    private SoundCitNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION).optional();
        registrar.playToClient(SoundCausePayload.TYPE, SoundCausePayload.STREAM_CODEC,
                (payload, context) -> SoundCIT.onSoundCause(payload));
    }

    /**
     * Tells nearby players what caused a sound about to be broadcast.
     *
     * <p>Recipients must be filtered by channel: sending a payload to a player who does not have
     * the mod throws. The audience mirrors the vanilla broadcast — same range, same exclusion of
     * the acting player — so a hint never arrives for a sound the player will not hear.</p>
     */
    public static void sendHint(ServerLevel level, double x, double y, double z, Holder<SoundEvent> sound,
            float volume, long seed, @Nullable ServerCauseTracker.Cause cause) {
        if (cause == null) {
            return;
        }
        double range = sound.value().getRange(volume);
        broadcast(level, x, y, z, range * range, SoundOrigin.seedKey(seed), cause);
    }

    /** Hint for a sound the client derives from an entity event, which carries no seed. */
    public static void sendEntityEventHint(ServerLevel level, Entity entity, byte eventId) {
        if (ServerCauseTracker.isEmpty()) {
            return;
        }
        ServerCauseTracker.Cause cause = ServerCauseTracker.find(entity.getX(), entity.getY(), entity.getZ());
        if (cause == null) {
            return;
        }
        // Entity events reach everyone tracking the entity; 32 blocks covers the sounds they cause.
        broadcast(level, entity.getX(), entity.getY(), entity.getZ(), 32.0 * 32.0,
                SoundOrigin.entityEventKey(entity.getId(), eventId), cause);
    }

    /** Hint for a workstation or block-interaction sound, which travels as a level event. */
    public static void sendLevelEventHint(ServerLevel level, int type, net.minecraft.core.BlockPos pos) {
        if (ServerCauseTracker.isEmpty()) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        ServerCauseTracker.Cause cause = ServerCauseTracker.find(x, y, z);
        if (cause == null) {
            return;
        }
        // Vanilla broadcasts level events to everyone within 64 blocks.
        broadcast(level, x, y, z, 64.0 * 64.0, SoundOrigin.levelEventKey(type, pos), cause);
    }

    private static void broadcast(ServerLevel level, double x, double y, double z, double rangeSqr,
            long key, ServerCauseTracker.Cause cause) {
        SoundCausePayload payload = new SoundCausePayload(SoundCausePayload.KIND_KEYED,
                key, cause.trigger().name(), cause.itemId(), cause.customName().getString());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) > rangeSqr) {
                continue;
            }
            if (!NetworkRegistry.hasChannel(player.connection, SoundCausePayload.TYPE.id())) {
                continue; // vanilla or non-SoundCIT client
            }
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
