package com.zomboidcraft.client.camera;

import com.zomboidcraft.Cinematic;
import com.zomboidcraft.ZomboidCraft;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/**
 * The client half of /zc cinematic (com.zomboidcraft.Cinematic): while the server says CLEAN, every HUD piece, ours
 * and Minecraft's, steps aside and the green interact dots go, but the gun stays in your hands (unlike F1). The scope
 * and night-vision views stay, they're what you see through. Chat comes back while it's open, so you can type
 * the next command. SMOOTH turns on Minecraft's eased mouse look and puts it back as it was after.
 */
public final class CinematicView {
    /** Our own HUD pieces to clear as well; the scope and night-vision overlays are left alone. */
    private static final String[] OURS = {"field_kit_body", "field_kit_hud", "skills", "flight_hud"};

    private static boolean smoothOn;
    private static boolean smoothBefore;

    public static boolean clean() {
        return (bits() & Cinematic.CLEAN) != 0;
    }

    private static int bits() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : player.getAttachedOrElse(Cinematic.VIEW, 0);
    }

    /** Registered last, after every other HUD piece, so each of them can be wrapped. */
    public static void register() {
        for (Field f : VanillaHudElements.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == Identifier.class) {
                try {
                    Identifier id = (Identifier) f.get(null);
                    if (!id.equals(VanillaHudElements.MISC_OVERLAYS)) {
                        hide(id, id.equals(VanillaHudElements.CHAT));
                    }
                } catch (IllegalAccessException | RuntimeException ignored) {
                    // a piece this version doesn't have
                }
            }
        }
        for (String name : OURS) {
            try {
                hide(ZomboidCraft.id(name), false);
            } catch (RuntimeException ignored) {
                // not registered in this build
            }
        }
        com.zomboidcraft.client.interact.Interactables.drawsOwn(CinematicView::clean);
        ClientTickEvents.END_CLIENT_TICK.register(CinematicView::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> smooth(client, false)));
    }

    private static void hide(Identifier id, boolean chat) {
        HudElementRegistry.replaceElement(id, inner -> (g, delta) -> {
            if (!clean() || (chat && Minecraft.getInstance().gui.screen() instanceof ChatScreen)) {
                inner.extractRenderState(g, delta);
            }
        });
    }

    private static void tick(Minecraft client) {
        smooth(client, (bits() & Cinematic.SMOOTH) != 0);
    }

    private static void smooth(Minecraft client, boolean on) {
        if (on == smoothOn) {
            return;
        }
        if (on) {
            smoothBefore = client.options.smoothCamera;
            client.options.smoothCamera = true;
        } else {
            client.options.smoothCamera = smoothBefore;
        }
        smoothOn = on;
    }

    private CinematicView() {
    }
}
