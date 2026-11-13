package dev.soulbound.module.misc;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.List;

/**
 * Saves you from fall damage: just before landing it looks down and places a water bucket
 * (or another soft block), then picks the water back up and puts your view and slot back.
 */
public final class AutoMlg extends Module {
    private final ModeSetting item = add(new ModeSetting("Item", "What to land in", "Auto",
            "Auto", "Water Bucket", "Powder Snow", "Cobweb", "Slime Block", "Hay Bale"));
    private final NumberSetting minFall = add(new NumberSetting("Min Fall", "Only clutch falls at least this high", 4, 3, 30, 0.5, "m"));
    private final BoolSetting pickUp = add(new BoolSetting("Pick Up Water", "Scoop the water back up after landing", true));
    private final BoolSetting switchBack = add(new BoolSetting("Switch Back", "Return to the slot you were holding", true));
    private final BoolSetting restoreView = add(new BoolSetting("Restore View", "Look back where you were looking", true));
    private final BoolSetting smartTiming = add(new BoolSetting("Smart Timing", "Ease the last tick of a fast fall so the clutch is always in reach", true));

    private static final List<Item> AUTO_ORDER = List.of(Items.WATER_BUCKET, Items.POWDER_SNOW_BUCKET, Items.COBWEB, Items.SLIME_BLOCK, Items.HAY_BLOCK);

    private boolean placed, placedWater;
    private int prevSlot = -1, pickupTicks;
    private float prevPitch;

    public AutoMlg() {
        super("Auto MLG", "Places water or a soft block just before you land.", Category.MISC);
    }

    @Override public String getIcon() { return "icon_misc"; }

    @Override
    protected void onDisable() { reset(); }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        boolean safe = p.isTouchingWater() || p.isClimbing() || p.getAbilities().flying || p.isGliding() || p.hasVehicle();
        if (p.isOnGround() || safe) {
            if (placed) afterLanding(p);
            return;
        }
        if (placed || p.fallDistance < minFall.get() || p.getVelocity().y > -0.1) return;

        // find the ground under us
        Vec3d from = new Vec3d(p.getX(), p.getY(), p.getZ());
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, from.add(0, -8, 0),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.ANY, p));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos ground = hit.getBlockPos();
        BlockState gs = mc.world.getBlockState(ground);
        if (!mc.world.getFluidState(ground).isEmpty() || gs.isOf(Blocks.SLIME_BLOCK) || gs.isOf(Blocks.HAY_BLOCK)
                || gs.isOf(Blocks.COBWEB) || gs.isOf(Blocks.POWDER_SNOW)) return; // already a soft landing

        Item use = pickItem(p);
        if (use == null) return;

        double distance = p.getY() - hit.getPos().y;
        double speed = -p.getVelocity().y;
        // the item has to reach the ground from your eyes: buckets need the strict reach,
        // blocks get the server's extra block of tolerance
        boolean bucket = use == Items.WATER_BUCKET || use == Items.POWDER_SNOW_BUCKET;
        double reach = p.getBlockInteractionRange() - 0.1 + (bucket ? 0 : 0.9);
        double window = reach - p.getStandingEyeHeight();
        if (distance > window) {
            // falling fast enough to skip the whole window in one tick: ease this tick's fall so the
            // next tick ends inside it
            if (smartTiming.isOn() && distance - speed < window * 0.4) {
                Vec3d v = p.getVelocity();
                p.setVelocity(v.x, -(distance - window * 0.5), v.z);
            }
            return;
        }
        int slot = slotOf(p, use);

        prevSlot = p.getInventory().getSelectedSlot();
        prevPitch = p.getPitch();
        p.getInventory().setSelectedSlot(slot);
        p.setPitch(90);
        if (use == Items.WATER_BUCKET || use == Items.POWDER_SNOW_BUCKET) {
            mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
        } else {
            Vec3d face = Vec3d.ofCenter(ground).add(0, 0.5, 0);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(face, Direction.UP, ground, false));
        }
        p.swingHand(Hand.MAIN_HAND);
        placed = true;
        placedWater = use == Items.WATER_BUCKET;
        pickupTicks = 0;
    }

    private void afterLanding(ClientPlayerEntity p) {
        if (placedWater && pickUp.isOn()) {
            // give the water a tick to appear, then scoop it up with the now-empty bucket
            if (++pickupTicks < 2) return;
            int bucket = slotOf(p, Items.BUCKET);
            if (bucket >= 0) {
                p.getInventory().setSelectedSlot(bucket);
                p.setPitch(90);
                mc.interactionManager.interactItem(p, Hand.MAIN_HAND);
                p.swingHand(Hand.MAIN_HAND);
            }
        }
        if (switchBack.isOn() && prevSlot >= 0) p.getInventory().setSelectedSlot(prevSlot);
        if (restoreView.isOn()) p.setPitch(prevPitch);
        reset();
    }

    private void reset() {
        placed = placedWater = false;
        prevSlot = -1;
        pickupTicks = 0;
    }

    private Item pickItem(ClientPlayerEntity p) {
        if (!item.is("Auto")) {
            Item want = switch (item.get()) {
                case "Powder Snow" -> Items.POWDER_SNOW_BUCKET;
                case "Cobweb" -> Items.COBWEB;
                case "Slime Block" -> Items.SLIME_BLOCK;
                case "Hay Bale" -> Items.HAY_BLOCK;
                default -> Items.WATER_BUCKET;
            };
            return slotOf(p, want) >= 0 ? want : null;
        }
        boolean nether = mc.world.getRegistryKey() == net.minecraft.world.World.NETHER; // water evaporates there
        for (Item it : AUTO_ORDER) {
            if (nether && it == Items.WATER_BUCKET) continue;
            if (slotOf(p, it) >= 0) return it;
        }
        return null;
    }

    private static int slotOf(ClientPlayerEntity p, Item it) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(it)) return i;
        return -1;
    }
}
