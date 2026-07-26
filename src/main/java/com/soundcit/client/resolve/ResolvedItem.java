package com.soundcit.client.resolve;

import com.soundcit.trigger.TriggerType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The item a sound was attributed to, plus which resolver found it (kept for debug output — when a
 * replacement looks wrong, the layer says whether it was a fact or a guess).
 */
public record ResolvedItem(Identifier itemId, String customName, @Nullable TriggerType trigger,
        String layer, @Nullable ItemStack stack) {

    public ResolvedItem(Identifier itemId, String customName, @Nullable TriggerType trigger, String layer) {
        this(itemId, customName, trigger, layer, null);
    }

    /** The same attribution with the real stack attached, so conditions can be evaluated. */
    public ResolvedItem withStack(@Nullable ItemStack found) {
        return found == null ? this : new ResolvedItem(itemId, customName, trigger, layer, found);
    }
    public static final String LAYER_SERVER = "server";
    public static final String LAYER_ENTITY = "entity";
    public static final String LAYER_CONTEXT = "context";
    public static final String LAYER_PROXIMITY = "proximity";
}
