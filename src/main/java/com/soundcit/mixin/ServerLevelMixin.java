package com.soundcit.mixin;

import com.soundcit.network.SoundCitNetwork;
import com.soundcit.server.ServerCauseTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Emits the "what caused this sound" hint just before the server broadcasts a sound.
 *
 * <p>Both server sound methods carry the seed the clients will receive, which makes an exact join
 * key — the client can match the hint to the sound without any position or timing heuristics.</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void soundcit$hintAtPosition(Entity player, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed, CallbackInfo ci) {
        SoundCitNetwork.sendHint((ServerLevel) (Object) this, x, y, z, sound, volume, seed,
                ServerCauseTracker.find(x, y, z));
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void soundcit$hintAtEntity(Entity player, Entity entity, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed, CallbackInfo ci) {
        SoundCitNetwork.sendHint((ServerLevel) (Object) this, entity.getX(), entity.getY(), entity.getZ(),
                sound, volume, seed, ServerCauseTracker.find(entity.getX(), entity.getY(), entity.getZ()));
    }

    /**
     * Some item sounds are not broadcast as sounds at all: the server sends an entity event and the
     * client turns it into a sound (totem use, shield block/break, a tool snapping). Those carry no
     * seed, so the hint is keyed by entity and event id instead.
     */
    @Inject(method = "broadcastEntityEvent", at = @At("HEAD"))
    private void soundcit$hintEntityEvent(Entity entity, byte eventId, CallbackInfo ci) {
        SoundCitNetwork.sendEntityEventHint((ServerLevel) (Object) this, entity, eventId);
    }

    /** Workstation sounds (anvil, grindstone, smithing) and bone meal / wax travel as level events. */
    @Inject(method = "levelEvent(Lnet/minecraft/world/entity/Entity;ILnet/minecraft/core/BlockPos;I)V",
            at = @At("HEAD"))
    private void soundcit$hintLevelEvent(Entity player, int type, BlockPos pos, int data, CallbackInfo ci) {
        SoundCitNetwork.sendLevelEventHint((ServerLevel) (Object) this, type, pos);
    }
}
