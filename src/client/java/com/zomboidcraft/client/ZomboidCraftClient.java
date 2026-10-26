package com.zomboidcraft.client;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.combat.AimSensitivity;
import com.zomboidcraft.client.combat.CombatClient;
import com.zomboidcraft.client.combat.CombatHud;
import com.zomboidcraft.client.combat.Crosshair;
import com.zomboidcraft.client.combat.RailLights;
import com.zomboidcraft.client.combat.RifleView;
import com.zomboidcraft.client.sound.SoundSliders;
import com.zomboidcraft.client.zombie.HordeSounds;
import com.zomboidcraft.client.zombie.WalkerAddonModel;
import com.zomboidcraft.client.zombie.WalkerModel;
import com.zomboidcraft.client.zombie.WalkerRenderer;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherView;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public class ZomboidCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        com.zomboidcraft.client.hud.Hud.register();
        com.zomboidcraft.client.carry.CarryClient.register();
        com.zomboidcraft.client.inventory.FieldKitScreen.register();
        com.zomboidcraft.client.kit.KitScreen.register();
        com.zomboidcraft.client.hud.QuickWheel.register();
        com.zomboidcraft.client.map.MapStore.register();
        com.zomboidcraft.client.kit.WeaponTip.register();
        SkillsHud.register();
        // the sky eases toward the weather the server last sent
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                WeatherView.update(client.player == null ? null : client.player.getAttached(Weather.TYPE)));
        HudElementRegistry.addLast(ZomboidCraft.id("skills"), SkillsHud::render);
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ZomboidCraft.id("scope"), CombatHud::scope);
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, Crosshair::wrap);
        CombatClient.register();
        com.zomboidcraft.client.gear.NightVisionClient.register();
        com.zomboidcraft.client.gear.RadioClient.register();
        RailLights.register();
        RifleView.register();
        AimSensitivity.register();
        HordeSounds.register();
        EntityRendererRegistry.register(com.zomboidcraft.world.cars.Cars.CAR, com.zomboidcraft.client.cars.CarRenderer::new);
        SoundSliders.register();
        com.zomboidcraft.client.cars.CarClient.register();
        com.zomboidcraft.client.sound.Ambience.register();
        com.zomboidcraft.client.weather.WeatherFx.register();
        if (ZcConfig.get().look.customZombies) {
            ModelLayerRegistry.registerModelLayer(WalkerModel.LAYER, WalkerModel::createLayer);
            ModelLayerRegistry.registerModelLayer(WalkerAddonModel.LAYER, WalkerAddonModel::createLayer);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.HUSK, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE_VILLAGER, WalkerRenderer::new);
        }
    }
}
