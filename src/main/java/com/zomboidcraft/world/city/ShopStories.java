package com.zomboidcraft.world.city;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/**
 * Zomboid's building stories for a shop floor (its Randomized Building Chance, the RB stories):
 * what happened in here after the shop shut, told over the fitted-out floor once {@link Shops}
 * has stocked it.
 * <ul>
 * <li>LOOTED (RBShopLooted): stripped by the crowds before the end. Shelves pulled over, stock
 * trodden into the floor, the till cracked open, the front glass in, and whoever came last;
 * <li>TRASHED (RBTrashed): no fight, just people with nothing left to lose. Rubbish, bottles,
 * tipped chairs, a sleeping bag where somebody stayed;
 * <li>GUNSTORE_SIEGE (RBGunstoreSiege): the owner and a few others held the gun shop. Sandbags
 * inside the glass, brass everywhere, the dead piled at the door and police among them, and
 * what got in still inside;
 * <li>STAFF_LAST_STAND (RBCafe, RBPizzaWhirled): the staff locked themselves in the kitchen end of
 * a diner or a pizza place. Plates broken, chairs over, the staff dead behind the counter and
 * their customers on their feet;
 * <li>BAR (RBBar): the bar kept serving at the end. Bottles everywhere, chairs over, the drinkers
 * dead at their tables or up and walking;
 * <li>HAIR_SALON (RBHairSalon): the barber and whoever was in the chair, turned among the towels
 * and the spilt tonic.
 * </ul>
 * Each shop rolls on its own dice, from where it stands, so a shop without a story builds exactly
 * as before and comes out the same whichever chunk builds it. The bodies and walkers are marks
 * ("!body:" and "!walkers:", see world.gen.StoryMarks) that become real ones when the chunk loads.
 */
public final class ShopStories {
    enum Tale {
        LOOTED(5), TRASHED(3), GUNSTORE_SIEGE(0), STAFF_LAST_STAND(0), BAR(0), HAIR_SALON(0);

        final int weight;

        Tale(int weight) {
            this.weight = weight;
        }
    }

    /** A shop's chance of a story at "sometimes" (as a house's); the sandbox's setting scales it. */
    static final double BASE = 0.1;
    /** The sandbox's Randomized Building Chance as a multiple of {@link #BASE} (RoadStories.rate's scale). */
    private static volatile DoubleSupplier rate = () -> Double.parseDouble(System.getProperty("shops.stories", "0.5"));

    /** Hands the generator the world's chance (world.gen.StoryMarks, from the sandbox). */
    public static void setRate(DoubleSupplier supplier) {
        rate = supplier;
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final int left, right, back, door;
    private final boolean pairDoor;
    private final Set<Long> taken = new HashSet<>();

    private ShopStories(Placer p, SplittableRandom rng, int left, int right, int back, int door, boolean pairDoor) {
        this.p = p;
        this.rng = rng;
        this.left = left;
        this.right = right;
        this.back = back;
        this.door = door;
        this.pairDoor = pairDoor;
    }

    /**
     * Rolls and tells a story over a main-street trade's floor that isn't a {@link Shops} fit-out
     * (world.county.Trades): "bar" or "barber" have their own, anything else is looted or trashed.
     * The floor runs x left..right, z 1..back, the door at x door on z = 0.
     */
    public static void tellTrade(Placer p, String trade, int left, int right, int back, int door, boolean pairDoor) {
        if (right - left < 4 || back < 4) {
            return;
        }
        SplittableRandom dice = roll(p, right + 2, back + 6);
        if (dice == null) {
            return;
        }
        Tale tale = switch (trade) {
            case "bar" -> dice.nextDouble() < 0.7 ? Tale.BAR : null;
            case "barber" -> dice.nextDouble() < 0.7 ? Tale.HAIR_SALON : null;
            default -> null;
        };
        if (tale == null) {
            tale = dice.nextDouble() < 0.6 ? Tale.LOOTED : Tale.TRASHED;
        }
        new ShopStories(p, dice, left, right, back, door, pairDoor).tell(forced(tale));
    }

    /** Rolls and tells a story over the fitted-out floor of a w by d shop of the type (called by {@link Shops#fitOut}). */
    static void tell(Placer p, Shops.Type type, int w, int d) {
        if (w < 6 || d < 9) {
            return;
        }
        SplittableRandom dice = roll(p, w, d);
        if (dice != null) {
            new ShopStories(p, dice, 1, w - 2, d - 6, w / 2, w >= 12).tell(forced(pick(dice, type)));
        }
    }

    /**
     * A building's own story dice at the Randomized Building Chance, or null when it gets none: for
     * builders that tell their own (the school's overrun rooms) and only need to know whether, and
     * the random numbers to tell it with. The building's w by d frame from p is its seed.
     */
    public static SplittableRandom dice(Placer p, int w, int d) {
        return roll(p, w, d);
    }

    /** As {@link #dice(Placer, int, int)}, salted (a tower's floor number) so each storey rolls its own. */
    public static SplittableRandom dice(Placer p, int w, int d, long salt) {
        SplittableRandom dice = new SplittableRandom(seed(p, w, d) ^ salt * 0xD1B54A32D192ED03L);
        double r;
        try {
            r = Math.max(0, rate.getAsDouble());
        } catch (RuntimeException e) {
            r = 0.5;
        }
        return dice.nextDouble() < Math.min(1, BASE * r) ? dice : null;
    }

    /** A body for StoryMarks at (x, y, z) in p's frame: its outfit, now and then a limb gone. */
    public static void body(Placer p, SplittableRandom rng, double x, int y, double z, String outfit) {
        int limbs = rng.nextDouble() < 0.2 ? 1 << rng.nextInt(8) : 0;
        p.vehicle(x, y, z, (float) (rng.nextDouble() * 360), "!body:" + outfit + ";" + limbs + ";0", rng.nextLong());
    }

    /** n walkers that stand up within radius of (x, y, z) indoors, on that floor (StoryMarks). */
    public static void walkersIndoors(Placer p, SplittableRandom rng, double x, int y, double z, String outfit, int n, int radius) {
        if (n > 0) {
            p.vehicle(x, y, z, 0, "!walkers:" + outfit + ";" + n + ";" + (-Math.max(1, radius)), rng.nextLong());
        }
    }

    /** The shop's dice if it has a story, else null. */
    private static SplittableRandom roll(Placer p, int w, int d) {
        SplittableRandom dice = new SplittableRandom(seed(p, w, d));
        double r;
        try {
            r = Math.max(0, rate.getAsDouble());
        } catch (RuntimeException e) {
            r = 0.5;
        }
        return dice.nextDouble() < Math.min(1, BASE * r) ? dice : null;
    }

    /** The tale, unless a preview asks for another (-Dshops.tale) to be told wherever it fits. */
    private static Tale forced(Tale tale) {
        String force = System.getProperty("shops.tale");
        return force != null ? Tale.valueOf(force) : tale;
    }

    private void tell(Tale tale) {
        switch (tale) {
            case LOOTED -> looted();
            case TRASHED -> trashed();
            case GUNSTORE_SIEGE -> siege();
            case STAFF_LAST_STAND -> lastStand();
            case BAR -> bar();
            case HAIR_SALON -> salon();
        }
    }

    /** The shop's own seed, from where its corners land in the world (so any chunk gets the same). */
    private static long seed(Placer p, int w, int d) {
        int[] a = p.world(0, 0), b = p.world(w - 1, d - 1);
        long h = a[0] * 73856093L ^ a[1] * 19349663L ^ b[0] * 83492791L ^ b[1] * 2654435761L;
        return h * 0x9E3779B97F4A7C15L + 0x5B0B;
    }

    private static Tale pick(SplittableRandom dice, Shops.Type type) {
        // the trades with a story of their own mostly get it
        if (type == Shops.Type.GUNS && dice.nextDouble() < 0.7) {
            return Tale.GUNSTORE_SIEGE;
        }
        if ((type == Shops.Type.DINER || type == Shops.Type.PIZZA) && dice.nextDouble() < 0.6) {
            return Tale.STAFF_LAST_STAND;
        }
        int total = 0;
        for (Tale t : Tale.values()) {
            total += t.weight;
        }
        int roll = dice.nextInt(total);
        for (Tale t : Tale.values()) {
            if ((roll -= t.weight) < 0) {
                return t;
            }
        }
        return Tale.LOOTED;
    }

    // ---------------------------------------------------------------- the stories

    /** RBShopLooted. */
    private void looted() {
        int pulled = 2 + rng.nextInt(3);
        for (int i = 0; i < 30 && pulled > 0; i++) {
            if (pullShelf()) {
                pulled--;
            }
        }
        scatter("store_spill", 6 + rng.nextInt(6));
        scatter("paper", 3 + rng.nextInt(4));
        scatter("cardboard_box", 1 + rng.nextInt(3));
        openTills();
        for (int x = left; x <= right; x++) {
            if (rng.nextDouble() < 0.6) {
                p.setIfAir(x, 1, 1, "glass_shards");
            }
        }
        if (rng.nextDouble() < 0.6) {
            body("civilian", left, 1, right, back);
        }
        if (rng.nextDouble() < 0.4) {
            walkers("civilian", 1 + rng.nextInt(2));
        }
    }

    /** RBTrashed. */
    private void trashed() {
        scatter("trash_bag", 3 + rng.nextInt(4));
        scatter("paper", 5 + rng.nextInt(5));
        scatter("home_bottles_low", 2 + rng.nextInt(3));
        scatter("home_cans_low", 2 + rng.nextInt(3));
        scatter("store_spill", 3 + rng.nextInt(3));
        scatter("home_chair_tipped", rng.nextInt(3));
        if (rng.nextDouble() < 0.5) {
            int[] c = cell(left, back / 2, right, back);
            if (c != null) {
                put(c[0], c[1], "home_mattress_floor[facing=" + side() + "]");
            }
        }
        if (rng.nextDouble() < 0.3) {
            pullShelf();
        }
    }

    /** RBGunstoreSiege. */
    private void siege() {
        // sandbags along the inside of the glass, a gap at the door they kept to shoot through
        for (int x = left; x <= right; x++) {
            if (Math.abs(x - door) > (pairDoor ? 1 : 0)) {
                p.setIfAir(x, 1, 1, "sandbags[facing=north]");
            }
        }
        for (int x = left; x <= right; x++) {
            p.setIfAir(x, 1, 2, rng.nextDouble() < 0.5 ? "bunk_casings" : rng.nextDouble() < 0.3 ? "glass_shards" : "air");
        }
        scatter("bunk_casings", 8 + rng.nextInt(6));
        scatter("blood_splat", 3 + rng.nextInt(3));
        // the dead in the doorway (the first through, and the police who came for them) and the defenders
        for (int i = 0, n = 2 + rng.nextInt(3); i < n; i++) {
            body(rng.nextDouble() < 0.35 ? "police" : "civilian", door - 2, 1, door + 2, 3);
        }
        for (int i = 0, n = 1 + rng.nextInt(2); i < n; i++) {
            body(rng.nextDouble() < 0.5 ? "survivor" : "civilian", left, 3, right, back);
        }
        walkers("civilian", 1 + rng.nextInt(3));
    }

    /** RBCafe, RBPizzaWhirled. */
    private void lastStand() {
        scatter("dn_plates_broken", 3 + rng.nextInt(4));
        scatter("home_chair_tipped", 2 + rng.nextInt(3));
        scatter("store_spill", 3 + rng.nextInt(3));
        scatter("blood_splat", 2 + rng.nextInt(3));
        openTills();
        // the staff behind the counter at the back half, their customers turned
        for (int i = 0, n = 2 + rng.nextInt(2); i < n; i++) {
            body("civilian", left, back / 2, right, back);
        }
        walkers("civilian", 2 + rng.nextInt(3));
    }

    /** RBBar. */
    private void bar() {
        scatter("home_bottles_low", 4 + rng.nextInt(4));
        scatter("glass_shards", 3 + rng.nextInt(3));
        scatter("home_chair_tipped", 2 + rng.nextInt(3));
        scatter("store_spill", 2 + rng.nextInt(3));
        scatter("blood_splat", 1 + rng.nextInt(3));
        for (int i = 0, n = 2 + rng.nextInt(3); i < n; i++) {
            body("civilian", left, 2, right, back);
        }
        walkers("civilian", 2 + rng.nextInt(3));
    }

    /** RBHairSalon. */
    private void salon() {
        scatter("store_spill", 2 + rng.nextInt(3));
        scatter("paper", 2 + rng.nextInt(3));
        scatter("blood_splat", 1 + rng.nextInt(2));
        scatter("home_chair_tipped", rng.nextInt(2));
        body("civilian", left, 2, right, back);
        walkers("civilian", 1 + rng.nextInt(2));
    }

    // ---------------------------------------------------------------- pieces

    /** Pulls one standing shelf over onto the floor beside it; false if the one tried can't fall. */
    private boolean pullShelf() {
        int x = left + rng.nextInt(right - left + 1), z = 2 + rng.nextInt(Math.max(1, back - 1));
        String b = p.get(x, 1, z);
        if (b == null || !b.startsWith("store_shelf") || b.startsWith("store_shelf_fallen")) {
            return false;
        }
        int to = b.contains("facing=west") ? x - 1 : b.contains("facing=east") ? x + 1 : rng.nextBoolean() ? x - 1 : x + 1;
        if (to < left || to > right || !air(to, z)) {
            return false;
        }
        p.set(x, 1, z, "air");
        if (p.get(x, 2, z) != null && p.get(x, 2, z).startsWith("store_")) {
            p.set(x, 2, z, "air");
        }
        p.set(to, 1, z, "store_shelf_fallen[facing=" + (rng.nextBoolean() ? "north" : "south") + "]");
        p.setIfAir(x, 1, z, "store_spill");
        return true;
    }

    /** The tills left open and empty on the counter (the register's loot table stays, little in it). */
    private void openTills() {
        for (int x = left; x <= right; x++) {
            for (int z = 1; z <= back; z++) {
                String b = p.get(x, 2, z);
                if (b != null && b.startsWith("store_register") && rng.nextDouble() < 0.7) {
                    p.setIfAir(x, 1, Math.max(1, z - 1), "paper");
                }
            }
        }
    }

    private void scatter(String block, int n) {
        for (int i = 0; i < n; i++) {
            int[] c = cell(left, 1, right, back);
            if (c != null) {
                put(c[0], c[1], block.equals("home_chair_tipped") || block.startsWith("home_bottles") || block.startsWith("home_cans")
                        || block.equals("trash_bag") || block.equals("cardboard_box") ? block + "[facing=" + side() + "]" : block);
            }
        }
    }

    /** A body for StoryMarks to lay in the box: its outfit, now and then a limb gone, a pool of blood under it. */
    private void body(String outfit, int x0, int z0, int x1, int z1) {
        int[] c = cell(Math.max(left, x0), Math.max(1, z0), Math.min(right, x1), Math.min(back, z1));
        if (c == null) {
            return;
        }
        body(p, rng, c[0] + 0.5, 1, c[1] + 0.5, outfit);
        if (rng.nextDouble() < 0.6) {
            put(c[0], c[1], rng.nextBoolean() ? "home_blood_pool" : "blood_splat");
        } else {
            taken.add(key(c[0], c[1]));
        }
    }

    /** Walkers that stand up on the shop floor (a negative radius keeps them indoors, off the roof). */
    private void walkers(String outfit, int n) {
        int cx = (left + right) / 2, cz = (1 + back) / 2;
        int radius = Math.max(1, Math.min(right - left, back) / 2);
        walkersIndoors(p, rng, cx + 0.5, 1, cz + 0.5, outfit, n, radius);
    }

    /** A clear floor cell in the box, off the doorway, not yet used by this story. */
    private int[] cell(int x0, int z0, int x1, int z1) {
        if (x1 < x0 || z1 < z0) {
            return null;
        }
        for (int tries = 0; tries < 40; tries++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1), z = z0 + rng.nextInt(z1 - z0 + 1);
            if (air(x, z) && !doorway(x, z) && !taken.contains(key(x, z))) {
                return new int[] {x, z};
            }
        }
        return null;
    }

    private void put(int x, int z, String block) {
        p.set(x, 1, z, block);
        taken.add(key(x, z));
    }

    private boolean air(int x, int z) {
        String b = p.get(x, 1, z);
        return b == null || b.equals("air");
    }

    private boolean doorway(int x, int z) {
        return z <= 2 && Math.abs(x - door) <= (pairDoor ? 2 : 1);
    }

    private String side() {
        return switch (rng.nextInt(4)) {
            case 0 -> "north";
            case 1 -> "east";
            case 2 -> "south";
            default -> "west";
        };
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ (z & 0xffffffffL);
    }
}
