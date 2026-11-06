package com.zomboidcraft.client.gear;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.gear.CommandNet;
import com.zomboidcraft.gear.HuntBoard;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/**
 * The hunt board as this client last heard it, for the UI thread's page: {@link #board()} is the server's JSON,
 * {total, hears, entries:[{id, kind, title, where, doneWhen, gives, caption, status, credit, time, doneAt, seen}],
 * log:[{text, time}], strike:{ready, readyAt, bonus, mine, pending?:{type, mark, names}, last?:{type, day, kills,
 * friendlies}}}. {@link #hears()} says whether the player can open it (a comms headset, or a live military or
 * field radio); {@link #seen} drops an entry's "New" tag.
 */
public final class HuntBoardClient {
    private static JsonObject board = new JsonObject();
    private static int version;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(HuntBoard.Sync.TYPE, (payload, context) -> context.client().execute(() -> {
            board = JsonParser.parseString(payload.json()).getAsJsonObject();
            version++;
        }));
    }

    /** The latest board (do not change it). */
    public static JsonObject board() {
        return board;
    }

    /** Goes up each time a new board arrives, so a page can tell when to redraw. */
    public static int version() {
        return version;
    }

    /** Whether the local player hears the command net now. */
    public static boolean hears() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && CommandNet.hears(mc.player);
    }

    /** Tells the server this entry has been seen. */
    public static void seen(String id) {
        ClientPlayNetworking.send(new HuntBoard.Seen(id));
    }

    private HuntBoardClient() {
    }
}
