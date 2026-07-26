package com.soundcit.mixin;

import com.soundcit.context.SoundOrigin;
import com.soundcit.context.SoundOriginStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures what the client knows about a sound right before it reaches the sound engine.
 *
 * <p>Every positional client sound funnels through the private {@code playSound(double,...)} —
 * network sounds, {@code playLocalSound}, level events, and {@code LocalPlayer.playSound}. Entity
 * sounds go through the two entity-taking methods. By the time {@code PlaySoundEvent} fires, the
 * entity and the seed are gone and the position has been rounded by the packet, so we stash them
 * here for {@link com.soundcit.client.resolve.ItemResolver}.</p>
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
            at = @At("HEAD"))
    private void soundcit$capturePositional(double x, double y, double z, SoundEvent soundEvent, SoundSource source,
            float volume, float pitch, boolean distanceDelay, long seed, CallbackInfo ci) {
        SoundOriginStack.push(SoundOrigin.atPosition(x, y, z, volume, pitch, seed));
    }

    @Inject(method = "playSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZJ)V",
            at = @At("RETURN"))
    private void soundcit$releasePositional(double x, double y, double z, SoundEvent soundEvent, SoundSource source,
            float volume, float pitch, boolean distanceDelay, long seed, CallbackInfo ci) {
        SoundOriginStack.pop();
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"))
    private void soundcit$captureSeededEntity(Player player, Entity entity, Holder<SoundEvent> sound,
            SoundSource category, float volume, float pitch, long seed, CallbackInfo ci) {
        SoundOriginStack.push(SoundOrigin.atEntity(entity, volume, pitch, seed));
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("RETURN"))
    private void soundcit$releaseSeededEntity(Player player, Entity entity, Holder<SoundEvent> sound,
            SoundSource category, float volume, float pitch, long seed, CallbackInfo ci) {
        SoundOriginStack.pop();
    }

    @Inject(method = "playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            at = @At("HEAD"))
    private void soundcit$captureLocalEntity(Entity entity, SoundEvent sound, SoundSource category,
            float volume, float pitch, CallbackInfo ci) {
        SoundOriginStack.push(SoundOrigin.atEntity(entity, volume, pitch, 0L));
    }

    @Inject(method = "playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V",
            at = @At("RETURN"))
    private void soundcit$releaseLocalEntity(Entity entity, SoundEvent sound, SoundSource category,
            float volume, float pitch, CallbackInfo ci) {
        SoundOriginStack.pop();
    }
}
