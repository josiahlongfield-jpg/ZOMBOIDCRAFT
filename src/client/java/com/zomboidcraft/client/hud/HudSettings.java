package com.zomboidcraft.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.zomboidcraft.ZomboidCraft;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

/**
 * The HUD's own choices, under Options > Video Settings and kept on this computer in config/zomboidcraft-hud.json.
 * For now one: whether your figure stays on screen or tucks away while you're fine.
 */
public final class HudSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft-hud.json");
    /** Your figure stays up all the time (Josiah's pick); off, it shows only when hurt or changing. */
    static boolean figureAlways = true;

    public static void register() {
        load();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof VideoSettingsScreen)) return;
            for (AbstractWidget widget : Screens.getWidgets(screen)) {
                if (widget instanceof OptionsList list) {
                    list.addHeader(Component.translatable("options.zomboidcraft.hud"));
                    Button figure = Button.builder(figureLabel(), b -> {
                        figureAlways = !figureAlways;
                        b.setMessage(figureLabel());
                        save();
                    }).width(150).build();
                    list.addSmall(List.of(figure));
                    break;
                }
            }
        });
    }

    private static Component figureLabel() {
        return Component.translatable("options.zomboidcraft.hud.figure",
                Component.translatable(figureAlways ? "options.zomboidcraft.hud.figure.always" : "options.zomboidcraft.hud.figure.fades"));
    }

    private static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject saved = GSON.fromJson(reader, JsonObject.class);
            if (saved != null && saved.has("figureAlways")) figureAlways = saved.get("figureAlways").getAsBoolean();
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Couldn't read {}, using the default HUD settings", FILE, e);
        }
    }

    private static void save() {
        JsonObject out = new JsonObject();
        out.addProperty("figureAlways", figureAlways);
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(out, writer);
        } catch (IOException e) {
            ZomboidCraft.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    private HudSettings() {
    }
}
