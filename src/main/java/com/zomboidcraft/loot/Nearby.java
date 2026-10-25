package com.zomboidcraft.loot;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.blocks.Fittings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's loot side: open the field kit (or use a shelf) and the floor round you plus every searchable container
 * in reach shows as tabs beside your own things. Shelves, crates and the like roll their loot the first time anyone
 * looks, as before; vanilla chests and barrels in reach join in too.
 */
public final class Nearby {
    /** One tab: what it's called, what its tab shows, how many slots, and whether it's the floor. */
    public record Source(Component name, ItemStack icon, int size, boolean floor) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Source> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, Source::name, ItemStack.OPTIONAL_STREAM_CODEC, Source::icon,
                ByteBufCodecs.VAR_INT, Source::size, ByteBufCodecs.BOOL, Source::floor, Source::new);
    }

    /** What the client needs to build the same menu: the tabs and which one is open. */
    public record Opening(List<Source> sources, int selected) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Opening> CODEC = StreamCodec.composite(
                Source.CODEC.apply(ByteBufCodecs.list(16)), Opening::sources, ByteBufCodecs.VAR_INT, Opening::selected,
                Opening::new);
    }

    /** The client asks for the field kit instead of opening the plain inventory itself. */
    public record OpenKit() implements CustomPacketPayload {
        public static final Type<OpenKit> TYPE = new Type<>(ZomboidCraft.id("open_kit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenKit> CODEC = StreamCodec.unit(new OpenKit());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A source that can drift out of reach as you move: its slots then stop taking or giving. */
    interface Reach {
        boolean inReach(Player player);
    }

    public static final ExtendedMenuType<NearbyMenu, Opening> MENU = new ExtendedMenuType<>(NearbyMenu::new, Opening.CODEC);

    private static final int MAX_CONTAINERS = 8;
    private static final int MAX_SLOTS = 27;

    public static void register() {
        Registry.register(BuiltInRegistries.MENU, ZomboidCraft.id("nearby"), MENU);
        PayloadTypeRegistry.serverboundPlay().register(OpenKit.TYPE, OpenKit.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(OpenKit.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.containerMenu == player.inventoryMenu && !player.isSpectator()) {
                    open(player, null);
                }
            });
        });
    }

    /** A block container, guarded so a broken or distant one gives and takes nothing. */
    private static final class BlockSource implements Container, Reach {
        final BaseContainerBlockEntity entity;

        BlockSource(BaseContainerBlockEntity entity) {
            this.entity = entity;
        }

        @Override
        public boolean inReach(Player player) {
            return !entity.isRemoved() && Container.stillValidBlockEntity(entity, player);
        }

        private boolean gone() {
            return entity.isRemoved();
        }

        @Override
        public int getContainerSize() {
            return entity.getContainerSize();
        }

        @Override
        public boolean isEmpty() {
            return gone() || entity.isEmpty();
        }

        @Override
        public ItemStack getItem(int slot) {
            return gone() ? ItemStack.EMPTY : entity.getItem(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            return gone() ? ItemStack.EMPTY : entity.removeItem(slot, count);
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return gone() ? ItemStack.EMPTY : entity.removeItemNoUpdate(slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (!gone()) {
                entity.setItem(slot, stack);
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return !gone() && entity.canPlaceItem(slot, stack);
        }

        @Override
        public void setChanged() {
            if (!gone()) {
                entity.setChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
        }

        @Override
        public void startOpen(net.minecraft.world.entity.ContainerUser user) {
            entity.startOpen(user);
        }

        @Override
        public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
            if (!gone()) {
                entity.stopOpen(user);
            }
        }
    }

    private static boolean searchable(BlockEntity entity) {
        return entity instanceof Fittings.Contents || entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity;
    }

    /** True when nothing solid stands between your eyes and the block (the block itself doesn't count). */
    private static boolean seen(Player player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = player.level().clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }

    /**
     * Opens the field kit with what's around the player. {@code used} is the container they used to get here
     * (searched first and shown first), or null when they opened their inventory.
     */
    public static void open(ServerPlayer player, BlockPos used) {
        List<BaseContainerBlockEntity> found = new ArrayList<>();
        BlockPos feet = player.blockPosition();
        if (used != null && player.level().getBlockEntity(used) instanceof BaseContainerBlockEntity clicked && searchable(clicked)) {
            found.add(clicked);
        }
        List<BaseContainerBlockEntity> around = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2))) {
            BlockEntity entity = player.level().getBlockEntity(at);
            if (entity instanceof BaseContainerBlockEntity container && searchable(container) && !found.contains(container)
                    && container.getContainerSize() <= MAX_SLOTS && container.canOpen(player)
                    && player.isWithinBlockInteractionRange(at, 0.5) && seen(player, at.immutable())) {
                around.add(container);
            }
        }
        around.sort(Comparator.comparingDouble(e -> e.getBlockPos().distToCenterSqr(player.position())));
        for (BaseContainerBlockEntity entity : around) {
            if (found.size() >= MAX_CONTAINERS) {
                break;
            }
            found.add(entity);
        }

        List<Source> sources = new ArrayList<>();
        List<Container> containers = new ArrayList<>();
        sources.add(new Source(Component.literal("Floor"), ItemStack.EMPTY, FloorContainer.SIZE, true));
        containers.add(new FloorContainer(player));
        for (BaseContainerBlockEntity entity : found) {
            if (entity instanceof RandomizableContainer loot) {
                loot.unpackLootTable(player);
            }
            sources.add(new Source(entity.getDisplayName(), new ItemStack(entity.getBlockState().getBlock().asItem()),
                    entity.getContainerSize(), false));
            containers.add(new BlockSource(entity));
        }
        // the first container's tab opens if there is one (the one used, when there was), else the floor
        int selected = found.isEmpty() ? 0 : 1;
        Opening opening = new Opening(List.copyOf(sources), selected);
        boolean lidUp = used != null && !found.isEmpty() && found.get(0).getBlockPos().equals(used);

        if (lidUp) {
            LootSounds.play(player.level(), used, LootSounds.event(found.get(0).getBlockState().getBlock()), false);
        }
        player.openMenu(new ExtendedMenuProvider<Opening>() {
            @Override
            public Opening getScreenOpeningData(ServerPlayer player) {
                return opening;
            }

            @Override
            public Component getDisplayName() {
                return Component.literal("Field kit");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                NearbyMenu menu = new NearbyMenu(id, inventory, opening.sources(), containers, opening.selected());
                if (lidUp) {
                    menu.opened(containers.get(1), used, LootSounds.event(found.get(0).getBlockState().getBlock()));
                }
                return menu;
            }
        });
    }

    private Nearby() {
    }
}
