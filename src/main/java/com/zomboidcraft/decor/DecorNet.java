package com.zomboidcraft.decor;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.Mood;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.zombie.Noise;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** The G menu's decor actions: switch it on or off, or play it (a game, an instrument, a film night). */
public final class DecorNet {
    public enum Action { TOGGLE, PLAY }

    public record Use(BlockPos pos, int action) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(ZomboidCraft.id("decor_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Use::pos, ByteBufCodecs.VAR_INT, Use::action, Use::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private DecorNet() {
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                BlockState s = player.level().getBlockState(payload.pos());
                if (player.isSpectator() || player.blockPosition().distSqr(payload.pos()) > 36
                        || !(s.getBlock() instanceof DecorBlock d) || payload.action() < 0 || payload.action() > 1) {
                    return;
                }
                ServerLevel level = (ServerLevel) player.level();
                if (payload.action() == 0) {
                    toggle(player, level, payload.pos(), s, d);
                } else {
                    play(player, level, payload.pos(), d);
                }
            });
        });
    }

    private static void toggle(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState s, DecorBlock d) {
        boolean on = !s.getValue(DecorBlock.ON);
        if (!d.setOn(level, pos, s, on)) {
            say(player, "No power.");
            return;
        }
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5f, on ? 1.3f : 1.0f);
    }

    private static void play(ServerPlayer player, ServerLevel level, BlockPos pos, DecorBlock d) {
        Decor.Spec spec = d.spec;
        if (spec.play() <= 0) {
            return;
        }
        if (!d.powered(level, pos)) {
            say(player, "It needs power.");
            return;
        }
        TimedActions.start(player, "decor_play:" + pos.asLong(), "Playing " + spec.name(), 200, p -> {
            if (!(level.getBlockState(pos).getBlock() instanceof DecorBlock)) {
                return;
            }
            Mood.add(p, Mood.Stat.BOREDOM, -spec.play());
            Mood.add(p, Mood.Stat.STRESS, -Math.max(1, spec.play() / 3));
            Mood.add(p, Mood.Stat.UNHAPPINESS, -Math.max(1, spec.play() / 4));
            if (spec.noise() > 0) {
                Noise.emit(level, Vec3.atCenterOf(pos), spec.noise());
            }
            say(p, "That passed the time.");
        });
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text));
    }
}
