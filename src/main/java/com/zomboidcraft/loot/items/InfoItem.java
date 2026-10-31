package com.zomboidcraft.loot.items;

import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Something that tells you something while it's in your main hand: a compass your heading, a GPS where you are, a
 * watch the time. The watch keeps survival time the way Zomboid does: day 1 starts at 9:00, a day every 20 minutes.
 */
public class InfoItem extends LootItem {
    public enum Kind { COMPASS, GPS, WATCH }

    static final Map<String, Kind> KINDS = Map.of(
            "compass", Kind.COMPASS, "gps_unit", Kind.GPS,
            "wristwatch", Kind.WATCH, "gold_watch", Kind.WATCH, "pocket_watch", Kind.WATCH,
            "chronometer", Kind.WATCH);

    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final Kind kind;

    InfoItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(InfoItem::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ItemStack held = sp.getMainHandItem();
            if (held.getItem() instanceof InfoItem info) {
                sp.sendOverlayMessage(Component.literal(info.read(sp)).withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private String read(ServerPlayer sp) {
        // yaw 0 faces south (+z); turned so 0 is north
        int heading = Math.floorMod(Math.round(sp.getYRot()) + 180, 360);
        String facing = POINTS[Math.floorMod(Math.round(heading / 45f), 8)] + " " + heading + "°";
        return switch (kind) {
            case COMPASS -> "Facing " + facing;
            case GPS -> String.format("X %d  Y %d  Z %d   %s", Mth.floor(sp.getX()), Mth.floor(sp.getY()),
                    Mth.floor(sp.getZ()), facing);
            case WATCH -> time(sp.level().getGameTime());
        };
    }

    static String time(long ticks) {
        long minutes = ticks * 1440 / 24000 + 9 * 60;
        return String.format("Day %d, %02d:%02d", minutes / 1440 + 1, minutes / 60 % 24, minutes % 60);
    }
}
