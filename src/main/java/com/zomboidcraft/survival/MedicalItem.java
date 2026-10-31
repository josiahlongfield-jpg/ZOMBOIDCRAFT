package com.zomboidcraft.survival;

import com.zomboidcraft.loot.items.LootItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A medical roster item: using it (or letting go over it on the quick-use wheel) starts what MedSpecs says it
 * does, on the part that needs it most. Nothing is used up until the bar fills.
 */
public class MedicalItem extends LootItem {
    private final String id;

    public MedicalItem(Properties properties, String id) {
        super(properties);
        this.id = id;
    }

    public String id() {
        return id;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (MedSpecs.spec(id).kind() == MedSpecs.Kind.PASSIVE) {
            if (player instanceof ServerPlayer sp) MedSpecs.use(sp, id);
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer sp && !TimedActions.busy(player)) {
            MedSpecs.use(sp, id);
        }
        // no swing: a swing would stop the treatment
        return InteractionResult.CONSUME;
    }
}
