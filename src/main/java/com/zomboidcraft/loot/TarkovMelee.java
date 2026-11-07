package com.zomboidcraft.loot;

import com.zomboidcraft.combat.LocalHands;
import com.zomboidcraft.combat.MeleeWeaponItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * Melee in loot is Tarkov's (Josiah 2026-11-07: "We won't be able to use any minecraft or zomboid melee weapons, all
 * of them will need to be tarkov ones"). Wherever a loot table drops one of our melee weapons, the drop becomes one of
 * the Tarkov blades on this computer ({@link LocalHands}, config/zomboidcraft/hands), picked by its rarity, so melee
 * turns up as often as before but is always a Tarkov one. With no Tarkov blades on the computer the drop is dropped.
 * Our tools that also hit (hammer, crowbar, shovel, axes, kitchen knife, frying pan) stay for their jobs.
 */
public final class TarkovMelee {
    /** Our melee items that are only weapons, never tools. */
    static final Set<String> WEAPONS = Set.of("pipe_wrench", "lead_pipe", "golf_club", "hockey_stick", "baseball_bat",
            "spiked_bat", "machete", "tomahawk", "katana", "wooden_spear", "fire_hardened_spear", "spear_knife",
            "plank_nails", "cudgel_nails");

    /** Each Tarkov blade's rarity letter (as the guns: C S R P G); one not listed is Common. */
    static final Map<String, String> RARITY = Map.of("tarkov_kabar", "C", "tarkov_kukri", "S");
    static final Map<String, Double> WEIGHT = Map.of("C", 1.0, "S", 0.5, "R", 0.25, "P", 0.1, "G", 0.04);

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            for (int i = 0; i < drops.size(); i++) {
                ItemStack s = drops.get(i);
                if (!(s.getItem() instanceof MeleeWeaponItem) || s.get(LocalHands.ID) != null) continue;
                String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                if (WEAPONS.contains(id)) {
                    List<LocalHands.Spec> all = blades();
                    drops.set(i, all.isEmpty() ? ItemStack.EMPTY : pick(all, context.getRandom()));
                }
            }
            drops.removeIf(ItemStack::isEmpty);
        });
    }

    /** The Tarkov blades on this computer (not its grenades). */
    static List<LocalHands.Spec> blades() {
        List<LocalHands.Spec> out = new ArrayList<>();
        for (LocalHands.Spec s : LocalHands.all()) {
            if (s.anchor == null) continue;
            var item = BuiltInRegistries.ITEM.getOptional(com.zomboidcraft.ZomboidCraft.id(s.anchor)).orElse(null);
            if (!(item instanceof MeleeWeaponItem)) continue;
            out.add(s);
        }
        return out;
    }

    static ItemStack pick(List<LocalHands.Spec> specs, RandomSource random) {
        double total = 0;
        for (LocalHands.Spec s : specs) total += weight(s);
        double r = random.nextDouble() * total;
        for (LocalHands.Spec s : specs) {
            r -= weight(s);
            if (r < 0) return LocalHands.make(s);
        }
        return LocalHands.make(specs.get(specs.size() - 1));
    }

    static double weight(LocalHands.Spec s) {
        return WEIGHT.getOrDefault(RARITY.getOrDefault(s.id, "C"), 1.0);
    }

    private TarkovMelee() {
    }
}
