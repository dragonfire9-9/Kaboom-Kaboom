package dev.soulbound.module.visual;

import dev.soulbound.mixin.SimpleOptionAccessor;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * X-Ray: rebuilds the world's meshes with everything hidden except the ores you pick, fully lit.
 * The checks run on the chunk-building threads, so they only read an immutable snapshot.
 */
public final class Xray extends Module {
    public static Xray INSTANCE;
    private final List<BoolSetting> toggles;
    private final BoolSetting fluids, fullbright, keepTorches;
    private final NumberSetting gamma;
    private static volatile Set<Block> visible = Set.of();
    private static volatile boolean on, showFluids;
    private int hash;
    private Double savedGamma;

    public Xray() {
        super("X-Ray", "See ores through the world.", Category.VISUAL);
        INSTANCE = this;
        add(new HeaderSetting("SHOW"));
        toggles = Ores.addToggles(this);
        keepTorches = add(new BoolSetting("Light Sources", "Also show torches, lava and glowstone so you keep your bearings", true));
        fluids = add(new BoolSetting("Fluids", "Show water and lava", false));
        add(new HeaderSetting("LIGHT"));
        fullbright = add(new BoolSetting("Fullbright", "Light everything up while X-Ray is on", true));
        gamma = add(new NumberSetting("Brightness", "Gamma while X-Ray is on", 1600, 100, 1600, 100, "%").visibleWhen(fullbright::isOn));
    }

    public static boolean active() { return on; }

    /** Should this block be meshed? (called on chunk-building threads) */
    public static boolean shows(BlockState s) { return visible.contains(s.getBlock()); }

    public static boolean fluids() { return showFluids; }

    @Override
    protected void onEnable() {
        rebuildSets();
        on = true;
        reload();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onDisable() {
        on = false;
        reload();
        if (savedGamma != null) ((SimpleOptionAccessor<Double>) (Object) mc.options.getGamma()).soulbound$setRaw(savedGamma);
        savedGamma = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onTick() {
        int h = toggles.stream().mapToInt(b -> b.isOn() ? 1 : 0).reduce(0, (a, b) -> a * 2 + b) * 4 + (fluids.isOn() ? 2 : 0) + (keepTorches.isOn() ? 1 : 0);
        if (h != hash) {
            rebuildSets();
            reload();
        }
        if (fullbright.isOn()) {
            if (savedGamma == null) savedGamma = mc.options.getGamma().getValue();
            ((SimpleOptionAccessor<Double>) (Object) mc.options.getGamma()).soulbound$setRaw(gamma.get() / 100.0);
        } else if (savedGamma != null) {
            ((SimpleOptionAccessor<Double>) (Object) mc.options.getGamma()).soulbound$setRaw(savedGamma);
            savedGamma = null;
        }
    }

    private void rebuildSets() {
        hash = toggles.stream().mapToInt(b -> b.isOn() ? 1 : 0).reduce(0, (a, b) -> a * 2 + b) * 4 + (fluids.isOn() ? 2 : 0) + (keepTorches.isOn() ? 1 : 0);
        Set<Block> v = new HashSet<>(Ores.enabled(toggles).keySet());
        if (keepTorches.isOn()) {
            v.addAll(List.of(net.minecraft.block.Blocks.TORCH, net.minecraft.block.Blocks.WALL_TORCH, net.minecraft.block.Blocks.SOUL_TORCH,
                    net.minecraft.block.Blocks.SOUL_WALL_TORCH, net.minecraft.block.Blocks.LANTERN, net.minecraft.block.Blocks.GLOWSTONE,
                    net.minecraft.block.Blocks.SEA_LANTERN, net.minecraft.block.Blocks.SHROOMLIGHT));
        }
        visible = Set.copyOf(v);
        showFluids = fluids.isOn();
    }

    private void reload() {
        if (mc.worldRenderer != null && mc.world != null) mc.worldRenderer.reload();
    }
}
