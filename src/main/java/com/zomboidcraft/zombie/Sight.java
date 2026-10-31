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
        if (distance > range(zombie, player, config) || !inCone(zombie, player, config.sightConeDegrees)
                || com.zomboidcraft.combat.throwing.Smoke.blocks(zombie.level(), zombie.getEyePosition(), player.getEyePosition())) {
            return false;
        }
        return zombie.getSensing().hasLineOfSight(player);
    }

    /**
     * How far this zombie could see this player right now: its own eyes (zombieLore sight, half outside its active
     * hours), and with the new stealth system a lit light in your hands carries half as far again, while crouched
     * behind a car, hedge or low wall you can't be picked out past arm's reach.
     */
    public static double range(Mob zombie, Player player, ZcConfig.Senses config) {
        double range = range(player, config) * Lore.sight(zombie);
        if (ZcConfig.get().zombieLore.stealthSystem) {
            if (lit(player)) {
                range *= LIGHT_GIVES_AWAY;
            } else if (player.isCrouching() && behindCover(zombie, player)) {
                range = Math.min(range, config.closeRange);
            }
        }
        return range;
    }

    /** A lit flashlight, lantern, torch or flare in either hand, or a gun with its light switched on. */
    private static final double LIGHT_GIVES_AWAY = 1.5;

    public static boolean lit(Player player) {
        return lit(player.getMainHandItem()) || lit(player.getOffhandItem());
    }

    private static boolean lit(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (com.zomboidcraft.loot.items.LightItem.lit(stack) || com.zomboidcraft.combat.Attachments.lightOn(stack)) {
            return true;
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return id.equals("torch") || id.equals("lantern") || id.equals("soul_torch") || id.contains("flare");
    }

    /**
     * Something solid between you and it, close to you and no taller than you crouch: a car, a hedge, a low wall,
     * a fence of boards, a counter. Looked for along the line from its eyes to your chest, in the last 3 blocks.
     */
    private static boolean behindCover(Mob zombie, Player player) {
        Vec3 chest = player.position().add(0, 0.6, 0);
        Vec3 eye = zombie.getEyePosition();
        for (com.zomboidcraft.world.cars.CarProp car : player.level().getEntitiesOfClass(com.zomboidcraft.world.cars.CarProp.class,
                player.getBoundingBox().inflate(4), c -> c.getVehicle() == null && !player.isPassengerOfSameVehicle(c))) {
            if (car.getBoundingBox().clip(chest, eye).isPresent()) {
                return true;
            }
        }
        Vec3 dir = eye.subtract(chest);
        double len = dir.length();
        if (len < 2) {
            return false;
        }
        dir = dir.scale(1 / len);
        net.minecraft.world.level.Level level = player.level();
        for (double d = 0.6; d <= Math.min(3, len - 1); d += 0.5) {
            Vec3 at = chest.add(dir.scale(d));
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(at);
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || state.is(net.minecraft.tags.BlockTags.LEAVES)) {
                // low cover: what's over it is open, so standing you'd be seen over it
                net.minecraft.core.BlockPos above = pos.above();
                if (level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
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
