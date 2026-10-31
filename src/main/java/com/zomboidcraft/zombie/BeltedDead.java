package com.zomboidcraft.zombie;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Grabs;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The dead still belted in (an addition, not Zomboid; Josiah said yes on 31 Oct): about one abandoned or wrecked car
 * in twenty (zombieLore.beltedPercent) has its driver still in the seat. It thrashes and bangs on the glass when it
 * sees you, which carries, and gets out only when you open its door or break the glass: then it comes out at you,
 * and may get hold of you. Each car is rolled once, the first time a player comes near it.
 */
public final class BeltedDead {
    /** On a zombie: it's belted into the car it rides. Saved, so it stays in its seat. */
    public static final AttachmentType<Boolean> BELTED = AttachmentRegistry.create(ZomboidCraft.id("belted"),
            builder -> builder.persistent(Codec.BOOL));
    /** On a car: whether it's been rolled for a dead driver yet. */
    public static final AttachmentType<Boolean> ROLLED = AttachmentRegistry.create(ZomboidCraft.id("belt_rolled"),
            builder -> builder.persistent(Codec.BOOL));

    private static final double ROLL_REACH = 64;
    private static final double SEES = 10;
    private static final Set<Zombie> BELTED_NOW = Collections.newSetFromMap(new WeakHashMap<>());
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(BeltedDead::tick);
    }

    public static boolean belted(Entity entity) {
        return entity instanceof Zombie && entity.getAttachedOrElse(BELTED, false);
    }

    /** On load: a belted zombie still sitting in its car goes back on the watch list; one that isn't is free. */
    public static void onLoad(Zombie zombie) {
        if (!belted(zombie)) {
            return;
        }
        BELTED_NOW.add(zombie);
    }

    /** A player opening a car's door: if the dead driver's there, it comes out at them instead. */
    public static boolean doorOpened(CarProp car, Player player) {
        for (Entity rider : car.getPassengers()) {
            if (belted(rider) && rider instanceof Zombie zombie && zombie.isAlive()) {
                unbelt(car, zombie, player);
                return true;
            }
        }
        return false;
    }

    /** Puts a dead driver into a car's seat (the driver's, else the first one left), dressed for the place. */
    public static Zombie belt(ServerLevel level, CarProp car) {
        float[][] seats = car.seatSpots();
        int seat = -1;
        for (int s = 0; s < seats.length && seat < 0; s++) {
            if (car.seatFitted(s)) {
                seat = s;
            }
        }
        if (seat < 0) {
            return null;
        }
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (zombie == null) {
            return null;
        }
        zombie.snapTo(car.getX(), car.getY(), car.getZ(), car.getYRot(), 0);
        zombie.setPersistenceRequired();
        zombie.setAttached(BELTED, true);
        String look = ZombieSpawner.uniform(level, car.blockPosition(), zombie.getRandom());
        if (look != null) {
            zombie.setAttached(ZombieLook.OUTFIT, look);
        }
        level.addFreshEntity(zombie);
        car.seatNext(seat);
        if (!zombie.startRiding(car)) {
            zombie.discard();
            return null;
        }
        BELTED_NOW.add(zombie);
        return zombie;
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        if (ticks % 40 == 0) {
            rollCars(server.overworld());
        }
        if (ticks % 10 != 0) {
            return;
        }
        for (Zombie zombie : List.copyOf(BELTED_NOW)) {
            if (zombie.isRemoved() || !zombie.isAlive() || !belted(zombie)) {
                BELTED_NOW.remove(zombie);
                continue;
            }
            if (!(zombie.getVehicle() instanceof CarProp car)) {
                zombie.removeAttached(BELTED);   // its car's gone from under it
                BELTED_NOW.remove(zombie);
                continue;
            }
            watch((ServerLevel) zombie.level(), car, zombie);
        }
    }

    /** Thrashing at anyone it sees; out through the glass once it's broken and they're close. */
    private static void watch(ServerLevel level, CarProp car, Zombie zombie) {
        ServerPlayer seen = null;
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceTo(zombie) < SEES
                    && (seen == null || p.distanceTo(zombie) < seen.distanceTo(zombie))) {
                seen = p;
            }
        }
        if (seen == null) {
            return;
        }
        zombie.getLookControl().setLookAt(seen, 40f, 40f);
        zombie.setYHeadRot((float) Math.toDegrees(Math.atan2(-(seen.getX() - zombie.getX()), seen.getZ() - zombie.getZ())));
        if (car.smashed() && seen.distanceTo(zombie) < 3.5) {
            unbelt(car, zombie, seen);
            return;
        }
        if (zombie.getRandom().nextInt(3) != 0) {
            return;
        }
        // straining at the belt, hands on the glass
        zombie.swing(zombie.getRandom().nextBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
        level.playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundSource.HOSTILE, 0.35f, 1.7f + zombie.getRandom().nextFloat() * 0.2f);
        if (zombie.getRandom().nextInt(3) == 0) {
            level.playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), SoundEvents.ZOMBIE_AMBIENT,
                    SoundSource.HOSTILE, 0.9f, 0.8f);
            Noise.emit(level, zombie.position(), 12);
        }
    }

    /** Out of the seat and out of the door on its side, at whoever let it out. */
    private static void unbelt(CarProp car, Zombie zombie, Player at) {
        int seat = car.seatOf(zombie);
        zombie.removeAttached(BELTED);
        BELTED_NOW.remove(zombie);
        zombie.stopRiding();
        CarModels.Model m = CarModels.get(car.model());
        float[][] seats = car.seatSpots();
        double side = seats.length > 0 && seats[Math.max(0, seat)][0] < 0 ? -1 : 1;
        double half = m == null ? 1 : m.size[0] / 2;
        double lz = seats.length > 0 ? seats[Math.max(0, seat)][2] : 0;
        Vec3 out = new Vec3(side * (half + 0.6), 0, lz).yRot((float) -Math.toRadians(car.getYRot()));
        Vec3 spot = car.position().add(out);
        BlockPos ground = BlockPos.containing(spot);
        zombie.snapTo(spot.x, ground.getY(), spot.z, zombie.getYRot(), 0);
        zombie.setTarget(at);
        if (at instanceof ServerPlayer player && player.distanceTo(zombie) < 2.5 && zombie.getRandom().nextFloat() < 0.5f) {
            Grabs.seize(player, zombie, false);
        }
        zombie.level().playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), SoundEvents.ZOMBIE_AMBIENT,
                SoundSource.HOSTILE, 1.3f, 0.7f);
    }

    /** Rolls each car near a player once: about one abandoned or wrecked car in twenty gets a dead driver. */
    private static void rollCars(ServerLevel level) {
        double pct = ZcConfig.get().zombieLore.beltedPercent;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            for (CarProp car : level.getEntitiesOfClass(CarProp.class, new AABB(p.blockPosition()).inflate(ROLL_REACH))) {
                if (car.getAttachedOrElse(ROLLED, false)) {
                    continue;
                }
                car.setAttached(ROLLED, true);
                String v = car.variant();
                CarModels.Model m = CarModels.get(car.model());
                if (pct <= 0 || !("abandoned".equals(v) || "wrecked".equals(v)) || car.aircraft() || car.survivor()
                        || m == null || m.boat() || m.wheels == null || m.wheels.length == 0 || !car.getPassengers().isEmpty()) {
                    continue;
                }
                if (new java.util.Random(car.getUUID().getMostSignificantBits() ^ 0x5EA7B317L).nextDouble() * 100 < pct) {
                    belt(level, car);
                }
            }
        }
    }

    private BeltedDead() {
    }
}
