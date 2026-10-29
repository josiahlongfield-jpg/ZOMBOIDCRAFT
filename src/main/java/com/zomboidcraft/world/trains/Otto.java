package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.ModSounds;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * OTTO, the robot conductor who runs every train now there's nobody left to: an Operator for
 * Transit, Timetables and Order. A cheerful customer-service voice from before it all went wrong,
 * gone foul-mouthed and rotten at the edges: he swears at his riders, calls them meatbags and dipshits,
 * honks at anything in the way, counts the doors shut whether you're through them or not, and
 * glitches mid-sentence. Our own character, with a nod to the robot bus driver of old zombie games.
 * He talks out loud (tools/trains/otto_voice.py records him) through every car's speakers.
 */
final class Otto {
    /** His lines for a stop he knows by name; any other stop gets a general one. */
    private static String[][] arrival(String stop) {
        String s = stop.toLowerCase(Locale.ROOT);
        if (s.contains("stadium")) {
            return OttoLines.ARRIVE_STADIUM;
        } else if (s.contains("union")) {
            return OttoLines.ARRIVE_UNION;
        } else if (s.contains("mercy") || s.contains("hospital")) {
            return OttoLines.ARRIVE_MERCY;
        } else if (s.contains("waterfront") || s.contains("harbo") || s.contains("pier")) {
            return OttoLines.ARRIVE_WATERFRONT;
        } else if (s.contains("tower") || s.contains("broadcast")) {
            return OttoLines.ARRIVE_TOWER;
        } else if (s.contains("power")) {
            return OttoLines.ARRIVE_POWER;
        }
        return OttoLines.ARRIVE_ANY;
    }

    static void arrived(ServerLevel level, TrainRun run, Line.Stop stop, boolean called) {
        List<String> clips = new ArrayList<>();
        if (called) {
            clips.addAll(clips(OttoLines.EXPRESS_ARRIVAL[0], stop));
        }
        clips.addAll(clips(pick(arrival(stop.name()), level), stop));
        // and as the doors open, a warm welcome
        clips.addAll(clips(level.getRandom().nextInt(2) == 0 ? OttoLines.DOORS_OPEN[0] : pick(OttoLines.DOORS_OPEN, level), stop));
        say(level, run, clips, true);
    }

    static void departed(ServerLevel level, TrainRun run) {
        say(level, run, clips(pick(OttoLines.DEPART, level), null), false);
    }

    static void closing(ServerLevel level, TrainRun run) {
        say(level, run, clips(pick(OttoLines.CLOSING, level), null), true);
    }

    static void stopRequested(ServerLevel level, TrainRun run, ServerPlayer by) {
        say(level, run, clips(pick(OttoLines.STOP_REQUESTED, level), null), false, by);
    }

    static void halted(ServerLevel level, TrainRun run) {
        say(level, run, clips(pick(OttoLines.HALTED, level), null), true);
    }

    static void doorsHeld(ServerLevel level, TrainRun run, ServerPlayer by) {
        say(level, run, clips(pick(OttoLines.DOORS_HELD, level), null), true, by);
    }

    static void doorsShut(ServerLevel level, TrainRun run, ServerPlayer by) {
        say(level, run, clips(pick(OttoLines.DOORS_SHUT, level), null), true, by);
    }

    static void cutShort(ServerLevel level, TrainRun run) {
        say(level, run, clips(pick(OttoLines.CUT_SHORT, level), null), true);
    }

    static void express(ServerLevel level, TrainRun run, Line.Stop stop) {
        say(level, run, clips(pick(OttoLines.EXPRESS, level), stop), false);
        TrainSounds.horn(level, run, 2);
    }

    static void called(ServerLevel level, Line line, Line.Stop stop, ServerPlayer by) {
        tell(by, clips(pick(OttoLines.CALLED, level), stop));
    }

    static void alreadyHere(ServerLevel level, TrainRun run, ServerPlayer by) {
        tell(by, clips(pick(OttoLines.HERE, level), null));
    }

    static void bump(ServerLevel level, TrainRun run) {
        if (run.quiet > 60) {
            say(level, run, clips(pick(OttoLines.BUMP, level), null), false);
        }
        TrainSounds.horn(level, run, 1);
    }

    /** Every tick on the move: honks for crossings and the stop ahead, idle remarks, and zombies aboard. */
    static void running(ServerLevel level, TrainRun run, ZcConfig.Trains cfg) {
        run.quiet++;
        Route route = run.line.route;
        for (double c : run.line.crossings) {
            double ahead = route.ahead(run.s + TrainCar.HALF, c);
            if (ahead < 120 && ahead > 60 && Math.abs(route.diff(run.honkedAt, c)) > 1) {
                run.honkedAt = c;
                TrainSounds.horn(level, run, 2);
            }
        }
        if (run.target >= 0 && run.announced != run.target) {
            Line.Stop stop = run.line.stops.get(run.target);
            double ahead = route.ahead(run.s, run.line.frontAt(stop));
            if (ahead < 160) {
                run.announced = run.target;
                say(level, run, clips(OttoLines.NEXT_STOP[0], stop), false);
                TrainSounds.horn(level, run, 1);
            }
        }
        if (cfg.ottoChatter > 0 && run.quiet > cfg.ottoChatter * 20 && level.getRandom().nextInt(200) == 0) {
            // half the time a remark, half the time a moan about his fucking job
            say(level, run, clips(pick(level.getRandom().nextBoolean() ? OttoLines.CHATTER : OttoLines.JOB, level), null), false);
        }
        if (level.getGameTime() % 100 == 0 && run.quiet > 400 && zombieAboard(level, run)) {
            say(level, run, clips(pick(OttoLines.ZOMBIE_ABOARD, level), null), false);
        }
    }

    /**
     * Every tick, stopped or moving: he notices riders getting on, getting off (or jumping off at
     * speed), and a crowd of zombies about the train.
     */
    static void watch(ServerLevel level, TrainRun run) {
        if (run.riderCooldown > 0) {
            run.riderCooldown--;
        }
        if (run.zombieCooldown > 0) {
            run.zombieCooldown--;
        }
        if (run.shotCooldown > 0) {
            run.shotCooldown--;
        }
        long now = level.getGameTime();
        if (run.shots > 0 && now % 300 == 0) {
            run.shots--;
        }
        if (now % 10 != 0 || run.entity == null || !(level.getEntity(run.entity) instanceof TrainEntity train)) {
            return;
        }
        Set<UUID> riding = new HashSet<>();
        for (ServerPlayer p : Trains.riders(level, train)) {
            riding.add(p.getUUID());
        }
        boolean boarded = false, left = false;
        for (UUID id : riding) {
            boarded |= !run.aboard.contains(id);
        }
        for (UUID id : run.aboard) {
            left |= !riding.contains(id) && level.getPlayerByUUID(id) != null;
        }
        run.aboard.clear();
        run.aboard.addAll(riding);
        if (run.riderCooldown <= 0 && (left || boarded)) {
            boolean jumped = left && run.phase == TrainRun.Phase.RUN && run.speedPerSecond() > 4;
            say(level, run, clips(pick(jumped ? OttoLines.JUMPED : left ? OttoLines.ALIGHT : OttoLines.BOARD, level), null), true);
            run.riderCooldown = 160;
        }
        if (now % 100 == 0 && run.zombieCooldown <= 0 && zombiesAbout(level, run, train) >= 4) {
            say(level, run, clips(pick(run.phase == TrainRun.Phase.DWELL ? OttoLines.ZOMBIES_PLATFORM : OttoLines.ZOMBIES_OUTSIDE, level), null), true);
            run.zombieCooldown = 900;
        }
    }

    private static int zombiesAbout(ServerLevel level, TrainRun run, TrainEntity train) {
        TrainCar.Pose mid = run.pose((run.line.cars - 1) / 2);
        double r = run.line.cars * TrainCar.PITCH / 2 + 16;
        return level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                new net.minecraft.world.phys.AABB(mid.x() - r, mid.y() - 8, mid.z() - r, mid.x() + r, mid.y() + 10, mid.z() + r),
                z -> z.isAlive() && ((TrainRider) z).zomboidcraft$train() != train).size();
    }

    /** Where he sits in the front car, and where the shot that crossed his window or windscreen went through (car frame). */
    private static final Vec3 HIM = new Vec3(0, 1.9, TrainCar.CAB + 1.0);

    /**
     * A shot passing the front car (a to b, in its frame): a round that hits him sparks off his
     * frame, one through his cab's window or windscreen cracks off the glass, and either, or a
     * near miss, gets an earful (worse each time it happens again).
     */
    static void shot(ServerLevel level, TrainRun run, ServerPlayer shooter, Vec3 a, Vec3 b, TrainCar.Pose pose) {
        double d = distance(a, b, HIM);
        Vec3 pane = through(a, b, TrainCar.CAB + TrainCar.WALL / 2, 0.7, 1.1, 2.75);
        if (pane == null) {
            pane = through(a, b, TrainCar.HALF - TrainCar.WALL / 2, 1.5, 1.2, 2.6);
        }
        boolean hit = d < 0.45;
        if (!hit && pane == null && d > 2.5) {
            return;
        }
        if (pane != null) {
            Vec3 w = pose.toWorld(pane.x, pane.y, pane.z);
            level.playSound(null, w.x, w.y, w.z, ModSounds.IMPACT_GLASS, SoundSource.PLAYERS, 0.9f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), w.x, w.y, w.z, 8, 0.1, 0.1, 0.1, 0.1);
        }
        if (hit) {
            Vec3 w = pose.toWorld(HIM.x, HIM.y, HIM.z);
            level.playSound(null, w.x, w.y, w.z, ModSounds.IMPACT_METAL, SoundSource.PLAYERS, 1f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            level.sendParticles(ParticleTypes.CRIT, w.x, w.y, w.z, 10, 0.15, 0.2, 0.15, 0.2);
        }
        if (run.shotCooldown > 0) {
            return;
        }
        run.shotCooldown = 50;
        run.shots++;
        String[][] group = run.shots >= 3 ? OttoLines.SHOT_ANGRY : hit || pane == null ? OttoLines.SHOT_AT : OttoLines.SHOT_GLASS;
        say(level, run, clips(pick(group, level), null), true, shooter);
    }

    /** How near the segment a-b passes p. */
    private static double distance(Vec3 a, Vec3 b, Vec3 p) {
        Vec3 ab = b.subtract(a);
        double len = ab.lengthSqr();
        double t = len < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len));
        return a.add(ab.scale(t)).distanceTo(p);
    }

    /** Where the segment a-b crosses the pane across the car at z (|x| up to halfWidth, y0 to y1), or null. */
    private static Vec3 through(Vec3 a, Vec3 b, double z, double halfWidth, double y0, double y1) {
        if ((a.z - z) * (b.z - z) > 0 || Math.abs(b.z - a.z) < 1e-9) {
            return null;
        }
        double t = (z - a.z) / (b.z - a.z);
        Vec3 c = a.add(b.subtract(a).scale(t));
        return Math.abs(c.x) <= halfWidth && c.y >= y0 && c.y <= y1 ? c : null;
    }

    private static boolean zombieAboard(ServerLevel level, TrainRun run) {
        if (run.entity == null || !(level.getEntity(run.entity) instanceof TrainEntity train)) {
            return false;
        }
        TrainCar.Pose mid = run.pose((run.line.cars - 1) / 2);
        double r = run.line.cars * TrainCar.PITCH / 2 + 4;
        return !level.getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                new net.minecraft.world.phys.AABB(mid.x() - r, mid.y() - 2, mid.z() - r, mid.x() + r, mid.y() + 6, mid.z() + r),
                z -> ((TrainRider) z).zomboidcraft$train() == train).isEmpty();
    }

    private static String[] pick(String[][] lines, ServerLevel level) {
        return lines[level.getRandom().nextInt(lines.length)];
    }

    /** A line's voice clips, with the stop's name said where it goes (left out for a stop he can't name). */
    private static List<String> clips(String[] pieces, Line.Stop stop) {
        List<String> out = new ArrayList<>();
        for (String p : pieces) {
            if (!p.equals("{stop}")) {
                out.add(p);
            } else if (stop != null) {
                String name = OttoLines.STOPS.get(stop.name().toLowerCase(Locale.ROOT));
                if (name != null) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    /** Over the station speaker to whoever pressed the call button. */
    private static void tell(ServerPlayer by, List<String> clips) {
        if (ZcConfig.get().trains.ottoTalks) {
            ServerPlayNetworking.send(by, new TrainNet.OttoSay(-1, clips));
        }
    }

    /** Says a line out loud to everyone riding and everyone near the train (and, at a stop, on its platform). */
    static void say(ServerLevel level, TrainRun run, List<String> clips, boolean platform) {
        say(level, run, clips, platform, null);
    }

    /** The same, and to one more player wherever they are (the one he's shouting at). */
    static void say(ServerLevel level, TrainRun run, List<String> clips, boolean platform, ServerPlayer also) {
        if (!ZcConfig.get().trains.ottoTalks || clips.isEmpty()) {
            return;
        }
        run.quiet = 0;
        List<ServerPlayer> to = new ArrayList<>();
        Entity e = run.entity == null ? null : level.getEntity(run.entity);
        int id = -1;
        if (e instanceof TrainEntity train) {
            train.talked();
            id = train.getId();
            to.addAll(Trains.riders(level, train));
        }
        TrainCar.Pose mid = run.pose((run.line.cars - 1) / 2);
        double reach = run.line.cars * TrainCar.PITCH / 2 + (platform ? 16 : 6);
        Vec3 c = new Vec3(mid.x(), mid.y(), mid.z());
        for (ServerPlayer p : level.players()) {
            if (!to.contains(p) && p.position().distanceToSqr(c) < reach * reach) {
                to.add(p);
            }
        }
        if (also != null && !to.contains(also)) {
            to.add(also);
        }
        TrainNet.OttoSay msg = new TrainNet.OttoSay(id, clips);
        for (ServerPlayer p : to) {
            ServerPlayNetworking.send(p, msg);
        }
    }

    private Otto() {
    }
}
