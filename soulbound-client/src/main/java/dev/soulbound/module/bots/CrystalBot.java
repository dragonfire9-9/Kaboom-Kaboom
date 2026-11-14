package dev.soulbound.module.bots;

import dev.soulbound.aim.AimSettings;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * Crystal PvP bot: keeps a totem in the offhand, places end crystals on obsidian or bedrock
 * where they hurt the target most, breaks crystals worth breaking, builds obsidian when needed,
 * face-places low targets and eats golden apples when low. All aiming is smooth, per frame.
 */
public final class CrystalBot extends BotModule {
    private final ModeSetting targets, priority, swapMode;
    private final NumberSetting fightRange, fightDistance, placeRange, breakRange, minDamage, maxSelf, placeDelay, breakDelay,
            facePlaceHealth, armorBreaker, gappleHealth, maxAimError;
    private final BoolSetting throughWalls, buildObsidian, facePlace, predict, antiWeakness, inhibit, autoTotem,
            eatGapples, chase, antiSuicide, avoidEdges;

    private int placeTimer, breakTimer, buildTimer;
    private final Map<Integer, Integer> recentlyHit = new HashMap<>();

    public CrystalBot() {
        super("Crystal PvP Bot", "Fights crystal PvP for you, start to finish.", "CB-02", "crystal_bot");
        addStyle("Trained");
        add(new HeaderSetting("TARGETING"));
        targets = add(new ModeSetting("Targets", "What the bot fights", "Players", "Players", "Mobs", "All"));
        priority = add(new ModeSetting("Priority", "Which target to pick first", "Closest", "Closest", "Lowest Health", "Crosshair"));
        fightRange = add(new NumberSetting("Fight Range", "How far to look for targets", 12, 4, 24, 1, "m"));
        throughWalls = add(new BoolSetting("Through Walls", "Target entities you can't see", false));

        aim = new AimSettings(this, 900, 0.3);
        maxAimError = add(new NumberSetting("Max Aim Error", "Only place or break when aimed within this", 8, 2, 30, 1, "°"));

        add(new HeaderSetting("PLACING"));
        placeRange = add(new NumberSetting("Place Range", "Reach for placing crystals and obsidian", 4.5, 2, 6, 0.1, "m"));
        placeDelay = add(new NumberSetting("Place Delay", "Ticks between placements", 1, 0, 10, 1, "t"));
        minDamage = add(new NumberSetting("Min Damage", "Least damage to the target worth a crystal", 5, 1, 20, 0.5, " HP"));
        facePlace = add(new BoolSetting("Face Place", "Place at head height on weak targets", true));
        facePlaceHealth = add(new NumberSetting("Face Place Health", "Face place at or below this health", 8, 1, 36, 1, " HP").visibleWhen(facePlace::isOn));
        armorBreaker = add(new NumberSetting("Armor Breaker", "Also face place when any armour piece is below this", 15, 0, 100, 5, "%"));
        predict = add(new BoolSetting("Predict", "Account for where the target is moving", true));
        buildObsidian = add(new BoolSetting("Build Obsidian", "Place obsidian next to the target when needed", true));
        swapMode = add(new ModeSetting("Swap Mode", "How to switch to crystals", "Normal", "Normal", "Swap Back"));

        add(new HeaderSetting("BREAKING"));
        breakRange = add(new NumberSetting("Break Range", "Reach for breaking crystals", 4.5, 2, 6, 0.1, "m"));
        breakDelay = add(new NumberSetting("Break Delay", "Ticks between breaks", 0, 0, 10, 1, "t"));
        antiWeakness = add(new BoolSetting("Anti Weakness", "Swap to a weapon when you have Weakness", true));
        inhibit = add(new BoolSetting("Inhibit", "Don't hit the same crystal again for a few ticks", true));

        add(new HeaderSetting("SAFETY"));
        maxSelf = add(new NumberSetting("Max Self Damage", "Most damage you're willing to take", 8, 0, 20, 0.5, " HP"));
        antiSuicide = add(new BoolSetting("Anti Suicide", "Never take damage that would kill you", true));
        autoTotem = add(new BoolSetting("Auto Totem", "Keep a totem in your offhand", true));
        eatGapples = add(new BoolSetting("Eat Gapples", "Eat golden apples when low", true));
        gappleHealth = add(new NumberSetting("Gapple Health", "Eat at or below this health", 10, 1, 20, 1, " HP").visibleWhen(eatGapples::isOn));

        add(new HeaderSetting("MOVEMENT"));
        chase = add(new BoolSetting("Chase", "Keep within crystal range of the target", true));
        fightDistance = add(new NumberSetting("Fight Distance", "Distance to hold from the target", 4.5, 2.5, 6, 0.1, "m"));
        avoidEdges = add(new BoolSetting("Avoid Edges", "Never walk off drops or into lava", true));
        addHudSettings();
    }

    @Override public String getIcon() { return "icon_crystal"; }

    @Override public String[] statLabels() { return new String[]{"BREAKS", "COMBO", "CPS"}; }

    @Override
    protected void tickBot(ClientPlayerEntity p) {
        placeTimer--;
        breakTimer--;
        buildTimer--;
        recentlyHit.replaceAll((k, v) -> v - 1);
        recentlyHit.values().removeIf(v -> v <= 0);

        if (autoTotem.isOn()) keepTotem(p);
        if (eatGapples.isOn() && handleEating(p, gappleHealth.get())) {
            state = State.HEALING;
            move(false, true, false, false, false, false, avoidEdges.isOn());
            return;
        }
        target = pickTarget(p, fightRange.get(), targets.get(), priority.get(), throughWalls.isOn(), 360, true, false);
        if (target == null) {
            release();
            state = State.SEARCHING;
            return;
        }
        double dist = p.distanceTo(target);
        state = dist > fightDistance.get() + 2 ? State.CHASING : State.ENGAGING;
        if (untouchable() && p.getHealth() + p.getAbsorptionAmount() <= 8) state = State.RETREATING;

        // 1. break the best crystal in reach
        EndCrystalEntity crystal = breakTimer <= 0 ? bestCrystal(p) : null;
        if (crystal != null) {
            if (lookAt(crystal.getBoundingBox().getCenter()) < maxAimError.getFloat()) {
                int restore = antiWeakness(p);
                mc.interactionManager.attackEntity(p, crystal);
                p.swingHand(Hand.MAIN_HAND);
                if (restore >= 0) p.getInventory().setSelectedSlot(restore);
                if (inhibit.isOn()) recentlyHit.put(crystal.getId(), 3);
                countHit();
                breakTimer = breakDelay.getInt();
            }
            walk(p, dist);
            return;
        }

        // 2. place a crystal where it hurts the target most
        int crystalSlot = hotbarSlot(p, Items.END_CRYSTAL);
        if (crystalSlot >= 0) {
            BlockPos base = bestBase(p);
            if (base != null) {
                Vec3d face = Vec3d.ofCenter(base).add(0, 0.5, 0);
                if (placeTimer <= 0 && lookAt(face) < maxAimError.getFloat()) {
                    int prev = p.getInventory().getSelectedSlot();
                    p.getInventory().setSelectedSlot(crystalSlot);
                    mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(face, Direction.UP, base, false));
                    p.swingHand(Hand.MAIN_HAND);
                    if (swapMode.is("Swap Back")) p.getInventory().setSelectedSlot(prev);
                    placeTimer = placeDelay.getInt();
                } else {
                    lookAt(face);
                }
                walk(p, dist);
                return;
            }
            // 3. nowhere to place: build obsidian beside the target
            if (buildObsidian.isOn() && buildTimer <= 0 && build(p)) {
                state = State.BUILDING;
                walk(p, dist);
                return;
            }
        }
        lookAt(target.getBoundingBox().getCenter());
        walk(p, dist);
    }

    private void walk(ClientPlayerEntity p, double dist) {
        if (!chase.isOn()) {
            move(false, false, false, false, false, false);
            return;
        }
        double keep = aggressive() ? Math.min(fightDistance.get(), 3.5) : untouchable() ? Math.max(fightDistance.get(), 5.5) : fightDistance.get();
        boolean forward = dist > keep + 0.6, back = dist < keep - 1.2;
        move(forward, back, false, false, false, dist > keep + 3, avoidEdges.isOn());
    }

    /** With Weakness and no Strength, crystals need a weapon: swap to one and return the slot to go back to. */
    private int antiWeakness(ClientPlayerEntity p) {
        if (!antiWeakness.isOn() || !p.hasStatusEffect(StatusEffects.WEAKNESS) || p.hasStatusEffect(StatusEffects.STRENGTH)) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES)) {
                int prev = p.getInventory().getSelectedSlot();
                p.getInventory().setSelectedSlot(i);
                return prev;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ crystals
    private boolean facePlacing() {
        if (!facePlace.isOn()) return false;
        if (target.getHealth() + target.getAbsorptionAmount() <= facePlaceHealth.get()) return true;
        if (armorBreaker.get() <= 0) return false;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = target.getEquippedStack(slot);
            if (st.isDamageable() && 100.0 * (st.getMaxDamage() - st.getDamage()) / st.getMaxDamage() <= armorBreaker.get()) return true;
        }
        return false;
    }

    private Vec3d offset() {
        if (!predict.isOn()) return Vec3d.ZERO;
        Vec3d v = target.getVelocity();
        return new Vec3d(v.x * 2, 0, v.z * 2);
    }

    private EndCrystalEntity bestCrystal(ClientPlayerEntity p) {
        EndCrystalEntity best = null;
        float bestDmg = 0;
        double r = breakRange.get();
        Vec3d off = offset();
        for (Entity e : mc.world.getOtherEntities(p, p.getBoundingBox().expand(r + 1), en -> en instanceof EndCrystalEntity)) {
            if (recentlyHit.containsKey(e.getId())) continue;
            if (p.getEyePos().distanceTo(e.getBoundingBox().getCenter()) > r) continue;
            Vec3d pos = new Vec3d(e.getX(), e.getY(), e.getZ());
            float dmg = crystalDamage(pos, target, off);
            if (!worthIt(p, pos, dmg)) continue;
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = (EndCrystalEntity) e;
            }
        }
        return best;
    }

    private BlockPos bestBase(ClientPlayerEntity p) {
        BlockPos center = target.getBlockPos();
        BlockPos best = null;
        float bestDmg = 0;
        double r = placeRange.get();
        Vec3d off = offset();
        for (BlockPos pos : BlockPos.iterate(center.add(-5, -3, -5), center.add(5, 2, 5))) {
            if (!canHoldCrystal(pos)) continue;
            Vec3d face = Vec3d.ofCenter(pos).add(0, 0.5, 0);
            if (p.getEyePos().distanceTo(face) > r) continue;
            Vec3d crystal = new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            float dmg = crystalDamage(crystal, target, off);
            if (!worthIt(p, crystal, dmg)) continue;
            if (dmg > bestDmg) {
                bestDmg = dmg;
                best = pos.toImmutable();
            }
        }
        return best;
    }

    private boolean canHoldCrystal(BlockPos pos) {
        BlockState base = mc.world.getBlockState(pos);
        if (!base.isOf(Blocks.OBSIDIAN) && !base.isOf(Blocks.BEDROCK)) return false;
        BlockPos up = pos.up();
        if (!mc.world.getBlockState(up).isAir()) return false;
        Box box = new Box(up.getX(), up.getY(), up.getZ(), up.getX() + 1, up.getY() + 2, up.getZ() + 1);
        return mc.world.getOtherEntities(null, box).isEmpty();
    }

    private boolean worthIt(ClientPlayerEntity p, Vec3d pos, float dmg) {
        float lethal = target.getHealth() + target.getAbsorptionAmount();
        float min = facePlacing() ? 1.5f : minDamage.getFloat();
        if (dmg < min && dmg < lethal) return false;
        float self = crystalDamage(pos, p, Vec3d.ZERO);
        float limit = maxSelf.getFloat() * (untouchable() ? 0.6f : 1f);
        if (self > limit && !(aggressive() && dmg >= lethal && self < p.getHealth() + p.getAbsorptionAmount() - 2)) return false;
        return !(antiSuicide.isOn() && self >= p.getHealth() + p.getAbsorptionAmount() - 1);
    }

    /** Vanilla crystal damage (power 6): exposure, difficulty, armour, Protection/Blast Protection and Resistance. */
    private float crystalDamage(Vec3d pos, LivingEntity e, Vec3d entityOffset) {
        double dist = Math.sqrt(e.squaredDistanceTo(pos.subtract(entityOffset))) / 12.0;
        if (dist > 1) return 0;
        double impact = (1 - dist) * ExplosionImpl.calculateReceivedDamage(pos, e);
        float dmg = (float) ((impact * impact + impact) / 2 * 7 * 12 + 1);
        if (e instanceof PlayerEntity) {
            Difficulty d = mc.world.getDifficulty();
            if (d == Difficulty.PEACEFUL) dmg = 0;
            else if (d == Difficulty.EASY) dmg = Math.min(dmg / 2 + 1, dmg);
            else if (d == Difficulty.HARD) dmg *= 1.5f;
        }
        float armor = e.getArmor();
        float tough = (float) e.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS);
        float g = MathHelper.clamp(armor - dmg / (2 + tough / 4), armor * 0.2f, 20);
        dmg *= 1 - g / 25;
        dmg *= 1 - Math.min(20, protectionPoints(e)) / 25f;
        StatusEffectInstance res = e.getStatusEffect(StatusEffects.RESISTANCE);
        if (res != null) dmg *= Math.max(0, 1 - (res.getAmplifier() + 1) * 0.2f);
        return Math.max(0, dmg);
    }

    private int protectionPoints(LivingEntity e) {
        var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var prot = registry.getOptional(Enchantments.PROTECTION);
        var blast = registry.getOptional(Enchantments.BLAST_PROTECTION);
        int points = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = e.getEquippedStack(slot);
            if (st.isEmpty()) continue;
            if (prot.isPresent()) points += EnchantmentHelper.getLevel(prot.get(), st);
            if (blast.isPresent()) points += 2 * EnchantmentHelper.getLevel(blast.get(), st);
        }
        return points;
    }

    // ------------------------------------------------------------------ obsidian
    private boolean build(ClientPlayerEntity p) {
        int slot = hotbarSlot(p, Items.OBSIDIAN);
        if (slot < 0) return false;
        BlockPos feet = target.getBlockPos();
        BlockPos best = null;
        float bestDmg = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int dy = -1; dy <= 0; dy++) {
                    BlockPos pos = feet.add(dx, dy, dz);
                    if (!mc.world.getBlockState(pos).isReplaceable()) continue;
                    BlockPos below = pos.down();
                    if (!mc.world.getBlockState(below).isSolidBlock(mc.world, below)) continue;
                    if (!mc.world.getOtherEntities(null, new Box(pos)).isEmpty()) continue;
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    Vec3d face = Vec3d.ofCenter(below).add(0, 0.5, 0);
                    if (p.getEyePos().distanceTo(face) > placeRange.get()) continue;
                    Vec3d crystal = new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    float dmg = crystalDamage(crystal, target, Vec3d.ZERO);
                    if (dmg > bestDmg && crystalDamage(crystal, p, Vec3d.ZERO) < p.getHealth() + p.getAbsorptionAmount() - 1) {
                        bestDmg = dmg;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        if (best == null) return false;
        BlockPos below = best.down();
        Vec3d face = Vec3d.ofCenter(below).add(0, 0.5, 0);
        if (lookAt(face) >= maxAimError.getFloat()) return true;
        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(face, Direction.UP, below, false));
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(prev);
        buildTimer = 3;
        return true;
    }
}
