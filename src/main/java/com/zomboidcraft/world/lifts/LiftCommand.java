package com.zomboidcraft.world.lifts;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** /zc lift test [floors]: builds a bare lift shaft in front of you, its doors facing you, floors 4 apart, to try a lift out. */
public final class LiftCommand {
    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("lift")
                                .then(Commands.literal("test")
                                        .executes(ctx -> test(ctx, 6))
                                        .then(Commands.argument("floors", IntegerArgumentType.integer(2, 40))
                                                .executes(ctx -> test(ctx, IntegerArgumentType.getInteger(ctx, "floors"))))))));
    }

    private static int test(CommandContext<CommandSourceStack> ctx, int floors) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        ServerPlayer player = src.getEntity() instanceof ServerPlayer p ? p : null;
        Direction look = player != null ? player.getDirection() : Direction.SOUTH;
        BlockPos feet = player != null ? player.blockPosition() : BlockPos.containing(src.getPosition());
        // the shaft's frame faces its doors north; turned so they face you, three blocks ahead
        Direction doors = look.getOpposite();
        int rot = doors.get2DDataValue() == 2 ? 0 : doors == Direction.EAST ? 1 : doors == Direction.SOUTH ? 2 : 3;
        BlockPos o = feet.relative(look, 3);
        BlockSink sink = new BlockSink() {
            @Override
            public void set(int x, int y, int z, String block) {
                BlockState state = block.equals("air") ? Blocks.AIR.defaultBlockState() : CityBlocks.resolve(block);
                if (state != null) {
                    level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
                }
            }
        };
        Placer p = new Placer(sink, o.getX(), feet.getY() - 1, o.getZ(), rot);
        int[] ys = new int[floors];
        for (int i = 0; i < floors; i++) {
            ys[i] = i * 4;
        }
        int top = ys[floors - 1] + 5;
        for (int y = 0; y <= top + 1; y++) {
            for (int x = -1; x <= 2; x++) {
                for (int z = 0; z <= 4; z++) {
                    boolean wall = x == -1 || x == 2 || z == 0 || z == 4 || y == 0 || y == top + 1;
                    p.set(x, y, z, wall ? "concrete_dark" : "air");
                }
            }
        }
        for (int i = 1; i < floors; i++) {
            // a ledge to step out onto
            p.fill(-1, ys[i], -3, 3, ys[i], -1, "floor_concrete");
            p.fill(-1, ys[i] + 1, -3, 3, ys[i] + 3, -1, "air");
        }
        LiftBuild.shaft(p::set, 0, 1, 2, 3, ys, 0, 0, top);
        src.sendSuccess(() -> Component.literal("Built a " + floors + "-floor test lift in front of you. Press its button (G or right-click)."), true);
        return 1;
    }

    private LiftCommand() {
    }
}
