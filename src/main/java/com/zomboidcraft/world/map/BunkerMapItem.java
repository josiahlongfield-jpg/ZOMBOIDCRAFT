package com.zomboidcraft.world.map;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A marked map: someone's map with a red ring drawn round a hidden bunker and a note pencilled beside
 * it. Rare: soldiers carry them, and they turn up in the containers of bases and the naval wharf. Read
 * it (right-click) and the ring goes on your map (M); the bunker it leads to is settled the first time
 * anyone reads it, and it isn't used up.
 */
public class BunkerMapItem extends Item {
    /** "kind,state,x,z,ringX,ringZ,radius": the bunker this map leads to, once it has been read. */
    public static final DataComponentType<String> TARGET = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("bunker_map"), DataComponentType.<String>builder()
                    .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    public BunkerMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.getCooldowns().isOnCooldown(player.getItemInHand(hand))) return InteractionResult.FAIL;
            player.getCooldowns().addCooldown(player.getItemInHand(hand), 60);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
            if (PaperMaps.readMarked(serverPlayer, player.getItemInHand(hand))) {
                serverPlayer.sendOverlayMessage(Component.literal("You study the map..."));
            } else {
                serverPlayer.sendOverlayMessage(Component.literal("This map is of somewhere else.").withStyle(ChatFormatting.GRAY));
            }
        }
        return InteractionResult.SUCCESS;
    }
}
