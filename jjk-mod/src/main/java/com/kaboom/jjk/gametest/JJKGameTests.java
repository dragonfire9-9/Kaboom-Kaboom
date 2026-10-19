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
import com.kaboom.jjk.ability.AbilityHandler;
import com.kaboom.jjk.ability.DomainManager;
import com.kaboom.jjk.ability.DomainType;
import com.kaboom.jjk.ability.JJKDamage;
import com.kaboom.jjk.block.ModBlocks;
import com.kaboom.jjk.config.JJKConfig;
import com.kaboom.jjk.data.ModTags;
import com.kaboom.jjk.entity.CurseEntity;
import com.kaboom.jjk.entity.CurseType;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.entity.ShikigamiEntity;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.item.CursedToolItem;
import com.kaboom.jjk.item.ModItems;
import com.kaboom.jjk.item.SukunaFingerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
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

    /** A survival mock player without the post-join spawn invulnerability, so damage tests mean something. */
    private static ServerPlayer vulnerablePlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ObfuscationReflectionHelper.setPrivateValue(ServerPlayer.class, player, 0, "spawnInvulnerableTime");
        return player;
    }

    // ------------------------------------------------------------------ Phase 2: items

    @GameTest(template = "empty")
    public static void sukunaFingerRaisesMaxCE(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CursedEnergyData data = ModAttachments.get(player);
        double before = data.getMaxCE();
        ItemStack finger = new ItemStack(ModItems.SUKUNA_FINGER.get());
        finger.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(data.getFingersEaten() == 1, "finger not counted");
        helper.assertTrue(data.getMaxCE() >= before + SukunaFingerItem.CE_PER_FINGER - 0.001, "max CE did not grow");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void talismanBlocksOneTechniqueHit(GameTestHelper helper) {
        ServerPlayer player = vulnerablePlayer(helper);
        player.getInventory().add(new ItemStack(ModItems.TALISMAN.get(), 1));
        // A mob attacker: the test server has PvP disabled.
        Zombie attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        float health = player.getHealth();
        player.hurt(JJKDamage.technique(attacker), 6f);
        helper.assertTrue(player.getHealth() == health, "talisman did not block the hit");
        helper.assertTrue(!player.getInventory().contains(new ItemStack(ModItems.TALISMAN.get())), "talisman not consumed");
        player.hurt(JJKDamage.technique(attacker), 6f);
        helper.assertTrue(player.getHealth() < health, "second hit should land");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void prisonRealmSealsTarget(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        ItemStack prison = new ItemStack(ModItems.PRISON_REALM.get());
        prison.getItem().interactLivingEntity(prison, player, zombie, InteractionHand.MAIN_HAND);
        helper.assertTrue(zombie.hasEffect(ModEffects.SEALED), "zombie not sealed");
        float health = zombie.getHealth();
        zombie.hurt(player.damageSources().playerAttack(player), 5f);
        helper.assertTrue(zombie.getHealth() == health, "sealed target took damage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void splitSoulKatanaIgnoresArmor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = new ItemStack(ModItems.SPLIT_SOUL_KATANA.get());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(10);
        float boosted = ((CursedToolItem) sword.getItem()).modifyDamage(sword, player, zombie, 10f);
        helper.assertTrue(boosted > 10f, "split soul katana should ignore armor");
        helper.succeed();
    }

    // ------------------------------------------------------------------ Phase 3: abilities, domains

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void domainBuildsAndRestoresTerrain(GameTestHelper helper) {
        JJKConfig.DOMAIN_SLOW_MOTION.set(false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CursedEnergyData data = ModAttachments.get(player);
        data.setTechnique(Technique.SHRINE, CETier.HIGH, CENature.STANDARD);
        data.setGrade(Grade.GRADE_1);
        BlockPos center = player.blockPosition();
        BlockPos shell = center.above(DomainManager.RADIUS);
        var before = helper.getLevel().getBlockState(shell);

        helper.assertTrue(DomainManager.expand(player, data, DomainType.MALEVOLENT_SHRINE), "domain failed to expand");
        helper.assertTrue(helper.getLevel().getBlockState(shell).is(ModBlocks.DOMAIN_BARRIER.get()), "no barrier at the top of the dome");
        helper.assertTrue(helper.getLevel().getBlockState(center.below()).is(ModBlocks.DOMAIN_FLOOR.get()), "no domain floor");

        for (DomainManager.ActiveDomain d : DomainManager.active()) {
            if (d.owner().equals(player.getUUID())) DomainManager.collapse(d, false);
        }
        helper.assertTrue(helper.getLevel().getBlockState(shell).equals(before), "terrain not restored at the shell");
        helper.assertTrue(!helper.getLevel().getBlockState(center.below()).is(ModBlocks.DOMAIN_FLOOR.get()), "floor not restored");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void infinityStopsAttacksButNotSureHit(GameTestHelper helper) {
        ServerPlayer player = vulnerablePlayer(helper);
        Zombie attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModEffects.INFINITY, 200));
        float health = player.getHealth();
        player.hurt(JJKDamage.technique(attacker), 6f);
        helper.assertTrue(player.getHealth() == health, "Infinity let a technique through");
        player.hurt(JJKDamage.sureHit(attacker), 4f);
        helper.assertTrue(player.getHealth() < health, "sure-hit must ignore Infinity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void projectileAbilitiesSpawnEntities(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CursedEnergyData data = ModAttachments.get(player);
        data.setTechnique(Technique.LIMITLESS, CETier.HIGH, CENature.STANDARD);
        data.setGrade(Grade.GRADE_1);
        data.setCurrentCE(data.getMaxCE());
        int before = helper.getLevel().getEntities(ModEntities.TECHNIQUE_PROJECTILE.get(), e -> true).size();
        helper.assertTrue(AbilityHandler.tryActivate(player, data, Abilities.get("red")), "Red failed to activate");
        int after = helper.getLevel().getEntities(ModEntities.TECHNIQUE_PROJECTILE.get(), e -> true).size();
        helper.assertTrue(after == before + 1, "Red did not spawn a projectile");
        helper.assertTrue(data.getCooldown("red") > 0, "cooldown not applied");
        helper.succeed();
    }

    // ------------------------------------------------------------------ Phase 4: curses and shikigami

    @GameTest(template = "empty")
    public static void everyCurseSpawnsWithItsStats(GameTestHelper helper) {
        for (CurseType t : CurseType.values()) {
            CurseEntity curse = helper.spawn(ModEntities.curse(t), new BlockPos(1, 1, 1));
            helper.assertTrue(curse.curseType() == t, "wrong curse type for " + t.id);
            helper.assertTrue(Math.abs(curse.getMaxHealth() - t.health) < 0.01, "wrong health for " + t.id);
            helper.assertTrue(curse.getType().is(ModTags.CURSES), t.id + " missing from the curses tag");
            curse.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void absorbAndReleaseCurse(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CursedEnergyData data = ModAttachments.get(player);
        data.setTechnique(Technique.CURSED_SPIRIT_MANIPULATION, CETier.HIGH, CENature.STANDARD);
        data.setGrade(Grade.GRADE_3);
        data.setCurrentCE(data.getMaxCE());
        CurseEntity fly = helper.spawn(ModEntities.curse(CurseType.FLY_HEAD), new BlockPos(1, 1, 1));
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, fly.getEyePosition());
        helper.assertTrue(AbilityHandler.tryActivate(player, data, Abilities.get("absorb_curse")), "absorb failed");
        helper.assertTrue(data.getAbsorbedCurses().contains("fly_head"), "curse not stored");
        helper.assertTrue(fly.isRemoved(), "absorbed curse still in the world");

        int before = helper.getLevel().getEntitiesOfClass(CurseEntity.class, player.getBoundingBox().inflate(16)).size();
        helper.assertTrue(AbilityHandler.tryActivate(player, data, Abilities.get("release_curse")), "release failed");
        var released = helper.getLevel().getEntitiesOfClass(CurseEntity.class, player.getBoundingBox().inflate(16));
        helper.assertTrue(released.size() == before + 1, "released curse did not appear");
        helper.assertTrue(released.stream().anyMatch(c -> player.getUUID().equals(c.getOwnerUUID())), "released curse has no owner");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tenShadowsSummonsShikigami(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CursedEnergyData data = ModAttachments.get(player);
        data.setTechnique(Technique.TEN_SHADOWS, CETier.HIGH, CENature.STANDARD);
        data.setCurrentCE(data.getMaxCE());
        helper.assertTrue(AbilityHandler.tryActivate(player, data, Abilities.get("divine_dogs")), "divine dogs failed");
        var dogs = helper.getLevel().getEntitiesOfClass(ShikigamiEntity.class, player.getBoundingBox().inflate(8));
        helper.assertTrue(dogs.size() == 2, "expected two divine dogs, got " + dogs.size());
        helper.assertTrue(dogs.stream().allMatch(d -> player.getUUID().equals(d.getOwnerUUID())), "dogs not bound to summoner");
        dogs.forEach(net.minecraft.world.entity.Entity::discard);
        helper.succeed();
    }
}
