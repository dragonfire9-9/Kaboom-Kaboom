package com.kaboom.jjk.client;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.Abilities;
import com.kaboom.jjk.ability.AbilityHandler;
import com.kaboom.jjk.ability.DomainHandler;
import com.kaboom.jjk.data.CENature;
import com.kaboom.jjk.data.CETier;
import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.Technique;
import com.kaboom.jjk.item.ModItems;
import com.kaboom.jjk.network.SyncJJKDataPayload;
import com.kaboom.jjk.roll.RollService;
import com.kaboom.jjk.roll.RollType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.function.Consumer;

/**
 * CI-only automation (enabled with {@code -Djjk.showcase=true}). Creates a flat creative world,
 * walks through the mod's features and saves screenshots to {@code run/screenshots/}, then quits.
 * Does nothing in normal play.
 */
@EventBusSubscriber(modid = JJKMod.MODID, value = Dist.CLIENT)
public final class ClientShowcase {
    private static final boolean ENABLED = Boolean.getBoolean("jjk.showcase");
    private static boolean worldRequested;
    private static int t = -1;

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();

        if (!worldRequested && mc.screen instanceof TitleScreen) {
            worldRequested = true;
            shot(mc, "00_title.png");
            createWorld(mc);
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        t++;

        switch (t) {
            case 40 -> server(mc, (server, p) -> {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule doDaylightCycle false");
                p.setYRot(0);
                p.setXRot(8);
                CursedEnergyData d = ModAttachments.get(p);
                d.setClan(Clan.GOJO);
                d.setTechnique(Technique.LIMITLESS, CETier.MASSIVE, CENature.PRISTINE);
                d.setGrade(Grade.GRADE_1);
                d.setRctUnlocked(true);
                d.setCurrentCE(d.getMaxCE() * 0.72);
                d.setCooldown("red", 120);
                d.setCooldown("hollow_purple", 700);
                for (int i = 0; i < 4; i++) {
                    Mob m = EntityType.HUSK.create(p.serverLevel());
                    if (m == null) continue;
                    m.moveTo(p.getX() - 3 + i * 2, p.getY(), p.getZ() + 9, 180, 0);
                    m.setNoAi(true);
                    p.serverLevel().addFreshEntity(m);
                }
                SyncJJKDataPayload.sync(p);
            });
            case 90 -> shot(mc, "01_hud_limitless.png");
            case 95 -> server(mc, (s, p) -> AbilityHandler.tryActivate(p, ModAttachments.get(p), Abilities.get("blue")));
            case 99 -> shot(mc, "02_blue.png");
            case 130 -> server(mc, (s, p) -> RollService.start(p, new ItemStack(ModItems.CURSED_ENERGY_SCROLL.get()), RollType.CURSED_ENERGY));
            case 150 -> shot(mc, "03_roll_spinning.png");
            case 230 -> shot(mc, "04_roll_reveal_technique.png");
            case 240 -> mc.setScreen(null);
            case 250 -> server(mc, (s, p) -> RollService.start(p, new ItemStack(ModItems.CLAN_SCROLL.get()), RollType.CLAN));
            case 345 -> shot(mc, "05_roll_reveal_clan.png");
            case 355 -> mc.setScreen(null);
            case 365 -> server(mc, (s, p) -> RollService.start(p, new ItemStack(ModItems.HEAVENLY_RESTRICTION_SCROLL.get()), RollType.HEAVENLY_RESTRICTION));
            case 460 -> shot(mc, "06_roll_reveal_restriction.png");
            case 470 -> mc.setScreen(null);
            case 480 -> server(mc, (s, p) -> {
                DomainHandler.tryExpand(p);
                ModAttachments.get(p).setCurrentCE(ModAttachments.get(p).getMaxCE());
            });
            case 484 -> shot(mc, "07_domain_pulse.png");
            case 520 -> server(mc, (s, p) -> {
                p.setGameMode(GameType.SURVIVAL);
                p.getInventory().clearContent();
                ModItems.ITEMS.getEntries().forEach(e -> {
                    if (e.get() != ModItems.JJK_EMBLEM.get()) p.getInventory().add(new ItemStack(e.get()));
                });
            });
            case 540 -> mc.setScreen(new InventoryScreen(mc.player));
            case 550 -> shot(mc, "08_items.png");
            case 560 -> {
                mc.setScreen(null);
                server(mc, (s, p) -> {
                    p.setGameMode(GameType.CREATIVE);
                    com.kaboom.jjk.event.SukunaTakeover.begin(p, ModAttachments.get(p), 400);
                });
            }
            case 575 -> {
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            }
            case 590 -> shot(mc, "09_sukuna_takeover.png");
            case 620 -> mc.stop();
            default -> {}
        }
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("jjk_showcase", GameType.CREATIVE, false, Difficulty.EASY, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(42L, false, false);
        mc.createWorldOpenFlows().createFreshLevel("jjk_showcase", settings, options,
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    private static void server(Minecraft mc, java.util.function.BiConsumer<MinecraftServer, ServerPlayer> action) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        var uuid = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) action.accept(server, p);
        });
    }

    private static void shot(Minecraft mc, String name) {
        Consumer<net.minecraft.network.chat.Component> log = c -> JJKMod.LOGGER.info("[showcase] {}", c.getString());
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), log);
    }

    private ClientShowcase() {}
}
