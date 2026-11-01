package com.zomboidcraft.client.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.Guns;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Frames for the calibre-based kills preview (Josiah 2026-11-01): one zombie at a time, shot in the head, the torso or
 * the legs with a 9mm, a .22, a 5.56, a .308 and a shotgun at different ranges, hip and aimed, counting the shots it
 * takes. Every frame's caption (gun, aim point, shots, health) goes to the log as a ZCKILL line, and the frames to the
 * shots/5nbhdi-kills branch. Run by .github/workflows/killfilm.yml.
 */
public class KillFilmTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCKILL");
    private static int frame;

    private enum Aim {
        HEAD(1.72, 0), TORSO(1.2, 0), LEGS(0.5, 0.13);

        final double up, side;

        Aim(double up, double side) {
            this.up = up;
            this.side = side;
        }
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            for (String c : new String[] {"gamemode creative @a", "time set noon", "weather clear", "gamerule doDaylightCycle false",
                    "gamerule doMobSpawning false", "zc config population.enabled false"}) {
                sp.getServer().runCommand(c);
            }
            ctx.getInput().resizeWindow(1280, 720);
            sp.getServer().runCommand("effect give @a night_vision infinite 0 true");
            stage(ctx, sp);
            LOG.info("ZCKILL rules torso x{} limbs x{} onlyHeadshots {}", ZcConfig.get().combat.zombieBodyShots,
                    ZcConfig.get().combat.zombieLimbShots, ZcConfig.get().combat.onlyHeadshotsKill);
            scene(ctx, sp, "pistol", "9mm pistol", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.HEAD, 5, true, 6);
            scene(ctx, sp, "pistol", "9mm pistol", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.TORSO, 5, true, 8);
            scene(ctx, sp, "pistol", "9mm pistol", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.LEGS, 5, true, 12);
            scene(ctx, sp, "pistol", "9mm pistol", ZcConfig.ZombieLore.Toughness.TOUGH, Aim.HEAD, 5, true, 6);
            scene(ctx, sp, "rimfire", ".22 rifle", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.HEAD, 5, true, 6);
            scene(ctx, sp, "assault_rifle", "5.56 rifle", ZcConfig.ZombieLore.Toughness.TOUGH, Aim.HEAD, 6, true, 6);
            scene(ctx, sp, "assault_rifle", "5.56 rifle", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.TORSO, 6, true, 6);
            scene(ctx, sp, "hunting_rifle", ".308 rifle", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.TORSO, 8, true, 4);
            scene(ctx, sp, "shotgun", "12 gauge shotgun", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.TORSO, 3, false, 1);
            scene(ctx, sp, "shotgun", "12 gauge shotgun", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.HEAD, 4, false, 1);
            scene(ctx, sp, "shotgun", "12 gauge shotgun", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.TORSO, 8, false, 1);
            scene(ctx, sp, "shotgun", "12 gauge shotgun", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.TORSO, 8, true, 1);
            scene(ctx, sp, "shotgun", "12 gauge shotgun", ZcConfig.ZombieLore.Toughness.NORMAL, Aim.TORSO, 15, true, 1);
        }
    }

    /** A flat stone yard 60 blocks across, cleared above. */
    private static void stage(ClientGameTestContext ctx, TestSingleplayerContext sp) {
        sp.getServer().runCommand("tp @a 0 120 0");
        ctx.waitTicks(20);
        sp.getServer().runCommand("fill -30 99 -30 30 99 30 minecraft:stone");
        for (int y = 100; y < 112; y += 4) {
            sp.getServer().runCommand("fill -30 " + y + " -30 30 " + (y + 3) + " 30 minecraft:air");
        }
        sp.getServer().runCommand("kill @e[type=!player]");
        ctx.waitTicks(40);
        sp.getConnection().waitForChunksRender();
    }

    /**
     * One zombie standing at the far end, the player {@code range} blocks off with a loaded gun, firing at one point
     * on it until it drops or {@code max} shots are spent.
     */
    private static void scene(ClientGameTestContext ctx, TestSingleplayerContext sp, String gun, String label,
                              ZcConfig.ZombieLore.Toughness tough, Aim aim, double range, boolean aiming, int max) {
        sp.getServer().runCommand("kill @e[type=zombie]");
        sp.getServer().runCommand("kill @e[type=zomboidcraft:corpse]");
        sp.getServer().runCommand("kill @e[type=item]");
        sp.getServer().runCommand("clear @a");
        ZcConfig.get().zombieLore.toughness = tough;
        sp.getServer().runCommand("summon zombie 0 100 0 {NoAI:1b,Rotation:[180f,0f]}");
        sp.getServer().runCommand("give @a zomboidcraft:" + gun);
        ctx.waitTicks(20);
        ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        // the player's eye on a line to the aim point
        double eyeY = 100 + 1.62, tx = aim.side, ty = 100 + aim.up, dz = range;
        double yaw = Math.toDegrees(Math.atan2(-(tx - 0), dz));
        double pitch = -Math.toDegrees(Math.atan2(ty - eyeY, Math.hypot(tx, dz)));
        sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a 0 100 %.3f %.2f %.2f", -range, yaw, pitch));
        sp.getServer().runOnServer(server -> {
            ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
            p.getMainHandItem().set(GunState.TYPE, new GunState(true, 30, false));
        });
        ctx.waitTicks(20);
        String tag = label + " | " + aim.name().toLowerCase() + " | " + tough.name().toLowerCase() + " zombie | "
                + (int) range + " blocks | " + (aiming ? "aimed" : "hip");
        float[] hp = health(sp);
        LOG.info("ZCKILL scene {} max hp {}", tag, hp[1]);
        int shots = 0;
        caption(ctx, tag, 0, hp, 8);
        while (shots < max && hp[0] > 0) {
            int n = ++shots;
            sp.getServer().runOnServer(server -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                Vec3 eye = p.getEyePosition();
                Dismember.BodyHit hit = Dismember.shot(p, eye, eye.add(p.getLookAngle().scale(40)),
                        e -> e instanceof Zombie && e.isAlive());
                LOG.info("ZCKILL shot {} ray lands on part {}", n, hit == null ? "none" : hit.part);
                Guns.fire(p, aiming);
            });
            // a step of frames after each shot; the pump or bolt needs its time before the next
            caption(ctx, tag, shots, null, 7);
            hp = health(sp);
            LOG.info("ZCKILL shot {} health {} / {}", shots, hp[0], hp[1]);
            caption(ctx, tag, shots, hp, 4);
        }
        LOG.info("ZCKILL result {} -> {} after {} shots", tag, hp[0] > 0 ? "still up at " + hp[0] : "down", shots);
        caption(ctx, tag + (hp[0] > 0 ? " | still standing" : " | killed in " + shots), shots, hp, 14);
    }

    /** The zombie's health and its full health; 0 once it's gone. */
    private static float[] health(TestSingleplayerContext sp) {
        float[] out = {0, 0};
        sp.getServer().runOnServer(server -> {
            for (Zombie z : server.overworld().getEntitiesOfClass(Zombie.class, new AABB(-3, 98, -3, 3, 104, 3), LivingEntity::isAlive)) {
                out[0] = z.getHealth();
                out[1] = z.getMaxHealth();
            }
        });
        return out;
    }

    /** {@code frames} screenshots two ticks apart, each with its caption in the log. */
    private static void caption(ClientGameTestContext ctx, String tag, int shots, float[] hp, int frames) {
        for (int i = 0; i < frames; i++) {
            ctx.waitTicks(2);
            String name = String.format("kill-%04d", frame++);
            ctx.takeScreenshot(name);
            LOG.info("ZCKILL cap {} {} | shots {}{}", name, tag, shots,
                    hp == null ? "" : String.format(java.util.Locale.ROOT, " | health %.1f / %.0f", hp[0], hp[1]));
        }
    }
}
