package com.zomboidcraft.creature;

import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The test room ({@code /zc harvestman room}): a closed hall high above where you stand, a doorway through to a den,
 * pillars, a lit end and dark corners, and one Harvestman in it that works through everything it does, one thing at a
 * time, each named on your screen as it starts. It never strikes at you here (it shows its strike on the dead).
 * {@code room next} skips on, {@code room stop} ends it and takes the Harvestman away.
 */
final class HarvestmanTour {
    /** One thing it shows: its plan's name, what it's called on screen, what's happening, and for how long. */
    private record Step(String plan, String title, String detail, int seconds) {
    }

    private static final List<Step> STEPS = List.of(
            new Step("tend", "Tending its den", "Its wounds close while it works its scrap and keeps the place. By day it keeps to its den.", 25),
            new Step("patrol", "Holding its ground", "It patrols where it hasn't been for longest and where people pass, stops to look and listen, and gouges its mark at the edges.", 40),
            new Step("lie_in_wait", "Lying in wait", "Over the doorway people pass (it's learned that), up under the ceiling, dead still, facing the way you'll come.", 35),
            new Step("zombie", "Feeding", "It takes one of the dead and drags it home to its den to eat.", 50),
            new Step("warn", "Warning you off its ground", "It shows itself between you and its den, rears and rages, then watches whether you back off. It remembers if you did.", 28),
            new Step("observe", "Watching you from the dark", "It stays well back and out of your sight, learning your ways. Look straight at it and it slips away.", 30),
            new Step("hide", "Keeping out of your way", "If you're armed, lit up or with friends, you're too dangerous, so it gets out of your sight.", 20),
            new Step("shelter", "Sheltering till dark", "By day, far from its den, it gets in under cover in the darkest corner and waits for night.", 25),
            new Step("stalk", "Hunting: stalking", "It follows the one it hunts from the dark, freezing when looked at and peeking round corners.", 30),
            new Step("ambush", "Hunting: waiting above", "It gets up over where you'll pass and waits for you to walk under.", 30),
            new Step("feint", "Hunting: a decoy", "It knocks off to your far side to turn you round.", 25),
            new Step("retreat", "Hunting: a fake retreat", "It pretends to flee, then waits for you to follow under it.", 25),
            new Step("display", "Hunting: standing lit up", "It stands where you can see it with its red showing, and it's gone when you look away.", 22),
            new Step("rage", "Its rage", "Its skull flares and it screams at you. It does this to let you know it's seen you, or to scare you off.", 12));

    /** The room's inside: 31 long (x), 22 wide (z), 7 high; the partition with its doorway at x 20; the den beyond. */
    private static final int LEN = 31, WID = 22, HIGH = 7, PART = 20;

    private static HarvestmanEntity h;
    private static ServerPlayer viewer;
    private static BlockPos origin;
    private static int step = -1, ticks;
    private static boolean registered;

    static void register() {
        if (!registered) {
            registered = true;
            ServerTickEvents.END_SERVER_TICK.register(HarvestmanTour::tick);
        }
    }

    /** Builds the room above the player, puts them in it and starts the tour. */
    static String start(ServerPlayer p) {
        register();
        stop();
        ServerLevel level = (ServerLevel) p.level();
        int y = Math.min(p.getBlockY() + 70, level.getMaxY() - HIGH - 4);
        origin = new BlockPos(p.getBlockX() - 6, y, p.getBlockZ() - WID / 2);
        build(level, origin);
        Vec3 doorway = Vec3.atBottomCenterOf(origin.offset(PART, 1, WID / 2));
        Vec3 den = Vec3.atBottomCenterOf(origin.offset(PART + 6, 1, WID / 2));
        h = HarvestmanDirector.makeDen(level, den, doorway);
        if (h == null) {
            return "Couldn't make the Harvestman's den in the room";
        }
        h.mind.showcase = true;
        // it already knows people come through the doorway, so that's where it lies in wait
        HarvestmanTerritory land = h.mind.goals.land();
        for (int i = 0; i < 6; i++) {
            land.person(doorway.add(-1.5, 0, 0), level.getGameTime(), false, 0);
        }
        viewer = p;
        p.teleportTo(level, origin.getX() + 6.5, origin.getY() + 1, origin.getZ() + WID / 2.0 + 0.5, java.util.Set.of(), -90f, 10f, true);
        step = -1;
        ticks = 0;
        return "The test room is round you. Its Harvestman will show you " + STEPS.size()
                + " things, one at a time. Use /zc harvestman room next to skip and room stop to end it.";
    }

    static String next() {
        if (h == null) {
            return "No tour running (/zc harvestman room starts one)";
        }
        ticks = Integer.MAX_VALUE / 2;
        return "Next";
    }

    static void stop() {
        if (h != null) {
            HarvestmanDirector.forget(h);
            h.discard();
        }
        h = null;
        viewer = null;
        step = -1;
    }

    private static void tick(MinecraftServer server) {
        if (h == null) {
            return;
        }
        if (!h.isAlive() || viewer == null || viewer.hasDisconnected() || viewer.level() != h.level()) {
            stop();
            return;
        }
        ServerLevel level = (ServerLevel) h.level();
        ticks++;
        Step now = step >= 0 && step < STEPS.size() ? STEPS.get(step) : null;
        if (now == null || ticks >= now.seconds() * 20) {
            step++;
            ticks = 0;
            if (step >= STEPS.size()) {
                viewer.sendSystemMessage(Component.literal("That's everything it does. /zc harvestman room runs it again; room stop takes it away.")
                        .withStyle(ChatFormatting.GRAY));
                h.mind.showcase = true;
                step = STEPS.size();
                return;
            }
            begin(level, STEPS.get(step));
            return;
        }
        if (ticks % 40 == 0) {
            viewer.sendOverlayMessage(caption(now));
        }
    }

    private static Component caption(Step s) {
        return Component.literal("[" + (step + 1) + "/" + STEPS.size() + "] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(s.title()).withStyle(ChatFormatting.RED));
    }

    private static void begin(ServerLevel level, Step s) {
        viewer.sendSystemMessage(caption(s).copy().append(Component.literal("  " + s.detail()).withStyle(ChatFormatting.GRAY)));
        viewer.sendOverlayMessage(caption(s));
        String why = null;
        switch (s.plan()) {
            case "rage" -> {
                if (!h.rage(viewer.getEyePosition(), true)) {
                    why = "it's busy";
                }
            }
            case "zombie" -> {
                // two of the dead let in at the far end of the hall
                for (int i = 0; i < 2; i++) {
                    Zombie z = EntityTypes.ZOMBIE.spawn(level, origin.offset(12 + i * 2, 1, 3 + i * 2), EntitySpawnReason.EVENT);
                    if (z != null) {
                        z.setPersistenceRequired();
                    }
                }
                why = h.mind.force(level, HarvestmanMind.ZOMBIE);
            }
            default -> why = h.mind.force(level, Arrays.asList(HarvestmanMind.PLANS).indexOf(s.plan()));
        }
        if (why != null) {
            viewer.sendSystemMessage(Component.literal("  (it can't show this one here: " + why + ")").withStyle(ChatFormatting.DARK_GRAY));
            ticks = Math.max(0, s.seconds() * 20 - 60);
        }
    }

    // ------------------------------------------------------------------------------------------------ the room

    private static void build(ServerLevel level, BlockPos o) {
        BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState(), floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                ceiling = Blocks.DEEPSLATE_TILES.defaultBlockState(), air = Blocks.AIR.defaultBlockState(),
                lamp = Blocks.SEA_LANTERN.defaultBlockState(), pillar = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        for (int x = -1; x <= LEN; x++) {
            for (int z = -1; z <= WID; z++) {
                for (int y = 0; y <= HIGH + 1; y++) {
                    boolean edge = x == -1 || x == LEN || z == -1 || z == WID;
                    BlockState s = y == 0 ? floor : y == HIGH + 1 ? ceiling : edge ? wall : air;
                    level.setBlock(o.offset(x, y, z), s, 2);
                }
            }
        }
        // the partition between hall and den, with its doorway (two wide, three high)
        for (int z = 0; z < WID; z++) {
            for (int y = 1; y <= HIGH; y++) {
                boolean door = (z == WID / 2 || z == WID / 2 - 1) && y <= 3;
                if (!door) {
                    level.setBlock(o.offset(PART, y, z), wall, 2);
                }
            }
        }
        // two pillars in the hall to hide behind
        for (int[] p : new int[][] {{9, 5}, {9, WID - 7}}) {
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    for (int y = 1; y <= HIGH; y++) {
                        level.setBlock(o.offset(p[0] + dx, y, p[1] + dz), pillar, 2);
                    }
                }
            }
        }
        // the lit end where you stand; the rest is dark
        for (int z : new int[] {4, WID / 2, WID - 5}) {
            level.setBlock(o.offset(3, HIGH + 1, z), lamp, 2);
        }
        // a low ledge along one wall to perch on
        for (int x = 12; x < PART; x++) {
            level.setBlock(o.offset(x, 4, WID - 1), wall, 2);
        }
    }

    private HarvestmanTour() {
    }
}
