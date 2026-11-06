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
    /** What the G menu asks of a piece. */
    public enum Action { TOGGLE, PLAY, OPEN, REST, FUEL, COOK, DRESS, ROLL, READ, NEXT_LOOK }

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
                        || !(s.getBlock() instanceof DecorBlock d) || payload.action() < 0
                        || payload.action() >= Action.values().length) {
                    return;
                }
                ServerLevel level = (ServerLevel) player.level();
                BlockPos pos = payload.pos();
                switch (Action.values()[payload.action()]) {
                    case TOGGLE -> toggle(player, level, pos, s, d);
                    case PLAY -> play(player, level, pos, d);
                    case OPEN -> {
                        if (d instanceof DecorBlock.Holding && !DecorUses.is(d, "dress_decoy")) {
                            com.zomboidcraft.loot.Nearby.open(player, pos);
                        }
                    }
                    case REST -> DecorUses.rest(player, level, pos, d);
                    case FUEL -> {
                        if (DecorUses.is(d, "stove_heat")) DecorUses.fuel(player, level, pos, s, d);
                    }
                    case COOK -> {
                        if (DecorUses.is(d, "stove_heat")) DecorUses.cook(player, level, pos);
                    }
                    case DRESS -> {
                        if (DecorUses.is(d, "dress_decoy")) DecorUses.dress(player, level, pos, s);
                    }
                    case ROLL -> {
                        if (DecorUses.is(d, "roll")) DecorUses.roll(player, level);
                    }
                    case READ -> DecorUses.read(player, level, pos, s, d);
                    case NEXT_LOOK -> {
                        if (DecorUses.is(d, "letters")) DecorUses.nextLook(player, level, pos, s, d);
                    }
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
        String missing = DecorUses.needs(player, level, pos, d);
        if (missing != null) {
            say(player, missing);
            return;
        }
        TimedActions.start(player, "decor_play:" + pos.asLong(), label(spec), 200, p -> {
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
            DecorUses.played(p, level, pos, d);   // a prize, a payout, a skill: its own word replaces that
        });
    }

    private static String label(Decor.Spec spec) {
        String sp = spec.special() == null ? "" : spec.special();
        return switch (sp) {
            case "train" -> "Training";
            case "aim_xp" -> "Throwing darts";
            case "vhs" -> "Watching a tape";
            case "needs_record", "old_broadcast" -> "Listening";
            case "needs_tv" -> "Playing video games";
            default -> "Playing " + spec.name();
        };
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text));
    }
}
