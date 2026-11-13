package dev.soulbound.module.bots;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sword PvP bot: picks a target, turns toward it smoothly, closes the distance, strafes,
 * sprint-resets (W-tap) and swings when the sword is fully charged.
 */
public final class SwordBot extends Module {
    public enum State {
        SEARCHING("SEARCHING"), CHASING("CHASING"), ENGAGING("ENGAGING"), RETREATING("RETREATING"), PAUSED("PAUSED");
        public final String label;
        State(String label) { this.label = label; }
    }

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
    public final BoolSetting hud = add(new BoolSetting("Show HUD", "Show the bot status card", true));
    public final ModeSetting hudPosition = add(new ModeSetting("HUD Position", "Where the card sits", "Below Crosshair",
            "Below Crosshair", "Top Left", "Top Right", "Bottom Left").visibleWhen(hud::isOn));

    // ------------------------------------------------------------------ runtime state
    private LivingEntity target;
    private State state = State.SEARCHING;
    private boolean controlling;
    private int strafeDir = 1, strafeTimer, wTapTicks;
    private int hits, combo, bestCombo, lastPlayerHurt;
    private final Deque<Long> swings = new ArrayDeque<>();

    public SwordBot() {
        super("Sword Bot", "Fights for you: aims, chases, strafes and times hits.", Category.BOTS);
    }

    // ------------------------------------------------------------------ public getters for the HUD
    public LivingEntity getTarget() { return target; }
    public State getState() { return state; }
    public int getHits() { return hits; }
    public int getCombo() { return combo; }
    public int getBestCombo() { return bestCombo; }

    public float getCps() {
        long now = System.currentTimeMillis();
        while (!swings.isEmpty() && now - swings.peekFirst() > 2000) swings.pollFirst();
        return swings.size() / 2f;
    }

    public double getReach() {
        if (target == null || mc.player == null) return 0;
        return reach(mc.player, target);
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    protected void onEnable() {
        hits = combo = bestCombo = 0;
        swings.clear();
        target = null;
        state = State.SEARCHING;
        if (mc.player != null) lastPlayerHurt = mc.player.hurtTime;
    }

    @Override
    protected void onDisable() {
        release();
        target = null;
        state = State.SEARCHING;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        if (mc.currentScreen != null || p.isDead() || p.isSpectator()) {
            release();
            state = State.PAUSED;
            return;
        }

        // combo breaks when we take a hit
        if (p.hurtTime > lastPlayerHurt) combo = 0;
        lastPlayerHurt = p.hurtTime;

        target = pickTarget(p);
        if (target == null) {
            release();
            state = State.SEARCHING;
            return;
        }

        if (autoSword.isOn()) selectSword(p);

        Vec3d aim = aimPoint(p, target);
        float[] want = angles(p.getEyePos(), aim);
        turn(p, want[0], want[1]);
        float aimError = Math.abs(MathHelper.wrapDegrees(want[0] - p.getYaw())) + Math.abs(want[1] - p.getPitch());

        double reach = reach(p, target);
        double range = attackRange.get();
        GameOptions o = mc.options;

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

        // strafing
        if (!strafe.is("Off") && reach < range + 1.5) {
            if (--strafeTimer <= 0 || p.horizontalCollision) {
                strafeDir = -strafeDir;
                ThreadLocalRandom r = ThreadLocalRandom.current();
                strafeTimer = strafe.is("Circle") ? r.nextInt(25, 45) : r.nextInt(5, 14);
            }
            left = strafeDir > 0;
            right = strafeDir < 0;
        }

        // sprint reset after a hit
        if (wTapTicks > 0) {
            wTapTicks--;
            forward = false;
            sprint = false;
        }

        // attacking
        boolean charged = p.getAttackCooldownProgress(0.5f) >= 0.93f;
        boolean inRange = reach <= range && !retreat;
        boolean aimed = aimError < 35f;
        if (inRange && charged && aimed) {
            boolean wantCrit = crits.isOn() && !p.isTouchingWater() && !p.isClimbing() && !p.hasVehicle();
            if (wantCrit && p.isOnGround()) {
                jump = true; // hit on the way down
            } else if (!wantCrit || (p.fallDistance > 0 && p.getVelocity().y < 0)) {
                attack(p, target);
            }
        }

        press(o.forwardKey, forward);
        press(o.backKey, back);
        press(o.leftKey, left);
        press(o.rightKey, right);
        press(o.jumpKey, jump);
        press(o.sprintKey, sprint);
        controlling = true;
    }

    // ------------------------------------------------------------------ targeting
    private LivingEntity pickTarget(ClientPlayerEntity p) {
        double range = searchRange.get();
        if (target != null && isValid(p, target, range + 2)) {
            // keep the current target unless a much better one shows up
            LivingEntity best = best(p, range);
            if (best == null || best == target || score(p, best) > score(p, target) * 0.6) return target;
            return best;
        }
        return best(p, range);
    }

    private LivingEntity best(ClientPlayerEntity p, double range) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !isValid(p, le, range)) continue;
            double s = score(p, le);
            if (s < bestScore) {
                bestScore = s;
                best = le;
            }
        }
        return best;
    }

    private double score(ClientPlayerEntity p, LivingEntity e) {
        return switch (priority.get()) {
            case "Lowest Health" -> e.getHealth() + e.getAbsorptionAmount();
            case "Crosshair" -> {
                float[] a = angles(p.getEyePos(), e.getBoundingBox().getCenter());
                yield Math.abs(MathHelper.wrapDegrees(a[0] - p.getYaw())) + Math.abs(a[1] - p.getPitch());
            }
            default -> p.squaredDistanceTo(e);
        };
    }

    private boolean isValid(ClientPlayerEntity p, LivingEntity e, double range) {
        if (e == p || !e.isAlive() || e.isDead() || e.isSpectator() || e.isRemoved()) return false;
        if (p.squaredDistanceTo(e) > range * range) return false;
        if (e instanceof PlayerEntity pe) {
            if (targets.is("Mobs") || pe.isCreative() || p.isTeammate(pe)) return false;
        } else {
            if (targets.is("Players") || !(e instanceof Monster)) return false;
        }
        return throughWalls.isOn() || p.canSee(e);
    }

    // ------------------------------------------------------------------ aiming
    private static Vec3d aimPoint(ClientPlayerEntity p, LivingEntity t) {
        Box b = t.getBoundingBox();
        Vec3d eye = p.getEyePos();
        double h = b.maxY - b.minY;
        double x = MathHelper.clamp(eye.x, b.minX + 0.1, b.maxX - 0.1);
        double y = MathHelper.clamp(eye.y, b.minY + h * 0.35, b.maxY - h * 0.1);
        double z = MathHelper.clamp(eye.z, b.minZ + 0.1, b.maxZ - 0.1);
        return new Vec3d(x, y, z);
    }

    private static float[] angles(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[]{yaw, MathHelper.clamp(pitch, -90f, 90f)};
    }

    private void turn(ClientPlayerEntity p, float yaw, float pitch) {
        float max = rotationSpeed.getFloat();
        float dy = MathHelper.wrapDegrees(yaw - p.getYaw());
        float dp = pitch - p.getPitch();
        // ease in: big turns are fast, the last few degrees settle smoothly
        float sy = MathHelper.clamp(dy * 0.65f, -max, max);
        float sp = MathHelper.clamp(dp * 0.65f, -max, max);
        p.setYaw(p.getYaw() + sy);
        p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90f, 90f));
    }

    /** Distance from the eyes to the nearest point of the target's hitbox (what the server checks). */
    private static double reach(ClientPlayerEntity p, LivingEntity t) {
        Box b = t.getBoundingBox();
        Vec3d eye = p.getEyePos();
        double x = MathHelper.clamp(eye.x, b.minX, b.maxX);
        double y = MathHelper.clamp(eye.y, b.minY, b.maxY);
        double z = MathHelper.clamp(eye.z, b.minZ, b.maxZ);
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    // ------------------------------------------------------------------ actions
    private void attack(ClientPlayerEntity p, LivingEntity t) {
        mc.interactionManager.attackEntity(p, t);
        p.swingHand(Hand.MAIN_HAND);
        hits++;
        combo++;
        bestCombo = Math.max(bestCombo, combo);
        swings.addLast(System.currentTimeMillis());
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

    private static void press(KeyBinding key, boolean down) { key.setPressed(down); }

    /** Hand movement keys back to the real keyboard state. */
    private void release() {
        if (!controlling) return;
        controlling = false;
        GameOptions o = mc.options;
        for (KeyBinding k : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sprintKey}) k.setPressed(false);
        KeyBinding.updatePressedStates(); // re-applies whatever is physically held
    }
}
