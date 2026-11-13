package dev.soulbound.module.combat;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Hit-crystal macro: right-click a block while holding obsidian (or a crystal on obsidian) and it
 * places obsidian, swaps to a crystal, places it and hits it.
 */
public final class AutoHitCrystal extends Module {
    private final ModeSetting mode;
    private final NumberSetting placeDelay, breakDelay;
    private final BoolSetting swapBack, autoBreak;

    private enum Step { IDLE, OBSIDIAN, CRYSTAL, BREAK }
    private Step step = Step.IDLE;
    private BlockPos base;
    private int timer, prevSlot = -1;

    public AutoHitCrystal() {
        super("Auto Hit Crystal", "Right-click to place obsidian, a crystal, and hit it.", Category.COMBAT);
        add(new HeaderSetting("BEHAVIOUR"));
        mode = add(new ModeSetting("Mode", "What a right-click does", "Full", "Full", "Crystal Only"));
        autoBreak = add(new BoolSetting("Auto Break", "Also hit any crystal you look at", true));
        swapBack = add(new BoolSetting("Swap Back", "Return to your previous slot afterwards", true));
        add(new HeaderSetting("TIMING"));
        placeDelay = add(new NumberSetting("Place Delay", "Ticks between steps", 1, 0, 6, 1, "t"));
        breakDelay = add(new NumberSetting("Break Delay", "Ticks before hitting the crystal", 1, 0, 6, 1, "t"));
    }

    @Override public String getIcon() { return "icon_crystal"; }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.currentScreen != null || mc.interactionManager == null) return;
        if (timer > 0) {
            timer--;
            return;
        }

        if (autoBreak.isOn() && step == Step.IDLE && mc.crosshairTarget instanceof EntityHitResult eh && eh.getEntity() instanceof EndCrystalEntity c) {
            mc.interactionManager.attackEntity(p, c);
            p.swingHand(Hand.MAIN_HAND);
            timer = breakDelay.getInt();
            return;
        }

        switch (step) {
            case IDLE -> {
                if (!mc.options.useKey.isPressed() || !(mc.crosshairTarget instanceof BlockHitResult bh)) return;
                BlockState looked = mc.world.getBlockState(bh.getBlockPos());
                boolean onObsidian = looked.isOf(Blocks.OBSIDIAN) || looked.isOf(Blocks.BEDROCK);
                prevSlot = p.getInventory().getSelectedSlot();
                if (onObsidian && bh.getSide() == Direction.UP) {
                    base = bh.getBlockPos();
                    step = Step.CRYSTAL;
                } else if (mode.is("Full") && p.getMainHandStack().isOf(Items.OBSIDIAN)) {
                    base = bh.getBlockPos().offset(bh.getSide());
                    step = Step.OBSIDIAN;
                }
            }
            case OBSIDIAN -> {
                // vanilla already placed the obsidian from the click; continue once it exists
                if (mc.world.getBlockState(base).isOf(Blocks.OBSIDIAN)) {
                    step = Step.CRYSTAL;
                    timer = placeDelay.getInt();
                } else if (!mc.options.useKey.isPressed()) {
                    finish(p);
                }
            }
            case CRYSTAL -> {
                int slot = slot(p);
                if (slot < 0 || !mc.world.getBlockState(base.up()).isAir()) {
                    finish(p);
                    return;
                }
                p.getInventory().setSelectedSlot(slot);
                Vec3d face = Vec3d.ofCenter(base).add(0, 0.5, 0);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(face, Direction.UP, base, false));
                p.swingHand(Hand.MAIN_HAND);
                step = Step.BREAK;
                timer = breakDelay.getInt();
            }
            case BREAK -> {
                BlockPos up = base.up();
                Box box = new Box(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
                var crystals = mc.world.getEntitiesByClass(EndCrystalEntity.class, box, e -> true);
                if (!crystals.isEmpty()) {
                    mc.interactionManager.attackEntity(p, crystals.getFirst());
                    p.swingHand(Hand.MAIN_HAND);
                }
                finish(p);
            }
        }
    }

    private int slot(ClientPlayerEntity p) {
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) return i;
        return -1;
    }

    private void finish(ClientPlayerEntity p) {
        if (swapBack.isOn() && prevSlot >= 0) p.getInventory().setSelectedSlot(prevSlot);
        prevSlot = -1;
        step = Step.IDLE;
        timer = placeDelay.getInt() + 2;
    }

    @Override
    protected void onDisable() { step = Step.IDLE; }
}
