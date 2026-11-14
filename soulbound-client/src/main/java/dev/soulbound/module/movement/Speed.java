package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;

/** Moves faster: auto bunny-hop, ground strafe boost, or a straight multiplier. */
public final class Speed extends Module {
    private final ModeSetting mode;
    private final NumberSetting speed, groundBoost;
    private final BoolSetting onlyMoving, notInWater, notSneaking, keepSprint;

    public Speed() {
        super("Speed", "Move faster on the ground.", Category.MOVEMENT);
        mode = add(new ModeSetting("Mode", "Bhop jumps for you and strafes in the air; Strafe boosts ground speed; Vanilla scales your speed", "Bhop", "Bhop", "Strafe", "Vanilla"));
        speed = add(new NumberSetting("Speed", "Target speed (vanilla sprint is about 0.28)", 0.32, 0.2, 1, 0.01, " b/t"));
        groundBoost = add(new NumberSetting("Ground Boost", "Extra push on each landing (Bhop)", 1.0, 1, 1.6, 0.05, "x").visibleWhen(() -> mode.is("Bhop")));
        onlyMoving = add(new BoolSetting("Only Moving", "Only while you press a movement key", true));
        keepSprint = add(new BoolSetting("Force Sprint", "Sprint the whole time", true));
        notInWater = add(new BoolSetting("Not In Water", "Off while swimming", true));
        notSneaking = add(new BoolSetting("Not Sneaking", "Off while sneaking", true));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || p.getAbilities().flying || p.hasVehicle() || p.isGliding()) return;
        if (notInWater.isOn() && (p.isTouchingWater() || p.isInLava())) return;
        if (notSneaking.isOn() && p.isSneaking()) return;
        Vec3d input = moveInput();
        if (onlyMoving.isOn() && input.lengthSquared() < 1e-4) return;
        if (keepSprint.isOn() && p.getHungerManager().getFoodLevel() > 6) p.setSprinting(true);
        double s = speed.get();
        if (p.hasStatusEffect(StatusEffects.SPEED)) s *= 1 + 0.2 * (p.getStatusEffect(StatusEffects.SPEED).getAmplifier() + 1);
        Vec3d v = p.getVelocity();
        switch (mode.get()) {
            case "Bhop" -> {
                if (p.isOnGround()) {
                    p.jump();
                    setHorizontal(input, s * groundBoost.get());
                } else {
                    setHorizontal(input, Math.max(Math.hypot(v.x, v.z), s * 0.9)); // air strafe keeps your speed
                }
            }
            case "Strafe" -> { if (p.isOnGround()) setHorizontal(input, s); }
            default -> {
                double cur = Math.hypot(v.x, v.z);
                if (cur > 1e-3 && cur < s) p.setVelocity(v.x * s / cur, v.y, v.z * s / cur);
            }
        }
    }

    private Vec3d moveInput() {
        var o = mc.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return Vec3d.ZERO;
        double yaw = Math.toRadians(mc.player.getYaw());
        double x = -Math.sin(yaw) * f + Math.cos(yaw) * s, z = Math.cos(yaw) * f + Math.sin(yaw) * s;
        return new Vec3d(x, 0, z).normalize();
    }

    private void setHorizontal(Vec3d dir, double speed) {
        var p = mc.player;
        if (dir.lengthSquared() < 1e-4) return;
        p.setVelocity(dir.x * speed, p.getVelocity().y, dir.z * speed);
    }
}
