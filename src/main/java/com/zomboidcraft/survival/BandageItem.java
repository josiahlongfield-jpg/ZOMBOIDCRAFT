package com.zomboidcraft.survival;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click to bind one bleeding wound. */
public class BandageItem extends Item {
    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        SurvivalStats stats = SurvivalStats.get(player);
        if (stats.wounds() == 0) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel) {
            SurvivalStats.set(player, stats.withWounds(stats.wounds() - 1));
            player.getItemInHand(hand).consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1f, 0.8f);
        }
        return InteractionResult.SUCCESS;
    }
}
