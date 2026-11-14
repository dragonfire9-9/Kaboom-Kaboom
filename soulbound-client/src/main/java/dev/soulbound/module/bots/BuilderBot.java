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
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds a schematic from SoulBound's schematic folder.
 *
 * <p>Each tick it first places anything it can reach from where it stands, so it rarely walks for
 * a single block. Otherwise it walks (A*) to a spot in reach; if no spot on the ground reaches,
 * it pillars up on scaffold blocks and digs the pillar back down when it leaves. Blocks in the way
 * are mined first. Directional blocks (stairs, logs, slabs, doors...) are placed facing the way
 * the schematic says by choosing the clicked face, hit height and the direction it looks. It
 * sneaks when it has to click against a chest, door or other usable block. When another player
 * comes close it stops, looks at them, says hi, and gets back to work.
 */
public final class BuilderBot extends BotModule {
    private static final Set<Property<?>> ORIENTATION = Set.of(Properties.FACING, Properties.HORIZONTAL_FACING, Properties.HOPPER_FACING,
            Properties.AXIS, Properties.HORIZONTAL_AXIS, Properties.BLOCK_HALF, Properties.SLAB_TYPE, Properties.BLOCK_FACE,
            Properties.ROTATION, Properties.DOOR_HINGE, Properties.ATTACHMENT, Properties.ORIENTATION);
    private static final List<Item> SCAFFOLD_ORDER = List.of(Items.DIRT, Items.COBBLESTONE, Items.NETHERRACK, Items.COBBLED_DEEPSLATE,
            Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.STONE, Items.OAK_PLANKS, Items.SPRUCE_PLANKS);

    private final ModeSetting rotation, order, scaffoldBlock, greeting, clearMode;
    private final NumberSetting reach, placeDelay, randomDelay, maxAimError, greetRange, greetCooldown, maxPillar, breakDelay;
    private final ModeSetting accuracy, previewMode, previewStyle;
    private final NumberSetting previewRange, previewMax, previewOpacity;
    private final BoolSetting preview, previewWalls, previewMistakes, previewArea, previewWhenOff;
    private final BoolSetting defend;
    private final NumberSetting defendRange;
    private final BoolSetting skipMissing, sprint, greet, lookAround, scaffold, removeScaffold, clearWrong,
            creativeFetch, sneakSafe, pauseInventory, avoidFalls;

    private static final boolean DEBUG = Boolean.getBoolean("soulbound.debugBuilder");
    private Schematic schematic;
    private BlockPos origin;
    private final Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
    private BlockPos boxMin, boxMax;
    private final List<Schematic.Entry> pending = new ArrayList<>();
    private final List<BlockPos> toBreak = new ArrayList<>();
    private final List<BlockPos> toToggle = new ArrayList<>();
    private final List<BlockPos> missingPos = new ArrayList<>();
    private final Set<Block> missing = new HashSet<>();
    private final Map<BlockPos, Integer> unreachable = new HashMap<>();
    private final Map<UUID, Long> greeted = new HashMap<>();
    private final Set<BlockPos> scaffoldBlocks = new LinkedHashSet<>();
    /** Escape steps we leave standing until the build is finished, then clean up. */
    private final Set<BlockPos> leftovers = new LinkedHashSet<>();

    private Schematic.Entry task;
    private Placement sticky;
    private int placingTicks;
    private int layer = Integer.MAX_VALUE; // lowest layer with something placeable (Bottom Up)
    private BlockPos breaking;
    private List<BlockPos> path = List.of();
    private BlockPos pathGoal;
    private int pathIndex, stuckTicks, placeTimer, rescan, greetTicks = -1, repaths, sneakTicks, aimedTicks, breakTimer;
    private PlayerEntity greetTarget;
    private Vec3d glance;
    private double bestToNode = Double.MAX_VALUE;

    private int glanceTicks;
    private boolean stateLoaded;

    public BuilderBot() {
        super("Schematic Builder", "Builds schematics from your schem folder, naturally.", "AB-04", "builder_bot");
        add(new HeaderSetting("SCHEMATIC"));
        add(new ActionSetting("Schematic", "Pick what to build", "Choose Schematic", SchematicPortal::open));
        add(new ActionSetting("Origin", "Build starting from where you stand", "Set Origin Here", this::setOriginHere));
        rotation = add(new ModeSetting("Rotation", "Turn the build around", "0°", "0°", "90°", "180°", "270°"));
        add(new ActionSetting("Progress", "Forget skipped and missing blocks", "Reset", this::resetProgress));

        add(new HeaderSetting("PREVIEW"));
        preview = add(new BoolSetting("Show Preview", "Draw the schematic in the world as a hologram", true));
        previewMode = add(new ModeSetting("Preview Shows", "Which blocks to draw", "Current Layer", "Current Layer", "Nearby", "Whole Build").visibleWhen(preview::isOn));
        previewStyle = add(new ModeSetting("Preview Style", "Ghost blocks tinted like the real block, outlines, or both", "Both", "Ghost", "Outline", "Both").visibleWhen(preview::isOn));
        previewOpacity = add(new NumberSetting("Ghost Opacity", "How solid the ghost blocks look", 22, 5, 60, 1, "%").visibleWhen(preview::isOn));
        previewRange = add(new NumberSetting("Preview Range", "Only draw blocks this close to you", 32, 8, 96, 4, "m").visibleWhen(preview::isOn));
        previewMax = add(new NumberSetting("Preview Limit", "Draw at most this many blocks (nearest first), so big builds never lag", 1500, 100, 6000, 100).visibleWhen(preview::isOn));
        previewMistakes = add(new BoolSetting("Show Mistakes", "Red: wrong blocks to clear. Orange: blocks you're missing", true).visibleWhen(preview::isOn));
        previewArea = add(new BoolSetting("Show Area", "Outline the whole build's box", true).visibleWhen(preview::isOn));
        previewWalls = add(new BoolSetting("Through Walls", "Draw the preview through blocks", false).visibleWhen(preview::isOn));
        previewWhenOff = add(new BoolSetting("Preview When Off", "Keep showing it while the bot is off, to line up the origin", true).visibleWhen(preview::isOn));

        add(new HeaderSetting("BUILDING"));
        order = add(new ModeSetting("Build Order", "Rows works through each layer row by row like a printer; Bottom Up finishes each layer nearest-first; Nearest places whatever is closest",
                "Rows", "Rows", "Bottom Up", "Nearest"));
        reach = add(new NumberSetting("Reach", "How far it places from", 4.3, 2.5, 4.5, 0.1, "m"));
        placeDelay = add(new NumberSetting("Place Delay", "Ticks between placements", 2, 0, 20, 1, "t"));
        randomDelay = add(new NumberSetting("Random Delay", "Up to this many extra ticks, so the rhythm isn't robotic", 2, 0, 10, 1, "t"));
        maxAimError = add(new NumberSetting("Max Aim Error", "Only place when looking this close to the spot", 8, 2, 30, 1, "°"));
        accuracy = add(new ModeSetting("Accuracy", "Block: right block. Facing: also the right way round (stairs, logs, slabs, doors). Exact: also doors, trapdoors and gates open or shut as drawn",
                "Facing", "Block", "Facing", "Exact"));
        sneakSafe = add(new BoolSetting("Sneak On Usable Blocks", "Sneak when it has to click against a chest, door or table", true));
        skipMissing = add(new BoolSetting("Skip Missing", "Skip blocks you don't have instead of stopping", true));
        creativeFetch = add(new BoolSetting("Creative Fetch", "In creative, take any block it needs from the creative inventory", true));
        pauseInventory = add(new BoolSetting("Refill Hotbar", "Move needed blocks from your inventory into the hotbar", true));

        add(new HeaderSetting("CLEARING"));
        clearWrong = add(new BoolSetting("Clear Wrong Blocks", "Mine blocks that sit where the schematic wants something else", true));
        clearMode = add(new ModeSetting("Clear Area", "Also mine terrain inside the build's box where the schematic has air", "Off", "Off", "On"));
        breakDelay = add(new NumberSetting("Break Delay", "Ticks to wait between mined blocks", 2, 0, 20, 1, "t"));

        add(new HeaderSetting("SCAFFOLD"));
        scaffold = add(new BoolSetting("Scaffold", "Pillar up on junk blocks to reach high parts", true));
        scaffoldBlock = add(new ModeSetting("Scaffold Block", "What to pillar with (Auto uses junk the schematic doesn't need)", "Auto",
                "Auto", "Dirt", "Cobblestone", "Netherrack").visibleWhen(scaffold::isOn));
        maxPillar = add(new NumberSetting("Max Pillar", "Highest pillar it will build", 24, 2, 64, 1, " blocks").visibleWhen(scaffold::isOn));
        removeScaffold = add(new BoolSetting("Remove Scaffold", "Dig the pillar back down when leaving it", true).visibleWhen(scaffold::isOn));

        add(new HeaderSetting("MOVEMENT"));
        sprint = add(new BoolSetting("Sprint", "Sprint on longer walks", true));
        avoidFalls = add(new BoolSetting("Safe Drops", "Only drop down three blocks at most while walking", true));
        aim = new AimSettings(this, 320, 0.6);
        lookAround = add(new BoolSetting("Look Around", "Occasionally glance around while walking", true));

        add(new HeaderSetting("SAFETY"));
        defend = add(new BoolSetting("Defend Self", "Fight off hostile mobs that come close, then get back to work", true));
        defendRange = add(new NumberSetting("Defend Range", "How close a mob must be", 4, 2, 8, 0.5, "m").visibleWhen(defend::isOn));

        add(new HeaderSetting("GREETING"));
        greet = add(new BoolSetting("Greet Players", "Stop, look at nearby players and say hi", true));
        greeting = add(new ModeSetting("Message", "What to say", "hi", "hi", "hey!", "hello :)", "o/", "yo").visibleWhen(greet::isOn));
        greetRange = add(new NumberSetting("Greet Range", "How close a player must come", 8, 3, 20, 1, "m").visibleWhen(greet::isOn));
        greetCooldown = add(new NumberSetting("Greet Cooldown", "Don't greet the same player again for", 300, 30, 1800, 30, "s").visibleWhen(greet::isOn));
        addHudSettings();
        dev.soulbound.render.WorldDraw.register(this::drawPreview);
    }

    @Override public Kind kind() { return Kind.UTILITY; }
    @Override public String getIcon() { return "icon_build"; }
    @Override public String[] statLabels() { return new String[]{"PLACED", "LEFT", "BPS"}; }
    @Override public int getCombo() { return pending.size(); }

    @Override
    public String[] idleLines() {
        if (schematic == null) return new String[]{"No schematic", "Choose one in the Bots tab"};
        String doing = switch (state) {
            case CLEARING -> "Clearing " + (breaking == null ? "blocks" : mc.world.getBlockState(breaking).getBlock().getName().getString());
            case CLIMBING -> "Scaffolding up";
            case DONE -> "Finished";
            case PAUSED -> !missing.isEmpty() ? "Out of materials"
                    : pending.stream().noneMatch(e -> hasSupport(e.pos())) ? pending.size() + " floating blocks left" : "Waiting";
            default -> task == null ? "Planning" : (state == State.PLACING ? "Placing " : "Walking to ") + task.state().getBlock().getName().getString();
        };
        String detail = schematic.name + (missing.isEmpty() ? "" : "  ·  missing " + missing.iterator().next().getName().getString()
                + (missing.size() > 1 ? " +" + (missing.size() - 1) : ""));
        return new String[]{doing, detail};
    }

    public Schematic getSchematic() { return schematic; }
    public BlockPos getOrigin() { return origin; }
    public int totalBlocks() { return plan.size(); }
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
            com.google.gson.JsonArray sc = new com.google.gson.JsonArray();
            for (BlockPos b : scaffoldBlocks) sc.add(b.asLong());
            for (BlockPos b : leftovers) sc.add(b.asLong());
            o.add("scaffold", sc);
            java.nio.file.Files.createDirectories(stateFile().getParent());
            java.nio.file.Files.writeString(stateFile(), o.toString());
        } catch (Exception ignored) { }
    }

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
            if (o.has("scaffold")) for (var el : o.getAsJsonArray("scaffold")) scaffoldBlocks.add(BlockPos.fromLong(el.getAsLong()));
        } catch (Exception e) {
            dev.soulbound.SoulBound.LOGGER.warn("Could not restore the builder's schematic", e);
        }
    }

    public void setOriginHere() {
        if (mc.player == null) return;
        float yaw = (float) Math.toRadians(mc.player.getYaw());
        origin = BlockPos.ofFloored(mc.player.getX() - Math.sin(yaw) * 2, mc.player.getY(), mc.player.getZ() + Math.cos(yaw) * 2);
        rebuildPlacements();
        saveState();
    }

    public void setRotation(String r) { rotation.set(r); rebuildPlacements(); }

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
        plan.clear();
        boxMin = boxMax = null;
        if (schematic != null && origin != null) {
            for (Schematic.Entry e : schematic.placed(origin, blockRotation())) {
                if (e.state().isAir()) continue;
                plan.put(e.pos(), e.state());
            }
            // the schematic's full box (air included), for Clear Area
            int[] mn = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE}, mx = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            BlockPos[] corners = {BlockPos.ORIGIN, new BlockPos(schematic.sizeX - 1, schematic.sizeY - 1, schematic.sizeZ - 1)};
            for (BlockPos c : corners) {
                BlockPos r = origin.add(c.rotate(blockRotation()));
                mn[0] = Math.min(mn[0], r.getX()); mn[1] = Math.min(mn[1], r.getY()); mn[2] = Math.min(mn[2], r.getZ());
                mx[0] = Math.max(mx[0], r.getX()); mx[1] = Math.max(mx[1], r.getY()); mx[2] = Math.max(mx[2], r.getZ());
            }
            boxMin = new BlockPos(mn[0], mn[1], mn[2]);
            boxMax = new BlockPos(mx[0], mx[1], mx[2]);
        }
        resetProgress();
    }

    private void resetProgress() {
        missing.clear();
        unreachable.clear();
        task = null;
        breaking = null;
        path = List.of();
        rescan = 0;
    }

    @Override
    protected void onEnable() {
        super.onEnable();
        if (schematic != null && origin == null) setOriginHere();
        resetProgress();
    }

    @Override
    protected void onDisable() {
        greetTicks = -1;
        if (breaking != null && mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        breaking = null;
        mc.options.sneakKey.setPressed(false);
        super.onDisable();
    }

    // ------------------------------------------------------------------ main loop
    @Override
    protected void tickBot(ClientPlayerEntity p) {
        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;
        unreachable.replaceAll((k, v) -> v - 1);
        unreachable.values().removeIf(v -> v <= 0);
        boolean wantSneak = false;
        try {
            if (!stateLoaded) loadState();
            if (greet.isOn() && handleGreeting(p)) return;
            if (defend.isOn() && defendSelf(p)) return;
            if (schematic == null || origin == null) {
                stop(State.PAUSED);
                return;
            }
            if (--rescan <= 0) {
                scan(p);
                rescan = 10;
            }

            // 1. mining: wrong blocks and terrain in the way first
            if (breaking != null || (!toBreak.isEmpty() && breakTimer <= 0)) {
                if (breaking == null) breaking = pickBreak(p);
                if (breaking != null) {
                    tickBreaking(p);
                    return;
                }
            }

            // 1b. Exact: open or shut doors, trapdoors and gates to match the drawing
            if (!toToggle.isEmpty() && placeTimer <= 0) {
                BlockPos tp = toToggle.getFirst();
                if (!openWrong(mc.world.getBlockState(tp), plan.getOrDefault(tp, mc.world.getBlockState(tp)))) {
                    toToggle.removeFirst();
                } else if (p.getEyePos().distanceTo(Vec3d.ofCenter(tp)) <= reach.get()) {
                    state = State.PLACING;
                    move(false, false, false, false, false, false);
                    if (lookAt(Vec3d.ofCenter(tp)) < maxAimError.getFloat()) {
                        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(tp), Direction.UP, tp, false));
                        p.swingHand(Hand.MAIN_HAND);
                        toToggle.removeFirst();
                        placeTimer = placeDelay.getInt() + 2;
                    }
                    return;
                } else if (pending.isEmpty()) {
                    walkTo(p, tp);
                    return;
                }
            }

            if (pending.isEmpty() && toBreak.isEmpty() && toToggle.isEmpty()) {
                if (leaveScaffold(p)) return;
                stop(missing.isEmpty() ? State.DONE : State.PAUSED);
                return;
            }

            // 2. anything placeable from right here, lowest first
            // (mid-pillar we only switch to placing once standing on the new block, or it flip-flops in the air)
            Placement here = !p.isOnGround() && !p.isTouchingWater() ? null : bestPlacementFromHere(p);
            if (here != null && sticky != null && !here.entry.pos().equals(sticky.entry.pos()) && placingTicks < 30) {
                Placement again = planPlacement(p, sticky.entry);
                if (again != null) here = again; // finish the block we were turning toward
            }
            if (here != null) {
                if (sticky == null || !sticky.entry.pos().equals(here.entry.pos())) placingTicks = 0;
                sticky = here;
                if (++placingTicks > 80) {
                    // couldn't settle on it (something in the way): leave it for a while
                    unreachable.put(here.entry.pos(), 200);
                    sticky = null;
                    placingTicks = 0;
                    return;
                }
                path = List.of();
                wantSneak = here.sneak;
                place(p, here);
                return;
            }

            // 3. otherwise go to the next block
            if (task == null || done(task) || unreachable.containsKey(task.pos())) {
                task = pickTask(p);
                path = List.of();
                repaths = 0;
            }
            if (task == null) {
                if (!toBreak.isEmpty()) return; // still mining
                stop(State.PAUSED);
                return;
            }
            walkTo(p, task.pos());
        } finally {
            if (DEBUG && p.age % 20 == 0) dev.soulbound.SoulBound.LOGGER.info("[builder] state={} task={} breaking={} pos={} yaw={} pitch={} pending={} break={} layer={} unreach={} aimed={}",
                    state, task == null ? null : task.pos().toShortString() + " " + task.state(), breaking, p.getBlockPos().toShortString(),
                    (int) p.getYaw(), (int) p.getPitch(), pending.size(), toBreak.size(), layer, unreachable.size(), aimedTicks);
            if (DEBUG && p.age % 100 == 0) dev.soulbound.SoulBound.LOGGER.info("[builder] unreach sample {} supported={}",
                    unreachable.entrySet().stream().limit(4).map(e -> e.getKey().toShortString() + "=" + e.getValue()).toList(),
                    pending.stream().limit(30).map(e -> e.pos().toShortString() + " " + e.state().getBlock().getTranslationKey()).toList());
            if (wantSneak) sneakTicks++; else sneakTicks = 0;
            mc.options.sneakKey.setPressed(wantSneak);
        }
    }

    private void stop(State s) {
        release();
        state = s;
    }

    private boolean orient() { return !accuracy.is("Block"); }

    private boolean done(Schematic.Entry e) { return matches(mc.world.getBlockState(e.pos()), e.state(), orient()); }

    private static boolean matches(BlockState now, BlockState want, boolean orientation) {
        if (!now.isOf(want.getBlock())) return false;
        if (!orientation) return true;
        for (Property<?> prop : want.getProperties()) {
            if (ORIENTATION.contains(prop) && now.contains(prop) && !now.get(prop).equals(want.get(prop))) return false;
        }
        return true;
    }

    private void scan(ClientPlayerEntity p) {
        // a block type we ran out of comes back as soon as it's in the inventory again
        if (!missing.isEmpty()) {
            var inv = p.getInventory();
            missing.removeIf(b -> {
                for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(b.asItem())) return true;
                return creative(p);
            });
        }
        pending.clear();
        toBreak.clear();
        toToggle.clear();
        missingPos.clear();
        for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
            BlockPos pos = e.getKey();
            BlockState now = mc.world.getBlockState(pos);
            if (matches(now, e.getValue(), orient())) {
                if (accuracy.is("Exact") && openWrong(now, e.getValue())) toToggle.add(pos);
                continue;
            }
            boolean wrongBlock = !now.isReplaceable() && !now.isAir();
            if (wrongBlock) {
                if (clearWrong.isOn() && breakable(now, pos)) toBreak.add(pos);
                continue;
            }
            Item item = e.getValue().getBlock().asItem();
            if (item == Items.AIR) continue;
            if (!creative(p) && !has(p, item)) {
                missing.add(e.getValue().getBlock());
                missingPos.add(pos);
                continue;
            }
            if (missing.contains(e.getValue().getBlock())) { missingPos.add(pos); continue; }
            pending.add(new Schematic.Entry(pos, e.getValue()));
        }
        if (!missing.isEmpty() && !skipMissing.isOn() && pending.isEmpty()) setEnabled(false);
        updateLayer();
        if (!clearMode.is("Off") && boxMin != null) {
            for (BlockPos pos : BlockPos.iterate(boxMin, boxMax)) {
                if (plan.containsKey(pos) || scaffoldBlocks.contains(pos)) continue;
                BlockState s = mc.world.getBlockState(pos);
                if (s.isAir() || !s.getFluidState().isEmpty() || !breakable(s, pos)) continue;
                toBreak.add(pos.toImmutable());
            }
        }
        boolean changed = scaffoldBlocks.removeIf(pos -> mc.world.getBlockState(pos).isAir());
        changed |= leftovers.removeIf(pos -> mc.world.getBlockState(pos).isAir() || plan.containsKey(pos));
        if (changed) saveState();
        buildPreview(p);
        // finished: clean up any scaffold left behind (from an interrupted pillar or an escape)
        if (pending.isEmpty() && toBreak.isEmpty()) {
            BlockPos under = p.getBlockPos().down();
            for (BlockPos b : leftovers) if (!b.equals(under)) toBreak.add(b);
            for (BlockPos b : scaffoldBlocks) if (!b.equals(under)) toBreak.add(b);
        }
    }

    private static boolean has(ClientPlayerEntity p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(item)) return true;
        return false;
    }

    private boolean breakable(BlockState s, BlockPos pos) {
        return s.getHardness(mc.world, pos) >= 0 && s.getFluidState().isEmpty();
    }

    private boolean creative(ClientPlayerEntity p) { return creativeFetch.isOn() && p.getAbilities().creativeMode; }

    /** Lowest layer that still has a placeable, reachable block (Bottom Up). */
    private void updateLayer() {
        layer = Integer.MAX_VALUE;
        if (order.is("Nearest")) return;
        for (Schematic.Entry e : pending) {
            if (e.pos().getY() < layer && !unreachable.containsKey(e.pos()) && hasSupport(e.pos())) layer = e.pos().getY();
        }
    }

    /** Next block to place: lowest layer (Bottom Up) or nearest, always with something to place against. */
    private Schematic.Entry pickTask(ClientPlayerEntity p) {
        updateLayer();
        Schematic.Entry best = null;
        double bestD = Double.MAX_VALUE;
        for (Schematic.Entry e : pending) {
            if (unreachable.containsKey(e.pos()) || !hasSupport(e.pos())) continue;
            if (layer != Integer.MAX_VALUE && e.pos().getY() != layer) continue;
            double d = order.is("Rows") ? rowIndex(e.pos()) : p.squaredDistanceTo(Vec3d.ofCenter(e.pos()));
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** Printer order: layer, then row (Z), then along the row (X), snaking back and forth. */
    private double rowIndex(BlockPos pos) {
        if (boxMin == null) return 0;
        int w = boxMax.getX() - boxMin.getX() + 1, l = boxMax.getZ() - boxMin.getZ() + 1;
        int rx = pos.getX() - boxMin.getX(), rz = pos.getZ() - boxMin.getZ(), ry = pos.getY() - boxMin.getY();
        return (double) ry * w * l + rz * w + (rz % 2 == 0 ? rx : w - 1 - rx);
    }

    private static boolean openWrong(BlockState now, BlockState want) {
        return want.contains(Properties.OPEN) && now.contains(Properties.OPEN) && !now.get(Properties.OPEN).equals(want.get(Properties.OPEN))
                && !(now.getBlock() instanceof net.minecraft.block.DoorBlock && now.isOf(net.minecraft.block.Blocks.IRON_DOOR))
                && !now.isOf(net.minecraft.block.Blocks.IRON_TRAPDOOR);
    }

    private boolean hasSupport(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            BlockState s = mc.world.getBlockState(n);
            if (!s.isReplaceable() && !s.getCollisionShape(mc.world, n).isEmpty()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ preview (hologram)
    private static final int K_PENDING = 0, K_WRONG = 1, K_MISSING = 2, K_TOGGLE = 3;
    // a snapshot rebuilt only when the bot rescans (every half second), so each frame just replays it
    private long[] pvPos = new long[0];
    private int[] pvColor = new int[0];
    private byte[] pvKind = new byte[0];
    private int pvCount, previewTimer;

    /** Keeps the preview fresh while the bot is off (called every client tick). */
    public void previewTick() {
        var p = mc.player;
        if (p == null || mc.world == null || isEnabled() || !preview.isOn() || !previewWhenOff.isOn()) return;
        if (!stateLoaded) loadState();
        if (schematic == null || origin == null || --previewTimer > 0) return;
        previewTimer = 20;
        scan(p);
        updateLayer();
    }

    private void buildPreview(ClientPlayerEntity p) {
        if (!preview.isOn()) {
            pvCount = 0;
            return;
        }
        double range = previewRange.get(), rangeSq = range * range;
        Vec3d at = p.getEntityPos();
        int showLayer = layer;
        if (showLayer == Integer.MAX_VALUE) for (Schematic.Entry e : pending) showLayer = Math.min(showLayer, e.pos().getY());
        boolean layerOnly = previewMode.is("Current Layer"), all = previewMode.is("Whole Build");
        List<long[]> c = new ArrayList<>(); // {pos, kind, color}
        for (Schematic.Entry e : pending) {
            if (layerOnly && e.pos().getY() != showLayer) continue;
            if (!all && e.pos().getSquaredDistance(at) > rangeSq) continue;
            int col = e.state().getMapColor(mc.world, e.pos()).color;
            c.add(new long[]{e.pos().asLong(), K_PENDING, col == 0 ? 0xB0B8C8 : col});
        }
        if (previewMistakes.isOn()) {
            for (BlockPos b : toBreak) if (b.getSquaredDistance(at) <= rangeSq * 2) c.add(new long[]{b.asLong(), K_WRONG, 0xFF4D5E});
            for (BlockPos b : missingPos) {
                if ((layerOnly && b.getY() != showLayer) || (!all && b.getSquaredDistance(at) > rangeSq)) continue;
                c.add(new long[]{b.asLong(), K_MISSING, 0xFFA64D});
            }
            for (BlockPos b : toToggle) c.add(new long[]{b.asLong(), K_TOGGLE, 0xFFD84D});
        }
        int max = previewMax.getInt();
        if (c.size() > max) {
            c.sort((a, b) -> Double.compare(BlockPos.fromLong(a[0]).getSquaredDistance(at), BlockPos.fromLong(b[0]).getSquaredDistance(at)));
            c = c.subList(0, max);
        }
        long[] pos = new long[c.size()];
        int[] col = new int[c.size()];
        byte[] kind = new byte[c.size()];
        for (int i = 0; i < c.size(); i++) {
            pos[i] = c.get(i)[0];
            kind[i] = (byte) c.get(i)[1];
            col[i] = (int) c.get(i)[2];
        }
        pvPos = pos;
        pvColor = col;
        pvKind = kind;
        pvCount = c.size();
    }

    private void drawPreview(dev.soulbound.render.WorldDraw d) {
        if (!preview.isOn() || schematic == null || origin == null || (!isEnabled() && !previewWhenOff.isOn())) return;
        boolean see = previewWalls.isOn();
        long[] pos = pvPos;
        int[] col = pvColor;
        byte[] kind = pvKind;
        int n = Math.min(pvCount, pos.length);
        float ghost = previewOpacity.getFloat() / 100f;
        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        int accent = dev.soulbound.gui.render.Theme.accent();
        if (!previewStyle.is("Outline")) {
            var fill = d.fill(see);
            for (int i = 0; i < n; i++) {
                long l = pos[i];
                int x = BlockPos.unpackLongX(l), y = BlockPos.unpackLongY(l), z = BlockPos.unpackLongZ(l);
                float a = kind[i] == K_PENDING ? ghost : ghost * 1.4f;
                d.filled(fill, x + 0.08, y + 0.08, z + 0.08, x + 0.92, y + 0.92, z + 0.92, argb(col[i], a));
            }
        }
        var lines = d.lines(see);
        if (!previewStyle.is("Ghost")) {
            for (int i = 0; i < n; i++) {
                long l = pos[i];
                int x = BlockPos.unpackLongX(l), y = BlockPos.unpackLongY(l), z = BlockPos.unpackLongZ(l);
                int c = kind[i] == K_PENDING ? argb(lighten(col[i]), 0.55f) : argb(col[i], 0.9f);
                d.box(lines, x + 0.06, y + 0.06, z + 0.06, x + 0.94, y + 0.94, z + 0.94, c, kind[i] == K_PENDING ? 1.5f : 2.5f);
            }
        }
        // the block it's working on, pulsing in the accent colour
        Schematic.Entry t = task;
        if (isEnabled() && t != null) {
            BlockPos b = t.pos();
            d.box(d.lines(true), b.getX() - 0.02, b.getY() - 0.02, b.getZ() - 0.02, b.getX() + 1.02, b.getY() + 1.02, b.getZ() + 1.02, argb(accent, 0.5f + 0.5f * pulse), 3f);
        }
        if (previewArea.isOn() && boxMin != null) {
            d.box(lines, boxMin.getX(), boxMin.getY(), boxMin.getZ(), boxMax.getX() + 1, boxMax.getY() + 1, boxMax.getZ() + 1, argb(accent, 0.6f), 2f);
        }
    }

    private static int argb(int rgb, float a) { return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (rgb & 0xFFFFFF); }

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + (255 - r) / 2) << 16) | ((g + (255 - g) / 2) << 8) | (b + (255 - b) / 2);
    }

    // ------------------------------------------------------------------ stats for the HUD
    public int placedCount() { return plan.size() - pending.size() - missingPos.size(); }

    public int layerNow() { return layer == Integer.MAX_VALUE || boxMin == null ? -1 : layer - boxMin.getY() + 1; }

    public int layerTotal() { return boxMin == null ? 0 : boxMax.getY() - boxMin.getY() + 1; }

    public int toClear() { return toBreak.size(); }

    public BlockState currentBlock() { return task == null ? null : task.state(); }

    public List<Block> missingList() { return new ArrayList<>(missing); }

    // ------------------------------------------------------------------ placing
    private record Placement(Schematic.Entry entry, BlockHitResult hit, Vec3d lookAt, boolean sneak, int slot) {}

    private Placement bestPlacementFromHere(ClientPlayerEntity p) {
        if (p.age % 5 == 0) updateLayer();
        Placement best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = p.getEyePos();
        double r = reach.get();
        for (Schematic.Entry e : pending) {
            if (layer != Integer.MAX_VALUE && e.pos().getY() > layer) continue;
            if (unreachable.containsKey(e.pos())) continue;
            Vec3d c = Vec3d.ofCenter(e.pos());
            if (eye.squaredDistanceTo(c) > (r + 0.9) * (r + 0.9)) continue;
            if (p.getBoundingBox().intersects(new Box(e.pos()))) continue;
            double score = order.is("Rows") ? rowIndex(e.pos())
                    : e.pos().getY() * 100 + eye.squaredDistanceTo(c) + (task != null && e.pos().equals(task.pos()) ? -50 : 0);
            if (score >= bestScore) continue;
            Placement pl = planPlacement(p, e);
            if (pl == null) continue;
            best = pl;
            bestScore = score;
        }
        return best;
    }

    /** Works out where to click and which way to look so the block lands as the schematic wants. */
    private Placement planPlacement(ClientPlayerEntity p, Schematic.Entry e) {
        BlockPos pos = e.pos();
        if (!mc.world.getBlockState(pos).isReplaceable()) return null;
        int slot = ensureInHotbar(p, e.state().getBlock().asItem(), false);
        if (slot < 0 && !(creative(p) || has(p, e.state().getBlock().asItem()))) return null;
        ItemStack stack = new ItemStack(e.state().getBlock().asItem());
        Vec3d eye = p.getEyePos();
        double r = reach.get();
        boolean orient = orient() && e.state().getProperties().stream().anyMatch(ORIENTATION::contains);

        Placement fallback = null;
        float savedYaw = p.getYaw(), savedPitch = p.getPitch();
        try {
            for (int pass = 0; pass < 2; pass++) { // pass 0: neighbours you can't use; pass 1: usable ones (sneak)
                for (Direction d : Direction.values()) {
                    BlockPos n = pos.offset(d);
                    BlockState ns = mc.world.getBlockState(n);
                    if (ns.isReplaceable() || ns.getCollisionShape(mc.world, n).isEmpty()) continue;
                    boolean usable = usable(ns);
                    if (usable != (pass == 1) || (usable && !sneakSafe.isOn())) continue;
                    Direction face = d.getOpposite();
                    double[] heights = face.getAxis().isHorizontal() ? new double[]{0.5, 0.25, 0.75} : new double[]{0.5};
                    for (double hy : heights) {
                        Vec3d hitVec = Vec3d.ofCenter(n).add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                        if (face.getAxis().isHorizontal()) hitVec = new Vec3d(hitVec.x, n.getY() + hy, hitVec.z);
                        if (eye.distanceTo(hitVec) > r) continue;
                        BlockHitResult hit = new BlockHitResult(hitVec, face, n, false);
                        float[] natural = angles(eye, hitVec);
                        if (!orient) return new Placement(e, hit, hitVec, usable, slot);
                        // try the natural look first, then the four compass directions
                        float[][] looks = {natural, {0, natural[1]}, {90, natural[1]}, {180, natural[1]}, {-90, natural[1]},
                                {natural[0], -80}, {natural[0], 80}};
                        for (float[] look : looks) {
                            BlockState predicted = predict(p, stack, hit, look[0], look[1]);
                            if (predicted == null) continue;
                            if (matches(predicted, e.state(), true)) {
                                Vec3d target = look == natural ? hitVec : eye.add(Vec3d.fromPolar(look[1], look[0]).multiply(3));
                                return new Placement(e, hit, target, usable, slot);
                            }
                        }
                        if (fallback == null) fallback = new Placement(e, hit, hitVec, usable, slot);
                    }
                }
            }
        } finally {
            p.setYaw(savedYaw);
            p.setPitch(savedPitch);
        }
        // a block that can't be oriented from here is still placed, unless the facing really matters
        // never place a directional block the wrong way round: Clear Wrong Blocks would just mine it again
        return orient ? null : fallback;
    }

    private BlockState predict(ClientPlayerEntity p, ItemStack stack, BlockHitResult hit, float yaw, float pitch) {
        p.setYaw(yaw);
        p.setPitch(pitch);
        try {
            ItemPlacementContext ctx = new ItemPlacementContext(p, Hand.MAIN_HAND, stack, hit);
            if (!ctx.canPlace()) return null;
            if (stack.getItem() instanceof BlockItem bi) {
                ctx = bi.getPlacementContext(ctx);
                if (ctx == null) return null;
            }
            return Block.getBlockFromItem(stack.getItem()).getPlacementState(ctx);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean usable(BlockState s) {
        Block b = s.getBlock();
        return s.hasBlockEntity() || b instanceof net.minecraft.block.DoorBlock || b instanceof net.minecraft.block.TrapdoorBlock
                || b instanceof net.minecraft.block.FenceGateBlock || b instanceof net.minecraft.block.ButtonBlock
                || b instanceof net.minecraft.block.LeverBlock || b instanceof net.minecraft.block.CraftingTableBlock
                || b instanceof net.minecraft.block.AnvilBlock || b instanceof net.minecraft.block.BedBlock
                || b instanceof net.minecraft.block.NoteBlock || b instanceof net.minecraft.block.RepeaterBlock
                || b instanceof net.minecraft.block.ComparatorBlock || b instanceof net.minecraft.block.CakeBlock
                || b instanceof net.minecraft.block.StonecutterBlock || b instanceof net.minecraft.block.GrindstoneBlock
                || b instanceof net.minecraft.block.LoomBlock || b instanceof net.minecraft.block.CartographyTableBlock
                || b instanceof net.minecraft.block.SmithingTableBlock || b instanceof net.minecraft.block.RespawnAnchorBlock;
    }

    private void place(ClientPlayerEntity p, Placement pl) {
        state = State.PLACING;
        task = pl.entry;
        move(false, false, false, false, false, false);
        float err = lookAt(pl.lookAt);
        aimedTicks = err < maxAimError.getFloat() ? aimedTicks + 1 : 0;
        // wait one tick on target so the server has our final rotation, and for sneak to register
        if (aimedTicks < 2 || placeTimer > 0 || (pl.sneak && sneakTicks < 2)) return;
        int slot = ensureInHotbar(p, pl.entry.state().getBlock().asItem(), true);
        if (slot < 0) {
            missing.add(pl.entry.state().getBlock());
            task = null;
            if (!skipMissing.isOn()) setEnabled(false);
            return;
        }
        p.getInventory().setSelectedSlot(slot);
        mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, pl.hit);
        p.swingHand(Hand.MAIN_HAND);
        countHit();
        placeTimer = placeDelay.getInt() + (randomDelay.getInt() > 0 ? ThreadLocalRandom.current().nextInt(randomDelay.getInt() + 1) : 0);
        aimedTicks = 0;
        sticky = null;
        placingTicks = 0;
        rescan = Math.min(rescan, 2);
    }

    // ------------------------------------------------------------------ mining
    private BlockPos pickBreak(ClientPlayerEntity p) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3d eye = p.getEyePos();
        for (BlockPos pos : toBreak) {
            if (unreachable.containsKey(pos)) continue;
            // top-down, so sand and gravel don't fall into the hole
            double d = eye.squaredDistanceTo(Vec3d.ofCenter(pos));
            double score = -pos.getY() * 50 + d;
            if (score < bestScore) {
                bestScore = score;
                best = pos;
            }
        }
        return best;
    }

    private void tickBreaking(ClientPlayerEntity p) {
        BlockPos pos = breaking;
        BlockState s = mc.world.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty()) {
            breaking = null;
            breakTimer = breakDelay.getInt();
            rescan = Math.min(rescan, 1);
            return;
        }
        Vec3d c = Vec3d.ofCenter(pos);
        if (p.getEyePos().distanceTo(c) > reach.get() + 0.4) {
            // walk over to it first
            walkTo(p, pos);
            return;
        }
        state = State.CLEARING;
        move(false, false, false, false, false, false);
        Direction face = Direction.getFacing(p.getEyePos().subtract(c));
        float err = lookAt(c.add(Vec3d.of(face.getVector()).multiply(0.45)));
        if (DEBUG && p.age % 20 == 0) dev.soulbound.SoulBound.LOGGER.info("[builder] break err={} face={} state={} cd={}", err, face, s, p.getAbilities().creativeMode);
        if (err > 12) return;
        selectTool(p, s);
        mc.interactionManager.updateBlockBreakingProgress(pos, face);
        p.swingHand(Hand.MAIN_HAND);
    }

    private void selectTool(ClientPlayerEntity p, BlockState s) {
        var inv = p.getInventory();
        int best = inv.getSelectedSlot();
        float bestSpeed = inv.getStack(best).getMiningSpeedMultiplier(s);
        for (int i = 0; i < 9; i++) {
            float sp = inv.getStack(i).getMiningSpeedMultiplier(s);
            if (sp > bestSpeed + 0.01f) {
                bestSpeed = sp;
                best = i;
            }
        }
        inv.setSelectedSlot(best);
    }

    // ------------------------------------------------------------------ scaffolding
    private Item scaffoldItem(ClientPlayerEntity p) {
        var inv = p.getInventory();
        List<Item> order = switch (scaffoldBlock.get()) {
            case "Dirt" -> List.of(Items.DIRT);
            case "Cobblestone" -> List.of(Items.COBBLESTONE);
            case "Netherrack" -> List.of(Items.NETHERRACK);
            default -> SCAFFOLD_ORDER;
        };
        Set<Item> needed = new HashSet<>();
        for (Schematic.Entry e : pending) needed.add(e.state().getBlock().asItem());
        for (Item it : order) {
            if (needed.contains(it) && order.size() > 1) continue;
            for (int i = 0; i < 36; i++) if (inv.getStack(i).isOf(it)) return it;
            if (creative(p)) return it;
        }
        return null;
    }

    /** Digs down out of a scaffold pillar before walking elsewhere. Returns true while doing so. */
    private boolean leaveScaffold(ClientPlayerEntity p) {
        if (!scaffold.isOn() || !removeScaffold.isOn()) return false;
        BlockPos below = p.getBlockPos().down();
        if (!scaffoldBlocks.contains(below) || !p.isOnGround()) return false;
        state = State.CLIMBING;
        move(false, false, false, false, false, false);
        BlockState s = mc.world.getBlockState(below);
        if (s.isAir()) {
            scaffoldBlocks.remove(below);
            return true;
        }
        if (lookAt(Vec3d.ofCenter(below).add(0, 0.5, 0)) > 15) return true;
        selectTool(p, s);
        mc.interactionManager.updateBlockBreakingProgress(below, Direction.UP);
        p.swingHand(Hand.MAIN_HAND);
        return true;
    }

    // ------------------------------------------------------------------ movement
    private void walkTo(ClientPlayerEntity p, BlockPos goal) {
        state = State.WALKING;
        BlockPos feet = p.getBlockPos();
        boolean onScaffold = scaffoldBlocks.contains(feet.down()) && p.isOnGround();
        if (path.isEmpty() || stuckTicks > 50 || !goal.equals(pathGoal)) {
            if (!p.isOnGround() && !p.isTouchingWater()) return; // plan from solid footing
            double r = reach.get() - 0.5;
            Item scaffoldWith = scaffold.isOn() ? scaffoldItem(p) : null;
            int ceiling = boxMax == null ? feet.getY() + maxPillar.getInt() : boxMax.getY() + 2;
            path = Pathfinder.find(mc.world, feet, pos -> {
                if (pos.equals(goal) || pos.up().equals(goal)) return false;
                Vec3d eye = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.62, pos.getZ() + 0.5);
                return eye.distanceTo(Vec3d.ofCenter(goal)) <= r;
            }, goal, avoidFalls.isOn() ? 3 : 12, scaffoldWith == null ? null
                    : pos -> pos.getY() < ceiling && !plan.containsKey(pos) && !plan.containsKey(pos.up()) && !unreachable.containsKey(pos));
            pathGoal = goal;
            pathIndex = path.size() > 1 ? 1 : 0; // node 0 is where we already stand
            stuckTicks = 0;
            bestToNode = Double.MAX_VALUE;
            if (path.isEmpty() && onScaffold && removeScaffold.isOn()) {
                // stranded on our own pillar: dig back down and plan again from lower
                path = List.of();
                leaveScaffold(p);
                return;
            }
            if (++repaths > 5 || path.isEmpty()) {
                // no way to stand in reach, even pillaring: come back to it later
                repaths = 0;
                path = List.of();
                unreachable.put(goal, 300);
                if (breaking != null && breaking.equals(goal)) breaking = null;
                task = null;
                release();
                return;
            }
        }
        // leaving a pillar downward (or nowhere left to go): dig it back down rather than leave it standing
        if (onScaffold && (pathIndex >= path.size() || path.get(pathIndex).getY() < feet.getY()) && leaveScaffold(p)) return;
        if (pathIndex >= path.size()) {
            path = List.of();
            move(false, false, false, false, false, false);
            return;
        }
        BlockPos node = path.get(pathIndex);
        BlockPos prev = path.get(Math.max(0, pathIndex - 1));
        // a pillar step: the next node is straight above the last and has nothing to stand on yet
        if (node.equals(prev.up()) && mc.world.getBlockState(prev).isReplaceable()) {
            pillarStep(p, prev);
            return;
        }
        Vec3d c = Vec3d.ofBottomCenter(node);
        double horiz = Math.hypot(c.x - p.getX(), c.z - p.getZ());
        boolean nextCloser = pathIndex + 1 < path.size()
                && Vec3d.ofBottomCenter(path.get(pathIndex + 1)).squaredDistanceTo(p.getX(), node.getY(), p.getZ()) < horiz * horiz;
        if ((horiz < (pathIndex == path.size() - 1 ? 0.3 : 0.5) || nextCloser) && Math.abs(p.getY() - node.getY()) < 1.2) {
            pathIndex++;
            stuckTicks = 0;
            bestToNode = Double.MAX_VALUE;
            if (pathIndex >= path.size()) {
                move(false, false, false, false, false, false);
                return;
            }
            prev = node;
            node = path.get(pathIndex);
            if (node.equals(prev.up()) && mc.world.getBlockState(prev).isReplaceable()) {
                pillarStep(p, prev);
                return;
            }
            c = Vec3d.ofBottomCenter(node);
        }
        // stuck = no real progress toward the current waypoint (jumping on the spot doesn't count)
        double toNode = new Vec3d(p.getX(), p.getY(), p.getZ()).distanceTo(c);
        if (toNode < bestToNode - 0.15) {
            bestToNode = toNode;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }

        BlockPos ahead = horiz < 1.2 ? path.get(Math.min(path.size() - 1, pathIndex + 1)) : node;
        Vec3d look = Vec3d.ofBottomCenter(ahead).add(0, 1.5, 0);
        if (ahead.equals(node) || Vec3d.ofBottomCenter(ahead).squaredDistanceTo(p.getX(), ahead.getY(), p.getZ()) < 0.3) {
            Vec3d dir = new Vec3d(c.x - p.getX(), 0, c.z - p.getZ());
            if (dir.lengthSquared() > 1e-4) look = p.getEyePos().add(dir.normalize().multiply(4)).add(0, -0.3, 0);
        }
        if (lookAround.isOn()) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            if (glanceTicks <= 0 && r.nextFloat() < 0.01f) {
                glance = new Vec3d(r.nextDouble(-3, 3), r.nextDouble(-1, 1.5), r.nextDouble(-3, 3));
                glanceTicks = 12 + r.nextInt(12);
            }
            if (glanceTicks > 0) {
                glanceTicks--;
                look = look.add(glance);
            }
        }
        lookAt(look);

        // walk toward the waypoint whichever way the head is turned: pick the key combination closest to it
        float wantYaw = (float) Math.toDegrees(Math.atan2(c.z - p.getZ(), c.x - p.getX())) - 90f;
        float rel = MathHelper.wrapDegrees(wantYaw - p.getYaw());
        boolean forward = Math.abs(rel) < 67.5f, back = Math.abs(rel) > 112.5f;
        boolean right = rel > 22.5f && rel < 157.5f, left = rel < -22.5f && rel > -157.5f;
        if (horiz < 0.15) forward = back = left = right = false;
        boolean jump = p.isOnGround() && (node.getY() > p.getBlockPos().getY() || (p.horizontalCollision && horiz > 0.3));
        boolean run = sprint.isOn() && forward && !left && !right && path.size() - pathIndex > 4 && !(stuckTicks > 10);
        move(forward, back, left, right, jump, run);
    }

    /** One step of pillaring: stand centred on {@code at}, jump, and place a scaffold block there under our feet. */
    private void pillarStep(ClientPlayerEntity p, BlockPos at) {
        state = State.CLIMBING;
        Item item = scaffoldItem(p);
        int slot = item == null ? -1 : ensureInHotbar(p, item, true);
        if (slot < 0) {
            path = List.of();
            return;
        }
        Vec3d centre = Vec3d.ofBottomCenter(at);
        double ox = centre.x - p.getX(), oz = centre.z - p.getZ();
        lookAt(Vec3d.ofCenter(at.down()).add(ox * 0.3, 0.5, oz * 0.3));
        // drift to the centre so the block lands under us
        float wantYaw = (float) Math.toDegrees(Math.atan2(oz, ox)) - 90f;
        float rel = MathHelper.wrapDegrees(wantYaw - p.getYaw());
        boolean off = Math.hypot(ox, oz) > 0.2;
        boolean forward = off && Math.abs(rel) < 67.5f, back = off && Math.abs(rel) > 112.5f;
        boolean right = off && rel > 22.5f && rel < 157.5f, left = off && rel < -22.5f && rel > -157.5f;
        boolean jump = p.isOnGround() && p.getPitch() > 70 && Math.hypot(ox, oz) < 0.3;
        move(forward, back, left, right, jump, false);
        p.getInventory().setSelectedSlot(slot);
        if (!p.isOnGround() && p.getY() >= at.getY() + 1.0 && placeTimer <= 0 && !mc.world.getBlockState(at.down()).isReplaceable()) {
            if (creative(p) && p.getInventory().getStack(slot).isEmpty()) fetchCreative(p, item, slot);
            BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(at.down()).add(0, 0.5, 0), Direction.UP, at.down(), false);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
            p.swingHand(Hand.MAIN_HAND);
            scaffoldBlocks.add(at.toImmutable());
            placeTimer = 1;
            saveState();
        }
        stuckTicks = 0;
    }

    // ------------------------------------------------------------------ inventory
    /** Puts {@code item} in the hotbar (moving it from the inventory, or from the creative menu) and returns its slot, or -1. */
    private int ensureInHotbar(ClientPlayerEntity p, Item item, boolean act) {
        if (item == Items.AIR) return -1;
        int slot = hotbarSlot(p, item);
        if (slot >= 0) return slot;
        var inv = p.getInventory();
        int target = inv.getSelectedSlot();
        for (int h = 0; h < 9; h++) if (inv.getStack(h).isEmpty()) { target = h; break; }
        if (!act) return -1;
        if (pauseInventory.isOn()) {
            for (int i = 9; i < 36; i++) {
                if (!inv.getStack(i).isOf(item)) continue;
                mc.interactionManager.clickSlot(p.playerScreenHandler.syncId, i, target, SlotActionType.SWAP, p);
                return target;
            }
        }
        if (creative(p)) {
            fetchCreative(p, item, target);
            return target;
        }
        return -1;
    }

    private void fetchCreative(ClientPlayerEntity p, Item item, int slot) {
        ItemStack stack = new ItemStack(item, item.getMaxCount());
        p.getInventory().setStack(slot, stack);
        mc.interactionManager.clickCreativeStack(stack, 36 + slot);
    }

    // ------------------------------------------------------------------ self defence
    private boolean defendSelf(ClientPlayerEntity p) {
        net.minecraft.entity.LivingEntity threat = null;
        double best = defendRange.get() * defendRange.get();
        for (var e : mc.world.getOtherEntities(p, p.getBoundingBox().expand(defendRange.get()), en -> en instanceof net.minecraft.entity.mob.Monster && en.isAlive())) {
            double d = p.squaredDistanceTo(e);
            if (d < best) { best = d; threat = (net.minecraft.entity.LivingEntity) e; }
        }
        if (threat == null) {
            target = null;
            return false;
        }
        target = threat;
        state = State.ENGAGING;
        int weapon = -1;
        double bestDmg = 0;
        for (int i = 0; i < 9; i++) {
            var st = p.getInventory().getStack(i);
            if (!st.isIn(net.minecraft.registry.tag.ItemTags.SWORDS) && !st.isIn(net.minecraft.registry.tag.ItemTags.AXES)) continue;
            double[] dmg = {0};
            st.applyAttributeModifiers(net.minecraft.entity.EquipmentSlot.MAINHAND, (a, m) -> { if (a.equals(net.minecraft.entity.attribute.EntityAttributes.ATTACK_DAMAGE)) dmg[0] += m.value(); });
            if (dmg[0] > bestDmg) { bestDmg = dmg[0]; weapon = i; }
        }
        if (weapon >= 0) p.getInventory().setSelectedSlot(weapon);
        float err = lookAt(aimPoint(p, threat, "Chest"));
        double reachTo = reach(p, threat);
        moveDir(threat.getEntityPos().subtract(p.getEntityPos()).multiply(reachTo > 2.8 ? 1 : reachTo < 1.8 ? -1 : 0), false, false, true);
        if (reachTo <= 3.0 && err < 25 && p.getAttackCooldownProgress(0.5f) >= 0.9f) {
            mc.interactionManager.attackEntity(p, threat);
            p.swingHand(Hand.MAIN_HAND);
        }
        return true;
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

    /** Short status line for the schematic screen. */
    public Text status() {
        if (schematic == null) return Text.literal("No schematic loaded");
        int total = plan.size(), left = pending.size();
        String extra = (missing.isEmpty() ? "" : "  ·  " + missing.size() + " missing types") + (toBreak.isEmpty() ? "" : "  ·  " + toBreak.size() + " to clear");
        return Text.literal((total - left) + " / " + total + " placed" + extra);
    }
}
