package com.zomboidcraft.building;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A wind-up alarm clock. Sneak + right-click winds it on to the next time (10 seconds up to 5 minutes); set down from
 * the Build page, wired into a car's alarm, or thrown (hold right-click), it rings when the time's up and everything
 * in earshot comes to see.
 */
public class AlarmClockItem extends com.zomboidcraft.combat.throwing.ThrowableItem {
    /** The times it can be set to, in seconds. */
    public static final int[] TIMES = {10, 30, 60, 120, 300};
    private static final int DEFAULT = 30;

    public AlarmClockItem(Properties properties) {
        super(com.zomboidcraft.combat.throwing.ThrowKind.ALARM_CLOCK, properties);
    }

    /** How long the clock in this stack is set to run, in seconds. */
    public static int seconds(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int s = data == null ? DEFAULT : data.copyTag().getIntOr("timer", DEFAULT);
        return s > 0 ? s : DEFAULT;
    }

    /** The timer of the first alarm clock the player carries, in seconds. */
    public static int carried(Player player) {
        ItemStack clock = Materials.find(player, s -> s.getItem() instanceof AlarmClockItem);
        return clock.isEmpty() ? DEFAULT : seconds(clock);
    }

    public static String says(int seconds) {
        return seconds < 60 ? seconds + " seconds" : seconds == 60 ? "1 minute" : seconds / 60 + " minutes";
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        ItemStack stack = player.getItemInHand(hand);
        int now = seconds(stack), next = TIMES[0];
        for (int i = 0; i < TIMES.length; i++) {
            if (TIMES[i] == now) next = TIMES[(i + 1) % TIMES.length];
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("timer", next);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        level.playSound(player, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.3f, 1.8f);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.sendOverlayMessage(Component.literal("Alarm set to ring " + says(next) + " after it's set down.").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Rings " + says(seconds(stack)) + " after it's set down or lands").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Hold right-click to throw. Sneak + right-click to change the time. Build page: set it down, or wire it to a car alarm")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
