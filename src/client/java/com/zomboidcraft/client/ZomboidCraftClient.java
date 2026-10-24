package com.zomboidcraft.client;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.zombie.WalkerAddonModel;
import com.zomboidcraft.client.zombie.WalkerModel;
import com.zomboidcraft.client.zombie.WalkerRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

public class ZomboidCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(ZomboidCraft.id("moodles"), MoodleHud::render);
        SkillsHud.register();
        HudElementRegistry.addLast(ZomboidCraft.id("skills"), SkillsHud::render);
        if (ZcConfig.get().look.customZombies) {
            ModelLayerRegistry.registerModelLayer(WalkerModel.LAYER, WalkerModel::createLayer);
            ModelLayerRegistry.registerModelLayer(WalkerAddonModel.LAYER, WalkerAddonModel::createLayer);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.HUSK, WalkerRenderer::new);
            EntityRendererRegistry.<Zombie>register(EntityTypes.ZOMBIE_VILLAGER, WalkerRenderer::new);
        }
    }
}
