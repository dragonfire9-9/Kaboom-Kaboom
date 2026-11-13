package dev.soulbound.gui;

import net.minecraft.client.MinecraftClient;

/** Opens the schematic picker: through the star-swirl transition when the menu is open. */
public final class SchematicPortal {
    private SchematicPortal() {}

    public static void open() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof ClickGuiScreen cg) cg.beginPortal();
        else mc.setScreen(new SchematicScreen(true));
    }
}
