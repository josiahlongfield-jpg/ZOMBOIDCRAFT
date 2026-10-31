package com.zomboidcraft.loot.items;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.TimedActions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.level.Level;

/**
 * A sealed can, Zomboid style: it keeps for ever, but there's nothing to eat until it's opened, and that takes a can
 * opener (or a knife, in a pinch: anything in zomboidcraft:can_openers). Opened, it's food like any other, leaves an
 * empty can (LootFood hands it back), and goes off in a few days like leftovers.
 */
public class SealedCanItem extends LootItem {
    public static final TagKey<Item> OPENERS = TagKey.create(Registries.ITEM, ZomboidCraft.id("can_openers"));

    public SealedCanItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.has(DataComponents.CONSUMABLE)) {
            return super.use(level, player, hand);      // already opened: eat it
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (!hasOpener(sp.getInventory())) {
            sp.sendOverlayMessage(Component.literal("You need a can opener (or a knife) to open this.")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResult.FAIL;
        }
        ItemStack what = stack.copyWithCount(1);
        TimedActions.start(sp, "open_can", "Opening " + stack.getHoverName().getString(), 40, p -> {
            ItemStack now = p.getItemInHand(hand);
            if (!ItemStack.isSameItemSameComponents(now, what)) {
                return;
            }
            now.shrink(1);
            ItemStack opened = open(what, p.level().getGameTime());
            if (now.isEmpty()) {
                p.setItemInHand(hand, opened);
            } else if (!p.getInventory().add(opened)) {
                p.spawnAtLocation((net.minecraft.server.level.ServerLevel) p.level(), opened);
            }
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.5f, 1.6f);
        });
        return InteractionResult.SUCCESS;
    }

    private static boolean hasOpener(Inventory inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).is(OPENERS)) {
                return true;
            }
        }
        return false;
    }

    /** The same can, opened: its food from the roster, an empty can when it's done, and a clock that now runs. */
    public static ItemStack open(ItemStack sealed, long now) {
        ItemStack opened = sealed.copyWithCount(1);
        LootSpec spec = LootItems.spec(sealed);
        if (spec == null || spec.food() == null) {
            return opened;
        }
        LootSpec.Food food = spec.food();
        opened.set(DataComponents.FOOD, new FoodProperties(food.hunger(), food.saturation(), food.is(LootSpec.Food.ALWAYS)));
        opened.set(DataComponents.CONSUMABLE, Consumables.DEFAULT_FOOD);
        Spoil spoil = new Spoil(0f, now, Spoil.OUT, Spoil.OPEN_FRESH, Spoil.OPEN_ROT, true, true);
        opened.set(Spoil.TYPE, spoil);
        Spoil.name(opened, spoil);
        return opened;
    }
}
