package com.zomboidcraft.combat.throwing;

import com.zomboidcraft.ZcConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Something to throw: hold use to wind up (the longer, the farther, up to a full throw), let go to throw it. A
 * grenade's pin comes out as the wind-up starts: hold on past its fuse and it goes off
 * in your hand, and putting it away mid wind-up drops it at your feet, still live (Throwing.tick).
 */
public class ThrowableItem extends Item {
    private final ThrowKind kind;

    public ThrowableItem(ThrowKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public ThrowKind kind() {
        return kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!ZcConfig.get().throwing.enabled) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(hand);
        if (player instanceof ServerPlayer server) {
            Throwing.windUp(server, kind);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (kind.fused && entity instanceof ServerPlayer player && Throwing.fuseLeft(player) <= 0) {
            // held too long: it goes off in the hand
            Throwing.release(player, stack, 0f, true);
            player.stopUsingItem();
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (!(entity instanceof ServerPlayer player)) {
            return true;
        }
        int held = getUseDuration(stack, entity) - remaining;
        float power = Math.min(1f, (float) held / Math.max(1, ZcConfig.get().throwing.fullDrawTicks));
        Throwing.release(player, stack, power, false);
        return true;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.TRIDENT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** For the arc preview: how far into a full wind-up the throw is. */
    public static float power(LivingEntity entity) {
        int held = entity.getTicksUsingItem();
        return Math.min(1f, (float) held / Math.max(1, ZcConfig.get().throwing.fullDrawTicks));
    }

    /** Speed it leaves the hand at for a wind-up of this much (blocks a tick). */
    public static double speed(ThrowKind kind, float power) {
        return ZcConfig.get().throwing.throwSpeed * kind.speed * (0.35 + 0.65 * power);
    }
}
