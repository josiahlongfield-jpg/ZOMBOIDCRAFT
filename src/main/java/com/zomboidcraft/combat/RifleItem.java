package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A gun (the assault rifle, the pistol, the pump shotgun: see {@link GunType}). Hold right-click to aim down the sights,
 * left-click to fire, R to reload. Firing and reloading run on the server (see {@link Guns}); this class is
 * the item's own side: aiming is "using" the item, which the item model turns into the sight picture.
 */
public class RifleItem extends Item {
    private final GunType type;

    public RifleItem(GunType type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public GunType type() {
        return type;
    }

    /** Rounds in an assault rifle magazine. */
    public static int capacity() {
        return GunType.ASSAULT_RIFLE.capacity();
    }

    /** Rounds in a magazine for this gun. */
    public static int capacity(ItemStack gun) {
        GunType type = GunType.of(gun);
        return (type == null ? GunType.ASSAULT_RIFLE : type).capacity();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** Rounds going down, the condition wearing and the flash don't make the gun bob as if swapped. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    /**
     * Stand-in for the weapon screen: right-click the gun with a part to fit it (whatever held that slot
     * comes back on the cursor), or with an empty cursor to take the first part off, optic first and
     * the magazine last.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, net.minecraft.world.inventory.Slot slot,
                                            ClickAction action, Player player, SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        if (other.isEmpty()) {
            Attachments.Slot filled = ZcConfig.get().guns.attachments ? Attachments.firstFilled(self) : null;
            if (filled == null) {
                return false;
            }
            carried.set(Attachments.unmount(self, filled));
            player.playSound(type.magOut(), 0.6f, 1.3f);
            return true;
        }
        if (!Attachments.canMount(self, other)) {
            return false;
        }
        // a swap hands back what was on the gun, which needs the cursor free
        Attachments.Slot into = Attachments.slotOf(other);
        if (other.getCount() > 1 && !Attachments.get(self, into).isEmpty()) {
            return false;
        }
        ItemStack old = Attachments.mount(self, other);
        other.shrink(1);
        if (old != null && !old.isEmpty()) {
            carried.set(old);
        }
        player.playSound(type.magIn(), 0.6f, 1.3f);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GunState gun = stack.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        if (type.tube) {
            tooltip.accept(Component.literal("Shells: " + gun.rounds() + "/" + type.capacity()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.accept(gun.mag()
                    ? Component.literal("Magazine: " + gun.rounds() + "/" + type.capacity()).withStyle(ChatFormatting.GRAY)
                    : Component.literal("No magazine").withStyle(ChatFormatting.DARK_RED));
        }
        if (gun.jammed()) {
            tooltip.accept(Component.literal("Jammed: press R to clear").withStyle(ChatFormatting.RED));
        }
        Component finish = GunFinish.line(stack);
        if (finish != null) {
            tooltip.accept(finish);
        }
        tooltip.accept(MeleeWeaponItem.condition(stack));
        for (Attachments.Slot part : Attachments.slots(stack)) {
            ItemStack att = Attachments.get(stack, part);
            if (part == Attachments.Slot.MAGAZINE) {
                continue;
            }
            tooltip.accept(Component.literal(AttachmentItem.slotName(part) + ": ").withStyle(ChatFormatting.GRAY)
                    .append(att.isEmpty() ? Component.literal("empty").withStyle(ChatFormatting.DARK_GRAY)
                            : att.getHoverName().copy().withStyle(ChatFormatting.WHITE)));
        }
        if (Attachments.firstFilled(stack) != null) {
            tooltip.accept(Component.literal("Right-click with an empty hand to take a part off").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.accept(Component.literal(type.opens ? "Right-click: aim   Left-click: fire   R: break open and load (fire to stop)"
                : type.tube ? "Right-click: aim   Left-click: fire   R: load shells (fire to stop)"
                : "Right-click: aim   Left-click: fire   R: reload").withStyle(ChatFormatting.DARK_GRAY));
        if (type.bolt) {
            tooltip.accept(Component.literal("Bolt action: the bolt is worked after every shot").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
