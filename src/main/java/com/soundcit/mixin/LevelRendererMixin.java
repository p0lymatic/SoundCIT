package com.soundcit.mixin;

import com.soundcit.context.SoundOrigin;
import com.soundcit.context.SoundOriginStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Workstation and block-interaction sounds (anvil, grindstone, smithing table, bone meal, wax) are
 * not sent as sounds at all: the server sends a level-event id and the client turns it into a
 * sound. There is no item anywhere near the playback site, so the event type and block position are
 * captured here as the only thing a server hint can be matched against.
 *
 * <p>This class is heavily transformed by rendering mods, so its mixin is optional — if it fails to
 * apply the mod loses only this category of sounds instead of crashing.</p>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "levelEvent", at = @At("HEAD"), require = 0)
    private void soundcit$captureLevelEvent(int type, BlockPos pos, int data, CallbackInfo ci) {
        SoundOriginStack.push(SoundOrigin.atPosition(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1.0F, 1.0F, 0L)
                .withLevelEvent(type, pos));
    }

    @Inject(method = "levelEvent", at = @At("RETURN"), require = 0)
    private void soundcit$releaseLevelEvent(int type, BlockPos pos, int data, CallbackInfo ci) {
        SoundOriginStack.pop();
    }
}
