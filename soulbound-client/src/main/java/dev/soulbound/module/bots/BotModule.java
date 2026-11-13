package dev.soulbound.module.bots;

import dev.soulbound.SoulBound;
import dev.soulbound.aim.AimController;
import dev.soulbound.aim.AimSettings;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

/** Shared base for bots: targeting, natural aiming, safe movement, survival helpers, HUD stats and a preview skin. */
public abstract class BotModule extends Module {
    public enum State {
        SEARCHING("SEARCHING"), CHASING("CHASING"), ENGAGING("ENGAGING"), BUILDING("BUILDING"), WALKING("WALKING"),
        GREETING("GREETING"), HEALING("HEALING"), RETREATING("RETREATING"), PAUSED("PAUSED"), DONE("DONE");
        public final String label;
        State(String label) { this.label = label; }
    }

    /** Which group the bot appears under in the Bots tab. */
    public enum Kind { PVP, UTILITY }

    public BoolSetting hud;
    public ModeSetting hudPosition;
    protected AimSettings aim;

    protected LivingEntity target;
    protected State state = State.SEARCHING;
    private boolean controlling;
    protected int hits, combo, bestCombo;
    private int lastPlayerHurt, totemTimer;
    private boolean eating;
    private int slotBeforeEating = -1;
    private final Deque<Long> actions = new ArrayDeque<>();
    private final String code, skinFile;

    protected BotModule(String name, String description, String code, String skinFile) {
        super(name, description, Category.BOTS);
        this.code = code;
        this.skinFile = skinFile;
    }

    /** Call at the end of the subclass constructor so the HUD settings sit last. */
    protected void addHudSettings() {
        add(new HeaderSetting("HUD"));
        hud = add(new BoolSetting("Show HUD", "Show the bot status card", true));
        hudPosition = add(new ModeSetting("HUD Position", "Where the card sits", "Below Crosshair",
                "Below Crosshair", "Top Left", "Top Right", "Bottom Left").visibleWhen(hud::isOn));
    }

    public Kind kind() { return Kind.PVP; }

    @Override public String getTag() { return kind() == Kind.PVP ? "PVP BOT" : "UTILITY BOT"; }
    @Override public String getBadge() { return "BETA"; }
    public String getCode() { return code; }
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
        for (Module m : SoulBound.modules().all()) if (m != this && m instanceof BotModule && m.isEnabled()) m.setEnabled(false);
        hits = combo = bestCombo = 0;
        actions.clear();
        target = null;
        state = State.SEARCHING;
        if (mc.player != null) lastPlayerHurt = mc.player.hurtTime;
    }

    @Override
    protected void onDisable() {
        stopEating();
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
        if (totemTimer > 0) totemTimer--;
        tickBot(p);
    }

    protected abstract void tickBot(ClientPlayerEntity p);

    // ------------------------------------------------------------------ aiming
    /** Ask the frame-rate aim controller to look at {@code point}; returns the current aim error in degrees. */
    protected float lookAt(Vec3d point) {
        AimController.request(this, point, aim);
        return AimController.errorTo(point);
    }

    protected static Vec3d aimPoint(ClientPlayerEntity p, LivingEntity t, String mode) {
        Box b = t.getBoundingBox();
        double h = b.maxY - b.minY;
        Vec3d c = b.getCenter();
        Vec3d eye = p.getEyePos();
        return switch (mode) {
            case "Head" -> new Vec3d(c.x, b.minY + h * 0.88, c.z);
            case "Chest" -> new Vec3d(c.x, b.minY + h * 0.62, c.z);
            case "Legs" -> new Vec3d(c.x, b.minY + h * 0.28, c.z);
            case "Dynamic" -> {
                // a point that drifts slowly around the upper body
                double t2 = System.currentTimeMillis() / 1000.0;
                double ox = Math.sin(t2 * 0.9) * (b.maxX - b.minX) * 0.25, oz = Math.cos(t2 * 0.7) * (b.maxZ - b.minZ) * 0.25;
                double oy = h * (0.55 + 0.15 * Math.sin(t2 * 1.3));
                yield new Vec3d(c.x + ox, b.minY + oy, c.z + oz);
            }
            default -> new Vec3d( // closest point, kept off the edges
                    MathHelper.clamp(eye.x, b.minX + 0.1, b.maxX - 0.1),
                    MathHelper.clamp(eye.y, b.minY + h * 0.35, b.maxY - h * 0.1),
                    MathHelper.clamp(eye.z, b.minZ + 0.1, b.maxZ - 0.1));
        };
    }

    protected static float[] angles(Vec3d from, Vec3d to) { return AimController.angles(from, to); }

    protected static double reach(ClientPlayerEntity p, LivingEntity t) {
        Box b = t.getBoundingBox();
        Vec3d eye = p.getEyePos();
        double x = MathHelper.clamp(eye.x, b.minX, b.maxX);
        double y = MathHelper.clamp(eye.y, b.minY, b.maxY);
        double z = MathHelper.clamp(eye.z, b.minZ, b.maxZ);
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    // ------------------------------------------------------------------ targeting
    /** Picks a target. {@code fov} is the full cone in degrees (360 = anywhere). */
    protected LivingEntity pickTarget(ClientPlayerEntity p, double range, String targets, String priority, boolean throughWalls,
                                      double fov, boolean ignoreInvisible, boolean lock) {
        Predicate<LivingEntity> valid = e -> isValid(p, e, range, targets, throughWalls, fov, ignoreInvisible);
        if (target != null && isValid(p, target, range + 2, targets, throughWalls, 360, ignoreInvisible)) {
            if (lock) return target;
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
            case "Crosshair" -> AimController.errorTo(e.getBoundingBox().getCenter());
            default -> p.squaredDistanceTo(e);
        };
    }

    private boolean isValid(ClientPlayerEntity p, LivingEntity e, double range, String targets, boolean throughWalls,
                            double fov, boolean ignoreInvisible) {
        if (e == p || !e.isAlive() || e.isDead() || e.isSpectator() || e.isRemoved()) return false;
        if (p.squaredDistanceTo(e) > range * range) return false;
        if (ignoreInvisible && e.isInvisible()) return false;
        if (e instanceof PlayerEntity pe) {
            if (targets.equals("Mobs") || pe.isCreative() || p.isTeammate(pe)) return false;
        } else if (targets.equals("Players") || !(e instanceof Monster)) {
            return false;
        }
        if (fov < 360) {
            float[] a = angles(p.getEyePos(), e.getBoundingBox().getCenter());
            if (Math.abs(MathHelper.wrapDegrees(a[0] - p.getYaw())) > fov / 2) return false;
        }
        return throughWalls || p.canSee(e);
    }

    // ------------------------------------------------------------------ inventory & survival
    protected static int hotbarSlot(ClientPlayerEntity p, Item item) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    protected void keepTotem(ClientPlayerEntity p) {
        if (totemTimer > 0 || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i < 9 ? 36 + i : i, 40, SlotActionType.SWAP, p);
            totemTimer = 5;
            return;
        }
    }

    /** Eats a golden apple while at or below {@code health}; returns true while eating. */
    protected boolean handleEating(ClientPlayerEntity p, double health) {
        int slot = -1;
        for (Item it : new Item[]{Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE}) if (slot < 0) slot = hotbarSlot(p, it);
        if (p.getHealth() > health || slot < 0) {
            stopEating();
            return false;
        }
        if (!eating) {
            slotBeforeEating = p.getInventory().getSelectedSlot();
            eating = true;
        }
        p.getInventory().setSelectedSlot(slot);
        mc.options.useKey.setPressed(true);
        controlling = true;
        return true;
    }

    protected void stopEating() {
        if (!eating) return;
        eating = false;
        mc.options.useKey.setPressed(false);
        if (mc.player != null && slotBeforeEating >= 0) mc.player.getInventory().setSelectedSlot(slotBeforeEating);
        slotBeforeEating = -1;
    }

    // ------------------------------------------------------------------ movement
    /**
     * Presses movement keys. With {@code avoidEdges}, any key that would walk off a drop
     * (or into lava/fire) is released instead.
     */
    protected void move(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sprint, boolean avoidEdges) {
        ClientPlayerEntity p = mc.player;
        if (avoidEdges && p != null && p.isOnGround()) {
            float yaw = (float) Math.toRadians(p.getYaw());
            Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
            if (forward && unsafe(p, fwd)) forward = false;
            if (back && unsafe(p, fwd.multiply(-1))) back = false;
            if (left && unsafe(p, side)) left = false;
            if (right && unsafe(p, side.multiply(-1))) right = false;
        }
        GameOptions o = mc.options;
        o.forwardKey.setPressed(forward);
        o.backKey.setPressed(back);
        o.leftKey.setPressed(left);
        o.rightKey.setPressed(right);
        o.jumpKey.setPressed(jump);
        o.sprintKey.setPressed(sprint && forward);
        controlling = true;
    }

    protected void move(boolean forward, boolean back, boolean left, boolean right, boolean jump, boolean sprint) {
        move(forward, back, left, right, jump, sprint, false);
    }

    private boolean unsafe(ClientPlayerEntity p, Vec3d dir) {
        Vec3d at = new Vec3d(p.getX(), p.getY(), p.getZ()).add(dir.multiply(0.8));
        BlockPos feet = BlockPos.ofFloored(at);
        for (int d = 0; d <= 3; d++) {
            BlockState s = mc.world.getBlockState(feet.down(d));
            if (s.isOf(Blocks.LAVA) || s.isOf(Blocks.FIRE) || s.isOf(Blocks.MAGMA_BLOCK)) return true;
            if (d > 0 && !s.getCollisionShape(mc.world, feet.down(d)).isEmpty()) return false;
        }
        return true; // more than a three-block drop
    }

    protected void release() {
        if (!controlling) return;
        controlling = false;
        GameOptions o = mc.options;
        for (KeyBinding k : new KeyBinding[]{o.forwardKey, o.backKey, o.leftKey, o.rightKey, o.jumpKey, o.sprintKey, o.useKey}) k.setPressed(false);
        KeyBinding.updatePressedStates();
    }
}
