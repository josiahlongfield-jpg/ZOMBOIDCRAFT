package com.zomboidcraft.carry;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Stamina;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Carrying, Zomboid style: a few pockets, your hands and belt, and whatever bags you wear. Everything weighs
 * something; carry past your limit and you slow down and tire faster.
 */
public final class Carry {
    public static final AttachmentType<GearStore> GEAR = AttachmentRegistry.create(
            ZomboidCraft.id("gear"),
            builder -> builder.persistent(GearStore.CODEC).initializer(GearStore::new).copyOnDeath());

    /** Load as a share of what you can carry (1 = right at the limit), synced to your own client for the moodle. */
    public static final AttachmentType<Float> LOAD = AttachmentRegistry.create(
            ZomboidCraft.id("load"),
            builder -> builder.initializer(() -> 0f).syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

    private static final Identifier HEAVY = ZomboidCraft.id("heavy_load");
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Carry::tick);
    }

    public static GearStore gear(Player player) {
        return player.getAttachedOrCreate(GEAR);
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
            if (spec == null) {
                continue;
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
            if (ticks % 10 != 0) {
                continue;
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
            }
        }
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
