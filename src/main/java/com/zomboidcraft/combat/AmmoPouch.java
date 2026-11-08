package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.Carry;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The rounds you carry (Josiah 2026-11-08: "anytime you reload you should have a full mag loaded in", and he chose a
 * hidden count over loose round items): one number per calibre, by its round's item id, never an item. Rounds and
 * magazines you pick up go into it, up to {@link #cap} (guns.spareMagazines spare magazines' worth); what's over
 * stays where it was. Every reload puts a full magazine in from it, or what's left if there's less, and the rounds
 * still in the gun go back into it (combat/Guns). There are no magazine items any more: any that turn up (an old
 * world, a gun's magazine taken off) are emptied into the count, or into loose rounds in a box or on the ground
 * (combat/Retired). Synced to your own client for the HUD's "in the gun / carried".
 */
public final class AmmoPouch {
    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final StreamCodec<ByteBuf, Map<String, Integer>> STREAM =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final AttachmentType<Map<String, Integer>> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("ammo_carried"),
            builder -> builder
                    .persistent(CODEC)
                    .initializer(Map::of)
                    .syncWith(STREAM, AttachmentSyncPredicate.targetOnly()));

    /** How often (ticks) pockets and bags are swept for rounds and magazines that came in another way. */
    private static final int SWEEP = 10;
    /** Ticks between "can't carry any more" lines for one player. */
    private static final int TOLD = 40;

    private static final Map<java.util.UUID, Long> TOLD_AT = new HashMap<>();
    private static Set<Item> rounds = Set.of();
    private static long roundsAt = Long.MIN_VALUE;

    private AmmoPouch() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long t = server.getTickCount();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if ((t + player.getId()) % SWEEP == 0 && player.isAlive()) {
                    sweep(player);
                }
            }
        });
    }

    private static String key(Item ammo) {
        return BuiltInRegistries.ITEM.getKey(ammo).toString();
    }

    /** Rounds of this kind carried. Works on your own client too (the HUD). */
    public static int carried(Player player, Item ammo) {
        Map<String, Integer> m = player.getAttached(TYPE);
        return m == null ? 0 : m.getOrDefault(key(ammo), 0);
    }

    /** Every round carried, of all kinds (the supplies page's total). */
    public static int total(Player player) {
        Map<String, Integer> m = player.getAttached(TYPE);
        return m == null ? 0 : m.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void set(Player player, Item ammo, int n) {
        Map<String, Integer> next = new HashMap<>(player.getAttachedOrCreate(TYPE));
        if (n <= 0) {
            next.remove(key(ammo));
        } else {
            next.put(key(ammo), n);
        }
        player.setAttached(TYPE, Map.copyOf(next));
    }

    /**
     * Puts up to n rounds in; returns how many went. Past the cap only when forced (a gun's own rounds coming back
     * out of it at a reload, which were carried a moment before).
     */
    public static int add(Player player, Item ammo, int n, boolean forced) {
        if (n <= 0) {
            return 0;
        }
        int have = carried(player, ammo);
        int took = forced ? n : (int) Math.max(0, Math.min((long) n, (long) cap(ammo) - have));
        if (took > 0) {
            set(player, ammo, have + took);
        }
        return took;
    }

    /** Takes up to n rounds out; returns how many it got. */
    public static int take(Player player, Item ammo, int n) {
        int have = carried(player, ammo);
        int took = Math.max(0, Math.min(n, have));
        if (took > 0) {
            set(player, ammo, have - took);
        }
        return took;
    }

    /** The most of a round anyone carries: spare magazines' worth of the biggest magazine that takes it. */
    public static int cap(Item ammo) {
        int spare = ZcConfig.get().guns.spareMagazines;
        return spare <= 0 ? Integer.MAX_VALUE : spare * biggestMagazine(ammo);
    }

    /** The biggest magazine (or tube, or cylinder) of a gun in the game that fires this round. */
    static int biggestMagazine(Item ammo) {
        int most = 0;
        for (LocalGuns.Spec s : LocalGuns.all()) {
            if (s.removed) continue;
            ItemStack gun = LocalGuns.loaded(s);
            if (LocalGuns.ammo(gun, s.type()) == ammo) most = Math.max(most, s.type().capacity(gun));
        }
        for (GunType t : GunType.values()) {
            if (most == 0 && t.ammo() == ammo) most = Math.max(most, t.capacity());
        }
        return Math.max(1, most);
    }

    /** Whether an item is a round some gun fires (so it's carried as a count, never as an item). */
    public static boolean round(Item item) {
        long now = System.currentTimeMillis();
        if (now - roundsAt > 10_000) {
            Set<Item> next = new HashSet<>();
            for (GunType t : GunType.values()) next.add(t.ammo());
            for (LocalGuns.Spec s : LocalGuns.all()) {
                if (!s.removed) next.add(LocalGuns.ammo(LocalGuns.loaded(s), s.type()));
            }
            next.removeIf(i -> Retired.id(BuiltInRegistries.ITEM.getKey(i).getPath()));
            rounds = next;
            roundsAt = now;
        }
        return rounds.contains(item);
    }

    /** Whether a stack goes into the count: loose rounds, or a magazine (its rounds; the magazine itself goes). */
    public static boolean counted(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof MagazineItem || round(stack.getItem()));
    }

    /** What a round is called, by the guns that fire it ("9x18mm PM"). */
    public static String calibre(Item ammo) {
        for (LocalGuns.Spec s : LocalGuns.all()) {
            if (s.removed || s.calibre == null) continue;
            if (LocalGuns.ammo(LocalGuns.loaded(s), s.type()) == ammo) return s.calibre;
        }
        for (GunType t : GunType.values()) {
            if (t.ammo() == ammo) return t.caliber();
        }
        return ammo.getName(new ItemStack(ammo)).getString();
    }

    /**
     * Takes a stack into the count as far as there's room: it shrinks by the rounds that went in (a magazine is gone
     * once its last round is out, an empty one at once). Says so when it's full. Returns whether anything went.
     */
    public static boolean absorb(Player player, ItemStack stack) {
        if (!counted(stack)) {
            return false;
        }
        Item ammo;
        int rounds;
        String calibre;
        if (stack.getItem() instanceof MagazineItem mag) {
            ammo = LocalGuns.ammo(stack, mag.type());
            rounds = MagazineItem.rounds(stack) * stack.getCount();
            calibre = LocalGuns.calibre(stack, mag.type());
        } else {
            ammo = stack.getItem();
            rounds = stack.getCount();
            calibre = null;
        }
        int took = add(player, ammo, rounds, false);
        if (stack.getItem() instanceof MagazineItem) {
            if (took >= rounds) {
                stack.setCount(0);
            } else if (took > 0) {
                stack.setCount(1);
                stack.set(GunState.MAG_ROUNDS, rounds - took);
            }
        } else {
            stack.shrink(took);
        }
        if (!stack.isEmpty()) {
            full(player, calibre != null ? calibre : calibre(ammo));
        }
        return took > 0 || stack.isEmpty();
    }

    private static void full(Player player, String calibre) {
        long now = player.level().getGameTime();
        Long at = TOLD_AT.get(player.getUUID());
        if (at != null && now - at < TOLD && now >= at) {
            return;
        }
        TOLD_AT.put(player.getUUID(), now);
        player.sendOverlayMessage(Component.literal("You can't carry any more " + calibre + " rounds.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Rounds and magazines that reached your pockets or bags some other way (taken from a box, an old save, a gun's
     * magazine taken off) go into the count; what's over the cap is dropped at your feet.
     */
    static void sweep(ServerPlayer player) {
        boolean changed = sweep(player, player.getInventory(), player.getInventory().getContainerSize());
        if (player.inventoryMenu instanceof Carry.Bags menu) {
            for (BagContainer bag : menu.zomboidcraft$bags()) {
                changed |= sweep(player, bag, bag.getContainerSize());
            }
        }
        if (changed) {
            player.containerMenu.broadcastChanges();
        }
    }

    private static boolean sweep(ServerPlayer player, Container box, int size) {
        boolean changed = false;
        for (int i = 0; i < size; i++) {
            ItemStack s = box.getItem(i);
            if (!counted(s)) continue;
            absorb(player, s);
            if (!s.isEmpty()) {
                spill(player, s.copy(), false);
            }
            box.setItem(i, ItemStack.EMPTY);
            changed = true;
        }
        if (changed) box.setChanged();
        return changed;
    }

    /** Drops a stack at your feet (or scattered round you), not to be picked straight back up. */
    private static void spill(Player player, ItemStack stack, boolean around) {
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getEyeY() - 0.3, player.getZ(), stack);
        dropped.setPickUpDelay(40);
        float speed = around ? player.getRandom().nextFloat() * 0.5f : 0.05f;
        float angle = player.getRandom().nextFloat() * (float) (Math.PI * 2);
        dropped.setDeltaMovement(-Math.sin(angle) * speed, 0.2, Math.cos(angle) * speed);
        player.level().addFreshEntity(dropped);
    }

    /** On death what you carried falls with you, as loose rounds, and the count starts again. */
    public static void dropAll(Player player) {
        Map<String, Integer> m = player.getAttached(TYPE);
        if (m == null || m.isEmpty()) {
            return;
        }
        m.forEach((id, n) -> {
            Item ammo = BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id)).orElse(null);
            if (ammo == null) return;
            int max = new ItemStack(ammo).getMaxStackSize();
            for (int left = n; left > 0; left -= max) {
                spill(player, new ItemStack(ammo, Math.min(left, max)), true);
            }
        });
        player.setAttached(TYPE, Map.of());
    }
}
