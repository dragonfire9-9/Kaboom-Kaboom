package com.deadcraft.world;

import com.deadcraft.DeadCraft;
import com.deadcraft.block.GravestoneBlock;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.registry.ModBlocks;
import com.deadcraft.registry.ModEntities;
import com.deadcraft.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.ChunkPos;

/**
 * Builds one {@link Landmark.Design}. Every design is written in local coordinates around its
 * origin (x, z centre; y = the first air block above the ground) and is clipped to the chunk being
 * generated, so a design can span several chunks. Ruins are decayed with a deterministic hash, so
 * each copy crumbles differently.
 */
public class LandmarkPiece extends StructurePiece {
    private final Landmark.Design design;
    private final long seed;
    private final int ox, oy, oz;

    // per-postProcess state
    private WorldGenLevel level;
    private BoundingBox clip;
    private RandomSource random;

    public LandmarkPiece(Landmark.Design design, long seed, int ox, int oy, int oz, BoundingBox box) {
        super(ModWorldgen.LANDMARK_PIECE.get(), 0, box);
        this.design = design;
        this.seed = seed;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        setOrientation(null);
    }

    public LandmarkPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModWorldgen.LANDMARK_PIECE.get(), tag);
        this.design = Landmark.Design.values()[Math.min(tag.getInt("Design"), Landmark.Design.values().length - 1)];
        this.seed = tag.getLong("Seed");
        this.ox = tag.getInt("OX");
        this.oy = tag.getInt("OY");
        this.oz = tag.getInt("OZ");
        setOrientation(null);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("Design", design.ordinal());
        tag.putLong("Seed", seed);
        tag.putInt("OX", ox);
        tag.putInt("OY", oy);
        tag.putInt("OZ", oz);
    }

    // ================================================================================================
    // Building helpers (local coordinates)
    // ================================================================================================
    private double hash(int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }

    private static BlockState s(Block b) { return b.defaultBlockState(); }

    private BlockState pick(int x, int y, int z, BlockState... options) {
        return options[(int) (hash(x, y, z) * options.length) % options.length];
    }

    private boolean inside(int x, int y, int z) {
        return clip.isInside(new BlockPos(ox + x, oy + y, oz + z));
    }

    private void b(int x, int y, int z, BlockState st) {
        BlockPos p = new BlockPos(ox + x, oy + y, oz + z);
        if (clip.isInside(p)) level.setBlock(p, st, 2);
    }

    private BlockState at(int x, int y, int z) {
        BlockPos p = new BlockPos(ox + x, oy + y, oz + z);
        return clip.isInside(p) ? level.getBlockState(p) : s(Blocks.STONE);
    }

    private void f(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, st);
    }

    /** Fill with a mix of materials. */
    private void fm(int x1, int y1, int z1, int x2, int y2, int z2, BlockState... mix) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) b(x, y, z, pick(x, y, z, mix));
    }

    private void air(int x1, int y1, int z1, int x2, int y2, int z2) {
        f(x1, y1, z1, x2, y2, z2, s(Blocks.AIR));
    }

    /** Hollow box: walls, floor and (optionally) roof of {@code mix}, air inside. Walls crumble with {@code decay}. */
    private void room(int x1, int y1, int z1, int x2, int y2, int z2, boolean roof, double decay, BlockState... mix) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    boolean shell = x == x1 || x == x2 || z == z1 || z == z2 || y == y1 || (roof && y == y2);
                    if (!shell) { b(x, y, z, s(Blocks.AIR)); continue; }
                    // crumble more toward the top
                    double t = (y - y1) / (double) Math.max(1, y2 - y1);
                    if (y > y1 && hash(x, y, z) < decay * t * t * 1.6) { b(x, y, z, s(Blocks.AIR)); continue; }
                    b(x, y, z, pick(x, y, z, mix));
                }
    }

    /** Vertical cylinder; hollow leaves walls of thickness 1. */
    private void cyl(int cx, int cz, int y1, int y2, double r, boolean hollow, double decay, BlockState... mix) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++)
            for (int z = -ri; z <= ri; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.3) continue;
                boolean wall = d > r - 1.0;
                for (int y = y1; y <= y2; y++) {
                    if (hollow && !wall && y != y1) { b(cx + x, y, cz + z, s(Blocks.AIR)); continue; }
                    double t = (y - y1) / (double) Math.max(1, y2 - y1);
                    if (hash(cx + x, y, cz + z) < decay * t * t * 1.6) { b(cx + x, y, cz + z, s(Blocks.AIR)); continue; }
                    b(cx + x, y, cz + z, pick(cx + x, y, cz + z, mix));
                }
            }
    }

    /** Hemisphere (dome) shell of radius r centred at (cx, cy, cz). */
    private void dome(int cx, int cy, int cz, double r, double decay, BlockState... mix) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++)
            for (int y = 0; y <= ri; y++)
                for (int z = -ri; z <= ri; z++) {
                    double d = Math.sqrt(x * x + y * y + z * z);
                    if (d > r || d < r - 1.2) continue;
                    if (hash(cx + x, cy + y, cz + z) < decay) continue;
                    b(cx + x, cy + y, cz + z, pick(cx + x, cy + y, cz + z, mix));
                }
    }

    private void sphere(int cx, int cy, int cz, double r, BlockState st) {
        int ri = (int) Math.ceil(r);
        for (int x = -ri; x <= ri; x++)
            for (int y = -ri; y <= ri; y++)
                for (int z = -ri; z <= ri; z++)
                    if (x * x + y * y + z * z <= r * r) b(cx + x, cy + y, cz + z, st);
    }

    private void line(int x1, int y1, int z1, int x2, int y2, int z2, BlockState st) {
        int n = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            b((int) Math.round(Mth.lerp(t, x1, x2)), (int) Math.round(Mth.lerp(t, y1, y2)), (int) Math.round(Mth.lerp(t, z1, z2)), st);
        }
    }

    /** Fills air and fluid under a footprint down to solid ground, so nothing floats. */
    private void footing(int x1, int z1, int x2, int z2, int maxDepth, BlockState... mix) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = -1; y >= -maxDepth; y--) {
                    BlockState st = at(x, y, z);
                    if (!st.isAir() && st.getFluidState().isEmpty() && !st.canBeReplaced()) break;
                    b(x, y, z, pick(x, y, z, mix));
                }
    }

    /** Clears everything above a footprint (trees, overhangs) up to {@code h}. */
    private void clearAbove(int x1, int z1, int x2, int z2, int h) {
        air(x1, 0, z1, x2, h, z2);
    }

    private int groundAt(int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, ox + x, oz + z) - oy;
    }

    private void chest(int x, int y, int z, String table) {
        BlockPos p = new BlockPos(ox + x, oy + y, oz + z);
        if (!clip.isInside(p)) return;
        createChest(level, clip, random, p, ResourceKey.create(Registries.LOOT_TABLE, DeadCraft.id("chests/" + table)), null);
    }

    private void barrel(int x, int y, int z, String table) {
        BlockPos p = new BlockPos(ox + x, oy + y, oz + z);
        if (!clip.isInside(p)) return;
        level.setBlock(p, s(Blocks.BARREL), 2);
        if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity be) {
            be.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, DeadCraft.id("chests/" + table)), random.nextLong());
        }
    }

    private void spawner(int x, int y, int z, DeadMob mob) {
        BlockPos p = new BlockPos(ox + x, oy + y, oz + z);
        if (!clip.isInside(p)) return;
        level.setBlock(p, s(Blocks.SPAWNER), 2);
        if (level.getBlockEntity(p) instanceof SpawnerBlockEntity sp) sp.setEntityId(ModEntities.type(mob), random);
    }

    private void mob(int x, int y, int z, DeadMob type) {
        mob(x, y, z, ModEntities.type(type));
    }

    private void mob(int x, int y, int z, EntityType<?> type) {
        BlockPos pos = new BlockPos(ox + x, oy + y, oz + z);
        if (!clip.isInside(pos)) return;
        var entity = type.create(level.getLevel());
        if (!(entity instanceof Mob m)) return;
        m.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, (float) (hash(x, y, z) * 360), 0);
        m.setPersistenceRequired();
        m.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        level.addFreshEntityWithPassengers(m);
    }

    private void candles(int x, int y, int z, int n) {
        b(x, y, z, s(Blocks.BLACK_CANDLE).setValue(CandleBlock.CANDLES, Mth.clamp(n, 1, 4)).setValue(CandleBlock.LIT, true));
    }

    private void skull(int x, int y, int z) {
        b(x, y, z, s(Blocks.SKELETON_SKULL).setValue(SkullBlock.ROTATION, (int) (hash(x, y, z) * 16)));
    }

    private void hangingLantern(int x, int y, int z, boolean soul) {
        b(x, y, z, s(soul ? Blocks.SOUL_LANTERN : Blocks.LANTERN).setValue(LanternBlock.HANGING, true));
    }

    private void chain(int x, int yTop, int yBottom, int z) {
        for (int y = yBottom; y <= yTop; y++) b(x, y, z, s(Blocks.CHAIN));
    }

    private void ladder(int x, int y1, int y2, int z, Direction facing) {
        for (int y = y1; y <= y2; y++) b(x, y, z, s(Blocks.LADDER).setValue(LadderBlock.FACING, facing));
    }

    private void gibbet(int x, int yTop, int z, int drop) {
        chain(x, yTop, yTop - drop + 1, z);
        int cy = yTop - drop;
        b(x, cy, z, s(Blocks.IRON_BARS));
        b(x, cy - 1, z, s(Blocks.IRON_BARS));
        if (hash(x, cy, z) < 0.6) skull(x, cy - 2, z);
        else b(x, cy - 2, z, s(Blocks.IRON_BARS));
    }

    private void cobwebs(int x1, int y1, int z1, int x2, int y2, int z2, double p) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    if (at(x, y, z).isAir() && hash(x, y + 77, z) < p) b(x, y, z, s(Blocks.COBWEB));
    }

    private void bloodSplatter(int x1, int z1, int x2, int z2, double p) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (hash(x, 91, z) >= p) continue;
                int y = groundAt(x, z);
                if (y > -4 && y < 6 && at(x, y, z).isAir() && at(x, y - 1, z).isSolid())
                    b(x, y, z, s(ModBlocks.BLOOD_PUDDLE.get()));
            }
    }

    // palettes
    private static final BlockState[] STONE_RUIN = {s(Blocks.STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS), s(Blocks.MOSSY_STONE_BRICKS),
            s(Blocks.STONE_BRICKS), s(Blocks.COBBLESTONE)};
    private static final BlockState[] DEEP_RUIN = {s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS), s(Blocks.DEEPSLATE_TILES),
            s(Blocks.POLISHED_DEEPSLATE)};
    private static final BlockState[] BLACKSTONE = {s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS),
            s(Blocks.BLACKSTONE), s(Blocks.POLISHED_BLACKSTONE_BRICKS)};
    private static final BlockState[] BONE = {s(Blocks.BONE_BLOCK)};

    private BlockState[] sky() {
        return new BlockState[]{s(ModBlocks.SKYSTONE.get()), s(Blocks.CALCITE), s(Blocks.SMOOTH_QUARTZ), s(ModBlocks.SKYSTONE.get())};
    }

    private BlockState[] voidStone() {
        return new BlockState[]{s(ModBlocks.VOIDROCK.get()), s(Blocks.OBSIDIAN), s(Blocks.POLISHED_BLACKSTONE), s(ModBlocks.VOIDROCK.get())};
    }

    private BlockState[] charnel() {
        return new BlockState[]{s(ModBlocks.CHARNEL_STONE.get()), s(Blocks.NETHER_BRICKS), s(Blocks.RED_NETHER_BRICKS), s(ModBlocks.CHARNEL_STONE.get())};
    }

    // ================================================================================================
    // Dispatch
    // ================================================================================================
    @Override
    public void postProcess(WorldGenLevel level, StructureManager sm, ChunkGenerator gen, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        this.level = level;
        this.clip = chunkBox;
        this.random = random;
        switch (design) {
            case PLAGUE_CHAPEL -> plagueChapel();
            case HANGING_TREE -> hangingTree();
            case BANDIT_CAMP -> banditCamp();
            case HAG_HOVEL -> hagHovel();
            case SUNKEN_CRYPT -> sunkenCrypt();
            case BLOODIED_FARM -> bloodiedFarm();
            case BONE_PIT -> bonePit();
            case RITUAL_CIRCLE -> ritualCircle();
            case RUINED_WATCHTOWER -> watchtower();
            case FALLEN_STAR -> fallenStar();
            case FLESH_SHRINE -> fleshShrine();
            case IMPALER_FIELD -> impalerField();
            case DEMON_FORGE -> demonForge();
            case CAGE_GALLOWS -> cageGallows();
            case SKY_TEMPLE -> skyTemple();
            case BELL_TOWER -> bellTower();
            case WRECKED_AIRSHIP -> airship();
            case OBSERVATORY -> observatory();
            case CATHEDRAL_RUIN -> cathedral();
            case MESA_OUTPOST -> mesaOutpost();
            case GALLOWS_SQUARE -> gallowsSquare();
            case ABANDONED_MINE -> abandonedMine();
            case VOID_OBELISK -> voidObelisk();
            case STORM_SPIRE -> stormSpire();
            case SHATTERED_SANCTUM -> shatteredSanctum();
        }
        this.level = null;
    }

    // ================================================================================================
    // Overworld
    // ================================================================================================
    /** A plague chapel: a crumbling nave of pews, a broken steeple with its bell, an altar of candles. */
    private void plagueChapel() {
        footing(-5, -9, 5, 9, 12, STONE_RUIN);
        clearAbove(-5, -9, 5, 9, 20);
        room(-4, -1, -6, 4, 7, 9, false, 0.45, STONE_RUIN);
        fm(-3, -1, -5, 3, -1, 8, s(Blocks.SPRUCE_PLANKS), s(Blocks.DARK_OAK_PLANKS), s(Blocks.COBBLESTONE));
        // pointed windows with bars
        for (int z = -3; z <= 7; z += 3) {
            for (int x : new int[]{-4, 4}) f(x, 2, z, x, 4, z, s(Blocks.IRON_BARS));
        }
        // a collapsed roof: a few rafters remain
        for (int z = -6; z <= 9; z += 2) {
            if (hash(0, 9, z) < 0.55) for (int x = -4; x <= 4; x++) b(x, 8 - Math.abs(x) / 2, z, s(Blocks.DARK_OAK_SLAB));
        }
        // pews
        for (int z = -3; z <= 5; z += 2) {
            for (int x : new int[]{-3, -2, 2, 3}) if (hash(x, 1, z) < 0.85) b(x, 0, z, s(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH));
        }
        // altar
        f(-2, 0, 7, 2, 0, 8, s(Blocks.POLISHED_DEEPSLATE));
        candles(-2, 1, 7, 3); candles(2, 1, 7, 4); candles(0, 1, 8, 2);
        b(0, 1, 7, s(Blocks.SKELETON_SKULL));
        chest(0, 0, 6, "chapel");
        air(-1, 0, -6, 1, 3, -6);
        // steeple
        room(-2, -1, -9, 2, 16, -5, false, 0.15, STONE_RUIN);
        for (int y = 17; y <= 20; y++) {
            int w = 20 - y;
            f(-w / 2 - 1 + 1, y, -7 - w / 2, w / 2, y, -7 + w / 2, s(Blocks.DEEPSLATE_TILES));
        }
        b(0, 13, -7, s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        b(0, 14, -7, s(Blocks.DARK_OAK_FENCE));
        air(-1, 0, -9, 1, 2, -9);
        cobwebs(-3, 3, -5, 3, 7, 8, 0.05);
        bloodSplatter(-3, -5, 3, 8, 0.05);
        mob(0, 0, 2, DeadMob.GHOST);
        mob(-2, 0, -2, DeadMob.GHOST);
    }

    /** A colossal hanging tree: gibbet cages and chains on every branch, bones heaped at its roots. */
    private void hangingTree() {
        clearAbove(-3, -3, 3, 3, 17);
        BlockState log = s(ModBlocks.CHARRED_LOG.get());
        f(-1, -2, -1, 0, 12, 0, log);
        for (int[] br : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {1, 0, 1}, {-1, 0, -1}}) {
            int len = 5 + (int) (hash(br[0], 3, br[2]) * 3);
            int y0 = 7 + (int) (hash(br[0], 4, br[2]) * 5);
            int ex = br[0] * len, ez = br[2] * len;
            line(0, y0, 0, ex, y0 + 3, ez, log.setValue(RotatedPillarBlock.AXIS, br[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
            gibbet(ex, y0 + 2, ez, 3 + (int) (hash(ex, 5, ez) * 3));
            if (len > 5) gibbet(ex / 2, y0 + 1, ez / 2, 2);
        }
        line(0, 12, 0, 2, 16, 1, log);
        line(0, 12, 0, -2, 15, -1, log);
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                int y = groundAt(x, z);
                if (y < -3 || y > 3) continue;
                double d = Math.sqrt(x * x + z * z);
                if (d < 6.5) b(x, y - 1, z, pick(x, 0, z, s(Blocks.SOUL_SOIL), s(Blocks.COARSE_DIRT), s(Blocks.PODZOL)));
                if (d < 5 && hash(x, 6, z) < 0.12 && at(x, y, z).isAir()) b(x, y, z, hash(x, 7, z) < 0.5 ? s(Blocks.BONE_BLOCK) : s(Blocks.SKELETON_SKULL));
            }
        chest(1, 0, 1, "hanging_tree");
        mob(3, 0, 3, DeadMob.THE_FORGOTTEN);
    }

    /** A bandit camp: a palisade, tents, a campfire, hay and loot barrels, and the bandits themselves. */
    private void banditCamp() {
        for (int a = 0; a < 72; a++) {
            double t = a / 72.0 * Math.PI * 2;
            int x = (int) Math.round(Math.cos(t) * 10), z = (int) Math.round(Math.sin(t) * 10);
            if (z > 8 && Math.abs(x) <= 1) continue; // gate
            int y = groundAt(x, z);
            if (y < -5 || y > 5) continue;
            int h = 3 + (int) (hash(x, 1, z) * 2);
            f(x, y, z, x, y + h - 1, z, s(Blocks.SPRUCE_LOG));
            if (hash(x, 2, z) < 0.5) b(x, y + h, z, s(Blocks.SPRUCE_FENCE));
        }
        footing(-8, -8, 8, 8, 4, s(Blocks.COARSE_DIRT));
        clearAbove(-8, -8, 8, 8, 6);
        fm(-8, -1, -8, 8, -1, 8, s(Blocks.COARSE_DIRT), s(Blocks.DIRT_PATH), s(Blocks.PODZOL), s(Blocks.COARSE_DIRT));
        // tents: A-frames of wool
        int[][] tents = {{-5, -4}, {4, -5}, {-4, 4}};
        BlockState[] wool = {s(Blocks.BROWN_WOOL), s(Blocks.RED_WOOL), s(Blocks.GRAY_WOOL)};
        for (int i = 0; i < tents.length; i++) {
            int tx = tents[i][0], tz = tents[i][1];
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 0; dy <= 2; dy++) {
                    b(tx - 2 + dy, dy, tz + dz, wool[i]);
                    b(tx + 2 - dy, dy, tz + dz, wool[i]);
                }
            b(tx, 3, tz - 2, s(Blocks.SPRUCE_FENCE));
            b(tx, 3, tz + 2, s(Blocks.SPRUCE_FENCE));
            b(tx, 0, tz, s(Blocks.WHITE_CARPET));
            barrel(tx, 0, tz + 1, "bandit_stash");
        }
        b(0, 0, 0, s(Blocks.CAMPFIRE));
        for (int[] c : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) b(c[0], 0, c[1], s(Blocks.SPRUCE_SLAB));
        f(5, 0, 3, 6, 1, 4, s(Blocks.HAY_BLOCK));
        chest(6, 0, 6, "bandit_stash");
        b(-7, 0, 0, s(Blocks.TARGET));
        mob(2, 0, 2, DeadMob.BANDIT);
        mob(-2, 0, -3, DeadMob.BANDIT);
        mob(3, 0, -2, DeadMob.BANDIT);
        mob(0, 0, 5, DeadMob.BANDIT_CAPTAIN);
    }

    /** A hag's hovel on stilts above the mud, cauldrons bubbling, skulls and candles everywhere. */
    private void hagHovel() {
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            f(c[0], -10, c[1], c[0], 3, c[1], s(Blocks.SPRUCE_LOG));
        }
        f(-4, 4, -4, 4, 4, 4, s(Blocks.SPRUCE_PLANKS));
        room(-3, 4, -3, 3, 9, 3, true, 0.0, s(Blocks.SPRUCE_PLANKS), s(Blocks.DARK_OAK_PLANKS), s(Blocks.MUD_BRICKS));
        for (int y = 10; y <= 12; y++) {
            int w = 12 - y + 1;
            f(-w, y, -w, w, y, w, s(Blocks.DARK_OAK_SLAB));
        }
        air(0, 5, -3, 0, 6, -3);
        f(-1, 6, 3, 1, 6, 3, s(Blocks.PURPLE_STAINED_GLASS_PANE));
        // steps up
        for (int i = 0; i < 4; i++) b(0, i, -5 - (3 - i), s(Blocks.SPRUCE_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH));
        f(0, -1, -8, 0, -1, -5, s(Blocks.SPRUCE_PLANKS));
        b(-2, 5, 2, s(Blocks.WATER_CAULDRON).setValue(LayeredCauldronBlock.LEVEL, 3));
        b(2, 5, 2, s(Blocks.BREWING_STAND));
        b(2, 5, -2, s(Blocks.CRAFTING_TABLE));
        candles(-2, 5, -2, 4);
        candles(1, 5, 1, 2);
        skull(-2, 6, 2);
        hangingLantern(0, 8, 0, true);
        chest(-2, 5, 0, "hag");
        for (int[] c : new int[][]{{-5, 1}, {5, -1}}) chain(c[0], 4, 1, c[1]);
        mob(0, 5, 0, DeadMob.WRETCHED);
        spawner(0, 3, 0, DeadMob.WRETCHED);
    }

    /** A sunken crypt: a mossy entrance arch, stairs down into a pillared vault of sarcophagi. */
    private void sunkenCrypt() {
        footing(-3, -8, 3, -4, 6, STONE_RUIN);
        // entrance arch
        f(-3, 0, -6, -3, 4, -6, s(Blocks.MOSSY_STONE_BRICKS));
        f(3, 0, -6, 3, 4, -6, s(Blocks.MOSSY_STONE_BRICKS));
        f(-3, 5, -6, 3, 5, -6, s(Blocks.STONE_BRICK_SLAB));
        f(-2, 4, -6, 2, 4, -6, s(Blocks.CHISELED_STONE_BRICKS));
        b(0, 3, -6, s(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
        // the stair shaft (descending south)
        for (int i = 0; i <= 8; i++) {
            int y = -1 - i, z = -6 + i;
            air(-1, y, z, 1, y + 4, z);
            f(-1, y - 1, z, 1, y - 1, z, s(Blocks.STONE_BRICK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
            f(-2, y - 1, z, -2, y + 4, z, s(Blocks.STONE_BRICKS));
            f(2, y - 1, z, 2, y + 4, z, s(Blocks.STONE_BRICKS));
            f(-1, y + 5, z, 1, y + 5, z, s(Blocks.STONE_BRICKS));
        }
        // the vault
        room(-5, -12, 2, 5, -5, 8, true, 0.0, DEEP_RUIN);
        for (int[] p : new int[][]{{-3, 4}, {3, 4}, {-3, 7}, {3, 7}}) f(p[0], -11, p[1], p[0], -6, p[1], s(Blocks.POLISHED_DEEPSLATE));
        for (int x : new int[]{-4, 4}) {
            f(x, -11, 3, x, -11, 6, s(Blocks.POLISHED_DEEPSLATE_SLAB));
        }
        f(-1, -11, 7, 1, -10, 7, s(Blocks.CHISELED_DEEPSLATE));
        candles(-1, -9, 7, 3); candles(1, -9, 7, 3);
        chest(0, -11, 6, "tomb");
        chest(-4, -10, 7, "tomb");
        spawner(0, -11, 4, DeadMob.SEVERED);
        cobwebs(-4, -11, 3, 4, -6, 7, 0.08);
        hangingLantern(0, -6, 5, true);
    }

    /** A bloodied farm: the farmhouse with its roof caved in, a red barn, a pen of blood and a scarecrow. */
    private void bloodiedFarm() {
        footing(-11, -9, 11, 9, 6, s(Blocks.DIRT));
        clearAbove(-11, -9, 11, 9, 9);
        fm(-11, -1, -9, 11, -1, 9, s(Blocks.COARSE_DIRT), s(Blocks.DIRT), s(Blocks.ROOTED_DIRT), s(Blocks.DIRT_PATH));
        // farmhouse
        room(-10, -1, -8, -3, 5, -2, false, 0.3, s(Blocks.OAK_PLANKS), s(Blocks.STRIPPED_OAK_LOG), s(Blocks.OAK_PLANKS));
        f(-10, 0, -8, -10, 5, -8, s(Blocks.OAK_LOG)); f(-3, 0, -8, -3, 5, -8, s(Blocks.OAK_LOG));
        f(-10, 0, -2, -10, 5, -2, s(Blocks.OAK_LOG)); f(-3, 0, -2, -3, 5, -2, s(Blocks.OAK_LOG));
        for (int x = -10; x <= -3; x++) if (hash(x, 6, 0) < 0.5) f(x, 6, -8, x, 6, -2, s(Blocks.SPRUCE_SLAB));
        air(-7, 0, -2, -6, 1, -2);
        f(-9, 2, -8, -8, 2, -8, s(Blocks.GLASS_PANE));
        b(-9, 0, -7, s(Blocks.RED_BED));
        chest(-4, 0, -7, "farm");
        bloodSplatter(-9, -7, -4, -3, 0.25);
        // red barn
        room(2, -1, -8, 10, 7, 0, false, 0.25, s(Blocks.RED_TERRACOTTA), s(Blocks.STRIPPED_SPRUCE_LOG), s(Blocks.RED_TERRACOTTA));
        for (int y = 8; y <= 11; y++) f(2 + (y - 8), y, -8, 10 - (y - 8), y, 0, s(Blocks.DARK_OAK_PLANKS));
        air(5, 0, 0, 7, 3, 0);
        fm(3, 0, -7, 9, 1, -5, s(Blocks.HAY_BLOCK), s(Blocks.HAY_BLOCK), s(Blocks.AIR));
        // the pen
        for (int x = -9; x <= 0; x++) { b(x, 0, 2, s(Blocks.OAK_FENCE)); b(x, 0, 8, s(Blocks.OAK_FENCE)); }
        for (int z = 2; z <= 8; z++) { b(-9, 0, z, s(Blocks.OAK_FENCE)); b(0, 0, z, s(Blocks.OAK_FENCE)); }
        bloodSplatter(-8, 3, -1, 7, 0.4);
        // scarecrow
        f(5, 0, 5, 5, 2, 5, s(Blocks.SPRUCE_FENCE));
        f(4, 2, 5, 6, 2, 5, s(Blocks.SPRUCE_FENCE));
        b(5, 2, 5, s(Blocks.HAY_BLOCK));
        b(5, 3, 5, s(Blocks.CARVED_PUMPKIN).setValue(CarvedPumpkinBlock.FACING, Direction.NORTH));
        mob(-5, 0, 5, DeadMob.ROTWALKER);
        mob(6, 0, -3, DeadMob.ROTWALKER);
        mob(0, 0, -4, DeadMob.ROTWALKER);
    }

    /** A bone pit: a round shaft lined with bone and soul soil, skulls and a spawner at the bottom. */
    private void bonePit() {
        for (int y = -12; y <= 0; y++) {
            double r = 6.5 - (y + 12) * 0.05;
            for (int x = -8; x <= 8; x++)
                for (int z = -8; z <= 8; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d < r) b(x, y, z, s(Blocks.AIR));
                    else if (d < r + 1.2) b(x, y, z, pick(x, y, z, s(Blocks.BONE_BLOCK), s(Blocks.SOUL_SOIL), s(Blocks.BONE_BLOCK), s(Blocks.COARSE_DIRT)));
                }
        }
        fm(-7, -13, -7, 7, -13, 7, s(Blocks.SOUL_SAND), s(Blocks.BONE_BLOCK), s(Blocks.SOUL_SOIL));
        for (int i = 0; i < 18; i++) {
            int x = (int) (hash(i, 1, 0) * 10) - 5, z = (int) (hash(i, 2, 0) * 10) - 5;
            if (x * x + z * z < 30) skull(x, -12, z);
        }
        // a spiral of bone steps
        for (int i = 0; i < 26; i++) {
            double t = i * 0.5;
            int x = (int) Math.round(Math.cos(t) * 5.6), z = (int) Math.round(Math.sin(t) * 5.6);
            b(x, -12 + i / 2, z, s(Blocks.BONE_BLOCK));
        }
        spawner(0, -12, 0, DeadMob.PARASITE);
        chest(2, -12, -2, "bone_pit");
        // rim of bone spikes
        for (int a = 0; a < 10; a++) {
            double t = a / 10.0 * Math.PI * 2;
            int x = (int) Math.round(Math.cos(t) * 8.5), z = (int) Math.round(Math.sin(t) * 8.5);
            int h = 2 + (int) (hash(x, 3, z) * 4);
            f(x, 0, z, x, h, z, s(Blocks.BONE_BLOCK));
            if (hash(x, 4, z) < 0.5) skull(x, h + 1, z);
        }
    }

    /** A ritual circle: monoliths around a ring of candles, a blood-soaked altar of crying obsidian. */
    private void ritualCircle() {
        footing(-9, -9, 9, 9, 4, s(Blocks.COARSE_DIRT));
        clearAbove(-9, -9, 9, 9, 7);
        for (int x = -9; x <= 9; x++)
            for (int z = -9; z <= 9; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < 9.3) b(x, -1, z, pick(x, -1, z, s(Blocks.SOUL_SOIL), s(Blocks.COARSE_DIRT), s(Blocks.GRAVEL)));
                if (Math.abs(d - 5) < 0.5) b(x, -1, z, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
            }
        for (int a = 0; a < 9; a++) {
            double t = a / 9.0 * Math.PI * 2;
            int x = (int) Math.round(Math.cos(t) * 8), z = (int) Math.round(Math.sin(t) * 8);
            int h = 3 + (int) (hash(x, 1, z) * 3);
            fm(x, 0, z, x, h, z, s(Blocks.DEEPSLATE_TILES), s(Blocks.POLISHED_DEEPSLATE), s(Blocks.COBBLED_DEEPSLATE));
            if (hash(x, 2, z) < 0.6) b(x, h + 1, z, s(Blocks.DEEPSLATE_TILE_SLAB));
            b(x, 2, z, s(Blocks.CRYING_OBSIDIAN));
        }
        for (int a = 0; a < 16; a++) {
            double t = a / 16.0 * Math.PI * 2;
            candles((int) Math.round(Math.cos(t) * 4), 0, (int) Math.round(Math.sin(t) * 4), 1 + a % 4);
        }
        f(-1, 0, -1, 1, 0, 1, s(Blocks.POLISHED_BLACKSTONE));
        b(0, 1, 0, s(Blocks.CRYING_OBSIDIAN));
        skull(0, 2, 0);
        bloodSplatter(-3, -3, 3, 3, 0.5);
        chest(0, -2, 0, "ritual");
        mob(3, 0, 3, DeadMob.ILLUSION);
        mob(-3, 0, 2, DeadMob.DEPART);
    }

    /** A ruined watchtower: a round stone tower with ladders between floors and a lookout up top. */
    private void watchtower() {
        footing(-4, -4, 4, 4, 10, STONE_RUIN);
        cyl(0, 0, -1, 19, 4, true, 0.35, STONE_RUIN);
        for (int y : new int[]{5, 11, 17}) {
            cyl(0, 0, y, y, 3.2, false, 0.0, s(Blocks.SPRUCE_PLANKS));
            b(0, y, 2, s(Blocks.AIR));
        }
        ladder(0, 0, 18, 3, Direction.NORTH);
        air(-1, 0, -4, 1, 2, -4);
        for (int y : new int[]{3, 8, 14}) { b(4, y, 0, s(Blocks.IRON_BARS)); b(-4, y, 0, s(Blocks.IRON_BARS)); }
        // crenellations
        for (int a = 0; a < 16; a += 2) {
            double t = a / 16.0 * Math.PI * 2;
            b((int) Math.round(Math.cos(t) * 4), 20, (int) Math.round(Math.sin(t) * 4), s(Blocks.STONE_BRICK_WALL));
        }
        chest(-2, 18, 0, "watchtower");
        b(1, 18, -1, s(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
        mob(1, 6, 0, DeadMob.THE_FORGOTTEN);
        mob(0, 0, 0, DeadMob.SEVERED);
    }

    /** Where a star fell: a scorched crater around a core of stardust and crystal. */
    private void fallenStar() {
        for (int x = -10; x <= 10; x++)
            for (int z = -10; z <= 10; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 10) continue;
                int depth = (int) Math.round(5 * (1 - (d / 10) * (d / 10)));
                air(x, -depth, z, x, 6, z);
                if (d < 10) b(x, -depth - 1, z, pick(x, 0, z, s(Blocks.MAGMA_BLOCK), s(Blocks.BLACKSTONE), s(Blocks.BASALT), s(Blocks.OBSIDIAN), s(Blocks.BLACKSTONE)));
                if (d > 8 && d < 10 && hash(x, 1, z) < 0.3) b(x, -depth, z, s(Blocks.FIRE));
            }
        sphere(0, -4, 0, 2.2, s(ModBlocks.STARDUST_BLOCK.get()));
        for (int i = 0; i < 6; i++) {
            double t = i / 6.0 * Math.PI * 2;
            int x = (int) Math.round(Math.cos(t) * 2), z = (int) Math.round(Math.sin(t) * 2);
            line(x, -3, z, x * 3, 2 + i % 3, z * 3, s(ModBlocks.VOID_CRYSTAL.get()));
        }
        b(0, -1, 0, s(ModBlocks.STARDUST_ORE.get()));
        chest(3, -4, 0, "fallen_star");
        mob(-4, -3, 2, DeadMob.STAR_FALL);
    }

    // ================================================================================================
    // Hell's Paradise
    // ================================================================================================
    /** A shrine of flesh: a sinew dais around a pool of blood, ribs arching over an altar. */
    private void fleshShrine() {
        footing(-6, -6, 6, 6, 8, charnel());
        clearAbove(-6, -6, 6, 6, 11);
        fm(-6, -1, -6, 6, -1, 6, s(ModBlocks.SINEW_BLOCK.get()), s(ModBlocks.SINEW_BLOCK.get()), s(Blocks.BONE_BLOCK));
        f(-1, -1, -1, 1, -1, 1, s(ModBlocks.BLOOD.get()));
        f(-2, -2, -2, 2, -2, 2, s(ModBlocks.SINEW_BLOCK.get()));
        for (int[] c : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) {
            f(c[0], 0, c[1], c[0], 6, c[1], s(Blocks.BONE_BLOCK));
            skull(c[0], 7, c[1]);
        }
        for (int z : new int[]{-3, 0, 3}) {
            for (int i = 0; i <= 12; i++) {
                double t = i / 12.0 * Math.PI;
                b((int) Math.round(Math.cos(t) * 5), (int) Math.round(Math.sin(t) * 8), z, s(Blocks.BONE_BLOCK));
            }
        }
        f(-1, 0, 4, 1, 0, 5, s(Blocks.RED_NETHER_BRICKS));
        candles(-1, 1, 4, 4); candles(1, 1, 4, 4);
        b(0, 1, 5, s(Blocks.CRYING_OBSIDIAN));
        chest(0, 0, 3, "hell_cache");
        spawner(0, 0, -4, DeadMob.BLOOD_DEMON);
        mob(3, 0, 0, DeadMob.SEVERED);
    }

    /** A field of impaled skulls: bone stakes in rows, chains and blood. */
    private void impalerField() {
        for (int x = -10; x <= 10; x += 2)
            for (int z = -10; z <= 10; z += 2) {
                if (hash(x, 1, z) < 0.35) continue;
                int dx = x + (int) (hash(x, 2, z) * 2), dz = z + (int) (hash(x, 3, z) * 2);
                int y = groundAt(dx, dz);
                if (y < -6 || y > 6) continue;
                int h = 2 + (int) (hash(x, 4, z) * 5);
                f(dx, y, dz, dx, y + h - 1, dz, s(Blocks.BONE_BLOCK));
                if (hash(x, 5, z) < 0.7) skull(dx, y + h, dz);
                else b(dx, y + h, dz, s(Blocks.CHAIN));
            }
        bloodSplatter(-10, -10, 10, 10, 0.18);
        chest(0, -2, 0, "hell_cache");
        mob(4, 0, 4, DeadMob.SEVERED);
        mob(-4, 0, -2, DeadMob.SCORCHED);
    }

    /** A demon forge: a blackstone smithy with a lava channel, anvils, blast furnaces and a chimney. */
    private void demonForge() {
        footing(-7, -6, 7, 6, 10, BLACKSTONE);
        clearAbove(-7, -6, 7, 6, 13);
        room(-7, -1, -6, 7, 7, 6, true, 0.1, BLACKSTONE);
        air(-1, 0, -6, 1, 3, -6);
        f(-5, -1, 0, 5, -1, 0, s(Blocks.LAVA));
        f(-5, -2, 0, 5, -2, 0, s(Blocks.BLACKSTONE));
        for (int x : new int[]{-4, 0, 4}) b(x, 0, 2, s(Blocks.ANVIL));
        for (int x = -5; x <= 5; x += 2) b(x, 0, 5, s(Blocks.BLAST_FURNACE).setValue(BlastFurnaceBlock.FACING, Direction.NORTH).setValue(BlastFurnaceBlock.LIT, true));
        b(-6, 0, -4, s(Blocks.LAVA_CAULDRON));
        b(6, 0, -4, s(Blocks.SMITHING_TABLE));
        for (int x = -4; x <= 4; x += 4) chain(x, 6, 3, -3);
        f(4, 8, 3, 5, 13, 4, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
        air(4, 8, 3, 4, 13, 3);
        b(4, 7, 3, s(Blocks.AIR));
        b(-5, 6, 0, s(Blocks.SHROOMLIGHT));
        b(5, 6, 0, s(Blocks.SHROOMLIGHT));
        chest(6, 0, 4, "forge");
        mob(0, 0, -3, DeadMob.BLOOD_DEMON);
        mob(-3, 0, 3, DeadMob.SCORCHED);
    }

    /** Gallows of the damned: a tall charred frame with gibbet cages swinging over a sinew pit. */
    private void cageGallows() {
        footing(-7, -3, 7, 3, 8, charnel());
        clearAbove(-7, -3, 7, 3, 14);
        fm(-7, -1, -3, 7, -1, 3, s(Blocks.NETHER_BRICKS), s(Blocks.RED_NETHER_BRICKS), s(ModBlocks.CHARNEL_STONE.get()));
        BlockState log = s(ModBlocks.CHARRED_LOG.get());
        for (int x : new int[]{-6, 6}) f(x, 0, 0, x, 12, 0, log);
        f(-6, 13, 0, 6, 13, 0, log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        line(-6, 9, 0, -3, 12, 0, log);
        line(6, 9, 0, 3, 12, 0, log);
        for (int x = -4; x <= 4; x += 2) gibbet(x, 12, 0, 3 + (int) (hash(x, 0, 0) * 4));
        air(-3, -1, -2, 3, -1, 2);
        f(-3, -3, -2, 3, -2, 2, s(ModBlocks.SINEW_BLOCK.get()));
        chest(-6, 0, 2, "hell_cache");
        mob(0, 0, 2, DeadMob.SCORCHED);
        mob(3, 0, -2, DeadMob.WRETCHED);
    }

    // ================================================================================================
    // Skyfall Torn
    // ================================================================================================
    /** A sky temple: a ring of pale columns under a broken dome, a pedestal of soul light. */
    private void skyTemple() {
        footing(-8, -8, 8, 8, 6, sky());
        clearAbove(-8, -8, 8, 8, 13);
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d < 8.5) b(x, -1, z, d < 2.5 ? s(Blocks.CHISELED_QUARTZ_BLOCK) : pick(x, -1, z, sky()));
            }
        for (int a = 0; a < 10; a++) {
            double t = a / 10.0 * Math.PI * 2;
            int x = (int) Math.round(Math.cos(t) * 7), z = (int) Math.round(Math.sin(t) * 7);
            int h = hash(x, 1, z) < 0.25 ? 3 + (int) (hash(x, 2, z) * 3) : 7;
            f(x, 0, z, x, h, z, s(Blocks.QUARTZ_PILLAR));
        }
        dome(0, 8, 0, 7.5, 0.4, s(Blocks.SMOOTH_QUARTZ), s(Blocks.CALCITE), s(ModBlocks.SKYSTONE.get()));
        f(0, 0, 0, 0, 1, 0, s(Blocks.CHISELED_QUARTZ_BLOCK));
        b(0, 2, 0, s(Blocks.SOUL_LANTERN));
        chest(1, 0, 0, "sky_cache");
        mob(3, 0, 3, DeadMob.GHOST);
        mob(-3, 0, -2, DeadMob.ILLUSION);
    }

    /** A pale bell tower, its great bell still hanging in the open belfry. */
    private void bellTower() {
        footing(-3, -3, 3, 3, 10, sky());
        clearAbove(-3, -3, 3, 3, 25);
        room(-3, -1, -3, 3, 18, 3, false, 0.08, sky());
        air(0, 0, -3, 0, 2, -3);
        for (int y = 4; y <= 16; y += 4) for (int[] c : new int[][]{{0, 3}, {3, 0}, {-3, 0}}) b(c[0], y, c[1], s(Blocks.WHITE_STAINED_GLASS_PANE));
        ladder(2, 0, 18, 2, Direction.NORTH);
        f(-3, 18, -3, 3, 18, 3, s(Blocks.SMOOTH_QUARTZ));
        b(2, 18, 2, s(Blocks.AIR));
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) f(c[0], 19, c[1], c[0], 22, c[1], s(Blocks.QUARTZ_PILLAR));
        f(-3, 23, -3, 3, 23, 3, s(Blocks.QUARTZ_SLAB));
        f(-2, 24, -2, 2, 24, 2, s(Blocks.QUARTZ_SLAB));
        b(0, 25, 0, s(Blocks.LIGHTNING_ROD));
        b(0, 21, 0, s(Blocks.BELL).setValue(BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.CEILING));
        f(0, 22, 0, 0, 22, 0, s(Blocks.DARK_OAK_FENCE));
        chest(-2, 19, -2, "sky_cache");
        mob(0, 0, 0, DeadMob.GHOST);
    }

    /** A wrecked airship: a cracked wooden hull run aground, its torn balloon draped over the mast. */
    private void airship() {
        clearAbove(-10, -5, 10, 5, 15);
        for (int x = -9; x <= 9; x++) {
            double half = 4.2 * Math.sqrt(Math.max(0, 1 - (x / 10.0) * (x / 10.0)));
            int tilt = x / 6;
            for (int z = -5; z <= 5; z++) {
                if (Math.abs(z) > half) continue;
                int keel = (int) Math.round(-2 + Math.abs(z) * 0.6);
                boolean side = Math.abs(z) > half - 1;
                for (int y = keel; y <= 3; y++) {
                    if (!side && y > keel) { if (y == 1) b(x, y + tilt, z, s(Blocks.SPRUCE_PLANKS)); else b(x, y + tilt, z, s(Blocks.AIR)); continue; }
                    if (hash(x, y, z) < 0.12 && y > keel) continue; // holes
                    b(x, y + tilt, z, pick(x, y, z, s(Blocks.SPRUCE_PLANKS), s(Blocks.DARK_OAK_PLANKS), s(Blocks.STRIPPED_SPRUCE_LOG)));
                }
            }
        }
        f(0, 2, 0, 0, 12, 0, s(Blocks.SPRUCE_LOG));
        f(-4, 9, 0, 4, 9, 0, s(Blocks.SPRUCE_FENCE));
        // the deflated balloon, slumped to one side
        for (int x = -6; x <= 6; x++)
            for (int z = -4; z <= 4; z++) {
                double d = Math.sqrt(x * x / 36.0 + z * z / 16.0);
                if (d > 1) continue;
                int y = 13 - (int) (d * 3) - (x > 0 ? x / 2 : 0);
                if (hash(x, 5, z) < 0.85) b(x, y, z, pick(x, y, z, s(Blocks.WHITE_WOOL), s(Blocks.LIGHT_GRAY_WOOL), s(Blocks.WHITE_WOOL)));
            }
        for (int x : new int[]{-4, 4}) chain(x, 11, 4, 3);
        b(7, 2, 0, s(Blocks.DISPENSER).setValue(DispenserBlock.FACING, Direction.EAST));
        b(-7, 2, 0, s(Blocks.DISPENSER).setValue(DispenserBlock.FACING, Direction.WEST));
        chest(-2, 2, 0, "airship");
        barrel(3, 2, 1, "airship");
        hangingLantern(2, 8, 0, false);
        mob(-4, 3, 0, DeadMob.DEPART);
    }

    /** An observatory: a pale hall under a glass dome, its telescope aimed at the shattered moon. */
    private void observatory() {
        footing(-5, -5, 5, 5, 8, sky());
        clearAbove(-5, -5, 5, 5, 13);
        room(-5, -1, -5, 5, 5, 5, false, 0.0, sky());
        air(0, 0, -5, 0, 2, -5);
        dome(0, 5, 0, 5.5, 0.0, s(Blocks.TINTED_GLASS), s(Blocks.GLASS), s(Blocks.GLASS));
        // telescope
        f(0, 0, 0, 0, 2, 0, s(Blocks.POLISHED_DEEPSLATE_WALL));
        line(-1, 3, -1, 3, 7, 3, s(Blocks.WAXED_CUT_COPPER));
        b(3, 8, 3, s(Blocks.WAXED_COPPER_BULB).setValue(BlockStateProperties.LIT, true));
        b(-3, 0, 3, s(Blocks.LECTERN));
        b(3, 0, -3, s(Blocks.BOOKSHELF)); b(3, 1, -3, s(Blocks.BOOKSHELF)); b(-3, 0, -3, s(Blocks.BOOKSHELF));
        chest(-3, 0, -2, "sky_cache");
        mob(2, 0, 2, DeadMob.ILLUSION);
    }

    // ================================================================================================
    // Light's Descent
    // ================================================================================================
    /** A cathedral ruin: a towering nave, red glass in pointed windows, buttresses and a crypt altar. */
    private void cathedral() {
        footing(-7, -13, 7, 13, 12, DEEP_RUIN);
        clearAbove(-7, -13, 7, 13, 19);
        room(-6, -1, -12, 6, 14, 12, false, 0.55, DEEP_RUIN);
        fm(-5, -1, -11, 5, -1, 11, s(Blocks.POLISHED_DEEPSLATE), s(Blocks.DEEPSLATE_TILES), s(Blocks.CRACKED_DEEPSLATE_TILES));
        for (int z = -9; z <= 9; z += 4) {
            for (int x : new int[]{-6, 6}) {
                f(x, 3, z, x, 8, z, s(Blocks.RED_STAINED_GLASS_PANE));
                b(x, 9, z, s(Blocks.BLACK_STAINED_GLASS_PANE));
                // buttress
                int bx = x < 0 ? x - 1 : x + 1;
                f(bx, 0, z + 2, bx, 7, z + 2, s(Blocks.DEEPSLATE_BRICKS));
                b(bx, 8, z + 2, s(Blocks.DEEPSLATE_BRICK_SLAB));
            }
        }
        // rose window over the door
        for (int x = -3; x <= 3; x++)
            for (int y = 7; y <= 13; y++) {
                double d = Math.sqrt(x * x + (y - 10) * (y - 10));
                if (d < 3.2) b(x, y, -12, d < 1 ? s(Blocks.ORANGE_STAINED_GLASS) : (x + y) % 2 == 0 ? s(Blocks.RED_STAINED_GLASS) : s(Blocks.BLACK_STAINED_GLASS));
            }
        air(-1, 0, -12, 1, 4, -12);
        for (int z = -8; z <= 6; z += 2) for (int x : new int[]{-4, -3, -2, 2, 3, 4})
            if (hash(x, 1, z) < 0.75) b(x, 0, z, s(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
        f(-2, 0, 9, 2, 1, 10, s(Blocks.CHISELED_DEEPSLATE));
        candles(-2, 2, 9, 4); candles(2, 2, 9, 4); skull(0, 2, 10);
        b(0, 2, 9, s(Blocks.SOUL_LANTERN));
        chest(0, 0, 8, "descent_cache");
        for (int z = -10; z <= 10; z += 5) hangingLantern(0, 13, z, true);
        spawner(0, -2, 6, DeadMob.THE_HOLLOW);
        cobwebs(-5, 8, -11, 5, 14, 11, 0.04);
        mob(0, 0, 0, DeadMob.THE_FORGOTTEN);
    }

    /** A bandit outpost dug into a mesa: a stockade, corner towers, a hut and the gang's loot. */
    private void mesaOutpost() {
        footing(-9, -9, 9, 9, 8, s(ModBlocks.DUSTROCK.get()));
        clearAbove(-9, -9, 9, 9, 11);
        fm(-9, -1, -9, 9, -1, 9, s(ModBlocks.BONE_SAND.get()), s(Blocks.COARSE_DIRT), s(Blocks.DIRT_PATH));
        for (int i = -9; i <= 9; i++) {
            for (int[] c : new int[][]{{i, -9}, {i, 9}, {-9, i}, {9, i}}) {
                if (c[1] == -9 && Math.abs(c[0]) <= 1) continue; // gate
                f(c[0], 0, c[1], c[0], 3 + (int) (hash(c[0], 0, c[1]) * 2), c[1], s(Blocks.DARK_OAK_LOG));
            }
        }
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            for (int[] d : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) f(c[0] + d[0], 0, c[1] + d[1], c[0] + d[0], 6, c[1] + d[1], s(Blocks.DARK_OAK_FENCE));
            f(c[0] - 1, 7, c[1] - 1, c[0] + 1, 7, c[1] + 1, s(Blocks.DARK_OAK_PLANKS));
            b(c[0], 8, c[1], s(Blocks.CAMPFIRE).setValue(CampfireBlock.LIT, false));
        }
        room(-3, -1, 2, 3, 4, 7, true, 0.0, s(Blocks.SPRUCE_PLANKS), s(Blocks.STRIPPED_SPRUCE_LOG));
        air(0, 0, 2, 0, 1, 2);
        chest(-2, 0, 6, "bandit_stash");
        barrel(2, 0, 6, "bandit_stash");
        barrel(-6, 0, -5, "bandit_stash");
        b(0, 0, -3, s(Blocks.CAMPFIRE));
        mob(-4, 0, -4, DeadMob.BANDIT);
        mob(4, 0, -4, DeadMob.BANDIT);
        mob(0, 0, 0, DeadMob.BANDIT_CAPTAIN);
        mob(-8, 8, 8, DeadMob.BANDIT);
    }

    /** A gallows square: cobbles, a scaffold with three nooses, stocks, a well and lamp posts. */
    private void gallowsSquare() {
        footing(-8, -8, 8, 8, 6, s(Blocks.COBBLESTONE));
        clearAbove(-8, -8, 8, 8, 8);
        fm(-8, -1, -8, 8, -1, 8, s(Blocks.COBBLESTONE), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.GRAVEL), s(Blocks.STONE_BRICKS));
        f(-4, 0, -2, 4, 1, 2, s(Blocks.DARK_OAK_PLANKS));
        for (int i = 0; i < 2; i++) b(0, i, -3 - (1 - i), s(Blocks.DARK_OAK_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH));
        for (int x : new int[]{-4, 4}) f(x, 2, 0, x, 7, 0, s(Blocks.DARK_OAK_LOG));
        f(-4, 7, 0, 4, 7, 0, s(Blocks.DARK_OAK_LOG).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        for (int x : new int[]{-2, 0, 2}) { chain(x, 6, 4, 0); if (hash(x, 9, 0) < 0.5) skull(x, 3, 0); }
        // stocks
        f(5, 0, 5, 7, 0, 5, s(Blocks.SPRUCE_FENCE));
        f(5, 1, 5, 7, 1, 5, s(Blocks.SPRUCE_SLAB));
        // well
        cyl(-6, 6, -1, 0, 1.6, true, 0, s(Blocks.STONE_BRICKS));
        air(-6, -6, 6, -6, -1, 6);
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            f(c[0], 0, c[1], c[0], 3, c[1], s(Blocks.DARK_OAK_FENCE));
            b(c[0], 4, c[1], s(Blocks.LANTERN));
        }
        bloodSplatter(-3, -1, 3, 1, 0.3);
        chest(3, 2, 1, "descent_cache");
        mob(-5, 0, -5, DeadMob.ROTWALKER);
        mob(5, 0, -5, DeadMob.ROTWALKER);
        mob(0, 2, 1, DeadMob.SEVERED);
    }

    /** An abandoned mine: a timber portal and a long rail tunnel plunging into the cliff. */
    private void abandonedMine() {
        // portal frame
        for (int x : new int[]{-2, 2}) f(x, 0, -12, x, 4, -12, s(Blocks.DARK_OAK_LOG));
        f(-2, 5, -12, 2, 5, -12, s(Blocks.DARK_OAK_LOG).setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        hangingLantern(0, 4, -12, false);
        b(3, 0, -12, s(Blocks.OAK_HANGING_SIGN));
        // the tunnel: down one block every two
        for (int i = 0; i <= 26; i++) {
            int z = -12 + i, y = -i / 2;
            air(-1, y, z, 1, y + 3, z);
            f(-1, y - 1, z, 1, y - 1, z, s(Blocks.COBBLED_DEEPSLATE));
            b(0, y, z, s(Blocks.RAIL));
            if (i % 4 == 0) {
                f(-2, y, z, -2, y + 3, z, s(Blocks.DARK_OAK_LOG));
                f(2, y, z, 2, y + 3, z, s(Blocks.DARK_OAK_LOG));
                f(-1, y + 4, z, 1, y + 4, z, s(Blocks.DARK_OAK_PLANKS));
                if (i % 8 == 0) hangingLantern(0, y + 3, z, false);
            }
            if (hash(0, y, z) < 0.1) b(-1, y, z, s(Blocks.COBWEB));
        }
        // the end chamber
        room(-4, -14, 13, 4, -8, 21, true, 0.0, s(Blocks.COBBLED_DEEPSLATE), s(Blocks.DEEPSLATE), s(ModBlocks.UMBRAL_STONE.get()));
        for (int i = 0; i < 10; i++) {
            int x = (int) (hash(i, 1, 1) * 7) - 3, z = 14 + (int) (hash(i, 2, 1) * 7);
            b(x, -13, z, pick(x, -13, z, s(ModBlocks.NECRITE_ORE.get()), s(Blocks.IRON_ORE), s(Blocks.COAL_ORE), s(ModBlocks.BLOODSTONE_ORE.get())));
        }
        chest(0, -13, 20, "mine");
        barrel(3, -13, 20, "mine");
        b(-3, -13, 20, s(Blocks.TNT));
        hangingLantern(0, -9, 17, false);
        spawner(0, -13, 16, DeadMob.CRACKED);
    }

    // ================================================================================================
    // Disposed Skies
    // ================================================================================================
    /** A void obelisk: a tapering black needle of runes, ringed by crystal and end rods. */
    private void voidObelisk() {
        footing(-6, -6, 6, 6, 8, voidStone());
        clearAbove(-6, -6, 6, 6, 27);
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) if (x * x + z * z < 40) b(x, -1, z, pick(x, -1, z, s(Blocks.POLISHED_BLACKSTONE), s(ModBlocks.VOIDROCK.get()), s(Blocks.OBSIDIAN)));
        for (int y = 0; y <= 25; y++) {
            int r = y < 10 ? 2 : y < 19 ? 1 : 0;
            for (int x = -r; x <= r; x++)
                for (int z = -r; z <= r; z++) {
                    boolean rune = (Math.abs(x) == r || Math.abs(z) == r) && (y % 3 == 1) && hash(x, y, z) < 0.5;
                    b(x, y, z, rune ? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN));
                }
        }
        b(0, 26, 0, s(Blocks.END_ROD));
        for (int a = 0; a < 6; a++) {
            double t = a / 6.0 * Math.PI * 2;
            int x = (int) Math.round(Math.cos(t) * 5), z = (int) Math.round(Math.sin(t) * 5);
            line(x, 0, z, (int) Math.round(x * 1.3), 4 + a % 3, (int) Math.round(z * 1.3), s(ModBlocks.VOID_CRYSTAL.get()));
            b(x / 2, 0, z / 2, s(Blocks.END_ROD));
        }
        chest(3, 0, 0, "void_cache");
        mob(-3, 0, 2, DeadMob.ILLUSION);
        mob(2, 0, -3, DeadMob.DEPART);
    }

    /** A storm spire: a green-copper tower crowned with lightning rods, wound with chains. */
    private void stormSpire() {
        footing(-4, -4, 4, 4, 8, voidStone());
        clearAbove(-4, -4, 4, 4, 23);
        for (int x = -4; x <= 4; x++)
            for (int z = -4; z <= 4; z++) if (x * x + z * z < 18) b(x, -1, z, s(ModBlocks.STATIC_CRUST.get()));
        cyl(0, 0, -1, 18, 2.5, true, 0.1, s(Blocks.OXIDIZED_COPPER), s(Blocks.OXIDIZED_CUT_COPPER), s(Blocks.WEATHERED_COPPER), s(Blocks.WAXED_OXIDIZED_COPPER));
        air(0, 0, -3, 0, 2, -3);
        ladder(0, 0, 17, 1, Direction.SOUTH);
        f(-1, 19, -1, 1, 19, 1, s(Blocks.OXIDIZED_CUT_COPPER));
        for (int[] c : new int[][]{{0, 0}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) f(c[0], 20, c[1], c[0], 21 + (c[0] == 0 ? 2 : 0), c[1], s(Blocks.LIGHTNING_ROD));
        for (int y = 2; y <= 16; y += 2) {
            double t = y * 0.7;
            b((int) Math.round(Math.cos(t) * 3), y, (int) Math.round(Math.sin(t) * 3), s(Blocks.CHAIN));
        }
        b(0, 18, 0, s(Blocks.WAXED_COPPER_BULB).setValue(BlockStateProperties.LIT, true));
        chest(-1, 0, 0, "void_cache");
        mob(3, 0, 3, DeadMob.STAR_FALL);
    }

    /** A shattered sanctum: the drifting floor of a temple, its portal ring broken, crystals growing through. */
    private void shatteredSanctum() {
        clearAbove(-8, -8, 8, 8, 11);
        for (int x = -8; x <= 8; x++)
            for (int z = -8; z <= 8; z++) {
                if (hash(x, 1, z) < 0.15 || hash(x / 3, 2, z / 3) < 0.12) continue; // cracks and missing slabs
                b(x, -1, z, pick(x, -1, z, s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), s(Blocks.CHISELED_POLISHED_BLACKSTONE)));
                if (hash(x, 3, z) < 0.5) b(x, -2, z, s(ModBlocks.VOIDROCK.get()));
            }
        for (int x = -8; x <= 8; x++) for (int z : new int[]{-8, 8}) {
            int h = (int) (hash(x, 4, z) * 6);
            fm(x, 0, z, x, h, z, BLACKSTONE);
        }
        // the broken portal ring
        for (int a = 0; a < 20; a++) {
            double t = a / 20.0 * Math.PI * 2;
            if (a > 13 && a < 17) continue;
            b((int) Math.round(Math.cos(t) * 4), 5 + (int) Math.round(Math.sin(t) * 4), 0, s(Blocks.OBSIDIAN));
        }
        f(-1, 0, 0, 1, 0, 0, s(Blocks.CRYING_OBSIDIAN));
        for (int i = 0; i < 5; i++) {
            int x = (int) (hash(i, 5, 0) * 12) - 6, z = (int) (hash(i, 6, 0) * 12) - 6;
            line(x, 0, z, x + 1, 3 + i % 3, z - 1, s(ModBlocks.VOID_CRYSTAL.get()));
        }
        candles(-3, 0, 3, 3); candles(3, 0, 3, 2);
        chest(0, 0, 4, "void_cache");
        mob(4, 0, -4, DeadMob.DEPART);
        mob(-4, 0, -3, DeadMob.ILLUSION);
    }
}
