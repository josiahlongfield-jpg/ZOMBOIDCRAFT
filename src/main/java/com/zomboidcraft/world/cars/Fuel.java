package com.zomboidcraft.world.cars;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.loot.items.GasCanItem;
import com.zomboidcraft.survival.TimedActions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's fuel for road vehicles: the town's gas pumps, each with its own fuel (Fuel Station Gas Min/Max, Empty
 * Chance, Infinite), working only while there's power ({@code PowerGrid}); filling a gas can or the car parked at the
 * pump; pouring a can into a tank and siphoning a tank into a can with a hose. The tank itself lives on {@link CarProp}.
 */
public final class Fuel {
    /** What a pump's underground tank holds when full, litres: Zomboid's Min/Max are shares of this. */
    public static final float PUMP_LITRES = 1000;
    /** How far from a pump its hose reaches a car's filler, blocks. */
    private static final double HOSE = 6;
    private static final double REACH = 5;

    /** Each pump's fuel by "dimension|x|y|z" (its lower block), once someone has used it. */
    private static final Map<String, Float> PUMPS = new HashMap<>();
    private static Path file;
    private static boolean dirty;

    public record Pump(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Pump> TYPE = new Type<>(ZomboidCraft.id("fuel_pump"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Pump> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Pump::pos, Pump::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Pump.TYPE, Pump.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Pump.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!player.isSpectator() && player.blockPosition().distSqr(payload.pos()) < REACH * REACH) {
                    usePump(player, payload.pos());
                }
            });
        });
        ServerLifecycleEvents.SERVER_STARTED.register(Fuel::open);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> save());
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (dirty && s.getTickCount() % 1200 == 0) {
                save();
            }
        });
    }

    // ---------------------------------------------------------------- pumps

    /** Whether this block is (half of) a gas station's pump. */
    public static boolean isPump(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return id.equals("gas_pump") || id.startsWith("gs_pump_") || id.startsWith("gs_oldpump_");
    }

    private static boolean smashed(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("smash");
    }

    /** The pump's lower block: its fuel is kept against that one. */
    private static BlockPos foot(ServerLevel level, BlockPos pos) {
        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
        return id.contains("_hi") && isPump(level.getBlockState(pos.below())) ? pos.below() : pos;
    }

    private static String key(ServerLevel level, BlockPos foot) {
        return level.dimension().identifier() + "|" + foot.getX() + "|" + foot.getY() + "|" + foot.getZ();
    }

    /** The litres left in a pump, rolled from the world's settings the first time anyone looks. */
    public static float pumpFuel(ServerLevel level, BlockPos foot) {
        return PUMPS.computeIfAbsent(key(level, foot), k -> {
            ZcConfig.World w = ZcConfig.get().world;
            java.util.Random rng = new java.util.Random(foot.asLong() * 31 + level.getSeed());
            if (rng.nextDouble() * 100 < w.fuelStationEmptyChance) {
                return 0f;
            }
            double lo = Math.min(w.fuelStationGasMin, w.fuelStationGasMax), hi = Math.max(w.fuelStationGasMin, w.fuelStationGasMax);
            dirty = true;
            return (float) ((lo + rng.nextDouble() * (hi - lo)) * PUMP_LITRES);
        });
    }

    private static void drawFrom(ServerLevel level, BlockPos foot, float litres) {
        if (ZcConfig.get().world.fuelStationGasInfinite) {
            return;
        }
        PUMPS.put(key(level, foot), Math.max(0, pumpFuel(level, foot) - litres));
        dirty = true;
    }

    /** G at a pump: fills the gas can in your hand, or else the car standing at the pump. */
    public static void usePump(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);
        if (!isPump(state)) {
            return;
        }
        if (smashed(state)) {
            say(player, "The pump's smashed open. Nothing comes out of it.");
            return;
        }
        BlockPos foot = foot(level, pos);
        if (!com.zomboidcraft.power.PowerGrid.hasPower(level, foot)) {
            say(player, "The pump's dead: no power. A generator nearby would run it.");
            return;
        }
        float left = ZcConfig.get().world.fuelStationGasInfinite ? Float.MAX_VALUE : pumpFuel(level, foot);
        if (left < 0.1f) {
            say(player, "The pump wheezes and gives nothing. Its tank's dry.");
            return;
        }
        ItemStack can = player.getMainHandItem();
        if (can.getItem() instanceof GasCanItem) {
            float room = GasCanItem.CAPACITY - GasCanItem.fuel(can);
            if (room < 0.05f) {
                say(player, "The can's full.");
                return;
            }
            TimedActions.start(player, "fuel_can", "Filling the gas can", Math.round(room * 5), p -> {
                ItemStack held = p.getMainHandItem();
                if (!(held.getItem() instanceof GasCanItem)) {
                    return;
                }
                float take = Math.min(GasCanItem.CAPACITY - GasCanItem.fuel(held), avail(level, foot));
                GasCanItem.setFuel(held, GasCanItem.fuel(held) + take);
                drawFrom(level, foot, take);
                level.playSound(null, foot, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.7f, 0.8f);
                say(p, String.format(java.util.Locale.ROOT, "The can's got %.1f L in it.", GasCanItem.fuel(held)));
            });
            return;
        }
        CarProp car = carAt(level, foot);
        if (car == null) {
            say(player, "Park a car beside the pump to fill it, or hold a gas can.");
            return;
        }
        float room = (1 - car.fuel()) * car.fuelCapacity();
        if (room < 0.2f) {
            say(player, "Its tank's already full.");
            return;
        }
        if (car.engineOn()) {
            say(player, "Turn the engine off first.");
            return;
        }
        TimedActions.start(player, "fuel_car", "Filling the tank", Math.round(Math.min(room, left) * 3), p -> {
            if (car.isRemoved() || car.distanceToSqr(Vec3.atCenterOf(foot)) > HOSE * HOSE) {
                return;
            }
            float take = Math.min((1 - car.fuel()) * car.fuelCapacity(), avail(level, foot));
            car.setFuel(car.fuel() + take / car.fuelCapacity());
            drawFrom(level, foot, take);
            level.playSound(null, foot, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.7f, 0.7f);
            say(p, String.format(java.util.Locale.ROOT, "%.0f L in. The gauge reads %s.", take, car.gauge()));
        });
    }

    private static float avail(ServerLevel level, BlockPos foot) {
        return ZcConfig.get().world.fuelStationGasInfinite ? Float.MAX_VALUE : pumpFuel(level, foot);
    }

    /** The road vehicle nearest the pump, in reach of its hose. */
    private static CarProp carAt(ServerLevel level, BlockPos foot) {
        CarProp best = null;
        double bestD = HOSE * HOSE;
        for (CarProp c : level.getEntitiesOfClass(CarProp.class, new net.minecraft.world.phys.AABB(foot).inflate(HOSE, 2, HOSE), CarProp::roadCar)) {
            double d = c.distanceToSqr(Vec3.atCenterOf(foot));
            if (d < bestD) {
                best = c;
                bestD = d;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- cans at the car

    /**
     * G at a car with a gas can in hand: pours it into the tank, or (an empty can, a hose in your bag, fuel in the
     * tank) siphons the tank into the can. Returns whether the can was what the press was about.
     */
    public static boolean useCan(ServerPlayer player, CarProp car) {
        ItemStack can = player.getMainHandItem();
        if (!(can.getItem() instanceof GasCanItem) || !car.roadCar()) {
            return false;
        }
        float inCan = GasCanItem.fuel(can), room = (1 - car.fuel()) * car.fuelCapacity();
        if (inCan >= 0.05f && room >= 0.2f) {
            TimedActions.start(player, "fuel_pour", "Pouring fuel into the tank", Math.round(Math.min(inCan, room) * 6), p -> {
                ItemStack held = p.getMainHandItem();
                if (!(held.getItem() instanceof GasCanItem) || car.isRemoved()) {
                    return;
                }
                float pour = Math.min(GasCanItem.fuel(held), (1 - car.fuel()) * car.fuelCapacity());
                GasCanItem.setFuel(held, GasCanItem.fuel(held) - pour);
                car.setFuel(car.fuel() + pour / car.fuelCapacity());
                p.level().playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.7f, 0.9f);
                say(p, String.format(java.util.Locale.ROOT, "%.1f L poured in. The gauge reads %s.", pour, car.gauge()));
            });
            return true;
        }
        if (inCan >= 0.05f) {
            say(player, "The tank's full.");
            return true;
        }
        if (car.fuel() * car.fuelCapacity() < 0.1f) {
            say(player, "Nothing in its tank to siphon.");
            return true;
        }
        if (!hasHose(player)) {
            say(player, "You'd need a hose to siphon it.");
            return true;
        }
        TimedActions.start(player, "fuel_siphon", "Siphoning the tank", 100, p -> {
            ItemStack held = p.getMainHandItem();
            if (!(held.getItem() instanceof GasCanItem) || car.isRemoved() || !hasHose(p)) {
                return;
            }
            float take = Math.min(GasCanItem.CAPACITY - GasCanItem.fuel(held), car.fuel() * car.fuelCapacity());
            GasCanItem.setFuel(held, GasCanItem.fuel(held) + take);
            car.setFuel(car.fuel() - take / car.fuelCapacity());
            say(p, String.format(java.util.Locale.ROOT, "You suck the hose and spit: %.1f L into the can.", take));
        });
        return true;
    }

    private static boolean hasHose(Player player) {
        net.minecraft.world.item.Item hose = com.zomboidcraft.loot.items.LootItems.item("rubber_hose");
        if (hose == null) {
            return false;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(hose)) {
                return true;
            }
        }
        return false;
    }

    private static void say(Player player, String text) {
        player.sendOverlayMessage(Component.literal(text));
    }

    // ---------------------------------------------------------------- saving

    private static void open(MinecraftServer server) {
        PUMPS.clear();
        dirty = false;
        file = server.getWorldPath(LevelResource.ROOT).resolve("zomboidcraft_pumps.json");
        if (Files.exists(file)) {
            try {
                JsonObject o = new Gson().fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
                o.entrySet().forEach(e -> PUMPS.put(e.getKey(), e.getValue().getAsFloat()));
            } catch (IOException | RuntimeException ex) {
                ZomboidCraft.LOGGER.warn("Couldn't read {}: {}", file, ex.toString());
            }
        }
    }

    private static void save() {
        if (file == null || !dirty) {
            return;
        }
        JsonObject o = new JsonObject();
        PUMPS.forEach(o::addProperty);
        try {
            Files.writeString(file, o.toString(), StandardCharsets.UTF_8);
            dirty = false;
        } catch (IOException ex) {
            ZomboidCraft.LOGGER.warn("Couldn't write {}: {}", file, ex.toString());
        }
    }

    private Fuel() {
    }
}
