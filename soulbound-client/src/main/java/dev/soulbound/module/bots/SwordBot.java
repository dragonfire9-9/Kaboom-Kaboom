package dev.soulbound.module.bots;

import dev.soulbound.aim.AimSettings;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sword PvP bot.
 *
 * <p>Movement is planned in world space (toward / around / away from the target) and turned into
 * whichever keys match the current view, so it never runs off at an angle while the aim catches
 * up. It aims where the target is going, holds the ideal spacing just inside reach, swings only
 * on a charged weapon and outside the target's invulnerability frames, sprint-resets each hit for
 * full knockback, times jump crits, strafes to dodge, blocks with a shield between swings, and
 * heals or retreats when low.
 */
public class SwordBot extends BotModule {
    // targeting
    protected final ModeSetting targets, priority;
    protected final NumberSetting searchRange, fov;
    protected final BoolSetting throughWalls, ignoreInvisible, targetLock;
    // attack
    protected final ModeSetting aimPoint, crits, weapon;
    protected final NumberSetting attackRange, minCooldown, hitChance, randomDelay, maxAimError, prediction;
    protected final BoolSetting skipIFrames, breakShields, firstHit;
    // movement
    protected final BoolSetting chase, sprint, jumpChase, avoidEdges;
    protected final ModeSetting strafe, wTap;
    protected final NumberSetting idealDistance, strafeChange, tapLength;
    // defense
    protected final ModeSetting autoBlock;
    protected final NumberSetting retreatHealth, gappleHealth;
    protected final BoolSetting eatGapples, autoTotem;

    private int strafeDir = 1, strafeTimer, tapTicks, blockTicks;
    private long readySince = -1, nextDelay;
    private int restoreSlot = -1;
    private boolean blocking;

    public SwordBot() {
        this("Sword PvP Bot", "Fights for you: aims, spaces, strafes and times every hit.", "SB-01", "sword_bot");
        addHudSettings();
    }

    protected SwordBot(String name, String description, String code, String skin) {
        super(name, description, code, skin);
        addStyle("Trained");
        add(new HeaderSetting("TARGETING"));
        targets = add(new ModeSetting("Targets", "What the bot fights", "Players", "Players", "Mobs", "All"));
        priority = add(new ModeSetting("Priority", "Which target to pick first", "Closest", "Closest", "Lowest Health", "Crosshair"));
        searchRange = add(new NumberSetting("Search Range", "How far to look for targets", 12, 4, 32, 1, "m"));
        fov = add(new NumberSetting("FOV", "Only pick targets inside this view cone", 360, 30, 360, 10, "°"));
        throughWalls = add(new BoolSetting("Through Walls", "Target entities you can't see", false));
        ignoreInvisible = add(new BoolSetting("Ignore Invisible", "Skip invisible entities", true));
        targetLock = add(new BoolSetting("Target Lock", "Stay on one target until it's gone", true));

        aim = new AimSettings(this, 780, 0.35, 50);
        aimPoint = add(new ModeSetting("Aim Point", "Where on the body to aim", "Closest", "Closest", "Head", "Chest", "Legs", "Dynamic"));
        prediction = add(new NumberSetting("Prediction", "Aim this many ticks ahead of a moving target", 2, 0, 6, 0.5, "t"));

        add(new HeaderSetting("ATTACK"));
        attackRange = add(new NumberSetting("Attack Range", "Distance to start swinging", 2.95, 2.0, 3.0, 0.05, "m"));
        minCooldown = add(new NumberSetting("Min Cooldown", "Swing once the weapon is this charged", 0.93, 0.5, 1.0, 0.01));
        skipIFrames = add(new BoolSetting("Skip I-Frames", "Don't waste swings while the target is still invulnerable from the last hit", true));
        crits = add(new ModeSetting("Crits", "Jump to land critical hits", "Smart", "Off", "Always", "Smart"));
        firstHit = add(new BoolSetting("First Hit", "Sprint in for the opening hit at full reach", true));
        hitChance = add(new NumberSetting("Hit Chance", "Chance a swing actually connects", 100, 50, 100, 1, "%"));
        randomDelay = add(new NumberSetting("Random Delay", "Extra random wait before each swing", 30, 0, 250, 5, "ms"));
        maxAimError = add(new NumberSetting("Max Aim Error", "Only swing when aimed within this", 18, 4, 60, 1, "°"));
        weapon = add(new ModeSetting("Weapon", "What to hold", "Best", "Best", "Sword", "Axe", "Keep"));
        breakShields = add(new BoolSetting("Break Shields", "Swap to an axe when the target blocks", true));

        add(new HeaderSetting("MOVEMENT"));
        chase = add(new BoolSetting("Chase", "Walk toward the target", true));
        sprint = add(new BoolSetting("Sprint", "Sprint while chasing", true));
        jumpChase = add(new BoolSetting("Jump Chase", "Sprint-jump to close long gaps faster", true));
        idealDistance = add(new NumberSetting("Ideal Distance", "Spacing to hold between hits", 2.6, 1.5, 3.0, 0.05, "m"));
        strafe = add(new ModeSetting("Strafe", "Side-to-side movement while fighting", "Smart", "Off", "Circle", "Random", "Smart"));
        strafeChange = add(new NumberSetting("Strafe Switch", "Average ticks before changing strafe direction", 14, 3, 60, 1, "t").visibleWhen(() -> !strafe.is("Off")));
        wTap = add(new ModeSetting("Sprint Reset", "Reset sprint after a hit so the next one knocks back fully", "W-Tap", "Off", "W-Tap", "S-Tap", "Sprint Tap"));
        tapLength = add(new NumberSetting("Tap Length", "How long the reset lasts", 2, 1, 6, 1, "t").visibleWhen(() -> !wTap.is("Off")));
        avoidEdges = add(new BoolSetting("Avoid Edges", "Never walk off drops or into lava", true));

        add(new HeaderSetting("DEFENSE"));
        autoBlock = add(new ModeSetting("Auto Block", "Raise an offhand shield between swings", "Smart", "Off", "Smart", "Always"));
        retreatHealth = add(new NumberSetting("Retreat Health", "Back off at or below this health (0 = never)", 0, 0, 20, 1, " HP"));
        eatGapples = add(new BoolSetting("Eat Gapples", "Eat golden apples when low", false));
        gappleHealth = add(new NumberSetting("Gapple Health", "Eat at or below this health", 8, 1, 20, 1, " HP").visibleWhen(eatGapples::isOn));
        autoTotem = add(new BoolSetting("Auto Totem", "Keep a totem in your offhand", false));
    }

    @Override public String getIcon() { return "icon_sword"; }

    @Override
    protected void tickBot(ClientPlayerEntity p) {
        if (autoTotem.isOn()) keepTotem(p);
        if (eatGapples.isOn() && handleEating(p, gappleHealth.get())) {
            stopBlocking();
            state = State.HEALING;
            if (target != null) {
                // back away while eating, still facing them
                lookAt(aimPoint(p, target, "Chest"));
                moveDir(away(p), false, false, avoidEdges.isOn());
            } else {
                move(false, false, false, false, false, false);
            }
            return;
        }
        target = pickTarget(p, searchRange.get(), targets.get(), priority.get(), throughWalls.isOn(),
                fov.get(), ignoreInvisible.isOn(), targetLock.isOn());
        if (target == null) {
            stopBlocking();
            release();
            state = State.SEARCHING;
            return;
        }
        if (!special(p)) melee(p);
    }

    /** Hook for subclasses (e.g. bow or rod play); return true if this tick was handled. */
    protected boolean special(ClientPlayerEntity p) { return false; }

    private Vec3d toward(ClientPlayerEntity p) {
        Vec3d d = new Vec3d(target.getX() - p.getX(), 0, target.getZ() - p.getZ());
        return d.lengthSquared() < 1e-6 ? Vec3d.ZERO : d.normalize();
    }

    private Vec3d away(ClientPlayerEntity p) { return toward(p).multiply(-1); }

    protected void melee(ClientPlayerEntity p) {
        selectWeapon(p);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        boolean agg = aggressive(), dodge = untouchable();

        // aim where they're going to be
        Vec3d lead = predicted(target, prediction.get());
        Vec3d aimAt = aimPoint(p, target, aimPoint.get()).add(lead);
        float aimError = lookAt(aimAt);

        double reach = reach(p, target), range = attackRange.get();
        double ideal = agg ? Math.min(idealDistance.get(), 2.3) : dodge ? Math.max(idealDistance.get(), range - 0.15) : idealDistance.get();
        double retreatAt = agg ? 0 : dodge ? Math.max(retreatHealth.get(), 9) : retreatHealth.get();
        boolean retreat = retreatAt > 0 && p.getHealth() + p.getAbsorptionAmount() <= retreatAt;
        float charge = p.getAttackCooldownProgress(0.5f);
        float needCharge = agg ? Math.min(minCooldown.getFloat(), 0.9f) : minCooldown.getFloat();

        // ----- where to move (world space): along the line to the target, plus a strafe component
        Vec3d to = toward(p), side = new Vec3d(-to.z, 0, to.x);
        double fwd = 0, sideAmt = 0;
        boolean jump = false, run = false;
        if (retreat) {
            state = State.RETREATING;
            fwd = -1;
            run = false;
        } else {
            state = reach > range ? State.CHASING : State.ENGAGING;
            if (chase.isOn()) {
                if (reach > range + 0.15) {
                    fwd = 1;
                    run = sprint.isOn() || agg;
                    // sprint-jump across long gaps (and for the opening hit)
                    boolean far = reach > range + (agg ? 1.8 : 3.0);
                    if ((jumpChase.isOn() || agg) && far && p.isOnGround() && p.isSprinting()) jump = true;
                } else if (reach < ideal - 0.35) {
                    fwd = -1; // too close: give ground so we keep landing hits without trading
                } else if (reach > ideal + 0.15 && charge > 0.7f) {
                    fwd = 1; // step in as the weapon finishes charging
                    run = sprint.isOn();
                }
            }
        }

        // strafing around them
        boolean strafing = (!strafe.is("Off") || dodge) && reach < range + 2;
        if (strafing) {
            if (--strafeTimer <= 0 || p.horizontalCollision) {
                strafeDir = -strafeDir;
                int base = strafeChange.getInt();
                strafeTimer = dodge ? r.nextInt(3, Math.max(4, base / 2 + 4)) : switch (strafe.get()) {
                    case "Circle" -> base * 3;
                    case "Random" -> r.nextInt(Math.max(1, base / 3), base * 2 + 1);
                    default -> target.hurtTime > 0 ? r.nextInt(2, 6) : r.nextInt(Math.max(1, base / 2), base + base / 2 + 1); // smart: juke after a hit
                };
            }
            sideAmt = strafeDir;
        }

        // sprint reset after a landed hit
        if (tapTicks > 0) {
            tapTicks--;
            switch (wTap.get()) {
                case "S-Tap" -> fwd = -1;
                case "Sprint Tap" -> run = false;
                default -> { if (fwd > 0) fwd = 0; }
            }
            if (dodge) fwd = -1;
            run = false;
        }

        // ----- shield between swings
        boolean shield = p.getOffHandStack().isOf(Items.SHIELD) && !autoBlock.is("Off") && !retreat;
        boolean wantBlock = shield && reach < range + 1.5 && charge < needCharge - 0.15f
                && (autoBlock.is("Always") || targetThreatens(p));
        if (wantBlock) {
            if (!blocking) blockTicks = 0;
            blocking = true;
            blockTicks++;
            mc.options.useKey.setPressed(true);
            run = false;
        } else {
            stopBlocking();
        }

        // ----- crits: jump so the swing lands on the way down
        boolean critMode = !crits.is("Off") && !p.isTouchingWater() && !p.isClimbing() && !p.hasVehicle() && !retreat;
        boolean critNow = critMode && (crits.is("Always") || (reach <= range + 0.3 && target.hurtTime <= 2 && !strafing || reach <= range));
        if (critNow && p.isOnGround() && charge > 0.72f && charge < 0.95f && reach <= range + 0.2) jump = true;

        // ----- swing
        boolean iFramesOk = !skipIFrames.isOn() || target.hurtTime <= (agg ? 2 : 1);
        boolean ready = reach <= range && !retreat && charge >= needCharge && aimError < maxAimError.getFloat() && iFramesOk;
        long now = System.currentTimeMillis();
        if (ready) {
            if (readySince < 0) {
                readySince = now;
                int maxDelay = agg ? randomDelay.getInt() / 2 : randomDelay.getInt();
                nextDelay = maxDelay == 0 ? 0 : r.nextLong(maxDelay + 1);
            }
            boolean airborneForCrit = critMode && !p.isOnGround() && p.getVelocity().y > -0.01 && p.fallDistance <= 0;
            boolean falling = p.fallDistance > 0 && p.getVelocity().y < 0;
            // hold the swing while rising on a crit jump (it lands a moment later, harder)
            if (now - readySince >= nextDelay && !(airborneForCrit && reach < range - 0.1)) {
                stopBlocking();
                swing(p);
                readySince = -1;
                if (falling) jump = false;
            }
        } else {
            readySince = -1;
        }

        Vec3d dir = to.multiply(fwd).add(side.multiply(sideAmt * (fwd > 0 ? 0.6 : 1.0)));
        moveDir(dir, jump, run, avoidEdges.isOn());
        if (blocking) mc.options.useKey.setPressed(true);
    }

    /** True when the target is facing us with a weapon close enough to hit. */
    private boolean targetThreatens(ClientPlayerEntity p) {
        if (!(target instanceof PlayerEntity tp)) return reach(p, target) < 3.2;
        float[] a = angles(target.getEyePos(), p.getBoundingBox().getCenter());
        float err = Math.abs(MathHelper.wrapDegrees(a[0] - target.getHeadYaw()));
        ItemStack held = tp.getMainHandStack();
        return err < 50 && (held.isIn(ItemTags.SWORDS) || held.isIn(ItemTags.AXES) || held.isOf(Items.MACE) || held.isOf(Items.TRIDENT));
    }

    private void stopBlocking() {
        if (!blocking) return;
        blocking = false;
        mc.options.useKey.setPressed(false);
    }

    private void swing(ClientPlayerEntity p) {
        boolean shield = breakShields.isOn() && target instanceof PlayerEntity pe && pe.isBlocking();
        if (shield) {
            int axe = -1;
            for (int i = 0; i < 9 && axe < 0; i++) if (p.getInventory().getStack(i).isIn(ItemTags.AXES)) axe = i;
            if (axe >= 0) {
                restoreSlot = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(axe);
            }
        }
        if (ThreadLocalRandom.current().nextDouble(100) < hitChance.get()) {
            mc.interactionManager.attackEntity(p, target);
            countHit();
            if (!wTap.is("Off") || untouchable()) tapTicks = untouchable() ? Math.max(4, tapLength.getInt()) : tapLength.getInt();
        }
        p.swingHand(Hand.MAIN_HAND);
        if (restoreSlot >= 0) {
            p.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
        }
    }

    /** Holds the best weapon for the setting: highest attack damage per hit of the allowed kinds. */
    protected void selectWeapon(ClientPlayerEntity p) {
        if (weapon.is("Keep")) return;
        var inv = p.getInventory();
        int best = -1;
        double bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            boolean sword = s.isIn(ItemTags.SWORDS), axe = s.isIn(ItemTags.AXES);
            if (!(weapon.is("Best") ? sword || axe : weapon.is("Sword") ? sword : axe)) continue;
            double dmg = damageOf(s);
            // swords swing faster: prefer them unless an axe is clearly stronger
            double score = sword ? dmg * 1.25 : dmg;
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0 && best != inv.getSelectedSlot()) inv.setSelectedSlot(best);
    }

    private static double damageOf(ItemStack s) {
        double[] dmg = {1};
        s.applyAttributeModifiers(net.minecraft.entity.EquipmentSlot.MAINHAND, (attr, mod) -> {
            if (attr.equals(EntityAttributes.ATTACK_DAMAGE)) dmg[0] += mod.value();
        });
        return dmg[0];
    }

    /** Kept for subclasses written against the old name. */
    protected void selectSword(ClientPlayerEntity p) { selectWeapon(p); }

    @Override
    protected void onDisable() {
        stopBlocking();
        super.onDisable();
    }

    protected Vec3d targetCenter() { return target.getBoundingBox().getCenter(); }

    protected LivingEntity target() { return target; }
}
