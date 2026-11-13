package dev.soulbound.module.bots;

import dev.soulbound.aim.AimSettings;
import dev.soulbound.gui.SchematicPortal;
import dev.soulbound.path.Pathfinder;
import dev.soulbound.schem.Schematic;
import dev.soulbound.setting.ActionSetting;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds a schematic from SoulBound's schematic folder: layer by layer from the bottom, walking
 * between spots with A* pathfinding and natural, per-frame head movement. When another player
 * comes close it stops, looks at them, says hi, and gets back to work.
 */
public final class BuilderBot extends BotModule {
    private final ModeSetting rotation, greeting;
    private final NumberSetting reach, placeDelay, greetRange, greetCooldown;
    private final BoolSetting exactStates, skipMissing, sprint, greet, lookAround;

    private Schematic schematic;
    private BlockPos origin;
    private List<Schematic.Entry> placements = List.of();
    private final List<Schematic.Entry> pending = new ArrayList<>();
    private final Set<Block> missing = new HashSet<>();
    private final Map<BlockPos, Integer> unreachable = new HashMap<>();
    private final Map<UUID, Long> greeted = new HashMap<>();

    private Schematic.Entry task;
    private List<BlockPos> path = List.of();
    private int pathIndex, stuckTicks, placeTimer, rescan, greetTicks = -1, repaths;
    private PlayerEntity greetTarget;
    private Vec3d lastPos;
    private Vec3d glance;
    private int glanceTicks;

    public BuilderBot() {
        super("Schematic Builder", "Builds schematics from your schem folder, naturally.", "AB-04", "builder_bot");
        add(new HeaderSetting("SCHEMATIC"));
        add(new ActionSetting("Schematic", "Pick what to build", "Choose Schematic", SchematicPortal::open));
        add(new ActionSetting("Origin", "Build starting from where you stand", "Set Origin Here", this::setOriginHere));
        rotation = add(new ModeSetting("Rotation", "Turn the build around", "0°", "0°", "90°", "180°", "270°"));
        add(new ActionSetting("Progress", "Forget skipped and missing blocks", "Reset", this::resetProgress));

        add(new HeaderSetting("BUILDING"));
        reach = add(new NumberSetting("Reach", "How far it places from", 4.2, 2.5, 4.5, 0.1, "m"));
        placeDelay = add(new NumberSetting("Place Delay", "Ticks between placements", 3, 0, 20, 1, "t"));
        exactStates = add(new BoolSetting("Exact States", "Count a block done only if its rotation/state matches", false));
        skipMissing = add(new BoolSetting("Skip Missing", "Skip blocks you don't have instead of stopping", true));
        sprint = add(new BoolSetting("Sprint", "Sprint on longer walks", true));

        aim = new AimSettings(this, 300, 0.65);
        lookAround = add(new BoolSetting("Look Around", "Occasionally glance around while walking", true));

        add(new HeaderSetting("GREETING"));
        greet = add(new BoolSetting("Greet Players", "Stop, look at nearby players and say hi", true));
        greeting = add(new ModeSetting("Message", "What to say", "hi", "hi", "hey!", "hello :)", "o/", "yo").visibleWhen(greet::isOn));
        greetRange = add(new NumberSetting("Greet Range", "How close a player must come", 8, 3, 20, 1, "m").visibleWhen(greet::isOn));
        greetCooldown = add(new NumberSetting("Greet Cooldown", "Don't greet the same player again for", 300, 30, 1800, 30, "s").visibleWhen(greet::isOn));
        addHudSettings();
    }

    @Override public Kind kind() { return Kind.UTILITY; }
    @Override public String getIcon() { return "icon_build"; }
    @Override public String[] statLabels() { return new String[]{"PLACED", "LEFT", "BPS"}; }
    @Override public int getCombo() { return pending.size(); }

    public Schematic getSchematic() { return schematic; }
    public BlockPos getOrigin() { return origin; }
    public int totalBlocks() { return placements.size(); }
    public int remaining() { return pending.size(); }
    public Set<Block> missing() { return missing; }

    public void setSchematic(Schematic s) {
        schematic = s;
        if (origin == null && mc.player != null) setOriginHere();
        rebuildPlacements();
        saveState();
    }

    // ------------------------------------------------------------------ persistence (config/soulbound/builder.json)
    private static java.nio.file.Path stateFile() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("soulbound").resolve("builder.json");
    }

    private void saveState() {
        try {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            if (schematic != null) o.addProperty("file", schematic.file.getFileName().toString());
            if (origin != null) {
                o.addProperty("x", origin.getX());
                o.addProperty("y", origin.getY());
                o.addProperty("z", origin.getZ());
            }
            java.nio.file.Files.createDirectories(stateFile().getParent());
            java.nio.file.Files.writeString(stateFile(), o.toString());
        } catch (Exception ignored) { }
    }

    /** Restores the last schematic and origin (called lazily the first time the bot runs). */
    private void loadState() {
        stateLoaded = true;
        try {
            if (!java.nio.file.Files.exists(stateFile())) return;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(stateFile())).getAsJsonObject();
            if (o.has("x")) origin = new BlockPos(o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt());
            if (o.has("file")) {
                java.nio.file.Path f = dev.soulbound.schem.SchematicLoader.folder().resolve(o.get("file").getAsString());
                if (java.nio.file.Files.exists(f)) schematic = dev.soulbound.schem.SchematicLoader.load(f);
            }
            rebuildPlacements();
        } catch (Exception e) {
            dev.soulbound.SoulBound.LOGGER.warn("Could not restore the builder's schematic", e);
        }
    }

    private boolean stateLoaded;

    public void setOriginHere() {
        if (mc.player == null) return;
        float yaw = (float) Math.toRadians(mc.player.getYaw());
        origin = BlockPos.ofFloored(mc.player.getX() - Math.sin(yaw) * 2, mc.player.getY(), mc.player.getZ() + Math.cos(yaw) * 2);
        rebuildPlacements();
        saveState();
    }

    public void setRotation(String r) { rotation.set(r); rebuildPlacements(); }

    /** The schematic for the picker screen, restoring the saved one if needed. */
    public Schematic currentSchematic() {
        if (!stateLoaded) loadState();
        return schematic;
    }

    private BlockRotation blockRotation() {
        return switch (rotation.get()) {
            case "90°" -> BlockRotation.CLOCKWISE_90;
            case "180°" -> BlockRotation.CLOCKWISE_180;
            case "270°" -> BlockRotation.COUNTERCLOCKWISE_90;
            default -> BlockRotation.NONE;
        };
    }

    private void rebuildPlacements() {
        placements = schematic == null || origin == null ? List.of() : schematic.placed(origin, blockRotation());
        resetProgress();
    }

    private void resetProgress() {
        missing.clear();
        unreachable.clear();
        task = null;
        path = List.of();
        rescan = 0;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        if (schematic != null && origin == null) setOriginHere();
        resetProgress();
    }

    // ------------------------------------------------------------------ main loop
    @Override
    protected void tickBot(ClientPlayerEntity p) {
        if (placeTimer > 0) placeTimer--;
        unreachable.replaceAll((k, v) -> v - 1);
        unreachable.values().removeIf(v -> v <= 0);

        if (!stateLoaded) loadState();
        if (greet.isOn() && handleGreeting(p)) return;
        if (schematic == null || origin == null) {
            release();
            state = State.PAUSED;
            return;
        }
        if (--rescan <= 0 || task == null) {
            scan();
            rescan = 10;
        }
        if (pending.isEmpty()) {
            release();
            state = missing.isEmpty() ? State.DONE : State.PAUSED;
            return;
        }
        if (task == null || done(task)) {
            task = pickTask(p);
            path = List.of();
            repaths = 0;
        }
        if (task == null) {
            release();
            state = State.PAUSED;
            return;
        }

        int slot = ensureInHotbar(p, task.state().getBlock().asItem());
        if (slot < 0) {
            missing.add(task.state().getBlock());
            task = null;
            if (!skipMissing.isOn()) setEnabled(false);
            return;
        }

        BlockHitResult hit = placementHit(p, task.pos());
        boolean blockedByUs = p.getBoundingBox().intersects(new Box(task.pos()));
        if (hit != null && !blockedByUs) {
            state = State.BUILDING;
            move(false, false, false, false, false, false);
            path = List.of();
            if (lookAt(hit.getPos()) < 7 && placeTimer <= 0) {
                p.getInventory().setSelectedSlot(slot);
                mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
                p.swingHand(Hand.MAIN_HAND);
                countHit();
                placeTimer = placeDelay.getInt() + ThreadLocalRandom.current().nextInt(2);
                rescan = Math.min(rescan, 2);
            }
            return;
        }
        walkTo(p, task.pos());
    }

    private boolean done(Schematic.Entry e) {
        BlockState now = mc.world.getBlockState(e.pos());
        return exactStates.isOn() ? now.equals(e.state()) : now.isOf(e.state().getBlock());
    }

    private void scan() {
        // a block type we ran out of comes back as soon as it's in the inventory again
        if (!missing.isEmpty() && mc.player != null) {
            var inv = mc.player.getInventory();
            missing.removeIf(b -> {
                for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(b.asItem())) return true;
                return false;
            });
        }
        pending.clear();
        for (Schematic.Entry e : placements) {
            if (done(e) || missing.contains(e.state().getBlock()) || e.state().getBlock().asItem() == Items.AIR) continue;
            pending.add(e);
        }
    }

    /** Lowest unfinished layer first, nearest block within it. */
    private Schematic.Entry pickTask(ClientPlayerEntity p) {
        int layer = Integer.MAX_VALUE;
        for (Schematic.Entry e : pending) if (!unreachable.containsKey(e.pos())) layer = Math.min(layer, e.pos().getY());
        Schematic.Entry best = null;
        double bestD = Double.MAX_VALUE;
        for (Schematic.Entry e : pending) {
            if (e.pos().getY() != layer || unreachable.containsKey(e.pos())) continue;
            if (!hasSupport(e.pos())) continue;
            double d = p.squaredDistanceTo(Vec3d.ofCenter(e.pos()));
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private boolean hasSupport(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (!mc.world.getBlockState(n).isReplaceable()) return true;
        }
        return false;
    }

    /** The click on a neighbouring block's face that places {@code pos}, if one is within reach. */
    private BlockHitResult placementHit(ClientPlayerEntity p, BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return null;
        BlockHitResult best = null;
        double bestD = Double.MAX_VALUE;
        Vec3d eye = p.getEyePos();
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState ns = mc.world.getBlockState(n);
            if (ns.isReplaceable() || ns.getCollisionShape(mc.world, n).isEmpty()) continue;
            Direction face = d.getOpposite();
            Vec3d hitVec = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            double dist = eye.distanceTo(hitVec);
            if (dist > reach.get() || dist >= bestD) continue;
            bestD = dist;
            best = new BlockHitResult(hitVec, face, n, false);
        }
        return best;
    }

    // ------------------------------------------------------------------ movement
    private void walkTo(ClientPlayerEntity p, BlockPos goal) {
        state = State.WALKING;
        BlockPos feet = p.getBlockPos();
        if (path.isEmpty() || stuckTicks > 40) {
            double r = reach.get() - 0.4;
            path = Pathfinder.find(mc.world, feet, pos -> {
                if (pos.equals(goal) || pos.up().equals(goal)) return false;
                Vec3d eye = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.62, pos.getZ() + 0.5);
                return eye.distanceTo(Vec3d.ofCenter(goal)) <= r;
            }, goal);
            pathIndex = path.size() > 1 ? 1 : 0; // node 0 is where we already stand
            stuckTicks = 0;
            if (++repaths > 4) {
                // keeps failing to make progress toward this block: come back to it later
                unreachable.put(goal, 200);
                task = null;
                path = List.of();
                repaths = 0;
                release();
                return;
            }
            if (path.isEmpty()) {
                unreachable.put(goal, 200);
                task = null;
                release();
                return;
            }
        }
        if (pathIndex >= path.size()) {
            path = List.of();
            return;
        }
        BlockPos node = path.get(pathIndex);
        Vec3d c = Vec3d.ofBottomCenter(node);
        double horiz = Math.hypot(c.x - p.getX(), c.z - p.getZ());
        boolean nextCloser = pathIndex + 1 < path.size()
                && Vec3d.ofBottomCenter(path.get(pathIndex + 1)).squaredDistanceTo(p.getX(), node.getY(), p.getZ()) < horiz * horiz;
        if ((horiz < (pathIndex == path.size() - 1 ? 0.35 : 0.5) || nextCloser) && Math.abs(p.getY() - node.getY()) < 1.2) {
            pathIndex++;
            stuckTicks = 0;
            if (pathIndex >= path.size()) {
                move(false, false, false, false, false, false);
                return;
            }
            node = path.get(pathIndex);
            c = Vec3d.ofBottomCenter(node);
        }
        Vec3d here = new Vec3d(p.getX(), p.getY(), p.getZ());
        stuckTicks = lastPos != null && here.squaredDistanceTo(lastPos) < 0.0004 ? stuckTicks + 1 : 0;
        lastPos = here;

        // look where we're walking (a little further ahead once close), at head height, with the odd glance around
        BlockPos ahead = horiz < 1.2 ? path.get(Math.min(path.size() - 1, pathIndex + 1)) : node;
        Vec3d look = Vec3d.ofBottomCenter(ahead).add(0, 1.5, 0);
        if (ahead.equals(node) || Vec3d.ofBottomCenter(ahead).squaredDistanceTo(p.getX(), ahead.getY(), p.getZ()) < 0.3) {
            // too close to look "at" it: look along the walking direction instead
            Vec3d dir = new Vec3d(c.x - p.getX(), 0, c.z - p.getZ());
            if (dir.lengthSquared() > 1e-4) look = p.getEyePos().add(dir.normalize().multiply(4)).add(0, -0.3, 0);
        }
        if (lookAround.isOn()) {
            if (glanceTicks <= 0 && ThreadLocalRandom.current().nextFloat() < 0.01f) {
                glance = new Vec3d(ThreadLocalRandom.current().nextDouble(-3, 3), ThreadLocalRandom.current().nextDouble(-1, 1.5),
                        ThreadLocalRandom.current().nextDouble(-3, 3));
                glanceTicks = 12 + ThreadLocalRandom.current().nextInt(12);
            }
            if (glanceTicks > 0) {
                glanceTicks--;
                look = look.add(glance);
            }
        }
        lookAt(look);

        float wantYaw = (float) Math.toDegrees(Math.atan2(c.z - p.getZ(), c.x - p.getX())) - 90f;
        float yawErr = Math.abs(MathHelper.wrapDegrees(wantYaw - p.getYaw()));
        boolean forward = yawErr < 60;
        boolean jump = p.isOnGround() && (node.getY() > p.getBlockPos().getY() || p.horizontalCollision);
        boolean run = sprint.isOn() && forward && yawErr < 20 && path.size() - pathIndex > 4;
        move(forward, false, false, false, jump, run);
        // steer: the body follows the head, but nudge with strafe when the waypoint is to the side
        if (yawErr > 25 && yawErr < 120 && horiz > 0.6) {
            float side = MathHelper.wrapDegrees(wantYaw - p.getYaw());
            mc.options.leftKey.setPressed(side < 0);
            mc.options.rightKey.setPressed(side > 0);
        }
    }

    // ------------------------------------------------------------------ inventory
    /** Puts {@code item} in the hotbar if it's anywhere in the inventory; returns its hotbar slot or -1. */
    private int ensureInHotbar(ClientPlayerEntity p, Item item) {
        if (item == Items.AIR) return -1;
        int slot = hotbarSlot(p, item);
        if (slot >= 0) return slot;
        var inv = p.getInventory();
        for (int i = 9; i < 36; i++) {
            if (!inv.getStack(i).isOf(item)) continue;
            int target = 8;
            for (int h = 0; h < 9; h++) if (inv.getStack(h).isEmpty()) { target = h; break; }
            mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, target, SlotActionType.SWAP, p);
            return target;
        }
        return -1;
    }

    // ------------------------------------------------------------------ greeting
    private boolean handleGreeting(ClientPlayerEntity p) {
        long now = System.currentTimeMillis();
        if (greetTicks < 0) {
            for (PlayerEntity other : mc.world.getPlayers()) {
                if (other == p || other.isSpectator() || p.distanceTo(other) > greetRange.get()) continue;
                Long last = greeted.get(other.getUuid());
                if (last != null && now - last < greetCooldown.getInt() * 1000L) continue;
                greetTarget = other;
                greetTicks = 0;
                break;
            }
            if (greetTicks < 0) return false;
        }
        // freeze, turn to them, say hi, then get back to work
        state = State.GREETING;
        move(false, false, false, false, false, false);
        target = greetTarget;
        lookAt(greetTarget.getEyePos());
        greetTicks++;
        if (greetTicks == 14) p.networkHandler.sendChatMessage(greeting.get());
        if (greetTicks == 15) p.swingHand(Hand.MAIN_HAND); // a little wave
        if (greetTicks >= 52 || greetTarget.isRemoved()) {
            greeted.put(greetTarget.getUuid(), now);
            greetTicks = -1;
            greetTarget = null;
            target = null;
        }
        return true;
    }

    @Override
    protected void onDisable() {
        greetTicks = -1;
        super.onDisable();
    }

    /** Short status line for the schematic screen. */
    public Text status() {
        if (schematic == null) return Text.literal("No schematic loaded");
        int total = placements.size(), left = pending.size();
        return Text.literal((total - left) + " / " + total + " placed" + (missing.isEmpty() ? "" : "  ·  " + missing.size() + " missing types"));
    }
}
