package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Our own guns from before the Brita pack, gone from the game (Josiah 2026-11-02: "get rid of all the guns (including
 * their audio) and attachments we added prior to the brita pack", keeping only the Intervention-style Heavy Sniper and
 * everything Brita). The item types stay registered, because each Brita gun is one of them underneath (its anchor), but
 * a plain one is never handed out again: not in loot, cases or the creative tab, and any copy already in a world
 * vanishes as it is loaded (a player's pockets, an opened chest, boot or bag, a dropped item, a mob's hands).
 * The Heavy Sniper keeps its own long-range scope, sniper suppressor, bipod, magazine and rounds.
 */
public final class Retired {
    /** Our guns that went: every type but the Heavy Sniper. */
    public static final Set<String> GUNS = Set.of("assault_rifle", "pistol", "shotgun", "hunting_rifle", "double_barrel",
            "revolver", "rimfire", "smg", "sawn_off", "magnum", "lever_action", "crossbow", "battle_rifle", "pocket_pistol",
            "machine_pistol", "suppressed_rifle", "precision_rifle", "grenade_launcher");
    /** Our attachments that went: all but the Heavy Sniper's own three. */
    public static final Set<String> ATTACHMENTS = Set.of("red_dot_sight", "holo_sight", "mini_red_dot", "compact_red_dot",
            "scope_4x", "combat_scope", "rifle_scope", "pistol_scope", "precision_scope", "hunting_scope", "crossbow_scope",
            "magnifier", "foregrip", "angled_grip", "stubby_grip", "skeleton_grip", "tactical_grip", "underbarrel_launcher",
            "weapon_light", "compact_light", "laser_sight", "rifle_suppressor", "pistol_suppressor", "rifle_stock");
    /** Plain magazines of those guns (a Brita gun's own magazine is one of these carrying its id, and stays). */
    public static final Set<String> MAGAZINES = Set.of("rifle_magazine", "pistol_magazine", "hunting_rifle_magazine",
            "rimfire_magazine", "smg_magazine", "battle_rifle_magazine", "pocket_pistol_magazine", "machine_pistol_magazine",
            "precision_rifle_magazine");
    /** Rounds nothing fires any more: .30-30 (our lever gun, and its spent cases) and 40mm grenades (our launcher, the
     * underbarrel one). */
    public static final Set<String> ROUNDS = Set.of("lever_ammo", "spent_3030", "grenade_round");

    private Retired() {
    }

    /** Whether an item id (its path) is one that went. */
    public static boolean id(String path) {
        return GUNS.contains(path) || ATTACHMENTS.contains(path) || MAGAZINES.contains(path) || ROUNDS.contains(path);
    }

    /**
     * Whether a stack is one of ours that went: never a Brita gun or one of its magazines (they carry its id), nor a
     * sight or grip of his (combat/LocalParts); a local gun he took out of the game (and its magazines) does go.
     */
    public static boolean is(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.has(LocalParts.ID)) {
            return false;       // a sight or grip of his kept on this computer rides on our red dot or foregrip item
        }
        if (stack.has(LocalGuns.ID)) {
            return LocalGuns.removed(stack.get(LocalGuns.ID));    // a local gun he took out, or one of its magazines
        }
        return id(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    /**
     * The stack with what went taken out of it: empty if it went itself, else (a bag, a gun case) with its contents
     * cleared of them. Null if nothing changed.
     */
    public static ItemStack cleaned(ItemStack stack) {
        if (is(stack)) {
            return ItemStack.EMPTY;
        }
        ItemStack out = null;
        // a kept gun (the Heavy Sniper) with one of our old parts fitted keeps the gun and loses the part
        Attachments fitted = stack.get(Attachments.TYPE);
        if (fitted != null && fitted.mounted().values().stream().anyMatch(Retired::is)) {
            java.util.Map<Attachments.Slot, ItemStack> kept = new java.util.EnumMap<>(Attachments.Slot.class);
            fitted.mounted().forEach((slot, part) -> {
                if (!is(part)) {
                    kept.put(slot, part);
                }
            });
            out = stack.copy();
            out.set(Attachments.TYPE, new Attachments(kept));
        }
        ItemContainerContents in = stack.get(DataComponents.CONTAINER);
        if (in == null) {
            return out;
        }
        List<ItemStack> items = new ArrayList<>();
        boolean changed = false;
        for (ItemStack s : in.nonEmptyItemCopyStream().toList()) {
            ItemStack c = cleaned(s);
            if (c != null) {
                changed = true;
                s = c;
            }
            items.add(s);
        }
        if (!changed) {
            return out;
        }
        out = out == null ? stack.copy() : out;
        out.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        return out;
    }

    /** Clears what went out of a container. Returns how many stacks it took or changed. */
    public static int purge(Container container) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack c = cleaned(container.getItem(i));
            if (c != null) {
                container.setItem(i, c);
                n++;
            }
        }
        return n;
    }

    private static int purgeMenu(ServerPlayer player) {
        int n = 0;
        for (Slot slot : player.containerMenu.slots) {
            ItemStack c = cleaned(slot.getItem());
            if (c != null) {
                slot.set(c);
                n++;
            }
        }
        ItemStack carried = cleaned(player.containerMenu.getCarried());
        if (carried != null) {
            player.containerMenu.setCarried(carried);
            n++;
        }
        return n;
    }

    public static void register() {
        // what's open in front of someone is cleared at once (a chest, a car's boot or glove box, a bag); their own
        // pockets every couple of seconds
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long t = server.getTickCount();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                int n = 0;
                if (player.containerMenu != player.inventoryMenu) {
                    n += purgeMenu(player);
                }
                if ((t + player.getId()) % 40 == 0) {
                    n += purge(player.getInventory());
                    n += purge(player.getEnderChestInventory());
                }
                if (n > 0) {
                    player.containerMenu.broadcastChanges();
                    ZomboidCraft.LOGGER.info("Took {} of our old guns, attachments or rounds from {}", n, player.getName().getString());
                }
            }
        });
        // lying on the ground, or in someone's hands, as the chunk comes in
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity item) {
                ItemStack c = cleaned(item.getItem());
                if (c != null) {
                    if (c.isEmpty()) {
                        item.discard();
                    } else {
                        item.setItem(c);
                    }
                }
            } else if (entity instanceof LivingEntity living && !(entity instanceof ServerPlayer)) {
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack c = cleaned(living.getItemBySlot(slot));
                    if (c != null) {
                        living.setItemSlot(slot, c);
                    }
                }
                if (entity instanceof Container box) {
                    purge(box);
                }
                if (entity instanceof com.zomboidcraft.npc.NpcEntity npc) {
                    purge(com.zomboidcraft.npc.NpcWork.pack(npc));
                }
            }
        });
    }
}
