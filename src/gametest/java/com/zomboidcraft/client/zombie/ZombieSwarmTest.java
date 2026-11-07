package com.zomboidcraft.client.zombie;

import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieTypes;
import com.zomboidcraft.zombie.population.Population;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames and numbers for the swarm preview: a planned county world (seed 12345), the player dropped in the middle of a
 * metro downtown at dusk with the new population defaults, then gunfire every few seconds. Logs, twice a second,
 * how many dead are real, the cap the tuner has settled on and the server's tick time, so the clip comes with proof
 * the server holds. CI pushes the numbered screenshots to the shots/w8d2gc branch. Run by
 * .github/workflows/zombieshots.yml.
 */
public class ZombieSwarmTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCSWARM");
    private static final long SEED = 12345L;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().adjustSettings(s -> {
            s.setSeed(String.valueOf(SEED));
            var all = new java.util.ArrayList<>(s.getNormalPresetList());
            all.addAll(s.getAltPresetList());
            for (var e : all) {
                if (e.preset() != null && e.preset().unwrapKey().map(k -> k.identifier().toString().equals("zomboidcraft:county")).orElse(false)) {
                    s.setWorldType(e);
                }
            }
        }).create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set 13000", "weather clear", "gamerule doDaylightCycle false",
                    "zc config nature.climateCycle no_weather", "zc config nature.fogCycle no_fog", "zc config searchHeli.frequency never",
                    "difficulty normal"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            int[] at = sp.getServer().computeOnServer(server -> {
                boolean county = server.overworld().getChunkSource().getGenerator() instanceof CountyChunkGenerator;
                County c = CountyChunkGenerator.gen(SEED).county();
                Place best = null;
                for (Place p : c.allPlaces()) {
                    if (p.type.name().equals("DOWNTOWN") && "metro".equals(p.variant) && !c.island(p.centreX(), p.centreZ())
                            && (best == null || Math.hypot(p.centreX(), p.centreZ()) < Math.hypot(best.centreX(), best.centreZ()))) {
                        best = p;
                    }
                }
                if (best == null) {
                    return new int[] {0, 100, 0, county ? 1 : 0};
                }
                int x = best.centreX(), z = best.centreZ();
                List<Place> near = c.placesNear(x - 32, z - 32, x + 32, z + 32, County.SKIRT);
                return new int[] {x, CountyChunkGenerator.surface(c, x, z, near) + 1, z, county ? 1 : 0};
            });
            LOG.info("ZCSWARM county world {} downtown at {} {} {}", at[3] == 1, at[0], at[1], at[2]);
            sp.getServer().runCommand(String.format("tp @a %d %d %d", at[0], at[1] + 40, at[2]));
            ctx.waitTicks(100);
            sp.getConnection().waitForChunksRender();
            // let the dead be placed round us before the street view
            for (int i = 0; i < 12; i++) {
                ctx.waitTicks(20);
                log(sp, "settle " + i);
            }
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            sp.getServer().runCommand("effect give @a resistance infinite 10 true");
            sp.getServer().runCommand("effect give @a saturation infinite 10 true");
            sp.getServer().runCommand(String.format("tp @a %d %d %d 0 25", at[0], at[1] + 10, at[2]));
            sp.getServer().runCommand(String.format("setblock %d %d %d minecraft:barrier", at[0], at[1] + 9, at[2]));
            sp.getServer().runCommand("gamemode survival @a");
            ctx.waitTicks(20);
            for (int f = 0; f < 160; f++) {
                if (f % 12 == 0) {
                    // a rifle shot, as loud as one (Guns' shotNoise)
                    sp.getServer().runOnServer(server -> {
                        var p = server.getPlayerList().getPlayers().get(0);
                        Noise.emit(server.overworld(), p.position(), 90);
                    });
                }
                sp.getServer().runCommand(String.format("tp @a %d %d %d %.1f %d", at[0], at[1] + 10, at[2], (f * 2.25f) % 360, f < 80 ? 35 : 60));
                ctx.waitTicks(10);
                ctx.takeScreenshot(String.format("swarm-%03d", f));
                if (f % 4 == 0) {
                    log(sp, "frame " + f);
                }
            }
            sp.getServer().runCommand("gamemode creative @a");
        }
    }

    private static void log(TestSingleplayerContext sp, String when) {
        sp.getServer().runOnServer(server -> {
            var level = server.overworld();
            var p = server.getPlayerList().getPlayers().get(0);
            int real = 0, close = 0;
            for (Zombie z : level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Zombie.class), z -> z.isAlive() && ZombieTypes.isWalker(z))) {
                real++;
                if (z.distanceTo(p) < 24) {
                    close++;
                }
            }
            LOG.info("ZCSWARM {}: real {} within 24 {} mspt {} | {}", when, real, close,
                    String.format("%.1f", server.getAverageTickTimeNanos() / 1e6), Population.stats());
        });
    }
}
