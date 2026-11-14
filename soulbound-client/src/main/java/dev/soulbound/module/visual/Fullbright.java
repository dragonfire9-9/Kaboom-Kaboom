package dev.soulbound.module.visual;

import dev.soulbound.mixin.SimpleOptionAccessor;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Lights up caves and nights: a client-side night vision that never flickers, or a raised gamma. */
public final class Fullbright extends Module {
    private final ModeSetting mode = add(new ModeSetting("Mode", "Night Vision tints colours slightly; Gamma keeps them true", "Night Vision", "Night Vision", "Gamma"));
    private final NumberSetting gamma = add(new NumberSetting("Gamma", "Brightness (vanilla max is 100%)", 1600, 100, 1600, 50, "%").visibleWhen(() -> mode.is("Gamma")));
    private Double savedGamma;

    public Fullbright() {
        super("Fullbright", "See clearly in the dark, everywhere.", Category.VISUAL);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onTick() {
        if (mc.player == null) return;
        if (mode.is("Gamma")) {
            clearNightVision();
            if (savedGamma == null) savedGamma = mc.options.getGamma().getValue();
            ((SimpleOptionAccessor<Double>) (Object) mc.options.getGamma()).soulbound$setRaw(gamma.get() / 100.0);
            return;
        }
        restoreGamma();
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur == null || !cur.isInfinite())
            mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
    }

    private void clearNightVision() {
        StatusEffectInstance cur = mc.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (cur != null && cur.isInfinite()) mc.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
    }

    @SuppressWarnings("unchecked")
    private void restoreGamma() {
        if (savedGamma == null) return;
        ((SimpleOptionAccessor<Double>) (Object) mc.options.getGamma()).soulbound$setRaw(savedGamma);
        savedGamma = null;
    }

    @Override
    protected void onDisable() {
        restoreGamma();
        if (mc.player != null) clearNightVision();
    }
}
