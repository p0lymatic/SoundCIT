package com.soundcit.client.sound;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Positional replacement sound. */
public class ReplacedSimpleSound extends SimpleSoundInstance implements SoundCITReplaced {
    public ReplacedSimpleSound(Identifier sound, SoundSource source, float volume, float pitch,
            long seed, double x, double y, double z) {
        super(sound, source, volume, pitch, RandomSource.create(seed), false, 0, Attenuation.LINEAR, x, y, z, false);
    }
}
