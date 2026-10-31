package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.rolls.Rolls;
import com.zomboidcraft.combat.rolls.Stat;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Melee condition the way Zomboid keeps it (Josiah 2026-10-31: Zomboid's numbers): a weapon has a small condition
 * (WeaponSpec.durability, 5 to 15) and each hit has a 1 in N chance of taking a point off (WeaponSpec.lower, from
 * 2 for a kitchen knife to 70 for a crowbar). Maintenance and the weapon's own skill make that chance smaller, as in
 * Zomboid, and a rolled copy's Durability stretches N. At nothing left the weapon is broken: it stays in your bags,
 * hits like a bare fist, and can be mended (Repairs).
 */
public final class Wear {
    /** Set once a stack's condition has been moved over from the old Minecraft-style wear (hundreds of points). */
    public static final DataComponentType<Boolean> ZOMBOID = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("zomboid_condition"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).ignoreSwapAnimation().build());

    /** Each weapon's full condition under the old wear, to carry what a copy had left over to the new. */
    private static final Map<String, Integer> OLD = Map.ofEntries(
            Map.entry("kitchen_knife", 140), Map.entry("hammer", 260), Map.entry("frying_pan", 170), Map.entry("crowbar", 480),
            Map.entry("pipe_wrench", 420), Map.entry("lead_pipe", 380), Map.entry("golf_club", 110), Map.entry("hockey_stick", 120),
            Map.entry("baseball_bat", 220), Map.entry("spiked_bat", 200), Map.entry("shovel", 300), Map.entry("machete", 300),
            Map.entry("katana", 360), Map.entry("fire_axe", 420), Map.entry("tomahawk", 350), Map.entry("sledgehammer", 600));

    private Wear() {
    }

    public static void register() {
        // loading the class registers the component
    }

    public static boolean broken(ItemStack stack) {
        return stack.getItem() instanceof MeleeWeaponItem && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage();
    }

    /** The N in the 1 in N chance a hit wears this weapon, for this player. */
    public static double oneIn(Player player, ItemStack stack, WeaponSpecs.WeaponSpec spec) {
        return spec.lower() * Rolls.multiplier(stack, Stat.DURABILITY)
                + 2 * Skills.level(player, Skill.MAINTENANCE) + Skills.level(player, spec.skill());
    }

    /**
     * A hit (or a tool's use) on a melee weapon: each of {@code uses} has its chance of taking a point off. Anything
     * else that wears is hurt as Minecraft does it.
     */
    public static void use(ItemStack stack, Player player, InteractionHand hand, int uses) {
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        if (spec == null) {
            stack.hurtAndBreak(uses, player, hand);
            return;
        }
        migrate(stack);
        if (!ZcConfig.get().combat.weaponWear || player.isCreative() || !stack.isDamageableItem() || broken(stack)) {
            return;
        }
        double n = Math.max(1, oneIn(player, stack, spec));
        int lost = 0;
        for (int i = 0; i < uses; i++) {
            if (player.getRandom().nextDouble() * n < 1) lost++;
        }
        if (lost == 0) {
            return;
        }
        int left = stack.getMaxDamage() - stack.getDamageValue() - lost;
        stack.setDamageValue(Math.min(stack.getMaxDamage(), stack.getMaxDamage() - left));
        if (left <= 0 && player instanceof ServerPlayer sp) {
            sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 0.8f, 0.9f);
            sp.sendOverlayMessage(Component.literal("Your " + stack.getHoverName().getString() + " broke")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** The share of condition left (0 = broken), right for a copy not yet moved over from the old wear too. */
    public static float left(ItemStack stack) {
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        int max = stack.getMaxDamage();
        if (spec != null && !Boolean.TRUE.equals(stack.get(ZOMBOID))) {
            max = Math.max(max, OLD.getOrDefault(spec.name(), max));
        }
        return 1f - Math.min(1f, (float) stack.getDamageValue() / Math.max(1, max));
    }

    /**
     * Moves a copy from the old wear to Zomboid's: its share of condition left carries over, its full condition is the
     * weapon's own (a roll now stretches the chance instead), and nothing is broken by the move.
     */
    public static void migrate(ItemStack stack) {
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        if (spec == null || Boolean.TRUE.equals(stack.get(ZOMBOID))) {
            return;
        }
        int own = stack.getMaxDamage();     // a rolled copy carried its own, larger full condition
        int oldMax = own > 40 ? own : OLD.getOrDefault(spec.name(), spec.durability());
        float worn = Math.clamp((float) stack.getDamageValue() / Math.max(1, oldMax), 0f, 1f);
        int max = stack.getItem().components().getOrDefault(DataComponents.MAX_DAMAGE, spec.durability());
        stack.set(DataComponents.MAX_DAMAGE, max);
        stack.setDamageValue(Math.clamp(Math.round(worn * max), 0, max - 1));
        stack.set(ZOMBOID, true);
    }
}
