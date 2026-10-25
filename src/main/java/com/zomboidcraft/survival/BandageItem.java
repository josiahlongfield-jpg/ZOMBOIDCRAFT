package com.zomboidcraft.survival;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click to bind your worst open wound: bleeding ones first, then the deepest. It takes a few seconds. */
public class BandageItem extends Item {
    public BandageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (Treatment.target(Body.get(player), Treatment.Kind.BANDAGE) == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && !TimedActions.busy(player)) {
            Treatment.start(serverPlayer, Treatment.Kind.BANDAGE, null);
        }
        // no swing: a swing would stop the bandaging
        return InteractionResult.CONSUME;
    }
}
