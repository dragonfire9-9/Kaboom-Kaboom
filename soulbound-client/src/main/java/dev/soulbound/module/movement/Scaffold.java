package dev.soulbound.module.movement;

import dev.soulbound.aim.AimController;
import dev.soulbound.aim.AimSettings;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import dev.soulbound.util.KeyState;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Places blocks under you as you walk off edges, turning to the block naturally (or not at all). */
public final class Scaffold extends Module {
    private final ModeSetting rotation, blockPick;
    private final NumberSetting delay, extend, maxAim;
    private final BoolSetting tower, sneakEdge, sprint, keepY, switchBack, safeWalk;
    private final AimSettings aim;
    private int timer, restore = -1, startY = Integer.MIN_VALUE;
    private boolean sneaking;

    public Scaffold() {
        super("Scaffold", "Place blocks under you while you walk.", Category.MOVEMENT);
        add(new HeaderSetting("PLACING"));
        rotation = add(new ModeSetting("Rotation", "Natural turns to the block; None places without turning (some servers flag it)", "Natural", "Natural", "None"));
        blockPick = add(new ModeSetting("Blocks", "Which blocks to use", "Biggest Stack", "Biggest Stack", "Held", "First"));
        delay = add(new NumberSetting("Delay", "Ticks between placements", 0, 0, 10, 1, "t"));
        extend = add(new NumberSetting("Extend", "Place this far ahead of you", 0, 0, 3, 0.5, " blocks"));
        maxAim = add(new NumberSetting("Max Aim Error", "Place once aimed this close (Natural)", 20, 2, 60, 1, "°").visibleWhen(() -> rotation.is("Natural")));
        add(new HeaderSetting("MOVEMENT"));
        tower = add(new BoolSetting("Tower", "Hold jump to pillar straight up", true));
        keepY = add(new BoolSetting("Keep Y", "Stay on the level you started on, even when jumping", false));
        sprint = add(new BoolSetting("Allow Sprint", "Keep sprinting while bridging", true));
        sneakEdge = add(new BoolSetting("Sneak At Edge", "Crouch for a moment at each edge, like a careful bridge", false));
        safeWalk = add(new BoolSetting("Safe Walk", "Never walk off while waiting for a block", true));
        switchBack = add(new BoolSetting("Switch Back", "Return to your slot when you turn it off", true));
        aim = new AimSettings(this, 900, 0.35, 20);
    }

    @Override
    protected void onEnable() {
        restore = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
        startY = mc.player != null ? mc.player.getBlockPos().getY() - 1 : Integer.MIN_VALUE;
    }

    @Override
    protected void onDisable() {
        if (switchBack.isOn() && restore >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(restore);
        if (sneaking) KeyState.release(mc.options.sneakKey);
        sneaking = false;
    }

    public boolean safeWalk() { return isEnabled() && safeWalk.isOn(); }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null) return;
        if (timer > 0) timer--;
        if (!sprint.isOn()) p.setSprinting(false);
        int slot = blockSlot();
        if (slot < 0) return;
        int y = keepY.isOn() ? startY : p.getBlockPos().getY() - 1;
        Vec3d v = p.getVelocity();
        Vec3d ahead = new Vec3d(p.getX(), 0, p.getZ());
        if (extend.get() > 0 && v.horizontalLengthSquared() > 1e-4) ahead = ahead.add(new Vec3d(v.x, 0, v.z).normalize().multiply(extend.get()));
        BlockPos target = BlockPos.ofFloored(ahead.x, y, ahead.z);
        boolean towering = tower.isOn() && mc.options.jumpKey.isPressed() && !mc.options.forwardKey.isPressed();
        if (towering) target = p.getBlockPos().down();
        if (!mc.world.getBlockState(target).isReplaceable()) {
            if (sneaking) { KeyState.release(mc.options.sneakKey); sneaking = false; }
            return;
        }
        BlockHitResult hit = hitFor(target);
        if (hit == null) return;
        if (sneakEdge.isOn() && !sneaking) { mc.options.sneakKey.setPressed(true); sneaking = true; }
        if (rotation.is("Natural")) {
            AimController.request(this, hit.getPos(), aim);
            if (AimController.errorTo(hit.getPos()) > maxAim.getFloat()) return;
        }
        if (timer > 0) return;
        p.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        p.swingHand(Hand.MAIN_HAND);
        timer = delay.getInt();
        if (towering && p.isOnGround()) p.jump();
    }

    private BlockHitResult hitFor(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState ns = mc.world.getBlockState(n);
            if (ns.isReplaceable() || ns.getCollisionShape(mc.world, n).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3d hv = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double dist = eye.distanceTo(hv);
            if (dist > 4.5 || dist >= bestD) continue;
            bestD = dist;
            best = new BlockHitResult(hv, face, n, false);
        }
        if (best == null) {
            // nothing to click against: try the block diagonally below to build a support
            for (Direction d : Direction.Type.HORIZONTAL) {
                BlockPos n = pos.offset(d);
                if (mc.world.getBlockState(n).isReplaceable() && !mc.world.getBlockState(n.down()).isReplaceable()) {
                    BlockPos base = n.down();
                    return new BlockHitResult(Vec3d.ofCenter(base).add(0, 0.5, 0), Direction.UP, base, false);
                }
            }
        }
        return best;
    }

    private int blockSlot() {
        var inv = mc.player.getInventory();
        if (blockPick.is("Held") && valid(inv.getStack(inv.getSelectedSlot()))) return inv.getSelectedSlot();
        int best = -1, bestCount = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.getStack(i);
            if (!valid(s)) continue;
            if (blockPick.is("First")) return i;
            if (s.getCount() > bestCount) { bestCount = s.getCount(); best = i; }
        }
        return best;
    }

    private static boolean valid(ItemStack s) {
        if (!(s.getItem() instanceof BlockItem bi)) return false;
        var st = bi.getBlock().getDefaultState();
        return st.isFullCube(mc.world, BlockPos.ORIGIN) && !st.hasBlockEntity() && !(bi.getBlock() instanceof net.minecraft.block.FallingBlock);
    }
}
