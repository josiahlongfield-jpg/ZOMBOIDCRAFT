package com.zomboidcraft.client.npc;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The interact key on an NPC, and the green dot showing what it'll do. Looking at her shoulders or face, the dot sits
 * on her chest: follow or wait (sneaking, talk to her). Looking lower, a companion with a wardrobe (NpcWardrobe) gets
 * the dot at her waist instead: pick her outfit yourself (WardrobeScreen).
 */
public final class NpcClient {
    private static final double REACH = 3.0;

    static void register() {
        Interactables.finder((client, player) -> {
            NpcEntity npc = lookedAt(client);
            if (npc == null) {
                return null;
            }
            if (atClothes(client, npc)) {
                // her waist, on the side facing you
                Vec3 toYou = player.position().subtract(npc.position()).multiply(1, 0, 1);
                Vec3 out = toYou.lengthSqr() > 1e-4 ? toYou.normalize().scale(0.16) : Vec3.ZERO;
                return new Interactables.Spot(npc.position().add(out.x, npc.getBbHeight() * 0.52, out.z), 0f);
            }
            return new Interactables.Spot(npc.position().add(0, npc.getBbHeight() * 0.7, 0), 0f);
        });
    }

    /** Sends the interact key to the NPC under the crosshair, if one's close; returns whether one was. */
    public static boolean interact(Minecraft client, HitResult hit) {
        NpcEntity npc = lookedAt(client);
        if (npc == null) {
            return false;
        }
        if (atClothes(client, npc)) {
            client.gui.setScreen(new WardrobeScreen(npc, true));
            return true;
        }
        if (client.player.isShiftKeyDown()) {
            // sneaking: talk to her instead
            client.gui.setScreen(new TalkScreen(npc));
            return true;
        }
        ClientPlayNetworking.send(new NpcNet.Toggle(npc.getId()));
        return true;
    }

    /** Looking at her below the chest (where the crosshair meets her), and she has clothes to change into. */
    private static boolean atClothes(Minecraft client, NpcEntity npc) {
        if (!(client.hitResult instanceof EntityHitResult e)) {
            return false;
        }
        double up = (e.getLocation().y - npc.getY()) / npc.getBbHeight();
        NpcSkin skin = up < 0.6 ? NpcSkin.get(npc.model()) : null;
        return skin != null && skin.has("sweater") && npc.isAlive();
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
