package com.zomboidcraft.combat;

import com.zomboidcraft.ModItems;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A rifle magazine. Like Zomboid, rounds go in one at a time: hold right-click with loose rounds
 * in your inventory to load it; sneak and hold right-click to thumb the rounds back out.
 */
public class MagazineItem extends Item {
    private static final int TICKS_PER_ROUND = 6;

    public MagazineItem(Properties properties) {
        super(properties);
    }

    public static int rounds(ItemStack stack) {
        return stack.getOrDefault(GunState.MAG_ROUNDS, 0);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean unloading = player.isShiftKeyDown();
        if (unloading ? rounds(stack) == 0 : rounds(stack) >= RifleItem.capacity() || countAmmo(player) == 0) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.literal(unloading ? "The magazine is empty."
                        : rounds(stack) >= RifleItem.capacity() ? "The magazine is full." : "You have no 5.56mm rounds.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer player) || (getUseDuration(stack, entity) - remaining) % TICKS_PER_ROUND != 0
                || getUseDuration(stack, entity) == remaining) {
            return;
        }
        int rounds = rounds(stack);
        if (player.isShiftKeyDown()) {
            if (rounds == 0) {
                player.stopUsingItem();
                return;
            }
            stack.set(GunState.MAG_ROUNDS, rounds - 1);
            ItemStack round = new ItemStack(ModItems.RIFLE_AMMO);
            if (!player.getInventory().add(round)) {
                player.spawnAtLocation((ServerLevel) level, round);
            }
        } else {
            if (rounds >= RifleItem.capacity() || !takeAmmo(player, 1)) {
                player.stopUsingItem();
                return;
            }
            stack.set(GunState.MAG_ROUNDS, rounds + 1);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.RIFLE_LOAD_ROUND, SoundSource.PLAYERS,
                0.5f, 0.9f + player.getRandom().nextFloat() * 0.2f);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * rounds(stack) / RifleItem.capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xE0B040;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Rounds: " + rounds(stack) + "/" + RifleItem.capacity()).withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Hold right-click to load, sneak to unload").withStyle(ChatFormatting.DARK_GRAY));
    }

    static int countAmmo(Player player) {
        if (player.hasInfiniteMaterials()) {
            return Integer.MAX_VALUE;
        }
        int n = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ModItems.RIFLE_AMMO)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    static boolean takeAmmo(Player player, int amount) {
        if (player.hasInfiniteMaterials()) {
            return true;
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.RIFLE_AMMO)) {
                int take = Math.min(amount, s.getCount());
                s.shrink(take);
                amount -= take;
            }
        }
        return amount == 0;
    }
}
