package com.zomboidcraft.gear;

import com.zomboidcraft.ZcConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A handheld radio: a toy walkie-talkie, a two-way radio or a military handheld. Hold the talk key to talk on it
 * ({@link Radios}); everyone with a radio on the same channel inside its reach hears you. Right-click to go up a
 * channel (sneak to go down), right-click it in the inventory with nothing to switch it on or off, or with an AA
 * battery to change the battery.
 */
public class RadioItem extends Item {
    /** The battery bar: a pale amber, like the two-way radio's screen. */
    static final int BAR = 0xE0B060;

    public enum Type {
        TOY("toy_radio", "A kids' walkie-talkie: not much reach"),
        TWO_WAY("two_way_radio", "A two-way radio"),
        MILITARY("military_radio", "A military handheld: a long reach");

        public final String key;
        final String blurb;

        Type(String key, String blurb) {
            this.key = key;
            this.blurb = blurb;
        }

        /** How far it carries a voice, in blocks. */
        public double range() {
            ZcConfig.Radio cfg = ZcConfig.get().radio;
            return Math.max(1, switch (this) {
                case TOY -> cfg.toyRange;
                case TWO_WAY -> cfg.twoWayRange;
                case MILITARY -> cfg.militaryRange;
            });
        }

        public int channels() {
            ZcConfig.Radio cfg = ZcConfig.get().radio;
            return Math.max(1, switch (this) {
                case TOY -> cfg.toyChannels;
                case TWO_WAY -> cfg.twoWayChannels;
                case MILITARY -> cfg.militaryChannels;
            });
        }
    }

    private final Type type;

    public RadioItem(Type type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    /** Right-click: up a channel; sneaking, down one. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            Radios.stepChannel(sp, player.getItemInHand(hand), player.isShiftKeyDown() ? -1 : 1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || self.getCount() != 1) {
            return false;
        }
        if (other.isEmpty()) {
            Radios.togglePower(self, player);
            return true;
        }
        if (other.getItem() instanceof BatteryItem) {
            return Radios.swapBattery(self, other, player);
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Radios.Tuning tuning = Radios.tuning(stack);
        tooltip.accept(Component.literal(type.blurb + ", about " + Math.round(type.range()) + " blocks")
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal(Broadcasts.label(tuning.channel(), type) + ", " + (tuning.on() ? "on" : "off"))
                .withStyle(tuning.on() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
        if (ZcConfig.get().radio.batteries) {
            tooltip.accept(Component.literal(stack.has(Headgear.CHARGE) ? Radios.chargeText(stack) : "Battery: not checked yet")
                    .withStyle(Radios.charge(stack) > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        tooltip.accept(Component.literal("Hold B to talk. Right-click: channel (sneak to go down)").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.literal("Right-click it in your inventory to switch it on or off").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ZcConfig.get().radio.batteries && stack.has(Headgear.CHARGE) && Radios.chargeFraction(stack) < 1f;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Radios.chargeFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR;
    }
}
