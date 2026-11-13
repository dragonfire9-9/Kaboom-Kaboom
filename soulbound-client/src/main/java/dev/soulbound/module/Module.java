package dev.soulbound.module;

import dev.soulbound.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;

/**
 * Base class for every module. Subclass it, add settings in the constructor with {@link #add},
 * and register the instance in {@link ModuleManager#init()}.
 */
public abstract class Module extends Configurable {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final Category category;
    private final KeybindSetting keybind = new KeybindSetting("Keybind", "Toggles the module", KeybindSetting.NONE);
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        super(name, description);
        this.category = category;
    }

    public Category getCategory() { return category; }

    /** Small caps tag above the title, e.g. "COMBAT MODULE". */
    public String getTag() { return category.displayName.toUpperCase(java.util.Locale.ROOT) + " MODULE"; }

    /** Optional badge next to the title, e.g. "BETA". */
    public String getBadge() { return null; }

    /** Icon name in textures/gui (used by the bots strip). */
    public String getIcon() { return category.icon; }
    public KeybindSetting getKeybind() { return keybind; }
    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void toggle() { setEnabled(!enabled); }

    protected void onEnable() {}
    protected void onDisable() {}
    /** Called every client tick while enabled. */
    public void onTick() {}
}
