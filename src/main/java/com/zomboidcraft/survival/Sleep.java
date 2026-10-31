package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieTypes;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Sleeping, Zomboid style: the interact key on a bed, sofa, mattress or cot (or on the floor, crouching) lies you down
 * there. What you lie on sets how much rest an hour gives; a bad bed or the floor leaves you stiff. You wake when
 * you're rested, when you're hurt, when you move, or when a zombie gets close and you hear it (light sleepers more
 * often). Zomboid's two server settings decide whether sleep is allowed and whether it's needed at all.
 * <p>
 * While everyone online is asleep the server fast-forwards (Minecraft's tick sprint), as solo Zomboid does, so a
 * night's sleep takes under a minute; the needs clock, spoilage and the world all run on in that time. Anyone awake
 * keeps time at its normal pace for everybody.
 */
public final class Sleep {
    /** What you're lying on: rest gained per Zomboid hour, as Zomboid's bed qualities. */
    public enum Bed {
        FLOOR("the floor", 6f), BAD("a poor bed", 8f), AVERAGE("a decent bed", 11f), GOOD("a good bed", 14f);

        public final String label;
        /** Fatigue (0..100) shed per Zomboid hour. */
        public final float restPerHour;

        Bed(String label, float restPerHour) {
            this.label = label;
            this.restPerHour = restPerHour;
        }
    }

    /** Asleep since {@code since} (game time), on {@code bed}, at {@code x,y,z}; bed -1 when awake. */
    public record State(int bed, double x, double y, double z, long since) {
        public static final State AWAKE = new State(-1, 0, 0, 0, 0);
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("bed").forGetter(State::bed),
                Codec.DOUBLE.fieldOf("x").forGetter(State::x),
                Codec.DOUBLE.fieldOf("y").forGetter(State::y),
                Codec.DOUBLE.fieldOf("z").forGetter(State::z),
                Codec.LONG.fieldOf("since").forGetter(State::since)
        ).apply(i, State::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, State::bed,
                ByteBufCodecs.DOUBLE, State::x,
                ByteBufCodecs.DOUBLE, State::y,
                ByteBufCodecs.DOUBLE, State::z,
                ByteBufCodecs.VAR_LONG, State::since,
                State::new);

        public boolean asleep() {
            return bed >= 0;
        }
    }

    public static final AttachmentType<State> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("sleep"),
            builder -> builder
                    .initializer(() -> State.AWAKE)
                    .syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    /** The client asks to lie down on the block at pos (or on the floor there). */
    public record LieDown(BlockPos pos) implements CustomPacketPayload {
        public static final Type<LieDown> ID = new Type<>(ZomboidCraft.id("sleep"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LieDown> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, LieDown::pos, LieDown::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return ID;
        }
    }

    /** Rest is never taken below this: you wake up on your own. */
    private static final float RESTED = 2f;
    /** Too fresh to sleep: Zomboid turns you away when you aren't tired. */
    private static final float NOT_TIRED = 25f;
    private static boolean sprinting;

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(LieDown.ID, LieDown.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LieDown.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            if (player.blockPosition().distSqr(payload.pos()) <= 16) {
                lieDown(player, payload.pos());
            }
        });
    }

    public static boolean asleep(Player player) {
        return player.getAttachedOrElse(TYPE, State.AWAKE).asleep();
    }

    /** What a block is to sleep on, or null when it isn't furniture you could sleep on. */
    public static Bed bedOf(BlockState state) {
        if (state.getBlock() instanceof BedBlock) return Bed.GOOD;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("bed_soil")) return null;
        if (path.contains("cot") && !path.contains("wainscot") || path.contains("bedroll") || path.contains("mattress")
                || path.contains("armchair") || path.contains("recliner") || path.contains("bunk_bodybag")) {
            return Bed.BAD;
        }
        if (path.contains("sofa") || path.contains("couch") || path.contains("futon") || path.contains("daybed")
                || path.contains("bunk_bed") || path.contains("medbed") || path.contains("lounger")) {
            return Bed.AVERAGE;
        }
        if (path.contains("_bed") || path.startsWith("bed")) return Bed.GOOD;
        return null;
    }

    /** Lies the player down on the block at pos, or tells them why not. */
    static void lieDown(ServerPlayer player, BlockPos pos) {
        ZcConfig.Survival config = ZcConfig.get().survival;
        if (asleep(player) || player.isPassenger()) return;
        if (!config.sleepAllowed) {
            say(player, "Sleeping is turned off on this server.");
            return;
        }
        Bed bed = bedOf(player.level().getBlockState(pos));
        if (bed == null) {
            if (!player.isShiftKeyDown()) return;
            bed = Bed.FLOOR;
        }
        float fatigue = SurvivalStats.get(player).fatigue();
        if (fatigue < NOT_TIRED) {
            say(player, "You're not tired enough to sleep.");
            return;
        }
        if (zombieNear(player, 10)) {
            say(player, "You can't sleep with zombies this close.");
            return;
        }
        Vec3 at = bed == Bed.FLOOR ? player.position() : Vec3.atBottomCenterOf(pos).add(0, 0.6, 0);
        player.teleportTo(at.x, at.y, at.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.setAttached(TYPE, new State(bed.ordinal(), at.x, at.y, at.z, player.level().getGameTime()));
        say(player, "You lie down on " + bed.label + ".");
    }

    /** Wakes the player, with why (null for no message). */
    public static void wake(ServerPlayer player, String why) {
        if (!asleep(player)) return;
        State state = player.getAttached(TYPE);
        player.setAttached(TYPE, State.AWAKE);
        if (why != null) say(player, why);
        if (state != null && state.bed() <= Bed.BAD.ordinal()) {
            say(player, state.bed() == Bed.FLOOR.ordinal() ? "You wake stiff and sore from the floor." : "You wake with a stiff neck.");
        }
    }

    /** Every tick: hold sleepers in place, and fast-forward while everyone online is asleep. */
    public static void tick(MinecraftServer server) {
        boolean anyone = false, allAsleep = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            anyone = true;
            State s = player.getAttachedOrElse(TYPE, State.AWAKE);
            if (!s.asleep()) {
                allAsleep = false;
                continue;
            }
            Vec3 at = new Vec3(s.x(), s.y(), s.z());
            // walking off (or being pushed far) wakes you; small drift is just the body settling
            if (player.position().distanceToSqr(at) > 0.6 * 0.6 || !player.isAlive()) {
                wake(player, player.isAlive() ? "You get up." : null);
                allAsleep = false;
                continue;
            }
            player.setDeltaMovement(Vec3.ZERO);
        }
        boolean want = anyone && allAsleep;
        if (want != sprinting) {
            sprinting = want;
            var rate = server.tickRateManager();
            if (want) rate.requestGameToSprint(24_000 * 4);
            else rate.stopSprinting();
        }
    }

    /** Once a second: rest by bed, and the reasons you wake (SurvivalEvents.tickPlayer). */
    static float rest(ServerPlayer player, float fatigue) {
        State s = player.getAttachedOrElse(TYPE, State.AWAKE);
        if (!s.asleep()) return fatigue;
        Bed bed = Bed.values()[Math.min(s.bed(), Bed.values().length - 1)];
        fatigue = Math.max(RESTED, fatigue - bed.restPerHour / Needs.hourSeconds());
        if (fatigue <= RESTED) {
            wake(player, "You wake up rested.");
        } else if (zombieNear(player, 8) && player.getRandom().nextInt(4) == 0) {
            wake(player, "Something woke you. It's close.");
        } else if (Nutrition.get(player).hungerStage() >= 4 || SurvivalStats.get(player).thirst() <= 16) {
            wake(player, Nutrition.get(player).hungerStage() >= 4 ? "Hunger wakes you." : "Thirst wakes you.");
        }
        return fatigue;
    }

    private static boolean zombieNear(ServerPlayer player, double range) {
        return !player.level().getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(range), ZombieTypes::isWalker).isEmpty();
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private Sleep() {
    }
}
