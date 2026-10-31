package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.population.Herds;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames for the population preview video (Zomboid's Zombie Population settings, wave 2): idle dead drifting together
 * into rally groups, and a herd far off turning toward gunfire and coming on as a crowd. CI pushes the numbered
 * screenshots to the shots/w8d2gc branch. Run by .github/workflows/zombieshots.yml.
 */
public class ZombieHerdTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCHERD");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            stage(ctx, sp);
            rally(ctx, sp);
            herd(ctx, sp);
        }
    }

    /** A flat yard of stone 140 blocks across, cleared above, and a pillar to watch from. */
    private static void stage(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("tp @a 0 140 -30");
        ctx.waitTicks(40);
        sp.getServer().runCommand("fill -70 99 -100 70 99 40 minecraft:stone");
        for (int y = 100; y < 116; y++) {
            sp.getServer().runCommand("fill -70 " + y + " -100 70 " + y + " 40 minecraft:air");
        }
        sp.getServer().runCommand("kill @e[type=!player]");
        ctx.waitTicks(40);
        sp.getConnection().waitForChunksRender();
    }

    /** Thirty-six idle dead scattered over a field; seen from above, they gather into groups of about twenty. */
    private static void rally(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runOnServer(server -> {
            var level = server.overworld();
            var random = level.getRandom();
            for (int i = 0; i < 36; i++) {
                double x = -25 + random.nextDouble() * 50, z = -55 + random.nextDouble() * 50;
                Zombie z0 = net.minecraft.world.entity.EntityTypes.ZOMBIE.spawn(level, net.minecraft.core.BlockPos.containing(x, 100, z),
                        net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                if (z0 != null) {
                    z0.setPersistenceRequired();
                }
            }
        });
        sp.getServer().runCommand("setblock 0 139 0 minecraft:barrier");
        sp.getServer().runCommand("tp @a 0 140 0 180 62");
        ctx.waitTicks(20);
        for (int f = 0; f < 70; f++) {
            ctx.waitTicks(20);
            ctx.takeScreenshot(String.format("rally-%03d", f));
            if (f % 10 == 0) {
                logGroups(sp, f);
            }
        }
        sp.getServer().runCommand("kill @e[type=zombie]");
        sp.getServer().runCommand("kill @e[type=zomboidcraft:corpse]");
        ctx.waitTicks(10);
    }

    private static void logGroups(TestSingleplayerContext sp, int f) {
        sp.getServer().runOnServer(server -> {
            var list = server.overworld().getEntitiesOfClass(Zombie.class, new AABB(-70, 90, -100, 70, 130, 40));
            int clumped = 0;
            for (Zombie z : list) {
                int near = server.overworld().getEntitiesOfClass(Zombie.class, z.getBoundingBox().inflate(4)).size() - 1;
                if (near >= 3) {
                    clumped++;
                }
            }
            LOG.info("ZCHERD rally frame {}: {} of {} stand with 3 or more others", f, clumped, list.size());
        });
    }

    /**
     * A herd of sixty set down 85 blocks off as numbers, out of street range (40 here, so it shows). A gunshot where
     * you stand: it turns, walks this way as a record, and spills out as real dead once it's in range.
     */
    private static void herd(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        for (String c : new String[] {"zc config population.density 0", "zc config population.streetRange 40",
                "zc config population.crowd false", "zc config population.enabled true"}) {
            sp.getServer().runCommand(c);
        }
        sp.getServer().runOnServer(server -> Herds.add(server.overworld(), 0, -80, 60));
        sp.getServer().runCommand("setblock 0 111 8 minecraft:barrier");
        sp.getServer().runCommand("tp @a 0 112 8 180 22");
        ctx.waitTicks(40);
        for (int f = 0; f < 95; f++) {
            if (f % 15 == 0) {
                // a rifle shot from the pillar
                sp.getServer().runOnServer(server -> Noise.emit(server.overworld(), new Vec3(0, 100, 5), 100));
            }
            ctx.waitTicks(40);
            ctx.takeScreenshot(String.format("herd-%03d", f));
            if (f % 5 == 0) {
                final int frame = f;
                sp.getServer().runOnServer(server -> {
                    var level = server.overworld();
                    StringBuilder b = new StringBuilder();
                    for (Herds.Herd h : Herds.list(level)) {
                        b.append(String.format(" [%.0f,%.0f x%d mode %d]", h.x(), h.z(), h.count(), h.mode()));
                    }
                    int real = level.getEntitiesOfClass(Zombie.class, new AABB(-70, 90, -100, 70, 130, 40)).size();
                    LOG.info("ZCHERD herd frame {}: herds{} real {}", frame, b, real);
                });
            }
        }
        sp.getServer().runCommand("zc config population.enabled false");
        ZcConfig.get().population.streetRange = 96;
    }
}
