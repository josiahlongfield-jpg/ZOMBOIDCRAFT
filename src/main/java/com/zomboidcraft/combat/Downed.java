package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Zombies on the ground. A hit or a shove can knock one flat for a few seconds; a heavy blow or a
 * shot to the legs can leave it crawling for good, and a few are found that way. Both lie low
 * (the swimming pose, with a half-height hitbox), so you look down to finish them, and a blow
 * to a zombie on the ground lands much harder.
 *
 * Lighter hits stagger: the zombie freezes for a moment, which is what makes a shove buy time.
 */
public final class Downed {
    /** Hitbox of a zombie lying or crawling. Under a block wide, so crawlers still fit through doorways. */
    public static final EntityDimensions LOW = EntityDimensions.scalable(0.8f, 0.5f);

    /** Whether this zombie crawls for good. Saved with it, and sent to clients so they draw it face down. */
    public static final AttachmentType<Boolean> CRAWLER = AttachmentRegistry.create(
            ZomboidCraft.id("crawler"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

    /** Whether this zombie has lost both legs. It always crawls; saved, and sent to clients so they draw the stumps. */
    public static final AttachmentType<Boolean> LEGLESS = AttachmentRegistry.create(
            ZomboidCraft.id("legless"),
            builder -> builder
                    .persistent(Codec.BOOL)
                    .syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

    private static final Identifier CRAWL_SPEED = ZomboidCraft.id("crawl");

    /** Ticks left on the ground and ticks left staggered, per zombie. Server thread only. */
    private static final class Timer {
        int down;
        int stun;
    }

    private static final Map<Mob, Timer> TIMERS = new WeakHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Downed::tick);
    }

    /** Lying or crawling: low to the ground. */
    public static boolean isLow(Entity entity) {
        return ZombieTypes.isWalker(entity) && entity.hasPose(Pose.SWIMMING);
    }

    public static boolean isCrawler(Entity entity) {
        return entity.getAttachedOrElse(CRAWLER, false);
    }

    public static boolean isLegless(Entity entity) {
        return entity.getAttachedOrElse(LEGLESS, false);
    }

    /** Frozen in place: knocked flat or staggered. Checked by the AI mixin, on the server only. */
    public static boolean isHeld(Mob mob) {
        if (mob.level().isClientSide()) {
            return false;
        }
        Timer t = TIMERS.get(mob);
        return t != null && (t.down > 0 || t.stun > 0);
    }

    /** Knocks a zombie flat for the configured time. A crawler is already down, so it is only staggered. */
    public static void knockDown(LivingEntity target) {
        ZcConfig.Combat config = ZcConfig.get().combat;
        if (!config.knockdowns || !(target instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie) || !zombie.isAlive()) {
            return;
        }
        if (isCrawler(zombie)) {
            stagger(zombie, 20);
            return;
        }
        Timer t = TIMERS.computeIfAbsent(zombie, z -> new Timer());
        boolean wasDown = t.down > 0;
        int ticks = (int) Math.round(config.downedSeconds * 20 * (0.85 + zombie.getRandom().nextFloat() * 0.3));
        t.down = Math.max(t.down, ticks);
        zombie.getNavigation().stop();
        zombie.setPose(Pose.SWIMMING);
        if (!wasDown) {
            zombie.level().playSound(null, zombie.getX(), zombie.getY(), zombie.getZ(), SoundEvents.GENERIC_SMALL_FALL,
                    SoundSource.HOSTILE, 1f, 0.55f + zombie.getRandom().nextFloat() * 0.1f);
        }
    }

    /** Each hit on a zombie already down keeps it down a little longer. */
    public static void keepDown(LivingEntity target, int ticks) {
        Timer t = target instanceof Mob mob ? TIMERS.get(mob) : null;
        if (t != null && t.down > 0) {
            t.down = Math.min(t.down + ticks, (int) (ZcConfig.get().combat.downedSeconds * 20 * 1.5));
        }
    }

    /** Freezes a zombie for a moment: it reels from a hit or a shove. */
    public static void stagger(LivingEntity target, int ticks) {
        if (target instanceof Zombie zombie && ZombieTypes.isWalker(zombie) && zombie.isAlive()) {
            Timer t = TIMERS.computeIfAbsent(zombie, z -> new Timer());
            t.stun = Math.max(t.stun, ticks);
            zombie.getNavigation().stop();
        }
    }

    /**
     * Rolls whether a blow cripples a zombie so it crawls from now on.
     *
     * @param scale multiplies the configured chance (heavier blows cripple more often)
     */
    public static boolean crippleChance(LivingEntity target, double scale) {
        ZcConfig.Combat config = ZcConfig.get().combat;
        if (!config.crawlers || !(target instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie)
                || !zombie.isAlive() || isCrawler(zombie)
                || zombie.getRandom().nextFloat() >= config.crippleChance * scale) {
            return false;
        }
        makeCrawler(zombie);
        return true;
    }

    public static void makeCrawler(Zombie zombie) {
        zombie.setAttached(CRAWLER, true);
        applyCrawl(zombie);
        Timer t = TIMERS.get(zombie);
        if (t != null) {
            t.down = 0;
        }
    }

    /** Takes both legs: it crawls from now on, on its arms. */
    public static void makeLegless(Zombie zombie) {
        zombie.setAttached(LEGLESS, true);
        makeCrawler(zombie);
    }

    /** Back on two legs for good (the test command's plain zombie). */
    public static void makeWalker(Zombie zombie) {
        zombie.setAttached(LEGLESS, false);
        zombie.setAttached(CRAWLER, false);
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CRAWL_SPEED);
        }
        zombie.setPose(Pose.STANDING);
    }

    /** On load: some zombies are found crawling, a few with no legs; crawlers lie down again (the pose isn't saved). */
    public static void onLoad(Zombie zombie) {
        ZcConfig.Combat config = ZcConfig.get().combat;
        if (!zombie.hasAttached(LEGLESS)) {
            zombie.setAttached(LEGLESS, config.crawlers && zombie.getRandom().nextFloat() < config.leglessSpawnChance);
            if (isLegless(zombie)) {
                zombie.setAttached(CRAWLER, true);
            }
        }
        if (!zombie.hasAttached(CRAWLER)) {
            zombie.setAttached(CRAWLER, config.crawlers && zombie.getRandom().nextFloat() < config.crawlerSpawnChance);
        }
        if (isCrawler(zombie) && config.crawlers) {
            applyCrawl(zombie);
        } else if (zombie.hasPose(Pose.SWIMMING)) {
            zombie.setPose(Pose.STANDING);
        }
    }

    private static void applyCrawl(Zombie zombie) {
        zombie.setPose(Pose.SWIMMING);
        AttributeInstance speed = zombie.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrReplacePermanentModifier(new AttributeModifier(CRAWL_SPEED, -0.55,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Map.Entry<Mob, Timer>> it = TIMERS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, Timer> entry = it.next();
            Mob mob = entry.getKey();
            Timer t = entry.getValue();
            if (mob == null || mob.isRemoved() || !mob.isAlive()) {
                it.remove();
                continue;
            }
            if (t.stun > 0) {
                t.stun--;
            }
            if (t.down > 0 && --t.down == 0) {
                standUp(mob, t);
            }
            if (t.down <= 0 && t.stun <= 0) {
                it.remove();
            }
        }
    }

    /** Gets back up, unless there's no headroom (then it waits) or it crawls now. */
    private static void standUp(Mob mob, Timer t) {
        if (isCrawler(mob) || !mob.hasPose(Pose.SWIMMING)) {
            return;
        }
        if (!mob.level().noCollision(mob, mob.getType().getDimensions().makeBoundingBox(mob.position()).deflate(0.05))) {
            t.down = 20;
            return;
        }
        mob.setPose(Pose.STANDING);
        t.stun = Math.max(t.stun, 6);
    }

    private Downed() {
    }
}
