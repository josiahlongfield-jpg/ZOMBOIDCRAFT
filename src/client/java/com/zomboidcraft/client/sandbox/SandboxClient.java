package com.zomboidcraft.client.sandbox;

import com.zomboidcraft.sandbox.Sandbox;
import com.zomboidcraft.sandbox.SandboxNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Takes the world's settings from the server, and opens the Sandbox page when /zc sandbox asks. The page itself is the
 * UI's: it sets {@link #opener}, reads Sandbox.groups() / options() / value(), and sends SandboxNet.Set and
 * SandboxNet.Preset (or calls Sandbox.set for a setting marked client, which stays on this PC).
 */
public final class SandboxClient {
    /** Opens the Sandbox page; until the page is built it points you at /zc sandbox list. */
    public static Runnable opener = () -> {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(
                    "The Sandbox page isn't in this build yet: /zc sandbox list shows the world's settings."), false);
        }
    };

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SandboxNet.Sync.TYPE, (payload, context) ->
                context.client().execute(() -> Sandbox.clientSync(payload.preset(), payload.values())));
        ClientPlayNetworking.registerGlobalReceiver(SandboxNet.Open.TYPE, (payload, context) ->
                context.client().execute(() -> opener.run()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(Sandbox::clientLeft));
    }

    private SandboxClient() {
    }
}
