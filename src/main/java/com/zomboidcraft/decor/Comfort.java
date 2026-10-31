package com.zomboidcraft.decor;

import com.zomboidcraft.survival.Mood;
import com.zomboidcraft.world.blocks.LightSwitch;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A comfortable base, ours on top of Zomboid's moods: every decor piece in the room you're in adds its comfort (a
 * powered one only while it works), and a comfortable room slowly eases boredom, unhappiness and stress while you're
 * in it. The room is what a light switch would find (world/blocks/LightSwitch.room), so outdoors there's none.
 */
public final class Comfort {
    /** Ticks between looks; comfort past this does nothing more. */
    private static final int EVERY = 200, MAX = 24;

    private Comfort() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Comfort::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % EVERY != 0 || Decor.all().isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            ServerLevel level = (ServerLevel) player.level();
            BlockPos head = player.blockPosition().above();
            if (level.canSeeSky(head)) {
                continue;
            }
            int c = Math.min(MAX, of(level, head));
            if (c <= 0) {
                continue;
            }
            Mood.add(player, Mood.Stat.BOREDOM, -Math.max(1, c / 4));
            Mood.add(player, Mood.Stat.UNHAPPINESS, -Math.max(1, c / 5));
            Mood.add(player, Mood.Stat.STRESS, -Math.max(1, c / 5));
        }
    }

    /** The comfort of the room round this spot. */
    public static int of(ServerLevel level, BlockPos at) {
        Set<BlockPos> room = LightSwitch.room(level, at);
        int sum = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (BlockPos column : room) {
            for (int dy = -2; dy <= 3; dy++) {
                p.set(column.getX(), column.getY() + dy, column.getZ());
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof DecorBlock d && (!d.spec.power() || s.getValue(DecorBlock.ON))) {
                    sum += d.spec.comfort();
                }
            }
        }
        return sum;
    }
}
