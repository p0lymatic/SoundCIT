package com.soundcit.client;

import com.soundcit.SoundCIT;
import com.soundcit.config.SoundRuleLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client entry point: registers the rule reload listener (F3+T hot reload), the sound
 * replacement handler and the action hooks.
 */
@Mod(value = SoundCIT.MODID, dist = Dist.CLIENT)
public class SoundCITClient {
    public SoundCITClient(IEventBus modEventBus) {
        modEventBus.addListener(this::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.register(SoundReplacementHandler.class);
        NeoForge.EVENT_BUS.register(ActionHooks.class);
        NeoForge.EVENT_BUS.register(ClientCommands.class);
        if (Boolean.getBoolean("soundcit.autotest") || "true".equals(System.getenv("SOUNDCIT_AUTOTEST"))) {
            NeoForge.EVENT_BUS.register(new AutoTest());
        }
    }

    private void onRegisterReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(SoundCIT.MODID, "rules"), new SoundRuleLoader());
    }
}
