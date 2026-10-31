package com.zomboidcraft.npc;

import com.zomboidcraft.barricade.BarricadeBlock;
import com.zomboidcraft.building.BuildBlocks;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.survival.BandageItem;
import com.zomboidcraft.survival.Drinks;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * A companion's home and her job there, and the shape of her day.
 * <p>
 * Give her a home ({@code /zc npc home}, where you stand) and a job ({@code /zc npc job}), then let her wait (G) and she
 * keeps the place going on her own: a guard walks the doors and windows and shouts when something's coming; a carpenter
 * patches beaten barricades and walls with planks from the stores; a farmer waters, harvests and replants the planters
 * and crop rows; a cook turns raw food from the stores into cooked over a fire or stove; a scavenger heads out to search
 * the buildings round about and brings back what she finds; a medic patches up whoever's hurt; a hauler tidies whatever's
 * lying about into the stores. The stores are every chest, barrel and shelf within STORE_RADIUS of home.
 * <p>
 * Her day runs by the clock: a day shift works from six till five, eats and unwinds till nine and sleeps till six; a
 * night shift (guards mostly) the other way round. She sleeps in a bed at home if there's one, dreams badly after a bad
 * day, and wakes anyone sleeping at home when something comes. Following you, she keeps your hours instead.
 */
public final class NpcWork {
    public enum Job {
        NONE("no job"), GUARD("guard"), REPAIR("carpenter"), FARM("farmer"), COOK("cook"), SCAVENGE("scavenger"),
        MEDIC("medic"), HAUL("hauler");

        public final String label;

        Job(String label) {
            this.label = label;
        }

        static Job of(String name) {
            for (Job j : values()) if (j.name().equalsIgnoreCase(name)) return j;
            return null;
        }
    }

    public enum Phase { WORK, FREE, SLEEP }

    /** What her body's doing, for the client's clips and poses (synced through NpcEntity.ACTION). */
    public static final int NONE = 0, SLEEP = 1, SIT = 2, KNEEL = 3, HAMMER = 4, DIG = 5, SEARCH = 6, LOOKOUT = 7, COOKING = 8,
            HUG = 9, KISS = 10, SEARCH_LOW = 11, POSE = 12, FIDGET = 13, COWER = 14;

    /** How far from home her jobs reach, where the stores are, and where a scavenger goes looking. */
    static final int HOME_RADIUS = 20, STORE_RADIUS = 12, SCAVENGE_MIN = 20, SCAVENGE_MAX = 64;

    BlockPos home;
    Job job = Job.NONE;
    boolean nightShift;
    /** What she carries: nine slots, more with a backpack on (NpcCare.kit). */
    SimpleContainer pack = new SimpleContainer(NpcCare.BASE_PACK);
    /** The backpack she's wearing, or EMPTY. */
    ItemStack bag = ItemStack.EMPTY;
    private final NpcEntity npc;

    NpcWork(NpcEntity npc) {
        this.npc = npc;
    }

    /** Makes her pack this many slots, keeping what's in it; what no longer fits drops at her feet. */
    void resize(NpcEntity npc, int size) {
        if (size == pack.getContainerSize()) return;
        SimpleContainer next = new SimpleContainer(size);
        for (ItemStack s : pack.removeAllItems()) {
            ItemStack left = next.addItem(s);
            if (!left.isEmpty() && npc.level() instanceof ServerLevel level) npc.spawnAtLocation(level, left);
        }
        pack = next;
    }
    /** Containers she's already searched (BlockPos longs), the oldest forgotten past 300. */
    final Set<Long> searched = new LinkedHashSet<>();
    boolean asleep;
    int action;
    private long lastAlarm = -1_000_000, lastOut = -1_000_000;
    private final Map<String, Long> grumbled = new java.util.HashMap<>();

    /** Where she is in her day, by the same clock the wristwatch shows (game tick 0 is 09:00; here 06:00 is 0). */
    Phase phase(ServerLevel level) {
        long t = Math.floorMod(level.getGameTime() + 3000L, 24000L);
        if (!nightShift) {
            return t < 11000 ? Phase.WORK : t < 15000 ? Phase.FREE : Phase.SLEEP;
        }
        return t >= 13000 && t < 23000 ? Phase.WORK : (t >= 23000 || t < 3000) ? Phase.FREE : Phase.SLEEP;
    }

    boolean atHome(NpcEntity npc) {
        return home != null && npc.blockPosition().distSqr(home) < HOME_RADIUS * HOME_RADIUS;
    }

    // ---------------------------------------------------------------- stores

    /** Every container within STORE_RADIUS of home, nearest first. */
    List<BlockPos> stores(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        if (home == null) return out;
        forEachContainer(level, home, STORE_RADIUS, (pos, c) -> out.add(pos));
        out.sort((a, b) -> Double.compare(a.distSqr(home), b.distSqr(home)));
        return out;
    }

    interface Visit {
        void at(BlockPos pos, Container container);
    }

    static void forEachContainer(ServerLevel level, BlockPos centre, int radius, Visit visit) {
        int c0x = (centre.getX() - radius) >> 4, c1x = (centre.getX() + radius) >> 4;
        int c0z = (centre.getZ() - radius) >> 4, c1z = (centre.getZ() + radius) >> 4;
        for (int cx = c0x; cx <= c1x; cx++) {
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
                    if (e.getValue() instanceof Container c && e.getKey().distSqr(centre) <= (double) radius * radius) {
                        visit.at(e.getKey().immutable(), c);
                    }
                }
            }
        }
    }

    static Container container(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container c ? c : null;
    }

    /** The nearest store holding something that matches, or null. */
    BlockPos storeWith(ServerLevel level, Predicate<ItemStack> want) {
        for (BlockPos pos : stores(level)) {
            Container c = container(level, pos);
            if (c != null && count(c, want) > 0) return pos;
        }
        return null;
    }

    static int count(Container c, Predicate<ItemStack> want) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && want.test(s)) n += s.getCount();
        }
        return n;
    }

    /** Moves up to `max` matching items from a container into her pack; returns how many moved. */
    int take(Container from, Predicate<ItemStack> want, int max) {
        int moved = 0;
        for (int i = 0; i < from.getContainerSize() && moved < max; i++) {
            ItemStack s = from.getItem(i);
            if (s.isEmpty() || !want.test(s)) continue;
            ItemStack part = from.removeItem(i, Math.min(max - moved, s.getCount()));
            ItemStack left = pack.addItem(part);
            moved += part.getCount() - left.getCount();
            if (!left.isEmpty()) {
                from.setItem(i, left.copyWithCount(left.getCount() + from.getItem(i).getCount()));
                break;
            }
        }
        from.setChanged();
        return moved;
    }

    /** Takes one matching item out of her pack, or EMPTY. */
    ItemStack takeFromPack(Predicate<ItemStack> want) {
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            if (!s.isEmpty() && want.test(s)) return pack.removeItem(i, 1);
        }
        return ItemStack.EMPTY;
    }

    boolean packHas(Predicate<ItemStack> want) {
        return count(pack, want) > 0;
    }

    /** Nothing in her pack for the stores: it's empty, or all she has is what she keeps on her (NpcCare.keeps). */
    boolean packEmpty() {
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            if (!s.isEmpty() && !NpcCare.keeps(npc, s)) return false;
        }
        return true;
    }

    /** Whether everything in her pack is of this kind (an empty pack counts). */
    boolean packOnly(Predicate<ItemStack> want) {
        for (int i = 0; i < pack.getContainerSize(); i++) {
            ItemStack s = pack.getItem(i);
            if (!s.isEmpty() && !want.test(s)) return false;
        }
        return true;
    }

    /** Puts everything in her pack into the stores but what she keeps on her; what doesn't fit stays with her. */
    void deposit(ServerLevel level) {
        for (BlockPos pos : stores(level)) {
            Container c = container(level, pos);
            if (c == null) continue;
            for (int i = 0; i < pack.getContainerSize(); i++) {
                ItemStack s = pack.getItem(i);
                if (!s.isEmpty() && !NpcCare.keeps(npc, s)) pack.setItem(i, HopperBlockEntity.addItem(null, c, s, null));
            }
            c.setChanged();
            if (packEmpty()) return;
        }
    }

    // ---------------------------------------------------------------- what counts as what

    static final Predicate<ItemStack> PLANKS = s -> s.is(ItemTags.PLANKS);
    static final Predicate<ItemStack> FOOD = s -> s.get(DataComponents.FOOD) != null;
    static final Predicate<ItemStack> DRINK = s -> Drinks.thirstValue(s) > 0 && s.get(DataComponents.FOOD) == null;
    static final Predicate<ItemStack> WATER = s -> {
        PotionContents p = s.get(DataComponents.POTION_CONTENTS);
        return s.is(Items.POTION) && p != null && p.is(Potions.WATER);
    };
    static final Predicate<ItemStack> SEEDS = s -> BuildBlocks.Crop.from(s) != null;
    static final Predicate<ItemStack> BANDAGE = s -> s.getItem() instanceof BandageItem;
    static final Map<Item, Item> COOKS = Map.ofEntries(
            Map.entry(Items.BEEF, Items.COOKED_BEEF), Map.entry(Items.PORKCHOP, Items.COOKED_PORKCHOP),
            Map.entry(Items.CHICKEN, Items.COOKED_CHICKEN), Map.entry(Items.MUTTON, Items.COOKED_MUTTON),
            Map.entry(Items.RABBIT, Items.COOKED_RABBIT), Map.entry(Items.COD, Items.COOKED_COD),
            Map.entry(Items.SALMON, Items.COOKED_SALMON), Map.entry(Items.POTATO, Items.BAKED_POTATO),
            Map.entry(Items.KELP, Items.DRIED_KELP));
    static final Predicate<ItemStack> RAW = s -> COOKS.containsKey(s.getItem());

    /** What a scavenger brings back first: food and drink, medical, ammo and guns, materials, seeds; then anything. */
    static int worth(ItemStack s) {
        if (FOOD.test(s) || DRINK.test(s)) return 5;
        if (BANDAGE.test(s) || s.getItem() instanceof com.zomboidcraft.survival.SplintItem) return 5;
        if (GunType.of(s) != null || GunType.ofMagazine(s) != null) return 4;
        if (PLANKS.test(s) || SEEDS.test(s)) return 3;
        return 1;
    }

    // ---------------------------------------------------------------- the day's errands

    /** One errand: walk to `at`, spend `ticks` there doing `action`, then `done`. */
    static final class Task {
        final BlockPos at;
        final int action;
        int ticks;
        final Runnable done;
        final double reach;

        Task(BlockPos at, int ticks, int action, double reach, Runnable done) {
            this.at = at;
            this.ticks = ticks;
            this.action = action;
            this.reach = reach;
            this.done = done;
        }
    }

    /** Says a grumble (out of planks, nothing to cook) at most every five minutes. */
    void grumble(NpcEntity npc, String what) {
        long now = npc.level().getGameTime();
        Long last = grumbled.get(what);
        if (last == null || now - last > 20 * 300) {
            grumbled.put(what, now);
            npc.lines.sayLine(npc, NpcLines.Kind.WORK, what);
        }
    }

    void practise(NpcEntity npc, NpcSkills.Skill skill, float xp) {
        int level = npc.skills.practise(skill, xp);
        if (level > 0) {
            npc.lines.sayLine(npc, NpcLines.Kind.LEVEL, levelLine(skill, level));
            if (level == 5 || level == 10) {
                npc.mind.remember(npc, (level == 10 ? "Mastered " : "Got good at ") + skill.label, 0.5f, 0.7f);
            }
            npc.mind.add(NpcMind.Need.MORALE, 3f);
        }
    }

    private static String levelLine(NpcSkills.Skill skill, int level) {
        return switch (skill) {
            case AIM -> level >= 8 ? "I don't miss much anymore." : "My aim's getting better.";
            case FIRST_AID -> "I'm getting the hang of patching people up.";
            case COOKING -> level >= 8 ? "Honestly? I could open a restaurant." : "I think I'm getting better at this cooking thing.";
            case CARPENTRY -> "My boards are holding better these days.";
            case SCAVENGING -> "I'm starting to know where people hid things.";
            case FARMING -> "Turns out I've got a green thumb.";
        };
    }

    /** The next errand for her job, or null when there's nothing to do just now. */
    Task plan(NpcEntity npc, ServerLevel level) {
        if (home == null) return null;
        return switch (job) {
            case NONE -> null;
            case GUARD -> guard(npc, level);
            case REPAIR -> repair(npc, level);
            case FARM -> farm(npc, level);
            case COOK -> cook(npc, level);
            case SCAVENGE -> scavenge(npc, level);
            case MEDIC -> medic(npc, level);
            case HAUL -> haul(npc, level, false);
        };
    }

    /** A trip to the stores to drop off her pack. */
    private Task depositTask(NpcEntity npc, ServerLevel level, Runnable after) {
        List<BlockPos> stores = stores(level);
        if (stores.isEmpty()) {
            grumble(npc, "Where am I meant to put all this? We need a chest or something.");
            return null;
        }
        return new Task(stores.get(0), 20, SEARCH, 2.2, () -> {
            deposit(level);
            if (after != null) after.run();
        });
    }

    /** A trip to the stores to fetch some of something. */
    private Task fetchTask(NpcEntity npc, ServerLevel level, Predicate<ItemStack> want, int max, String missing) {
        BlockPos at = storeWith(level, want);
        if (at == null) {
            if (missing != null) grumble(npc, missing);
            return null;
        }
        return new Task(at, 20, SEARCH, 2.2, () -> {
            Container c = container(level, at);
            if (c != null) take(c, want, max);
        });
    }

    // guard: the doors and windows in turn, a while at each, looking out
    private int post;

    private Task guard(NpcEntity npc, ServerLevel level) {
        List<BlockPos> posts = new ArrayList<>();
        BlockPos.betweenClosedStream(home.offset(-HOME_RADIUS, -4, -HOME_RADIUS), home.offset(HOME_RADIUS, 4, HOME_RADIUS))
                .filter(p -> p.distSqr(home) < HOME_RADIUS * HOME_RADIUS)
                .filter(p -> {
                    BlockState s = level.getBlockState(p);
                    return s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                            || s.getBlock() instanceof BarricadeBlock;
                })
                .limit(24).forEach(p -> posts.add(p.immutable()));
        BlockPos at;
        if (posts.isEmpty()) {
            int k = post++ % 4;
            Direction d = Direction.from2DDataValue(k);
            at = home.relative(d, 8);
        } else {
            at = posts.get(post++ % posts.size());
        }
        return new Task(at, 20 * 20, LOOKOUT, 2.5, null);
    }

    // carpenter: the worst-beaten barricade or wall first, a plank from the stores for each
    private Task repair(NpcEntity npc, ServerLevel level) {
        BlockPos worst = null;
        int most = 0;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-HOME_RADIUS, -4, -HOME_RADIUS), home.offset(HOME_RADIUS, 4, HOME_RADIUS))) {
            BlockState s = level.getBlockState(p);
            int dmg = damage(s);
            if (dmg > most && p.distSqr(home) < HOME_RADIUS * HOME_RADIUS) {
                most = dmg;
                worst = p.immutable();
            }
        }
        if (worst == null) {
            return packEmpty() ? null : depositTask(npc, level, null);
        }
        if (!packHas(PLANKS)) {
            return fetchTask(npc, level, PLANKS, 6, "We're out of planks. I can't fix anything without them.");
        }
        BlockPos at = worst;
        int carp = npc.skills.level(NpcSkills.Skill.CARPENTRY);
        return new Task(at, Math.max(30, 140 - carp * 10), HAMMER, 2.4, () -> {
            BlockState s = level.getBlockState(at);
            if (damage(s) <= 0 || takeFromPack(PLANKS).isEmpty()) return;
            BlockState fixed = s;
            if (s.getBlock() instanceof BarricadeBlock) {
                fixed = s.setValue(BarricadeBlock.DAMAGE, 0).setValue(BarricadeBlock.STRENGTH,
                        Math.max(s.getValue(BarricadeBlock.STRENGTH), BarricadeBlock.strengthFor(carp)));
            } else if (BuildBlocks.bashDamage(s) != null) {
                fixed = s.setValue(BuildBlocks.bashDamage(s), 0);
            }
            level.setBlockAndUpdate(at, fixed);
            level.playSound(null, at, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1f, 0.9f);
            practise(npc, NpcSkills.Skill.CARPENTRY, 6);
            npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, pick(npc, "That'll hold.", "Good as new. Well, nearly.", "Patched."));
        });
    }

    static int damage(BlockState s) {
        if (s.getBlock() instanceof BarricadeBlock) return s.getValue(BarricadeBlock.DAMAGE);
        var prop = BuildBlocks.bashDamage(s);
        return prop == null ? 0 : s.getValue(prop);
    }

    // farmer: ripe first, then dry beds, then empty beds and fields to sow
    private Task farm(NpcEntity npc, ServerLevel level) {
        int farming = npc.skills.level(NpcSkills.Skill.FARMING);
        BlockPos ripe = null, dry = null, empty = null, cropRipe = null;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-HOME_RADIUS, -4, -HOME_RADIUS), home.offset(HOME_RADIUS, 4, HOME_RADIUS))) {
            if (p.distSqr(home) > HOME_RADIUS * HOME_RADIUS) continue;
            BlockState s = level.getBlockState(p);
            if (s.is(BuildBlocks.PLANTER)) {
                BuildBlocks.Crop crop = s.getValue(BuildBlocks.Planter.CROP);
                if (crop != BuildBlocks.Crop.NONE && s.getValue(BuildBlocks.Planter.AGE) >= 7) { if (ripe == null) ripe = p.immutable(); }
                else if (!s.getValue(BuildBlocks.Planter.WET)) { if (dry == null) dry = p.immutable(); }
                else if (crop == BuildBlocks.Crop.NONE && empty == null) empty = p.immutable();
            } else if (s.getBlock() instanceof CropBlock cb && cb.isMaxAge(s) && cropRipe == null) {
                cropRipe = p.immutable();
            }
        }
        if (pack.getItem(pack.getContainerSize() - 1).getCount() > 0 || ripe == null && cropRipe == null && dry == null && empty == null && !packEmpty() && !packHas(SEEDS.or(WATER))) {
            return depositTask(npc, level, null);
        }
        if (ripe != null) {
            BlockPos at = ripe;
            return new Task(at, 60, KNEEL, 2.2, () -> {
                BlockState s = level.getBlockState(at);
                if (!s.is(BuildBlocks.PLANTER) || s.getValue(BuildBlocks.Planter.AGE) < 7) return;
                BuildBlocks.Crop crop = s.getValue(BuildBlocks.Planter.CROP);
                int n = (crop == BuildBlocks.Crop.BEET ? 2 : 3) + npc.getRandom().nextInt(3) + (npc.getRandom().nextInt(10) < farming ? 1 : 0);
                pack.addItem(new ItemStack(crop.produce(), n));
                if (npc.getRandom().nextFloat() < 0.6f + farming * 0.03f) pack.addItem(new ItemStack(crop.seeds()));
                level.setBlockAndUpdate(at, s.setValue(BuildBlocks.Planter.CROP, BuildBlocks.Crop.NONE).setValue(BuildBlocks.Planter.AGE, 0));
                level.playSound(null, at, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
                practise(npc, NpcSkills.Skill.FARMING, 6);
            });
        }
        if (cropRipe != null) {
            BlockPos at = cropRipe;
            return new Task(at, 50, KNEEL, 2.2, () -> {
                BlockState s = level.getBlockState(at);
                if (!(s.getBlock() instanceof CropBlock cb) || !cb.isMaxAge(s)) return;
                for (ItemStack drop : Block.getDrops(s, level, at, null)) pack.addItem(drop);
                level.setBlockAndUpdate(at, cb.getStateForAge(0));
                level.playSound(null, at, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
                practise(npc, NpcSkills.Skill.FARMING, 4);
            });
        }
        if (dry != null) {
            BlockPos barrel = rainBarrel(level);
            if (!packHas(WATER) && barrel == null) {
                Task fetch = fetchTask(npc, level, WATER, 4, "Everything's drying out. I need water bottles or a rain barrel.");
                if (fetch != null) return fetch;
            } else {
                BlockPos at = dry;
                return new Task(at, 40, KNEEL, 2.2, () -> {
                    BlockState s = level.getBlockState(at);
                    if (!s.is(BuildBlocks.PLANTER) || s.getValue(BuildBlocks.Planter.WET)) return;
                    if (takeFromPack(WATER).isEmpty()) {
                        BlockPos b = rainBarrel(level);
                        if (b == null) return;
                        BlockState bs = level.getBlockState(b);
                        level.setBlockAndUpdate(b, bs.setValue(BuildBlocks.RainBarrel.LEVEL, bs.getValue(BuildBlocks.RainBarrel.LEVEL) - 1));
                    } else {
                        pack.addItem(new ItemStack(Items.GLASS_BOTTLE));
                    }
                    level.setBlockAndUpdate(at, s.setValue(BuildBlocks.Planter.WET, true));
                    level.playSound(null, at, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 1f);
                    practise(npc, NpcSkills.Skill.FARMING, 2);
                });
            }
        }
        if (empty != null) {
            if (!packHas(SEEDS)) {
                return fetchTask(npc, level, SEEDS, 8, "No seeds left to plant.");
            }
            BlockPos at = empty;
            return new Task(at, 40, DIG, 2.2, () -> {
                BlockState s = level.getBlockState(at);
                if (!s.is(BuildBlocks.PLANTER) || s.getValue(BuildBlocks.Planter.CROP) != BuildBlocks.Crop.NONE) return;
                ItemStack seed = takeFromPack(SEEDS);
                BuildBlocks.Crop crop = BuildBlocks.Crop.from(seed);
                if (crop == null) return;
                level.setBlockAndUpdate(at, s.setValue(BuildBlocks.Planter.CROP, crop).setValue(BuildBlocks.Planter.AGE, 0));
                level.playSound(null, at, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
                practise(npc, NpcSkills.Skill.FARMING, 3);
            });
        }
        return packEmpty() ? null : depositTask(npc, level, null);
    }

    private BlockPos rainBarrel(ServerLevel level) {
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-STORE_RADIUS, -3, -STORE_RADIUS), home.offset(STORE_RADIUS, 3, STORE_RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BuildBlocks.RAIN_BARREL) && s.getValue(BuildBlocks.RainBarrel.LEVEL) > 0) return p.immutable();
        }
        return null;
    }

    // cook: raw food from the stores, cooked over a lit campfire or any furnace, back to the stores
    private Task cook(NpcEntity npc, ServerLevel level) {
        BlockPos heat = null;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-HOME_RADIUS, -3, -HOME_RADIUS), home.offset(HOME_RADIUS, 3, HOME_RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.CAMPFIRES) && s.getValue(CampfireBlock.LIT) || s.getBlock() instanceof AbstractFurnaceBlock) {
                heat = p.immutable();
                break;
            }
        }
        if (heat == null) {
            // no fire: say so once in a while, and leave the raw food in the stores rather than fetch it back and forth
            if (packHas(RAW) || storeWith(level, RAW) != null) grumble(npc, "I can't cook without a fire. A campfire or a stove, anything.");
            return packEmpty() ? null : depositTask(npc, level, null);
        }
        if (packHas(RAW)) {
            int cooking = npc.skills.level(NpcSkills.Skill.COOKING);
            return new Task(heat, Math.max(60, 200 - cooking * 12), COOKING, 2.4, () -> {
                int made = 0;
                for (int i = 0; i < pack.getContainerSize(); i++) {
                    ItemStack s = pack.getItem(i);
                    Item cooked = COOKS.get(s.getItem());
                    if (cooked == null) continue;
                    int n = s.getCount();
                    // a good cook wastes nothing and stretches it further
                    int extra = 0;
                    for (int k = 0; k < n; k++) if (npc.getRandom().nextInt(25) < cooking) extra++;
                    pack.setItem(i, new ItemStack(cooked, n + extra));
                    made += n + extra;
                }
                level.playSound(null, npc.blockPosition(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1f, 1f);
                practise(npc, NpcSkills.Skill.COOKING, 3 + made);
                npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, pick(npc, "Food's up!", "Dinner. Don't complain.", "Hot food, come and get it."));
            });
        }
        if (!packEmpty()) return depositTask(npc, level, null);
        return fetchTask(npc, level, RAW, 6, "There's nothing in the stores to cook.");
    }

    // scavenger: out to the nearest unsearched containers, three at most or a full pack, then home
    private int searchedThisRun;

    private Task scavenge(NpcEntity npc, ServerLevel level) {
        boolean full = pack.getItem(pack.getContainerSize() - 1).getCount() > 0;
        if (full || searchedThisRun >= 3) {
            int found = 0;
            for (int i = 0; i < pack.getContainerSize(); i++) found += pack.getItem(i).isEmpty() ? 0 : 1;
            int haul = found;
            return depositTask(npc, level, () -> {
                searchedThisRun = 0;
                if (haul > 0) {
                    npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, pick(npc, "I'm back. Got a few things.", "Back in one piece. Look what I found.",
                            "Made it back. Stores are a bit fuller."));
                    npc.mind.remember(npc, "Scavenging run, came back with " + haul + " lots of things", 0.3f, 0.3f);
                }
            });
        }
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        Vec3 from = npc.position();
        List<BlockPos> found = new ArrayList<>();
        forEachContainer(level, home, SCAVENGE_MAX, (pos, c) -> {
            double dh = pos.distSqr(home);
            if (dh >= SCAVENGE_MIN * SCAVENGE_MIN && !searched.contains(pos.asLong())) found.add(pos);
        });
        for (BlockPos p : found) {
            double d = p.distToCenterSqr(from);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        if (best == null) {
            if (!packEmpty()) return depositTask(npc, level, () -> searchedThisRun = 0);
            grumble(npc, "I've picked everything round here clean. We'll have to go further out.");
            return null;
        }
        long now = level.getGameTime();
        if (searchedThisRun == 0 && now - lastOut > 20 * 120) {
            lastOut = now;
            npc.lines.sayLine(npc, NpcLines.Kind.WORK, pick(npc, "Heading out. Back soon.", "Going for a look round. Keep the door shut.",
                    "Supply run. Don't wait up."));
        }
        BlockPos at = best;
        int scav = npc.skills.level(NpcSkills.Skill.SCAVENGING);
        return new Task(at, Math.max(40, 140 - scav * 9), SEARCH, 2.2, () -> {
            searched.add(at.asLong());
            while (searched.size() > 300) searched.remove(searched.iterator().next());
            searchedThisRun++;
            Container c = container(level, at);
            if (c == null) return;
            if (c instanceof RandomizableContainer loot) loot.unpackLootTable(null);
            int stacks = 2 + scav / 3;
            for (int w = 5; w >= 1 && stacks > 0; w--) {
                for (int i = 0; i < c.getContainerSize() && stacks > 0; i++) {
                    ItemStack s = c.getItem(i);
                    if (s.isEmpty() || worth(s) != w) continue;
                    ItemStack left = pack.addItem(c.removeItemNoUpdate(i));
                    c.setItem(i, left);
                    if (left.isEmpty()) stacks--; else break;
                }
            }
            c.setChanged();
            practise(npc, NpcSkills.Skill.SCAVENGING, 4);
        });
    }

    // medic: whoever's hurt at home, herself included, with a bandage from her pack or the stores if there is one
    private final Map<java.util.UUID, Long> treated = new java.util.HashMap<>();

    private Task medic(NpcEntity npc, ServerLevel level) {
        long now = level.getGameTime();
        Player hurt = null;
        for (Player p : level.players()) {
            if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(home)) < HOME_RADIUS * HOME_RADIUS
                    && p.getHealth() < p.getMaxHealth() * 0.75f && now - treated.getOrDefault(p.getUUID(), -1_000_000L) > 20 * 30) {
                hurt = p;
                break;
            }
        }
        boolean self = hurt == null && npc.getHealth() < npc.getMaxHealth() * 0.6f && now - treated.getOrDefault(npc.getUUID(), -1_000_000L) > 20 * 30;
        if (hurt == null && !self) {
            if (!packHas(BANDAGE)) {
                BlockPos at = storeWith(level, BANDAGE);
                if (at != null) return fetchTask(npc, level, BANDAGE, 3, null);
            }
            // between patients she tidies up, but keeps her bandages on her
            return haul(npc, level, true);
        }
        Player patient = hurt;
        int aid = npc.skills.level(NpcSkills.Skill.FIRST_AID);
        BlockPos at = patient != null ? patient.blockPosition() : npc.blockPosition();
        return new Task(at, Math.max(30, 100 - aid * 6), KNEEL, 2.2, () -> {
            boolean bandage = !takeFromPack(BANDAGE).isEmpty();
            float heal = (bandage ? 6f : 2f) + aid * 0.6f;
            if (patient != null) {
                if (patient.distanceToSqr(npc) > 9) return;
                patient.heal(heal);
                treated.put(patient.getUUID(), now);
                npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, pick(npc, "Hold still.", "This'll sting.", "There. Try not to do that again."));
            } else {
                npc.heal(heal);
                treated.put(npc.getUUID(), now);
            }
            level.playSound(null, npc.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.8f, 1.2f);
            practise(npc, NpcSkills.Skill.FIRST_AID, bandage ? 6 : 3);
        });
    }

    // hauler: anything lying about at home into the stores (guns stay for whoever picks them up)
    private Task haul(NpcEntity npc, ServerLevel level, boolean keepBandages) {
        boolean full = pack.getItem(pack.getContainerSize() - 1).getCount() > 0;
        ItemEntity loose = null;
        if (!full) {
            double best = Double.MAX_VALUE;
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(home).inflate(HOME_RADIUS),
                    e -> e.isAlive() && GunType.of(e.getItem()) == null)) {
                double d = e.distanceToSqr(npc);
                if (d < best) {
                    best = d;
                    loose = e;
                }
            }
        }
        if (loose == null) return packEmpty() || keepBandages && packOnly(BANDAGE) ? null : depositTask(npc, level, null);
        ItemEntity item = loose;
        return new Task(item.blockPosition(), 8, KNEEL, 1.8, () -> {
            if (!item.isAlive()) return;
            ItemStack left = pack.addItem(item.getItem().copy());
            if (left.isEmpty()) item.discard(); else item.setItem(left);
        });
    }

    static String pick(NpcEntity npc, String... lines) {
        return lines[npc.getRandom().nextInt(lines.length)];
    }

    // ---------------------------------------------------------------- alarms

    /**
     * Awake at home, she watches for zombies coming in: the first she sees inside the yard, she shouts which side
     * they're coming from and wakes anyone asleep at home.
     */
    void watch(NpcEntity npc, ServerLevel level) {
        if (home == null || npc.following) return;
        long now = level.getGameTime();
        if (now - lastAlarm < 20 * 45) return;
        Vec3 centre = Vec3.atCenterOf(home);
        double reach = HOME_RADIUS + 8;
        Zombie seen = null;
        for (Zombie z : level.getEntitiesOfClass(Zombie.class, new net.minecraft.world.phys.AABB(home).inflate(reach), Zombie::isAlive)) {
            if (z.distanceToSqr(centre) < reach * reach && (!asleep && npc.getSensing().hasLineOfSight(z)
                    || z.distanceToSqr(npc) < (asleep ? 6 * 6 : 10 * 10))) {
                seen = z;
                break;
            }
        }
        if (seen == null) return;
        lastAlarm = now;
        if (asleep) {
            asleep = false;
            npc.mind.jolt(30f);
        }
        String side = side(centre, seen.position());
        npc.lines.sayLine(npc, NpcLines.Kind.ALARM, pick(npc, "Zombies, " + side + " side!", "Movement to the " + side + "!",
                "They're at the " + side + "! Get up!", "Heads up, " + side + " side!"));
        for (Player p : level.players()) {
            if (p.isSleeping() && p.distanceToSqr(centre) < reach * reach && p instanceof ServerPlayer sp) {
                sp.stopSleepInBed(true, true);
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal(npc.speakerName() + " shakes you awake.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC));
            }
        }
        npc.mind.remember(npc, "Raised the alarm, zombies on the " + side + " side", -0.2f, 0.3f);
    }

    static String side(Vec3 centre, Vec3 at) {
        double dx = at.x - centre.x, dz = at.z - centre.z;
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "east" : "west";
        return dz > 0 ? "south" : "north";
    }

    // ---------------------------------------------------------------- the beds

    /** A free bed within home reach (its foot), or null. */
    BlockPos bed(ServerLevel level, NpcEntity npc) {
        if (home == null) return null;
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-HOME_RADIUS, -3, -HOME_RADIUS), home.offset(HOME_RADIUS, 3, HOME_RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.is(BlockTags.BEDS) && s.getValue(BedBlock.PART) == BedPart.FOOT && !s.getValue(BedBlock.OCCUPIED)) {
                BlockPos foot = p.immutable();
                boolean taken = !level.getEntitiesOfClass(NpcEntity.class, new net.minecraft.world.phys.AABB(foot).inflate(0.6),
                        o -> o != npc && o.work.asleep).isEmpty();
                if (!taken) return foot;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- saving

    String saveSearched() {
        StringBuilder b = new StringBuilder();
        for (long l : searched) b.append(l).append(' ');
        return b.toString().trim();
    }

    void loadSearched(String s) {
        searched.clear();
        if (s == null || s.isBlank()) return;
        for (String f : s.trim().split(" ")) {
            try {
                searched.add(Long.parseLong(f));
            } catch (NumberFormatException e) {
                // skip it
            }
        }
    }

    // ---------------------------------------------------------------- goals

    /** Her job, in working hours, when she's at home and not following anyone. */
    static final class Work extends Goal {
        private final NpcEntity npc;
        private Task task;
        private int replan, repath;

        Work(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            NpcWork w = npc.work;
            return !npc.following && w.home != null && w.job != Job.NONE && !w.asleep && npc.level() instanceof ServerLevel level
                    && w.phase(level) == Phase.WORK;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            task = null;
            npc.work.action = NONE;
            npc.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ServerLevel level = (ServerLevel) npc.level();
            NpcWork w = npc.work;
            if (task == null) {
                w.action = NONE;
                if (--replan > 0) return;
                replan = 40;
                task = w.plan(npc, level);
                if (task == null) {
                    // nothing to do: drift back toward home and stand about
                    if (!w.atHome(npc) || npc.getRandom().nextInt(4) == 0) {
                        BlockPos near = w.home.offset(npc.getRandom().nextInt(9) - 4, 0, npc.getRandom().nextInt(9) - 4);
                        npc.getNavigation().moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5, 0.9 * npc.mind.pace());
                    }
                    return;
                }
                repath = 0;
            }
            Vec3 at = Vec3.atBottomCenterOf(task.at);
            if (npc.position().distanceToSqr(at) > task.reach * task.reach) {
                w.action = NONE;
                if (--repath <= 0) {
                    repath = 20;
                    double far = npc.position().distanceTo(at);
                    if (!npc.getNavigation().moveTo(at.x, at.y, at.z, (far > 16 ? 1.3 : 1.0) * npc.mind.pace()) && far > 48) {
                        task = null;  // can't get there
                    }
                }
                return;
            }
            npc.getNavigation().stop();
            npc.getLookControl().setLookAt(at.x, at.y + 0.5, at.z);
            // a container down at her feet she kneels to; one higher up she searches standing
            w.action = task.action == SEARCH && task.at.getY() <= npc.getBlockY() ? SEARCH_LOW : task.action;
            if (task.action == LOOKOUT && npc.tickCount % 60 == 0) {
                // a guard looks out, away from home
                Vec3 out = at.add(at.subtract(Vec3.atCenterOf(w.home)).normalize().scale(6));
                npc.getLookControl().setLookAt(out.x, npc.getEyeY(), out.z);
            }
            if (--task.ticks <= 0) {
                Task done = task;
                task = null;
                replan = 0;
                w.action = NONE;
                if (done.done != null) done.done.run();
            }
        }
    }

    /**
     * Her evening: food and drink from the stores if she needs them, then a seat near whoever's home (or near home if
     * nobody is). Following you, none of this: she's with you.
     */
    static final class Evening extends Goal {
        private final NpcEntity npc;
        private int repath;

        Evening(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            NpcWork w = npc.work;
            if (npc.following || w.home == null || w.asleep || !(npc.level() instanceof ServerLevel level)) return false;
            Phase p = w.phase(level);
            // free time, a day with no job, and bedtime till she's tired enough to turn in (Sleep takes over then)
            return p == Phase.FREE || p == Phase.SLEEP || p == Phase.WORK && w.job == Job.NONE;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            npc.work.action = NONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            ServerLevel level = (ServerLevel) npc.level();
            NpcWork w = npc.work;
            NpcMind m = npc.mind;
            boolean hungry = m.get(NpcMind.Need.HUNGER) > 30, thirsty = m.get(NpcMind.Need.THIRST) > 30;
            if ((hungry || thirsty) && npc.tickCount % 20 == 0) {
                Predicate<ItemStack> want = hungry && thirsty ? FOOD.or(DRINK) : hungry ? FOOD : DRINK;
                ItemStack mine = w.takeFromPack(want);
                if (mine.isEmpty()) {
                    BlockPos store = w.storeWith(level, want);
                    if (store != null) {
                        if (npc.position().distanceToSqr(Vec3.atBottomCenterOf(store)) > 2.4 * 2.4) {
                            if (--repath <= 0) {
                                repath = 20;
                                npc.getNavigation().moveTo(store.getX() + 0.5, store.getY(), store.getZ() + 0.5, 1.0 * m.pace());
                            }
                            return;
                        }
                        Container c = container(level, store);
                        if (c != null) w.take(c, want, 1);
                        mine = w.takeFromPack(want);
                    }
                }
                if (!mine.isEmpty()) {
                    var food = mine.get(DataComponents.FOOD);
                    m.ate(npc, mine.getHoverName().getString(), food == null ? 0 : food.nutrition() * 6f, Drinks.thirstValue(mine));
                    npc.playSound(food != null ? SoundEvents.GENERIC_EAT.value() : SoundEvents.GENERIC_DRINK.value(), 0.8f, 1f);
                    return;
                }
            }
            // company: sit near someone at home, else near home
            Player near = null;
            for (Player p : level.players()) {
                if (!p.isSpectator() && p.distanceToSqr(Vec3.atCenterOf(w.home)) < HOME_RADIUS * HOME_RADIUS) {
                    near = p;
                    break;
                }
            }
            Vec3 spot = near != null ? near.position() : Vec3.atBottomCenterOf(w.home);
            if (npc.position().distanceToSqr(spot) > 3.5 * 3.5) {
                w.action = NONE;
                if (--repath <= 0) {
                    repath = 20;
                    npc.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0 * m.pace());
                }
            } else {
                npc.getNavigation().stop();
                w.action = SIT;
                if (near != null) npc.getLookControl().setLookAt(near, 10f, 30f);
            }
        }
    }

    /**
     * Sleep: at home in her hours (or whenever she's dead on her feet and it's quiet), in a bed if there's one free;
     * following you, when you lie down nearby. A bad day can bring a nightmare that wakes her shaking.
     */
    static final class Sleep extends Goal {
        private final NpcEntity npc;
        private BlockPos bed;
        private int nightmareAt, slept, repath;

        Sleep(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        private boolean sleepy() {
            if (!(npc.level() instanceof ServerLevel level)) return false;
            NpcWork w = npc.work;
            NpcMind m = npc.mind;
            if (m.get(NpcMind.Need.FEAR) > 40 || NpcBrain.nearestZombie(npc, 12, false) != null) return false;
            if (npc.following) {
                Player leader = npc.leader();
                return leader != null && leader.isSleeping() && leader.distanceToSqr(npc) < 12 * 12;
            }
            if (w.home == null) return m.get(NpcMind.Need.TIRED) > 90;
            return w.phase(level) == Phase.SLEEP || m.get(NpcMind.Need.TIRED) > 90;
        }

        @Override
        public boolean canUse() {
            return npc.getRandom().nextInt(20) == 0 && sleepy();
        }

        @Override
        public boolean canContinueToUse() {
            NpcWork w = npc.work;
            if (w.asleep) {
                // asleep, she stays down till rested and her hours are over, or something wakes her
                ServerLevel level = (ServerLevel) npc.level();
                boolean hours = npc.following ? npc.leader() != null && npc.leader().isSleeping()
                        : w.home != null && w.phase(level) == Phase.SLEEP;
                return (hours || npc.mind.get(NpcMind.Need.TIRED) > 5) && NpcBrain.nearestZombie(npc, 6, false) == null && w.asleep;
            }
            return sleepy();
        }

        @Override
        public void start() {
            ServerLevel level = (ServerLevel) npc.level();
            bed = npc.following ? null : npc.work.bed(level, npc);
            slept = 0;
            repath = 0;
            // a bad day: a strong, bad memory from the last day brings a nightmare half the time
            nightmareAt = -1;
            long now = level.getGameTime();
            for (NpcMind.Memory mem : npc.mind.diary) {
                if (mem.feel() < -0.5f && mem.strength() > 0.6f && now - mem.at() < 24000 && npc.getRandom().nextBoolean()) {
                    nightmareAt = 20 * (60 + npc.getRandom().nextInt(180));
                    break;
                }
            }
        }

        @Override
        public void stop() {
            NpcWork w = npc.work;
            if (w.asleep) {
                w.asleep = false;
                if (npc.mind.get(NpcMind.Need.TIRED) < 20) {
                    npc.lines.sayLine(npc, NpcLines.Kind.WAKE, pick(npc, "Morning. I think.", "Okay. I'm up.", "Still raining, then."));
                }
            }
            w.action = NONE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            NpcWork w = npc.work;
            if (!w.asleep) {
                Vec3 spot = bed != null ? Vec3.atBottomCenterOf(bed) : null;
                if (spot != null && npc.position().distanceToSqr(spot) > 1.2 * 1.2) {
                    if (--repath <= 0) {
                        repath = 20;
                        if (!npc.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0)) bed = null;
                    }
                    return;
                }
                npc.getNavigation().stop();
                if (bed != null) {
                    BlockState s = npc.level().getBlockState(bed);
                    if (s.is(BlockTags.BEDS)) {
                        Direction head = s.getValue(BedBlock.FACING);
                        // until she has a sleep clip she lies down with the end of her fall (hips forward of her feet),
                        // so her hips go three quarters of a block toward the pillow to keep her whole body on the bed
                        npc.snapTo(bed.getX() + 0.5 + head.getStepX() * 0.75, bed.getY() + 0.5625, bed.getZ() + 0.5 + head.getStepZ() * 0.75,
                                head.toYRot(), 0f);
                        npc.setYBodyRot(head.toYRot());
                        npc.setYHeadRot(head.toYRot());
                    }
                }
                w.asleep = true;
                npc.lines.sayLine(npc, NpcLines.Kind.SLEEPY, pick(npc, "I'm turning in.", "Wake me if anything happens.", "Night.",
                        "Don't let me sleep through the end of the world."));
                return;
            }
            w.action = SLEEP;
            slept++;
            if (nightmareAt > 0 && slept == nightmareAt) {
                w.asleep = false;
                npc.mind.add(NpcMind.Need.FEAR, 40f);
                npc.mind.add(NpcMind.Need.MORALE, -4f);
                npc.lines.sayLine(npc, NpcLines.Kind.NIGHTMARE, pick(npc, "No! ...Oh. A dream. Just a dream.",
                        "I was back there. I was back there again.", "Sorry. Bad dream. Go back to sleep."));
                npc.mind.remember(npc, "Woke from a nightmare", -0.4f, 0.4f);
            }
        }
    }

    static String jobsList() {
        List<String> out = new ArrayList<>();
        for (Job j : Job.values()) out.add(j.name().toLowerCase(Locale.ROOT));
        return String.join(", ", out);
    }
}
