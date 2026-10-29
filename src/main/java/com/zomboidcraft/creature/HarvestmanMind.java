package com.zomboidcraft.creature;

import com.zomboidcraft.zombie.Corpse;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a Harvestman wants and what it does about it. It has needs that drift (hunger, restlessness, curiosity,
 * wariness), traits of its own (how bold, how patient), and what it has learned: how far back to stalk, how much to keep
 * to the ceilings, how often it has been seen. When a plan runs out, or something happens (a noise, a hit, being seen, a
 * chance to strike), it weighs what it could do by how it feels and picks one, with some chance in it.
 *
 * <p>The one it hunts (HarvestmanDirector gives it a target) it stalks from the dark, out of sight and above when it
 * can, freezing when looked at; it waits on ceilings where they pass; now and then it lets itself be glimpsed, or leaves
 * a body where they'll find it. When they are alone and not looking it strikes: it drops or rushes in, rears up and
 * lunges, and drags what it catches somewhere dark to feed. Hurt badly it flees to its lair. Starving, it eats zombies.
 * Every Harvestman roams near its lair, hides in the dark, perches high and listens; only the hunter goes after a
 * player, unless one comes into its lair.
 */
public final class HarvestmanMind {
    public static final int IDLE = 0, WANDER = 1, PERCH = 2, CEILING = 3, HIDE = 4, LISTEN = 5, INVESTIGATE = 6,
            STALK = 7, AMBUSH = 8, STRIKE = 9, DRAG = 10, FEED = 11, FLEE = 12, LAIR = 13, REST = 14, ZOMBIE = 15,
            GLIMPSE = 16, LEAVE_BODY = 17, TRAVEL = 18, FEINT = 19;
    /**
     * How fast it can go when it wants to (blocks a second): closing on prey, vanishing once seen, getting away hurt;
     * faster than a sprinting player when the prey runs; the lunge itself. It creeps everywhere else.
     */
    static final double BURST = 7.5, SPRINT = 9.5, LUNGE = 11;

    public static final String[] PLANS = {"idle", "wander", "perch", "ceiling", "hide", "listen", "investigate", "stalk",
            "ambush", "strike", "drag", "feed", "flee", "lair", "rest", "zombie", "glimpse", "leave_body", "travel", "feint"};
    /** What a plan is, said plainly (/zc harvestman info). */
    private static final String[] SAY = {"waiting", "wandering", "perching high on a wall", "crossing the ceiling",
            "hiding in the dark", "listening", "going to see what it heard", "stalking %s", "waiting above where %s passes",
            "striking at %s", "dragging its catch into the dark", "feeding", "fleeing, hurt", "going back to its lair",
            "resting in its lair", "hunting a zombie to eat", "letting %s glimpse it", "leaving a body for %s to find",
            "travelling toward %s", "drawing %s off with a noise"};

    /** Every direction it feels along: the 26 neighbours of a cube. */
    static final Vec3[] DIRS;

    static {
        java.util.List<Vec3> dirs = new java.util.ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x != 0 || y != 0 || z != 0) {
                        dirs.add(new Vec3(x, y, z).normalize());
                    }
                }
            }
        }
        DIRS = dirs.toArray(new Vec3[0]);
    }

    private final HarvestmanEntity h;

    // needs, 0..1
    double hunger = 0.4, restless = 0.3, curious = 0.4, wary = 0.15, alert;
    // traits
    double bold = 0.5, patience = 0.5;
    // learned
    double stalkDist = 10, ceilingBias = 0.35, ambushBias = 0.3;
    /**
     * How used it is to light and to gunfire (0 it backs off from both, 1 they don't turn it), and how well its feints
     * have worked: it learns these as it goes.
     */
    double lightNerve, gunNerve, feintBias = 0.3;
    int seen, grabs, escapes, feeds, stage = 1;
    /** Its den; where it was made. */
    BlockPos lair;
    /** The heap of scrap it's working while it grows a leg back. */
    private BlockPos heap;
    /** Its identity in HarvestmanDirector's records, and which copy of it this is. */
    String hid = "";
    int gen;

    int plan = IDLE, forced = -1;
    private int planTicks, wait = 20, phase, phaseTicks, senseTicks;
    private Vec3 heard;
    private long heardAt = -10_000;
    private double heardLoud;
    private Entity quarry;
    private Vec3 quarrySeen;
    private int watchedTicks, unseenTicks;
    private boolean watched;
    private Entity carried;
    private long glimpseAt, bodyAt;
    private boolean forcedHunt;
    private double hurtRecent;
    /** Ticks it has stood in bright light; when it may next back off from a gunshot. */
    private int litTicks;
    private long gunShyUntil;
    /** Peeking round a corner: where its body waits, the leg it reaches round with, and until when. */
    private Vec3 peekAt;
    private int peekLeg = -1, peekTicks;
    /** A feint: where the decoy noise was made, and the way they were looking when it was. */
    private Vec3 decoy, decoyLook;
    private long feintAt, doorAt;

    HarvestmanMind(HarvestmanEntity h) {
        this.h = h;
    }

    public String planName() {
        return PLANS[plan];
    }

    /** What it's doing, said plainly. */
    public String doing() {
        String who = quarry instanceof Player p ? p.getName().getString() : quarry != null ? "a zombie" : "someone";
        return String.format(SAY[plan], who);
    }

    public int stage() {
        return stage;
    }

    public boolean forcedHunt() {
        return forcedHunt;
    }

    /** Is someone looking right at it now? */
    boolean watched() {
        return watched;
    }

    /** Is it carrying a body somewhere (to leave it for someone)? */
    boolean carrying() {
        return carried != null;
    }

    /** Makes it take up a plan next (a /zc harvestman test); hunting plans go after the nearest player. */
    /** Has it take up a plan next (the director: back to its den, rest, go after its target). */
    void queue(int plan) {
        forced = plan;
        h.goal = null;
        wait = 0;
        end();
    }

    /**
     * Makes it take up a plan now (/zc harvestman do). Returns null when it has, or why it can't from where it is.
     */
    public String force(ServerLevel level, int plan) {
        forcedHunt = plan == STRIKE || plan == STALK || plan == AMBUSH || plan == GLIMPSE || plan == LEAVE_BODY
                || plan == TRAVEL || plan == FEINT;
        h.goal = null;
        wait = 0;
        end();
        Player near = level.getNearestPlayer(h, 48);
        if (near != null && near.isSpectator()) {
            near = null;
        }
        switch (plan) {
            case DRAG, FEED -> {
                if (h.held() == null) {
                    return "it only drags and feeds once a strike has caught someone (try do strike)";
                }
            }
            case STRIKE, STALK, AMBUSH, GLIMPSE, FEINT -> {
                if (near == null) {
                    return "there's no player within 48 blocks";
                }
            }
            case TRAVEL -> {
                return "it travels on its own when its target is more than 56 blocks away";
            }
            case LISTEN, INVESTIGATE -> {
                // as if it heard the nearest player
                if (near != null) {
                    heard = near.position();
                    heardAt = level.getGameTime();
                    alert = 1;
                }
            }
            default -> {
            }
        }
        forced = plan;
        choose(level);
        if (this.plan != IDLE) {
            return null;
        }
        return switch (plan) {
            case CEILING -> "there's no ceiling within reach to climb to (it needs a roof over it)";
            case PERCH -> "there's no wall rising above it nearby";
            case ZOMBIE -> "there's no zombie within 32 blocks";
            case LEAVE_BODY -> "it needs a body (corpse) within 24 blocks and someone to leave it for";
            case FEINT -> "it needs somewhere off to the far side of them to make its noise";
            case INVESTIGATE, LISTEN -> "there's nobody near for it to hear";
            default -> "it can't find anywhere to do that from here";
        };
    }

    // ------------------------------------------------------------------------------------------------ how it moves now

    public boolean sneaking() {
        return plan == STALK || plan == AMBUSH || plan == HIDE || plan == GLIMPSE || plan == LEAVE_BODY || plan == FEINT
                || plan == STRIKE && phase == 0 && !watched;
    }

    public boolean watching() {
        return h.look != null && (plan == STALK || plan == GLIMPSE || plan == LISTEN || plan == AMBUSH || plan == STRIKE
                || plan == FEINT);
    }

    /**
     * The player it has its attention on from out of the way (stalking them, waiting above them, hiding and watching
     * them pass), as an entity id for their game's unease; -1 for none.
     */
    int watchingId(ServerLevel level) {
        if (plan == STRIKE || plan == DRAG || plan == FEED || plan == FLEE || plan == REST) {
            return -1;
        }
        if ((plan == STALK || plan == AMBUSH || plan == GLIMPSE || plan == FEINT) && quarry instanceof ServerPlayer p && p.isAlive()) {
            return p.distanceToSqr(h) < 40 * 40 ? p.getId() : -1;
        }
        if (plan == HIDE || plan == PERCH || plan == CEILING || plan == IDLE || plan == LISTEN) {
            ServerPlayer t = target(level);
            if (t != null && t.distanceToSqr(h) < 24 * 24 && h.canSee(t.getEyePosition())) {
                return t.getId();
            }
        }
        return -1;
    }

    /** How low it crouches (stalking, hiding), 0..1. */
    public double crouch() {
        return plan == STALK || plan == HIDE || plan == AMBUSH ? 0.5 : plan == STRIKE && phase == 0 ? 0.3 : 0;
    }

    /**
     * How black it makes itself (0 the red showing, 1 all black but its eyes): black whenever it roams, sneaks or hunts;
     * the red comes up when it rears to strike, while it holds and feeds (it lights up), and a little at rest in its den.
     */
    public double camo() {
        if (plan == FEED || plan == DRAG) {
            return 0;
        }
        if (plan == STRIKE && phase >= 1 || plan == ZOMBIE && phase >= 1) {
            return 0.15;
        }
        if (plan == REST) {
            return 0.55;
        }
        return 1;
    }

    public double speedScale() {
        double s = 1 + 0.04 * (stage - 1);
        if (hunger > 0.95) {
            s *= 0.8;
        }
        s *= Math.max(0.35, 1 - 0.22 * Integer.bitCount(h.lost()));
        return s;
    }

    public double accelScale() {
        return plan == STRIKE ? 3 : 1;
    }

    /** What gets through to it: starving, it's weaker. */
    public double toughnessScale() {
        return hunger > 0.95 ? 1.25 : 1;
    }

    // ------------------------------------------------------------------------------------------------ each tick

    void tick(ServerLevel level) {
        planTicks++;
        phaseTicks++;
        // needs drift
        hunger = Math.min(1, hunger + 1.0 / (20 * 60 * 50));
        restless = Math.min(1, restless + 1.0 / (20 * 60 * 3));
        curious = Math.min(1, curious + 1.0 / (20 * 60 * 6));
        wary = Math.max(0.05, wary - 1.0 / (20 * 60 * 4));
        alert = Math.max(0, alert - 1.0 / (20 * 30));
        hurtRecent = Math.max(0, hurtRecent - 1.0);
        if (carried != null) {
            carry();
        }
        if (++senseTicks % 5 == 0) {
            sense(level);
        }
        if (wait > 0) {
            wait--;
            h.goal = null;
            return;
        }
        if (plan == IDLE || h.goal == null && done()) {
            choose(level);
        }
        act(level);
    }

    /** Does the current plan end once its goal is reached? (the ones that wait there end on their own) */
    private boolean done() {
        return switch (plan) {
            case STALK, AMBUSH, STRIKE, DRAG, FEED, LISTEN, REST, GLIMPSE, ZOMBIE, LEAVE_BODY, TRAVEL, FEINT -> false;
            default -> true;
        };
    }

    private void end() {
        stopPeek();
        plan = IDLE;
        phase = 0;
        phaseTicks = 0;
        planTicks = 0;
        h.wantFloor = false;
        h.rearWant = 0;
        h.look = null;
    }

    private void start(int p, Vec3 goal, double speed) {
        stopPeek();
        plan = p;
        phase = 0;
        phaseTicks = 0;
        planTicks = 0;
        h.goal = goal;
        h.goalSpeed = speed;
        h.wantFloor = false;
        h.rearWant = 0;
        h.look = null;
    }

    /** Sees and hears: whether it's being looked at, and how its quarry is doing. */
    private void sense(ServerLevel level) {
        Vec3 c = h.centre();
        boolean was = watched;
        watched = false;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(c) > 40 * 40) {
                continue;
            }
            if (lookingAt(p, c)) {
                watched = true;
                break;
            }
        }
        watchedTicks = watched ? watchedTicks + 5 : 0;
        unseenTicks = watched ? 0 : unseenTicks + 5;
        if (watched && !was) {
            seen++;
            wary = Math.min(1, wary + 0.08);
        }
        if (quarry instanceof Player p && p.isAlive()) {
            quarrySeen = p.position();
        }
        // bright lamplight: at first it won't stay in it (worse when it's being looked at there); it learns to bear it
        int light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, BlockPos.containing(c));
        boolean roaming = plan == STALK || plan == WANDER || plan == INVESTIGATE || plan == AMBUSH || plan == PERCH
                || plan == CEILING || plan == FEINT || plan == IDLE;
        litTicks = light >= 11 && roaming ? litTicks + 5 : 0;
        if (litTicks >= (watched ? 10 : 30) && h.held() == null) {
            litTicks = 0;
            if (h.getRandom().nextDouble() > lightNerve) {
                lightNerve = Math.min(1, lightNerve + 0.06);
                end();
                start(HIDE, darkSpot(level, 18), 2.8);
                level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.HISS, SoundSource.HOSTILE, 0.5f, 1.2f);
            } else {
                lightNerve = Math.min(1, lightNerve + 0.01);
            }
        }
    }

    /** The darkest place it can see round it, for getting out of the light. */
    private Vec3 darkSpot(ServerLevel level, double range) {
        Vec3 c = h.centre(), best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 30; k++) {
            Vec3 d = randomDir();
            BlockHitResult hit = cast(level, c, c.add(d.scale(range)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 p = hit.getLocation().add(normal(hit).scale(1.1));
            double score = -level.getMaxLocalRawBrightness(BlockPos.containing(p)) - p.distanceTo(c) * 0.05 + h.getRandom().nextDouble() * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best != null ? best : spot(HIDE, range);
    }

    /** Is this player looking right at it, and could they make it out (in the light, or close)? */
    static boolean lookingAt(Player p, Vec3 c) {
        Vec3 eye = p.getEyePosition(), to = c.subtract(eye);
        double d = to.length();
        if (d < 1e-3 || p.getLookAngle().dot(to.scale(1 / d)) < Math.cos(Math.toRadians(22))) {
            return false;
        }
        int light = p.level().getMaxLocalRawBrightness(BlockPos.containing(c));
        if (d > 10 && light < 5 && d > 6 + light * 3) {
            return false;   // a dark shape in the dark, too far to make out
        }
        BlockHitResult hit = p.level().clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ------------------------------------------------------------------------------------------------ choosing

    private ServerPlayer target(ServerLevel level) {
        if (forcedHunt) {
            Player p = level.getNearestPlayer(h, 48);
            return p instanceof ServerPlayer sp && !sp.isSpectator() ? sp : null;
        }
        return HarvestmanDirector.targetFor(level, h);
    }

    private void choose(ServerLevel level) {
        end();
        Vec3 c = h.centre();
        ServerPlayer target = target(level);
        int next = forced >= 0 ? forced : -1;
        boolean wasForced = next >= 0;
        forced = -1;
        if (!wasForced) {
            forcedHunt = false;
        }
        if (next < 0) {
            next = pick(level, target);
        }
        quarry = target;
        switch (next) {
            case STALK -> startStalk(level, target);
            case STRIKE -> startStrike(level, target != null ? target : nearestPrey(level));
            case AMBUSH -> startAmbush(level, target);
            case GLIMPSE -> startGlimpse(level, target);
            case LEAVE_BODY -> startLeaveBody(level, target);
            case FEINT -> startFeint(level, target);
            case TRAVEL -> start(TRAVEL, target != null ? target.position().add(0, 2, 0) : null, 3.2);
            case ZOMBIE -> startZombie(level);
            case FLEE, LAIR -> {
                Vec3 to = lair != null ? Vec3.atCenterOf(lair) : spot(HIDE, 20);
                start(next, to, next == FLEE ? 3.6 : 2.0);
            }
            case REST -> {
                start(REST, null, 0);
                wait = 0;
            }
            case LISTEN -> {
                start(LISTEN, null, 0);
                h.look = heard;
            }
            case INVESTIGATE -> start(INVESTIGATE, heard, 1.8);
            default -> {
                Vec3 g = spot(next, wasForced ? 28 : 16);
                if (g == null && wasForced) {
                    g = spot(next, 28);
                }
                if (g == null) {
                    wait = 20;
                    return;
                }
                start(next, g, next == HIDE ? 2.1 : next == WANDER ? 1.6 : 1.9);
            }
        }
        if (plan == IDLE) {
            wait = 20;
        }
    }

    /** Weighs what it could do by how it feels, and picks one (with some chance in it). */
    private int pick(ServerLevel level, ServerPlayer target) {
        Vec3 c = h.centre();
        double health = h.getHealth() / h.getMaxHealth();
        int legsGone = Integer.bitCount(h.lost());
        double[] w = new double[PLANS.length];
        boolean atLair = lair != null && c.distanceToSqr(Vec3.atCenterOf(lair)) < 10 * 10;
        // hurt badly, it doesn't fight on: it goes to ground in its lair and stays there till it has mended
        if (health < 0.35 || legsGone >= 2) {
            if (atLair) {
                return REST;
            }
            return lair != null ? FLEE : HIDE;
        }
        if (atLair && (legsGone > 0 || health < 0.9 || hunger < 0.3 && target == null)) {
            w[REST] = 2 + legsGone + (health < 0.6 ? 4 : 0);
        }
        if (target != null) {
            double d = target.position().distanceTo(c);
            if (d > 56) {
                w[TRAVEL] = 2;
            } else {
                boolean alone = level.getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(12), p -> p != target && !p.isSpectator()).isEmpty();
                boolean backTurned = target.getLookAngle().dot(c.subtract(target.getEyePosition()).normalize()) < 0.2;
                boolean unaware = !lookingAt(target, c);
                if (d < 12 && unaware && (backTurned || d < 6) && (alone || bold > 0.7) && h.canSee(target.getEyePosition())
                        && health > 0.6) {
                    w[STRIKE] = 1.5 + bold;
                }
                if (d > 8 && d < 26 && unaware && level.getGameTime() - feintAt > 20 * 120) {
                    w[FEINT] = 0.25 + 0.5 * feintBias;
                }
                w[STALK] = 1.0;
                w[AMBUSH] = 0.5 * ambushBias + (d > 20 ? 0.2 : 0);
                if (level.getGameTime() - glimpseAt > 20 * 90) {
                    w[GLIMPSE] = 0.25;
                }
            }
            if (level.getGameTime() - bodyAt > 20 * 60 * 5 && d > 24 && nearCorpse(level, 24) != null) {
                w[LEAVE_BODY] = 0.3;
            }
        } else {
            // not hunting: someone in its den is driven out; anyone close is watched from the dark
            Player near = level.getNearestPlayer(h, 20);
            if (near != null && !near.isSpectator() && !near.isCreative()) {
                boolean inLair = lair != null && near.position().distanceToSqr(Vec3.atCenterOf(lair)) < 12 * 12;
                if (inLair && near.position().distanceTo(c) < 12) {
                    quarry = near;
                    return STRIKE;
                }
                w[HIDE] += 1.2 * (0.5 + wary);
                w[PERCH] += 0.4;
            }
            if (lair != null && !atLair && c.distanceToSqr(Vec3.atCenterOf(lair)) > 48 * 48) {
                w[LAIR] = 1.2;
            }
        }
        if (hunger > 0.85 && nearestZombie(level, 32) != null) {
            w[ZOMBIE] = 2 * hunger;
        }
        if (heard != null && level.getGameTime() - heardAt < 20 * 8) {
            w[LISTEN] = 0.8 + alert;
            w[INVESTIGATE] = curious * (1 - wary) * 1.5;
            w[HIDE] += wary * 1.2;
        }
        w[WANDER] += 0.3 + restless;
        w[PERCH] += 0.25 + 0.3 * ceilingBias;
        w[CEILING] += 0.15 + 0.4 * ceilingBias;
        w[HIDE] += 0.3 + wary + (level.getSkyDarken() < 4 ? 0.5 : 0);
        double total = 0;
        for (double v : w) {
            total += v;
        }
        double r = h.getRandom().nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) {
                return i;
            }
        }
        return WANDER;
    }

    // ------------------------------------------------------------------------------------------------ acting

    private void act(ServerLevel level) {
        Vec3 c = h.centre();
        switch (plan) {
            case WANDER, PERCH, CEILING, HIDE, INVESTIGATE -> {
                if (h.goal != null && near(h.goal, 0.9) || planTicks > 20 * 25) {
                    restless = Math.max(0, restless - 0.3);
                    if (plan == INVESTIGATE) {
                        curious = Math.max(0, curious - 0.3);
                        heard = null;
                    }
                    wait = 20 * (plan == HIDE ? 6 + h.getRandom().nextInt(10) : 2 + h.getRandom().nextInt(5));
                    h.goal = null;
                    h.look = heard;
                }
            }
            case LISTEN -> {
                h.goal = null;
                h.look = heard;
                if (planTicks > 20 * 3) {
                    end();
                    wait = 0;
                }
            }
            case FLEE, LAIR -> {
                if (h.goal == null || near(h.goal, 1.5) || planTicks > 20 * 60) {
                    end();
                    wait = 20;
                }
            }
            case REST -> rest(level);
            case TRAVEL -> {
                ServerPlayer t = target(level);
                if (t == null || t.position().distanceTo(c) < 40 || planTicks > 20 * 120) {
                    end();
                } else if (planTicks % 40 == 0) {
                    h.goal = t.position().add(0, 2, 0);
                }
            }
            case STALK -> stalk(level);
            case AMBUSH -> ambush(level);
            case STRIKE -> strike(level);
            case DRAG -> drag(level);
            case FEED -> {
                if (h.held() == null) {
                    end();
                    wait = 40;
                }
            }
            case ZOMBIE -> strike(level);
            case GLIMPSE -> glimpse(level);
            case LEAVE_BODY -> leaveBody(level);
            case FEINT -> feint(level);
            default -> {
            }
        }
    }

    /** Is it at p: within r along its surface, and on the same face (not just under or over it)? */
    private boolean near(Vec3 p, double r) {
        Vec3 to = p.subtract(h.centre());
        double off = to.dot(h.up());
        return off < HarvestmanEntity.REACH_UP && off > -HarvestmanEntity.REACH_OFF && to.subtract(h.up().scale(off)).length() < r;
    }

    // stalking: from the dark, out of their sight, keeping its distance; it freezes when looked at, and slips away if
    // they keep looking
    private void startStalk(ServerLevel level, ServerPlayer target) {
        if (target == null) {
            return;
        }
        start(STALK, stalkSpot(level, target), 1.5);
    }

    private void stalk(ServerLevel level) {
        if (!(quarry instanceof ServerPlayer t) || !t.isAlive() || t.level() != level) {
            end();
            return;
        }
        Vec3 c = h.centre();
        double d = t.position().distanceTo(c);
        h.look = t.getEyePosition();
        if (watched && peekAt != null && peekTicks > 8) {
            // caught looking round the corner: it draws back out of sight
            wary = Math.min(1, wary + 0.05);
            end();
            start(HIDE, hideFrom(level, t), 2.6);
            return;
        }
        if (watched) {
            h.goal = null;      // dead still while it's being looked at
            if (watchedTicks > 20 * 3 && d < 18) {
                // it keeps being looked at: it slips off out of sight, and learns to keep further back and higher
                stalkDist = Math.min(20, stalkDist + 1);
                ceilingBias = Math.min(0.9, ceilingBias + 0.06);
                end();
                Vec3 away = hideFrom(level, t);
                start(HIDE, away, 2.8);
            }
            return;
        }
        if (peekAt != null) {
            peek(level, t);
            return;
        }
        if (planTicks % 30 == 0 || h.goal == null) {
            h.goal = stalkSpot(level, t);
        }
        // they're out of its sight round a corner: it creeps to the edge and looks round it, one leg showing
        if (planTicks % 40 == 20 && d < 22 && !h.canSee(t.getEyePosition()) && h.getRandom().nextFloat() < 0.5f) {
            startPeek(level, t);
        }
        // a door they shut, somewhere behind them: it quietly leaves it standing open
        if (planTicks % 60 == 0 && level.getGameTime() - doorAt > 20 * 90 && h.getRandom().nextFloat() < 0.35f) {
            if (HarvestmanSigns.openDoorBehind(level, h, t)) {
                doorAt = level.getGameTime();
            }
        }
        // a chance: close, alone, not looking
        if (planTicks % 10 == 0 && d < 10 && unseenTicks > 40 && h.getRandom().nextDouble() < 0.12 + 0.2 * bold) {
            startStrike(level, t);
            return;
        }
        if (d > 56 || planTicks > 20 * 60 * (1 + patience)) {
            end();
        }
    }

    /**
     * Looking round a corner: a point off to one side (along its surface) from which they'd be in sight. Its body waits
     * just short of it, behind the edge, and the front leg on that side reaches round on to the corner.
     */
    private void startPeek(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre(), up = h.up(), eye = t.getEyePosition();
        Vec3 to = eye.subtract(c);
        to = to.subtract(up.scale(to.dot(up)));
        if (to.lengthSqr() < 1e-4) {
            return;
        }
        Vec3 side = up.cross(to.normalize());
        for (double s : new double[] {1, -1, 1.6, -1.6, 2.3, -2.3, 3, -3}) {
            Vec3 p = c.add(side.scale(s));
            if (cast(level, c, p).getType() != HitResult.Type.MISS || cast(level, p, eye).getType() != HitResult.Type.MISS) {
                continue;
            }
            // where they'd come into sight: it stops a body's width short of there
            double back = Math.max(0, Math.abs(s) - 0.9) * Math.signum(s);
            peekAt = c.add(side.scale(back));
            Vec3 right = up.cross(h.forward());
            peekLeg = side.scale(s).dot(right) >= 0 ? 2 : 0;
            if ((h.lost() >> peekLeg & 1) != 0) {
                peekLeg = 2 - peekLeg;
            }
            peekTicks = 0;
            h.goal = peekAt;
            h.goalSpeed = 0.9;
            return;
        }
    }

    private void peek(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre(), up = h.up();
        peekTicks++;
        h.look = t.getEyePosition();
        if (near(peekAt, 0.6) || peekTicks > 60) {
            h.goal = null;
            // the point set on the corner, round toward them
            Vec3 to = t.getEyePosition().subtract(c);
            to = to.subtract(up.scale(to.dot(up)));
            Vec3 tip = c.add(to.lengthSqr() > 1e-4 ? to.normalize().scale(2.2) : h.forward().scale(2.2));
            BlockHitResult down = cast(level, tip.add(up.scale(0.5)), tip.subtract(up.scale(HarvestmanEntity.STAND + 1)));
            if (down.getType() != HitResult.Type.MISS && (h.lost() >> peekLeg & 1) == 0) {
                h.legs.reach(peekLeg, down.getLocation());
            }
        }
        // seen peeking, it draws back out of sight; otherwise it holds there a while, then carries on
        if (watched && peekTicks > 8) {
            stopPeek();
            wary = Math.min(1, wary + 0.05);
            end();
            start(HIDE, hideFrom(level, t), 2.6);
            return;
        }
        if (peekTicks > 60 + 60 * patience) {
            stopPeek();
            h.goal = stalkSpot(level, t);
        }
    }

    private void stopPeek() {
        if (peekLeg >= 0) {
            h.legs.reach(peekLeg, null);
        }
        peekAt = null;
        peekLeg = -1;
    }

    /**
     * A feint: a noise made off to the far side of them (a knock, a scrape, a clatter of what's there) to turn them that
     * way, while it comes in from its own side. If they turn to the noise, it closes in (striking when it's near enough);
     * if they don't, it learns that it didn't work.
     */
    private void startFeint(ServerLevel level, ServerPlayer target) {
        if (target == null) {
            return;
        }
        Vec3 eye = target.getEyePosition(), c = h.centre();
        Vec3 away = eye.subtract(c).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? target.getLookAngle().multiply(-1, 0, -1) : away.normalize();
        Vec3 best = null;
        for (int k = 0; k < 16 && best == null; k++) {
            Vec3 d = away.add(randomDir().multiply(0.6, 0.2, 0.6)).normalize();
            BlockHitResult hit = cast(level, eye, eye.add(d.scale(14)));
            double dist = hit.getLocation().distanceTo(eye);
            if (hit.getType() != HitResult.Type.MISS && dist > 5) {
                best = hit.getLocation().add(normal(hit).scale(0.3));
            }
        }
        if (best == null) {
            return;
        }
        start(FEINT, null, 0);
        quarry = target;
        decoy = best;
        decoyLook = target.getLookAngle();
        feintAt = level.getGameTime();
        HarvestmanSigns.decoyNoise(level, h, best);
    }

    private void feint(ServerLevel level) {
        if (!(quarry instanceof ServerPlayer t) || !t.isAlive() || decoy == null) {
            end();
            return;
        }
        h.look = t.getEyePosition();
        Vec3 toDecoy = decoy.subtract(t.getEyePosition()).normalize();
        boolean turned = t.getLookAngle().dot(toDecoy) > 0.75 && decoyLook.dot(toDecoy) < 0.6;
        if (phase == 0 && turned) {
            // it worked: they're looking the other way
            phase = 1;
            phaseTicks = 0;
            feintBias = Math.min(1, feintBias + 0.08);
        }
        if (phase == 1) {
            double d = t.position().distanceTo(h.centre());
            if (d < 10 && !lookingAt(t, h.centre())) {
                startStrike(level, t);
                return;
            }
            h.goal = t.position().add(0, 2, 0);
            h.goalSpeed = 3.6;
            if (phaseTicks > 20 * 6 || watched) {
                end();
            }
        } else if (planTicks > 20 * 4) {
            // they didn't bite
            feintBias = Math.max(0.05, feintBias - 0.05);
            end();
        }
    }

    /** Shot in the eyes: it throws itself back with a shriek, drops what it holds and backs off out of the line. */
    void eyeHit(ServerLevel level, Entity by) {
        Vec3 c = h.centre();
        level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.HISS, SoundSource.HOSTILE, 1.6f, 1.35f);
        wary = Math.min(1, wary + 0.15);
        if (plan == FEED || plan == DRAG) {
            return;     // it won't let go of a meal for that alone
        }
        end();
        h.rearWant = 0;
        Vec3 away = by instanceof Player p ? hideFrom(level, p) : spot(HIDE, 12);
        start(HIDE, away, BURST);
        wait = 0;
    }

    /** Somewhere a stalking distance from them, out of their sight, dark, and high when it likes the ceilings. */
    private Vec3 stalkSpot(ServerLevel level, ServerPlayer t) {
        Vec3 eye = t.getEyePosition(), c = h.centre(), best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 28; k++) {
            Vec3 d = randomDir();
            Vec3 from = eye.add(d.scale(stalkDist * 0.6));
            BlockHitResult hit = cast(level, eye, from.add(d.scale(stalkDist)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 n = normal(hit), p = hit.getLocation().add(n.scale(1.2));
            double dist = p.distanceTo(eye);
            if (dist < 6) {
                continue;
            }
            BlockPos air = BlockPos.containing(p);
            double dark = 15 - level.getMaxLocalRawBrightness(air);
            boolean hidden = cast(level, eye, p).getType() != HitResult.Type.MISS || !inView(t, p);
            double score = -Math.abs(dist - stalkDist) * 0.3 + dark * 0.15 + (hidden ? 1.5 : 0)
                    + (n.y < -0.5 ? 1.2 * ceilingBias : Math.abs(n.y) < 0.3 ? 0.5 * ceilingBias : 0)
                    - p.distanceTo(c) * 0.03 + h.getRandom().nextDouble() * 0.4;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best != null ? best : quarrySeen != null ? quarrySeen.add(0, 2, 0) : null;
    }

    private static boolean inView(Player p, Vec3 at) {
        Vec3 to = at.subtract(p.getEyePosition()).normalize();
        return p.getLookAngle().dot(to) > Math.cos(Math.toRadians(55));
    }

    /** Somewhere out of their sight, away from them. */
    private Vec3 hideFrom(ServerLevel level, Player t) {
        Vec3 c = h.centre(), best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 24; k++) {
            Vec3 d = randomDir();
            BlockHitResult hit = cast(level, c, c.add(d.scale(14)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 p = hit.getLocation().add(normal(hit).scale(1.2));
            boolean hidden = cast(level, t.getEyePosition(), p).getType() != HitResult.Type.MISS;
            double score = (hidden ? 2 : 0) + p.distanceTo(t.position()) * 0.1 + h.getRandom().nextDouble() * 0.3;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    // ambush: it waits on a ceiling where they're going, and drops on them
    private void startAmbush(ServerLevel level, ServerPlayer target) {
        if (target == null) {
            return;
        }
        Vec3 ahead = target.position().add(target.getDeltaMovement().multiply(1, 0, 1).scale(20 * 6));
        Vec3 home = HarvestmanDirector.favourite(target, level);
        Vec3 where = home != null && h.getRandom().nextDouble() < 0.4 ? home : ahead;
        BlockHitResult hit = cast(level, where.add(0, 1.5, 0), where.add(0, 14, 0));
        if (hit.getType() == HitResult.Type.MISS) {
            startStalk(level, target);
            return;
        }
        start(AMBUSH, hit.getLocation().add(0, -1.2, 0), 2.0);
    }

    private void ambush(ServerLevel level) {
        if (!(quarry instanceof ServerPlayer t) || !t.isAlive() || t.level() != level) {
            end();
            return;
        }
        Vec3 c = h.centre();
        if (h.goal != null && near(h.goal, 1.0)) {
            h.goal = null;
            phase = 1;
        }
        if (phase == 1) {
            h.look = t.getEyePosition();
            Vec3 flat = t.position().subtract(c);
            double across = Math.hypot(flat.x, flat.z);
            if (across < 4 && c.y > t.getEyeY() && !lookingAt(t, c)) {
                startStrike(level, t);
                return;
            }
        }
        if (planTicks > 20 * (40 + 40 * patience)) {
            ambushBias = Math.max(0.1, ambushBias - 0.05);
            end();
        }
    }

    // striking: in fast, low; it rears up an arm's length off, and lunges
    private void startStrike(ServerLevel level, Entity prey) {
        if (prey == null) {
            return;
        }
        start(prey instanceof Zombie ? ZOMBIE : STRIKE, prey.position(), BURST);
        quarry = prey;
        h.wantFloor = true;
        if (prey instanceof Player) {
            alert = 1;
        }
    }

    private void strike(ServerLevel level) {
        if (quarry == null || !quarry.isAlive() || quarry.level() != level) {
            end();
            return;
        }
        Vec3 g = h.gripPoint(), head = quarry instanceof LivingEntity le ? le.getEyePosition() : quarry.position().add(0, 1.5, 0);
        double d = g.distanceTo(head);
        h.look = head;
        h.wantFloor = true;
        switch (phase) {
            case 0 -> {
                h.goal = quarry.position();
                // it closes fast, and faster still on prey that runs
                h.goalSpeed = quarry.isSprinting() ? SPRINT : BURST;
                if (d < 3.4) {
                    phase = 1;
                    phaseTicks = 0;
                    h.rearWant = 1;
                    h.goal = null;
                    level.playSound(null, g.x, g.y, g.z, HarvestmanSounds.HISS, SoundSource.HOSTILE, 1.3f, 0.9f);
                }
                if (planTicks > 20 * 12) {
                    end();
                }
            }
            case 1 -> {
                // reared, a beat, then down and in
                if (phaseTicks > 7) {
                    phase = 2;
                    phaseTicks = 0;
                    h.rearWant = 0;
                    level.playSound(null, g.x, g.y, g.z, HarvestmanSounds.SCREECH, SoundSource.HOSTILE, 2f, 1.05f);
                }
            }
            case 2 -> {
                h.goal = head.add(head.subtract(h.centre()).normalize().scale(1.2));
                h.goalSpeed = LUNGE;
                if (d < 1.5 && quarry instanceof LivingEntity le && h.grab(level, le)) {
                    grabs++;
                    bold = Math.min(1, bold + 0.03);
                    end();
                    plan = DRAG;
                    planTicks = 0;
                    Vec3 dark = spot(HIDE, 18);
                    h.goal = dark;
                    h.goalSpeed = 1.7;
                    h.wantFloor = true;
                } else if (phaseTicks > 22) {
                    // missed: it backs off, hissing, and comes again later
                    level.playSound(null, g.x, g.y, g.z, HarvestmanSounds.HISS, SoundSource.HOSTILE, 1f, 1.1f);
                    end();
                    Vec3 away = quarry instanceof Player p ? hideFrom(level, p) : null;
                    if (away != null) {
                        start(HIDE, away, 3.0);
                    } else {
                        wait = 40;
                    }
                }
            }
            default -> end();
        }
    }

    // dragging what it caught somewhere dark, then feeding
    private void drag(ServerLevel level) {
        if (h.held() == null) {
            end();
            wait = 40;
            return;
        }
        h.wantFloor = true;
        h.look = null;
        if (h.goal == null || near(h.goal, 1.0) || planTicks > (h.held() instanceof Player ? 20 * 6 : 20 * 3)) {
            h.goal = null;
            plan = FEED;
            planTicks = 0;
            h.startFeeding();
        }
    }

    // feeding on a zombie when starving
    private void startZombie(ServerLevel level) {
        Zombie z = nearestZombie(level, 32);
        if (z != null) {
            startStrike(level, z);
        }
    }

    // glimpse: it shows itself at the end of their sight for a moment, then is gone
    private void startGlimpse(ServerLevel level, ServerPlayer target) {
        if (target == null) {
            return;
        }
        Vec3 eye = target.getEyePosition(), look = target.getLookAngle(), best = null;
        for (int k = 0; k < 16 && best == null; k++) {
            Vec3 d = look.add(randomDir().scale(0.35)).normalize();
            BlockHitResult hit = cast(level, eye, eye.add(d.scale(26)));
            double dist = hit.getLocation().distanceTo(eye);
            if (hit.getType() != HitResult.Type.MISS && dist > 13) {
                best = hit.getLocation().add(normal(hit).scale(1.4));
            }
        }
        if (best == null) {
            return;
        }
        glimpseAt = level.getGameTime();
        start(GLIMPSE, best, 2.4);
    }

    private void glimpse(ServerLevel level) {
        if (!(quarry instanceof ServerPlayer t) || !t.isAlive()) {
            end();
            return;
        }
        h.look = t.getEyePosition();
        if (phase == 0 && (h.goal == null || near(h.goal, 1.0))) {
            phase = 1;
            phaseTicks = 0;
            h.goal = null;
        }
        // seen for a moment (or long enough waiting), it's gone
        if (phase == 1 && (watchedTicks > 15 || phaseTicks > 20 * 8)) {
            if (watchedTicks > 0) {
                level.playSound(null, h.getX(), h.getY(), h.getZ(), HarvestmanSounds.CLICK, SoundSource.HOSTILE, 0.8f, 0.8f);
            }
            end();
            start(HIDE, hideFrom(level, t), BURST);    // gone in a blink
        }
        if (planTicks > 20 * 30) {
            end();
        }
    }

    // a body left for them to find: it carries one to where they spend their time while they're away
    private void startLeaveBody(ServerLevel level, ServerPlayer target) {
        Corpse body = nearCorpse(level, 24);
        Vec3 home = target != null ? HarvestmanDirector.favourite(target, level) : null;
        if (home == null && forcedHunt && target != null) {
            home = target.position();
        }
        if (body == null || home == null) {
            return;
        }
        bodyAt = level.getGameTime();
        start(LEAVE_BODY, body.position().add(0, 2, 0), 2.0);
        carried = null;
        quarry = body;
        h.wantFloor = true;
        phase = 0;
    }

    private void leaveBody(ServerLevel level) {
        ServerPlayer t = target(level);
        if (phase == 0) {
            if (!(quarry instanceof Corpse body) || body.isRemoved()) {
                end();
                return;
            }
            h.goal = body.position().add(0, 2, 0);
            if (near(body.position(), 2.0)) {
                carried = body;
                phase = 1;
                Vec3 home = t != null ? HarvestmanDirector.favourite(t, level) : null;
                h.goal = home != null ? home.add(0, 2, 0) : null;
            }
        } else if (h.goal == null || near(h.goal, 1.5) || planTicks > 20 * 90) {
            // laid down where they'll walk in on it: hung up, slumped in a doorway or sat facing the way in
            if (carried instanceof Corpse body && !body.isRemoved()) {
                HarvestmanDens.setOut(level, body, h.centre(), t != null ? t.position() : null, 0.35f);
            }
            carried = null;
            end();
            start(HIDE, spot(HIDE, 16), 2.5);
        }
        if (planTicks > 20 * 120) {
            carried = null;
            end();
        }
    }

    private void carry() {
        if (carried == null || carried.isRemoved()) {
            carried = null;
            return;
        }
        Vec3 g = h.gripPoint();
        carried.setPos(g.x, g.y - 1.4, g.z);
        carried.setDeltaMovement(Vec3.ZERO);
    }

    // resting in its lair: its wounds close and a lost leg grows back from scrap, a day a leg
    private void rest(ServerLevel level) {
        h.goal = null;
        if (lair == null || h.centre().distanceToSqr(Vec3.atCenterOf(lair)) > 12 * 12) {
            end();
            return;
        }
        if (planTicks % 20 == 0) {
            h.heal((float) (HarvestmanEntity.MAX_HEALTH / (24000.0 / 20)));
        }
        int lost = h.lost();
        if (lost != 0) {
            workScrap(level);
        }
        if (lost != 0 && planTicks > 0 && planTicks % 24000 == 0) {
            h.regrow(Integer.numberOfTrailingZeros(lost));
            if (heap != null) {
                HarvestmanDens.use(level, heap);
                heap = null;
            }
        }
        if (planTicks % 200 == 0 && h.getRandom().nextFloat() < 0.3f) {
            level.playSound(null, h.getX(), h.getY(), h.getZ(), HarvestmanSounds.CLICK, SoundSource.HOSTILE, 0.5f, 0.7f);
        }
        // up when something calls it: its target close, starving, or healed
        if (planTicks % 100 == 0 && (lost == 0 && h.getHealth() >= h.getMaxHealth() * 0.95 || target(level) != null && h.getRandom().nextFloat() < 0.3f)) {
            end();
        }
    }

    /**
     * Growing a leg back: it goes to a heap of scrap in its den and works it, rearing and stabbing down into it, the
     * steel clanging and sparking, now and then dragging a sheet of it screeching across the floor.
     */
    private void workScrap(ServerLevel level) {
        if (heap == null || planTicks % 200 == 0) {
            heap = HarvestmanDens.scrap(level, lair, 7);
        }
        if (heap == null) {
            h.rearWant = 0;
            return;
        }
        Vec3 at = Vec3.atCenterOf(heap);
        h.look = at;
        Vec3 flat = h.centre().subtract(at).multiply(1, 0, 1);
        if (flat.lengthSqr() > 2.6 * 2.6) {
            h.goal = at.add(flat.normalize().scale(1.8)).add(0, 1, 0);
            h.goalSpeed = 1.2;
            h.rearWant = 0;
            return;
        }
        int beat = planTicks % 50;
        h.rearWant = beat < 18 ? 0.5 : 0;
        if (beat == 22) {
            HarvestmanDens.work(level, heap);
            level.playSound(null, at.x, at.y, at.z, HarvestmanSounds.CLANG, SoundSource.HOSTILE, 0.7f,
                    0.7f + h.getRandom().nextFloat() * 0.3f);
        }
        if (planTicks % 260 == 130) {
            level.playSound(null, at.x, at.y, at.z, HarvestmanSounds.SCRAPE, SoundSource.HOSTILE, 0.6f, 0.8f);
        }
    }

    // ------------------------------------------------------------------------------------------------ events

    /** A noise it heard (HarvestmanDirector passes every Noise on). */
    void heard(ServerLevel level, Vec3 pos, double loud) {
        if (plan == STRIKE || plan == DRAG || plan == FEED) {
            return;
        }
        heard = pos;
        heardAt = level.getGameTime();
        heardLoud = loud;
        // a gunshot close by: early on it shies off from it; as it gets used to them they stop turning it
        Vec3 c = h.centre();
        if (loud >= 24 && pos.distanceToSqr(c) < 22 * 22 && h.held() == null && plan != FLEE && plan != REST
                && level.getGameTime() > gunShyUntil) {
            gunShyUntil = level.getGameTime() + 20 * 4;
            if (h.getRandom().nextDouble() > gunNerve) {
                gunNerve = Math.min(1, gunNerve + 0.07);
                wary = Math.min(1, wary + 0.1);
                end();
                start(HIDE, awayFrom(level, pos), BURST * 0.6);
                return;
            }
            gunNerve = Math.min(1, gunNerve + 0.01);
        }
        alert = Math.min(1, alert + loud / 30);
        // a loud noise while it's idling or roaming breaks off what it's doing
        if (plan == IDLE || plan == WANDER || plan == PERCH || plan == CEILING || plan == REST && loud > 20) {
            end();
            wait = 4 + h.getRandom().nextInt(10);
            h.look = pos;
        }
    }

    /** Somewhere out of the line of a point (a shot), away from it. */
    private Vec3 awayFrom(ServerLevel level, Vec3 from) {
        Vec3 c = h.centre(), best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 20; k++) {
            Vec3 d = randomDir();
            BlockHitResult hit = cast(level, c, c.add(d.scale(12)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 p = hit.getLocation().add(normal(hit).scale(1.2));
            boolean hidden = cast(level, from, p).getType() != HitResult.Type.MISS;
            double score = (hidden ? 2 : 0) + p.distanceTo(from) * 0.1 + h.getRandom().nextDouble() * 0.3;
            if (score > bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best != null ? best : spot(HIDE, 12);
    }

    void hurt(ServerLevel level, Entity by, double amount) {
        hurtRecent += amount;
        wary = Math.min(1, wary + amount / 200);
        alert = 1;
        if (by instanceof ServerPlayer p) {
            HarvestmanDirector.grudge(level, h, p, amount);
        }
        // badly hurt, it breaks off whatever it was doing and limps home to mend (it won't fight to the end)
        if (h.held() == null && h.getHealth() - amount < h.getMaxHealth() * 0.3 && plan != FLEE && plan != REST) {
            end();
            start(FLEE, lair != null ? Vec3.atCenterOf(lair) : by instanceof Player p ? hideFrom(level, p) : spot(HIDE, 20), SPRINT);
            level.playSound(null, h.getX(), h.getY(), h.getZ(), HarvestmanSounds.HISS, SoundSource.HOSTILE, 1.4f, 0.8f);
            return;
        }
        // a hard burst while it isn't holding anything sends it off
        if (h.held() == null && hurtRecent > 60 && plan != FLEE && plan != STRIKE) {
            end();
            start(FLEE, by instanceof Player p ? hideFrom(level, p) : spot(HIDE, 16), SPRINT);
            level.playSound(null, h.getX(), h.getY(), h.getZ(), HarvestmanSounds.HURT, SoundSource.HOSTILE, 1.4f, 1f);
        }
    }

    void lostLeg(ServerLevel level) {
        wary = 1;
        if (h.held() != null) {
            h.release();
        }
        end();
        Vec3 to = lair != null ? Vec3.atCenterOf(lair) : spot(HIDE, 20);
        start(FLEE, to, BURST);
    }

    void lostPrey(ServerLevel level) {
        escapes++;
        bold = Math.max(0.1, bold - 0.05);
        end();
        start(HIDE, spot(HIDE, 16), BURST);
    }

    void fed(ServerLevel level, Entity what) {
        feeds++;
        hunger = Math.max(0, hunger - (what instanceof Player ? 0.8 : 0.45));
        if (what instanceof Player && feeds % 2 == 0 && stage < 5) {
            stage++;    // it grows
        }
        if (what instanceof ServerPlayer p) {
            HarvestmanDirector.killed(level, h, p);
        }
        end();
        Vec3 to = lair != null ? Vec3.atCenterOf(lair) : spot(HIDE, 20);
        start(LAIR, to, 1.8);
    }

    // ------------------------------------------------------------------------------------------------ places

    private Vec3 randomDir() {
        Vec3 d = new Vec3(h.getRandom().nextGaussian(), h.getRandom().nextGaussian(), h.getRandom().nextGaussian());
        return d.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : d.normalize();
    }

    private BlockHitResult cast(ServerLevel level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, h));
    }

    private static Vec3 normal(BlockHitResult hit) {
        Direction d = hit.getDirection();
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** A place for a plan, from surfaces it can see round it within range. */
    Vec3 spot(int plan, double range) {
        ServerLevel level = (ServerLevel) h.level();
        Vec3 c = h.centre(), best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 40; k++) {
            Vec3 d = randomDir();
            BlockHitResult hit = cast(level, c, c.add(d.scale(range)));
            if (hit.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 n = normal(hit), p = hit.getLocation();
            double dist = p.distanceTo(c);
            if (dist < 3) {
                continue;
            }
            BlockPos air = hit.getBlockPos().relative(hit.getDirection());
            int light = level.getMaxLocalRawBrightness(air);
            double score = switch (plan) {
                case WANDER -> n.y > 0.7 ? 1 + h.getRandom().nextDouble() - Math.abs(dist - 9) * 0.05 - light * 0.03 : -1e9;
                case PERCH -> Math.abs(n.y) < 0.3 && p.y > c.y + 1 ? (p.y - c.y) * 0.3 + h.getRandom().nextDouble() - light * 0.05 : -1e9;
                case CEILING -> n.y < -0.7 ? 1 + h.getRandom().nextDouble() - light * 0.05 : -1e9;
                default -> 15 - light + 0.8 * corner(level, air) - dist * 0.04 + h.getRandom().nextDouble() * 0.5;
            };
            if (score > bestScore) {
                bestScore = score;
                best = p.add(n.scale(1.0));
            }
        }
        return bestScore > -1e8 ? best : null;
    }

    private static int corner(ServerLevel level, BlockPos air) {
        int n = 0;
        for (Direction d : Direction.values()) {
            if (!level.getBlockState(air.relative(d)).getCollisionShape(level, air.relative(d)).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private LivingEntity nearestPrey(ServerLevel level) {
        Player p = level.getNearestPlayer(h, 24);
        return p != null && !p.isSpectator() ? p : null;
    }

    private Zombie nearestZombie(ServerLevel level, double r) {
        Zombie best = null;
        double bd = r * r;
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, h.getBoundingBox().inflate(r), z -> ZombieTypes.isWalker(z) && z.isAlive())) {
            double d = z.distanceToSqr(h);
            if (d < bd) {
                bd = d;
                best = z;
            }
        }
        return best;
    }

    private Corpse nearCorpse(ServerLevel level, double r) {
        for (Corpse c : level.getEntitiesOfClass(Corpse.class, h.getBoundingBox().inflate(r))) {
            return c;
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------ saving

    void save(ValueOutput o) {
        o.putDouble("hmHunger", hunger);
        o.putDouble("hmBold", bold);
        o.putDouble("hmPatience", patience);
        o.putDouble("hmStalk", stalkDist);
        o.putDouble("hmCeiling", ceilingBias);
        o.putDouble("hmAmbush", ambushBias);
        o.putDouble("hmLightNerve", lightNerve);
        o.putDouble("hmGunNerve", gunNerve);
        o.putDouble("hmFeint", feintBias);
        o.putInt("hmSeen", seen);
        o.putInt("hmGrabs", grabs);
        o.putInt("hmEscapes", escapes);
        o.putInt("hmFeeds", feeds);
        o.putInt("hmStage", stage);
        o.putString("hmId", hid);
        o.putInt("hmGen", gen);
        if (lair != null) {
            o.putLong("hmLair", lair.asLong());
        }
    }

    void load(ValueInput in) {
        hunger = in.getDoubleOr("hmHunger", hunger);
        bold = in.getDoubleOr("hmBold", 0.3 + 0.5 * h.getRandom().nextDouble());
        patience = in.getDoubleOr("hmPatience", 0.3 + 0.5 * h.getRandom().nextDouble());
        stalkDist = in.getDoubleOr("hmStalk", stalkDist);
        ceilingBias = in.getDoubleOr("hmCeiling", ceilingBias);
        ambushBias = in.getDoubleOr("hmAmbush", ambushBias);
        lightNerve = in.getDoubleOr("hmLightNerve", 0);
        gunNerve = in.getDoubleOr("hmGunNerve", 0);
        feintBias = in.getDoubleOr("hmFeint", 0.3);
        seen = in.getIntOr("hmSeen", 0);
        grabs = in.getIntOr("hmGrabs", 0);
        escapes = in.getIntOr("hmEscapes", 0);
        feeds = in.getIntOr("hmFeeds", 0);
        stage = in.getIntOr("hmStage", 1);
        hid = in.getStringOr("hmId", "");
        gen = in.getIntOr("hmGen", 0);
        long l = in.getLongOr("hmLair", Long.MIN_VALUE);
        lair = l == Long.MIN_VALUE ? null : BlockPos.of(l);
    }

    /** Its identity for the director, made the first time it's asked for. */
    String id() {
        if (hid.isEmpty()) {
            hid = UUID.randomUUID().toString();
        }
        return hid;
    }
}
