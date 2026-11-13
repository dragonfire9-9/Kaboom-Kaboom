package dev.soulbound.hud;

import dev.soulbound.SoulBound;
import dev.soulbound.gui.render.Anim;
import dev.soulbound.module.visual.CustomHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/** Draws the Custom HUD and tracks mouse clicks for the CPS counters. */
public final class HudRenderer {
    private static CustomHud hud;
    private static final Anim.Value show = new Anim.Value(0);
    private static final Deque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
    private static boolean leftDown, rightDown;

    private HudRenderer() {}

    public static void register() {
        hud = SoulBound.modules().get(CustomHud.class);
        HudElementRegistry.addLast(Identifier.of(SoulBound.MOD_ID, "custom_hud"), HudRenderer::render);
        HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (ctx, tick) -> {
            boolean hide = hud.isEnabled() && hud.hideVanillaEffects.isOn() && potions().enabled.isOn();
            if (!hide) vanilla.render(ctx, tick);
        });
    }

    private static HudElement potions() {
        for (HudElement e : hud.elements) if (e instanceof dev.soulbound.hud.elements.PotionsElement) return e;
        return hud.elements.getFirst();
    }

    public static double background() { return hud == null ? 0.75 : hud.background.get(); }

    private static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        pollMouse(mc);
        if (mc.options.hudHidden || mc.currentScreen instanceof HudEditorScreen) return;
        Anim.frame();
        float s = show.to(hud.isEnabled() ? 1 : 0, 10);
        if (s < 0.01f) return;
        renderAll(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), false, Anim.easeOutCubic(s));
    }

    static void renderAll(DrawContext ctx, float sw, float sh, boolean editing, float alpha) {
        for (HudElement e : hud.elements) {
            if (!e.enabled.isOn() && !editing) continue;
            if (!e.layout(editing)) continue;
            e.render(ctx, sw, sh, editing, alpha * (e.enabled.isOn() ? 1 : 0.35f));
        }
    }

    static CustomHud hud() { return hud; }

    // ------------------------------------------------------------------ clicks per second
    private static void pollMouse(MinecraftClient mc) {
        long win = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS && mc.currentScreen == null;
        boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS && mc.currentScreen == null;
        long now = System.currentTimeMillis();
        if (l && !leftDown) left.add(now);
        if (r && !rightDown) right.add(now);
        leftDown = l;
        rightDown = r;
        while (!left.isEmpty() && now - left.peekFirst() > 1000) left.pollFirst();
        while (!right.isEmpty() && now - right.peekFirst() > 1000) right.pollFirst();
    }

    public static int leftCps() { return left.size(); }
    public static int rightCps() { return right.size(); }
    public static boolean leftDown() { return leftDown; }
    public static boolean rightDown() { return rightDown; }
}
