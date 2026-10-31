package com.zomboidcraft.client.zombie;

import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import com.zomboidcraft.zombie.BeltedDead;
import com.zomboidcraft.zombie.LyingInWait;
import com.zomboidcraft.zombie.PlaceOutfits;
import com.zomboidcraft.zombie.ZombieLook;
import com.zomboidcraft.zombie.ZombieSpawner;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames for the wave 3 preview ("the dead where they belong"): a hospital ward and a police station's dead dressed
 * for where they died, a street of bodies some of which are only playing dead, a crawler under a parked car that
 * catches an ankle, and a dead driver belted into an abandoned car who comes out when the door's opened. CI pushes
 * the numbered screenshots to the shots/w8d2gc branch. Run by .github/workflows/zombieshots.yml.
 */
public class ZombieDeadTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCDEAD");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false", "zc config nature.climateCycle no_weather",
                    "zc config nature.fogCycle no_fog", "difficulty normal"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            stage(ctx, sp);
            room(ctx, sp, "hospital", 0, "minecraft:white_concrete", "zomboidcraft:hs_tile", "zomboidcraft:hs_wall");
            room(ctx, sp, "police", 48, "minecraft:gray_concrete", "zomboidcraft:sh_floor_vct", "zomboidcraft:sh_booking");
            street(ctx, sp);
            cars(ctx, sp);
        }
    }

    /** Flat stone 160 blocks across, cleared above. */
    private static void stage(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("tp @a 0 140 0");
        ctx.waitTicks(40);
        sp.getServer().runCommand("fill -40 99 -40 120 99 120 minecraft:stone");
        for (int y = 100; y < 112; y++) {
            sp.getServer().runCommand("fill -40 " + y + " -40 120 " + y + " 120 minecraft:air");
        }
        sp.getServer().runCommand("kill @e[type=!player]");
        sp.getServer().runCommand("weather clear");
        ctx.waitTicks(200);
        sp.getConnection().waitForChunksRender();
    }

    /**
     * A room built from a building kit's blocks (floor and a wall run), sixteen of the dead put in it and dressed by
     * where they stand, seen from the corner.
     */
    private static void room(ClientGameTestContext ctx, TestSingleplayerContext sp, String name, int x0, String under,
            String floor, String wall) {
        int z0 = 0;
        sp.getServer().runCommand(String.format("fill %d 99 %d %d 99 %d %s", x0, z0, x0 + 15, z0 + 15, under));
        sp.getServer().runCommand(String.format("fill %d 100 %d %d 100 %d %s", x0, z0, x0 + 15, z0 + 15, floor));
        sp.getServer().runCommand(String.format("fill %d 101 %d %d 101 %d %s", x0, z0 + 15, x0 + 15, z0 + 15, wall));
        ctx.waitTicks(5);
        sp.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            PlaceOutfits.clear();
            Map<String, Integer> counts = new TreeMap<>();
            for (int i = 0; i < 16; i++) {
                BlockPos pos = new BlockPos(x0 + 2 + (i % 4) * 3, 101, z0 + 2 + (i / 4) * 3);
                Zombie z = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.COMMAND);
                if (z == null) {
                    continue;
                }
                z.setPersistenceRequired();
                String look = ZombieSpawner.uniform(level, pos, z.getRandom());
                if (look != null) {
                    z.setAttached(ZombieLook.OUTFIT, look);
                }
                counts.merge(ZombieLook.outfit(z), 1, Integer::sum);
            }
            LOG.info("ZCDEAD {} outfits: {}", name, counts);
        });
        sp.getServer().runCommand(String.format("setblock %d 107 %d minecraft:barrier", x0 - 4, z0 - 4));
        sp.getServer().runCommand(String.format("tp @a %d 108 %d -45 35", x0 - 4, z0 - 4));
        ctx.waitTicks(30);
        for (int f = 0; f < 25; f++) {
            ctx.waitTicks(10);
            ctx.takeScreenshot(String.format("%s-%03d", name, f));
        }
        sp.getServer().runCommand(String.format("kill @e[type=zombie,x=%d,y=100,z=%d,dx=16,dy=6,dz=16]", x0, z0));
    }

    /**
     * A street of bodies at z 60: seven real corpses and seven only playing dead. The player walks down it in
     * survival (unhurtable) and the fake ones get up as they come near, grabbing at their legs.
     */
    private static void street(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("fill -4 99 52 40 99 68 minecraft:gray_concrete");
        sp.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            for (int i = 0; i < 14; i++) {
                BlockPos pos = new BlockPos(2 + i * 2 + (i % 2), 100, 57 + (i * 7 % 6));
                Zombie z = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.COMMAND);
                if (z == null) {
                    continue;
                }
                z.setPersistenceRequired();
                if (i % 2 == 0) {
                    z.kill(level);   // a real body
                } else {
                    LyingInWait.lie(z, LyingInWait.FAKE_DEAD);
                }
            }
        });
        ctx.waitTicks(40);
        sp.getServer().runCommand("gamemode survival @a");
        sp.getServer().runCommand("effect give @a resistance infinite 10 true");
        double x = -6;
        for (int f = 0; f < 90; f++) {
            sp.getServer().runCommand(String.format("tp @a %.2f 100 60 -90 20", x));
            x += 0.45;
            ctx.waitTicks(6);
            ctx.takeScreenshot(String.format("street-%03d", f));
            if (f % 10 == 0) {
                logStates(sp, "street " + f, new AABB(-4, 95, 50, 44, 110, 70));
            }
        }
        sp.getServer().runCommand("gamemode creative @a");
        sp.getServer().runCommand("kill @e[type=zombie]");
    }

    /**
     * Two abandoned cars at z 90: one with a crawler tucked under it that catches the player's ankle as they walk by,
     * one with its dead driver belted in, thrashing at the glass until the door's opened.
     */
    private static void cars(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("fill -4 99 82 40 99 98 minecraft:gray_concrete");
        sp.getServer().runOnServer(server -> {
            ServerLevel level = server.overworld();
            CarProp under = Cars.spawn(level, 8, 100, 92, 90, "hatchback", "abandoned", true);
            Zombie crawler = EntityTypes.ZOMBIE.spawn(level, new BlockPos(8, 100, 89), EntitySpawnReason.COMMAND);
            if (under != null && crawler != null) {
                under.setAttached(BeltedDead.ROLLED, true);
                crawler.setPersistenceRequired();
                Downed.makeLegless(crawler);
                LyingInWait.tuckUnder(crawler, under);
            }
            CarProp belted = Cars.spawn(level, 24, 100, 92, 90, "hatchback", "abandoned", true);
            if (belted != null) {
                belted.setAttached(BeltedDead.ROLLED, true);
                BeltedDead.belt(level, belted);
            }
        });
        ctx.waitTicks(40);
        sp.getServer().runCommand("gamemode survival @a");
        // walk past the first car's side, close enough to be caught
        double x = 2;
        for (int f = 0; f < 40; f++) {
            sp.getServer().runCommand(String.format("tp @a %.2f 100 89.9 0 50", x));
            x += 0.3;
            ctx.waitTicks(6);
            ctx.takeScreenshot(String.format("under-%03d", f));
            if (f % 10 == 0) {
                logStates(sp, "under " + f, new AABB(-4, 95, 80, 44, 110, 100));
            }
        }
        // the second car: stand at its door and watch the driver, then open the door
        sp.getServer().runCommand("tp @a 24 100 89.4 0 15");
        for (int f = 0; f < 30; f++) {
            ctx.waitTicks(8);
            ctx.takeScreenshot(String.format("belted-%03d", f));
            if (f == 18) {
                sp.getServer().runOnServer(server -> {
                    var player = server.getPlayerList().getPlayers().get(0);
                    for (CarProp car : server.overworld().getEntitiesOfClass(CarProp.class, new AABB(20, 95, 88, 28, 110, 96))) {
                        car.getIn(player, 0);
                    }
                });
            }
            if (f % 6 == 0) {
                logStates(sp, "belted " + f, new AABB(-4, 95, 80, 44, 110, 100));
            }
        }
        sp.getServer().runCommand("gamemode creative @a");
    }

    private static void logStates(TestSingleplayerContext sp, String when, AABB box) {
        sp.getServer().runOnServer(server -> {
            StringBuilder b = new StringBuilder();
            for (Zombie z : server.overworld().getEntitiesOfClass(Zombie.class, box)) {
                b.append(String.format(" [%.1f,%.1f wait %d belted %s riding %s target %s]", z.getX(), z.getZ(), LyingInWait.how(z),
                        BeltedDead.belted(z), z.isPassenger(), z.getTarget() != null));
            }
            var p = server.getPlayerList().getPlayers().get(0);
            LOG.info("ZCDEAD {}: grabbed {}{}", when, com.zomboidcraft.combat.Grabs.isGrabbed(p), b);
        });
    }
}
