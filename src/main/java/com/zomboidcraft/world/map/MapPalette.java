package com.zomboidcraft.world.map;

/**
 * The paper map's few inks, shared by the server (which draws a map you read from the county plan) and the client
 * (which paints it into your map). Cell value 0 means the map shows nothing there.
 */
public final class MapPalette {
    public static final byte NONE = 0, WATER = 1, DEEP = 2, GRASS = 3, FIELD = 4, FOREST = 5, ROCK = 6, SAND = 7,
            STREET = 8, PAVEMENT = 9, BUILDING = 10, HIGHWAY = 11, ROAD = 12, TRACK = 13, TOWN = 14;

    private static final int[] ARGB = {
            0x00000000, 0xFF8FAEC2, 0xFF7598B0, 0xFFCFC9A6, 0xFFD6C994, 0xFFA3B184, 0xFFB8AE9A, 0xFFE0D3A6,
            0xFFF3EFE4, 0xFFD9D2C3, 0xFFA88E78, 0xFFE6B25C, 0xFFF3EFE4, 0xFFC4AB7E, 0xFFC9B9A2};

    public static int argb(byte cell) {
        int i = cell & 0xFF;
        return i < ARGB.length ? ARGB[i] : 0;
    }

    private MapPalette() {
    }
}
