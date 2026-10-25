package com.zomboidcraft.client.inventory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.carry.GearStore;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.loot.NearbyMenu;
import java.io.Reader;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The weapon bench: the gun you carry, big and turning, with every empty slot shown as a see-through green part
 * and a crosshair where it mounts. Hold a part and click its socket to fit it; click a fitted part to take it off.
 * The gun is drawn through Minecraft's entity-in-GUI renderer as a dropped item pinned in place, so the gun and its
 * ghost parts are one model with proper depth.
 */
final class WeaponBench {
    // the stage, relative to the screen's left/top
    static final int X = 0, Y = 0, W = 336, H = 214;
    private static final int GUN_Y0 = 30, GUN_Y1 = 150;
    private static final int GHOST = 0xFF4DFF6E;
    private static final int BRASS = 0xFFE2C27A;

    private record Mount(Attachments.Slot slot, Vector3f at, Identifier ghost) {
    }

    private static final Map<String, List<Mount>> MOUNTS = new HashMap<>();

    private static final Map<Attachments.Slot, String> LABELS = new EnumMap<>(Map.of(
            Attachments.Slot.OPTIC, "Optic", Attachments.Slot.MUZZLE, "Muzzle", Attachments.Slot.UNDERBARREL, "Under-barrel",
            Attachments.Slot.SIDE_RAIL, "Side rail", Attachments.Slot.STOCK, "Stock", Attachments.Slot.MAGAZINE, "Magazine",
            Attachments.Slot.BIPOD, "Bipod"));
    /** Where each socket's tag sits from its mount point, in GUI pixels. */
    private static final Map<Attachments.Slot, int[]> TAG_OFFSET = new EnumMap<>(Map.of(
            Attachments.Slot.OPTIC, new int[]{0, -40}, Attachments.Slot.MUZZLE, new int[]{-6, -38},
            Attachments.Slot.UNDERBARREL, new int[]{-8, 40}, Attachments.Slot.SIDE_RAIL, new int[]{-48, 30},
            Attachments.Slot.STOCK, new int[]{6, -36}, Attachments.Slot.MAGAZINE, new int[]{30, 40},
            Attachments.Slot.BIPOD, new int[]{0, 40}));

    private final ItemEntityRenderState gunState = new ItemEntityRenderState();
    private final long opened = System.currentTimeMillis();
    private float yaw = 90f, pitch = 14f;
    private boolean dragging;
    private long lastDrag;
    private double dragX, dragY;
    /** Socket tags as last drawn: slot, left, top, width, height. */
    private final Map<Attachments.Slot, int[]> tags = new EnumMap<>(Attachments.Slot.class);
    private int backX, backY, backW;

    /** The menu slot of the gun to show: in hand, slung, then anywhere you carry it. -1 if none. */
    static int findGun(NearbyMenu menu, Minecraft mc) {
        int held = InventoryMenu.USE_ROW_SLOT_START + mc.player.getInventory().getSelectedSlot();
        int sling = menu.firstGear + GearStore.SLING;
        int[] order = new int[2 + 9 + 27 + 1];
        int n = 0;
        order[n++] = held;
        order[n++] = sling;
        for (int i = InventoryMenu.USE_ROW_SLOT_START; i < InventoryMenu.USE_ROW_SLOT_END; i++) order[n++] = i;
        for (int i = InventoryMenu.INV_SLOT_START; i < InventoryMenu.INV_SLOT_END; i++) order[n++] = i;
        order[n++] = InventoryMenu.SHIELD_SLOT;
        for (int i : order) {
            if (i < menu.slots.size() && Attachments.isModular(menu.slots.get(i).getItem())) return i;
        }
        return -1;
    }

    private static List<Mount> mounts(String gun) {
        return MOUNTS.computeIfAbsent(gun, id -> {
            List<Mount> list = new java.util.ArrayList<>();
            var resource = Minecraft.getInstance().getResourceManager().getResource(ZomboidCraft.id("gun_slots/" + id + ".json"));
            if (resource.isEmpty()) return list;
            try (Reader reader = resource.get().openAsReader()) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (JsonElement e : root.getAsJsonArray("slots")) {
                    JsonObject o = e.getAsJsonObject();
                    Attachments.Slot slot = Attachments.Slot.valueOf(o.get("slot").getAsString().toUpperCase(java.util.Locale.ROOT));
                    var m = o.getAsJsonArray("mount");
                    String ghost = o.get("ghost").getAsString();
                    String name = ghost.substring(ghost.lastIndexOf('/') + 1);
                    list.add(new Mount(slot, new Vector3f(m.get(0).getAsFloat(), m.get(1).getAsFloat(), m.get(2).getAsFloat()),
                            ZomboidCraft.id("bench_ghost/" + name)));
                }
            } catch (Exception ex) {
                ZomboidCraft.LOGGER.warn("Couldn't read the slot list for {}: {}", id, ex.toString());
            }
            return list;
        });
    }

    /** Model pixel point to stage GUI point, given the frame's transform. */
    private static float[] project(Vector3f pixel, Quaternionf turn, Vector3f lift, Vector3f shift, float scale, float cx, float cy) {
        Vector3f p = new Vector3f(pixel).div(16f).sub(0.5f, 0.5f, 0.5f).add(lift);
        turn.transform(p);
        p.add(shift);
        return new float[]{cx + p.x * scale, cy + p.y * scale};
    }

    /** Draws the bench over the gear and carried panels. */
    void draw(GuiGraphicsExtractor g, Font font, Minecraft mc, NearbyMenu menu, int gunSlot, int left, int top, int mouseX, int mouseY) {
        int x0 = left + X, y0 = top + Y;
        KitArt.panel(g, KitArt.Material.METAL, x0, y0, W, H);
        // a dark stage with a faint green grid, lit from the middle
        g.fillGradient(x0 + 6, y0 + 6, x0 + W - 6, y0 + H - 6, 0xF01D2330, 0xF00A0C12);
        for (int gx = x0 + 6 + 16; gx < x0 + W - 6; gx += 16) g.fill(gx, y0 + 26, gx + 1, y0 + 152, 0x0E4DFF6E);
        for (int gy = y0 + 26; gy < y0 + 152; gy += 16) g.fill(x0 + 6, gy, x0 + W - 6, gy + 1, 0x0E4DFF6E);

        ItemStack gun = menu.slots.get(gunSlot).getItem();
        String id = Attachments.gunId(gun);
        KitArt.header(g, font, gun.getHoverName().getString(), x0 + 12, y0 + 12);
        if (gun.isDamageableItem()) {
            int cond = Math.round(100f * (gun.getMaxDamage() - gun.getDamageValue()) / gun.getMaxDamage());
            KitArt.tape(g, font, "Condition " + cond + "%", x0 + 128, y0 + 9, false, cond < 35 ? KitArt.MARKER_RED : KitArt.MARKER_INK);
        }
        backW = KitArt.tape(g, font, "back to kit", x0 + W - 9, y0 + 9, true, KitArt.MARKER_INK);
        backX = x0 + W - 9 - backW;
        backY = y0 + 9;

        // the turn: drag to spin it, otherwise it sways gently side to side
        float t = (System.currentTimeMillis() - opened) / 1000f;
        if (!dragging && System.currentTimeMillis() - lastDrag > 2500) {
            yaw += ((90f + (float) Math.sin(t / 2.6f) * 26f) - yaw) * 0.04f;
            pitch += (14f - pitch) * 0.04f;
        }
        float theta = (float) Math.toRadians(yaw);

        // the gun with a ghost part in each empty slot, as one model
        gunState.item.clear();
        mc.getItemModelResolver().updateForTopItem(gunState.item, gun, ItemDisplayContext.NONE, mc.level, mc.player, 0);
        List<Mount> mounts = id == null ? List.of() : mounts(id);
        ItemStack carried = menu.getCarried();
        Attachments.Slot wanted = carried.isEmpty() ? null : Attachments.slotOf(carried);
        for (Mount mount : mounts) {
            if (Attachments.key(gun, mount.slot()) == null) {
                ItemStack ghost = new ItemStack(Items.STICK);
                ghost.set(DataComponents.ITEM_MODEL, mount.ghost());
                mc.getItemModelResolver().appendItemLayers(gunState.item, ghost, ItemDisplayContext.NONE, mc.level, mc.player, 0);
            }
        }
        if (gunState.item.isEmpty()) return;
        gunState.entityType = EntityType.ITEM;
        gunState.count = 1;
        gunState.lightCoords = 0xF000F0;
        // pin the dropped-item renderer: its spin becomes our turn and its bob a fixed lift
        gunState.ageInTicks = -20f * theta;
        gunState.bobOffset = 2f * theta;
        AABB box = gunState.item.getModelBoundingBox();
        Vector3f lift = new Vector3f(0, 0.1f + (float) -box.minY + 0.0625f, 0);
        Quaternionf view = new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.PI);
        Quaternionf turn = new Quaternionf(view).rotateY(theta);
        Vector3f middle = new Vector3f((float) (box.minX + box.maxX) / 2, (float) (box.minY + box.maxY) / 2, (float) (box.minZ + box.maxZ) / 2)
                .add(lift);
        turn.transform(middle);
        Vector3f shift = middle.negate();
        float longest = (float) Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
        float scale = Math.min((W - 40) / longest, (GUN_Y1 - GUN_Y0) / longest * 2.2f) * 0.92f;
        int gx0 = x0 + 8, gx1 = x0 + W - 8, gy0 = y0 + GUN_Y0, gy1 = y0 + GUN_Y1;
        g.entity(gunState, scale, shift, view, null, gx0, gy0, gx1, gy1);
        float cx = (gx0 + gx1) / 2f, cy = (gy0 + gy1) / 2f;

        // crosshairs at the mounts, leader lines and a tag per socket
        tags.clear();
        for (Mount mount : mounts) {
            float[] at = project(mount.at(), turn, lift, shift, scale, cx, cy);
            boolean full = Attachments.key(gun, mount.slot()) != null;
            boolean fits = wanted == mount.slot();
            boolean dim = wanted != null && !fits;
            int colour = full ? BRASS : GHOST;
            int ax = Math.round(at[0]), ay = Math.round(at[1]);
            if (!full) {
                g.fill(ax - 4, ay, ax + 5, ay + 1, 0xE64DFF6E);
                g.fill(ax, ay - 4, ax + 1, ay + 5, 0xE64DFF6E);
            }
            int[] off = TAG_OFFSET.getOrDefault(mount.slot(), new int[]{0, -36});
            ItemStack part = Attachments.get(gun, mount.slot());
            String label = LABELS.getOrDefault(mount.slot(), mount.slot().getSerializedName());
            String what = full ? part.getHoverName().getString() : "empty";
            int tw = Math.max(font.width(label) / 2, font.width(what)) + 10;
            int th = 19;
            int tx = Math.max(x0 + 8, Math.min(x0 + W - 8 - tw, ax + off[0] - tw / 2));
            int ty = Math.max(y0 + 26, Math.min(y0 + 152 - th, ay + off[1] - th / 2));
            line(g, ax, ay, tx + tw / 2, ty + (off[1] < 0 ? th : 0), (full ? 0x80E2C27A : 0x8C4DFF6E));
            boolean hover = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + th;
            int alpha = dim ? 0x40 : 0xFF;
            g.fill(tx, ty, tx + tw, ty + th, (dim ? 0x60 : 0xD0) << 24 | (full ? 0x0E0C09 : 0x060E09));
            int border = (fits || hover ? colour : (colour & 0x00FFFFFF) | 0x73000000);
            g.fill(tx, ty, tx + tw, ty + 1, border);
            g.fill(tx, ty + th - 1, tx + tw, ty + th, border);
            g.fill(tx, ty, tx + 1, ty + th, border);
            g.fill(tx + tw - 1, ty, tx + tw, ty + th, border);
            if (fits) g.fill(tx - 1, ty - 1, tx + tw + 1, ty, colour);
            KitArt.slotLabel(g, font, label, tx + tw / 2, ty + 3);
            g.centeredText(font, Component.literal(what), tx + tw / 2, ty + 9, (alpha << 24) | (full ? 0xE2C27A : 0xBFFFCB));
            tags.put(mount.slot(), new int[]{tx, ty, tw, th});
        }

        stats(g, font, gun, x0 + 12, y0 + 158);
        g.text(font, Component.literal("Hold a part and click its socket. Click a fitted part to take it off. Drag to turn."),
                x0 + 12, y0 + H - 12, 0x73BED7FF, false);
    }

    /** Dotted line from a mount to its tag. */
    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int colour) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) / 2;
        for (int i = 0; i <= steps; i++) {
            int x = x0 + (x1 - x0) * i / Math.max(1, steps);
            int y = y0 + (y1 - y0) * i / Math.max(1, steps);
            g.fill(x, y, x + 1, y + 1, colour);
        }
    }

    /** Handling: what the fitted parts do, as changes from the bare gun. */
    private static void stats(GuiGraphicsExtractor g, Font font, ItemStack gun, int x, int y) {
        g.text(font, KitArt.stencil("HANDLING"), x, y, 0xAAE6DCC0, false);
        String optic = Attachments.key(gun, Attachments.Slot.OPTIC) == null ? "Iron sights"
                : Attachments.get(gun, Attachments.Slot.OPTIC).getHoverName().getString();
        GunState state = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        String mag = state.mag() ? state.rounds() + " / " + RifleItem.capacity() + " rds" : "none";
        row(g, font, x, y + 11, "Sight", optic, 0);
        row(g, font, x, y + 21, "Magazine", mag, state.mag() ? 0 : -1);
        row(g, font, x, y + 31, "Noise", Attachments.suppressed(gun) ? "Suppressed" : "Loud", Attachments.suppressed(gun) ? 1 : 0);
        int col = x + 160;
        factor(g, font, col, y + 11, "Aimed spread", Attachments.aimSpreadFactor(gun));
        factor(g, font, col, y + 21, "Hip spread", Attachments.hipSpreadFactor(gun));
        factor(g, font, col, y + 31, "Recoil", Attachments.recoilFactor(gun));
        if (!ZcConfig.get().guns.attachments) {
            g.text(font, Component.literal("Attachments are off in settings."), x, y + 43, 0xFFE07A5A, false);
        }
    }

    private static void row(GuiGraphicsExtractor g, Font font, int x, int y, String name, String value, int mood) {
        g.text(font, Component.literal(name), x, y, KitArt.INK_DIM, false);
        int colour = mood > 0 ? 0xFF8FD16A : mood < 0 ? 0xFFE07A5A : KitArt.INK;
        g.text(font, Component.literal(value), x + 58, y, colour, false);
    }

    /** A multiplier where lower is better, shown as a percentage change. */
    private static void factor(GuiGraphicsExtractor g, Font font, int x, int y, String name, double f) {
        int pct = (int) Math.round((f - 1) * 100);
        String value = pct == 0 ? "-" : (pct > 0 ? "+" : "") + pct + "%";
        g.text(font, Component.literal(name), x, y, KitArt.INK_DIM, false);
        g.text(font, Component.literal(value), x + 78, y, pct < 0 ? 0xFF8FD16A : pct > 0 ? 0xFFE07A5A : KitArt.INK, false);
    }

    // ---------------------------------------------------------------- input

    /** True when the click was the bench's: "back to kit" (returns false through {@code back}), a socket, or a drag. */
    boolean clicked(double mx, double my, int left, int top, Minecraft mc, NearbyMenu menu, int gunSlot, Runnable back) {
        if (backW > 0 && mx >= backX && mx < backX + backW && my >= backY && my < backY + 13) {
            back.run();
            return true;
        }
        for (Map.Entry<Attachments.Slot, int[]> e : tags.entrySet()) {
            int[] r = e.getValue();
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                mc.gameMode.handleInventoryButtonClick(menu.containerId, NearbyMenu.benchButton(gunSlot, e.getKey()));
                return true;
            }
        }
        if (mx >= left + X && mx < left + X + W && my >= top + Y + GUN_Y0 && my < top + Y + GUN_Y1 && menu.getCarried().isEmpty()) {
            dragging = true;
            dragX = mx;
            dragY = my;
            return true;
        }
        return false;
    }

    boolean dragged(double mx, double my) {
        if (!dragging) return false;
        yaw -= (float) (mx - dragX) * 0.7f;
        pitch = Math.max(-35f, Math.min(50f, pitch + (float) (my - dragY) * 0.5f));
        dragX = mx;
        dragY = my;
        lastDrag = System.currentTimeMillis();
        return true;
    }

    boolean released() {
        boolean was = dragging;
        dragging = false;
        lastDrag = System.currentTimeMillis();
        return was;
    }

    /** True when the point is on the stage, so clicks there don't count as dropping the held stack. */
    static boolean over(double mx, double my, int left, int top) {
        return mx >= left + X && mx < left + X + W && my >= top + Y && my < top + Y + H;
    }
}
