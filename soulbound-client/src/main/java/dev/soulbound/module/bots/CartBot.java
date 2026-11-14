package dev.soulbound.module.bots;

import dev.soulbound.aim.AimSettings;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.ExplosionImpl;

import java.util.List;

/**
 * Cart PvP bot: lays a rail under (or just ahead of) the target, stacks TNT minecarts on it, backs
 * off to a safe distance and sets them off with a Flame bow, which detonates a cart the moment the
 * burning arrow hits. Between combos it fights with the sword, and it keeps a totem and eats
 * golden apples like the other bots.
 */
public final class CartBot extends BotModule {
    private enum Phase { APPROACH, RAIL, CARTS, IGNITE, RECOVER }

    private final ModeSetting targets, priority, railType, placement;
    private final NumberSetting minRailDistance, searchRange, placeRange, cartsPerRail, safeDistance, bowDraw, maxArrowError, comboDelay,
            maxSelf, gappleHealth, placeDelay;
    private final BoolSetting throughWalls, predictMove, swordFallback, antiSuicide, autoTotem, eatGapples, avoidEdges, retreatAfter;

    private Phase phase = Phase.APPROACH;
    private BlockPos rail;
    private final java.util.List<BlockPos> rails = new java.util.ArrayList<>();
    private int cartsPlaced, phaseTicks, cooldown, placeTimer;
    private boolean drawing;

    public CartBot() {
        super("Cart PvP Bot", "Rails, TNT minecarts and a Flame bow: cart PvP on autopilot.", "TB-05", "cart_bot");
        addStyle("Trained");
        add(new HeaderSetting("TARGETING"));
        targets = add(new ModeSetting("Targets", "What the bot fights", "Players", "Players", "Mobs", "All"));
        priority = add(new ModeSetting("Priority", "Which target to pick first", "Closest", "Closest", "Lowest Health", "Crosshair"));
        searchRange = add(new NumberSetting("Search Range", "How far to look for targets", 16, 4, 32, 1, "m"));
        throughWalls = add(new BoolSetting("Through Walls", "Target entities you can't see", false));

        aim = new AimSettings(this, 820, 0.35, 50);

        add(new HeaderSetting("CARTS"));
        placement = add(new ModeSetting("Rail Spot", "Where to lay the rail", "Feet", "Feet", "Ahead", "Behind"));
        predictMove = add(new BoolSetting("Predict", "Lay the rail where the target is heading", true));
        railType = add(new ModeSetting("Rail", "Which rail to use", "Any", "Any", "Rail", "Powered", "Detector", "Activator"));
        cartsPerRail = add(new NumberSetting("Carts Per Combo", "Rails (one TNT minecart each) laid side by side before lighting", 2, 1, 4, 1));
        placeRange = add(new NumberSetting("Place Range", "Reach for rails and carts", 4.5, 2, 5, 0.1, "m"));
        placeDelay = add(new NumberSetting("Place Delay", "Ticks between placing each piece", 1, 0, 6, 1, "t"));
        minRailDistance = add(new NumberSetting("Min Rail Distance", "Never lay a combo closer to you than this (carts can go off as they land)", 3.5, 1, 4.5, 0.1, "m"));

        add(new HeaderSetting("IGNITION"));
        bowDraw = add(new NumberSetting("Bow Draw", "Ticks to draw the Flame bow (longer = flatter, faster arrow and a bigger blast)", 8, 3, 20, 1, "t"));
        maxArrowError = add(new NumberSetting("Max Aim Error", "Only release when aimed this close to the cart", 4, 1, 15, 0.5, "°"));
        safeDistance = add(new NumberSetting("Safe Distance", "Only light carts at least this far away", 3.4, 2.5, 8, 0.1, "m"));
        comboDelay = add(new NumberSetting("Combo Delay", "Ticks to wait between combos", 6, 0, 40, 1, "t"));

        add(new HeaderSetting("SAFETY"));
        maxSelf = add(new NumberSetting("Max Self Damage", "Don't light carts that would hit you harder than this", 10, 0, 20, 0.5, " HP"));
        antiSuicide = add(new BoolSetting("Anti Suicide", "Never light a combo that could kill you", true));
        retreatAfter = add(new BoolSetting("Retreat After", "Keep backing off while the blast goes off", true));
        autoTotem = add(new BoolSetting("Auto Totem", "Keep a totem in your offhand", true));
        eatGapples = add(new BoolSetting("Eat Gapples", "Eat golden apples when low", true));
        gappleHealth = add(new NumberSetting("Gapple Health", "Eat at or below this health", 10, 1, 20, 1, " HP").visibleWhen(eatGapples::isOn));

        add(new HeaderSetting("MOVEMENT"));
        swordFallback = add(new BoolSetting("Sword Fallback", "Fight with the sword when out of carts or too close", true));
        avoidEdges = add(new BoolSetting("Avoid Edges", "Never walk off drops or into lava", true));
        addHudSettings();
    }

    @Override public String getIcon() { return "icon_cart"; }
    @Override public String[] statLabels() { return new String[]{"COMBOS", "COMBO", "CPS"}; }

    @Override
    protected void onEnable() {
        super.onEnable();
        reset();
    }

    @Override
    protected void onDisable() {
        stopDrawing();
        super.onDisable();
    }

    private void reset() {
        phase = Phase.APPROACH;
        rail = null;
        rails.clear();
        cartsPlaced = 0;
        phaseTicks = 0;
    }

    // playstyle: Aggressive lights closer and chains faster; Untouchable keeps more room and risks less
    private double minRail() { return minRailDistance.get() * (aggressive() ? 0.85 : untouchable() ? 1.15 : 1); }

    private double safeDist() { return safeDistance.get() + (aggressive() ? -0.4 : untouchable() ? 1.0 : 0); }

    private float selfLimit() { return maxSelf.getFloat() * (aggressive() ? 1.3f : untouchable() ? 0.6f : 1f); }

    private int comboWait() { return (int) (comboDelay.getInt() * (aggressive() ? 0.5 : untouchable() ? 1.5 : 1)); }

    private void go(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    @Override
    protected void tickBot(ClientPlayerEntity p) {
        if (cooldown > 0) cooldown--;
        if (placeTimer > 0) placeTimer--;
        phaseTicks++;
        if (autoTotem.isOn()) keepTotem(p);
        if (eatGapples.isOn() && phase != Phase.IGNITE && handleEating(p, gappleHealth.get())) {
            stopDrawing();
            state = State.HEALING;
            if (target != null) moveDir(p.getEntityPos().subtract(target.getEntityPos()), false, false, avoidEdges.isOn());
            else move(false, false, false, false, false, false);
            return;
        }
        target = pickTarget(p, searchRange.get(), targets.get(), priority.get(), throughWalls.isOn(), 360, true, true);
        if (target == null) {
            stopDrawing();
            release();
            state = State.SEARCHING;
            reset();
            return;
        }
        double dist = p.distanceTo(target);
        int bowSlot = flameBow(p);
        int railSlot = railSlot(p);
        int cartSlot = toHotbar(p, st -> st.isOf(Items.TNT_MINECART), 6);
        boolean armed = railSlot >= 0 && cartSlot >= 0 && bowSlot >= 0 && hasArrows(p);

        switch (phase) {
            case APPROACH -> {
                state = dist > placeRange.get() + 1 ? State.CHASING : State.ENGAGING;
                if (!armed || cooldown > 0) {
                    melee(p, dist);
                    return;
                }
                BlockPos spot = railSpot(p);
                if (spot != null) {
                    rail = spot;
                    rails.clear();
                    rails.add(spot);
                    // extra carts go on neighbouring rails: two carts on one rail shove each other and go off early
                    for (Direction d : Direction.Type.HORIZONTAL) {
                        if (rails.size() >= cartsPerRail.getInt()) break;
                        BlockPos n = spot.offset(d);
                        if (validRail(p, n)) rails.add(n);
                    }
                    cartsPlaced = 0;
                    go(Phase.RAIL);
                    return;
                }
                if (dist < minRail() + 0.5) {
                    melee(p, dist); // too close for carts: sword them back first
                    return;
                }
                lookAt(target.getBoundingBox().getCenter());
                moveDir(target.getEntityPos().subtract(p.getEntityPos()), false, dist > 6, avoidEdges.isOn());
            }
            case RAIL -> {
                state = State.BUILDING;
                BlockPos next = null;
                for (BlockPos b : rails) if (!mc.world.getBlockState(b).isIn(BlockTags.RAILS)) { next = b; break; }
                if (next == null) {
                    go(Phase.CARTS);
                    return;
                }
                if (phaseTicks > 20 || !mc.world.getBlockState(next).isReplaceable()) {
                    rails.remove(next);
                    if (rails.isEmpty()) reset();
                    return;
                }
                BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(next.down()).add(0, 0.5, 0), Direction.UP, next.down(), false);
                if (lookAt(hit.getPos()) < 10 && placeTimer <= 0 && railSlot >= 0) {
                    p.getInventory().setSelectedSlot(railSlot);
                    mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                    p.swingHand(Hand.MAIN_HAND);
                    placeTimer = placeDelay.getInt();
                }
                move(false, false, false, false, false, false);
            }
            case CARTS -> {
                state = State.BUILDING;
                rails.removeIf(b -> !mc.world.getBlockState(b).isIn(BlockTags.RAILS));
                if (rails.isEmpty() || phaseTicks > 40) {
                    if (cartsPlaced > 0) go(Phase.IGNITE); else reset();
                    return;
                }
                if (cartsPlaced >= rails.size() || cartSlot < 0) {
                    go(Phase.IGNITE);
                    return;
                }
                BlockPos at = rails.get(cartsPlaced);
                Vec3d railTop = Vec3d.ofBottomCenter(at).add(0, 0.1, 0);
                BlockHitResult hit = new BlockHitResult(railTop, Direction.UP, at, false);
                if (lookAt(railTop) < 10 && placeTimer <= 0 && p.getEyePos().distanceTo(railTop) <= placeRange.get()) {
                    p.getInventory().setSelectedSlot(cartSlot);
                    mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                    p.swingHand(Hand.MAIN_HAND);
                    cartsPlaced++;
                    placeTimer = placeDelay.getInt();
                }
                move(false, false, false, false, false, false);
            }
            case IGNITE -> {
                state = State.ENGAGING;
                TntMinecartEntity cart = nearestCart(p);
                if (cart == null || bowSlot < 0 || phaseTicks > 80) {
                    stopDrawing();
                    cooldown = comboWait();
                    reset();
                    return;
                }
                Vec3d c = cart.getBoundingBox().getCenter();
                double cartDist = p.getEyePos().distanceTo(c);
                float self = selfDamage(p, c);
                boolean safe = cartDist >= safeDist() && self <= selfLimit()
                        && !(antiSuicide.isOn() && self >= p.getHealth() + p.getAbsorptionAmount() - 1);
                if (Boolean.getBoolean("soulbound.debugBuilder") && phaseTicks % 5 == 0) dev.soulbound.SoulBound.LOGGER.info("[cart] ignite cartDist={} self={} safe={} using={} use={} slot={} bow={}",
                        String.format("%.2f", cartDist), self, safe, p.isUsingItem(), p.getItemUseTime(), p.getInventory().getSelectedSlot(), bowSlot);
                if (!safe && phaseTicks > 25) {
                    // can't light it safely (too close, or no blast protection): drop the combo
                    stopDrawing();
                    cooldown = comboWait() + 20;
                    reset();
                    return;
                }
                // back away to a safe distance with the bow drawn, then fire
                Vec3d away = p.getEntityPos().subtract(c);
                moveDir(safe ? Vec3d.ZERO : away, false, false, avoidEdges.isOn());
                p.getInventory().setSelectedSlot(bowSlot);
                float err = lookAt(ballistic(p.getEyePos(), c, arrowSpeed(bowDraw.getInt())));
                mc.options.useKey.setPressed(true);
                drawing = true;
                if (safe && p.isUsingItem() && p.getItemUseTime() >= bowDraw.getInt() && err < maxArrowError.getFloat()) {
                    if (Boolean.getBoolean("soulbound.debugBuilder")) dev.soulbound.SoulBound.LOGGER.info("[cart] fire cartDist={} self={} carts={} vel={} draw={}",
                            cartDist, self, mc.world.getOtherEntities(p, new Box(rail).expand(3), en -> en instanceof TntMinecartEntity).size(), cart.getVelocity(), p.getItemUseTime());
                    stopDrawing(); // releasing fires the burning arrow into the cart
                    countHit();
                    cooldown = comboWait();
                    go(Phase.RECOVER);
                }
            }
            case RECOVER -> {
                state = State.RETREATING;
                if (retreatAfter.isOn() && phaseTicks < 10) {
                    lookAt(target.getBoundingBox().getCenter());
                    moveDir(p.getEntityPos().subtract(target.getEntityPos()), false, false, avoidEdges.isOn());
                } else {
                    reset();
                }
            }
        }
    }

    /** Sword fight while reloading, or with nothing to cart with. */
    private void melee(ClientPlayerEntity p, double dist) {
        stopDrawing();
        if (!swordFallback.isOn()) {
            lookAt(target.getBoundingBox().getCenter());
            move(false, false, false, false, false, false);
            return;
        }
        int sword = -1;
        for (int i = 0; i < 9 && sword < 0; i++) {
            ItemStack st = p.getInventory().getStack(i);
            if (st.isIn(ItemTags.SWORDS) || st.isIn(ItemTags.AXES)) sword = i;
        }
        if (sword >= 0) p.getInventory().setSelectedSlot(sword);
        float err = lookAt(aimPoint(p, target, "Closest").add(predicted(target, 2)));
        double r = reach(p, target);
        if (sword >= 0 && r <= 2.95 && err < 18 && p.getAttackCooldownProgress(0.5f) >= 0.92f && target.hurtTime <= 1) {
            mc.interactionManager.attackEntity(p, target);
            p.swingHand(Hand.MAIN_HAND);
        }
        Vec3d to = target.getEntityPos().subtract(p.getEntityPos());
        double f = r > 2.9 ? 1 : r < 2.2 ? -1 : 0;
        Vec3d side = new Vec3d(-to.z, 0, to.x).normalize().multiply((p.age / 15) % 2 == 0 ? 0.7 : -0.7);
        moveDir(to.normalize().multiply(f).add(side), false, f > 0, avoidEdges.isOn());
    }

    private void stopDrawing() {
        if (!drawing) return;
        drawing = false;
        mc.options.useKey.setPressed(false);
    }

    // ------------------------------------------------------------------ choosing the spot
    private BlockPos railSpot(ClientPlayerEntity p) {
        Vec3d at = target.getEntityPos();
        Vec3d vel = predicted(target, 4);
        if (predictMove.isOn()) at = at.add(vel);
        if (placement.is("Ahead") && vel.horizontalLengthSquared() > 1e-4) at = at.add(vel.normalize());
        if (placement.is("Behind") && vel.horizontalLengthSquared() > 1e-4) at = at.subtract(vel.normalize());
        BlockPos base = BlockPos.ofFloored(at);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : List.of(base, base.north(), base.south(), base.east(), base.west())) {
            for (int dy = 0; dy >= -1; dy--) {
                BlockPos r = pos.up(dy);
                if (!validRail(p, r)) continue;
                double d = r.getSquaredDistance(target.getEntityPos());
                if (d < bestD) {
                    bestD = d;
                    best = r.toImmutable();
                }
            }
        }
        return best;
    }

    private boolean validRail(ClientPlayerEntity p, BlockPos r) {
        BlockState here = mc.world.getBlockState(r);
        if (!here.isReplaceable() || !mc.world.getBlockState(r.down()).isSideSolidFullSquare(mc.world, r.down(), Direction.UP)) return false;
        Vec3d face = Vec3d.ofCenter(r.down()).add(0, 0.5, 0);
        if (p.getEyePos().distanceTo(face) > placeRange.get()) return false;
        if (p.getBoundingBox().intersects(new Box(r))) return false; // never under our own feet
        return Vec3d.ofBottomCenter(r).distanceTo(p.getEntityPos()) >= minRail(); // too close to light safely
    }

    // ------------------------------------------------------------------ inventory
    private int railSlot(ClientPlayerEntity p) {
        Item[] order = switch (railType.get()) {
            case "Rail" -> new Item[]{Items.RAIL};
            case "Powered" -> new Item[]{Items.POWERED_RAIL};
            case "Detector" -> new Item[]{Items.DETECTOR_RAIL};
            case "Activator" -> new Item[]{Items.ACTIVATOR_RAIL};
            default -> new Item[]{Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL};
        };
        for (Item it : order) {
            int s = toHotbar(p, st -> st.isOf(it), 7);
            if (s >= 0) return s;
        }
        return -1;
    }

    /** A bow with Flame (or any bow while the cart is already burning would do, but Flame is the reliable way). */
    private int flameBow(ClientPlayerEntity p) {
        var flame = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOptional(Enchantments.FLAME);
        int slot = toHotbar(p, st -> st.isOf(Items.BOW) && flame.isPresent() && EnchantmentHelper.getLevel(flame.get(), st) > 0, 8);
        // a plain bow still sets carts off while we're on fire (the arrow catches), but don't rely on it
        if (slot < 0 && p.isOnFire()) slot = toHotbar(p, st -> st.isOf(Items.BOW), 8);
        return slot;
    }

    private static boolean hasArrows(ClientPlayerEntity p) {
        return p.isCreative() || p.getInventory().contains(s -> s.isOf(Items.ARROW) || s.isOf(Items.SPECTRAL_ARROW) || s.isOf(Items.TIPPED_ARROW));
    }

    private TntMinecartEntity nearestCart(ClientPlayerEntity p) {
        if (rail == null) return null;
        TntMinecartEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : mc.world.getOtherEntities(p, new Box(rail).expand(2.5), en -> en instanceof TntMinecartEntity)) {
            double d = e.squaredDistanceTo(Vec3d.ofCenter(rail));
            if (d < bestD) {
                bestD = d;
                best = (TntMinecartEntity) e;
            }
        }
        return best;
    }

    private int blastPoints(ClientPlayerEntity p) {
        var registry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        var prot = registry.getOptional(Enchantments.PROTECTION);
        var blast = registry.getOptional(Enchantments.BLAST_PROTECTION);
        int points = 0;
        for (var slot : new net.minecraft.entity.EquipmentSlot[]{net.minecraft.entity.EquipmentSlot.HEAD, net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS, net.minecraft.entity.EquipmentSlot.FEET}) {
            ItemStack st = p.getEquippedStack(slot);
            if (prot.isPresent()) points += EnchantmentHelper.getLevel(prot.get(), st);
            if (blast.isPresent()) points += 2 * EnchantmentHelper.getLevel(blast.get(), st);
        }
        return points;
    }

    /** Rough damage one cart's blast (power ~4, more with a strong arrow) would do to us, before armour. */
    private float selfDamage(ClientPlayerEntity p, Vec3d at) {
        double power = 4 + 1.5 * Math.min(5, arrowSpeed(bowDraw.getInt()));
        double radius = power * 2;
        double dist = Math.sqrt(p.squaredDistanceTo(at)) / radius;
        if (dist > 1) return 0;
        double impact = (1 - dist) * ExplosionImpl.calculateReceivedDamage(at, p);
        float dmg = (float) ((impact * impact + impact) / 2 * 7 * radius + 1);
        float armor = p.getArmor();
        float tough = (float) p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.ARMOR_TOUGHNESS);
        dmg *= 1 - net.minecraft.util.math.MathHelper.clamp(armor - dmg / (2 + tough / 4), armor * 0.2f, 20) / 25f;
        dmg *= 1 - Math.min(20, blastPoints(p)) / 25f;
        // stacked carts chain-react, but the later blasts mostly land after the first knocks us back
        return dmg * (1 + 0.5f * (cartsPerRail.getInt() - 1));
    }
}
