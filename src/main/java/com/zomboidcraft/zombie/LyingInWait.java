package com.zomboidcraft.zombie;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.combat.Grabs;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The dead lying in wait, still as the real dead until you're close.
 *
 * <p>Fake dead (Zomboid's Fake Dead Zombie Reanimation): about one in six of the dead found lying in the street and
 * one in ten indoors (zombieLore.fakeDead*Percent) are only playing dead, dressed and kitted like any other. One gets
 * up when you come within two to four blocks of it, turn your back on it close by, hurt it, or make a noise it
 * hears, and it grabs at your legs as it rises. A head hit while it lies there kills it like any zombie, so a careful
 * player stomps every body. With World and Combat, some zombies knocked flat by blunt melee stay down and play dead.
 *
 * <p>Under cars (Zomboid's Crawl Under Vehicle): crawlers tuck under parked cars, only their arms out at the sill,
 * and catch the ankle of anyone who walks past within a block. Shoot it, stomp it or kick free; once you're loose it
 * crawls out after you. Idle crawlers near a car go under it; the setting decides how many of the street dead near
 * parked cars are found already lying there, legless (a zombie only crawls with both legs gone).
 */
public final class LyingInWait {
    public static final int FAKE_DEAD = 1, UNDER_CAR = 2;

    /** How it lies in wait (FAKE_DEAD, UNDER_CAR), absent when it isn't. Saved, and synced for the look of it. */
    public static final AttachmentType<Integer> WAITING = AttachmentRegistry.create(ZomboidCraft.id("lying_in_wait"),
            builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static final Set<Zombie> LYING = Collections.newSetFromMap(new WeakHashMap<>());
    /** Under-car crawlers that have someone by the ankle: once that player's loose, it comes out after them. */
    private static final Map<Zombie, ServerPlayer> HOLDING = new WeakHashMap<>();
    /** How close a crawler under a car reaches for a passing ankle (blocks from its hands). */
    private static final double ANKLE_REACH = 1.3;
    /** How far an idle crawler goes to get under a car, and how near a street body must be to one to be found there. */
    private static final double CAR_REACH = 10, FOUND_NEAR_CAR = 8;
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LyingInWait::tick);
    }

    public static int how(Entity entity) {
        return entity.getAttachedOrElse(WAITING, 0);
    }

    public static boolean waiting(Entity entity) {
        return how(entity) != 0;
    }

    /** Lies down to wait: face down and still, as the real dead lie. */
    public static void lie(Zombie zombie, int how) {
        zombie.setAttached(WAITING, how);
        zombie.getNavigation().stop();
        zombie.setTarget(null);
        ((NoiseListener) zombie).zomboidcraft$forget();
        zombie.setPose(Pose.SWIMMING);
        zombie.setDeltaMovement(Vec3.ZERO);
        LYING.add(zombie);
    }

    /** On load: the pose isn't saved, so one still waiting lies back down. */
    public static void onLoad(Zombie zombie) {
        if (waiting(zombie)) {
            zombie.setPose(Pose.SWIMMING);
            LYING.add(zombie);
        }
    }

    // ---- who starts out lying in wait

    /**
     * Whether one of the dead made real at pos from the world's count (not walking anywhere) is only playing dead.
     * Decided by the spot and the world, so the same body plays dead each time it's met.
     */
    public static boolean rollFakeDead(ServerLevel level, BlockPos pos, boolean indoor) {
        ZcConfig.ZombieLore lore = ZcConfig.get().zombieLore;
        if (lore.fakeDead == ZcConfig.ZombieLore.FakeDead.OFF) {
            return false;
        }
        double pct = indoor ? lore.fakeDeadIndoorPercent : lore.fakeDeadStreetPercent;
        return new java.util.Random(pos.asLong() * 31 ^ level.getSeed()).nextDouble() * 100 < pct;
    }

    /**
     * Zomboid's World and Combat: a zombie knocked flat by blunt melee may stay down and play dead instead of getting
     * up. True if it does.
     */
    public static boolean stayDown(Mob mob) {
        ZcConfig.ZombieLore lore = ZcConfig.get().zombieLore;
        if (!(mob instanceof Zombie zombie) || lore.fakeDead != ZcConfig.ZombieLore.FakeDead.WORLD_AND_COMBAT
                || Downed.isCrawler(zombie) || zombie.getRandom().nextDouble() * 100 >= lore.fakeDeadCombatPercent) {
            return false;
        }
        lie(zombie, FAKE_DEAD);
        return true;
    }

    /** Zomboid's Crawl Under Vehicle as the share of parked cars near the street dead with one already under. */
    private static double underCarShare() {
        return switch (ZcConfig.get().zombieLore.crawlUnderVehicle) {
            case CRAWLERS_ONLY -> 0;
            case EXTREMELY_RARE -> 0.02;
            case RARE -> 0.05;
            case SOMETIMES -> 0.1;
            case OFTEN -> 0.2;
            case VERY_OFTEN -> 0.35;
            case EXTREMELY_OFTEN -> 0.5;
        };
    }

    /**
     * One of the street dead just made real near a parked car with nobody under it yet: by the setting's share (rolled
     * once per car), it's found under that car, legless. True if so.
     */
    public static boolean placeUnderCar(ServerLevel level, Zombie zombie) {
        double share = underCarShare();
        if (share <= 0) {
            return false;
        }
        CarProp car = parkedCarNear(level, zombie.position(), FOUND_NEAR_CAR);
        if (car == null || new java.util.Random(car.getUUID().getLeastSignificantBits() ^ 0xC4A3L).nextDouble() >= share) {
            return false;
        }
        Downed.makeLegless(zombie);
        tuckUnder(zombie, car);
        return true;
    }

    /** A parked road car near pos with nobody under it yet, or null. */
    private static CarProp parkedCarNear(ServerLevel level, Vec3 pos, double reach) {
        CarProp best = null;
        double bestD = reach * reach;
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, new AABB(pos, pos).inflate(reach))) {
            CarModels.Model m = CarModels.get(car.model());
            if (m == null || m.boat() || m.wheels == null || m.wheels.length == 0 || car.aircraft()
                    || !car.getPassengers().isEmpty() && !BeltedDead.belted(car.getFirstPassenger()) || car.movingSpeed() > 0.01
                    || "burnt".equals(car.variant()) || occupied(level, car)) {
                continue;
            }
            double d = car.distanceToSqr(pos);
            if (d < bestD) {
                bestD = d;
                best = car;
            }
        }
        return best;
    }

    private static boolean occupied(ServerLevel level, CarProp car) {
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, car.getBoundingBox().inflate(1))) {
            if (how(z) == UNDER_CAR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Under the car on its nearer side, body beneath the sill and head and arms just out, facing out to the street.
     */
    public static void tuckUnder(Zombie zombie, CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        double yaw = Math.toRadians(car.getYRot());
        Vec3 local = zombie.position().subtract(car.position()).yRot((float) yaw);
        double side = local.x < 0 ? -1 : 1;
        double half = m.size[0] / 2, len = m.size[2] / 2;
        double lz = Math.clamp(local.z, -len + 0.8, len - 0.8);
        Vec3 at = car.position().add(new Vec3(side * (half - 0.5), 0, lz).yRot((float) -yaw));
        Vec3 out = new Vec3(side, 0, 0).yRot((float) -yaw);
        float facing = (float) Math.toDegrees(Math.atan2(-out.x, out.z));
        zombie.snapTo(at.x, car.getY(), at.z, facing, 0);
        zombie.setYHeadRot(facing);
        zombie.setYBodyRot(facing);
        lie(zombie, UNDER_CAR);
    }

    // ---- getting up

    private static void tick(MinecraftServer server) {
        ticks++;
        if (ticks % 5 != 0) {
            return;
        }
        boolean look = ticks % 100 == 0;
        for (Zombie zombie : List.copyOf(LYING)) {
            if (zombie.isRemoved() || !zombie.isAlive() || !waiting(zombie)) {
                LYING.remove(zombie);
                HOLDING.remove(zombie);
                continue;
            }
            if (!zombie.hasPose(Pose.SWIMMING)) {
                zombie.setPose(Pose.SWIMMING);
            }
            if (how(zombie) == UNDER_CAR) {
                underCar((ServerLevel) zombie.level(), zombie);
            } else {
                fakeDead((ServerLevel) zombie.level(), zombie);
            }
        }
        if (look) {
            crawlersFindCars(server.overworld());
        }
    }

    private static void fakeDead(ServerLevel level, Zombie zombie) {
        ServerPlayer near = nearest(level, zombie, 8);
        if (zombie.hurtTime > 0) {
            rise(zombie, near);
            return;
        }
        if (((NoiseListener) zombie).zomboidcraft$heardAt() != null) {
            Vec3 heard = ((NoiseListener) zombie).zomboidcraft$heardAt();
            rise(zombie, null);
            ((NoiseListener) zombie).zomboidcraft$hear(heard);   // up, and off to see what it was
            return;
        }
        if (near == null) {
            return;
        }
        double d = near.distanceTo(zombie);
        // each body has its own nerve: two to four blocks
        double reach = 2 + 2 * Lore.roll(zombie, 30);
        if (d <= reach) {
            rise(zombie, near);
            return;
        }
        Vec3 facing = near.getLookAngle().multiply(1, 0, 1);
        Vec3 to = zombie.position().subtract(near.position()).multiply(1, 0, 1);
        if (d < 6 && facing.lengthSqr() > 1e-6 && to.lengthSqr() > 1e-6 && facing.normalize().dot(to.normalize()) < -0.3
                && zombie.getRandom().nextInt(6) == 0) {
            rise(zombie, near);   // you turned your back on it
        }
    }

    private static void underCar(ServerLevel level, Zombie zombie) {
        ((NoiseListener) zombie).zomboidcraft$forget();   // it stays put whatever it hears
        ServerPlayer holding = HOLDING.get(zombie);
        if (holding != null) {
            if (!Grabs.isGrabbed(holding) || zombie.hurtTime > 0) {
                HOLDING.remove(zombie);
                rise(zombie, holding);   // you got loose: out it comes after you
            }
            return;
        }
        ServerPlayer near = nearest(level, zombie, 4);
        if (zombie.hurtTime > 0) {
            rise(zombie, near);
            return;
        }
        if (near == null || near.isCreative()) {
            return;
        }
        Vec3 hands = zombie.position().add(zombie.getForward().multiply(1, 0, 1).scale(0.6));
        double dx = near.getX() - hands.x, dz = near.getZ() - hands.z;
        if (dx * dx + dz * dz <= ANKLE_REACH * ANKLE_REACH && Math.abs(near.getY() - zombie.getY()) < 1.5
                && Grabs.seize(near, zombie, true)) {
            HOLDING.put(zombie, near);
        }
    }

    /** Idle crawlers near a parked car go under it to wait. */
    private static void crawlersFindCars(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            for (Zombie z : level.getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(48))) {
                if (!Downed.isCrawler(z) || waiting(z) || z.getTarget() != null || ((NoiseListener) z).zomboidcraft$heardAt() != null
                        || !ZombieTypes.isWalker(z) || p.distanceTo(z) < 12) {
                    continue;
                }
                CarProp car = parkedCarNear(level, z.position(), CAR_REACH);
                if (car == null) {
                    continue;
                }
                if (z.distanceTo(car) < Math.max(2.5, CarModels.get(car.model()).size[0] / 2 + 1)) {
                    tuckUnder(z, car);
                } else {
                    z.getNavigation().moveTo(car, 0.8);
                }
            }
        }
    }

    /** Gets up (or crawls out from under the car), at who disturbed it, grabbing at their legs if they're close. */
    public static void rise(Zombie zombie, ServerPlayer at) {
        int how = how(zombie);
        zombie.removeAttached(WAITING);
        LYING.remove(zombie);
        if (how == UNDER_CAR) {
            Vec3 out = zombie.getForward().multiply(1, 0, 1);
            zombie.setPos(zombie.getX() + out.x * 0.9, zombie.getY(), zombie.getZ() + out.z * 0.9);
        }
        if (!Downed.isCrawler(zombie)) {
            zombie.setPose(Pose.STANDING);
        }
        zombie.level().playSound(null, zombie.getX(), zombie.getY() + 0.5, zombie.getZ(), SoundEvents.ZOMBIE_AMBIENT,
                SoundSource.HOSTILE, 1.3f, 0.7f);
        if (at == null || at.isCreative() || at.isSpectator()) {
            return;
        }
        zombie.setTarget(at);
        if (at.distanceTo(zombie) < 1.8) {
            Grabs.seize(at, zombie, Downed.isCrawler(zombie));
        }
    }

    private static ServerPlayer nearest(ServerLevel level, Zombie zombie, double reach) {
        ServerPlayer best = null;
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceTo(zombie) < reach
                    && (best == null || p.distanceTo(zombie) < best.distanceTo(zombie))) {
                best = p;
            }
        }
        return best;
    }

    private LyingInWait() {
    }
}
