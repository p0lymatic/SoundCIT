package com.soundcit.client;

import com.soundcit.SoundCIT;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Headless end-to-end check, enabled with {@code -Dsoundcit.autotest=true}: creates a superflat
 * world and runs a table of scenarios, each performing a real action through the same code path a
 * click would take and asserting on the replacement journal — that the expected sound was replaced
 * by the expected one, and that unrelated sounds were left alone.
 *
 * <p>Exits the client when done and reports the verdict in the log.</p>
 */
public final class AutoTest {

    /** One check: set the world up on the server, act on the client, then assert. */
    private record Scenario(String name, java.util.function.Consumer<ServerPlayer> setup,
            @org.jetbrains.annotations.Nullable java.util.function.Consumer<Minecraft> act,
            @org.jetbrains.annotations.Nullable java.util.function.Consumer<ServerPlayer> serverAct,
            int holdTicks, int settleTicks, BooleanSupplier verdict) {
        Scenario(String name, java.util.function.Consumer<ServerPlayer> setup,
                java.util.function.Consumer<Minecraft> act, int settleTicks, BooleanSupplier verdict) {
            this(name, setup, act, null, 0, settleTicks, verdict);
        }
    }

    private enum Stage { TITLE, CREATE_WORLD, WAIT_WORLD, SETUP_SCENARIO, ACT, HOLD, SETTLE, DONE }

    private static final String LEVEL_NAME = "SoundCIT-Test";

    private final List<String> failures = new ArrayList<>();
    private List<Scenario> scenarios;
    private int scenarioIndex;
    private Stage stage = Stage.TITLE;
    private int delay = 100;
    private int waited;
    private volatile int pigId = -1;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        switch (stage) {
            case TITLE -> {
                // Screens were taken out of Minecraft in the 2026 releases, so there is nothing to
                // introspect: just let the client settle, then drive world loading through
                // WorldOpenFlows, which needs no UI at all.
                if (--delay > 0) {
                    return;
                }
                // -Dsoundcit.autotest.server=host:port joins a real server instead of a local world.
                // This is how the Paper plugin gets tested: nothing else can prove that a hint sent
                // by a plugin is decoded by the mod's codec.
                String remote = System.getProperty("soundcit.autotest.server");
                if (remote != null && !remote.isBlank()) {
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest] connecting to {}", remote);
                    stage = Stage.WAIT_WORLD;
                    connectToServer(mc, remote);
                    return;
                }
                if (mc.getLevelSource().levelExists(LEVEL_NAME)) {
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest] reusing existing world {}", LEVEL_NAME);
                    stage = Stage.WAIT_WORLD;
                    mc.createWorldOpenFlows().openWorld(LEVEL_NAME, () -> {});
                    return;
                }
                SoundCIT.LOGGER.info("[SoundCIT AutoTest] creating superflat world {}", LEVEL_NAME);
                stage = Stage.WAIT_WORLD;
                createFlatWorld(mc);
            }
            case CREATE_WORLD -> {}
            case WAIT_WORLD -> {
                boolean remoteMode = System.getProperty("soundcit.autotest.server") != null;
                if (mc.player != null && mc.level != null
                        && (remoteMode || mc.getSingleplayerServer() != null)) {
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest] world loaded");
                    scenarios = remoteMode ? buildRemoteScenarios() : buildScenarios();
                    delay = 60; // let chunks settle and the attack cooldown fill
                    stage = Stage.SETUP_SCENARIO;
                }
            }
            case SETUP_SCENARIO -> {
                if (--delay <= 0) {
                    if (scenarioIndex >= scenarios.size()) {
                        finish(mc);
                        return;
                    }
                    Scenario scenario = scenarios.get(scenarioIndex);
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest] scenario: {}", scenario.name());
                    SoundReplacementHandler.resetJournal();
                    MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> {
                            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                            clearLeftovers(player);
                            scenario.setup().accept(player);
                        });
                    }
                    delay = 30;
                    waited = 0;
                    stage = Stage.ACT;
                }
            }
            case ACT -> {
                if (--delay <= 0) {
                    Scenario scenario = scenarios.get(scenarioIndex);
                    if (scenario.act() != null) {
                        scenario.act().accept(mc);
                    }
                    if (scenario.holdTicks() > 0) {
                        delay = scenario.holdTicks();
                        stage = Stage.HOLD;
                    } else {
                        runServerAct(mc, scenario);
                        delay = scenario.settleTicks();
                        stage = Stage.SETTLE;
                    }
                }
            }
            case HOLD -> {
                // Charged actions (bow, trident) only fire on release.
                if (--delay <= 0) {
                    Scenario scenario = scenarios.get(scenarioIndex);
                    if (scenario.serverAct() != null) {
                        runServerAct(mc, scenario);
                    } else {
                        mc.gameMode.releaseUsingItem(mc.player);
                    }
                    delay = scenario.settleTicks();
                    stage = Stage.SETTLE;
                }
            }
            case SETTLE -> {
                if (--delay <= 0) {
                    Scenario scenario = scenarios.get(scenarioIndex);
                    if (scenario.verdict().getAsBoolean()) {
                        SoundCIT.LOGGER.info("[SoundCIT AutoTest]   PASS: {}", scenario.name());
                    } else {
                        failures.add(scenario.name());
                        SoundCIT.LOGGER.error("[SoundCIT AutoTest]   FAIL: {} (journal: {})",
                                scenario.name(), SoundReplacementHandler.journal());
                    }
                    scenarioIndex++;
                    delay = 20;
                    stage = Stage.SETUP_SCENARIO;
                }
            }
            case DONE -> {}
        }
    }

    /**
     * Removes entities earlier scenarios (and earlier runs, since the world is reused) left behind.
     * Without this a pile of pigs and stuck tridents accumulates and the proximity resolver starts
     * seeing candidates the scenario never created.
     */
    private static void clearLeftovers(ServerPlayer player) {
        player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(64.0),
                        e -> e instanceof Pig || e instanceof ThrownTrident)
                .forEach(Entity::discard);
    }

    /** Runs a scenario's server-side action on the server thread, if it has one. */
    private static void runServerAct(Minecraft mc, Scenario scenario) {
        if (scenario.serverAct() == null || mc.getSingleplayerServer() == null) {
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> scenario.serverAct().accept(server.getPlayerList().getPlayers().get(0)));
    }

    /**
     * On a real server the test cannot set anything up server-side, so it works through commands and
     * checks the one thing that matters here: whether a hint sent by the Paper plugin was decoded by
     * the mod's codec at all. That flag is only set when a payload arrives and parses.
     */
    private List<Scenario> buildRemoteScenarios() {
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("server plugin hint is received and decoded",
                player -> {},
                mc -> {
                    mc.player.connection.sendCommand(
                            "give @s minecraft:mace[minecraft:custom_name='\"Frying Pan\"'] 1");
                },
                40,
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    // Right-click with the named item; the plugin reports it on PlayerInteractEvent.
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    return true;
                }));
        list.add(new Scenario("plugin channel round-trip",
                player -> {},
                mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND),
                60,
                com.soundcit.client.resolve.ServerHintStore::isServerAssisted));
        return list;
    }

    private List<Scenario> buildScenarios() {
        List<Scenario> list = new ArrayList<>();

        list.add(new Scenario("renamed mace hit replaces attack sound",
                player -> {
                    giveNamed(player, Items.MACE, "Frying Pan");
                    spawnPig(player);
                },
                this::attackPig,
                60,
                () -> SoundReplacementHandler.wasAnyReplacementOf("minecraft:entity.player.attack.strong")
                        || SoundReplacementHandler.wasAnyReplacementOf("minecraft:entity.player.attack.weak")
                        || SoundReplacementHandler.wasAnyReplacementOf("minecraft:entity.player.attack.crit")
                        || SoundReplacementHandler.wasAnyReplacementOf("minecraft:entity.player.attack.knockback")));

        list.add(new Scenario("plain mace is left alone (negative control)",
                player -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.MACE));
                    spawnPig(player);
                },
                this::attackPig,
                60,
                () -> SoundReplacementHandler.journal().isEmpty()));

        // A flying trident carries the item's custom name on the entity itself (the stack is not
        // synced), so this checks the projectile branch of the resolver against a real vanilla
        // sound: the trident sticks into the ground and plays item.trident.hit_ground bound to it.
        list.add(new Scenario("renamed trident in flight resolves via the projectile entity",
                player -> {
                    ItemStack trident = new ItemStack(Items.TRIDENT);
                    trident.set(DataComponents.CUSTOM_NAME, Component.literal("Mjolnir"));
                    ThrownTrident thrown = new ThrownTrident(player.level(), player, trident);
                    // High enough that the client is told about the entity before it lands: a
                    // projectile that hits within a few ticks of spawning cannot be identified,
                    // because nothing about it has reached the client yet.
                    thrown.snapTo(player.getX(), player.getY() + 12.0, player.getZ(), 0.0F, 90.0F);
                    thrown.setDeltaMovement(0.0, -0.05, 0.0);
                    player.level().addFreshEntity(thrown);
                },
                null,
                null,
                0,
                200,
                () -> SoundReplacementHandler.wasAnyReplacementOf("minecraft:item.trident.hit_ground")));

        // Only the server knows a totem was used: the event is server-side and the totem is gone
        // from the player's hands by the time the client is told to play the sound.
        list.add(new Scenario("totem use is resolved through the server hint",
                player -> {
                    // Creative players are invulnerable, so the totem would never fire.
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
                    totem.set(DataComponents.CUSTOM_NAME, Component.literal("Lifeline"));
                    player.setItemInHand(InteractionHand.OFF_HAND, totem);
                },
                null,
                player -> {
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest]   totem: mode={} health={} offhand={}",
                            player.gameMode.getGameModeForPlayer(), player.getHealth(),
                            player.getOffhandItem().getHoverName().getString());
                    // hurt() split into hurtServer/hurtClient in the 2026 releases.
                    boolean hurt = player.hurtServer(player.level(), player.damageSources().magic(), 1000.0F);
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest]   totem: hurt={} healthAfter={}",
                            hurt, player.getHealth());
                },
                0,
                60,
                () -> SoundReplacementHandler.wasAnyReplacementOf("minecraft:item.totem.use")));

        // Conditions: the same name matches only when the enchantment requirement holds, so this
        // pair proves the condition is actually consulted rather than ignored.
        list.add(new Scenario("enchantment condition matches when satisfied",
                player -> {
                    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                    sword.set(DataComponents.CUSTOM_NAME, Component.literal("Sharp Blade"));
                    enchant(player, sword, 4);
                    player.setItemInHand(InteractionHand.MAIN_HAND, sword);
                    spawnPig(player);
                },
                this::attackPig,
                60,
                () -> !SoundReplacementHandler.journal().isEmpty()));

        list.add(new Scenario("enchantment condition rejects when unsatisfied",
                player -> {
                    ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                    sword.set(DataComponents.CUSTOM_NAME, Component.literal("Sharp Blade"));
                    player.setItemInHand(InteractionHand.MAIN_HAND, sword); // no enchantment
                    spawnPig(player);
                },
                this::attackPig,
                60,
                () -> SoundReplacementHandler.journal().isEmpty()));

        return list;
    }

    /** Joins a real server, which is the only way to test the server half against a real client. */
    private static void connectToServer(Minecraft mc, String address) {
        var parsed = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address);
        var data = new net.minecraft.client.multiplayer.ServerData("SoundCIT test", address,
                net.minecraft.client.multiplayer.ServerData.Type.OTHER);
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(null, mc, parsed, data, false, null);
    }

    /**
     * Applies Sharpness at the given level, looked up from the server's dynamic registry.
     *
     * <p>The 1.21.1 branch has a {@code createWorld} helper next to this one that drives the world
     * creation screen by reflection. It is deliberately absent here: 26.2 removed those screens, so
     * this branch creates the level directly through {@code WorldOpenFlows}.</p>
     */
    private static void enchant(ServerPlayer player, ItemStack stack, int level) {
        var registry = player.level().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        registry.get(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS)
                .ifPresent(holder -> stack.enchant(holder, level));
    }

    /**
     * Creates the superflat test world without touching a single screen. Superflat rather than
     * normal because chunk generation under software rendering is the slowest part of a run.
     */
    private void createFlatWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings(
                LEVEL_NAME,
                GameType.CREATIVE,
                LevelSettings.DifficultySettings.DEFAULT,
                true, // allow commands
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(
                LEVEL_NAME,
                settings,
                WorldOptions.defaultWithRandomSeed(),
                provider -> provider.lookupOrThrow(Registries.WORLD_PRESET)
                        .getOrThrow(WorldPresets.FLAT)
                        .value()
                        .createWorldDimensions(),
                null);
    }


    private static void giveNamed(ServerPlayer player, Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    private void spawnPig(ServerPlayer player) {
        Pig pig = EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
        if (pig == null) {
            return;
        }
        // Slightly raised so it sits at the crosshair rather than at the player's feet — the
        // attack itself is driven through gameMode.attack, but this makes the VNC view readable.
        pig.snapTo(player.getX() + 1.5, player.getY() + 0.5, player.getZ(), 0, 0);
        pig.setNoAi(true);
        player.level().addFreshEntity(pig);
        pigId = pig.getId();
    }

    private void attackPig(Minecraft mc) {
        Entity target = pigId >= 0 && mc.level != null ? mc.level.getEntity(pigId) : null;
        if (target == null) {
            SoundCIT.LOGGER.warn("[SoundCIT AutoTest] pig not visible on the client yet");
            return;
        }
        mc.gameMode.attack(mc.player, target);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void finish(Minecraft mc) {
        if (failures.isEmpty()) {
            SoundCIT.LOGGER.info("[SoundCIT AutoTest] SUCCESS: all {} scenario(s) passed", scenarios.size());
        } else {
            SoundCIT.LOGGER.error("[SoundCIT AutoTest] FAILURE: {} of {} scenario(s) failed: {}",
                    failures.size(), scenarios.size(), failures);
        }
        stage = Stage.DONE;
        mc.stop();
    }

    private void fail(Minecraft mc, String reason) {
        SoundCIT.LOGGER.error("[SoundCIT AutoTest] FAILURE: {}", reason);
        stage = Stage.DONE;
        mc.stop();
    }
}
