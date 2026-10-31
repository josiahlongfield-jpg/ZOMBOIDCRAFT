package com.zomboidcraft.world.blocks;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shop fittings you can search, the way Zomboid's shelves work: a shelf looks stocked, and
 * using it opens the field kit's nearby side with what's actually on it, rolled from its loot table
 * (data/zomboidcraft/loot_table/shop) the first time anyone looks. The city generator names the
 * shop's own table on each fitting; one placed by hand rolls its default from tools/city/interiors.py.
 */
public final class Fittings {
    private static final Map<Block, String> DEFAULT_TABLE = new HashMap<>();
    private static BlockEntityType<Contents> type;

    /** Remembers the table a fitting rolls when nobody named one (from {@link CitySpecs}). */
    static void defaultTable(Block block, String table) {
        DEFAULT_TABLE.put(block, table);
    }

    static void register(Collection<Block> blocks) {
        type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, ZomboidCraft.id("fitting"),
                FabricBlockEntityTypeBuilder.create(Contents::new, blocks.toArray(Block[]::new)).build());
    }

    public static ResourceKey<LootTable> table(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, ZomboidCraft.id("shop/" + name));
    }

    /** Points a placed fitting at a loot table, or empties it when shop loot is turned off. */
    public static void stock(BlockEntity entity, String table) {
        if (entity instanceof Contents contents) {
            contents.setLootTable(ZcConfig.get().world.shopLoot ? table(table) : null, 0L);
            contents.setChanged();
        } else if (entity instanceof RandomizableContainer container) {
            // a vanilla barrel or chest the generator stocked: "minecraft:barrel[facing=up,loot=farm]"
            container.setLootTable(ZcConfig.get().world.shopLoot ? table(table) : null, 0L);
            entity.setChanged();
        }
    }

    private static InteractionResult open(Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof Contents contents
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // an auto shop's key board: the first look hangs the keys of the customer cars out front
            if (!contents.keysHung && BuiltInRegistries.BLOCK.getKey(contents.getBlockState().getBlock()).getPath().endsWith("as_keyboard")) {
                contents.unpackLootTable(player);
                com.zomboidcraft.world.cars.CarKeys.board(server, pos, contents);
                contents.keysHung = true;
                contents.setChanged();
            }
            // searched in the field kit, beside the floor and anything else in reach
            // (it plays the fitting's own open and close sounds)
            com.zomboidcraft.loot.Nearby.open(serverPlayer, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** A searchable fitting that doesn't turn: crates, bins, paint cans. */
    static class Plain extends CityBlock implements EntityBlock {
        Plain(Properties properties, VoxelShape shape) {
            super(properties, shape);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Contents(pos, state);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return open(level, pos, player);
        }
    }

    /** A searchable fitting that faces into the room: shelves, coolers, counters, cases. */
    static class Facing extends CityBlock.Facing implements EntityBlock {
        Facing(Properties properties, VoxelShape north, boolean onWall) {
            super(properties, north, onWall);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Contents(pos, state);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            return open(level, pos, player);
        }
    }

    /** What a fitting holds: a chest's worth of slots, rolled on first look. */
    public static final class Contents extends RandomizableContainerBlockEntity {
        private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        /** A key board that has hung its shop's customer keys (world/cars/CarKeys#board). */
        boolean keysHung;

        Contents(BlockPos pos, BlockState state) {
            super(type, pos, state);
            String table = DEFAULT_TABLE.get(state.getBlock());
            if (table != null && ZcConfig.get().world.shopLoot) {
                lootTable = table(table);
            }
        }

        @Override
        protected void saveAdditional(ValueOutput output) {
            super.saveAdditional(output);
            if (keysHung) {
                output.putBoolean("keys_hung", true);
            }
            if (!trySaveLootTable(output)) {
                ContainerHelper.saveAllItems(output, items);
            }
        }

        @Override
        protected void loadAdditional(ValueInput input) {
            super.loadAdditional(input);
            lootTable = null;  // a searched fitting saves no table, so it mustn't fall back to its default
            keysHung = input.getBooleanOr("keys_hung", false);
            items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
            if (!tryLoadLootTable(input)) {
                ContainerHelper.loadAllItems(input, items);
            }
        }

        @Override
        public int getContainerSize() {
            return 27;
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
            return ChestMenu.threeRows(id, inventory, this);
        }
    }

    private Fittings() {
    }
}
