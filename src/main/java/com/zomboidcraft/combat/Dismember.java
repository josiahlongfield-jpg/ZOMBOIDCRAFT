package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.ModDamageTypes;
import com.zomboidcraft.zombie.ZombieLook;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
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
 *
 * A big enough hit to the head bursts it: a hunting rifle headshot, or most of a blast of buckshot up close. Most of
 * a blast of buckshot in the chest from point-blank range blows a hole right through it.
 *
 * A pipe bomb or grenade going off tears at every limb at once, the ones facing it and nearest it most ({@link #blast}),
 * and right on top of one the head sometimes goes too.
 */
public final class Dismember {
    /** Limbs gone, one bit each (forearm at the elbow, whole arm at the shoulder, shin at the knee, leg at the hip). */
    public static final int EL_R = 1, EL_L = 2, SH_R = 4, SH_L = 8, KN_R = 16, KN_L = 32, HIP_R = 64, HIP_L = 128, CUTS = 255;
    public static final int RIGHT_LEG = KN_R | HIP_R, LEFT_LEG = KN_L | HIP_L, RIGHT_ARM = EL_R | SH_R, LEFT_ARM = EL_L | SH_L;
    /** Its head has been blown apart (bit 18 of the saved value, apart from the limbs). */
    public static final int HEAD_GONE = 1 << 18;
    /** Its chest has been blown open by buckshot up close, a hole right through it (bit 19). */
    public static final int CHEST_HOLE = 1 << 19;
    /** What it bears apart from its limbs: the head gone, the chest blown open. */
    public static final int MARKS = HEAD_GONE | CHEST_HOLE;

    /**
     * The limbs a zombie has lost (bits 0-7), which way the last one flew (the shot's heading in 256ths of a turn,
     * bits 8-15) and how hard (0 handgun to 3 buckshot, bits 16-17), whether its head burst (bit 18) and whether its
     * chest was blown open (bit 19). Saved, and sent to clients, which draw the stumps and the hole and throw the limb
     * or the pieces.
     */
    public static final AttachmentType<Integer> LIMBS = AttachmentRegistry.create(
            ZomboidCraft.id("limbs"),
            builder -> builder
                    .persistent(Codec.INT)
                    .syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

    private static final Identifier HOP_SPEED = ZomboidCraft.id("hop");
    private static final Identifier WRIGGLE_SPEED = ZomboidCraft.id("wriggle");

    // the body's parts: head, torso (two side by side), pelvis, then right upper arm, forearm, left upper arm, forearm,
    // right thigh, shin, left thigh, shin. Their shapes in each pose are in BodyCaps.
    private static final int PARTS = 12, HEAD = 0, CHEST = 1;
    private static final int[] CUT_OF = {0, 0, 0, 0, SH_R, EL_R, SH_L, EL_L, HIP_R, KN_R, HIP_L, KN_L};
    private static final int[] MIRROR = {0, 2, 1, 3, 6, 7, 4, 5, 10, 11, 8, 9};
    /** How far past its box a walker's body reaches (arms held out, lying flat, crawling), in blocks at scale 1. */
    private static final double REACH = 1.0, REACH_UP = 0.3;
    /** How far a walker can have moved in the ticks a shot looks back, in blocks. */
    private static final double MOVED = 3.0;
    private static final String[] PART_NAMES = {"head", "chest", "chest", "hips", "right upper arm", "right forearm",
            "left upper arm", "left forearm", "right thigh", "right shin", "left thigh", "left shin"};

    /**
     * What each limb has taken so far, by cut bit, and the last shot that struck it; the parts struck this tick and by
     * whom, for the hits readout. Server thread only.
     */
    private static final class Wounds {
        final float[] dealt = new float[8];
        Vec3 dir;
        GunType gun;
        int struck;
        ServerPlayer shooter;
        /** What this tick's shots put into the head, toward bursting it, and the last shot that did. */
        float head;
        Vec3 headDir;
        GunType headGun;
        /** Pellets of buckshot that went into its chest this tick from point-blank range, toward blowing it open. */
        int chestPellets;
        /** The last shot to land anywhere, and when: the way it falls if that killed it (Deaths). */
        Vec3 shotDir;
        GunType shotGun;
        int shotPart;
        long shotTick;
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

    /** Its head has been blown apart. */
    public static boolean headGone(Entity entity) {
        return (limbs(entity) & HEAD_GONE) != 0;
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
        return hands(zombie) > 0;
    }

    /** How many hands it has left, 0 to 2. */
    public static int hands(Entity zombie) {
        int cuts = cuts(zombie), missing = ZombieLook.missingLimb(zombie);
        boolean right = (cuts & RIGHT_ARM) == 0 && missing != 1 && missing != 3;
        boolean left = (cuts & LEFT_ARM) == 0 && missing != 2 && missing != 4;
        return (right ? 1 : 0) + (left ? 1 : 0);
    }

    /**
     * On the ground a zombie drags itself on its arms: slower with one left, and with none it can only wriggle along
     * on its belly (still coming for you; only a headshot stops it). Up on its feet, it walks as it is.
     */
    public static void crawlSpeed(Zombie zombie) {
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        int hands = hands(zombie);
        if (!Downed.isCrawler(zombie) || hands == 2) {
            speed.removeModifier(WRIGGLE_SPEED);
            return;
        }
        ZcConfig.Limbs config = ZcConfig.get().limbs;
        speed.addOrReplacePermanentModifier(new AttributeModifier(WRIGGLE_SPEED,
                (hands == 1 ? config.oneArmCrawl : config.wriggleSpeed) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /**
     * What a shot along eye to stop (where it meets a block, or the end of its range) hits first. A walker is hit where
     * its body is drawn, not by its box: the box is a thin post, and the arms reach out past it (all the way when they
     * reach for you), as does most of a zombie lying or crawling. So a shot at an arm held out or a leg on the ground
     * lands, and one through the box beside the body misses. It's hit where the shooter saw it, a few ticks back
     * (BodyTrack), so a zombie running across your sights is hit where it was drawn; a tick either side of that
     * still counts if the line misses there. Anything else is hit by its box, as vanilla does.
     */
    public static BodyHit shot(Entity shooter, Vec3 eye, Vec3 stop, Predicate<Entity> canHit) {
        Vec3 line = stop.subtract(eye);
        double range = line.length();
        if (range < 1e-6) {
            return null;
        }
        Vec3 dir = line.scale(1 / range);
        int lag = BodyTrack.lag(shooter);
        BodyHit best = null;
        double nearest = range;
        for (Entity e : shooter.level().getEntities(shooter, new AABB(eye, stop).inflate(REACH + MOVED), canHit)) {
            if (e instanceof com.zomboidcraft.creature.HarvestmanEntity) {
                continue;   // its own pass, below
            }
            if (shooter.isPassenger() && e.getRootVehicle() == shooter.getRootVehicle()) {
                continue;   // not whoever's riding with you
            }
            if (e instanceof Zombie zombie && ZombieTypes.isWalker(zombie)) {
                for (int back : new int[] {lag, lag + 1, lag - 1}) {
                    if (back < 0) {
                        continue;
                    }
                    BodyTrack.Frame f = BodyTrack.frame(zombie, back);
                    double s = Math.max(0.1, zombie.getScale());
                    AABB reach = zombie.getBoundingBox().move(f.x() - zombie.getX(), f.y() - zombie.getY(), f.z() - zombie.getZ())
                            .inflate(REACH * s, REACH_UP * s, REACH * s);
                    if (!reach.contains(eye) && reach.clip(eye, stop).isEmpty()) {
                        continue;
                    }
                    Strike strike = strike(zombie, f, eye, dir);
                    if (strike != null) {
                        if (strike.distance() < nearest) {
                            nearest = strike.distance();
                            best = new BodyHit(zombie, eye.add(dir.scale(nearest)), strike.part());
                        }
                        break;
                    }
                }
            } else {
                AABB box = e.getBoundingBox().inflate(e.getPickRadius());
                Optional<Vec3> at = box.contains(eye) ? Optional.of(eye) : box.clip(eye, stop);
                if (at.isPresent() && eye.distanceTo(at.get()) < nearest) {
                    nearest = eye.distanceTo(at.get());
                    best = new BodyHit(e, at.get(), -1);
                }
            }
        }
        // a Harvestman is hit where its hull, belly and legs are (they reach well past its box)
        com.zomboidcraft.creature.HarvestmanEntity hm = null;
        com.zomboidcraft.creature.HarvestmanBody.Strike hs = null;
        for (com.zomboidcraft.creature.HarvestmanEntity h : shooter.level().getEntitiesOfClass(
                com.zomboidcraft.creature.HarvestmanEntity.class, new AABB(eye, stop).inflate(6), canHit::test)) {
            com.zomboidcraft.creature.HarvestmanBody.Strike s = h.strike(eye, dir, nearest);
            if (s != null && s.distance() < nearest) {
                nearest = s.distance();
                hm = h;
                hs = s;
            }
        }
        if (hm != null) {
            hm.struck(hs);
            return new BodyHit(hm, hs.at(), -1);
        }
        return best;
    }

    /**
     * A shot's hit: what, where it went in, and on a walker the part of the body (head 0, torso 1 and 2, pelvis 3, then
     * the arms and legs as in the tables), or -1 for anything else.
     */
    public static final class BodyHit extends EntityHitResult {
        public final int part;

        BodyHit(Entity entity, Vec3 at, int part) {
            super(entity, at);
            this.part = part;
        }

        /** It went into a walker's head. */
        public boolean head() {
            return part == HEAD;
        }

        /** It went into a walker's thigh or shin. */
        public boolean leg() {
            return part >= 8;
        }
    }

    /**
     * One bullet or pellet striking a zombie (from the gun code, once per pellet that hit it).
     *
     * @param shooter who fired it, for the hits readout
     * @param hit where it went in, and the part of the body it struck (from {@link #shot})
     * @param damage the hit's damage as dealt (with the gun's rolled power and buckshot falloff)
     * @param head the shot was counted a headshot: the head took it, not a limb
     * @param legs the shot was counted low (the gun deals 0.7 of its damage there; a limb takes all of it)
     */
    public static void pellet(ServerPlayer shooter, LivingEntity target, BodyHit hit, Vec3 dir, float damage, boolean head,
                              boolean legs, GunType gun) {
        ZcConfig.Limbs config = ZcConfig.get().limbs;
        if (!(target instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie)) {
            return;
        }
        int part = head ? HEAD : hit.part;
        if (part < 0) {
            return;
        }
        Wounds w = WOUNDS.computeIfAbsent(zombie, z -> new Wounds());
        w.struck |= 1 << part;
        w.shooter = shooter;
        w.shotDir = dir;
        w.shotGun = gun;
        w.shotPart = part;
        w.shotTick = zombie.level().getGameTime();
        if (!SHOT.contains(zombie)) {
            SHOT.add(zombie);
        }
        if (!config.enabled) {
            return;
        }
        if (CUT_OF[part] != 0) {
            w.dealt[Integer.numberOfTrailingZeros(CUT_OF[part])] += (float) (damage / (legs ? 0.7 : 1.0) * tearing(gun, config));
            w.dir = dir;
            w.gun = gun;
        } else if (part == HEAD) {
            w.head += (float) (damage * bursting(gun, config));
            w.headDir = dir;
            w.headGun = gun;
        } else if ((part == CHEST || part == 2) && gun.pellets() > 1 && shooter != null
                && shooter.getEyePosition().distanceTo(hit.getLocation()) <= config.chestHoleRange) {
            w.chestPellets++;
        }
    }

    /** The shot that last landed on a zombie, if it was this tick or the one before. */
    record Shot(Vec3 dir, GunType gun, int part) {
    }

    static Shot lastShot(Zombie zombie) {
        Wounds w = WOUNDS.get(zombie);
        if (w == null || w.shotDir == null || zombie.level().getGameTime() - w.shotTick > 1) {
            return null;
        }
        return new Shot(w.shotDir, w.shotGun, w.shotPart);
    }

    /**
     * Only the head kills (combat.onlyHeadshotsKill): a bullet or bolt that struck a walker this tick without touching
     * its head can't take the last of its health, so one shot to pieces lives on, crawling or wriggling, until it's
     * shot in the head; one that did strike the head is finished, whatever it wears. Anything else hurts as before.
     */
    public static float onlyTheHead(LivingEntity target, DamageSource source, float amount) {
        if (!(target instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie) || zombie.level().isClientSide()
                || !source.is(ModDamageTypes.BULLET) || !ZcConfig.get().combat.onlyHeadshotsKill) {
            return amount;
        }
        Wounds w = WOUNDS.get(zombie);
        boolean head = headGone(zombie) || w != null && (w.struck >> HEAD & 1) != 0
                && w.shotTick == zombie.level().getGameTime();
        if (head) {
            return Math.max(amount, (zombie.getMaxHealth() + zombie.getAbsorptionAmount()) * 25f);
        }
        return Math.min(amount, Math.max(0f, zombie.getHealth() - 1f));
    }

    /** After the shots: the body jerks where they struck, and every limb past its toughness comes off. */
    private static void settle(Zombie zombie) {
        Wounds w = WOUNDS.get(zombie);
        if (w == null || zombie.isRemoved()) {
            return;
        }
        if (zombie.isAlive() && w.struck != 0 && w.shotDir != null) {
            Deaths.jerk(zombie, w.struck, w.shotDir, force(w.shotGun));
        }
        ZcConfig.Limbs config = ZcConfig.get().limbs;
        if (!config.enabled) {
            w.struck = 0;
            w.chestPellets = 0;
            return;
        }
        float[] took = w.dealt.clone();
        int severed = 0;
        for (int cut : ORDER) {
            int i = Integer.numberOfTrailingZeros(cut);
            if ((cuts(zombie) & (cut | above(cut))) != 0) {
                w.dealt[i] = 0f;
            } else if (w.dealt[i] >= toughness(cut, config)) {
                w.dealt[i] = 0f;
                severed |= cut;
                sever(zombie, cut, partOf(cut), w.dir, w.gun);
            }
        }
        // most of a blast in the chest from point-blank range: it goes right through
        boolean blown = config.chestHoles && w.chestPellets > 0 && w.shotGun != null
                && w.chestPellets > w.shotGun.pellets() * config.chestHoleShare && (limbs(zombie) & CHEST_HOLE) == 0;
        if (blown) {
            blowOpen(zombie, w);
        }
        w.chestPellets = 0;
        float head = w.head;
        boolean burst = config.headBursts && head > 0f && head >= config.headBurst && !headGone(zombie);
        if (burst) {
            burst(zombie, w);
        }
        w.head = 0f;
        if (config.showHits && w.shooter != null && w.struck != 0) {
            w.shooter.sendOverlayMessage(readout(w.struck, took, severed, head, burst, blown, config));
        }
        w.struck = 0;
    }

    /**
     * Blows its chest open: a hole right through it, in a spray of blood and bits out of its back (the client throws
     * the pieces and draws the hole), and it's knocked back a step. With only headshots killing, it keeps coming.
     */
    private static void blowOpen(Zombie zombie, Wounds w) {
        Vec3 dir = w.shotDir;
        int heading = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) / 360f * 256f), 256);
        zombie.setAttached(LIMBS, limbs(zombie) & (CUTS | MARKS) | CHEST_HOLE | heading << 8 | force(w.shotGun) << 16);
        ServerLevel level = (ServerLevel) zombie.level();
        Vec3 at = chest(zombie);
        BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.sendParticles(blood, at.x, at.y, at.z, 50, 0.16, 0.16, 0.16, 0.35);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                at.x, at.y, at.z, 10, 0.12, 0.12, 0.12, 0.3);
        for (int i = 0; i < 18; i++) {
            // most of it goes out of its back, with the shot
            Vec3 v = dir.scale(0.6 + zombie.getRandom().nextDouble() * 0.6).add(
                    zombie.getRandom().nextGaussian() * 0.18, zombie.getRandom().nextDouble() * 0.15,
                    zombie.getRandom().nextGaussian() * 0.18);
            level.sendParticles(blood, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
        }
        level.playSound(null, at.x, at.y, at.z, ModSounds.HEAD_BURST, SoundSource.HOSTILE, 1.5f,
                0.65f + zombie.getRandom().nextFloat() * 0.1f);
        BLEEDING.add(new Bleed(zombie, CHEST, level.getGameTime() + 100));
        if (!Downed.isCrawler(zombie)) {
            Downed.stagger(zombie, 14);
        }
    }

    /**
     * Bursts its head: gone, in a spray of blood and bits of skull along the shot (the client throws the pieces), and
     * the zombie drops dead where it stood.
     */
    private static void burst(Zombie zombie, Wounds w) {
        burst(zombie, w.headDir, force(w.headGun), w.shooter);
    }

    private static void burst(Zombie zombie, Vec3 dir, int force, ServerPlayer shooter) {
        int heading = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) / 360f * 256f), 256);
        zombie.setAttached(LIMBS, limbs(zombie) & (CUTS | MARKS) | HEAD_GONE | heading << 8 | force << 16);
        ServerLevel level = (ServerLevel) zombie.level();
        Vec3 at = joint(zombie, HEAD);
        BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.sendParticles(blood, at.x, at.y, at.z, 70, 0.14, 0.14, 0.14, 0.45);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                at.x, at.y, at.z, 16, 0.1, 0.1, 0.1, 0.35);
        for (int i = 0; i < 14; i++) {
            // the rest goes out the far side, with the shot
            Vec3 v = dir.scale(0.5 + zombie.getRandom().nextDouble() * 0.5).add(
                    zombie.getRandom().nextGaussian() * 0.15, 0.1 + zombie.getRandom().nextDouble() * 0.2,
                    zombie.getRandom().nextGaussian() * 0.15);
            level.sendParticles(blood, at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
        }
        level.playSound(null, at.x, at.y, at.z, ModSounds.HEAD_BURST, SoundSource.HOSTILE, 1.4f,
                0.9f + zombie.getRandom().nextFloat() * 0.2f);
        BLEEDING.add(new Bleed(zombie, HEAD, level.getGameTime() + 80));
        StuckBolts.shed(zombie, at);    // a bolt in the head goes with it
        if (zombie.isAlive()) {
            zombie.hurtServer(level, shooter != null ? ModDamageTypes.source(level, ModDamageTypes.BULLET, shooter)
                    : level.damageSources().generic(), 1e6f);
        }
    }

    /** The hits readout: the parts a shot struck, how far along each limb is to coming off, and what came off. */
    private static Component readout(int struck, float[] took, int severed, float head, boolean burst, boolean blown,
                                     ZcConfig.Limbs config) {
        Set<String> parts = new LinkedHashSet<>();
        if (burst) {
            parts.add("head BURST");
        }
        if (blown) {
            parts.add("chest BLOWN OPEN");
        }
        for (int cut : ORDER) {
            if ((severed & cut) != 0) {
                parts.add(cutName(cut) + " OFF");
            }
        }
        for (int k = 0; k < PARTS; k++) {
            int cut = CUT_OF[k];
            if ((struck >> k & 1) == 0 || cut != 0 && (severed & (cut | above(cut))) != 0) {
                continue;
            }
            if (k == HEAD && burst) {
                continue;
            }
            parts.add(k == HEAD && head > 0f && config.headBursts ? String.format("head %.0f/%.0f", head, config.headBurst)
                    : cut == 0 ? PART_NAMES[k] : String.format("%s %.0f/%.0f", PART_NAMES[k],
                    took[Integer.numberOfTrailingZeros(cut)], toughness(cut, config)));
        }
        return Component.literal("Hit: " + String.join(", ", parts)).withStyle(ChatFormatting.GRAY);
    }

    private static String cutName(int cut) {
        return switch (cut) {
            case EL_R -> "right forearm";
            case EL_L -> "left forearm";
            case SH_R -> "right arm";
            case SH_L -> "left arm";
            case KN_R -> "right shin";
            case KN_L -> "left shin";
            case HIP_R -> "right leg";
            default -> "left leg";
        };
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

    /** How hard each kind of gun bursts heads (0 never). */
    private static double bursting(GunType gun, ZcConfig.Limbs config) {
        if (gun.pellets() > 1) {
            return config.burstShotguns;
        }
        return switch (gun) {
            case HUNTING_RIFLE -> config.burstHuntingRifle;
            case LEVER_ACTION -> config.burstLeverAction;
            case MAGNUM -> config.burstMagnum;
            default -> config.burstOtherGuns;
        };
    }

    /** How hard a limb is flung (and a body pushed): 0 handgun, 1 rifle, 2 hunting rifle, 3 buckshot. */
    static int force(GunType gun) {
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
        sever(zombie, cut, part, dir, force(gun));
    }

    private static void sever(Zombie zombie, int cut, int part, Vec3 dir, int force) {
        int before = cuts(zombie), cuts = before | cut;
        int heading = Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) / 360f * 256f), 256);
        zombie.setAttached(LIMBS, cuts | limbs(zombie) & MARKS | heading << 8 | force << 16);
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
        crawlSpeed(zombie);
    }

    private static void removeHop(Zombie zombie) {
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(HOP_SPEED);
        }
    }

    /** Puts every limb (and the head) back (the test command's fresh zombie). */
    public static void restore(Zombie zombie) {
        zombie.setAttached(LIMBS, 0);
        WOUNDS.remove(zombie);
        removeHop(zombie);
    }

    /** Blows its chest open with no shot (the test command's holed zombie), as if by a blast from in front. */
    public static void blowOpen(Zombie zombie) {
        Wounds w = new Wounds();
        w.shotDir = zombie.getLookAngle().reverse();
        w.shotGun = GunType.SHOTGUN;
        blowOpen(zombie, w);
    }

    /** Takes a limb off with no shot (the test command's hopper), as if blown off from behind. */
    public static void cut(Zombie zombie, int cut) {
        sever(zombie, cut, partOf(cut), zombie.getLookAngle().reverse(), GunType.PISTOL);
    }

    /** A blast with nobody to credit: see {@link #blast(Zombie, Vec3, double, ServerPlayer)}. */
    public static void blast(Zombie zombie, Vec3 centre, double strength) {
        blast(zombie, centre, strength, null);
    }

    /**
     * A pipe bomb or grenade going off near a walker: it tears at every limb at once, adding to what shots have done
     * to them, and the limbs facing the blast and nearest it take the most (one stood over a grenade on the floor
     * loses its legs first; its arms may go too, and the side turned away mostly keeps them). Whatever passes its
     * toughness comes off and flies away from the blast as hard as buckshot throws it; legs lost, it hops or falls
     * as from a shot. Right on top of the blast the head bursts now and then.
     *
     * Only the limbs: the blast's own damage, push and knockdown are the thrower's. Call it once per blast for each
     * walker the blast can see, before or after its damage (a body still falling keeps what it loses).
     *
     * @param centre where it went off
     * @param strength how close: 1 right on top of it, 0 at the edge of its reach
     * @param thrower who threw it, for a kill by head burst and the hits readout; may be null
     */
    public static void blast(Zombie zombie, Vec3 centre, double strength, ServerPlayer thrower) {
        ZcConfig.Limbs config = ZcConfig.get().limbs;
        if (!config.enabled || !config.blasts || strength <= 0 || zombie.isRemoved() || !ZombieTypes.isWalker(zombie)
                || !(zombie.level() instanceof ServerLevel)) {
            return;
        }
        strength = Math.min(1, strength);
        Vec3 body = chest(zombie), toBlast = centre.subtract(body);
        double far = toBlast.length();
        Vec3 facing = far > 0.05 ? toBlast.scale(1 / far) : null;
        // limbs fly away from the blast; from right underneath, out behind it
        Vec3 away = new Vec3(-toBlast.x, 0, -toBlast.z);
        away = away.lengthSqr() > 0.01 ? away.normalize() : zombie.getLookAngle().reverse();
        Wounds w = WOUNDS.computeIfAbsent(zombie, z -> new Wounds());
        int gone = goneParts(zombie, cuts(zombie));
        for (int k = 4; k < PARTS; k++) {
            if ((gone >> k & 1) != 0) {
                continue;
            }
            w.dealt[Integer.numberOfTrailingZeros(CUT_OF[k])] += (float) (config.blastTearing * strength
                    * exposure(at(zombie, k, true), body, facing, far, centre)
                    * (0.75 + zombie.getRandom().nextDouble() * 0.5));
        }
        int severed = 0;
        for (int cut : ORDER) {
            int i = Integer.numberOfTrailingZeros(cut);
            if ((cuts(zombie) & (cut | above(cut))) != 0) {
                w.dealt[i] = 0f;
            } else if (w.dealt[i] >= toughness(cut, config)) {
                w.dealt[i] = 0f;
                severed |= cut;
                sever(zombie, cut, partOf(cut), away, 3);
            }
        }
        boolean burst = false;
        if (config.headBursts && !headGone(zombie) && (gone & 1 << HEAD) == 0) {
            double close = Math.clamp((strength - config.blastHeadBurstFrom) / Math.max(0.01, 1 - config.blastHeadBurstFrom), 0, 1);
            Vec3 head = joint(zombie, HEAD), up = head.subtract(body);
            double faced = facing == null || up.lengthSqr() < 1e-6 ? 1 : Math.max(0, up.normalize().dot(facing));
            burst = zombie.getRandom().nextDouble() < config.blastHeadBurst * close * (0.6 + 0.4 * faced);
            if (burst) {
                Vec3 out = head.subtract(centre);
                burst(zombie, out.lengthSqr() > 1e-4 ? out.normalize() : away, 3, thrower);
            }
        }
        if (config.showHits && thrower != null && (severed != 0 || burst)) {
            List<String> parts = new ArrayList<>();
            if (burst) {
                parts.add("head BURST");
            }
            for (int cut : ORDER) {
                if ((severed & cut) != 0) {
                    parts.add(cutName(cut) + " OFF");
                }
            }
            thrower.sendOverlayMessage(Component.literal(String.format("Blast %.0f%%: %s", strength * 100,
                    String.join(", ", parts))).withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * How much of a blast a limb at mid takes, 0.1 to 1: all of it from one going off inside the body; otherwise
     * more as it faces the blast from the chest, and more again as it's nearer the blast than the chest is.
     */
    private static double exposure(Vec3 mid, Vec3 body, Vec3 facing, double far, Vec3 centre) {
        if (facing == null) {
            return 1;
        }
        Vec3 out = mid.subtract(body);
        double face = out.lengthSqr() > 1e-6 ? out.normalize().dot(facing) : 0;
        double near = Math.clamp((far - mid.distanceTo(centre)) / 0.5, -1, 1);
        return Math.clamp(0.45 + 0.35 * face + 0.2 * near, 0.1, 1);
    }

    private static int partOf(int cut) {
        for (int i = 0; i < PARTS; i++) {
            if (CUT_OF[i] == cut) {
                return i;
            }
        }
        return HEAD;
    }

    /**
     * Where a shot went into the body: the part (head 0, torso 1 and 2, pelvis 3, then the arms and legs as in the
     * tables), its capsule's ends and radius in the pose it was in, the point the shot went in at and the shot's
     * heading, all in the zombie's own frame (x to its left, y up from its feet, z ahead) at scale 1; and how far
     * from the eye it went in, in blocks.
     */
    public record Strike(int part, Vec3 a, Vec3 b, double radius, Vec3 at, Vec3 dir, double distance) {
    }

    /** The part a shot went into first and where (see {@link Strike}), or null if its line misses them all. */
    public static Strike strike(Zombie zombie, Vec3 eye, Vec3 dir) {
        return strike(zombie, BodyTrack.now(zombie), eye, dir);
    }

    /**
     * The same, against the zombie as it was at one moment (where it stood, its facing and its pose, and the pose it
     * was still blending out of, if any: a shot into either counts).
     */
    static Strike strike(Zombie zombie, BodyTrack.Frame f, Vec3 eye, Vec3 dir) {
        int cuts = cuts(zombie);
        int gone = goneParts(zombie, cuts);
        // the shot's line in the zombie's own frame: x to its left, y up from its feet, z ahead
        double yaw = Math.toRadians(f.yaw()), scale = Math.max(0.1, zombie.getScale());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
        double ox = eye.x - f.x(), oy = eye.y - f.y(), oz = eye.z - f.z();
        double px = (ox * lx + oz * lz) / scale, py = oy / scale, pz = (ox * fx + oz * fz) / scale;
        double dx = dir.x * lx + dir.z * lz, dy = dir.y, dz = dir.x * fx + dir.z * fz;
        Strike best = null;
        for (int pose : new int[] {f.pose(), f.blendingFrom()}) {
            if (pose < 0) {
                continue;
            }
            float[] caps = BodyCaps.TABLES[pose];
            // a zombie on its right leg is the one-leg table (drawn on the left) mirrored: look it up mirrored,
            // answer unmirrored
            boolean mirror = BodyCaps.oneLeg(pose) && (cuts & LEFT_LEG) != 0;
            double m = mirror ? -1 : 1;
            for (int k = 0; k < caps.length; k += 8) {
                int part = (int) caps[k], real = mirror ? MIRROR[part] : part;
                if ((gone >> real & 1) != 0) {
                    continue;
                }
                double t = enter(px * m, py, pz, dx * m, dy, dz, caps, k + 1);
                if (t >= 0 && (best == null || t * scale < best.distance())) {
                    best = new Strike(real, new Vec3(caps[k + 1] * m, caps[k + 2], caps[k + 3]),
                            new Vec3(caps[k + 4] * m, caps[k + 5], caps[k + 6]), caps[k + 7],
                            new Vec3(px + t * dx, py + t * dy, pz + t * dz), new Vec3(dx, dy, dz), t * scale);
                }
            }
        }
        return best;
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
        if (headGone(zombie)) {
            gone |= 1 << HEAD;
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

    /**
     * Where a part meets the body (its capsule's upper end) in the world, near enough; for the head, the middle of
     * it.
     */
    private static Vec3 joint(Zombie zombie, int part) {
        return at(zombie, part, part == HEAD);
    }

    /** The middle of its chest (both halves of the torso) in the world, near enough. */
    private static Vec3 chest(Zombie zombie) {
        return at(zombie, CHEST, true).add(at(zombie, 2, true)).scale(0.5);
    }

    /** Where a part meets the body in the world, or (middle) the middle of it. */
    private static Vec3 at(Zombie zombie, int part, boolean middle) {
        BodyTrack.Frame f = BodyTrack.now(zombie);
        float[] caps = BodyCaps.TABLES[f.pose()];
        boolean mirror = BodyCaps.oneLeg(f.pose()) && (cuts(zombie) & LEFT_LEG) != 0;
        int want = mirror ? MIRROR[part] : part;
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (int k = 0; k < caps.length; k += 8) {
            if ((int) caps[k] != want) {
                continue;
            }
            if (middle) {
                x += (caps[k + 1] + caps[k + 4]) / 2;
                y += (caps[k + 2] + caps[k + 5]) / 2;
                z += (caps[k + 3] + caps[k + 6]) / 2;
                n++;
            } else {
                x = caps[k + 1];
                y = caps[k + 2];
                z = caps[k + 3];
                n = 1;
                break;
            }
        }
        if (n == 0) {
            return zombie.position();
        }
        x = x / n * (mirror ? -1 : 1);
        y /= n;
        z /= n;
        double yaw = Math.toRadians(f.yaw()), scale = zombie.getScale();
        double fx = -Math.sin(yaw), fz = Math.cos(yaw), lx = Math.cos(yaw), lz = Math.sin(yaw);
        return new Vec3(f.x() + (x * lx + z * fx) * scale, f.y() + y * scale, f.z() + (x * lz + z * fz) * scale);
    }

    private static void tick(MinecraftServer server) {
        if (!SHOT.isEmpty()) {
            for (Zombie zombie : SHOT) {
                settle(zombie);
            }
            SHOT.clear();
        }
        BodyTrack.tick(server);
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
                Vec3 at = b.part() == CHEST ? chest(zombie).add(0, -0.1, 0)
                        : joint(zombie, b.part()).add(0, b.part() == HEAD ? -0.15 : 0, 0);
                level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                        at.x, at.y - 0.05, at.z, 1, 0.03, 0.02, 0.03, 0.0);
            }
        }
    }

    private Dismember() {
    }
}
