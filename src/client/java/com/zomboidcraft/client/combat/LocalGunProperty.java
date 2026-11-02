package com.zomboidcraft.client.combat;

import com.mojang.serialization.MapCodec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.LocalGuns;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The "zomboidcraft:local_gun" condition: the stack is a gun kept on this computer (LocalGuns) and its mesh is here.
 * The block guns (lever action, rimfire) have no mesh of ours to swap, so their item models ask this first and draw
 * the local gun's mesh in place of the block pieces; whoever hasn't got the file sees the block gun.
 */
public record LocalGunProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<LocalGunProperty> MAP_CODEC = MapCodec.unit(new LocalGunProperty());

    public static void register() {
        ConditionalItemModelProperties.ID_MAPPER.put(ZomboidCraft.id("local_gun"), MAP_CODEC);
    }

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                       ItemDisplayContext context) {
        String local = stack.get(LocalGuns.ID);
        return local != null && MeshGuns.hasLocal(local);
    }

    @Override
    public MapCodec<LocalGunProperty> type() {
        return MAP_CODEC;
    }
}
