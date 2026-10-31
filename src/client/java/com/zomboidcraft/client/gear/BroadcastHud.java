package com.zomboidcraft.client.gear;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.hud.Hud;
import com.zomboidcraft.gear.Broadcasts;
import java.util.ArrayDeque;
import java.util.Deque;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The broadcast captions, Zomboid's way: what the radio says comes up as text in the station's colour, with the
 * station's name over it, above the hotbar. The last three lines stay up, the oldest fading first.
 */
public final class BroadcastHud {
    private record Line(String station, int colour, String text, long at) {
    }

    private static final int KEEP = 3;
    private static final Deque<Line> LINES = new ArrayDeque<>();

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Broadcasts.Caption.TYPE, (payload, context) -> context.client().execute(() -> {
            LINES.addLast(new Line(payload.station(), payload.colour(), payload.text(), System.currentTimeMillis()));
            while (LINES.size() > KEEP) {
                LINES.removeFirst();
            }
        }));
        HudElementRegistry.addLast(ZomboidCraft.id("broadcast_captions"), BroadcastHud::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (LINES.isEmpty() || mc.player == null || mc.options.hideGui) {
            return;
        }
        long now = System.currentTimeMillis();
        long life = Math.round(ZcConfig.get().broadcast.captionSeconds * 1000);
        LINES.removeIf(l -> now - l.at() > life);
        Font font = mc.font;
        int width = Math.min(320, g.guiWidth() - 40);
        int x = (g.guiWidth() - width) / 2;
        int y = g.guiHeight() - 72;
        // newest at the bottom, the older ones stacked up above it
        Line[] lines = LINES.toArray(new Line[0]);
        for (int i = lines.length - 1; i >= 0; i--) {
            Line l = lines[i];
            float a = Math.min(1f, (life - (now - l.at())) / 1200f) * (i == lines.length - 1 ? 1f : 0.7f);
            Component text = Hud.text(l.text());
            int h = font.split(text, width - 8).size() * 9;
            y -= h + 14;
            g.fill(x, y - 2, x + width, y + h + 12, Hud.alpha(0x99000000, a));
            g.text(font, Hud.text(l.station().toUpperCase(java.util.Locale.ROOT)), x + 4, y, Hud.alpha(0xFF000000 | l.colour(), a * 0.8f), false);
            g.textWithWordWrap(font, text, x + 4, y + 11, width - 8, Hud.alpha(0xFF000000 | l.colour(), a), false);
        }
    }

    private BroadcastHud() {
    }
}
