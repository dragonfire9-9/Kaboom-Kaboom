package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

/** Turns ground hits into critical hits. */
public final class Criticals extends Module {
    private final ModeSetting mode;
    private final NumberSetting minCharge, height;
    private final BoolSetting onlyLiving, skipCrystals;

    public Criticals() {
        super("Criticals", "Make ground hits critical hits.", Category.COMBAT);
        mode = add(new ModeSetting("Mode", "Packet fakes a tiny hop; Jump does a real short hop before the hit", "Packet", "Packet", "Jump"));
        height = add(new NumberSetting("Hop Height", "Height of the faked hop", 0.0625, 0.01, 0.2, 0.005, "m").visibleWhen(() -> mode.is("Packet")));
        minCharge = add(new NumberSetting("Min Charge", "Only on hits at least this charged (crits need 0.9)", 0.9, 0, 1, 0.05));
        onlyLiving = add(new BoolSetting("Only Living", "Skip items, boats and other non-living things", true));
        skipCrystals = add(new BoolSetting("Skip Crystals", "Never crit end crystals", true));
        dev.soulbound.event.Events.ATTACK.add(this::onAttack);
    }

    private void onAttack(Entity target) {
        var p = mc.player;
        if (!isEnabled() || p == null || mc.getNetworkHandler() == null) return;
        if (onlyLiving.isOn() && !(target instanceof LivingEntity)) return;
        if (skipCrystals.isOn() && target instanceof EndCrystalEntity) return;
        if (!p.isOnGround() || p.isTouchingWater() || p.isInLava() || p.isClimbing() || p.hasVehicle()) return;
        if (p.getAttackCooldownProgress(0.5f) < minCharge.getFloat()) return;
        if (mode.is("Jump")) {
            p.setVelocity(p.getVelocity().x, 0.1, p.getVelocity().z);
            return;
        }
        double x = p.getX(), y = p.getY(), z = p.getZ();
        var nh = mc.getNetworkHandler();
        nh.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + height.get(), z, false, p.horizontalCollision));
        nh.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, p.horizontalCollision));
    }
}
