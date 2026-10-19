package com.kaboom.jjk.roll;

import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.data.CENature;
import com.kaboom.jjk.data.CETier;
import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.Technique;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Server-side weighted rolls. The client only ever sees the result. */
public final class RollEngine {

    public static RollResult roll(RollType type, CursedEnergyData data, RandomSource random) {
        return switch (type) {
            case CURSED_ENERGY -> {
                Technique t = rollTechnique(data.getClan(), random);
                CETier tier = pick(List.of(CETier.values()), x -> x.weight, random);
                CENature nature = pick(List.of(CENature.values()), x -> x.weight, random);
                yield new RollResult(type, t.id(), tier.id(), nature.id());
            }
            case CLAN -> new RollResult(type, pick(List.of(Clan.values()), c -> c.weight, random).id(), "", "");
            case HEAVENLY_RESTRICTION -> new RollResult(type, rollRestriction(random).id(), "", "");
        };
    }

    public static Technique rollTechnique(Clan clan, RandomSource random) {
        List<Technique> pool = new ArrayList<>(List.of(Technique.values()));
        pool.remove(Technique.NONE);
        double biasScale = JJKConfig.CLAN_BIAS_MULTIPLIER.get();
        return pick(pool, t -> t.rarity.techniqueWeight() * (1.0 + (clan.bias(t) - 1.0) * biasScale), random);
    }

    public static HeavenlyRestriction rollRestriction(RandomSource random) {
        int roll = random.nextInt(100);
        int physical = JJKConfig.HR_PHYSICAL_CHANCE.get();
        int ce = JJKConfig.HR_CURSED_ENERGY_CHANCE.get();
        if (roll < physical) return HeavenlyRestriction.PHYSICAL;
        if (roll < physical + ce) return HeavenlyRestriction.CURSED_ENERGY;
        return HeavenlyRestriction.NONE;
    }

    public static <T> T pick(List<T> options, ToDoubleFunction<T> weight, RandomSource random) {
        double total = 0;
        for (T o : options) total += Math.max(0, weight.applyAsDouble(o));
        if (total <= 0) return options.get(random.nextInt(options.size()));
        double r = random.nextDouble() * total;
        for (T o : options) {
            r -= Math.max(0, weight.applyAsDouble(o));
            if (r < 0) return o;
        }
        return options.get(options.size() - 1);
    }

    /** Applies an accepted result to the player's data. */
    public static void apply(RollResult result, CursedEnergyData data) {
        switch (result.type()) {
            case CURSED_ENERGY -> data.setTechnique(Technique.byId(result.primary()),
                    CETier.byId(result.secondary()), CENature.byId(result.tertiary()));
            case CLAN -> data.setClan(Clan.byId(result.primary()));
            case HEAVENLY_RESTRICTION -> {
                HeavenlyRestriction hr = HeavenlyRestriction.byId(result.primary());
                // "No restriction" means the scroll found nothing to bind; the player keeps what they had.
                if (hr != HeavenlyRestriction.NONE) data.setRestriction(hr);
            }
        }
    }

    private RollEngine() {}
}
