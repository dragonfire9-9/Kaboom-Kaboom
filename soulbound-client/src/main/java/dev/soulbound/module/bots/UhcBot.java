package dev.soulbound.module.bots;

import dev.soulbound.SoulBound;
import dev.soulbound.module.misc.AutoMlg;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * UHC bot: the sword bot's melee plus bow shots at range (with arc and lead), fishing-rod
 * hooks for knockback, a water clutch on big falls and golden apples when low.
 */
public final class UhcBot extends SwordBot {
    private final BoolSetting useBow, leadShots, useRod, waterClutch;
    private final NumberSetting bowMin, bowMax, bowCharge, rodMin, rodMax, rodCooldown;

    private boolean drawing;
    private int bowCool, rodTimer = -1, rodCool, rodSlotRestore = -1;

    public UhcBot() {
        super("UHC Bot", "UHC fighter: sword, bow, rod, gapples and water clutches.", "UB-03", "uhc_bot");
        add(new HeaderSetting("BOW"));
        useBow = add(new BoolSetting("Use Bow", "Shoot at targets out of melee range", true));
        bowMin = add(new NumberSetting("Bow Min Range", "Shoot when farther than this", 9, 4, 30, 1, "m").visibleWhen(useBow::isOn));
        bowMax = add(new NumberSetting("Bow Max Range", "Don't shoot beyond this", 40, 10, 80, 1, "m").visibleWhen(useBow::isOn));
        bowCharge = add(new NumberSetting("Bow Charge", "Ticks to draw before releasing", 20, 6, 20, 1, "t").visibleWhen(useBow::isOn));
        leadShots = add(new BoolSetting("Lead Shots", "Aim ahead of moving targets", true).visibleWhen(useBow::isOn));
        add(new HeaderSetting("ROD"));
        useRod = add(new BoolSetting("Use Rod", "Hook targets for knockback before closing in", true));
        rodMin = add(new NumberSetting("Rod Min Range", "Hook when farther than this", 3.5, 2, 8, 0.5, "m").visibleWhen(useRod::isOn));
        rodMax = add(new NumberSetting("Rod Max Range", "Hook when closer than this", 7, 3, 12, 0.5, "m").visibleWhen(useRod::isOn));
        rodCooldown = add(new NumberSetting("Rod Cooldown", "Ticks between hooks", 30, 10, 100, 5, "t").visibleWhen(useRod::isOn));
        add(new HeaderSetting("UTILITY"));
        waterClutch = add(new BoolSetting("Water Clutch", "Place water under you on big falls", true));
        eatGapples.set(true);
        autoTotem.set(true);
        addHudSettings();
    }

    @Override public String getIcon() { return "icon_bow"; }

    @Override
    protected boolean special(ClientPlayerEntity p) {
        if (waterClutch.isOn()) {
            AutoMlg mlg = SoulBound.modules().get(AutoMlg.class);
            if (mlg != null && !mlg.isEnabled()) mlg.runClutch();
        }
        if (bowCool > 0) bowCool--;
        if (rodCool > 0) rodCool--;
        double dist = p.distanceTo(target);

        // the rod is mid-cast: wait for it to land, then reel and go back to the sword
        if (rodTimer >= 0) {
            rodTimer++;
            lookAt(target.getBoundingBox().getCenter().add(0, 0.4, 0));
            if (rodTimer >= 7 || (p.fishHook != null && p.fishHook.getHookedEntity() != null)) {
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                if (rodSlotRestore >= 0) p.getInventory().setSelectedSlot(rodSlotRestore);
                rodTimer = -1;
                rodSlotRestore = -1;
                rodCool = rodCooldown.getInt();
            }
            state = State.ENGAGING;
            move(true, false, false, false, false, sprint.isOn(), avoidEdges.isOn());
            return true;
        }

        // bow at range
        int bow = hotbarSlot(p, Items.BOW);
        boolean hasArrows = p.getInventory().contains(s -> s.isOf(Items.ARROW) || s.isOf(Items.SPECTRAL_ARROW) || s.isOf(Items.TIPPED_ARROW)) || p.isCreative();
        if (useBow.isOn() && bow >= 0 && hasArrows && dist >= bowMin.get() && dist <= bowMax.get() && p.canSee(target) && bowCool <= 0) {
            p.getInventory().setSelectedSlot(bow);
            state = State.ENGAGING;
            float err = lookAt(ballisticPoint(p));
            mc.options.useKey.setPressed(true);
            drawing = true;
            if (p.isUsingItem() && p.getItemUseTime() >= bowCharge.getInt() && err < 2.5f) {
                mc.options.useKey.setPressed(false); // releasing the key fires the arrow
                drawing = false;
                countHit();
                bowCool = 6;
            }
            move(false, false, (p.age / 30) % 2 == 0, (p.age / 30) % 2 == 1, false, false, avoidEdges.isOn());
            return true;
        }
        if (drawing) {
            mc.options.useKey.setPressed(false);
            drawing = false;
        }

        // rod for knockback while closing the gap
        int rod = hotbarSlot(p, Items.FISHING_ROD);
        if (useRod.isOn() && rod >= 0 && rodCool <= 0 && dist >= rodMin.get() && dist <= rodMax.get()) {
            Vec3d aimAt = target.getBoundingBox().getCenter().add(0, 0.3 + dist * 0.03, 0);
            if (lookAt(aimAt) < 6) {
                rodSlotRestore = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(rod);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
                rodTimer = 0;
            }
            state = State.CHASING;
            move(true, false, false, false, false, sprint.isOn(), avoidEdges.isOn());
            return true;
        }
        return false;
    }

    /** A point along the launch direction that lands a full-power arrow on the (predicted) target. */
    private Vec3d ballisticPoint(ClientPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d aimAt = target.getBoundingBox().getCenter();
        double v = 3.0 * Math.min(1, Math.pow(Math.min(20, bowCharge.getInt()) / 20.0, 2) * 0.5 + 0.5), g = 0.05;
        if (leadShots.isOn()) {
            double flight = eye.distanceTo(aimAt) / v;
            Vec3d vel = target.getVelocity();
            aimAt = aimAt.add(vel.x * flight, 0, vel.z * flight);
        }
        double dx = Math.hypot(aimAt.x - eye.x, aimAt.z - eye.z), dy = aimAt.y - eye.y;
        double disc = v * v * v * v - g * (g * dx * dx + 2 * dy * v * v);
        double theta = disc < 0 ? Math.toRadians(40) : Math.atan((v * v - Math.sqrt(disc)) / (g * dx));
        float yaw = (float) Math.toDegrees(Math.atan2(aimAt.z - eye.z, aimAt.x - eye.x)) - 90f;
        float pitch = (float) -Math.toDegrees(theta);
        float ry = (float) Math.toRadians(yaw), rp = (float) Math.toRadians(MathHelper.clamp(pitch, -89, 89));
        Vec3d dir = new Vec3d(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
        return eye.add(dir.multiply(10));
    }

    @Override
    protected void onDisable() {
        if (drawing) mc.options.useKey.setPressed(false);
        drawing = false;
        rodTimer = -1;
        super.onDisable();
    }
}
