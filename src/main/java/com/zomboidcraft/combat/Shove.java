package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * The shove (V): push the zombies in front of you back a step and make them reel, maybe off
 * their feet, more often from behind. It's how you make room, and it breaks a grab.
 */
public final class Shove {
    private static final int COOLDOWN = 12;
    private static final double CONE = Math.cos(Math.toRadians(55));
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();

    public static void shove(ServerPlayer player) {
        ZcConfig.Combat config = ZcConfig.get().combat;
        if (!config.shove || player.isSpectator() || !player.isAlive()) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST.get(player);
        if (last != null && now - last < COOLDOWN) {
            return;
        }
        LAST.put(player, now);
        if (Stamina.get(player) < 2 && !player.isCreative()) {
            player.sendOverlayMessage(Component.literal("Too out of breath to shove.").withStyle(ChatFormatting.GRAY));
            return;
        }
        float vigour = Stamina.vigour(player);
        Stamina.use(player, config.shoveCost);
        player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : look.normalize();

        if (Grabs.isGrabbed(player)) {
            Grabs.breakFree(player);
            return;
        }
        // Zomboid: over a zombie on the ground the shove key stomps it, whatever's in your hands
        if (config.groundAttacks && stomp(player, level, look, vigour)) {
            return;
        }
        boolean any = false;
        double range = config.shoveRange;
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(range))) {
            if (!ZombieTypes.isWalker(zombie) || !zombie.isAlive()) {
                continue;
            }
            Vec3 to = zombie.position().subtract(player.position()).multiply(1, 0, 1);
            double dist = to.length();
            if (dist > range + zombie.getBbWidth() / 2 || dist > 1e-3 && to.scale(1 / dist).dot(look) < CONE) {
                continue;
            }
            any = true;
            if (Downed.isLow(zombie)) {
                // a zombie on the ground just gets kicked back
                push(zombie, look, 0.35);
                Downed.keepDown(zombie, 10);
                continue;
            }
            // from behind: the zombie faces the way it's being pushed
            Vec3 facing = Vec3.directionFromRotation(0, zombie.getYRot());
            boolean behind = facing.dot(look) > 0.4;
            push(zombie, look, 0.75 * vigour);
            double chance = config.shoveKnockdownChance * (behind ? 2 : 1) * vigour;
            if (zombie.getRandom().nextFloat() < chance) {
                Downed.knockDown(zombie);
            } else {
                Downed.stagger(zombie, 16 + zombie.getRandom().nextInt(10));
            }
            level.playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), ModSounds.SHOVE, SoundSource.PLAYERS,
                    0.9f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
        if (!any) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SWING, SoundSource.PLAYERS,
                    0.5f, 1.2f);
        }
    }

    /** Footwear's weight behind a stomp: bare feet weakest, heavy boots strongest (Zomboid B42). */
    private static final java.util.Map<String, Double> FOOTWEAR = java.util.Map.of(
            "slippers", 0.8, "sneakers", 1.0, "running_shoes", 1.0, "dress_shoes", 1.05, "rubber_boots", 1.15,
            "cowboy_boots", 1.25, "hiking_boots", 1.3, "work_boots", 1.45, "combat_boots", 1.5);

    public static double footwear(Player player) {
        com.zomboidcraft.clothing.Garment g = com.zomboidcraft.clothing.Clothes.of(
                com.zomboidcraft.clothing.ClothingSlot.FEET.worn(player));
        return g == null ? 0.7 : FOOTWEAR.getOrDefault(g.id(), 1.0);
    }

    /** True while a stomp's blow lands, so it counts as a stomp and not a blow with what's in hand (CombatEvents). */
    static boolean stomping;

    /** Stomps the nearest zombie lying at your feet, if there is one. Quiet, and it costs its own breath. */
    private static boolean stomp(ServerPlayer player, ServerLevel level, Vec3 look, float vigour) {
        ZcConfig.Combat config = ZcConfig.get().combat;
        Zombie target = null;
        double best = config.stompRange;
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(config.stompRange))) {
            if (!ZombieTypes.isWalker(zombie) || !zombie.isAlive() || !Downed.isLow(zombie)) {
                continue;
            }
            Vec3 to = zombie.position().subtract(player.position()).multiply(1, 0, 1);
            double dist = to.length();
            if (dist < best && (dist < 0.6 || to.scale(1 / dist).dot(look) > 0.2)) {
                best = dist;
                target = zombie;
            }
        }
        if (target == null) {
            return false;
        }
        Stamina.use(player, config.stompCost);
        float damage = (float) (config.stompDamage * footwear(player) * (0.6 + 0.4 * vigour));
        stomping = true;
        try {
            target.hurtServer(level, player.damageSources().playerAttack(player), damage);
        } finally {
            stomping = false;
        }
        Downed.keepDown(target, 10);
        Noise.emit(level, target.position(), config.stompNoise);
        return true;
    }

    /** Pushes an entity along a direction, less if it resists knockback. */
    static void push(LivingEntity entity, Vec3 dir, double strength) {
        double s = strength * (1 - entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        if (s <= 0) {
            return;
        }
        // push() flags the change so it reaches clients at once
        Vec3 v = entity.getDeltaMovement();
        entity.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
        entity.push(dir.x * s, entity.onGround() ? 0.2 * s : 0, dir.z * s);
    }

    private Shove() {
    }
}
