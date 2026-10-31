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
        // medical supports drawn on the body, from the synced worn-medical mask
        com.zomboidcraft.client.clothing.ClothingLayer.medicalMask = com.zomboidcraft.survival.WornMedical::mask;
        com.zomboidcraft.client.hud.Hud.register();
        com.zomboidcraft.client.carry.CarryClient.register();
        com.zomboidcraft.client.kit.KitScreen.register();
        com.zomboidcraft.client.kit.FilesClient.register();
        com.zomboidcraft.client.camera.BreachTremor.register();
        com.zomboidcraft.client.kit.SpawnScreen.register();
        com.zomboidcraft.client.hud.QuickWheel.register();
        com.zomboidcraft.client.map.MapStore.register();
        com.zomboidcraft.client.map.CountyAtlas.register();
        com.zomboidcraft.client.map.MapPlayers.register();
        com.zomboidcraft.client.kit.WeaponTip.register();
        SkillsHud.register();
        com.zomboidcraft.client.survival.DownedClient.register();
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
        com.zomboidcraft.client.gear.HandLights.register();
        com.zomboidcraft.client.building.Placer.register();
        com.zomboidcraft.client.zombie.body.Gibs.register();
        com.zomboidcraft.client.zombie.body.CrowdFx.register();
        com.zomboidcraft.client.zombie.body.ZDetail.register();
        com.zomboidcraft.client.combat.ImpactFx.register();
        com.zomboidcraft.client.camera.ShoulderCam.register();
        RifleView.register();
        AimSensitivity.register();
        HordeSounds.register();
        EntityRendererRegistry.register(com.zomboidcraft.world.cars.Cars.CAR, com.zomboidcraft.client.cars.CarRenderer::new);
        EntityRendererRegistry.register(com.zomboidcraft.world.cars.Cars.DEBRIS, com.zomboidcraft.client.cars.DebrisRenderer::new);
        EntityRendererRegistry.register(com.zomboidcraft.world.cars.Cars.HOIST, com.zomboidcraft.client.cars.HoistRenderer::new);
        EntityRendererRegistry.register(com.zomboidcraft.combat.throwing.Throwing.THROWN, com.zomboidcraft.client.throwing.ThrownRenderer::new);
        com.zomboidcraft.client.throwing.ThrowEffects.register();
        EntityRendererRegistry.register(com.zomboidcraft.zombie.Corpses.CORPSE, com.zomboidcraft.client.zombie.body.CorpseRenderer::new);
        com.zomboidcraft.client.creature.HarvestmanClient.register();
        com.zomboidcraft.client.npc.NpcRenderer.register();
        com.zomboidcraft.client.zombie.body.ZBodyAnimator.register();
        SoundSliders.register();
        com.zomboidcraft.client.interact.Interactables.register();
        com.zomboidcraft.client.hud.EnemyBars.register();
        com.zomboidcraft.client.cars.CarClient.register();
        com.zomboidcraft.client.trains.TrainClient.register();
        com.zomboidcraft.client.ferry.FerryClient.register();
        com.zomboidcraft.client.lights.LightClient.register();
        com.zomboidcraft.client.lifts.LiftClient.register();
        com.zomboidcraft.client.cars.FlightClient.register();
        com.zomboidcraft.client.sound.Ambience.register();
        com.zomboidcraft.client.sound.WorldEventSound.register();
        com.zomboidcraft.client.sound.Score.register();
        com.zomboidcraft.client.weather.WeatherFx.register();
        com.zomboidcraft.client.sandbox.SandboxClient.register();
        com.zomboidcraft.client.kit.SandboxScreen.register();
        // last, so it can wrap every HUD piece registered above
        com.zomboidcraft.client.camera.CinematicView.register();
        if (ZcConfig.get().look.customZombies) {
            ModelLayerRegistry.registerModelLayer(WalkerModel.LAYER, WalkerModel::createLayer);
            ModelLayerRegistry.registerModelLayer(WalkerAddonModel.LAYER, WalkerAddonModel::createLayer);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.HUSK, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE_VILLAGER, WalkerRenderer::new);
        }
    }
}
