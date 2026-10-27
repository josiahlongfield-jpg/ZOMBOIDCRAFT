package com.zomboidcraft.carry;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.CaseItem;
import com.zomboidcraft.combat.Stamina;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/**
 * Carrying, Zomboid style: a few pockets, your hands and belt, and whatever bags you wear. Everything weighs
 * something; carry past your limit and you slow down and tire faster.
 */
public final class Carry {
    /** The buckle clip when a bag goes on (sounds.json ui.wear). */
    private static final SoundEvent WEAR_SOUND = SoundEvent.createVariableRangeEvent(ZomboidCraft.id("ui.wear"));

    public static final AttachmentType<GearStore> GEAR = AttachmentRegistry.create(
            ZomboidCraft.id("gear"),
            builder -> builder.persistent(GearStore.CODEC).initializer(GearStore::new).copyOnDeath());

    /** Load as a share of what you can carry (1 = right at the limit), synced to your own client for the moodle. */
    public static final AttachmentType<Float> LOAD = AttachmentRegistry.create(
            ZomboidCraft.id("load"),
            builder -> builder.initializer(() -> 0f).syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

    /**
     * What everyone can see you wearing (back, vest, waist, sling), synced to every client for drawing on the body.
     * Plain copies of the bags, so what's inside a bag stays with its owner; the slung weapon as it is.
     */
    public static final AttachmentType<List<ItemStack>> WORN = AttachmentRegistry.create(
            ZomboidCraft.id("worn"),
            builder -> builder.initializer(List::of).syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.all()));

    private static final Identifier HEAVY = ZomboidCraft.id("heavy_load");
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Carry::tick);
        // load the patched classes now, so a mixin that no longer fits fails the server smoke test, not a player
        ZomboidCraft.LOGGER.debug("Carry hooks into {}, {} and {}", net.minecraft.world.inventory.InventoryMenu.class,
                Inventory.class, Slot.class);
    }

    public static GearStore gear(Player player) {
        return player.getAttachedOrCreate(GEAR);
    }

    /**
     * Puts a picked-up bag straight on when its place is free: back, vest or waist, or the off hand for a duffel.
     * Empties the stack and returns true when it went on.
     */
    public static boolean wear(Player player, ItemStack stack) {
        BagSpec spec = BagItem.spec(stack);
        if (spec == null || player.level().isClientSide() || !ZcConfig.get().carry.autoWear || player.hasInfiniteMaterials()) {
            return false;
        }
        Inventory inventory = player.getInventory();
        int gearSlot = GearStore.slotFor(spec.wear());
        if (gearSlot >= 0 && gear(player).get(gearSlot).isEmpty()) {
            gear(player).items.set(gearSlot, stack.copyAndClear());
        } else if (gearSlot < 0 && inventory.getItem(Inventory.SLOT_OFFHAND).isEmpty()) {
            inventory.setItem(Inventory.SLOT_OFFHAND, stack.copyAndClear());
        } else {
            return false;
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), WEAR_SOUND,
                SoundSource.PLAYERS, 1.0f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        return true;
    }

    // ---------------------------------------------------------------- pockets

    public static int pockets() {
        return Math.max(0, Math.min(27, ZcConfig.get().carry.pocketSlots));
    }

    /** Main-inventory slots past the pockets take nothing new. Hotbar, armour and off hand are never locked. */
    public static boolean locked(Player player, int inventorySlot) {
        ZcConfig.Carry config = ZcConfig.get().carry;
        return config.pocketsOnly && !player.hasInfiniteMaterials()
                && inventorySlot >= Inventory.getSelectionSize() + pockets() && inventorySlot < Inventory.INVENTORY_SIZE;
    }

    // ---------------------------------------------------------------- weapons

    /** Guns and melee weapons: carried in hand, on the belt, slung or in a bag, never in a pocket. */
    public static boolean weapon(ItemStack stack) {
        return stack.getItem() instanceof com.zomboidcraft.combat.RifleItem || stack.getItem() instanceof com.zomboidcraft.combat.MeleeWeaponItem;
    }

    /** What the sling takes across your back: every gun bar a handgun, and melee weapons. */
    public static boolean slings(ItemStack stack) {
        com.zomboidcraft.combat.GunType type = com.zomboidcraft.combat.GunType.of(stack);
        return weapon(stack) && type != com.zomboidcraft.combat.GunType.PISTOL && type != com.zomboidcraft.combat.GunType.REVOLVER
                && type != com.zomboidcraft.combat.GunType.MAGNUM;
    }

    /** Whether a slot of the player's inventory is a pocket (the main rows, locked or not), where weapons never go. */
    public static boolean pocket(Player player, int inventorySlot) {
        return !player.hasInfiniteMaterials() && inventorySlot >= Inventory.getSelectionSize() && inventorySlot < Inventory.INVENTORY_SIZE;
    }

    /**
     * Takes a weapon you pick up, keeping it out of your pockets: a free belt slot, then the sling if it goes there,
     * then your bags. Returns true if any of it went somewhere (the stack is emptied).
     */
    public static boolean takeWeapon(Player player, ItemStack stack) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack.copyAndClear());
                return true;
            }
        }
        if (slings(stack) && gear(player).get(GearStore.SLING).isEmpty()) {
            gear(player).items.set(GearStore.SLING, stack.copyAndClear());
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), WEAR_SOUND,
                    SoundSource.PLAYERS, 0.8f, 1.05f + player.getRandom().nextFloat() * 0.1f);
            return true;
        }
        return stow(player, stack);
    }

    // ---------------------------------------------------------------- bags

    /** The bag containers of a player's menu, back first, as the menu mixin built them. */
    public interface Bags {
        BagContainer[] zomboidcraft$bags();
    }

    /** Puts as much of the stack as fits into worn bags, back first. Returns true if any went in. */
    public static boolean stow(Player player, ItemStack stack) {
        if (!(player.inventoryMenu instanceof Bags menu) || stack.isEmpty() || !stack.getItem().canFitInsideContainerItems()) {
            return false;
        }
        int before = stack.getCount();
        for (BagContainer bag : menu.zomboidcraft$bags()) {
            BagSpec spec = bag.spec();
            if (spec == null || !spec.holds().test(stack)) {
                continue;       // nothing worn here, or a case or can that won't take it
            }
            // top up matching stacks, then fill empty slots
            for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
                for (int i = 0; i < bag.usableSlots() && !stack.isEmpty(); i++) {
                    ItemStack there = bag.getItem(i);
                    boolean merge = pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack);
                    boolean fill = pass == 1 && there.isEmpty();
                    if (!merge && !fill) {
                        continue;
                    }
                    int room = room(bag, spec, i, stack) - there.getCount();
                    if (room <= 0) {
                        continue;
                    }
                    int moved = Math.min(room, stack.getCount());
                    if (fill) {
                        bag.setItem(i, stack.split(moved));
                    } else {
                        there.grow(moved);
                        stack.shrink(moved);
                        bag.setChanged();
                    }
                }
            }
        }
        return stack.getCount() < before;
    }

    /** How many of this stack one bag slot could hold in all, by stack size and by the bag's weight limit. */
    static int room(BagContainer bag, BagSpec spec, int slot, ItemStack stack) {
        double unit = Weights.unit(stack);
        int max = stack.getMaxStackSize();
        if (unit <= 0) {
            return max;
        }
        double room = spec.capacity() - bag.contentsWeight(slot);
        return Math.max(0, Math.min(max, (int) Math.floor(room / unit + 1e-6)));
    }

    // ---------------------------------------------------------------- weight

    public static double capacity() {
        return Math.max(1, ZcConfig.get().carry.capacity);
    }

    /** Kilograms on you: hands, belt, pockets, off hand and worn gear (bags count their contents less the bag's help). */
    public static double load(Player player) {
        Inventory inventory = player.getInventory();
        double w = 0;
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            w += Weights.of(inventory.getItem(i));
        }
        w += Weights.of(inventory.getItem(Inventory.SLOT_OFFHAND));
        GearStore gear = gear(player);
        for (int i = 0; i < GearStore.SIZE; i++) {
            w += Weights.of(gear.get(i));
        }
        return w;
    }

    /** 0 fine, 1 heavy, 2 very heavy, 3 overloaded, 4 crushing. */
    public static int tier(float ratio) {
        return ratio > 1.75f ? 4 : ratio > 1.5f ? 3 : ratio > 1.25f ? 2 : ratio > 1.0f ? 1 : 0;
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.inventoryMenu instanceof Bags menu) {
                for (BagContainer bag : menu.zomboidcraft$bags()) {
                    bag.flush();
                }
            }
            showWorn(player);
            if (ticks % 10 != 0) {
                continue;
            }
            // a gun case or ammo can found shut is filled the first time someone has it; a found gun's finish is
            // rolled, and a weapon that didn't come out of a loot table gets its tier
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (CaseItem.unpack(inventory.getItem(i), player.getRandom())
                        | com.zomboidcraft.combat.rolls.Rolls.roll(inventory.getItem(i),
                                com.zomboidcraft.combat.rolls.Rolls.UNKNOWN, player.getRandom())
                        | com.zomboidcraft.combat.GunFinish.roll(inventory.getItem(i), player.getRandom())) {
                    inventory.setChanged();
                }
            }
            boolean on = ZcConfig.get().carry.weight && !player.isCreative() && !player.isSpectator();
            float ratio = on ? Math.round(load(player) / capacity() * 100f) / 100f : 0f;
            if (player.getAttachedOrElse(LOAD, 0f) != ratio) {
                player.setAttached(LOAD, ratio);
            }
            int tier = tier(ratio);
            slow(player, switch (tier) {
                case 1 -> -0.08;
                case 2 -> -0.18;
                case 3 -> -0.30;
                case 4 -> -0.45;
                default -> 0;
            });
            // a heavy load wears you out on the move
            if (tier >= 2 && player.getDeltaMovement().horizontalDistanceSqr() > 0.001) {
                Stamina.use(player, 0.15 * (tier - 1));
                // and a crushing one hurts: half a heart every six seconds you keep moving under it
                if (tier >= 4 && ticks % 120 == 0) {
                    player.hurtServer(player.level(), com.zomboidcraft.survival.ModDamageTypes.source(player.level(),
                            com.zomboidcraft.survival.ModDamageTypes.STRAIN), 1f);
                }
            }
        }
    }

    /** Updates what others see you wearing when it changes. */
    private static void showWorn(ServerPlayer player) {
        GearStore gear = gear(player);
        List<ItemStack> shown = player.getAttachedOrElse(WORN, List.of());
        boolean same = shown.size() == GearStore.SIZE;
        for (int i = 0; same && i < GearStore.SIZE; i++) {
            same = i == GearStore.SLING ? ItemStack.isSameItemSameComponents(shown.get(i), gear.get(i))
                    : shown.get(i).getItem() == gear.get(i).getItem() && shown.get(i).isEmpty() == gear.get(i).isEmpty();
        }
        if (same) {
            return;
        }
        List<ItemStack> now = new ArrayList<>();
        for (int i = 0; i < GearStore.SIZE; i++) {
            ItemStack stack = gear.get(i);
            // the slung weapon keeps its parts and finish, so it looks the same on your back
            now.add(stack.isEmpty() ? ItemStack.EMPTY : i == GearStore.SLING ? stack.copyWithCount(1) : new ItemStack(stack.getItem()));
        }
        player.setAttached(WORN, List.copyOf(now));
    }

    private static void slow(ServerPlayer player, double amount) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        AttributeModifier current = speed.getModifier(HEAVY);
        if (amount == 0) {
            if (current != null) {
                speed.removeModifier(HEAVY);
            }
        } else if (current == null || current.amount() != amount) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(HEAVY, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** True for the slots the field kit adds. */
    public static boolean ours(Slot slot) {
        return slot instanceof CarrySlots.GearSlot || slot instanceof CarrySlots.BagSlot;
    }

    private Carry() {
    }
}
