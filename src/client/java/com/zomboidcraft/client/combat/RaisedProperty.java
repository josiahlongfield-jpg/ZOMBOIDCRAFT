package com.zomboidcraft.client.combat;

import com.mojang.serialization.MapCodec;
import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The guns' item models' "zomboidcraft:raised" condition: the gun is up, pointing where its carrier looks, rather
 * than at the low ready (HoldPoses.raised). Picks the third-person hold the model is fitted to.
 */
public record RaisedProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<RaisedProperty> MAP_CODEC = MapCodec.unit(new RaisedProperty());

    public static void register() {
        ConditionalItemModelProperties.ID_MAPPER.put(ZomboidCraft.id("raised"), MAP_CODEC);
    }

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                       ItemDisplayContext context) {
        return owner instanceof Player player && HoldPoses.raised(player);
    }

    @Override
    public MapCodec<RaisedProperty> type() {
        return MAP_CODEC;
    }
}
