package com.zomboidcraft.client;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

public class ZomboidCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(ZomboidCraft.id("moodles"), MoodleHud::render);
    }
}
