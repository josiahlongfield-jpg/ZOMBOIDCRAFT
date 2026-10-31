package com.zomboidcraft.npc;

import com.zomboidcraft.clothing.ClothWear;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.Garment;
import com.zomboidcraft.clothing.Spot;
import com.zomboidcraft.clothing.Tailoring;
import com.zomboidcraft.loot.items.LootItems;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarParts;
import com.zomboidcraft.world.cars.CarParts.Part;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Mechanics;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Her trades on top of the first jobs, each run by the same rules the player's own work is (PZ review section 18):
 * <ul>
 * <li><b>Tailor</b> (Tailoring): any piece with a hole in it, in the stores or handed to her, she takes, sits down with
 * and patches, one hole at a time, with a sewing kit, thread and the best strip there is (leather, denim, then sheet or
 * rag), and puts back in the stores mended. Stitches pull loose as often as yours would at her Tailoring level. She only
 * closes holes: reinforcing a whole piece is yours to choose.</li>
 * <li><b>Mechanic</b> (Mechanics): the cars parked at home. She tops up oil, brake fluid and coolant that have run low,
 * and swaps a worn-out battery, spark plugs, fan belt, bulb, horn or radio for a better one from the stores, when she has
 * the level and the tool for it. The old part goes into the stores with what's left of it. Anything that needs the car
 * up (tyres, brakes, the underside) or a manual stays your job.</li>
 * <li><b>Helping hands</b>: working on a car with a companion bonded to you close by and awake, she comes over and holds
 * things, and each job goes a fifth quicker (the mechanics screen's times show it); her own Mechanics level makes your
 * slips rarer. She learns from watching you as she always does (NpcSkills.watched).</li>
 * </ul>
 */
public final class NpcTrades {
    // ---------------------------------------------------------------- tailor

    private static final String KIT = "sewing_kit", THREAD = "thread_spool";
    /** Strips, best first. */
    private static final String[] STRIPS = {"leather_strips", "denim_strips", "ripped_sheet", "rag"};

    static boolean is(ItemStack s, String id) {
        Item item = LootItems.item(id);
        return !s.isEmpty() && (item != null ? s.is(item) : id.equals(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath()));
    }

    static final Predicate<ItemStack> SEWING_KIT = s -> is(s, KIT), THREADS = s -> is(s, THREAD);
    static final Predicate<ItemStack> STRIP = s -> {
        for (String id : STRIPS) if (is(s, id)) return true;
        return false;
    };

    /** The first hole on a piece that can take a patch, or null. */
    static int[] hole(ItemStack s) {
        Garment g = Clothes.of(s);
        if (g == null || !Tailoring.patchable(g)) return null;
        ClothWear wear = ClothWear.of(s);
        if (wear.holeCount() == 0) return null;
        for (Spot spot : g.covers()) {
            for (Region r : spot.regions()) {
                if (wear.hole(spot, r)) return new int[]{spot.ordinal(), r.ordinal()};
            }
        }
        return null;
    }

    static final Predicate<ItemStack> HOLED = s -> hole(s) != null;

    private static boolean sewingKitOn(NpcWork w) {
        return w.packHas(SEWING_KIT) && w.packHas(THREADS) && w.packHas(STRIP);
    }

    /** What a tailor or mechanic keeps on her between jobs rather than putting in the stores. */
    static boolean keeps(NpcEntity npc, ItemStack s) {
        NpcWork w = npc.work;
        return switch (w.job) {
            case TAILOR -> SEWING_KIT.test(s) || THREADS.test(s) || STRIP.test(s) || HOLED.test(s) && sewingKitOn(w);
            case MECHANIC -> TOOL.test(s) || FLUID.test(s);
            default -> false;
        };
    }

    static NpcWork.Task tailor(NpcWork w, NpcEntity npc, ServerLevel level) {
        boolean handed = w.packHas(HOLED);
        if (!handed && w.storeWith(level, HOLED) == null) {
            // nothing torn: mended pieces back to the stores
            return w.packEmpty() ? null : w.depositTask(npc, level, null);
        }
        // the needle, thread and something to sew on, from the stores if she hasn't them
        if (!w.packHas(SEWING_KIT)) return w.fetchTask(npc, level, SEWING_KIT, 1, "I can't sew anything without a needle. Is there a sewing kit anywhere?");
        if (!w.packHas(THREADS)) return w.fetchTask(npc, level, THREADS, 2, "I'm out of thread.");
        if (!w.packHas(STRIP)) {
            return w.fetchTask(npc, level, STRIP, 4, "I need something to patch with: rags, sheets, denim or leather strips.");
        }
        if (!handed) return w.fetchTask(npc, level, HOLED, 1, null);
        int tailoring = npc.skills.level(NpcSkills.Skill.TAILORING);
        return new NpcWork.Task(npc.blockPosition(), Math.round(100 * (1 - 0.05f * tailoring)), NpcWork.SIT, 3, () -> patch(w, npc, level));
    }

    /** One patch on the first holed piece in her pack: Tailoring's own odds, strip, thread and experience. */
    private static void patch(NpcWork w, NpcEntity npc, ServerLevel level) {
        int at = -1, stripAt = -1, threadAt = -1;
        for (int i = 0; i < w.pack.getContainerSize(); i++) {
            ItemStack s = w.pack.getItem(i);
            if (at < 0 && HOLED.test(s)) at = i;
            if (threadAt < 0 && THREADS.test(s)) threadAt = i;
        }
        for (String id : STRIPS) {
            for (int i = 0; i < w.pack.getContainerSize() && stripAt < 0; i++) {
                if (is(w.pack.getItem(i), id)) stripAt = i;
            }
        }
        if (at < 0 || stripAt < 0 || threadAt < 0 || !w.packHas(SEWING_KIT)) return;
        ItemStack piece = w.pack.getItem(at);
        int[] hole = hole(piece);
        Spot spot = Spot.values()[hole[0]];
        Region region = Region.values()[hole[1]];
        ItemStack strip = w.pack.getItem(stripAt);
        ClothWear.Patch kind = is(strip, "leather_strips") ? ClothWear.Patch.LEATHER
                : is(strip, "denim_strips") ? ClothWear.Patch.DENIM : ClothWear.Patch.FABRIC;
        strip.shrink(1);
        if (npc.getRandom().nextFloat() < 0.34f) w.pack.getItem(threadAt).shrink(1);
        int tailoring = npc.skills.level(NpcSkills.Skill.TAILORING);
        if (npc.getRandom().nextFloat() < 0.25f - 0.025f * tailoring) {
            w.practise(npc, NpcSkills.Skill.TAILORING, 2);
            w.grumble(npc, NpcWork.pick(npc, "Ugh, the stitches pulled out.", "That one's wasted. Again.", "Needle slipped. Hang on."));
            return;
        }
        piece.set(ClothWear.TYPE, ClothWear.of(piece).patched(spot, region, kind));
        w.pack.setChanged();
        w.practise(npc, NpcSkills.Skill.TAILORING, 6);
        level.playSound(null, npc.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.6f, 1.4f);
        if (hole(piece) == null) {
            npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, NpcWork.pick(npc, "There. Good as new. Nearly.",
                    "That " + piece.getHoverName().getString().toLowerCase(Locale.ROOT) + "'s mended.", "One more thing that won't let a bite through."));
        }
    }

    // ---------------------------------------------------------------- mechanic

    /** What she swaps on her own: no car up on a hoist, no manual needed (Mechanics' universal parts). */
    private static final EnumSet<Part> SWAPS = EnumSet.of(Part.BATTERY, Part.SPARK_PLUGS, Part.FAN_BELT, Part.HEADLIGHT,
            Part.TAIL_LIGHT, Part.HORN, Part.RADIO);
    /** A fluid she tops up below this, and a part she swaps below this (when the spare is clearly better). */
    private static final int LOW_FLUID = 60, WORN = 35, BETTER = 25;

    private static String tool(Part part) {
        return switch (part) {
            case HEADLIGHT, TAIL_LIGHT, HORN, RADIO -> "screwdriver";
            case BATTERY -> null;
            default -> "socket_set";
        };
    }

    private static String fluid(Part part) {
        return switch (part) {
            case OIL -> "motor_oil";
            case BRAKE_FLUID -> "brake_fluid";
            default -> "antifreeze";
        };
    }

    private static Predicate<ItemStack> fluidFor(Part part) {
        return part == Part.RADIATOR ? s -> is(s, "antifreeze") || is(s, "coolant") : s -> is(s, fluid(part));
    }

    static final Predicate<ItemStack> TOOL = s -> is(s, "screwdriver") || is(s, "socket_set");
    static final Predicate<ItemStack> FLUID = s -> is(s, "motor_oil") || is(s, "brake_fluid") || is(s, "antifreeze") || is(s, "coolant");

    /** A car part item for this part at least this good. */
    private static Predicate<ItemStack> spare(Part part, int atLeast) {
        return s -> {
            Mechanics.Held h = Mechanics.held(s);
            return h != null && h.id().equals(part.item) && h.condition() >= atLeast;
        };
    }

    private static List<CarProp> cars(NpcWork w, ServerLevel level) {
        List<CarProp> cars = level.getEntitiesOfClass(CarProp.class, new AABB(w.home).inflate(NpcWork.HOME_RADIUS), c -> !c.isRemoved());
        cars.sort((a, b) -> Double.compare(a.distanceToSqr(Vec3.atCenterOf(w.home)), b.distanceToSqr(Vec3.atCenterOf(w.home))));
        return cars;
    }

    private static String carName(CarProp car) {
        return "car";
    }

    static NpcWork.Task mechanic(NpcWork w, NpcEntity npc, ServerLevel level) {
        int mechanics = npc.skills.level(NpcSkills.Skill.MECHANICS);
        for (CarProp car : cars(w, level)) {
            String parts = car.parts();
            if (parts == null || parts.isEmpty()) continue;
            for (Part p : new Part[]{Part.OIL, Part.BRAKE_FLUID, Part.RADIATOR}) {
                int slot = CarParts.slot(p, 0);
                int c = CarParts.get(parts, slot);
                if (c < 0 || c >= LOW_FLUID || p == Part.RADIATOR && c < 25) continue;
                Predicate<ItemStack> want = fluidFor(p);
                if (!w.packHas(want)) {
                    if (w.storeWith(level, want) == null) {
                        w.grumble(npc, "The " + carName(car) + "'s low on " + (p == Part.RADIATOR ? "coolant" : CarParts.SLOTS.get(slot).title().toLowerCase(Locale.ROOT))
                                + ". We've got nothing to top it up with.");
                        continue;
                    }
                    return w.fetchTask(npc, level, want, 2, null);
                }
                return new NpcWork.Task(car.blockPosition(), 60, NpcWork.KNEEL, 3.2, () -> topUp(w, npc, level, car, p));
            }
            for (int slot = 0; slot < CarParts.SLOTS.size(); slot++) {
                Part p = CarParts.SLOTS.get(slot).part();
                if (!SWAPS.contains(p) || mechanics < p.level) continue;
                int c = CarParts.get(parts, slot);
                if (c < 0 || c >= WORN) continue;
                Predicate<ItemStack> better = spare(p, c + BETTER);
                String tool = tool(p);
                boolean spareOn = w.packHas(better), spareIn = spareOn || w.storeWith(level, better) != null;
                if (!spareIn) continue;
                if (tool != null && !w.packHas(s -> is(s, tool))) {
                    Predicate<ItemStack> t = s -> is(s, tool);
                    if (w.storeWith(level, t) == null) {
                        w.grumble(npc, "I could fix the " + carName(car) + "'s " + p.title.toLowerCase(Locale.ROOT) + " if we had a "
                                + tool.replace('_', ' ') + ".");
                        continue;
                    }
                    return w.fetchTask(npc, level, t, 1, null);
                }
                if (!spareOn) return w.fetchTask(npc, level, better, 1, null);
                int s = slot;
                int ticks = (int) Math.round((60 + p.level * 30) * 2 * (1 - 0.03 * Math.max(0, mechanics - p.level)));
                return new NpcWork.Task(car.blockPosition(), ticks, NpcWork.HAMMER, 3.2, () -> swap(w, npc, level, car, s));
            }
        }
        // nothing wants doing: old parts and the like into the stores
        return w.packEmpty() ? null : w.depositTask(npc, level, null);
    }

    private static void topUp(NpcWork w, NpcEntity npc, ServerLevel level, CarProp car, Part p) {
        if (car.isRemoved()) return;
        int slot = CarParts.slot(p, 0);
        String parts = car.parts();
        int c = CarParts.get(parts, slot);
        if (c < 0 || c >= 100) return;
        if (w.takeFromPack(fluidFor(p)).isEmpty()) return;
        int add = p == Part.OIL ? 50 : p == Part.BRAKE_FLUID ? 60 : 30;
        car.setParts(CarParts.set(parts, slot, Math.min(100, c + add)));
        w.practise(npc, NpcSkills.Skill.MECHANICS, 4);
        level.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 0.6f, 1.2f);
        npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, "Topped up the " + carName(car) + ".");
    }

    private static void swap(NpcWork w, NpcEntity npc, ServerLevel level, CarProp car, int slot) {
        if (car.isRemoved()) return;
        Part p = CarParts.SLOTS.get(slot).part();
        String parts = car.parts();
        int c = CarParts.get(parts, slot);
        String tool = tool(p);
        if (c < 0 || c >= WORN || tool != null && !w.packHas(s -> is(s, tool))) return;
        // the best spare she carries
        int best = -1, bestC = -1;
        for (int i = 0; i < w.pack.getContainerSize(); i++) {
            Mechanics.Held h = Mechanics.held(w.pack.getItem(i));
            if (h != null && h.id().equals(p.item) && h.condition() > bestC) {
                best = i;
                bestC = h.condition();
            }
        }
        if (best < 0 || bestC < c + BETTER) return;
        int mechanics = npc.skills.level(NpcSkills.Skill.MECHANICS);
        double slip = Math.max(0.03, 0.3 - 0.05 * (mechanics - p.level));
        CarParts.Kind kind = CarParts.kind(CarModels.get(car.model()));
        int old = npc.getRandom().nextDouble() < slip ? Math.max(0, c - 15 - npc.getRandom().nextInt(26)) : c;
        int fit = npc.getRandom().nextDouble() < slip ? Math.max(0, bestC - 5 - npc.getRandom().nextInt(16)) : bestC;
        w.pack.getItem(best).shrink(1);
        car.setParts(CarParts.set(parts, slot, fit));
        ItemStack off = Mechanics.stack(p, old, kind);
        ItemStack left = w.pack.addItem(off);
        if (!left.isEmpty()) npc.spawnAtLocation(level, left);
        w.practise(npc, NpcSkills.Skill.MECHANICS, 8 + p.level * 2 + 12 + p.level * 3);
        level.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.NEUTRAL, 0.7f, 1.1f);
        npc.lines.sayLine(npc, NpcLines.Kind.WORK_DONE, "New " + p.title.toLowerCase(Locale.ROOT) + " in the " + carName(car)
                + (fit < bestC ? ". Fought me the whole way." : "."));
    }

    // ---------------------------------------------------------------- helping hands

    /** How near the car a companion has to be to lend a hand, and how much quicker your jobs go with her. */
    public static final double HELP_REACH = 6, HELP_SPEED = 0.8;

    /** Whether she's bonded to this player; the client sets its own (it knows its bonds from the companions list). */
    public static BiPredicate<Player, NpcEntity> bonded = (p, n) -> n.bonds.contains(p.getUUID());

    /** The nearest companion bonded to you who's awake near this car, or null. Same answer on the client and the server. */
    public static NpcEntity helper(Player player, Entity car) {
        NpcEntity best = null;
        double near = Double.MAX_VALUE;
        for (NpcEntity n : player.level().getEntitiesOfClass(NpcEntity.class, car.getBoundingBox().inflate(HELP_REACH),
                n -> n.isAlive() && n.action() != NpcWork.SLEEP && bonded.test(player, n))) {
            double d = n.distanceToSqr(car);
            if (d < near) {
                near = d;
                best = n;
            }
        }
        return best;
    }

    /** You've started a job on this car: a companion close by comes and gives you a hand for as long as it takes. */
    public static void helpWith(Player player, Entity car, int ticks) {
        NpcEntity n = helper(player, car);
        if (n == null || n.level().isClientSide()) return;
        n.work.helpCar = car;
        n.work.helpUntil = n.level().getGameTime() + ticks;
        if (n.getRandom().nextInt(3) == 0) {
            n.lines.sayLine(n, NpcLines.Kind.REPLY, NpcWork.pick(n, "I'll hold it.", "Here, pass me that.", "Want a hand?",
                    "Careful, it's sharp under there."));
        }
    }

    /** How much rarer her steady hands make your slips: two points a Mechanics level. */
    public static double steadier(Player player, Entity car) {
        NpcEntity n = helper(player, car);
        return n == null ? 0 : 0.02 * n.skills.level(NpcSkills.Skill.MECHANICS);
    }

    /** Over by the car you're working on, holding things, till the job's done. */
    static final class Help extends Goal {
        private final NpcEntity npc;
        private int repath;

        Help(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            NpcWork w = npc.work;
            return w.helpCar != null && !w.helpCar.isRemoved() && npc.level().getGameTime() < w.helpUntil && !w.asleep;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            npc.work.action = NpcWork.NONE;
            npc.work.helpCar = null;
            npc.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Entity car = npc.work.helpCar;
            if (car == null) return;
            npc.getLookControl().setLookAt(car.getX(), car.getY() + 0.6, car.getZ());
            double d = npc.distanceTo(car) - car.getBbWidth() / 2;
            if (d > 1.6) {
                npc.work.action = NpcWork.NONE;
                if (--repath <= 0) {
                    repath = 15;
                    npc.getNavigation().moveTo(car.getX(), car.getY(), car.getZ(), 1.0 * npc.mind.pace());
                }
                return;
            }
            npc.getNavigation().stop();
            npc.work.action = NpcWork.KNEEL;
        }
    }

    private NpcTrades() {
    }
}
