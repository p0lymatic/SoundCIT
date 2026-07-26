package com.soundcit.client.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Replacement for a sound that was bound to an entity, so the custom sound keeps following it
 * (a thrown trident in flight, a mob eating while it walks).
 */
public class ReplacedEntitySound extends EntityBoundSoundInstance implements SoundCITReplaced {
    public ReplacedEntitySound(ResourceLocation sound, SoundSource source, float volume, float pitch,
            Entity entity, long seed) {
        super(SoundEvent.createVariableRangeEvent(sound), source, volume, pitch, entity, seed);
    }
}
