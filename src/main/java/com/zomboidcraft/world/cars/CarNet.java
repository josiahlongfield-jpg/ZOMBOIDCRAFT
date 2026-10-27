package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The interact key on a car: get in, search the boot, or (pressed inside one) get out; the seat
 * picked by its door handle, or a seat moved to from inside; and the horn, the ignition and the
 * headlights. Keys of their own rather than right-click, which aims a gun.
 */
public final class CarNet {
    /** Stand-ins for a car id: get out of the car you're in, sound its horn, turn the key, switch the headlights. */
    public static final int GET_OUT = -1, HORN = -2, ENGINE = -3, LIGHTS = -4, FOB = -5;

    /** Use car (its entity id) at a point relative to it, or GET_OUT or HORN for the car you're in. */
    public record Use(int car, float x, float y, float z) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(ZomboidCraft.id("use_car"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Use::car, ByteBufCodecs.FLOAT, Use::x, ByteBufCodecs.FLOAT, Use::y,
                ByteBufCodecs.FLOAT, Use::z, Use::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Into seat of car (its entity id) by that seat's door, from outside; or, from inside it, over
     * into that seat.
     */
    public record Seat(int car, int seat) implements CustomPacketPayload {
        /** Not a seat: open the glovebox, from a front seat. */
        public static final int GLOVEBOX = -1;
        public static final Type<Seat> TYPE = new Type<>(ZomboidCraft.id("car_seat"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Seat> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Seat::car, ByteBufCodecs.VAR_INT, Seat::seat, Seat::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Seat.TYPE, Seat.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Seat.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!(player.level().getEntity(payload.car()) instanceof CarProp car) || player.isSpectator()) {
                    return;
                }
                if (player.getVehicle() == car) {
                    if (payload.seat() == Seat.GLOVEBOX) {
                        car.openGlovebox(player);
                    } else {
                        car.switchSeat(player, payload.seat());
                    }
                } else if (player.getVehicle() == null && car.seatEye(payload.seat()).distanceToSqr(player.getEyePosition()) < 4 * 4) {
                    car.getIn(player, payload.seat());
                }
            });
        });
        // a driver on the move has a hand on the wheel: no swinging anything (handguns aside)
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                CarProp.mayUse(player, player.getMainHandItem()) ? net.minecraft.world.InteractionResult.PASS
                        : net.minecraft.world.InteractionResult.FAIL);
        PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (payload.car() == FOB) {
                    panicButton(player);
                    return;
                }
                if (payload.car() < 0) {
                    if (player.getVehicle() instanceof CarProp car) {
                        switch (payload.car()) {
                            case HORN -> car.honk(player);
                            case ENGINE -> car.toggleEngine(player);
                            case LIGHTS -> car.toggleLights(player);
                            default -> player.stopRiding();
                        }
                    }
                    return;
                }
                if (player.level().getEntity(payload.car()) instanceof CarProp car && !player.isSpectator()
                        && car.getBoundingBox().inflate(1).distanceToSqr(player.getEyePosition()) < 4 * 4) {
                    car.use(player, new Vec3(payload.x(), payload.y(), payload.z()));
                }
            });
        });
    }

    /**
     * The car key's panic button, pressed with the interact key while holding it and not at a car: from up to
     * keyFobRange blocks its car's alarm whoops (a decoy to pull zombies off you), and pressed again it stops.
     */
    private static void panicButton(ServerPlayer player) {
        net.minecraft.world.item.ItemStack key = player.getMainHandItem();
        if (!key.is(com.zomboidcraft.ModItems.CAR_KEY) || player.isSpectator()) {
            return;
        }
        net.minecraft.world.item.component.CustomData data = key.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        String id = data == null ? "" : data.copyTag().getStringOr("car", "");
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
        level.playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.25f, 2f);
        net.minecraft.world.entity.Entity found = null;
        try {
            found = id.isEmpty() ? null : level.getEntity(java.util.UUID.fromString(id));
        } catch (IllegalArgumentException ignored) {
            // not a key cut for a car
        }
        double range = com.zomboidcraft.ZcConfig.get().building.keyFobRange;
        String said;
        if (!(found instanceof CarProp car) || car.distanceToSqr(player) > range * range) {
            said = "Nothing. The car must be out of range.";
        } else if (car.alarming()) {
            car.stopAlarm();
            said = "Alarm off.";
        } else if (car.tripAlarm(com.zomboidcraft.ZcConfig.get().building.carAlarmSeconds * 20)) {
            said = "Panic button: the car's alarm is going off.";
        } else {
            said = car.isVehicle() ? "Nothing. Someone's in it." : "Nothing. Its battery must be dead.";
        }
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(said).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private CarNet() {
    }
}
