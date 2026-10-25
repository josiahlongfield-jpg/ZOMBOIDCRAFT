package com.zomboidcraft.client.inventory;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * The field-kit look, shared by every screen that opens a container: material panels with stitched or riveted
 * frames, recessed slots, masking-tape labels and stencilled headers. Art comes from tools/ui/gen_inventory_gui.py.
 */
public final class KitArt {
    public enum Material {
        CANVAS("canvas", "frame_canvas"), METAL("metal", "frame_metal"), WOOD("wood", "frame_metal"),
        CONCRETE("concrete", "frame_metal"), WEBBING("webbing", "frame_canvas");

        final Identifier tile;
        final Identifier frame;

        Material(String tile, String frame) {
            this.tile = sprite(tile);
            this.frame = sprite(frame);
        }
    }

    public static final Identifier SLOT = sprite("slot");
    public static final Identifier SLOT_SELECTED = sprite("slot_selected");
    public static final Identifier TAPE = sprite("tape");
    public static final Identifier MIRROR = sprite("mirror");
    public static final Identifier VIGNETTE = sprite("vignette");
    public static final Identifier TORCH = sprite("torch");
    public static final Identifier TAB = sprite("tab");
    public static final Identifier TAB_SELECTED = sprite("tab_selected");
    public static final Identifier TOOLTIP_STYLE = ZomboidCraft.id("kit");
    private static final FontDescription STENCIL = new FontDescription.Resource(ZomboidCraft.id("stencil"));
    private static final FontDescription MARKER = new FontDescription.Resource(ZomboidCraft.id("marker"));

    public static final int INK = 0xFFE6DCC0;
    public static final int INK_DIM = 0xFFA59D82;
    public static final int STENCIL_INK = 0xD8E8DEC4;
    public static final int MARKER_INK = 0xFF1D1B16;
    public static final int MARKER_RED = 0xFF7A1A10;

    private static Identifier sprite(String name) {
        return ZomboidCraft.id("inventory/" + name);
    }

    public static void panel(GuiGraphicsExtractor g, Material material, int x, int y, int w, int h) {
        // drop shadow, then the material, then the frame over it
        g.fill(x + 2, y + 4, x + w + 3, y + h + 6, 0x66000000);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, material.tile, x, y, w, h);
        g.fillGradient(x, y, x + w, y + h, 0x10FFF0C8, 0x38000000);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, material.frame, x, y, w, h);
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x - 1, y - 1, 18, 18);
    }

    public static MutableComponent stencil(String text) {
        return Component.literal(text).withStyle(s -> s.withFont(STENCIL));
    }

    public static MutableComponent marker(String text) {
        return Component.literal(text).withStyle(s -> s.withFont(MARKER));
    }

    /** A stencilled header, sprayed on: a soft dark pass under the paint. */
    public static void header(GuiGraphicsExtractor g, Font font, String text, int x, int y) {
        Component c = stencil(text.toUpperCase());
        g.text(font, c, x + 1, y + 1, 0x70000000, false);
        g.text(font, c, x, y, STENCIL_INK, false);
    }

    /** A strip of masking tape with marker writing; returns its width. Right-aligned when {@code right} is set. */
    public static int tape(GuiGraphicsExtractor g, Font font, String text, int x, int y, boolean right, int ink) {
        Component c = marker(text);
        int w = font.width(c) + 12;
        int left = right ? x - w : x;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, TAPE, left, y, w, 13);
        g.text(font, c, left + 6, y + 3, ink, false);
        return w;
    }

    /** Tiny label printed under a slot, like a stencil on a pouch. */
    public static void slotLabel(GuiGraphicsExtractor g, Font font, String text, int cx, int y) {
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(0.5f, 0.5f);
        g.centeredText(font, text.toUpperCase(), 0, 0, 0x88E6DCC0);
        g.pose().popMatrix();
    }

    private KitArt() {
    }
}
