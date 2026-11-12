package com.deadcraft.world;

import com.deadcraft.DeadCraft;
import com.deadcraft.block.GravestoneBlock;
import com.deadcraft.block.RiftPortalBlock;
import com.deadcraft.entity.DeadMob;
import com.deadcraft.registry.ModBlocks;
import com.deadcraft.registry.ModEntities;
import com.deadcraft.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Procedural builder for every DeadCraft structure piece. All randomness is a pure function of
 * (seed, x, y, z) so a piece that spans several chunks generates identically in each of them.
 */
public class DeadPiece extends StructurePiece {
    public enum Shape { TOMB, GRAVEYARD, CASTLE, GATEWAY, PORTAL, STREETS, BUILDING, CRATER }

    /** City destruction styles: burned, collapsed, blighted, butchered, meteor-struck. */
    public static final int CITY_STYLES = 5;

    private final Shape shape;
    private final int style;
    private final long seed;
    private final int destination;
    private final String boss;

    public DeadPiece(Shape shape, int style, long seed, int destination, String boss, BoundingBox box) {
        super(ModWorldgen.DEAD_PIECE.get(), 0, box);
        this.shape = shape;
        this.style = style;
        this.seed = seed;
        this.destination = destination;
        this.boss = boss;
        setOrientation(null);
    }

    public DeadPiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(ModWorldgen.DEAD_PIECE.get(), tag);
        this.shape = Shape.values()[Math.min(tag.getInt("Shape"), Shape.values().length - 1)];
        this.style = tag.getInt("Style");
        this.seed = tag.getLong("Seed");
        this.destination = tag.getInt("Destination");
        this.boss = tag.getString("Boss");
        setOrientation(null);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putInt("Shape", shape.ordinal());
        tag.putInt("Style", style);
        tag.putLong("Seed", seed);
        tag.putInt("Destination", destination);
        tag.putString("Boss", boss);
    }

    // ---- Deterministic noise -------------------------------------------------------------------
    private double hash(int x, int y, int z) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return (h >>> 11) * 0x1.0p-53;
    }

    private BlockState pick(int x, int y, int z, BlockState... options) {
        return options[(int) (hash(x, y, z) * options.length) % options.length];
    }

    // ---- Block helpers (absolute coordinates) -------------------------------------------------
    private void set(WorldGenLevel level, BoundingBox clip, int x, int y, int z, BlockState state) {
        BlockPos p = new BlockPos(x, y, z);
        if (clip.isInside(p)) level.setBlock(p, state, 2);
    }

    private BlockState get(WorldGenLevel level, BoundingBox clip, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        return clip.isInside(p) ? level.getBlockState(p) : Blocks.STONE.defaultBlockState();
    }

    private void fill(WorldGenLevel level, BoundingBox clip, int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(level, clip, x, y, z, state);
    }

    /** Fills air/water below (x, y, z) down to solid ground, so nothing floats. */
    private void foundation(WorldGenLevel level, BoundingBox clip, int x, int y, int z, BlockState state) {
        for (int i = 0; i < 16; i++) {
            BlockPos p = new BlockPos(x, y - i, z);
            if (!clip.isInside(p)) return;
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) return;
            level.setBlock(p, state, 2);
        }
    }

    private int ground(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    private void spawn(WorldGenLevel level, BoundingBox clip, EntityType<?> type, int x, int y, int z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!clip.isInside(pos)) return;
        var entity = type.create(level.getLevel());
        if (!(entity instanceof Mob mob)) return;
        mob.moveTo(x + 0.5, y, z + 0.5, (float) (hash(x, y, z) * 360), 0);
        mob.setPersistenceRequired();
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        level.addFreshEntityWithPassengers(mob);
    }

    private void spawnMob(WorldGenLevel level, BoundingBox clip, DeadMob mob, int x, int y, int z) {
        spawn(level, clip, ModEntities.type(mob), x, y, z);
    }

    private void spawnBoss(WorldGenLevel level, BoundingBox clip, int x, int y, int z) {
        if (boss.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(boss);
        if (id == null) return;
        BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(t -> spawn(level, clip, t, x, y, z));
    }

    private void chest(WorldGenLevel level, BoundingBox clip, RandomSource random, int x, int y, int z, String table) {
        BlockPos p = new BlockPos(x, y, z);
        if (!clip.isInside(p)) return;
        ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, DeadCraft.id("chests/" + table));
        createChest(level, clip, random, p, key, null);
    }

    private static BlockState s(Block b) { return b.defaultBlockState(); }

    /** Fire that keeps burning: it sits on netherrack, so it never goes out or spreads into stone. */
    private void fire(WorldGenLevel level, BoundingBox clip, int x, int y, int z) {
        set(level, clip, x, y - 1, z, s(Blocks.NETHERRACK));
        set(level, clip, x, y, z, s(Blocks.FIRE));
    }

    // ---- Dispatch -------------------------------------------------------------------------------
    @Override
    public void postProcess(WorldGenLevel level, StructureManager sm, ChunkGenerator gen, RandomSource random,
                            BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        switch (shape) {
            case TOMB -> tomb(level, chunkBox, random);
            case GRAVEYARD -> graveyard(level, chunkBox, random);
            case CASTLE -> castle(level, chunkBox, random);
            case GATEWAY -> gateway(level, chunkBox);
            case PORTAL -> portal(level, chunkBox);
            case STREETS -> streets(level, chunkBox);
            case BUILDING -> building(level, chunkBox, random);
            case CRATER -> crater(level, chunkBox);
        }
    }

    // ---- Tomb: a mausoleum above a buried crypt ------------------------------------------------
    private void tomb(WorldGenLevel level, BoundingBox clip, RandomSource random) {
        BoundingBox b = boundingBox;
        int cx = b.minX() + 7, gy = b.minY() + 11, oz = b.minZ();
        BlockState bricks = s(Blocks.STONE_BRICKS), cracked = s(Blocks.CRACKED_STONE_BRICKS), mossy = s(Blocks.MOSSY_STONE_BRICKS);
        BlockState deep = s(Blocks.DEEPSLATE_TILES), deepCracked = s(Blocks.CRACKED_DEEPSLATE_TILES), polished = s(Blocks.POLISHED_DEEPSLATE);
        // Mausoleum (7x7) on the surface
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = oz; z <= oz + 6; z++) {
                foundation(level, clip, x, gy - 1, z, bricks);
                set(level, clip, x, gy - 1, z, pick(x, gy, z, bricks, bricks, cracked, mossy));
            }
        for (int[] c : new int[][]{{cx - 3, oz}, {cx + 3, oz}, {cx - 3, oz + 6}, {cx + 3, oz + 6}}) {
            for (int y = gy; y < gy + 5; y++) set(level, clip, c[0], y, c[1], s(Blocks.CHISELED_STONE_BRICKS));
        }
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = oz; z <= oz + 6; z++) {
                boolean edge = x == cx - 3 || x == cx + 3 || z == oz || z == oz + 6;
                if (hash(x, gy + 5, z) < 0.12) continue; // roof is caving in
                set(level, clip, x, gy + 5, z, edge ? s(Blocks.STONE_BRICK_SLAB) : bricks);
            }
        // Side walls with a doorway facing north
        for (int y = gy; y < gy + 5; y++)
            for (int i = -2; i <= 2; i++) {
                if (hash(cx + i, y, oz + 6) > 0.15) set(level, clip, cx + i, y, oz + 6, pick(cx + i, y, oz, bricks, cracked, mossy));
                if (hash(cx - 3, y, oz + 1 + i + 2) > 0.15) set(level, clip, cx - 3, y, oz + 3 + i, pick(cx, y, oz + i, bricks, cracked));
                if (hash(cx + 3, y, oz + 3 + i) > 0.15) set(level, clip, cx + 3, y, oz + 3 + i, pick(cx + 1, y, oz + i, bricks, mossy));
            }
        set(level, clip, cx - 2, gy, oz + 1, s(Blocks.SOUL_LANTERN));
        set(level, clip, cx + 2, gy, oz + 1, s(Blocks.SOUL_LANTERN));
        // Staircase down (ascends toward the north)
        for (int i = 0; i < 9; i++) {
            int y = gy - 1 - i, z = oz + 2 + i;
            for (int dx = -1; dx <= 1; dx++) {
                set(level, clip, cx + dx, y, z, s(Blocks.STONE_BRICK_STAIRS).setValue(StairBlock.FACING, Direction.NORTH));
                for (int up = 1; up <= 3; up++) set(level, clip, cx + dx, y + up, z, s(Blocks.AIR));
                set(level, clip, cx + dx, y - 1, z, deep);
            }
            set(level, clip, cx - 2, y + 1, z, deep);
            set(level, clip, cx + 2, y + 1, z, deep);
        }
        // Crypt hall
        int fy = gy - 11, z0 = oz + 10, z1 = oz + 18, x0 = cx - 6, x1 = cx + 6;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = fy; y <= fy + 6; y++) {
                    boolean shell = x == x0 || x == x1 || z == z0 || z == z1 || y == fy || y == fy + 6;
                    set(level, clip, x, y, z, shell ? pick(x, y, z, deep, deep, deepCracked, polished) : s(Blocks.AIR));
                }
        for (int dx = -1; dx <= 1; dx++) for (int up = 1; up <= 3; up++) set(level, clip, cx + dx, fy + up, z0, s(Blocks.AIR));
        // Sarcophagi along both walls
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            for (int side : new int[]{x0 + 1, x1 - 2}) {
                set(level, clip, side, fy + 1, z, s(Blocks.CHISELED_DEEPSLATE));
                set(level, clip, side + 1, fy + 1, z, s(Blocks.CHISELED_DEEPSLATE));
                set(level, clip, side, fy + 2, z, s(Blocks.POLISHED_DEEPSLATE_SLAB));
                set(level, clip, side + 1, fy + 2, z, s(Blocks.POLISHED_DEEPSLATE_SLAB));
            }
        }
        for (int x = x0 + 1; x < x1; x++)
            for (int z = z0 + 1; z < z1; z++) {
                if (hash(x, fy + 5, z) < 0.08) set(level, clip, x, fy + 5, z, s(Blocks.COBWEB));
                if (hash(x, fy + 1, z) < 0.03 && get(level, clip, x, fy + 1, z).isAir()) set(level, clip, x, fy + 1, z, s(Blocks.BONE_BLOCK));
                if (hash(x, fy, z) < 0.05) set(level, clip, x, fy + 1, z, s(ModBlocks.BLOOD_PUDDLE.get()));
            }
        set(level, clip, cx, fy + 5, z1 - 4, s(Blocks.CHAIN));
        set(level, clip, cx, fy + 4, z1 - 4, s(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
        set(level, clip, cx - 4, fy + 5, z0 + 3, s(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
        set(level, clip, cx + 4, fy + 5, z0 + 3, s(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
        chest(level, clip, random, cx, fy + 1, z1 - 1, "tomb");
        spawnMob(level, clip, DeadMob.THE_FORGOTTEN, cx - 3, fy + 1, z1 - 3);
        spawnMob(level, clip, DeadMob.THE_HOLLOW, cx + 3, fy + 1, z0 + 4);
        spawnBoss(level, clip, cx, fy + 1, z1 - 4);
    }

    // ---- Graveyard ------------------------------------------------------------------------------
    private void graveyard(WorldGenLevel level, BoundingBox clip, RandomSource random) {
        BoundingBox b = boundingBox;
        int x0 = b.minX(), z0 = b.minZ(), x1 = b.maxX(), z1 = b.maxZ();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                BlockPos col = new BlockPos(x, b.minY(), z);
                if (!clip.isInside(new BlockPos(x, clip.minY(), z)) || !b.isInside(col)) continue;
                int gy = ground(level, x, z);
                set(level, clip, x, gy - 1, z, pick(x, 0, z, s(Blocks.COARSE_DIRT), s(Blocks.PODZOL), s(Blocks.ROOTED_DIRT), s(Blocks.SOUL_SOIL), s(Blocks.COARSE_DIRT)));
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge) {
                    boolean post = (x - x0) % 4 == 0 && (z - z0) % 4 == 0;
                    if (post) {
                        set(level, clip, x, gy, z, s(Blocks.STONE_BRICK_WALL));
                        set(level, clip, x, gy + 1, z, s(Blocks.STONE_BRICK_WALL));
                        if (hash(x, 1, z) < 0.5) set(level, clip, x, gy + 2, z, s(Blocks.SOUL_LANTERN));
                    } else if (hash(x, 2, z) > 0.2 && !(z == z0 && Math.abs(x - (x0 + x1) / 2) <= 1)) {
                        set(level, clip, x, gy, z, s(Blocks.IRON_BARS));
                    }
                    continue;
                }
                // Rows of graves
                int lx = x - x0, lz = z - z0;
                if (lx >= 3 && lx <= x1 - x0 - 3 && lz % 4 == 3 && lx % 3 == 0) {
                    if (hash(x, 3, z) < 0.85) {
                        set(level, clip, x, gy, z, s(ModBlocks.GRAVESTONE.get()).setValue(GravestoneBlock.FACING, Direction.NORTH));
                        if (hash(x, 4, z) < 0.25) {
                            set(level, clip, x, gy - 1, z - 1, s(Blocks.AIR));
                            set(level, clip, x, gy - 2, z - 1, s(Blocks.SOUL_SAND));
                            set(level, clip, x, gy - 1, z - 2, s(Blocks.AIR));
                            set(level, clip, x, gy - 2, z - 2, s(Blocks.SOUL_SAND));
                        } else {
                            set(level, clip, x, gy, z - 1, s(Blocks.COARSE_DIRT));
                        }
                    }
                } else if (hash(x, 5, z) < 0.02) {
                    set(level, clip, x, gy, z, s(Blocks.BONE_BLOCK));
                } else if (hash(x, 6, z) < 0.03) {
                    set(level, clip, x, gy, z, s(Blocks.DEAD_BUSH));
                }
            }
        // Dead tree and mausoleum
        int tx = x0 + 3, tz = z1 - 3;
        if (clip.isInside(new BlockPos(tx, clip.minY(), tz))) {
            int gy = ground(level, tx, tz);
            com.deadcraft.world.feature.DeadTreeFeature.place(level, new BlockPos(tx, gy, tz), seed, clip);
        }
        int mx = (x0 + x1) / 2, mz = z1 - 4;
        if (clip.isInside(new BlockPos(mx, clip.minY(), mz))) {
            int gy = ground(level, mx, mz);
            for (int x = mx - 2; x <= mx + 2; x++)
                for (int z = mz - 2; z <= mz + 2; z++)
                    for (int y = gy; y <= gy + 4; y++) {
                        boolean shell = Math.abs(x - mx) == 2 || Math.abs(z - mz) == 2 || y == gy + 4;
                        boolean door = z == mz - 2 && x == mx && y < gy + 3;
                        set(level, clip, x, y, z, shell && !door ? pick(x, y, z, s(Blocks.POLISHED_DEEPSLATE), s(Blocks.DEEPSLATE_BRICKS), s(Blocks.CRACKED_DEEPSLATE_BRICKS)) : s(Blocks.AIR));
                    }
            chest(level, clip, random, mx, gy, mz + 1, "graveyard");
            set(level, clip, mx, gy + 3, mz, s(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
        }
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        if (clip.isInside(new BlockPos(cx, clip.minY(), cz))) {
            int gy = ground(level, cx, cz);
            spawnMob(level, clip, DeadMob.SEVERED, cx - 4, gy, cz);
            spawnMob(level, clip, DeadMob.GHOST, cx + 4, gy + 1, cz - 3);
            spawnMob(level, clip, DeadMob.THE_FORGOTTEN, cx, gy, cz + 3);
            spawnBoss(level, clip, cx, gy, cz);
        }
    }

    // ---- Castle -----------------------------------------------------------------------------
    private BlockState castleStone(int x, int y, int z) {
        double h = hash(x, y, z);
        if (h < 0.35) return s(Blocks.DEEPSLATE_BRICKS);
        if (h < 0.55) return s(Blocks.CRACKED_DEEPSLATE_BRICKS);
        if (h < 0.75) return s(Blocks.STONE_BRICKS);
        if (h < 0.88) return s(Blocks.CRACKED_STONE_BRICKS);
        return s(Blocks.MOSSY_STONE_BRICKS);
    }

    private void castle(WorldGenLevel level, BoundingBox clip, RandomSource random) {
        BoundingBox b = boundingBox;
        int x0 = b.minX(), z0 = b.minZ(), x1 = b.maxX(), z1 = b.maxZ(), gy = b.minY() + 1;
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        // Courtyard floor
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                foundation(level, clip, x, gy - 1, z, s(Blocks.DEEPSLATE_BRICKS));
                set(level, clip, x, gy - 1, z, pick(x, gy, z, s(Blocks.GRAVEL), s(Blocks.COBBLED_DEEPSLATE), s(Blocks.COARSE_DIRT), s(Blocks.STONE_BRICKS)));
                for (int y = gy; y < gy + 24; y++) set(level, clip, x, y, z, s(Blocks.AIR));
            }
        // Curtain walls, ruined on top and holed
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean wall = x <= x0 + 1 || x >= x1 - 1 || z <= z0 + 1 || z >= z1 - 1;
                if (!wall) continue;
                int top = gy + 9 - (int) (hash(x, 0, z) * hash(x / 3, 1, z / 3) * 8);
                boolean gate = z <= z0 + 1 && Math.abs(x - cx) <= 2;
                for (int y = gy; y <= top; y++) {
                    if (gate && y < gy + 6) continue;
                    if (hash(x, y, z) < 0.06) continue;
                    set(level, clip, x, y, z, castleStone(x, y, z));
                }
                if (top >= gy + 9 && (x + z) % 2 == 0) set(level, clip, x, top + 1, z, castleStone(x, top + 1, z));
                if (gate) for (int y = gy + 3; y < gy + 6; y++) if (hash(x, y, 7) < 0.6) set(level, clip, x, y, z0, s(Blocks.IRON_BARS));
            }
        // Corner towers
        for (int[] t : new int[][]{{x0 + 3, z0 + 3}, {x1 - 3, z0 + 3}, {x0 + 3, z1 - 3}, {x1 - 3, z1 - 3}}) {
            int collapse = (int) (hash(t[0], 99, t[1]) * 9);
            for (int x = t[0] - 3; x <= t[0] + 3; x++)
                for (int z = t[1] - 3; z <= t[1] + 3; z++) {
                    boolean shell = Math.abs(x - t[0]) == 3 || Math.abs(z - t[1]) == 3;
                    boolean corner = Math.abs(x - t[0]) == 3 && Math.abs(z - t[1]) == 3;
                    if (corner) continue;
                    int top = gy + 18 - collapse - (int) (hash(x, 2, z) * 3);
                    for (int y = gy; y <= top; y++) {
                        if (shell) {
                            boolean window = (y - gy) % 5 == 3 && (x == t[0] || z == t[1]);
                            set(level, clip, x, y, z, window ? s(Blocks.AIR) : castleStone(x, y, z));
                        } else if ((y - gy) % 6 == 5) {
                            if (hash(x, y, z) > 0.2) set(level, clip, x, y, z, s(Blocks.DARK_OAK_PLANKS));
                        }
                    }
                    if (shell && collapse < 2 && (x + z) % 2 == 0) set(level, clip, x, top + 1, z, castleStone(x, top + 1, z));
                }
            set(level, clip, t[0], gy + 1, t[1], s(Blocks.SOUL_CAMPFIRE));
            chest(level, clip, random, t[0] + 1, gy + 6, t[1] + 1, "castle");
        }
        // The keep and throne room
        int k = 6;
        for (int x = cx - k; x <= cx + k; x++)
            for (int z = cz - k + 2; z <= cz + k + 2; z++) {
                boolean shell = Math.abs(x - cx) == k || z == cz - k + 2 || z == cz + k + 2;
                int top = gy + 14 - (int) (hash(x, 3, z) * hash(x / 2, 4, z / 2) * 6);
                for (int y = gy; y <= top; y++) {
                    if (shell) {
                        boolean door = z == cz - k + 2 && Math.abs(x - cx) <= 1 && y < gy + 4;
                        boolean window = (y - gy) % 7 == 3 && (x - cx) % 3 == 0;
                        if (!door && !window && hash(x, y, z) > 0.04) set(level, clip, x, y, z, castleStone(x, y, z));
                    } else if (y == gy + 7 && hash(x, y, z) > 0.25) {
                        set(level, clip, x, y, z, s(Blocks.DARK_OAK_PLANKS));
                    }
                }
                if (!shell) set(level, clip, x, gy - 1, z, s(Blocks.POLISHED_BLACKSTONE_BRICKS));
            }
        for (int z = cz - k + 3; z <= cz + k; z++) set(level, clip, cx, gy, z, s(Blocks.RED_CARPET));
        int tz = cz + k;
        set(level, clip, cx, gy, tz, s(Blocks.POLISHED_BLACKSTONE_STAIRS).setValue(StairBlock.FACING, Direction.SOUTH));
        set(level, clip, cx, gy + 1, tz + 1, s(Blocks.GILDED_BLACKSTONE));
        set(level, clip, cx - 1, gy, tz, s(Blocks.POLISHED_BLACKSTONE_WALL));
        set(level, clip, cx + 1, gy, tz, s(Blocks.POLISHED_BLACKSTONE_WALL));
        for (int[] l : new int[][]{{cx - 4, cz - 1}, {cx + 4, cz - 1}, {cx - 4, cz + 5}, {cx + 4, cz + 5}}) {
            set(level, clip, l[0], gy + 6, l[1], s(Blocks.CHAIN));
            set(level, clip, l[0], gy + 5, l[1], s(Blocks.SOUL_LANTERN).setValue(LanternBlock.HANGING, true));
        }
        chest(level, clip, random, cx + 3, gy + 8, cz + 6, "castle");
        // Courtyard dressing
        for (int x = x0 + 3; x <= x1 - 3; x++)
            for (int z = z0 + 3; z <= z1 - 3; z++) {
                if (Math.abs(x - cx) <= k + 1 && z >= cz - k + 1) continue;
                double h = hash(x, 7, z);
                if (h < 0.015) set(level, clip, x, gy, z, s(Blocks.BONE_BLOCK));
                else if (h < 0.03) set(level, clip, x, gy, z, s(Blocks.COBWEB));
                else if (h < 0.035) set(level, clip, x, gy, z, s(Blocks.SOUL_CAMPFIRE));
            }
        if (clip.isInside(new BlockPos(cx - 8, clip.minY(), cz - 9))) com.deadcraft.world.feature.DeadTreeFeature.place(level, new BlockPos(cx - 8, gy, cz - 9), seed, clip);
        spawnBoss(level, clip, cx, gy, cz + 3);
        for (int i = 0; i < 4; i++) spawn(level, clip, EntityType.SKELETON, cx - 6 + i * 4, gy, cz - 10);
        spawnMob(level, clip, DeadMob.THE_HOLLOW, x0 + 3, gy + 1, z1 - 3);
    }

    // ---- Gateway: a grand arch holding a rift -------------------------------------------------
    private void gateway(WorldGenLevel level, BoundingBox clip) {
        BoundingBox b = boundingBox;
        int x0 = b.minX(), z0 = b.minZ(), x1 = b.maxX(), z1 = b.maxZ(), gy = b.minY() + 4;
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                foundation(level, clip, x, gy - 1, z, s(Blocks.POLISHED_BLACKSTONE));
                set(level, clip, x, gy - 1, z, pick(x, 0, z, s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), s(Blocks.POLISHED_BLACKSTONE)));
                for (int y = gy; y < gy + 16; y++) set(level, clip, x, y, z, s(Blocks.AIR));
            }
        int inner = 4;
        for (int side : new int[]{-1, 1}) {
            int px = cx + side * (inner + 2);
            for (int x = px - 1; x <= px + 1; x++)
                for (int z = cz - 1; z <= cz + 1; z++)
                    for (int y = gy; y < gy + 12; y++) set(level, clip, x, y, z, (y - gy) % 4 == 0 ? s(Blocks.CHISELED_POLISHED_BLACKSTONE) : s(Blocks.POLISHED_BLACKSTONE_BRICKS));
            set(level, clip, px, gy + 12, cz, s(Blocks.SOUL_CAMPFIRE));
        }
        for (int x = cx - inner - 3; x <= cx + inner + 3; x++)
            for (int y = gy + 11; y <= gy + 12; y++)
                for (int z = cz - 1; z <= cz + 1; z++) set(level, clip, x, y, z, y == gy + 12 ? s(Blocks.POLISHED_BLACKSTONE_BRICK_SLAB) : s(Blocks.GILDED_BLACKSTONE));
        for (int x = cx - inner - 1; x <= cx + inner + 1; x++) {
            set(level, clip, x, gy, cz, s(Blocks.CRYING_OBSIDIAN));
            set(level, clip, x, gy + 10, cz, s(Blocks.CRYING_OBSIDIAN));
        }
        BlockState rift = s(ModBlocks.RIFT_PORTAL.get()).setValue(RiftPortalBlock.DESTINATION, destination);
        for (int x = cx - inner - 1; x <= cx + inner + 1; x++)
            for (int y = gy + 1; y < gy + 10; y++) set(level, clip, x, y, cz, rift);
        // Obelisks
        for (int[] o : new int[][]{{x0 + 1, z0 + 1}, {x1 - 1, z0 + 1}, {x0 + 1, z1 - 1}, {x1 - 1, z1 - 1}}) {
            int h = 3 + (int) (hash(o[0], 0, o[1]) * 4);
            for (int y = gy; y < gy + h; y++) set(level, clip, o[0], y, o[1], s(Blocks.OBSIDIAN));
            set(level, clip, o[0], gy + h, o[1], s(Blocks.SOUL_LANTERN));
        }
        spawnMob(level, clip, DeadMob.DEPART, cx - 3, gy + 1, cz - 3);
        spawnMob(level, clip, DeadMob.STAR_FALL, cx + 3, gy + 1, cz + 3);
        spawnBoss(level, clip, cx, gy, cz - 3);
    }

    // ---- Portal shrine: a ring of obsidian around a rift -----------------------------------------
    private void portal(WorldGenLevel level, BoundingBox clip) {
        BoundingBox b = boundingBox;
        int cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2, gy = b.minY() + 4;
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > 6.2) continue;
                foundation(level, clip, x, gy - 1, z, s(Blocks.DEEPSLATE_TILES));
                set(level, clip, x, gy - 1, z, d > 5.2 ? s(Blocks.POLISHED_BLACKSTONE_BRICKS) : ((int) d % 2 == 0 ? s(Blocks.DEEPSLATE_TILES) : s(Blocks.CRYING_OBSIDIAN)));
                for (int y = gy; y < gy + 10; y++) set(level, clip, x, y, z, s(Blocks.AIR));
            }
        BlockState rift = s(ModBlocks.RIFT_PORTAL.get()).setValue(RiftPortalBlock.DESTINATION, destination);
        int ry = gy + 4;
        for (int x = cx - 4; x <= cx + 4; x++)
            for (int y = ry - 4; y <= ry + 4; y++) {
                double d = Math.sqrt((x - cx) * (x - cx) + (y - ry) * (y - ry));
                if (d <= 3.2) set(level, clip, x, y, cz, rift);
                else if (d <= 4.4 && y >= gy) set(level, clip, x, y, cz, hash(x, y, cz) < 0.2 ? s(Blocks.CRYING_OBSIDIAN) : s(Blocks.OBSIDIAN));
            }
        for (int[] c : new int[][]{{cx - 4, cz - 4}, {cx + 4, cz - 4}, {cx - 4, cz + 4}, {cx + 4, cz + 4}}) {
            set(level, clip, c[0], gy, c[1], s(Blocks.POLISHED_BLACKSTONE_WALL));
            set(level, clip, c[0], gy + 1, c[1], s(ModBlocks.DREAD_LANTERN.get()));
        }
        spawnBoss(level, clip, cx, gy, cz + 4);
    }

    // ---- Destroyed cities ------------------------------------------------------------------------
    private BlockState wall(int x, int y, int z) {
        return switch (style) {
            case 0 -> pick(x, y, z, s(Blocks.BLACKSTONE), s(Blocks.POLISHED_BLACKSTONE_BRICKS), s(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS), s(Blocks.BLACK_CONCRETE), s(Blocks.COAL_BLOCK));
            case 1 -> pick(x, y, z, s(Blocks.STONE_BRICKS), s(Blocks.CRACKED_STONE_BRICKS), s(Blocks.ANDESITE), s(Blocks.LIGHT_GRAY_CONCRETE), s(Blocks.COBBLESTONE));
            case 2 -> pick(x, y, z, s(Blocks.MOSSY_STONE_BRICKS), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.GRAY_CONCRETE), s(Blocks.MOSS_BLOCK), s(Blocks.CRACKED_STONE_BRICKS));
            case 3 -> pick(x, y, z, s(Blocks.WHITE_CONCRETE), s(Blocks.RED_CONCRETE), s(Blocks.WHITE_TERRACOTTA), s(Blocks.RED_NETHER_BRICKS), s(Blocks.NETHER_WART_BLOCK));
            default -> pick(x, y, z, s(Blocks.GRAY_CONCRETE), s(Blocks.CRACKED_DEEPSLATE_BRICKS), s(Blocks.BASALT), s(Blocks.SMOOTH_BASALT), s(Blocks.TUFF));
        };
    }

    private BlockState debris(int x, int y, int z) {
        return switch (style) {
            case 0 -> pick(x, y, z, s(Blocks.COAL_BLOCK), s(Blocks.BLACKSTONE), s(ModBlocks.CHARRED_LOG.get()), s(Blocks.MAGMA_BLOCK));
            case 1 -> pick(x, y, z, s(Blocks.GRAVEL), s(Blocks.COBBLESTONE), s(Blocks.ANDESITE), s(Blocks.STONE_BRICKS));
            case 2 -> pick(x, y, z, s(Blocks.MOSS_BLOCK), s(Blocks.MOSSY_COBBLESTONE), s(Blocks.COBWEB), s(Blocks.SCULK));
            case 3 -> pick(x, y, z, s(Blocks.BONE_BLOCK), s(Blocks.NETHER_WART_BLOCK), s(Blocks.RED_CONCRETE_POWDER), s(Blocks.WHITE_CONCRETE_POWDER));
            default -> pick(x, y, z, s(Blocks.MAGMA_BLOCK), s(Blocks.OBSIDIAN), s(Blocks.BASALT), s(Blocks.CRYING_OBSIDIAN));
        };
    }

    private void streets(WorldGenLevel level, BoundingBox clip) {
        BoundingBox b = boundingBox;
        for (int x = Math.max(b.minX(), clip.minX()); x <= Math.min(b.maxX(), clip.maxX()); x++)
            for (int z = Math.max(b.minZ(), clip.minZ()); z <= Math.min(b.maxZ(), clip.maxZ()); z++) {
                int lx = Math.floorMod(x - b.minX(), 16), lz = Math.floorMod(z - b.minZ(), 16);
                boolean road = lx < 3 || lz < 3;
                int gy = ground(level, x, z);
                if (!road) {
                    if (hash(x, 1, z) < 0.04) set(level, clip, x, gy, z, debris(x, gy, z));
                    continue;
                }
                double h = hash(x, 0, z);
                BlockState asphalt = h < 0.08 ? s(Blocks.GRAVEL) : h < 0.25 ? s(Blocks.GRAY_CONCRETE_POWDER) : s(Blocks.BLACK_CONCRETE);
                if (style == 4 && h < 0.05) asphalt = s(Blocks.MAGMA_BLOCK);
                boolean marking = (lx == 1 && lz >= 3 && lz % 4 < 2) || (lz == 1 && lx >= 3 && lx % 4 < 2);
                set(level, clip, x, gy - 1, z, marking && h > 0.3 ? s(Blocks.YELLOW_TERRACOTTA) : asphalt);
                for (int y = gy; y < gy + 3; y++) {
                    BlockState above = get(level, clip, x, y, z);
                    if (!above.isAir() && !above.is(Blocks.WATER)) set(level, clip, x, y, z, s(Blocks.AIR));
                }
                double d = hash(x, 2, z);
                if (d < 0.035) set(level, clip, x, gy, z, debris(x, gy, z));
                else if (d < 0.045 && style == 0) fire(level, clip, x, gy, z);
                else if (d < 0.05 && style == 3) set(level, clip, x, gy, z, s(ModBlocks.BLOOD_PUDDLE.get()));
                // Street lamps on corners
                if (lx == 0 && lz == 0) {
                    for (int y = gy; y < gy + 4; y++) set(level, clip, x, y, z, s(Blocks.DARK_OAK_FENCE));
                    if (hash(x, 3, z) < 0.5) set(level, clip, x, gy + 4, z, s(Blocks.LANTERN));
                }
                // Wrecked cars
                if (lx == 1 && lz == 9 && hash(x, 4, z) < 0.35) wreck(level, clip, x, gy, z);
                // Shamblers
                if (lx == 1 && lz == 5 && hash(x, 5, z) < 0.5) spawnMob(level, clip, DeadMob.ROTWALKER, x, gy, z);
            }
    }

    private void wreck(WorldGenLevel level, BoundingBox clip, int x, int y, int z) {
        BlockState body = style == 0 ? s(Blocks.BLACK_CONCRETE) : pick(x, 0, z, s(Blocks.RED_CONCRETE), s(Blocks.BLUE_CONCRETE), s(Blocks.WHITE_CONCRETE), s(Blocks.GREEN_CONCRETE));
        for (int dz = 0; dz < 4; dz++) set(level, clip, x, y, z + dz, body);
        set(level, clip, x, y + 1, z + 1, s(Blocks.IRON_BARS));
        set(level, clip, x, y + 1, z + 2, s(Blocks.IRON_TRAPDOOR));
        if (style == 0) fire(level, clip, x, y + 1, z + 3);
    }

    private void building(WorldGenLevel level, BoundingBox clip, RandomSource random) {
        BoundingBox b = boundingBox;
        int x0 = b.minX(), z0 = b.minZ(), x1 = b.maxX(), z1 = b.maxZ(), gy = b.minY() + 1, h = b.maxY() - gy;
        // Collapse profile: tall jagged tops, one side sheared away
        double shear = hash(0, 0, 0);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean shell = x == x0 || x == x1 || z == z0 || z == z1;
                double slope = shear < 0.5 ? (double) (x - x0) / Math.max(1, x1 - x0) : (double) (z - z0) / Math.max(1, z1 - z0);
                int top = gy + (int) (h * (1.0 - 0.6 * slope * hash(1, 1, 1))) - (int) (hash(x, 0, z) * 4);
                if (style == 1) top -= (int) (hash(x / 2, 9, z / 2) * h * 0.5);
                foundation(level, clip, x, gy - 1, z, wall(x, gy - 1, z));
                set(level, clip, x, gy - 1, z, wall(x, gy - 1, z));
                for (int y = gy; y <= b.maxY(); y++) {
                    if (y > top) {
                        set(level, clip, x, y, z, s(Blocks.AIR));
                        continue;
                    }
                    int fl = (y - gy) % 4;
                    if (shell) {
                        boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                        boolean window = !corner && fl >= 1 && fl <= 2 && ((x - x0) % 3 == 1 || (z - z0) % 3 == 1);
                        if (window) {
                            double wv = hash(x, y, z);
                            set(level, clip, x, y, z, wv < 0.25 ? s(Blocks.IRON_BARS) : wv < 0.35 ? s(Blocks.GLASS_PANE) : s(Blocks.AIR));
                        } else if (hash(x, y, z) < (style == 1 ? 0.16 : 0.07)) {
                            set(level, clip, x, y, z, s(Blocks.AIR));
                        } else {
                            set(level, clip, x, y, z, wall(x, y, z));
                        }
                    } else if (fl == 0 && y > gy) {
                        double fv = hash(x, y, z);
                        set(level, clip, x, y, z, fv < 0.2 ? s(Blocks.AIR) : style == 0 && fv < 0.45 ? s(ModBlocks.CHARRED_LOG.get()) : (style == 0 ? s(Blocks.POLISHED_BLACKSTONE_SLAB) : s(Blocks.SPRUCE_SLAB)).setValue(SlabBlock.TYPE, SlabType.TOP));
                    } else {
                        set(level, clip, x, y, z, s(Blocks.AIR));
                    }
                }
                if (!shell) {
                    double r = hash(x, 3, z);
                    if (r < 0.12) set(level, clip, x, gy, z, debris(x, gy, z));
                    else if (r < 0.15 && style == 3) set(level, clip, x, gy, z, s(ModBlocks.BLOOD_PUDDLE.get()));
                    else if (r < 0.17 && style == 2) set(level, clip, x, gy + 3, z, s(Blocks.COBWEB));
                    else if (r < 0.18 && style == 0) fire(level, clip, x, gy, z);
                }
            }
        // Doorway on the street side
        int dx = (x0 + x1) / 2;
        for (int y = gy; y < gy + 3; y++) set(level, clip, dx, y, z0, s(Blocks.AIR));
        // Upper-floor stairs remnant
        for (int i = 0; i < 4; i++) set(level, clip, x0 + 1 + i, gy + i, z1 - 1, s(Blocks.STONE_BRICK_STAIRS).setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM));
        if (hash(5, 5, 5) < 0.6) chest(level, clip, random, x1 - 1, gy, z1 - 1, "city");
        int horde = 2 + (int) (hash(6, 6, 6) * 3);
        for (int i = 0; i < horde; i++) spawnMob(level, clip, DeadMob.ROTWALKER, x0 + 2 + i % 3 * 2, gy, z0 + 2 + i / 3 * 2);
        if (style == 3 && hash(7, 7, 7) < 0.4) spawnMob(level, clip, DeadMob.BLOOD_DEMON, dx, gy, (z0 + z1) / 2);
        if (style == 0 && hash(7, 7, 7) < 0.4) spawnMob(level, clip, DeadMob.SCORCHED, dx, gy, (z0 + z1) / 2);
        if (style == 2 && hash(7, 7, 7) < 0.4) spawnMob(level, clip, DeadMob.PARASITE, dx, gy, (z0 + z1) / 2);
        if (style == 1 && hash(7, 7, 7) < 0.3) spawnMob(level, clip, DeadMob.COLLAPSE, dx, gy, (z0 + z1) / 2);
        if (style == 4 && hash(7, 7, 7) < 0.4) spawnMob(level, clip, DeadMob.STAR_FALL, dx, gy + 2, (z0 + z1) / 2);
        if (hash(8, 8, 8) < 0.25) spawnMob(level, clip, DeadMob.BANDIT, dx + 1, gy, z0 + 2);
    }

    private void crater(WorldGenLevel level, BoundingBox clip) {
        BoundingBox b = boundingBox;
        int cx = (b.minX() + b.maxX()) / 2, cz = (b.minZ() + b.maxZ()) / 2;
        for (int x = b.minX(); x <= b.maxX(); x++)
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                if (!clip.isInside(new BlockPos(x, clip.minY(), z))) continue;
                double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
                if (d > 5.5) continue;
                int gy = ground(level, x, z);
                int depth = (int) ((5.5 - d) * 0.9);
                for (int y = gy - depth; y < gy + 2; y++) set(level, clip, x, y, z, s(Blocks.AIR));
                set(level, clip, x, gy - depth - 1, z, debris(x, gy, z));
                if (style == 4 && d < 1.5) set(level, clip, x, gy - depth - 1, z, s(ModBlocks.STARDUST_ORE.get()));
                if (style == 0 && hash(x, 1, z) < 0.1) fire(level, clip, x, gy - depth, z);
            }
        int gy = ground(level, cx, cz);
        spawnMob(level, clip, DeadMob.ROTWALKER, cx + 3, gy, cz);
    }
}
