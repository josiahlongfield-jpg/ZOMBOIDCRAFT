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

    /**
     * Our melee weapons, gone for Tarkov's (Josiah 2026-11-07: "all of them will need to be tarkov ones"). The types
     * stay registered because each Tarkov blade is one underneath (its anchor: the Ka-Bar a kitchen knife, the Freeman
     * crowbar a crowbar, so it does the crowbar's jobs too); only a plain one goes.
     */
    public static final Set<String> MELEE = Set.of("lead_pipe", "golf_club", "hockey_stick", "baseball_bat",
            "spiked_bat", "machete", "tomahawk", "katana", "wooden_spear", "fire_hardened_spear", "spear_knife",
            "stone_axe", "stone_hammer", "stone_knife", "plank_nails", "cudgel_nails");
    /**
     * Tools with a Tarkov twin (the kitchen knife, crowbar, shovel, fire axe and sledgehammer: Josiah 2026-11-07, "weapons
     * go, tools stay" and the twins take their jobs). A plain one stays as a tool that can't hit until a Tarkov blade
     * anchored on it is on this computer; then the Tarkov one does its jobs and the plain one goes.
     */
    public static final Set<String> TWIN_TOOLS = Set.of("kitchen_knife", "crowbar", "shovel", "fire_axe", "sledgehammer");
    /** The tools that stay but never hit (the hammer, frying pan and pipe wrench have no Tarkov twin). */
    public static final Set<String> TOOLS_ONLY = Set.of("hammer", "frying_pan", "pipe_wrench");

    /** Whether a plain one of this tool can't hit: no Tarkov twin, or (gone anyway) one that has one. */
    public static boolean toolOnly(String path) {
        return TOOLS_ONLY.contains(path) || TWIN_TOOLS.contains(path);
    }

    /** Whether a Tarkov blade on this computer is one of this item underneath (its anchor). */
    static boolean twinHere(String path) {
        for (LocalHands.Spec s : LocalHands.all()) {
            if (path.equals(s.anchor)) return true;
        }
        return false;
    }

    /** Whether an item id (its path) is one that went. */
    public static boolean id(String path) {
        return GUNS.contains(path) || ATTACHMENTS.contains(path) || MAGAZINES.contains(path) || ROUNDS.contains(path)
                || MELEE.contains(path) || TWIN_TOOLS.contains(path) && twinHere(path);
    }

    /**
     * Whether a stack is one of ours that went: a local gun he took out of the game (its file says "removed", as every
     * Brita gun's does since 2026-11-07) and its magazines, or a sight or grip of his whose part file is gone.
     */
    public static boolean is(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.has(LocalParts.ID)) {
            // a sight or grip of his kept on this computer rides on our red dot or foregrip item; one whose file has gone
            // (his pack parts went with the Brita guns, Josiah 2026-11-07) goes too
            return LocalParts.get(stack.get(LocalParts.ID)) == null;
        }
        if (stack.has(LocalHands.ID)) {
            return false;       // a Tarkov blade on its anchor
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
        if (stack.getItem() instanceof MagazineItem mag) {
            // no magazines any more (Josiah 2026-11-08, combat/AmmoPouch): one found is its rounds, loose
            int n = MagazineItem.rounds(stack) * stack.getCount();
            net.minecraft.world.item.Item ammo = LocalGuns.ammo(stack, mag.type());
            return n <= 0 ? ItemStack.EMPTY : new ItemStack(ammo, Math.min(n, new ItemStack(ammo).getMaxStackSize()));
        }
        ItemStack out = padMoved(stack);
        if (out != null) stack = out;
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

    /**
     * A Tarkov gun saved before butt pads had their own bench slot (2026-11-07) kept its pad in the stock slot: the pad
     * moves to its own slot and the gun's kit stock goes back under it. Null if nothing changed.
     */
    private static @org.jetbrains.annotations.Nullable ItemStack padMoved(ItemStack gun) {
        Attachments fitted = gun.get(Attachments.TYPE);
        if (fitted == null) return null;
        ItemStack pad = fitted.mounted().get(Attachments.Slot.STOCK);
        String padId = pad == null ? null : TarkovParts.of(pad);
        TarkovParts.Part p = padId == null ? null : TarkovParts.get(padId);
        if (p == null || p.slot() != Attachments.Slot.BUTTPAD || fitted.mounted().containsKey(Attachments.Slot.BUTTPAD)) return null;
        java.util.Map<Attachments.Slot, ItemStack> now = new java.util.EnumMap<>(Attachments.Slot.class);
        now.putAll(fitted.mounted());
        now.remove(Attachments.Slot.STOCK);
        now.put(Attachments.Slot.BUTTPAD, pad);
        String stock = TarkovFit.kitSockets(LocalGuns.of(gun)).get(Attachments.Slot.STOCK);
        if (stock != null) {
            ItemStack s = TarkovParts.make(stock);
            if (!s.isEmpty()) now.put(Attachments.Slot.STOCK, s);
        }
        ItemStack out = gun.copy();
        out.set(Attachments.TYPE, new Attachments(now));
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
