package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Close quarters. A zombie that reaches you can grab hold: you're nearly pinned and it keeps
 * biting until you break free by mashing jump or shoving. Zombies a few steps away sometimes
 * lunge to close the gap. Both make letting them get near the real danger, as in Zomboid.
 */
public final class Grabs {
    /** Struggles left to break free, or absent when free. Synced to the player's client for the prompt. */
    public static final AttachmentType<Integer> GRABBED = AttachmentRegistry.create(
            ZomboidCraft.id("grabbed"),
            builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly()));

    private static final Identifier HELD = ZomboidCraft.id("held");
    private static final int BITE_EVERY = 25;
    private static final int COOLDOWN = 60;

    private static final class Hold {
        final Zombie by;
        int taps;
        int ticks;

        Hold(Zombie by, int taps) {
            this.by = by;
            this.taps = taps;
        }
    }

    private static final Map<ServerPlayer, Hold> HOLDS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> FREED_AT = new WeakHashMap<>();
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Grabs::tick);
    }

    public static boolean isGrabbed(ServerPlayer player) {
        return HOLDS.containsKey(player);
    }

    /** One jump press while held. Enough of them and you pull free. */
    public static void struggle(ServerPlayer player) {
        Hold hold = HOLDS.get(player);
        if (hold == null) {
            return;
        }
        Stamina.use(player, 1.0);
        if (--hold.taps <= 0) {
            breakFree(player);
        } else {
            player.setAttached(GRABBED, hold.taps);
        }
    }

    /** Pulls free and shoves the zombie back a step. */
    public static void breakFree(ServerPlayer player) {
        Hold hold = HOLDS.get(player);
        if (hold == null) {
            return;
        }
        Vec3 away = hold.by.position().subtract(player.position()).multiply(1, 0, 1);
        if (away.lengthSqr() > 1e-6) {
            Shove.push(hold.by, away.normalize(), 0.5);
        }
        Downed.stagger(hold.by, 20);
        player.level().playSound(null, hold.by.getX(), hold.by.getEyeY(), hold.by.getZ(), ModSounds.SHOVE,
                SoundSource.PLAYERS, 0.9f, 0.85f);
        release(player);
    }

    private static void grab(ServerPlayer player, Zombie zombie) {
        Hold hold = new Hold(zombie, Math.max(1, ZcConfig.get().combat.struggleTaps));
        HOLDS.put(player, hold);
        player.setAttached(GRABBED, hold.taps);
        player.stopUsingItem();
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(HELD, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        zombie.getNavigation().stop();
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.GRAB, SoundSource.HOSTILE,
                1f, 0.9f + zombie.getRandom().nextFloat() * 0.2f);
        player.sendOverlayMessage(Component.literal("A zombie has hold of you! Mash jump or shove (V) to break free.")
                .withStyle(ChatFormatting.DARK_RED));
    }

    private static void release(ServerPlayer player) {
        HOLDS.remove(player);
        player.removeAttached(GRABBED);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(HELD);
        }
        FREED_AT.put(player, player.level().getGameTime());
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        ZcConfig.Combat config = ZcConfig.get().combat;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Hold hold = HOLDS.get(player);
            if (hold != null) {
                holdTick(player, hold);
            } else if (ticks % 20 == Math.floorMod(player.getId(), 20) && player.isAlive()
                    && !player.isCreative() && !player.isSpectator()) {
                closeIn(player, config);
            }
        }
    }

    private static void holdTick(ServerPlayer player, Hold hold) {
        Zombie by = hold.by;
        if (!ZcConfig.get().combat.grabs || !player.isAlive() || player.isCreative() || player.isSpectator()
                || !by.isAlive() || by.isRemoved() || Downed.isLow(by) || !Dismember.hasHands(by)
                || by.distanceToSqr(player) > 2.5 * 2.5) {
            release(player);
            return;
        }
        by.getNavigation().stop();
        by.getLookControl().setLookAt(player, 30f, 30f);
        if (++hold.ticks % BITE_EVERY == 0) {
            by.doHurtTarget((ServerLevel) player.level(), player);
        }
    }

    /** Once a second per player: a zombie in reach may grab, one a few steps off may lunge. */
    private static void closeIn(ServerPlayer player, ZcConfig.Combat config) {
        if (com.zomboidcraft.world.cars.CarProp.shielded(player)) {
            return;     // no grabbing through a car's glass
        }
        ServerLevel level = (ServerLevel) player.level();
        Long freed = FREED_AT.get(player);
        boolean canGrab = config.grabs && (freed == null || level.getGameTime() - freed > COOLDOWN);
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(5))) {
            if (!ZombieTypes.isWalker(zombie) || !zombie.isAlive() || zombie.getTarget() != player
                    || Downed.isLow(zombie) || Downed.isHeld(zombie)) {
                continue;
            }
            double dist = zombie.distanceTo(player);
            if (canGrab && dist < 1.4 && Dismember.hasHands(zombie) && zombie.getRandom().nextFloat() < config.grabChance) {
                grab(player, zombie);
                return;
            }
            if (config.lunges && dist > 2.2 && dist < 4.5 && zombie.onGround() && zombie.hasLineOfSight(player)
                    && zombie.getRandom().nextFloat() < config.lungeChance) {
                Vec3 to = player.position().subtract(zombie.position()).multiply(1, 0, 1).normalize();
                zombie.push(to.x * 0.55, 0.22, to.z * 0.55);
                level.playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), SoundEvents.ZOMBIE_AMBIENT,
                        SoundSource.HOSTILE, 1.2f, 0.75f);
            }
        }
    }

    private Grabs() {
    }
}
