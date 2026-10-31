package com.zomboidcraft.client.cars;

import com.zomboidcraft.loot.items.GasCanItem;
import com.zomboidcraft.loot.items.LootItems;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import com.zomboidcraft.world.cars.Fuel;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Films road-car fuel for the preview (.github/workflows/fuelfilm.yml joins the frames): a sedan run nearly dry
 * beside a gas station pump, a gas can filled at the pump and poured into the tank, then the tank filled from the
 * pump's hose, each with its progress bar and the gauge read out.
 */
public class FuelFilmTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCFUEL");
    private int frame;
    private Vec3 base;
    private BlockPos pump;
    private int carId;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.level();
                BlockPos b = p.blockPosition().above(6);
                base = new Vec3(b.getX(), b.getY(), b.getZ());
                // a station forecourt: a concrete apron under a canopy lit from below, the pump on its island
                for (int x = -9; x <= 9; x++) {
                    for (int z = -9; z <= 9; z++) {
                        for (int y = -1; y <= 6; y++) {
                            level.setBlock(b.offset(x, y, z), (y == 6 ? (x % 3 == 0 && z % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_STONE)
                                    : y == -1 ? Blocks.SMOOTH_STONE : Blocks.AIR).defaultBlockState(), 2);
                        }
                    }
                }
                pump = b.offset(0, 0, 0);
                level.setBlock(b.offset(0, -1, 0), Blocks.POLISHED_ANDESITE.defaultBlockState(), 2);
                level.setBlock(pump, CityBlocks.block("gs_pump_c_lo").defaultBlockState(), 2);
                level.setBlock(pump.above(), CityBlocks.block("gs_pump_c_hi").defaultBlockState(), 2);
                CarProp car = Cars.spawn(level, b.getX() + 2.6, b.getY(), b.getZ() + 0.5, 0, "sedan", "usable", true);
                carId = car.getId();
            });
            ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> {
                if (server.overworld().getEntity(carId) instanceof CarProp car) {
                    car.setFuel(0.03f);
                    LOG.info("ZCFUEL car tank {} L, gauge {}", car.fuelCapacity(), car.gauge());
                }
            });
            // 1. the forecourt: the pump on its island, the car beside it
            shot(sp, -4.0, 1.7, -5.0, -44, 12);
            frames(ctx, 14, 2);
            // 2. an empty gas can, filled at the pump
            sp.getServer().runCommand("gamemode survival @a");
            stand(sp, -1.2, -1.2, -40, 25);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ItemStack can = new ItemStack(LootItems.item("gas_can"));
                p.setItemInHand(InteractionHand.MAIN_HAND, can);
                Fuel.usePump(p, pump);
                LOG.info("ZCFUEL can filling");
            });
            frames(ctx, 30, 2);
            // 3. poured into the car's tank
            stand(sp, 1.4, -1.6, -24, 30);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                LOG.info("ZCFUEL can holds {} L", GasCanItem.fuel(p.getMainHandItem()));
                if (server.overworld().getEntity(carId) instanceof CarProp car) {
                    Fuel.useCan(p, car);
                }
            });
            frames(ctx, 36, 2);
            // 4. the pump's hose into the tank, hands empty
            stand(sp, -1.2, -1.2, -40, 25);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                if (server.overworld().getEntity(carId) instanceof CarProp car) {
                    LOG.info("ZCFUEL after the can: {}", car.gauge());
                }
                p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                Fuel.usePump(p, pump);
            });
            frames(ctx, 60, 3);
            sp.getServer().runOnServer(server -> {
                if (server.overworld().getEntity(carId) instanceof CarProp car) {
                    LOG.info("ZCFUEL after the pump: {} ({} of {} L)", car.gauge(), car.fuel() * car.fuelCapacity(), car.fuelCapacity());
                }
            });
            sp.getServer().runCommand("gamemode creative @a");
            shot(sp, 4.5, 1.7, -4.5, 31, 15);
            frames(ctx, 10, 2);
            LOG.info("ZCFUEL frames {}", frame);
        }
    }

    /** Stands the player (on foot, survival) at an offset from the pump, looking yaw and pitch. */
    private void stand(TestSingleplayerContext sp, double x, double z, double yaw, double pitch) {
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.teleportTo(server.overworld(), base.x + 0.5 + x, base.y, base.z + 0.5 + z, java.util.Set.of(), (float) yaw, (float) pitch, false);
        });
    }

    /** Puts the camera at an offset from the pump, looking yaw and pitch. */
    private void shot(TestSingleplayerContext sp, double x, double eyeY, double z, double yaw, double pitch) {
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(server.overworld(), base.x + x, base.y + eyeY - p.getEyeHeight(), base.z + z, java.util.Set.of(), (float) yaw,
                    (float) pitch, false);
        });
    }

    private void frames(ClientGameTestContext ctx, int count, int every) {
        for (int i = 0; i < count; i++) {
            ctx.waitTicks(every);
            ctx.takeScreenshot(String.format("fuel-%04d", frame++));
        }
    }
}
