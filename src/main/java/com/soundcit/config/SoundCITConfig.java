package com.soundcit.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.soundcit.SoundCIT;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User-facing settings, kept deliberately loader-agnostic: a plain JSON file read and written by
 * hand, with no dependency on any config library. The screens that edit it are per-loader, but this
 * class is not, so Fabric and NeoForge builds share the same file and the same defaults.
 *
 * <p>Everything here answers a question a real user or pack author would ask: "can I turn off the
 * guessing?", "why is my sound not replaced?", "can I stop it touching workstation sounds?".</p>
 */
public final class SoundCITConfig {

    /** Which resolution layers are allowed to attribute a sound to an item. */
    public boolean useServerHints = true;
    public boolean useEntityResolution = true;
    public boolean usePredictedContexts = true;
    /** The guessing layer. Off means fewer replacements but never a wrong one. */
    public boolean useProximityGuessing = true;

    /** Categories of sound the mod is allowed to replace. */
    public boolean replaceWorkstationSounds = true;
    public boolean replaceOtherPlayersSounds = true;
    /** Music and records are left alone by default; renaming a disc to play your own track opts in. */
    public boolean replaceMusicAndRecords = false;

    /** How long a predicted action stays valid, in ticks. Raise it on a laggy server. */
    public int contextLifetimeTicks = 25;
    /** How close a guessed candidate must be to the sound, in blocks. */
    public double proximityRadius = 2.5;
    /** Minimum ticks between two plays of the same replacement, to stop fast actions machine-gunning. */
    public int repeatCooldownTicks = 3;

    /** Chat report after each resource reload, naming rule files that failed to parse. */
    public boolean reportReloads = true;
    /** Verbose logging of every resolution decision, including refusals. */
    public boolean debugLogging = false;

    // ------------------------------------------------------------------ loading and saving

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static SoundCITConfig instance = new SoundCITConfig();
    private static Path file;

    public static SoundCITConfig get() {
        return instance;
    }

    public static void load(Path configDirectory) {
        file = configDirectory.resolve("soundcit.json");
        if (!Files.exists(file)) {
            save();
            return;
        }
        try {
            SoundCITConfig loaded = GSON.fromJson(Files.readString(file), SoundCITConfig.class);
            if (loaded != null) {
                instance = loaded;
            }
            SoundCIT.setDebug(instance.debugLogging);
        } catch (Exception e) {
            // A broken config should never stop the mod loading; keep defaults and say so.
            SoundCIT.LOGGER.error("SoundCIT: could not read {} ({}), using defaults", file, e.getMessage());
        }
    }

    public static void save() {
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(instance));
        } catch (IOException e) {
            SoundCIT.LOGGER.error("SoundCIT: could not write {}: {}", file, e.getMessage());
        }
    }

    private SoundCITConfig() {}
}
