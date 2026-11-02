package com.zomboidcraft.decor;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.loot.items.Spoil;
import java.util.Collection;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a decor piece holds: the bottles on a home bar, the cans in a vending machine (kept cold while it has power),
 * the sweets in a gumball machine, the wood burning in a stove, the outfit on a mannequin. Searched in the field kit
 * like any shelf (loot/Nearby).
 */
public final class DecorEntity extends BaseContainerBlockEntity {
    private static BlockEntityType<DecorEntity> type;

    private NonNullList<ItemStack> items;
    /** Ticks of fire left in a stove. */
    int fuel;

    static void register(Collection<Block> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ZomboidCraft.id("decor"),
                FabricBlockEntityTypeBuilder.create(DecorEntity::new, blocks.toArray(Block[]::new)).build());
    }

    static BlockEntityType<DecorEntity> type() {
        return type;
    }

    public DecorEntity(BlockPos pos, BlockState state) {
        super(type, pos, state);
        items = NonNullList.withSize(slots(state), ItemStack.EMPTY);
    }

    private static int slots(BlockState state) {
        if (!(state.getBlock() instanceof DecorBlock d) || d.spec.special() == null) {
            return 9;
        }
        return switch (d.spec.special()) {
            case "cold_store" -> 27;
            case "drinks_store" -> 18;
            default -> 9;
        };
    }

    /** The vending machine keeps what's in it cold while it works; once a second-ish from the block's tick. */
    void chill(Level level, boolean cold) {
        long now = level.getGameTime();
        boolean changed = false;
        for (ItemStack s : items) {
            if (!s.isEmpty() && Spoil.update(s, now, cold ? Spoil.FRIDGE : Spoil.OUT)) {
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putInt("fuel", fuel);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(slots(getBlockState()), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        fuel = input.getIntOr("fuel", 0);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return items.size() == 27 ? ChestMenu.threeRows(id, inventory, this)
                : items.size() == 18 ? new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x2, id, inventory, this, 2)
                : new ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inventory, this, 1);
    }
}
