package com.zomboidcraft.building.base;

/** Base claims and the room check (rooms plan step 1). */
public final class Bases {
    public static void register() {
        Claims.register();
        Rooms.register();
    }

    private Bases() {
    }
}
