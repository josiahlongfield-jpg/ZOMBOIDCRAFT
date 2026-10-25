package com.zomboidcraft.client.combat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.MouseSettingsScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Mouse sensitivity of its own while aiming down a gun's sights, and another through the 4x scope, set on the same
 * scale as the game's own Sensitivity slider (Options > Controls > Mouse Settings). Kept on this computer in
 * config/zomboidcraft-aim.json.
 */
public final class AimSensitivity {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft-aim.json");
    /** 0 to 1 like the game's slider, where 0.5 shows as 100%. */
    private static double sights = 0.45, scope = 0.3;

    public static void register() {
        load();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof MouseSettingsScreen)) {
                return;
            }
            for (AbstractWidget widget : Screens.getWidgets(screen)) {
                if (widget instanceof OptionsList list) {
                    list.addHeader(Component.translatable("options.zomboidcraft.aim"));
                    List<AbstractWidget> sliders = new ArrayList<>();
                    sliders.add(new Slider("options.zomboidcraft.aim.sights", false));
                    sliders.add(new Slider("options.zomboidcraft.aim.scope", true));
                    list.addSmall(sliders);
                    break;
                }
            }
            ScreenEvents.remove(screen).register(closed -> save());
        });
    }

    /** What to multiply the mouse's turning by right now: the aiming sensitivity over the normal one. */
    public static double factor() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !player.isUsingItem() || !player.getUseItem().is(ModItems.ASSAULT_RIFLE)
                || CombatClient.reloading(player.getUseItem())) {
            return 1;
        }
        double look = speed(mc.options.sensitivity().get());
        return look <= 0 ? 1 : speed(Attachments.scoped(player.getUseItem()) ? scope : sights) / look;
    }

    /** How the game turns a Sensitivity slider value into turning speed. */
    private static double speed(double slider) {
        double d = slider * 0.6 + 0.2;
        return d * d * d * 8;
    }

    private static void load() {
        if (!Files.exists(FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(FILE)) {
            Map<String, Double> saved = GSON.fromJson(reader, new TypeToken<Map<String, Double>>() {}.getType());
            if (saved != null) {
                sights = clamp(saved.getOrDefault("sights", sights));
                scope = clamp(saved.getOrDefault("scope", scope));
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Couldn't read {}, using the default aim sensitivity", FILE, e);
        }
    }

    private static void save() {
        Map<String, Double> out = new LinkedHashMap<>();
        out.put("sights", Math.round(sights * 1000) / 1000.0);
        out.put("scope", Math.round(scope * 1000) / 1000.0);
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(out, writer);
        } catch (IOException e) {
            ZomboidCraft.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static final class Slider extends AbstractSliderButton {
        private final String key;
        private final boolean forScope;

        Slider(String key, boolean forScope) {
            super(0, 0, 150, 20, Component.empty(), forScope ? scope : sights);
            this.key = key;
            this.forScope = forScope;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("options.percent_value", Component.translatable(key), (int) Math.round(value * 200)));
        }

        @Override
        protected void applyValue() {
            if (forScope) {
                scope = clamp(value);
            } else {
                sights = clamp(value);
            }
        }
    }

    private AimSensitivity() {
    }
}
