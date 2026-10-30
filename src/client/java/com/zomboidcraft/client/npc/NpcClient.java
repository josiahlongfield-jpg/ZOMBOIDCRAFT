package com.zomboidcraft.client.npc;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The interact key on an NPC (follow or wait; sneaking, talk to her), and the green dot on her chest when you're close enough to use it. */
public final class NpcClient {
    private static final double REACH = 3.0;

    static void register() {
        Interactables.finder((client, player) -> {
            NpcEntity npc = lookedAt(client);
            return npc == null ? null : new Interactables.Spot(npc.position().add(0, npc.getBbHeight() * 0.7, 0), 0f);
        });
    }

    /** Sends the interact key to the NPC under the crosshair, if one's close; returns whether one was. */
    public static boolean interact(Minecraft client, HitResult hit) {
        NpcEntity npc = lookedAt(client);
        if (npc == null) {
            return false;
        }
        if (client.player.isShiftKeyDown()) {
            // sneaking: talk to her instead
            client.gui.setScreen(new TalkScreen(npc));
            return true;
        }
        ClientPlayNetworking.send(new NpcNet.Toggle(npc.getId()));
        return true;
    }

    private static NpcEntity lookedAt(Minecraft client) {
        if (client.player == null || !(client.hitResult instanceof EntityHitResult e) || !(e.getEntity() instanceof NpcEntity npc)) {
            return null;
        }
        Vec3 eye = client.player.getEyePosition();
        return e.getLocation().distanceTo(eye) <= REACH ? npc : null;
    }

    private NpcClient() {
    }
}
