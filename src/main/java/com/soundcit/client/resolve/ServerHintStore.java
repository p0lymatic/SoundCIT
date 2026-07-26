package com.soundcit.client.resolve;

import com.soundcit.context.SoundContextTracker;
import com.soundcit.network.SoundCausePayload;
import com.soundcit.trigger.TriggerType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Hints received from a SoundCIT-aware server, keyed by the sound's seed.
 *
 * <p>Matching on the seed rather than position or arrival order makes this immune to packet
 * reordering and to the 1/8-block quantisation of sound positions: either the seed matches or it
 * does not.</p>
 */
public final class ServerHintStore {
    private static final int CAPACITY = 64;
    /** Hints outlive their sound by a moment at most; anything older is stale. */
    private static final int TTL_TICKS = 20;

    private record Hint(ResolvedItem item, int expiresAtTick) {}

    private static final Map<Long, Hint> BY_SEED = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Hint> eldest) {
            return size() > CAPACITY;
        }
    };

    private static volatile boolean serverAssisted;

    private ServerHintStore() {}

    public static void accept(SoundCausePayload payload) {
        serverAssisted = true;
        TriggerType trigger = TriggerType.byName(payload.trigger());
        if (trigger == null) {
            return; // a newer server knows a trigger this client does not; ignore rather than guess
        }
        if (payload.kind() == SoundCausePayload.KIND_CONTEXT) {
            // Not about one sound but about an entity's next action of this type — exactly what the
            // client records for its own predictions, so it goes in the same place.
            SoundContextTracker.pushRemote(trigger, payload.itemId(), payload.customName(),
                    (int) payload.key());
            return;
        }
        ResolvedItem item = new ResolvedItem(payload.itemId(), payload.customName(),
                trigger, ResolvedItem.LAYER_SERVER);
        BY_SEED.put(payload.key(), new Hint(item, SoundContextTracker.currentTick() + TTL_TICKS));
    }

    @Nullable
    public static ResolvedItem take(long seed) {
        Hint hint = BY_SEED.remove(seed);
        if (hint == null) {
            return null;
        }
        return SoundContextTracker.currentTick() > hint.expiresAtTick() ? null : hint.item();
    }

    /** True once this connection has delivered at least one hint. */
    public static boolean isServerAssisted() {
        return serverAssisted;
    }

    public static void clear() {
        BY_SEED.clear();
        serverAssisted = false;
    }
}
