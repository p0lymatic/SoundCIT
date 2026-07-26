package com.soundcit.context;

import org.jetbrains.annotations.Nullable;

/**
 * Hands a {@link SoundOrigin} from the mixin that observed the sound being emitted to the
 * {@code PlaySoundEvent} handler a few frames down the same call stack.
 *
 * <p>A stack rather than a single slot: a mod listening to {@code PlaySoundEvent} may play another
 * sound from inside the handler, which would otherwise consume the outer sound's origin. Entries
 * live only for the duration of one synchronous call, so nothing leaks when a sound is discarded
 * before reaching the engine.</p>
 *
 * <p>Client sounds are all emitted on the render thread; a {@link ThreadLocal} keeps a stray
 * off-thread sound from another mod out of our stack rather than corrupting it.</p>
 */
public final class SoundOriginStack {
    private static final int MAX_DEPTH = 4;

    private static final ThreadLocal<SoundOrigin[]> STACK = ThreadLocal.withInitial(() -> new SoundOrigin[MAX_DEPTH]);
    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    private SoundOriginStack() {}

    public static void push(SoundOrigin origin) {
        int[] depth = DEPTH.get();
        if (depth[0] >= MAX_DEPTH) {
            return;
        }
        STACK.get()[depth[0]++] = origin;
    }

    public static void pop() {
        int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            STACK.get()[--depth[0]] = null;
        }
    }

    /** The innermost origin, or null if the sound reached the engine without passing a known emitter. */
    @Nullable
    public static SoundOrigin current() {
        int[] depth = DEPTH.get();
        return depth[0] > 0 ? STACK.get()[depth[0] - 1] : null;
    }

    /**
     * Marks the current origin as used, so a sound played from inside a {@code PlaySoundEvent}
     * handler cannot claim it a second time.
     */
    public static void consume() {
        int[] depth = DEPTH.get();
        if (depth[0] > 0) {
            STACK.get()[depth[0] - 1] = null;
        }
    }
}
