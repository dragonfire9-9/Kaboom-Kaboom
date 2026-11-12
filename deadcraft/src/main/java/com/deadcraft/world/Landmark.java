package com.deadcraft.world;

import com.deadcraft.registry.ModWorldgen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Locale;
import java.util.Optional;

/**
 * The 25 smaller DeadCraft landmarks (chapels, camps, shrines, towers, wrecks...). One structure
 * type; the {@code design} field in the structure JSON picks which one is built by
 * {@link LandmarkPiece}.
 */
public class Landmark extends Structure {
    /** Each design: half-width (x), half-depth (z), height above ground, depth below, where it sits. */
    public enum Design implements StringRepresentable {
        // Overworld
        PLAGUE_CHAPEL(6, 10, 22, 2, Place.SURFACE),
        HANGING_TREE(8, 8, 18, 2, Place.SURFACE),
        BANDIT_CAMP(11, 11, 8, 1, Place.SURFACE),
        HAG_HOVEL(5, 5, 13, 2, Place.SURFACE),
        SUNKEN_CRYPT(6, 8, 6, 12, Place.SURFACE),
        BLOODIED_FARM(12, 10, 10, 1, Place.SURFACE),
        BONE_PIT(9, 9, 6, 13, Place.SURFACE),
        RITUAL_CIRCLE(10, 10, 7, 3, Place.SURFACE),
        RUINED_WATCHTOWER(5, 5, 22, 1, Place.SURFACE),
        FALLEN_STAR(10, 10, 10, 6, Place.SURFACE),
        // Hell's Paradise
        FLESH_SHRINE(7, 7, 12, 2, Place.CAVERN),
        IMPALER_FIELD(11, 11, 9, 1, Place.CAVERN),
        DEMON_FORGE(8, 7, 14, 2, Place.CAVERN),
        CAGE_GALLOWS(7, 4, 15, 1, Place.CAVERN),
        // Skyfall Torn
        SKY_TEMPLE(9, 9, 14, 2, Place.ISLAND),
        BELL_TOWER(4, 4, 26, 1, Place.ISLAND),
        WRECKED_AIRSHIP(10, 5, 16, 3, Place.ISLAND),
        OBSERVATORY(6, 6, 14, 1, Place.ISLAND),
        // Light's Descent
        CATHEDRAL_RUIN(8, 14, 20, 1, Place.SURFACE),
        MESA_OUTPOST(9, 9, 12, 1, Place.SURFACE),
        GALLOWS_SQUARE(8, 8, 8, 1, Place.SURFACE),
        ABANDONED_MINE(4, 14, 8, 22, Place.SURFACE),
        // Disposed Skies
        VOID_OBELISK(7, 7, 28, 2, Place.ISLAND),
        STORM_SPIRE(5, 5, 24, 1, Place.ISLAND),
        SHATTERED_SANCTUM(8, 8, 12, 2, Place.ISLAND),
        // Second wave: new creatures and the lairs of the new bosses
        SLAUGHTERHOUSE(9, 7, 12, 1, Place.SURFACE),
        QUARANTINE_CAMP(11, 11, 6, 1, Place.SURFACE),
        SCARECROW_FIELD(10, 10, 6, 1, Place.SURFACE),
        LAMPLIGHTER_ROAD(4, 16, 7, 1, Place.SURFACE),
        HIVE_NEST(12, 12, 16, 3, Place.CAVERN),
        BRIDE_CHAPEL(8, 13, 18, 1, Place.ISLAND),
        SKYRAY_ROOST(8, 8, 18, 1, Place.ISLAND),
        WYRM_BURROW(9, 9, 6, 16, Place.SURFACE),
        HANGMANS_HILL(10, 10, 14, 1, Place.SURFACE),
        CRYSTAL_GARDEN(10, 10, 16, 1, Place.ISLAND);

        public static final Codec<Design> CODEC = StringRepresentable.fromEnum(Design::values);
        public final int hx, hz, height, depth;
        public final Place place;

        Design(int hx, int hz, int height, int depth, Place place) {
            this.hx = hx;
            this.hz = hz;
            this.height = height;
            this.depth = depth;
            this.place = place;
        }

        @Override
        public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }

    public enum Place { SURFACE, CAVERN, ISLAND }

    public static final MapCodec<Landmark> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Design.CODEC.fieldOf("design").forGetter(s -> s.design)
    ).apply(i, Landmark::new));

    private final Design design;

    public Landmark(StructureSettings settings, Design design) {
        super(settings);
        this.design = design;
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.LANDMARK.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX(), z = cp.getMiddleBlockZ();
        int y = switch (design.place) {
            case CAVERN -> cavernFloor(ctx, x, z, design.height);
            case SURFACE, ISLAND -> surface(ctx, x, z);
        };
        if (y == Integer.MIN_VALUE) return Optional.empty();
        // the whole footprint must be on solid ground of roughly one height (no cliff edges)
        if (design.place != Place.CAVERN) {
            int lo = y, hi = y;
            for (int[] c : new int[][]{{-design.hx, -design.hz}, {design.hx, -design.hz}, {-design.hx, design.hz}, {design.hx, design.hz}}) {
                int h = surface(ctx, x + c[0], z + c[1]);
                if (h == Integer.MIN_VALUE) return Optional.empty();
                lo = Math.min(lo, h);
                hi = Math.max(hi, h);
            }
            if (hi - lo > (design.place == Place.ISLAND ? 5 : 8)) return Optional.empty();
        }
        long seed = ctx.random().nextLong();
        BoundingBox box = new BoundingBox(x - design.hx - 1, y - design.depth - 1, z - design.hz - 1,
                x + design.hx + 1, y + design.height, z + design.hz + 1);
        int fy = y;
        return Optional.of(new GenerationStub(new net.minecraft.core.BlockPos(x, y, z),
                b -> b.addPiece(new LandmarkPiece(design, seed, x, fy, z, box))));
    }

    private static int surface(GenerationContext ctx, int x, int z) {
        int top = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, ctx.heightAccessor(), ctx.randomState());
        int floor = ctx.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
        if (top <= ctx.heightAccessor().getMinBuildHeight() + 4) return Integer.MIN_VALUE;
        if (top - floor > 1) return Integer.MIN_VALUE;
        return top;
    }

    /** The first open floor (with enough headroom) above the blood sea of a ceiling dimension. */
    private static int cavernFloor(GenerationContext ctx, int x, int z, int headroom) {
        NoiseColumn col = ctx.chunkGenerator().getBaseColumn(x, z, ctx.heightAccessor(), ctx.randomState());
        int min = ctx.heightAccessor().getMinBuildHeight();
        int need = Math.min(headroom, 14);
        for (int y = min + 50; y < min + 150; y++) {
            BlockState below = col.getBlock(y - 1);
            if (below.isAir() || !below.getFluidState().isEmpty()) continue;
            boolean open = true;
            for (int dy = 0; dy < need && open; dy++) open = col.getBlock(y + dy).isAir();
            if (open) return y;
        }
        return Integer.MIN_VALUE;
    }
}
