package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZcConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * OTTO, the robot conductor who runs every train now there's nobody left to: an Operator for
 * Transit, Timetables and Order. A cheerful customer-service voice from before it all went wrong,
 * cracking at the edges: he calls riders "valued customers" and zombies "non-paying passengers",
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
        say(level, run, clips, true);
    }

    static void departed(ServerLevel level, TrainRun run) {
        say(level, run, clips(pick(OttoLines.DEPART, level), null), false);
    }

    static void closing(ServerLevel level, TrainRun run) {
        say(level, run, clips(pick(OttoLines.CLOSING, level), null), true);
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
            say(level, run, clips(pick(OttoLines.CHATTER, level), null), false);
        }
        if (level.getGameTime() % 100 == 0 && run.quiet > 400 && zombieAboard(level, run)) {
            say(level, run, clips(pick(OttoLines.ZOMBIE_ABOARD, level), null), false);
        }
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
        TrainNet.OttoSay msg = new TrainNet.OttoSay(id, clips);
        for (ServerPlayer p : to) {
            ServerPlayNetworking.send(p, msg);
        }
    }

    private Otto() {
    }
}
