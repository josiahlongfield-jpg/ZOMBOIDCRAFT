package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.Lore;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieTypes;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames for the Zombie Lore preview video (Zomboid's lore settings, wave 1): weapons left stuck in the dead, a crowd
 * of mixed speeds and senses coming on, and a big enough crowd pushing a chain-link fence down. Each scene is a run of
 * numbered screenshots that CI pushes to the shots/w8d2gc branch, stitched into a clip there.
 * Run by .github/workflows/zombieshots.yml.
 */
public class ZombieLoreTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCLORE");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            // a clean picture: no HUD, and night vision against the endless night
            ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            stage(ctx, sp);
            weapons(ctx, sp);
            crowd(ctx, sp);
            fence(ctx, sp);
        }
    }

    /** A flat yard of stone 60 blocks across round the player, cleared of everything above it. */
    private static void stage(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("tp @a 0 120 0");
        ctx.waitTicks(20);
        sp.getServer().runCommand("fill -30 99 -30 30 99 30 minecraft:stone");
        for (int y = 100; y < 112; y += 4) {
            sp.getServer().runCommand("fill -30 " + y + " -30 30 " + (y + 3) + " 30 minecraft:air");
        }
        sp.getServer().runCommand("kill @e[type=!player]");
        sp.getServer().runCommand("tp @a 0 100 -12 0 15");
        ctx.waitTicks(40);
        sp.getConnection().waitForChunksRender();
    }

    /** Every zombie with something stuck in it, from behind and then from the front, standing still. */
    private static void weapons(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        ZcConfig.get().zombieLore.attachedWeaponPercent = 100;
        for (int i = 0; i < 7; i++) {
            double x = -4.5 + i * 1.5;
            sp.getServer().runCommand("summon zombie " + x + " 100 -8 {NoAI:1b,Rotation:[0f,0f]}");
        }
        ctx.waitTicks(40);
        sp.getServer().runOnServer(server -> {
            for (Zombie z : server.overworld().getEntitiesOfClass(Zombie.class, new net.minecraft.world.phys.AABB(-40, 90, -40, 40, 130, 40))) {
                LOG.info("ZCLORE stuck {}", Lore.describe(z));
            }
        });
        sp.getServer().runCommand("tp @a 0 100 -12.5 0 12");
        ctx.waitTicks(20);
        ctx.takeScreenshot("weapons-back-wide");
        // what the client sees on each
        String seen = ctx.computeOnClient(mc -> {
            StringBuilder b = new StringBuilder();
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof Zombie z) {
                    b.append(String.format(" [%.1f: %s]", z.getX(), com.zomboidcraft.zombie.AttachedWeapons.of(z)));
                }
            }
            return b.toString();
        });
        LOG.info("ZCLORE client stuck{}", seen);
        // each one close: from behind, from its side, and from the front
        for (int i = 0; i < 7; i++) {
            double x = -4.5 + i * 1.5;
            sp.getServer().runCommand("tp @a " + x + " 100.4 -10.4 0 20");
            ctx.waitTicks(10);
            ctx.takeScreenshot("weapons-back-" + i);
            sp.getServer().runCommand("tp @a " + (x - 0.75) + " 100.4 -8.4 -60 20");
            ctx.waitTicks(10);
            ctx.takeScreenshot("weapons-side-" + i);
            sp.getServer().runCommand("tp @a " + x + " 100.4 -5.6 180 20");
            ctx.waitTicks(10);
            ctx.takeScreenshot("weapons-front-" + i);
        }
        sp.getServer().runCommand("kill @e[type=zombie]");
        ctx.waitTicks(5);
        // the bodies and the blades that fell out of them, shown once, then cleared for the next scene
        sp.getServer().runCommand("tp @a 0 100.4 -12.5 0 25");
        ctx.waitTicks(10);
        ctx.takeScreenshot("weapons-fallen");
        sp.getServer().runCommand("kill @e[type=zomboidcraft:corpse]");
        sp.getServer().runCommand("kill @e[type=item]");
        ZcConfig.get().zombieLore.attachedWeaponPercent = 6;
        ctx.waitTicks(10);
    }

    /** Sixteen of the dead come at you from 26 blocks: shamblers, fast shamblers and a sprinter or two, each its own. */
    private static void crowd(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("tp @a 0 100 -14 0 8");
        for (int i = 0; i < 16; i++) {
            double x = -9 + (i % 8) * 2.5, z = 12 + (i / 8) * 2;
            sp.getServer().runCommand("summon zombie " + x + " 100 " + z + " {Rotation:[180f,0f]}");
        }
        ctx.waitTicks(20);
        sp.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            for (Zombie z : server.overworld().getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(40), ZombieTypes::isWalker)) {
                LOG.info("ZCLORE crowd {}", Lore.describe(z));
            }
        });
        // a noise where you stand draws them on, each at its own pace (you're in creative so they don't bite)
        for (int f = 0; f < 72; f++) {
            if (f % 5 == 0) {
                sp.getServer().runOnServer(server -> Noise.emit(server.overworld(), new Vec3(0, 100, -12), 40));
            }
            ctx.waitTicks(4);
            ctx.takeScreenshot(String.format("crowd-%03d", f));
        }
        sp.getServer().runCommand("kill @e[type=zombie]");
        ctx.waitTicks(10);
    }

    /**
     * A chain-link fence across the yard and thirty zombies on the far side drawn to you by noise: under Zomboid's
     * rule (25 to damage a tall fence) they lean on it until a panel goes.
     */
    private static void fence(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        // a closed pen of chain-link, so the only way out is through it
        sp.getServer().runCommand("fill -5 100 2 5 101 2 zomboidcraft:chainlink[axis=x]");
        sp.getServer().runCommand("fill -5 100 11 5 101 11 zomboidcraft:chainlink[axis=x]");
        sp.getServer().runCommand("fill -5 100 2 -5 101 11 zomboidcraft:chainlink");
        sp.getServer().runCommand("fill 5 100 2 5 101 11 zomboidcraft:chainlink");
        sp.getServer().runCommand("tp @a 0 100.5 -7 0 12");
        for (int i = 0; i < 30; i++) {
            double x = -3 + (i % 6) * 1.2, z = 4 + (i / 6) * 1.2;
            sp.getServer().runCommand("summon zombie " + x + " 100 " + z + " {Rotation:[180f,0f]}");
        }
        for (int f = 0; f < 150; f++) {
            if (f % 2 == 0) {
                sp.getServer().runOnServer(server -> Noise.emit(server.overworld(), new Vec3(0, 100, -5), 30));
            }
            ctx.waitTicks(10);
            ctx.takeScreenshot(String.format("fence-%03d", f));
            if (f % 15 == 0) {
                int left = ctx.computeOnClient(mc -> {
                    int n = 0;
                    for (int x = -4; x <= 4; x++) {
                        n += mc.level.getBlockState(new BlockPos(x, 100, 2)).isAir() ? 0 : 1;
                    }
                    return n;
                });
                LOG.info("ZCLORE fence frame {} front panels standing {} of 9", f, left);
            }
        }
        sp.getServer().runCommand("kill @e[type=zombie]");
    }
}
