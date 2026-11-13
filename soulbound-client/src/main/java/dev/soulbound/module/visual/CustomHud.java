package dev.soulbound.module.visual;

import dev.soulbound.hud.HudEditorScreen;
import dev.soulbound.hud.HudElement;
import dev.soulbound.hud.elements.ArmorElement;
import dev.soulbound.hud.elements.CoordsElement;
import dev.soulbound.hud.elements.KeybindsElement;
import dev.soulbound.hud.elements.KeystrokesElement;
import dev.soulbound.hud.elements.ModuleListElement;
import dev.soulbound.hud.elements.PotionsElement;
import dev.soulbound.hud.elements.WatermarkElement;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.ActionSetting;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;

import java.util.List;

/** SoulBound's in-game HUD: a set of movable elements styled like the menu. */
public final class CustomHud extends Module {
    public final List<HudElement> elements = List.of(
            new WatermarkElement(), new ModuleListElement(), new CoordsElement(), new KeybindsElement(),
            new PotionsElement(), new ArmorElement(), new KeystrokesElement());

    public final NumberSetting background;
    public final BoolSetting hideVanillaEffects;

    public CustomHud() {
        super("Custom HUD", "SoulBound's HUD: watermark, modules, armor, effects, coords, keys.", Category.VISUAL);
        add(new ActionSetting("Layout", "Drag elements around the screen", "Edit Layout",
                () -> mc.setScreen(new HudEditorScreen(mc.currentScreen))));
        for (HudElement e : elements) add(e.enabled);
        background = add(new NumberSetting("Background", "Opacity of element backgrounds", 0.75, 0, 1, 0.05));
        hideVanillaEffects = add(new BoolSetting("Hide Vanilla Effects", "Hide Minecraft's effect icons while Potions is on", true));
        for (HudElement e : elements) {
            add(e.posX);
            add(e.posY);
        }
        setEnabled(true);
    }

    @Override public String getIcon() { return "icon_visual"; }
}
