package com.zomboidcraft.client.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.Grabs;
import com.zomboidcraft.combat.RifleItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * The rounds left beside the hotbar while you hold the rifle, the struggle prompt when grabbed, and the
 * view through a 4x scope (the gun itself isn't drawn then: you're looking down the tube).
 */
public final class CombatHud {
    private static final Identifier SCOPE = ZomboidCraft.id("textures/gui/scope_4x.png");
    private static final int BLACK = 0xFF050505;

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || player.isSpectator()) {
            return;
        }
        Font font = minecraft.font;
        ItemStack hand = player.getMainHandItem();
        if (hand.is(ModItems.ASSAULT_RIFLE)) {
            ammo(graphics, font, hand);
        }
        Integer grabbed = player.getAttached(Grabs.GRABBED);
        if (grabbed != null && grabbed > 0) {
            grabbed(graphics, font, grabbed, player.tickCount);
        }
    }

    /** Drawn under the rest of the HUD, like the spyglass. */
    public static void scope(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !minecraft.options.getCameraType().isFirstPerson() || !player.isUsingItem()
                || !player.getUseItem().is(ModItems.ASSAULT_RIFLE) || !Attachments.scoped(player.getUseItem())
                || CombatClient.reloading(player.getUseItem())) {
            return;
        }
        int w = graphics.guiWidth(), h = graphics.guiHeight();
        int x = (w - h) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SCOPE, x, 0, 0, 0, h, h, h, h);
        graphics.fill(0, 0, x, h, BLACK);
        graphics.fill(x + h, 0, w, h, BLACK);
    }

    private static void ammo(GuiGraphicsExtractor graphics, Font font, ItemStack gun) {
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        String rounds = g.mag() ? g.rounds() + " / " + RifleItem.capacity() : "no mag";
        String status = g.jammed() ? "JAMMED - R" : CombatClient.reloading(gun) ? "reloading" : g.mag() && g.rounds() == 0 ? "empty - R" : "";
        int x = graphics.guiWidth() / 2 + 98;
        int y = graphics.guiHeight() - 20;
        int width = Math.max(font.width(rounds), font.width(status)) + 8;
        graphics.fill(x, y - (status.isEmpty() ? 0 : 10), x + width, y + 12, 0xA0101010);
        int colour = !g.mag() || g.rounds() == 0 ? 0xFFD05030 : g.rounds() <= RifleItem.capacity() / 5 ? 0xFFE0B040 : 0xFFE8E8E8;
        graphics.text(font, rounds, x + 4, y + 2, colour);
        if (!status.isEmpty()) {
            graphics.text(font, status, x + 4, y - 8, g.jammed() ? 0xFFE03020 : 0xFFA0A0A0);
        }
    }

    private static void grabbed(GuiGraphicsExtractor graphics, Font font, int left, int ticks) {
        int cx = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + 18;
        String line = "GRABBED!  Mash jump or shove (V)";
        int width = font.width(line) + 12;
        graphics.fill(cx - width / 2, y, cx + width / 2, y + 24, 0xB0200808);
        graphics.centeredText(font, line, cx, y + 3, (ticks / 4) % 2 == 0 ? 0xFFFF4030 : 0xFFE0C0C0);
        int pip = 6, gap = 3, total = left * (pip + gap) - gap;
        for (int i = 0; i < left; i++) {
            int px = cx - total / 2 + i * (pip + gap);
            graphics.fill(px, y + 15, px + pip, y + 20, 0xFFD03020);
        }
    }

    private CombatHud() {
    }
}
