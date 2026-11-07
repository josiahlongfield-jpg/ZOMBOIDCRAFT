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
 * Our tools stay for their jobs (Josiah 2026-11-07, "Weapons go, tools stay"); one with a Tarkov twin on this computer
 * (combat/Retired.TWIN_TOOLS: knife, crowbar, shovel, axe, sledgehammer) turns up as that twin.
 */
public final class TarkovMelee {
    /** Our melee items that are only weapons, never tools (as combat/Retired.MELEE). */
    static final Set<String> WEAPONS = com.zomboidcraft.combat.Retired.MELEE;

    /** Each Tarkov blade's rarity letter (as the guns: C S R P G); one not listed is Common. */
    static final Map<String, String> RARITY = Map.ofEntries(
            Map.entry("tarkov_kabar", "C"), Map.entry("tarkov_kukri", "S"),
            Map.entry("tarkov_a2607", "C"), Map.entry("tarkov_a2607d", "C"), Map.entry("tarkov_6x5", "C"),
            Map.entry("tarkov_crowbar", "C"), Map.entry("tarkov_mpl50", "C"), Map.entry("tarkov_taran", "C"),
            Map.entry("tarkov_camperaxe", "C"),
            Map.entry("tarkov_fulcrum", "S"), Map.entry("tarkov_kiba", "S"), Map.entry("tarkov_sp8", "S"),
            Map.entry("tarkov_taiga", "S"), Map.entry("tarkov_crashaxe", "S"), Map.entry("tarkov_rebel", "S"),
            Map.entry("tarkov_voodoo", "S"), Map.entry("tarkov_akula", "S"),
            Map.entry("tarkov_m2sword", "R"), Map.entry("tarkov_gladius", "R"), Map.entry("tarkov_cultist", "R"),
            Map.entry("tarkov_wycc", "R"),
            Map.entry("tarkov_db2020", "P"), Map.entry("tarkov_labrys", "P"), Map.entry("tarkov_scythe", "P"));
    static final Map<String, Double> WEIGHT = Map.of("C", 1.0, "S", 0.5, "R", 0.25, "P", 0.1, "G", 0.04);

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            for (int i = 0; i < drops.size(); i++) {
                ItemStack s = drops.get(i);
                if (!(s.getItem() instanceof MeleeWeaponItem) || s.get(LocalHands.ID) != null) continue;
                String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                if (WEAPONS.contains(id)) {
                    List<LocalHands.Spec> all = blades(null);
                    drops.set(i, all.isEmpty() ? ItemStack.EMPTY : pick(all, context.getRandom()));
                } else if (com.zomboidcraft.combat.Retired.TWIN_TOOLS.contains(id)) {
                    // a tool with a Tarkov twin on this computer turns up as that twin, which does its jobs
                    List<LocalHands.Spec> twins = blades(id);
                    if (!twins.isEmpty()) drops.set(i, pick(twins, context.getRandom()));
                }
            }
            drops.removeIf(ItemStack::isEmpty);
        });
    }

    /** The Tarkov blades on this computer (not its grenades), or only those on that item of ours. */
    static List<LocalHands.Spec> blades(String anchor) {
        List<LocalHands.Spec> out = new ArrayList<>();
        for (LocalHands.Spec s : LocalHands.all()) {
            if (s.anchor == null) continue;
            var item = BuiltInRegistries.ITEM.getOptional(com.zomboidcraft.ZomboidCraft.id(s.anchor)).orElse(null);
            if (!(item instanceof MeleeWeaponItem)) continue;
            if (anchor == null || anchor.equals(s.anchor)) out.add(s);
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
