package com.soundcit.client;

import com.soundcit.config.RuleManager;
import com.soundcit.config.SoundRule;
import com.soundcit.context.TriggerSounds;
import com.soundcit.trigger.TriggerType;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Explains what the mod would do with the item in the player's hand: which rules match it, which
 * sounds each would replace, and which rule wins.
 *
 * <p>This is the answer to the question every pack author asks first — "why isn't my rule firing?"
 * — without making them read a log or guess at the matching syntax.</p>
 */
public final class InspectCommand {

    /** Renders the report as chat lines. */
    public static void run(java.util.function.Consumer<Component> out) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getMainHandItem();
        if (stack.isEmpty()) {
            out.accept(grey("SoundCIT: hold an item in your main hand to inspect it."));
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String customName = RuleManager.customNameOf(stack);
        out.accept(Component.literal("SoundCIT: " + itemId).withStyle(ChatFormatting.WHITE));
        if (customName == null) {
            out.accept(grey("  no custom name — rules match on the name, so nothing can apply."
                    + " Rename it on an anvil first."));
            return;
        }
        out.accept(grey("  custom name: \"" + customName + "\""));

        boolean any = false;
        for (SoundRule rule : RuleManager.getRules()) {
            if (!rule.appliesTo(itemId, customName)) {
                continue;
            }
            any = true;
            out.accept(Component.literal("  matches " + rule.source
                            + (rule.priority != 0 ? "  (priority " + rule.priority + ")" : ""))
                    .withStyle(ChatFormatting.GREEN));
            for (Map.Entry<TriggerType, ResourceLocation> entry : rule.triggerSounds.entrySet()) {
                out.accept(grey("    " + entry.getKey().name().toLowerCase(java.util.Locale.ROOT)
                        + " -> " + entry.getValue() + describeTrigger(entry.getKey())));
            }
            for (Map.Entry<ResourceLocation, ResourceLocation> entry : rule.directOverrides.entrySet()) {
                out.accept(grey("    " + entry.getKey() + " -> " + entry.getValue()));
            }
        }
        if (!any) {
            out.accept(Component.literal("  no rule matches this item")
                    .withStyle(ChatFormatting.YELLOW));
            out.accept(grey("  check the \"item\" and \"pattern\" fields; the name is compared in full,"
                    + " so use ipattern:*part* to match a fragment."));
        }
    }

    /** A hint of how many vanilla sounds a trigger covers, so its breadth is not a surprise. */
    private static String describeTrigger(TriggerType trigger) {
        int exact = TriggerSounds.exactSounds(trigger).size();
        int families = TriggerSounds.families(trigger).size();
        if (exact == 0 && families == 0) {
            return "  (no vanilla sounds known for this trigger)";
        }
        return families > 0 ? "  (" + exact + " sound(s) + families)" : "  (" + exact + " sound(s))";
    }

    private static Component grey(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private InspectCommand() {}
}
