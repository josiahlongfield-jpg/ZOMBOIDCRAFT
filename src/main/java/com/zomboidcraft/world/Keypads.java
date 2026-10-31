package com.zomboidcraft.world;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.barricade.CrowbarItem;
import com.zomboidcraft.zombie.Noise;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * A blast door's keypad (bunk_keypad), the way a code heard on the radio gets you in: the interact key on
 * the pad opens its number pad (client/interact/KeypadScreen), and the six digits typed go to the server.
 * The right code chirps and swings the blast door beside it open, as a crowbar would but quietly; a wrong
 * one gives the long low buzz, loud enough to turn heads nearby.
 * <p>
 * The codes belong to places, not to the pads: Camp Black Pine's hillside bunker by the radio mast takes
 * 417702, the code the broadcasts give out (AUDIO's broadcasts plan; Josiah picked the spot 2026-10-31).
 */
public final class Keypads {
    /** A place's code: every pad within reach of its middle takes it. */
    private record Code(int x, int z, int reach, String digits) {
    }

    private static final Code[] CODES = {
            new Code(-6600, -3250, 260, "417702"),     // Camp Black Pine (county_plan "camp")
    };

    public static final int DIGITS = 6;
    /** How far a typed code carries from the pad, and the buzz of a wrong one. */
    private static final double REACH = 6, WRONG_NOISE = 16;

    /** The digits typed at a pad. */
    public record Enter(BlockPos pos, String code) implements CustomPacketPayload {
        public static final Type<Enter> TYPE = new Type<>(ZomboidCraft.id("keypad_enter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Enter> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Enter::pos, ByteBufCodecs.STRING_UTF8, Enter::code, Enter::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Enter.TYPE, Enter.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Enter.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> enter(player, payload.pos(), payload.code()));
        });
        // the pad itself does nothing on the server: the client opens its number pad
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                is(level.getBlockState(hit.getBlockPos())) ? InteractionResult.SUCCESS : InteractionResult.PASS);
    }

    /** Whether a block is a keypad (client or server). */
    public static boolean is(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals("bunk_keypad");
    }

    /** The code a pad at pos takes, or null when no place round it has one. */
    static String code(BlockPos pos) {
        for (Code c : CODES) {
            if (Math.abs(pos.getX() - c.x) <= c.reach && Math.abs(pos.getZ() - c.z) <= c.reach) {
                return c.digits;
            }
        }
        return null;
    }

    private static void enter(ServerPlayer player, BlockPos pos, String typed) {
        if (player.isSpectator() || !(player.level() instanceof ServerLevel level)
                || player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > REACH || !is(level.getBlockState(pos))) {
            return;
        }
        String code = code(pos);
        BlockPos door = code != null && code.equals(typed) ? door(level, pos) : null;
        if (door != null && CrowbarItem.pry(level, door)) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.8f, 1.6f);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, 0.8f, 2.0f);
            level.playSound(null, door, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.4f, 0.5f);
            level.playSound(null, door, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.9f, 0.5f);
            player.sendOverlayMessage(Component.literal("The bolts draw back.").withStyle(ChatFormatting.GRAY));
        } else {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.BLOCKS, 1.0f, 0.5f);
            Noise.emit(level, Vec3.atCenterOf(pos), WRONG_NOISE);
            player.sendOverlayMessage(Component.literal(door == null && code != null && code.equals(typed)
                    ? "The code takes, but the door's already open." : "ACCESS DENIED").withStyle(ChatFormatting.RED));
        }
    }

    /**
     * A block of the blast door the pad stands beside, still shut in its frame (facing out the way the pad
     * does: an open leaf stands turned against the wall inside), or null.
     */
    private static BlockPos door(ServerLevel level, BlockPos pad) {
        BlockState at = level.getBlockState(pad);
        if (!at.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return null;
        }
        Direction out = at.getValue(BlockStateProperties.HORIZONTAL_FACING);
        for (BlockPos p : BlockPos.betweenClosed(pad.offset(-3, -2, -3), pad.offset(3, 1, 3))) {
            BlockState s = level.getBlockState(p);
            if (CrowbarItem.row(s) >= 0 && s.getValue(BlockStateProperties.HORIZONTAL_FACING) == out) {
                return p.immutable();
            }
        }
        return null;
    }

    private Keypads() {
    }
}
