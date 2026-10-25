package com.zomboidcraft.client.sound;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;

/** Adds a ZomboidCraft section, one slider per sound group, to the bottom of Options > Music & Sounds. */
public final class SoundSliders {
    public static void register() {
        SoundVolumes.load();
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof SoundOptionsScreen)) {
                return;
            }
            for (AbstractWidget widget : Screens.getWidgets(screen)) {
                if (widget instanceof OptionsList list) {
                    list.addHeader(Component.translatable("options.zomboidcraft.sounds"));
                    List<AbstractWidget> sliders = new ArrayList<>();
                    for (SoundVolumes.Group group : SoundVolumes.Group.values()) {
                        sliders.add(new Slider(group));
                    }
                    sliders.add(new MuffleSlider());
                    list.addSmall(sliders);
                    break;
                }
            }
            ScreenEvents.remove(screen).register(closed -> SoundVolumes.save());
        });
    }

    private static final class Slider extends AbstractSliderButton {
        private final SoundVolumes.Group group;

        Slider(SoundVolumes.Group group) {
            super(0, 0, 150, 20, Component.empty(), group.volume());
            this.group = group;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(value <= 0 ? Component.translatable("options.generic_value", group.label(), Component.translatable("options.off"))
                    : Component.translatable("options.percent_value", group.label(), (int) Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            SoundVolumes.set(group, value);
        }
    }

    /** How much walls and floors muffle what's behind them. */
    private static final class MuffleSlider extends AbstractSliderButton {
        MuffleSlider() {
            super(0, 0, 150, 20, Component.empty(), SoundVolumes.muffle());
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            Component label = Component.translatable("options.zomboidcraft.sound." + SoundVolumes.MUFFLE_KEY);
            setMessage(value <= 0 ? Component.translatable("options.generic_value", label, Component.translatable("options.off"))
                    : Component.translatable("options.percent_value", label, (int) Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            SoundVolumes.setMuffle(value);
        }
    }

    private SoundSliders() {
    }
}
