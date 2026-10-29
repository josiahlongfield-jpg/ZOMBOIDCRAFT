package com.zomboidcraft.client.map;

import com.zomboidcraft.world.map.MapNet;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Where every survivor is, as the server last said (twice a second), for the map page. */
public final class MapPlayers {
    private static volatile List<MapNet.Spot> spots = List.of();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(MapNet.Players.TYPE, (payload, context) -> spots = List.copyOf(payload.spots()));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> spots = List.of());
    }

    public static List<MapNet.Spot> spots() {
        return spots;
    }

    /** A colour of its own for each survivor, the same every time. */
    public static int colour(java.util.UUID id) {
        int[] inks = {0xFF7FAEDB, 0xFFA8BC6E, 0xFFD0675E, 0xFFE2B84C, 0xFFB98AD6, 0xFF6EC4B8, 0xFFE08A5C, 0xFFE8DFC8};
        return inks[Math.floorMod(id.hashCode(), inks.length)];
    }

    private MapPlayers() {
    }
}
