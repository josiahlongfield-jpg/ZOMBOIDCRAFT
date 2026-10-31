package com.zomboidcraft.loot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.AttachmentItem;
import com.zomboidcraft.combat.MagazineItem;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.combat.rolls.Rarity;
import com.zomboidcraft.loot.items.LootItems;
import com.zomboidcraft.loot.items.LootSpec;
import com.zomboidcraft.zombie.ZombieTypes;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's Loot sandbox settings, applied to every container we stock (shop/, car/ and the story tables) as it is
 * first opened. Our tables are tuned at Zomboid's Apocalypse values, so each setting works as a ratio to its default:
 * <ul>
 * <li>the 21 per-category multipliers (Food, Canned Food, Literature ... Tools), each item by its Zomboid category
 * (data/zomboidcraft/loot_categories.json, from the roster; guns, ammo and melee by their kind);</li>
 * <li>the rarity factors, read against our tiers: Common is Common, Scarce Normal, Rare Rare, Prized Extremely Rare,
 * Grail Insane;</li>
 * <li>Rolls Multiplier, on everything;</li>
 * <li>Zombie Population Loot Effect: more and better loot where the dead crowd round;</li>
 * <li>pre-looted containers: Maximum Looted Building Chance, reached over Days Until Maximum Looted (per container
 * here, where Zomboid goes by building); diminished loot the same way;</li>
 * <li>the Loot Item Removal List, and with Remove Story Loot / Remove Zombie Loot the story and zombie tables too.</li>
 * </ul>
 * A ratio under 1 keeps each item with that chance; over 1, it may add another of it.
 */
public final class LootSettings {
    private static final Map<String, String> CATEGORY = load();
    private static final Map<String, Double> DEFAULTS = Map.ofEntries(
            Map.entry("Food", 0.8), Map.entry("Literature", 0.6), Map.entry("SkillBook", 0.6),
            Map.entry("RecipeResource", 0.6), Map.entry("Medical", 0.6), Map.entry("SurvivalGears", 0.6),
            Map.entry("CannedFood", 0.6), Map.entry("Weapon", 0.6), Map.entry("RangedWeapon", 1.2),
            Map.entry("Ammo", 0.6), Map.entry("Mechanics", 0.6), Map.entry("Other", 0.8), Map.entry("Clothing", 0.6),
            Map.entry("Container", 0.6), Map.entry("Key", 0.4), Map.entry("Media", 0.6), Map.entry("Memento", 0.6),
            Map.entry("Cookware", 0.6), Map.entry("Material", 0.6), Map.entry("Farming", 0.6), Map.entry("Tool", 0.6));
    private static final double DAY = 24000;
    private static final Set<String> EXEMPT = Set.of("zomboidcraft:shop/crusader_kit", "zomboidcraft:shop/museum_armour",
            "zomboidcraft:shop/kingsbridge_stash");

    private LootSettings() {
    }

    private static Map<String, String> load() {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = LootSettings.class.getResourceAsStream("/data/zomboidcraft/loot_categories.json")) {
            if (in != null) {
                JsonObject o = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
                o.keySet().forEach(k -> out.put(k, o.get(k).getAsString()));
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read the loot categories", e);
        }
        return out;
    }

    /** Whether this life has had its starter kit (not kept on death: a new life gets a new one). */
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> KIT =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(ZomboidCraft.id("starter_kit"),
                    b -> b.persistent(com.mojang.serialization.Codec.BOOL).initializer(() -> false));
    private static final String[] STARTER = {"chips_bag", "water_bottle", "school_backpack", "baseball_bat", "hammer"};

    private static void kit(net.minecraft.server.level.ServerPlayer player) {
        if (!ZcConfig.get().loot.starterKit || player.getAttachedOrElse(KIT, false) || player.isSpectator()) {
            return;
        }
        player.setAttached(KIT, true);
        for (String id : STARTER) {
            Item item = BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(id));
            if (item != net.minecraft.world.item.Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (!player.getInventory().add(stack)) {
                    player.spawnAtLocation((ServerLevel) player.level(), stack);
                }
            }
        }
    }

    public static void register() {
        LootRespawn.register();
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> server.execute(() -> kit(handler.getPlayer())));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> kit(newPlayer));
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            String id = table.unwrapKey().map(k -> k.identifier().toString()).orElse("");
            // whole sets and hand-placed stashes are what they are: never thinned or pre-looted
            boolean ours = (id.startsWith("zomboidcraft:shop/") || id.startsWith("zomboidcraft:car/")) && !EXEMPT.contains(id);
            boolean story = id.startsWith("zomboidcraft:shop/story_");
            boolean zombie = id.contains("entities/") && id.contains("zombie") || id.startsWith("zomboidcraft:entities/");
            if (!ours && !zombie || drops.isEmpty()) {
                return;
            }
            apply(context.getLevel(), context.getOptional(LootContextParams.ORIGIN), context.getRandom(),
                    drops, ours, story, zombie);
            if (ours && id.startsWith("zomboidcraft:shop/")) {
                LootRespawn.rolled(context.getLevel(), context.getOptional(LootContextParams.ORIGIN), id);
            }
        });
    }

    static void apply(ServerLevel level, Vec3 at, RandomSource random, List<ItemStack> drops, boolean ours,
                      boolean story, boolean zombie) {
        ZcConfig.Loot cfg = ZcConfig.get().loot;
        Set<String> removed = removal(cfg.removalList);
        boolean remove = ours && !story || story && cfg.removeStoryLoot || zombie && cfg.removeZombieLoot;
        if (remove && !removed.isEmpty()) {
            drops.removeIf(s -> removed.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()));
        }
        if (!ours) {
            return;
        }
        double days = level.getGameTime() / DAY + LootArea.monthsSinceApo(cfg) * 30;
        // already looted before you got here
        double looted = cfg.maximumLooted / 100.0 * ramp(days, cfg.daysUntilMaximumLooted);
        if (!story && looted > 0 && at != null) {
            BlockPos pos = BlockPos.containing(at);
            if (LootArea.rooms(level, pos) > cfg.maximumLootedBuildingRooms) {
                looted = 0;
            } else if (LootArea.rural(level, pos)) {
                looted *= cfg.ruralLooted;
            }
        }
        if (!story && random.nextDouble() < looted) {
            drops.removeIf(s -> random.nextFloat() < 0.9f);
            return;
        }
        double diminished = cfg.maximumDiminished / 100.0 * ramp(days, cfg.daysUntilMaximumDiminished);
        double crowd = 1;
        if (cfg.zombiePopLootEffect > 0 && at != null) {
            int n = level.getEntitiesOfClass(Zombie.class, new AABB(at, at).inflate(32), ZombieTypes::isWalker).size();
            crowd = 1 + cfg.zombiePopLootEffect * Math.min(n, 20) / 200.0;
        }
        List<ItemStack> extra = new ArrayList<>();
        for (var it = drops.iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            double m = cfg.rollsMultiplier * category(cfg, s) * rarity(cfg, s) * (1 - diminished);
            if (crowd > 1 && rarity(s) != Rarity.COMMON) {
                m *= crowd;
            }
            if (m < 1) {
                if (random.nextDouble() >= m) {
                    it.remove();
                }
                continue;
            }
            for (double left = m - 1; left > 0; left -= 1) {
                if (random.nextDouble() < left) {
                    extra.add(s.copy());
                }
            }
        }
        drops.addAll(extra);
    }

    private static double ramp(double days, double until) {
        return until <= 0 ? 1 : Math.min(1, days / until);
    }

    private static Set<String> removal(String list) {
        Set<String> out = new HashSet<>();
        for (String s : list.split(",")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            int colon = t.indexOf(':');
            if (colon >= 0) {
                t = t.substring(colon + 1);
            }
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** Zomboid's category for an item: the roster's, else by what kind of thing it is. */
    public static String category(ItemStack stack) {
        Item item = stack.getItem();
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        LootSpec spec = LootItems.spec(stack);
        if (spec != null && spec.food() != null) {
            return spec.food().perishes() ? "Food" : "CannedFood";
        }
        String c = CATEGORY.get(id);
        if (c != null) {
            return c;
        }
        if (item instanceof RifleItem || item instanceof AttachmentItem) return "RangedWeapon";
        if (item instanceof MagazineItem || id.endsWith("_ammo") || id.endsWith("_shell") || id.endsWith("_bolt")) return "Ammo";
        if (item instanceof MeleeWeaponItem) return "Weapon";
        return "Other";
    }

    private static double category(ZcConfig.Loot cfg, ItemStack s) {
        String c = category(s);
        Double def = DEFAULTS.get(c);
        double now = switch (c) {
            case "Food" -> cfg.food;
            case "Literature" -> cfg.literature;
            case "SkillBook" -> cfg.skillBook;
            case "RecipeResource" -> cfg.recipeResource;
            case "Medical" -> cfg.medical;
            case "SurvivalGears" -> cfg.survivalGears;
            case "CannedFood" -> cfg.cannedFood;
            case "Weapon" -> cfg.weapon;
            case "RangedWeapon" -> cfg.rangedWeapon;
            case "Ammo" -> cfg.ammo;
            case "Mechanics" -> cfg.mechanics;
            case "Other" -> cfg.other;
            case "Clothing" -> cfg.clothing;
            case "Container" -> cfg.container;
            case "Key" -> cfg.key;
            case "Media" -> cfg.media;
            case "Memento" -> cfg.memento;
            case "Cookware" -> cfg.cookware;
            case "Material" -> cfg.material;
            case "Farming" -> cfg.farming;
            case "Tool" -> cfg.tool;
            default -> def == null ? 1 : def;
        };
        return def == null || def <= 0 ? 1 : now / def;
    }

    private static Rarity rarity(ItemStack s) {
        LootSpec spec = LootItems.spec(s);
        return spec == null ? Rarity.COMMON : spec.rarity();
    }

    private static double rarity(ZcConfig.Loot cfg, ItemStack s) {
        return switch (rarity(s)) {
            case COMMON -> cfg.commonFactor / 2.0;
            case SCARCE -> cfg.normalFactor / 1.0;
            case RARE -> cfg.rareFactor / 0.6;
            case PRIZED -> cfg.extremeFactor / 0.2;
            case GRAIL -> cfg.insaneFactor / 0.05;
        };
    }
}
