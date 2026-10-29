package com.zomboidcraft.loot.items;

import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.SkillBooks;
import com.zomboidcraft.skills.Skills;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A skill book (SkillBooks): hold right-click to read the volume for the level you're at. The book isn't used up; pass
 * it on, or keep it for the next volume.
 */
public class SkillBookItem extends LootItem {
    /** Reading one volume, in ticks. */
    private static final int READ_TICKS = 15 * 20;

    private final Skill skill;

    public SkillBookItem(Properties properties, Skill skill) {
        super(properties);
        this.skill = skill;
    }

    public Skill skill() {
        return skill;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        int lvl = Skills.level(player, skill);
        int volume = SkillBooks.volume(lvl);
        String no = lvl >= Skill.MAX_LEVEL ? "There's nothing left in this book to teach you."
                : SkillBooks.read(player, skill, volume) ? "You've already read Vol " + volume + "." : null;
        if (no != null) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.literal(no).withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        int done = READ_TICKS - remaining;
        if (done > 0 && done % 60 == 0) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                    0.6f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        }
        if (done % 20 == 0) {
            int volume = SkillBooks.volume(Skills.level(player, skill));
            player.sendOverlayMessage(Component.literal("Reading " + skill.displayName() + " Vol " + volume + "... "
                    + Math.round(100f * done / READ_TICKS) + "%").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            int volume = SkillBooks.volume(Skills.level(player, skill));
            SkillBooks.markRead(player, skill, volume);
            player.sendOverlayMessage(Component.literal("Finished " + skill.displayName() + " Vol " + volume + ": x"
                    + SkillBooks.multiplier(volume) + " XP up to level " + SkillBooks.lastLevel(volume) + ".")
                    .withStyle(ChatFormatting.WHITE));
        }
        return stack;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return READ_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.literal("Hold right-click to read the volume for your level.").withStyle(ChatFormatting.GRAY));
    }
}
