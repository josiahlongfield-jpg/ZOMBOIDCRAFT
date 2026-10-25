package com.zomboidcraft.barricade;

import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.TimedActions;
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
import net.minecraft.world.InteractionHand;
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
 * Sneak + right-click a barricade to pry a board off. Either takes a few seconds of standing still.
 */
public class HammerItem extends MeleeWeaponItem {
    private static final int NAILS_PER_BOARD = 2;
    private static final double HAMMER_NOISE = 16;

    public HammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // no swing either side: a swing would stop the hammering
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            BlockState state = context.getLevel().getBlockState(context.getClickedPos());
            boolean board = state.is(ModBlocks.BARRICADE)
                    || context.getClickedFace().getAxis().isHorizontal() && (isWindow(state) || state.is(BlockTags.DOORS));
            return board ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        InteractionHand hand = context.getHand();
        boolean prying = player.isShiftKeyDown();
        if (!act(level, player, clicked, face, hand, prying, false)) {
            return InteractionResult.PASS;
        }
        String key = "hammer:" + clicked.asLong() + ":" + prying;
        if (TimedActions.doing(player, key)) return InteractionResult.CONSUME;
        boolean pry = prying && level.getBlockState(clicked).is(ModBlocks.BARRICADE);
        int carpentry = Skills.level(player, Skill.CARPENTRY);
        int ticks = pry ? 30 : Math.max(30, 60 - 3 * carpentry);
        level.playSound(null, clicked, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.5f, 1.1f);
        TimedActions.start(player, key, pry ? "Prying off a board" : "Boarding up", ticks, p -> {
            if (p.getItemInHand(hand).getItem() instanceof HammerItem) act(level, p, clicked, face, hand, prying, true);
        });
        return InteractionResult.CONSUME;
    }

    /**
     * What a hammer click on this block does. With {@code commit} false it only checks that it can (materials
     * included); with it true it does it.
     */
    private boolean act(ServerLevel level, ServerPlayer player, BlockPos clicked, Direction face, InteractionHand hand,
                        boolean prying, boolean commit) {
        BlockState state = level.getBlockState(clicked);
        if (state.is(ModBlocks.BARRICADE)) {
            if (prying) {
                if (commit) pryBoard(level, player, clicked, state);
                return true;
            }
            return addBoard(level, player, clicked, state, hand, commit);
        }
        if (face.getAxis().isVertical()) {
            return false;
        }
        if (isWindow(state)) {
            return placeBarricade(level, player, clicked, face, hand, commit);
        }
        if (state.is(BlockTags.DOORS)) {
            BlockPos inFront = clicked.relative(face);
            if (level.getBlockState(inFront).isAir()) {
                return placeBarricade(level, player, inFront, face.getOpposite(), hand, commit);
            }
        }
        return false;
    }

    private static boolean isWindow(BlockState state) {
        return state.is(Blocks.GLASS_PANE) || state.is(Blocks.GLASS) || state.is(BlockTags.IMPERMEABLE)
                || state.getBlock().getDescriptionId().contains("glass_pane");
    }

    private boolean placeBarricade(ServerLevel level, ServerPlayer player, BlockPos pos, Direction facing,
                                   InteractionHand hand, boolean commit) {
        if (!level.getBlockState(pos).isAir() && !isWindow(level.getBlockState(pos))) return false;
        if (!commit) return hasMaterials(player);
        if (!takeMaterials(player)) {
            return false;
        }
        int strength = BarricadeBlock.strengthFor(Skills.level(player, Skill.CARPENTRY));
        level.setBlockAndUpdate(pos, ModBlocks.BARRICADE.defaultBlockState()
                .setValue(BarricadeBlock.FACING, facing)
                .setValue(BarricadeBlock.STRENGTH, strength));
        hammered(level, player, pos, hand);
        return true;
    }

    private boolean addBoard(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state,
                             InteractionHand hand, boolean commit) {
        int planks = state.getValue(BarricadeBlock.PLANKS);
        if (planks >= BarricadeBlock.MAX_PLANKS) {
            player.sendOverlayMessage(Component.literal("Can't fit any more boards.").withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (!commit) return hasMaterials(player);
        if (!takeMaterials(player)) {
            return false;
        }
        int strength = Math.max(state.getValue(BarricadeBlock.STRENGTH),
                BarricadeBlock.strengthFor(Skills.level(player, Skill.CARPENTRY)));
        level.setBlockAndUpdate(pos, state.setValue(BarricadeBlock.PLANKS, planks + 1)
                .setValue(BarricadeBlock.STRENGTH, strength));
        hammered(level, player, pos, hand);
        return true;
    }

    private void pryBoard(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        int planks = state.getValue(BarricadeBlock.PLANKS);
        if (planks <= 1) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, state.setValue(BarricadeBlock.PLANKS, planks - 1));
        }
        if (!player.getInventory().add(new ItemStack(Items.OAK_PLANKS))) {
            net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), new ItemStack(Items.OAK_PLANKS));
        }
        level.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.9f, 0.8f);
        Noise.emit(level, Vec3.atCenterOf(pos), HAMMER_NOISE / 2);
        Skills.addXp(player, Skill.CARPENTRY, 2);
    }

    private void hammered(ServerLevel level, ServerPlayer player, BlockPos pos, InteractionHand hand) {
        level.playSound(null, pos, ModSounds.HAMMER_NAIL, SoundSource.PLAYERS, 1f, 0.92f + level.getRandom().nextFloat() * 0.16f);
        Noise.emit(level, Vec3.atCenterOf(pos), HAMMER_NOISE);
        player.getItemInHand(hand).hurtAndBreak(1, player, hand);
        Skills.addXp(player, Skill.CARPENTRY, 6);
    }

    /** Whether a plank and the nails are on you, saying so when they aren't. */
    private static boolean hasMaterials(ServerPlayer player) {
        if (player.hasInfiniteMaterials()) return true;
        Inventory inventory = player.getInventory();
        boolean plank = false;
        int nails = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            plank |= stack.is(ItemTags.PLANKS);
            if (stack.is(ModItems.NAILS)) nails += stack.getCount();
        }
        if (!plank || nails < NAILS_PER_BOARD) {
            player.sendOverlayMessage(Component.literal("You need a plank and " + NAILS_PER_BOARD + " nails.")
                    .withStyle(ChatFormatting.GRAY));
            return false;
        }
        return true;
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
