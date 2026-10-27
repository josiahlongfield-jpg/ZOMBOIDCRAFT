package com.zomboidcraft.loot.items;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A sealed kit or pack: use it to open it out into what it holds (Packs). */
public class PackItem extends Item {
    private final String id;

    public PackItem(Properties properties, String id) {
        super(properties);
        this.id = id;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        RandomSource random = sp.getRandom();
        int got = 0;
        for (Packs.Line line : Packs.CONTENTS.getOrDefault(id, List.of())) {
            Item item = LootItems.item(line.item());
            if (item == null || random.nextFloat() >= line.chance()) {
                continue;
            }
            ItemStack out = new ItemStack(item, line.min() + random.nextInt(line.max() - line.min() + 1));
            got += out.getCount();
            if (!sp.getInventory().add(out)) {
                sp.drop(out, false);
            }
        }
        stack.consume(1, sp);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 0.7f, 1f);
        sp.sendOverlayMessage(Component.literal("Opened it: " + got + " things inside.").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }
}
