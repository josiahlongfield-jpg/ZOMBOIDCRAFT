package com.zomboidcraft.world.blocks;

import com.zomboidcraft.ZomboidCraft;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A room's light switch (Josiah 2026-10-31: switches and working lights in every room, rooms start
 * dark). The interact key flips it: every room lamp in its room (data/zomboidcraft/lamps.txt pairs)
 * goes on, or off. The room is the open space round the switch at its height, walled in by
 * anything you can't walk through and by doors, out to {@link #CELLS} columns; its lamps are
 * whatever hangs over, stands in or sits under those columns, up to the first solid block above.
 */
public final class LightSwitch {
    static final String OFF = "lx_switch", ON = "lx_switch_on";
    private static final int CELLS = 2500, RANGE = 40, REACH = 6;
    private static Map<Block, Block> lit, dark;

    /** The switch block, both ways up. */
    public static class Switch extends CityBlock.Facing {
        Switch(Properties properties, VoxelShape north) {
            super(properties, north, true);
        }
    }

    /** From a player's game: the switch at pos flipped. */
    public record Flip(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Flip> TYPE = new Type<>(ZomboidCraft.id("light_switch"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Flip> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Flip::pos, Flip::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private LightSwitch() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Flip.TYPE, Flip.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Flip.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                BlockState state = player.level().getBlockState(payload.pos());
                if (!player.isSpectator() && player.blockPosition().distSqr(payload.pos()) < REACH * REACH
                        && state.getBlock() instanceof Switch) {
                    ServerLevel level = (ServerLevel) player.level();
                    if (state.getBlock() != CityBlocks.block(ON) && !com.zomboidcraft.power.PowerGrid.hasPower(level, payload.pos())) {
                        // the mains are off and no generator reaches: the switch clicks, nothing comes on
                        level.playSound(null, payload.pos(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, 1.3f);
                        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("No power."));
                        return;
                    }
                    flip(level, payload.pos(), state);
                }
            });
        });
    }

    /** Flips the switch at pos and sets its room's lamps to match. */
    public static void flip(ServerLevel level, BlockPos pos, BlockState state) {
        pairs();
        boolean on = state.getBlock() != CityBlocks.block(ON);
        level.setBlock(pos, copy(state, CityBlocks.block(on ? ON : OFF)), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, on ? 1.3f : 1.1f);
        Map<Block, Block> to = on ? lit : dark;
        for (BlockPos column : room(level, pos)) {
            BlockPos.MutableBlockPos at = column.mutable().move(0, -2, 0);
            for (int dy = -2; dy <= 10; dy++, at.move(Direction.UP)) {
                BlockState s = level.getBlockState(at);
                Block other = to.get(s.getBlock());
                if (other != null) {
                    level.setBlock(at, copy(s, other), Block.UPDATE_CLIENTS);
                }
                if (dy > 0 && s.isCollisionShapeFullBlock(level, at)) {
                    break;  // the ceiling (a lamp set in it is turned above)
                }
            }
        }
    }

    /** The columns of the room round a switch, at its height. */
    static Set<BlockPos> room(ServerLevel level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < CELLS) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos q = p.relative(d);
                if (seen.contains(q) || Math.abs(q.getX() - start.getX()) > RANGE || Math.abs(q.getZ() - start.getZ()) > RANGE) {
                    continue;
                }
                BlockState s = level.getBlockState(q);
                if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock || !s.getCollisionShape(level, q).isEmpty()) {
                    continue;
                }
                seen.add(q);
                queue.add(q);
            }
        }
        return seen;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copy(BlockState from, Block to) {
        BlockState out = to.defaultBlockState();
        for (Property p : from.getProperties()) {
            if (out.hasProperty(p)) {
                out = out.setValue(p, from.getValue(p));
            }
        }
        return out;
    }

    private static synchronized void pairs() {
        if (lit != null) {
            return;
        }
        Map<Block, Block> on = new HashMap<>(), off = new HashMap<>();
        try (InputStream in = LightSwitch.class.getResourceAsStream("/data/zomboidcraft/lamps.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    String[] f = line.trim().split(" ");
                    if (f.length != 2 || line.startsWith("#")) {
                        continue;
                    }
                    Block a = CityBlocks.block(f[0]), b = CityBlocks.block(f[1]);
                    if (a != null && b != null) {
                        on.put(a, b);
                        off.put(b, a);
                    }
                }
            }
        } catch (IOException e) {
            ZomboidCraft.LOGGER.warn("Couldn't read the room lamps", e);
        }
        lit = on;
        dark = off;
    }
}
