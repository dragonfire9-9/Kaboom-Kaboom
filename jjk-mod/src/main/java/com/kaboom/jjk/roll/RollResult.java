package com.kaboom.jjk.roll;

import com.kaboom.jjk.data.CETier;
import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.Rarity;
import com.kaboom.jjk.data.Technique;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The outcome of one scroll roll. Generic string slots keep the wire format identical for all scroll types:
 * <ul>
 *     <li>CURSED_ENERGY: primary = technique, secondary = CE tier, tertiary = CE nature</li>
 *     <li>CLAN: primary = clan</li>
 *     <li>HEAVENLY_RESTRICTION: primary = restriction</li>
 * </ul>
 */
public record RollResult(RollType type, String primary, String secondary, String tertiary) {
    public static final StreamCodec<ByteBuf, RollResult> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, r -> r.type().ordinal(),
            ByteBufCodecs.STRING_UTF8, RollResult::primary,
            ByteBufCodecs.STRING_UTF8, RollResult::secondary,
            ByteBufCodecs.STRING_UTF8, RollResult::tertiary,
            (t, a, b, c) -> new RollResult(RollType.byOrdinal(t), a, b, c));

    public Rarity rarity() {
        return switch (type) {
            case CURSED_ENERGY -> {
                Rarity tech = Technique.byId(primary).rarity;
                Rarity tier = CETier.byId(secondary).rarity;
                yield tech.ordinal() >= tier.ordinal() ? tech : tier;
            }
            case CLAN -> Clan.byId(primary).rarity;
            case HEAVENLY_RESTRICTION -> HeavenlyRestriction.byId(primary).rarity;
        };
    }

    /** Colour of the reel cell / reveal glow. */
    public int color() {
        return rarity().color;
    }

    public Component title() {
        return switch (type) {
            case CURSED_ENERGY -> Technique.byId(primary).displayName();
            case CLAN -> Clan.byId(primary).displayName();
            case HEAVENLY_RESTRICTION -> HeavenlyRestriction.byId(primary).displayName();
        };
    }

    public Component description() {
        return switch (type) {
            case CURSED_ENERGY -> Technique.byId(primary).description();
            case CLAN -> Clan.byId(primary).description();
            case HEAVENLY_RESTRICTION -> Component.translatable("jjk.heavenly_restriction." + primary + ".desc");
        };
    }
}
