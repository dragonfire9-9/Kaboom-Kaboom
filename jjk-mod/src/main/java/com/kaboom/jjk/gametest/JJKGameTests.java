package com.kaboom.jjk.gametest;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.ability.Abilities;
import com.kaboom.jjk.ability.Ability;
import com.kaboom.jjk.data.CETier;
import com.kaboom.jjk.data.CENature;
import com.kaboom.jjk.data.Clan;
import com.kaboom.jjk.data.CursedEnergyData;
import com.kaboom.jjk.data.Grade;
import com.kaboom.jjk.data.HeavenlyRestriction;
import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.data.Technique;
import com.kaboom.jjk.event.PlayerStats;
import com.kaboom.jjk.roll.RollEngine;
import com.kaboom.jjk.roll.RollResult;
import com.kaboom.jjk.roll.RollType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Run with {@code ./gradlew runGameTestServer}. CI runs these on every push. */
@GameTestHolder(JJKMod.MODID)
@PrefixGameTestTemplate(false)
public class JJKGameTests {

    @GameTest(template = "empty")
    public static void rollsProduceValidResults(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        CursedEnergyData data = new CursedEnergyData();
        for (RollType type : RollType.values()) {
            for (int i = 0; i < 500; i++) {
                RollResult r = RollEngine.roll(type, data, random);
                switch (type) {
                    case CURSED_ENERGY -> {
                        helper.assertTrue(Technique.byId(r.primary()) != Technique.NONE, "rolled NONE technique: " + r);
                        helper.assertTrue(CETier.byId(r.secondary()).id().equals(r.secondary()), "bad tier: " + r);
                        helper.assertTrue(CENature.byId(r.tertiary()).id().equals(r.tertiary()), "bad nature: " + r);
                    }
                    case CLAN -> helper.assertTrue(Clan.byId(r.primary()).id().equals(r.primary()), "bad clan: " + r);
                    case HEAVENLY_RESTRICTION -> helper.assertTrue(
                            HeavenlyRestriction.byId(r.primary()).id().equals(r.primary()), "bad restriction: " + r);
                }
            }
        }
        // Clan bias: Ryomen should roll Shrine far more often than Clanless.
        int ryomen = 0, clanless = 0;
        for (int i = 0; i < 4000; i++) {
            if (RollEngine.rollTechnique(Clan.RYOMEN, random) == Technique.SHRINE) ryomen++;
            if (RollEngine.rollTechnique(Clan.CLANLESS, random) == Technique.SHRINE) clanless++;
        }
        helper.assertTrue(ryomen > clanless * 3, "clan bias too weak: ryomen=" + ryomen + " clanless=" + clanless);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyTechniqueAbilityIsRegistered(GameTestHelper helper) {
        for (Technique t : Technique.values()) {
            for (String id : t.abilities) {
                Ability a = Abilities.get(id);
                helper.assertTrue(a != null, "missing ability " + id + " for " + t.id());
                helper.assertTrue(a.technique() == t, "ability " + id + " bound to wrong technique");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void dataSurvivesNbtRoundTrip(GameTestHelper helper) {
        var provider = helper.getLevel().registryAccess();
        CursedEnergyData data = new CursedEnergyData();
        data.setClan(Clan.GOJO);
        data.setTechnique(Technique.LIMITLESS, CETier.MONSTROUS, CENature.PRISTINE);
        data.setGrade(Grade.GRADE_1);
        data.setRctUnlocked(true);
        data.setCurrentCE(123);
        data.setCooldown("blue", 40);
        CompoundTag tag = data.serializeNBT(provider);

        CursedEnergyData copy = new CursedEnergyData();
        copy.deserializeNBT(provider, tag);
        helper.assertTrue(copy.getTechnique() == Technique.LIMITLESS, "technique lost");
        helper.assertTrue(copy.getClan() == Clan.GOJO, "clan lost");
        helper.assertTrue(copy.getCeTier() == CETier.MONSTROUS, "tier lost");
        helper.assertTrue(copy.getGrade() == Grade.GRADE_1, "grade lost");
        helper.assertTrue(copy.isRctUnlocked(), "rct lost");
        helper.assertTrue(Math.abs(copy.getCurrentCE() - 123) < 0.001, "CE lost");
        helper.assertTrue(copy.getCooldown("blue") == 40, "cooldown lost");
        helper.assertTrue(copy.getUnlockedAbilities().contains("hollow_purple"), "grade 1 Limitless should unlock Hollow Purple");
        helper.assertTrue(copy.isDomainUnlocked(), "grade 1 should unlock domain");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void heavenlyRestrictionAppliesToPlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CursedEnergyData data = ModAttachments.get(player);
        double baseDamage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

        data.setTechnique(Technique.SHRINE, CETier.HIGH, CENature.STANDARD);
        data.setRestriction(HeavenlyRestriction.PHYSICAL);
        PlayerStats.applyAttributes(player, data);

        helper.assertTrue(data.getMaxCE() == 0, "physical restriction must zero CE");
        helper.assertTrue(data.getTechnique() == Technique.NONE, "physical restriction must strip technique");
        helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) > baseDamage + 5, "strength bonus missing");
        helper.assertTrue(data.getUnlockedAbilities().contains("heavenly_dash"), "physical ability missing");
        helper.assertTrue(data.canSeeCurses(), "restricted body should perceive curses");

        data.setRestriction(HeavenlyRestriction.NONE);
        PlayerStats.applyAttributes(player, data);
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ATTACK_DAMAGE) - baseDamage) < 0.001, "modifiers not removed");
        helper.succeed();
    }
}
