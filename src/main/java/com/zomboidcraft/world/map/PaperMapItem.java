package com.zomboidcraft.world.map;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A street map or road atlas: right-click to read it, and its area fills in on your map (M). It isn't used up. */
public class PaperMapItem extends Item {
    private final PaperMaps.Kind kind;

    public PaperMapItem(PaperMaps.Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) return InteractionResult.FAIL;
            player.getCooldowns().addCooldown(player.getItemInHand(hand), 60);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
            if (PaperMaps.read(serverPlayer, kind)) {
                serverPlayer.sendOverlayMessage(Component.literal("You study the map. It's on your map now (M)."));
            } else {
                serverPlayer.sendOverlayMessage(Component.literal("This map is of somewhere else.").withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
