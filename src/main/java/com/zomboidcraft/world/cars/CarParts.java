package com.zomboidcraft.world.cars;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A car's parts, Zomboid style but with every part doing something you feel: each has a condition 0-100 and
 * the drive, the start, the brakes, the grip and the noise all come from them (see {@link #drive}). Aircraft,
 * boats and the farm wagons keep their own state; this is for everything with an engine and four wheels.
 *
 * <p>Kept as one synced string, a character a slot in {@link #SLOTS} order, so the driver's side (which does the
 * driving) reads the same parts the server wears: {@link #FITTED_0} + condition, {@link #MISSING} for a part
 * that's been taken off or never was, {@link #NONE} for a slot this model doesn't have (rear doors on a coupe).
 */
public final class CarParts {
    public static final char FITTED_0 = '0', MISSING = '-', NONE = '.';

    /** Where on the car a part is worked on, which decides where you stand (and what has to be lifted). */
    public enum Area { ENGINE, UNDER, WHEEL, BODY }

    /** Standard, heavy-duty or sport: a part fits its own class (from Mechanics 7, others with a penalty). */
    public enum Kind { STANDARD, HEAVY, SPORT }

    public enum Part {
        ENGINE("engine_block", "Engine", 1, Area.ENGINE, false, 8),
        SPARK_PLUGS("spark_plugs", "Spark plugs", 1, Area.ENGINE, false, 1),
        BATTERY("car_battery", "Battery", 1, Area.ENGINE, false, 0),
        ALTERNATOR("alternator", "Alternator", 1, Area.ENGINE, false, 4),
        STARTER("starter_motor", "Starter motor", 1, Area.ENGINE, false, 4),
        RADIATOR("radiator", "Radiator", 1, Area.ENGINE, false, 4),
        FAN_BELT("fan_belt", "Fan belt", 1, Area.ENGINE, false, 2),
        OIL("motor_oil", "Oil and filter", 1, Area.UNDER, true, 3),
        GEARBOX("gearbox", "Gearbox", 1, Area.UNDER, true, 7),
        FUEL_TANK("fuel_tank", "Fuel tank", 1, Area.UNDER, true, 6),
        FUEL_PUMP("fuel_pump", "Fuel pump", 1, Area.UNDER, true, 6),
        EXHAUST("exhaust", "Exhaust and muffler", 1, Area.UNDER, true, 5),
        STEERING("steering_rack", "Steering rack", 1, Area.UNDER, true, 5),
        BRAKE_FLUID("brake_fluid", "Brake fluid", 1, Area.ENGINE, false, 6),
        BRAKES("brake_pads", "Brakes", 4, Area.WHEEL, true, 3),
        SUSPENSION("suspension", "Suspension", 4, Area.WHEEL, true, 5),
        TIRE("car_tire", "Tyre", 4, Area.WHEEL, true, 0),
        RIM("wheel_rim", "Wheel rim", 4, Area.WHEEL, true, 4),
        HEADLIGHT("headlight", "Headlight", 2, Area.BODY, false, 0),
        TAIL_LIGHT("tail_light", "Tail light", 2, Area.BODY, false, 1),
        DOOR("car_door", "Door", 4, Area.BODY, false, 3),
        BONNET("car_bonnet", "Bonnet", 1, Area.BODY, false, 2),
        BOOT_LID("boot_lid", "Boot lid", 1, Area.BODY, false, 2),
        HORN("car_horn", "Horn", 1, Area.ENGINE, false, 1),
        RADIO("car_radio", "Radio", 1, Area.BODY, false, 1),
        /** Zomboid's seats: one gone or broken can't be sat in (eight slots, as many used as the model has). */
        SEAT("car_seat", "Seat", 8, Area.BODY, false, 2),
        /** Zomboid's glovebox: gone, there's nowhere to keep things up front. */
        GLOVEBOX("glovebox", "Glovebox", 1, Area.BODY, false, 1);

        /** The loot item the part comes off as (LOOT's id, condition and kind as its data). */
        public final String item, title;
        public final int count;
        public final Area area;
        /** Whether the car has to be up (on a hoist, or that corner on a jack) to reach it. */
        public final boolean lifted;
        /** The Mechanics level to take it off or put it on. */
        public final int level;

        Part(String item, String title, int count, Area area, boolean lifted, int level) {
            this.item = item;
            this.title = title;
            this.count = count;
            this.area = area;
            this.lifted = lifted;
            this.level = level;
        }
    }

    /** One place on the car a part goes: the part and which of them (0 front left, 1 front right, 2 rear left, 3 rear right). */
    public record Slot(Part part, int index) {
        public String title() {
            if (part.count == 1) {
                return part.title;
            }
            if (part.count == 2) {
                return (index == 0 ? "Left " : "Right ") + part.title.toLowerCase(java.util.Locale.ROOT);
            }
            if (part.count == 8) {
                return part.title + " " + (index + 1);
            }
            return CORNERS[index] + " " + part.title.toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final String[] CORNERS = {"Front left", "Front right", "Rear left", "Rear right"};
    public static final List<Slot> SLOTS;
    private static final int[] FIRST = new int[Part.values().length];

    static {
        List<Slot> slots = new ArrayList<>();
        for (Part p : Part.values()) {
            FIRST[p.ordinal()] = slots.size();
            for (int i = 0; i < p.count; i++) {
                slots.add(new Slot(p, i));
            }
        }
        SLOTS = List.copyOf(slots);
    }

    public static int slot(Part part, int index) {
        return FIRST[part.ordinal()] + index;
    }

    /** Whether a model carries parts at all: driven road vehicles and tractors, not aircraft, boats or wagons. */
    public static boolean applies(CarModels.Model m) {
        return m != null && !m.jet() && !m.boat() && !m.searchedOnly();
    }

    public static Kind kind(CarModels.Model m) {
        if (m == null) {
            return Kind.STANDARD;
        }
        return switch (m.id) {
            case "sports_coupe", "convertible", "coupe" -> Kind.SPORT;
            default -> switch (m.kind == null ? "car" : m.kind) {
                case "truck", "bus", "van", "emergency", "farm", "military" -> Kind.HEAVY;
                default -> "jeep".equals(m.id) || "jeep_open".equals(m.id) || "pickup".equals(m.id) || "suv".equals(m.id)
                        ? Kind.HEAVY : Kind.STANDARD;
            };
        };
    }

    /** The doors a model has: four on a four-seater car, two on the rest. */
    private static int doors(CarModels.Model m) {
        int seats = m.seats == null ? 0 : m.seats.length;
        return seats >= 4 && ("car".equals(m.kind) || "emergency".equals(m.kind)) ? 4 : 2;
    }

    /**
     * A fresh set of parts for a car found in the given overall condition (percent) and state: each part scattered
     * about the overall, wrecks missing some, burnt-out shells with little but the rims left.
     */
    public static String roll(CarModels.Model m, String variant, int condition, Random rng) {
        StringBuilder out = new StringBuilder(SLOTS.size());
        int doors = doors(m);
        boolean burnt = "burnt".equals(variant), wrecked = "wrecked".equals(variant);
        for (Slot s : SLOTS) {
            if (s.part == Part.DOOR && s.index >= doors || s.part == Part.SEAT && s.index >= (m.seats == null ? 0 : m.seats.length)
                    || s.part == Part.GLOVEBOX && m.glovebox == null) {
                out.append(NONE);
                continue;
            }
            int c;
            if (burnt) {
                c = s.part == Part.RIM ? 30 + rng.nextInt(40) : s.part == Part.ENGINE ? rng.nextInt(8) : rng.nextInt(4) == 0 ? rng.nextInt(10) : -1;
            } else {
                // parts wear at their own pace: tyres and plugs scatter widely, the block and the gearbox hold close
                int spread = switch (s.part) {
                    case TIRE, SPARK_PLUGS, BRAKES, BATTERY, OIL -> 40;
                    case ENGINE, GEARBOX, FUEL_TANK -> 15;
                    default -> 25;
                };
                c = Math.clamp(condition + rng.nextInt(spread * 2 + 1) - spread, 0, 100);
                if (wrecked && rng.nextInt(4) == 0 || !wrecked && c < 8 && rng.nextInt(3) == 0) {
                    c = -1;     // stripped by someone before you, or long gone
                }
                if (s.part == Part.ENGINE && !wrecked) {
                    c = Math.max(c, 20);    // a car left parked still has an engine in it
                }
            }
            out.append(c < 0 ? MISSING : (char) (FITTED_0 + c));
        }
        return out.toString();
    }

    /** A slot's condition 0-100, -1 missing, -2 not on this model. Short or empty strings read as perfect. */
    public static int get(String parts, int slot) {
        if (parts == null || slot >= parts.length()) {
            return 100;
        }
        char c = parts.charAt(slot);
        return c == NONE ? -2 : c == MISSING ? -1 : Math.clamp(c - FITTED_0, 0, 100);
    }

    public static int get(String parts, Part part, int index) {
        return get(parts, slot(part, index));
    }

    public static String set(String parts, int slot, int condition) {
        char[] chars = parts.toCharArray();
        if (slot < chars.length && chars[slot] != NONE) {
            chars[slot] = condition < 0 ? MISSING : (char) (FITTED_0 + Math.clamp(condition, 0, 100));
        }
        return new String(chars);
    }

    /** A part's working share, 0-1: missing or broken parts count 0, absent slots aren't asked. */
    private static double ok(String parts, Part part, int index) {
        int c = get(parts, part, index);
        return c < 0 ? 0 : c / 100.0;
    }

    private static double mean(String parts, Part part) {
        double sum = 0;
        for (int i = 0; i < part.count; i++) {
            sum += ok(parts, part, i);
        }
        return sum / part.count;
    }

    /** The overall condition shown for the car: a weighted mean of what matters most to running it. */
    public static int overall(String parts) {
        double v = 0.3 * ok(parts, Part.ENGINE, 0) + 0.1 * mean(parts, Part.TIRE) + 0.1 * mean(parts, Part.BRAKES)
                + 0.1 * mean(parts, Part.SUSPENSION) + 0.1 * ok(parts, Part.GEARBOX, 0) + 0.1 * ok(parts, Part.BATTERY, 0)
                + 0.05 * ok(parts, Part.SPARK_PLUGS, 0) + 0.05 * ok(parts, Part.STARTER, 0) + 0.05 * ok(parts, Part.RADIATOR, 0)
                + 0.05 * ok(parts, Part.FUEL_PUMP, 0);
        return (int) Math.round(v * 100);
    }

    /**
     * What the parts do to the drive, all as multipliers on a car in perfect order except the pulls:
     * power (acceleration), top (top speed), brake (stopping), grip (tyres holding the line), steer (how fast
     * the wheel answers), pull (drift to the left per tick, negative right, from flats and bent rims),
     * brakePull (the same, only while braking, from uneven brakes) and noise (how far the engine carries).
     */
    public record Drive(double power, double top, double brake, double grip, double steer, double pull, double brakePull, double noise) {
        public static final Drive PERFECT = new Drive(1, 1, 1, 1, 1, 0, 0, 1);
    }

    private static String cachedFor;
    private static Drive cached = Drive.PERFECT;

    public static synchronized Drive drive(String parts) {
        if (parts == null || parts.isEmpty()) {
            return Drive.PERFECT;
        }
        if (parts.equals(cachedFor)) {
            return cached;
        }
        double engine = ok(parts, Part.ENGINE, 0), gearbox = ok(parts, Part.GEARBOX, 0);
        double plugs = ok(parts, Part.SPARK_PLUGS, 0), pump = ok(parts, Part.FUEL_PUMP, 0), exhaust = ok(parts, Part.EXHAUST, 0);
        // a worn engine loses power and top end; bad plugs and a tired pump starve it on top of that
        double power = (0.35 + 0.65 * engine) * (0.5 + 0.5 * gearbox) * (plugs < 0.25 ? 0.8 : 1) * (pump < 0.25 ? 0.8 : 1);
        double top = (0.6 + 0.4 * engine) * (pump < 0.25 ? 0.85 : 1);
        double fluid = ok(parts, Part.BRAKE_FLUID, 0);
        double fluidK = fluid <= 0 ? 0.25 : fluid < 0.25 ? 0.6 : 1;
        double brake = Math.max(0.15, (0.3 + 0.7 * mean(parts, Part.BRAKES)) * fluidK);
        double left = ok(parts, Part.BRAKES, 0) + ok(parts, Part.BRAKES, 2), right = ok(parts, Part.BRAKES, 1) + ok(parts, Part.BRAKES, 3);
        double brakePull = (right - left) * 0.012;  // the side that bites harder drags the nose toward it
        double grip = 1, pull = 0;
        for (int i = 0; i < 4; i++) {
            int tyre = get(parts, Part.TIRE, i);
            double t = tyre < 0 ? 0 : tyre / 100.0;
            grip -= 0.08 * (1 - t);
            double side = i % 2 == 0 ? 1 : -1;
            if (tyre <= 0) {
                pull += side * 0.02;    // a flat or a bare rim drags that side
                top *= 0.82;
            }
            if (ok(parts, Part.RIM, i) < 0.25) {
                pull += side * 0.006;
            }
        }
        grip *= 0.75 + 0.25 * mean(parts, Part.SUSPENSION);
        double steer = 0.45 + 0.55 * ok(parts, Part.STEERING, 0);
        double noise = 1 + 2 * (1 - exhaust);   // a rotted-out exhaust carries three times as far
        cachedFor = parts;
        cached = new Drive(power, top, brake, grip, steer, pull, brakePull, noise);
        return cached;
    }

    /** Why it won't start right now, as the driver hears it (named only to someone who knows), or null if it catches. */
    public static String startFault(String parts, Random rng, boolean knows) {
        if (ok(parts, Part.BATTERY, 0) <= 0) {
            return knows ? "Nothing at all: no battery, or a dead one." : "Nothing. Not even a click.";
        }
        double starter = ok(parts, Part.STARTER, 0);
        if (starter <= 0 || starter < 0.25 && rng.nextDouble() < 0.4) {
            return knows ? "Click. Nothing turns over: the starter motor." : "Click. Nothing turns over.";
        }
        if (ok(parts, Part.ENGINE, 0) <= 0.05) {
            return knows ? "It turns over but never catches: the engine is shot." : "It turns over but never catches.";
        }
        if (ok(parts, Part.FUEL_PUMP, 0) <= 0 || ok(parts, Part.FUEL_TANK, 0) <= 0) {
            return knows ? "It cranks and cranks with no fuel getting through: the fuel pump or tank." : "It cranks and cranks, and nothing.";
        }
        double plugs = ok(parts, Part.SPARK_PLUGS, 0);
        if (plugs <= 0 || plugs < 0.25 && rng.nextDouble() < 0.35) {
            return knows ? "It coughs and dies: the spark plugs." : "It coughs and dies.";
        }
        return null;
    }

    /** The worst faults first, as one-line diagnoses for the mechanics screen (Mechanics 1 and up). */
    public static List<String> faults(String parts) {
        List<String> out = new ArrayList<>();
        for (Slot s : SLOTS) {
            int c = get(parts, SLOTS.indexOf(s));
            if (c == -1) {
                out.add(s.title() + ": missing");
            } else if (c >= 0 && c < 25) {
                out.add(s.title() + ": failing (" + c + "%)");
            }
        }
        return out;
    }

    /** The names a slot answers to on the command line: "tire_0" for one, "tire" for all four, "all". */
    public static List<String> names() {
        List<String> out = new ArrayList<>(List.of("all"));
        for (Part p : Part.values()) {
            String name = p.name().toLowerCase(java.util.Locale.ROOT);
            out.add(name);
            for (int i = 0; p.count > 1 && i < p.count; i++) {
                out.add(name + "_" + i);
            }
        }
        return out;
    }

    public static List<Integer> slots(String name) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < SLOTS.size(); i++) {
            Slot s = SLOTS.get(i);
            String part = s.part.name().toLowerCase(java.util.Locale.ROOT);
            if (name.equals("all") || name.equals(part) || name.equals(part + "_" + s.index)) {
                out.add(i);
            }
        }
        return out;
    }

    /** Every part and its condition on one block of text, worst first, then what that does to the drive. */
    public static String describe(String model, String parts) {
        StringBuilder out = new StringBuilder(model + ": overall " + overall(parts) + "%\n");
        for (int i = 0; i < SLOTS.size(); i++) {
            int c = get(parts, i);
            if (c != -2) {
                out.append(SLOTS.get(i).title()).append(' ').append(c < 0 ? "missing" : c + "%").append(i % 3 == 2 ? "\n" : ", ");
            }
        }
        Drive d = drive(parts);
        out.append(String.format(java.util.Locale.ROOT, "\nPower %.0f%%, top speed %.0f%%, brakes %.0f%%, grip %.0f%%, steering %.0f%%, pull %.3f, noise x%.1f",
                d.power() * 100, d.top() * 100, d.brake() * 100, d.grip() * 100, d.steer() * 100, d.pull(), d.noise()));
        return out.toString();
    }

    private CarParts() {
    }
}
