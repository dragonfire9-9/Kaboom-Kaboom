package com.deadcraft.world;

import com.deadcraft.registry.ModWorldgen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Locale;
import java.util.Optional;

/**
 * Every DeadCraft structure: one structure type configured from JSON
 * (data/deadcraft/worldgen/structure/*.json). The pieces are generated procedurally so every
 * tomb, castle and city is different.
 */
public class DeadStructure extends Structure {
    public enum Kind implements StringRepresentable {
        TOMB, GRAVEYARD, CASTLE, GATEWAY, PORTAL, RUINED_CITY;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public static final MapCodec<DeadStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Kind.CODEC.fieldOf("kind").forGetter(s -> s.kind),
            Codec.intRange(0, 4).optionalFieldOf("destination", 1).forGetter(s -> s.destination),
            ResourceLocation.CODEC.optionalFieldOf("boss").forGetter(s -> s.boss),
            Codec.floatRange(0f, 1f).optionalFieldOf("boss_chance", 0f).forGetter(s -> s.bossChance),
            Codec.BOOL.optionalFieldOf("cavern", false).forGetter(s -> s.cavern)
    ).apply(i, DeadStructure::new));

    private final Kind kind;
    private final int destination;
    private final Optional<ResourceLocation> boss;
    private final float bossChance;
    private final boolean cavern;

    public DeadStructure(StructureSettings settings, Kind kind, int destination, Optional<ResourceLocation> boss, float bossChance, boolean cavern) {
        super(settings);
        this.kind = kind;
        this.destination = destination;
        this.boss = boss;
        this.bossChance = bossChance;
        this.cavern = cavern;
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.DEAD_STRUCTURE.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        int y = cavern ? cavernFloor(ctx, x, z) : surface(ctx, x, z);
        if (y == Integer.MIN_VALUE) return Optional.empty();
        long seed = ctx.random().nextLong();
        String bossId = boss.map(ResourceLocation::toString).orElse("");
        boolean spawnBoss = !bossId.isEmpty() && ctx.random().nextFloat() < bossChance;
        BlockPos origin = new BlockPos(x, y, z);
        return Optional.of(new GenerationStub(origin, b -> addPieces(b, ctx, origin, seed, spawnBoss ? bossId : "")));
    }

    /** Surface height, or MIN_VALUE when the spot is under water or over the void. */
    private static int surface(GenerationContext ctx, int x, int z) {
        int top = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        int floor = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
        if (top <= ctx.heightAccessor().getMinBuildHeight() + 4) return Integer.MIN_VALUE;
        if (top - floor > 1) return Integer.MIN_VALUE;
        return top;
    }

    /** For ceiling dimensions: the first open floor above the lava sea. */
    private static int cavernFloor(GenerationContext ctx, int x, int z) {
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        int min = ctx.heightAccessor().getMinBuildHeight();
        for (int y = min + 34; y < min + 110; y++) {
            BlockState below = col.getBlock(y - 1);
            if (below.isAir() || !below.getFluidState().isEmpty()) continue;
            boolean open = true;
            for (int dy = 0; dy < 8 && open; dy++) open = col.getBlock(y + dy).isAir();
            if (open) return y;
        }
        return Integer.MIN_VALUE;
    }

    private void addPieces(StructurePiecesBuilder builder, GenerationContext ctx, BlockPos o, long seed, String bossId) {
        RandomSource r = RandomSource.create(seed);
        int x = o.getX(), y = o.getY(), z = o.getZ();
        switch (kind) {
            case TOMB -> builder.addPiece(new DeadPiece(DeadPiece.Shape.TOMB, 0, seed, destination, bossId,
                    new BoundingBox(x - 7, y - 11, z - 7, x + 7, y + 7, z + 11)));
            case GRAVEYARD -> builder.addPiece(new DeadPiece(DeadPiece.Shape.GRAVEYARD, 0, seed, destination, bossId,
                    new BoundingBox(x - 11, y - 3, z - 11, x + 11, y + 8, z + 11)));
            case CASTLE -> builder.addPiece(new DeadPiece(DeadPiece.Shape.CASTLE, 0, seed, destination, bossId,
                    new BoundingBox(x - 16, y - 1, z - 16, x + 16, y + 24, z + 16)));
            case GATEWAY -> builder.addPiece(new DeadPiece(DeadPiece.Shape.GATEWAY, 0, seed, destination, bossId,
                    new BoundingBox(x - 9, y - 4, z - 5, x + 9, y + 16, z + 5)));
            case PORTAL -> builder.addPiece(new DeadPiece(DeadPiece.Shape.PORTAL, 0, seed, destination, bossId,
                    new BoundingBox(x - 6, y - 4, z - 6, x + 6, y + 10, z + 6)));
            case RUINED_CITY -> addCity(builder, x, y, z, seed, r);
        }
    }

    /** A grid of wrecked buildings and streets in one of five destruction styles. */
    private void addCity(StructurePiecesBuilder builder, int cx, int cy, int cz, long seed, RandomSource r) {
        int style = r.nextInt(DeadPiece.CITY_STYLES);
        int lots = 4;
        int lot = 16;
        int half = lots * lot / 2;
        int minX = cx - half, minZ = cz - half;
        builder.addPiece(new DeadPiece(DeadPiece.Shape.STREETS, style, seed, 0, "",
                new BoundingBox(minX, cy - 1, minZ, minX + lots * lot - 1, cy + 4, minZ + lots * lot - 1)));
        for (int i = 0; i < lots; i++) {
            for (int j = 0; j < lots; j++) {
                int x0 = minX + i * lot + 3;
                int z0 = minZ + j * lot + 3;
                long pieceSeed = seed ^ (i * 341873128712L + j * 132897987541L);
                RandomSource pr = RandomSource.create(pieceSeed);
                if (pr.nextFloat() < 0.78f) {
                    int w = 7 + pr.nextInt(4);
                    int d = 7 + pr.nextInt(4);
                    int h = 8 + pr.nextInt(style == 1 ? 12 : 22);
                    builder.addPiece(new DeadPiece(DeadPiece.Shape.BUILDING, style, pieceSeed, 0, "",
                            new BoundingBox(x0, cy - 1, z0, x0 + w - 1, cy + h, z0 + d - 1)));
                } else {
                    builder.addPiece(new DeadPiece(DeadPiece.Shape.CRATER, style, pieceSeed, 0, "",
                            new BoundingBox(x0, cy - 6, z0, x0 + 9, cy + 4, z0 + 9)));
                }
            }
        }
    }
}
