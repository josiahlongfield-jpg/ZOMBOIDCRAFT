package com.zomboidcraft.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.rolls.Rolls;
import com.zomboidcraft.combat.rolls.Stat;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.ModDamageTypes;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server side of the guns: hitscan shots with spread, headshots, the bang that brings every
 * zombie for blocks around, jams on worn guns, and the reload sequence (magazine out, magazine
 * in, charging handle or slide) that also drives the reload item models. Each gun's numbers and
 * timings come from its {@link GunType}.
 *
 * A gun's custom model data carries two numbers for its item model: [0] the reload step
 * (1 magazine out, 2 magazine in, 3 charging handle; 1.5 is magazine out on an empty gun, so first
 * person knows the handle will be worked) and [1] the muzzle flash, plus the attachment keys
 * {@link Attachments} writes. First person animates between the steps (RifleView), on these timings.
 *
 * A tube gun (the pump shotgun) loads instead: step 1 while shells go in one at a time (1.5 onto an empty
 * chamber), until it's full, the shells run out or the trigger is pulled; then step 3 while the pump is worked
 * to chamber one, if it was empty. Each shot is a spread of pellets, and the pump is worked after it.
 * A bolt gun (the hunting rifle) reloads like a magazine gun, its bolt worked at step 3, and after each shot.
 * A gun that opens to load (the double-barrel) loads like a tube gun with its action open (step 1, its spent shells
 * coming out as it opens), then shuts at step 3 however it stopped; the trigger only stops the loading.
 */
public final class Guns {
    private static final int FLASH_TICKS = 2;

    private static final class State {
        long lastShot = -100;
        /** On full auto: when the next shot is due (game time, a fraction for rates between whole ticks). */
        double autoNext = -100;
        /** How far the spread has bloomed open (degrees), as of game time bloomAt. */
        double bloom;
        long bloomAt;
        int reloadTick = -1;
        /** The gun being reloaded: putting it away (or swapping to another kind) stops the reload. */
        GunType reloading;
        boolean needsCharge;
        int flashLeft;
        /** A tube gun's loading: stopped (full, out of shells or the trigger pulled) at stopTick, on this stack. */
        boolean loadStopped;
        int stopTick;
        ItemStack loading = ItemStack.EMPTY;
        /** When the pump or bolt is worked after a shot (game time), or -1; when its empty case lands. */
        long cycleAt = -1, caseAt = -1;
    }

    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Guns::tick);
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player, p -> new State());
    }

    // ------------------------------------------------------------------ firing

    public static void fire(ServerPlayer player, boolean aiming) {
        ZcConfig.Guns config = ZcConfig.get().guns;
        ItemStack gun = player.getMainHandItem();
        GunType type = GunType.of(gun);
        if (!config.enabled || type == null || player.isSpectator() || !com.zomboidcraft.world.cars.CarProp.mayUse(player, gun)) {
            return;
        }
        ZcConfig.GunStats stats = type.stats();
        State st = state(player);
        long now = player.level().getGameTime();
        if (launcherOn(gun)) {
            fireLauncher(player, gun, st, now, aiming);
            return;
        }
        boolean auto = fullAuto(gun);
        // full auto is timed on the shooter's clock, between ticks: take a little slack for shots that land in the same
        // tick; the rate still holds on average
        if (auto ? now < st.autoNext - 2.0 : now - st.lastShot < type.fireDelay()) {
            return;
        }
        if (st.reloadTick >= 0) {
            if (!type.tube || st.reloading != type || st.loadStopped) {
                return;
            }
            // the trigger stops the loading: with a shell chambered it goes off at once, onto an empty chamber
            // the pump is worked first; a gun open to load is shut first
            if (st.needsCharge || type.opens) {
                stopLoading(st);
                return;
            }
            endReload(gun, st);
        }
        st.lastShot = now;
        GunRaise.shot(player);
        if (auto) {
            st.autoNext = nextAuto(st.autoNext, now, stats);
        }
        ServerLevel level = (ServerLevel) player.level();
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        if (g.jammed() || !g.mag() || g.rounds() <= 0) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), type.dry(), SoundSource.PLAYERS, 0.8f, 1f);
            if (g.jammed()) {
                player.sendOverlayMessage(Component.literal("Jammed! Press R to clear it.").withStyle(ChatFormatting.RED));
            }
            return;
        }
        // a worn gun can jam instead of firing
        float wear = gun.isDamageableItem() ? (float) gun.getDamageValue() / gun.getMaxDamage() : 0;
        double jam = config.jamNew + config.jamWorn * wear * wear;
        if (config.jams && type.canJam() && player.getRandom().nextDouble() < jam * Rolls.multiplier(gun, Stat.RELIABILITY)) {
            gun.set(GunState.TYPE, g.withJam(true));
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), type.dry(), SoundSource.PLAYERS, 0.8f, 0.8f);
            player.sendOverlayMessage(Component.literal("Jammed! Press R to clear it.").withStyle(ChatFormatting.RED));
            return;
        }
        gun.set(GunState.TYPE, g.withRounds(g.rounds() - 1));
        gun.hurtAndBreak(1, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        st.flashLeft = FLASH_TICKS;
        setModelData(gun, 0, config.muzzleFlash ? 1 : 0);

        // the bang: the shooter's client plays the close one at once; everyone else hears it by how far off they are.
        // A suppressor takes the crack down to a cough, and shortens how far the zombies hear it.
        boolean suppressed = Attachments.suppressed(gun);
        bang(level, player, type, suppressed, suppressed ? (float) config.suppressedVolume : 1f,
                (suppressed ? (float) stats.suppressedPitch : 1f) * (0.95f + player.getRandom().nextFloat() * 0.1f));
        if (type.manual()) {
            st.cycleAt = now + type.cycleAfterShot;     // the empty hull or case comes out when it's worked
        } else if (!type.opens && type != GunType.CROSSBOW) {   // a double keeps its empties till it's opened
            // the shooter's own client plays its case in time with the shot; the server's tick would bunch them
            level.playSound(player, player.getX(), player.getY(), player.getZ(), casing(type), SoundSource.PLAYERS,
                    0.4f, 0.9f + player.getRandom().nextFloat() * 0.25f);
        }
        Noise.emit(level, player.position(), stats.shotNoise * (suppressed ? config.suppressedNoise : 1.0));

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        // the spread the crosshair's ring shows: this shot lands inside it, then it blooms open a little more
        st.bloom = bloomNow(st.bloom, (now - st.bloomAt) / 20.0, stats);
        double spread = spread(player, gun, type, aiming, st.bloom);
        st.bloom = bloomed(st.bloom, auto, stats);
        st.bloomAt = now;
        Vec3 aim = jitter(look, spread, player);
        Vec3 muzzle = eye.add(look.scale(0.9)).add(look.cross(new Vec3(0, 1, 0)).normalize().scale(aiming ? 0 : 0.25))
                .add(0, aiming ? -0.08 : -0.22, 0);
        if (type == GunType.GRENADE_LAUNCHER) {
            // a 40mm round lobbed on its own arc, bursting where it lands (combat/throwing/Thrown)
            com.zomboidcraft.combat.throwing.Thrown.fire(level, player, new ItemStack(ModItems.GRENADE_ROUND), muzzle, aim);
            return;
        }
        if (config.muzzleSmoke && type != GunType.CROSSBOW) {
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
            if (!suppressed) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // each pellet (one bullet, or a shotgun's buckshot) flies on its own; the hits on each target add up into
        // one blow, since a target only takes the first of several hits in the same moment
        int pellets = type.pellets();
        double pelletSpread = pelletSpread(type);
        float power = (float) Rolls.multiplier(gun, Stat.DAMAGE);
        Map<LivingEntity, Hit> hits = new java.util.LinkedHashMap<>();
        boolean ricochet = false;
        boolean nearby = false;     // the crack of a round landing close, for the others round where it struck
        boolean bow = type == GunType.CROSSBOW;
        Vec3 boltAt = null;     // where a crossbow's bolt ends up: under what it hit, or stuck where it struck
        Zombie boltIn = null;   // or the zombie it went into, to stay standing in it
        Vec3 boltDir = null;
        for (int i = 0; i < pellets; i++) {
            Vec3 dir = pellets > 1 ? jitter(aim, pelletSpread, player) : aim;
            Vec3 end = eye.add(dir.scale(stats.range));
            // a bullet goes on through glass (see GlassShots): look on past each pane it meets, up to a few
            // and in at a parked car's window and out the far side of it (see CarGlass)
            java.util.List<BlockHitResult> panes = new java.util.ArrayList<>();
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 from = eye;
            for (int pass = 0; !bow && block.getType() != HitResult.Type.MISS && pass < 8; pass++) {
                BlockState bs = level.getBlockState(block.getBlockPos());
                Vec3 on;
                if (panes.size() < 6 && GlassShots.isGlass(bs)) {
                    panes.add(block);
                    on = GlassShots.past(block.getBlockPos(), block.getLocation(), dir);
                } else if (!bs.is(com.zomboidcraft.world.cars.Cars.HULL)
                        || (on = com.zomboidcraft.world.cars.CarGlass.throughHull(level, block, from, dir)) == null) {
                    break;
                }
                from = on;
                block = level.clip(new ClipContext(on, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            }
            Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            com.zomboidcraft.world.trains.Trains.shot(level, player, eye, stop);    // OTTO minds being shot at
            // a zombie is hit where its body is drawn, not by its box (see Dismember.shot)
            Dismember.BodyHit hit = Dismember.shot(player, eye, stop,
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && e != player);
            // the panes it went through on the way (only those before whoever it hit), each taking some out of it
            double reached = hit != null ? hit.getLocation().distanceTo(eye) : Double.MAX_VALUE;
            float spent = 1f;
            for (BlockHitResult pane : panes) {
                if (pane.getLocation().distanceTo(eye) < reached) {
                    GlassShots.shot(level, player, pane, dir, level.getBlockState(pane.getBlockPos()), !ricochet);
                    ricochet = true;
                    spent *= GlassShots.THROUGH;
                }
            }
            // and the car windows it went through, each holed where it went in
            if (!bow) {
                for (var c : com.zomboidcraft.world.cars.CarGlass.along(level, eye, hit != null ? hit.getLocation() : stop)) {
                    c.car().shootWindow(level, c.window(), c.u(), c.v(), c.at(), !ricochet);
                    ricochet = true;
                    spent *= GlassShots.THROUGH;
                }
            }
            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                Pellet struck = pellet(target, hit, eye, stats, power * spent);
                hits.computeIfAbsent(target, t -> new Hit(dir)).add(struck);
                Dismember.pellet(player, target, hit, dir, struck.tears(), struck.head(), struck.legs(), type);
                if (!nearby && !bow) {
                    nearby = true;
                    nearbyImpact(level, player, hit.getLocation());
                }
                if (bow) {
                    boltAt = target.position().add(0, 0.2, 0);
                    boltIn = target instanceof Zombie hitZombie ? hitZombie : null;
                    boltDir = dir;
                    level.playSound(null, target.getX(), target.getEyeY(), target.getZ(), ModSounds.CROSSBOW_HIT,
                            SoundSource.PLAYERS, 1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
                }
            } else if (bow && block.getType() != HitResult.Type.MISS) {
                // a bolt thuds in and stays, a little short of the face it struck
                BlockState bs = level.getBlockState(block.getBlockPos());
                Vec3 p = block.getLocation();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), p.x, p.y, p.z, 4, 0.03, 0.03, 0.03, 0.08);
                level.playSound(null, p.x, p.y, p.z, ModSounds.CROSSBOW_HIT_BLOCK, SoundSource.PLAYERS, 0.8f,
                        0.9f + player.getRandom().nextFloat() * 0.2f);
                boltAt = p.subtract(dir.scale(0.3));
                if (bs.is(Blocks.GLASS) || bs.is(Blocks.GLASS_PANE) || bs.getBlock().getDescriptionId().contains("glass")) {
                    level.destroyBlock(block.getBlockPos(), false, player);
                }
            } else if (block.getType() != HitResult.Type.MISS) {
                BlockState bs = level.getBlockState(block.getBlockPos());
                Vec3 p = block.getLocation();
                // a hole, a puff of the block's dust and chips off it (drawn by each client)
                Impacts.block(level, block, dir, bs, pellets > 1 ? 0.6f : (float) Math.min(1.6, 0.6 + stats.damage / 20));
                // the strike sounds of what it hit (once a blast, for buckshot); a glancing hit on something hard
                // sometimes whines off it
                if (!ricochet) {
                    ricochet = true;
                    Surface surface = Surface.of(bs);
                    if (!nearby) {
                        nearby = true;
                        nearbyImpact(level, player, p);
                    }
                    level.playSound(null, p.x, p.y, p.z, surface.impact(), SoundSource.PLAYERS, pellets > 1 ? 1f : 0.85f,
                            0.92f + player.getRandom().nextFloat() * 0.16f);
                    var face = block.getDirection();
                    double glance = Math.abs(dir.x * face.getStepX() + dir.y * face.getStepY() + dir.z * face.getStepZ());
                    if (surface.hard() && glance < 0.35 && player.getRandom().nextFloat() < config.ricochetChance) {
                        level.playSound(null, p.x, p.y, p.z, ModSounds.BULLET_RICOCHET, SoundSource.PLAYERS, 0.5f,
                                0.9f + player.getRandom().nextFloat() * 0.3f);
                    }
                }
            }
        }
        // the bolt stays in the zombie it hit (before the hit lands, so one it kills drops it with the body), or lies
        // where it fell
        if (boltAt != null && stats instanceof ZcConfig.CrossbowStats cb && player.getRandom().nextDouble() >= cb.boltBreakChance
                && (boltIn == null || !StuckBolts.stick(boltIn, eye, boltDir))) {
            ItemEntity bolt = new ItemEntity(level, boltAt.x, boltAt.y, boltAt.z, new ItemStack(ModItems.CROSSBOW_BOLT));
            bolt.setDeltaMovement(Vec3.ZERO);
            bolt.setPickUpDelay(10);
            level.addFreshEntity(bolt);
        }
        hits.forEach((target, hit) -> hitEntity(level, player, target, hit, pellets, stats));
        if (!hits.isEmpty()) {
            Skills.addXp(player, Skill.AIMING, 2);
        }
    }

    /**
     * One round from a mounted gun (the Warthog's minigun, see world/cars/Turret): from the muzzle toward dir, through
     * glass and past the hull of the vehicle it's mounted on, striking like a rifle's bullet. Returns where it ended.
     */
    public static Vec3 mountedRound(ServerPlayer shooter, Vec3 muzzle, Vec3 dir, ZcConfig.GunStats stats, Entity mount,
                                    boolean sounds) {
        ServerLevel level = (ServerLevel) shooter.level();
        Vec3 end = muzzle.add(dir.scale(stats.range));
        java.util.List<BlockHitResult> panes = new java.util.ArrayList<>();
        net.minecraft.world.phys.AABB own = mount.getBoundingBox().inflate(1.5);
        BlockHitResult block = level.clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 from = muzzle;
        for (int pass = 0; block.getType() != HitResult.Type.MISS && pass < 8; pass++) {
            BlockState bs = level.getBlockState(block.getBlockPos());
            Vec3 on;
            if (panes.size() < 6 && GlassShots.isGlass(bs)) {
                panes.add(block);
                on = GlassShots.past(block.getBlockPos(), block.getLocation(), dir);
            } else if (bs.is(com.zomboidcraft.world.cars.Cars.HULL) && own.contains(block.getLocation())) {
                on = block.getLocation().add(dir.scale(0.05));     // its own vehicle's hull, under the gun
            } else if (!bs.is(com.zomboidcraft.world.cars.Cars.HULL)
                    || (on = com.zomboidcraft.world.cars.CarGlass.throughHull(level, block, from, dir)) == null) {
                break;
            }
            from = on;
            block = level.clip(new ClipContext(on, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        }
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        Dismember.BodyHit hit = Dismember.shot(shooter, muzzle, stop, e -> e instanceof LivingEntity && e.isAlive()
                && !e.isSpectator() && e.isPickable() && e != shooter && e != mount && !mount.hasPassenger(e));
        double reached = hit != null ? hit.getLocation().distanceTo(muzzle) : Double.MAX_VALUE;
        float spent = 1f;
        boolean struck = !sounds;
        for (BlockHitResult pane : panes) {
            if (pane.getLocation().distanceTo(muzzle) < reached) {
                GlassShots.shot(level, shooter, pane, dir, level.getBlockState(pane.getBlockPos()), !struck);
                struck = true;
                spent *= GlassShots.THROUGH;
            }
        }
        for (var c : com.zomboidcraft.world.cars.CarGlass.along(level, muzzle, hit != null ? hit.getLocation() : stop)) {
            if (c.car() != mount) {
                c.car().shootWindow(level, c.window(), c.u(), c.v(), c.at(), !struck);
                struck = true;
                spent *= GlassShots.THROUGH;
            }
        }
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            Pellet p = pellet(target, hit, muzzle, stats, spent);
            Dismember.pellet(shooter, target, hit, dir, p.tears(), p.head(), p.legs(), GunType.ASSAULT_RIFLE);
            hitEntity(level, shooter, target, new Hit(dir).add(p), 1, stats);
            return hit.getLocation();
        }
        if (block.getType() != HitResult.Type.MISS) {
            BlockState bs = level.getBlockState(block.getBlockPos());
            Impacts.block(level, block, dir, bs, (float) Math.min(1.6, 0.6 + stats.damage / 20));
            if (!struck) {
                Vec3 p = block.getLocation();
                level.playSound(null, p.x, p.y, p.z, Surface.of(bs).impact(), SoundSource.PLAYERS, 0.7f,
                        0.92f + shooter.getRandom().nextFloat() * 0.16f);
            }
        }
        return stop;
    }

    /** What the pellets that struck one target add up to. */
    private static final class Hit {
        final Vec3 dir;
        float damage;
        int count;
        boolean head, legs;
        Vec3 at;

        Hit(Vec3 dir) {
            this.dir = dir;
        }

        Hit add(Pellet p) {
            damage += p.damage();
            count++;
            head |= p.head();
            legs |= p.legs();
            at = at == null || p.head() ? p.at() : at;
            return this;
        }
    }

    /** damage is what it does to the target's health; tears what it does to the limb it struck (see Dismember). */
    private record Pellet(float damage, float tears, boolean head, boolean legs, Vec3 at) {
    }

    /**
     * One bullet or pellet striking: where, whether in the head or legs, and how hard (weaker far off for buckshot;
     * power is the gun's rolled damage).
     */
    private static Pellet pellet(LivingEntity target, Dismember.BodyHit hit, Vec3 eye, ZcConfig.GunStats stats, float power) {
        Vec3 at = hit.getLocation();
        boolean downed = Downed.isLow(target);
        // a zombie's head and legs are where its body is drawn, standing or on the ground; anything else's go by height
        // on its box, and not at all once it's down
        boolean head = hit.part >= 0 ? hit.head() : !downed && at.y >= target.getEyeY() - 0.28;
        boolean legs = hit.part >= 0 ? hit.leg() : !downed && at.y < target.getY() + target.getBbHeight() * 0.35;
        float damage = (float) (stats.damage * power * (head ? stats.headshotMultiplier : legs ? 0.7 : 1));
        if (stats instanceof ZcConfig.ShotgunStats sg) {
            double d = eye.distanceTo(at), over = (d - sg.falloffStart) / Math.max(1, stats.range - sg.falloffStart);
            damage *= (float) (1 - 0.75 * Mth.clamp(over, 0, 1));
        }
        // body hits on a zombie slow it, it takes the head to kill one; a limb still tears at the bullet's full force
        float body = !head && !downed && ZombieTypes.isWalker(target) ? (float) ZcConfig.get().combat.zombieBodyShots : 1f;
        return new Pellet(damage * body, damage, head, legs, at);
    }

    private static void hitEntity(ServerLevel level, ServerPlayer player, LivingEntity target, Hit hit, int pellets,
                                  ZcConfig.GunStats stats) {
        Vec3 at = hit.at;
        target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BULLET, player), hit.damage);
        if (target instanceof com.zomboidcraft.creature.HarvestmanEntity) {
            return;     // it rings off the plates or bleeds red from the core itself (see HarvestmanEntity.hurtServer)
        }
        // a shot doesn't throw a zombie back: it flinches and its advance is checked for a split second, longer for a
        // close load of buckshot; only a blunt blow knocks one back
        Downed.flinch(target, stats instanceof ZcConfig.ShotgunStats sg
                ? 5 + (int) Math.round(10 * sg.knockback * Math.min(1, 1.5 * hit.count / pellets)) : 5);
        // blood out along the shot, and splashed on the wall and floor behind (drawn by each client)
        float size = (float) Math.min(2.5, (0.6 + stats.damage / 20) * (1 + 0.15 * (hit.count - 1)) * (hit.head ? 1.3 : 1));
        Impacts.flesh(level, target, at, hit.dir, size);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BULLET_FLESH, SoundSource.PLAYERS, 1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        // the shooter alone hears their round whizz in as it sinks into a zombie, within a street or so
        double whizz = ZcConfig.get().guns.hitWhizzRange;
        if (ZombieTypes.isWalker(target) && player.getEyePosition().distanceToSqr(at) < whizz * whizz) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ModSounds.BULLET_WHIZZ), SoundSource.PLAYERS,
                    at.x, at.y, at.z, 0.8f, 0.9f + player.getRandom().nextFloat() * 0.2f, player.getRandom().nextLong()));
        }
    }

    /** Within this many blocks of a shot you hear the close bang; past FAR_BANG the far-off take; between, a crossfade. */
    private static final double CLOSE_BANG = 16, FAR_BANG = 48;

    /** A shot as each other player hears it: the close bang near the shooter, fading into the far-off take with distance. */
    private static void bang(ServerLevel level, ServerPlayer shooter, GunType type, boolean suppressed, float volume, float pitch) {
        SoundEvent close = type.shot(suppressed, false), far = type.shot(suppressed, true);
        double range = Math.max(close.getRange(volume), far.getRange(volume));
        long seed = shooter.getRandom().nextLong();
        double x = shooter.getX(), y = shooter.getEyeY(), z = shooter.getZ();
        for (ServerPlayer p : level.players()) {
            double d = Math.sqrt(p.distanceToSqr(x, y, z));
            if (p == shooter || d > range) {
                continue;
            }
            // equal-power, so the shot doesn't dip in loudness halfway between
            double t = close == far ? 0 : Mth.clamp((d - CLOSE_BANG) / (FAR_BANG - CLOSE_BANG), 0, 1) * Math.PI / 2;
            if (t < Math.PI / 2) {
                send(p, close, x, y, z, volume * (float) Math.cos(t), pitch, seed);
            }
            if (t > 0) {
                send(p, far, x, y, z, volume * (float) Math.sin(t), pitch, seed);
            }
        }
    }

    private static void send(ServerPlayer p, SoundEvent sound, double x, double y, double z, float volume, float pitch, long seed) {
        if (volume > 0.01f) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                    net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                    x, y, z, volume, pitch, seed));
        }
    }

    private static Vec3 jitter(Vec3 look, double degrees, ServerPlayer player) {
        if (degrees <= 0) {
            return look;
        }
        double r = Math.toRadians(degrees) * Math.sqrt(player.getRandom().nextDouble());
        double a = player.getRandom().nextDouble() * Math.PI * 2;
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        return look.add(right.scale(Math.cos(a) * Math.tan(r))).add(up.scale(Math.sin(a) * Math.tan(r))).normalize();
    }

    /**
     * The cone a shot can go anywhere inside (degrees from the aim point, the whole of it): the gun's spread from the
     * hip or on the sights, narrowed by attachments, the Aiming skill and its roll, widened on the move, plus how far
     * it has bloomed. Buckshot spreads further round where this puts the shot ({@link #pelletSpread}). The crosshair
     * draws the same number.
     */
    public static double spread(Player player, ItemStack gun, GunType type, boolean aiming, double bloom) {
        ZcConfig.GunStats stats = type.stats();
        int aimLevel = Skills.level(player, Skill.AIMING);
        double spread = (aiming ? stats.aimSpread * Attachments.aimSpreadFactor(gun) : stats.hipSpread * Attachments.hipSpreadFactor(gun))
                * (1 - 0.05 * aimLevel) * Rolls.multiplier(gun, Stat.ACCURACY);
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.003) {
            spread *= aimLevel >= 5 ? 1.3 : 1.6;    // Aiming 5: a steadier hand on the move
        } else if (aiming && player.isCrouching() && Attachments.hasBipod(gun)) {
            spread *= ZcConfig.get().guns.bipodSpread;
        }
        return spread + bloom * (aiming ? 0.5 : 1.0);
    }

    /** How far a shotgun's pellets fan out round the shot's line (degrees), 0 for single bullets. */
    public static double pelletSpread(GunType type) {
        return type.pellets() > 1 && type.stats() instanceof ZcConfig.ShotgunStats sg ? sg.pelletSpread : 0;
    }

    /** The bloom left after seconds without a shot: it closes back toward nothing. */
    public static double bloomNow(double bloom, double seconds, ZcConfig.GunStats stats) {
        return Math.max(0, bloom - stats.bloomRecover * Math.max(0, seconds));
    }

    /** The bloom just after a shot: a semi-auto shot kicks it open a little; full auto piles it up, to a limit. */
    public static double bloomed(double bloom, boolean auto, ZcConfig.GunStats stats) {
        return auto ? Math.max(bloom, Math.min(stats.autoBloomMax, bloom + stats.autoBloomPerShot)) : Math.min(Math.max(bloom, stats.autoBloomMax), bloom + stats.shotBloom);
    }

    /** The sound of this gun's spent case landing: a hull for shotguns, brass for the rest. */
    public static net.minecraft.sounds.SoundEvent casing(GunType type) {
        return type.ammo() == ModItems.SHOTGUN_SHELL ? ModSounds.CASING_SHOTGUN : ModSounds.CASING;
    }

    /** Everyone near where a shot struck hears it land close by, on top of what it hit; the shooter doesn't. */
    private static void nearbyImpact(ServerLevel level, ServerPlayer shooter, Vec3 p) {
        level.playSound(shooter, p.x, p.y, p.z, ModSounds.IMPACT_NEARBY, SoundSource.PLAYERS, 0.9f,
                0.92f + shooter.getRandom().nextFloat() * 0.16f);
    }

    /** The gun has a full-auto setting and is set to it. */
    public static boolean fullAuto(ItemStack gun) {
        GunType type = GunType.of(gun);
        return type != null && type.stats().selectFire && gun.getOrDefault(GunState.FULL_AUTO, false) && !launcherOn(gun);
    }

    /**
     * When the next full-auto shot is due after one fired now: a steady rate carries on from the last one due, a fresh
     * pull of the trigger starts from now.
     */
    public static double nextAuto(double due, long now, ZcConfig.GunStats stats) {
        double gap = 1200.0 / Math.max(60.0, stats.autoRpm);
        return (now - due > gap || now < due - 2 * gap ? now : due) + gap;
    }

    /**
     * Switches a select-fire gun between semi and full auto, with the selector's click; with an underbarrel grenade
     * launcher on, the launcher is the next setting round.
     */
    public static void toggleFireMode(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        GunType type = GunType.of(gun);
        boolean launcher = type != null && Attachments.hasLauncher(gun);
        if (type == null || !type.stats().selectFire && !launcher) {
            return;
        }
        String mode;
        if (launcherOn(gun)) {
            gun.set(GunState.LAUNCHER_ON, false);
            gun.set(GunState.FULL_AUTO, false);
            mode = "Semi-auto";
        } else if (type.stats().selectFire && !gun.getOrDefault(GunState.FULL_AUTO, false)) {
            gun.set(GunState.FULL_AUTO, true);
            mode = "Full auto";
        } else if (launcher) {
            gun.set(GunState.LAUNCHER_ON, true);
            mode = gun.getOrDefault(GunState.LAUNCHER_LOADED, false) ? "Grenade launcher" : "Grenade launcher (empty: R to load)";
        } else {
            gun.set(GunState.FULL_AUTO, false);
            mode = "Semi-auto";
        }
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON,
                SoundSource.PLAYERS, 0.3f, mode.equals("Full auto") ? 1.5f : mode.equals("Semi-auto") ? 1.3f : 1.1f);
        player.sendOverlayMessage(Component.literal(mode));
    }

    /** The trigger works the underbarrel grenade launcher. */
    public static boolean launcherOn(ItemStack gun) {
        return gun.getOrDefault(GunState.LAUNCHER_ON, false) && Attachments.hasLauncher(gun);
    }

    /** The underbarrel launcher's one round goes, lobbed like the six-shot's (combat/throwing/Thrown.fire). */
    private static void fireLauncher(ServerPlayer player, ItemStack gun, State st, long now, boolean aiming) {
        if (now - st.lastShot < 10 || st.reloadTick >= 0) {
            return;
        }
        st.lastShot = now;
        ServerLevel level = (ServerLevel) player.level();
        if (!gun.getOrDefault(GunState.LAUNCHER_LOADED, false)) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SHOTGUN_DRY, SoundSource.PLAYERS, 0.8f, 0.8f);
            player.sendOverlayMessage(Component.literal("Launcher empty: press R to load a 40mm grenade.").withStyle(ChatFormatting.GRAY));
            return;
        }
        gun.set(GunState.LAUNCHER_LOADED, false);
        GunRaise.shot(player);
        Vec3 look = player.getLookAngle();
        Vec3 aim = jitter(look, aiming ? 0.6 : 2.0, player);
        Vec3 muzzle = player.getEyePosition().add(look.scale(0.9)).add(0, aiming ? -0.2 : -0.35, 0);
        com.zomboidcraft.combat.throwing.Thrown.fire(level, player, new ItemStack(ModItems.GRENADE_ROUND), muzzle, aim);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.SHOTGUN_SHOT, SoundSource.PLAYERS, 1f, 0.6f);
        Noise.emit(level, player.position(), ZcConfig.get().grenadeLauncher.shotNoise);
    }

    /** Pushes a 40mm round into the underbarrel launcher. */
    private static void loadLauncher(ServerPlayer player, ItemStack gun) {
        if (gun.getOrDefault(GunState.LAUNCHER_LOADED, false)) {
            player.sendOverlayMessage(Component.literal("The launcher is loaded.").withStyle(ChatFormatting.GRAY));
            return;
        }
        Inventory inv = player.getInventory();
        int at = -1;
        for (int i = 0; i < inv.getContainerSize() && at < 0; i++) {
            if (inv.getItem(i).is(ModItems.GRENADE_ROUND)) {
                at = i;
            }
        }
        if (at < 0 && !player.hasInfiniteMaterials()) {
            player.sendOverlayMessage(Component.literal("No 40mm grenades.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (at >= 0 && !player.hasInfiniteMaterials()) {
            inv.getItem(at).shrink(1);
        }
        gun.set(GunState.LAUNCHER_LOADED, true);
        sound(player, ModSounds.SHOTGUN_SHELL_IN);
        player.sendOverlayMessage(Component.literal("Launcher loaded"));
    }

    // ------------------------------------------------------------------ light and laser

    /** Switches the gun's light on or off, with a click. */
    public static void toggleLight(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        if (GunType.isGun(gun) && Attachments.hasLight(gun)) {
            toggle(player, gun, GunState.RAIL_ON);
        }
    }

    /** Switches the gun's laser on or off, with a click. */
    public static void toggleLaser(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        if (GunType.isGun(gun) && Attachments.hasLaser(gun)) {
            toggle(player, gun, GunState.LASER_ON);
        }
    }

    private static void toggle(ServerPlayer player, ItemStack gun, DataComponentType<Boolean> switched) {
        boolean on = !gun.getOrDefault(switched, false);
        gun.set(switched, on);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON,
                SoundSource.PLAYERS, 0.25f, on ? 2.0f : 1.7f);
    }

    // ------------------------------------------------------------------ reloading

    public static void reload(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        GunType type = GunType.of(gun);
        if (type == null) {
            return;
        }
        if (launcherOn(gun)) {
            loadLauncher(player, gun);
            return;
        }
        State st = state(player);
        if (st.reloadTick >= 0 || type.bolt && player.level().getGameTime() - st.lastShot < type.fireDelay()) {
            return;     // already reloading, or the bolt is still being worked after a shot
        }
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        if (type.tube) {
            startLoading(player, gun, type, st, g);
            return;
        }
        if (g.jammed()) {
            // clear the jam: work the charging handle, losing the stuck round
            gun.set(GunState.TYPE, new GunState(g.mag(), Math.max(0, g.rounds() - 1), false));
            player.stopUsingItem();
            st.reloadTick = type.jamClear;
            st.reloading = type;
            st.needsCharge = true;
            setModelData(gun, 3, 0);
            return;
        }
        int best = bestMagazine(player, type);
        if (best < 0 && !player.hasInfiniteMaterials()) {
            player.sendOverlayMessage(Component.literal(g.mag() && g.rounds() > 0 ? "No fuller magazine to swap in."
                    : "No loaded magazines. Load one: hold right-click with it.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (best >= 0 && g.mag() && MagazineItem.rounds(player.getInventory().getItem(best)) <= g.rounds()) {
            player.sendOverlayMessage(Component.literal("No fuller magazine to swap in.").withStyle(ChatFormatting.GRAY));
            return;
        }
        player.stopUsingItem();
        st.reloadTick = 0;
        st.reloading = type;
        st.needsCharge = !g.mag() || g.rounds() == 0;
        // the old magazine comes out with whatever is left in it
        if (g.mag()) {
            ItemStack old = new ItemStack(type.magazine());
            old.set(GunState.MAG_ROUNDS, g.rounds());
            if (!player.getInventory().add(old)) {
                player.spawnAtLocation(level(player), old);
            }
            gun.set(GunState.TYPE, new GunState(false, 0, false));
        }
        setModelData(gun, st.needsCharge ? 1.5f : 1, 0);
        sound(player, type.magOut());
    }

    /** A tube gun: raised to the loading port, then shells go in one by one (see tickLoading). */
    private static void startLoading(ServerPlayer player, ItemStack gun, GunType type, State st, GunState g) {
        if (g.rounds() >= type.capacity()) {
            player.sendOverlayMessage(Component.literal("It's full.").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (countAmmo(player, type) == 0 && !player.hasInfiniteMaterials()) {
            player.sendOverlayMessage(Component.literal("You have no " + type.caliber() + " " + type.rounds() + ".").withStyle(ChatFormatting.GRAY));
            return;
        }
        player.stopUsingItem();
        st.reloadTick = 0;
        st.reloading = type;
        st.needsCharge = !type.opens && g.rounds() == 0;
        st.loadStopped = false;
        st.loading = gun;
        setModelData(gun, st.needsCharge ? 1.5f : 1, 0);
        if (type.opens) {
            // thrown open: the empties come out as the barrels drop
            sound(player, type.magOut());
            st.caseAt = player.level().getGameTime() + type.raise / 2;
        }
    }

    private static void tickLoading(ServerPlayer player, ItemStack gun, GunType type, State st) {
        st.reloadTick++;
        if (type == GunType.CROSSBOW && st.reloadTick == GunType.COCK_AT) {
            sound(player, type.charged());      // the whole draw, as the hand takes the string; it seats COCK_SEAT later
        }
        if (!st.loadStopped) {
            int t = st.reloadTick - type.raise;
            if (t > 0 && t % type.perShell == 0) {
                if (takeAmmo(player, type) || player.hasInfiniteMaterials()) {
                    GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
                    gun.set(GunState.TYPE, g.withRounds(g.rounds() + 1));
                    sound(player, type.magIn());
                }
                if (gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds() >= type.capacity()
                        || countAmmo(player, type) == 0 && !player.hasInfiniteMaterials()) {
                    stopLoading(st);
                }
            }
            return;
        }
        int since = st.reloadTick - st.stopTick;
        if (type.opens) {
            // shut again: snapped closed halfway through
            if (since == 1) {
                setModelData(gun, 3, 0);
            } else if (since == 1 + type.close / 2) {
                sound(player, type.charged());
            } else if (since >= 1 + type.close) {
                endReload(gun, st);
                Skills.addXp(player, Skill.AIMING, 1);
            }
            return;
        }
        if (!st.needsCharge) {
            endReload(gun, st);
        } else if (since == 1) {
            setModelData(gun, 3, 0);
        } else if (since == 1 + type.cycle / 2) {
            sound(player, type.charged());
        } else if (since >= 1 + type.cycle) {
            endReload(gun, st);
            Skills.addXp(player, Skill.AIMING, 1);
        }
    }

    private static void stopLoading(State st) {
        st.loadStopped = true;
        st.stopTick = st.reloadTick;
    }

    private static void endReload(ItemStack gun, State st) {
        setModelData(gun, 0, 0);
        st.reloadTick = -1;
        st.loading = ItemStack.EMPTY;
    }

    /** Loose rounds or shells of this gun's kind in the inventory. */
    private static int countAmmo(ServerPlayer player, GunType type) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(type.ammo())) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    private static boolean takeAmmo(ServerPlayer player, GunType type) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(type.ammo())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static int bestMagazine(ServerPlayer player, GunType type) {
        Inventory inv = player.getInventory();
        int best = -1, most = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(type.magazine()) && MagazineItem.rounds(s) > most) {
                most = MagazineItem.rounds(s);
                best = i;
            }
        }
        return best;
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State st = STATES.get(player);
            if (st == null) {
                continue;
            }
            ItemStack gun = player.getMainHandItem();
            if (st.flashLeft > 0 && --st.flashLeft == 0 && GunType.isGun(gun)) {
                setModelData(gun, -1, 0);
            }
            if (st.cycleAt >= 0 && player.level().getGameTime() >= st.cycleAt) {
                st.cycleAt = -1;
                GunType held = GunType.of(gun);
                if (held != null && held.manual()) {
                    sound(player, held.charged());
                    // a pump throws its hull out as it's worked; a bolt's case comes out at the end of the back stroke
                    st.caseAt = player.level().getGameTime() + (held.bolt ? GunType.CASE_OUT : 0);
                }
            }
            if (st.caseAt >= 0 && player.level().getGameTime() >= st.caseAt) {
                st.caseAt = -1;
                GunType held = GunType.of(gun);
                if (held != null && (held.manual() || held.opens)) {
                    if (held.eject() != null) {
                        sound(player, held.eject());
                    }
                    // a shotgun hull lands with a dull plastic bounce, a brass case with a ring
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), casing(held),
                            SoundSource.PLAYERS, 0.4f, 0.85f + player.getRandom().nextFloat() * 0.2f);
                }
            }
            if (st.reloadTick < 0) {
                continue;
            }
            GunType type = st.reloading;
            if (GunType.of(gun) != type) {
                // put away mid-reload: the magazine stays out; a tube gun just stops loading
                if (type.tube && GunType.of(st.loading) == type) {
                    endReload(st.loading, st);
                }
                st.reloadTick = -1;
                continue;
            }
            if (type.tube) {
                tickLoading(player, gun, type, st);
                continue;
            }
            st.reloadTick++;
            if (st.reloadTick == type.magIn) {
                insertMagazine(player, gun, type);
                setModelData(gun, 2, 0);
                sound(player, type.magIn());
            } else if (st.reloadTick == type.charge && st.needsCharge) {
                setModelData(gun, 3, 0);
                sound(player, type.charged());
            } else if (st.reloadTick >= (st.needsCharge ? type.chargeDone : type.tacticalDone)) {
                setModelData(gun, 0, 0);
                st.reloadTick = -1;
            }
        }
    }

    private static void insertMagazine(ServerPlayer player, ItemStack gun, GunType type) {
        int best = bestMagazine(player, type);
        int rounds;
        if (best >= 0) {
            ItemStack mag = player.getInventory().getItem(best);
            rounds = MagazineItem.rounds(mag);
            mag.shrink(1);
        } else if (player.hasInfiniteMaterials()) {
            rounds = type.capacity();
        } else {
            return;
        }
        gun.set(GunState.TYPE, new GunState(true, rounds, false));
        Skills.addXp(player, Skill.AIMING, 1);
    }

    /** Sets the reload step (or -1 to keep it) and the flash in the gun's custom model data, keeping its attachments. */
    private static void setModelData(ItemStack gun, float step, int flash) {
        CustomModelData old = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        float oldStep = old != null && !old.floats().isEmpty() ? old.floats().get(0) : 0;
        Attachments.writeModelData(gun, step < 0 ? oldStep : step, flash);
    }

    private static ServerLevel level(ServerPlayer player) {
        return (ServerLevel) player.level();
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.9f, 1f);
    }

    /** Whether the player is mid-reload (the HUD and client firing check it through the model data too). */
    public static boolean reloading(ServerPlayer player) {
        State st = STATES.get(player);
        return st != null && st.reloadTick >= 0;
    }

    private Guns() {
    }
}
