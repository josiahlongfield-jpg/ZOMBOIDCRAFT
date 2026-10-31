package com.zomboidcraft.client.kit;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.CarrySlots;
import com.zomboidcraft.loot.Nearby;
import com.zomboidcraft.loot.NearbyMenu;
import com.zomboidcraft.client.sound.UiSounds;
import com.zomboidcraft.mixin.client.SlotAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The kit screen, and the only inventory: your survivor laid out like a looter shooter's character screen. E (or I)
 * opens it, and so does searching a container. It runs over the nearby menu when the server has the mod (your own
 * slots in the inventory menu's order, then the floor and the containers in reach) or the plain inventory otherwise.
 * Q and R flip its pages: Character (weapons, belt, clothing, carry gear and your figure), Pack (pockets, belt and every
 * bag beside the floor and containers in reach, plus field craft), Supplies (everything you carry, sorted), the Bench
 * (a weapon up close, with every part you carry that fits it, and its condition), Health (each body part and treating
 * it), Skills (levels and what they really do) and the Map (where you've been, on M as well).
 * <p>
 * Nothing here is a vanilla slot on screen: every tile is drawn by hand, and clicks become the same slot clicks and
 * menu buttons a vanilla screen sends, so the server checks every move as usual.
 */
public class KitScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    static final int W = 616, H = 346;
    private static final int AWAY = -10000;

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZomboidCraft.id("kit"));
    public static final KeyMapping OPEN = new KeyMapping("key.zomboidcraft.kit", InputConstants.KEY_I, CATEGORY);
    /** Opens the kit screen straight on the map page. */
    public static final KeyMapping MAP_KEY = new KeyMapping("key.zomboidcraft.map", InputConstants.KEY_M, CATEGORY);

    enum Page {
        CHARACTER("Character", true), CLOTHING("Clothing", true), PACK("Pack", true), SUPPLIES("Supplies", true), BENCH("Bench", true), BUILD("Build", true), HEALTH("Health", true), SKILLS("Skills", true),
        FILES("Files", true), MAP("Map", true);

        final String label;
        final boolean open;

        Page(String label, boolean open) {
            this.label = label;
            this.open = open;
        }
    }

    Page page = Page.CHARACTER;
    /** The page a key asked for, for the screen the server is about to open; null when a container opened it. */
    private static @Nullable Page requested;
    /** Shift was held for the click being handled. */
    boolean shift;

    final CharacterPage character = new CharacterPage(this);
    final ClothingPage clothing = new ClothingPage(this);
    final PackPage pack = new PackPage(this);
    final SuppliesPage supplies = new SuppliesPage(this);
    final BenchPage bench = new BenchPage(this);
    final BuildPage build = new BuildPage(this);
    final HealthPage health = new HealthPage(this);
    final SkillsPage skills = new SkillsPage(this);
    final MapPage map = new MapPage(this);
    final FilesPage files = new FilesPage(this);

    /** Something on screen that answers the mouse: a tile, a link, a tab. Rebuilt every frame. */
    record Hot(int x, int y, int w, int h, ItemStack card, @Nullable Runnable click, @Nullable Runnable alt, @Nullable String tip,
               int slot) {
        boolean has(double mx, double my) {
            return KitDraw.in(mx, my, x, y, w, h);
        }
    }

    private final List<Hot> hots = new ArrayList<>();
    private final List<Hot> flyHots = new ArrayList<>();
    private @Nullable Hot hover;
    /** A left press on a filled slot, waiting to see whether it's a click or the start of a drag. */
    private @Nullable Hot pressed;
    private double pressX, pressY;
    /** The slot a dragged thing came from, or -1 when nothing is being dragged. */
    private int dragFrom = -1;
    private final Map<Slot, int[]> original = new HashMap<>();

    /** A slot's picker: what you carry that could go there. */
    record Flyout(String title, int target, List<Integer> choices, int x, int y, int tileW, int tileH) {
    }

    @Nullable Flyout fly;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(OPEN);
        KeyMappingHelper.registerKeyMapping(MAP_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(KitScreen::tick);
        MenuScreens.<NearbyMenu, KitScreen<NearbyMenu>>register(Nearby.MENU, KitScreen::new);
    }

    private static void tick(Minecraft mc) {
        while (OPEN.consumeClick()) {
            if (mc.gui.screen() == null) open(mc, Page.CHARACTER);
        }
        while (MAP_KEY.consumeClick()) {
            if (mc.gui.screen() == null) open(mc, Page.MAP);
        }
    }

    /**
     * Opens the kit on a page. With the mod on the server it asks for the nearby menu, whose screen this becomes when
     * it arrives; otherwise it opens straight over the plain inventory.
     */
    private static void open(Minecraft mc, Page page) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        requested = page;
        if (ClientPlayNetworking.canSend(Nearby.OpenKit.TYPE)) {
            ClientPlayNetworking.send(new Nearby.OpenKit());
        } else {
            mc.gui.setScreen(new KitScreen<>(player.inventoryMenu, player.getInventory(), Component.literal("Kit")));
        }
    }

    /** A file was just read in the world: the kit opens on the Files page showing it. */
    public static void openFile(Minecraft mc, String id) {
        FilesPage.show(id);
        if (mc.gui.screen() == null) open(mc, Page.FILES);
    }

    /** Minecraft's survival inventory is about to open (E): the kit opens instead. Creative keeps its own screen. */
    public static @Nullable Screen replace(InventoryScreen vanilla) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !ZcConfig.get().look.fieldKitInventory || player.hasInfiniteMaterials()) return vanilla;
        open(mc, Page.CHARACTER);
        return null;
    }

    public KitScreen(M menu, Inventory inventory, Component title) {
        super(menu, inventory, title, W, H);
        // a key asked for a page (the kit key always asks for Character, wherever it was closed); searching a container
        // opens on the pack, with that container showing
        page = requested != null ? requested : menu instanceof NearbyMenu ? Page.PACK : Page.CHARACTER;
        requested = null;
    }

    LocalPlayer player() {
        return minecraft.player;
    }

    Minecraft mc() {
        return minecraft;
    }

    int left() {
        return leftPos;
    }

    int top() {
        return topPos;
    }

    AbstractContainerMenu menu() {
        return menu;
    }

    /** The floor and containers in reach, or null over the plain inventory. */
    @Nullable NearbyMenu near() {
        return menu instanceof NearbyMenu near ? near : null;
    }

    @Override
    protected void init() {
        fitGuiScale();
        super.init();
        CarrySlots.clientShows = slot -> true;
        for (Slot slot : menu.slots) {
            original.putIfAbsent(slot, new int[]{slot.x, slot.y});
            ((SlotAccessor) slot).zomboidcraft$setX(AWAY);
            ((SlotAccessor) slot).zomboidcraft$setY(AWAY);
        }
    }

    /** Like the field kit: at a large GUI scale, drop to the biggest one the screen fits at while it's open. */
    private void fitGuiScale() {
        var window = minecraft.getWindow();
        int scale = window.getGuiScale();
        while (scale > 1 && (window.getWidth() / scale < W + 8 || window.getHeight() / scale < H + 8)) scale--;
        if (scale == window.getGuiScale()) return;
        window.setGuiScale(scale);
        width = window.getGuiScaledWidth();
        height = window.getGuiScaledHeight();
    }

    @Override
    public void onClose() {
        // closed mid-drag: the dragged thing goes back where it came from
        if (dragFrom >= 0 && !menu.getCarried().isEmpty()) click(dragFrom, 0, ContainerInput.PICKUP);
        dragFrom = -1;
        super.onClose();
    }

    @Override
    public void removed() {
        original.forEach((slot, xy) -> {
            ((SlotAccessor) slot).zomboidcraft$setX(xy[0]);
            ((SlotAccessor) slot).zomboidcraft$setY(xy[1]);
        });
        CarrySlots.clientShows = slot -> false;
        var window = minecraft.getWindow();
        var options = minecraft.options;
        int wanted = window.calculateScale(options.guiScale().get(), options.forceUnicodeFont().get());
        if (window.getGuiScale() != wanted) window.setGuiScale(wanted);
        super.removed();
    }

    // ------------------------------------------------------------------ moving things

    void click(int slot, int button, ContainerInput input) {
        if (slot < 0 || slot >= menu.slots.size()) return;
        slotClicked(menu.slots.get(slot), slot, button, input);
    }

    /** The slots that are yours to take from: pockets, quick slots, off hand and inside worn bags. */
    List<Integer> carried() {
        List<Integer> out = new ArrayList<>();
        for (int i = InventoryMenu.INV_SLOT_START; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot instanceof CarrySlots.GearSlot) continue;
            if (slot instanceof CarrySlots.BagSlot bag && !bag.open()) continue;
            if (slot instanceof NearbyMenu.NearbySlot) break;
            out.add(i);
        }
        return out;
    }

    /**
     * Puts what's in {@code from} into {@code to}, and whatever {@code to} held back where it came from (or anywhere
     * it fits). Three ordinary clicks, so the server checks each one.
     */
    void swap(int from, int to) {
        if (!menu.getCarried().isEmpty() || from == to) return;
        click(from, 0, ContainerInput.PICKUP);
        click(to, 0, ContainerInput.PICKUP);
        if (!menu.getCarried().isEmpty()) click(from, 0, ContainerInput.PICKUP);
        if (!menu.getCarried().isEmpty()) putDown();
        UiSounds.play(UiSounds.ITEM_MOVE);
    }

    /** Takes a slot's thing off you into your bags (or pockets), the way a shift-click does. */
    void unequip(int slot) {
        click(slot, 0, ContainerInput.QUICK_MOVE);
        UiSounds.play(UiSounds.ITEM_MOVE);
    }

    /**
     * Lets go of a dragged thing: over a slot it goes there (swapping with what was in it, which goes back where the
     * dragged thing came from); anywhere else it goes back where it came from.
     */
    private void drop(double mx, double my) {
        int from = dragFrom;
        dragFrom = -1;
        if (menu.getCarried().isEmpty()) return;
        int to = -1;
        for (int i = hots.size() - 1; i >= 0 && to < 0; i--) {
            Hot h = hots.get(i);
            if (h.slot() >= 0 && h.slot() < menu.slots.size() && h.has(mx, my)) to = h.slot();
        }
        if (to >= 0 && to != from) {
            click(to, 0, ContainerInput.PICKUP);
            UiSounds.play(UiSounds.ITEM_MOVE);
        }
        if (!menu.getCarried().isEmpty()) click(from, 0, ContainerInput.PICKUP);
        if (!menu.getCarried().isEmpty()) putDown();
    }

    /** Whatever is left on the cursor goes into the first carried slot that takes it. */
    private void putDown() {
        ItemStack held = menu.getCarried();
        for (int i : carried()) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem() && slot.mayPlace(held)) {
                click(i, 0, ContainerInput.PICKUP);
                if (menu.getCarried().isEmpty()) return;
            }
        }
    }

    /** A bench menu button, applied here at once and sent to the server, the way vanilla screens send theirs. */
    void button(int id) {
        if (menu.clickMenuButton(minecraft.player, id)) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    void open(Page p) {
        if (!p.open || p == page) return;
        page = p;
        fly = null;
        UiSounds.play(UiSounds.TAB);
    }

    /** A plain click on a slot, the way a vanilla screen sends it: shift moves it across, right takes half. */
    void slotClick(int slot, int button) {
        boolean had = !menu.getCarried().isEmpty() || slot >= 0 && slot < menu.slots.size() && menu.slots.get(slot).hasItem();
        click(slot, button, shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP);
        if (had) UiSounds.play(UiSounds.ITEM_MOVE);
    }

    void inspect(int slot) {
        bench.show(slot);
        open(Page.BENCH);
    }

    void flip(int dir) {
        Page[] pages = Page.values();
        int i = page.ordinal();
        do {
            i = Math.floorMod(i + dir, pages.length);
        } while (!pages[i].open);
        open(pages[i]);
    }

    // ------------------------------------------------------------------ hot spots

    void hot(int x, int y, int w, int h, ItemStack card, @Nullable Runnable click, @Nullable Runnable alt, @Nullable String tip, int slot) {
        hots.add(new Hot(x, y, w, h, card, click, alt, tip, slot));
    }

    void hot(int x, int y, int w, int h, @Nullable Runnable click) {
        hot(x, y, w, h, ItemStack.EMPTY, click, null, null, -1);
    }

    /** A footer key hint that does what it says when clicked. Returns the width used. */
    int hint(GuiGraphicsExtractor g, Font font, String k, String what, int x, int y, @Nullable Runnable run) {
        int w = KitDraw.hint(g, font, k, what, x, y);
        if (run != null) hot(x - 1, y - 2, w - 8, 13, run);
        return w;
    }

    /** The two hints every page ends with: flipping pages and closing. Returns the width used. */
    int pagesAndClose(GuiGraphicsExtractor g, Font font, int x, int y) {
        int w = hint(g, font, "Q/R", "Pages", x, y, () -> flip(1));
        return w + hint(g, font, minecraft.options.keyInventory.getTranslatedKeyMessage().getString(), "Close", x + w, y, this::onClose);
    }

    boolean hovered(int x, int y, int w, int h, int mx, int my) {
        return fly == null && KitDraw.in(mx, my, x, y, w, h);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        boolean right = event.button() == 1;
        shift = minecraft.hasShiftDown();
        if (fly == null && page == Page.MAP && map.pressed(mx, my, event.button())) return true;
        if (fly != null) {
            for (Hot h : flyHots) {
                if (h.has(mx, my)) {
                    Runnable r = right && h.alt() != null ? h.alt() : h.click();
                    if (r != null) r.run();
                    return true;
                }
            }
            fly = null;
            return true;
        }
        // on the bench the right button only turns the weapon; everything there is picked with the left
        if (page == Page.BENCH && right) {
            bench.pressed(mx, my);
            return true;
        }
        for (int i = hots.size() - 1; i >= 0; i--) {
            Hot h = hots.get(i);
            if (h.has(mx, my)) {
                // shift + right click drops the whole stack on the floor at your feet
                if (right && shift && h.slot() >= 0 && h.slot() < menu.slots.size() && menu.slots.get(h.slot()).hasItem()
                        && menu.getCarried().isEmpty()) {
                    click(h.slot(), 1, ContainerInput.THROW);
                    UiSounds.play(UiSounds.ITEM_MOVE);
                    return true;
                }
                // a left press on something you could drag waits: moved, it's a drag; let go where it is, a click
                if (!right && h.slot() >= 0 && h.slot() < menu.slots.size() && menu.slots.get(h.slot()).hasItem() && menu.getCarried().isEmpty()) {
                    pressed = h;
                    pressX = mx;
                    pressY = my;
                    return true;
                }
                Runnable r = right && h.alt() != null ? h.alt() : h.click();
                if (r != null) r.run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragFrom >= 0) {
            drop(event.x(), event.y());
        } else if (event.button() == 0 && pressed != null) {
            Hot h = pressed;
            pressed = null;
            if (h.click() != null) h.click().run();
        }
        pressed = null;
        if (page == Page.MAP) map.released(event.x(), event.y());
        if (page == Page.BENCH) bench.released();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (event.button() == 0 && pressed != null && dragFrom < 0 && Math.hypot(event.x() - pressX, event.y() - pressY) > 3) {
            // picked up off its slot and carried under the mouse until you let go
            int from = pressed.slot();
            pressed = null;
            click(from, 0, ContainerInput.PICKUP);
            if (!menu.getCarried().isEmpty()) {
                dragFrom = from;
                UiSounds.play(UiSounds.ITEM_MOVE);
            }
            return true;
        }
        if (page == Page.MAP) map.dragged(event.x(), event.y());
        if (page == Page.BENCH) bench.dragged(event.x(), event.y());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (page == Page.SUPPLIES && supplies.scrolled(mx, my, sy)) return true;
        if (page == Page.BENCH && bench.scrolled(mx, my, sy)) return true;
        if (page == Page.MAP && map.scrolled(mx, my, sy)) return true;
        if (page == Page.SKILLS && skills.scrolled(mx, my, sy)) return true;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (fly != null && event.key() == InputConstants.KEY_ESCAPE) {
            fly = null;
            return true;
        }
        if (minecraft.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        if (minecraft.options.keyDrop.matches(event)) {
            flip(-1);
            return true;
        }
        if (event.key() == InputConstants.KEY_R) {
            flip(1);
            return true;
        }
        if (OPEN.matches(event) || MAP_KEY.matches(event) && page == Page.MAP) {
            onClose();
            return true;
        }
        if (MAP_KEY.matches(event)) {
            open(Page.MAP);
            return true;
        }
        if (event.key() == InputConstants.KEY_F && hover != null && hover.slot() >= 0 && KitItems.weapon(hover.card())) {
            inspect(hover.slot());
            return true;
        }
        if (page == Page.SUPPLIES && supplies.keyPressed(event)) return true;
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractBlurredBackground(g);
        g.fill(0, 0, width, height, 0xB8080A0C);
        g.fillGradient(0, 0, width, 60, 0xE6050607, 0x00050607);
        g.fillGradient(0, height - 50, width, height, 0x00050607, 0xD9050607);
        hots.clear();
        int x = leftPos, y = topPos;
        top(g, x, y, mouseX, mouseY);
        switch (page) {
            case CHARACTER -> character.draw(g, x, y, mouseX, mouseY);
            case CLOTHING -> clothing.draw(g, x, y, mouseX, mouseY);
            case PACK -> pack.draw(g, x, y, mouseX, mouseY);
            case SUPPLIES -> supplies.draw(g, x, y, mouseX, mouseY);
            case BENCH -> bench.draw(g, x, y, mouseX, mouseY);
            case BUILD -> build.draw(g, x, y, mouseX, mouseY);
            case HEALTH -> health.draw(g, x, y, mouseX, mouseY);
            case SKILLS -> skills.draw(g, x, y, mouseX, mouseY);
            case FILES -> files.draw(g, x, y, mouseX, mouseY);
            case MAP -> map.draw(g, x, y, mouseX, mouseY);
        }
    }

    /** The top bar: who you are, the pages, and the time and weather. */
    private void top(GuiGraphicsExtractor g, int x, int y, int mx, int my) {
        LocalPlayer player = minecraft.player;
        KitDraw.frame(g, x + 12, y + 7, 18, 18, 0x80E8E4DA);
        g.fill(x + 13, y + 8, x + 29, y + 24, 0x99141614);
        KitDraw.item(g, new ItemStack(net.minecraft.world.item.Items.SKELETON_SKULL), x + 14, y + 9, 14);
        KitDraw.caps(g, font, player.getName().getString(), x + 36, y + 7, KitDraw.BRIGHT, 1.3f);
        long day = minecraft.level == null ? 1 : Math.floorDiv(minecraft.level.getDefaultClockTime(), 24000L) + 1;
        KitDraw.text(g, font, "Survivor · day " + day, x + 36, y + 19, KitDraw.DIM, 0.9f);

        // Q · pages · E
        String[] labels = new String[Page.values().length];
        float total = 0;
        for (Page p : Page.values()) {
            labels[p.ordinal()] = p.label;
            total += KitDraw.capsWidth(font, p.label, 1f) + 14;
        }
        total += 2 * 13;
        float px = x + W / 2f - total / 2f;
        KitDraw.key(g, font, "Q", Math.round(px), y + 9, KitDraw.FAINT);
        hot(Math.round(px), y + 9, 10, 10, () -> flip(-1));
        px += 13 + 7;
        for (Page p : Page.values()) {
            float w = KitDraw.capsWidth(font, p.label, 1f);
            boolean on = p == page;
            boolean over = p.open && KitDraw.in(mx, my, Math.round(px) - 2, y + 7, Math.round(w) + 4, 14);
            int colour = on ? 0xFFFFFFFF : !p.open ? 0x40E8E4DA : over ? 0xFFFFFFFF : 0x9EE8E4DA;
            KitDraw.caps(g, font, p.label, px, y + 10, colour, 1f);
            if (on) g.fill(Math.round(px), y + 20, Math.round(px + w), y + 21, KitDraw.TEXT);
            if (p.open) hot(Math.round(px) - 2, y + 7, Math.round(w) + 4, 14, () -> open(p));
            else hot(Math.round(px) - 2, y + 7, Math.round(w) + 4, 14, ItemStack.EMPTY, null, null, "Not built yet", -1);
            px += w + 14;
        }
        px += 7 - 14 + 6;
        KitDraw.key(g, font, "R", Math.round(px), y + 9, KitDraw.DIM);
        hot(Math.round(px), y + 9, 10, 10, () -> flip(1));

        // time and weather
        if (minecraft.level != null) {
            long t = Math.floorMod(minecraft.level.getDefaultClockTime() + 6000, 24000L);
            String time = String.format(java.util.Locale.ROOT, "%02d:%02d", t / 1000, t % 1000 * 60 / 1000);
            KitDraw.capsRight(g, font, time + " · day " + day, x + W - 12, y + 8, KitDraw.BRIGHT, 1.2f);
            String weather = minecraft.level.isThundering() ? "Thunderstorm" : minecraft.level.isRaining() ? "Rain" : "Dry";
            KitDraw.textRight(g, font, weather, x + W - 12, y + 19, KitDraw.DIM, 0.9f);
        }
    }

    @Override
    protected void extractSlots(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // every tile is drawn by the pages
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.nextStratum();
        flyHots.clear();
        if (fly != null) {
            drawFlyout(g, fly, mouseX, mouseY);
            hover = null;
            for (Hot h : flyHots) if (h.has(mouseX, mouseY)) hover = h;
        } else {
            hover = null;
            for (int i = hots.size() - 1; i >= 0 && hover == null; i--) {
                if (hots.get(i).has(mouseX, mouseY)) hover = hots.get(i);
            }
        }
        if (hover == null || !menu.getCarried().isEmpty()) return;
        if (!hover.card().isEmpty()) {
            KitCard.draw(g, font, hover.card(), hover.slot(), mouseX, mouseY, width, height, page == Page.BENCH);
        } else if (hover.tip() != null) {
            int w = Math.round(KitDraw.width(font, hover.tip(), 1f)) + 10;
            int tx = Math.min(mouseX + 10, width - w - 4), ty = mouseY - 16;
            KitDraw.panel(g, tx, ty, w, 14);
            KitDraw.text(g, font, hover.tip(), tx + 5, ty + 3, KitDraw.TEXT, 1f);
        }
    }

    // ------------------------------------------------------------------ the picker

    /** Opens the picker for a slot beside the tile at x, y. */
    void pick(String title, int target, List<Integer> choices, int x, int y, int tileW, int tileH) {
        fly = new Flyout(title, target, choices, x, y, tileW, tileH);
        UiSounds.play(UiSounds.CLICK);
    }

    private void drawFlyout(GuiGraphicsExtractor g, Flyout f, int mx, int my) {
        boolean full = menu.slots.get(f.target()).hasItem();
        int n = f.choices().size() + (full ? 1 : 0);
        int cols = Math.max(1, Math.min(n, f.tileW() > 40 ? 2 : 4));
        int rows = Math.max(1, (n + cols - 1) / cols);
        int pad = 7, gap = 4;
        int w = Math.max(120, pad * 2 + cols * f.tileW() + (cols - 1) * gap);
        int h = pad * 2 + 13 + (n == 0 ? 20 : rows * (f.tileH() + gap) - gap);
        int x = Math.min(Math.max(4, f.x()), width - w - 4);
        int y = Math.min(Math.max(4, f.y()), height - h - 4);
        g.fill(x + 3, y + 5, x + w + 3, y + h + 5, 0x66000000);
        KitDraw.panel(g, x, y, w, h);
        KitDraw.caps(g, font, f.title(), x + pad, y + pad, KitDraw.TEXT, 0.85f);
        KitDraw.textRight(g, font, "Esc", x + w - pad, y + pad - 1, KitDraw.FAINT, 0.9f);
        int ty = y + pad + 13;
        if (n == 0) {
            KitDraw.text(g, font, "Nothing else you carry goes here.", x + pad, ty + 4, KitDraw.DIM, 0.9f);
            return;
        }
        int i = 0;
        for (int slot : f.choices()) {
            int cx = x + pad + (i % cols) * (f.tileW() + gap), cy = ty + (i / cols) * (f.tileH() + gap);
            ItemStack stack = menu.slots.get(slot).getItem();
            KitDraw.tile(g, font, stack, cx, cy, f.tileW(), f.tileH(), KitDraw.in(mx, my, cx, cy, f.tileW(), f.tileH()));
            KitDraw.pips(g, cx + 3, cy + 3, KitItems.tier(stack), KitDraw.TEXT, 1);
            int target = f.target();
            flyHots.add(new Hot(cx, cy, f.tileW(), f.tileH(), stack, () -> {
                fly = null;
                swap(slot, target);
            }, null, null, slot));
            i++;
        }
        if (full) {
            int cx = x + pad + (i % cols) * (f.tileW() + gap), cy = ty + (i / cols) * (f.tileH() + gap);
            boolean over = KitDraw.in(mx, my, cx, cy, f.tileW(), f.tileH());
            KitDraw.tile(g, font, ItemStack.EMPTY, cx, cy, f.tileW(), f.tileH(), over);
            KitDraw.capsCentred(g, font, "Take off", cx + f.tileW() / 2f, cy + f.tileH() / 2f - 3, over ? KitDraw.BRIGHT : KitDraw.DIM, 0.75f);
            int target = f.target();
            flyHots.add(new Hot(cx, cy, f.tileW(), f.tileH(), ItemStack.EMPTY, () -> {
                fly = null;
                unequip(target);
            }, null, "Into your bags", -1));
        }
    }
}
