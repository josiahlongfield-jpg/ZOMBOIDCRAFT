package com.zomboidcraft.survival;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.clothing.ClothWear;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Garment;
import com.zomboidcraft.clothing.Spot;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.survival.Body.Wound;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Broken glass, Zomboid style. Climbing through a window that still has jagged glass in its frame cuts your hands,
 * forearms or legs, and a shard can stay in the cut; walking on shards with nothing on your feet does the same to
 * your feet. Gloves, sleeves, trousers and shoes keep it off the parts they cover. A shard left in keeps the wound
 * open until it's pulled out (tweezers, or your fingers if you must; see MedSpecs).
 */
public final class Glass {
    /** Ticks after a cut before glass can cut you again, so one climb is one cut. */
    private static final int COOLDOWN = 30;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    /** Every tick, for each player. */
    static void tick(ServerPlayer player) {
        if (!ZcConfig.get().survival.injuries || player.isCreative() || player.isSpectator() || player.isPassenger()) return;
        long now = player.level().getGameTime();
        Long last = LAST.get(player.getUUID());
        if (last != null && now - last < COOLDOWN) return;
        RandomSource random = player.getRandom();
        BlockPos feet = player.blockPosition();
        boolean moving = player.getDeltaMovement().horizontalDistanceSqr() > 0.0004;
        if (jagged(player.level().getBlockState(feet)) || jagged(player.level().getBlockState(feet.above()))) {
            // through the frame: hands and forearms most, then the legs you swing over the sill
            if (moving && random.nextFloat() < 0.5f) {
                float r = random.nextFloat();
                Region side = r < 0.35f ? (random.nextBoolean() ? Region.LEFT_HAND : Region.RIGHT_HAND)
                        : r < 0.65f ? (random.nextBoolean() ? Region.LEFT_FOREARM : Region.RIGHT_FOREARM)
                        : r < 0.85f ? (random.nextBoolean() ? Region.LEFT_THIGH : Region.RIGHT_THIGH)
                        : Region.LOWER_TORSO;
                cut(player, side, "The window glass cuts your ");
                LAST.put(player.getUUID(), now);
            }
        } else if (moving && shards(player.level().getBlockState(feet)) && random.nextFloat() < 0.08f) {
            Region foot = random.nextBoolean() ? Region.LEFT_FOOT : Region.RIGHT_FOOT;
            if (!covered(player, foot)) {
                cut(player, foot, "You step on broken glass. It's in your ");
                LAST.put(player.getUUID(), now);
            }
        }
    }

    private static void cut(ServerPlayer player, Region part, String message) {
        if (covered(player, part) && player.getRandom().nextFloat() < 0.85f) return;
        Body.set(player, Body.get(player).wound(part, Wound.LACERATION, true));
        if (player.getRandom().nextFloat() < 0.6f) Ailments.set(player, Medicine.k("lodged", part), 1);
        player.sendOverlayMessage(Component.literal(message + part.label + ".").withStyle(ChatFormatting.RED));
    }

    /** Whether something worn, and not torn there, covers the part. */
    static boolean covered(ServerPlayer player, Region part) {
        for (ClothingSlot slot : ClothingSlot.values()) {
            ItemStack stack = slot.worn(player);
            Garment g = Clothes.of(stack);
            if (g == null) continue;
            ClothWear wear = ClothWear.of(stack);
            for (Spot spot : Spot.of(part)) {
                if (g.covers().contains(spot) && !wear.open(g, spot, part)) return true;
            }
        }
        return false;
    }

    /** A window with broken glass still in its frame (cleared frames end in _empty). */
    static boolean jagged(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("win") && path.contains("_broken");
    }

    static boolean shards(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().contains("glass_shards");
    }

    private Glass() {
    }
}
