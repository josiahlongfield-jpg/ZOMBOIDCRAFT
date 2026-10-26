package com.zomboidcraft.client.gear;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.combat.CombatNet;
import com.zomboidcraft.gear.Headgear;
import com.zomboidcraft.gear.Radios;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * The radio's talk key. Holding it tells the server you're keyed up (it plays the click on everyone's radios and sends
 * your voice down the channel), and, with the voice chat mod installed, opens your microphone while you hold it, as if
 * you held the voice mod's own push-to-talk key (mixin/client/VoicePttMixin).
 *
 * The voice mod comes with M, N, H and V bound, which ZomboidCraft already uses (map, night vision, horn, shove). The
 * first time the game starts with it installed, any of those still on their clashing default are moved out of the
 * way: its menu to Y, mute to U, and "disable voice chat" and "hide icons" left unbound. Done once; a key the player
 * sets after that is theirs.
 */
public final class RadioClient {
    public static final KeyMapping TALK = new KeyMapping("key.zomboidcraft.radio", InputConstants.KEY_B, CombatClient.CATEGORY);

    /** Whether the talk key is held with a radio to talk on: the voice mod's microphone stays open while it is. */
    public static volatile boolean transmitting;

    private static final Path KEYS_MOVED = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft-voicekeys.txt");
    private static boolean down;
    private static boolean keysChecked;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(TALK);
        ClientTickEvents.END_CLIENT_TICK.register(RadioClient::tick);
    }

    /** Whether the local player has a comms headset on, which dulls gunshots (SoundVolumes). */
    public static boolean wearingHeadset() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Headgear.headset(player.getItemBySlot(EquipmentSlot.HEAD)) != null;
    }

    private static void tick(Minecraft mc) {
        if (!keysChecked) {
            keysChecked = true;
            moveVoiceKeys(mc);
        }
        LocalPlayer player = mc.player;
        boolean now = player != null && mc.gui.screen() == null && TALK.isDown();
        if (now != down && player != null) {
            ClientPlayNetworking.send(new CombatNet.Action(now ? CombatNet.Action.RADIO_DOWN : CombatNet.Action.RADIO_UP));
        }
        down = now;
        transmitting = now && !Radios.talkRadio(player).isEmpty();
    }

    /** Moves the voice mod's keys off ours, once (see the class comment). */
    private static void moveVoiceKeys(Minecraft mc) {
        if (!FabricLoader.getInstance().isModLoaded("voicechat") || Files.exists(KEYS_MOVED) || mc.options == null) {
            return;
        }
        List<String> ours = new ArrayList<>();
        for (KeyMapping key : mc.options.keyMappings) {
            if (key.getName().startsWith("key.zomboidcraft.")) {
                ours.add(key.saveString());
            }
        }
        List<String> moved = new ArrayList<>();
        for (KeyMapping key : mc.options.keyMappings) {
            InputConstants.Key to = switch (key.getName()) {
                case "key.voice_chat" -> InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_Y);
                case "key.mute_microphone" -> InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_U);
                case "key.disable_voice_chat", "key.hide_icons" -> InputConstants.UNKNOWN;
                default -> null;
            };
            if (to == null || !key.isDefault() || !ours.contains(key.saveString())) {
                continue;
            }
            key.setKey(to);
            moved.add(Component.translatable(key.getName()).getString() + (to == InputConstants.UNKNOWN ? " unbound"
                    : " to " + to.getDisplayName().getString()));
        }
        try {
            Files.writeString(KEYS_MOVED, "ZomboidCraft moved the voice chat mod's keys that clashed with its own, once: "
                    + (moved.isEmpty() ? "none needed moving" : String.join(", ", moved))
                    + ".\nDelete this file to have it check again.\n");
        } catch (IOException e) {
            ZomboidCraft.LOGGER.warn("Couldn't write {}", KEYS_MOVED, e);
        }
        if (moved.isEmpty()) {
            return;
        }
        KeyMapping.resetMapping();
        mc.options.save();
        ZomboidCraft.LOGGER.info("Moved voice chat keys off ZomboidCraft's: {}", moved);
    }

    private RadioClient() {
    }
}
