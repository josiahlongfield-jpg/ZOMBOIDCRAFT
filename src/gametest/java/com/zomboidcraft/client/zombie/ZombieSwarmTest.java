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
            for (String c : new String[] {"gamemode creative @a", "time set 18000", "weather clear", "gamerule doDaylightCycle false",
                    "zc config nature.climateCycle no_weather", "zc config nature.fogCycle no_fog", "zc config searchHeli.frequency never",
                    "zc config survival.characterCreation false", "zc config start.picker false",
                    "difficulty normal"}) {
                sp.getServer().runCommand(c);
            }
            // the roster and spawn picker open on a county world's first join: out of the way for the camera
            ctx.waitTicks(20);
            sp.getServer().runOnServer(server -> {
                for (var p : server.getPlayerList().getPlayers()) {
                    p.setAttached(com.zomboidcraft.spawn.SpawnPicker.SPAWNED, true);
                    p.removeAttached(com.zomboidcraft.spawn.SpawnPicker.HELD);
                }
            });
            ctx.runOnClient(ZombieSwarmTest::skipRoster);
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
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            sp.getServer().runCommand("effect give @a resistance infinite 10 true");
            sp.getServer().runCommand("effect give @a saturation infinite 10 true");
            // a perch in the open street, just out of reach: an open stretch near the middle, looking along it
            int[] street = sp.getServer().computeOnServer(server -> {
                var level = server.overworld();
                var hm = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
                for (int r = 4; r <= 120; r += 2) {
                    for (int k = 0; k < 32; k++) {
                        double t = k * Math.PI / 16;
                        int x = at[0] + (int) Math.round(Math.cos(t) * r), z = at[2] + (int) Math.round(Math.sin(t) * r);
                        int ground = level.getHeight(hm, x, z);
                        boolean open = ground <= at[1] + 2;
                        for (int dx = -8; dx <= 8 && open; dx += 2) {
                            for (int dz = -8; dz <= 8 && open; dz += 2) {
                                open = Math.abs(level.getHeight(hm, x + dx, z + dz) - ground) <= 1;
                            }
                        }
                        if (open) {
                            return new int[] {x, ground, z};
                        }
                    }
                }
                return new int[] {at[0], at[1], at[2]};
            });
            int perch = street[1] + 6;
            LOG.info("ZCSWARM street at {} {} {}, perch at {}", street[0], street[1], street[2], perch);
            sp.getServer().runCommand(String.format("setblock %d %d %d minecraft:barrier", street[0], perch - 1, street[2]));
            sp.getServer().runCommand(String.format("tp @a %d %d %d 0 20", street[0], perch, street[2]));
            ctx.waitTicks(40);
            sp.getConnection().waitForChunksRender();
            // let the dead round the street be placed and wake before the camera rolls
            for (int i = 0; i < 12; i++) {
                ctx.waitTicks(20);
                log(sp, "settle " + i);
            }
            sp.getServer().runCommand("gamemode survival @a");
            ctx.waitTicks(20);
            ctx.runOnClient(ZombieSwarmTest::skipRoster);
            for (int f = 0; f < 160; f++) {
                if (f % 12 == 0) {
                    // a rifle shot, as loud as one (Guns' shotNoise)
                    sp.getServer().runOnServer(server -> {
                        var p = server.getPlayerList().getPlayers().get(0);
                        Noise.emit(server.overworld(), p.position(), 90);
                    });
                }
                sp.getServer().runCommand(String.format("tp @a %d %d %d %.1f %d", street[0], perch, street[2], (f * 2.25f) % 360, f < 80 ? 25 : 45));
                ctx.waitTicks(10);
                ctx.takeScreenshot(String.format("swarm-%03d", f));
                if (f % 4 == 0) {
                    log(sp, "frame " + f);
                }
            }
            sp.getServer().runCommand("gamemode creative @a");
        }
    }

    /** The roster reopens itself until a survivor is made: spend the offer, then close it. */
    private static void skipRoster(net.minecraft.client.Minecraft mc) {
        try {
            var made = com.zomboidcraft.client.kit.CharacterScreen.class.getDeclaredMethod("made");
            made.setAccessible(true);
            made.invoke(null);
            var offered = com.zomboidcraft.client.kit.SpawnScreen.class.getDeclaredField("offered");
            offered.setAccessible(true);
            offered.set(null, null);
        } catch (ReflectiveOperationException e) {
            LOG.warn("ZCSWARM couldn't skip the roster or picker", e);
        }
        mc.gui.setScreen(null);
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
