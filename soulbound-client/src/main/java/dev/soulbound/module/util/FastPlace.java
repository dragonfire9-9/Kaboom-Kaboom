package dev.soulbound.module.util;

import dev.soulbound.mixin.MinecraftClientAccessor;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;

/** Shortens the delay between right-clicks while you hold use (vanilla is 4 ticks). */
public final class FastPlace extends Module {
    private final NumberSetting delay;
    private final BoolSetting blocksOnly, crystals, xp;

    public FastPlace() {
        super("Fast Place", "Place blocks and use items faster.", Category.UTIL);
        delay = add(new NumberSetting("Delay", "Ticks between uses while holding right-click (vanilla 4)", 0, 0, 4, 1, "t"));
        blocksOnly = add(new BoolSetting("Blocks Only", "Only for blocks", false));
        crystals = add(new BoolSetting("Crystals", "Also for end crystals", true).visibleWhen(blocksOnly::isOn));
        xp = add(new BoolSetting("XP Bottles", "Also for bottles o' enchanting", true).visibleWhen(blocksOnly::isOn));
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        var held = mc.player.getMainHandStack();
        if (blocksOnly.isOn() && !(held.getItem() instanceof BlockItem) && !(crystals.isOn() && held.isOf(Items.END_CRYSTAL))
                && !(xp.isOn() && held.isOf(Items.EXPERIENCE_BOTTLE))) return;
        var acc = (MinecraftClientAccessor) mc;
        if (acc.soulbound$getItemUseCooldown() > delay.getInt()) acc.soulbound$setItemUseCooldown(delay.getInt());
    }
}
