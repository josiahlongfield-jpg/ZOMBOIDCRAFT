package com.zomboidcraft.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import com.zomboidcraft.ZomboidCraft;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Zomboid's own moodle icons (Josiah 2026-11-01: "the exact ones project zomboid has so they fit the game properly"),
 * read from the player's own copy in config/zomboidcraft/moodles: Mood_*.png / Status_*.png and _Moodles_BGsolid.png
 * from Zomboid's media/ui/Moodles/128. They never ship in the jar; without them every plate keeps our own sprite.
 * Each file is read once; the round background is tinted per level the way Zomboid's are, four bad and four good.
 */
public final class PzMoodles {
    /** Our moodle keys to Zomboid's icon files; a key with no Zomboid icon keeps its own sprite. */
    public static final Map<String, String> ICONS = Map.ofEntries(
            Map.entry("hunger", "Status_Hunger"), Map.entry("full", "Status_Hunger"), Map.entry("thirst", "Status_Thirst"),
            Map.entry("tired", "Mood_Sleepy"), Map.entry("bleeding", "Status_Bleeding"), Map.entry("injury", "Status_InjuredMinor"),
            Map.entry("fracture", "Status_InjuredMajor"), Map.entry("ill", "Mood_Ill"), Map.entry("nauseous", "Mood_Nauseous"),
            Map.entry("pain", "Mood_Pained"), Map.entry("cold", "Mood_Ill"), Map.entry("drunk", "Mood_Drunk"),
            Map.entry("panic", "Mood_Panicked"), Map.entry("bored", "Mood_Bored"), Map.entry("unhappy", "Mood_Sad"),
            Map.entry("stress", "Mood_Stressed"), Map.entry("breath", "Mood_Exhausted"),
            Map.entry("air", "Status_DifficultyBreathing"), Map.entry("load", "Status_HeavyLoad"), Map.entry("wet", "Status_Wet"),
            Map.entry("hot", "Status_TemperatureHot"), Map.entry("chilly", "Status_TemperatureLow"),
            Map.entry("windchill", "Status_Windchill"), Map.entry("zombie", "Mood_Zombified"), Map.entry("dead", "Mood_Dead"),
            Map.entry("angry", "Mood_Angry"), Map.entry("uncomfortable", "Mood_Discomfort"),
            Map.entry("smell", "Mood_NoxiousSmell"), Map.entry("cant_sprint", "Status_MovementRestricted"),
            Map.entry("hungover", "Mood_Hungover"), Map.entry("dizzy", "Mood_Dizzy"), Map.entry("scared", "Mood_Scared"),
            Map.entry("sedated", "Status_Sedated"), Map.entry("wired", "Status_Wired"), Map.entry("happy", "Mood_Happy"));

    /** Zomboid's plate colours: bad from pale yellow to red, good from pale to deep green (stage 1 to 4). */
    private static final int[] BAD = {0xFFF2E3A0, 0xFFEDB25A, 0xFFE0703A, 0xFFC43A2E};
    private static final int[] GOOD = {0xFFCFE6B0, 0xFFA8D27E, 0xFF7CB956, 0xFF4E9A36};

    private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft/moodles");
    /** What each file became: its texture, or null when it isn't there (looked for once). */
    private static final Map<String, Identifier> LOADED = new HashMap<>();
    private static final Map<String, Boolean> TRIED = new HashMap<>();

    /**
     * Draws Zomboid's plate for this moodle if the player has Zomboid's icons, and says whether it did.
     * {@code stage} is 1-4 (Zomboid's stages), {@code good} for a good moodle.
     */
    static boolean draw(GuiGraphicsExtractor g, String pzIcon, int stage, boolean good, float x, float y, int size, float alpha) {
        if (pzIcon == null || alpha <= 0.01f) return false;
        Identifier icon = texture(pzIcon, 0);
        if (icon == null) return false;
        int level = Math.max(1, Math.min(4, stage));
        Identifier bg = texture("_Moodles_BGsolid", good ? 4 + level : level);
        int ix = Math.round(x), iy = Math.round(y);
        int colour = (Math.round(alpha * 255) << 24) | 0xFFFFFF;
        if (bg != null) g.blit(RenderPipelines.GUI_TEXTURED, bg, ix, iy, 0, 0, size, size, size, size, colour);
        g.blit(RenderPipelines.GUI_TEXTURED, icon, ix, iy, 0, 0, size, size, size, size, colour);
        return true;
    }

    /** One of Zomboid's files as a texture; {@code tint} 1-4 bad and 5-8 good colours the background, 0 leaves it. */
    private static @Nullable Identifier texture(String file, int tint) {
        String key = file + "#" + tint;
        if (TRIED.containsKey(key)) return LOADED.get(key);
        TRIED.put(key, true);
        Path path = DIR.resolve(file + ".png");
        if (!Files.isRegularFile(path)) return null;
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) return null;
            int ink = tint == 0 ? 0xFFFFFFFF : tint <= 4 ? BAD[tint - 1] : GOOD[tint - 5];
            NativeImage out = new NativeImage(img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) out.setPixel(x, y, multiply(img.getRGB(x, y), ink));
            }
            Identifier id = ZomboidCraft.id("pz_moodle/" + file.toLowerCase(java.util.Locale.ROOT) + "_" + tint);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ZomboidCraft moodle " + key, out));
            LOADED.put(key, id);
            return id;
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read Zomboid moodle icon {}", path, e);
            return null;
        }
    }

    private static int multiply(int argb, int ink) {
        int a = argb >>> 24;
        int r = ((argb >> 16) & 0xFF) * ((ink >> 16) & 0xFF) / 255;
        int g = ((argb >> 8) & 0xFF) * ((ink >> 8) & 0xFF) / 255;
        int b = (argb & 0xFF) * (ink & 0xFF) / 255;
        return a << 24 | r << 16 | g << 8 | b;
    }

    private PzMoodles() {
    }
}
