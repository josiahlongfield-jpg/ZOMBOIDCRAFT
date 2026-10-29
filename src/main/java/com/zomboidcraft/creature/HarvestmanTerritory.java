package com.zomboidcraft.creature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * What one Harvestman knows of its ground, learned as it goes (HarvestmanGoals): an 8-block cube at a time, where
 * people pass and when (by night or by day), how lit it is, where the dead gather, where it's been hurt or shot at,
 * where it's lain in wait and whether anyone came, where it's fed, which places it's marked as its own and when it
 * last went there. And the people it's met: how often, what they've done to it, whether its warnings worked. Kept with
 * its record (HarvestmanDirector), so it's not lost when it travels or the world is closed.
 */
final class HarvestmanTerritory {
    static final int CELL = 8, MAX_CELLS = 600;
    /** How long what it's learned of a place holds (ticks): people's comings and goings fade over three days. */
    private static final double FADE = 24_000 * 3;

    /** One cube of ground. */
    static final class Cell {
        final int x, y, z;
        /** People seen passing, by night and by day; when last. */
        float night, day;
        long lastPerson;
        /** The most light it's seen there (block light). */
        int light;
        /** The dead seen there, and when last. */
        float dead;
        long lastDead;
        /** Hurt or shot at there; how its waits there have gone (+ someone came, - nobody did); fed there. */
        float danger, waits;
        int fed;
        /** When it last went through; whether it's marked it. */
        long visited;
        boolean marked;
        /** A floor point in it (where it last saw the ground), for going back to. */
        int fx, fy, fz;
        boolean hasFloor;

        Cell(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 centre() {
            return hasFloor ? new Vec3(fx + 0.5, fy + 1, fz + 0.5) : new Vec3(x * CELL + CELL / 2.0, y * CELL + CELL / 2.0, z * CELL + CELL / 2.0);
        }

        /** People passing here, faded by how long ago; by night or day. */
        double traffic(long now, boolean byDay) {
            double age = Math.max(0, now - lastPerson);
            return (byDay ? day + night * 0.3 : night + day * 0.3) * Math.exp(-age / FADE);
        }

        double deadNow(long now) {
            return dead * Math.exp(-Math.max(0, now - lastDead) / (24_000.0 / 2));
        }

        /** Worth keeping in mind (the least is forgotten first). */
        double worth(long now) {
            return traffic(now, false) + traffic(now, true) + deadNow(now) + Math.abs(waits) + danger + fed + (marked ? 1 : 0)
                    - Math.max(0, now - visited) / (24_000.0 * 4);
        }
    }

    /** One person it's met. */
    static final class Known {
        String name = "";
        int seen, heeded, ignored, warned;
        double hurtMe;
        long lastSeen;
    }

    final Map<Long, Cell> cells = new HashMap<>();
    final Map<UUID, Known> people = new HashMap<>();
    /** Its ground's heart (its den, or where it first came to) and how far it reaches. */
    BlockPos heart;

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFF) << 42 | ((long) y & 0xFFFFF) << 22 | (long) z & 0x3FFFFF;
    }

    Cell cell(Vec3 p) {
        int x = (int) Math.floor(p.x / CELL), y = (int) Math.floor(p.y / CELL), z = (int) Math.floor(p.z / CELL);
        return cells.computeIfAbsent(key(x, y, z), k -> new Cell(x, y, z));
    }

    /** Someone passing a point (on its floor), by night or day. */
    void person(Vec3 floor, long now, boolean byDay, int light) {
        Cell c = cell(floor);
        // fade what was there, then add
        double keep = Math.exp(-Math.max(0, now - c.lastPerson) / FADE);
        c.night = (float) (c.night * keep + (byDay ? 0 : 1));
        c.day = (float) (c.day * keep + (byDay ? 1 : 0));
        c.lastPerson = now;
        c.light = Math.max(c.light, light);
        floor(c, floor);
    }

    /** How many of the dead it can see in a place now (it keeps the most it's seen there, fading). */
    void dead(Vec3 at, long now, int n) {
        Cell c = cell(at);
        c.dead = (float) Math.max(c.deadNow(now), n);
        c.lastDead = now;
        floor(c, at);
    }

    void danger(Vec3 at, double amount) {
        Cell c = cell(at);
        c.danger = (float) Math.min(20, c.danger + amount);
    }

    void been(Vec3 at, long now, int light) {
        Cell c = cell(at);
        c.visited = now;
        c.light = Math.max(c.light, light);
    }

    private static void floor(Cell c, Vec3 p) {
        c.fx = (int) Math.floor(p.x);
        c.fy = (int) Math.floor(p.y) - 1;
        c.fz = (int) Math.floor(p.z);
        c.hasFloor = true;
    }

    Known known(UUID who, String name) {
        Known k = people.computeIfAbsent(who, u -> new Known());
        if (name != null) {
            k.name = name;
        }
        return k;
    }

    /** How many places it knows. */
    int size() {
        return cells.size();
    }

    /** The busiest place people pass at this time of day within reach of a point (for lying in wait), or null. */
    Cell busiest(Vec3 from, double reach, long now, boolean byDay, RandomSource rnd) {
        Cell best = null;
        double bestScore = 0.8;
        for (Cell c : cells.values()) {
            double d = c.centre().distanceTo(from);
            if (d > reach || !c.hasFloor) {
                continue;
            }
            double s = c.traffic(now, byDay) * (1 + 0.25 * c.waits) - c.danger * 0.3 - (c.light >= 11 ? 1.5 : 0) + rnd.nextDouble() * 0.6;
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

    /** Where the dead have been gathering within reach (for feeding), or null. */
    Cell deadest(Vec3 from, double reach, long now, RandomSource rnd) {
        Cell best = null;
        double bestScore = 0.6;
        for (Cell c : cells.values()) {
            double d = c.centre().distanceTo(from);
            if (d > reach || !c.hasFloor) {
                continue;
            }
            double s = c.deadNow(now) - d * 0.02 - c.danger * 0.2 + rnd.nextDouble() * 0.4;
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

    /** Somewhere on its ground it hasn't been for longest, people's paths first (for patrolling), or null. */
    Cell stalest(Vec3 from, double reach, Vec3 heart, double radius, long now, RandomSource rnd) {
        Cell best = null;
        double bestScore = -1e9;
        for (Cell c : cells.values()) {
            Vec3 p = c.centre();
            double d = p.distanceTo(from);
            if (d < 6 || d > reach || !c.hasFloor || p.distanceTo(heart) > radius) {
                continue;
            }
            double s = Math.min(3, (now - c.visited) / 6000.0) + 0.4 * Math.min(3, c.traffic(now, false) + c.traffic(now, true))
                    - c.danger * 0.25 - d * 0.02 + rnd.nextDouble();
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

    /** Forgets the least worth keeping, past the most it holds. */
    void trim(long now) {
        while (cells.size() > MAX_CELLS) {
            long least = 0;
            double few = Double.MAX_VALUE;
            for (Map.Entry<Long, Cell> e : cells.entrySet()) {
                double w = e.getValue().worth(now);
                if (w < few) {
                    few = w;
                    least = e.getKey();
                }
            }
            cells.remove(least);
        }
        if (people.size() > 64) {
            UUID oldest = null;
            long when = Long.MAX_VALUE;
            for (Map.Entry<UUID, Known> e : people.entrySet()) {
                if (e.getValue().lastSeen < when) {
                    when = e.getValue().lastSeen;
                    oldest = e.getKey();
                }
            }
            people.remove(oldest);
        }
    }

    // ------------------------------------------------------------------------------------------------ saving

    JsonObject save() {
        JsonObject o = new JsonObject();
        if (heart != null) {
            o.addProperty("heart", heart.asLong());
        }
        JsonArray a = new JsonArray();
        for (Cell c : cells.values()) {
            JsonArray r = new JsonArray();
            r.add(c.x);
            r.add(c.y);
            r.add(c.z);
            r.add(c.night);
            r.add(c.day);
            r.add(c.lastPerson);
            r.add(c.light);
            r.add(c.dead);
            r.add(c.lastDead);
            r.add(c.danger);
            r.add(c.waits);
            r.add(c.fed);
            r.add(c.visited);
            r.add(c.marked ? 1 : 0);
            r.add(c.hasFloor ? 1 : 0);
            r.add(c.fx);
            r.add(c.fy);
            r.add(c.fz);
            a.add(r);
        }
        o.add("cells", a);
        JsonObject p = new JsonObject();
        for (Map.Entry<UUID, Known> e : people.entrySet()) {
            Known k = e.getValue();
            JsonArray r = new JsonArray();
            r.add(k.name);
            r.add(k.seen);
            r.add(k.heeded);
            r.add(k.ignored);
            r.add(k.warned);
            r.add(k.hurtMe);
            r.add(k.lastSeen);
            p.add(e.getKey().toString(), r);
        }
        o.add("people", p);
        return o;
    }

    static HarvestmanTerritory load(JsonObject o) {
        HarvestmanTerritory t = new HarvestmanTerritory();
        try {
            if (o.has("heart")) {
                t.heart = BlockPos.of(o.get("heart").getAsLong());
            }
            if (o.has("cells")) {
                for (JsonElement e : o.getAsJsonArray("cells")) {
                    JsonArray r = e.getAsJsonArray();
                    Cell c = new Cell(r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt());
                    c.night = r.get(3).getAsFloat();
                    c.day = r.get(4).getAsFloat();
                    c.lastPerson = r.get(5).getAsLong();
                    c.light = r.get(6).getAsInt();
                    c.dead = r.get(7).getAsFloat();
                    c.lastDead = r.get(8).getAsLong();
                    c.danger = r.get(9).getAsFloat();
                    c.waits = r.get(10).getAsFloat();
                    c.fed = r.get(11).getAsInt();
                    c.visited = r.get(12).getAsLong();
                    c.marked = r.get(13).getAsInt() != 0;
                    c.hasFloor = r.get(14).getAsInt() != 0;
                    c.fx = r.get(15).getAsInt();
                    c.fy = r.get(16).getAsInt();
                    c.fz = r.get(17).getAsInt();
                    t.cells.put(key(c.x, c.y, c.z), c);
                }
            }
            if (o.has("people")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("people").entrySet()) {
                    JsonArray r = e.getValue().getAsJsonArray();
                    Known k = new Known();
                    k.name = r.get(0).getAsString();
                    k.seen = r.get(1).getAsInt();
                    k.heeded = r.get(2).getAsInt();
                    k.ignored = r.get(3).getAsInt();
                    k.warned = r.get(4).getAsInt();
                    k.hurtMe = r.get(5).getAsDouble();
                    k.lastSeen = r.get(6).getAsLong();
                    t.people.put(UUID.fromString(e.getKey()), k);
                }
            }
        } catch (RuntimeException e) {
            com.zomboidcraft.ZomboidCraft.LOGGER.warn("A Harvestman's memory of its ground couldn't be read; it starts again", e);
        }
        return t;
    }
}
