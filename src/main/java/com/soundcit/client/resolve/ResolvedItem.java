package com.soundcit.client.resolve;

import com.soundcit.trigger.TriggerType;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The item a sound was attributed to, plus which resolver found it (kept for debug output — when a
 * replacement looks wrong, the layer says whether it was a fact or a guess).
 */
public record ResolvedItem(Identifier itemId, String customName, @Nullable TriggerType trigger, String layer) {
    public static final String LAYER_SERVER = "server";
    public static final String LAYER_ENTITY = "entity";
    public static final String LAYER_CONTEXT = "context";
    public static final String LAYER_PROXIMITY = "proximity";
}
