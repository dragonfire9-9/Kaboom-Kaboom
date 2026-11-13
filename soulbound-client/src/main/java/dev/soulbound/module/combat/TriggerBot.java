package dev.soulbound.module.combat;

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
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

/** Attacks whatever valid target is under your crosshair, with human-like timing. */
public final class TriggerBot extends Module {
    private final ModeSetting targets;
    private final NumberSetting minCooldown, minDelay, maxDelay, hitChance;
    private final BoolSetting onlyWeapon, onlyCrits, ignoreInvisible, ignoreBlocking;

    private long armedAt = -1, wait;

    public TriggerBot() {
        super("Trigger Bot", "Attacks the target under your crosshair with natural timing.", Category.COMBAT);
        add(new HeaderSetting("TARGETING"));
        targets = add(new ModeSetting("Targets", "What it attacks", "Players", "Players", "Mobs", "All"));
        ignoreInvisible = add(new BoolSetting("Ignore Invisible", "Skip invisible entities", true));
        ignoreBlocking = add(new BoolSetting("Ignore Blocking", "Don't swing into a raised shield", false));
        add(new HeaderSetting("TIMING"));
        minCooldown = add(new NumberSetting("Min Cooldown", "Swing once your weapon is this charged", 0.92, 0.5, 1.0, 0.01));
        minDelay = add(new NumberSetting("Min Delay", "Shortest wait after the crosshair lands", 30, 0, 300, 5, "ms"));
        maxDelay = add(new NumberSetting("Max Delay", "Longest wait after the crosshair lands", 90, 0, 400, 5, "ms"));
        hitChance = add(new NumberSetting("Hit Chance", "Chance each swing connects", 100, 50, 100, 1, "%"));
        add(new HeaderSetting("CONDITIONS"));
        onlyWeapon = add(new BoolSetting("Only With Weapon", "Only while holding a sword or axe", true));
        onlyCrits = add(new BoolSetting("Only Crits", "Only swing while falling", false));
    }

    @Override public String getIcon() { return "icon_target"; }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit) || !valid(p, hit.getEntity())) {
            armedAt = -1;
            return;
        }
        if (onlyWeapon.isOn() && !p.getMainHandStack().isIn(ItemTags.SWORDS) && !p.getMainHandStack().isIn(ItemTags.AXES)) return;
        if (p.getAttackCooldownProgress(0.5f) < minCooldown.getFloat()) return;
        if (onlyCrits.isOn() && !(p.fallDistance > 0 && !p.isOnGround() && p.getVelocity().y < 0)) return;
        long now = System.currentTimeMillis();
        if (armedAt < 0) {
            armedAt = now;
            long lo = minDelay.getInt(), hi = Math.max(lo, maxDelay.getInt());
            wait = lo + (hi > lo ? ThreadLocalRandom.current().nextLong(hi - lo + 1) : 0);
        }
        if (now - armedAt < wait) return;
        if (ThreadLocalRandom.current().nextDouble(100) < hitChance.get()) mc.interactionManager.attackEntity(p, hit.getEntity());
        p.swingHand(Hand.MAIN_HAND);
        armedAt = -1;
    }

    private boolean valid(ClientPlayerEntity p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == p) return false;
        if (ignoreInvisible.isOn() && le.isInvisible()) return false;
        if (le instanceof PlayerEntity pe) {
            if (targets.is("Mobs") || pe.isCreative() || p.isTeammate(pe)) return false;
            if (ignoreBlocking.isOn() && pe.isBlocking()) return false;
            return true;
        }
        return !targets.is("Players") && le instanceof Monster;
    }
}
