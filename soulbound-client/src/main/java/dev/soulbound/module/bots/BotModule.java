package dev.soulbound.module.bots;

import dev.soulbound.SoulBound;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.item.Item;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

/** Shared base for bots: targeting, aiming, movement keys, stats for the HUD, and a skin for the preview. */
public abstract class BotModule extends Module {
    public enum State {
        SEARCHING("SEARCHING"), CHASING("CHASING"), ENGAGING("ENGAGING"), BUILDING("BUILDING"),
        HEALING("HEALING"), RETREATING("RETREATING"), PAUSED("PAUSED");
        public final String label;
        State(String label) { this.label = label; }
    }

    public BoolSetting hud;
    public ModeSetting hudPosition;

    protected LivingEntity target;
    protected State state = State.SEARCHING;
    private boolean controlling;
    protected int hits, combo, bestCombo;
    private int lastPlayerHurt;
    private final Deque<Long> actions = new ArrayDeque<>();
    private final String code, skinFile;

    protected BotModule(String name, String description, String code, String skinFile) {
        super(name, description, Category.BOTS);
        this.code = code;
        this.skinFile = skinFile;
    }

    /** Call at the end of the subclass constructor so the HUD settings sit last. */
    protected void addHudSettings() {
        hud = add(new BoolSetting("Show HUD", "Show the bot status card", true));
        hudPosition = add(new ModeSetting("HUD Position", "Where the card sits", "Below Crosshair",
                "Below Crosshair", "Top Left", "Top Right", "Bottom Left").visibleWhen(hud::isOn));
    }

    @Override public String getTag() { return "PVP BOT"; }
    @Override public String getBadge() { return "BETA"; }

    /** Short code shown on the preview, e.g. "SB-01". */
    public String getCode() { return code; }

    /** Labels for the HUD's three stats, e.g. HITS / COMBO / CPS. */
    public String[] statLabels() { return new String[]{"HITS", "COMBO", "CPS"}; }

    public SkinTextures getSkin() {
        Identifier id = Identifier.of(SoulBound.MOD_ID, "textures/entity/" + skinFile + ".png");
        return SkinTextures.create(new AssetInfo.TextureAssetInfo(id, id), null, null, PlayerSkinType.WIDE);
    }

    // ------------------------------------------------------------------ stats
    public LivingEntity getTarget() { return target; }
    public State getState() { return state; }
    public int getHits() { return hits; }
    public int getCombo() { return combo; }
    public int getBestCombo() { return bestCombo; }

    public float getCps() {
        long now = System.currentTimeMillis();
        while (!actions.isEmpty() && now - actions.peekFirst() > 2000) actions.pollFirst();
        return actions.size() / 2f;
    }

    public double getReach() {
        if (target == null || mc.player == null) return 0;
        return reach(mc.player, target);
    }

    protected void countHit() {
        hits++;
        combo++;
        bestCombo = Math.max(bestCombo, combo);
        actions.addLast(System.currentTimeMillis());
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    protected void onEnable() {
        // only one bot drives the player at a time
        for (Module m : SoulBound.modules().all()) if (m != this && m instanceof BotModule && m.isEnabled()) m.setEnabled(false);
        hits = combo = bestCombo = 0;
        actions.clear();
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
    public final void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null || p.isDead() || p.isSpectator()) {
            release();
            state = State.PAUSED;
            return;
        }
        if (p.hurtTime > lastPlayerHurt) combo = 0;
        lastPlayerHurt = p.hurtTime;
        tickBot(p);
    }

    protected abstract void tickBot(ClientPlayerEntity p);

    // ------------------------------------------------------------------ targeting
    protected LivingEntity pickTarget(ClientPlayerEntity p, double range, String targets, String priority, boolean throughWalls) {
        Predicate<LivingEntity> valid = e -> isValid(p, e, range, targets, throughWalls);
        if (target != null && isValid(p, target, range + 2, targets, throughWalls)) {
            LivingEntity best = best(p, valid, priority);
            if (best == null || best == target || score(p, best, priority) > score(p, target, priority) * 0.6) return target;
            return best;
        }
        return best(p, valid, priority);
    }

    private LivingEntity best(ClientPlayerEntity p, Predicate<LivingEntity> valid, String priority) {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !valid.test(le)) continue;
            double s = score(p, le, priority);
            if (s < bestScore) {
                bestScore = s;
                best = le;
            }
        }
        return best;
    }

    private double score(ClientPlayerEntity p, LivingEntity e, String priority) {
        return switch (priority) {
            case "Lowest Health" -> e.getHealth() + e.getAbsorptionAmount();
            case "Crosshair" -> {
                float[] a = angles(p.getEyePos(), e.getBoundingBox().getCenter());
                yield Math.abs(MathHelper.wrapDegrees(a[0] - p.getYaw())) + Math.abs(a[1] - p.getPitch());
            }
            default -> p.squaredDistanceTo(e);
        };
    }

    private boolean isValid(ClientPlayerEntity p, LivingEntity e, double range, String targets, boolean throughWalls) {
        if (e == p || !e.isAlive() || e.isDead() || e.isSpectator() || e.isRemoved()) return false;
        if (p.squaredDistanceTo(e) > range * range) return false;
        if (e instanceof PlayerEntity pe) {
            if (targets.equals("Mobs") || pe.isCreative() || p.isTeammate(pe)) return false;
        } else if (targets.equals("Players") || !(e instanceof Monster)) {
            return false;
        }
        return throughWalls || p.canSee(e);
    }

    // ------------------------------------------------------------------ aiming
    protected static float[] angles(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[]{yaw, MathHelper.clamp(pitch, -90f, 90f)};
    }

    /** Turns toward a point at up to {@code max} degrees per tick; returns the remaining aim error. */
    protected static float turnTo(ClientPlayerEntity p, Vec3d point, float max) {
        float[] want = angles(p.getEyePos(), point);
        float dy = MathHelper.wrapDegrees(want[0] - p.getYaw());
        float dp = want[1] - p.getPitch();
        float sy = MathHelper.clamp(dy * 0.65f, -max, max);
        float sp = MathHelper.clamp(dp * 0.65f, -max, max);
        if (Math.abs(dy) <= max) sy = dy;
        if (Math.abs(dp) <= max) sp = dp;
        p.setYaw(p.getYaw() + sy);
        p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90f, 90f));
        return Math.abs(dy - sy) + Math.abs(dp - sp);
    }

    /** Distance from the eyes to the nearest point of the target's hitbox (what the server checks). */
    protected static double reach(ClientPlayerEntity p, LivingEntity t) {
        Box b = t.getBoundingBox();
        Vec3d eye = p.getEyePos();
        double x = MathHelper.clamp(eye.x, b.minX, b.maxX);
        double y = MathHelper.clamp(eye.y, b.minY, b.maxY);
        double z = MathHelper.clamp(eye.z, b.minZ, b.maxZ);
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    // ------------------------------------------------------------------ inventory
    /** Hotbar slot holding an item matching {@code item}, or -1. */
    protected static int hotbarSlot(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    // ------------------------------------------------------------------ movement keys
    protected void move(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sprint) {
        GameOptions o = mc.options;
        o.forwardKey.setPressed(forward);
        o.backKey.setPressed(back);
        o.leftKey.setPressed(left);
        o.rightKey.setPressed(right);
        o.jumpKey.setPressed(jump);
        o.sprintKey.setPressed(sprint);
        controlling = true;
    }

    /** Hand movement keys back to the real keyboard state. */
    protected void release() {
        if (!controlling) return;
        controlling = false;
        GameOptions o = mc.options;
        for (KeyBinding k : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sprintKey, o.useKey}) k.setPressed(false);
        KeyBinding.updatePressedStates();
    }
}
