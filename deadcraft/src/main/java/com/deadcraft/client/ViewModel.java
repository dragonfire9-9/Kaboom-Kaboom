package com.deadcraft.client;

import com.deadcraft.client.anim.SwingStyle;
import com.deadcraft.item.DeadWeaponItem;
import com.deadcraft.item.GunItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * First-person view models with the player's own arms. A gun is held in both hands (right hand on
 * the grip, left hand under the barrel) and is brought up to the eye while sneaking to aim down
 * the sights; a melee weapon is held in a visible hand. The arms use the player's skin and sleeves.
 *
 * <p>Arm placement is a list of offsets layered on vanilla's first-person arm pose. They can be
 * tuned live by writing {@code deadcraft_viewmodel.txt} in the game folder (lines of
 * {@code key dx dy dz rx ry rz}); the file is re-read when it changes.
 */
public final class ViewModel {
    private ViewModel() {}

    /** dx, dy, dz (blocks) then rx, ry, rz (degrees), applied after vanilla's arm placement. */
    private static final Map<String, float[]> TUNE = new HashMap<>(Map.of(
            "gun_right", new float[]{0.06f, 0.10f, 0.08f, 0f, 0f, 0f},
            "gun_left", new float[]{1.55f, 0.16f, -0.60f, -5f, -10f, 32f},
            "melee_right", new float[]{0.02f, 0.06f, 0.04f, 0f, 0f, 0f},
            "ads", new float[]{-0.66f, 0.08f, -0.05f, 0f, 0f, 0f}
    ));
    private static long lastCheck, lastModified;
    private static float aim;

    private static float[] tune(String key) {
        long now = System.currentTimeMillis();
        if (now - lastCheck > 1000) {
            lastCheck = now;
            Path file = FMLPaths.GAMEDIR.get().resolve("deadcraft_viewmodel.txt");
            try {
                if (Files.exists(file) && Files.getLastModifiedTime(file).toMillis() != lastModified) {
                    lastModified = Files.getLastModifiedTime(file).toMillis();
                    for (String line : Files.readAllLines(file)) {
                        String[] p = line.trim().split("\\s+");
                        if (p.length < 7 || p[0].startsWith("#")) continue;
                        float[] v = new float[6];
                        for (int i = 0; i < 6; i++) v[i] = Float.parseFloat(p[i + 1]);
                        TUNE.put(p[0], v);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return TUNE.getOrDefault(key, new float[6]);
    }

    /** Returns true if the hand was drawn here (and the event should be cancelled). */
    public static boolean render(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || player.isInvisible()) return false;
        ItemStack main = player.getMainHandItem();
        boolean gun = main.getItem() instanceof GunItem;
        boolean blade = main.getItem() instanceof DeadWeaponItem;
        if (!gun && !blade) return false;
        // with a gun both hands are on it, so the off-hand item is not drawn
        if (event.getHand() == InteractionHand.OFF_HAND) return gun;

        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float equip = event.getEquipProgress();
        float swing = event.getSwingProgress();
        boolean right = player.getMainArm() == HumanoidArm.RIGHT;
        float side = right ? 1f : -1f;
        var renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);

        // aiming down the sights while sneaking with a gun
        aim = Mth.lerp(0.18f, aim, gun && player.isShiftKeyDown() ? 1f : 0f);

        pose.pushPose();
        if (gun) {
            float[] ads = tune("ads");
            pose.translate(ads[0] * side * aim, ads[1] * aim, ads[2] * aim);
        }
        // the weapon, placed exactly as vanilla places a held item
        pose.pushPose();
        pose.translate(side * 0.56f, -0.52f + equip * (blade ? -0.22f : -0.6f), -0.72f);
        if (blade) SwingStyle.of(main).firstPerson(pose, side, swing);
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, main,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, pose, buffers, light);
        pose.popPose();

        // the hand on the grip (follows the weapon's swing)
        pose.pushPose();
        float[] t = tune(gun ? "gun_right" : "melee_right");
        pose.translate(0, equip * (blade ? -0.22f : -0.6f), 0);
        if (blade) {
            pose.translate(side * 0.56f, -0.52f, -0.72f);
            SwingStyle.of(main).firstPerson(pose, side, swing);
            pose.translate(-side * 0.56f, 0.52f, 0.72f);
        }
        arm(pose, buffers, light, renderer, player, right, t);
        pose.popPose();

        // the supporting hand under the barrel
        if (gun) {
            pose.pushPose();
            pose.translate(0, equip * -0.6f, 0);
            float[] l = tune("gun_left");
            arm(pose, buffers, light, renderer, player, !right, new float[]{l[0] * side, l[1], l[2], l[3], l[4] * side, l[5] * side});
            pose.popPose();
        }
        pose.popPose();
        return true;
    }

    /** Vanilla's first-person arm pose, with our offsets layered on, then the arm itself. */
    private static void arm(PoseStack pose, MultiBufferSource buffers, int light, PlayerRenderer renderer,
                            AbstractClientPlayer player, boolean rightArm, float[] t) {
        float f = rightArm ? 1f : -1f;
        pose.translate(t[0], t[1], t[2]);
        pose.translate(f * 0.64000005f, -0.6f, -0.71999997f);
        pose.mulPose(Axis.YP.rotationDegrees(f * 45.0f));
        pose.mulPose(Axis.XP.rotationDegrees(t[3]));
        pose.mulPose(Axis.YP.rotationDegrees(t[4]));
        pose.mulPose(Axis.ZP.rotationDegrees(t[5]));
        pose.translate(f * -1.0f, 3.6f, 3.5f);
        pose.mulPose(Axis.ZP.rotationDegrees(f * 120.0f));
        pose.mulPose(Axis.XP.rotationDegrees(200.0f));
        pose.mulPose(Axis.YP.rotationDegrees(f * -135.0f));
        pose.translate(f * 5.6f, 0.0f, 0.0f);
        if (rightArm) renderer.renderRightHand(pose, buffers, light, player);
        else renderer.renderLeftHand(pose, buffers, light, player);
    }

    /** How far the view is pulled toward aiming (for the FOV zoom). */
    public static float aim() {
        return aim;
    }
}
