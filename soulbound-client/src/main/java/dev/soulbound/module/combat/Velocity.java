package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/** Takes less knockback: scales the knockback the server pushes onto you. */
public final class Velocity extends Module {
    public static Velocity INSTANCE;
    private final NumberSetting horizontal, vertical, chance, explosionH, explosionV;
    private final BoolSetting onlyOnGround, onlyWhileAttacking, notInWater;
    private long lastAttack;

    public Velocity() {
        super("Velocity", "Reduce the knockback you take.", Category.COMBAT);
        INSTANCE = this;
        add(new HeaderSetting("KNOCKBACK"));
        horizontal = add(new NumberSetting("Horizontal", "How much sideways knockback to keep", 60, 0, 100, 1, "%"));
        vertical = add(new NumberSetting("Vertical", "How much upward knockback to keep", 100, 0, 100, 1, "%"));
        chance = add(new NumberSetting("Chance", "Chance each hit is reduced", 100, 0, 100, 1, "%"));
        add(new HeaderSetting("EXPLOSIONS"));
        explosionH = add(new NumberSetting("Explosion Horizontal", "Sideways push from explosions to keep", 80, 0, 100, 1, "%"));
        explosionV = add(new NumberSetting("Explosion Vertical", "Upward push from explosions to keep", 100, 0, 100, 1, "%"));
        add(new HeaderSetting("CONDITIONS"));
        onlyOnGround = add(new BoolSetting("Only On Ground", "Only reduce knockback taken on the ground", false));
        onlyWhileAttacking = add(new BoolSetting("Only In Fights", "Only within a few seconds of hitting something", false));
        notInWater = add(new BoolSetting("Not In Water", "Leave knockback alone while swimming", true));
        dev.soulbound.event.Events.ATTACK.add(e -> lastAttack = System.currentTimeMillis());
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.isEnabled() && mc.player != null; }

    /** Returns the velocity to apply instead of {@code incoming} (null = ignore it entirely). */
    public Vec3d apply(Vec3d current, Vec3d incoming, boolean explosion) {
        var p = mc.player;
        if (notInWater.isOn() && p.isTouchingWater()) return incoming;
        if (onlyOnGround.isOn() && !p.isOnGround()) return incoming;
        if (onlyWhileAttacking.isOn() && System.currentTimeMillis() - lastAttack > 3000) return incoming;
        if (ThreadLocalRandom.current().nextDouble(100) >= chance.get()) return incoming;
        double h = (explosion ? explosionH.get() : horizontal.get()) / 100, v = (explosion ? explosionV.get() : vertical.get()) / 100;
        if (explosion) return new Vec3d(incoming.x * h, incoming.y * v, incoming.z * h);
        // the packet sets velocity outright: blend between what we had and what the hit wants
        return new Vec3d(current.x + (incoming.x - current.x) * h, current.y + (incoming.y - current.y) * v, current.z + (incoming.z - current.z) * h);
    }
}
