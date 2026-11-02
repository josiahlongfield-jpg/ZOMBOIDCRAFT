package com.zomboidcraft.decor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.loot.items.LootSpec;
import com.zomboidcraft.loot.items.LootSpecs;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Base decor (wave 4 stage 5): the 100 pieces in data/zomboidcraft/decor.json (tools/items/loot/decor.py), each with
 * a job, Zomboid style. Every piece adds comfort to the room it stands in ({@link Comfort}); some need power and give
 * light; some are played from the G menu to cut boredom; some are loud. A piece is a block once set down
 * ({@link DecorBlock}, "decor_ID"), its loot item in the hand. Only pieces that have their model (a live roster row)
 * get a block, so the list can be ahead of the models.
 */
public final class Decor {
    /** What a piece does. */
    public record Spec(String id, String name, String rarity, double kg, int looks, int comfort, boolean power,
                       boolean big, boolean lightSource, boolean toggle, int play, int noise, int light, String special) {
        /** Highest LOOK a block can show (blockstates hold up to 16; more looks repeat). */
        public int maxLook() {
            return Math.max(0, Math.min(DecorBlock.LOOKS, looks) - 1);
        }
    }

    private static final Map<String, Spec> SPECS = load();
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();

    private Decor() {
    }

    private static Map<String, Spec> load() {
        Map<String, Spec> out = new LinkedHashMap<>();
        try (InputStream in = Decor.class.getResourceAsStream("/data/zomboidcraft/decor.json")) {
            if (in == null) {
                return out;
            }
            JsonObject all = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
            for (String id : all.keySet()) {
                JsonObject o = all.getAsJsonObject(id);
                out.put(id, new Spec(id, o.get("name").getAsString(), o.get("rarity").getAsString(),
                        o.get("kg").getAsDouble(), o.get("looks").getAsInt(), o.get("comfort").getAsInt(),
                        o.get("power").getAsBoolean(), o.get("big").getAsBoolean(), o.get("light_source").getAsBoolean(),
                        o.get("toggle").getAsBoolean(), o.get("play").getAsInt(), o.get("noise").getAsInt(),
                        o.get("light").getAsInt(), o.get("special").isJsonNull() ? null : o.get("special").getAsString()));
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.warn("Couldn't read the decor list", e);
        }
        return out;
    }

    /** The piece with this id, or null. */
    public static Spec spec(String id) {
        return SPECS.get(id);
    }

    public static Map<String, Spec> all() {
        return Collections.unmodifiableMap(SPECS);
    }

    /** The set-down block of a modelled piece, or null. */
    public static Block block(String id) {
        return BLOCKS.get(id);
    }

    /** Registers the blocks of every piece that has a live roster row. Before LootItems.register (its items need them). */
    public static void registerBlocks() {
        for (LootSpec spec : LootSpecs.ALL) {
            Spec d = SPECS.get(spec.id());
            if (d == null || !spec.loot() || d.special() != null && d.special().equals("hand_truck")) {
                continue;
            }
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("decor_" + d.id()));
            BlockBehaviour.Properties p = BlockBehaviour.Properties.of().setId(key).strength(-1f, 4f).noOcclusion()
                    .forceSolidOff().sound(d.kg() >= 20 ? SoundType.WOOD : SoundType.LANTERN);
            int light = d.lightSource() ? d.light() : glowOf(d.special());
            boolean someLooks = "glows".equals(d.special());   // only the posters printed in day-glo ink
            if (light > 0) {
                p.lightLevel(s -> s.getValue(DecorBlock.ON) && (!someLooks || s.getValue(DecorBlock.LOOK) % 2 == 0) ? light : 0);
            }
            boolean holds = d.special() != null && DecorUses.HOLDS.contains(d.special());
            BLOCKS.put(d.id(), Registry.register(BuiltInRegistries.BLOCK, key,
                    holds ? new DecorBlock.Holding(p, d) : new DecorBlock(p, d)));
        }
        DecorEntity.register(BLOCKS.values().stream().filter(b -> b instanceof DecorBlock.Holding).toList());
    }

    /** Light from a piece that isn't a lamp: a stove's fire, the sun through stained glass, a poster under black light. */
    private static int glowOf(String special) {
        if (special == null) return 0;
        return switch (special) {
            case "stove_heat" -> 10;
            case "coloured_light" -> 9;
            case "glows" -> 4;
            default -> 0;
        };
    }

    public static void register() {
        Comfort.register();
        DecorNet.register();
        // a piece found in the world comes in one of its looks (the word on a neon sign, a lava lamp's colours...)
        net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            for (net.minecraft.world.item.ItemStack stack : drops) {
                if (stack.getItem() instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() instanceof DecorBlock db
                        && db.spec.maxLook() > 0) {
                    int look = context.getLevel().getRandom().nextInt(db.spec.maxLook() + 1);
                    net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                            stack, tag -> {
                                if (!tag.contains("look")) {
                                    tag.putInt("look", look);
                                }
                            });
                }
            }
        });
    }
}
