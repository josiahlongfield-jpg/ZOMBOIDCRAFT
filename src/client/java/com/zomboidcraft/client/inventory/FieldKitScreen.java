package com.zomboidcraft.client.inventory;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.Wear;
import com.zomboidcraft.carry.Weights;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
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
    static final int BAG_Y = 48, POCKET_Y = 122, LOAD_Y = 178, TAB_W = 24;

    private final Map<Slot, int[]> original = new HashMap<>();
    private final long opened = System.currentTimeMillis();

    /** Decides whether the field kit takes over from the vanilla inventory it was about to open. */
    public static net.minecraft.client.gui.screens.Screen replace(InventoryScreen vanilla) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ZcConfig.get().look.fieldKitInventory) return vanilla;
        if (player.hasInfiniteMaterials()) return vanilla; // creative keeps its own tabbed screen
        return new FieldKitScreen(player);
    }

    public FieldKitScreen(Player player) {
        super(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"), W, H);
    }

    @Override
    protected void init() {
        fitGuiScale();
        super.init();
        CarrySlots.clientShows = this::shows;
        CarrySlots.lockedShows = slot -> tab == Tab.LOOSE;
        layoutSlots();
    }

    /**
     * The kit is wider than Minecraft's inventory, so at a large GUI scale it would run off the screen. While it is
     * open, drop to the biggest scale it fits at; {@link #removed()} puts the player's own scale back.
     */
    private void fitGuiScale() {
        var window = minecraft.getWindow();
        int scale = window.getGuiScale();
        while (scale > 1 && (window.getWidth() / scale < W + 8 || window.getHeight() / scale < H + 8)) scale--;
        if (scale == window.getGuiScale()) return;
        window.setGuiScale(scale);
        width = window.getGuiScaledWidth();
        height = window.getGuiScaledHeight();
    }

    private void restoreGuiScale() {
        var window = minecraft.getWindow();
        var options = minecraft.options;
        int wanted = window.calculateScale(options.guiScale().get(), options.forceUnicodeFont().get());
        if (window.getGuiScale() != wanted) window.setGuiScale(wanted);
    }

    private void place(Slot slot, int x, int y) {
        original.putIfAbsent(slot, new int[]{slot.x, slot.y});
        if (slot.x != x) ((SlotAccessor) slot).zomboidcraft$setX(x);
        if (slot.y != y) ((SlotAccessor) slot).zomboidcraft$setY(y);
    }

    private void place(int index, int x, int y) {
        place(menu.slots.get(index), x, y);
    }

    // ------------------------------------------------------------------ carried tabs

    /** What the middle panel shows: one worn bag, the pockets when there are too many for their row, or old loot. */
    enum Tab { BACK, VEST, WAIST, HAND, POCKETS, LOOSE }

    private Tab tab = Tab.BACK;

    private static Tab tabFor(Wear wear) {
        return Tab.values()[wear.ordinal()];
    }

    private BagContainer bagFor(Tab t) {
        if (t.ordinal() > Tab.HAND.ordinal() || !(menu instanceof Carry.Bags bags)) return null;
        BagContainer bag = bags.zomboidcraft$bags()[t.ordinal()];
        return bag.spec() != null ? bag : null;
    }

    private boolean manyPockets() {
        return Carry.pockets() > 9 || !ZcConfig.get().carry.pocketsOnly;
    }

    private boolean anyLoose() {
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
            Slot slot = menu.slots.get(i);
            if (Carry.locked(minecraft.player, slot.getContainerSlot()) && slot.hasItem()) return true;
        }
        return false;
    }

    private List<Tab> tabs() {
        List<Tab> list = new ArrayList<>();
        for (Tab t : List.of(Tab.BACK, Tab.VEST, Tab.WAIST, Tab.HAND)) {
            if (bagFor(t) != null) list.add(t);
        }
        if (manyPockets()) list.add(Tab.POCKETS);
        if (anyLoose()) list.add(Tab.LOOSE);
        return list;
    }

    private boolean shows(Slot slot) {
        if (slot instanceof CarrySlots.GearSlot) return true;
        if (slot instanceof CarrySlots.BagSlot bag) return tabFor(bag.bag.wear()) == tab;
        return false;
    }

    private void layoutSlots() {
        List<Tab> tabs = tabs();
        if (!tabs.contains(tab)) tab = tabs.isEmpty() ? Tab.BACK : tabs.get(0);
        // armour down the left of the character; gear and the off hand down the right
        for (int i = 0; i < 4; i++) place(InventoryMenu.ARMOR_SLOT_START + i, GEAR_X + 13, MIRROR_Y + 4 + i * 30);
        int gx = GEAR_X + GEAR_W - 29;
        place(InventoryMenu.SHIELD_SLOT, gx, MIRROR_Y + 4 + 4 * 28);
        // pockets in their own row, or on a tab of their own when there are many; locked ones on the loose tab
        int pockets = Carry.pockets();
        int pocketCols = Math.min(9, pockets);
        int pocketX = CARRY_X + (CARRY_W - pocketCols * 18) / 2;
        int loose = 0;
        int pocket = 0;
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
            Slot slot = menu.slots.get(i);
            if (Carry.locked(minecraft.player, slot.getContainerSlot())) {
                place(slot, CARRY_X + 12 + (loose % 9) * 18, BAG_Y + (loose / 9) * 18);
                loose++;
            } else if (manyPockets()) {
                place(slot, CARRY_X + 12 + (pocket % 9) * 18, BAG_Y + (pocket / 9) * 18);
                pocket++;
            } else {
                place(slot, pocketX + pocket * 18, POCKET_Y);
                pocket++;
            }
        }
        // hotbar on the belt
        for (int c = 0; c < 9; c++) place(InventoryMenu.USE_ROW_SLOT_START + c, CARRY_X + 13 + c * 18, BELT_Y + 22);
        // gear and bag slots
        for (Slot slot : menu.slots) {
            if (slot instanceof CarrySlots.GearSlot gear) {
                place(slot, gx, MIRROR_Y + 4 + gear.gear * 28);
            } else if (slot instanceof CarrySlots.BagSlot bag) {
                BagSpec spec = bag.bag.spec();
                int cols = spec == null ? 6 : spec.columns();
                int i = bag.getContainerSlot();
                place(slot, CARRY_X + (CARRY_W - cols * 18) / 2 + (i % cols) * 18, BAG_Y + (i / cols) * 18);
            }
        }
        // 2x2 crafting and its result on the right
        for (int i = 0; i < 4; i++) {
            place(InventoryMenu.CRAFT_SLOT_START + i, NEAR_X + 18 + (i % 2) * 18, 46 + (i / 2) * 18);
        }
        place(InventoryMenu.RESULT_SLOT, NEAR_X + 88, 55);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // bags put on or taken off while the kit is open change the tabs and grids
        layoutSlots();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int tx = leftPos + CARRY_X + 10;
        for (Tab t : tabs()) {
            if (event.x() >= tx && event.x() < tx + TAB_W && event.y() >= topPos + 4 && event.y() < topPos + 26) {
                if (tab != t) {
                    tab = t;
                    layoutSlots();
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARMOR_EQUIP_LEATHER, 1.2f, 0.5f));
                }
                return true;
            }
            tx += TAB_W + 2;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        // put the slots back so any other screen that shows this menu finds them where it expects
        original.forEach((slot, xy) -> {
            ((SlotAccessor) slot).zomboidcraft$setX(xy[0]);
            ((SlotAccessor) slot).zomboidcraft$setY(xy[1]);
        });
        CarrySlots.clientShows = slot -> false;
        CarrySlots.lockedShows = slot -> true;
        restoreGuiScale();
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0x55080A0C);
        rain(g);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.VIGNETTE, 0, 0, width, height);
        int x = leftPos, y = topPos;
        gearPanel(g, x, y, mouseX, mouseY);
        carryPanel(g, x, y);

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

    private void gearPanel(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        KitArt.panel(g, KitArt.Material.CANVAS, x + GEAR_X, y, GEAR_W, H);
        KitArt.header(g, font, "Survivor", x + GEAR_X + 12, y + 12);
        KitArt.tape(g, font, "Day " + (minecraft.level == null ? 1 : minecraft.level.getGameTime() / 24000 + 1),
                x + GEAR_X + GEAR_W - 9, y + 9, true, KitArt.MARKER_INK);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.MIRROR, x + MIRROR_X, y + MIRROR_Y, MIRROR_W, MIRROR_H);
        InventoryScreen.extractEntityInInventoryFollowsMouse(g, x + MIRROR_X + 2, y + MIRROR_Y + 4,
                x + MIRROR_X + MIRROR_W - 2, y + MIRROR_Y + MIRROR_H - 4, 46, 0.0625f, mouseX, mouseY, minecraft.player);
        String[] armour = {"Head", "Torso", "Legs", "Feet"};
        for (int i = 0; i < 4; i++) KitArt.slotLabel(g, font, armour[i], x + GEAR_X + 21, y + MIRROR_Y + 4 + i * 30 + 18);
        String[] gear = {"Back", "Vest", "Waist", "Sling", "Off hand"};
        for (int i = 0; i < gear.length; i++) {
            KitArt.slotLabel(g, font, gear[i], x + GEAR_X + GEAR_W - 21, y + MIRROR_Y + 4 + i * 28 + 18);
        }
        // load: what you carry against what you can
        if (ZcConfig.get().carry.weight) {
            double load = Carry.load(minecraft.player);
            double cap = Carry.capacity();
            float ratio = (float) (load / cap);
            int tier = Carry.tier(ratio);
            int ink = tier == 0 ? KitArt.MARKER_INK : KitArt.MARKER_RED;
            KitArt.tape(g, font, Weights.format(load) + " / " + Weights.format(cap), x + GEAR_X + 10, y + LOAD_Y, false, ink);
            int bx = x + GEAR_X + 12, bw = GEAR_W - 24, by = y + LOAD_Y + 18;
            g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xAA000000);
            int fill = (int) (bw * Math.min(1f, ratio / 2f));
            int colour = tier == 0 ? 0xFF7FAE4E : tier <= 2 ? 0xFFD9A43C : 0xFFC24A2C;
            g.fillGradient(bx, by, bx + fill, by + 5, colour, colour & 0xFFB0B0B0 | 0xFF000000);
            g.fill(bx + bw / 2, by - 2, bx + bw / 2 + 1, by + 7, 0xCCE6DCC0); // your limit
            if (tier > 0) {
                String[] words = {"", "Heavy load", "Very heavy", "Overloaded", "Crushing"};
                g.text(font, Component.literal(words[tier]), bx, by + 8, 0xFFE07A5A, false);
            }
        }
    }

    private void carryPanel(GuiGraphicsExtractor g, int x, int y) {
        KitArt.panel(g, KitArt.Material.CANVAS, x + CARRY_X, y, CARRY_W, H);
        // tabs: one per worn bag, like Minecraft's creative tabs
        List<Tab> tabs = tabs();
        int tx = x + CARRY_X + 10;
        for (Tab t : tabs) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, t == tab ? KitArt.TAB_SELECTED : KitArt.TAB, tx, y + 4, TAB_W, 22);
            BagContainer bag = bagFor(t);
            if (bag != null) {
                g.item(bag.bag(), tx + (TAB_W - 16) / 2, y + 7);
            } else {
                g.centeredText(font, KitArt.stencil(t == Tab.LOOSE ? "!" : "P"), tx + TAB_W / 2, y + 11, KitArt.STENCIL_INK);
            }
            tx += TAB_W + 2;
        }
        int hy = y + 32;
        BagContainer bag = bagFor(tab);
        if (bag != null) {
            BagSpec spec = bag.spec();
            KitArt.header(g, font, bag.bag().getHoverName().getString(), x + CARRY_X + 12, hy);
            double inside = bag.contentsWeight(-1);
            KitArt.tape(g, font, Weights.format(inside) + " / " + Weights.format(spec.capacity()),
                    x + CARRY_X + CARRY_W - 9, hy - 3, true, KitArt.MARKER_INK);
            int bx = x + CARRY_X + 12, bw = CARRY_W - 24;
            g.fill(bx, y + BAG_Y - 5, bx + bw, y + BAG_Y - 3, 0x88000000);
            float full = (float) Math.min(1, inside / spec.capacity());
            g.fill(bx, y + BAG_Y - 5, bx + (int) (bw * full), y + BAG_Y - 3, full < 0.8f ? 0xFF7FAE4E : full < 1f ? 0xFFD9A43C : 0xFFC24A2C);
        } else if (tab == Tab.POCKETS && tabs.contains(Tab.POCKETS)) {
            KitArt.header(g, font, "Pockets", x + CARRY_X + 12, hy);
        } else if (tab == Tab.LOOSE && tabs.contains(Tab.LOOSE)) {
            // things from before bags: they can come out, nothing new goes in
            KitArt.header(g, font, "Loose kit", x + CARRY_X + 12, hy);
            KitArt.tape(g, font, "take out only", x + CARRY_X + CARRY_W - 9, hy - 3, true, KitArt.MARKER_RED);
        } else {
            KitArt.header(g, font, "No bag", x + CARRY_X + 12, hy);
            g.text(font, KitArt.marker("Wear a bag on your back,"), x + CARRY_X + 16, y + 62, KitArt.INK_DIM, false);
            g.text(font, KitArt.marker("vest or waist, or carry one"), x + CARRY_X + 16, y + 74, KitArt.INK_DIM, false);
            g.text(font, KitArt.marker("in your off hand."), x + CARRY_X + 16, y + 86, KitArt.INK_DIM, false);
        }
        if (!manyPockets()) {
            double pocketW = 0;
            for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
                Slot slot = menu.slots.get(i);
                if (!Carry.locked(minecraft.player, slot.getContainerSlot())) pocketW += Weights.of(slot.getItem());
            }
            g.text(font, KitArt.stencil("POCKETS"), x + CARRY_X + 14, y + POCKET_Y - 11, 0xAAE6DCC0, false);
            g.text(font, Component.literal(Weights.format(pocketW)), x + CARRY_X + CARRY_W - 14 - font.width(Weights.format(pocketW)),
                    y + POCKET_Y - 11, 0x88E6DCC0, false);
        }
        KitArt.panel(g, KitArt.Material.WEBBING, x + CARRY_X + 6, y + BELT_Y, CARRY_W - 12, 50);
        g.text(font, KitArt.stencil("BELT & HANDS"), x + CARRY_X + 14, y + BELT_Y + 9, 0xAAE6DCC0, false);
        g.text(font, Component.literal("1-9"), x + CARRY_X + CARRY_W - 30, y + BELT_Y + 9, 0x88E6DCC0, false);
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
