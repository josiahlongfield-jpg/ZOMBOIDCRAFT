package com.zomboidcraft.client.inventory;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.mixin.client.SlotAccessor;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The survival inventory as a field kit: Minecraft's own slots, clicks and hotbar, laid out as gear, carried and
 * nearby panels over the blurred world. Only slot positions change, so the server sees the usual inventory menu.
 */
public class FieldKitScreen extends AbstractContainerScreen<InventoryMenu> {
    static final int W = 470;
    static final int H = 214;
    // panel rectangles, relative to leftPos/topPos
    static final int GEAR_X = 0, GEAR_W = 142;
    static final int CARRY_X = 150, CARRY_W = 186;
    static final int NEAR_X = 344, NEAR_W = 126;
    static final int MIRROR_X = 36, MIRROR_Y = 30, MIRROR_W = 70, MIRROR_H = 140;
    static final int BELT_Y = 150;

    private final Map<Slot, int[]> original = new HashMap<>();
    private final long opened = System.currentTimeMillis();

    /** Decides whether the field kit takes over from the vanilla inventory it was about to open. */
    public static net.minecraft.client.gui.screens.Screen replace(InventoryScreen vanilla) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ZcConfig.get().look.fieldKitInventory) return vanilla;
        int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
        if (gw < W + 8 || gh < H + 8) return vanilla; // too small at this GUI scale; keep Minecraft's
        return new FieldKitScreen(player);
    }

    public FieldKitScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"), W, H);
    }

    @Override
    protected void init() {
        super.init();
        layoutSlots();
    }

    private void place(int index, int x, int y) {
        Slot slot = menu.slots.get(index);
        original.putIfAbsent(slot, new int[]{slot.x, slot.y});
        ((SlotAccessor) slot).zomboidcraft$setX(x);
        ((SlotAccessor) slot).zomboidcraft$setY(y);
    }

    private void layoutSlots() {
        // armour down the left of the character, off hand on the right
        for (int i = 0; i < 4; i++) place(InventoryMenu.ARMOR_SLOT_START + i, GEAR_X + 13, MIRROR_Y + 4 + i * 30);
        place(InventoryMenu.SHIELD_SLOT, GEAR_X + GEAR_W - 29, MIRROR_Y + 4);
        // the 27 main slots are the pack until real bags arrive
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                place(InventoryMenu.INV_SLOT_START + r * 9 + c, CARRY_X + 13 + c * 18, 42 + r * 18);
            }
        }
        // hotbar on the belt
        for (int c = 0; c < 9; c++) place(InventoryMenu.USE_ROW_SLOT_START + c, CARRY_X + 13 + c * 18, BELT_Y + 22);
        // 2x2 crafting and its result on the right
        for (int i = 0; i < 4; i++) {
            place(InventoryMenu.CRAFT_SLOT_START + i, NEAR_X + 18 + (i % 2) * 18, 46 + (i / 2) * 18);
        }
        place(InventoryMenu.RESULT_SLOT, NEAR_X + 88, 55);
    }

    @Override
    public void removed() {
        // put the slots back so any other screen that shows this menu finds them where it expects
        original.forEach((slot, xy) -> {
            ((SlotAccessor) slot).zomboidcraft$setX(xy[0]);
            ((SlotAccessor) slot).zomboidcraft$setY(xy[1]);
        });
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0x55080A0C);
        rain(g);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.VIGNETTE, 0, 0, width, height);

        int x = leftPos, y = topPos;
        // gear
        KitArt.panel(g, KitArt.Material.CANVAS, x + GEAR_X, y, GEAR_W, H);
        KitArt.header(g, font, "Survivor", x + GEAR_X + 12, y + 12);
        KitArt.tape(g, font, "Day " + (minecraft.level == null ? 1 : minecraft.level.getGameTime() / 24000 + 1),
                x + GEAR_X + GEAR_W - 9, y + 9, true, KitArt.MARKER_INK);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.MIRROR, x + MIRROR_X, y + MIRROR_Y, MIRROR_W, MIRROR_H);
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + MIRROR_X + 2, y + MIRROR_Y + 4,
                x + MIRROR_X + MIRROR_W - 2, y + MIRROR_Y + MIRROR_H - 4, 46, 0.0625f, mouseX, mouseY, minecraft.player);
        String[] armour = {"Head", "Torso", "Legs", "Feet"};
        for (int i = 0; i < 4; i++) KitArt.slotLabel(g, font, armour[i], x + GEAR_X + 21, y + MIRROR_Y + 4 + i * 30 + 18);
        KitArt.slotLabel(g, font, "Off hand", x + GEAR_X + GEAR_W - 21, y + MIRROR_Y + 22);
        g.text(font, Component.literal("Bags, vest and sling slots").withColor(KitArt.INK_DIM), x + GEAR_X + 12, y + 180, KitArt.INK_DIM, false);
        g.text(font, Component.literal("arrive in the next build"), x + GEAR_X + 12, y + 190, KitArt.INK_DIM, false);

        // carried
        KitArt.panel(g, KitArt.Material.CANVAS, x + CARRY_X, y, CARRY_W, H);
        KitArt.header(g, font, "Pack", x + CARRY_X + 12, y + 12);
        KitArt.tape(g, font, filled(InventoryMenu.INV_SLOT_START, InventoryMenu.INV_SLOT_END) + " / 27",
                x + CARRY_X + CARRY_W - 9, y + 9, true, KitArt.MARKER_INK);
        KitArt.panel(g, KitArt.Material.WEBBING, x + CARRY_X + 6, y + BELT_Y, CARRY_W - 12, 50);
        g.text(font, KitArt.stencil("BELT & HANDS"), x + CARRY_X + 14, y + BELT_Y + 9, 0xAAE6DCC0, false);
        g.text(font, Component.literal("1-9"), x + CARRY_X + CARRY_W - 30, y + BELT_Y + 9, 0x88E6DCC0, false);

        // nearby: crafting for now; loot shelves and the floor land here next
        KitArt.panel(g, KitArt.Material.METAL, x + NEAR_X, y, NEAR_W, H);
        KitArt.header(g, font, "Field craft", x + NEAR_X + 12, y + 12);
        g.text(font, Component.literal("->"), x + NEAR_X + 64, y + 59, KitArt.INK_DIM, false);

        // slot sockets under every visible slot, and a brass ring on the one in your hand
        for (Slot slot : menu.slots) {
            if (slot.isActive()) KitArt.slot(g, x + slot.x, y + slot.y);
        }
        int held = minecraft.player.getInventory().getSelectedSlot();
        Slot hand = menu.slots.get(InventoryMenu.USE_ROW_SLOT_START + held);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.SLOT_SELECTED, x + hand.x - 3, y + hand.y - 3, 22, 22);

        // torch light follows the cursor
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.TORCH, mouseX - 110, mouseY - 110, 220, 220);
    }

    private int filled(int from, int to) {
        int n = 0;
        for (int i = from; i < to; i++) if (menu.slots.get(i).hasItem()) n++;
        return n;
    }

    /** Thin rain streaks drifting down the glass. */
    private void rain(GuiGraphicsExtractor g) {
        long t = System.currentTimeMillis() - opened;
        for (int i = 0; i < 70; i++) {
            int seed = i * 7919;
            int sx = Math.floorMod(seed * 31, Math.max(1, width));
            int speed = 120 + (seed % 90);
            int sy = (int) Math.floorMod((long) (seed * 13) + t * speed / 1000, height + 40L) - 20;
            int len = 6 + seed % 9;
            int drift = (int) ((t * speed / 1000) / 6) % 4;
            g.fill(sx - drift, sy, sx - drift + 1, sy + len, 0x1CC8D4E0);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // headers are drawn with the panels
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (hoveredSlot != null && hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            ItemStack stack = hoveredSlot.getItem();
            g.setTooltipForNextFrame(font, getTooltipFromContainerItem(stack), stack.getTooltipImage(), mouseX, mouseY,
                    KitArt.TOOLTIP_STYLE);
        }
    }
}
