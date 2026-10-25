package com.zomboidcraft.client.sound;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

/** The interface's own quiet foley (sounds.json ui.*), played to you alone with a touch of pitch drift. */
public final class UiSounds {
    public static final SoundEvent TAB = event("ui.tab");
    public static final SoundEvent CLICK = event("ui.click");
    public static final SoundEvent ITEM_MOVE = event("ui.item_move");
    public static final SoundEvent WEAR = event("ui.wear");
    public static final SoundEvent MOODLE = event("ui.moodle");

    private static final RandomSource RANDOM = RandomSource.create();

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(ZomboidCraft.id(name));
    }

    public static void play(SoundEvent event) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 0.95f + RANDOM.nextFloat() * 0.1f, 1.0f));
    }

    private UiSounds() {
    }
}
