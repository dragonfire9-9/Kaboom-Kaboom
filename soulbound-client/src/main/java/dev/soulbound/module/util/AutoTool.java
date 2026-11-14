package dev.soulbound.module.util;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;

/** Switches to the fastest tool in your hotbar for the block you're mining. */
public final class AutoTool extends Module {
    private final BoolSetting switchBack = add(new BoolSetting("Switch Back", "Return to your slot when you stop mining", true));
    private final BoolSetting saveTools = add(new BoolSetting("Save Tools", "Skip tools that are about to break", true));
    private int restore = -1;

    public AutoTool() {
        super("Auto Tool", "Picks the best tool for the block you mine.", Category.UTIL);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        boolean mining = mc.options.attackKey.isPressed() && mc.crosshairTarget instanceof BlockHitResult;
        if (!mining) {
            if (restore >= 0 && switchBack.isOn()) p.getInventory().setSelectedSlot(restore);
            restore = -1;
            return;
        }
        BlockState state = mc.world.getBlockState(((BlockHitResult) mc.crosshairTarget).getBlockPos());
        if (state.isAir()) return;
        var inv = p.getInventory();
        int cur = inv.getSelectedSlot(), best = cur;
        float bestSpeed = speed(inv.getStack(cur), state);
        for (int i = 0; i < 9; i++) {
            float s = speed(inv.getStack(i), state);
            if (s > bestSpeed + 0.01f) { bestSpeed = s; best = i; }
        }
        if (best != cur) {
            if (restore < 0) restore = cur;
            inv.setSelectedSlot(best);
        }
    }

    private float speed(ItemStack s, BlockState state) {
        if (s.isEmpty()) return 1;
        if (saveTools.isOn() && s.isDamageable() && s.getMaxDamage() - s.getDamage() <= 3) return 0;
        return s.getMiningSpeedMultiplier(state);
    }
}
