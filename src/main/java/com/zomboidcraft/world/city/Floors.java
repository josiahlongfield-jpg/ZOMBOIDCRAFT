package com.zomboidcraft.world.city;

import com.zomboidcraft.world.city.Floorplan.Room;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * What the floors of the city's buildings are for, and how each is fitted out on a
 * {@link Floorplan}.
 *
 * A building gets one purpose first (an insurance company's headquarters, a law and finance
 * tower, offices to let, a newspaper, a medical building, a hotel) and every floor follows from
 * it, the way a real building's tenants decide its floors: a headquarters has its departments'
 * open offices and cubicles, a records floor, the computer room, a staff cafeteria, a plant
 * floor in a tall one, the executives at the top. The floors of one kind never come out the
 * same twice: the rooms are cut to different sizes, the furniture falls differently, and each
 * floor is left in its own state (looted, barricaded, burnt, or just abandoned).
 *
 * Drawn from the photo library (refs/city/office-*, hotel-*, medical-clinic-floor,
 * mechanical-floor, nightclub) and how such floors are laid out: cellular offices along the
 * windows, the corridor ring, the service rooms (toilets, kitchen, copy room, stores) round the
 * core where there's no daylight.
 */
public final class Floors {
    private Floors() {
    }

    /** One floor's use. */
    public enum Type {
        OPEN_OFFICE, CUBICLES, CELLULAR, LAW, CALL_CENTRE, NEWSROOM, RECORDS, COMPUTERS, CAFETERIA, GYM, EXECUTIVE,
        SUITES, MEDICAL, DENTAL, GOVERNMENT, RADIO, RENOVATION, VACANT, PLANT, LAST_STAND,
        HOTEL_ROOMS, HOTEL_SUITES, HOTEL_SERVICE, HOTEL_DINING, HOTEL_SPA, NIGHTCLUB, PENTHOUSE,
        DORM, CARE
    }

    /** What a building is. */
    public enum Purpose {
        HEADQUARTERS, LAW_FINANCE, OFFICES_TO_LET, NEWSPAPER, MEDICAL_BUILDING, HOTEL, APARTMENTS, DORMITORY, CARE_HOME
    }

    /**
     * An office tower's purpose, by its height: hotels only in the tall ones, a newspaper or a
     * medical building only in the smaller.
     */
    public static Purpose officePurpose(SplittableRandom rng, int floors) {
        String only = System.getProperty("zc.purpose");  // previews
        if (only != null) {
            return Purpose.valueOf(only);
        }
        double r = rng.nextDouble();
        if (floors >= 12 && r < 0.22) {
            return Purpose.HOTEL;
        }
        r = rng.nextDouble();
        if (floors <= 14 && r < 0.12) {
            return Purpose.NEWSPAPER;
        }
        if (floors <= 12 && r < 0.24) {
            return Purpose.MEDICAL_BUILDING;
        }
        r = rng.nextDouble();
        return r < 0.4 ? Purpose.HEADQUARTERS : r < 0.65 ? Purpose.LAW_FINANCE : Purpose.OFFICES_TO_LET;
    }

    /** A block of flats' purpose: mostly homes, now and then a student hall or a care home. */
    public static Purpose flatsPurpose(SplittableRandom rng) {
        double r = rng.nextDouble();
        return r < 0.08 ? Purpose.DORMITORY : r < 0.16 ? Purpose.CARE_HOME : Purpose.APARTMENTS;
    }

    /**
     * The floors 1..n above the street (index 0 is unused: the street floor is the host's own),
     * from the building's purpose. No floor is the same as the one under it unless the purpose
     * is all one kind (a hotel's guest floors, a block of flats), and those vary in their cut.
     */
    public static Type[] program(Purpose purpose, int n, SplittableRandom rng) {
        Type[] t = new Type[n + 1];
        if (n <= 0) {
            return t;
        }
        String only = System.getProperty("zc.floors");  // previews: every floor one type
        if (only != null) {
            java.util.Arrays.fill(t, 1, n + 1, Type.valueOf(only));
            return t;
        }
        switch (purpose) {
            case HOTEL -> hotel(t, n, rng);
            case APARTMENTS -> fill(t, 1, n, rng, Type.HOTEL_ROOMS);  // the host lays flats; see isFlats
            case DORMITORY -> fill(t, 1, n, rng, Type.DORM);
            case CARE_HOME -> fill(t, 1, n, rng, Type.CARE);
            case MEDICAL_BUILDING -> {
                fill(t, 1, n, rng, Type.MEDICAL, Type.DENTAL, Type.MEDICAL);
                if (n >= 4) {
                    t[n] = Type.CELLULAR;  // the practice's offices and records
                }
            }
            case NEWSPAPER -> {
                fill(t, 1, n, rng, Type.NEWSROOM, Type.OPEN_OFFICE, Type.CELLULAR);
                put(t, Math.min(n, 2), Type.COMPUTERS);
                if (n >= 4) {
                    put(t, 1 + rng.nextInt(n - 1), Type.RADIO);
                    put(t, 1, Type.RECORDS);
                }
                t[n] = Type.EXECUTIVE;
            }
            case LAW_FINANCE -> {
                fill(t, 1, n, rng, Type.LAW, Type.CELLULAR, Type.LAW, Type.OPEN_OFFICE);
                if (n >= 6) {
                    put(t, 2 + rng.nextInt(Math.max(1, n / 3)), Type.RECORDS);
                }
                if (n >= 10) {
                    put(t, n / 2, Type.CAFETERIA);
                }
                t[n] = Type.EXECUTIVE;
                plant(t, n, rng);
            }
            case OFFICES_TO_LET -> {
                fill(t, 1, n, rng, Type.SUITES, Type.OPEN_OFFICE, Type.SUITES, Type.CELLULAR, Type.CUBICLES);
                List<Type> one = new ArrayList<>(List.of(Type.CALL_CENTRE, Type.MEDICAL, Type.DENTAL, Type.GOVERNMENT, Type.RENOVATION,
                        Type.VACANT, Type.GYM));
                Collections.shuffle(one, new java.util.Random(rng.nextLong()));
                int k = 2;
                for (Type o : one) {
                    if (k > n) {
                        break;
                    }
                    if (rng.nextDouble() < 0.6) {
                        put(t, k, o);
                    }
                    k += 2 + rng.nextInt(3);
                }
                plant(t, n, rng);
            }
            default -> {
                // a headquarters: its departments, the computer room, a records floor, the canteen, the gym
                fill(t, 1, n, rng, Type.OPEN_OFFICE, Type.CUBICLES, Type.OPEN_OFFICE, Type.CELLULAR);
                put(t, Math.min(n, 2), Type.COMPUTERS);
                if (n >= 5) {
                    put(t, 3 + rng.nextInt(Math.max(1, n / 4)), Type.RECORDS);
                }
                if (n >= 9) {
                    put(t, n / 3 + rng.nextInt(2), Type.CAFETERIA);
                }
                if (n >= 15) {
                    put(t, n / 2 + 1 + rng.nextInt(3), Type.GYM);
                }
                if (n >= 7 && rng.nextDouble() < 0.35) {
                    put(t, 1 + rng.nextInt(n - 1), Type.CALL_CENTRE);
                }
                t[n] = Type.EXECUTIVE;
                plant(t, n, rng);
            }
        }
        // where some held out: one floor in a few buildings
        if (n >= 3 && rng.nextDouble() < 0.12) {
            int k = 1 + rng.nextInt(n);
            if (t[k] != Type.PENTHOUSE && t[k] != Type.PLANT) {
                t[k] = Type.LAST_STAND;
            }
        }
        return t;
    }

    /** The guest floors and what a hotel tower needs round them. */
    private static void hotel(Type[] t, int n, SplittableRandom rng) {
        fill(t, 1, n, rng, Type.HOTEL_ROOMS);
        t[1] = Type.HOTEL_DINING;
        if (n >= 4) {
            t[2] = Type.HOTEL_SERVICE;
        }
        if (n >= 8) {
            t[3 + rng.nextInt(2)] = Type.HOTEL_SPA;
        }
        t[n] = Type.PENTHOUSE;
        if (n >= 12) {
            // a tall hotel's top three floors are one penthouse ({@link Penthouse}), the sky club under it
            t[n - 1] = Type.PENTHOUSE;
            t[n - 2] = Type.PENTHOUSE;
            t[n - 3] = Type.NIGHTCLUB;
            t[n - 4] = Type.HOTEL_SUITES;
            if (n >= 16) {
                t[n - 5] = Type.HOTEL_SUITES;
            }
            if (n >= 24) {
                t[n / 2] = Type.PLANT;
            }
            return;
        }
        if (n >= 6) {
            t[n - 1] = Type.NIGHTCLUB;
        }
        if (n >= 9) {
            t[n - 2] = Type.HOTEL_SUITES;
        }
    }

    /** A plant floor every 13 or so in a tall tower (not on top). */
    private static void plant(Type[] t, int n, SplittableRandom rng) {
        if (n < 20) {
            return;
        }
        for (int k = 12 + rng.nextInt(3); k < n - 1; k += 13 + rng.nextInt(3)) {
            // on a plain floor of offices, never over the canteen, the records or the like
            int at = k;
            while (at < n - 1 && !plain(t[at])) {
                at++;
            }
            if (at < n - 1) {
                t[at] = Type.PLANT;
            }
        }
    }

    private static boolean plain(Type v) {
        return v == Type.OPEN_OFFICE || v == Type.CUBICLES || v == Type.CELLULAR || v == Type.LAW || v == Type.SUITES || v == Type.HOTEL_ROOMS;
    }

    private static void put(Type[] t, int k, Type v) {
        if (k >= 1 && k < t.length) {
            t[k] = v;
        }
    }

    /** Floors a..b from the cycle, shuffled a little, never twice running unless only one kind is given. */
    private static void fill(Type[] t, int a, int b, SplittableRandom rng, Type... kinds) {
        Type last = null;
        for (int k = a; k <= b; k++) {
            Type v = kinds[rng.nextInt(kinds.length)];
            for (int i = 0; i < 4 && v == last && kinds.length > 1; i++) {
                v = kinds[rng.nextInt(kinds.length)];
            }
            t[k] = v;
            last = v;
        }
    }

    // ------------------------------------------------------------------ what each kind of room is like

    /** The floor laid in a room of a kind (null leaves the slab's own). */
    static String floorOf(String kind, SplittableRandom rng) {
        return switch (kind) {
            case "wc", "showers", "spa" -> "home_floor_tile";
            case "kitchen", "kitchen_big", "laundry", "lab", "xray", "exam", "dental", "ward", "care_room", "nurse", "linen",
                 "vending", "pharmacy" -> rng.nextBoolean() ? "sc_vct" : "home_floor_lino";
            case "server", "store", "copy", "boiler", "pumps", "generator", "workshop", "chiller", "cages", "armoury", "morgue" -> "floor_concrete";
            case "exec", "board", "library", "hotel_suite", "penthouse_bed", "penthouse_lounge" -> rng.nextBoolean() ? "home_floor_parquet" : "home_floor_carpet_brown";
            case "hotel_room" -> rng.nextBoolean() ? "home_floor_carpet_blue" : "home_floor_carpet_green";
            case "studio" -> "home_floor_carpet_brown";
            case "ph_bath", "ph_treat" -> rng.nextBoolean() ? "marble_white" : "pt_marble";
            case "ph_sauna" -> "of_cedar_wall";
            case "ph_kitchen", "ph_staff", "ph_juice" -> rng.nextBoolean() ? "lobby_floor_white" : "home_floor_tile";
            case "ph_gym" -> "of_rubber";
            case "ph_vault", "ph_cash", "ph_guns" -> "floor_concrete";
            case "ph_master", "ph_closet", "ph_dressing", "ph_cinema", "ph_cigar", "ph_champagne" -> rng.nextBoolean() ? "home_floor_carpet_brown" : "home_floor_carpet_blue";
            case "ph_study", "ph_billiards", "ph_cards", "ph_music", "ph_trophy", "ph_wine", "ph_tea", "ph_dining", "ph_bar", "ph_lounge" -> "home_floor_parquet";
            default -> null;
        };
    }

    static String doorOf(String kind) {
        return switch (kind) {
            case "server", "boiler", "pumps", "generator", "chiller", "store", "workshop", "armoury", "cages", "ph_vault", "ph_cash", "ph_guns" -> Doors.STEEL;
            case "meeting", "board", "reception", "waiting" -> Doors.SHOP;
            default -> Doors.FLAT;
        };
    }

    // ------------------------------------------------------------------ fitting a floor out

    /** How the floor's been left. */
    enum State { ABANDONED, LOOTED, BARRICADED, BURNT }

    /**
     * Plans floor type on f and fits it out. wallPaint is the drywall the building's partitions
     * are made of on this floor.
     */
    public static void fit(Floorplan f, Type type, SplittableRandom rng) {
        double s = rng.nextDouble();
        State state = s < 0.4 ? State.ABANDONED : s < 0.78 ? State.LOOTED : s < 0.9 ? State.BARRICADED : State.BURNT;
        new Fitter(f, type, state, rng).run();
        if (Boolean.getBoolean("zc.floordebug")) {
            StringBuilder b = new StringBuilder(type + " y" + f.y + "\n");
            for (int z = 0; z < f.d; z++) {
                for (int x = 0; x < f.w; x++) {
                    b.append(" o.#rIx@d".charAt(f.g[z * f.w + x]));
                }
                b.append('\n');
            }
            System.err.print(b);
        }
    }

    /**
     * One level of a hotel's penthouse ({@link Penthouse}): the rooms are given, rectangles of
     * open floor with their kinds; open says what fills the floor between them. The cells in
     * keepClear (x, z pairs) are kept free of furniture: the glass doors out to the terraces.
     */
    public static void penthouseLevel(Floorplan f, SplittableRandom rng, List<int[]> rects, List<String> kinds, String open, List<int[]> keepClear) {
        double s = rng.nextDouble();
        State state = s < 0.5 ? State.ABANDONED : s < 0.9 ? State.LOOTED : State.BARRICADED;
        Fitter ft = new Fitter(f, Type.PENTHOUSE, state, rng);
        ft.penthouseLevel(rects, kinds, open, keepClear);
        ft.aftermath();
        if (Boolean.getBoolean("zc.floordebug")) {
            StringBuilder b = new StringBuilder("PENTHOUSE " + open + " y" + f.y + "\n");
            for (int z = 0; z < f.d; z++) {
                for (int x = 0; x < f.w; x++) {
                    b.append(" o.#rIx@d".charAt(f.g[z * f.w + x]));
                }
                b.append('\n');
            }
            System.err.print(b);
        }
    }

    /** One floor being fitted out. */
    private static final class Fitter {
        final Floorplan f;
        final Type type;
        final State state;
        final SplittableRandom rng;
        final String wall;
        final String carpet;

        Fitter(Floorplan f, Type type, State state, SplittableRandom rng) {
            this.f = f;
            this.type = type;
            this.state = state;
            this.rng = rng;
            String[] walls = switch (type) {
                case HOTEL_ROOMS, HOTEL_SUITES, PENTHOUSE, DORM, CARE -> new String[] {"home_wall_cream", "home_wall_damask", "home_wall_stripe", "home_wall_sage"};
                case PLANT -> new String[] {"concrete_dark", "concrete_panel"};
                case LAW, EXECUTIVE -> new String[] {"wood_panel", "home_wall_panelling", "drywall"};
                default -> new String[] {"drywall", "drywall", "home_wall_cream"};
            };
            String w0 = walls[rng.nextInt(walls.length)];
            this.wall = state == State.BURNT && !w0.startsWith("concrete") && rng.nextBoolean() && w0.startsWith("home_wall") ? w0 + "_worn" : w0;
            String[] carpets = {"office_floor", "office_floor", "fit_carpet", "home_floor_carpet_blue", "home_floor_carpet_brown", "home_floor_carpet_green"};
            this.carpet = carpets[rng.nextInt(carpets.length)];
            f.airy = airy(type);
        }

        /**
         * Floors laid out open plan: no corridor ring, only a few rooms on the windows (glass
         * walled) so the floor reads as one big space out to the view. Bedrooms, flats, the
         * separate firms of a suites floor and the plant stay cut up into rooms.
         */
        static boolean airy(Type t) {
            return switch (t) {
                case OPEN_OFFICE, CELLULAR, LAW, CALL_CENTRE, NEWSROOM, COMPUTERS, CAFETERIA, GYM, EXECUTIVE, MEDICAL, DENTAL, GOVERNMENT, RADIO,
                        HOTEL_SERVICE, HOTEL_DINING, HOTEL_SPA, NIGHTCLUB -> true;
                default -> false;
            };
        }

        void run() {
            switch (type) {
                case OPEN_OFFICE -> openOffice(false);
                case CUBICLES -> openOffice(true);
                case CELLULAR -> cellular(false);
                case LAW -> cellular(true);
                case CALL_CENTRE -> benches(false);
                case NEWSROOM -> benches(true);
                case RECORDS -> records();
                case COMPUTERS -> computers();
                case CAFETERIA -> cafeteria();
                case GYM -> gym();
                case EXECUTIVE -> executive();
                case SUITES -> suites();
                case MEDICAL -> medical(false);
                case DENTAL -> medical(true);
                case GOVERNMENT -> government();
                case RADIO -> radio();
                case RENOVATION -> renovation();
                case VACANT -> vacant();
                case PLANT -> plant();
                case LAST_STAND -> lastStand();
                case HOTEL_ROOMS -> hotelRooms(false);
                case HOTEL_SUITES -> hotelRooms(true);
                case HOTEL_SERVICE -> hotelService();
                case HOTEL_DINING -> hotelDining();
                case HOTEL_SPA -> hotelSpa();
                case NIGHTCLUB -> nightclub();
                case PENTHOUSE -> penthouse();
                case DORM -> dorm();
                case CARE -> care();
                default -> openOffice(false);
            }
            aftermath();
        }

        // ---- the plan of a floor: corridor, rooms by the windows, rooms by the core

        /** A rotation of room kinds, picked in turn (with a few at random). */
        Floorplan.Kinds cycle(String... kinds) {
            return new Floorplan.Kinds() {
                int i = rng.nextInt(kinds.length);

                @Override
                public String next(Floorplan fp) {
                    return kinds[i++ % kinds.length];
                }
            };
        }

        /** Each kind once in turn, then the last one for the rest. */
        Floorplan.Kinds list(String rest, String... first) {
            return new Floorplan.Kinds() {
                int i;

                @Override
                public String next(Floorplan fp) {
                    return i < first.length ? first[i++] : rest;
                }
            };
        }

        /** Rooms round the core: the toilets, a kitchen, then what else the floor needs. */
        List<Room> core(int max, String... kinds) {
            return f.rooms(true, 7, 4, 7, list(kinds[kinds.length - 1], kinds), wall, true, max);
        }

        /** Rooms round the core: first each of first once, then the tail in turn for the rest of the inner floor. */
        List<Room> coreThen(int max, String[] first, String... tail) {
            Floorplan.Kinds k = new Floorplan.Kinds() {
                int i, j = rng.nextInt(tail.length);

                @Override
                public String next(Floorplan fp) {
                    return i < first.length ? first[i++] : tail[j++ % tail.length];
                }
            };
            // open plan: only the rooms the floor can't do without, the rest of the inner floor left
            // open, and the rooms people work in walled in glass
            List<Room> rs = f.rooms(true, 7, 4, 7, k, wall, true, f.airy ? Math.min(max, first.length) : max);
            if (f.airy) {
                for (Room r : rs) {
                    r.glass = Set.of("office", "meeting", "board", "exec", "library", "reception", "day_room", "studio").contains(r.kind);
                }
            }
            return rs;
        }

        List<Room> windows(int max, int min, int size, String... kinds) {
            if (!f.airy) {
                return f.rooms(false, f.band, min, size, cycle(kinds), wall, true, max);
            }
            // open plan: a handful of glass rooms on the windows, the rest of the glass left to the floor
            List<Room> rs = f.rooms(false, f.band, min, size, cycle(kinds), wall, true, Math.min(max, 3 + rng.nextInt(2)));
            for (Room r : rs) {
                r.glass = true;
            }
            return rs;
        }

        void furnish(List<Room> rs) {
            f.buildRooms();
            for (Room r : rs) {
                room(r);
            }
        }

        // ---- floor types

        void openOffice(boolean cubicles) {
            // a cubicle farm's own aisles are the way round; no ring through it
            f.corridor(4 + rng.nextInt(3), !cubicles);
            List<Room> rs = new ArrayList<>(core(5, "wc", "kitchen", "copy", "meeting", "store"));
            // a manager's office or two in the corners, glass meeting rooms
            rs.addAll(f.rooms(false, f.band, 4, 6, cycle("office", "meeting"), wall, true, 2 + rng.nextInt(3)));
            for (Room r : rs) {
                r.glass = r.kind.equals("meeting") || r.kind.equals("office") && rng.nextBoolean();
            }
            furnish(rs);
            if (cubicles) {
                cubicleFarm();
            } else {
                deskPods();
            }
            dotOpen(new String[] {"trade_water_cooler[facing=%s]", "pk_houseplant[facing=%s]", "pk_trash_can[facing=%s]", "pk_file_tall[facing=%s]",
                    "filing_cabinet[facing=%s]"}, 0.025);
            f.floors(carpet, carpet);
        }

        void cellular(boolean law) {
            f.corridor(4 + rng.nextInt(2), true);
            List<Room> rs = new ArrayList<>(coreThen(16, law ? new String[] {"wc", "kitchen", "library", "files", "board", "store"}
                    : new String[] {"wc", "kitchen", "files", "copy", "meeting", "store"}, law ? new String[] {"files", "meeting", "library", "copy"}
                    : new String[] {"meeting", "files", "office", "copy"}));
            rs.addAll(windows(60, 3, 4, law ? new String[] {"office", "office", "office", "exec"} : new String[] {"office", "office", "office", "meeting"}));
            for (Room r : rs) {
                r.glass |= !law && r.kind.equals("meeting");
            }
            furnish(rs);
            deskPods();
            f.floors(law ? "home_floor_carpet_brown" : "office_floor", law ? "home_floor_carpet_brown" : carpet);
        }

        void benches(boolean news) {
            f.corridor(5, true);
            List<Room> rs = new ArrayList<>(core(5, news ? new String[] {"wc", "kitchen", "studio", "library", "store"}
                    : new String[] {"wc", "kitchen", "server", "office", "store"}));
            rs.addAll(f.rooms(false, f.band, 5, 7, cycle("office", news ? "meeting" : "store"), wall, true, 2));
            for (Room r : rs) {
                r.glass = r.kind.equals("office") || r.kind.equals("meeting");
            }
            furnish(rs);
            // long benches of desks, a chair to each side, aisles between
            rowsOf(news ? 3 : 2, news ? 5 : 9, (x, z, along, side) -> {
                String desk = news && rng.nextDouble() < 0.4 ? "home_desk_pc" : "desk";
                return desk;
            });
            f.floors("office_floor", "office_floor");
        }

        void records() {
            f.corridor(3, true);
            List<Room> rs = new ArrayList<>(core(4, "wc", "kitchen", "office", "files"));
            rs.addAll(windows(6, 4, 7, "files", "office", "files", "copy"));
            furnish(rs);
            // the open floor stacked with archive shelving in rows
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (z % 3 == 0 && x % 6 != 0 && f.at(x, z) == Floorplan.OPEN) {
                    String b = pick("pk_file_tall", "filing_cabinet", "pk_metal_rack", "store_shelf_empty", "bunk_rack_boxes");
                    f.place(x, z, b + "[facing=" + Floorplan.face(rng.nextBoolean() ? 0 : 2) + "]", null);
                }
            }
            f.floors("floor_concrete_crack", "sc_vct");
        }

        void computers() {
            f.corridor(4, true);
            List<Room> rs = new ArrayList<>(core(4, "wc", "kitchen", "store", "server"));
            rs.addAll(windows(8, 4, 6, "office", "office", "meeting"));
            furnish(rs);
            // the computer hall: racks in rows on the raised floor, a console desk
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (z % 3 == 1 && x % 7 != 3 && f.at(x, z) == Floorplan.OPEN) {
                    f.place(x, z, "bunk_server[facing=" + Floorplan.face(z % 6 == 1 ? 0 : 2) + "]", null);
                }
            }
            dotOpen(new String[] {"bunk_switchboard[facing=%s]", "home_desk_pc[facing=%s]", "bunk_fusebox[facing=%s]"}, 0.03);
            f.floors("sc_vct", "bunk_floor");
        }

        void cafeteria() {
            f.corridor(3, true);
            List<Room> rs = new ArrayList<>(core(4, "wc", "kitchen_big", "store", "kitchen_big"));
            furnish(rs);
            // tables of four in rows by the windows
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (x % 4 == 1 && z % 4 == 1) {
                    if (f.place(x, z, "sc_cafe_table[axis=x]", null)) {
                        for (int[] n : Floorplan.N4) {
                            if (rng.nextDouble() < 0.8) {
                                int s = n[0] > 0 ? 3 : n[0] < 0 ? 1 : n[1] > 0 ? 0 : 2;
                                f.place(x + n[0], z + n[1], (rng.nextDouble() < 0.2 ? "home_chair_tipped" : "pk_chair_wooden") + "[facing=" + Floorplan.face(s) + "]", null);
                            }
                        }
                    }
                }
            }
            dotOpen(new String[] {"pk_vending[facing=%s]", "pk_trash_can[facing=%s]", "trade_water_cooler[facing=%s]"}, 0.03);
            f.floors("dn_checker", "dn_checker");
        }

        void gym() {
            // one hall: the machines in a row at the glass, free weights, the stretching mats
            f.corridor(3, false);
            List<Room> rs = new ArrayList<>(coreThen(8, new String[] {"wc", "showers", "showers", "reception"}, "store", "office"));
            furnish(rs);
            cardioRow();
            for (int i = 0; i < 6; i++) {
                // on a slim floor the weights may take in the walkway beside them
                Zone z = i % 3 == 1 ? this::mats : this::freeWeights;
                int min = 4, cells = i % 3 == 1 ? 5 : 7, max = i % 3 == 1 ? 9 : 11;
                if (!zone(min, cells, max, z) && !zone(min, cells, max, true, z)) {
                    break;
                }
            }
            release();
            dotOpen(new String[] {"trade_water_cooler[facing=%s]", "pk_trash_can[facing=%s]", "of_plate_tree[facing=%s]"}, 0.01);
            f.floors("of_rubber", "of_rubber");
        }

        interface Zone {
            void fill(int x0, int z0, int x1, int z1);
        }

        /** Fills the biggest clear patch left (min to max across) with fill, a cell of floor kept round it. */
        boolean zone(int min, int minCells, int max, Zone fill) {
            return zone(min, minCells, max, false, fill);
        }

        /** As above; overWalk lets the patch take in the walkway too (place() keeps the way through). */
        boolean zone(int min, int minCells, int max, boolean overWalk, Zone fill) {
            int[] b = f.biggest(v -> v == Floorplan.OPEN || overWalk && v == Floorplan.WALK, minCells, min, max, max);
            if (b == null) {
                return false;
            }
            fill.fill(b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1);
            // the patch is used: later zones look elsewhere (released when the floor is done)
            for (int z = b[1]; z <= b[3]; z++) {
                for (int x = b[0]; x <= b[2]; x++) {
                    if (f.at(x, z) == Floorplan.OPEN) {
                        f.put(x, z, Floorplan.CLEAR);
                        zoned.add(new int[] {x, z});
                    }
                }
            }
            return true;
        }

        final List<int[]> zoned = new ArrayList<>();

        /** Gives the zones' spare floor back to the open floor. */
        void release() {
            for (int[] c : zoned) {
                if (f.at(c[0], c[1]) == Floorplan.CLEAR) {
                    f.put(c[0], c[1], Floorplan.OPEN);
                }
            }
            zoned.clear();
        }

        /** Treadmills and bikes along the glass, each facing out at the view, a gap between pairs. */
        void cardioRow() {
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (f.dist[z * f.w + x] != 1 || Math.floorMod(x + z, 3) == 2) {
                    continue;
                }
                int s = facingOut(x, z);
                int[] back = Floorplan.step(x, z, s + 2);
                f.place(x, z, (Math.floorMod(x + z, 6) < 3 ? "of_treadmill" : "of_bike") + "[facing=" + Floorplan.face(s) + "]", back);
            }
        }

        /** Racks of dumbbells along the back of the patch, benches in front of them, a power rack each end. */
        void freeWeights(int x0, int z0, int x1, int z1) {
            for (int x = x0; x <= x1; x++) {
                f.place(x, z0, "of_dumbbells[facing=south]", null);
            }
            for (int x = x0 + 1; x <= x1 - 1; x += 3) {
                f.place(x, z0 + 3, "of_bench_press[facing=north]", null);
            }
            if (z1 - z0 >= 5) {
                f.place(x0, z1, "of_squat_rack[facing=north]", null);
                f.place(x1, z1, "of_squat_rack[facing=north]", null);
                f.place((x0 + x1) / 2, z1, "of_plate_tree[facing=north]", null);
            }
        }

        /** Stretching mats laid out in rows with room to walk between. */
        void mats(int x0, int z0, int x1, int z1) {
            for (int z = z0; z <= z1; z += 2) {
                for (int x = x0; x <= x1; x++) {
                    if (Math.floorMod(x - x0, 3) != 2) {
                        if (f.at(x, z) == Floorplan.OPEN) {
                            f.set(x, 1, z, "of_mat[facing=" + (rng.nextBoolean() ? "north" : "south") + "]");
                            f.set(x, 0, z, "of_rubber");
                            f.put(x, z, Floorplan.CLEAR);
                        }
                    }
                }
            }
        }

        void executive() {
            f.corridor(4 + rng.nextInt(2), true);
            List<Room> rs = new ArrayList<>(coreThen(14, new String[] {"wc", "kitchen", "board", "reception", "files"}, "office", "meeting", "library",
                    "copy"));
            rs.addAll(windows(60, 5, 8, "exec", "exec", "board", "office"));
            furnish(rs);
            // the open floor a lounge: sofas looking out of the glass, sitting groups between
            String sofa = pick("brown", "blue");
            byGlass(sofa);
            groups(6, 5, 7, i -> 0, sofa);
            dotOpen(new String[] {"pk_houseplant[facing=%s]", "home_armchair_brown[facing=%s]", "home_floor_lamp", "fit_painting_a[facing=%s]"}, 0.02);
            f.floors("home_floor_carpet_brown", "home_floor_parquet");
        }

        void suites() {
            f.corridor(5 + rng.nextInt(2), true);
            List<Room> rs = new ArrayList<>(coreThen(14, new String[] {"wc", "store", "kitchen"}, "office", "files", "store", "copy"));
            String[] firms = {"travel", "insurance", "tailor", "realtor", "pawn", "accountant", "dental", "typing", "office"};
            List<String> l = new ArrayList<>(List.of(firms));
            Collections.shuffle(l, new java.util.Random(rng.nextLong()));
            rs.addAll(windows(60, 5, 8, l.toArray(new String[0])));
            furnish(rs);
            f.floors("office_floor", "office_floor");
        }

        void medical(boolean dental) {
            f.corridor(4, true);
            List<Room> rs = new ArrayList<>(coreThen(16, new String[] {"wc", "pharmacy", "lab", "store", dental ? "xray" : "ward"},
                    dental ? new String[] {"dental", "files", "dental", "store"} : new String[] {"exam", "files", "exam", "nurse"}));
            rs.addAll(f.rooms(false, f.band, 6, 10, list(dental ? "dental" : "exam", "reception"), wall, true, 1));
            rs.addAll(windows(60, 3, 4, dental ? new String[] {"dental", "dental", "office"} : new String[] {"exam", "exam", "office", "exam"}));
            furnish(rs);
            waitingSeats("fit_chair_ward");
            dotOpen(new String[] {"fit_chair_ward[facing=%s]", "bunk_iv_stand", "pk_trash_can[facing=%s]"}, 0.02);
            f.floors("home_floor_lino", "home_floor_lino");
        }

        void government() {
            f.corridor(3, true);
            List<Room> rs = new ArrayList<>(coreThen(12, new String[] {"wc", "files", "kitchen", "store"}, "files", "office", "copy"));
            rs.addAll(windows(60, 4, 6, "office", "office", "files", "meeting"));
            furnish(rs);
            // a counter across the floor and benches for the queue in front of it, cubicles behind
            counterLine();
            cubicleFarm();
            f.floors("sc_vct", "sc_vct");
        }

        void radio() {
            f.corridor(4, true);
            List<Room> rs = new ArrayList<>(core(5, "wc", "kitchen", "server", "library", "store"));
            rs.addAll(windows(8, 4, 6, "studio", "studio", "office", "meeting"));
            for (Room r : rs) {
                r.glass |= r.kind.equals("studio");
            }
            furnish(rs);
            deskPods();
            f.floors(carpet, carpet);
        }

        void renovation() {
            f.corridor(4, true);
            List<Room> rs = new ArrayList<>(core(2, "wc", "store"));
            furnish(rs);
            for (int[] c : f.openCells()) {
                double r = rng.nextDouble();
                if (r < 0.03) {
                    f.place(c[0], c[1], pick("crate", "cardboard_box[facing=north]", "pk_toolbox[facing=north]", "trade_stepladder[facing=north]",
                            "trade_drop_cloth[facing=north]", "home_boxes", "store_paint_cans"), null);
                } else if (r < 0.05) {
                    f.set(c[0], 1, c[1], "rubble_pile");
                }
            }
            // stud walls half up where offices were going
            f.floors("floor_concrete", "floor_concrete_crack");
        }

        void vacant() {
            f.corridor(4, true);
            List<Room> rs = new ArrayList<>(core(2, "wc", "store"));
            furnish(rs);
            for (int[] c : f.openCells()) {
                double r = rng.nextDouble();
                if (r < 0.02) {
                    f.place(c[0], c[1], pick("cardboard_box[facing=east]", "office_chair_fallen[facing=west]", "trash_bag[facing=north]"), null);
                } else if (r < 0.05) {
                    f.set(c[0], 1, c[1], pick("moss_carpet", "glass_shards", "rubble_pile"));
                }
            }
            f.floors("floor_concrete", "floor_concrete_crack");
        }

        void plant() {
            f.corridor(3, true);
            List<Room> rs = new ArrayList<>(core(4, "workshop", "generator", "store", "pumps"));
            rs.addAll(windows(8, 6, 10, "boiler", "chiller", "pumps", "generator"));
            furnish(rs);
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if ((x + z) % 7 == 0 && rng.nextDouble() < 0.5) {
                    f.place(x, z, pick("hvac", "water_tank", "bunk_tank"), null);
                }
            }
            f.floors("floor_concrete", "floor_concrete_crack");
        }

        void lastStand() {
            f.corridor(4, true);
            List<Room> rs = new ArrayList<>(core(4, "wc", "armoury", "morgue", "command"));
            rs.addAll(windows(4, 5, 8, "command", "ward", "armoury"));
            furnish(rs);
            // cots in rows where the desks were, crates and the dead
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (x % 3 == 0 && z % 4 == 0) {
                    String s = Floorplan.face(2);
                    if (f.place(x, z, "sc_cot_head[facing=" + s + "]", null)) {
                        if (!f.place(x, z + 1, "sc_cot_foot[facing=" + s + "]", null)) {
                            f.set(x, 1, z, "air");
                            f.put(x, z, Floorplan.OPEN);
                        }
                    }
                } else if (rng.nextDouble() < 0.02) {
                    f.place(x, z, pick("pk_military_crate[facing=north]", "bunk_crate_ration[facing=north]", "bunk_bodybag[facing=east]",
                            "home_mattress_floor[facing=north]", "cardboard_box[facing=north]"), null);
                }
            }
            f.floors("bunk_floor_dirty", "bunk_floor_dirty");
        }

        void hotelRooms(boolean suites) {
            f.corridor(suites ? 11 : 9, true);
            List<Room> rs = new ArrayList<>(coreThen(10, new String[] {"linen", "vending", "store"}, "linen", "store", "vending"));
            rs.addAll(windows(40, suites ? 7 : 4, suites ? 10 : 5, suites ? "hotel_suite" : "hotel_room"));
            furnish(rs);
            f.floors(suites ? "home_floor_carpet_brown" : "home_floor_carpet_blue", "home_floor_carpet_blue");
        }

        void hotelService() {
            f.corridor(4, true);
            List<Room> rs = new ArrayList<>(coreThen(16, new String[] {"laundry", "linen", "store", "kitchen", "wc"}, "linen", "store", "laundry",
                    "workshop"));
            rs.addAll(windows(60, 5, 8, "office", "laundry", "linen", "kitchen", "store", "laundry"));
            furnish(rs);
            f.floors("sc_vct", "sc_vct");
        }

        void hotelDining() {
            f.corridor(3, true);
            List<Room> rs = new ArrayList<>(core(4, "kitchen_big", "kitchen_big", "wc", "store"));
            furnish(rs);
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (x % 3 == 1 && z % 3 == 1) {
                    if (f.place(x, z, rng.nextDouble() < 0.15 ? "dn_table_mess[facing=north]" : "dn_table_set[facing=north]", null)) {
                        for (int s : new int[] {1, 3}) {
                            int[] n = Floorplan.step(x, z, s);
                            if (rng.nextDouble() < 0.85) {
                                f.place(n[0], n[1], "pk_chair_dining[facing=" + Floorplan.face(s + 2) + "]", null);
                            }
                        }
                    }
                }
            }
            f.floors("home_floor_parquet", "home_floor_parquet");
        }

        void hotelSpa() {
            // the pool hall: a raised pool in the middle, loungers round it, treatment rooms and the gym off it
            f.corridor(3, false);
            // the pool hall first, so the rooms take what's left
            if (!zone(6, 9, 16, this::pool)) {
                zone(5, 7, 16, true, this::pool);
            }
            List<Room> rs = new ArrayList<>(coreThen(6, new String[] {"showers", "showers", "spa", "store", "reception"}, "spa", "linen"));
            rs.addAll(windows(4, 5, 8, "spa", "spa"));
            furnish(rs);
            release();
            cardioRow();
            dotOpen(new String[] {"of_lounger[facing=%s]", "pk_houseplant[facing=%s]", "of_lounger[facing=%s]"}, 0.04);
            f.floors("home_floor_tile", "home_floor_tile");
        }

        /** A pool on the slab: the coping round it a step up, the water in, a ladder, loungers along one side. */
        void pool(int x0, int z0, int x1, int z1) {
            int px0 = x0 + 1, pz0 = z0, px1 = x1 - 1, pz1 = z1 - 2;
            if (px1 - px0 < 3 || pz1 - pz0 < 3) {
                return;
            }
            for (int z = pz0; z <= pz1; z++) {
                for (int x = px0; x <= px1; x++) {
                    boolean rim = x == px0 || x == px1 || z == pz0 || z == pz1;
                    f.set(x, 0, z, "of_pool_tile");
                    f.set(x, 1, z, rim ? "of_coping" : "minecraft:water");
                    f.put(x, z, Floorplan.ITEM);
                }
            }
            for (int x = px0 + 1; x < px1; x += 3) {
                f.place(x, pz1 + 2, "of_lounger[facing=north]", null);
            }
        }

        void nightclub() {
            // the sky club: booths at the glass, the dance floor under the mirror ball, the DJ, the bar on the core
            f.corridor(2, false);
            List<Room> rs = new ArrayList<>(coreThen(4, new String[] {"wc", "wc", "store", "office"}, "store"));
            furnish(rs);
            // the dance floor as big as the floor allows: a slim top floor still gets one
            if (!zone(7, 9, 15, this::danceFloor) && !zone(6, 7, 15, this::danceFloor) && !zone(5, 6, 15, this::danceFloor)) {
                zone(5, 6, 15, true, this::danceFloor);  // the floor takes in the walkway
            }
            release();
            backBar();
            groups(4, 5, 7, i -> 0, pick("brown", "blue"));
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                int dd = f.dist[z * f.w + x];
                if (dd == 1 && rng.nextDouble() < 0.6) {
                    f.place(x, z, pick("dn_booth_r", "dn_booth_b") + "[facing=" + Floorplan.face(facingOut(x, z) + 2) + "]", null);
                } else if (dd == 3 && Math.floorMod(x + z, 4) == 0) {
                    if (f.place(x, z, "of_high_table[facing=north]", null)) {
                        int[] st = Floorplan.step(x, z, rng.nextInt(4));
                        f.place(st[0], st[1], "pk_bar_chair[facing=north]", null);
                    }
                } else if (dd > 4 && rng.nextDouble() < 0.01) {
                    f.place(x, z, pick("trade_neon_beer[facing=north]", "pk_trash_can[facing=north]", "trade_rope_post[axis=x]"), null);
                }
            }
            f.floors("lobby_floor_dark", "lobby_floor_dark");
        }

        /** The light-up floor, a speaker stack at each corner, the DJ booth facing it from one side, the ball over the middle. */
        void danceFloor(int x0, int z0, int x1, int z1) {
            // the DJ's side: the booth on the north edge facing south over the floor, the DJ behind it
            int bz = z0, fz0 = z0 + 1;
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                for (int z = fz0; z <= z1; z++) {
                    String k = "abcd".charAt(Math.floorMod(x * 3 + z * 5, 4)) + (rng.nextDouble() < 0.3 ? "" : "_off");
                    f.set(x, 0, z, "of_dance_" + k);
                    f.put(x, z, Floorplan.CLEAR);
                }
            }
            int cx = (x0 + x1) / 2;
            for (int x = cx - 1; x <= cx + 1; x++) {
                f.place(x, bz, "of_dj_booth[facing=south]", null);
            }
            for (int[] c : new int[][] {{x0, fz0}, {x1, fz0}, {x0, z1}, {x1, z1}}) {
                f.place(c[0], c[1], "of_speaker[facing=" + (c[1] == fz0 ? "south" : "north") + "]", null);
            }
            f.set(cx, f.rows, (fz0 + z1) / 2, "of_mirrorball");
        }

        void penthouse() {
            // one home: the great room open from the lift to the glass, the private rooms round it
            // a slim top floor (stepped in for the terrace) keeps its rooms shallow by the glass
            boolean slim = f.deepest() < 8;
            f.corridor(slim ? 3 : 8, false);
            if (slim) {
                f.band = 3;
            }
            // open plan: the powder room and the wine store by the core, the master suite (and on a
            // deep floor one guest room) on the glass, everything else one great room out to the view
            f.airy = true;
            List<Room> rs = new ArrayList<>(core(2, "wc", "ph_wine"));
            rs.addAll(f.rooms(false, f.band, slim ? 4 : 6, slim ? 7 : 10, list("penthouse_bed", "ph_master"), wall, true, slim ? 1 : 2));
            furnish(rs);
            // the great room: sitting groups under chandeliers, the long dining table, the grand
            // piano by the glass, the bar, sculpture on plinths between
            String sofa = pick("brown", "blue", "floral");
            byGlass(sofa);
            groups(16, 4, 9, i -> switch (i % 6) {
                case 0, 3 -> 0;
                case 1 -> 1;
                case 2 -> 4;
                case 4 -> 3;
                default -> 5;
            }, sofa);
            f.floors("marble_white", "marble_white");
        }

        /** One level of a big penthouse: the given rooms, then the open floor filled by its use. */
        void penthouseLevel(List<int[]> rects, List<String> kinds, String open, List<int[]> keepClear) {
            List<Room> rs = new ArrayList<>();
            for (int i = 0; i < rects.size(); i++) {
                int[] q = rects.get(i);
                Room r = f.room(q[0], q[1], q[2], q[3], kinds.get(i), wall(kinds.get(i)));
                if (r == null) {
                    continue;
                }
                r.glass = kinds.get(i).equals("ph_study") || kinds.get(i).equals("ph_gym") || kinds.get(i).equals("ph_cards");
                rs.add(r);
            }
            for (Room r : new ArrayList<>(rs)) {
                if (!f.door(r, true) && !f.door(r, false)) {
                    // boxed in among the others: fixDoors / joinUp give it a way through a neighbour
                    r.dx = -1;
                }
            }
            for (int[] c : keepClear) {
                byte v = f.at(c[0], c[1]);
                if (v == Floorplan.ROOM || v == Floorplan.OPEN) {
                    f.put(c[0], c[1], Floorplan.CLEAR);
                }
            }
            furnish(rs);
            String sofa = pick("brown", "blue", "floral");
            switch (open) {
                case "club" -> {
                    if (!zone(7, 9, 15, this::danceFloor) && !zone(6, 7, 15, this::danceFloor)) {
                        zone(5, 6, 15, this::danceFloor);
                    }
                    release();
                    backBar();
                    booths("dn_booth_r", "dn_booth_b");
                    groups(4, 5, 7, i -> 0, sofa);
                    f.floors("lobby_floor_dark", "lobby_floor_dark");
                }
                case "velvet" -> {
                    backBar();
                    booths("of_booth", "of_booth_purple");
                    highTables();
                    groups(3, 5, 7, i -> 3, sofa);
                    f.floors("lobby_floor_dark", "lobby_floor_dark");
                }
                case "bar" -> {
                    backBar();
                    highTables();
                    groups(3, 5, 7, i -> 0, sofa);
                    f.floors("lobby_floor_dark", "lobby_floor_dark");
                }
                case "gallery" -> {
                    byGlass(sofa);
                    groups(12, 4, 7, i -> i % 3 == 2 ? 0 : 5, sofa);
                    f.floors("marble_white", "marble_white");
                }
                case "cars", "pool", "garden" -> {
                    groups(3, 5, 8, i -> i == 1 ? 3 : 0, sofa);
                    dotOpen(new String[] {"pk_houseplant[facing=%s]", "of_tree_pot", "of_lounger[facing=%s]"}, open.equals("pool") ? 0.05 : 0.015);
                    f.floors(open.equals("pool") ? "home_floor_tile" : "pt_marble", open.equals("pool") ? "home_floor_tile" : "pt_marble");
                }
                case "lounge" -> {
                    byGlass(sofa);
                    groups(8, 4, 8, i -> i % 4 == 3 ? 3 : 0, sofa);
                    f.floors("home_floor_parquet", "home_floor_parquet");
                }
                default -> {
                    // the great room: sitting groups under chandeliers, the long table, the piano, the bar, sculpture
                    byGlass(sofa);
                    groups(16, 4, 9, i -> switch (i % 6) {
                        case 0, 3 -> 0;
                        case 1 -> 1;
                        case 2 -> 4;
                        case 4 -> 3;
                        default -> 5;
                    }, sofa);
                    f.floors("marble_white", "marble_white");
                }
            }
            finishFloor(open);
        }

        /**
         * The penthouse's floor detail: a dark marble border along the glass and the walls, a
         * diamond inlay across the open marble, rugs under the furniture, lamps and plants and
         * urns dotted about, a chandelier now and then.
         */
        void finishFloor(String open) {
            boolean marble = !open.equals("club") && !open.equals("velvet") && !open.equals("bar") && !open.equals("pool");
            for (int i = 0; i < f.g.length; i++) {
                int x = i % f.w, z = i / f.w;
                byte v = f.g[i];
                boolean openish = v == Floorplan.OPEN || v == Floorplan.WALK || v == Floorplan.CLEAR || v == Floorplan.ITEM && !inRoom(x, z);
                if (!openish || !marble) {
                    continue;
                }
                if (f.dist[i] == 1) {
                    f.p.set(x, f.y, z, "lobby_floor_dark");
                } else if (Math.floorMod(x + z, 6) == 0 || Math.floorMod(x - z, 6) == 0) {
                    f.p.set(x, f.y, z, "pt_marble");
                }
            }
            String[] rug = open.equals("velvet") ? new String[] {"of_carpet_red"} : new String[] {"of_carpet_red", "home_floor_carpet_blue", "home_floor_carpet_brown"};
            for (int[] r : rugs) {
                String b = rug[rng.nextInt(rug.length)];
                for (int x = r[0]; x <= r[2]; x++) {
                    for (int z = r[1]; z <= r[3]; z++) {
                        byte v = f.at(x, z);
                        if ((v == Floorplan.OPEN || v == Floorplan.ITEM || v == Floorplan.CLEAR) && !inRoom(x, z)) {
                            f.p.set(x, f.y, z, b);
                        }
                    }
                }
            }
            dotOpen(new String[] {"home_floor_lamp", "pk_houseplant[facing=%s]", "mu_urn", "of_tree_pot", "pk_sideboard[facing=%s]"}, 0.02);
            for (int[] c : f.openCells()) {
                if (Math.floorMod(c[0], 6) == 3 && Math.floorMod(c[1], 6) == 3 && (f.p.get(c[0], f.y + f.rows, c[1]) == null || "air".equals(f.p.get(c[0], f.y + f.rows, c[1])))) {
                    f.set(c[0], f.rows, c[1], rng.nextDouble() < f.lit + 0.2 ? "tr_chandelier_on" : "tr_chandelier");
                }
            }
        }

        boolean inRoom(int x, int z) {
            for (Room r : f.rooms) {
                if (r.has(x, z)) {
                    return true;
                }
            }
            return false;
        }

        /** A room's walls: the sauna lined in cedar, the club rooms in velvet, the vault in concrete. */
        String wall(String kind) {
            return switch (kind) {
                case "ph_sauna" -> "of_cedar_wall";
                case "ph_champagne", "ph_cigar" -> "of_velvet_wall";
                case "ph_vault", "ph_cash" -> "concrete_dark";
                case "ph_study", "ph_billiards", "ph_cards", "ph_trophy", "ph_guns", "library" -> "home_wall_panelling";
                case "ph_bath" -> "marble_white";
                default -> wall;
            };
        }

        /** Booths back to the glass looking into the room. */
        void booths(String a, String b) {
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (f.dist[z * f.w + x] == 1 && Math.floorMod(x + z, 3) != 0 && rng.nextDouble() < 0.7) {
                    f.place(x, z, pick(a, b) + "[facing=" + Floorplan.face(facingOut(x, z) + 2) + "]", null);
                } else if (f.dist[z * f.w + x] == 1 && rng.nextDouble() < 0.4) {
                    f.place(x, z, "of_champagne", null);
                }
            }
        }

        /** High tables with stools, a few steps in from the glass. */
        void highTables() {
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (f.dist[z * f.w + x] == 3 && Math.floorMod(x + z, 4) == 0 && f.place(x, z, "of_high_table[facing=north]", null)) {
                    int[] st = Floorplan.step(x, z, rng.nextInt(4));
                    f.place(st[0], st[1], "pk_bar_chair[facing=north]", null);
                }
            }
        }

        void dorm() {
            f.corridor(8, true);
            List<Room> rs = new ArrayList<>(coreThen(14, new String[] {"showers", "kitchen", "showers", "laundry", "day_room"}, "library", "store",
                    "day_room", "laundry"));
            rs.addAll(windows(30, 4, 5, "bunks"));
            furnish(rs);
            f.floors("sc_vct", "sc_vct");
        }

        void care() {
            f.corridor(8, true);
            List<Room> rs = new ArrayList<>(coreThen(14, new String[] {"nurse", "showers", "kitchen", "linen", "pharmacy"}, "day_room", "linen",
                    "store", "showers"));
            rs.addAll(windows(30, 4, 5, "care_room", "care_room", "care_room", "day_room"));
            furnish(rs);
            dotOpen(new String[] {"fit_chair_ward[facing=%s]", "bunk_iv_stand", "pk_houseplant[facing=%s]"}, 0.02);
            f.floors("home_floor_lino", "home_floor_lino");
        }

        // ---- open floor fills

        /**
         * Desks in long back-to-back pairs across the open floor, chairs either side, a cross aisle
         * every few desks; the rows run along whichever way the floor is longer.
         */
        void deskPods() {
            boolean alongX = longWay();
            int run = 3 + rng.nextInt(3), off = rng.nextInt(4);
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                int a = alongX ? x : z, b = alongX ? z : x;
                int m = Math.floorMod(b + off, 5);
                if (m != 1 && m != 2 || Math.floorMod(a, run + 1) == run) {
                    continue;
                }
                // the desk faces its sitter: the north desk of a pair has its chair to the north
                int toward = alongX ? (m == 1 ? 0 : 2) : (m == 1 ? 3 : 1);
                int[] seat = Floorplan.step(x, z, toward);
                if (f.at(seat[0], seat[1]) != Floorplan.OPEN && f.at(seat[0], seat[1]) != Floorplan.WALK) {
                    continue;
                }
                String desk = rng.nextDouble() < 0.35 ? "home_desk_pc" : "desk";
                if (f.place(x, z, desk + "[facing=" + Floorplan.face(toward) + "]", null) && rng.nextDouble() < 0.85
                        && f.at(seat[0], seat[1]) == Floorplan.OPEN) {
                    f.place(seat[0], seat[1], chair(toward + 2), null);
                }
            }
        }

        /** Whether the open floor runs further along x than along z. */
        boolean longWay() {
            int x0 = f.w, x1 = 0, z0 = f.d, z1 = 0;
            for (int[] c : f.openCells()) {
                x0 = Math.min(x0, c[0]);
                x1 = Math.max(x1, c[0]);
                z0 = Math.min(z0, c[1]);
                z1 = Math.max(z1, c[1]);
            }
            return x1 - x0 >= z1 - z0;
        }

        /**
         * Cubicles: double rows of them back to back across the open floor, a shared panel down the
         * middle and a divider between each, each one open onto the aisle with its desk against the
         * back panel; four to a run, then a cross aisle.
         */
        void cubicleFarm() {
            boolean alongX = longWay();
            int offA = rng.nextInt(19), offB = rng.nextInt(7);
            mine = new boolean[f.g.length];
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                int a = alongX ? x : z, b = alongX ? z : x;
                int pa = Math.floorMod(a + offA, 19), pb = Math.floorMod(b + offB, 7);
                // one cubicle at its corner: the divider cell on the run, the panel row of the pair
                if (pa % 4 != 0 || pa > 12 || pb != 1) {
                    continue;
                }
                for (int side = 0; side < 2; side++) {
                    cubicle(alongX, a, b, side, x, z);
                }
            }
        }

        /** One cubicle 3 across (a+1..a+3) by 2 deep, its back panel on row b+2, side 0 the near half, 1 the far. */
        void cubicle(boolean alongX, int a, int b, int side, int x0, int z0) {
            int back = b + 2, open = side == 0 ? b : b + 4, desk = side == 0 ? b + 1 : b + 3;
            int bLo = Math.min(open, back), bHi = Math.max(open, back);
            for (int aa = a; aa <= a + 4; aa++) {
                for (int bb = bLo; bb <= bHi; bb++) {
                    int[] q = cell(alongX, aa, bb);
                    if (f.at(q[0], q[1]) != Floorplan.OPEN && !(f.in(q[0], q[1]) && mine[q[1] * f.w + q[0]])) {
                        return;
                    }
                }
            }
            String panel = "cubicle_wall[facing=" + (alongX ? "north" : "east") + "]";
            String divider = "cubicle_wall[facing=" + (alongX ? "east" : "north") + "]";
            // the shared back panel (laid once, by the near side), the dividers at either end
            for (int aa = a; aa <= a + 4; aa++) {
                int[] q = cell(alongX, aa, back);
                if (f.at(q[0], q[1]) == Floorplan.OPEN && f.place(q[0], q[1], panel, null)) {
                    mine[q[1] * f.w + q[0]] = true;
                }
            }
            for (int aa : new int[] {a, a + 4}) {
                int[] q = cell(alongX, aa, desk);
                if (f.at(q[0], q[1]) == Floorplan.OPEN && f.place(q[0], q[1], divider, null)) {
                    mine[q[1] * f.w + q[0]] = true;
                }
            }
            // the desk against the back panel facing its sitter, the chair, a cabinet in the corner
            int toward = alongX ? (side == 0 ? 0 : 2) : (side == 0 ? 3 : 1);
            int mid = a + 1 + rng.nextInt(3);
            int[] dq = cell(alongX, mid, desk), sq = cell(alongX, mid, open);
            String d = rng.nextDouble() < 0.55 ? "home_desk_pc" : "desk";
            if (f.place(dq[0], dq[1], d + "[facing=" + Floorplan.face(toward) + "]", null) && rng.nextDouble() < 0.8) {
                f.place(sq[0], sq[1], chair(toward + 2), null);
            }
            if (rng.nextDouble() < 0.5) {
                int ca = mid == a + 1 ? a + 3 : a + 1;
                int[] cq = cell(alongX, ca, desk);
                f.place(cq[0], cq[1], pick("filing_cabinet", "pk_file_cabinet", "pk_houseplant", "pk_trash_can") + "[facing=" + Floorplan.face(toward) + "]", null);
            }
        }

        boolean[] mine;
        /** Rugs laid under the furniture groups (x0, z0, x1, z1), painted into the floor once it's down. */
        final List<int[]> rugs = new ArrayList<>();

        interface GroupPick {
            int kind(int i);
        }

        /**
         * Furniture in groups over the open floor, each in the biggest clear patch left (at least
         * min across, at most max): 0 a sitting group, 1 a dining table, 2 the piano, 3 a bar.
         */
        void groups(int count, int min, int max, GroupPick pick, String sofa) {
            List<int[]> used = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int[] b = f.biggest(v -> v == Floorplan.OPEN, min, min, max, max);
                if (b == null) {
                    break;
                }
                // a cell of floor all round the group so the patches join up
                int x0 = b[0] + 1, z0 = b[1] + 1, x1 = b[2] - 1, z1 = b[3] - 1;
                switch (pick.kind(i)) {
                    case 0 -> sitting(x0, z0, x1, z1, sofa);
                    case 1 -> dining(x0, z0, x1, z1);
                    case 2 -> piano(x0, z0, x1, z1);
                    case 4 -> grandPiano(x0, z0, x1, z1);
                    case 5 -> gallery(x0, z0, x1, z1);
                    default -> bar(x0, z0, x1, z1);
                }
                for (int z = b[1]; z <= b[3]; z++) {
                    for (int x = b[0]; x <= b[2]; x++) {
                        if (f.at(x, z) == Floorplan.OPEN) {
                            f.put(x, z, Floorplan.CLEAR);
                            used.add(new int[] {x, z});
                        }
                    }
                }
            }
            for (int[] c : used) {
                if (f.at(c[0], c[1]) == Floorplan.CLEAR) {
                    f.put(c[0], c[1], Floorplan.OPEN);
                }
            }
        }

        /** A three-piece sofa facing south across a coffee table at two armchairs, a lamp, a plant. */
        void sitting(int x0, int z0, int x1, int z1, String sofa) {
            int cx = (x0 + x1) / 2;
            rugs.add(new int[] {cx - 2, z0, cx + 2, z1});
            boolean flip = rng.nextBoolean();
            int sz = flip ? z1 : z0, tz = flip ? z1 - 2 : z0 + 2, az = flip ? z1 - 4 : z0 + 4;
            String toward = flip ? "north" : "south", back = flip ? "south" : "north";
            // the sofa's _l at the sitter's left: west when facing south is the high x
            String lo = flip ? "_l" : "_r", hi = flip ? "_r" : "_l";
            f.place(cx - 1, sz, "home_sofa_" + sofa + lo + "[facing=" + toward + "]", null);
            f.place(cx, sz, "home_sofa_" + sofa + "_m[facing=" + toward + "]", null);
            f.place(cx + 1, sz, "home_sofa_" + sofa + hi + "[facing=" + toward + "]", null);
            f.place(cx, tz, "pk_coffee_table[facing=north]", null);
            if (Math.abs(az - sz) <= Math.abs(z1 - z0)) {
                f.place(cx - 1, az, "home_armchair_" + sofa + "[facing=" + back + "]", null);
                f.place(cx + 1, az, "home_armchair_" + sofa + "[facing=" + back + "]", null);
            }
            f.place(cx - 2, sz, "home_lamp_table[facing=north]", null);
            f.place(cx + 2, sz, pick("pk_houseplant", "pk_planter") + "[facing=north]", null);
        }

        /** A long dining table down the patch, chairs either side. */
        void dining(int x0, int z0, int x1, int z1) {
            int cz = (z0 + z1) / 2;
            rugs.add(new int[] {x0, cz - 1, x1, cz + 1});
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                if (f.place(x, cz, "pk_table_wooden[facing=north]", null)) {
                    f.place(x, cz - 1, (rng.nextDouble() < 0.15 ? "home_chair_tipped" : "pk_chair_dining") + "[facing=south]", null);
                    f.place(x, cz + 1, (rng.nextDouble() < 0.15 ? "home_chair_tipped" : "pk_chair_dining") + "[facing=north]", null);
                }
            }
            f.place(x0, cz, "pk_chair_dining[facing=east]", null);
            f.place(x0 + 1, z0, "pk_sideboard[facing=south]", null);
        }

        /** The grand piano and its stool, a pair of armchairs to listen from. */
        void piano(int x0, int z0, int x1, int z1) {
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            if (f.place(cx, cz, "home_piano_l[facing=south]", null)) {
                f.place(cx - 1, cz, "home_piano_r[facing=south]", null);
                f.place(cx, cz + 1, "home_chair[facing=north]", null);
            }
            f.place(x0, z1, "home_armchair_brown[facing=north]", null);
            f.place(x1, z1, "home_armchair_brown[facing=north]", null);
        }

        /**
         * The great room along the outside wall (the glass, once the facade is up), every seven blocks or so: a sofa looking out over a
         * coffee table with armchairs either side, the grand piano, a dining table for six, a
         * sculpture; a chandelier over each. What's left behind them is the way round.
         */
        void byGlass(String sofa) {
            List<int[]> spots = new ArrayList<>();
            for (int[] c : f.openCells()) {
                int side = -1, n = 0;
                for (int s = 0; s < 4; s++) {
                    int[] o = Floorplan.step(c[0], c[1], s);
                    if (!f.in(o[0], o[1]) || f.at(o[0], o[1]) == Floorplan.SHELL) {
                        side = s;
                        n++;
                    }
                }
                if (n == 1) {
                    spots.add(new int[] {c[0], c[1], side});
                }
            }
            spots.sort((a, b) -> a[2] != b[2] ? a[2] - b[2] : (a[2] % 2 == 0 ? a[0] - b[0] : a[1] - b[1]));
            int last = Integer.MIN_VALUE, lastSide = -1, i = rng.nextInt(4);
            for (int[] c : spots) {
                int along = c[2] % 2 == 0 ? c[0] : c[1];
                if (c[2] == lastSide && along - last < 7) {
                    continue;
                }
                int x = c[0], z = c[1], s = c[2];
                int[] back = Floorplan.step(x, z, s + 2), back2 = Floorplan.step(back[0], back[1], s + 2);
                if (f.at(back[0], back[1]) != Floorplan.OPEN || f.at(back2[0], back2[1]) != Floorplan.OPEN) {
                    continue;
                }
                last = along;
                lastSide = s;
                switch (i++ % 4) {
                    case 0, 2 -> viewSofa(x, z, s, sofa);
                    case 1 -> {
                        // the piano side on to the glass, its bench to the room
                        if (f.place(x, z, "of_grand_piano_l[facing=" + Floorplan.face(s + 2) + "]", null)) {
                            int[] r = Floorplan.step(x, z, s + 3);
                            f.place(r[0], r[1], "of_grand_piano_r[facing=" + Floorplan.face(s + 2) + "]", null);
                            f.place(back[0], back[1], "home_chair[facing=" + Floorplan.face(s) + "]", null);
                        }
                    }
                    default -> {
                        f.place(x, z, "of_sculpture[facing=" + Floorplan.face(s + 2) + "]", null);
                        int[] l = Floorplan.step(x, z, s + 1), r = Floorplan.step(x, z, s + 3);
                        f.place(l[0], l[1], pick("pk_houseplant", "pk_planter") + "[facing=north]", null);
                        f.place(r[0], r[1], "home_floor_lamp", null);
                    }
                }
                f.set(back[0], f.rows, back[1], "tr_chandelier");
            }
        }

        /** A sofa looking out of the glass at s, the coffee table before it, armchairs at its ends. */
        void viewSofa(int x, int z, int s, String sofa) {
            String look = Floorplan.face(s);
            int[] m = Floorplan.step(x, z, s + 2);
            int[] l = Floorplan.step(m[0], m[1], s + 3), r = Floorplan.step(m[0], m[1], s + 1);
            if (!f.place(m[0], m[1], "home_sofa_" + sofa + "_m[facing=" + look + "]", null)) {
                return;
            }
            f.place(l[0], l[1], "home_sofa_" + sofa + "_l[facing=" + look + "]", null);
            f.place(r[0], r[1], "home_sofa_" + sofa + "_r[facing=" + look + "]", null);
            f.place(x, z, "pk_coffee_table[facing=north]", null);
            int[] al = Floorplan.step(x, z, s + 3), ar = Floorplan.step(x, z, s + 1);
            f.place(al[0], al[1], "home_armchair_" + sofa + "[facing=" + Floorplan.face(s + 1) + "]", null);
            f.place(ar[0], ar[1], "home_armchair_" + sofa + "[facing=" + Floorplan.face(s + 3) + "]", null);
        }

        /** A grand piano with its bench, a chandelier over it, two armchairs to listen from. */
        void grandPiano(int x0, int z0, int x1, int z1) {
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            rugs.add(new int[] {cx - 2, cz - 1, cx + 1, cz + 1});
            if (f.place(cx, cz, "of_grand_piano_l[facing=south]", null)) {
                f.place(cx - 1, cz, "of_grand_piano_r[facing=south]", null);
                f.place(cx, cz + 1, "home_chair[facing=north]", null);
                f.set(cx, f.rows, cz, "tr_chandelier");
            }
            f.place(x0, z0, "home_armchair_brown[facing=south]", null);
            f.place(x1, z0, "home_armchair_brown[facing=south]", null);
        }

        /** Sculpture on plinths in the open, a bench to look at them from, a chandelier. */
        void gallery(int x0, int z0, int x1, int z1) {
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            f.place(x0, z0, "of_sculpture[facing=south]", null);
            f.place(x1, z1, "of_sculpture[facing=north]", null);
            f.place(cx, cz, "cv_hall_bench[facing=north]", null);
            f.place(x1, z0, pick("pk_houseplant", "pk_planter") + "[facing=north]", null);
            f.set(cx, f.rows, cz, "tr_chandelier");
        }

        /** A short bar with the stools in front, the drinks shelf behind. */
        void bar(int x0, int z0, int x1, int z1) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                f.place(x, z0, pick("pk_shelf_liquor_1", "pk_shelf_liquor_2", "pk_shelf_liquor_3") + "[facing=south]", null);
                if (f.place(x, z0 + 2, "trade_bar[facing=south]", null)) {
                    f.place(x, z0 + 3, "pk_bar_chair[facing=north]", null);
                }
            }
        }

        int[] cell(boolean alongX, int a, int b) {
            return alongX ? new int[] {a, b} : new int[] {b, a};
        }

        /** Rows of waiting-room chairs back to back over what open floor is left, a table and a plant here and there. */
        void waitingSeats(String seat) {
            boolean alongX = !longWay();
            int n = 0;
            for (int[] c : f.openCells()) {
                if (n > 40) {
                    break;
                }
                int x = c[0], z = c[1];
                int a = alongX ? x : z, b = alongX ? z : x;
                int m = Math.floorMod(b, 4);
                if (m != 1 && m != 2 || Math.floorMod(a, 7) == 6) {
                    continue;
                }
                int toward = alongX ? (m == 1 ? 0 : 2) : (m == 1 ? 3 : 1);
                int[] front = Floorplan.step(x, z, toward);
                if (f.at(front[0], front[1]) != Floorplan.OPEN && f.at(front[0], front[1]) != Floorplan.WALK) {
                    continue;
                }
                String b0 = Math.floorMod(a, 7) == 0 ? pick("pk_houseplant[facing=north]", "pk_coffee_table[facing=north]", "pk_trash_can[facing=north]")
                        : seat + "[facing=" + Floorplan.face(toward) + "]";
                if (f.place(x, z, b0, front)) {
                    n++;
                }
            }
        }

        interface DeskPick {
            String desk(int x, int z, boolean along, int side);
        }

        /** Rows of desks back to back along x, every gap cells, a chair behind each. */
        void rowsOf(int gap, int maxLen, DeskPick pick) {
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (z % (gap + 2) != 0) {
                    continue;
                }
                if (f.at(x, z) != Floorplan.OPEN || f.at(x, z + 1) != Floorplan.OPEN || (x % (maxLen + 1)) == 0) {
                    continue;
                }
                f.place(x, z, pick.desk(x, z, true, 0) + "[facing=north]", null);
                f.place(x, z + 1, pick.desk(x, z + 1, true, 2) + "[facing=south]", null);
                if (rng.nextDouble() < 0.85) {
                    f.place(x, z - 1, chair(2), null);
                }
                if (rng.nextDouble() < 0.85) {
                    f.place(x, z + 2, chair(0), null);
                }
            }
        }

        /** A service counter across the open floor near the core, benches for the queue in front. */
        void counterLine() {
            int n = 0;
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (f.dist[z * f.w + x] == f.band + 3 && n < 14 && rng.nextDouble() < 0.7) {
                    if (f.place(x, z, "bk_counter[facing=" + Floorplan.face(facingOut(x, z)) + "]", null)) {
                        n++;
                    }
                }
            }
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (f.dist[z * f.w + x] == 2 && (x + z) % 3 == 0) {
                    f.place(x, z, "tx_bench[facing=" + Floorplan.face(facingOut(x, z) + 2) + "]", null);
                }
            }
        }

        /** A bar counter in front of the core with a back bar, stools along it. */
        /**
         * The club's bar against a wall of the core rooms: the bottles on shelves against the wall,
         * a cell for the bartender, the counter, the stools on the floor side.
         */
        void backBar() {
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (f.dist[z * f.w + x] < 6 || f.at(x, z) != Floorplan.OPEN) {
                    continue;
                }
                for (int s = 0; s < 4; s++) {
                    int[] wall = Floorplan.step(x, z, s);
                    byte v = f.at(wall[0], wall[1]);
                    if (v != Floorplan.WALL && v != Floorplan.CORE) {
                        continue;
                    }
                    // a straight run of at least 6 along that wall, all open three deep
                    int along = (s + 1) % 4, n = 0;
                    for (int[] q = {x, z}; n < 10; q = Floorplan.step(q[0], q[1], along), n++) {
                        int[] w2 = Floorplan.step(q[0], q[1], s);
                        if (f.at(w2[0], w2[1]) != v || !openDeep(q[0], q[1], s + 2, 4)) {
                            break;
                        }
                    }
                    if (n < 6) {
                        continue;
                    }
                    int[] q = {x, z};
                    for (int i = 0; i < n; i++, q = Floorplan.step(q[0], q[1], along)) {
                        int[] keep = Floorplan.step(q[0], q[1], s + 2), counter = Floorplan.step(keep[0], keep[1], s + 2);
                        int[] stool = Floorplan.step(counter[0], counter[1], s + 2);
                        // the ends left open so the bartender can get behind
                        if (i == 0 || i == n - 1) {
                            continue;
                        }
                        f.place(q[0], q[1], pick("pk_shelf_liquor_1", "pk_shelf_liquor_2", "pk_shelf_liquor_3", "trade_back_bar") + "[facing="
                                + Floorplan.face(s + 2) + "]", null);
                        f.put(keep[0], keep[1], Floorplan.CLEAR);
                        if (f.place(counter[0], counter[1], (i == n / 2 ? "trade_bar_taps" : "trade_bar") + "[facing=" + Floorplan.face(s + 2) + "]", null)
                                && rng.nextDouble() < 0.8) {
                            f.place(stool[0], stool[1], "pk_bar_chair[facing=" + Floorplan.face(s) + "]", null);
                        }
                    }
                    return;
                }
            }
        }

        boolean openDeep(int x, int z, int s, int deep) {
            int[] q = {x, z};
            for (int i = 0; i < deep; i++) {
                if (f.at(q[0], q[1]) != Floorplan.OPEN) {
                    return false;
                }
                q = Floorplan.step(q[0], q[1], s);
            }
            return true;
        }

        void barRun() {
            int n = 0;
            for (int[] c : f.openCells()) {
                int x = c[0], z = c[1];
                if (f.dist[z * f.w + x] == f.band + 3 && n < 10) {
                    int s = facingOut(x, z);
                    if (f.place(x, z, "trade_bar[facing=" + Floorplan.face(s) + "]", null)) {
                        n++;
                        int[] st = Floorplan.step(x, z, s);
                        f.place(st[0], st[1], "pk_bar_chair[facing=" + Floorplan.face(s + 2) + "]", null);
                    }
                }
            }
        }

        /** The way out from the core to the windows at a cell: which way the outside is nearest. */
        int facingOut(int x, int z) {
            int best = 0, bd = Integer.MAX_VALUE;
            for (int s = 0; s < 4; s++) {
                int[] n = Floorplan.step(x, z, s);
                int dd = f.in(n[0], n[1]) ? f.dist[n[1] * f.w + n[0]] : -1;
                if (dd < bd) {
                    bd = dd;
                    best = s;
                }
            }
            return best;
        }

        boolean freeBox(int x0, int z0, int x1, int z1) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    if (f.at(x, z) != Floorplan.OPEN) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Scatters single pieces over the open floor, against nothing in particular. */
        void dotOpen(String[] kinds, double chance) {
            for (int[] c : f.openCells()) {
                if (rng.nextDouble() < chance) {
                    String b = kinds[rng.nextInt(kinds.length)].replace("%s", Floorplan.face(rng.nextInt(4)));
                    f.place(c[0], c[1], b, null);
                }
            }
        }

        String chair(int s) {
            if (rng.nextDouble() < 0.12) {
                return "office_chair_fallen[facing=" + Floorplan.face(rng.nextInt(4)) + "]";
            }
            return "office_chair[facing=" + Floorplan.face(rng.nextDouble() < 0.25 ? rng.nextInt(4) : s) + "]";
        }

        String gymKit() {
            return pick("sc_pad_a", "pk_metal_rack", "sc_horse", "tx_bench");
        }

        String pick(String... s) {
            return s[rng.nextInt(s.length)];
        }

        // ---- rooms

        void room(Room r) {
            Fit k = new Fit(r);
            switch (r.kind) {
                case "wc" -> k.toilets();
                case "showers" -> k.showers();
                case "kitchen" -> k.kitchen();
                case "kitchen_big" -> k.bigKitchen();
                case "copy" -> k.copyRoom();
                case "store", "cages" -> k.store();
                case "files" -> k.files();
                case "meeting" -> k.meeting(false);
                case "board" -> k.meeting(true);
                case "office" -> k.office(false);
                case "exec" -> k.office(true);
                case "reception", "waiting" -> k.reception();
                case "library" -> k.library();
                case "server" -> k.server();
                case "studio" -> k.studio();
                case "exam" -> k.exam();
                case "dental" -> k.dental();
                case "ward", "care_room" -> k.ward(r.kind.equals("care_room"));
                case "pharmacy" -> k.pharmacy();
                case "lab" -> k.lab();
                case "xray" -> k.xray();
                case "nurse" -> k.nurse();
                case "day_room" -> k.dayRoom();
                case "hotel_room" -> k.hotelRoom(false);
                case "hotel_suite" -> k.hotelRoom(true);
                case "penthouse_bed" -> k.hotelRoom(true);
                case "ph_master" -> k.master();
                case "ph_cinema" -> k.cinema();
                case "ph_wine" -> k.wine();
                case "ph_kitchen" -> k.homeKitchen();
                case "penthouse_lounge", "ph_lounge" -> k.lounge();
                case "ph_bath" -> k.bath();
                case "ph_closet", "ph_dressing" -> k.closet(r.kind.equals("ph_dressing"));
                case "ph_study" -> k.study();
                case "ph_billiards" -> k.billiards();
                case "ph_cards" -> k.cards();
                case "ph_cigar" -> k.cigar();
                case "ph_music" -> k.music();
                case "ph_staff" -> k.staff();
                case "ph_guns" -> k.guns();
                case "ph_trophy" -> k.trophy();
                case "ph_vault" -> k.vault();
                case "ph_cash" -> k.cash();
                case "ph_champagne" -> k.champagne();
                case "ph_sauna" -> k.sauna();
                case "ph_treat" -> k.spa();
                case "ph_juice", "ph_bar" -> k.homeBar();
                case "ph_tea" -> k.tea();
                case "ph_dining" -> k.diningRoom();
                case "ph_gym" -> k.homeGym();
                case "linen" -> k.linen();
                case "vending" -> k.vending();
                case "laundry" -> k.laundry();
                case "spa" -> k.spa();
                case "bunks" -> k.bunks();
                case "boiler", "chiller", "pumps" -> k.plantRoom(r.kind);
                case "generator" -> k.generator();
                case "workshop" -> k.workshop();
                case "armoury" -> k.armoury();
                case "morgue" -> k.morgue();
                case "command" -> k.command();
                case "travel", "insurance", "realtor", "accountant", "typing" -> k.smallFirm(r.kind);
                case "tailor" -> k.tailor();
                case "pawn" -> k.pawn();
                default -> k.office(false);
            }
            if (type == Type.PENTHOUSE && !r.kind.equals("ph_vault") && !r.kind.equals("ph_sauna") && !r.kind.equals("wc")) {
                k.dress();
            }
        }

        /** Fitting one room: pieces against its walls (none in front of the door), pieces in the middle. */
        final class Fit {
            final Room r;
            final List<int[]> walls;

            Fit(Room r) {
                this.r = r;
                this.walls = f.alongWalls(r);
                Collections.shuffle(walls, new java.util.Random(rng.nextLong()));
            }

            /** A piece against a wall, front facing into the room; tall ones keep off the windows. */
            boolean wall(String block, boolean tall) {
                for (int i = 0; i < walls.size(); i++) {
                    int[] c = walls.get(i);
                    if (f.at(c[0], c[1]) != Floorplan.ROOM) {
                        continue;
                    }
                    if (tall && f.window(c[0], c[1], c[2] + 2)) {
                        continue;
                    }
                    String b = block.contains("%s") ? block.replace("%s", Floorplan.face(c[2])) : block;
                    if (f.place(c[0], c[1], b, null)) {
                        walls.remove(i);
                        return true;
                    }
                }
                return false;
            }

            /** A toilet: its cistern to the wall. */
            boolean wallBack(String block) {
                for (int i = 0; i < walls.size(); i++) {
                    int[] c = walls.get(i);
                    if (f.at(c[0], c[1]) == Floorplan.ROOM && f.place(c[0], c[1], block.replace("%s", Floorplan.face(c[2] + 2)), null)) {
                        walls.remove(i);
                        return true;
                    }
                }
                return false;
            }

            /** Something hung on the wall at eye height over a cell already used or free. */
            void hang(String block) {
                for (int[] c : walls) {
                    if (f.at(c[0], c[1]) == Floorplan.ROOM && !f.window(c[0], c[1], c[2] + 2)) {
                        f.set(c[0], 2, c[1], block.replace("%s", Floorplan.face(c[2])));
                        return;
                    }
                }
            }

            /** A desk against a wall facing into the room with its chair in front. */
            boolean desk(String desk) {
                for (int i = 0; i < walls.size(); i++) {
                    int[] c = walls.get(i);
                    int[] fr = Floorplan.step(c[0], c[1], c[2]);
                    if (f.at(c[0], c[1]) != Floorplan.ROOM || f.at(fr[0], fr[1]) != Floorplan.ROOM) {
                        continue;
                    }
                    if (f.place(c[0], c[1], desk + "[facing=" + Floorplan.face(c[2]) + "]", null)) {
                        f.place(fr[0], fr[1], chair(c[2] + 2), null);
                        walls.remove(i);
                        return true;
                    }
                }
                return false;
            }

            /** A desk out in the room facing the door, its chair behind it. */
            boolean deskFacingDoor(String desk) {
                int s = r.dside;  // the way into the room: the desk faces back toward the door
                int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
                for (int t = 0; t < 6; t++) {
                    int x = cx + (t % 3) - 1, z = cz + t / 3;
                    int[] back = Floorplan.step(x, z, s);
                    if (f.at(x, z) == Floorplan.ROOM && f.at(back[0], back[1]) == Floorplan.ROOM
                            && f.place(x, z, desk + "[facing=" + Floorplan.face(s) + "]", null)) {
                        f.place(back[0], back[1], chair(s + 2), null);
                        int[] guest = Floorplan.step(x, z, s + 2);
                        if (rng.nextBoolean()) {
                            f.place(guest[0], guest[1], "pk_chair_wooden[facing=" + Floorplan.face(s) + "]", null);
                        }
                        return true;
                    }
                }
                return false;
            }

            /** n pieces against the walls, from the kinds. */
            void several(int n, boolean tall, String... kinds) {
                for (int i = 0; i < n; i++) {
                    wall(kinds[rng.nextInt(kinds.length)], tall);
                }
            }

            /** A table down the middle of the room with chairs round it. */
            void table(String top, String seat, boolean longWay) {
                boolean alongX = r.w() >= r.d();
                int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
                int len = Math.max(1, (alongX ? r.w() : r.d()) - 4);
                if (!longWay) {
                    len = Math.min(len, 2);
                }
                int a0 = (alongX ? cx : cz) - (len - 1) / 2;
                for (int i = 0; i < len; i++) {
                    int x = alongX ? a0 + i : cx, z = alongX ? cz : a0 + i;
                    if (!f.place(x, z, top, null)) {
                        continue;
                    }
                    for (int s : alongX ? new int[] {0, 2} : new int[] {1, 3}) {
                        int[] n = Floorplan.step(x, z, s);
                        if (rng.nextDouble() < 0.85) {
                            f.place(n[0], n[1], seat.replace("%s", Floorplan.face(s + 2)), null);
                        }
                    }
                }
            }

            void scatter(int n, String... kinds) {
                for (int i = 0; i < n; i++) {
                    int x = r.x0 + rng.nextInt(r.w()), z = r.z0 + rng.nextInt(r.d());
                    if (f.at(x, z) == Floorplan.ROOM) {
                        f.set(x, 1, z, kinds[rng.nextInt(kinds.length)].replace("%s", Floorplan.face(rng.nextInt(4))));
                    }
                }
            }

            // ---- the rooms

            void toilets() {
                // a row of stalls along the longest wall, sinks and a mirror opposite
                int stalls = Math.max(1, r.area() / 6);
                for (int i = 0; i < stalls; i++) {
                    wallBack("fit_toilet_stall[facing=%s]");
                }
                several(2, false, "fit_sink_cabin[facing=%s]");
                hang("fit_mirror_long[facing=%s]");
                scatter(1, "home_towel[facing=%s]", "trash_bag[facing=%s]");
            }

            void showers() {
                several(Math.max(2, r.area() / 5), false, "bunk_shower[facing=%s]", "bunk_shower[facing=%s]", "sc_locker_a[facing=%s]");
                several(1, false, "fit_sink_cabin[facing=%s]");
                scatter(1, "home_towel[facing=%s]");
            }

            void kitchen() {
                wall("home_fridge[facing=%s]", true);
                several(2 + rng.nextInt(2), false, "home_counter_white[facing=%s]", "home_counter_white[facing=%s]", "home_sink_white[facing=%s]");
                wall("pk_vending[facing=%s]", true);
                wall("trade_water_cooler[facing=%s]", false);
                if (r.area() >= 12) {
                    table("pk_table_wooden[facing=north]", "pk_chair_wooden[facing=%s]", false);
                }
                scatter(1, "pk_trash_can[facing=%s]");
            }

            void bigKitchen() {
                several(2, true, "dn_range[facing=%s]", "dn_lowboy[facing=%s]");
                several(3, false, "dn_prep[facing=%s]", "dn_sink[facing=%s]", "fit_counter_steel[facing=%s]", "dn_dish_lo[facing=%s]");
                wall("dn_dry[facing=%s]", true);
                if (r.area() >= 14) {
                    table("dn_prep_bare[facing=north]", "air", false);
                }
            }

            void copyRoom() {
                wall("of_copier[facing=%s]", false);
                several(2, true, "store_shelf_empty[facing=%s]", "pk_file_tall[facing=%s]", "pk_metal_rack[facing=%s]");
                several(2, false, "cardboard_box[facing=%s]", "home_boxes", "fit_counter_steel[facing=%s]");
            }

            void store() {
                several(Math.max(2, r.area() / 3), true, "pk_metal_rack[facing=%s]", "store_shelf_empty[facing=%s]", "bunk_rack_boxes[facing=%s]",
                        "home_garage_shelf[facing=%s]");
                several(2, false, "cardboard_box[facing=%s]", "crate");
            }

            void files() {
                several(Math.max(3, r.area() / 2), true, "filing_cabinet[facing=%s]", "pk_file_tall[facing=%s]", "pk_file_cabinet[facing=%s]");
                scatter(1, "cardboard_box[facing=%s]");
            }

            void meeting(boolean board) {
                table(board ? "cv_table[axis=" + (r.w() >= r.d() ? "x" : "z") + "]" : "pk_table_wooden[facing=north]",
                        board ? "pk_armchair[facing=%s]" : "office_chair[facing=%s]", true);
                if (board) {
                    hang("fit_painting_a[facing=%s]");
                    wall("trade_back_bar[facing=%s]", true);
                } else {
                    hang("bk_wall_clock[facing=%s]");
                    hang("of_whiteboard[facing=%s]");
                }
            }

            void office(boolean exec) {
                if (exec) {
                    deskFacingDoor("sc_tdesk_ped");
                    several(2, true, "home_bookcase[facing=%s]", "pk_cabinet_oak[facing=%s]");
                    wall("home_sofa_brown_m[facing=%s]", false);
                    wall("pk_houseplant[facing=%s]", false);
                    hang("fit_painting_b[facing=%s]");
                    if (rng.nextDouble() < 0.35) {
                        wall("store_safe[facing=%s]", false);
                    }
                } else {
                    if (!desk(rng.nextBoolean() ? "home_desk_pc" : "pk_desk_metal")) {
                        deskFacingDoor("desk");
                    }
                    several(1 + rng.nextInt(2), true, "filing_cabinet[facing=%s]", "pk_bookshelf_worn[facing=%s]", "pk_file_tall[facing=%s]");
                    wall(rng.nextBoolean() ? "pk_houseplant[facing=%s]" : "pk_trash_can[facing=%s]", false);
                    hang(rng.nextBoolean() ? "bk_wall_clock[facing=%s]" : "fit_painting_c[facing=%s]");
                }
            }

            void reception() {
                deskFacingDoor("cv_circ_desk");
                several(2 + rng.nextInt(2), false, "home_sofa_blue_m[facing=%s]", "home_armchair_blue[facing=%s]", "pk_armchair_green[facing=%s]");
                several(2, false, "pk_houseplant[facing=%s]", "trade_water_cooler[facing=%s]", "home_coffee_table[facing=%s]");
            }

            void library() {
                several(Math.max(3, r.area() / 2), true, "home_bookcase[facing=%s]", "pk_bookshelf_worn[facing=%s]");
                table("cv_table_lamp[axis=" + (r.w() >= r.d() ? "x" : "z") + "]", "pk_chair_wooden[facing=%s]", false);
            }

            void server() {
                several(Math.max(3, r.area() / 2), true, "bunk_server[facing=%s]");
                several(1, true, "bunk_switchboard[facing=%s]");
                hang("bunk_fusebox[facing=%s]");
            }

            void studio() {
                desk("bunk_radio_desk");
                several(2, true, "pk_bookshelf_worn[facing=%s]", "bunk_server[facing=%s]");
                scatter(1, "pk_cassette_player[facing=%s]", "pk_boombox[facing=%s]");
            }

            void exam() {
                wall("of_exam_table[facing=%s]", false);
                several(2, false, "fit_medcab[facing=%s]", "fit_sink_cabin[facing=%s]", "bunk_medcab[facing=%s]");
                wall("bunk_iv_stand", false);
                hang("sc_eye_chart[facing=%s]");
                deskFacingDoor("desk");
            }

            void dental() {
                wall("of_dental_chair[facing=%s]", false);
                several(2, false, "fit_medcab[facing=%s]", "fit_sink_cabin[facing=%s]", "fit_counter_steel[facing=%s]");
                hang("fit_surgical");
            }

            void ward(boolean care) {
                // beds head to the walls, a curtain's width between them
                int beds = Math.max(1, r.area() / (care ? 8 : 5));
                for (int i = 0; i < beds; i++) {
                    bed(care ? "home_bed_quilt" : "bunk_medbed");
                }
                several(2, false, "bunk_iv_stand", "fit_chair_ward[facing=%s]", care ? "pk_nightstand[facing=%s]" : "bunk_medcab[facing=%s]");
                if (care) {
                    wall("home_wardrobe[facing=%s]", true);
                    hang("fit_painting_c[facing=%s]");
                }
            }

            /** A bed: its head against a wall, the foot out into the room. */
            boolean bed(String bed) {
                for (int i = 0; i < walls.size(); i++) {
                    int[] c = walls.get(i);
                    int[] foot = Floorplan.step(c[0], c[1], c[2]);
                    if (f.at(c[0], c[1]) != Floorplan.ROOM || f.at(foot[0], foot[1]) != Floorplan.ROOM) {
                        continue;
                    }
                    String s = Floorplan.face(c[2]);
                    if (f.place(c[0], c[1], bed + "_head[facing=" + s + "]", null)) {
                        if (f.place(foot[0], foot[1], bed + "_foot[facing=" + s + "]", null)) {
                            walls.remove(i);
                            return true;
                        }
                        f.set(c[0], 1, c[1], "air");
                        f.put(c[0], c[1], Floorplan.ROOM);
                    }
                }
                return false;
            }

            void pharmacy() {
                several(Math.max(3, r.area() / 2), true, "pk_shelf_pharmacy_1[facing=%s]", "pk_shelf_pharmacy_2[facing=%s]",
                        "cellar_shelf_medical[facing=%s]", "store_shelf_pharmacy[facing=%s]");
            }

            void lab() {
                several(4, false, "fit_counter_steel[facing=%s]", "bunk_sink[facing=%s]", "fit_counter_steel[facing=%s]");
                several(2, true, "home_fridge[facing=%s]", "bunk_medcab[facing=%s]");
            }

            void xray() {
                wall("bunk_medbed_head[facing=%s]", false);
                several(2, true, "bunk_switchboard[facing=%s]", "bunk_server[facing=%s]");
                hang("fit_surgical");
            }

            void nurse() {
                deskFacingDoor("cv_circ_desk");
                several(3, true, "bunk_medcab[facing=%s]", "pk_file_tall[facing=%s]", "cellar_shelf_medical[facing=%s]");
            }

            void dayRoom() {
                several(4, false, "home_armchair_floral[facing=%s]", "home_armchair_brown[facing=%s]", "home_sofa_floral_m[facing=%s]");
                wall("pk_tv_wood[facing=%s]", false);
                table("pk_table_wooden[facing=north]", "pk_chair_wooden[facing=%s]", false);
            }

            void hotelRoom(boolean suite) {
                String bed = pick("home_bed_blue", "home_bed_rose", "home_bed_quilt");
                bed(bed);
                if (suite || r.area() >= 12) {
                    bed(bed);
                }
                several(1, false, "pk_nightstand[facing=%s]");
                wall("home_dresser[facing=%s]", false);
                wall("home_wardrobe[facing=%s]", true);
                if (suite) {
                    several(2, false, "home_sofa_brown_m[facing=%s]", "home_armchair_brown[facing=%s]");
                    wall("trade_back_bar[facing=%s]", true);
                    wall("pk_tv_wood[facing=%s]", false);
                } else {
                    wall(rng.nextBoolean() ? "pk_tv_crt[facing=%s]" : "home_desk[facing=%s]", false);
                }
                wall("home_vanity[facing=%s]", false);
                hang(rng.nextBoolean() ? "fit_painting_a[facing=%s]" : "fit_painting_b[facing=%s]");
                if (state == State.LOOTED) {
                    // clothes thrown down; the luggage cart left by a wall, not across the way
                    scatter(1, "home_clothes_pile");
                    if (rng.nextBoolean()) {
                        wall("trade_luggage_cart[facing=%s]", false);
                    }
                }
            }

            /**
             * The master suite: a king bed (two pushed together) between nightstands, the
             * freestanding tub and the vanity at one end, the dressing wall of wardrobes, a chaise
             * by the glass, paintings.
             */
            void master() {
                String bed = pick("home_bed_rose", "home_bed_quilt", "home_bed_blue");
                bed(bed);
                bed(bed);
                wall("home_tub_l[facing=%s]", false);
                several(2, false, "pk_nightstand[facing=%s]");
                wall("home_vanity[facing=%s]", false);
                several(3, true, "home_wardrobe[facing=%s]");
                wall("home_dresser[facing=%s]", false);
                several(2, false, "home_armchair_floral[facing=%s]", "home_floor_lamp");
                hang("of_art_a[facing=%s]");
                hang("fit_mirror_long[facing=%s]");
                scatter(state == State.LOOTED ? 2 : 0, "home_clothes_pile", "home_towel[facing=%s]");
            }

            /** The screening room: the screen on the far wall, rows of sofas facing it. */
            void cinema() {
                int s = r.dside;
                int[] in = Floorplan.step(0, 0, s);
                for (int z = r.z0; z <= r.z1; z++) {
                    for (int x = r.x0; x <= r.x1; x++) {
                        int nx = x + in[0], nz = z + in[1];
                        boolean far = nx < r.x0 || nx > r.x1 || nz < r.z0 || nz > r.z1;
                        if (!far) {
                            continue;
                        }
                        f.set(x, 2, z, "of_screen[facing=" + Floorplan.face(s + 2) + "]");
                        for (int row = 2; row <= 4; row += 2) {
                            int sx = x - in[0] * row, sz = z - in[1] * row;
                            if (sx > r.x0 && sx < r.x1 || sz > r.z0 && sz < r.z1) {
                                f.place(sx, sz, "home_sofa_blue_m[facing=" + Floorplan.face(s) + "]", null);
                            }
                        }
                    }
                }
                several(2, false, "home_floor_lamp", "home_lamp_table[facing=%s]");
                wall("trade_back_bar[facing=%s]", true);
            }

            /** The wine room: racks floor to ceiling all round, a tasting table. */
            void wine() {
                several(Math.max(4, r.area() / 2), true, "cellar_wine_rack[facing=%s]");
                table("pk_coffee_table[facing=north]", "pk_bar_chair[facing=%s]", false);
            }

            /** The penthouse kitchen: counters and the range round the walls, a marble island with stools. */
            void homeKitchen() {
                wall("home_fridge[facing=%s]", true);
                several(3 + rng.nextInt(2), false, "home_counter_white[facing=%s]", "home_sink_white[facing=%s]", "home_stove[facing=%s]");
                table("of_island[facing=north]", "pk_bar_chair[facing=%s]", true);
                scatter(1, "pk_trash_can[facing=%s]");
            }

            void lounge() {
                several(3, false, "home_sofa_brown_l[facing=%s]", "home_sofa_brown_m[facing=%s]", "home_armchair_brown[facing=%s]");
                wall("trade_back_bar[facing=%s]", true);
                wall("trade_jukebox[facing=%s]", false);
                wall("home_floor_lamp", false);
                table("home_coffee_table[facing=north]", "air", false);
                hang("fit_painting_c[facing=%s]");
            }

            // ---- the penthouse's rooms

            /**
             * A rich man's room never looks bare: a rug under the middle, paintings and a lamp, and
             * for a big room more pieces against the walls until it's furnished for its size.
             */
            void dress() {
                boolean wet = r.kind.equals("ph_bath") || r.kind.equals("ph_treat") || r.kind.equals("ph_kitchen") || r.kind.equals("ph_staff")
                        || r.kind.equals("ph_gym") || r.kind.equals("ph_juice");
                if (!wet && r.w() >= 6 && r.d() >= 6) {
                    String rug = pick("of_carpet_red", "home_floor_carpet_blue", "home_floor_carpet_beige");
                    for (int x = r.x0 + 2; x <= r.x1 - 2; x++) {
                        for (int z = r.z0 + 2; z <= r.z1 - 2; z++) {
                            if (f.at(x, z) == Floorplan.ROOM || f.at(x, z) == Floorplan.ITEM || f.at(x, z) == Floorplan.CLEAR) {
                                f.p.set(x, f.y, z, rug);
                            }
                        }
                    }
                }
                hang(pick("of_art_a[facing=%s]", "of_art_b[facing=%s]", "fit_painting_a[facing=%s]", "fit_painting_c[facing=%s]"));
                hang(pick("of_art_a[facing=%s]", "of_art_b[facing=%s]", "fit_mirror_long[facing=%s]"));
                int extra = Math.max(0, r.area() / 10 - 1);
                String[] filler = wet ? new String[] {"pk_houseplant[facing=%s]", "home_towel[facing=%s]", "home_counter_white[facing=%s]"}
                        : new String[] {"home_floor_lamp", "pk_houseplant[facing=%s]", "pk_sideboard[facing=%s]", "home_bookcase[facing=%s]",
                            "home_armchair_brown[facing=%s]", "mu_urn", "of_tree_pot", "pk_cabinet_oak[facing=%s]", "of_sculpture[facing=%s]"};
                several(extra, false, filler);
                int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
                if (r.area() >= 30 && !wet && f.p.get(cx, f.y + f.rows, cz) == null) {
                    f.set(cx, f.rows, cz, "tr_chandelier");
                }
            }

            /** The marble bathroom: the tub, twin basins, the shower, the toilet, a mirror. */
            void bath() {
                wall("home_tub_l[facing=%s]", false);
                several(2, false, "fit_sink_cabin[facing=%s]");
                wallBack("home_toilet[facing=%s]");
                hang("fit_mirror_long[facing=%s]");
                several(1, false, "pk_houseplant[facing=%s]", "home_towel[facing=%s]");
            }

            /** The walk-in closet or the dancers' dressing room: wardrobes all round, mirrors, a vanity. */
            void closet(boolean dressing) {
                several(Math.max(3, r.area() / 3), true, "home_wardrobe[facing=%s]");
                several(dressing ? 3 : 1, false, "home_vanity[facing=%s]");
                hang(dressing ? "of_mirror[facing=%s]" : "fit_mirror_long[facing=%s]");
                hang("fit_mirror_long[facing=%s]");
                several(1, false, "mu_ottoman", "home_dresser[facing=%s]");
                scatter(state == State.LOOTED ? 2 : 1, "home_clothes_pile");
            }

            /** The study: the desk facing the door, bookcases, a globe of a drinks cabinet, a leather chair. */
            void study() {
                deskFacingDoor("home_desk_pc");
                several(Math.max(2, r.area() / 4), true, "home_bookcase[facing=%s]", "pk_cabinet_oak[facing=%s]");
                several(2, false, "home_armchair_brown[facing=%s]", "trade_back_bar[facing=%s]", "home_floor_lamp");
                hang("of_art_b[facing=%s]");
            }

            /** The billiard room: the table in the middle, the cue rack and the drinks against the walls. */
            void billiards() {
                int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
                boolean alongX = r.w() >= r.d();
                int[] b = alongX ? new int[] {cx + 1, cz} : new int[] {cx, cz + 1};
                String fc = alongX ? "south" : "west";
                if (f.place(cx, cz, "of_pool_table_l[facing=" + fc + "]", null) && !f.place(b[0], b[1], "of_pool_table_r[facing=" + fc + "]", null)) {
                    f.set(cx, 1, cz, "air");
                    f.put(cx, cz, Floorplan.ROOM);
                }
                f.set(cx, f.rows, cz, "tr_chandelier");
                several(2, false, "pk_bar_chair[facing=%s]", "home_armchair_brown[facing=%s]");
                several(2, true, "pk_shelf_liquor_1[facing=%s]", "trade_back_bar[facing=%s]");
                hang("fit_painting_c[facing=%s]");
            }

            /** The card room: felt tables with four chairs, the humidor, the bar. */
            void cards() {
                int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
                for (int[] t : new int[][] {{cx - 2, cz}, {cx + 2, cz}}) {
                    if (f.at(t[0], t[1]) == Floorplan.ROOM && f.place(t[0], t[1], "of_card_table", null)) {
                        for (int s2 = 0; s2 < 4; s2++) {
                            int[] c = Floorplan.step(t[0], t[1], s2);
                            if (rng.nextDouble() < 0.8) {
                                f.place(c[0], c[1], (rng.nextDouble() < 0.15 ? "home_chair_tipped" : "pk_chair_dining") + "[facing=" + Floorplan.face(s2 + 2) + "]", null);
                            }
                        }
                        f.set(t[0], f.rows, t[1], "tr_chandelier");
                    }
                }
                wall("of_humidor[facing=%s]", false);
                several(2, true, "trade_back_bar[facing=%s]", "pk_shelf_liquor_2[facing=%s]");
            }

            /** The cigar lounge: leather chairs round low tables, the humidor, the decanters. */
            void cigar() {
                several(2, false, "of_humidor[facing=%s]");
                several(4, false, "home_armchair_brown[facing=%s]", "home_sofa_brown_m[facing=%s]");
                table("pk_coffee_table[facing=north]", "home_armchair_brown[facing=%s]", false);
                several(1, true, "pk_shelf_liquor_3[facing=%s]");
                hang("fit_painting_a[facing=%s]");
            }

            /** The music room: the grand piano, a stand, chairs to listen from. */
            void music() {
                int cx = (r.x0 + r.x1) / 2, cz = (r.z0 + r.z1) / 2;
                if (f.place(cx, cz, "of_grand_piano_l[facing=south]", null)) {
                    f.place(cx - 1, cz, "of_grand_piano_r[facing=south]", null);
                    f.set(cx, f.rows, cz, "tr_chandelier");
                }
                several(3, false, "mu_music_stand[facing=%s]", "home_armchair_floral[facing=%s]", "home_armchair_floral[facing=%s]");
                several(1, true, "home_bookcase[facing=%s]");
                hang("of_art_a[facing=%s]");
            }

            /** The staff quarters: two narrow beds, lockers, a table. */
            void staff() {
                bed("home_bed_blue");
                bed("home_bed_blue");
                several(2, true, "sc_locker_a[facing=%s]", "home_wardrobe[facing=%s]");
                several(2, false, "home_sink_white[facing=%s]", "home_counter_white[facing=%s]", "fit_laundry_bag[facing=%s]");
            }

            /** The gun room: cabinets and racks round the walls, a glass case in the middle. */
            void guns() {
                several(Math.max(3, r.area() / 3), true, "home_gun_cabinet[facing=%s]", "store_gun_rack[facing=%s]", "sh_gun_rack[facing=%s]");
                table("store_case_guns[facing=north]", "air", false);
                hang("mu_swords[facing=%s]");
                several(1, false, "bunk_crate_gun[facing=%s]");
            }

            /** The trophy hall: mounted beasts and armour round the walls, a pair of chairs between. */
            void trophy() {
                several(Math.max(3, r.area() / 4), false, "mu_bear_lo[facing=%s]", "mu_deer_lo[facing=%s]", "mu_wolf[facing=%s]", "mu_armour_lo[facing=%s]",
                        "mu_fox[facing=%s]");
                hang("mu_shield[facing=%s]");
                hang("mu_swords[facing=%s]");
                several(2, false, "home_armchair_brown[facing=%s]", "mu_eagle[facing=%s]");
            }

            /** The vault: safes and cases of jewels and coin. */
            void vault() {
                several(Math.max(2, r.area() / 2), false, "cellar_safe[facing=%s]", "mu_table_jewels", "mu_table_coins", "bk_coffer");
                scatter(2, "bk_notes[facing=%s]");
            }

            /** The cash office: the safe, the counting desk, the bags of takings. */
            void cash() {
                deskFacingDoor("home_desk");
                several(2, false, "cellar_safe[facing=%s]", "bk_bags[facing=%s]", "filing_cabinet[facing=%s]");
                scatter(2, "bk_notes[facing=%s]");
            }

            /** The private champagne rooms: velvet booths round the walls, a bucket on each table. */
            void champagne() {
                several(Math.max(3, r.area() / 3), false, "of_booth[facing=%s]", "of_booth_purple[facing=%s]");
                table("pk_coffee_table[facing=north]", "air", false);
                scatter(2, "of_champagne");
                hang("of_neon_pink[facing=%s]");
            }

            /** The sauna: cedar benches round the walls, the stove. */
            void sauna() {
                several(Math.max(3, r.area() / 2), false, "of_sauna_bench[axis=x]", "of_sauna_bench[axis=z]");
                wall("of_sauna_stove", false);
                several(1, false, "home_towel[facing=%s]");
            }

            /** A bar in a room: the counter, stools, the bottles behind. */
            void homeBar() {
                several(Math.max(2, r.area() / 4), true, "pk_shelf_liquor_1[facing=%s]", "pk_shelf_liquor_2[facing=%s]", "trade_back_bar[facing=%s]");
                table("trade_bar[facing=north]", "pk_bar_chair[facing=%s]", true);
                several(2, false, "of_high_table[facing=%s]", "trade_neon_beer[facing=%s]");
            }

            /** The tea room by the garden: a round table, cane chairs, plants. */
            void tea() {
                table("pk_table_wooden[facing=north]", "pk_chair_wooden[facing=%s]", false);
                several(3, false, "mu_fern", "pk_houseplant[facing=%s]", "of_tree_pot");
                several(1, false, "pk_sideboard[facing=%s]");
            }

            /** The formal dining room: the long table, the sideboards, the chandeliers. */
            void diningRoom() {
                table("pk_table_wooden[facing=north]", "pk_chair_dining[facing=%s]", true);
                several(2, false, "pk_sideboard[facing=%s]", "pk_cabinet_oak[facing=%s]");
                f.set((r.x0 + r.x1) / 2, f.rows, (r.z0 + r.z1) / 2, "tr_chandelier");
                hang("of_art_a[facing=%s]");
            }

            /** The private gym: a treadmill, the rack, the bench, the mirror. */
            void homeGym() {
                several(2, false, "of_treadmill[facing=%s]", "of_bike[facing=%s]");
                several(2, false, "of_squat_rack[facing=%s]", "of_bench_press[facing=%s]", "of_dumbbells[facing=%s]");
                hang("of_mirror[facing=%s]");
                hang("of_mirror[facing=%s]");
            }

            void linen() {
                several(Math.max(2, r.area() / 2), true, "store_shelf_thrift[facing=%s]", "home_garage_shelf[facing=%s]");
                several(2, false, "fit_laundry_bag[facing=%s]", "store_cart[facing=%s]");
            }

            void vending() {
                several(2, true, "pk_vending[facing=%s]", "store_vending[facing=%s]");
                several(1, false, "dn_ice_lo[facing=%s]");
            }

            void laundry() {
                several(Math.max(3, r.area() / 2), true, "store_washer[facing=%s]", "store_dryer[facing=%s]");
                several(2, false, "home_ironing_board[facing=%s]", "fit_laundry_bag[facing=%s]", "store_cart[facing=%s]");
            }

            void spa() {
                several(3, false, "home_tub_l[facing=%s]", "bunk_shower[facing=%s]", "fit_sink_cabin[facing=%s]");
                bed("bunk_medbed");
                several(1, false, "pk_houseplant[facing=%s]");
            }

            void bunks() {
                bed("bunk_bed");
                bed("bunk_bed");
                several(2, true, "sc_locker_a[facing=%s]", "home_wardrobe[facing=%s]");
                desk("home_desk");
                scatter(2, "home_clothes_pile", "home_books_stack_low[facing=%s]");
            }

            void plantRoom(String kind) {
                String big = kind.equals("boiler") ? "bunk_tank" : kind.equals("chiller") ? "hvac" : "water_tank";
                for (int z = r.z0 + 1; z < r.z1; z += 3) {
                    for (int x = r.x0 + 1; x < r.x1; x += 3) {
                        f.place(x, z, big, null);
                    }
                }
                several(3, false, "bunk_pipes[facing=%s]", "bunk_fusebox[facing=%s]", "home_water_heater", "bunk_pipes_valve[facing=%s]");
            }

            void generator() {
                for (int z = r.z0 + 1; z < r.z1; z += 3) {
                    f.place((r.x0 + r.x1) / 2, z, "bunk_gen_engine[facing=north]", null);
                }
                several(3, false, "bunk_jerry_cans[facing=%s]", "bunk_drum_water", "bunk_switchboard[facing=%s]");
            }

            void workshop() {
                several(2, false, "home_workbench[facing=%s]", "pk_tool_chest[facing=%s]");
                several(2, true, "bunk_pegboard[facing=%s]", "pk_metal_rack[facing=%s]");
                scatter(1, "pk_toolbox[facing=%s]");
            }

            void armoury() {
                several(Math.max(3, r.area() / 3), false, "bunk_crate_ammo[facing=%s]", "bunk_crate_gun[facing=%s]", "pk_military_crate[facing=%s]",
                        "bunk_gun_rack[facing=%s]");
            }

            void morgue() {
                for (int i = 0; i < Math.max(2, r.area() / 3); i++) {
                    wall("bunk_bodybag[facing=%s]", false);
                }
                scatter(2, "home_blood_pool");
            }

            void command() {
                desk("bunk_radio_desk");
                table("bunk_map_table", "office_chair[facing=%s]", false);
                hang("bunk_map_wall[facing=%s]");
                several(2, false, "bunk_crate_ration[facing=%s]", "bunk_footlocker[facing=%s]");
            }

            void smallFirm(String kind) {
                deskFacingDoor(kind.equals("typing") ? "desk" : "home_desk_pc");
                if (kind.equals("typing")) {
                    for (int i = 0; i < 3; i++) {
                        desk("desk");
                    }
                }
                several(2, true, "filing_cabinet[facing=%s]", "pk_bookshelf_worn[facing=%s]", kind.equals("travel") ? "trade_listings[facing=%s]" : "pk_file_tall[facing=%s]");
                several(2, false, "pk_armchair[facing=%s]", "pk_houseplant[facing=%s]");
                if (kind.equals("realtor") || kind.equals("travel")) {
                    hang("trade_listings_b[facing=%s]");
                }
            }

            void tailor() {
                several(3, true, "store_clothes_rack[facing=%s]", "store_clothes_rack[facing=%s]", "home_wardrobe[facing=%s]");
                deskFacingDoor("store_counter");
                hang("home_mirror[facing=%s]");
            }

            void pawn() {
                several(3, true, "store_shelf_pawn[facing=%s]", "store_pawn_wall[facing=%s]");
                deskFacingDoor("store_case_jewelry");
                several(1, false, "store_safe[facing=%s]");
            }
        }

        // ---- the state it was left in

        /** Litter you can walk over. */
        boolean flat(String b) {
            return b.startsWith("glass") || b.startsWith("bunk_casings") || b.startsWith("home_blood") || b.startsWith("bunk_s")
                    || b.startsWith("moss") || b.startsWith("bunk_grit");
        }

        boolean byDoor(int x, int z) {
            for (int s = 0; s < 4; s++) {
                int[] n = Floorplan.step(x, z, s);
                if (f.at(n[0], n[1]) == Floorplan.DOOR) {
                    return true;
                }
            }
            return false;
        }

        void aftermath() {
            double junk = switch (state) {
                case ABANDONED -> 0.01;
                case LOOTED -> 0.035;
                case BARRICADED -> 0.02;
                case BURNT -> 0.05;
            };
            for (int i = 0; i < f.g.length; i++) {
                int x = i % f.w, z = i / f.w;
                byte v = f.g[i];
                if ((v == Floorplan.OPEN || v == Floorplan.ROOM || v == Floorplan.WALK) && rng.nextDouble() < junk) {
                    String b = switch (state) {
                        case BURNT -> pick("rubble_pile", "bunk_scorch[facing=north]", "bunk_grit", "glass_shards");
                        case BARRICADED -> pick("bunk_casings", "home_blood_pool", "glass_shards", "trash_bag[facing=north]");
                        default -> pick("glass_shards", "trash_bag[facing=north]", "home_clothes_pile", "moss_carpet", "cardboard_box[facing=east]",
                                "home_books_stack_low[facing=north]");
                    };
                    if (v == Floorplan.WALK && !b.startsWith("glass") && !b.startsWith("bunk_casings") && !b.startsWith("home_blood")
                            && !b.startsWith("bunk_s") && !b.startsWith("moss")) {
                        continue;  // only flat litter in the corridor
                    }
                    if (flat(b)) {
                        f.set(x, 1, z, b);
                    } else if (!byDoor(x, z)) {
                        f.place(x, z, b, null);  // a bag or a box never cuts a room off or blocks a door
                    }
                }
            }
            if (state == State.BARRICADED) {
                // the way in from the stairs blocked with furniture, a gap left to squeeze through
                int n = 0;
                for (int i = 0; i < f.g.length && n < 6; i++) {
                    if (f.g[i] == Floorplan.WALK && f.touchesCore(i) && rng.nextDouble() < 0.5) {
                        int x = i % f.w, z = i / f.w;
                        f.set(x, 1, z, pick("home_spray_help_0[facing=north]", "bunk_casings", "home_blood_drag[facing=north]"));
                        n++;
                    }
                }
            }
        }
    }
}
