package com.zomboidcraft.world.trains;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.blocks.CityBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * /zc train test [rail|subway]: lays a test loop beside you with two stations and call buttons, and
 * starts a train on it. /zc train list, /zc train remove id, /zc train here (sends the nearest line's
 * train in to the nearest stop), /zc train ride (puts you aboard), /zc train call stop (as if its
 * button were pressed, flare or siren and all).
 */
public final class TrainCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("train")
                                .then(Commands.literal("test")
                                        .executes(ctx -> test(ctx, Line.Kind.RAIL))
                                        .then(Commands.literal("rail").executes(ctx -> test(ctx, Line.Kind.RAIL)))
                                        .then(Commands.literal("subway").executes(ctx -> test(ctx, Line.Kind.SUBWAY))))
                                .then(Commands.literal("list").executes(TrainCommand::list))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Trains.lines(false).keySet(), b))
                                                .executes(ctx -> remove(ctx, StringArgumentType.getString(ctx, "id")))))
                                .then(Commands.literal("here").executes(TrainCommand::here))
                                .then(Commands.literal("ride").executes(TrainCommand::ride))
                                .then(Commands.literal("call")
                                        .then(Commands.argument("stop", StringArgumentType.greedyString())
                                                .executes(ctx -> call(ctx, StringArgumentType.getString(ctx, "stop"))))))));
    }

    private static int test(CommandContext<CommandSourceStack> ctx, Line.Kind kind) {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getEntity() instanceof ServerPlayer p ? p : null;
        ServerLevel level = src.getLevel();
        // along the way you face (squared up to x or z), the near straight beside you with its platform
        float yaw = player != null ? player.getYRot() : 0;
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        int dx = 0, dz = 0;
        if (Math.abs(fx) > Math.abs(fz)) {
            dx = fx > 0 ? 1 : -1;
        } else {
            dz = fz > 0 ? 1 : -1;
        }
        int px = -dz, pz = dx;       // across, to your right
        int length = 160, width = 100;
        BlockPos feet = player != null ? player.blockPosition() : BlockPos.containing(src.getPosition());
        int y = feet.getY();
        // the near straight's centreline runs on a block edge 5 blocks to your left; the loop lies beyond it
        int ox = feet.getX() - dx * length / 2 - px * 5, oz = feet.getZ() - dz * length / 2 - pz * 5;
        int[][] corners = {{0, 0}, {length, 0}, {length, -width}, {0, -width}};
        List<int[]> points = new ArrayList<>();
        for (int[] c : corners) {
            points.add(new int[] {ox + dx * c[0] + px * c[1], y, oz + dz * c[0] + pz * c[1]});
        }
        String id = "test_" + kind.id();
        String a = kind == Line.Kind.SUBWAY ? "Grayhaven Stadium" : "Union Station";
        String b = kind == Line.Kind.SUBWAY ? "Mercy General" : "Waterfront";
        // the stops: one on your side of the near straight, one outside the far straight
        Vec3 stopA = new Vec3(ox + dx * length / 2.0 + px * 4, y, oz + dz * length / 2.0 + pz * 4);
        Vec3 stopB = new Vec3(ox + dx * length / 2.0 - px * (width + 4), y, oz + dz * length / 2.0 - pz * (width + 4));
        Trains.remove(id);
        Line line = Trains.line(level, id, kind, "Test " + kind.id(), points,
                List.of(new Trains.StopSpec(a, stopA.x, stopA.z), new Trains.StopSpec(b, stopB.x, stopB.z)));
        layBed(level, line, y);
        int half = (int) Math.ceil(line.platformHalf()) + 4;
        platform(level, ox + dx * length / 2, oz + dz * length / 2, dx, dz, px, pz, half, y, 1);
        platform(level, ox + dx * length / 2 - px * width, oz + dz * length / 2 - pz * width, dx, dz, px, pz, half, y, -1);
        // up onto the platform (it's a block above the ground you stood on)
        if (player != null) {
            player.teleportTo(level, player.getX(), y + 1.05, player.getZ(), java.util.Set.of(), player.getYRot(), player.getXRot(), false);
        }
        // the train comes in to your stop
        TrainRun run = line.trains.get(0);
        run.s = line.route.wrapS(line.frontAt(line.stops.get(0)) - 200);
        run.v = (kind == Line.Kind.SUBWAY ? ZcConfig.get().trains.subwaySpeed : ZcConfig.get().trains.railSpeed) / 20 * 0.7;
        src.sendSuccess(() -> Component.literal("Laid test line " + id + " (" + Math.round(line.route.length()) + " blocks round): "
                + a + " beside you, " + b + " across the loop. The train's coming in. Call buttons on the platforms: G."), true);
        return 1;
    }

    /** The bed under the whole loop (6 wide), headroom cleared over it, and propped up where the ground's low. */
    private static void layBed(ServerLevel level, Line line, int y) {
        BlockState bed = state("concrete_dark", Blocks.STONE);
        Route r = line.route;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double s = 0; s < r.length(); s += 0.5) {
            double x = r.x(s), z = r.z(s), ddx = r.x(s + 0.5) - x, ddz = r.z(s + 0.5) - z;
            double len = Math.hypot(ddx, ddz);
            double nx = -ddz / len, nz = ddx / len;
            for (double k = -3; k <= 3; k += 0.5) {
                int bx = (int) Math.floor(x + nx * k), bz = (int) Math.floor(z + nz * k);
                pos.set(bx, y - 1, bz);
                level.setBlock(pos, bed, Block.UPDATE_CLIENTS);
                for (int up = 0; up < 7; up++) {
                    pos.set(bx, y + up, bz);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                prop(level, bx, y - 2, bz, bed);
            }
        }
    }

    /** A platform along the straight at (cx, cz), on its outer side (side +1: toward +across), with two call buttons on its back wall. */
    private static void platform(ServerLevel level, int cx, int cz, int dx, int dz, int px, int pz, int half, int y, int side) {
        BlockState floor = state("floor_concrete_b", Blocks.SMOOTH_STONE);
        BlockState wall = state("concrete_panel", Blocks.SMOOTH_STONE);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // the platform covers the 4 columns from 2 to 6 off the centreline
        int ax = px * side, az = pz * side;
        for (int u = -half; u < half; u++) {
            for (int w = 2; w < 6; w++) {
                int bx = cx + dx * u + col(ax, w), bz = cz + dz * u + col(az, w);
                pos.set(bx, y, bz);
                level.setBlock(pos, floor, Block.UPDATE_CLIENTS);
                for (int up = 1; up < 5; up++) {
                    pos.set(bx, y + up, bz);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                prop(level, bx, y - 1, bz, floor);
            }
            // the back wall, three high
            int bx = cx + dx * u + col(ax, 6), bz = cz + dz * u + col(az, 6);
            for (int up = 0; up < 4; up++) {
                pos.set(bx, y + up, bz);
                level.setBlock(pos, wall, Block.UPDATE_CLIENTS);
            }
            prop(level, bx, y - 1, bz, wall);
        }
        Direction facing = dirOf(-ax, -az);
        for (int u : new int[] {-8, 8}) {
            int bx = cx + dx * u + col(ax, 5), bz = cz + dz * u + col(az, 5);
            pos.set(bx, y + 2, bz);
            level.setBlock(pos, Trains.CALL_BUTTON.defaultBlockState().setValue(CallButtonBlock.FACING, facing), Block.UPDATE_CLIENTS);
        }
    }

    /** The block column w blocks off a centreline on block edges, toward unit a: 0..w-1 on the + side, -w.. on the - side. */
    private static int col(int a, int w) {
        return a > 0 ? w : a < 0 ? -w - 1 : 0;
    }

    private static Direction dirOf(int x, int z) {
        return x > 0 ? Direction.EAST : x < 0 ? Direction.WEST : z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void prop(ServerLevel level, int x, int y, int z, BlockState with) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        for (int i = 0; i < 12 && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
            level.setBlock(pos, with, Block.UPDATE_CLIENTS);
            pos.move(Direction.DOWN);
        }
    }

    private static BlockState state(String city, Block fallback) {
        BlockState s = CityBlocks.resolve(city);
        return s == null ? fallback.defaultBlockState() : s;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var lines = Trains.lines(false);
        if (lines.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No train lines yet (/zc train test lays one)"), false);
            return 0;
        }
        for (Line l : lines.values()) {
            StringBuilder sb = new StringBuilder(l.id + " (" + l.kind.id() + ", " + Math.round(l.route.length()) + " blocks, "
                    + l.cars + " cars): ");
            for (Line.Stop st : l.stops) {
                sb.append(st.name()).append(l.calls.contains(l.stops.indexOf(st)) ? " [called]" : "").append(", ");
            }
            for (TrainRun run : l.trains) {
                TrainCar.Pose p = run.pose(0);
                sb.append(String.format("train %d at %d %d %d, %.1f b/s%s, %d dead riding unseen; ", run.index, Math.round(p.x()),
                        Math.round(p.y()), Math.round(p.z()), run.speedPerSecond(), run.phase == TrainRun.Phase.DWELL ? " (stopped)" : "",
                        run.passengers));
            }
            String text = sb.toString();
            ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        }
        return lines.size();
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, String id) {
        boolean ok = Trains.remove(id);
        ctx.getSource().sendSuccess(() -> Component.literal(ok ? "Removed line " + id : "No line " + id), true);
        return ok ? 1 : 0;
    }

    /** The line and stop nearest the command's position. */
    private static Object[] nearest(CommandSourceStack src) {
        Vec3 at = src.getPosition();
        Line best = null;
        int stop = -1;
        double bestD = Double.MAX_VALUE;
        for (Line l : Trains.lines(false).values()) {
            for (int i = 0; i < l.stops.size(); i++) {
                Line.Stop st = l.stops.get(i);
                double d = Math.hypot(l.route.x(st.s()) - at.x, l.route.z(st.s()) - at.z);
                if (d < bestD) {
                    bestD = d;
                    best = l;
                    stop = i;
                }
            }
        }
        return best == null ? null : new Object[] {best, stop};
    }

    private static int here(CommandContext<CommandSourceStack> ctx) {
        Object[] n = nearest(ctx.getSource());
        if (n == null) {
            ctx.getSource().sendFailure(Component.literal("No stops anywhere"));
            return 0;
        }
        Line line = (Line) n[0];
        int stop = (int) n[1];
        TrainRun run = line.trains.get(0);
        run.phase = TrainRun.Phase.RUN;
        run.doors = 0;
        run.dwell = 0;
        run.atStop = -1;
        run.s = line.route.wrapS(line.frontAt(line.stops.get(stop)) - 220);
        run.v = (line.kind == Line.Kind.SUBWAY ? ZcConfig.get().trains.subwaySpeed : ZcConfig.get().trains.railSpeed) / 20 * 0.8;
        run.a = 0;
        ctx.getSource().sendSuccess(() -> Component.literal("Train on " + line.id + " coming in to " + line.stops.get(stop).name()), true);
        return 1;
    }

    private static int ride(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        Object[] n = nearest(ctx.getSource());
        if (n == null) {
            ctx.getSource().sendFailure(Component.literal("No lines anywhere"));
            return 0;
        }
        Line line = (Line) n[0];
        TrainRun run = line.trains.get(0);
        TrainCar.Pose p = run.pose(Math.min(1, line.cars - 1));
        Vec3 at = p.toWorld(0, 0.05, 0);
        player.teleportTo(ctx.getSource().getLevel(), at.x, at.y, at.z, java.util.Set.of(), p.yawDegrees(), 0, false);
        return 1;
    }

    private static int call(CommandContext<CommandSourceStack> ctx, String name) {
        for (Line l : Trains.lines(false).values()) {
            for (int i = 0; i < l.stops.size(); i++) {
                Line.Stop st = l.stops.get(i);
                if (st.name().equalsIgnoreCase(name) && !l.calls.contains(i)) {
                    BlockPos at = BlockPos.containing(l.route.x(st.s()), l.route.y(st.s()) + 2, l.route.z(st.s()));
                    l.calls.add(i);
                    Beacons.start(ctx.getSource().getLevel(), l, i, at);
                    ctx.getSource().sendSuccess(() -> Component.literal("Called the " + l.id + " train to " + st.name()), true);
                    return 1;
                }
            }
        }
        ctx.getSource().sendFailure(Component.literal("No uncalled stop called " + name));
        return 0;
    }

    private TrainCommand() {
    }
}
