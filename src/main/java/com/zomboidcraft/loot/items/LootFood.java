package com.zomboidcraft.loot.items;

import com.zomboidcraft.survival.SurvivalStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * What eating or drinking a roster item does beyond filling you: thirst back (salty and dry food takes some), and
 * Zomboid's catches: stale food sits badly, rotten food or raw meat can make you sick, and strong drink makes you
 * drunk.
 */
public final class LootFood {
    /** The roster's thirst for this stack, or null when it isn't roster food. */
    public static Float thirst(ItemStack stack) {
        LootSpec spec = LootItems.spec(stack);
        return spec == null || spec.food() == null ? null : spec.food().thirst();
    }

    public static void onConsumed(Player player, ItemStack stack) {
        LootSpec spec = LootItems.spec(stack);
        if (spec == null || spec.food() == null || !(player instanceof ServerPlayer sp)) {
            return;
        }
        LootSpec.Food food = spec.food();
        RandomSource random = sp.getRandom();
        if (food.thirst() < 0) {
            SurvivalStats stats = SurvivalStats.get(sp);
            SurvivalStats.set(sp, stats.withThirst(stats.thirst() + food.thirst()));
        }
        Spoil spoil = stack.get(Spoil.TYPE);
        if (spoil != null && spoil.opened() && food.left() != null) {
            net.minecraft.world.item.Item left = LootItems.item(food.left());
            if (left != null) {
                ItemStack can = new ItemStack(left);
                if (!sp.getInventory().add(can)) {
                    sp.spawnAtLocation((net.minecraft.server.level.ServerLevel) sp.level(), can);
                }
            }
        }
        int state = spoil == null ? Spoil.FRESH : spoil.at(sp.level().getGameTime(), spoil.where()).state();
        if (state == Spoil.ROTTEN) {
            sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 1));
            sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 300, 0));
            if (random.nextFloat() < 0.4f) {
                sp.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            }
            sp.sendOverlayMessage(Component.literal("That was rotten. You feel sick.").withStyle(ChatFormatting.GRAY));
        } else if (state == Spoil.STALE) {
            sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0));
        }
        if (food.is(LootSpec.Food.RAW) && random.nextFloat() < 0.5f) {
            sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, 400, 0));
            sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
        }
        if (food.alcohol() >= 2) {
            sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120 * food.alcohol(), 0));
        }
    }

    private LootFood() {
    }
}
