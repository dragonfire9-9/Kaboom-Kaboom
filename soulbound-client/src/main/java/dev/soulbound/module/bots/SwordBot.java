package dev.soulbound.module.bots;

import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sword PvP bot: picks a target, turns toward it smoothly, closes the distance, strafes,
 * sprint-resets (W-tap) and swings when the sword is fully charged.
 */
public final class SwordBot extends BotModule {
    private final ModeSetting targets = add(new ModeSetting("Targets", "What the bot fights", "Players", "Players", "Mobs", "All"));
    private final ModeSetting priority = add(new ModeSetting("Priority", "Which target to pick first", "Closest", "Closest", "Lowest Health", "Crosshair"));
    private final NumberSetting searchRange = add(new NumberSetting("Search Range", "How far to look for targets", 12, 4, 32, 1, "m"));
    private final NumberSetting attackRange = add(new NumberSetting("Attack Range", "Distance to start swinging", 2.85, 2.0, 3.0, 0.05, "m"));
    private final NumberSetting rotationSpeed = add(new NumberSetting("Turn Speed", "Max degrees turned per tick", 40, 5, 180, 5, "°"));
    private final BoolSetting chase = add(new BoolSetting("Chase", "Walk toward the target", true));
    private final ModeSetting strafe = add(new ModeSetting("Strafe", "Side-to-side movement while fighting", "Circle", "Off", "Circle", "Random"));
    private final BoolSetting wTap = add(new BoolSetting("W-Tap", "Reset sprint after each hit for more knockback", true));
    private final BoolSetting crits = add(new BoolSetting("Jump Crits", "Jump and hit while falling", false));
    private final BoolSetting autoSword = add(new BoolSetting("Auto Sword", "Switch to the best sword in the hotbar", true));
    private final BoolSetting throughWalls = add(new BoolSetting("Through Walls", "Target entities you can't see", false));
    private final NumberSetting retreatHealth = add(new NumberSetting("Retreat Health", "Back off at or below this health (0 = never)", 0, 0, 20, 1, " HP"));

    private int strafeDir = 1, strafeTimer, wTapTicks;

    public SwordBot() {
        super("Sword PvP Bot", "Fights for you: aims, chases, strafes and times hits.", "SB-01", "sword_bot");
        addHudSettings();
    }

    @Override public String getIcon() { return "icon_sword"; }

    @Override
    protected void tickBot(ClientPlayerEntity p) {
        target = pickTarget(p, searchRange.get(), targets.get(), priority.get(), throughWalls.isOn());
        if (target == null) {
            release();
            state = State.SEARCHING;
            return;
        }
        if (autoSword.isOn()) selectSword(p);

        float aimError = turnTo(p, aimPoint(p, target), rotationSpeed.getFloat());
        double reach = reach(p, target), range = attackRange.get();
        boolean retreat = retreatHealth.get() > 0 && p.getHealth() <= retreatHealth.get();
        boolean forward = false, back = false, left = false, right = false, jump = false, sprint = false;

        if (retreat) {
            state = State.RETREATING;
            back = true;
            if (strafe.is("Off")) left = ThreadLocalRandom.current().nextBoolean();
        } else {
            state = reach > range ? State.CHASING : State.ENGAGING;
            if (chase.isOn() && reach > range * 0.75) {
                forward = true;
                sprint = true;
            }
        }

        if (!strafe.is("Off") && reach < range + 1.5) {
            if (--strafeTimer <= 0 || p.horizontalCollision) {
                strafeDir = -strafeDir;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                strafeTimer = strafe.is("Circle") ? r.nextInt(25, 45) : r.nextInt(5, 14);
            }
            left = strafeDir > 0;
            right = strafeDir < 0;
        }

        if (wTapTicks > 0) {
            wTapTicks--;
            forward = false;
            sprint = false;
        }

        boolean charged = p.getAttackCooldownProgress(0.5f) >= 0.93f;
        if (reach <= range && !retreat && charged && aimError < 35f) {
            boolean wantCrit = crits.isOn() && !p.isTouchingWater() && !p.isClimbing() && !p.hasVehicle();
            if (wantCrit && p.isOnGround()) {
                jump = true; // hit on the way down
            } else if (!wantCrit || (p.fallDistance > 0 && p.getVelocity().y < 0)) {
                attack(p, target);
            }
        }
        move(forward, back, left, right, jump, sprint);
    }

    private static Vec3d aimPoint(ClientPlayerEntity p, LivingEntity t) {
        Box b = t.getBoundingBox();
        Vec3d eye = p.getEyePos();
        double h = b.maxY - b.minY;
        double x = MathHelper.clamp(eye.x, b.minX + 0.1, b.maxX - 0.1);
        double y = MathHelper.clamp(eye.y, b.minY + h * 0.35, b.maxY - h * 0.1);
        double z = MathHelper.clamp(eye.z, b.minZ + 0.1, b.maxZ - 0.1);
        return new Vec3d(x, y, z);
    }

    private void attack(ClientPlayerEntity p, LivingEntity t) {
        mc.interactionManager.attackEntity(p, t);
        p.swingHand(Hand.MAIN_HAND);
        countHit();
        if (wTap.isOn()) wTapTicks = 2;
    }

    private void selectSword(ClientPlayerEntity p) {
        var inv = p.getInventory();
        int current = inv.getSelectedSlot();
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.isIn(ItemTags.SWORDS)) continue;
            int score = s.getMaxDamage(); // durability ranks material: netherite > diamond > iron > stone > wood
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0 && best != current) {
            ItemStack cur = inv.getStack(current);
            if (!cur.isIn(ItemTags.SWORDS) || cur.getMaxDamage() < bestScore) inv.setSelectedSlot(best);
        }
    }
}
