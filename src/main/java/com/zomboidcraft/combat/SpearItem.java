package com.zomboidcraft.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.survival.TimedActions;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B42's spears (Josiah 2026-10-31: Zomboid's version). A carved wooden spear is hardened by holding its point in a
 * fire (use it on a lit campfire), and a kitchen knife can be tied onto it with duct tape, a torn sheet or rope (use it
 * while sneaking with both on you). Spears reach further than anything else, stab rather than knock back, and train
 * the Spear skill; like any weapon they strike a zombie on the ground harder.
 */
public class SpearItem extends MeleeWeaponItem {
    private static final int HARDEN_TICKS = 100;
    private static final int TIE_TICKS = 80;
    private static final List<String> BINDINGS = List.of("duct_tape", "ripped_sheet", "rope");

    public SpearItem(Properties properties) {
        super(properties);
    }

    /** Where the makings come from, as Zomboid's foraging finds them: branches off the trees, stones in the gravel. */
    public static void register() {
        net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
            if (path.startsWith("blocks/") && path.endsWith("_leaves") && context.getRandom().nextFloat() < 0.12f) {
                drops.add(new ItemStack(ModItems.TREE_BRANCH));
            } else if (path.equals("blocks/gravel") && context.getRandom().nextFloat() < 0.25f) {
                drops.add(new ItemStack(ModItems.STONE));
            }
        });
    }

    private static boolean is(ItemStack stack, Item item) {
        return stack.getItem() == item;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack held = context.getItemInHand();
        BlockPos pos = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(pos);
        if (!is(held, ModItems.WOODEN_SPEAR) || !CampfireBlock.isLitCampfire(state)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.CONSUME;
        }
        InteractionHand hand = context.getHand();
        level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1f, 1f);
        TimedActions.start(player, "harden:" + pos.asLong(), "Hardening the spear in the fire", HARDEN_TICKS, p -> {
            ItemStack now = p.getItemInHand(hand);
            if (!is(now, ModItems.WOODEN_SPEAR) || !CampfireBlock.isLitCampfire(level.getBlockState(pos))) {
                return;
            }
            p.setItemInHand(hand, now.transmuteCopy(ModItems.FIRE_HARDENED_SPEAR, 1));
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
        });
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!is(held, ModItems.WOODEN_SPEAR) || !player.isShiftKeyDown()) {
            return super.use(level, player, hand);
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.CONSUME;
        }
        if (find(sp.getInventory(), ModItems.KITCHEN_KNIFE) < 0 || binding(sp.getInventory()) < 0) {
            sp.sendOverlayMessage(Component.literal("Tying on a knife takes a kitchen knife and duct tape, a torn sheet or rope")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        TimedActions.start(sp, "tie_knife", "Tying the knife onto the spear", TIE_TICKS, p -> {
            Inventory inv = p.getInventory();
            int knife = find(inv, ModItems.KITCHEN_KNIFE), tie = binding(inv);
            ItemStack now = p.getItemInHand(hand);
            if (knife < 0 || tie < 0 || !is(now, ModItems.WOODEN_SPEAR)) {
                return;
            }
            inv.getItem(knife).shrink(1);
            inv.getItem(tie).shrink(1);
            p.setItemInHand(hand, now.transmuteCopy(ModItems.SPEAR_KNIFE, 1));
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 0.8f, 1.2f);
        });
        return InteractionResult.CONSUME;
    }

    private static int find(Inventory inv, Item item) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (is(inv.getItem(i), item)) return i;
        }
        return -1;
    }

    private static int binding(Inventory inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && BINDINGS.contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath())) {
                return i;
            }
        }
        return -1;
    }
}
