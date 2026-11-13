package dev.soulbound.module.bots;

import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.explosion.ExplosionImpl;

/**
 * Crystal PvP bot: keeps a totem in the offhand, places end crystals on obsidian or bedrock
 * where they hurt the target most, breaks crystals that are worth it, builds obsidian next to
 * the target when there's nowhere to place, and eats golden apples when low.
 */
public final class CrystalBot extends BotModule {
    private final ModeSetting playstyle = add(new ModeSetting("Playstyle", "How aggressively it fights", "Balanced", "Terminator", "Balanced", "Safe"));
    private final ModeSetting targets = add(new ModeSetting("Targets", "What the bot fights", "Players", "Players", "Mobs", "All"));
    private final ModeSetting priority = add(new ModeSetting("Priority", "Which target to pick first", "Closest", "Closest", "Lowest Health", "Crosshair"));
    private final NumberSetting fightRange = add(new NumberSetting("Fight Range", "How far to look for targets", 12, 4, 24, 1, "m"));
    private final NumberSetting placeRange = add(new NumberSetting("Place Range", "Reach for placing crystals and obsidian", 4.5, 2, 6, 0.1, "m"));
    private final NumberSetting breakRange = add(new NumberSetting("Break Range", "Reach for breaking crystals", 4.5, 2, 6, 0.1, "m"));
    private final NumberSetting minDamage = add(new NumberSetting("Min Damage", "Least damage to the target worth a crystal", 5, 1, 20, 0.5, " HP"));
    private final NumberSetting maxSelf = add(new NumberSetting("Max Self Damage", "Most damage you're willing to take", 8, 0, 20, 0.5, " HP"));
    private final NumberSetting placeDelay = add(new NumberSetting("Place Delay", "Ticks between placements", 1, 0, 10, 1, "t"));
    private final NumberSetting breakDelay = add(new NumberSetting("Break Delay", "Ticks between breaks", 0, 0, 10, 1, "t"));
    private final NumberSetting turnSpeed = add(new NumberSetting("Turn Speed", "Max degrees turned per tick", 180, 10, 180, 5, "°"));
    private final BoolSetting buildObsidian = add(new BoolSetting("Build Obsidian", "Place obsidian next to the target when needed", true));
    private final BoolSetting autoTotem = add(new BoolSetting("Auto Totem", "Keep a totem in your offhand", true));
    private final BoolSetting eatGapples = add(new BoolSetting("Eat Gapples", "Eat golden apples when low", true));
    private final NumberSetting gappleHealth = add(new NumberSetting("Gapple Health", "Eat at or below this health", 10, 1, 20, 1, " HP").visibleWhen(eatGapples::isOn));
    private final BoolSetting chase = add(new BoolSetting("Chase", "Keep within crystal range of the target", true));
    private final BoolSetting antiSuicide = add(new BoolSetting("Anti Suicide", "Never take damage that would kill you", true));
    private final BoolSetting throughWalls = add(new BoolSetting("Through Walls", "Target entities you can't see", false));

    private int placeTimer, breakTimer, buildTimer, totemTimer;
    private boolean eating;
    private int slotBeforeEating = -1;

    public CrystalBot() {
        super("Crystal PvP Bot", "Fights crystal PvP for you, start to finish.", "CB-02", "crystal_bot");
        addHudSettings();
    }

    @Override public String getIcon() { return "icon_crystal"; }

    @Override public String[] statLabels() { return new String[]{"BREAKS", "COMBO", "CPS"}; }

    @Override
    protected void onDisable() {
        stopEating();
        super.onDisable();
    }

    @Override
    protected void tickBot(ClientPlayerEntity p) {
        placeTimer--;
        breakTimer--;
        buildTimer--;
        totemTimer--;

        if (autoTotem.isOn()) keepTotem(p);
        if (eatGapples.isOn() && handleEating(p)) {
            state = State.HEALING;
            return;
        }

        target = pickTarget(p, fightRange.get(), targets.get(), priority.get(), throughWalls.isOn());
        if (target == null) {
            release();
            state = State.SEARCHING;
            return;
        }

        double dist = p.distanceTo(target);
        double keep = switch (playstyle.get()) {
            case "Terminator" -> 3.5;
            case "Safe" -> 5.5;
            default -> 4.5;
        };
        state = dist > keep + 2 ? State.CHASING : State.ENGAGING;

        // 1. break the best crystal in reach
        if (breakTimer <= 0) {
            EndCrystalEntity crystal = bestCrystal(p);
            if (crystal != null && turnTo(p, crystal.getBoundingBox().getCenter(), turnSpeed.getFloat()) < 10) {
                mc.interactionManager.attackEntity(p, crystal);
                p.swingHand(Hand.MAIN_HAND);
                countHit();
                breakTimer = placeDelay.getInt() == 0 && breakDelay.getInt() == 0 ? 0 : breakDelay.getInt();
                walk(p, dist, keep);
                return;
            }
        }

        // 2. place a crystal where it hurts the target most
        int crystalSlot = hotbarSlot(p, Items.END_CRYSTAL);
        if (placeTimer <= 0 && crystalSlot >= 0) {
            BlockPos base = bestBase(p);
            if (base != null) {
                Vec3d face = Vec3d.ofCenter(base).add(0, 0.5, 0);
                if (turnTo(p, face, turnSpeed.getFloat()) < 10) {
                    p.getInventory().setSelectedSlot(crystalSlot);
                    mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(face, Direction.UP, base, false));
                    p.swingHand(Hand.MAIN_HAND);
                    placeTimer = placeDelay.getInt();
                }
                walk(p, dist, keep);
                return;
            }
            // 3. nowhere to place: build obsidian beside the target
            if (buildObsidian.isOn() && buildTimer <= 0 && build(p)) {
                state = State.BUILDING;
                walk(p, dist, keep);
                return;
            }
        }

        turnTo(p, target.getBoundingBox().getCenter(), turnSpeed.getFloat() * 0.5f);
        walk(p, dist, keep);
    }

    private void walk(ClientPlayerEntity p, double dist, double keep) {
        if (!chase.isOn()) {
            move(false, false, false, false, false, false);
            return;
        }
        boolean forward = dist > keep + 0.6, back = dist < keep - 1.2;
        move(forward, back, false, false, false, forward && dist > keep + 3);
    }

    // ------------------------------------------------------------------ crystals
    private EndCrystalEntity bestCrystal(ClientPlayerEntity p) {
        EndCrystalEntity best = null;
        float bestDmg = 0;
        double r = breakRange.get();
        Box area = p.getBoundingBox().expand(r + 1);
        for (Entity e : mc.world.getOtherEntities(p, area, en -> en instanceof EndCrystalEntity)) {
            if (p.getEyePos().distanceTo(e.getBoundingBox().getCenter()) > r) continue;
            Vec3d pos = new Vec3d(e.getX(), e.getY(), e.getZ());
            float dmg = crystalDamage(pos, target);
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
        for (BlockPos pos : BlockPos.iterate(center.add(-5, -3, -5), center.add(5, 2, 5))) {
            if (!canHoldCrystal(pos)) continue;
            Vec3d face = Vec3d.ofCenter(pos).add(0, 0.5, 0);
            if (p.getEyePos().distanceTo(face) > r) continue;
            Vec3d crystal = new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
            float dmg = crystalDamage(crystal, target);
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

    /** Damage to the target is high enough (or lethal) and the damage to us is acceptable. */
    private boolean worthIt(ClientPlayerEntity p, Vec3d pos, float dmg) {
        float lethal = target.getHealth() + target.getAbsorptionAmount();
        if (dmg < minDamage.getFloat() && dmg < lethal) return false;
        float self = crystalDamage(pos, p);
        float limit = maxSelf.getFloat() * (playstyle.is("Safe") ? 0.6f : 1f);
        if (self > limit && !(playstyle.is("Terminator") && dmg >= lethal && self < p.getHealth() + p.getAbsorptionAmount() - 2)) return false;
        return !(antiSuicide.isOn() && self >= p.getHealth() + p.getAbsorptionAmount() - 1);
    }

    /** Vanilla crystal explosion damage (power 6) after exposure, difficulty, armour and Resistance. */
    private float crystalDamage(Vec3d pos, LivingEntity e) {
        double dist = Math.sqrt(e.squaredDistanceTo(pos)) / 12.0;
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
        // Protection (1 per level) and Blast Protection (2 per level), capped at 20
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
                    float dmg = crystalDamage(crystal, target);
                    if (dmg > bestDmg && crystalDamage(crystal, p) < p.getHealth() + p.getAbsorptionAmount() - 1) {
                        bestDmg = dmg;
                        best = pos.toImmutable();
                    }
                }
            }
        }
        if (best == null) return false;
        BlockPos below = best.down();
        Vec3d face = Vec3d.ofCenter(below).add(0, 0.5, 0);
        if (turnTo(p, face, turnSpeed.getFloat()) >= 10) return true;
        int prev = p.getInventory().getSelectedSlot();
        p.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(face, Direction.UP, below, false));
        p.swingHand(Hand.MAIN_HAND);
        p.getInventory().setSelectedSlot(prev);
        buildTimer = 3;
        return true;
    }

    // ------------------------------------------------------------------ survival
    private void keepTotem(ClientPlayerEntity p) {
        if (totemTimer > 0 || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (!inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) continue;
            int handlerSlot = i < 9 ? 36 + i : i;
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, handlerSlot, 40, SlotActionType.SWAP, p);
            totemTimer = 5;
            return;
        }
    }

    /** Eats a golden apple while low; returns true while eating. */
    private boolean handleEating(ClientPlayerEntity p) {
        boolean low = p.getHealth() <= gappleHealth.get();
        int slot = gappleSlot(p);
        if (!low || slot < 0) {
            if (eating) stopEating();
            return false;
        }
        if (!eating) {
            slotBeforeEating = p.getInventory().getSelectedSlot();
            eating = true;
        }
        p.getInventory().setSelectedSlot(slot);
        move(false, true, false, false, false, false);
        mc.options.useKey.setPressed(true);
        return true;
    }

    private static int gappleSlot(ClientPlayerEntity p) {
        for (Item it : new Item[]{Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE}) {
            int s = hotbarSlot(p, it);
            if (s >= 0) return s;
        }
        return -1;
    }

    private void stopEating() {
        if (!eating) return;
        eating = false;
        mc.options.useKey.setPressed(false);
        if (mc.player != null && slotBeforeEating >= 0) mc.player.getInventory().setSelectedSlot(slotBeforeEating);
        slotBeforeEating = -1;
    }
}
