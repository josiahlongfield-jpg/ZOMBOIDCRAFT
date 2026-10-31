package com.zomboidcraft.loot;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.loot.items.LootItems;
import com.zomboidcraft.survival.MedSpecs;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.world.blocks.CityBlocks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Dale's cache on the Kingsbridge water tower (his radio broadcasts point to it). The ladder is chained a third of
 * the way up; the padlock's key is taped under the hand pump at the tower's foot. Unlock the chain with the key, or
 * cut it with bolt cutters, and the locker on the catwalk (loot table kingsbridge_stash) is yours.
 * <p>
 * The pieces are found by block (LANDSCAPE's Landmarks.waterTower, stash=true): the chain is sy_chain with the
 * ladder or its cage under it, used nowhere else; the pump is fm_pump with that chain in the column east of it.
 * Each pump gives its key once per world.
 */
public final class TowerStash {
    public enum Kind { PUMP, CHAIN }

    public static final String LOCK = "water_tower";

    public record Use(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(ZomboidCraft.id("tower_stash"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Use::pos, Use::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Pumps already searched (BlockPos.asLong), on the level. */
    private static final AttachmentType<List<Long>> SEARCHED = AttachmentRegistry.create(ZomboidCraft.id("tower_pumps_searched"),
            builder -> builder.persistent(Codec.LONG.listOf()).initializer(List::of));

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> use(player, payload.pos()));
        });
    }

    /** What this block is, or null if it's not part of a tower stash. Both sides. */
    public static Kind kind(BlockGetter level, BlockPos pos) {
        Block chain = CityBlocks.block("sy_chain"), pump = CityBlocks.block("fm_pump");
        BlockState state = level.getBlockState(pos);
        if (chain != null && state.is(chain) && climbable(level.getBlockState(pos.below()))) return Kind.CHAIN;
        if (pump != null && state.is(pump)) {
            for (int dy = 2; dy <= 16; dy++) {
                BlockPos p = pos.offset(1, dy, 0);
                if (chain != null && level.getBlockState(p).is(chain) && climbable(level.getBlockState(p.below()))) return Kind.PUMP;
            }
        }
        return null;
    }

    private static boolean climbable(BlockState state) {
        Block cage = CityBlocks.block("rd_cage");
        return state.is(Blocks.LADDER) || cage != null && state.is(cage);
    }

    public static boolean isKey(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && LOCK.equals(data.copyTag().getStringOr("lock", ""));
    }

    private static boolean hasKey(Player player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (isKey(player.getInventory().getItem(i))) return true;
        }
        return false;
    }

    private static void use(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        if (TimedActions.busy(player) || player.distanceToSqr(pos.getCenter()) > 5 * 5) return;
        Kind kind = kind(level, pos);
        if (kind == Kind.PUMP) {
            TimedActions.start(player, "stash:" + pos.asLong(), "Feeling under the pump", 60, p -> search(p, pos));
        } else if (kind == Kind.CHAIN) {
            if (hasKey(player)) {
                TimedActions.start(player, "stash:" + pos.asLong(), "Unlocking the padlock", 50, p -> open(p, pos, "The padlock springs open."));
            } else if (MedSpecs.carries(player, "bolt_cutters")) {
                TimedActions.start(player, "stash:" + pos.asLong(), "Cutting the chain", 140, p -> {
                    level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
                    com.zomboidcraft.zombie.Noise.emit(level, pos.getCenter(), 12);
                    open(p, pos, "The chain snaps and drops away.");
                });
            } else {
                say(player, "It's padlocked. You need the key, or bolt cutters.");
            }
        }
    }

    private static void search(ServerPlayer player, BlockPos pos) {
        ServerLevel level = (ServerLevel) player.level();
        List<Long> done = level.getAttachedOrElse(SEARCHED, List.of());
        if (kind(level, pos) != Kind.PUMP || done.contains(pos.asLong())) {
            say(player, "Nothing under there but rust.");
            return;
        }
        var item = LootItems.item("padlock_key");
        if (item == null) return;
        ItemStack key = new ItemStack(item);
        CustomData.update(DataComponents.CUSTOM_DATA, key, tag -> tag.putString("lock", LOCK));
        key.set(DataComponents.CUSTOM_NAME, Component.literal("Water Tower Key").withStyle(s -> s.withItalic(false)));
        List<Long> next = new ArrayList<>(done);
        next.add(pos.asLong());
        level.setAttached(SEARCHED, List.copyOf(next));
        if (!player.getInventory().add(key)) player.spawnAtLocation(level, key);
        say(player, "A key, taped under the pump's spout.");
    }

    private static void open(ServerPlayer player, BlockPos pos, String text) {
        ServerLevel level = (ServerLevel) player.level();
        if (kind(level, pos) != Kind.CHAIN) return;
        // the ladder carries on where the chain was
        level.setBlockAndUpdate(pos, level.getBlockState(pos.below()));
        say(player, text);
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private TowerStash() {
    }
}
