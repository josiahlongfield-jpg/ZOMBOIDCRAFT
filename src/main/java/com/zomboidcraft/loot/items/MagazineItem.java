package com.zomboidcraft.loot.items;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.TimedActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/**
 * A magazine that teaches something for good, Zomboid's way: read it once (a few seconds) and you know it; reading
 * it again teaches nothing. What you've read is kept on you (lost on death, like everything else you learnt).
 * Systems ask {@link #knows}.
 */
public class MagazineItem extends LootItem {
    /** What each magazine teaches, as the line you're told when you've read it. */
    public static final Map<String, String> TEACHES = Map.of(
            "mag_generators", "You know how to connect a generator now.");

    private static final AttachmentType<List<String>> READ = AttachmentRegistry.create(ZomboidCraft.id("magazines_read"),
            builder -> builder.persistent(Codec.STRING.listOf()).initializer(List::of));

    public MagazineItem(Properties properties) {
        super(properties);
    }

    /** Has this player read this magazine? */
    public static boolean knows(Player player, String magazine) {
        return player.getAttachedOrElse(READ, List.of()).contains(magazine);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (knows(sp, id)) {
            sp.sendOverlayMessage(Component.literal("You've read this one already.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        TimedActions.start(sp, "read:" + id, "Reading", 100, p -> {
            List<String> read = new ArrayList<>(p.getAttachedOrElse(READ, List.of()));
            if (!read.contains(id)) {
                read.add(id);
                p.setAttached(READ, List.copyOf(read));
            }
            p.sendOverlayMessage(Component.literal(TEACHES.getOrDefault(id, "You've read it.")).withStyle(ChatFormatting.GRAY));
        });
        return InteractionResult.SUCCESS;
    }
}
