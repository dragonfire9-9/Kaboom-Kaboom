package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.KeybindSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/** Hold a key to zoom in smoothly, like a spyglass without the item. */
public final class Zoom extends Module {
    public static Zoom INSTANCE;
    private final KeybindSetting key = add(new KeybindSetting("Zoom Key", "Hold to zoom", GLFW.GLFW_KEY_C));
    private final NumberSetting factor = add(new NumberSetting("Zoom", "How far to zoom in", 4, 1.5, 12, 0.5, "x"));
    private final NumberSetting speed = add(new NumberSetting("Speed", "How fast the zoom eases in and out", 10, 2, 30, 1));
    private final BoolSetting cinematic = add(new BoolSetting("Cinematic Camera", "Smooth the camera while zoomed", true));
    private final BoolSetting hideHand = add(new BoolSetting("Hide Hand", "Hide your hand while zoomed", true));
    private final BoolSetting scroll = add(new BoolSetting("Scroll To Zoom", "Scroll while zoomed to zoom further in or out", true));
    private final BoolSetting lowerSens = add(new BoolSetting("Lower Sensitivity", "Turn slower the further you zoom", true));
    private float scrollFactor = 1;
    private Double savedSens;

    private float amount; // 0..1 eased
    private long last = System.nanoTime();
    private boolean smoothWas, hadSmooth;

    public Zoom() {
        super("Zoom", "Hold a key to zoom in smoothly.", Category.VISUAL);
        INSTANCE = this;
    }

    private boolean held() {
        int k = key.get();
        if (k == KeybindSetting.NONE || mc.currentScreen != null) return false;
        long w = mc.getWindow().getHandle();
        return k <= GLFW.GLFW_MOUSE_BUTTON_LAST ? GLFW.glfwGetMouseButton(w, k) == GLFW.GLFW_PRESS : InputUtil.isKeyPressed(mc.getWindow(), k);
    }

    @Override
    public void onTick() {
        boolean zooming = held();
        if (!zooming) scrollFactor = 1;
        if (lowerSens.isOn() && zooming) {
            if (savedSens == null) savedSens = mc.options.getMouseSensitivity().getValue();
            mc.options.getMouseSensitivity().setValue(savedSens / Math.max(1, Math.sqrt(factor.get() * scrollFactor)));
        } else if (savedSens != null) {
            mc.options.getMouseSensitivity().setValue(savedSens);
            savedSens = null;
        }
        if (cinematic.isOn()) {
            if (zooming && !hadSmooth) { smoothWas = mc.options.smoothCameraEnabled; mc.options.smoothCameraEnabled = true; hadSmooth = true; }
            else if (!zooming && hadSmooth) { mc.options.smoothCameraEnabled = smoothWas; hadSmooth = false; }
        }
    }

    /** Mouse wheel while zoomed (see MouseMixin); returns true if consumed. */
    public static boolean onScroll(double amount) {
        Zoom z = INSTANCE;
        if (z == null || !z.isEnabled() || !z.scroll.isOn() || !z.held()) return false;
        z.scrollFactor = (float) Math.max(0.25, Math.min(6, z.scrollFactor * (amount > 0 ? 1.2 : 1 / 1.2)));
        return true;
    }

    @Override
    protected void onDisable() {
        if (savedSens != null) mc.options.getMouseSensitivity().setValue(savedSens);
        savedSens = null;
        if (hadSmooth) { mc.options.smoothCameraEnabled = smoothWas; hadSmooth = false; }
        amount = 0;
    }

    /** Called from the FOV mixin every frame. */
    public static float apply(float fov) {
        Zoom z = INSTANCE;
        if (z == null) return fov;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - z.last) / 1e9f);
        z.last = now;
        float goal = z.isEnabled() && z.held() ? 1 : 0;
        z.amount += (goal - z.amount) * (1 - (float) Math.exp(-z.speed.get() * dt));
        if (z.amount < 0.001f) return fov;
        return fov / (1 + (z.factor.getFloat() * z.scrollFactor - 1) * z.amount);
    }

    public static boolean hideHand() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.hideHand.isOn() && INSTANCE.amount > 0.3f;
    }
}
