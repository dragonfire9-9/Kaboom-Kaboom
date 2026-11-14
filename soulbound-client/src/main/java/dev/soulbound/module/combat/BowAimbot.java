package dev.soulbound.module.combat;

import dev.soulbound.aim.AimController;
import dev.soulbound.aim.AimSettings;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Items;
import net.minecraft.item.TridentItem;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** While you draw a bow, crossbow or trident, aims along the arc so the shot lands, leading moving targets. */
public final class BowAimbot extends Module {
    private final ModeSetting targets, priority, aimPart;
    private final NumberSetting range, fov, lead;
    private final BoolSetting throughWalls, ignoreInvisible, trident, autoRelease;
    private final NumberSetting releaseCharge;
    private final AimSettings aim;
    private LivingEntity target;

    public BowAimbot() {
        super("Bow Aimbot", "Aims your bow shots along the arc, ahead of moving targets.", Category.COMBAT);
        add(new HeaderSetting("TARGETS"));
        targets = add(new ModeSetting("Targets", "What to aim at", "Players", "Players", "Mobs", "All"));
        priority = add(new ModeSetting("Priority", "Which target first", "Crosshair", "Crosshair", "Closest", "Lowest Health"));
        range = add(new NumberSetting("Range", "How far to look for targets", 60, 8, 120, 1, "m"));
        fov = add(new NumberSetting("FOV", "Only targets inside this cone", 90, 10, 360, 5, "°"));
        throughWalls = add(new BoolSetting("Through Walls", "Aim at targets you can't see", false));
        ignoreInvisible = add(new BoolSetting("Ignore Invisible", "Skip invisible entities", true));
        add(new HeaderSetting("SHOT"));
        aimPart = add(new ModeSetting("Aim At", "Where on the body", "Chest", "Head", "Chest", "Feet"));
        lead = add(new NumberSetting("Lead", "How much to lead moving targets (1 = exact)", 1, 0, 2, 0.05, "x"));
        trident = add(new BoolSetting("Tridents", "Also aim thrown tridents", true));
        autoRelease = add(new BoolSetting("Auto Release", "Let go once the shot is lined up and charged", false));
        releaseCharge = add(new NumberSetting("Release Charge", "Draw at least this much first", 1, 0.2, 1, 0.05).visibleWhen(autoRelease::isOn));
        aim = new AimSettings(this, 600, 0.5, 60);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.currentScreen != null || !p.isUsingItem()) {
            target = null;
            return;
        }
        var item = p.getActiveItem().getItem();
        boolean bow = item instanceof BowItem, cross = item instanceof CrossbowItem, tri = trident.isOn() && item instanceof TridentItem;
        if (!bow && !cross && !tri) return;
        target = pick();
        if (target == null) return;
        double v;
        if (bow) {
            double f = p.getItemUseTime() / 20.0;
            f = Math.min(1, (f * f + f * 2) / 3);
            v = Math.max(0.3, f * 3);
        } else {
            v = cross ? 3.15 : 2.5;
        }
        Vec3d at = part(target);
        Vec3d eye = p.getEyePos();
        double flight = eye.distanceTo(at) / v;
        Vec3d vel = new Vec3d(target.getX() - target.lastX, 0, target.getZ() - target.lastZ);
        at = at.add(vel.multiply(flight * lead.get()));
        Vec3d point = ballistic(eye, at, v, tri ? 0.05 : 0.05);
        AimController.request(this, point, aim);
        if (autoRelease.isOn() && bow && BowItem.getPullProgress(p.getItemUseTime()) >= releaseCharge.getFloat() && AimController.errorTo(point) < 1.2f) {
            mc.options.useKey.setPressed(false);
        }
    }

    private Vec3d part(LivingEntity e) {
        var b = e.getBoundingBox();
        double h = b.maxY - b.minY;
        double y = switch (aimPart.get()) {
            case "Head" -> b.minY + h * 0.85;
            case "Feet" -> b.minY + 0.1;
            default -> b.minY + h * 0.55;
        };
        return new Vec3d(b.getCenter().x, y, b.getCenter().z);
    }

    private LivingEntity pick() {
        var p = mc.player;
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == p || !le.isAlive() || p.distanceTo(le) > range.get()) continue;
            if (ignoreInvisible.isOn() && le.isInvisible()) continue;
            if (le instanceof PlayerEntity pe) {
                if (targets.is("Mobs") || pe.isCreative() || pe.isSpectator() || p.isTeammate(pe)) continue;
            } else if (targets.is("Players") || !(le instanceof Monster)) continue;
            float err = AimController.errorTo(le.getBoundingBox().getCenter());
            float[] a = AimController.angles(p.getEyePos(), le.getBoundingBox().getCenter());
            if (fov.get() < 360 && Math.abs(MathHelper.wrapDegrees(a[0] - p.getYaw())) > fov.get() / 2) continue;
            if (!throughWalls.isOn() && !p.canSee(le)) continue;
            double s = switch (priority.get()) {
                case "Closest" -> p.squaredDistanceTo(le);
                case "Lowest Health" -> le.getHealth();
                default -> err;
            };
            if (s < bestScore) {
                bestScore = s;
                best = le;
            }
        }
        return best;
    }

    private static Vec3d ballistic(Vec3d eye, Vec3d at, double v, double g) {
        double dx = Math.hypot(at.x - eye.x, at.z - eye.z), dy = at.y - eye.y;
        double disc = v * v * v * v - g * (g * dx * dx + 2 * dy * v * v);
        double theta = disc < 0 ? Math.toRadians(40) : Math.atan((v * v - Math.sqrt(disc)) / (g * dx));
        float yaw = (float) Math.toDegrees(Math.atan2(at.z - eye.z, at.x - eye.x)) - 90f;
        float ry = (float) Math.toRadians(yaw), rp = (float) -theta;
        Vec3d dir = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        return eye.add(dir.multiply(10));
    }
}
