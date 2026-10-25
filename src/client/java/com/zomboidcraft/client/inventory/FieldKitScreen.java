package com.zomboidcraft.client.inventory;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.BagContainer;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.carry.Wear;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.loot.Nearby;
import com.zomboidcraft.loot.NearbyMenu;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;
import com.zomboidcraft.client.sound.UiSounds;
import net.minecraft.world.inventory.ContainerInput;
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
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The survival inventory as a field kit: Minecraft's own slots, clicks and hotbar, laid out as gear, carried and
 * nearby panels over the blurred world. It shows either the nearby menu (your things plus the floor and containers
 * in reach, slot for slot like the inventory menu after that) or, on a server without the mod, the plain inventory.
 */
public class FieldKitScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    static final int W = 530;
    static final int H = 214;
    // panel rectangles, relative to leftPos/topPos
    static final int GEAR_X = 0, GEAR_W = 142;
    static final int CARRY_X = 150, CARRY_W = 186;
    static final int NEAR_X = 344, NEAR_W = 186;
    static final int CRAFT_Y = 140;
    static final int MIRROR_X = 36, MIRROR_Y = 30, MIRROR_W = 70, MIRROR_H = 140;
    static final int BELT_Y = 150;
    static final int BAG_Y = 48, POCKET_Y = 122, LOAD_Y = 178, TAB_W = 24;

    private static final int AWAY = -2000;

    private final Map<Slot, int[]> original = new HashMap<>();
    private final long opened = System.currentTimeMillis();
    /** The weapon bench takes the left of the kit; the carried panel moves right so parts can come from your bags. */
    private boolean bench;
    private int benchGun = -1;
    private final WeaponBench benchView = new WeaponBench();

    public static void register() {
        MenuScreens.<NearbyMenu, FieldKitScreen<NearbyMenu>>register(Nearby.MENU, FieldKitScreen::new);
    }

    /**
     * Decides whether the field kit takes over from the vanilla inventory it was about to open. With the mod on the
     * server it asks for the nearby menu and opens nothing itself (the server's menu brings the screen); otherwise
     * it lays out the plain inventory.
     */
    public static net.minecraft.client.gui.screens.Screen replace(InventoryScreen vanilla) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ZcConfig.get().look.fieldKitInventory) return vanilla;
        if (player.hasInfiniteMaterials()) return vanilla; // creative keeps its own tabbed screen
        if (ClientPlayNetworking.canSend(Nearby.OpenKit.TYPE)) {
            ClientPlayNetworking.send(new Nearby.OpenKit());
            return null;
        }
        return new FieldKitScreen<>(player.inventoryMenu, player.getInventory(), Component.translatable("container.crafting"));
    }

    public FieldKitScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
    }

    private NearbyMenu near() {
        return menu instanceof NearbyMenu near ? near : null;
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

    /** Where the carried panel sits: the middle, or the right while the bench is up. */
    private int carryX() {
        return bench ? NEAR_X : CARRY_X;
    }

    /** The gun the bench would show, or -1 when there is none or no bench (a server without the mod). */
    private int gunSlot() {
        NearbyMenu near = near();
        return near == null ? -1 : WeaponBench.findGun(near, minecraft);
    }

    private boolean shows(Slot slot) {
        if (slot instanceof CarrySlots.GearSlot) return true;
        if (slot instanceof CarrySlots.BagSlot bag) return tabFor(bag.bag.wear()) == tab;
        return false;
    }

    private void layoutSlots() {
        List<Tab> tabs = tabs();
        if (!tabs.contains(tab)) tab = tabs.isEmpty() ? Tab.BACK : tabs.get(0);
        benchGun = gunSlot();
        if (benchGun < 0) bench = false;
        int carryX = carryX();
        // with the bench up, everything but your carried things steps out of the way
        int away = bench ? AWAY : 0;
        // armour down the left of the character; gear and the off hand down the right
        for (int i = 0; i < 4; i++) place(InventoryMenu.ARMOR_SLOT_START + i, away + GEAR_X + 13, MIRROR_Y + 4 + i * 30);
        int gx = away + GEAR_X + GEAR_W - 29;
        place(InventoryMenu.SHIELD_SLOT, gx, MIRROR_Y + 4 + 4 * 28);
        // pockets in their own row, or on a tab of their own when there are many; locked ones on the loose tab
        int pockets = Carry.pockets();
        int pocketCols = Math.min(9, pockets);
        int pocketX = carryX + (CARRY_W - pocketCols * 18) / 2;
        int loose = 0;
        int pocket = 0;
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) {
            Slot slot = menu.slots.get(i);
            if (Carry.locked(minecraft.player, slot.getContainerSlot())) {
                place(slot, carryX + 12 + (loose % 9) * 18, BAG_Y + (loose / 9) * 18);
                loose++;
            } else if (manyPockets()) {
                place(slot, carryX + 12 + (pocket % 9) * 18, BAG_Y + (pocket / 9) * 18);
                pocket++;
            } else {
                place(slot, pocketX + pocket * 18, POCKET_Y);
                pocket++;
            }
        }
        // hotbar on the belt
        for (int c = 0; c < 9; c++) place(InventoryMenu.USE_ROW_SLOT_START + c, carryX + 13 + c * 18, BELT_Y + 22);
        // gear and bag slots
        for (Slot slot : menu.slots) {
            if (slot instanceof CarrySlots.GearSlot gear) {
                place(slot, gx, MIRROR_Y + 4 + gear.gear * 28);
            } else if (slot instanceof CarrySlots.BagSlot bag) {
                BagSpec spec = bag.bag.spec();
                int cols = spec == null ? 6 : spec.columns();
                int i = bag.getContainerSlot();
                place(slot, carryX + (CARRY_W - cols * 18) / 2 + (i % cols) * 18, BAG_Y + (i / cols) * 18);
            }
        }
        // 2x2 crafting and its result on the right: under the loot, or on its own without it
        NearbyMenu near = near();
        int craftX = away + NEAR_X + (near != null ? 40 : 48);
        int craftY = near != null ? CRAFT_Y : 46;
        for (int i = 0; i < 4; i++) {
            place(InventoryMenu.CRAFT_SLOT_START + i, craftX + (i % 2) * 18, craftY + (i / 2) * 18);
        }
        place(InventoryMenu.RESULT_SLOT, craftX + 70, craftY + 9);
        // the floor and containers in reach, nine across like a chest
        if (near != null) {
            for (Slot slot : menu.slots) {
                if (slot instanceof NearbyMenu.NearbySlot loot) {
                    int i = loot.getContainerSlot();
                    place(slot, away + NEAR_X + 12 + (i % 9) * 18, BAG_Y + (i / 9) * 18);
                }
            }
        }
    }

    /** What the gear slots held last tick, so putting a bag on or off buckles however it happened. */
    private List<ItemStack> wornLast;

    @Override
    protected void containerTick() {
        super.containerTick();
        // bags put on or taken off while the kit is open change the tabs and grids
        layoutSlots();
        List<ItemStack> worn = new ArrayList<>();
        for (Slot slot : menu.slots) {
            if (slot instanceof CarrySlots.GearSlot) worn.add(slot.getItem());
        }
        if (wornLast != null && !same(worn, wornLast)) UiSounds.play(UiSounds.WEAR);
        wornLast = worn.stream().map(ItemStack::copy).toList();
    }

    private static boolean same(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        // gear slots buckle instead (see containerTick)
        if (slot != null && !(slot instanceof CarrySlots.GearSlot) && (slot.hasItem() || !menu.getCarried().isEmpty())) {
            UiSounds.play(UiSounds.ITEM_MOVE);
        }
        super.slotClicked(slot, slotId, button, input);
    }

    /** Where the "take all" tape was last drawn, for clicks. */
    private int takeX, takeY, takeW;

    private void tabSound() {
        UiSounds.play(UiSounds.TAB);
    }

    /** The nearby tab under the mouse, or -1. */
    private int nearTabAt(double mx, double my) {
        NearbyMenu near = near();
        if (near == null || bench || my < topPos + 4 || my >= topPos + 26) return -1;
        int tx = leftPos + NEAR_X + 10;
        for (int k = 0; k < near.sources().size(); k++) {
            if (mx >= tx && mx < tx + TAB_W) return k;
            tx += TAB_W + 2;
        }
        return -1;
    }

    private void toggleBench() {
        bench = !bench && benchGun >= 0;
        layoutSlots();
        tabSound();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        NearbyMenu near = near();
        if (bench && near != null && benchView.clicked(event.x(), event.y(), leftPos, topPos, minecraft, near, benchGun,
                this::toggleBench)) {
            return true;
        }
        int k = bench ? -1 : nearTabAt(event.x(), event.y());
        if (near != null && k >= 0) {
            if (near.selected != k) {
                near.selected = k;
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, k);
                layoutSlots();
                tabSound();
            }
            return true;
        }
        if (!bench && near != null && takeW > 0 && event.x() >= takeX && event.x() < takeX + takeW && event.y() >= takeY
                && event.y() < takeY + 13) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, NearbyMenu.TAKE_ALL + near.selected);
            UiSounds.play(UiSounds.CLICK);
            return true;
        }
        int tx = leftPos + carryX() + 10;
        boolean onTabs = event.y() >= topPos + 4 && event.y() < topPos + 26;
        for (Tab t : tabs()) {
            if (onTabs && event.x() >= tx && event.x() < tx + TAB_W) {
                if (tab != t) {
                    tab = t;
                    layoutSlots();
                    tabSound();
                }
                return true;
            }
            tx += TAB_W + 2;
        }
        if (benchGun >= 0 && onTabs && event.x() >= benchTabX() && event.x() < benchTabX() + TAB_W) {
            toggleBench();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The bench tab sits at the far end of the carried tabs. */
    private int benchTabX() {
        return leftPos + carryX() + CARRY_W - 10 - TAB_W;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (bench && benchView.dragged(event.x(), event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (bench && benchView.released()) return true;
        return super.mouseReleased(event);
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
        NearbyMenu near = near();
        if (bench && near != null && benchGun >= 0) {
            carryPanel(g, x + NEAR_X - CARRY_X, y);
            benchView.draw(g, font, minecraft, near, benchGun, x, y, mouseX, mouseY);
        } else {
            gearPanel(g, x, y, mouseX, mouseY);
            carryPanel(g, x, y);
            nearPanel(g, x, y);
        }

        // slot sockets under every visible slot, and a brass ring on the one in your hand
        for (Slot slot : menu.slots) {
            if (slot.isActive() && slot.x > AWAY / 2) KitArt.slot(g, x + slot.x, y + slot.y);
        }
        int held = minecraft.player.getInventory().getSelectedSlot();
        if (menu.slots.size() <= InventoryMenu.USE_ROW_SLOT_START + held) return;
        Slot hand = menu.slots.get(InventoryMenu.USE_ROW_SLOT_START + held);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.SLOT_SELECTED, x + hand.x - 3, y + hand.y - 3, 22, 22);

        // torch light follows the cursor
        g.blitSprite(RenderPipelines.GUI_TEXTURED, KitArt.TORCH, mouseX - 110, mouseY - 110, 220, 220);
    }

    /** The world side: the floor and containers in reach as tabs, and the 2x2 under them. */
    private void nearPanel(GuiGraphicsExtractor g, int x, int y) {
        KitArt.panel(g, KitArt.Material.METAL, x + NEAR_X, y, NEAR_W, H);
        NearbyMenu near = near();
        takeW = 0;
        if (near == null) {
            KitArt.header(g, font, "Field craft", x + NEAR_X + 12, y + 12);
            g.text(font, Component.literal("->"), x + NEAR_X + 92, y + 59, KitArt.INK_DIM, false);
            return;
        }
        List<Nearby.Source> sources = near.sources();
        int tx = x + NEAR_X + 10;
        for (int k = 0; k < sources.size(); k++) {
            Nearby.Source source = sources.get(k);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, k == near.selected ? KitArt.TAB_SELECTED : KitArt.TAB, tx, y + 4, TAB_W, 22);
            if (!source.icon().isEmpty()) {
                g.item(source.icon(), tx + (TAB_W - 16) / 2, y + 7);
            } else {
                String mark = source.floor() ? "FL" : source.name().getString().substring(0, 1);
                g.centeredText(font, KitArt.stencil(mark), tx + TAB_W / 2, y + 11, KitArt.STENCIL_INK);
            }
            tx += TAB_W + 2;
        }
        int k = Math.max(0, Math.min(near.selected, sources.size() - 1));
        Nearby.Source source = sources.get(k);
        int hy = y + 32;
        double inside = 0;
        boolean any = false;
        for (int i = near.sourceStart(k); i < near.sourceEnd(k); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            inside += Weights.of(stack);
            any |= !stack.isEmpty();
        }
        String name = source.name().getString();
        if (font.width(name) > 96) name = font.plainSubstrByWidth(name, 90) + "..";
        KitArt.header(g, font, name, x + NEAR_X + 12, hy);
        if (any) {
            takeW = KitArt.tape(g, font, "take all", x + NEAR_X + NEAR_W - 9, hy - 3, true, KitArt.MARKER_INK);
            takeX = x + NEAR_X + NEAR_W - 9 - takeW;
            takeY = hy - 3;
            g.text(font, Component.literal(Weights.format(inside)), takeX - 6 - font.width(Weights.format(inside)), hy,
                    0x88E6DCC0, false);
        } else if (source.floor()) {
            g.text(font, KitArt.marker("Nothing on the ground."), x + NEAR_X + 16, y + 108, KitArt.INK_DIM, false);
            g.text(font, KitArt.marker("Put things here to set them down."), x + NEAR_X + 16, y + 120 - 2, KitArt.INK_DIM, false);
        } else {
            g.text(font, KitArt.marker("Empty."), x + NEAR_X + 16, y + 108, KitArt.INK_DIM, false);
        }
        g.text(font, KitArt.stencil("FIELD CRAFT"), x + NEAR_X + 14, y + CRAFT_Y - 11, 0xAAE6DCC0, false);
        g.text(font, Component.literal("->"), x + NEAR_X + 85, y + CRAFT_Y + 13, KitArt.INK_DIM, false);
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
        if (benchGun >= 0) {
            int bx = x + CARRY_X + CARRY_W - 10 - TAB_W;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, bench ? KitArt.TAB_SELECTED : KitArt.TAB, bx, y + 4, TAB_W, 22);
            g.item(menu.slots.get(benchGun).getItem(), bx + (TAB_W - 16) / 2, y + 7);
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
        int k = nearTabAt(mouseX, mouseY);
        if (k >= 0 && menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(font, List.of(near().sources().get(k).name()), Optional.empty(), mouseX, mouseY,
                    KitArt.TOOLTIP_STYLE);
            return;
        }
        if (benchGun >= 0 && mouseY >= topPos + 4 && mouseY < topPos + 26 && mouseX >= benchTabX()
                && mouseX < benchTabX() + TAB_W && menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(font, List.of(Component.literal(bench ? "Back to the kit" : "Weapon bench")),
                    Optional.empty(), mouseX, mouseY, KitArt.TOOLTIP_STYLE);
            return;
        }
        if (hoveredSlot != null && hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            ItemStack stack = hoveredSlot.getItem();
            g.setTooltipForNextFrame(font, getTooltipFromContainerItem(stack), stack.getTooltipImage(), mouseX, mouseY,
                    KitArt.TOOLTIP_STYLE);
        }
    }
}
