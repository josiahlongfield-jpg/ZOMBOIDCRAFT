package com.zomboidcraft.client.zombie;

import com.zomboidcraft.world.events.SearchHeli;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames for the wave 4 preview: the search helicopter from arrival to losing you indoors. It comes in, sweeps its
 * light, finds the player in the open and hovers over them while the dead from all round walk in; the player gets
 * under a roof and it circles, searching, then gives up. The camera is the player, always looking at the helicopter.
 * CI pushes the numbered screenshots to the shots/w8d2gc branch. Run by .github/workflows/zombieshots.yml.
 */
public class ZombieHeliTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCHELI");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set midnight", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false", "zc config nature.climateCycle no_weather",
                    "zc config nature.fogCycle no_fog", "zc config searchHeli.frequency never", "difficulty normal"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            sp.getServer().runCommand("tp @a 0 140 0");
            ctx.waitTicks(40);
            // in quarters: one fill can't take more than 32768 blocks
            for (int[] q : new int[][] {{-100, -100}, {0, -100}, {-100, 0}, {0, 0}}) {
                String box = String.format("%d %%d %d %d %%d %d", q[0], q[1], q[0] + 100, q[1] + 100);
                sp.getServer().runCommand("fill " + String.format(box, 99, 99) + " minecraft:stone");
                for (int y = 100; y < 112; y++) {
                    sp.getServer().runCommand("fill " + String.format(box, y, y) + " minecraft:air");
                }
            }
            // a shelter: a stone roof on four posts, to get under
            sp.getServer().runCommand("fill 14 100 -4 14 103 -4 minecraft:stone_bricks");
            sp.getServer().runCommand("fill 22 100 -4 22 103 -4 minecraft:stone_bricks");
            sp.getServer().runCommand("fill 14 100 4 14 103 4 minecraft:stone_bricks");
            sp.getServer().runCommand("fill 22 100 4 22 103 4 minecraft:stone_bricks");
            sp.getServer().runCommand("fill 13 104 -5 23 104 5 minecraft:stone_brick_slab");
            sp.getServer().runCommand("kill @e[type=!player]");
            ctx.waitTicks(60);
            sp.getConnection().waitForChunksRender();
            sp.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                var random = level.getRandom();
                for (int i = 0; i < 40; i++) {
                    double a = random.nextDouble() * Math.PI * 2, r = 45 + random.nextDouble() * 40;
                    Zombie z = EntityTypes.ZOMBIE.spawn(level, BlockPos.containing(Math.cos(a) * r, 100, Math.sin(a) * r),
                            EntitySpawnReason.COMMAND);
                    if (z != null) {
                        z.setPersistenceRequired();
                    }
                }
            });
            sp.getServer().runCommand("tp @a 0 100 0");
            sp.getServer().runCommand("gamemode survival @a");
            sp.getServer().runCommand("effect give @a resistance infinite 10 true");
            sp.getServer().runCommand("effect give @a saturation infinite 10 true");
            ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> SearchHeli.start(server.overworld(), server.getPlayerList().getPlayers().get(0), 260));
            double[] stand = {0, 0};
            for (int f = 0; f < 120; f++) {
                if (f == 70) {
                    stand = new double[] {18, 0};   // under the roof
                }
                final double sx = stand[0], sz = stand[1];
                final int frame = f;
                sp.getServer().runOnServer(server -> {
                    Vec3 heli = SearchHeli.position();
                    var p = server.getPlayerList().getPlayers().get(0);
                    float yaw = 0, pitch = -20;
                    if (heli != null) {
                        Vec3 to = heli.subtract(sx, 101.6, sz);
                        yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                        pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
                        pitch = Math.max(pitch, -72f);
                    }
                    // now and then look down at the dead walking in instead
                    boolean crowd = frame >= 46 && frame < 62 || frame >= 100 && frame < 110;
                    if (crowd) {
                        Vec3 sum = Vec3.ZERO;
                        int n = 0;
                        for (Zombie z : server.overworld().getEntitiesOfClass(Zombie.class, new AABB(sx - 60, 95, sz - 60, sx + 60, 110, sz + 60))) {
                            sum = sum.add(z.position());
                            n++;
                        }
                        if (n > 0) {
                            Vec3 to = sum.scale(1.0 / n).subtract(sx, 101.6, sz);
                            yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                            pitch = 12;
                        }
                    }
                    // the searchlight shows best in the dark; the crowd needs the night vision
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), crowd
                            ? "effect give @a night_vision infinite 0 true" : "effect clear @a night_vision");
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                            String.format("tp %s %.2f 100 %.2f %.1f %.1f", p.getName().getString(), sx, sz, yaw, pitch));
                    if (frame % 5 == 0) {
                        int near = server.overworld().getEntitiesOfClass(Zombie.class, new AABB(sx - 20, 95, sz - 20, sx + 20, 110, sz + 20)).size();
                        LOG.info("ZCHELI frame {}: phase {} at {} zombies within 20: {}", frame, SearchHeli.phase(),
                                heli == null ? "-" : String.format("%.0f %.0f %.0f", heli.x, heli.y, heli.z), near);
                    }
                });
                ctx.waitTicks(15);
                ctx.takeScreenshot(String.format("heli-%03d", f));
            }
            sp.getServer().runOnServer(server -> SearchHeli.end());
            sp.getServer().runCommand("gamemode creative @a");
        }
    }
}
