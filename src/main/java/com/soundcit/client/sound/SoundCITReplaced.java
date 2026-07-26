package com.soundcit.client.sound;

/**
 * Marker for sound instances SoundCIT produced. Checked before doing any work so a replacement
 * is never re-processed — matching on the namespace would both miss replacements that reuse the
 * {@code soundcit} namespace and wrongly claim unrelated sounds from it.
 */
public interface SoundCITReplaced {}
