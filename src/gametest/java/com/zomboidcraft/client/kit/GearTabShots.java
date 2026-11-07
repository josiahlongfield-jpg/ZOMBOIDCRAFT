package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.carry.GearContainer;
import com.zomboidcraft.carry.GearStore;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshots the Tarkov gear tab (Josiah 2026-11-07): a rucksack and a chest rig worn and filled, so the cell grids,
 * item footprints and pockets show. Run by .github/workflows/invshots.yml, which pushes them to shots/inventory.
 */
public class GearTabShots implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("ZCINV");

    @Override
    public void runTest(ClientGameTestContext ctx) {
        try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();
            sp.getServer().runCommand("gamemode survival @a");
            sp.getServer().runOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                GearContainer gear = new GearContainer(p);
                gear.setItem(GearStore.BACK, new ItemStack(ModItems.MILITARY_RUCKSACK));
                gear.setItem(GearStore.VEST, new ItemStack(ModItems.CHEST_RIG));
            });
            ctx.waitTicks(5);
            for (String c : new String[]{"give @a zomboidcraft:assault_rifle", "give @a zomboidcraft:pistol", "give @a zomboidcraft:smg",
                    "give @a zomboidcraft:bandage 3", "give @a minecraft:bread 5", "give @a zomboidcraft:hammer",
                    "give @a zomboidcraft:nails 8", "give @a zomboidcraft:fire_axe", "give @a zomboidcraft:shovel",
                    "give @a zomboidcraft:propane_torch", "give @a zomboidcraft:scrap_metal 4"}) {
                sp.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            ctx.getInput().resizeWindow(1600, 900);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(0);
                mc.resizeGui();
            });
            ctx.waitTicks(5);
            ctx.getInput().pressKey(InputConstants.KEY_E);
            ctx.waitTicks(15);
            String page = ctx.computeOnClient(mc -> mc.gui.screen() instanceof KitScreen<?> k ? k.page.name() : String.valueOf(mc.gui.screen()));
            LOG.info("ZCINV opened on {}", page);
            ctx.takeScreenshot("gear-tab");
            // the pack page draws the same grids bigger
            ctx.runOnClient(mc -> {
                if (mc.gui.screen() instanceof KitScreen<?> k) k.open(KitScreen.Page.PACK);
            });
            ctx.waitTicks(5);
            ctx.takeScreenshot("pack-page");
            String where = sp.getServer().computeOnServer(s -> {
                ServerPlayer p = s.getPlayerList().getPlayers().get(0);
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                    ItemStack st = p.getInventory().getItem(i);
                    if (!st.isEmpty()) out.append(i).append('=').append(st).append(' ');
                }
                return out.toString();
            });
            LOG.info("ZCINV inventory: {}", where);
        }
    }
}
