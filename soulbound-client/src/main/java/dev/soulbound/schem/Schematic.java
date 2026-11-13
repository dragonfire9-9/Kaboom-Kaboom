package dev.soulbound.schem;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A loaded schematic: its size and every non-air block, relative to its minimum corner. */
public final class Schematic {
    public record Entry(BlockPos pos, BlockState state) {}

    public final String name, format;
    public final Path file;
    public final int sizeX, sizeY, sizeZ;
    private final List<Entry> blocks;

    public Schematic(String name, String format, Path file, int sizeX, int sizeY, int sizeZ, List<Entry> blocks) {
        this.name = name;
        this.format = format;
        this.file = file;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.blocks = blocks;
    }

    public List<Entry> blocks() { return Collections.unmodifiableList(blocks); }

    public int blockCount() { return blocks.size(); }

    /** Blocks placed at {@code origin} with {@code rotation} around the vertical axis, sorted bottom-up. */
    public List<Entry> placed(BlockPos origin, BlockRotation rotation) {
        List<Entry> out = new ArrayList<>(blocks.size());
        for (Entry e : blocks) {
            BlockPos r = rotate(e.pos(), rotation);
            out.add(new Entry(origin.add(r), e.state().rotate(rotation)));
        }
        out.sort((a, b) -> a.pos().getY() != b.pos().getY() ? Integer.compare(a.pos().getY(), b.pos().getY())
                : Integer.compare(a.pos().getX() * 31 + a.pos().getZ(), b.pos().getX() * 31 + b.pos().getZ()));
        return out;
    }

    private BlockPos rotate(BlockPos p, BlockRotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> new BlockPos(sizeZ - 1 - p.getZ(), p.getY(), p.getX());
            case CLOCKWISE_180 -> new BlockPos(sizeX - 1 - p.getX(), p.getY(), sizeZ - 1 - p.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(p.getZ(), p.getY(), sizeX - 1 - p.getX());
            default -> p;
        };
    }

    /** Count of each block type, most common first. */
    public List<Map.Entry<BlockState, Integer>> materials() {
        Map<net.minecraft.block.Block, BlockState> rep = new HashMap<>();
        Map<net.minecraft.block.Block, Integer> counts = new HashMap<>();
        for (Entry e : blocks) {
            if (e.state().isOf(Blocks.AIR)) continue;
            rep.putIfAbsent(e.state().getBlock(), e.state());
            counts.merge(e.state().getBlock(), 1, Integer::sum);
        }
        List<Map.Entry<BlockState, Integer>> out = new ArrayList<>();
        counts.forEach((b, c) -> out.add(Map.entry(rep.get(b), c)));
        out.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return out;
    }
}
