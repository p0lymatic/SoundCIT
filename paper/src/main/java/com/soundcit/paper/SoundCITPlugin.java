package com.soundcit.paper;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Server half of SoundCIT for Paper.
 *
 * <p>The mod on its own has to work out which item caused a sound from what the client can see,
 * which is guesswork for anything another player did. This plugin removes the guessing: it watches
 * the events that cause sounds and tells nearby clients which item was responsible.</p>
 *
 * <p>It deliberately does <em>not</em> know anything about sound packs. Rules live in each player's
 * resource pack, the server never sees them, and two players can run completely different packs.
 * All the server says is "this player's next hit comes from an item called X".</p>
 *
 * <p>Note on the design: the natural join key would be the sound's seed, but Bukkit does not expose
 * the seed of vanilla sounds (a long-standing Paper request), and reading it would mean intercepting
 * packets with an extra library. Instead the hint is keyed by entity and action, which the client
 * already tracks for its own predictions — no packet interception needed.</p>
 */
public final class SoundCITPlugin extends JavaPlugin {

    /** Must match the payload id the mod registers. */
    public static final String CHANNEL = "soundcit:sound_cause";
    /** Hint kinds, mirrored in the mod. */
    public static final byte KIND_KEYED = 0;
    public static final byte KIND_CONTEXT = 1;

    /** How far a hint is worth sending, in blocks. Beyond this the sound is inaudible anyway. */
    private static final double RANGE = 32.0;

    @Override
    public void onEnable() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getPluginManager().registerEvents(new SoundCauseListener(this), this);
        getLogger().info("SoundCIT server support enabled — clients with the mod will get exact"
                + " item attribution for sounds.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
    }

    /**
     * Tells every nearby player which item caused an action.
     *
     * <p>Only items with a custom name are worth sending: that is the only thing rules match on, so
     * anything else would be pure traffic. In ordinary play this sends almost nothing.</p>
     */
    public void sendCause(Player source, String trigger, ItemStack stack) {
        String customName = customNameOf(stack);
        if (customName == null) {
            return;
        }
        NamespacedKey itemKey = stack.getType().getKey();
        byte[] payload = encode(KIND_CONTEXT, source.getEntityId(), trigger,
                itemKey.toString(), customName);

        for (Player nearby : source.getWorld().getPlayers()) {
            if (nearby.getLocation().distanceSquared(source.getLocation()) > RANGE * RANGE) {
                continue;
            }
            // Sending on a channel the client never registered is silently dropped by the platform,
            // so players without the mod cost nothing and notice nothing.
            nearby.sendPluginMessage(this, CHANNEL, payload);
        }
    }

    private static String customNameOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        var meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        // Plain text: the mod compares names without formatting.
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName());
    }

    /**
     * Writes the payload exactly as the mod's codec reads it: a var-long key, a kind byte, then
     * three length-prefixed UTF-8 strings.
     */
    private static byte[] encode(byte kind, long key, String trigger, String itemId, String customName) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64);
        writeVarLong(out, key);
        out.write(kind);
        writeString(out, trigger);
        writeString(out, itemId);
        writeString(out, customName);
        return out.toByteArray();
    }

    private static void writeVarLong(ByteArrayOutputStream out, long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                out.write((int) value);
                return;
            }
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static void writeString(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.writeBytes(bytes);
    }
}
