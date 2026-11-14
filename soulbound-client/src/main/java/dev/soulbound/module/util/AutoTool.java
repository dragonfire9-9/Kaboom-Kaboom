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
    private final dev.soulbound.setting.ModeSetting prefer = add(new dev.soulbound.setting.ModeSetting("Prefer", "Between equally fast tools", "Any", "Any", "Silk Touch", "Fortune"));
    private final BoolSetting onlySneak = add(new BoolSetting("Only Sneaking", "Only switch while sneaking", false));
    private int restore = -1;

    public AutoTool() {
        super("Auto Tool", "Picks the best tool for the block you mine.", Category.UTIL);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;
        boolean mining = mc.options.attackKey.isPressed() && mc.crosshairTarget instanceof BlockHitResult && (!onlySneak.isOn() || p.isSneaking());
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
        float sp = s.getMiningSpeedMultiplier(state);
        if (sp > 1 && !prefer.is("Any") && mc.world != null) {
            var reg = mc.world.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
            var key = prefer.is("Silk Touch") ? net.minecraft.enchantment.Enchantments.SILK_TOUCH : net.minecraft.enchantment.Enchantments.FORTUNE;
            if (reg.getOptional(key).map(e -> net.minecraft.enchantment.EnchantmentHelper.getLevel(e, s) > 0).orElse(false)) sp += 0.5f;
        }
        return sp;
    }
}
