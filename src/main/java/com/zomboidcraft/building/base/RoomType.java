package com.zomboidcraft.building.base;

import java.util.EnumSet;
import java.util.Set;

/**
 * The nine rooms of a base (rooms plan section 2, Josiah's go 2026-11-06 12:13). A room is whichever of these it has
 * the most key fixtures for; it's only ever one, so a med bay with a stove in it is still a med bay.
 */
public enum RoomType {
    BUNK("Bunk room", EnumSet.of(Fixture.BED, Fixture.FOOTLOCKER)),
    KITCHEN("Kitchen", EnumSet.of(Fixture.STOVE, Fixture.WOOD_STOVE, Fixture.COUNTER, Fixture.FRIDGE)),
    MED_BAY("Med bay", EnumSet.of(Fixture.MEDICAL, Fixture.HOSPITAL_BED, Fixture.SURGICAL)),
    ARMOURY("Armoury", EnumSet.of(Fixture.GUN_BENCH, Fixture.GUN_RACK, Fixture.AMMO, Fixture.PARTS_BIN)),
    WORKSHOP("Workshop", EnumSet.of(Fixture.WORKBENCH, Fixture.TOOL_RACK, Fixture.SAWHORSE, Fixture.ANVIL, Fixture.WELDER)),
    MOTOR_POOL("Motor pool", EnumSet.of(Fixture.LIFT, Fixture.TOOL_CART, Fixture.GARAGE_DOOR, Fixture.FUEL_TANK)),
    COMMS("Comms post", EnumSet.of(Fixture.RADIO, Fixture.MAP_TABLE)),
    POWER("Power and water", EnumSet.of(Fixture.GENERATOR, Fixture.SWITCHBOARD, Fixture.WATER_TANK, Fixture.FILTER)),
    FARM("Farm", EnumSet.of(Fixture.PLANTER, Fixture.COMPOST));

    public final String label;
    public final Set<Fixture> keys;

    RoomType(String label, Set<Fixture> keys) {
        this.label = label;
        this.keys = keys;
    }

    public enum Grade {
        NONE("Not fitted"), BASIC("Basic"), EQUIPPED("Equipped"), FULL("Full");

        public final String label;

        Grade(String label) {
            this.label = label;
        }
    }
}
