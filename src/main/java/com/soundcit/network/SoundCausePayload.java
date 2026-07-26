package com.soundcit.network;

import com.soundcit.SoundCIT;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent by a SoundCIT-aware server just before it plays a sound caused by a named item: "the sound
 * with this seed was caused by item X named N, action T".
 *
 * <p>Deliberately rule-agnostic. Rules live in the player's resource pack, which the server neither
 * has nor should have — the server states the cause, the client decides what to do about it. That
 * keeps packs private, avoids syncing rule files, and lets two players on the same server use
 * completely different sound packs.</p>
 *
 * <p>Only the item id and custom name are sent, never the whole {@code ItemStack}: a stack drags
 * its entire component patch (enchantments, lore, block data) along, which would be tens to
 * hundreds of bytes on every pickaxe swing.</p>
 *
 * @param key        exact join key for the sound this hint describes — see
 *                   {@link com.soundcit.context.SoundOrigin#seedKey} and
 *                   {@link com.soundcit.context.SoundOrigin#entityEventKey}. Matching on a key
 *                   rather than position or arrival order makes this immune to packet reordering
 *                   and to the 1/8-block quantisation of sound positions.
 * @param trigger    semantic trigger name (sent as a string so adding triggers cannot desync)
 * @param itemId     registry id of the item responsible
 * @param customName the item's custom name
 */
public record SoundCausePayload(long key, String trigger, Identifier itemId, Component customName)
        implements CustomPacketPayload {

    public static final Type<SoundCausePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SoundCIT.MODID, "sound_cause"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoundCausePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, SoundCausePayload::key,
            ByteBufCodecs.STRING_UTF8, SoundCausePayload::trigger,
            Identifier.STREAM_CODEC, SoundCausePayload::itemId,
            ComponentSerialization.STREAM_CODEC, SoundCausePayload::customName,
            SoundCausePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
