package com.zomboidcraft.clothing;

import com.zomboidcraft.carry.GearContainer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A piece of clothing. Ones for the armour slots put themselves on the vanilla way; the rest (glasses, masks, scarves,
 * shirts, gloves, vests) go into their gear store layer here, swapping with what was there.
 */
public class ClothingItem extends Item {
    public ClothingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        Garment garment = Clothes.of(held);
        if (garment == null || garment.slot().armour != null) {
            return super.use(level, player, hand);
        }
        if (!level.isClientSide()) {
            GearContainer gear = new GearContainer(player);
            int index = garment.slot().gear;
            ItemStack was = gear.getItem(index);
            gear.setItem(index, held.copyWithCount(1));
            held.shrink(1);
            if (!was.isEmpty() && !player.getInventory().add(was)) {
                player.drop(was, false);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(),
                    SoundSource.PLAYERS, 1f, 1f);
        }
        return InteractionResult.SUCCESS;
    }
}
