package com.zomboidcraft.world.cars;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.Guns;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.world.events.AirStrikes;
import com.zomboidcraft.zombie.Noise;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A vehicle's mounted gun (the Warthog's three-barrel minigun): the gunner stands behind it and it points where they
 * look, the post turning all the way round and the gun tilting on it. Holding the attack button spins the barrels up,
 * then it fires off its belt; R loads the belt from the gunner's own loose 5.56 rounds. Rounds strike like rifle
 * bullets (Guns.mountedRound), one in a few a tracer.
 */
public final class Turret {
    private Turret() {
    }

    /** The gun's turn about its post (radians, model space) for a gunner whose look is yaw degrees, the vehicle's carYaw. */
    public static float yaw(float gunnerYaw, float carYaw) {
        return (float) -Math.toRadians(Mth.wrapDegrees(gunnerYaw - carYaw));
    }

    /** The gun's tilt (radians about +x, positive nose down) for a gunner looking pitch degrees (down +). */
    public static float pitch(CarModels.Turret t, float pitch) {
        return (float) Math.toRadians(t.rest + Mth.clamp(pitch, -t.up, t.down));
    }

    /** A point of the gun at rest, moved with it: tilted about its pivot, then turned with the post. Model space. */
    public static float[] move(CarModels.Turret t, float[] p, float yaw, float tilt) {
        float x = p[0], y = p[1] - t.pitch[1], z = p[2] - t.pitch[2];
        float c = Mth.cos(tilt), s = Mth.sin(tilt);
        float y2 = y * c - z * s, z2 = y * s + z * c;
        y = y2 + t.pitch[1];
        z = z2 + t.pitch[2];
        x -= t.yaw[0];
        z -= t.yaw[2];
        c = Mth.cos(yaw);
        s = Mth.sin(yaw);
        return new float[] {x * c + z * s + t.yaw[0], y, -x * s + z * c + t.yaw[2]};
    }

    /** A point that only turns with the post (the gunner's place behind the gun). Model space. */
    public static float[] turn(CarModels.Turret t, float[] p, float yaw) {
        float x = p[0] - t.yaw[0], z = p[2] - t.yaw[2], c = Mth.cos(yaw), s = Mth.sin(yaw);
        return new float[] {x * c + z * s + t.yaw[0], p[1], -x * s + z * c + t.yaw[2]};
    }

    /** The gun's spread (degrees, the cone's half-angle): tighter while the gunner's zoomed in. The client's ring uses it too. */
    public static double spread(boolean zoomed, ZcConfig.MinigunStats stats) {
        return zoomed ? stats.aimSpread : stats.hipSpread;
    }

    static double spread(CarProp car, ZcConfig.MinigunStats stats) {
        return spread(car.turretZoom && gunner(car) != null, stats);
    }

    /** The gunner's rider, or null. */
    public static Player gunner(CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null || m.turret == null || m.gunner < 0) {
            return null;
        }
        for (Entity rider : car.getPassengers()) {
            if (car.seatOf(rider) == m.gunner && rider instanceof Player p) {
                return p;
            }
        }
        return null;
    }

    public static boolean isGunner(Entity rider) {
        return rider.getVehicle() instanceof CarProp car && gunner(car) == rider;
    }

    /** The gunner holds or lets go of the trigger. */
    public static void trigger(CarProp car, ServerPlayer player, boolean on) {
        if (gunner(car) != player) {
            return;
        }
        if (on && !car.turretTrigger && car.turretSpin() < 0.05f && car.belt() > 0) {
            ServerLevel level = (ServerLevel) car.level();
            Vec3 at = car.position().add(0, 2, 0);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8f, 1.9f);
        }
        car.turretTrigger = on;
    }

    /** R from the gunner's place: the belt filled up from their loose 5.56 rounds, taking a few seconds. */
    public static void loadBelt(CarProp car, ServerPlayer player) {
        ZcConfig.MinigunStats stats = ZcConfig.get().minigun;
        if (gunner(car) != player || car.beltBusy > 0) {
            return;
        }
        int room = stats.beltSize - car.belt();
        if (room <= 0) {
            player.sendOverlayMessage(Component.literal("The belt's full: " + car.belt() + " rounds."));
            return;
        }
        Inventory inv = player.getInventory();
        int took = 0;
        for (int i = 0; i < inv.getContainerSize() && took < room; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.RIFLE_AMMO)) {
                int n = Math.min(room - took, s.getCount());
                s.shrink(n);
                took += n;
            }
        }
        if (took == 0) {
            player.sendOverlayMessage(Component.literal("No 5.56 rounds to load the belt with.").withStyle(ChatFormatting.RED));
            return;
        }
        car.setBelt(car.belt() + took);
        car.beltBusy = (int) Math.round(stats.beltLoadSeconds * 20);
        car.turretTrigger = false;
        ServerLevel level = (ServerLevel) car.level();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), GunType.ASSAULT_RIFLE.magOut(), SoundSource.PLAYERS, 0.9f, 0.8f);
        player.sendOverlayMessage(Component.literal("Loading the belt: " + took + " rounds in, " + car.belt() + " on it."));
    }

    /** Each server tick of a vehicle with a turret: the barrels spin up or run down, and fire while held. */
    static void tick(CarProp car, ServerLevel level) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null || m.turret == null) {
            return;
        }
        ZcConfig.MinigunStats stats = ZcConfig.get().minigun;
        Player gunner = gunner(car);
        if (!(gunner instanceof ServerPlayer shooter) || shooter.isSpectator()) {
            car.turretTrigger = false;
            car.turretZoom = false;
        }
        if (car.beltBusy > 0) {
            car.beltBusy--;
            if (car.beltBusy == 10 && gunner != null) {
                level.playSound(null, gunner.getX(), gunner.getY(), gunner.getZ(), GunType.ASSAULT_RIFLE.magIn(), SoundSource.PLAYERS, 0.9f, 0.8f);
            }
        }
        boolean held = car.turretTrigger && car.beltBusy == 0 && !"burnt".equals(car.variant());
        float spin = car.turretSpin();
        float was = spin;
        spin = held ? Math.min(1f, spin + (float) (1 / (20 * Math.max(0.05, stats.spinUpSeconds))))
                : Math.max(0f, spin - (float) (1 / (20 * Math.max(0.05, stats.spinDownSeconds))));
        if (spin != was) {
            car.setTurretSpin(spin);
        }
        if (!held && was >= 1f && spin < 1f) {
            Vec3 at = car.position().add(0, 2, 0);
            level.playSound(null, at.x, at.y, at.z, ModSounds.MINIGUN_SPIN_DOWN, SoundSource.PLAYERS, 1f, 1f);
        }
        boolean firing = held && spin >= 1f && car.belt() > 0;
        if (firing != car.turretFiring()) {
            car.setTurretFiring(firing);
            if (!firing) {
                stopLoop(car, level);
            }
        }
        if (held && spin >= 1f && car.belt() <= 0 && level.getGameTime() % 8 == 0 && gunner != null) {
            level.playSound(null, gunner.getX(), gunner.getEyeY(), gunner.getZ(), GunType.ASSAULT_RIFLE.dry(), SoundSource.PLAYERS, 0.8f, 0.7f);
            gunner.sendOverlayMessage(Component.literal("The belt's empty. R loads it from your 5.56 rounds.").withStyle(ChatFormatting.RED));
        }
        if (!firing) {
            car.fireTicks = 0;
            return;
        }
        // in step with the recording: its loop from the top every loopTicks, and each group of rounds half on the
        // tick the group's first shot is heard, the rest on the next (the four shots are 45 ms apart)
        int t = car.fireTicks++;
        if (t % Math.max(1, stats.loopTicks) == 0) {
            Vec3 at = car.toWorld(move(m.turret, m.turret.muzzle, yaw(gunner.getYRot(), car.getYRot()), pitch(m.turret, gunner.getXRot())));
            level.playSound(null, at.x, at.y, at.z, ModSounds.MINIGUN_LOOP, SoundSource.PLAYERS, 1f, 1f);
        }
        int g = t % Math.max(1, stats.groupTicks);
        int rounds = g == 0 ? (stats.groupRounds + 1) / 2 : g == 1 ? stats.groupRounds / 2 : 0;
        for (int i = 0; i < rounds && car.belt() > 0; i++) {
            round(car, m, (ServerPlayer) gunner, stats, level);
        }
    }

    /** The firing loop cut off where it is (the run-down plays over it), for everyone who could hear it. */
    private static void stopLoop(CarProp car, ServerLevel level) {
        var stop = new net.minecraft.network.protocol.game.ClientboundStopSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getKey(ModSounds.MINIGUN_LOOP), SoundSource.PLAYERS);
        for (ServerPlayer p : PlayerLookup.around(level, car.position(), 160)) {
            p.connection.send(stop);
        }
    }

    private static void round(CarProp car, CarModels.Model m, ServerPlayer gunner, ZcConfig.MinigunStats stats, ServerLevel level) {
        car.setBelt(car.belt() - 1);
        int n = ++car.turretShots;
        float yaw = yaw(gunner.getYRot(), car.getYRot()), tilt = pitch(m.turret, gunner.getXRot());
        Vec3 muzzle = car.toWorld(move(m.turret, m.turret.muzzle, yaw, tilt));
        // it goes where the gunner's looking: at what the sights are on, from the barrels
        Vec3 eye = gunner.getEyePosition(), look = gunner.getLookAngle();
        Vec3 far = eye.add(look.scale(stats.range));
        HitResult seen = level.clip(new ClipContext(eye.add(look.scale(2.5)), far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, gunner));
        Vec3 aim = (seen.getType() == HitResult.Type.MISS ? far : seen.getLocation()).subtract(muzzle).normalize();
        double spread = Math.toRadians(spread(car, stats)) * Math.sqrt(gunner.getRandom().nextDouble());
        double a = gunner.getRandom().nextDouble() * Math.PI * 2;
        Vec3 right = aim.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(aim).normalize();
        Vec3 dir = aim.add(right.scale(Math.cos(a) * Math.tan(spread))).add(up.scale(Math.sin(a) * Math.tan(spread))).normalize();
        Vec3 stop = Guns.mountedRound(gunner, muzzle, dir, stats, car, n % 2 == 0);
        // the sound is the firing loop's (tick), each round in it
        if (n % 3 == 0) {
            level.playSound(null, muzzle.x, muzzle.y - 0.4, muzzle.z, Guns.casing(GunType.ASSAULT_RIFLE), SoundSource.PLAYERS, 0.35f,
                    0.9f + gunner.getRandom().nextFloat() * 0.25f);
        }
        if (n % 5 == 0) {
            Noise.emit(level, muzzle, stats.shotNoise);
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.05, 0.05, 0.05, 0.01);
        }
        if (stats.tracerEvery > 0 && n % stats.tracerEvery == 0) {
            AirStrikes.Tracer tracer = new AirStrikes.Tracer((float) muzzle.x, (float) muzzle.y, (float) muzzle.z,
                    (float) stop.x, (float) stop.y, (float) stop.z);
            for (ServerPlayer p : PlayerLookup.around(level, muzzle, 200)) {
                ServerPlayNetworking.send(p, tracer);
            }
        }
    }
}
