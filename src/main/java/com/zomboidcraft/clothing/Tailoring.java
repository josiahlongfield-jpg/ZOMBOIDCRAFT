package com.zomboidcraft.clothing;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.survival.TimedActions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Patching and ripping clothes, Zomboid style. A patch needs a needle (a sewing kit), thread and a strip to sew on:
 * ripped sheets or rags patch in fabric, denim strips in denim, leather strips in leather, and the best you carry is
 * used. It closes the first hole, or reinforces the first spot not patched yet. Ripping a piece up gives sheets, denim
 * strips or leather strips, more for a bigger piece and fewer for one full of holes. Both are timed and both teach
 * Tailoring, which makes stitches hold and the work quicker.
 *
 * The kit's Clothing page asks for them as menu buttons (combat/Bench.click hands them here).
 */
public final class Tailoring {
    public static final int PATCH = 0, RIP = 1;
    /** Menu button ids: TAILOR + action * SLOTS + menu slot. */
    public static final int SLOTS = 4096;

    private static final String KIT = "sewing_kit", THREAD = "thread_spool";
    /** Strips, best first, and the patch each one makes. */
    private static final String[] STRIPS = {"leather_strips", "denim_strips", "ripped_sheet", "rag"};

    public static boolean click(AbstractContainerMenu menu, Player player, int n, int carriedEnd) {
        int action = n / SLOTS, slot = n % SLOTS;
        if (slot < InventoryMenu.ARMOR_SLOT_START || slot >= menu.slots.size()) return false;
        ItemStack stack = menu.slots.get(slot).getItem();
        Garment g = Clothes.of(stack);
        if (g == null) return false;
        // the client hears how it went from the server, when the bar fills
        if (!(player instanceof ServerPlayer server)) return true;
        if (action == PATCH) startPatch(server, menu, slot, carriedEnd);
        else if (action == RIP) startRip(server, slot);
        return true;
    }

    /** What a patch would sew over next on this piece, holes first; null when every spot already has one. */
    public static int[] nextPatch(Garment g, ClothWear wear) {
        int[] reinforce = null;
        for (Spot s : g.covers()) {
            for (Region r : s.regions()) {
                if (wear.hole(s, r)) return new int[]{s.ordinal(), r.ordinal()};
                if (reinforce == null && wear.patch(s, r) == ClothWear.Patch.NONE) reinforce = new int[]{s.ordinal(), r.ordinal()};
            }
        }
        return reinforce;
    }

    public static boolean patchable(Garment g) {
        return g.cloth() != Garment.Cloth.HARD && g.cloth() != Garment.Cloth.NONE && !g.covers().isEmpty();
    }

    public static boolean rippable(Garment g) {
        return g.cloth() == Garment.Cloth.FABRIC || g.cloth() == Garment.Cloth.DENIM || g.cloth() == Garment.Cloth.LEATHER;
    }

    private static void startPatch(ServerPlayer player, AbstractContainerMenu menu, int slot, int carriedEnd) {
        ItemStack stack = menu.slots.get(slot).getItem();
        Garment g = Clothes.of(stack);
        if (g == null || !patchable(g)) {
            tell(player, "That can't take a patch.");
            return;
        }
        if (Tailoring.nextPatch(g, ClothWear.of(stack)) == null) {
            tell(player, "Every spot on it is patched already.");
            return;
        }
        if (find(menu, carriedEnd, KIT) < 0) {
            tell(player, "You need a sewing kit for the needle.");
            return;
        }
        if (find(menu, carriedEnd, THREAD) < 0) {
            tell(player, "You need thread.");
            return;
        }
        if (strip(menu, carriedEnd) < 0) {
            tell(player, "You need something to sew on: a rag, a ripped sheet, or denim or leather strips.");
            return;
        }
        int level = Skills.level(player, Skill.TAILORING);
        TimedActions.start(player, "patch:" + slot, "Patching", Math.round(100 * (1 - 0.05f * level)), p -> patch(p, slot));
    }

    private static void patch(ServerPlayer player, int slot) {
        AbstractContainerMenu menu = player.inventoryMenu;
        int end = menu.slots.size();
        ItemStack stack = menu.slots.get(slot).getItem();
        Garment g = Clothes.of(stack);
        int stripAt = strip(menu, end), threadAt = find(menu, end, THREAD);
        if (g == null || stripAt < 0 || threadAt < 0 || find(menu, end, KIT) < 0) return;
        ClothWear wear = ClothWear.of(stack);
        int[] at = nextPatch(g, wear);
        if (at == null) return;
        Spot spot = Spot.values()[at[0]];
        Region region = Region.values()[at[1]];
        ItemStack strip = menu.slots.get(stripAt).getItem();
        ClothWear.Patch kind = patchOf(strip);
        strip.shrink(1);
        // a spool sews about three patches
        if (player.getRandom().nextFloat() < 0.34f) menu.slots.get(threadAt).getItem().shrink(1);
        int level = Skills.level(player, Skill.TAILORING);
        if (player.getRandom().nextFloat() < 0.25f - 0.025f * level) {
            Skills.addXp(player, Skill.TAILORING, 2);
            tell(player, "The stitches pulled loose. The strip's wasted.");
            return;
        }
        stack.set(ClothWear.TYPE, wear.patched(spot, region, kind));
        Skills.addXp(player, Skill.TAILORING, 6);
        menu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                SoundSource.PLAYERS, 0.6f, 1.4f);
        boolean hole = wear.hole(spot, region);
        player.sendOverlayMessage(Component.literal((hole ? "Patched the hole on the " : "Reinforced the ")
                + side(spot, region) + " with " + kind.label + ".").withStyle(ChatFormatting.GRAY));
    }

    private static void startRip(ServerPlayer player, int slot) {
        ItemStack stack = player.inventoryMenu.slots.get(slot).getItem();
        Garment g = Clothes.of(stack);
        if (g == null || !rippable(g)) {
            tell(player, "That won't rip into anything useful.");
            return;
        }
        if (holding(stack)) {
            tell(player, "Empty it first.");
            return;
        }
        int ticks = g.cloth() == Garment.Cloth.FABRIC ? 60 : 100;
        TimedActions.start(player, "rip:" + slot, "Ripping", ticks, p -> rip(p, slot));
    }

    private static void rip(ServerPlayer player, int slot) {
        Slot from = player.inventoryMenu.slots.get(slot);
        ItemStack stack = from.getItem();
        Garment g = Clothes.of(stack);
        if (g == null || !rippable(g) || holding(stack)) return;
        int places = 0;
        for (Spot s : g.covers()) places += s.regions().length;
        ClothWear wear = ClothWear.of(stack);
        int count = Math.max(1, Math.round((places / 3f + 1) * (1 - wear.torn(g) * 0.6f)));
        String out = switch (g.cloth()) {
            case DENIM -> "denim_strips";
            case LEATHER -> "leather_strips";
            default -> "ripped_sheet";
        };
        Item item = item(out);
        from.set(ItemStack.EMPTY);
        ItemStack strips = new ItemStack(item, count);
        if (!player.getInventory().add(strips)) {
            player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), player.getX(),
                    player.getEyeY() - 0.3, player.getZ(), strips));
        }
        Skills.addXp(player, Skill.TAILORING, 1);
        player.inventoryMenu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_BREAK, SoundSource.PLAYERS, 0.8f, 1f);
    }

    /** A rig with gear still in its pouches: ripping it would lose what's in it. */
    private static boolean holding(ItemStack stack) {
        var contents = stack.get(net.minecraft.core.component.DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItemCopyStream().findAny().isPresent();
    }

    private static ClothWear.Patch patchOf(ItemStack strip) {
        String id = BuiltInRegistries.ITEM.getKey(strip.getItem()).getPath();
        return switch (id) {
            case "leather_strips" -> ClothWear.Patch.LEATHER;
            case "denim_strips" -> ClothWear.Patch.DENIM;
            default -> ClothWear.Patch.FABRIC;
        };
    }

    /** The best strip carried, or -1. */
    private static int strip(AbstractContainerMenu menu, int end) {
        for (String id : STRIPS) {
            int at = find(menu, end, id);
            if (at >= 0) return at;
        }
        return -1;
    }

    /** The first carried slot (pockets and open bags) holding this item, or -1. */
    private static int find(AbstractContainerMenu menu, int end, String id) {
        Item item = item(id);
        if (item == Items.AIR) return -1;
        for (int i = InventoryMenu.INV_SLOT_START; i < Math.min(end, menu.slots.size()); i++) {
            Slot s = menu.slots.get(i);
            if (s instanceof com.zomboidcraft.carry.CarrySlots.GearSlot) continue;
            if (s instanceof com.zomboidcraft.carry.CarrySlots.BagSlot bag && !bag.open()) continue;
            if (s.getItem().is(item)) return i;
        }
        return -1;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(id));
    }

    private static String side(Spot spot, Region region) {
        String label = switch (spot) {
            case UPPER_ARM -> "upper arm";
            case ELBOW -> "elbow";
            case FOREARM -> "forearm";
            case HAND -> "hand";
            case THIGH -> "thigh";
            case KNEE -> "knee";
            case SHIN -> "shin";
            case FOOT -> "foot";
            default -> spot.label;
        };
        return switch (region) {
            case LEFT_ARM, LEFT_LEG -> "left " + label;
            case RIGHT_ARM, RIGHT_LEG -> "right " + label;
            default -> label;
        };
    }

    private static void tell(ServerPlayer player, String s) {
        player.sendOverlayMessage(Component.literal(s).withStyle(ChatFormatting.GRAY));
    }

    private Tailoring() {
    }
}
