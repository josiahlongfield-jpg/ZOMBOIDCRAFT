package com.zomboidcraft.creature;

import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a Harvestman is about when it isn't hunting (every one but the hunter, and the hunter while its prey is away):
 * a goal it keeps for minutes at a time and works toward, every plan a step to it, and a reason for it it could give.
 *
 * <ul>
 *   <li>Hold its ground: it patrols the ground round its den, going where it hasn't been longest and where people pass,
 *   stopping to look and listen, marking the edges with gouges, learning the place (HarvestmanTerritory). It keeps off
 *   the others' ground.</li>
 *   <li>Feed: hungry, it goes where it's seen the dead gather, hunts one down and drags it home to its den to eat.</li>
 *   <li>Lie in wait: over the busiest way people pass at this time of day, in the dark (a ceiling over it if it can),
 *   it settles, faces the way they'll come and stays still. Whether anyone came teaches it where to wait.</li>
 *   <li>Tend its den: wounded, or by day, it goes home, mends, works its scrap and keeps the place.</li>
 *   <li>Shelter: by day, far from its den, it gets in under cover, into the dark, and waits for night. Never out on a
 *   wall under the sky.</li>
 * </ul>
 *
 * Someone who crosses its path it deals with (HarvestmanChoice): drives them out of its den, warns them off its ground,
 * shadows them from the dark to learn them, keeps out of their way, lets them pass under where it waits, or, starving
 * and given the chance, takes them. What they do back (heeding a warning, shooting it) it remembers.
 */
final class HarvestmanGoals {
    static final int HOLD = 0, FEED = 1, WAIT = 2, DEN = 3, SHELTER = 4;
    static final String[] GOALS = {"holding its ground", "feeding", "lying in wait", "tending its den", "sheltering till dark"};
    /** How far it notices someone: seen, or heard close. */
    private static final double NOTICE = 28, HEAR = 10;

    private final HarvestmanMind m;
    private final HarvestmanEntity h;
    /** Its ground, when it has no record yet to keep it in. */
    private final HarvestmanTerritory spare = new HarvestmanTerritory();

    int goal = HOLD;
    String why = "it's finding its way round";
    private long goalSince = -100_000, nextThink;
    private int senses;

    // the one it's dealing with
    private UUID meet;
    private int meetChoice = -1;
    private String meetWhy = "";
    private long meetAt;
    private double warnedFrom;
    private final Map<UUID, Long> warnedAt = new HashMap<>();

    // the step it's on
    private int stops, stopTicks, holdTicks;
    private Vec3 over, lookFrom;
    private HarvestmanTerritory.Cell at;
    private boolean someoneCame;

    HarvestmanGoals(HarvestmanMind m) {
        this.m = m;
        this.h = m.h;
    }

    HarvestmanTerritory land() {
        HarvestmanTerritory t = HarvestmanDirector.land(h);
        if (t == null) {
            t = spare;
        }
        if (t.heart == null || m.lair != null && !m.lair.equals(t.heart)) {
            t.heart = m.lair != null ? m.lair : h.blockPosition();
        }
        return t;
    }

    Vec3 heart() {
        return Vec3.atCenterOf(land().heart);
    }

    /** How far its ground reaches: it grows with it, and stops short of the next one's. */
    double radius() {
        double r = 40 + 12 * m.stage;
        Vec3 heart = heart();
        for (BlockPos other : HarvestmanDirector.otherDens(h)) {
            r = Math.min(r, Math.max(20, Vec3.atCenterOf(other).distanceTo(heart) * 0.55));
        }
        return r;
    }

    static boolean day(ServerLevel level) {
        return HarvestmanNights.night(level) < 0;
    }

    /** Its goal and why, said plainly. */
    String describe() {
        String s = GOALS[goal] + ": " + why;
        if (meet != null && meetChoice >= 0 && h.level().getGameTime() - meetAt < 20 * 30) {
            s += "; chose to " + HarvestmanChoice.NAMES[meetChoice] + " someone (" + meetWhy + ")";
        }
        return s + "; knows " + land().size() + " places";
    }

    // ------------------------------------------------------------------------------------------------ deciding its goal

    /** Weighs its goals by how it is and what it knows, keeping to the one it has a while. */
    void think(ServerLevel level) {
        long now = level.getGameTime();
        HarvestmanTerritory land = land();
        boolean byDay = day(level);
        Vec3 c = h.centre();
        double health = h.getHealth() / h.getMaxHealth();
        int legsGone = Integer.bitCount(h.lost()), cut = Integer.bitCount(h.cut());
        double home = c.distanceTo(heart());
        double[] w = new double[GOALS.length];
        String[] whys = new String[GOALS.length];

        w[FEED] = m.hunger < 0.45 ? 0 : (m.hunger - 0.35) * 3.5;
        HarvestmanTerritory.Cell dead = land.deadest(c, radius() * 1.3, now, h.getRandom());
        if (dead != null) {
            w[FEED] += 0.25;
        }
        whys[FEED] = String.format("hungry (%.0f%%)%s", m.hunger * 100, dead != null ? ", and it knows where the dead gather" : "");

        w[DEN] = (1 - health) * 3 + legsGone * 1.5 + cut * 0.4 + (byDay && home < 64 ? 0.9 : 0);
        whys[DEN] = legsGone > 0 ? "it's lost a leg and goes home to grow it back" : health < 0.9 ? "it's hurt and goes home to mend"
                : cut > 0 ? "it's lost a limb and goes home to mend" : "day has come, and it keeps to its den by day";

        w[SHELTER] = byDay && home >= 64 ? 1.1 + m.wary * 0.5 : 0;
        whys[SHELTER] = "day has come, too far from its den: it gets under cover in the dark";

        HarvestmanTerritory.Cell busy = land.busiest(c, radius(), now, byDay, h.getRandom());
        w[WAIT] = (byDay ? 0.3 : 0.75) + m.ambushBias * 0.8 + m.patience * 0.3 + (busy != null ? 0.6 : -0.6)
                - (m.hunger > 0.8 ? 0.6 : 0);
        whys[WAIT] = busy != null ? "people pass a place it knows" : "it waits by a way in";

        w[HOLD] = 0.55 + m.restless * 0.6 + (land.size() < 40 ? 0.7 : 0) + (byDay ? 0 : 0.3);
        whys[HOLD] = land.size() < 40 ? "it's still learning its ground" : "it keeps its ground and learns who comes";

        // it keeps to what it's doing for a while, unless something presses
        if (now - goalSince < 20 * 150) {
            w[goal] += 0.5;
        }
        int best = HOLD;
        double bestV = Double.NEGATIVE_INFINITY;
        for (int g = 0; g < w.length; g++) {
            double v = w[g] + h.getRandom().nextDouble() * 0.2;
            if (v > bestV) {
                bestV = v;
                best = g;
            }
        }
        if (best != goal) {
            goal = best;
            goalSince = now;
            stops = 0;
        }
        why = whys[goal];
        nextThink = now + 20 * 60;
    }

    /**
     * Its next step when it isn't hunting (HarvestmanMind.pick): a plan toward its goal. A noise it heard close by it
     * goes to see about if its goal lets it.
     */
    int next(ServerLevel level, Vec3 heard, long heardAt) {
        long now = level.getGameTime();
        if (now >= nextThink) {
            think(level);
        }
        Vec3 c = h.centre();
        boolean fresh = heard != null && now - heardAt < 20 * 8;
        if (fresh && (goal == HOLD || goal == FEED) && heard.distanceTo(heart()) < radius() * 1.2) {
            // something on its ground: it goes to see what (food, or someone)
            return h.getRandom().nextDouble() < 0.35 + m.curious * 0.5 ? HarvestmanMind.INVESTIGATE : HarvestmanMind.LISTEN;
        }
        if (fresh && (goal == WAIT || goal == SHELTER || goal == DEN)) {
            return HarvestmanMind.LISTEN;
        }
        return switch (goal) {
            case FEED -> nearestDead(level, 28) != null ? HarvestmanMind.ZOMBIE : HarvestmanMind.FORAGE;
            case WAIT -> HarvestmanMind.LIE_IN_WAIT;
            case DEN -> {
                if (m.lair == null || c.distanceToSqr(Vec3.atCenterOf(m.lair)) > 9 * 9) {
                    yield m.lair != null ? HarvestmanMind.LAIR : HarvestmanMind.SHELTER;
                }
                yield h.getHealth() < h.getMaxHealth() * 0.95 || h.lost() != 0 || h.cut() != 0 ? HarvestmanMind.REST : HarvestmanMind.TEND;
            }
            case SHELTER -> HarvestmanMind.SHELTER;
            default -> HarvestmanMind.PATROL;
        };
    }

    // ------------------------------------------------------------------------------------------------ starting a step

    /** Starts one of its goal plans; false if there's nowhere to do it from here. */
    boolean begin(ServerLevel level, int plan) {
        Vec3 c = h.centre();
        long now = level.getGameTime();
        stopTicks = 0;
        holdTicks = 0;
        someoneCame = false;
        at = null;
        over = null;
        switch (plan) {
            case HarvestmanMind.PATROL -> {
                Vec3 to = waypoint(level);
                if (to == null) {
                    return false;
                }
                m.start(plan, to, 1.7);
                stops = 0;
                return true;
            }
            case HarvestmanMind.FORAGE -> {
                HarvestmanTerritory.Cell dead = land().deadest(c, radius() * 1.3, now, h.getRandom());
                Vec3 to = dead != null ? surfaceNear(level, dead.centre()) : waypoint(level);
                if (to == null) {
                    return false;
                }
                m.start(plan, to, 2.0);
                return true;
            }
            case HarvestmanMind.LIE_IN_WAIT -> {
                boolean byDay = day(level);
                HarvestmanTerritory.Cell busy = land().busiest(c, radius(), now, byDay, h.getRandom());
                Vec3 path = busy != null ? busy.centre() : wayIn(level);
                if (path == null) {
                    return false;
                }
                Vec3 site = siteOver(level, path);
                if (site == null) {
                    return false;
                }
                at = busy;
                over = path;
                m.start(plan, site, 1.8);
                return true;
            }
            case HarvestmanMind.SHELTER -> {
                Vec3 to = covered(level, c, 22);
                if (to == null) {
                    return false;
                }
                m.start(plan, to, 2.2);
                return true;
            }
            case HarvestmanMind.TEND -> {
                if (m.lair == null) {
                    return false;
                }
                m.start(plan, aroundDen(level), 1.1);
                stops = 0;
                return true;
            }
            case HarvestmanMind.WARN, HarvestmanMind.OBSERVE -> {
                Player p = level.getNearestPlayer(h, 48);
                if (!(p instanceof ServerPlayer sp) || sp.isSpectator()) {
                    return false;
                }
                return plan == HarvestmanMind.WARN ? warn(level, sp) : observe(level, sp);
            }
            default -> {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ each step

    void act(ServerLevel level) {
        switch (m.plan) {
            case HarvestmanMind.PATROL -> patrol(level);
            case HarvestmanMind.FORAGE -> forage(level);
            case HarvestmanMind.LIE_IN_WAIT -> lieInWait(level);
            case HarvestmanMind.SHELTER -> shelter(level);
            case HarvestmanMind.TEND -> tend(level);
            case HarvestmanMind.WARN -> warning(level);
            case HarvestmanMind.OBSERVE -> observing(level);
            default -> {
            }
        }
    }

    /** Patrolling: to a waypoint, a stop there to look and listen (marking the edge of its ground), on to the next. */
    private void patrol(ServerLevel level) {
        Vec3 c = h.centre();
        if (m.phase == 0) {
            if (h.goal == null || m.near(h.goal, 1.6) || m.phaseTicks > 20 * 30) {
                m.phase = 1;
                m.phaseTicks = 0;
                h.goal = null;
                stopTicks = 60 + h.getRandom().nextInt(120);
                // the edge of its ground: it scores its mark there
                HarvestmanTerritory.Cell here = land().cell(c);
                if (!here.marked && c.distanceTo(heart()) > radius() * 0.55 && h.getRandom().nextFloat() < 0.45f) {
                    h.signs.mark(level);
                    here.marked = true;
                }
            }
            return;
        }
        // stopped: it turns its head about, listening
        if (m.phaseTicks % 30 == 1) {
            Vec3 d = m.randomDir().multiply(1, 0.3, 1);
            h.look = c.add(d.scale(6));
        }
        if (m.phaseTicks >= stopTicks) {
            stops++;
            if (stops >= 3 + h.getRandom().nextInt(4)) {
                m.end();
                m.wait = 20 + h.getRandom().nextInt(40);
                return;
            }
            Vec3 to = waypoint(level);
            if (to == null) {
                m.end();
                return;
            }
            m.phase = 0;
            m.phaseTicks = 0;
            h.look = null;
            h.goal = to;
            h.goalSpeed = 1.7;
        }
    }

    /** Looking for the dead: to where they gather, watching for one; it goes for the first it sees. */
    private void forage(ServerLevel level) {
        if (m.planTicks % 10 == 0) {
            Zombie z = nearestDead(level, 28);
            if (z != null) {
                m.end();
                m.startStrike(level, z);
                return;
            }
        }
        if (h.goal != null && !m.near(h.goal, 1.6) && m.planTicks < 20 * 45) {
            return;
        }
        h.goal = null;
        if (m.phaseTicks % 30 == 1) {
            h.look = h.centre().add(m.randomDir().multiply(1, 0.2, 1).scale(8));
        }
        if (++holdTicks > 80) {
            m.end();
            m.wait = 10;
        }
    }

    /** Lying in wait: to its place over the way they pass, then dead still, facing it, till someone comes or it gives up. */
    private void lieInWait(ServerLevel level) {
        Vec3 c = h.centre();
        long now = level.getGameTime();
        if (m.phase == 0) {
            if (h.goal == null || m.near(h.goal, 1.2) || m.planTicks > 20 * 40) {
                m.phase = 1;
                m.phaseTicks = 0;
                h.goal = null;
                holdTicks = 20 * (90 + (int) (150 * m.patience));
            }
            return;
        }
        h.goal = null;
        Player passing = null;
        if (over != null) {
            passing = level.getNearestPlayer(over.x, over.y, over.z, 10, p -> !p.isSpectator() && !((Player) p).isCreative());
        }
        h.look = passing != null ? passing.getEyePosition() : over;
        if (passing != null) {
            someoneCame = true;
        }
        if (m.phaseTicks >= holdTicks) {
            // what it learned: whether anyone came this way while it waited
            if (at != null) {
                at.waits = Math.max(-5, Math.min(5, at.waits + (someoneCame ? 1 : -0.5f)));
            }
            m.ambushBias = Math.max(0.1, Math.min(0.9, m.ambushBias + (someoneCame ? 0.03 : -0.02)));
            m.end();
            m.wait = 20;
            if (now >= nextThink - 20 * 30) {
                nextThink = now;    // and thinks again what to do
            }
        }
    }

    /** Sheltering: in under cover in the dark, still, till nightfall (or till it's been a good while, to think again). */
    private void shelter(ServerLevel level) {
        if (m.phase == 0) {
            if (h.goal == null || m.near(h.goal, 1.4) || m.planTicks > 20 * 40) {
                m.phase = 1;
                m.phaseTicks = 0;
                h.goal = null;
            }
            return;
        }
        h.goal = null;
        if (m.phaseTicks % 100 == 0 && (!day(level) || m.phaseTicks > 20 * 300)) {
            m.end();
            nextThink = level.getGameTime();
        }
    }

    /** Tending its den: slow about it, stopping at its heaps and nest a while, now and then working the scrap. */
    private void tend(ServerLevel level) {
        if (m.lair == null) {
            m.end();
            return;
        }
        if (m.phase == 0) {
            if (h.goal == null || m.near(h.goal, 1.4) || m.phaseTicks > 20 * 15) {
                m.phase = 1;
                m.phaseTicks = 0;
                h.goal = null;
                stopTicks = 20 * (6 + h.getRandom().nextInt(9));
                BlockPos heap = HarvestmanDens.scrap(level, m.lair, 7);
                h.look = heap != null && h.getRandom().nextBoolean() ? Vec3.atCenterOf(heap) : null;
            }
            return;
        }
        if (h.look != null && m.phaseTicks % 50 == 22 && h.getRandom().nextFloat() < 0.5f) {
            h.rearWant = 0.4;
            level.playSound(null, h.look.x, h.look.y, h.look.z, HarvestmanSounds.CLANG, SoundSource.HOSTILE, 0.4f, 0.8f);
        } else if (m.phaseTicks % 50 == 30) {
            h.rearWant = 0;
        }
        if (m.phaseTicks >= stopTicks) {
            h.rearWant = 0;
            if (++stops >= 4 + h.getRandom().nextInt(4)) {
                m.end();
                nextThink = level.getGameTime();
                return;
            }
            m.phase = 0;
            m.phaseTicks = 0;
            h.goal = aroundDen(level);
            h.goalSpeed = 1.1;
        }
    }

    // ------------------------------------------------------------------------------------------------ someone on its path

    /** Warning them off: where they'll see it, between them and its den; it rages, hisses, and watches what they do. */
    private boolean warn(ServerLevel level, ServerPlayer p) {
        Vec3 c = h.centre(), eye = p.getEyePosition();
        double d = c.distanceTo(eye);
        Vec3 to = null;
        if (d < 8 || d > 22 || !h.canSee(eye)) {
            Vec3 toward = heart().subtract(p.position()).multiply(1, 0, 1);
            toward = toward.lengthSqr() < 1e-4 ? c.subtract(p.position()).multiply(1, 0, 1) : toward;
            to = surfaceNear(level, p.position().add(toward.normalize().scale(12)));
        }
        m.start(HarvestmanMind.WARN, to, 3.0);
        m.quarry = p;
        warnedFrom = p.position().distanceTo(heart());
        HarvestmanTerritory.Known k = land().known(p.getUUID(), p.getName().getString());
        k.warned++;
        warnedAt.put(p.getUUID(), level.getGameTime());
        return true;
    }

    private void warning(ServerLevel level) {
        if (!(m.quarry instanceof ServerPlayer p) || !p.isAlive() || p.level() != level) {
            m.end();
            return;
        }
        Vec3 eye = p.getEyePosition();
        switch (m.phase) {
            case 0 -> {
                h.look = eye;
                if (h.goal == null || m.near(h.goal, 1.6) || m.phaseTicks > 20 * 8) {
                    m.phase = 1;
                    m.phaseTicks = 0;
                    h.goal = null;
                    HarvestmanTerritory.Known k = land().known(p.getUUID(), null);
                    // it shows itself: up, the red let up, and its rage
                    h.rearWant = 0.7;
                    if (!h.rage(eye, k.warned <= 1 || h.getRandom().nextFloat() < 0.5f)) {
                        Vec3 c = h.centre();
                        level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.HISS, SoundSource.HOSTILE, 1.6f, 0.75f);
                    }
                }
            }
            case 1 -> {
                h.look = eye;
                h.goal = null;
                if (m.phaseTicks > 20 * 5) {
                    m.phase = 2;
                    m.phaseTicks = 0;
                    h.rearWant = 0;
                }
            }
            default -> {
                // it watches what they do: back off, and it lets them go; come on, and it remembers
                h.look = eye;
                h.goal = null;
                if (m.phaseTicks > 20 * 10) {
                    double now = p.position().distanceTo(heart());
                    HarvestmanTerritory.Known k = land().known(p.getUUID(), p.getName().getString());
                    if (now > warnedFrom + 4) {
                        k.heeded++;
                    } else if (now < warnedFrom - 2) {
                        k.ignored++;
                    }
                    m.end();
                    m.start(HarvestmanMind.HIDE, m.hideFrom(level, p), 2.6);
                    meetAt = level.getGameTime() - 20 * 6;   // and looks at them again soon
                }
            }
        }
    }

    /** Shadowing them from the dark, well back, to learn them. */
    private boolean observe(ServerLevel level, ServerPlayer p) {
        Vec3 to = watchSpot(level, p);
        m.start(HarvestmanMind.OBSERVE, to, 1.6);
        m.quarry = p;
        holdTicks = 20 * (60 + (int) (90 * m.curious));
        return true;
    }

    private void observing(ServerLevel level) {
        if (!(m.quarry instanceof ServerPlayer p) || !p.isAlive() || p.level() != level || p.distanceToSqr(h) > 44 * 44) {
            m.end();
            return;
        }
        h.look = p.getEyePosition();
        if (m.watched()) {
            h.goal = null;
            if (m.watchedTicks > 40) {
                // seen at it: it slips away out of their sight
                m.wary = Math.min(1, m.wary + 0.04);
                m.end();
                m.start(HarvestmanMind.HIDE, m.hideFrom(level, p), 2.8);
            }
            return;
        }
        if (m.planTicks % 40 == 0 || h.goal == null && m.planTicks % 10 == 0) {
            h.goal = watchSpot(level, p);
            h.goalSpeed = 1.6;
        }
        if (m.planTicks > holdTicks) {
            m.end();
            m.wait = 20;
        }
    }

    /** Sees and hears who's about, learns its ground, and decides about anyone who crosses its path. */
    void sense(ServerLevel level) {
        senses++;
        long now = level.getGameTime();
        Vec3 c = h.centre();
        HarvestmanTerritory land = land();
        boolean byDay = day(level);
        if (senses % 4 == 0) {
            land.been(c, now, level.getBrightness(LightLayer.BLOCK, BlockPos.containing(c)));
        }
        if (senses % 8 == 0) {
            Map<Long, Integer> dead = new HashMap<>();
            Map<Long, Vec3> where = new HashMap<>();
            for (Zombie z : level.getEntitiesOfClass(Zombie.class, h.getBoundingBox().inflate(24), z -> ZombieTypes.isWalker(z) && z.isAlive())) {
                if (!h.canSee(z.getEyePosition())) {
                    continue;
                }
                HarvestmanTerritory.Cell cell = land.cell(z.position());
                long k = HarvestmanTerritory.key(cell.x, cell.y, cell.z);
                dead.merge(k, 1, Integer::sum);
                where.putIfAbsent(k, z.position());
            }
            for (Map.Entry<Long, Integer> e : dead.entrySet()) {
                land.dead(where.get(e.getKey()), now, e.getValue());
            }
            land.trim(now);
        }
        if (h.held() != null) {
            return;
        }
        ServerPlayer target = m.target(level);
        ServerPlayer nearest = null;
        double nd = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive()) {
                continue;
            }
            double d = p.position().distanceTo(c);
            if (d > NOTICE) {
                continue;
            }
            boolean seen = h.canSee(p.getEyePosition());
            if (!seen && d > HEAR && !(p.isSprinting() && d < 18)) {
                continue;
            }
            if (senses % 4 == 0) {
                // where people pass, and who
                land.person(p.position(), now, byDay, level.getBrightness(LightLayer.BLOCK, p.blockPosition()));
                HarvestmanTerritory.Known k = land.known(p.getUUID(), p.getName().getString());
                if (now - k.lastSeen > 20 * 60) {
                    k.seen++;
                }
                k.lastSeen = now;
            }
            if (p != target && d < nd) {
                nd = d;
                nearest = p;
            }
        }
        if (nearest != null && !m.showcase) {
            meet(level, nearest, target);
        }
    }

    /** Someone has crossed its path: it decides what to do about them (again, now and then, while they're about). */
    private void meet(ServerLevel level, ServerPlayer p, ServerPlayer target) {
        int plan = m.plan;
        if (plan == HarvestmanMind.STRIKE || plan == HarvestmanMind.ZOMBIE && m.phase > 0 || plan == HarvestmanMind.DRAG
                || plan == HarvestmanMind.FEED || plan == HarvestmanMind.FLEE || plan == HarvestmanMind.LEAVE_BODY
                || target != null && (HarvestmanTricks.hunting(plan) || plan == HarvestmanMind.STALK || plan == HarvestmanMind.AMBUSH
                || plan == HarvestmanMind.GLIMPSE || plan == HarvestmanMind.FEINT || plan == HarvestmanMind.TRAVEL)) {
            return;     // busy with what it's at
        }
        long now = level.getGameTime();
        Vec3 c = h.centre(), heart = heart();
        double toHeart = p.position().distanceTo(heart);
        boolean inDen = m.lair != null && toHeart < 12, inCore = toHeart < 28;
        boolean again = p.getUUID().equals(meet);
        // it keeps to a choice a while, unless they come into its den or the heart of its ground
        if (again && now - meetAt < 20 * 8 && !(inDen && meetChoice != HarvestmanChoice.STRIKE)) {
            return;
        }
        if (again && plan == HarvestmanMind.WARN) {
            return;     // it's still waiting to see what they make of its warning
        }
        HarvestmanChoice.Seen s = new HarvestmanChoice.Seen();
        s.dist = p.position().distanceTo(c);
        s.inDen = inDen;
        s.inCore = inCore;
        s.others = level.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(12), o -> o != p && !o.isSpectator()).size();
        s.aware = HarvestmanMind.lookingAt(p, c);
        s.lit = level.getBrightness(LightLayer.BLOCK, p.blockPosition()) >= 9 || light(p.getMainHandItem()) || light(p.getOffhandItem());
        s.armed = GunType.isGun(p.getMainHandItem()) || GunType.isGun(p.getOffhandItem());
        s.theirHealth = p.getHealth() / Math.max(1, p.getMaxHealth());
        s.itsHealth = h.getHealth() / h.getMaxHealth();
        s.legsGone = Integer.bitCount(h.lost());
        s.hunger = m.hunger;
        s.bold = m.bold;
        s.wary = m.wary;
        s.curious = m.curious;
        s.patience = m.patience;
        s.gunNerve = m.gunNerve;
        s.lightNerve = m.lightNerve;
        HarvestmanTerritory.Known k = land().known(p.getUUID(), p.getName().getString());
        s.hurtMe = k.hurtMe;
        s.timesSeen = k.seen;
        s.heeded = k.heeded;
        s.ignored = k.ignored;
        s.grace = HarvestmanDirector.grace(level);
        s.huntingOther = HarvestmanDirector.isHunter(h);
        s.waiting = plan == HarvestmanMind.LIE_IN_WAIT && m.phase == 1 || plan == HarvestmanMind.TEND || plan == HarvestmanMind.REST
                || plan == HarvestmanMind.SHELTER && m.phase == 1;
        s.day = day(level);
        // under where it waits: the chance is better (it's above them, and they don't look up)
        if (s.waiting && plan == HarvestmanMind.LIE_IN_WAIT && s.dist < 6 && c.y > p.getEyeY()) {
            s.aware = false;
        }
        // it only warns the same person every so often
        Long lastWarn = warnedAt.get(p.getUUID());
        double[] noise = new double[5];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = h.getRandom().nextDouble();
        }
        HarvestmanChoice.Choice ch = HarvestmanChoice.decide(s, noise);
        int choice = ch.what();
        if (choice == HarvestmanChoice.WARN && lastWarn != null && now - lastWarn < 20 * 40) {
            choice = s.inDen ? HarvestmanChoice.STRIKE : HarvestmanChoice.WATCH;
        }
        meet = p.getUUID();
        meetAt = now;
        meetChoice = choice;
        meetWhy = HarvestmanChoice.NAMES[choice].equals(ch.name()) ? ch.why() : "it warned them a moment ago";
        act(level, p, choice);
    }

    private void act(ServerLevel level, ServerPlayer p, int choice) {
        int plan = m.plan;
        if (choice != HarvestmanChoice.LET_PASS) {
            m.wait = 0;     // no pause first: it acts on it now
        }
        switch (choice) {
            case HarvestmanChoice.STRIKE -> {
                m.end();
                m.startStrike(level, p);
            }
            case HarvestmanChoice.WARN -> {
                m.end();
                warn(level, p);
            }
            case HarvestmanChoice.WATCH -> {
                if (plan == HarvestmanMind.OBSERVE && m.quarry == p) {
                    return;
                }
                m.end();
                observe(level, p);
            }
            case HarvestmanChoice.AVOID -> {
                if (plan == HarvestmanMind.HIDE && m.quarry == p) {
                    return;
                }
                m.end();
                m.start(HarvestmanMind.HIDE, m.hideFrom(level, p), 2.6);
                m.quarry = p;
            }
            default -> {
                // it lets them pass: still where it is, its head turned after them
                if (plan == HarvestmanMind.LIE_IN_WAIT || plan == HarvestmanMind.TEND || plan == HarvestmanMind.REST
                        || plan == HarvestmanMind.SHELTER) {
                    return;
                }
                h.goal = null;
                h.look = p.getEyePosition();
                m.wait = 30 + h.getRandom().nextInt(40);
            }
        }
    }

    /** It was hurt: by whom (remembered), and where (it remembers that too). */
    void hurt(ServerLevel level, net.minecraft.world.entity.Entity by, double amount) {
        land().danger(h.centre(), amount / 40);
        if (by instanceof ServerPlayer p) {
            HarvestmanTerritory.Known k = land().known(p.getUUID(), p.getName().getString());
            k.hurtMe += amount;
            k.lastSeen = level.getGameTime();
            meetAt = 0;     // it thinks again about them
        }
    }

    /** It fed here. */
    void fed(ServerLevel level) {
        land().cell(h.centre()).fed++;
    }

    // ------------------------------------------------------------------------------------------------ places

    /** The next place on its patrol: somewhere it hasn't been for longest (people's paths first), or new ground. */
    private Vec3 waypoint(ServerLevel level) {
        Vec3 c = h.centre(), heart = heart();
        double r = radius();
        long now = level.getGameTime();
        HarvestmanTerritory land = land();
        if (land.size() >= 25 && h.getRandom().nextFloat() < 0.65f) {
            HarvestmanTerritory.Cell cell = land.stalest(c, 30, heart, r, now, h.getRandom());
            if (cell != null) {
                Vec3 p = surfaceNear(level, cell.centre());
                if (p != null && ours(p)) {
                    return p;
                }
            }
        }
        // new ground: off in a direction it knows least, kept within its own
        Vec3 best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 10; k++) {
            double a = h.getRandom().nextDouble() * Math.PI * 2, dist = 12 + h.getRandom().nextDouble() * 14;
            Vec3 p = c.add(Math.cos(a) * dist, 0, Math.sin(a) * dist);
            if (p.distanceTo(heart) > r) {
                p = c.add(heart.subtract(c).multiply(1, 0, 1).normalize().scale(dist));
            }
            p = surfaceNear(level, p);
            if (p == null || !ours(p)) {
                continue;
            }
            HarvestmanTerritory.Cell cell = land.cells.get(key(p));
            double stale = cell == null ? 3 : Math.min(3, (now - cell.visited) / 6000.0);
            double lit = level.getBrightness(LightLayer.BLOCK, BlockPos.containing(p)) >= 11 ? 1.5 : 0;
            double sky = day(level) && level.canSeeSky(BlockPos.containing(p).above()) ? 1 : 0;
            double s = stale - lit - sky + h.getRandom().nextDouble();
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        return best;
    }

    private static long key(Vec3 p) {
        int x = (int) Math.floor(p.x / HarvestmanTerritory.CELL), y = (int) Math.floor(p.y / HarvestmanTerritory.CELL),
                z = (int) Math.floor(p.z / HarvestmanTerritory.CELL);
        return HarvestmanTerritory.key(x, y, z);
    }

    /** On its own ground: nearer its own den than any other's. */
    private boolean ours(Vec3 p) {
        double mine = p.distanceToSqr(heart());
        for (BlockPos other : HarvestmanDirector.otherDens(h)) {
            if (p.distanceToSqr(Vec3.atCenterOf(other)) < mine) {
                return false;
            }
        }
        return true;
    }

    /** Where its body goes over the floor at or below a point (within a few blocks), or null. */
    Vec3 surfaceNear(ServerLevel level, Vec3 p) {
        for (double up : new double[] {2.5, 5, 9}) {
            BlockHitResult hit = m.cast(level, p.add(0, up, 0), p.add(0, -10, 0));
            if (hit.getType() == HitResult.Type.MISS || hit.getDirection() != net.minecraft.core.Direction.UP) {
                continue;
            }
            Vec3 at = hit.getLocation().add(0, 1.0, 0);
            if (level.getBlockState(BlockPos.containing(at)).getCollisionShape(level, BlockPos.containing(at)).isEmpty()) {
                return at;
            }
        }
        return null;
    }

    /** Where to wait over a path: under a ceiling over it if there's one, else a dark corner by it out of the light. */
    private Vec3 siteOver(ServerLevel level, Vec3 path) {
        BlockHitResult up = m.cast(level, path.add(0, 1.5, 0), path.add(0, 14, 0));
        if (up.getType() != HitResult.Type.MISS && up.getLocation().y - path.y > 3 && h.getRandom().nextDouble() < 0.35 + m.ceilingBias) {
            return up.getLocation().add(0, -1.2, 0);
        }
        Vec3 dark = darkNear(level, path, 9, true);
        return dark != null ? dark : up.getType() != HitResult.Type.MISS ? up.getLocation().add(0, -1.2, 0) : null;
    }

    /** With nowhere it knows people pass: a way into a building near its den (a doorway's dark side). */
    private Vec3 wayIn(ServerLevel level) {
        Vec3 from = m.lair != null && h.getRandom().nextBoolean() ? heart() : h.centre();
        for (int k = 0; k < 24; k++) {
            Vec3 d = m.randomDir().multiply(1, 0.1, 1);
            BlockHitResult hit = m.cast(level, from, from.add(d.scale(18)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            BlockPos at = hit.getBlockPos();
            if (level.getBlockState(at).getBlock() instanceof net.minecraft.world.level.block.DoorBlock
                    || level.getBlockState(at.below()).getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                return surfaceNear(level, hit.getLocation().subtract(d.scale(1.5)));
            }
        }
        return surfaceNear(level, h.centre().add(m.randomDir().multiply(1, 0, 1).scale(10)));
    }

    /** The darkest covered place it can see round a point: no sky over it, a corner if it can. */
    Vec3 darkNear(ServerLevel level, Vec3 from, double range, boolean cornered) {
        Vec3 best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 30; k++) {
            Vec3 d = m.randomDir();
            BlockHitResult hit = m.cast(level, from, from.add(d.scale(range)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 p = hit.getLocation().add(HarvestmanMind.normal(hit).scale(1.1));
            BlockPos air = BlockPos.containing(p);
            if (level.canSeeSky(air)) {
                continue;
            }
            int light = level.getMaxLocalRawBrightness(air);
            double s = 15 - light + (cornered ? 0.6 * corner(level, air) : 0) - p.distanceTo(from) * 0.05 + h.getRandom().nextDouble() * 0.5;
            if (s > bestScore) {
                bestScore = s;
                best = p;
            }
        }
        return best;
    }

    /** Somewhere under cover in the dark near it, for waiting out the day. */
    private Vec3 covered(ServerLevel level, Vec3 c, double range) {
        Vec3 p = darkNear(level, c, range, true);
        if (p == null) {
            // nothing over it here: it heads for the nearest building it can see and looks again from there
            Vec3 d = m.randomDir().multiply(1, 0, 1);
            p = surfaceNear(level, c.add(d.scale(range)));
        }
        return p;
    }

    private static int corner(ServerLevel level, BlockPos air) {
        int n = 0;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
            if (!level.getBlockState(air.relative(d)).getCollisionShape(level, air.relative(d)).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** A spot about its den. */
    private Vec3 aroundDen(ServerLevel level) {
        Vec3 heart = Vec3.atCenterOf(m.lair);
        for (int k = 0; k < 8; k++) {
            double a = h.getRandom().nextDouble() * Math.PI * 2, r = 1.5 + h.getRandom().nextDouble() * 4;
            Vec3 p = surfaceNear(level, heart.add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (p != null) {
                return p;
            }
        }
        return heart.add(0, HarvestmanEntity.STAND - HarvestmanEntity.MID, 0);
    }

    /** Somewhere well back from them, out of their sight and in the dark, above if it can. */
    private Vec3 watchSpot(ServerLevel level, ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), c = h.centre(), best = null;
        double bestScore = -1e9;
        double want = 18 + 6 * m.wary;
        for (int k = 0; k < 24; k++) {
            Vec3 d = m.randomDir();
            BlockHitResult hit = m.cast(level, eye.add(d.scale(want * 0.5)), eye.add(d.scale(want * 1.3)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 n = HarvestmanMind.normal(hit), at = hit.getLocation().add(n.scale(1.2));
            double dist = at.distanceTo(eye);
            if (dist < 12) {
                continue;
            }
            boolean hidden = m.cast(level, eye, at).getType() != HitResult.Type.MISS
                    || p.getLookAngle().dot(at.subtract(eye).normalize()) < Math.cos(Math.toRadians(55));
            double dark = 15 - level.getMaxLocalRawBrightness(BlockPos.containing(at));
            double s = -Math.abs(dist - want) * 0.25 + dark * 0.15 + (hidden ? 1.5 : 0) + (n.y < -0.5 ? 0.8 * m.ceilingBias : 0)
                    - at.distanceTo(c) * 0.03 + h.getRandom().nextDouble() * 0.4;
            if (s > bestScore) {
                bestScore = s;
                best = at;
            }
        }
        return best;
    }

    private Zombie nearestDead(ServerLevel level, double r) {
        Zombie best = null;
        double bd = r * r;
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, h.getBoundingBox().inflate(r), z -> ZombieTypes.isWalker(z) && z.isAlive())) {
            double d = z.distanceToSqr(h);
            if (d < bd && h.canSee(z.getEyePosition())) {
                bd = d;
                best = z;
            }
        }
        return best;
    }

    /** Something giving light in their hands (a torch, a lantern). */
    private static boolean light(ItemStack stack) {
        return stack.getItem() instanceof BlockItem b && b.getBlock().defaultBlockState().getLightEmission() > 0;
    }
}
