package com.zomboidcraft.carry;

/** Where a bag goes on you. Hand bags ride in the off hand and take that hand. */
public enum Wear {
    BACK("Back"),
    VEST("Vest"),
    WAIST("Waist"),
    HAND("Hand");

    public final String label;

    Wear(String label) {
        this.label = label;
    }
}
