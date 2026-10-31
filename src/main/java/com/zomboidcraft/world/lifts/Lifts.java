package com.zomboidcraft.world.lifts;

import com.zomboidcraft.ZomboidCraft;
import java.util.function.Function;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Working lifts and escalators. Every tower's lift is a shaft with a machine on top
 * ({@link LiftMachine}), landing doors and a call button on every floor, and a car you ride
 * ({@link LiftCar}); escalators carry whoever stands on them ({@link LiftPhysics}). They all run:
 * whatever else in the city is dead, the lifts and escalators still have power.
 */
public final class Lifts {
    public static final Block DOOR = block("lift_door", LiftDoorBlock::new,
            BlockBehaviour.Properties.of().strength(8f, 12f).sound(SoundType.METAL).noOcclusion());
    public static final Block BUTTON = block("lift_button", LiftButtonBlock::new,
            BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(LiftButtonBlock.LIT) ? 5 : 0));
    public static final Block MACHINE = block("lift_machine", LiftMachineBlock::new,
            BlockBehaviour.Properties.of().strength(10f, 12f).sound(SoundType.METAL));
    public static final Block ESCALATOR = block("escalator", EscalatorBlock::new,
            BlockBehaviour.Properties.of().strength(2f, 3f).sound(SoundType.METAL).noOcclusion());

    public static final BlockEntityType<LiftMachine> MACHINE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            ZomboidCraft.id("lift_machine"), FabricBlockEntityTypeBuilder.create(LiftMachine::new, MACHINE).build());

    private static final ResourceKey<EntityType<?>> CAR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("lift_car"));
    public static final EntityType<LiftCar> CAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, CAR_KEY,
            EntityType.Builder.<LiftCar>of(LiftCar::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).clientTrackingRange(12).updateInterval(1).fireImmune().noLootTable()
                    .build(CAR_KEY));

    public static final SoundEvent DING_SOUND = sound("lift.ding", 24f);
    public static final SoundEvent DOORS_SOUND = sound("lift.doors", 16f);
    public static final SoundEvent MOTOR_SOUND = sound("lift.motor", 16f);
    public static final SoundEvent BUTTON_SOUND = sound("lift.button", 12f);

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> make, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, make.apply(properties.setId(key)));
    }

    private static SoundEvent sound(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    /** A call button (or a landing door) pressed with the interact key. */
    public record Call(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Call> TYPE = new Type<>(ZomboidCraft.id("lift_call"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Call> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Call::pos, Call::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A floor picked on a car's panel (its entity id; landing -1 holds the doors, -2 shuts them). */
    public record Pick(int car, int landing) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(ZomboidCraft.id("lift_pick"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pick::car, ByteBufCodecs.VAR_INT, Pick::landing, Pick::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Call.TYPE, Call.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Pick.TYPE, Pick.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Call.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!player.isSpectator() && player.blockPosition().distSqr(payload.pos()) < 6 * 6) {
                    BlockState at = player.level().getBlockState(payload.pos());
                    if (at.is(BUTTON)) {
                        press((ServerLevel) player.level(), payload.pos(), player);
                    } else if (at.is(DOOR)) {
                        pressDoor((ServerLevel) player.level(), payload.pos(), player);
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Pick.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.isSpectator()) {
                    return;
                }
                if (player.level().getEntity(payload.car()) instanceof LiftCar car && car.machine != null
                        && car.machine.shaft() != null && car.machine.shaft().over(player.getX(), player.getZ(), -0.5)
                        && Math.abs(player.getY() - car.carY()) < 2.5) {
                    car.machine.carCall((ServerLevel) player.level(), payload.landing());
                    player.level().playSound(null, player.getX(), player.getY() + 1, player.getZ(), BUTTON_SOUND, SoundSource.BLOCKS, 0.6f, 1.2f);
                } else {
                    ZomboidCraft.LOGGER.info("Lift panel press from {} refused: car {} {}", player.getName().getString(), payload.car(),
                            player.level().getEntity(payload.car()) instanceof LiftCar c ? (c.machine == null ? "has no machine" : "is out of reach at y " + c.carY() + ", player at " + player.position()) : "not found");
                    player.sendOverlayMessage(Component.literal("Nothing happens.").withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });
        });
        LiftCommand.register();
    }

    /** A landing's call button pressed: the lift it belongs to comes to that floor. */
    static void press(ServerLevel level, BlockPos pos, Player by) {
        level.playSound(null, pos, BUTTON_SOUND, SoundSource.BLOCKS, 0.8f, 1f);
        BlockState state = level.getBlockState(pos);
        if (!state.is(BUTTON)) {
            return;
        }
        Direction f = state.getValue(LiftButtonBlock.FACING);
        // the shaft's front row is two blocks back from the button, beside it
        call(level, by, machineFor(level, pos.relative(f, -2), f.getClockWise(), 1, pos.getY() - 1), pos.getY() - 1);
    }

    /** A landing door used: the same as its call button, for whoever reaches for the doors. */
    static void pressDoor(ServerLevel level, BlockPos pos, Player by) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(DOOR)) {
            return;
        }
        BlockPos lower = state.getValue(LiftDoorBlock.PART) == LiftDoorBlock.Part.UPPER ? pos.below() : pos;
        Direction f = state.getValue(LiftDoorBlock.FACING);
        level.playSound(null, lower.above(), BUTTON_SOUND, SoundSource.BLOCKS, 0.6f, 0.9f);
        // the shaft's front row is right behind the doors
        call(level, by, machineFor(level, lower.relative(f, -1), f.getClockWise(), 0, lower.getY()), lower.getY());
    }

    private static void call(ServerLevel level, Player by, LiftMachine m, int y) {
        int landing = m == null ? -1 : m.shaft().landingAt(y);
        if (landing < 0) {
            by.sendOverlayMessage(Component.literal("Nothing happens.").withStyle(net.minecraft.ChatFormatting.GRAY));
            return;
        }
        m.hallCall(level, landing);
    }

    /**
     * The machine of the lift with a landing at height y: up the shaft columns from row, from min
     * to 4 blocks either side of it along r.
     */
    private static LiftMachine machineFor(ServerLevel level, BlockPos row, Direction r, int min, int y) {
        int top = Math.min(level.getMaxY(), y + 420);
        for (int k = min; k <= 4; k++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                BlockPos col = row.relative(r, sign * k);
                for (int yy = y; yy <= top; yy++) {
                    BlockPos at = new BlockPos(col.getX(), yy, col.getZ());
                    if (level.getBlockState(at).is(MACHINE)) {
                        if (level.getBlockEntity(at) instanceof LiftMachine m && m.shaft() != null && m.shaft().landingAt(y) >= 0) {
                            return m;
                        }
                        break;
                    }
                }
                if (k == 0) {
                    break;
                }
            }
        }
        return null;
    }

    private Lifts() {
    }
}
