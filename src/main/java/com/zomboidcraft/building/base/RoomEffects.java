package com.zomboidcraft.building.base;

import com.zomboidcraft.building.base.RoomType.Grade;
import com.zomboidcraft.survival.Sleep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * What the bunk room, med bay and kitchen do (rooms plan step 2, Josiah's go 2026-11-07 11:28). Each system asks here
 * with one line where it already does the work: sleep (survival/Sleep), treatment and healing (survival/Treatment,
 * Injuries), cooking and food keeping (decor/DecorUses, loot/items/LootHooks). The numbers are the plan's.
 */
public final class RoomEffects {
    /** The grade of the room the player is in, if it's this type; NONE otherwise. */
    public static Grade grade(Player player, RoomType type) {
        Rooms.Room r = Rooms.current(player);
        return r == null || r.type() != type ? Grade.NONE : r.grade();
    }

    // ---------------------------------------------------------------- bunk room

    /**
     * The bunk room's grade for sleeping in it now: Full only with the lights off (a lit lamp in the room keeps it at
     * Equipped), since the Full room is the one you can sleep through the night in.
     */
    public static Grade bunk(Player player) {
        Rooms.Room r = Rooms.current(player);
        if (r == null || r.type() != RoomType.BUNK) return Grade.NONE;
        if (r.grade() == Grade.FULL && r.count(Fixture.LAMP_LIT) > 0) return Grade.EQUIPPED;
        return r.grade();
    }

    /** Rest from sleep, times the bed's: a quarter more in a Basic bunk room, half again from Equipped. */
    public static float restScale(Player player) {
        return switch (bunk(player)) {
            case BASIC -> 1.25f;
            case EQUIPPED, FULL -> 1.5f;
            default -> 1f;
        };
    }

    /** Stress shed on waking rested in an Equipped bunk room or better. */
    public static int wakeStressRelief(Player player) {
        return bunk(player).compareTo(Grade.EQUIPPED) >= 0 ? 25 : 0;
    }

    /** A Full bunk room, lights off: only something inside the base wakes you, and boredom and unhappiness ease. */
    public static boolean soundSleep(Player player) {
        return bunk(player) == Grade.FULL;
    }

    /** Whether something at this spot can wake the sleeper: in a Full bunk room, only from inside their base. */
    public static boolean canWake(Player player, BlockPos from) {
        if (!soundSleep(player)) return true;
        Claim c = Claims.at(player.level(), player.blockPosition());
        return c != null && c.contains(from);
    }

    // ---------------------------------------------------------------- med bay

    /** Treatment time, times this: a quarter less in a med bay. */
    public static float treatScale(Player treater) {
        return grade(treater, RoomType.MED_BAY).compareTo(Grade.BASIC) >= 0 ? 0.75f : 1f;
    }

    /** Asleep in a hospital bed in an Equipped med bay or better: wounds mend half again as fast. */
    public static boolean hospitalRest(Player player) {
        if (!Sleep.asleep(player) || grade(player, RoomType.MED_BAY).compareTo(Grade.EQUIPPED) < 0) return false;
        BlockPos at = player.blockPosition();
        for (BlockPos p : new BlockPos[]{at, at.below()}) {
            if (Fixture.of(player.level().getBlockState(p)) == Fixture.HOSPITAL_BED) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- kitchen

    /** Cooking time, times this: a fifth faster in a kitchen. */
    public static float cookScale(Player cook) {
        return grade(cook, RoomType.KITCHEN).compareTo(Grade.BASIC) >= 0 ? 0.8f : 1f;
    }

    /** Cooking xp, times this: half as much again in a kitchen. */
    public static float cookXp(Player cook) {
        return grade(cook, RoomType.KITCHEN).compareTo(Grade.BASIC) >= 0 ? 1.5f : 1f;
    }

    /**
     * Food stored in an Equipped kitchen (a powered fridge in it) keeps as long as in a fridge: is this container in
     * the kitchen the player stands in?
     */
    public static boolean keepsCold(Player player, Level level, BlockPos container) {
        Rooms.Room r = Rooms.current(player);
        if (r == null || r.type() != RoomType.KITCHEN || r.grade().compareTo(Grade.EQUIPPED) < 0) return false;
        // containers stand on the floor (or hang one or two up the wall): compare on the room's floor
        BlockPos floor = new BlockPos(container.getX(), r.anchor().getY(), container.getZ());
        if (r.cells().contains(floor)) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (r.cells().contains(floor.relative(d))) return true;
        }
        return false;
    }

    private RoomEffects() {
    }
}
