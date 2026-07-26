package com.soundcit.client;

import com.soundcit.SoundCIT;
import com.soundcit.client.resolve.ItemResolver;
import com.soundcit.client.resolve.ResolvedItem;
import com.soundcit.client.sound.ReplacedEntitySound;
import com.soundcit.client.sound.ReplacedSimpleSound;
import com.soundcit.client.sound.SoundCITReplaced;
import com.soundcit.config.RuleManager;
import com.soundcit.context.SoundOrigin;
import com.soundcit.context.SoundOriginStack;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The single replacement point: every sound the client is about to play passes through here, and
 * those attributable to a renamed item get swapped for the custom one.
 *
 * <p>Runs at {@link EventPriority#LOW} so other sound mods decide first, and never overrides their
 * decision: a sound another mod silenced stays silent, and a sound another mod already swapped is
 * matched on what it swapped it to.</p>
 */
public final class SoundReplacementHandler {
    /** Categories no item can plausibly cause; rejected before any lookup. */
    private static final EnumSet<SoundSource> IGNORED_SOURCES =
            EnumSet.of(SoundSource.MUSIC, SoundSource.RECORDS, SoundSource.AMBIENT, SoundSource.VOICE);

    /** Recent decisions, for {@code /soundcit why} and for strict assertions in the headless test. */
    public record Replacement(ResourceLocation from, ResourceLocation to, String layer, int tick) {}

    private static final int JOURNAL_LIMIT = 32;
    private static final Deque<Replacement> JOURNAL = new ArrayDeque<>();
    private static long replacementCount;

    private SoundReplacementHandler() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlaySound(PlaySoundEvent event) {
        SoundOrigin origin = SoundOriginStack.current();
        try {
            handle(event, origin);
        } finally {
            SoundOriginStack.consume();
        }
    }

    private static void handle(PlaySoundEvent event, @Nullable SoundOrigin origin) {
        if (RuleManager.isEmpty()) {
            return;
        }
        // Read the *current* sound, not the original: another mod may have silenced or swapped it.
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof SoundCITReplaced) {
            return;
        }
        if (IGNORED_SOURCES.contains(sound.getSource())) {
            return;
        }
        ResourceLocation soundId = sound.getLocation();
        if (!RuleManager.index().isInteresting(soundId)) {
            return;
        }

        // Candidates come back most-trustworthy first; the first one an actual rule covers wins, so
        // a confident but wrong attribution cannot shadow a correct one further down.
        ResolvedItem resolved = null;
        ResourceLocation replacement = null;
        for (ResolvedItem candidate : ItemResolver.resolve(soundId, sound, origin)) {
            ResourceLocation match = RuleManager.findReplacement(
                    candidate.itemId(), candidate.customName(), candidate.trigger(), soundId);
            if (match != null) {
                resolved = candidate;
                replacement = match;
                break;
            }
            if (SoundCIT.debug()) {
                SoundCIT.LOGGER.info("[SoundCIT] {} attributed to \"{}\" ({}) via {} — no rule covers it, trying next",
                        soundId, candidate.customName(), candidate.itemId(), candidate.layer());
            }
        }
        if (replacement == null) {
            if (SoundCIT.debug()) {
                SoundCIT.LOGGER.info("[SoundCIT] {} matches a rule's sound set but no item could be attributed"
                        + " (entity {}, pos {} {} {})", soundId,
                        origin != null && origin.entity() != null ? origin.entity().getType() : "none",
                        sound.getX(), sound.getY(), sound.getZ());
            }
            return;
        }

        event.setSound(build(sound, origin, replacement));
        replacementCount++;
        record(new Replacement(soundId, replacement, resolved.layer(), SoundOrigin.tickOf(origin)));
        if (SoundCIT.debug()) {
            SoundCIT.LOGGER.info("[SoundCIT] {} -> {} (via {}, item {} \"{}\")",
                    soundId, replacement, resolved.layer(), resolved.itemId(), resolved.customName());
        }
    }

    /**
     * Builds the replacement instance. Entity-bound sounds must stay entity-bound or the custom
     * sound stops following its source (a trident in flight, a walking mob).
     */
    private static SoundInstance build(SoundInstance original, @Nullable SoundOrigin origin, ResourceLocation replacement) {
        float volume = origin != null ? origin.volume() : 1.0F;
        float pitch = origin != null ? origin.pitch() : 1.0F;
        long seed = origin != null ? origin.seed() : 0L;
        if (origin != null && origin.entity() != null) {
            return new ReplacedEntitySound(replacement, original.getSource(), volume, pitch, origin.entity(), seed);
        }
        double x = origin != null ? origin.x() : original.getX();
        double y = origin != null ? origin.y() : original.getY();
        double z = origin != null ? origin.z() : original.getZ();
        return new ReplacedSimpleSound(replacement, original.getSource(), volume, pitch, seed, x, y, z);
    }

    private static void record(Replacement replacement) {
        JOURNAL.addFirst(replacement);
        while (JOURNAL.size() > JOURNAL_LIMIT) {
            JOURNAL.removeLast();
        }
    }

    public static long getReplacementCount() {
        return replacementCount;
    }

    public static List<Replacement> journal() {
        return List.copyOf(JOURNAL);
    }

    /** True if a sound with this id was replaced by that id since the last reset. */
    public static boolean wasReplaced(String from, String to) {
        return JOURNAL.stream().anyMatch(r -> r.from().toString().equals(from) && r.to().toString().equals(to));
    }

    public static boolean wasAnyReplacementOf(String from) {
        return JOURNAL.stream().anyMatch(r -> r.from().toString().equals(from));
    }

    public static void resetJournal() {
        JOURNAL.clear();
        replacementCount = 0;
    }
}
