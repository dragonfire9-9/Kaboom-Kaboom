package dev.soulbound.schem;

import dev.soulbound.SoulBound;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Reads schematics from SoulBound's own folder: Sponge .schem (v2 and v3), Litematica
 * .litematic and vanilla structure .nbt files.
 */
public final class SchematicLoader {
    private SchematicLoader() {}

    public static Path folder() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("soulbound").resolve("schematics");
        try {
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                // first run: drop in a small example to try
                try (var in = SchematicLoader.class.getResourceAsStream("/assets/soulbound/schematics/starter_hut.schem")) {
                    if (in != null) Files.copy(in, dir.resolve("starter_hut.schem"));
                }
            }
        } catch (IOException ignored) { }
        return dir;
    }

    /** Schematic files in the folder, newest first. */
    public static List<Path> list() {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder())) {
            s.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".schem") || n.endsWith(".litematic") || n.endsWith(".nbt");
            }).forEach(out::add);
        } catch (IOException e) {
            SoulBound.LOGGER.warn("Could not list schematics", e);
        }
        out.sort((a, b) -> {
            try {
                return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
            } catch (IOException e) {
                return 0;
            }
        });
        return out;
    }

    public static Schematic load(Path file) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes());
        String fname = file.getFileName().toString();
        String name = fname.contains(".") ? fname.substring(0, fname.lastIndexOf('.')) : fname;
        String lower = fname.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".litematic")) return litematic(name, file, root);
        if (lower.endsWith(".nbt")) return structure(name, file, root);
        return sponge(name, file, root);
    }

    // ------------------------------------------------------------------ Sponge .schem
    private static Schematic sponge(String name, Path file, NbtCompound root) {
        NbtCompound s = root.getCompound("Schematic").orElse(root); // v3 wraps everything in "Schematic"
        int w = s.getShort("Width", (short) 0), h = s.getShort("Height", (short) 0), l = s.getShort("Length", (short) 0);
        NbtCompound palette;
        byte[] data;
        if (s.contains("Blocks")) { // v3
            NbtCompound blocks = s.getCompoundOrEmpty("Blocks");
            palette = blocks.getCompoundOrEmpty("Palette");
            data = blocks.getByteArray("Data").orElse(new byte[0]);
        } else { // v2
            palette = s.getCompoundOrEmpty("Palette");
            data = s.getByteArray("BlockData").orElse(new byte[0]);
        }
        BlockState[] states = new BlockState[palette.getKeys().size() + 1];
        for (String key : palette.getKeys()) {
            int id = palette.getInt(key, 0);
            if (id >= 0 && id < states.length) states[id] = parseState(key);
        }
        List<Schematic.Entry> out = new ArrayList<>();
        int index = 0, i = 0;
        while (i < data.length) {
            int value = 0, shift = 0, b;
            do { // varint
                b = data[i++];
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && i < data.length);
            int y = index / (w * l), rem = index % (w * l), z = rem / w, x = rem % w;
            BlockState st = value < states.length ? states[value] : null;
            if (st != null && !st.isAir()) out.add(new Schematic.Entry(new BlockPos(x, y, z), st));
            index++;
        }
        return new Schematic(name, "SCHEM", file, w, h, l, out);
    }

    /** "minecraft:oak_stairs[facing=east,half=bottom]" → BlockState. */
    private static BlockState parseState(String s) {
        NbtCompound tag = new NbtCompound();
        int br = s.indexOf('[');
        tag.putString("Name", br < 0 ? s : s.substring(0, br));
        if (br >= 0 && s.endsWith("]")) {
            NbtCompound props = new NbtCompound();
            for (String kv : s.substring(br + 1, s.length() - 1).split(",")) {
                int eq = kv.indexOf('=');
                if (eq > 0) props.putString(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
            tag.put("Properties", props);
        }
        return NbtHelper.toBlockState(Registries.BLOCK, tag);
    }

    // ------------------------------------------------------------------ Litematica
    private static Schematic litematic(String name, Path file, NbtCompound root) {
        NbtCompound regions = root.getCompoundOrEmpty("Regions");
        List<Schematic.Entry> raw = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (String key : regions.getKeys()) {
            NbtCompound r = regions.getCompoundOrEmpty(key);
            NbtCompound pos = r.getCompoundOrEmpty("Position"), size = r.getCompoundOrEmpty("Size");
            int sx = size.getInt("x", 0), sy = size.getInt("y", 0), sz = size.getInt("z", 0);
            int ox = pos.getInt("x", 0) + (sx < 0 ? sx + 1 : 0), oy = pos.getInt("y", 0) + (sy < 0 ? sy + 1 : 0), oz = pos.getInt("z", 0) + (sz < 0 ? sz + 1 : 0);
            sx = Math.abs(sx);
            sy = Math.abs(sy);
            sz = Math.abs(sz);
            NbtList pal = r.getListOrEmpty("BlockStatePalette");
            BlockState[] states = new BlockState[pal.size()];
            for (int i = 0; i < pal.size(); i++) states[i] = NbtHelper.toBlockState(Registries.BLOCK, pal.getCompoundOrEmpty(i));
            long[] arr = r.getLongArray("BlockStates").orElse(new long[0]);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, states.length - 1)));
            long mask = (1L << bits) - 1;
            long volume = (long) sx * sy * sz;
            for (long idx = 0; idx < volume; idx++) {
                long start = idx * bits;
                int si = (int) (start >> 6), ei = (int) (((idx + 1) * bits - 1) >> 6), sb = (int) (start & 63);
                if (ei >= arr.length) break;
                long v = si == ei ? (arr[si] >>> sb) & mask : ((arr[si] >>> sb) | (arr[ei] << (64 - sb))) & mask;
                if (v <= 0 || v >= states.length) continue;
                BlockState st = states[(int) v];
                if (st.isAir()) continue;
                int x = (int) (idx % sx), z = (int) ((idx / sx) % sz), y = (int) (idx / ((long) sx * sz));
                int ax = ox + x, ay = oy + y, az = oz + z;
                raw.add(new Schematic.Entry(new BlockPos(ax, ay, az), st));
                minX = Math.min(minX, ax); minY = Math.min(minY, ay); minZ = Math.min(minZ, az);
                maxX = Math.max(maxX, ax); maxY = Math.max(maxY, ay); maxZ = Math.max(maxZ, az);
            }
        }
        return normalise(name, "LITEMATIC", file, raw, minX, minY, minZ, maxX, maxY, maxZ);
    }

    // ------------------------------------------------------------------ vanilla structure .nbt
    private static Schematic structure(String name, Path file, NbtCompound root) {
        NbtList pal = root.getListOrEmpty("palette");
        BlockState[] states = new BlockState[pal.size()];
        for (int i = 0; i < pal.size(); i++) states[i] = NbtHelper.toBlockState(Registries.BLOCK, pal.getCompoundOrEmpty(i));
        NbtList size = root.getListOrEmpty("size");
        List<Schematic.Entry> out = new ArrayList<>();
        NbtList blocks = root.getListOrEmpty("blocks");
        for (int i = 0; i < blocks.size(); i++) {
            NbtCompound b = blocks.getCompoundOrEmpty(i);
            NbtList pos = b.getListOrEmpty("pos");
            int st = b.getInt("state", -1);
            if (st < 0 || st >= states.length || states[st].isAir()) continue;
            out.add(new Schematic.Entry(new BlockPos(pos.getInt(0, 0), pos.getInt(1, 0), pos.getInt(2, 0)), states[st]));
        }
        return new Schematic(name, "NBT", file, size.getInt(0, 0), size.getInt(1, 0), size.getInt(2, 0), out);
    }

    private static Schematic normalise(String name, String fmt, Path file, List<Schematic.Entry> raw,
                                       int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (raw.isEmpty()) return new Schematic(name, fmt, file, 0, 0, 0, raw);
        List<Schematic.Entry> out = new ArrayList<>(raw.size());
        for (Schematic.Entry e : raw) out.add(new Schematic.Entry(e.pos().add(-minX, -minY, -minZ), e.state()));
        return new Schematic(name, fmt, file, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, out);
    }

    @SuppressWarnings("unused")
    private static String str(NbtElement e) { return e instanceof NbtString s ? s.value() : ""; }
}
