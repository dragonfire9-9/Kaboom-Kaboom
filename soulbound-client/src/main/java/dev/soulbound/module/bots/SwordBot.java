package dev.soulbound.module.bots;

import dev.soulbound.aim.AimSettings;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sword PvP bot: picks a target, aims at it smoothly every frame, closes the distance,
 * strafes, sprint-resets and times each hit.
 */
public class SwordBot extends BotModule {
    // targeting
    protected final ModeSetting targets, priority;
    protected final NumberSetting searchRange, fov;
    protected final BoolSetting throughWalls, ignoreInvisible, targetLock;
    // attack
    protected final ModeSetting aimPoint;
    protected final NumberSetting attackRange, minCooldown, hitChance, randomDelay, maxAimError;
    protected final BoolSetting crits, breakShields, autoSword;
    // movement
    protected final BoolSetting chase, sprint, jumpChase, avoidEdges;
    protected final ModeSetting strafe, wTap;
    protected final NumberSetting keepDistance;
    // survival
    protected final NumberSetting retreatHealth, gappleHealth;
    protected final BoolSetting eatGapples, autoTotem;

    private int strafeDir = 1, strafeTimer, tapTicks;
    private long readySince = -1, nextDelay;
    private int axeSlotRestore = -1;

    public SwordBot() {
        this("Sword PvP Bot", "Fights for you: aims, chases, strafes and times hits.", "SB-01", "sword_bot");
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
        targetLock = add(new BoolSetting("Target Lock", "Stay on one target until it's gone", false));

        aim = new AimSettings(this, 420, 0.55);
        aimPoint = add(new ModeSetting("Aim Point", "Where on the body to aim", "Closest", "Closest", "Head", "Chest", "Legs", "Dynamic"));

        add(new HeaderSetting("ATTACK"));
        attackRange = add(new NumberSetting("Attack Range", "Distance to start swinging", 2.85, 2.0, 3.0, 0.05, "m"));
        minCooldown = add(new NumberSetting("Min Cooldown", "Swing once the sword is this charged", 0.92, 0.5, 1.0, 0.01));
        hitChance = add(new NumberSetting("Hit Chance", "Chance a swing actually connects", 100, 50, 100, 1, "%"));
        randomDelay = add(new NumberSetting("Random Delay", "Extra random wait before each swing", 40, 0, 250, 5, "ms"));
        maxAimError = add(new NumberSetting("Max Aim Error", "Only swing when aimed within this", 22, 4, 60, 1, "°"));
        crits = add(new BoolSetting("Jump Crits", "Jump and hit while falling", false));
        breakShields = add(new BoolSetting("Break Shields", "Swap to an axe when the target blocks", true));
        autoSword = add(new BoolSetting("Auto Sword", "Switch to the best sword in the hotbar", true));

        add(new HeaderSetting("MOVEMENT"));
        chase = add(new BoolSetting("Chase", "Walk toward the target", true));
        sprint = add(new BoolSetting("Sprint", "Sprint while chasing", true));
        jumpChase = add(new BoolSetting("Jump Chase", "Hop to close long gaps faster", false));
        strafe = add(new ModeSetting("Strafe", "Side-to-side movement while fighting", "Smart", "Off", "Circle", "Random", "Smart"));
        wTap = add(new ModeSetting("Sprint Reset", "Reset sprint after a hit for knockback", "W-Tap", "Off", "W-Tap", "S-Tap"));
        keepDistance = add(new NumberSetting("Keep Distance", "Back off when closer than this (0 = never)", 0, 0, 3, 0.1, "m"));
        avoidEdges = add(new BoolSetting("Avoid Edges", "Never walk off drops or into lava", true));

        add(new HeaderSetting("SURVIVAL"));
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
            state = State.HEALING;
            move(false, true, false, false, false, false, avoidEdges.isOn());
            return;
        }
        target = pickTarget(p, searchRange.get(), targets.get(), priority.get(), throughWalls.isOn(),
                fov.get(), ignoreInvisible.isOn(), targetLock.isOn());
        if (target == null) {
            release();
            state = State.SEARCHING;
            return;
        }
        if (!special(p)) melee(p);
    }

    /** Hook for subclasses (e.g. bow or rod play); return true if this tick was handled. */
    protected boolean special(ClientPlayerEntity p) { return false; }

    protected void melee(ClientPlayerEntity p) {
        if (autoSword.isOn()) selectSword(p);
        float aimError = lookAt(aimPoint(p, target, aimPoint.get()));
        double reach = reach(p, target), range = attackRange.get();
        // playstyle shapes the settings rather than replacing them
        boolean agg = aggressive(), dodge = untouchable();
        double retreatAt = agg ? 0 : dodge ? Math.max(retreatHealth.get(), 9) : retreatHealth.get();
        double keep = agg ? 0 : dodge ? Math.max(keepDistance.get(), range - 0.35) : keepDistance.get();
        boolean retreat = retreatAt > 0 && p.getHealth() <= retreatAt;
        boolean forward = false, back = false, left = false, right = false, jump = false, run = false;
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (retreat) {
            state = State.RETREATING;
            back = true;
        } else {
            state = reach > range ? State.CHASING : State.ENGAGING;
            if (chase.isOn() && reach > range * (agg ? 0.55 : 0.75)) {
                forward = true;
                run = sprint.isOn() || agg;
                if ((jumpChase.isOn() || agg) && reach > range + (agg ? 1.5 : 2.5) && p.isOnGround()) jump = true;
            }
            if (keep > 0 && reach < keep) {
                forward = false;
                back = true;
            }
        }

        if ((!strafe.is("Off") || dodge) && reach < range + 1.5) {
            if (--strafeTimer <= 0 || p.horizontalCollision) {
                strafeDir = -strafeDir;
                strafeTimer = dodge ? r.nextInt(4, 11) : switch (strafe.get()) {
                    case "Circle" -> r.nextInt(25, 45);
                    case "Random" -> r.nextInt(5, 14);
                    default -> target.hurtTime > 0 ? r.nextInt(3, 7) : r.nextInt(10, 22); // smart: juke after landing a hit
                };
            }
            left = strafeDir > 0;
            right = strafeDir < 0;
        }

        if (tapTicks > 0) {
            tapTicks--;
            if (wTap.is("S-Tap") || dodge) {
                forward = false;
                back = true;
            } else {
                forward = false;
            }
            run = false;
        }

        // swing timing: charged, in range, aimed, plus a small random human delay
        boolean charged = p.getAttackCooldownProgress(0.5f) >= (agg ? Math.min(minCooldown.getFloat(), 0.9f) : minCooldown.getFloat());
        boolean ready = reach <= range && !retreat && charged && aimError < maxAimError.getFloat();
        long now = System.currentTimeMillis();
        if (ready) {
            if (readySince < 0) {
                readySince = now;
                int maxDelay = agg ? randomDelay.getInt() / 2 : randomDelay.getInt();
                nextDelay = maxDelay == 0 ? 0 : r.nextLong(maxDelay + 1);
            }
            if (now - readySince >= nextDelay) {
                boolean wantCrit = crits.isOn() && !p.isTouchingWater() && !p.isClimbing() && !p.hasVehicle();
                if (wantCrit && p.isOnGround()) {
                    jump = true;
                } else if (!wantCrit || (p.fallDistance > 0 && p.getVelocity().y < 0)) {
                    swing(p);
                    readySince = -1;
                }
            }
        } else {
            readySince = -1;
        }
        move(forward, back, left, right, jump, run, avoidEdges.isOn());
    }

    private void swing(ClientPlayerEntity p) {
        boolean shield = breakShields.isOn() && target instanceof PlayerEntity pe && pe.isBlocking();
        if (shield) {
            int axe = -1;
            for (int i = 0; i < 9 && axe < 0; i++) if (p.getInventory().getStack(i).isIn(ItemTags.AXES)) axe = i;
            if (axe >= 0) {
                axeSlotRestore = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(axe);
            }
        }
        if (ThreadLocalRandom.current().nextDouble(100) < hitChance.get()) {
            mc.interactionManager.attackEntity(p, target);
            countHit();
            if (!wTap.is("Off") || untouchable()) tapTicks = untouchable() ? 4 : 2; // untouchable backs off after every hit
        }
        p.swingHand(Hand.MAIN_HAND);
        if (axeSlotRestore >= 0) {
            p.getInventory().setSelectedSlot(axeSlotRestore);
            axeSlotRestore = -1;
        }
    }

    protected void selectSword(ClientPlayerEntity p) {
        var inv = p.getInventory();
        int current = inv.getSelectedSlot();
        int best = -1, bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.isIn(ItemTags.SWORDS)) continue;
            if (s.getMaxDamage() > bestScore) {
                bestScore = s.getMaxDamage();
                best = i;
            }
        }
        if (best >= 0 && best != current) {
            ItemStack cur = inv.getStack(current);
            if (!cur.isIn(ItemTags.SWORDS) || cur.getMaxDamage() < bestScore) inv.setSelectedSlot(best);
        }
    }

    protected Vec3d targetCenter() { return target.getBoundingBox().getCenter(); }

    protected LivingEntity target() { return target; }
}
