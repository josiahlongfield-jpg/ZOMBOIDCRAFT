package com.zomboidcraft.client;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.client.combat.CombatHud;
import com.zomboidcraft.client.zombie.HordeSounds;
import com.zomboidcraft.client.zombie.WalkerAddonModel;
import com.zomboidcraft.client.zombie.WalkerModel;
import com.zomboidcraft.client.zombie.WalkerRenderer;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public class ZomboidCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(ZomboidCraft.id("moodles"), MoodleHud::render);
        SkillsHud.register();
        // the sky eases toward the weather the server last sent
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                WeatherView.update(client.player == null ? null : client.player.getAttached(Weather.TYPE)));
        HudElementRegistry.addLast(ZomboidCraft.id("skills"), SkillsHud::render);
        HudElementRegistry.addLast(ZomboidCraft.id("combat"), CombatHud::render);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ZomboidCraft.id("scope"), CombatHud::scope);
        CombatClient.register();
        HordeSounds.register();
        EntityRendererRegistry.register(com.zomboidcraft.world.cars.Cars.CAR, com.zomboidcraft.client.cars.CarRenderer::new);
        if (ZcConfig.get().look.customZombies) {
            ModelLayerRegistry.registerModelLayer(WalkerModel.LAYER, WalkerModel::createLayer);
            ModelLayerRegistry.registerModelLayer(WalkerAddonModel.LAYER, WalkerAddonModel::createLayer);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.HUSK, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE_VILLAGER, WalkerRenderer::new);
        }
    }
}
