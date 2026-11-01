package com.zomboidcraft.client.lifts;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.world.lifts.LiftCar;
import com.zomboidcraft.world.lifts.LiftShaft;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rides a test lift in a real client the way a player does (.github/workflows/liftride.yml pushes the shots):
 * G on the closed landing doors to call it, step in, G at the car's panel to open the floor window, click a floor
 * with the mouse, ride up, then pick the ground floor again with the keys. Logs what the client and server see.
 */
public class LiftRideTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCLIFT");
    private int frame;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode survival @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "effect give @a resistance 9999 4 true",
                    "fill -10 99 -16 10 99 6 stone", "fill -10 100 -16 10 130 6 air", "tp @a 0 100 0 180 0"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.waitTicks(10);
            sp.getServer().runCommand("execute as @p at @p run zc lift test 4");
            ctx.waitTicks(60);
            log(sp, ctx, "built");
            LiftShaft s = sp.getServer().computeOnServer(server -> LiftCar.loaded(false).isEmpty() ? null : LiftCar.loaded(false).get(0).shaft());
            if (s == null) {
                LOG.info("ZCLIFT no car on the server: the lift never started");
                return;
            }
            // 1. on the landing in front of the closed doors, looking at them, G
            BlockPos door = s.door(0, 0);
            double fx = s.f.getStepX(), fz = s.f.getStepZ();
            double dx = s.wx(s.w / 2.0, -0.5) + fx * 1.4, dz = s.wz(s.w / 2.0, -0.5) + fz * 1.4;
            stand(sp, dx, s.landings[0], dz, yaw(s.f.getOpposite()), 12);
            ctx.waitTicks(10);
            shot(ctx, "landing");
            String landingDot = ctx.computeOnClient(mc -> String.valueOf(dotAt(mc)));
            String aim = ctx.computeOnClient(mc -> mc.hitResult == null ? "none" : mc.hitResult.getType() + " " + mc.hitResult.getLocation());
            LOG.info("ZCLIFT dot at landing: {}, crosshair {}", landingDot, aim);
            ctx.getInput().pressKey(InputConstants.KEY_G);
            for (int i = 0; i < 6; i++) {
                ctx.waitTicks(10);
                shot(ctx, "calling");
            }
            log(sp, ctx, "after G on the doors");
            // 2. into the car, facing the panel on its right-hand wall
            double cx = s.wx(s.w / 2.0, s.d / 2.0), cz = s.wz(s.w / 2.0, s.d / 2.0);
            stand(sp, cx, s.landings[0], cz, yaw(s.r), 16);
            ctx.waitTicks(10);
            shot(ctx, "in-car");
            String dot = ctx.computeOnClient(mc -> String.valueOf(dotAt(mc)));
            LOG.info("ZCLIFT dot in car: {}", dot);
            ctx.getInput().pressKey(InputConstants.KEY_G);
            ctx.waitTicks(8);
            String screen = ctx.computeOnClient(mc -> mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
            LOG.info("ZCLIFT screen after G at the panel: {}", screen);
            shot(ctx, "panel-window");
            // 3. click the top floor with the mouse
            int top = s.landings.length - 1;
            int[] g = ctx.computeOnClient(mc -> mc.gui.screen() instanceof LiftScreen ls ? ls.cell(s, ls.box(s), top) : null);
            if (g != null) {
                click(ctx, g[0] + 12, g[1] + 12);
                ctx.waitTicks(6);
                shot(ctx, "clicked-top");
                log(sp, ctx, "after clicking the top floor");
            }
            for (int i = 0; i < 30; i++) {
                ctx.waitTicks(10);
                shot(ctx, "ride-up");
                if (i % 5 == 4) {
                    log(sp, ctx, "riding up " + i);
                }
            }
            // 4. the ground floor again, with the keys: down to it, Enter
            ctx.runOnClient(mc -> {
                if (!(mc.gui.screen() instanceof LiftScreen)) {
                    LOG.info("ZCLIFT the floor window closed on the way: {}", mc.gui.screen());
                }
            });
            if (ctx.computeOnClient(mc -> !(mc.gui.screen() instanceof LiftScreen))) {
                ctx.getInput().pressKey(InputConstants.KEY_G);
                ctx.waitTicks(8);
            }
            for (int i = 0; i < 6; i++) {
                ctx.getInput().pressKey(InputConstants.KEY_DOWN);
                ctx.waitTicks(2);
            }
            ctx.getInput().pressKey(InputConstants.KEY_RETURN);
            ctx.waitTicks(6);
            shot(ctx, "keys-ground");
            log(sp, ctx, "after Enter on the ground floor");
            for (int i = 0; i < 30; i++) {
                ctx.waitTicks(10);
                shot(ctx, "ride-down");
                if (i % 5 == 4) {
                    log(sp, ctx, "riding down " + i);
                }
            }
            LOG.info("ZCLIFT frames {}", frame);
        }
    }

    private static float yaw(Direction d) {
        return d == Direction.SOUTH ? 0 : d == Direction.WEST ? 90 : d == Direction.NORTH ? 180 : -90;
    }

    private static Object dotAt(net.minecraft.client.Minecraft mc) {
        try {
            var f = com.zomboidcraft.client.interact.Interactables.class.getDeclaredField("spot");
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            return e.toString();
        }
    }

    private static void stand(TestSingleplayerContext sp, double x, double y, double z, float yaw, float pitch) {
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.teleportTo(server.overworld(), x, y, z, java.util.Set.of(), yaw, pitch, false);
        });
    }

    private static void log(TestSingleplayerContext sp, ClientGameTestContext ctx, String when) {
        String server = sp.getServer().computeOnServer(sv -> {
            StringBuilder b = new StringBuilder();
            for (LiftCar c : LiftCar.loaded(false)) {
                b.append(String.format("car y %.2f at %d doors %.2f calls ", c.carY(), c.at(), c.doorsOpen()));
                for (int i = 0; c.shaft() != null && i < c.shaft().landings.length; i++) {
                    b.append(c.called(i) ? '1' : '0');
                }
            }
            ServerPlayer p = sv.getPlayerList().getPlayers().get(0);
            return b + String.format(" | player %.2f %.2f %.2f", p.getX(), p.getY(), p.getZ());
        });
        String client = ctx.computeOnClient(mc -> {
            StringBuilder b = new StringBuilder();
            for (LiftCar c : LiftCar.loaded(true)) {
                b.append(String.format("car y %.2f calls ", c.carY()));
                for (int i = 0; c.shaft() != null && i < c.shaft().landings.length; i++) {
                    b.append(c.called(i) ? '1' : '0');
                }
            }
            return b + String.format(" | player %.2f %.2f %.2f screen %s", mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
        });
        LOG.info("ZCLIFT {}: server [{}] client [{}]", when, server, client);
    }

    private static void click(ClientGameTestContext ctx, int gx, int gy) {
        double[] px = ctx.computeOnClient(mc -> {
            var w = mc.getWindow();
            double f = (double) w.getScreenWidth() / w.getGuiScaledWidth();
            return new double[] {(gx + 0.5) * f, (gy + 0.5) * f};
        });
        ctx.getInput().setCursorPos(px[0], px[1]);
        ctx.waitTick();
        ctx.getInput().pressMouse(0);
        LOG.info("ZCLIFT click at gui {},{} -> window {},{}", gx, gy, px[0], px[1]);
    }

    private void shot(ClientGameTestContext ctx, String name) {
        ctx.takeScreenshot(String.format("lift-%04d-%s", frame++, name));
    }
}
