package com.zomboidcraft.survival;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Two sticks and a strip of cloth: right-click to set a broken bone, legs first. It knits three times faster. */
public class SplintItem extends Item {
    public SplintItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (Treatment.target(Body.get(player), Treatment.Kind.SPLINT) == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && !TimedActions.busy(player)) {
            Treatment.start(serverPlayer, Treatment.Kind.SPLINT, null);
        }
        return InteractionResult.CONSUME;
    }
}
