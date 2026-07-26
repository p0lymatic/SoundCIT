package com.soundcit.mixin;

import com.soundcit.context.SoundOrigin;
import com.soundcit.context.SoundOriginStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The totem-of-undying sound is played here rather than by the entity itself, so this is where its
 * origin has to be captured. Marks the sound as coming from entity event so a server hint filed
 * under the same key can claim it.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

    /**
     * Packet handlers run twice: once on the netty thread, where they abort and reschedule, and
     * again on the main thread where the work actually happens. Only the second pass is useful —
     * on the first the level is not set yet (which used to NPE and drop the connection) and the
     * origin would be pushed onto the wrong thread's stack anyway.
     */
    private static boolean soundcit$onMainThreadWithLevel(ClientPacketListener self) {
        return Minecraft.getInstance().isSameThread() && self.getLevel() != null;
    }

    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void soundcit$captureEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        if (!soundcit$onMainThreadWithLevel(self)) {
            return;
        }
        Entity entity = packet.getEntity(self.getLevel());
        if (entity != null) {
            SoundOriginStack.push(SoundOrigin.atEntityEvent(entity, packet.getEventId()));
        }
    }

    @Inject(method = "handleEntityEvent", at = @At("RETURN"))
    private void soundcit$releaseEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        ClientPacketListener self = (ClientPacketListener) (Object) this;
        if (soundcit$onMainThreadWithLevel(self) && packet.getEntity(self.getLevel()) != null) {
            SoundOriginStack.pop();
        }
    }
}
