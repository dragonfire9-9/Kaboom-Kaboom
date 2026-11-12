package com.deadcraft.client.anim;

import com.deadcraft.item.DeadWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;

/**
 * How the player swings whatever is in their hand, shared by the first-person view model and the
 * third-person body: knives stab, heavy blades and axes chop overhead, swords cut a horizontal arc,
 * tools swing like a pick, and an empty hand throws a jab.
 */
public enum SwingStyle {
    STAB, OVERHEAD, ARC, TOOL, JAB, VANILLA;

    public static SwingStyle of(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.isEmpty()) return JAB;
        if (item instanceof DeadWeaponItem dw) return dw.isKnife() ? STAB : dw.isHeavy() ? OVERHEAD : ARC;
        if (item instanceof AxeItem) return OVERHEAD;
        if (item instanceof SwordItem) return ARC;
        if (item instanceof DiggerItem) return TOOL;
        return VANILLA;
    }

    /**
     * First-person transform for the swing, applied after the arm is placed. {@code side} is +1 for
     * the right hand and -1 for the left. Returns false to let vanilla handle it.
     */
    public boolean firstPerson(PoseStack pose, float side, float swing) {
        if (swing <= 0) return this != VANILLA && this != TOOL && this != JAB;
        float s = Mth.sin(swing * Mth.PI);
        switch (this) {
            case STAB -> {
                pose.translate(-0.12f * side * s, 0.06f * s, -0.5f * s);
                pose.mulPose(Axis.XP.rotationDegrees(-25f * s));
                pose.mulPose(Axis.YP.rotationDegrees(15f * side * s));
            }
            case OVERHEAD -> {
                float up = swing < 0.35f ? swing / 0.35f : 1f - (swing - 0.35f) / 0.65f;
                float chop = swing < 0.35f ? 0f : Mth.sin((swing - 0.35f) / 0.65f * Mth.PI);
                pose.translate(-0.18f * side * up - 0.22f * side * chop, 0.12f * up - 0.18f * chop, -0.12f * chop);
                pose.mulPose(Axis.ZP.rotationDegrees(22f * side * up));
                pose.mulPose(Axis.XP.rotationDegrees(-55f * chop));
            }
            case ARC -> {
                float y = Mth.lerp(swing, 20f, -55f);
                pose.translate(-0.42f * side * swing, 0.05f * s, -0.12f * s);
                pose.mulPose(Axis.YP.rotationDegrees(y * side));
                pose.mulPose(Axis.ZP.rotationDegrees(-40f * side * s));
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
