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
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Attacks whatever valid target is under your crosshair.
 *
 * <p>Two timing models: <b>Cooldown</b> swings when the weapon is charged (modern combat), and
 * <b>CPS</b> clicks at a randomised rate (for servers with old combat). On top of that: a reaction
 * delay when the crosshair first lands, Gaussian or uniform jitter, hurt-time syncing, crit
 * timing, deliberate misses, and a stack of conditions for when it's allowed to fire.
 */
public final class TriggerBot extends Module {
    // targets
    private final BoolSetting players, monsters, animals, others, invisible, teammates, ignoreBlocking, ignoreNaked;
    private final NumberSetting range;
    // timing
    private final ModeSetting timing, distribution;
    private final NumberSetting minCooldown, minCps, maxCps, reactMin, reactMax, hurtTime;
    // hits
    private final ModeSetting crits;
    private final NumberSetting hitChance;
    private final BoolSetting missSwing, axeOnShield;
    // conditions
    private final ModeSetting weapon;
    private final BoolSetting requireClick, notWhileUsing, notInWater, onlyOnGround;

    private Entity lastEntity;
    private long landedAt = -1, reaction, nextClick;
    private int restoreSlot = -1;

    public TriggerBot() {
        super("Trigger Bot", "Attacks the target under your crosshair with natural timing.", Category.COMBAT);
        add(new HeaderSetting("TARGETS"));
        players = add(new BoolSetting("Players", "Attack players", true));
        monsters = add(new BoolSetting("Monsters", "Attack hostile mobs", false));
        animals = add(new BoolSetting("Animals", "Attack passive mobs", false));
        others = add(new BoolSetting("Others", "Attack any other living thing", false));
        invisible = add(new BoolSetting("Invisible", "Also attack invisible entities", false));
        teammates = add(new BoolSetting("Teammates", "Also attack players on your team", false));
        ignoreBlocking = add(new BoolSetting("Ignore Blocking", "Don't swing into a raised shield (unless you can axe it)", false));
        ignoreNaked = add(new BoolSetting("Ignore Unarmoured", "Skip players wearing no armour (often bots or AFK players)", false));
        range = add(new NumberSetting("Max Range", "Only attack within this distance", 3.0, 2.0, 6.0, 0.05, "m"));

        add(new HeaderSetting("TIMING"));
        timing = add(new ModeSetting("Timing", "Cooldown: swing on a charged weapon. CPS: click at a set rate (old combat)", "Cooldown", "Cooldown", "CPS"));
        minCooldown = add(new NumberSetting("Min Cooldown", "Swing once your weapon is this charged", 0.92, 0.5, 1.0, 0.01).visibleWhen(() -> timing.is("Cooldown")));
        minCps = add(new NumberSetting("Min CPS", "Slowest click rate", 9, 1, 20, 0.5).visibleWhen(() -> timing.is("CPS")));
        maxCps = add(new NumberSetting("Max CPS", "Fastest click rate", 13, 1, 20, 0.5).visibleWhen(() -> timing.is("CPS")));
        reactMin = add(new NumberSetting("Reaction Min", "Shortest wait after the crosshair lands on a new target", 40, 0, 400, 5, "ms"));
        reactMax = add(new NumberSetting("Reaction Max", "Longest wait after the crosshair lands on a new target", 110, 0, 500, 5, "ms"));
        distribution = add(new ModeSetting("Randomness", "How delays are spread: Gaussian clusters near the middle like a person", "Gaussian", "Gaussian", "Uniform"));
        hurtTime = add(new NumberSetting("Hurt Time", "Only swing when the target's hurt time is at most this (10 = always)", 10, 0, 10, 1));

        add(new HeaderSetting("HITS"));
        crits = add(new ModeSetting("Crits", "Only Crits waits for a falling hit; Smart waits only if you're already in the air", "Off", "Off", "Smart", "Only Crits"));
        hitChance = add(new NumberSetting("Hit Chance", "Chance each swing connects", 100, 50, 100, 1, "%"));
        missSwing = add(new BoolSetting("Miss Swings", "A 'missed' swing still swings at the air, like a real miss", true));
        axeOnShield = add(new BoolSetting("Axe On Shield", "Swap to an axe for one hit when the target blocks", true));

        add(new HeaderSetting("CONDITIONS"));
        weapon = add(new ModeSetting("Weapon", "What you must be holding", "Sword/Axe", "Any", "Sword/Axe", "Sword", "Axe", "Mace"));
        requireClick = add(new BoolSetting("Require Click", "Only while you hold attack", false));
        notWhileUsing = add(new BoolSetting("Not While Using", "Pause while eating, drinking or blocking", true));
        notInWater = add(new BoolSetting("Not In Water", "Pause while swimming", false));
        onlyOnGround = add(new BoolSetting("Only On Ground", "Only swing with your feet on the ground", false));
    }

    @Override public String getIcon() { return "icon_target"; }

    @Override
    protected void onDisable() {
        landedAt = -1;
        lastEntity = null;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null) return;
        if (restoreSlot >= 0) {
            p.getInventory().setSelectedSlot(restoreSlot);
            restoreSlot = -1;
        }
        Entity e = mc.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null;
        if (e == null || !valid(p, e)) {
            landedAt = -1;
            lastEntity = null;
            return;
        }
        if (!conditionsMet(p)) return;

        long now = System.currentTimeMillis();
        if (e != lastEntity || landedAt < 0) {
            lastEntity = e;
            landedAt = now;
            reaction = rand(reactMin.get(), reactMax.get());
        }
        if (now - landedAt < reaction) return;

        LivingEntity le = (LivingEntity) e;
        if (hurtTime.getInt() < 10 && le.hurtTime > hurtTime.getInt()) return;

        // crits
        boolean falling = p.fallDistance > 0 && !p.isOnGround() && p.getVelocity().y < 0;
        if (crits.is("Only Crits") && !falling) return;
        if (crits.is("Smart") && !p.isOnGround() && !falling && !p.isTouchingWater() && !p.isClimbing()) return; // rising: wait for the fall

        // timing
        if (timing.is("Cooldown")) {
            if (p.getAttackCooldownProgress(0.5f) < minCooldown.getFloat()) return;
        } else {
            if (now < nextClick) return;
            double lo = Math.min(minCps.get(), maxCps.get()), hi = Math.max(minCps.get(), maxCps.get());
            double cps = lo + ThreadLocalRandom.current().nextDouble() * (hi - lo);
            if (distribution.is("Gaussian")) cps = Math.max(lo, Math.min(hi, (lo + hi) / 2 + ThreadLocalRandom.current().nextGaussian() * (hi - lo) / 4));
            nextClick = now + Math.round(1000.0 / Math.max(1, cps));
        }

        // shield: one axe hit disables it
        if (le instanceof PlayerEntity pe && pe.isBlocking() && axeOnShield.isOn()) {
            for (int i = 0; i < 9; i++) {
                if (!p.getInventory().getStack(i).isIn(ItemTags.AXES)) continue;
                if (i != p.getInventory().getSelectedSlot()) {
                    restoreSlot = p.getInventory().getSelectedSlot();
                    p.getInventory().setSelectedSlot(i);
                }
                break;
            }
        }

        boolean connect = ThreadLocalRandom.current().nextDouble(100) < hitChance.get();
        if (connect) mc.interactionManager.attackEntity(p, e);
        if (connect || missSwing.isOn()) {
            p.swingHand(Hand.MAIN_HAND);
            if (!connect) p.resetTicksSinceLastAttack(); // a real miss still resets your cooldown
        }
        // the next swing on the same target waits a fresh (shorter) beat
        landedAt = now;
        reaction = rand(reactMin.get() * 0.3, reactMax.get() * 0.3);
    }

    private boolean conditionsMet(ClientPlayerEntity p) {
        if (requireClick.isOn() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) return false;
        if (notWhileUsing.isOn() && p.isUsingItem()) return false;
        if (notInWater.isOn() && p.isTouchingWater()) return false;
        if (onlyOnGround.isOn() && !p.isOnGround()) return false;
        ItemStack held = p.getMainHandStack();
        return switch (weapon.get()) {
            case "Sword/Axe" -> held.isIn(ItemTags.SWORDS) || held.isIn(ItemTags.AXES);
            case "Sword" -> held.isIn(ItemTags.SWORDS);
            case "Axe" -> held.isIn(ItemTags.AXES);
            case "Mace" -> held.isOf(Items.MACE);
            default -> true;
        };
    }

    private boolean valid(ClientPlayerEntity p, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == p) return false;
        if (p.distanceTo(e) > range.get() + 0.5 || p.getEyePos().distanceTo(e.getBoundingBox().getCenter()) > range.get() + 1.2) return false;
        if (le.isInvisible() && !invisible.isOn()) return false;
        if (le instanceof PlayerEntity pe) {
            if (!players.isOn() || pe.isCreative() || pe.isSpectator()) return false;
            if (!teammates.isOn() && p.isTeammate(pe)) return false;
            if (ignoreBlocking.isOn() && pe.isBlocking() && !axeOnShield.isOn()) return false;
            if (ignoreNaked.isOn() && pe.getArmor() == 0) return false;
            return true;
        }
        if (le instanceof Monster) return monsters.isOn();
        if (le instanceof AnimalEntity) return animals.isOn();
        return others.isOn();
    }

    private long rand(double lo, double hi) {
        if (hi <= lo) return Math.round(lo);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (distribution.is("Uniform")) return Math.round(lo + r.nextDouble() * (hi - lo));
        double mid = (lo + hi) / 2, sd = (hi - lo) / 4;
        return Math.round(Math.max(lo, Math.min(hi, mid + r.nextGaussian() * sd)));
    }
}
