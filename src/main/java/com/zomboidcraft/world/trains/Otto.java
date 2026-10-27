package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZcConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * OTTO, the robot conductor who runs every train now there's nobody left to: an Operator for
 * Transit, Timetables and Order. A cheerful customer-service voice from before it all went wrong,
 * cracking at the edges: he calls riders "valued customers" and zombies "non-paying passengers",
 * honks at anything in the way, counts the doors shut whether you're through them or not, and
 * glitches mid-sentence. Our own character, with a nod to the robot bus driver of old zombie games.
 * He talks in chat to everyone riding and everyone on the platform.
 */
final class Otto {
    private static final String NAME = "OTTO";

    private static final String[] DEPART = {
        "Doors closed. Please hold on. Or don't. Gravity is free.",
        "Departing. Keep all limbs inside the train. You may need them later.",
        "Next stop: somewhere slightly less on fire. Probably.",
        "This train is now leaving. Anyone banging on the doors outside: we are not hiring.",
        "Departure on schedule. The schedule is whatever I say it is.",
        "Hold tight, valued customers. I brake for nobody. Well. Almost nobody.",
    };
    private static final String[] CLOSING = {
        "Doors closing. Five. Four. I lost count. Closing.",
        "Stand clear of the doors. They are hungrier than they look.",
        "Last call. Stragglers will be left for the local wildlife.",
        "Doors closing. If something is chasing you, now would be the time.",
    };
    private static final String[] CHATTER = {
        "Fun fact: this line has not had a single complaint in months. Customers stopped complaining. And breathing.",
        "If you see something, say something. Then shoot it.",
        "Reminder: eating is not permitted on this train. That goes double for the passengers who bite.",
        "Your conductor today is OTTO. Your conductor tomorrow is also OTTO. OTTO is always the conductor.",
        "Please do not feed the passengers.",
        "We apologise for the delay. There was a man on the line. There was also most of a man under the line.",
        "Smile! You are on camera. Nobody is watching the camera. But smile.",
        "I have run this route eleven thousand times. The view is still... it is... it is a view.",
        "Customer satisfaction remains high among customers who remain.",
        "Ticket inspection is cancelled due to lack of tickets. And inspectors.",
        "Did you know? Trains are the safest way to travel. Statistically. Statistics are mostly dead now.",
        "Please keep the aisle clear. Of bodies. Especially moving ones.",
    };
    private static final String[] BUMP = {
        "Bump.",
        "Non-paying passenger removed from the line.",
        "Something was on the track. Something is now everywhere on the track.",
        "Sorry. Not sorry. Schedule.",
        "That one did not have a ticket.",
    };
    private static final String[] ZOMBIE_ABOARD = {
        "Attention: we have a non-paying passenger aboard. Please deal with it.",
        "A passenger appears to be eating another passenger. This is not permitted.",
        "Biting is against train policy. I will be writing a strongly worded report.",
    };
    private static final String[] EXPRESS = {
        "Someone rang. Express service to %s. All other stops are cancelled. Their loss.",
        "A valued customer is calling from %s. Skipping everything else. Hold on to something.",
        "Rerouting: non-stop to %s. Whoever is out there, they lit a flare. Brave. Stupid. Brave.",
    };
    private static final String[] CALLED = {
        "Call received at %s. A train is on its way. So, I expect, is everything else.",
        "%s: train requested. You have made yourself very noticeable. Good luck.",
        "Request logged at %s. Please wait on the platform. Try not to get eaten in the meantime.",
    };
    private static final String[] HERE = {
        "I am right here. Get on.",
        "The train is at the platform. The doors are open. This is the easy part.",
    };
    private static final String[] CUT_SHORT = {
        "Someone up the line rang. Doors closing early. Sorry, not sorry.",
        "Change of plan, valued customers. Somebody needs us more than you do.",
    };

    /** What he says pulling into stops he knows. */
    private static String arrival(String stop, RandomSource r) {
        String s = stop.toLowerCase(Locale.ROOT);
        String[] lines;
        if (s.contains("stadium")) {
            lines = new String[] {
                "Grayhaven Stadium. Tonight's game has been cancelled. Indefinitely. The crowd is still here though.",
                "Stadium. Home of the Grayhaven Grizzlies. The fans are more dedicated than ever. They will not leave.",
            };
        } else if (s.contains("union")) {
            lines = new String[] {
                "Union Station. Change here for the county railway. Change here for nothing else. Nothing else runs.",
                "Union Station. Mind the gap. Mind the things in the gap.",
            };
        } else if (s.contains("mercy") || s.contains("hospital")) {
            lines = new String[] {
                "Mercy General. Visiting hours are over. The patients are visiting you now.",
                "Mercy General. If you require medical attention, I recommend another hospital. Any other hospital.",
            };
        } else if (s.contains("waterfront") || s.contains("harbo") || s.contains("pier")) {
            lines = new String[] {
                "Waterfront. Lovely sea air. Do not swim. The water is full of swimmers.",
                "Waterfront. Boats leave hourly. Boats do not come back. Boats are a metaphor.",
            };
        } else if (s.contains("tower") || s.contains("broadcast")) {
            lines = new String[] {
                "Broadcast Tower. Still on the air. Nobody is listening. Except me. I am always listening.",
                "Broadcast Tower. If you find the emergency message, please tell it to stop repeating itself.",
            };
        } else if (s.contains("power")) {
            lines = new String[] {
                "Power Station. Without it, I would be very quiet. Please do not touch anything.",
                "Power Station. Please thank the reactor for keeping me alive. I do. Every day. Nervously.",
            };
        } else {
            lines = new String[] {
                stop + ". Doors opening. Please exit carefully. The platform has opinions.",
                stop + ". Thank you for riding. Your survival is not guaranteed, but it is appreciated.",
                stop + ". Please take all belongings and all limbs with you.",
            };
        }
        return lines[r.nextInt(lines.length)];
    }

    static void arrived(ServerLevel level, TrainRun run, Line.Stop stop, boolean called) {
        String line = arrival(stop.name(), level.getRandom());
        say(level, run, called ? "Express arrival. " + line : line, true);
    }

    static void departed(ServerLevel level, TrainRun run) {
        say(level, run, pick(DEPART, level), false);
    }

    static void closing(ServerLevel level, TrainRun run) {
        say(level, run, pick(CLOSING, level), true);
    }

    static void cutShort(ServerLevel level, TrainRun run) {
        say(level, run, pick(CUT_SHORT, level), true);
    }

    static void express(ServerLevel level, TrainRun run, Line.Stop stop) {
        say(level, run, String.format(pick(EXPRESS, level), stop.name()), false);
        TrainSounds.horn(level, run, 2);
    }

    static void called(ServerLevel level, Line line, Line.Stop stop, ServerPlayer by) {
        Component msg = voice(String.format(CALLED[level.getRandom().nextInt(CALLED.length)], stop.name()));
        by.sendSystemMessage(msg);
    }

    static void alreadyHere(ServerLevel level, TrainRun run, ServerPlayer by) {
        by.sendSystemMessage(voice(pick(HERE, level)));
    }

    static void bump(ServerLevel level, TrainRun run) {
        if (run.quiet > 60) {
            say(level, run, pick(BUMP, level), false);
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
                say(level, run, "Next stop: " + stop.name() + ".", false);
                TrainSounds.horn(level, run, 1);
            }
        }
        if (cfg.ottoChatter > 0 && run.quiet > cfg.ottoChatter * 20 && level.getRandom().nextInt(200) == 0) {
            say(level, run, pick(CHATTER, level), false);
        }
        if (level.getGameTime() % 100 == 0 && run.quiet > 400 && zombieAboard(level, run)) {
            say(level, run, pick(ZOMBIE_ABOARD, level), false);
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

    private static String pick(String[] lines, ServerLevel level) {
        return lines[level.getRandom().nextInt(lines.length)];
    }

    private static Component voice(String text) {
        return Component.literal("[" + NAME + "] ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(Component.literal(glitch(text)).withStyle(ChatFormatting.GRAY));
    }

    /** Now and then a word sticks, the way a damaged voice chip repeats. */
    private static String glitch(String text) {
        RandomSource r = RandomSource.create();
        if (r.nextInt(4) != 0) {
            return text;
        }
        String[] words = text.split(" ");
        int i = r.nextInt(words.length);
        String w = words[i];
        if (w.length() > 3) {
            String stem = w.substring(0, 2);
            words[i] = stem + "-" + stem + "-" + w;
        }
        return String.join(" ", words);
    }

    /** Says a line to everyone riding and everyone near the train (and, at a stop, on its platform). */
    static void say(ServerLevel level, TrainRun run, String text, boolean platform) {
        if (!ZcConfig.get().trains.ottoTalks) {
            return;
        }
        run.quiet = 0;
        Component msg = voice(text);
        List<ServerPlayer> to = new ArrayList<>();
        Entity e = run.entity == null ? null : level.getEntity(run.entity);
        if (e instanceof TrainEntity train) {
            train.talked();
            to.addAll(Trains.riders(level, train));
        }
        TrainCar.Pose mid = run.pose((run.line.cars - 1) / 2);
        double reach = platform ? run.line.cars * TrainCar.PITCH / 2 + 16 : run.line.cars * TrainCar.PITCH / 2 + 4;
        Vec3 c = new Vec3(mid.x(), mid.y(), mid.z());
        for (ServerPlayer p : level.players()) {
            if (!to.contains(p) && p.position().distanceToSqr(c) < reach * reach) {
                to.add(p);
            }
        }
        for (ServerPlayer p : to) {
            p.sendSystemMessage(msg);
        }
        TrainSounds.otto(level, run);
    }

    private Otto() {
    }
}
