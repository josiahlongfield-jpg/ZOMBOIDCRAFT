package com.zomboidcraft.creature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.barricade.BarricadeBlock;
import com.zomboidcraft.zombie.Corpse;
import com.zomboidcraft.zombie.Corpses;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the Harvestman hunting someone does by night, out of their sight, whether or not it's anywhere near (it was
 * always there):
 *
 * <ul>
 * <li>Some nights it does nothing but watch (a patient night: it won't strike the one it hunts until the next).</li>
 * <li>Once a night it prises a single plank off a barricade where they stay, and leaves it on the floor.</li>
 * <li>What it has picked up of theirs (HarvestmanSigns pockets things left lying about) turns up on their doorstep,
 * one thing a night.</li>
 * <li>Someone it killed finds their own body at their door a day or two after they come back, slumped in the doorway,
 * with something they carried beside it.</li>
 * </ul>
 *
 * What it owes each player (the body to return, the things it took) is kept on the player and survives their death.
 */
public final class HarvestmanNights {
    /** When night starts and ends in the day's 24000 ticks. */
    private static final long DUSK = 12_500, DAWN = 23_500;

    /** A body to bring back (and when), and what it took of theirs: kept on the player, across deaths. */
    public record Owed(long bodyDue, List<ItemStack> withBody, List<ItemStack> trophies, long lastPlank, long lastGift) {
        public static final Codec<Owed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("bodyDue", 0L).forGetter(Owed::bodyDue),
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("withBody", List.of()).forGetter(Owed::withBody),
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("trophies", List.of()).forGetter(Owed::trophies),
                Codec.LONG.optionalFieldOf("lastPlank", -1L).forGetter(Owed::lastPlank),
                Codec.LONG.optionalFieldOf("lastGift", -1L).forGetter(Owed::lastGift)).apply(i, Owed::new));
        static final Owed NONE = new Owed(0, List.of(), List.of(), -1, -1);
    }

    public static final AttachmentType<Owed> OWED = AttachmentRegistry.create(
            ZomboidCraft.id("harvestman_owed"),
            builder -> builder.persistent(Owed.CODEC).copyOnDeath());

    static void register() {
        // loading the class registers the attachment
    }

    // ------------------------------------------------------------------------------------------------ nights

    /** Which night it is (counting from the world's first dusk), or -1 by day. */
    static long night(ServerLevel level) {
        long t = level.getDefaultClockTime();
        long inDay = Math.floorMod(t, 24_000L);
        if (inDay < DUSK || inDay >= DAWN) {
            return -1;
        }
        return Math.floorDiv(t, 24_000L);
    }

    /**
     * A patient night (and the day after it): about one in four, never two in a row. The same on every check, from the
     * world's seed and which night it is.
     */
    static boolean patient(ServerLevel level) {
        long n = Math.floorDiv(level.getDefaultClockTime() - DUSK, 24_000L);
        return patientNight(level.getSeed(), n) && !patientNight(level.getSeed(), n - 1);
    }

    private static boolean patientNight(long seed, long n) {
        long x = seed ^ n * 0x9E3779B97F4A7C15L;
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        return Math.floorMod(x, 100L) < 25;
    }

    // ------------------------------------------------------------------------------------------------ each second

    /** The Director's second: works the nights for its target, and brings back bodies owed to anyone. */
    static void tick(ServerLevel level, ServerPlayer target) {
        long night = night(level);
        if (night < 0) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            Owed o = p.getAttachedOrElse(OWED, Owed.NONE);
            if (o.bodyDue() > 0 && level.getGameTime() >= o.bodyDue() && p.isAlive() && !p.isSpectator()) {
                returnBody(level, p, o);
            }
        }
        if (target == null || !target.isAlive() || target.isSpectator() || target.isCreative() || target.level() != level) {
            return;
        }
        // a while into the night, a little chance each second, so it's never at the same hour
        Owed o = target.getAttachedOrElse(OWED, Owed.NONE);
        if (o.lastPlank() != night && level.getRandom().nextDouble() < 1 / 400.0 && prise(level, target)) {
            o = target.getAttachedOrElse(OWED, Owed.NONE);
            target.setAttached(OWED, new Owed(o.bodyDue(), o.withBody(), o.trophies(), night, o.lastGift()));
        }
        o = target.getAttachedOrElse(OWED, Owed.NONE);
        if (o.lastGift() != night && !o.trophies().isEmpty() && level.getRandom().nextDouble() < 1 / 300.0) {
            Vec3 step = doorstep(level, target);
            if (step != null) {
                List<ItemStack> left = new ArrayList<>(o.trophies());
                drop(level, step, left.remove(0));
                target.setAttached(OWED, new Owed(o.bodyDue(), o.withBody(), left, o.lastPlank(), night));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ barricades

    /**
     * One plank prised off a barricade near where they stay, with nobody near enough to see or stop it, left lying on
     * the floor: a safe room slowly stops being safe. True if it found one.
     */
    private static boolean prise(ServerLevel level, ServerPlayer t) {
        Vec3 home = HarvestmanDirector.favourite(t, level);
        if (home == null) {
            return false;
        }
        BlockPos c = BlockPos.containing(home);
        if (!level.isLoaded(c)) {
            return false;
        }
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -4, -12), c.offset(12, 6, 12))) {
            BlockState s = level.getBlockState(q);
            if (!s.is(ModBlocks.BARRICADE) || watched(level, Vec3.atCenterOf(q), 10)) {
                continue;
            }
            BlockPos at = q.immutable();
            int planks = s.getValue(BarricadeBlock.PLANKS);
            if (planks <= 1) {
                level.removeBlock(at, false);
            } else {
                level.setBlockAndUpdate(at, s.setValue(BarricadeBlock.PLANKS, planks - 1));
            }
            Vec3 p = Vec3.atBottomCenterOf(at).add(0, 0.1, 0);
            drop(level, p, new ItemStack(Items.OAK_PLANKS));
            level.playSound(null, p.x, p.y + 1, p.z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.4f, 0.6f);
            level.playSound(null, p.x, p.y + 1, p.z, HarvestmanSounds.CREAK, SoundSource.HOSTILE, 0.5f, 0.8f);
            return true;
        }
        return false;
    }

    /** Is someone within `near` of a point, or able to see it from where they are (within 48)? */
    private static boolean watched(ServerLevel level, Vec3 p, double near) {
        for (ServerPlayer pl : level.players()) {
            if (pl.isSpectator()) {
                continue;
            }
            double d = pl.position().distanceTo(p);
            if (d < near) {
                return true;
            }
            if (d < 48) {
                var hit = level.clip(new ClipContext(pl.getEyePosition(), p, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pl));
                if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(BlockPos.containing(p))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void drop(ServerLevel level, Vec3 at, ItemStack stack) {
        ItemEntity e = new ItemEntity(level, at.x, at.y, at.z, stack);
        e.setDeltaMovement(Vec3.ZERO);
        e.setUnlimitedLifetime();
        level.addFreshEntity(e);
        LEFT.add(e.getUUID());
    }

    /** What it has set out itself (so it doesn't pick those up again). */
    private static final java.util.Set<java.util.UUID> LEFT = new java.util.HashSet<>();

    /** Did it leave this here itself? */
    static boolean leftIt(ItemEntity e) {
        return LEFT.contains(e.getUUID());
    }

    // ------------------------------------------------------------------------------------------------ doorsteps

    /**
     * Their doorstep: the outer side of a door near where they stay (the side open to the sky, or else the one further
     * from the middle of the place), somewhere nobody is or can see. Null when there's none to use now.
     */
    private static Vec3 doorstep(ServerLevel level, ServerPlayer t) {
        Vec3 home = HarvestmanDirector.favourite(t, level);
        if (home == null || !level.isLoaded(BlockPos.containing(home))) {
            return null;
        }
        BlockPos c = BlockPos.containing(home);
        Vec3 best = null;
        double bd = 1e9;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-12, -4, -12), c.offset(12, 6, 12))) {
            BlockState s = level.getBlockState(q);
            if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            Direction f = s.getValue(DoorBlock.FACING);
            BlockPos a = q.relative(f), b = q.relative(f.getOpposite());
            boolean aSky = level.canSeeSky(a), bSky = level.canSeeSky(b);
            BlockPos out = aSky != bSky ? (aSky ? a : b)
                    : Vec3.atCenterOf(a).distanceToSqr(home) > Vec3.atCenterOf(b).distanceToSqr(home) ? a : b;
            if (!level.isEmptyBlock(out) || level.isEmptyBlock(out.below())) {
                continue;
            }
            Vec3 p = Vec3.atBottomCenterOf(out);
            double d = Vec3.atCenterOf(q).distanceToSqr(home);
            if (d < bd && !watched(level, p.add(0, 0.5, 0), 12)) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------------------------------------ bodies

    /**
     * A Harvestman has killed this player: it will bring their body back to their door a day or two after they're back
     * on their feet, with one thing of theirs from where they fell (the first thing that doesn't stack: a bag, a gun,
     * a tool).
     */
    static void killed(ServerLevel level, ServerPlayer p) {
        ItemStack keep = ItemStack.EMPTY;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(p.position(), p.position()).inflate(3))) {
            if (e.isAlive() && e.getItem().getMaxStackSize() == 1) {
                keep = e.getItem().copy();
                e.discard();
                break;
            }
        }
        Owed o = p.getAttachedOrElse(OWED, Owed.NONE);
        long due = level.getGameTime() + 24_000 + level.getRandom().nextInt(24_000);
        p.setAttached(OWED, new Owed(due, keep.isEmpty() ? List.of() : List.of(keep), o.trophies(), o.lastPlank(), o.lastGift()));
    }

    private static void returnBody(ServerLevel level, ServerPlayer p, Owed o) {
        Vec3 step = doorstep(level, p);
        if (step == null) {
            return;     // nowhere fit tonight; another night
        }
        Corpse body = Corpses.lay(level, step, level.getRandom().nextFloat() * 360f, "survivor", 0, true);
        if (body != null) {
            Vec3 home = HarvestmanDirector.favourite(p, level);
            HarvestmanDens.setOut(level, body, step, home, 0f);
        }
        for (ItemStack s : o.withBody()) {
            drop(level, step.add(0.4, 0.1, 0.2), s);
        }
        p.setAttached(OWED, new Owed(0, List.of(), o.trophies(), o.lastPlank(), o.lastGift()));
        ZomboidCraft.LOGGER.info("A Harvestman left {}'s body at {}", p.getName().getString(), BlockPos.containing(step).toShortString());
    }

    /** Something of theirs it picked up (HarvestmanSigns): it keeps it for their doorstep. Up to three at a time. */
    static boolean pocket(ServerPlayer t, ItemStack stack) {
        Owed o = t.getAttachedOrElse(OWED, Owed.NONE);
        if (o.trophies().size() >= 3) {
            return false;
        }
        List<ItemStack> list = new ArrayList<>(o.trophies());
        list.add(stack.copy());
        t.setAttached(OWED, new Owed(o.bodyDue(), o.withBody(), list, o.lastPlank(), o.lastGift()));
        return true;
    }

    private HarvestmanNights() {
    }
}
