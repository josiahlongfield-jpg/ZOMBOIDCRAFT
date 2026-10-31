package com.zomboidcraft.power;

import com.zomboidcraft.loot.items.LootItem;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;

/** A generator in the hand: use it on the ground to set it down ({@link Generators#place}). */
public class GeneratorItem extends LootItem {
    private final Generators.Kind kind;

    public GeneratorItem(Properties properties, Generators.Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos at = context.getClickedPos().relative(context.getClickedFace());
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() == null || !Generators.place(level, at, context.getHorizontalDirection().getOpposite(),
                context.getItemInHand(), kind)) {
            return InteractionResult.FAIL;
        }
        if (!context.getPlayer().hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        String state = Generators.describe(stack);
        if (state != null) {
            tooltip.accept(Component.literal(state).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, display, tooltip, flag);
    }
}
