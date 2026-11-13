package dev.soulbound.module.combat;

import dev.soulbound.aim.AimController;
import dev.soulbound.aim.AimSettings;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Gently pulls your aim toward the nearest target inside a view cone, using natural per-frame aiming. */
public final class AimAssist extends Module {
    private final ModeSetting targets, aimPoint;
    private final NumberSetting fov, distance;
    private final BoolSetting onlyClicking, onlyWeapon, sticky, throughWalls;
    private final AimSettings aim;
    private LivingEntity locked;

    public AimAssist() {
        super("Aim Assist", "Smoothly helps your aim onto nearby targets.", Category.COMBAT);
        add(new HeaderSetting("TARGETING"));
        targets = add(new ModeSetting("Targets", "What to assist onto", "Players", "Players", "Mobs", "All"));
        fov = add(new NumberSetting("FOV", "Only assist inside this cone", 90, 10, 360, 5, "°"));
        distance = add(new NumberSetting("Distance", "Maximum target distance", 4.5, 2, 8, 0.1, "m"));
        aimPoint = add(new ModeSetting("Aim Point", "Where on the body to pull toward", "Chest", "Head", "Chest", "Legs", "Closest"));
        sticky = add(new BoolSetting("Sticky Target", "Stay on one target while it's valid", true));
        throughWalls = add(new BoolSetting("Through Walls", "Assist onto targets behind blocks", false));
        aim = new AimSettings(this, 160, 0.75);
        aim.jitter.set(0.15);
        add(new HeaderSetting("CONDITIONS"));
        onlyClicking = add(new BoolSetting("Only While Clicking", "Assist only while attacking", true));
        onlyWeapon = add(new BoolSetting("Only With Weapon", "Only while holding a sword or axe", false));
    }

    @Override public String getIcon() { return "icon_target"; }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null) return;
        if (onlyClicking.isOn() && !mc.options.attackKey.isPressed()) return;
        if (onlyWeapon.isOn() && !p.getMainHandStack().isIn(ItemTags.SWORDS) && !p.getMainHandStack().isIn(ItemTags.AXES)) return;
        // bots take priority over the assist
        if (AimController.isActive() && !AimController.isOwnedBy(this)) return;

        if (!(sticky.isOn() && locked != null && valid(p, locked))) {
            locked = null;
            float best = Float.MAX_VALUE;
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || !valid(p, le)) continue;
                float err = AimController.errorTo(le.getBoundingBox().getCenter());
                if (err < best) {
                    best = err;
                    locked = le;
                }
            }
        }
        if (locked != null) AimController.request(this, point(p, locked), aim);
    }

    private Vec3d point(ClientPlayerEntity p, LivingEntity t) {
        Box b = t.getBoundingBox();
        double h = b.maxY - b.minY;
        Vec3d c = b.getCenter();
        return switch (aimPoint.get()) {
            case "Head" -> new Vec3d(c.x, b.minY + h * 0.88, c.z);
            case "Legs" -> new Vec3d(c.x, b.minY + h * 0.3, c.z);
            case "Closest" -> new Vec3d(MathHelper.clamp(p.getEyePos().x, b.minX, b.maxX),
                    MathHelper.clamp(p.getEyePos().y, b.minY, b.maxY), MathHelper.clamp(p.getEyePos().z, b.minZ, b.maxZ));
            default -> new Vec3d(c.x, b.minY + h * 0.62, c.z);
        };
    }

    private boolean valid(ClientPlayerEntity p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isInvisible()) return false;
        if (p.distanceTo(e) > distance.get()) return false;
        if (e instanceof PlayerEntity pe) {
            if (targets.is("Mobs") || pe.isCreative() || p.isTeammate(pe)) return false;
        } else if (targets.is("Players") || !(e instanceof Monster)) {
            return false;
        }
        float[] a = AimController.angles(p.getEyePos(), e.getBoundingBox().getCenter());
        if (Math.abs(MathHelper.wrapDegrees(a[0] - p.getYaw())) > fov.get() / 2) return false;
        return throughWalls.isOn() || p.canSee(e);
    }

    @Override
    protected void onDisable() { locked = null; }
}
