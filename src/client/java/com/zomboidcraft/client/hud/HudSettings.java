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
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.commands.SharedSuggestionProvider;
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
 * Whether your figure stays on screen or tucks away while you're fine, and how much rain gathers on your face.
 */
public final class HudSettings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft-hud.json");
    /** Your figure stays up all the time (Josiah's pick); off, it shows only when hurt or changing. */
    static boolean figureAlways = true;
    /** Rain on your face: 0 off, then light, normal and heavy. */
    static float rainOnFace = 1f;
    private static final float[] RAIN_STEPS = {0f, 0.5f, 1f, 1.6f};
    private static final String[] RAIN_WORDS = {"off", "light", "normal", "heavy"};

    /**
     * Which HUD you see (Josiah 2026-11-08): Halo: Reach's for a Spartan, Tarkov's near-empty one for an operator, or
     * the old field kit. Reach's and Tarkov's art is on this computer only (config/zomboidcraft/hud); without it the
     * field kit shows. Spartan until character creation picks it for you.
     */
    public enum Style {
        SPARTAN, OPERATOR, CLASSIC;

        public String word() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static Style style = Style.SPARTAN;

    /** The HUD drawn now: the chosen one when its art is here, else the field kit. */
    public static Style style() {
        return switch (style) {
            case SPARTAN -> ReachHud.ready() ? Style.SPARTAN : Style.CLASSIC;
            case OPERATOR -> TarkovHud.ready() ? Style.OPERATOR : Style.CLASSIC;
            case CLASSIC -> Style.CLASSIC;
        };
    }

    /** The chosen HUD, whether or not its art is here. */
    public static Style chosen() {
        return style;
    }

    public static void setStyle(Style s) {
        style = s;
        HudKit.reload();
        save();
    }

    public static void register() {
        load();
        // /zchud spartan|operator|classic|reload: its own word, since a client-side /zc would stand in front of the
        // server's /zc commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("zchud")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(Component.literal(status()));
                            return 1;
                        })
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("style", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(List.of("spartan", "operator", "classic", "reload"), b))
                                .executes(ctx -> {
                                    String word = StringArgumentType.getString(ctx, "style").toLowerCase(java.util.Locale.ROOT);
                                    if (word.equals("reload")) {
                                        HudKit.reload();
                                    } else {
                                        Style pick = null;
                                        for (Style st : Style.values()) if (st.word().equals(word)) pick = st;
                                        if (pick == null) {
                                            ctx.getSource().sendError(Component.literal("Pick spartan, operator, classic or reload."));
                                            return 0;
                                        }
                                        setStyle(pick);
                                    }
                                    ctx.getSource().sendFeedback(Component.literal(status()));
                                    return 1;
                                }))));
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
                    Button rain = Button.builder(rainLabel(), b -> {
                        rainOnFace = RAIN_STEPS[(rainStep() + 1) % RAIN_STEPS.length];
                        b.setMessage(rainLabel());
                        save();
                    }).width(150).build();
                    list.addSmall(List.of(figure, rain));
                    Button hud = Button.builder(styleLabel(), b -> {
                        setStyle(Style.values()[(style.ordinal() + 1) % Style.values().length]);
                        b.setMessage(styleLabel());
                    }).width(150).build();
                    list.addSmall(List.of(hud));
                    break;
                }
            }
        });
    }

    private static Component figureLabel() {
        return Component.translatable("options.zomboidcraft.hud.figure",
                Component.translatable(figureAlways ? "options.zomboidcraft.hud.figure.always" : "options.zomboidcraft.hud.figure.fades"));
    }

    /** What /zchud says: the HUD picked, and whether its art was found. */
    private static String status() {
        String art = switch (style) {
            case SPARTAN -> ReachHud.ready() ? "Reach art found" : "Reach art not found in config/zomboidcraft/hud/reach, showing the field kit";
            case OPERATOR -> TarkovHud.ready() ? "Tarkov art found" : "Tarkov art not found in config/zomboidcraft/hud/tarkov, showing the field kit";
            case CLASSIC -> "field kit";
        };
        return "HUD: " + style.word() + " (" + art + ")";
    }

    private static Component styleLabel() {
        return Component.translatable("options.zomboidcraft.hud.style",
                Component.translatable("options.zomboidcraft.hud.style." + style.word()));
    }

    private static int rainStep() {
        int best = 0;
        for (int i = 1; i < RAIN_STEPS.length; i++) {
            if (Math.abs(RAIN_STEPS[i] - rainOnFace) < Math.abs(RAIN_STEPS[best] - rainOnFace)) best = i;
        }
        return best;
    }

    private static Component rainLabel() {
        return Component.translatable("options.zomboidcraft.hud.rain",
                Component.translatable("options.zomboidcraft.hud.rain." + RAIN_WORDS[rainStep()]));
    }

    private static void load() {
        if (!Files.exists(FILE)) return;
        try (Reader reader = Files.newBufferedReader(FILE)) {
            JsonObject saved = GSON.fromJson(reader, JsonObject.class);
            if (saved != null && saved.has("figureAlways")) figureAlways = saved.get("figureAlways").getAsBoolean();
            if (saved != null && saved.has("rainOnFace")) rainOnFace = Math.max(0, saved.get("rainOnFace").getAsFloat());
            if (saved != null && saved.has("style")) {
                for (Style s : Style.values()) if (s.word().equals(saved.get("style").getAsString())) style = s;
            }
        } catch (IOException | RuntimeException e) {
            ZomboidCraft.LOGGER.warn("Couldn't read {}, using the default HUD settings", FILE, e);
        }
    }

    static void save() {
        JsonObject out = new JsonObject();
        out.addProperty("figureAlways", figureAlways);
        out.addProperty("rainOnFace", rainOnFace);
        out.addProperty("style", style.word());
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(out, writer);
        } catch (IOException e) {
            ZomboidCraft.LOGGER.warn("Couldn't save {}", FILE, e);
        }
    }

    private HudSettings() {
    }
}
