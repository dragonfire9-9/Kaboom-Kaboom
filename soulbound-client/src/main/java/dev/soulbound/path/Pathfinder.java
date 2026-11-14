package dev.soulbound.path;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * A* over standable block positions (feet). Moves: 8-way walking (no corner cutting), one-block
 * step up, and drops of up to three blocks. Avoids lava, fire, water, cactus and berry bushes.
 */
public final class Pathfinder {
    private static final int MAX_NODES = 6000;

    private Pathfinder() {}

    private record Node(BlockPos pos, double g, double f) {}

    /** Finds a path from {@code start} to any position accepted by {@code goal}; empty if none. */
    public static List<BlockPos> find(World w, BlockPos start, Predicate<BlockPos> goal, BlockPos heuristicTarget) {
        return find(w, start, goal, heuristicTarget, 3, null);
    }

    public static List<BlockPos> find(World w, BlockPos start, Predicate<BlockPos> goal, BlockPos heuristicTarget, int maxDrop) {
        return find(w, start, goal, heuristicTarget, maxDrop, null);
    }

    /**
     * As above, allowing drops of up to {@code maxDrop} blocks. With {@code pillar}, the path may also
     * pillar straight up from any feet position the predicate accepts (jump and place a block there):
     * a node directly above its parent, where no block exists yet, is a pillar step.
     */
    public static List<BlockPos> find(World w, BlockPos start, Predicate<BlockPos> goal, BlockPos heuristicTarget, int maxDrop,
                                      Predicate<BlockPos> pillar) {
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
        Map<BlockPos, Double> best = new HashMap<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        start = start.toImmutable();
        open.add(new Node(start, 0, h(start, heuristicTarget)));
        best.put(start, 0.0);
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node n = open.poll();
            if (n.g > best.getOrDefault(n.pos, Double.MAX_VALUE) + 1e-6) continue;
            if (goal.test(n.pos)) return rebuild(parent, n.pos);
            if (pillar != null && passable(w, n.pos.up(2)) && pillar.test(n.pos)) {
                relax(open, best, parent, n, n.pos.up(), 5.0, heuristicTarget); // pillar up one (pricier than walking round)
            }
            // a pillared node floats until its block is placed: it can only keep pillaring
            if (!standable(w, n.pos) && !n.pos.equals(start)) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    boolean diag = dx != 0 && dz != 0;
                    if (diag && (!passable(w, n.pos.add(dx, 0, 0)) || !passable(w, n.pos.add(0, 0, dz)))) continue;
                    BlockPos side = n.pos.add(dx, 0, dz);
                    double base = diag ? 1.414 : 1.0;
                    if (standable(w, side)) {
                        relax(open, best, parent, n, side, base, heuristicTarget);
                    } else if (!diag && standable(w, side.up()) && headroom(w, n.pos.up(2))) {
                        relax(open, best, parent, n, side.up(), base + 0.6, heuristicTarget); // step / jump up
                    } else if (passable(w, side)) {
                        for (int drop = 1; drop <= maxDrop; drop++) {
                            BlockPos down = side.down(drop);
                            if (standable(w, down)) {
                                relax(open, best, parent, n, down, base + 0.3 * drop + (drop > 3 ? 2.0 * drop : 0), heuristicTarget);
                                break;
                            }
                            if (!passable(w, down)) break;
                        }
                    }
                }
            }
        }
        return List.of();
    }

    private static void relax(PriorityQueue<Node> open, Map<BlockPos, Double> best, Map<BlockPos, BlockPos> parent,
                              Node from, BlockPos to, double cost, BlockPos target) {
        to = to.toImmutable();
        double g = from.g + cost;
        if (g < best.getOrDefault(to, Double.MAX_VALUE)) {
            best.put(to, g);
            parent.put(to, from.pos);
            open.add(new Node(to, g, g + h(to, target)));
        }
    }

    private static double h(BlockPos a, BlockPos b) {
        double dx = Math.abs(a.getX() - b.getX()), dz = Math.abs(a.getZ() - b.getZ()), dy = Math.abs(a.getY() - b.getY());
        return Math.max(dx, dz) + 0.414 * Math.min(dx, dz) + dy * 0.5;
    }

    private static List<BlockPos> rebuild(Map<BlockPos, BlockPos> parent, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos p = end; p != null; p = parent.get(p)) path.add(p);
        Collections.reverse(path);
        return path;
    }

    /** Feet and head fit, and there is solid ground underneath. */
    public static boolean standable(World w, BlockPos feet) {
        return passable(w, feet) && passable(w, feet.up()) && solidTop(w, feet.down());
    }

    private static boolean headroom(World w, BlockPos p) { return passable(w, p); }

    private static boolean passable(World w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        if (danger(s) || !s.getFluidState().isEmpty()) return false;
        return s.getCollisionShape(w, p).isEmpty();
    }

    private static boolean solidTop(World w, BlockPos p) {
        BlockState s = w.getBlockState(p);
        if (danger(s)) return false;
        return !s.getCollisionShape(w, p).isEmpty() && s.getCollisionShape(w, p).getMax(net.minecraft.util.math.Direction.Axis.Y) >= 0.5;
    }

    private static boolean danger(BlockState s) {
        return s.isOf(Blocks.LAVA) || s.isOf(Blocks.FIRE) || s.isOf(Blocks.SOUL_FIRE) || s.isOf(Blocks.MAGMA_BLOCK)
                || s.isOf(Blocks.CACTUS) || s.isOf(Blocks.SWEET_BERRY_BUSH) || s.isOf(Blocks.POWDER_SNOW) || s.isOf(Blocks.COBWEB);
    }
}
