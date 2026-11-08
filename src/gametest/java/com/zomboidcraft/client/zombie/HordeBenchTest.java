package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieSpawner;
import com.zomboidcraft.zombie.ZombieTypes;
import com.zomboidcraft.zombie.perf.HordeStats;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The horde benchmark (FPS thread, 2026-11-08): 800 walkers round a player stood on a pillar in a walled yard, the
 * server's tick time measured with the horde savings off and on, turn about, so the two see the same crowd. A noise
 * moves round the yard every second, so the horde keeps re-planning. Every reading is a ZCBENCH line in the log.
 * Run by .github/workflows/hordebench.yml (also with Lithium).
 */
public class HordeBenchTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCBENCH");
    private static final int COUNT = Integer.getInteger("zc.bench.count", 800);
    private static final int YARD = 70, FLOOR = 99;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode survival @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false", "difficulty normal",
                    "effect give @a resistance infinite 255 true", "effect give @a regeneration infinite 255 true",
                    "effect give @a saturation infinite 255 true"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(320, 180);
            ctx.runOnClient(mc -> {
                mc.options.renderDistance().set(4);
                mc.options.framerateLimit().set(10);
            });
            sp.getServer().runCommand("tp @a 0 " + (FLOOR + 5) + " 0 0 -90");
            ctx.waitTicks(40);
            sp.getServer().runOnServer(HordeBenchTest::yard);
            ctx.waitTicks(40);
            sp.getServer().runCommand("kill @e[type=!player]");
            sp.getServer().runCommand("tp @a 0 " + (FLOOR + 5) + " 0 0 -90");
            ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                int made = ZombieSpawner.spawnAround((ServerLevel) p.level(), p, COUNT, 40);
                LOG.info("ZCBENCH spawned {} of {} walkers", made, COUNT);
            });
            ctx.waitTicks(200);
            String[] phases = {"off", "on", "off", "on", "resting", "collisions", "paths", "on"};
            for (String phase : phases) {
                set(phase);
                ctx.waitTicks(60);          // the change settles, the 100-tick average starts to turn over
                measure(ctx, sp, phase, 400);
            }
        }
    }

    /** A flat stone yard, cleared above, a ring of low walls and a few huts with a gap in each, and the pillar. */
    private static void yard(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BlockState stone = Blocks.STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -YARD; x <= YARD; x++) {
            for (int z = -YARD; z <= YARD; z++) {
                level.setBlock(m.set(x, FLOOR, z), stone, 2);
                for (int y = FLOOR + 1; y < FLOOR + 14; y++) {
                    level.setBlock(m.set(x, y, z), air, 2);
                }
                boolean edge = Math.abs(x) == YARD || Math.abs(z) == YARD;
                if (edge) {
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                        level.setBlock(m.set(x, y, z), stone, 2);
                    }
                }
            }
        }
        // huts: 7x7, 3 high, a doorway on one side; zombies have to path round and through them
        java.util.Random r = new java.util.Random(26_11_08);
        for (int i = 0; i < 24; i++) {
            int cx = r.nextInt(2 * YARD - 20) - YARD + 10, cz = r.nextInt(2 * YARD - 20) - YARD + 10;
            if (Math.abs(cx) < 8 && Math.abs(cz) < 8) {
                continue;
            }
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) != 3 && Math.abs(dz) != 3 || dx == 0 && dz == 3) {
                        continue;
                    }
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                        level.setBlock(m.set(cx + dx, y, cz + dz), stone, 2);
                    }
                }
            }
        }
        for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
            level.setBlock(m.set(0, y, 0), stone, 2);
        }
    }

    private static void set(String phase) {
        ZcConfig.Performance p = ZcConfig.get().performance;
        boolean all = phase.equals("on");
        p.restingWalkers = all || phase.equals("resting");
        p.quietCollisions = all || phase.equals("collisions");
        p.sharedPaths = all || phase.equals("paths");
    }

    /** The server's tick over {@code ticks} ticks, read each 100 (its own rolling average), with a noise moving round. */
    private static void measure(ClientGameTestContext ctx, TestSingleplayerContext sp, String phase, int ticks) {
        HordeStats.reset();
        double sum = 0, worst = 0;
        int reads = 0;
        for (int t = 0; t < ticks; t += 20) {
            int step = t;
            sp.getServer().runOnServer(server -> {
                double a = step / 20.0 * 0.7;
                Noise.emit(server.overworld(), new Vec3(Math.cos(a) * 12, FLOOR + 1, Math.sin(a) * 12), 48);
            });
            ctx.waitTicks(20);
            if ((t + 20) % 100 == 0) {
                double[] ms = new double[1];
                sp.getServer().runOnServer(server -> ms[0] = server.getAverageTickTimeNanos() / 1e6);
                sum += ms[0];
                worst = Math.max(worst, ms[0]);
                reads++;
            }
        }
        int[] census = new int[3];
        sp.getServer().runOnServer(server -> {
            for (Entity e : server.overworld().getAllEntities()) {
                if (ZombieTypes.isWalker(e)) {
                    census[0]++;
                    if (((Mob) e).getTarget() != null) {
                        census[1]++;
                    }
                    if (e.getDeltaMovement().horizontalDistanceSqr() < 1e-4) {
                        census[2]++;
                    }
                }
            }
        });
        LOG.info(String.format(Locale.ROOT, "ZCBENCH phase %-10s tick %.1f ms (worst 100-tick average %.1f) walkers %d chasing %d still %d | %s",
                phase, sum / Math.max(1, reads), worst, census[0], census[1], census[2], HordeStats.line()));
    }
}
