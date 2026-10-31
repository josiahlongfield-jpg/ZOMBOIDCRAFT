package com.zomboidcraft.building;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.TimedActions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Pick up and Scrap from the G menu, Zomboid's two moveable options: picked, they join the action queue as a timed
 * bar, and when it fills the server checks it all again (still there, still in reach, tool still in hand) before the
 * piece comes up whole (or breaks, by the same chance as lifting it by hand) or comes apart for materials.
 */
public final class MoveNet {
    /** Scrap with the tool for its material: a bar of this many ticks. */
    public static final int SCRAP_TICKS = 60;

    public record Move(BlockPos pos, boolean scrap) implements CustomPacketPayload {
        public static final Type<Move> TYPE = new Type<>(ZomboidCraft.id("move_furniture"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Move> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Move::pos, ByteBufCodecs.BOOL, Move::scrap, Move::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** How long picking it up takes, in ticks: the same as holding the swing on it. */
    public static int liftTicks(Moving.Piece piece, net.minecraft.world.entity.player.Player player, net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        float p = Moving.progress(piece, player, level, pos);
        return p <= 0 ? 0 : (int) Math.ceil(1 / p);
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Move.TYPE, Move.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Move.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> start(player, payload.pos(), payload.scrap()));
        });
    }

    private static void start(ServerPlayer player, BlockPos pos, boolean scrap) {
        if (!(player.level() instanceof ServerLevel level) || why(player, level, pos, scrap) != null) return;
        BlockState state = level.getBlockState(pos);
        String name = state.getBlock().getName().getString().toLowerCase(java.util.Locale.ROOT);
        int ticks = scrap ? SCRAP_TICKS : liftTicks(Moving.piece(state, level, pos), player, level, pos);
        TimedActions.start(player, (scrap ? "scrap:" : "lift:") + pos.asLong(), (scrap ? "Scrapping the " : "Picking up the ") + name, ticks, p -> {
            String no = why(p, level, pos, scrap);
            if (no != null) {
                p.sendOverlayMessage(Component.literal(no).withStyle(ChatFormatting.GRAY));
                return;
            }
            BlockState now = level.getBlockState(pos);
            if (scrap) {
                DigRules.strip(p, level, pos, DigRules.propTier(now, level, pos), false);
            } else {
                Moving.pickUp(p, level, pos, now, Moving.piece(now, level, pos));
            }
        });
    }

    /** Why it can't be done now, or null: gone, out of reach, or the tool or emptying it needs. */
    private static String why(ServerPlayer player, ServerLevel level, BlockPos pos, boolean scrap) {
        BlockState state = level.getBlockState(pos);
        Moving.Piece piece = Moving.piece(state, level, pos);
        if (piece == null || player.distanceToSqr(pos.getCenter()) > 36 || !ZcConfig.get().digging.rules) {
            return "It's not there to move.";
        }
        if (scrap) {
            DigRules.Tier tier = DigRules.propTier(state, level, pos);
            if (tier == null) return "That doesn't come apart for materials.";
            return DigRules.fits(tier, player.getMainHandItem()) ? null : "You need " + tier.tools() + " to scrap it.";
        }
        return Moving.refusal(piece, player, level, pos);
    }

    private MoveNet() {
    }
}
