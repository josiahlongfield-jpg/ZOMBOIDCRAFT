package com.zomboidcraft.carry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/** A bag you wear or carry. What's inside rides on the stack as a container component, so it goes where the bag goes. */
public class BagItem extends Item {
    private final BagSpec spec;

    public BagItem(BagSpec spec, Properties properties) {
        super(properties.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        this.spec = spec;
    }

    public BagSpec spec() {
        return spec;
    }

    /** The bag's spec, or null when the stack isn't a bag. */
    public static BagSpec spec(ItemStack stack) {
        return stack.getItem() instanceof BagItem bag ? bag.spec : null;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        // a bag burnt or blown up spills what it held
        ItemContainerContents contents = entity.getItem().get(DataComponents.CONTAINER);
        if (contents == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        contents.nonEmptyItemCopyStream().forEach(stack ->
                level.addFreshEntity(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), stack)));
    }
}
