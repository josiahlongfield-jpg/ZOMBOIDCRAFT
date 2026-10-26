package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieLook;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Shooting limbs off. A bullet or pellet that strikes a zombie's arm or leg rather than its head tears at that
 * limb: every hit adds its damage (times how hard that kind of gun tears) to the forearm, upper arm, shin or thigh
 * it went into, and past that part's toughness it comes off at the joint above, flying along the shot. Buckshot up
 * close takes a limb nearly every time, a handgun takes a lot of hits, a hunting rifle can do it in one.
 *
 * Which part a shot went into is worked out from the line it took through the zombie, against the body's parts in
 * the pose the zombie is drawn in (standing, shambling, arms out after you, on one leg, crawling or flat on its
 * back), as measured off the character preview's poses.
 *
 * Losing a leg, it stumbles and then hops slowly on the other; losing both, it falls and crawls. A zombie with no
 * hands left can't grab hold of you.
 */
public final class Dismember {
    /** Limbs gone, one bit each (forearm at the elbow, whole arm at the shoulder, shin at the knee, leg at the hip). */
    public static final int EL_R = 1, EL_L = 2, SH_R = 4, SH_L = 8, KN_R = 16, KN_L = 32, HIP_R = 64, HIP_L = 128, CUTS = 255;
    public static final int RIGHT_LEG = KN_R | HIP_R, LEFT_LEG = KN_L | HIP_L, RIGHT_ARM = EL_R | SH_R, LEFT_ARM = EL_L | SH_L;

    /**
     * The limbs a zombie has lost (bits 0-7), which way the last one flew (the shot's heading in 256ths of a turn,
     * bits 8-15) and how hard (0 handgun to 3 buckshot, bits 16-17). Saved, and sent to clients, which draw the stumps
     * and throw the limb.
     */
    public static final AttachmentType<Integer> LIMBS = AttachmentRegistry.create(
            ZomboidCraft.id("limbs"),
            builder -> builder
                    .persistent(Codec.INT)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static final Identifier HOP_SPEED = ZomboidCraft.id("hop");

    // the body's parts, as capsules: from (x, y, z) to (x, y, z), radius, in blocks; x to the zombie's left, y up
    // from its feet, z ahead of it. Head, torso (two side by side), pelvis, then right upper arm, forearm, left upper
    // arm, forearm, right thigh, shin, left thigh, shin.
    private static final int PARTS = 12, HEAD = 0;
    private static final int[] CUT_OF = {0, 0, 0, 0, SH_R, EL_R, SH_L, EL_L, HIP_R, KN_R, HIP_L, KN_L};
    private static final int[] MIRROR = {0, 2, 1, 3, 6, 7, 4, 5, 10, 11, 8, 9};

    private static final float[] STAND = {-0.019f, 1.621f, 0.162f, -0.058f, 1.809f, 0.231f, 0.187f,
            0.097f, 1.127f, 0.025f, 0.097f, 1.415f, 0.087f, 0.119f,
            -0.097f, 1.127f, 0.025f, -0.097f, 1.415f, 0.087f, 0.119f,
            -0.091f, 0.925f, 0.000f, 0.091f, 0.925f, 0.000f, 0.108f,
            -0.282f, 1.482f, 0.101f, -0.322f, 1.202f, 0.038f, 0.080f,
            -0.322f, 1.202f, 0.038f, -0.399f, 0.796f, 0.041f, 0.077f,
            0.287f, 1.481f, 0.105f, 0.288f, 1.207f, 0.010f, 0.080f,
            0.288f, 1.207f, 0.010f, 0.314f, 0.796f, -0.011f, 0.077f,
            -0.103f, 0.862f, 0.000f, -0.112f, 0.454f, 0.029f, 0.097f,
            -0.112f, 0.454f, 0.029f, -0.120f, 0.041f, 0.042f, 0.085f,
            0.103f, 0.862f, 0.000f, 0.120f, 0.455f, 0.035f, 0.097f,
            0.120f, 0.455f, 0.035f, 0.132f, 0.043f, 0.037f, 0.085f};
    private static final float[] WALK = {-0.020f, 1.566f, 0.243f, -0.061f, 1.732f, 0.354f, 0.187f,
            0.097f, 1.100f, 0.039f, 0.097f, 1.380f, 0.135f, 0.119f,
            -0.097f, 1.100f, 0.039f, -0.097f, 1.380f, 0.135f, 0.119f,
            -0.090f, 0.903f, 0.000f, 0.090f, 0.903f, -0.000f, 0.108f,
            -0.284f, 1.445f, 0.153f, -0.306f, 1.166f, 0.091f, 0.080f,
            -0.306f, 1.166f, 0.091f, -0.367f, 0.764f, 0.121f, 0.077f,
            0.283f, 1.442f, 0.130f, 0.311f, 1.190f, 0.267f, 0.080f,
            0.311f, 1.190f, 0.267f, 0.395f, 1.004f, 0.617f, 0.077f,
            -0.102f, 0.840f, -0.001f, -0.093f, 0.449f, -0.008f, 0.097f,
            -0.093f, 0.449f, -0.008f, -0.085f, 0.079f, -0.102f, 0.085f,
            0.102f, 0.840f, -0.001f, 0.112f, 0.439f, -0.028f, 0.097f,
            0.112f, 0.439f, -0.028f, 0.118f, 0.040f, -0.080f, 0.085f};
    private static final float[] CHASE = {-0.006f, 1.476f, 0.325f, -0.018f, 1.679f, 0.335f, 0.187f,
            0.096f, 1.013f, 0.103f, 0.096f, 1.272f, 0.245f, 0.119f,
            -0.096f, 1.013f, 0.103f, -0.096f, 1.272f, 0.245f, 0.119f,
            -0.090f, 0.824f, 0.045f, 0.090f, 0.824f, 0.045f, 0.108f,
            -0.288f, 1.305f, 0.228f, -0.277f, 1.278f, 0.515f, 0.080f,
            -0.277f, 1.278f, 0.515f, -0.287f, 1.333f, 0.923f, 0.077f,
            0.287f, 1.307f, 0.228f, 0.282f, 1.262f, 0.513f, 0.080f,
            0.282f, 1.262f, 0.513f, 0.303f, 1.307f, 0.921f, 0.077f,
            -0.103f, 0.761f, 0.045f, -0.093f, 0.405f, 0.081f, 0.097f,
            -0.093f, 0.405f, 0.081f, -0.082f, 0.097f, -0.014f, 0.085f,
            0.103f, 0.761f, 0.045f, 0.107f, 0.391f, 0.079f, 0.097f,
            0.107f, 0.391f, 0.079f, 0.087f, 0.048f, -0.000f, 0.085f};
    private static final float[] CRAWL = {-0.013f, 0.304f, 0.280f, -0.033f, 0.500f, 0.329f, 0.187f,
            0.096f, 0.136f, -0.168f, 0.095f, 0.189f, 0.122f, 0.119f,
            -0.097f, 0.135f, -0.169f, -0.098f, 0.188f, 0.122f, 0.119f,
            -0.090f, 0.113f, -0.371f, 0.090f, 0.117f, -0.371f, 0.108f,
            -0.277f, 0.208f, 0.128f, -0.377f, 0.083f, 0.349f, 0.080f,
            -0.377f, 0.083f, 0.349f, -0.507f, 0.048f, 0.703f, 0.077f,
            0.275f, 0.206f, 0.128f, 0.360f, 0.115f, 0.373f, 0.080f,
            0.360f, 0.115f, 0.373f, 0.467f, 0.050f, 0.726f, 0.077f,
            -0.103f, 0.112f, -0.433f, -0.154f, 0.069f, -0.835f, 0.097f,
            -0.154f, 0.069f, -0.835f, -0.183f, 0.179f, -1.247f, 0.085f,
            0.103f, 0.117f, -0.433f, 0.157f, 0.056f, -0.832f, 0.097f,
            0.157f, 0.056f, -0.832f, 0.172f, 0.194f, -1.236f, 0.085f};
    private static final float[] DOWN = {0.000f, 0.121f, -0.357f, 0.000f, 0.222f, -0.534f, 0.187f,
            0.097f, 0.088f, 0.137f, 0.097f, 0.064f, -0.158f, 0.119f,
            -0.097f, 0.088f, 0.137f, -0.097f, 0.064f, -0.158f, 0.119f,
            -0.091f, 0.098f, 0.341f, 0.091f, 0.098f, 0.341f, 0.108f,
            -0.279f, 0.031f, -0.203f, -0.349f, 0.295f, -0.109f, 0.080f,
            -0.349f, 0.295f, -0.109f, -0.478f, 0.674f, -0.217f, 0.077f,
            0.277f, 0.041f, -0.217f, 0.361f, 0.213f, 0.000f, 0.080f,
            0.361f, 0.213f, 0.000f, 0.480f, 0.458f, 0.308f, 0.077f,
            -0.103f, 0.098f, 0.403f, -0.144f, 0.293f, 0.761f, 0.097f,
            -0.144f, 0.293f, 0.761f, -0.173f, 0.155f, 1.164f, 0.085f,
            0.103f, 0.098f, 0.403f, 0.152f, 0.138f, 0.808f, 0.097f,
            0.152f, 0.138f, 0.808f, 0.202f, 0.116f, 1.225f, 0.085f};
    /** On its left leg, the right gone (a zombie on its right leg is the mirror image). */
    private static final float[] HOP = {-0.177f, 1.564f, 0.250f, -0.208f, 1.746f, 0.335f, 0.187f,
            -0.002f, 1.114f, 0.046f, -0.048f, 1.385f, 0.152f, 0.119f,
            -0.193f, 1.085f, 0.039f, -0.239f, 1.356f, 0.145f, 0.119f,
            -0.165f, 0.899f, 0.000f, 0.017f, 0.910f, 0.000f, 0.108f,
            -0.408f, 1.382f, 0.156f, -0.609f, 1.182f, 0.108f, 0.080f,
            -0.609f, 1.182f, 0.108f, -0.899f, 0.918f, 0.215f, 0.077f,
            0.108f, 1.467f, 0.173f, 0.332f, 1.288f, 0.157f, 0.080f,
            0.332f, 1.288f, 0.157f, 0.628f, 1.082f, 0.344f, 0.077f,
            -0.173f, 0.836f, 0.000f, -0.232f, 0.453f, 0.119f, 0.097f,
            -0.232f, 0.453f, 0.119f, -0.290f, 0.052f, 0.204f, 0.085f,
            0.033f, 0.849f, 0.000f, 0.023f, 0.473f, 0.146f, 0.097f,
            0.023f, 0.473f, 0.146f, 0.019f, 0.068f, 0.117f, 0.085f};
    private static final float[] HOP_REACH = {-0.177f, 1.564f, 0.250f, -0.208f, 1.746f, 0.335f, 0.187f,
            -0.002f, 1.114f, 0.046f, -0.048f, 1.385f, 0.152f, 0.119f,
            -0.193f, 1.085f, 0.039f, -0.239f, 1.356f, 0.145f, 0.119f,
            -0.165f, 0.899f, 0.000f, 0.017f, 0.910f, 0.000f, 0.108f,
            -0.423f, 1.374f, 0.122f, -0.498f, 1.243f, 0.367f, 0.080f,
            -0.498f, 1.243f, 0.367f, -0.635f, 1.164f, 0.747f, 0.077f,
            0.128f, 1.455f, 0.140f, 0.188f, 1.373f, 0.408f, 0.080f,
            0.188f, 1.373f, 0.408f, 0.272f, 1.350f, 0.810f, 0.077f,
            -0.173f, 0.836f, 0.000f, -0.232f, 0.453f, 0.119f, 0.097f,
            -0.232f, 0.453f, 0.119f, -0.290f, 0.052f, 0.204f, 0.085f,
            0.033f, 0.849f, 0.000f, 0.023f, 0.473f, 0.146f, 0.097f,
            0.023f, 0.473f, 0.146f, 0.019f, 0.068f, 0.117f, 0.085f};

    /** What each limb has taken so far, by cut bit, and the last shot that struck it. Server thread only. */
    private static final class Wounds {
        final float[] dealt = new float[8];
        Vec3 dir;
        GunType gun;
    }

    private static final Map<Zombie, Wounds> WOUNDS = new WeakHashMap<>();
    /** Zombies shot this tick: a whole blast lands before any limb comes off. */
    private static final List<Zombie> SHOT = new ArrayList<>();
    /** Arms and legs, the whole limb first: when a blast takes both halves, it comes off in one piece. */
    private static final int[] ORDER = {SH_R, SH_L, HIP_R, HIP_L, EL_R, EL_L, KN_R, KN_L};

    /** A fresh stump still bleeding: drips for a few seconds. */
    private record Bleed(Zombie zombie, int part, long until) {
    }

    private static final List<Bleed> BLEEDING = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Dismember::tick);
    }

    /** The limbs this zombie has lost (the cut bits). */
    public static int cuts(Entity entity) {
        return entity.getAttachedOrElse(LIMBS, 0) & CUTS;
    }

    /** The whole saved value: cuts, plus which way and how hard the last limb flew. */
    public static int limbs(Entity entity) {
        return entity.getAttachedOrElse(LIMBS, 0);
    }

    /** How many legs it has lost: 0, 1 or 2. */
    public static int legsGone(int cuts) {
        return ((cuts & RIGHT_LEG) != 0 ? 1 : 0) + ((cuts & LEFT_LEG) != 0 ? 1 : 0);
    }

    /** Has at least one hand: shot off at either joint, or lost long ago, an arm has none. */
    public static boolean hasHands(Entity zombie) {
        int cuts = cuts(zombie), missing = ZombieLook.missingLimb(zombie);
        boolean right = (cuts & RIGHT_ARM) == 0 && missing != 1 && missing != 3;
        boolean left = (cuts & LEFT_ARM) == 0 && missing != 2 && missing != 4;
        return right || left;
    }

    /**
     * One bullet or pellet striking a zombie (from the gun code, once per pellet that hit it).
     *
     * @param damage the hit's damage as dealt (with the gun's rolled power and buckshot falloff)
     * @param head the shot was counted a headshot: the head took it, not a limb
     * @param legs the shot was counted low (the gun deals 0.7 of its damage there; a limb takes all of it)
     */
    public static void pellet(LivingEntity target, Vec3 eye, Vec3 dir, float damage, boolean head, boolean legs,
                              GunType gun) {
        ZcConfig.Limbs config = ZcConfig.get().limbs;
        if (!config.enabled || head || !(target instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie)) {
            return;
        }
        int part = part(zombie, eye, dir);
        if (part < 0 || CUT_OF[part] == 0) {
            return;
        }
        Wounds w = WOUNDS.computeIfAbsent(zombie, z -> new Wounds());
        w.dealt[Integer.numberOfTrailingZeros(CUT_OF[part])] += (float) (damage / (legs ? 0.7 : 1.0) * tearing(gun, config));
        w.dir = dir;
        w.gun = gun;
        if (!SHOT.contains(zombie)) {
            SHOT.add(zombie);
        }
    }

    /** After the shots: every limb past its toughness comes off. */
    private static void settle(Zombie zombie) {
        Wounds w = WOUNDS.get(zombie);
        if (w == null || zombie.isRemoved()) {
            return;
        }
        ZcConfig.Limbs config = ZcConfig.get().limbs;
        for (int cut : ORDER) {
            int i = Integer.numberOfTrailingZeros(cut);
            if ((cuts(zombie) & (cut | above(cut))) != 0) {
                w.dealt[i] = 0f;
            } else if (w.dealt[i] >= toughness(cut, config)) {
                w.dealt[i] = 0f;
                sever(zombie, cut, partOf(cut), w.dir, w.gun);
            }
        }
    }

    private static double tearing(GunType gun, ZcConfig.Limbs config) {
        if (gun.pellets() > 1) {
            return config.shotguns;
        }
        return switch (gun) {
            case PISTOL, REVOLVER, MAGNUM, SMG, RIMFIRE -> config.handguns;
            case ASSAULT_RIFLE -> config.rifles;
            case HUNTING_RIFLE, LEVER_ACTION -> config.huntingRifles;
            case CROSSBOW -> config.crossbows;
            default -> config.otherGuns;
        };
    }

    /** How hard a limb is flung: 0 handgun, 1 rifle, 2 hunting rifle, 3 buckshot. */
    private static int force(GunType gun) {
        if (gun.pellets() > 1) {
            return 3;
        }
        return switch (gun) {
            case HUNTING_RIFLE, LEVER_ACTION -> 2;
            case ASSAULT_RIFLE, MAGNUM -> 1;
            default -> 0;
        };
    }

    private static double toughness(int cut, ZcConfig.Limbs config) {
        return switch (cut) {
            case EL_R, EL_L -> config.forearm;
            case SH_R, SH_L -> config.arm;
            case KN_R, KN_L -> config.shin;
            default -> config.leg;
        };
    }

    /** Takes the limb off: it flies along the shot, the stump bleeds, and the zombie copes with what it has left. */
    private static void sever(Zombie zombie, int cut, int part, Vec3 dir, GunType gun) {
        int before = cuts(zombie), cuts = before | cut;
        int heading = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) / 360f * 256f), 256);
        zombie.setAttached(LIMBS, cuts | heading << 8 | force(gun) << 16);
        ServerLevel level = (ServerLevel) zombie.level();
        Vec3 joint = joint(zombie, part);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                joint.x, joint.y, joint.z, 30, 0.1, 0.1, 0.1, 0.3);
        level.playSound(null, joint.x, joint.y, joint.z, ModSounds.BULLET_FLESH, SoundSource.HOSTILE, 1.2f,
                0.55f + zombie.getRandom().nextFloat() * 0.1f);
        BLEEDING.add(new Bleed(zombie, part, level.getGameTime() + 120));
        StuckBolts.shed(zombie, joint);     // any bolts in the limb fall out with it
        if (!zombie.isAlive()) {
            return;
        }
        int legsBefore = legsGone(before), legs = legsGone(cuts);
        if (legs == 2 && legsBefore < 2) {
            // down it goes, and drags itself on its arms from now on
            removeHop(zombie);
            Downed.makeCrawler(zombie);
        } else if (legs == 1 && legsBefore == 0) {
            // it stumbles onto its knee, then gets up and hops on the leg it has
            Downed.stagger(zombie, 34);
            AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.addOrReplacePermanentModifier(new AttributeModifier(HOP_SPEED,
                        ZcConfig.get().limbs.hopSpeed - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static void removeHop(Zombie zombie) {
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(HOP_SPEED);
        }
    }

    /** Puts every limb back (the test command's fresh zombie). */
    public static void restore(Zombie zombie) {
        zombie.setAttached(LIMBS, 0);
        WOUNDS.remove(zombie);
        removeHop(zombie);
    }

    /** Takes a limb off with no shot (the test command's hopper), as if blown off from behind. */
    public static void cut(Zombie zombie, int cut) {
        sever(zombie, cut, partOf(cut), zombie.getLookAngle().reverse(), GunType.PISTOL);
    }

    private static int partOf(int cut) {
        for (int i = 0; i < PARTS; i++) {
            if (CUT_OF[i] == cut) {
                return i;
            }
        }
        return HEAD;
    }

    /** The pose the zombie is drawn in, near enough: which part table, and whether it's the mirror image. */
    private static float[] pose(Zombie zombie, int cuts, boolean[] mirror) {
        mirror[0] = false;
        if (Downed.isLow(zombie)) {
            return Downed.isCrawler(zombie) ? CRAWL : DOWN;
        }
        if (legsGone(cuts) == 1) {
            mirror[0] = (cuts & LEFT_LEG) != 0;
            return zombie.isAggressive() ? HOP_REACH : HOP;
        }
        if (zombie.isAggressive()) {
            return CHASE;
        }
        return zombie.getDeltaMovement().horizontalDistanceSqr() > 1e-4 ? WALK : STAND;
    }

    /**
     * Which of the body's parts a shot went into first, or -1 if its line misses them all (between the legs, say).
     * Parts already gone don't stop it.
     */
    private static int part(Zombie zombie, Vec3 eye, Vec3 dir) {
        Strike strike = strike(zombie, eye, dir);
        return strike == null ? -1 : strike.part();
    }

    /**
     * Where a shot went into the body: the part (head 0, torso 1 and 2, pelvis 3, then the arms and legs as in the
     * tables), its capsule's ends and radius in the pose it was in, the point the shot went in at and the shot's
     * heading, all in the zombie's own frame (x to its left, y up from its feet, z ahead) at scale 1.
     */
    public record Strike(int part, Vec3 a, Vec3 b, double radius, Vec3 at, Vec3 dir) {
    }

    /** The part a shot went into first and where (see {@link Strike}), or null if its line misses them all. */
    public static Strike strike(Zombie zombie, Vec3 eye, Vec3 dir) {
        int cuts = cuts(zombie);
        boolean[] mirror = new boolean[1];
        float[] caps = pose(zombie, cuts, mirror);
        int gone = goneParts(zombie, cuts);
        // the shot's line in the zombie's own frame: x to its left, y up from its feet, z ahead
        double yaw = Math.toRadians(zombie.yBodyRot), scale = Math.max(0.1, zombie.getScale());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
        double ox = eye.x - zombie.getX(), oy = eye.y - zombie.getY(), oz = eye.z - zombie.getZ();
        double px = (ox * lx + oz * lz) / scale, py = oy / scale, pz = (ox * fx + oz * fz) / scale;
        double dx = dir.x * lx + dir.z * lz, dy = dir.y, dz = dir.x * fx + dir.z * fz;
        // a zombie hopping on its right leg is the left-leg table mirrored: look it up mirrored, answer unmirrored
        double m = mirror[0] ? -1 : 1;
        int best = -1, bestK = -1;
        double nearest = Double.MAX_VALUE;
        for (int k = 0; k < PARTS; k++) {
            int real = mirror[0] ? MIRROR[k] : k;
            if ((gone >> real & 1) != 0) {
                continue;
            }
            double t = enter(px * m, py, pz, dx * m, dy, dz, caps, k * 7);
            if (t >= 0 && t < nearest) {
                nearest = t;
                best = real;
                bestK = k;
            }
        }
        if (best < 0) {
            return null;
        }
        int o = bestK * 7;
        return new Strike(best, new Vec3(caps[o] * m, caps[o + 1], caps[o + 2]),
                new Vec3(caps[o + 3] * m, caps[o + 4], caps[o + 5]), caps[o + 6],
                new Vec3(px + nearest * dx, py + nearest * dy, pz + nearest * dz), new Vec3(dx, dy, dz));
    }

    /** The part isn't there any more: shot off, or lost before it rose. */
    public static boolean gone(Zombie zombie, int part) {
        return (goneParts(zombie, cuts(zombie)) >> part & 1) != 0;
    }

    /** The parts not there to hit: shot off, lost before it rose, or (with no legs at all) both legs. */
    private static int goneParts(Zombie zombie, int cuts) {
        int gone = 0;
        for (int k = 0; k < PARTS; k++) {
            int c = CUT_OF[k];
            if (c != 0 && (cuts & (c | above(c))) != 0) {
                gone |= 1 << k;
            }
        }
        switch (ZombieLook.missingLimb(zombie)) {
            case 1 -> gone |= 1 << 5;
            case 2 -> gone |= 1 << 7;
            case 3 -> gone |= 1 << 4 | 1 << 5;
            case 4 -> gone |= 1 << 6 | 1 << 7;
            default -> { }
        }
        if (Downed.isLegless(zombie)) {
            gone |= 0xF << 8;
        }
        return gone;
    }

    /** The cut at the joint above: losing the whole arm or leg takes the lower part too. */
    private static int above(int cut) {
        return switch (cut) {
            case EL_R -> SH_R;
            case EL_L -> SH_L;
            case KN_R -> HIP_R;
            case KN_L -> HIP_L;
            default -> 0;
        };
    }

    /**
     * How far along the line (p + t d) it enters a capsule, or -1 if it passes outside it: the nearest approach
     * to the capsule's core, less the depth of the rim there.
     */
    private static double enter(double px, double py, double pz, double dx, double dy, double dz, float[] c, int o) {
        double ax = c[o], ay = c[o + 1], az = c[o + 2];
        double ux = c[o + 3] - ax, uy = c[o + 4] - ay, uz = c[o + 5] - az, r = c[o + 6];
        double wx = px - ax, wy = py - ay, wz = pz - az;
        double a = dx * dx + dy * dy + dz * dz, b = dx * ux + dy * uy + dz * uz, uu = ux * ux + uy * uy + uz * uz;
        double dw = dx * wx + dy * wy + dz * wz, uw = ux * wx + uy * wy + uz * wz;
        double den = a * uu - b * b;
        double s = den > 1e-9 ? Math.clamp((a * uw - b * dw) / den, 0, 1) : 0;
        double t = (s * b - dw) / a;
        if (t < 0) {
            t = 0;
            s = Math.clamp(uw / uu, 0, 1);
        }
        double cx = wx + t * dx - s * ux, cy = wy + t * dy - s * uy, cz = wz + t * dz - s * uz;
        double d2 = cx * cx + cy * cy + cz * cz;
        if (d2 > r * r) {
            return -1;
        }
        return Math.max(0, t - Math.sqrt((r * r - d2) / a));
    }

    /** Where a part meets the body (its capsule's upper end) in the world, near enough. */
    private static Vec3 joint(Zombie zombie, int part) {
        int cuts = cuts(zombie);
        boolean[] mirror = new boolean[1];
        float[] caps = pose(zombie, cuts, mirror);
        int k = mirror[0] ? MIRROR[part] : part;
        double x = caps[k * 7] * (mirror[0] ? -1 : 1), y = caps[k * 7 + 1], z = caps[k * 7 + 2];
        double yaw = Math.toRadians(zombie.yBodyRot), scale = zombie.getScale();
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
        return new Vec3(zombie.getX() + (x * lx + z * fx) * scale, zombie.getY() + y * scale,
                zombie.getZ() + (x * lz + z * fz) * scale);
    }

    private static void tick(MinecraftServer server) {
        if (!SHOT.isEmpty()) {
            for (Zombie zombie : SHOT) {
                settle(zombie);
            }
            SHOT.clear();
        }
        if (BLEEDING.isEmpty()) {
            return;
        }
        Iterator<Bleed> it = BLEEDING.iterator();
        while (it.hasNext()) {
            Bleed b = it.next();
            Zombie zombie = b.zombie();
            if (zombie.isRemoved() || !(zombie.level() instanceof ServerLevel level) || level.getGameTime() > b.until()) {
                it.remove();
                continue;
            }
            if (level.getGameTime() % 4 == 0) {
                Vec3 at = joint(zombie, b.part());
                level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        at.x, at.y - 0.05, at.z, 1, 0.03, 0.02, 0.03, 0.0);
            }
        }
    }

    private Dismember() {
    }
}
