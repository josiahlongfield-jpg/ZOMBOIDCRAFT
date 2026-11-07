package com.zomboidcraft.carry.grid;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.QuickSlots;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.combat.MagazineItem;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.combat.SpearItem;
import com.zomboidcraft.combat.TarkovParts;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * How many inventory cells an item covers, as in Tarkov: a rifle five by two, a magazine one by two, a round one by
 * one. Tarkov's own items carry their size from Tarkov's item list (a local gun's or part's {@code cells}); ours are
 * sized by kind and then by weight. A turned item ({@link #ROTATED}) swaps width and height.
 * <p>
 * {@code config/zomboidcraft/item_sizes.json} ({"zomboidcraft:hammer": [1, 2], ...}) overrides any item by id, so sizes
 * can be tuned on a PC without a new build.
 */
public final class ItemSize {
    /** Turned a quarter: lying the other way in its grid. Only ever set on items that aren't square. */
    public static final DataComponentType<Boolean> ROTATED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("grid_rot"), DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("item_sizes.json");
    private static Map<String, int[]> overrides;
    private static long loadedAt;

    private ItemSize() {
    }

    public static void register() {
        ZomboidCraft.LOGGER.debug("Grid sizes in {}", ROTATED);
    }

    /** Width then height in cells, as it lies now (turned or not). */
    public static int[] of(ItemStack stack) {
        int[] base = base(stack);
        return rotated(stack) ? new int[]{base[1], base[0]} : base;
    }

    public static boolean rotated(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ROTATED));
    }

    /** Whether it fits one of Tarkov's pockets (one cell). */
    public static boolean pocketable(ItemStack stack) {
        int[] s = base(stack);
        return s[0] == 1 && s[1] == 1;
    }

    /** Turns it a quarter; a square item never carries the mark, so it still stacks with its kind. */
    public static void turn(ItemStack stack) {
        int[] s = base(stack);
        if (s[0] == s[1] || rotated(stack)) {
            stack.remove(ROTATED);
        } else {
            stack.set(ROTATED, true);
        }
    }

    /** Width then height as it was made, unturned. */
    public static int[] base(ItemStack stack) {
        if (stack.isEmpty()) {
            return new int[]{1, 1};
        }
        int[] o = overrides().get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (valid(o)) {
            return o.clone();
        }
        LocalGuns.Spec local = LocalGuns.of(stack);
        if (local != null && valid(local.cells)) {
            return local.cells.clone();
        }
        String partId = stack.get(TarkovParts.ID);
        if (partId != null) {
            TarkovParts.Part part = TarkovParts.get(partId);
            return part != null && valid(part.cells) ? part.cells.clone() : new int[]{1, 1};
        }
        if (stack.getItem() instanceof RifleItem rifle) {
            return gun(rifle.type());
        }
        if (stack.getItem() instanceof MagazineItem mag) {
            return switch (mag.type()) {
                case PISTOL, POCKET_PISTOL, RIMFIRE -> new int[]{1, 1};
                case HEAVY_SNIPER -> new int[]{1, 2};
                default -> new int[]{1, 2};
            };
        }
        BagSpec bag = BagItem.spec(stack);
        if (bag != null) {
            // folded flat, as Tarkov's packs and rigs lie in a grid: about the size of what they hold, no bigger than 5x7
            GridShape shape = bag.shape();
            int w = 0, h = 0;
            for (GridShape.Grid g : shape.grids()) {
                w = Math.max(w, g.w());
                h += g.h();
            }
            return new int[]{Math.min(5, Math.max(1, w)), Math.min(7, Math.max(1, Math.min(h, (shape.cells() + w - 1) / Math.max(1, w))))};
        }
        if (stack.getItem() instanceof SpearItem) {
            return new int[]{5, 1};
        }
        if (stack.getItem() instanceof MeleeWeaponItem) {
            if (stack.is(QuickSlots.KNIVES)) {
                return new int[]{2, 1};
            }
            if (stack.is(QuickSlots.MACHETES) || stack.is(QuickSlots.HIP_WEAPONS)) {
                return new int[]{3, 1};
            }
            return new int[]{4, 1};
        }
        // everything else by heft: light things one cell, a bottle or a tool two, anything heavy a block of four
        double kg = Weights.unit(stack);
        if (kg >= 3) {
            return new int[]{2, 2};
        }
        if (kg >= 0.9) {
            return new int[]{1, 2};
        }
        return new int[]{1, 1};
    }

    /** Tarkov's sizes for each kind of our guns (an M4 5x2, a Glock 2x1, an MP5 3x2, an SVD 6x2). */
    private static int[] gun(GunType type) {
        return switch (type) {
            case PISTOL, POCKET_PISTOL, REVOLVER, MAGNUM, MACHINE_PISTOL -> new int[]{2, 1};
            case SMG, SAWN_OFF -> new int[]{3, 2};
            case RIMFIRE, ASSAULT_RIFLE, BATTLE_RIFLE, SUPPRESSED_RIFLE, DOUBLE_BARREL, LEVER_ACTION, CROSSBOW, SHOTGUN -> new int[]{5, 2};
            case HUNTING_RIFLE, PRECISION_RIFLE -> new int[]{6, 2};
            case HEAVY_SNIPER, GRENADE_LAUNCHER -> new int[]{6, 2};
        };
    }

    private static boolean valid(int[] cells) {
        return cells != null && cells.length >= 2 && cells[0] >= 1 && cells[1] >= 1 && cells[0] <= 12 && cells[1] <= 12;
    }

    private static synchronized Map<String, int[]> overrides() {
        long now = System.currentTimeMillis();
        if (overrides != null && now - loadedAt < 10_000) {
            return overrides;
        }
        loadedAt = now;
        Map<String, int[]> map = new HashMap<>();
        if (Files.isRegularFile(FILE)) {
            try (Reader in = Files.newBufferedReader(FILE)) {
                Map<String, int[]> read = new Gson().fromJson(in, new TypeToken<Map<String, int[]>>() { }.getType());
                if (read != null) {
                    map.putAll(read);
                }
            } catch (Exception e) {
                ZomboidCraft.LOGGER.warn("Couldn't read {}: {}", FILE, e.toString());
            }
        }
        overrides = map;
        return map;
    }
}
