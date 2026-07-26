package com.soundcit.client;

import com.soundcit.SoundCIT;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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
    private int delay;
    private int waited;
    private volatile int pigId = -1;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        switch (stage) {
            case TITLE -> {
                if (mc.screen instanceof AccessibilityOnboardingScreen) {
                    mc.options.onboardAccessibility = false;
                    mc.options.save();
                    mc.setScreen(new TitleScreen());
                    return;
                }
                if (mc.screen instanceof TitleScreen) {
                    // Generating a superflat world costs ~40s under software rendering, so reuse
                    // the one a previous run left behind.
                    if (mc.getLevelSource().levelExists(LEVEL_NAME)) {
                        SoundCIT.LOGGER.info("[SoundCIT AutoTest] reusing existing world {}", LEVEL_NAME);
                        stage = Stage.WAIT_WORLD;
                        mc.createWorldOpenFlows().openWorld(LEVEL_NAME, () -> mc.setScreen(new TitleScreen()));
                        return;
                    }
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest] opening world creation screen");
                    CreateWorldScreen.openFresh(mc, null);
                    stage = Stage.CREATE_WORLD;
                }
            }
            case CREATE_WORLD -> createWorld(mc);
            case WAIT_WORLD -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                    SoundCIT.LOGGER.info("[SoundCIT AutoTest] world loaded");
                    scenarios = buildScenarios();
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
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        clearLeftovers(player);
                        scenario.setup().accept(player);
                    });
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
        player.serverLevel().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(64.0),
                        e -> e instanceof Pig || e instanceof ThrownTrident)
                .forEach(Entity::discard);
    }

    /** Runs a scenario's server-side action on the server thread, if it has one. */
    private static void runServerAct(Minecraft mc, Scenario scenario) {
        if (scenario.serverAct() == null) {
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> scenario.serverAct().accept(server.getPlayerList().getPlayers().get(0)));
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
                    ThrownTrident thrown = new ThrownTrident(player.serverLevel(), player, trident);
                    // High enough that the client is told about the entity before it lands: a
                    // projectile that hits within a few ticks of spawning cannot be identified,
                    // because nothing about it has reached the client yet.
                    thrown.moveTo(player.getX(), player.getY() + 12.0, player.getZ(), 0.0F, 90.0F);
                    thrown.setDeltaMovement(0.0, -0.05, 0.0);
                    player.serverLevel().addFreshEntity(thrown);
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
                    boolean hurt = player.hurt(player.damageSources().magic(), 1000.0F);
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

    /** Applies Sharpness at the given level, looked up from the server's dynamic registry. */
    private static void enchant(ServerPlayer player, ItemStack stack, int level) {
        var registry = player.serverLevel().registryAccess()
                .lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        registry.get(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS)
                .ifPresent(holder -> stack.enchant(holder, level));
    }

    private void createWorld(Minecraft mc) {
        if (!(mc.screen instanceof CreateWorldScreen screen)) {
            return;
        }
        WorldCreationUiState ui = screen.getUiState();
        ui.setName(LEVEL_NAME);
        ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
        ui.setAllowCommands(true);
        ui.getNormalPresetList().stream()
                .filter(e -> e.preset() != null && e.preset().is(WorldPresets.FLAT))
                .findFirst()
                .ifPresent(ui::setWorldType);
        try {
            Method onCreate = CreateWorldScreen.class.getDeclaredMethod("onCreate");
            onCreate.setAccessible(true);
            SoundCIT.LOGGER.info("[SoundCIT AutoTest] creating superflat world");
            stage = Stage.WAIT_WORLD;
            onCreate.invoke(screen);
        } catch (ReflectiveOperationException e) {
            fail(mc, "cannot invoke CreateWorldScreen.onCreate: " + e);
        }
    }

    private static void giveNamed(ServerPlayer player, Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    private void spawnPig(ServerPlayer player) {
        Pig pig = EntityType.PIG.create(player.serverLevel());
        if (pig == null) {
            return;
        }
        // Slightly raised so it sits at the crosshair rather than at the player's feet — the
        // attack itself is driven through gameMode.attack, but this makes the VNC view readable.
        pig.moveTo(player.getX() + 1.5, player.getY() + 0.5, player.getZ(), 0, 0);
        pig.setNoAi(true);
        player.serverLevel().addFreshEntity(pig);
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
