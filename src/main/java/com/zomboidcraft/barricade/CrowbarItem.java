package com.zomboidcraft.barricade;

import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.zombie.Noise;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * A crowbar pries a hidden bunker's sealed blast door: right-click it and work at it for ten seconds
 * while the bolts screech and bang loud enough to bring every idle zombie near. The door swings in
 * on its hinges and stays standing open against the decon room's wall, the way world/county/Bunker
 * leaves one somebody looted first.
 */
public class CrowbarItem extends MeleeWeaponItem {
    private static final int TICKS = 200;
    /** Each bang while you work at it, and the door giving. */
    private static final double PRY_NOISE = 28, OPEN_NOISE = 44;

    /** Who is prying which door, for the bangs while the bar fills. */
    private static final Map<UUID, Prying> PRYING = new HashMap<>();

    private record Prying(ServerLevel level, BlockPos pos, String key) {
    }

    public CrowbarItem(Properties properties) {
        super(properties);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CrowbarItem::tick);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos clicked = context.getClickedPos();
        if (row(context.getLevel().getBlockState(clicked)) < 0) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.CONSUME;
        }
        String key = "crowbar:" + clicked.asLong();
        if (TimedActions.doing(player, key)) {
            return InteractionResult.CONSUME;
        }
        InteractionHand hand = context.getHand();
        bang(level, clicked);
        PRYING.put(player.getUUID(), new Prying(level, clicked, key));
        TimedActions.start(player, key, "Prying the blast door", TICKS, p -> {
            PRYING.remove(p.getUUID());
            ItemStack held = p.getItemInHand(hand);
            if (held.getItem() instanceof CrowbarItem && pry(level, clicked)) {
                level.playSound(null, clicked, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.5f, 0.4f);
                level.playSound(null, clicked, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 1.2f, 0.6f);
                Noise.emit(level, Vec3.atCenterOf(clicked), OPEN_NOISE);
                held.hurtAndBreak(6, p, hand);
            }
        });
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.literal("Right-click a bunker's blast door to pry it open (loud)")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** A bang and a screech every second and a half while someone works at a door. */
    private static void tick(MinecraftServer server) {
        if (PRYING.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Prying>> it = PRYING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Prying> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Prying pr = e.getValue();
            if (player == null || !TimedActions.doing(player, pr.key())) {
                it.remove();
            } else if (pr.level().getGameTime() % 30 == 0) {
                bang(pr.level(), pr.pos());
            }
        }
    }

    private static void bang(ServerLevel level, BlockPos pos) {
        float pitch = 0.75f + level.getRandom().nextFloat() * 0.2f;
        level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.BLOCKS, 0.9f, pitch);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35f, 0.5f);
        Noise.emit(level, Vec3.atCenterOf(pos), PRY_NOISE);
    }

    /**
     * Which row of the blast door a block is (0 at the foot), or -1 when it isn't one. The door is
     * two wide and three tall, "bunk_blast_w" + row and "bunk_blast_e" + row, its e half on the
     * right of the door's facing (east when it faces north), where the hinges are.
     */
    private static int row(BlockState state) {
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (!name.startsWith("bunk_blast_") || name.length() != 13 || !state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return -1;
        }
        return name.charAt(12) - '0';
    }

    private static char half(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().charAt(11);
    }

    /**
     * Swings the door the clicked block belongs to in on its hinges: out of its frame, standing
     * open against the wall inside, two blocks deep along its hinge side. Where something stands
     * in the way of the open leaf, the door comes away instead. False when it isn't all there.
     */
    private static boolean pry(ServerLevel level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        int row = row(state);
        if (row < 0) {
            return false;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos foot = clicked.below(row);
        BlockPos e = half(state) == 'e' ? foot : foot.relative(facing.getClockWise());
        BlockPos w = e.relative(facing.getCounterClockWise());
        for (int r = 0; r < 3; r++) {
            if (row(level.getBlockState(e.above(r))) != r || row(level.getBlockState(w.above(r))) != r) {
                return false;
            }
        }
        Direction in = facing.getOpposite();
        boolean room = true;
        for (int d = 1; d <= 2; d++) {
            for (int r = 0; r < 3; r++) {
                room &= clear(level, e.relative(in, d).above(r));
            }
        }
        String turned = "[facing=" + facing.getCounterClockWise().getSerializedName() + "]";
        for (int r = 0; r < 3; r++) {
            level.setBlock(e.above(r), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(w.above(r), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            if (room) {
                set(level, e.relative(in, 1).above(r), "bunk_blast_e" + r + turned);
                set(level, e.relative(in, 2).above(r), "bunk_blast_w" + r + turned);
            }
        }
        return true;
    }

    /** Nothing there the open door would crush: air, or a lamp or litter with nothing to keep. */
    private static boolean clear(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isAir() || s.canBeReplaced()
                || !s.hasBlockEntity() && s.getCollisionShape(level, pos).isEmpty();
    }

    private static void set(ServerLevel level, BlockPos pos, String block) {
        BlockState s = CityBlocks.resolve(block);
        if (s != null) {
            level.setBlock(pos, s, Block.UPDATE_ALL);
        }
    }
}
