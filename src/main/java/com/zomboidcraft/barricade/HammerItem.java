package com.zomboidcraft.barricade;

import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.zombie.Noise;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click a window, door or barricade with planks and nails in your inventory to nail up a board.
 * Sneak + right-click a barricade to pry a board off.
 */
public class HammerItem extends MeleeWeaponItem {
    private static final int NAILS_PER_BOARD = 2;
    private static final double HAMMER_NOISE = 16;

    public HammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        Direction face = context.getClickedFace();

        if (state.is(ModBlocks.BARRICADE)) {
            return player.isShiftKeyDown() ? pryBoard(level, player, clicked, state) : addBoard(level, player, clicked, state, context);
        }
        if (face.getAxis().isVertical()) {
            return InteractionResult.PASS;
        }
        if (isWindow(state)) {
            return placeBarricade(level, player, clicked, face, context);
        }
        if (state.is(BlockTags.DOORS)) {
            BlockPos inFront = clicked.relative(face);
            if (level.getBlockState(inFront).isAir()) {
                return placeBarricade(level, player, inFront, face.getOpposite(), context);
            }
        }
        return InteractionResult.PASS;
    }

    private static boolean isWindow(BlockState state) {
        return state.is(Blocks.GLASS_PANE) || state.is(Blocks.GLASS) || state.is(BlockTags.IMPERMEABLE)
                || state.getBlock().getDescriptionId().contains("glass_pane");
    }

    private InteractionResult placeBarricade(ServerLevel level, ServerPlayer player, BlockPos pos, Direction facing,
                                             UseOnContext context) {
        if (!takeMaterials(player)) {
            return InteractionResult.FAIL;
        }
        int strength = BarricadeBlock.strengthFor(Skills.level(player, Skill.CARPENTRY));
        level.setBlockAndUpdate(pos, ModBlocks.BARRICADE.defaultBlockState()
                .setValue(BarricadeBlock.FACING, facing)
                .setValue(BarricadeBlock.STRENGTH, strength));
        hammered(level, player, pos, context);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult addBoard(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                                       UseOnContext context) {
        int planks = state.getValue(BarricadeBlock.PLANKS);
        if (planks >= BarricadeBlock.MAX_PLANKS) {
            player.sendOverlayMessage(Component.literal("Can't fit any more boards.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        if (!takeMaterials(player)) {
            return InteractionResult.FAIL;
        }
        int strength = Math.max(state.getValue(BarricadeBlock.STRENGTH),
                BarricadeBlock.strengthFor(Skills.level(player, Skill.CARPENTRY)));
        level.setBlockAndUpdate(pos, state.setValue(BarricadeBlock.PLANKS, planks + 1)
                .setValue(BarricadeBlock.STRENGTH, strength));
        hammered(level, player, pos, context);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult pryBoard(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        int planks = state.getValue(BarricadeBlock.PLANKS);
        if (planks <= 1) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, state.setValue(BarricadeBlock.PLANKS, planks - 1));
        }
        if (!player.getInventory().add(new ItemStack(Items.OAK_PLANKS))) {
            net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), new ItemStack(Items.OAK_PLANKS));
        }
        level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.PLAYERS, 0.6f, 1.3f);
        Noise.emit(level, Vec3.atCenterOf(pos), HAMMER_NOISE / 2);
        Skills.addXp(player, Skill.CARPENTRY, 2);
        return InteractionResult.SUCCESS;
    }

    private void hammered(ServerLevel level, ServerPlayer player, BlockPos pos, UseOnContext context) {
        level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.15f, 1.8f);
        Noise.emit(level, Vec3.atCenterOf(pos), HAMMER_NOISE);
        context.getItemInHand().hurtAndBreak(1, player, context.getHand());
        Skills.addXp(player, Skill.CARPENTRY, 6);
    }

    /** One plank and two nails, unless in creative. Higher carpentry sometimes saves the nails. */
    private static boolean takeMaterials(ServerPlayer player) {
        if (player.hasInfiniteMaterials()) {
            return true;
        }
        Inventory inventory = player.getInventory();
        int plankSlot = -1;
        int nails = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (plankSlot < 0 && stack.is(ItemTags.PLANKS)) plankSlot = i;
            if (stack.is(ModItems.NAILS)) nails += stack.getCount();
        }
        if (plankSlot < 0 || nails < NAILS_PER_BOARD) {
            player.sendOverlayMessage(Component.literal("You need a plank and " + NAILS_PER_BOARD + " nails.")
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        inventory.removeItem(plankSlot, 1);
        boolean saveNails = player.getRandom().nextFloat() < 0.05f * Skills.level(player, Skill.CARPENTRY);
        int toRemove = saveNails ? 0 : NAILS_PER_BOARD;
        for (int i = 0; i < inventory.getContainerSize() && toRemove > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(ModItems.NAILS)) {
                int taken = Math.min(toRemove, stack.getCount());
                inventory.removeItem(i, taken);
                toRemove -= taken;
            }
        }
        return true;
    }
}
