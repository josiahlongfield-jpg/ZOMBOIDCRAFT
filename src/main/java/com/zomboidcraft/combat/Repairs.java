package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Mending weapons at the bench, the way Zomboid does it: duct tape and wood glue for melee weapons, gun oil and spare
 * gun parts for guns, or strip another of the same weapon for its parts. Each repair gives back less than the last
 * (the weapon remembers how often it's been mended), and the Maintenance skill raises what every repair gives back.
 */
public final class Repairs {
    /** How many times a weapon has been repaired. */
    public static final DataComponentType<Integer> REPAIRED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("repaired"), DataComponentType.<Integer>builder()
                    .persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).ignoreSwapAnimation().build());

    /** Each earlier repair leaves the next this much as useful. */
    private static final float FALLOFF = 0.8f;

    public static void register() {
        // loading the class registers the component
    }

    public static boolean repairable(ItemStack weapon) {
        return weapon.isDamageableItem() && (weapon.getItem() instanceof RifleItem || weapon.getItem() instanceof MeleeWeaponItem);
    }

    /**
     * The share of a weapon's full condition a material gives back before skill and earlier repairs: 0 when it can't
     * mend this weapon. Another of the same weapon gives back half of what's left in it.
     */
    public static float base(ItemStack weapon, ItemStack material) {
        if (!repairable(weapon) || material.isEmpty() || material == weapon) return 0;
        boolean gun = weapon.getItem() instanceof RifleItem;
        if (material.is(weapon.getItem())) {
            float left = 1f - (float) material.getDamageValue() / Math.max(1, material.getMaxDamage());
            return Math.max(0.05f, 0.5f * left);
        }
        if (material.is(ModItems.DUCT_TAPE)) return gun ? 0.06f : 0.14f;
        if (material.is(ModItems.WOOD_GLUE)) return gun ? 0 : 0.22f;
        if (material.is(ModItems.NAILS)) return nailed(weapon) ? 0.2f : 0;
        if (material.is(ModItems.GUN_OIL)) return gun ? 0.12f : 0;
        if (material.is(ModItems.GUN_PARTS)) return gun ? 0.35f : 0;
        return 0;
    }

    /** Zomboid mends its nailed clubs with more nails. */
    private static boolean nailed(ItemStack weapon) {
        WeaponSpecs.WeaponSpec spec = Weapons.spec(weapon);
        return spec != null && (spec.name().equals("spiked_bat") || spec.name().endsWith("_nails"));
    }

    public static int times(ItemStack weapon) {
        return weapon.getOrDefault(REPAIRED, 0);
    }

    /** What the Maintenance skill makes of a repair: 60% at level 0 up to 120% at level 10. */
    public static float skill(Player player) {
        return 0.6f + 0.06f * Skills.level(player, Skill.MAINTENANCE);
    }

    /** Durability points a repair with this material would give back, capped at what's worn. */
    public static int gain(Player player, ItemStack weapon, ItemStack material) {
        float share = base(weapon, material) * skill(player) * (float) Math.pow(FALLOFF, times(weapon));
        return Math.min(weapon.getDamageValue(), Math.max(1, Math.round(share * weapon.getMaxDamage())));
    }

    /** Mends the weapon in one carried slot with one of what's in another. */
    static boolean repair(AbstractContainerMenu menu, Player player, int weaponSlot, int materialSlot) {
        if (weaponSlot == materialSlot) return false;
        Slot ws = menu.slots.get(weaponSlot), ms = menu.slots.get(materialSlot);
        ItemStack weapon = ws.getItem(), material = ms.getItem();
        Wear.migrate(weapon);
        if (weapon.getDamageValue() <= 0 || base(weapon, material) <= 0 || !ms.mayPickup(player)) return false;
        int gain = gain(player, weapon, material);
        weapon.setDamageValue(weapon.getDamageValue() - gain);
        weapon.set(REPAIRED, times(weapon) + 1);
        material.shrink(1);
        if (material.isEmpty()) {
            ms.setByPlayer(ItemStack.EMPTY);
        } else {
            ms.setChanged();
        }
        ws.setChanged();
        if (player instanceof ServerPlayer server) Skills.addXp(server, Skill.MAINTENANCE, 4);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.25f, 1.6f);
        return true;
    }

    private Repairs() {
    }
}
