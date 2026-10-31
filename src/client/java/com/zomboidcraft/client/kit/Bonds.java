package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.NpcPanel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Companions you've bonded with (Josiah 2026-10-30): the first G on someone asks whether to bond, and once bonded, K
 * brings her page up from anywhere. With more than one, K opens the selection screen (BondsScreen) instead.
 */
public final class Bonds {
    public static final KeyMapping KEY = new KeyMapping("key.zomboidcraft.companions", InputConstants.KEY_K, CombatClient.CATEGORY);

    private static List<NpcPanel.Bonded> list = List.of();
    /** Stand-ins for companions too far off for the client to have loaded, so their pages can still draw them. */
    private static final Map<String, NpcEntity> standIns = new HashMap<>();
    /** Who you said "not now" to this session: G opens their page without asking again. */
    private static final Set<java.util.UUID> declined = new HashSet<>();

    public static void register() {
        KeyMappingHelper.registerKeyMapping(KEY);
        ClientPlayNetworking.registerGlobalReceiver(NpcPanel.Bonds.TYPE, (payload, context) -> list = payload.list());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (NpcEntity n : standIns.values()) n.tickCount++;
            while (KEY.consumeClick()) {
                if (client.gui.screen() == null && client.player != null) open(client);
            }
        });
    }

    /** K: her page if you're bonded with one, the selection if with more. */
    public static void open(Minecraft client) {
        List<NpcEntity> all = companions(client);
        if (all.isEmpty()) {
            client.player.sendOverlayMessage(Component.literal("You haven't bonded with anyone yet: walk up to someone and press G"));
        } else if (all.size() == 1) {
            client.gui.setScreen(new CompanionScreen(all.get(0), CompanionScreen.Tab.HER));
        } else {
            client.gui.setScreen(new BondsScreen(0));
        }
    }

    public static boolean bonded(NpcEntity npc) {
        String id = npc.getUUID().toString();
        for (NpcPanel.Bonded b : list) {
            if (b.uuid().equals(id)) return true;
        }
        return false;
    }

    /** Whether G on her should ask to bond first. */
    public static boolean ask(NpcEntity npc) {
        return !bonded(npc) && !declined.contains(npc.getUUID());
    }

    public static void decline(NpcEntity npc) {
        declined.add(npc.getUUID());
    }

    public static void bond(NpcEntity npc, boolean on) {
        ClientPlayNetworking.send(new NpcPanel.Bond(npc.getId(), on));
        // shown at once; the server's list follows
        List<NpcPanel.Bonded> next = new ArrayList<>(list);
        next.removeIf(b -> b.uuid().equals(npc.getUUID().toString()));
        if (on) {
            next.add(new NpcPanel.Bonded(npc.getUUID().toString(), npc.getId(), npc.getName().getString(), npc.model(), npc.look(),
                    String.join(",", npc.hidden())));
        } else {
            declined.add(npc.getUUID());
        }
        list = next;
    }

    /** Your bonded companions: the ones the client has, or stand-ins dressed as the server last saw them. */
    public static List<NpcEntity> companions(Minecraft client) {
        List<NpcEntity> out = new ArrayList<>();
        if (client.level == null) return out;
        for (NpcPanel.Bonded b : list) {
            if (client.level.getEntity(b.id()) instanceof NpcEntity npc && npc.getUUID().toString().equals(b.uuid())) {
                out.add(npc);
                continue;
            }
            NpcEntity stand = standIns.computeIfAbsent(b.uuid(), u -> {
                NpcEntity n = com.zomboidcraft.npc.Npcs.NPC.create(client.level, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                if (n != null) n.setUUID(java.util.UUID.fromString(u));
                return n;
            });
            if (stand == null) continue;
            stand.setId(b.id());
            stand.setModel(b.model(), b.look());
            stand.setHidden(b.hidden().isEmpty() ? Set.of() : new HashSet<>(List.of(b.hidden().split(","))));
            stand.setCustomName(Component.literal(b.name()));
            out.add(stand);
        }
        return out;
    }

    private Bonds() {
    }
}
