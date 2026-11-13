package dev.soulbound;

import dev.soulbound.config.ClientSettings;
import dev.soulbound.config.ConfigManager;
import dev.soulbound.gui.ClickGuiScreen;
import dev.soulbound.gui.SoulTitleScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import dev.soulbound.hud.BotHud;
import dev.soulbound.hud.HudRenderer;
import dev.soulbound.module.ModuleManager;
import dev.soulbound.module.bots.BotModule;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.util.Identifier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SoulBound implements ClientModInitializer {
    public static final String MOD_ID = "soulbound";
    public static final String NAME = "SoulBound";
    public static final String VERSION = "1.0";
    public static final Logger LOGGER = LoggerFactory.getLogger(NAME);

    private static ModuleManager modules;
    private static ClientSettings settings;
    private static ConfigManager config;

    @Override
    public void onInitializeClient() {
        settings = new ClientSettings();
        modules = new ModuleManager();
        modules.init();
        config = new ConfigManager();
        config.load();

        HudRenderer.register();
        BotHud botHud = new BotHud(modules.all().stream().filter(m -> m instanceof BotModule).map(m -> (BotModule) m).toList());
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "bot_hud"), botHud::render);

        // a way back from the classic title screen
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen && settings.ui.titleScreen.isOn()) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("SoulBound Menu"), b -> {
                    SoulTitleScreen.useVanilla = false;
                    client.setScreen(new SoulTitleScreen());
                }).dimensions(6, 6, 100, 20).build());
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) modules.onTick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
        LOGGER.info("{} {} loaded with {} modules", NAME, VERSION, modules.all().size());
    }

    /** Raw key press while no screen is open (see KeyboardMixin). */
    public static void onKeyPressed(int key) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (settings.ui.guiKey.isBound() && key == settings.ui.guiKey.get()) {
            // deferred so this same key press isn't also delivered to the new screen (which would close it)
            mc.send(() -> mc.setScreen(new ClickGuiScreen()));
            return;
        }
        modules.onKey(key);
    }

    public static ModuleManager modules() { return modules; }
    public static ClientSettings settings() { return settings; }
    public static ConfigManager config() { return config; }
}
