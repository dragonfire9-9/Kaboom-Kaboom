package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Lights up caves and nights with a client-side night vision effect that never flickers. */
public final class Fullbright extends Module {
    public Fullbright() {
        super("Fullbright", "See clearly in the dark, everywhere.", Category.VISUAL);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || !cur.isInfinite())
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
    }

    @Override
    protected void onDisable() {
        if (mc.player == null) return;
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur != null && cur.isInfinite()) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }
}
