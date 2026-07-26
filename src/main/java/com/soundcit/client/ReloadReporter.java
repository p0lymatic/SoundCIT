package com.soundcit.client;

import com.soundcit.config.RuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Tells the player what happened on the last resource reload.
 *
 * <p>Pack authors press F3+T constantly and, until now, had to alt-tab to the log to find out
 * whether their edit even parsed. The report is only shown when something is worth saying: a broken
 * rule always, a successful reload only when rules are actually loaded.</p>
 */
public final class ReloadReporter {
    private static int lastSeenGeneration = -1;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        int generation = RuleManager.generation();
        if (generation == lastSeenGeneration) {
            return;
        }
        // First world join also counts as a reload; skip announcing the very first one.
        boolean first = lastSeenGeneration == -1;
        lastSeenGeneration = generation;
        if (first) {
            return;
        }

        int ruleCount = RuleManager.getRules().size();
        var problems = RuleManager.getProblems();
        if (problems.isEmpty()) {
            if (ruleCount > 0) {
                say(minecraft, Component.literal("SoundCIT: " + ruleCount + " rule(s) reloaded")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        say(minecraft, Component.literal("SoundCIT: " + ruleCount + " rule(s) loaded, "
                + problems.size() + " failed").withStyle(ChatFormatting.RED));
        for (String problem : problems) {
            say(minecraft, Component.literal("  " + problem).withStyle(ChatFormatting.RED));
        }
    }

    private static void say(Minecraft minecraft, Component message) {
        // 26.2 moved the chat behind Gui#hud and made addMessage private; addClientSystemMessage is
        // the public entry point, and is the right one here — this is the mod talking, not a server.
        minecraft.gui.hud.getChat().addClientSystemMessage(message);
    }

    private ReloadReporter() {}
}
