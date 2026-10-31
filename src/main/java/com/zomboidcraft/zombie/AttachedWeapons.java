package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Zomboid's Chance Of Attached Weapon (zombieLore.attachedWeaponPercent, 6%): a share of the dead still carry what
 * someone tried to stop them with, a kitchen knife in the back, a machete or a tomahawk in a shoulder, a fire axe in
 * the back. It's drawn as the weapon's own model standing in the body (client BodyWeaponLayer), rolled from the UUID
 * so every player sees the same one, and it falls out beside the body when the zombie goes down, worn from use.
 */
public final class AttachedWeapons {
    /** Where it's stuck. */
    public enum Spot { BACK, CHEST, SHOULDER }

    public record Stuck(String item, Spot spot) {
        @Override
        public String toString() {
            return item.replace('_', ' ') + " in the " + spot.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private record Kind(String item, int weight, Spot... spots) {
    }

    /** What turns up, and where each one sticks: knives anywhere, the heavier blades in the back and shoulders. */
    private static final List<Kind> KINDS = List.of(
            new Kind("kitchen_knife", 45, Spot.BACK, Spot.CHEST, Spot.SHOULDER),
            new Kind("machete", 16, Spot.BACK, Spot.SHOULDER),
            new Kind("tomahawk", 16, Spot.BACK, Spot.SHOULDER),
            new Kind("fire_axe", 9, Spot.BACK),
            new Kind("katana", 4, Spot.BACK, Spot.CHEST));
    private static final int TOTAL = KINDS.stream().mapToInt(Kind::weight).sum();

    public static @Nullable Stuck of(Entity zombie) {
        if (!ZombieTypes.isWalker(zombie) || Lore.roll(zombie, 10) * 100 >= ZcConfig.get().zombieLore.attachedWeaponPercent) {
            return null;
        }
        int pick = (int) (Lore.roll(zombie, 11) * TOTAL);
        for (Kind k : KINDS) {
            pick -= k.weight();
            if (pick < 0) {
                return new Stuck(k.item(), k.spots()[(int) (Lore.roll(zombie, 12) * k.spots().length)]);
            }
        }
        return null;
    }

    public static ItemStack stack(Stuck stuck) {
        Item item = BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(stuck.item()));
        return new ItemStack(item);
    }

    /** It goes down, and the blade comes out with it: on the ground beside the body, well used. */
    public static void drop(ServerLevel level, Entity zombie) {
        Stuck stuck = of(zombie);
        if (stuck == null) {
            return;
        }
        ItemStack stack = stack(stuck);
        if (stack.isEmpty()) {
            return;
        }
        if (stack.isDamageableItem()) {
            stack.setDamageValue((int) (stack.getMaxDamage() * (0.45 + Lore.roll(zombie, 13) * 0.4)));
        }
        ItemEntity item = new ItemEntity(level, zombie.getX(), zombie.getY() + 0.3, zombie.getZ(), stack);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    private AttachedWeapons() {
    }
}
