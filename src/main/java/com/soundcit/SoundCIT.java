package com.soundcit;

import com.mojang.logging.LogUtils;
import com.soundcit.network.SoundCausePayload;
import com.soundcit.network.SoundCitNetwork;
import com.soundcit.server.ServerActionHooks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * SoundCIT — CIT (Custom Item Textures) but for sounds.
 *
 * <p>Items renamed to match a rule from a resource pack play custom sounds instead of their vanilla
 * ones. The replacement itself is client-side, and the mod works on a vanilla server. Installing it
 * on the server as well is optional and only improves accuracy: the server then tells clients
 * exactly which item caused each sound.</p>
 */
@Mod(SoundCIT.MODID)
public class SoundCIT {
    public static final String MODID = "soundcit";
    public static final Logger LOGGER = LogUtils.getLogger();

    /** When true, every resolution decision is logged. Toggle with -Dsoundcit.debug=true or /soundcit debug. */
    private static volatile boolean debug = Boolean.getBoolean("soundcit.debug");

    public SoundCIT(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(SoundCitNetwork::register);
        NeoForge.EVENT_BUS.register(ServerActionHooks.class);
    }

    /**
     * Handles a server hint. Lives here rather than in the client package so the network layer,
     * which is common to both sides, does not reference client-only classes.
     */
    public static void onSoundCause(SoundCausePayload payload) {
        com.soundcit.client.resolve.ServerHintStore.accept(payload);
    }

    public static boolean debug() {
        return debug;
    }

    public static void setDebug(boolean value) {
        debug = value;
    }
}
