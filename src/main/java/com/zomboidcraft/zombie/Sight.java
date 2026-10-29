package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.weather.WeatherDirector;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid-style zombie eyesight: a forward vision cone whose range shrinks in the dark
 * and against crouching, sneaky players. Anything very close is noticed regardless.
 */
public final class Sight {
    public static boolean canSee(Mob zombie, Player player) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative()
                || com.zomboidcraft.Cinematic.ghost(player)) {
            return false;
        }
        ZcConfig.Senses config = ZcConfig.get().senses;
        double distance = zombie.distanceTo(player);
        if (distance <= config.closeRange) {
            return zombie.getSensing().hasLineOfSight(player);
        }
        if (distance > range(zombie, player, config) || !inCone(zombie, player, config.sightConeDegrees)) {
            return false;
        }
        return zombie.getSensing().hasLineOfSight(player);
    }

    /** How far this zombie could see this player right now. */
    public static double range(Mob zombie, Player player, ZcConfig.Senses config) {
        return range(player, config);
    }

    /** How far any zombie could see this player right now: what the HUD's sight meter shows. */
    public static double range(Player player, ZcConfig.Senses config) {
        double light = player.level().getMaxLocalRawBrightness(player.blockPosition()) / 15.0;
        double range = config.sightRange * (config.darkSight + (1 - config.darkSight) * light)
                * WeatherDirector.sight(player.level());
        if (player.isCrouching()) {
            range *= config.crouchSight;
        }
        return range * (1 - 0.03 * Skills.level(player, Skill.SNEAKING));
    }

    private static boolean inCone(Mob zombie, Player player, double coneDegrees) {
        Vec3 look = zombie.getViewVector(1f);
        Vec3 toPlayer = player.getEyePosition().subtract(zombie.getEyePosition());
        Vec3 flatLook = new Vec3(look.x, 0, look.z);
        Vec3 flatTo = new Vec3(toPlayer.x, 0, toPlayer.z);
        if (flatLook.lengthSqr() < 1e-6 || flatTo.lengthSqr() < 1e-6) {
            return true;
        }
        double cos = flatLook.normalize().dot(flatTo.normalize());
        return cos >= Math.cos(Math.toRadians(coneDegrees / 2));
    }

    private Sight() {
    }
}
