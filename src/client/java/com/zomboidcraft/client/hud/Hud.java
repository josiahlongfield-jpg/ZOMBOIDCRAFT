package com.zomboidcraft.client.hud;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.inventory.KitArt;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.joml.Matrix3x2fStack;

/**
 * The field-kit HUD: Minecraft's hearts, hunger, armour, air, XP and hotbar make way for a belt of bare squares, a
 * small figure of yourself, enamel moodle plates, a wristwatch and the weapon in your hands. Each piece stays out of
 * sight until it has something to say, then fades back out; danger keeps it up, and holding Left Alt shows the lot.
 */
public final class Hud {
    public static final int BONE = 0xFFE9E1CB;
    public static final int BONE_DIM = 0x9EE9E1CB;
    public static final int BONE_FAINT = 0x47E9E1CB;
    public static final int AMBER = 0xFFE0A85A;
    public static final int RED = 0xFFD0482E;

    static final FontDescription TEXT = new FontDescription.Resource(ZomboidCraft.id("hud"));
    static final FontDescription BIG = new FontDescription.Resource(ZomboidCraft.id("hud_big"));

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZomboidCraft.id("hud"));
    /** Hold to see every piece of the HUD at once. */
    public static final KeyMapping PEEK = new KeyMapping("key.zomboidcraft.peek", InputConstants.KEY_LALT, CATEGORY);

    private static long lastFrame;
    /** Seconds since the last HUD frame, for fades. */
    static float dt;
    static boolean peek;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(PEEK);
        HudSettings.register();
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) helper.register(new InjuryGlowLayer(avatar));
        });
        // Minecraft's own pieces step aside while the field kit is up, and come back in creative or with it off
        HudElementRegistry.replaceElement(VanillaHudElements.HOTBAR, vanilla -> (g, delta) -> {
            if (active()) BeltHud.render(g, delta);
            else vanilla.extractRenderState(g, delta);
        });
        hide(VanillaHudElements.HEALTH_BAR);
        hide(VanillaHudElements.ARMOR_BAR);
        hide(VanillaHudElements.FOOD_BAR);
        hide(VanillaHudElements.AIR_BAR);
        hide(VanillaHudElements.EXPERIENCE_LEVEL);
        hide(VanillaHudElements.HELD_ITEM_TOOLTIP);
        hide(VanillaHudElements.MOB_EFFECTS);
        // the XP bar goes, but a horse's jump bar stays
        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR, vanilla -> (g, delta) -> {
            if (!active() || riding()) vanilla.extractRenderState(g, delta);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.INFO_BAR_FOREGROUND, vanilla -> (g, delta) -> {
            if (!active() || riding()) vanilla.extractRenderState(g, delta);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, ZomboidCraft.id("field_kit_body"), Hud::under);
        HudElementRegistry.addLast(ZomboidCraft.id("field_kit_hud"), Hud::over);
    }

    private static void hide(Identifier element) {
        HudElementRegistry.replaceElement(element, vanilla -> (g, delta) -> {
            if (!active()) vanilla.extractRenderState(g, delta);
        });
    }

    private static boolean riding() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getVehicle() != null;
    }

    /** True while the field kit replaces Minecraft's HUD: on in settings, and playing survival or adventure. */
    public static boolean active() {
        LocalPlayer player = Minecraft.getInstance().player;
        return ZcConfig.get().look.fieldKitHud && player != null && !player.isCreative() && !player.isSpectator();
    }

    /** The blood at the screen's edge and your figure, drawn before the rest. */
    private static void under(GuiGraphicsExtractor g, DeltaTracker delta) {
        long now = System.nanoTime();
        dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        peek = PEEK.isDown();
        if (!active()) return;
        BodyHud.vignette(g, delta);
        BodyHud.render(g, delta);
    }

    private static void over(GuiGraphicsExtractor g, DeltaTracker delta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) return;
        int top = WatchHud.render(g);
        if (!player.isCreative()) MoodleHud.render(g, top);
        WeaponHud.render(g, delta);
        SkillTape.render(g);
        GrabHud.render(g);
        if (peek && active()) {
            Font font = Minecraft.getInstance().font;
            Component c = KitArt.stencil("CHECKING YOUR KIT");
            g.text(font, c, (g.guiWidth() - font.width(c)) / 2, 10, 0xC8E8DEC4, true);
        }
    }

    // ------------------------------------------------------------------ drawing helpers

    static Component text(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(TEXT));
    }

    static Component big(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(BIG));
    }

    /** Width of capitals drawn with {@link #caps}. */
    static float capsWidth(Font font, String s, float spacing, float scale) {
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += font.width(text(s.substring(i, i + 1))) + spacing;
        return (w - spacing) * scale;
    }

    /** Letter-spaced capitals, the way the stencilled labels read. Returns the width drawn. */
    static float caps(GuiGraphicsExtractor g, Font font, String s, float x, float y, int color, float spacing, float scale) {
        s = s.toUpperCase();
        if (ARGB.alpha(color) < 5) return capsWidth(font, s, spacing, scale);
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        float cx = 0;
        for (int i = 0; i < s.length(); i++) {
            Component c = text(s.substring(i, i + 1));
            g.text(font, c, Math.round(cx), 0, color, true);
            cx += font.width(c) + spacing;
        }
        pose.popMatrix();
        return (cx - spacing) * scale;
    }

    /** A sprite at a fractional position and size. */
    static void sprite(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h, int color) {
        if (ARGB.alpha(color) < 3) return;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(w / 64f, h / 64f);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, id, 0, 0, 64, 64, color);
        pose.popMatrix();
    }

    static Identifier id(String name) {
        return ZomboidCraft.id("hud/" + name);
    }

    static int alpha(int color, float a) {
        return ARGB.multiplyAlpha(color, Math.max(0f, Math.min(1f, a)));
    }

    /** A piece's visibility: in fast (0.22 s), out slow (1.1 s), like a glance. */
    static final class Fade {
        float a;

        float update(boolean on) {
            float rate = on ? 1 / 0.22f : 1 / 1.1f;
            a = on ? Math.min(1, a + dt * rate) : Math.max(0, a - dt * rate);
            return a;
        }
    }

    private Hud() {
    }
}
