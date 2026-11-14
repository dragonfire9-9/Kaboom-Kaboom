package dev.soulbound.module.visual;

import dev.soulbound.module.Configurable;
import dev.soulbound.setting.BoolSetting;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The ore groups Ore ESP and X-Ray can show, each with its own toggle and colour. */
public final class Ores {
    public record Type(String name, int color, boolean on, Block... blocks) {}

    public static final List<Type> TYPES = List.of(
            new Type("Diamond", 0xFF4FE3FF, true, Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE),
            new Type("Ancient Debris", 0xFFB0655A, true, Blocks.ANCIENT_DEBRIS),
            new Type("Emerald", 0xFF3DFF7A, true, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE),
            new Type("Gold", 0xFFFFD84D, true, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE),
            new Type("Iron", 0xFFE8C2A0, false, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE),
            new Type("Redstone", 0xFFFF3B3B, false, Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE),
            new Type("Lapis", 0xFF3B6BFF, false, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE),
            new Type("Copper", 0xFFFF8A4C, false, Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE),
            new Type("Coal", 0xFF5A5A62, false, Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE),
            new Type("Quartz", 0xFFF2EEE6, false, Blocks.NETHER_QUARTZ_ORE),
            new Type("Spawner", 0xFFC24DFF, true, Blocks.SPAWNER, Blocks.TRIAL_SPAWNER),
            new Type("Chests", 0xFFFFA64D, false, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST));

    /** Adds one toggle per ore group to a module; returns them in {@link #TYPES} order. */
    public static List<BoolSetting> addToggles(Configurable owner) {
        List<BoolSetting> out = new ArrayList<>();
        for (Type t : TYPES) out.add(owner.add(new BoolSetting(t.name(), "Show " + t.name().toLowerCase(java.util.Locale.ROOT), t.on())));
        return out;
    }

    /** Block -> colour for the groups switched on (0 = not shown). */
    public static Map<Block, Integer> enabled(List<BoolSetting> toggles) {
        Map<Block, Integer> m = new HashMap<>();
        for (int i = 0; i < TYPES.size(); i++) {
            if (!toggles.get(i).isOn()) continue;
            for (Block b : TYPES.get(i).blocks()) m.put(b, TYPES.get(i).color());
        }
        return m;
    }

    private Ores() {}
}
