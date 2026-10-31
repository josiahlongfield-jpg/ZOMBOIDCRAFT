package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.CarHoist;
import com.zomboidcraft.world.cars.CarParts;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Films the car lift working, frame by frame, for the preview video (.github/workflows/hoistfilm.yml joins the
 * frames): a sedan parked in a bay on a concrete floor, the arms swinging in, the car going up through the three
 * notches, a walk underneath, the control box and power unit up close, the mechanics screen on the raised car,
 * and the valve letting it down.
 */
public class HoistFilmTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCHOIST");
    private int frame;
    private Vec3 base;
    private int hoistId, carId;

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
                // a garage: concrete floor, back wall, and the air cleared round it
                for (int x = -9; x <= 9; x++) {
                    for (int z = -9; z <= 9; z++) {
                        for (int y = -1; y <= 7; y++) {
                            BlockPos at = b.offset(x, y, z);
                            // a roof against the rain, lit by a grid of ceiling lamps
                            level.setBlock(at, y == 7 ? (x % 3 == 0 && z % 3 == 0 ? Blocks.SEA_LANTERN : Blocks.SMOOTH_STONE).defaultBlockState()
                                    : y == -1 ? Blocks.SMOOTH_STONE.defaultBlockState()
                                    : z == 6 && y < 6 || Math.abs(x) == 9 && y < 6 ? Blocks.SMOOTH_STONE.defaultBlockState()
                                    : Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
                CarHoist hoist = Cars.HOIST.create(level, EntitySpawnReason.COMMAND);
                hoist.snapTo(b.getX(), b.getY(), b.getZ(), 0, 0);
                level.addFreshEntity(hoist);
                hoistId = hoist.getId();
                CarProp car = Cars.spawn(level, b.getX() + 0.8, b.getY(), b.getZ() + 1.3, 14, "step_van", "usable", true);
                carId = car.getId();
                p.teleportTo(level, b.getX() + 0.5, b.getY(), b.getZ() - 8, java.util.Set.of(), 0, 15, false);
            });
            ctx.waitTicks(40);
            // 1. the bay, from the front and a little to the side
            shot(sp, -3.2, 1.7, -6.5, -25, 12);
            frames(ctx, 12, 2);
            // 2. the arms swing in
            use(sp, CarHoist.Use.CONTROL);
            frames(ctx, 18, 2);
            // 3. up through wheels free, chest height and full height
            for (int n = 0; n < 3; n++) {
                use(sp, CarHoist.Use.CONTROL);
                frames(ctx, n == 0 ? 10 : 16, 2);
            }
            frames(ctx, 10, 2);
            // 4. walk in under it, looking up at the underside
            for (int i = 0; i <= 20; i++) {
                double t = i / 20.0;
                shot(sp, -3.2 + 3.0 * t, 1.6, -6.5 + 5.5 * t, -25 + 25 * t, 12 - 50 * t);
                frames(ctx, 1, 2);
            }
            frames(ctx, 8, 2);
            // 5. the control box and power unit up close
            shot(sp, 3.2, 1.45, -0.9, 57, 5);
            frames(ctx, 14, 2);
            // the power unit and the hand pump's lever
            shot(sp, 3.0, 1.3, 0.9, 128, 20);
            frames(ctx, 6, 2);
            // 6. the mechanics screen on the raised car, as a survivor sees it
            sp.getServer().runCommand("gamemode survival @a");
            sp.getServer().runOnServer(server -> {
                if (server.overworld().getEntity(carId) instanceof CarProp car && !car.parts().isEmpty()) {
                    String parts = car.parts();
                    parts = CarParts.set(parts, CarParts.slot(CarParts.Part.TIRE, 0), 0);
                    parts = CarParts.set(parts, CarParts.slot(CarParts.Part.BRAKES, 1), 12);
                    parts = CarParts.set(parts, CarParts.slot(CarParts.Part.EXHAUST, 0), 18);
                    car.setParts(parts);
                    LOG.info("ZCHOIST parts {}", CarParts.describe(car.model(), car.parts()));
                }
            });
            shot(sp, 0, 1.0, -4.2, 0, 10);
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> {
                if (mc.level.getEntity(carId) instanceof CarProp car) {
                    mc.gui.setScreen(new com.zomboidcraft.client.kit.MechanicsScreen(car));
                }
            });
            frames(ctx, 16, 2);
            ctx.runOnClient(mc -> {
                mc.gui.setScreen(null);
            });
            sp.getServer().runCommand("gamemode creative @a");
            // 7. the valve: down to the floor, arms out
            shot(sp, -3.2, 1.7, -6.5, -25, 12);
            for (int n = 0; n < 3; n++) {
                use(sp, CarHoist.Use.CONTROL_DOWN);
                frames(ctx, n == 2 ? 30 : 22, 2);
            }
            use(sp, CarHoist.Use.CONTROL_DOWN);
            frames(ctx, 16, 2);
            LOG.info("ZCHOIST frames {}", frame);
        }
    }

    private void use(TestSingleplayerContext sp, CarHoist.Use use) {
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getEntity(hoistId) instanceof CarHoist hoist) {
                hoist.use(p, use);
                LOG.info("ZCHOIST {} -> height {} target {} arms {}", use, hoist.height(), hoist.target(), hoist.arms());
            }
        });
    }

    /** Puts the camera at an offset from the bay's middle, looking yaw and pitch. */
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
            ctx.takeScreenshot(String.format("hoist-%04d", frame++));
        }
    }
}
