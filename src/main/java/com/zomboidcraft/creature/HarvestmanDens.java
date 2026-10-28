package com.zomboidcraft.creature;

import com.zomboidcraft.world.blocks.CityBlock;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.zombie.Corpse;
import com.zomboidcraft.zombie.Corpses;
import com.zomboidcraft.zombie.ZombieLook;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A Harvestman's den, and the bodies it sets out. Where it settles it tears the room clear, heaps the scrap round the
 * walls, mats a nest in the middle of what it dragged in, scores the walls, and leaves the trail it dragged them in
 * by. Its bodies hang from the ceiling on steel cable (or wrapped, like meat), sit against the walls facing the way
 * in, or slump in the doorways; one it leaves for someone is set out the same way. The scrap is what it grows its legs
 * back from (HarvestmanMind's rest): a heap goes down a size for every leg.
 */
final class HarvestmanDens {
    private static final String[] HEAPS = {"hd_heap", "hd_heap_b", "hd_heap_tall", "hd_heap_low"};
    private static final String[] GROUND = {"hd_debris", "hd_debris_b", "hd_bones"};

    // ------------------------------------------------------------------------------------------------ the den

    /** Builds its den round lair (the air at the floor), for someone who'll come in from where `from` is. */
    static void carve(ServerLevel level, BlockPos lair, Vec3 from) {
        RandomSource rnd = level.getRandom();
        int y = lair.getY();
        List<BlockPos> walls = new ArrayList<>();
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > 7.2) {
                    continue;
                }
                BlockPos c = lair.offset(dx, 0, dz);
                if (r < 4.5) {
                    tearOut(level, c);
                    tearOut(level, c.above());
                }
                if (!floor(level, c)) {
                    continue;
                }
                Direction wall = wallBeside(level, c);
                if (wall != null && r >= 2.5 && !passage(level, c)) {
                    walls.add(c);
                    continue;
                }
                if (r < 1.6) {
                    put(level, c, rnd.nextBoolean() ? "hd_nest" : "hd_nest_b", rnd);
                } else if (r < 4.8 && rnd.nextFloat() < 0.55) {
                    put(level, c, GROUND[rnd.nextInt(GROUND.length)], rnd);
                } else if (r >= 3 && rnd.nextFloat() < 0.1) {
                    put(level, c, "hd_heap_low", rnd);
                }
            }
        }
        // the scrap heaped against the walls, and the walls above it scored where it climbed
        for (BlockPos c : walls) {
            float k = rnd.nextFloat();
            if (k < 0.65f) {
                put(level, c, k < 0.18f && level.isEmptyBlock(c.above()) ? "hd_heap_tall" : rnd.nextBoolean() ? "hd_heap" : "hd_heap_b", rnd);
            } else if (k < 0.8f) {
                gouge(level, c, rnd);
            }
            for (int up = 1; up <= 3; up++) {
                if (rnd.nextFloat() < 0.3f) {
                    gouge(level, c.above(up), rnd);
                }
            }
        }
        drag(level, lair, from);
    }

    /** What stood in the room goes: furniture, things on the floor (never walls, doors, stairs, windows or rails). */
    private static void tearOut(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir() || s.getBlock() instanceof DoorBlock || s.isCollisionShapeFullBlock(level, p)
                || s.getDestroySpeed(level, p) < 0 || !s.getFluidState().isEmpty()) {
            return;
        }
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        for (String keep : new String[] {"stair", "step", "ladder", "window", "glass", "rail", "door", "lift", "esc",
                "slab", "pane", "fence", "bars", "hatch", "trap", "wall", "barricade", "hd_"}) {
            if (id.contains(keep)) {
                return;
            }
        }
        level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }

    /** The trail it dragged its bodies in by: from the nest toward the way in. */
    private static void drag(ServerLevel level, BlockPos lair, Vec3 from) {
        if (from == null) {
            return;
        }
        Vec3 d = new Vec3(from.x - lair.getX() - 0.5, 0, from.z - lair.getZ() - 0.5);
        if (d.lengthSqr() < 1) {
            return;
        }
        d = d.normalize();
        Direction.Axis axis = Math.abs(d.x) > Math.abs(d.z) ? Direction.Axis.X : Direction.Axis.Z;
        for (int k = 2; k <= 14; k++) {
            BlockPos c = BlockPos.containing(lair.getX() + 0.5 + d.x * k, lair.getY(), lair.getZ() + 0.5 + d.z * k);
            if (!level.isEmptyBlock(c) || !floor(level, c)) {
                if (k > 4) {
                    break;
                }
                continue;
            }
            BlockState s = CityBlocks.resolve("hd_drag");
            if (s != null) {
                level.setBlock(c, s.setValue(CityBlock.Axis.AXIS, axis), 3);
            }
        }
    }

    private static void gouge(ServerLevel level, BlockPos c, RandomSource rnd) {
        if (!level.isEmptyBlock(c)) {
            return;
        }
        Direction wall = wallBeside(level, c);
        BlockState s = CityBlocks.resolve("hd_gouges_" + rnd.nextInt(3));
        if (wall != null && s != null) {
            level.setBlock(c, s.setValue(CityBlock.Facing.FACING, wall.getOpposite()), 3);
        }
    }

    private static void put(ServerLevel level, BlockPos c, String name, RandomSource rnd) {
        BlockState s = CityBlocks.resolve(name);
        if (s == null || !level.isEmptyBlock(c)) {
            return;
        }
        if (s.hasProperty(CityBlock.Facing.FACING)) {
            s = s.setValue(CityBlock.Facing.FACING, Direction.Plane.HORIZONTAL.getRandomDirection(rnd));
        }
        level.setBlock(c, s, 3);
    }

    /** The bodies of its den: some hung, one maybe wrapped, the rest sat round the walls or dropped on the nest. */
    static void bodies(ServerLevel level, BlockPos lair, Vec3 from, int count) {
        RandomSource rnd = level.getRandom();
        List<String> outfits = ZombieLook.outfits();
        Vec3 centre = Vec3.atBottomCenterOf(lair);
        if (rnd.nextFloat() < 0.6f) {
            cocoon(level, lair, rnd);
        }
        for (int i = 0; i < count; i++) {
            int limbs = rnd.nextFloat() < 0.6f ? 1 << rnd.nextInt(8) : 0;     // drained and torn: most are missing something
            double a = rnd.nextDouble() * Math.PI * 2, r = 0.6 + rnd.nextDouble() * 1.2;
            Vec3 at = centre.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Corpse body = Corpses.lay(level, at, rnd.nextFloat() * 360f, outfits.get(rnd.nextInt(outfits.size())), limbs, true);
            if (body != null && (i < 2 || rnd.nextFloat() < 0.6f)) {
                setOut(level, body, centre, from, i < 2 ? 0.7f : 0.25f);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ setting a body out

    /**
     * Sets a body out near `at` for someone coming in from `from`: hung from the ceiling (by the given chance, where
     * there's a ceiling to hang it from), slumped in a doorway, or sat against a wall facing them. False if nowhere
     * would do (it stays where it lies).
     */
    static boolean setOut(ServerLevel level, Corpse body, Vec3 at, Vec3 from, float hang) {
        RandomSource rnd = level.getRandom();
        if (from == null) {
            from = at.add(rnd.nextGaussian() * 8, 0, rnd.nextGaussian() * 8);
        }
        if (rnd.nextFloat() < hang && hang(level, body, at, from)) {
            return true;
        }
        if (rnd.nextFloat() < 0.4f && doorway(level, body, at, from)) {
            return true;
        }
        return againstWall(level, body, at, from) || doorway(level, body, at, from) || hang > 0 && hang(level, body, at, from);
    }

    private static boolean hang(ServerLevel level, Corpse body, Vec3 at, Vec3 from) {
        BlockPos base = BlockPos.containing(at);
        for (BlockPos c : nearby(base, 3)) {
            if (!floor(level, c) || !level.isEmptyBlock(c.above()) || taken(level, c)) {
                continue;
            }
            int ceil = ceiling(level, c);
            if (ceil < 3 || !hangCable(level, c, 2, ceil)) {
                continue;
            }
            double neck = c.getY() + 2.05;
            body.pose(Corpse.HUNG);
            place(body, c.getX() + 0.5, neck - 1.68 * body.size(), c.getZ() + 0.5, facing(c, from));
            return true;
        }
        return false;
    }

    /** A body wrapped in plastic and cable, hung up by its feet like a side of meat. */
    private static void cocoon(ServerLevel level, BlockPos lair, RandomSource rnd) {
        for (BlockPos c : nearby(lair, 4)) {
            if (c.distSqr(lair) < 4 || !floor(level, c) || rnd.nextFloat() < 0.5f) {
                continue;
            }
            int ceil = ceiling(level, c);
            int bottom = ceil == 3 ? 0 : 1;
            if (ceil < 3 || !level.isEmptyBlock(c.above()) || !hangCable(level, c, bottom + 2, ceil)) {
                continue;
            }
            BlockState low = CityBlocks.resolve("hd_cocoon"), top = CityBlocks.resolve("hd_cocoon_top");
            if (low == null || top == null) {
                return;
            }
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(rnd);
            level.setBlock(c.above(bottom), low.setValue(CityBlock.Facing.FACING, d), 3);
            level.setBlock(c.above(bottom + 1), top.setValue(CityBlock.Facing.FACING, d), 3);
            return;
        }
    }

    /** Steel cable from the ceiling (ceil cells above c) down to the cell `from` cells above c, the knot at its end. */
    private static boolean hangCable(ServerLevel level, BlockPos c, int from, int ceil) {
        if (from >= ceil) {
            return false;
        }
        for (int k = from; k < ceil; k++) {
            if (!level.isEmptyBlock(c.above(k))) {
                return false;
            }
        }
        for (int k = from; k < ceil; k++) {
            String name = k == from ? "hd_cable_end" : k == ceil - 1 ? "hd_cable_top" : "hd_cable";
            BlockState s = CityBlocks.resolve(name);
            if (s != null) {
                level.setBlock(c.above(k), s, 3);
            }
        }
        return true;
    }

    private static boolean doorway(ServerLevel level, Corpse body, Vec3 at, Vec3 from) {
        BlockPos base = BlockPos.containing(at);
        for (BlockPos c : nearby(base, 8)) {
            if (!floor(level, c) || !level.isEmptyBlock(c.above()) || taken(level, c)) {
                continue;
            }
            // jambs either side, a lintel over, open both ways through
            for (Direction side : new Direction[] {Direction.EAST, Direction.SOUTH}) {
                Direction through = side.getClockWise();
                if (wall(level, c.relative(side)) && wall(level, c.relative(side.getOpposite()))
                        && wall(level, c.relative(side).above()) && wall(level, c.relative(side.getOpposite()).above())
                        && !level.getBlockState(c.above(2)).isAir()
                        && open(level, c.relative(through)) && open(level, c.relative(through.getOpposite()))) {
                    Vec3 to = from.subtract(Vec3.atBottomCenterOf(c));
                    Direction face = to.x * through.getStepX() + to.z * through.getStepZ() >= 0 ? through : through.getOpposite();
                    body.pose(Corpse.SLUMPED);
                    place(body, c.getX() + 0.5 - face.getStepX() * 0.2, c.getY(), c.getZ() + 0.5 - face.getStepZ() * 0.2,
                            face.toYRot());
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean againstWall(ServerLevel level, Corpse body, Vec3 at, Vec3 from) {
        BlockPos base = BlockPos.containing(at);
        BlockPos best = null;
        Direction bestWall = null;
        double bestScore = -1e9;
        RandomSource rnd = level.getRandom();
        for (BlockPos c : nearby(base, 4)) {
            if (!floor(level, c) || !open(level, c) || taken(level, c)) {
                continue;
            }
            for (Direction w : Direction.Plane.HORIZONTAL) {
                if (!wall(level, c.relative(w)) || !wall(level, c.relative(w).above())) {
                    continue;
                }
                Vec3 to = from.subtract(Vec3.atBottomCenterOf(c)).multiply(1, 0, 1).normalize();
                // facing the way they'll come in, close to where it was going to leave it
                double score = -(to.x * w.getStepX() + to.z * w.getStepZ()) * 2 - Math.sqrt(c.distSqr(base)) * 0.3 + rnd.nextDouble() * 0.3;
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                    bestWall = w;
                }
            }
        }
        if (best == null) {
            return false;
        }
        body.pose(Corpse.SAT);
        place(body, best.getX() + 0.5 + bestWall.getStepX() * 0.2, best.getY(), best.getZ() + 0.5 + bestWall.getStepZ() * 0.2,
                bestWall.getOpposite().toYRot());
        return true;
    }

    private static void place(Corpse body, double x, double y, double z, float yaw) {
        body.snapTo(x, y, z, yaw, 0f);
        body.setYRot(yaw);
        body.setDeltaMovement(Vec3.ZERO);
        body.keep();
    }

    // ------------------------------------------------------------------------------------------------ the scrap

    /** The heap of scrap nearest its den, or null. */
    static BlockPos scrap(ServerLevel level, BlockPos lair, int r) {
        BlockPos best = null;
        double bestD = 1e9;
        for (BlockPos p : BlockPos.betweenClosed(lair.offset(-r, -1, -r), lair.offset(r, 2, r))) {
            String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath();
            if (id.startsWith("hd_heap") && p.distSqr(lair) < bestD) {
                bestD = p.distSqr(lair);
                best = p.immutable();
            }
        }
        return best;
    }

    /** It works a heap for the steel it grows a leg from: sparks and grit off the heap. */
    static void work(ServerLevel level, BlockPos heap) {
        BlockState s = level.getBlockState(heap);
        Vec3 c = Vec3.atCenterOf(heap);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.2, c.z, 8, 0.3, 0.2, 0.3, 0.15);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), c.x, c.y, c.z, 10, 0.3, 0.2, 0.3, 0.05);
    }

    /** A leg's worth of the heap gone: it goes down a size, the last of it to nothing. */
    static void use(ServerLevel level, BlockPos heap) {
        BlockState s = level.getBlockState(heap);
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        String next = switch (id) {
            case "hd_heap_tall" -> "hd_heap";
            case "hd_heap", "hd_heap_b" -> "hd_heap_low";
            default -> null;
        };
        BlockState to = next == null ? null : CityBlocks.resolve(next);
        if (to != null && s.hasProperty(CityBlock.Facing.FACING)) {
            to = to.setValue(CityBlock.Facing.FACING, s.getValue(CityBlock.Facing.FACING));
        }
        level.setBlock(heap, to != null ? to : Blocks.AIR.defaultBlockState(), 3);
        work(level, heap);
    }

    // ------------------------------------------------------------------------------------------------ looking round

    private static List<BlockPos> nearby(BlockPos base, int r) {
        List<BlockPos> out = new ArrayList<>();
        for (int d = 0; d <= r; d++) {
            for (int dy = 0; dy >= -2; dy--) {
                for (int dx = -d; dx <= d; dx++) {
                    for (int dz = -d; dz <= d; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) == d) {
                            out.add(base.offset(dx, dy, dz));
                        }
                    }
                }
            }
        }
        return out;
    }

    /** Air with a floor under it. */
    private static boolean floor(ServerLevel level, BlockPos c) {
        return level.getBlockState(c).getCollisionShape(level, c).isEmpty()
                && level.getBlockState(c.below()).isFaceSturdy(level, c.below(), Direction.UP);
    }

    private static boolean open(ServerLevel level, BlockPos c) {
        return level.getBlockState(c).getCollisionShape(level, c).isEmpty()
                && level.getBlockState(c.above()).getCollisionShape(level, c.above()).isEmpty();
    }

    private static boolean wall(ServerLevel level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        return s.isCollisionShapeFullBlock(level, p) && !(s.getBlock() instanceof DoorBlock);
    }

    private static Direction wallBeside(ServerLevel level, BlockPos c) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (wall(level, c.relative(d))) {
                return d;
            }
        }
        return null;
    }

    /** A doorway or a corridor (walls both sides) or beside a door: never heaped shut. */
    private static boolean passage(ServerLevel level, BlockPos c) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(c.relative(d)).getBlock() instanceof DoorBlock) {
                return true;
            }
        }
        return wall(level, c.east()) && wall(level, c.west()) || wall(level, c.north()) && wall(level, c.south());
    }

    /** How many cells up the ceiling is (the first solid underside, up to 7), or 0 if there's none that close. */
    private static int ceiling(ServerLevel level, BlockPos c) {
        for (int k = 1; k <= 7; k++) {
            BlockPos p = c.above(k);
            BlockState s = level.getBlockState(p);
            if (!s.isAir()) {
                return s.isFaceSturdy(level, p, Direction.DOWN) ? k : 0;
            }
        }
        return 0;
    }

    /** Another body already there. */
    private static boolean taken(ServerLevel level, BlockPos c) {
        return !level.getEntitiesOfClass(Corpse.class, new net.minecraft.world.phys.AABB(c).inflate(0.2, 0, 0.2),
                b -> b.pose() != Corpse.FALLEN).isEmpty();
    }

    private static float facing(BlockPos c, Vec3 from) {
        return (float) Math.toDegrees(Math.atan2(-(from.x - c.getX() - 0.5), from.z - c.getZ() - 0.5));
    }

    private HarvestmanDens() {
    }
}
