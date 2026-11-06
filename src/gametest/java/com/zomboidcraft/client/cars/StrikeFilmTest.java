package com.zomboidcraft.client.cars;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.events.StrikeCalls;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames for the called air strikes preview (Josiah 2026-11-06, "Calling in air strikes"): a street of blocks with a
 * crowd of the dead in it, a radio operator up on a roof down the street. First he holds his laser on the crowd and
 * calls in bombs, coming in from behind him down the street; then a red smoke signal goes off in the street and he
 * calls a gun run on the smoke. Every moment the strike reports (checked in, in hot, released, the result) goes to
 * the log as a ZCSTRIKE line for the captions, and the frames to the shots/dcbfp2-strikes branch. Run by
 * .github/workflows/strikefilm.yml.
 */
public class StrikeFilmTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCSTRIKE");
    /** The street's ground level, high over whatever the world has here. */
    private static final int Y = 160;
    private static int frame;
    private static int eta;

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false"}) {
                sp.getServer().runCommand(c);
            }
            ZcConfig.get().world.worldEvents = false;
            // a clear day, so the strike shows from down the street
            ZcConfig.get().weather.enabled = false;
            ZcConfig.get().weather.fog = false;
            ZcConfig.get().weather.endlessNight = false;
            ZcConfig.get().zombieLore.fakeDead = ZcConfig.ZombieLore.FakeDead.OFF;
            ctx.getInput().resizeWindow(1280, 720);
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            StrikeCalls.listen(u -> LOG.info("ZCSTRIKE moment {} {} {} at {} {} {} kills {} hurt {} why {}", u.id(), u.type(), u.moment(),
                    (int) u.mark().x, (int) u.mark().y, (int) u.mark().z, u.kills(), u.hurt(), u.why()));
            sp.getServer().runCommand("tp @a 0 " + (Y + 20) + " -60");
            ctx.waitTicks(40);
            sp.getServer().runOnServer(server -> street(server.overworld()));
            ctx.waitTicks(40);
            sp.getServer().runCommand("clear @a");
            sp.getServer().runCommand("give @a zomboidcraft:laser_rangefinder");
            sp.getServer().runCommand("give @a zomboidcraft:field_radio");
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));

            // 1. the laser: from the roof, held on the crowd; bombs in from behind him, down the street
            crowd(sp);
            stand(sp, 8.6, Y + 12, -72.6, new Vec3(0.5, Y, 0.5));
            ctx.waitTicks(30);
            sp.getConnection().waitForChunksRender();
            ctx.getInput().holdKey(options -> options.keyUse);
            frames(ctx, 15, 2);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                // first without confirming: he's close enough to the stick to be asked
                StrikeCalls.CallResult first = StrikeCalls.call(p.level(), p, StrikeCalls.StrikeType.BOMBS, StrikeCalls.laser(p),
                        new Vec3(0, 0, 1), 0, false);
                LOG.info("ZCSTRIKE call bombs on the laser: {} {} eta {} danger close {}", first.accepted(), first.reason(),
                        first.etaTicks(), first.dangerClose());
                StrikeCalls.CallResult r = first.accepted() ? first : StrikeCalls.call(p.level(), p, StrikeCalls.StrikeType.BOMBS,
                        StrikeCalls.laser(p), new Vec3(0, 0, 1), 0, true);
                LOG.info("ZCSTRIKE call bombs confirmed: {} {} eta {}", r.accepted(), r.reason(), r.etaTicks());
                eta = r.etaTicks();
            });
            ctx.waitTicks(Math.max(0, eta - 150));
            frames(ctx, 140, 2);
            ctx.getInput().releaseKey(options -> options.keyUse);
            ctx.waitTicks(120);
            count(sp, "after the bombs");

            // 2. smoke: a red signal in the street, a gun run on it
            sp.getServer().runCommand("kill @e[type=zomboidcraft:corpse]");
            crowd(sp);
            sp.getServer().runOnServer(server -> StrikeCalls.smoke(server.overworld(), new Vec3(0.5, Y, 0.5), "red", 2400));
            stand(sp, 8.6, Y + 12, -72.6, new Vec3(0.5, Y + 2, 0.5));
            ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
            ctx.waitTicks(40);
            frames(ctx, 20, 3);
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                StrikeCalls.addBonus(StrikeCalls.BASE.apply(p));
                StrikeCalls.Mark mark = StrikeCalls.smoke(p.level(), p, "red", Vec3.ZERO);
                StrikeCalls.CallResult r = StrikeCalls.call(p.level(), p, StrikeCalls.StrikeType.GUN_RUN, mark, new Vec3(0, 0, 1), 0, true);
                LOG.info("ZCSTRIKE call gun run on the red smoke: {} {} eta {} danger close {}", r.accepted(), r.reason(), r.etaTicks(),
                        r.dangerClose());
                eta = r.etaTicks();
                StrikeCalls.Status s = StrikeCalls.status(p.level(), StrikeCalls.BASE.apply(p));
                LOG.info("ZCSTRIKE status ready {} left {} of {} bonus {}", s.ready(), s.ticksLeft(), s.cycleTicks(), s.bonus());
            });
            ctx.waitTicks(Math.max(0, eta - 130));
            frames(ctx, 110, 2);
            ctx.waitTicks(80);
            count(sp, "after the gun run");
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                StrikeCalls.Status s = StrikeCalls.status(p.level(), StrikeCalls.BASE.apply(p));
                LOG.info("ZCSTRIKE status ready {} left {} of {} bonus {} last {}", s.ready(), s.ticksLeft(), s.cycleTicks(), s.bonus(),
                        s.lastResult());
            });
            LOG.info("ZCSTRIKE frames {}", frame);
        }
    }

    /**
     * A street running north-south through the origin: a ten-block road between two rows of blocks of buildings,
     * ten to eighteen high with alleys between them, from 160 blocks south of the mark to 60 north.
     */
    private static void street(ServerLevel level) {
        BlockState road = Blocks.POLISHED_ANDESITE.defaultBlockState(), walk = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState[] walls = {Blocks.BRICKS.defaultBlockState(), Blocks.STONE_BRICKS.defaultBlockState(),
                Blocks.STONE.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState()};
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int z = -170; z <= 70; z++) {
            for (int x = -26; x <= 26; x++) {
                p.set(x, Y - 1, z);
                level.setBlock(p, Math.abs(x) <= 5 ? road : walk, 2);
                int block = Math.floorDiv(z + 170, 17), along = Math.floorMod(z + 170, 17);
                boolean house = Math.abs(x) >= 8 && Math.abs(x) <= 20 && along < 14;
                int height = 10 + Math.floorMod(block * 7 + (x < 0 ? 3 : 0), 9);
                if (x > 0 && z >= -100 && z <= -71) {
                    // the operator's roof, at the street's edge
                    house = x >= 8 && x <= 20;
                    height = 12;
                } else if (x > 0 && z > -71 && z <= 10) {
                    // lower blocks between him and the mark, under his line of sight
                    height = 10;
                }
                BlockState wall = walls[Math.floorMod(block + (x < 0 ? 1 : 0), walls.length)];
                for (int y = Y; y < Y + 30; y++) {
                    p.set(x, y, z);
                    level.setBlock(p, house && y < Y + height ? wall : air, 2);
                }
            }
        }
    }

    /** Two dozen of the dead standing about in the street round the mark. */
    private static void crowd(TestSingleplayerContext sp) {
        sp.getServer().runCommand("kill @e[type=zombie]");
        java.util.Random r = new java.util.Random(7);
        for (int i = 0; i < 24; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * 9;
            double x = 0.5 + Math.cos(a) * Math.min(d, 4.5), z = 0.5 + Math.sin(a) * d;
            sp.getServer().runCommand(String.format(Locale.ROOT, "summon zombie %.2f %d %.2f {NoAI:1b,Rotation:[%df,0f]}", x, Y, z,
                    r.nextInt(360)));
        }
    }

    private static void count(TestSingleplayerContext sp, String when) {
        sp.getServer().runOnServer(server -> {
            int alive = server.overworld().getEntitiesOfClass(Zombie.class, new AABB(-30, Y - 2, -30, 30, Y + 10, 30), Zombie::isAlive).size();
            LOG.info("ZCSTRIKE zombies standing {}: {}", when, alive);
        });
    }

    /** The operator (the camera) standing at x y z, looking at a point. */
    private static void stand(TestSingleplayerContext sp, double x, double y, double z, Vec3 look) {
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            double dx = look.x - x, dy = look.y - (y + p.getEyeHeight()), dz = look.z - z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
            p.teleportTo(server.overworld(), x, y, z, Set.of(), yaw, pitch, false);
        });
    }

    private static void frames(ClientGameTestContext ctx, int count, int every) {
        for (int i = 0; i < count; i++) {
            ctx.waitTicks(every);
            ctx.takeScreenshot(String.format("strike-%04d", frame++));
        }
    }
}
