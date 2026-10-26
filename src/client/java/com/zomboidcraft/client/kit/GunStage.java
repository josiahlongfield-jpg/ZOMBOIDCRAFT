package com.zomboidcraft.client.kit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.Attachments;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The bench's gun, big and turning: its own 3D model with every empty socket filled by a see-through green ghost of
 * the part that goes there, and a crosshair on each mount. Drawn through Minecraft's entity-in-GUI renderer as a
 * dropped item pinned in place, so the gun and its ghosts are one model with proper depth. Drag to turn it.
 */
final class GunStage {
    static final int GHOST = 0xFF4DFF6E;

    record Mount(Attachments.Slot slot, Vector3f at, Identifier ghost) {
    }

    /** A mount as last drawn, in GUI pixels. */
    record Point(Attachments.Slot slot, int x, int y, boolean fitted) {
    }

    private static final Map<String, List<Mount>> MOUNTS = new HashMap<>();

    private final ItemEntityRenderState state = new ItemEntityRenderState();
    private float yaw = 90f, pitch = 14f;
    private boolean dragging;
    private double dragX, dragY;
    private int bx, by, bw, bh;
    final List<Point> points = new ArrayList<>();

    static List<Mount> mounts(String gun) {
        return MOUNTS.computeIfAbsent(gun, id -> {
            List<Mount> list = new ArrayList<>();
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

    /** Model pixel point to GUI point, given the frame's transform. */
    private static float[] project(Vector3f pixel, Quaternionf turn, Vector3f lift, Vector3f shift, float scale, float cx, float cy) {
        Vector3f p = new Vector3f(pixel).div(16f).sub(0.5f, 0.5f, 0.5f).add(lift);
        turn.transform(p);
        p.add(shift);
        return new float[]{cx + p.x * scale, cy + p.y * scale};
    }

    /**
     * Draws the gun inside the box. {@code lit} is the socket to light up (the part under the mouse fits there), or
     * null. Returns false if the gun has no model to show.
     */
    boolean draw(GuiGraphicsExtractor g, ItemStack gun, int x0, int y0, int w, int h, int mx, int my, @Nullable Attachments.Slot lit) {
        Minecraft mc = Minecraft.getInstance();
        bx = x0;
        by = y0;
        bw = w;
        bh = h;
        points.clear();
        // a faint green grid, lit from the middle
        g.fillGradient(x0, y0, x0 + w, y0 + h, 0x18101820, 0x30000000);
        for (int gx = x0 + 12; gx < x0 + w; gx += 16) g.fill(gx, y0, gx + 1, y0 + h, 0x0C4DFF6E);
        for (int gy = y0 + 8; gy < y0 + h; gy += 16) g.fill(x0, gy, x0 + w, gy + 1, 0x0C4DFF6E);

        state.item.clear();
        mc.getItemModelResolver().updateForTopItem(state.item, gun, ItemDisplayContext.NONE, mc.level, mc.player, 0);
        String id = Attachments.gunId(gun);
        List<Mount> mounts = id == null ? List.of() : mounts(id);
        for (Mount mount : mounts) {
            if (Attachments.key(gun, mount.slot()) == null) {
                ItemStack ghost = new ItemStack(Items.STICK);
                ghost.set(DataComponents.ITEM_MODEL, mount.ghost());
                mc.getItemModelResolver().appendItemLayers(state.item, ghost, ItemDisplayContext.NONE, mc.level, mc.player, 0);
            }
        }
        if (state.item.isEmpty()) return false;
        float theta = (float) Math.toRadians(yaw);
        state.entityType = EntityTypes.ITEM;
        state.count = 1;
        state.lightCoords = 0xF000F0;
        // pin the dropped-item renderer: its spin becomes our turn and its bob a fixed lift
        state.ageInTicks = -20f * theta;
        state.bobOffset = 2f * theta;
        AABB box = state.item.getModelBoundingBox();
        Vector3f lift = new Vector3f(0, 0.1f + (float) -box.minY + 0.0625f, 0);
        Quaternionf view = new Quaternionf().rotateX((float) Math.toRadians(pitch)).rotateZ((float) Math.PI);
        Quaternionf turn = new Quaternionf(view).rotateY(theta);
        Vector3f middle = new Vector3f((float) (box.minX + box.maxX) / 2, (float) (box.minY + box.maxY) / 2, (float) (box.minZ + box.maxZ) / 2)
                .add(lift);
        turn.transform(middle);
        Vector3f shift = middle.negate();
        float longest = (float) Math.max(box.getXsize(), Math.max(box.getYsize(), box.getZsize()));
        float scale = Math.min((w - 24) / longest, h / longest * 2.2f) * 0.78f;
        g.entity(state, scale, shift, view, null, x0, y0, x0 + w, y0 + h);
        float cx = x0 + w / 2f, cy = y0 + h / 2f;

        // a crosshair on each mount: green on an empty one, brass on a fitted one you point at
        for (Mount mount : mounts) {
            float[] at = project(mount.at(), turn, lift, shift, scale, cx, cy);
            boolean fitted = Attachments.key(gun, mount.slot()) != null;
            int ax = Math.round(at[0]), ay = Math.round(at[1]);
            points.add(new Point(mount.slot(), ax, ay, fitted));
            boolean over = !dragging && Math.abs(mx - ax) <= 6 && Math.abs(my - ay) <= 6;
            if (fitted) {
                if (over) cross(g, ax, ay, 4, 0xFFE2C27A);
                continue;
            }
            boolean on = over || mount.slot() == lit;
            int alpha = on ? 0xFF : lit != null ? 0x40 : 0xD0;
            cross(g, ax, ay, on ? 6 : 4, (alpha << 24) | (GHOST & 0xFFFFFF));
        }
        return true;
    }

    private static void cross(GuiGraphicsExtractor g, int x, int y, int arm, int colour) {
        g.fill(x - arm, y, x + arm + 1, y + 1, colour);
        g.fill(x, y - arm, x + 1, y + arm + 1, colour);
    }

    /** What to call a mount when you point at it. */
    static String label(Point p, ItemStack gun) {
        String socket = AttachmentItem.slotName(p.slot());
        if (p.fitted()) return socket + ": " + Attachments.get(gun, p.slot()).getHoverName().getString() + " (click to take off)";
        return socket + ": empty";
    }

    boolean pressed(double mx, double my) {
        if (!KitDraw.in(mx, my, bx, by, bw, bh)) return false;
        dragging = true;
        dragX = mx;
        dragY = my;
        return true;
    }

    void dragged(double mx, double my) {
        if (!dragging) return;
        yaw += (float) (mx - dragX) * 0.7f;
        pitch = Math.max(-35f, Math.min(50f, pitch + (float) (my - dragY) * 0.5f));
        dragX = mx;
        dragY = my;
    }

    void released() {
        dragging = false;
    }
}
