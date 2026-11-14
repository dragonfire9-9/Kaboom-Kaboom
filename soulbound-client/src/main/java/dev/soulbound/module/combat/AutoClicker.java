package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/** Clicks for you while you hold a mouse button, at a randomised, human-shaped rate. */
public final class AutoClicker extends Module {
    private final BoolSetting left, right, breakBlocks, onlyWeapon, onlyEntity, inScreens;
    private final NumberSetting leftMin, leftMax, rightMin, rightMax, dropChance, spikeChance;
    private final ModeSetting pattern;
    private long nextLeft, nextRight;
    private double burst = 1;

    public AutoClicker() {
        super("Auto Clicker", "Clicks for you while you hold a mouse button.", Category.COMBAT);
        add(new HeaderSetting("LEFT CLICK"));
        left = add(new BoolSetting("Left Click", "Click while holding left", true));
        leftMin = add(new NumberSetting("Left Min CPS", "Slowest left click rate", 10, 1, 25, 0.5).visibleWhen(left::isOn));
        leftMax = add(new NumberSetting("Left Max CPS", "Fastest left click rate", 14, 1, 25, 0.5).visibleWhen(left::isOn));
        breakBlocks = add(new BoolSetting("Break Blocks", "Hold still (no clicking) while mining a block", true).visibleWhen(left::isOn));
        onlyWeapon = add(new BoolSetting("Only With Weapon", "Only with a sword or axe in hand", false).visibleWhen(left::isOn));
        onlyEntity = add(new BoolSetting("Only On Entities", "Only while aiming at an entity", false).visibleWhen(left::isOn));
        add(new HeaderSetting("RIGHT CLICK"));
        right = add(new BoolSetting("Right Click", "Click while holding right", false));
        rightMin = add(new NumberSetting("Right Min CPS", "Slowest right click rate", 12, 1, 25, 0.5).visibleWhen(right::isOn));
        rightMax = add(new NumberSetting("Right Max CPS", "Fastest right click rate", 16, 1, 25, 0.5).visibleWhen(right::isOn));
        add(new HeaderSetting("HUMANISE"));
        pattern = add(new ModeSetting("Pattern", "How the rate wanders: Butterfly is bursty, Jitter is even, Drag ramps up", "Jitter", "Jitter", "Butterfly", "Drag", "Flat"));
        dropChance = add(new NumberSetting("Drop Chance", "Chance of a short pause, like a slipped finger", 3, 0, 20, 1, "%"));
        spikeChance = add(new NumberSetting("Spike Chance", "Chance of a quick double click", 4, 0, 20, 1, "%"));
        inScreens = add(new BoolSetting("In Inventories", "Also click inside inventory screens", false));
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (mc.currentScreen != null && !inScreens.isOn()) return;
        long w = mc.getWindow().getHandle(), now = System.currentTimeMillis();
        boolean l = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(w, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        if (left.isOn() && l && leftAllowed()) {
            while (now >= nextLeft) {
                click(mc.options.attackKey);
                nextLeft = Math.max(nextLeft, now - 200) + delay(leftMin.get(), leftMax.get());
            }
        } else {
            nextLeft = now;
        }
        if (right.isOn() && r) {
            while (now >= nextRight) {
                click(mc.options.useKey);
                nextRight = Math.max(nextRight, now - 200) + delay(rightMin.get(), rightMax.get());
            }
        } else {
            nextRight = now;
        }
    }

    private boolean leftAllowed() {
        if (breakBlocks.isOn() && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) return false;
        if (onlyEntity.isOn() && (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY)) return false;
        return !onlyWeapon.isOn() || mc.player.getMainHandStack().isIn(ItemTags.SWORDS) || mc.player.getMainHandStack().isIn(ItemTags.AXES);
    }

    private static void click(KeyBinding kb) { KeyBinding.onKeyPressed(KeyBindingHelper.getBoundKeyOf(kb)); }

    private long delay(double min, double max) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double lo = Math.min(min, max), hi = Math.max(min, max);
        double cps = switch (pattern.get()) {
            case "Butterfly" -> {
                if (r.nextDouble() < 0.12) burst = r.nextDouble(0.8, 1.25);
                yield (lo + r.nextDouble() * (hi - lo)) * burst;
            }
            case "Drag" -> {
                burst = Math.min(1.25, burst + 0.02);
                if (r.nextDouble() < 0.05) burst = 0.85;
                yield (lo + (hi - lo) * 0.5) * burst;
            }
            case "Flat" -> (lo + hi) / 2;
            default -> Math.max(lo, Math.min(hi, (lo + hi) / 2 + r.nextGaussian() * (hi - lo) / 4));
        };
        long ms = Math.round(1000.0 / Math.max(1, cps));
        if (r.nextDouble() * 100 < dropChance.get()) ms += r.nextLong(60, 160);
        if (r.nextDouble() * 100 < spikeChance.get()) ms = Math.max(25, ms / 3);
        return ms;
    }
}
