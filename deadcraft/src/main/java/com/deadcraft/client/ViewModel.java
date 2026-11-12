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
            "gun_right", new float[]{0.0f, -0.08f, 0.0f, 10f, 0f, 0f},
            "gun_left", new float[]{1.48f, -0.08f, -0.95f, -60f, -30f, 40f},
            "melee_right", new float[]{0.02f, 0.06f, 0.04f, 0f, 0f, 0f},
            "ads", new float[]{-0.66f, 0.08f, -0.05f, 0f, 0f, 0f},
            "gun_base", new float[]{0f, 0f, 0f, 0f, 0f, 0f},
            "blade_base", new float[]{-0.06f, 0.07f, 0.0f, 0f, 0f, 0f},
            "blade_item", new float[]{-0.12f, 0.06f, 0.0f, 0f, 0f, 0f, 0.9f}
    ));
    /** Per-weapon placement: dx dy dz rx ry rz scale (item), applied around the held item. */
    private static final Map<String, float[]> ITEM = new HashMap<>(Map.of(
            "rusted_revolver", new float[]{0.02f, -0.04f, 0.05f, 0f, 172f, 0f, 0.8f},
            "doomsday_shotgun", new float[]{0.05f, -0.06f, 0.0f, 0f, 172f, 0f, 0.92f},
            "marksman_rifle", new float[]{0.05f, -0.06f, 0.0f, 0f, 172f, 0f, 0.92f},
            "scrap_smg", new float[]{0.05f, -0.06f, 0.0f, 0f, 172f, 0f, 0.92f}
    ));
    /** Where the muzzle sits in view space for the first-person flash: x y z size. */
    private static final Map<String, float[]> MUZZLE = new HashMap<>(Map.of(
            "rusted_revolver", new float[]{0.48f, -0.27f, -1.00f, 0.28f},
            "doomsday_shotgun", new float[]{0.46f, -0.23f, -1.20f, 0.40f},
            "marksman_rifle", new float[]{0.31f, -0.21f, -1.30f, 0.32f},
            "scrap_smg", new float[]{0.31f, -0.24f, -1.10f, 0.30f}
    ));
    static {
        TUNE.put("left:rusted_revolver", new float[]{1.25f, -0.16f, -0.55f, -40f, -30f, 40f});
    }

    private static long lastCheck, lastModified;
    private static float aim;
    private static long firedAt;
    private static float flashRoll;

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
                        if (p.length < 4 || p[0].startsWith("#")) continue;
                        float[] v = new float[p.length - 1];
                        for (int i = 0; i < v.length; i++) v[i] = Float.parseFloat(p[i + 1]);
                        if (p[0].startsWith("item:")) ITEM.put(p[0].substring(5), v);
                        else if (p[0].startsWith("muzzle:")) MUZZLE.put(p[0].substring(7), v);
                        else TUNE.put(p[0], v);
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
        // the whole rig (arms and weapon) brought up and in, so the weapon reads clearly on screen
        float[] base = tune(gun ? "gun_base" : "blade_base");
        pose.translate(base[0] * side * (1 - aim), base[1] * (1 - aim), base[2] * (1 - aim));
        if (gun) {
            float[] ads = tune("ads");
            pose.translate(ads[0] * side * aim, ads[1] * aim, ads[2] * aim);
        }
        // the weapon, placed exactly as vanilla places a held item
        pose.pushPose();
        pose.translate(side * 0.56f, -0.52f + equip * -0.22f, -0.72f);
        if (blade) SwingStyle.of(main).firstPerson(pose, side, swing);
        String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(main.getItem()).getPath();
        tune("");
        float[] it = ITEM.containsKey(key) ? ITEM.get(key) : blade ? tune("blade_item") : null;
        if (it != null) {
            pose.translate(it[0] * side, it[1], it[2]);
            pose.mulPose(Axis.XP.rotationDegrees(it[3]));
            pose.mulPose(Axis.YP.rotationDegrees(it[4] * side));
            pose.mulPose(Axis.ZP.rotationDegrees(it[5] * side));
            if (it.length > 6) pose.scale(it[6], it[6], it[6]);
        }
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, main,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, pose, buffers, light);
        pose.popPose();

        // the hand on the grip (follows the weapon's swing)
        pose.pushPose();
        tune("");
        float[] t = TUNE.containsKey("right:" + key) ? TUNE.get("right:" + key) : tune(gun ? "gun_right" : "melee_right");
        pose.translate(0, equip * -0.22f, 0);
        if (blade) {
            pose.translate(side * 0.56f, -0.52f, -0.72f);
            SwingStyle.of(main).firstPerson(pose, side, swing);
            pose.translate(-side * 0.56f, 0.52f, 0.72f);
        }
        arm(pose, buffers, light, renderer, player, right, t);
        pose.popPose();

        // the muzzle flash, for the two frames after a shot
        if (gun && System.currentTimeMillis() - firedAt < 75) {
            float[] m = MUZZLE.get(key);
            if (m != null) flash(pose, buffers, m[0] * side, m[1] + equip * -0.22f, m[2], m[3]);
        }

        // the supporting hand under the barrel
        if (gun) {
            pose.pushPose();
            pose.translate(0, equip * -0.22f, 0);
            float[] l = TUNE.containsKey("left:" + key) ? TUNE.get("left:" + key) : tune("gun_left");
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

    private static final net.minecraft.resources.ResourceLocation FLASH = com.deadcraft.DeadCraft.id("textures/misc/muzzle_flash_fp.png");

    public static void fired(int gun) {
        firedAt = System.currentTimeMillis();
        flashRoll = (float) (Math.random() * 360);
    }

    /** A bright, camera-facing star at the muzzle plus a side-on cone. */
    private static void flash(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float size) {
        var vc = buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(FLASH));
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.ZP.rotationDegrees(flashRoll));
        quad(pose, vc, size);
        pose.mulPose(Axis.YP.rotationDegrees(90));
        pose.scale(1f, 0.55f, 1f);
        pose.translate(0, 0, 0);
        quad(pose, vc, size * 1.3f);
        pose.popPose();
    }

    private static void quad(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer vc, float s) {
        var p = pose.last();
        float h = s / 2;
        float[][] v = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
        for (float[] c : v) {
            vc.addVertex(p, c[0], c[1], 0).setColor(255, 214, 150, 255).setUv(c[2], c[3])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(p, 0, 0, 1);
        }
    }

    /** How far the view is pulled toward aiming (for the FOV zoom). */
    public static float aim() {
        return aim;
    }
}
