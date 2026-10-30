package com.zomboidcraft.client.npc;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcNet;
import com.zomboidcraft.npc.NpcWardrobe;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The interact key on an NPC, and the green dot showing what it'll do. Looking at her face or neck, the dot sits at her
 * collar: follow or wait (sneaking, talk to her). A companion with a wardrobe (NpcWardrobe) also has a dot on each
 * piece she's wearing, where the crosshair meets it (shoes, stockings, skirt or shorts, sweater or slip dress): that
 * one opens her clothes (WardrobeScreen) for that piece.
 */
public final class NpcClient {
    private static final double REACH = 3.0;

    /** A part of her: from and to (fractions of her height), where its dot sits, how far out from her middle, and the pieces worn there. */
    private record Zone(float from, float to, float at, float out, NpcWardrobe.Piece... pieces) {
    }

    private static final Zone[] ZONES = {
            new Zone(0f, 0.14f, 0.05f, 0.13f, NpcWardrobe.Piece.BOOTS, NpcWardrobe.Piece.HEELS),
            new Zone(0.14f, 0.46f, 0.30f, 0.10f, NpcWardrobe.Piece.STOCKINGS),
            new Zone(0.46f, 0.62f, 0.54f, 0.17f, NpcWardrobe.Piece.SKIRT, NpcWardrobe.Piece.SHORTS, NpcWardrobe.Piece.DRESS),
            new Zone(0.62f, 0.80f, 0.70f, 0.16f, NpcWardrobe.Piece.SWEATER, NpcWardrobe.Piece.DRESS),
    };

    static void register() {
        Interactables.finder((client, player) -> {
            NpcEntity npc = lookedAt(client);
            if (npc == null) {
                return null;
            }
            Zone z = zone(client, npc);
            float at = z != null ? z.at() : 0.84f, out = z != null ? z.out() : 0.08f;
            // on the side facing you
            Vec3 toYou = player.position().subtract(npc.position()).multiply(1, 0, 1);
            Vec3 o = toYou.lengthSqr() > 1e-4 ? toYou.normalize().scale(out) : Vec3.ZERO;
            return new Interactables.Spot(npc.position().add(o.x, npc.getBbHeight() * at, o.z), 0f);
        });
    }

    /** Sends the interact key to the NPC under the crosshair, if one's close; returns whether one was. */
    public static boolean interact(Minecraft client, HitResult hit) {
        NpcEntity npc = lookedAt(client);
        if (npc == null) {
            return false;
        }
        NpcWardrobe.Piece piece = piece(client, npc);
        if (piece != null) {
            client.gui.setScreen(new WardrobeScreen(npc, piece));
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

    /** The part of her under the crosshair, if a piece she's wearing is there (and she has a wardrobe). */
    private static Zone zone(Minecraft client, NpcEntity npc) {
        if (!(client.hitResult instanceof EntityHitResult e) || !npc.isAlive()) {
            return null;
        }
        double up = (e.getLocation().y - npc.getY()) / npc.getBbHeight();
        NpcSkin skin = up < 0.8 ? NpcSkin.get(npc.model()) : null;
        if (skin == null || !skin.has("sweater")) {
            return null;
        }
        for (Zone z : ZONES) {
            if (up >= z.from() && up < z.to() && worn(npc, z) != null) return z;
        }
        return null;
    }

    private static NpcWardrobe.Piece worn(NpcEntity npc, Zone z) {
        for (NpcWardrobe.Piece p : z.pieces()) {
            if (!npc.hidden().contains(p.id)) return p;
        }
        return null;
    }

    private static NpcWardrobe.Piece piece(Minecraft client, NpcEntity npc) {
        Zone z = zone(client, npc);
        return z == null ? null : worn(npc, z);
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
