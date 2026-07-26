package com.soundcit.client;

import com.mojang.brigadier.Command;
import com.soundcit.SoundCIT;
import com.soundcit.client.resolve.ServerHintStore;
import com.soundcit.config.RuleManager;
import com.soundcit.config.SoundRule;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * {@code /soundcit list} — loaded rules.
 * {@code /soundcit debug} — verbose logging of every resolution decision, including refusals.
 * {@code /soundcit why} — the last replacements and which layer resolved each.
 */
public final class ClientCommands {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("soundcit")
                .then(Commands.literal("debug").executes(ctx -> {
                    SoundCIT.setDebug(!SoundCIT.debug());
                    reply(ctx.getSource(), "SoundCIT debug logging: " + (SoundCIT.debug() ? "ON" : "OFF"));
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    if (RuleManager.isEmpty()) {
                        reply(ctx.getSource(), "SoundCIT: no rules loaded");
                    } else {
                        reply(ctx.getSource(), "SoundCIT rules ("
                                + (ServerHintStore.isServerAssisted() ? "server-assisted" : "client-only") + "):");
                        for (SoundRule rule : RuleManager.getRules()) {
                            reply(ctx.getSource(), "  " + rule.source + "  pattern: " + rule.nameMatcher
                                    + (rule.priority != 0 ? "  priority: " + rule.priority : ""));
                        }
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("why").executes(ctx -> {
                    var journal = SoundReplacementHandler.journal();
                    if (journal.isEmpty()) {
                        reply(ctx.getSource(), "SoundCIT: nothing replaced yet."
                                + " Use /soundcit debug to log why sounds are not being matched.");
                        return Command.SINGLE_SUCCESS;
                    }
                    reply(ctx.getSource(), "SoundCIT: last " + journal.size() + " replacement(s), newest first:");
                    for (SoundReplacementHandler.Replacement r : journal) {
                        reply(ctx.getSource(), "  " + r.from() + " -> " + r.to() + "  [" + r.layer() + "]");
                    }
                    return Command.SINGLE_SUCCESS;
                })));
    }

    private static void reply(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GRAY), false);
    }

    private ClientCommands() {}
}
