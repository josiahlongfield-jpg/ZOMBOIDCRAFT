package com.zomboidcraft.client.clothing;

import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.InteractMenu.Option;
import com.zomboidcraft.clothing.ClothDirt;
import com.zomboidcraft.clothing.ClothWear;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.Garment;
import com.zomboidcraft.clothing.Tailoring;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Clothing on the G menu, as Zomboid's right-click has it: looking at water (a river, a pond, a filled cauldron),
 * "Wash" lists every dirty or bloody piece you wear or carry; looking at nothing, "Patch clothing" lists the torn
 * pieces and "Hair and beard" opens the haircut page. Each goes through the same timed actions as the kit's keys.
 */
public final class ClothingMenu {
    private ClothingMenu() {
    }

    public static void register() {
        InteractMenu.provider(ClothingMenu::options);
    }

    private static void options(Minecraft client, LocalPlayer player, HitResult hit, List<Option> out) {
        InventoryMenu menu = player.inventoryMenu;
        List<Option> wash = new ArrayList<>(), patch = new ArrayList<>();
        for (int i = InventoryMenu.ARMOR_SLOT_START; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s instanceof com.zomboidcraft.carry.CarrySlots.BagSlot bag && !bag.open()) continue;
            ItemStack stack = s.getItem();
            Garment g = Clothes.of(stack);
            if (g == null) continue;
            String name = stack.getHoverName().getString();
            int slot = i;
            ClothDirt d = ClothDirt.of(stack);
            if (!d.clean()) wash.add(Option.timed(name + " (" + d.describe().toLowerCase(java.util.Locale.ROOT) + ")", 3,
                    () -> press(client, Tailoring.WASH, slot)));
            if (Tailoring.patchable(g) && Tailoring.nextPatch(g, ClothWear.of(stack)) != null && ClothWear.of(stack).holeCount() > 0) {
                patch.add(Option.timed("Patch " + name, 5, () -> press(client, Tailoring.PATCH, slot)));
            }
        }
        if (!wash.isEmpty() && water(client, hit, player)) out.add(Option.menu("Wash", wash));
        if (hit == null || hit.getType() == HitResult.Type.MISS) {                  // looking at nothing: yourself
            if (!patch.isEmpty()) out.add(Option.menu("Patch clothing", patch));
            out.add(Option.of("Hair and beard", () -> client.gui.setScreen(new com.zomboidcraft.client.kit.GroomingScreen(null))));
        }
    }

    private static void press(Minecraft client, int action, int slot) {
        int id = com.zomboidcraft.combat.Bench.TAILOR + action * Tailoring.SLOTS + slot;
        InventoryMenu menu = client.player.inventoryMenu;
        if (menu.clickMenuButton(client.player, id)) client.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private static boolean water(Minecraft client, HitResult hit, LocalPlayer player) {
        if (player.isInWater()) return true;
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos p = b.getBlockPos();
            for (BlockPos q : new BlockPos[]{p, p.above()}) {
                BlockState s = client.level.getBlockState(q);
                if (s.getFluidState().is(FluidTags.WATER) || s.is(Blocks.WATER_CAULDRON)) return true;
            }
        }
        return false;
    }
}
