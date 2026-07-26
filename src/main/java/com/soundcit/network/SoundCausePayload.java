package com.soundcit.network;

import com.soundcit.SoundCIT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent by a SoundCIT-aware server just before it plays a sound caused by a named item.
 *
 * <p>Deliberately rule-agnostic. Rules live in the player's resource pack, which the server neither
 * has nor should have — the server states the cause, the client decides what to do about it. Packs
 * stay private, nothing needs syncing, and two players can run completely different sound packs.</p>
 *
 * <p>Only the item id and custom name are sent, never the whole {@code ItemStack}: a stack drags its
 * entire component patch along, which would be hundreds of bytes on every pickaxe swing. The name
 * travels as plain text rather than a {@code Component} so that a Bukkit plugin, which has no access
 * to Minecraft's component encoding, can produce this payload with nothing but a byte array.</p>
 *
 * @param kind       {@link #KIND_KEYED} when the hint names one specific sound, {@link #KIND_CONTEXT}
 *                   when it names an entity's next action of that type
 * @param key        for a keyed hint, the join key (see {@code SoundOrigin}); for a context hint,
 *                   the entity id
 * @param trigger    semantic trigger name (a string, so adding triggers can never desync the two sides)
 * @param itemId     registry id of the item responsible
 * @param customName the item's custom name, as plain text
 */
public record SoundCausePayload(byte kind, long key, String trigger, ResourceLocation itemId, String customName)
        implements CustomPacketPayload {

    /**
     * The hint identifies one specific sound. Used by the mod's own server side, which can see the
     * sound's seed at the moment it is broadcast.
     */
    public static final byte KIND_KEYED = 0;
    /**
     * The hint identifies an entity and an action rather than a sound. Used by the Paper plugin,
     * because Bukkit does not expose the seed of vanilla sounds and reading it would mean
     * intercepting packets. The client files these alongside its own predictions.
     */
    public static final byte KIND_CONTEXT = 1;

    public static final Type<SoundCausePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(SoundCIT.MODID, "sound_cause"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoundCausePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, SoundCausePayload::kind,
            ByteBufCodecs.VAR_LONG, SoundCausePayload::key,
            ByteBufCodecs.STRING_UTF8, SoundCausePayload::trigger,
            ResourceLocation.STREAM_CODEC, SoundCausePayload::itemId,
            ByteBufCodecs.STRING_UTF8, SoundCausePayload::customName,
            SoundCausePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
