package dev.soulbound.module.visual;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.render.WorldDraw;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.HeaderSetting;
import dev.soulbound.setting.ModeSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Highlights ores (and spawners/chests) through walls.
 *
 * <p>Lag-free by design: chunks are scanned a few sections per tick in the background, sections
 * with no wanted block are skipped with a palette check (no per-block loop), results are cached per
 * chunk and only re-scanned every few seconds, and drawing is capped and batched into a couple of
 * draw calls per frame.
 */
public final class OreEsp extends Module {
    private record Ore(BlockPos pos, int color) {}

    private final List<BoolSetting> toggles;
    private final NumberSetting range, maxBoxes, opacity, rescan, budget;
    private final ModeSetting style;
    private final BoolSetting exposedOnly, throughWalls, fade;

    private final Map<Long, List<Ore>> found = new HashMap<>();
    private final Map<Long, Long> scannedAt = new HashMap<>();
    private final Map<Long, List<Ore>> building = new HashMap<>(); // a re-scan fills this, then swaps in (no flicker)
    private final ArrayDeque<long[]> queue = new ArrayDeque<>(); // {chunk, sectionIndex, isLast}
    private Map<Block, Integer> wanted = Map.of();
    private int settingsHash;

    public OreEsp() {
        super("Ore ESP", "Highlight ores through walls.", Category.VISUAL);
        add(new HeaderSetting("ORES"));
        toggles = Ores.addToggles(this);
        add(new HeaderSetting("LOOK"));
        style = add(new ModeSetting("Style", "How each ore is drawn", "Both", "Outline", "Filled", "Both"));
        opacity = add(new NumberSetting("Fill Opacity", "How solid the fill is", 25, 5, 80, 5, "%").visibleWhen(() -> !style.is("Outline")));
        throughWalls = add(new BoolSetting("Through Walls", "See them through blocks", true));
        fade = add(new BoolSetting("Distance Fade", "Fade far ores so close ones stand out", true));
        add(new HeaderSetting("SCANNING"));
        range = add(new NumberSetting("Range", "How far to look, in blocks", 48, 16, 128, 8, "m"));
        exposedOnly = add(new BoolSetting("Exposed Only", "Only ores touching air, water or lava (cuts the noise in caves)", false));
        maxBoxes = add(new NumberSetting("Max Shown", "Draw at most this many, nearest first", 600, 50, 4000, 50));
        rescan = add(new NumberSetting("Rescan", "Re-check each chunk this often", 4, 1, 30, 1, "s"));
        budget = add(new NumberSetting("Scan Speed", "Chunk sections scanned per tick", 24, 4, 128, 4));
        WorldDraw.register(this::draw);
    }

    @Override
    protected void onEnable() { clear(); }

    @Override
    protected void onDisable() { clear(); }

    private void clear() {
        found.clear();
        building.clear();
        scannedAt.clear();
        queue.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        int hash = toggles.stream().mapToInt(b -> b.isOn() ? 1 : 0).reduce(0, (a, b) -> a * 2 + b) * 31 + (exposedOnly.isOn() ? 1 : 0);
        if (hash != settingsHash) {
            settingsHash = hash;
            wanted = Ores.enabled(toggles);
            clear();
        }
        if (wanted.isEmpty()) return;
        int r = (int) Math.ceil(range.get() / 16.0);
        ChunkPos center = mc.player.getChunkPos();
        long now = System.currentTimeMillis();
        // forget far chunks
        found.keySet().removeIf(k -> Math.abs(ChunkPos.getPackedX(k) - center.x) > r + 1 || Math.abs(ChunkPos.getPackedZ(k) - center.z) > r + 1);
        scannedAt.keySet().removeIf(k -> !found.containsKey(k) && (Math.abs(ChunkPos.getPackedX(k) - center.x) > r + 1 || Math.abs(ChunkPos.getPackedZ(k) - center.z) > r + 1));
        // queue chunks that are due, nearest first
        if (queue.isEmpty()) {
            for (int ring = 0; ring <= r; ring++) {
                for (int dx = -ring; dx <= ring; dx++) {
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                        long key = ChunkPos.toLong(center.x + dx, center.z + dz);
                        Long at = scannedAt.get(key);
                        if (at != null && now - at < rescan.get() * 1000) continue;
                        if (!(mc.world.getChunk(center.x + dx, center.z + dz, net.minecraft.world.chunk.ChunkStatus.FULL, false) instanceof WorldChunk wc)) continue;
                        scannedAt.put(key, now);
                        building.put(key, new ArrayList<>());
                        int n = wc.getSectionArray().length;
                        for (int s = 0; s < n; s++) queue.add(new long[]{key, s, s == n - 1 ? 1 : 0});
                    }
                }
            }
        }
        int left = budget.getInt();
        while (left-- > 0 && !queue.isEmpty()) scanSection(queue.poll());
    }

    private void scanSection(long[] job) {
        try {
            scanInto(job);
        } finally {
            if (job[2] == 1) {
                List<Ore> done = building.remove(job[0]);
                if (done != null) found.put(job[0], done);
            }
        }
    }

    private void scanInto(long[] job) {
        long key = job[0];
        int cx = ChunkPos.getPackedX(key), cz = ChunkPos.getPackedZ(key);
        if (!(mc.world.getChunk(cx, cz, net.minecraft.world.chunk.ChunkStatus.FULL, false) instanceof WorldChunk wc)) return;
        ChunkSection[] sections = wc.getSectionArray();
        int si = (int) job[1];
        if (si >= sections.length) return;
        ChunkSection sec = sections[si];
        if (sec == null || sec.isEmpty()) return;
        Map<Block, Integer> want = wanted;
        if (!sec.hasAny(st -> want.containsKey(st.getBlock()))) return; // palette check: no per-block loop
        List<Ore> list = building.computeIfAbsent(key, k -> new ArrayList<>());
        int baseY = mc.world.sectionIndexToCoord(si) * 16;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState st = sec.getBlockState(x, y, z);
                    Integer color = want.get(st.getBlock());
                    if (color == null) continue;
                    m.set(cx * 16 + x, baseY + y, cz * 16 + z);
                    if (exposedOnly.isOn() && !exposed(m)) continue;
                    list.add(new Ore(m.toImmutable(), color));
                }
            }
        }
    }

    private boolean exposed(BlockPos p) {
        for (Direction d : Direction.values()) {
            BlockState n = mc.world.getBlockState(p.offset(d));
            if (n.isAir() || !n.getFluidState().isEmpty()) return true;
        }
        return false;
    }

    private void draw(WorldDraw d) {
        if (!isEnabled() || found.isEmpty()) return;
        double maxSq = range.get() * range.get();
        // nearest first, capped
        List<Ore> near = new ArrayList<>();
        for (List<Ore> l : found.values()) {
            for (Ore o : l) {
                if (d.distSq(o.pos.getX() + 0.5, o.pos.getY() + 0.5, o.pos.getZ() + 0.5) > maxSq) continue;
                if (!wanted.containsKey(mc.world.getBlockState(o.pos).getBlock())) continue; // mined since the last scan
                near.add(o);
            }
        }
        if (near.size() > maxBoxes.getInt()) {
            near.sort((a, b) -> Double.compare(d.distSq(a.pos.getX(), a.pos.getY(), a.pos.getZ()), d.distSq(b.pos.getX(), b.pos.getY(), b.pos.getZ())));
            near = near.subList(0, maxBoxes.getInt());
        }
        boolean see = throughWalls.isOn();
        VertexConsumer fill = style.is("Outline") ? null : d.fill(see);
        float fillA = opacity.getFloat() / 100f;
        for (Ore o : near) {
            double dist = Math.sqrt(d.distSq(o.pos.getX() + 0.5, o.pos.getY() + 0.5, o.pos.getZ() + 0.5));
            float k = fade.isOn() ? (float) Math.max(0.25, 1 - dist / range.get()) : 1f;
            int x = o.pos.getX(), y = o.pos.getY(), z = o.pos.getZ();
            if (fill != null) d.filled(fill, x + 0.02, y + 0.02, z + 0.02, x + 0.98, y + 0.98, z + 0.98, alpha(o.color, fillA * k));
        }
        if (!style.is("Filled")) {
            VertexConsumer lines = d.lines(see);
            for (Ore o : near) {
                double dist = Math.sqrt(d.distSq(o.pos.getX() + 0.5, o.pos.getY() + 0.5, o.pos.getZ() + 0.5));
                float k = fade.isOn() ? (float) Math.max(0.3, 1 - dist / range.get()) : 1f;
                int x = o.pos.getX(), y = o.pos.getY(), z = o.pos.getZ();
                d.box(lines, x + 0.01, y + 0.01, z + 0.01, x + 0.99, y + 0.99, z + 0.99, alpha(o.color, 0.9f * k), 2f);
            }
        }
    }

    @Override
    public String getIcon() { return "icon_visual"; }

    private static int alpha(int c, float a) { return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (c & 0xFFFFFF); }

    /** Count of ores currently known (for the module list badge). */
    public int count() {
        int n = 0;
        for (List<Ore> l : found.values()) n += l.size();
        return n;
    }

}
