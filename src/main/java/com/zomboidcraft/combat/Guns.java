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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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
        /** A revolver's hammer, thumbed back on the sights: down again with the next pull of the trigger. */
        boolean hammerBack;
    }

    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Guns::tick);
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player, p -> new State());
    }

    // ------------------------------------------------------------------ firing

    /** Brought up onto the sights: a loaded revolver has its hammer thumbed back, once until the trigger drops it. */
    public static void aim(ServerPlayer player, ItemStack gun) {
        GunType type = GunType.of(gun);
        net.minecraft.sounds.SoundEvent hammer = type == null ? null : type.hammer();
        State st = state(player);
        if (hammer == null || st.hammerBack || st.reloadTick >= 0
                || gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds() <= 0) {
            return;
        }
        st.hammerBack = true;
        sound(player, hammer);
    }

    public static void fire(ServerPlayer player, boolean aiming) {
        ZcConfig.Guns config = ZcConfig.get().guns;
        ItemStack gun = player.getMainHandItem();
        GunType type = GunType.of(gun);
        if (!config.enabled || type == null || player.isSpectator()) {
            return;
        }
        ZcConfig.GunStats stats = type.stats();
        State st = state(player);
        long now = player.level().getGameTime();
        boolean auto = fullAuto(gun);
        // full auto takes a tick's slack for shots the network bunched up; its rate still holds on average
        if (auto ? now < st.autoNext - 1.0 : now - st.lastShot < type.fireDelay()) {
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
        st.hammerBack = false;
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

        // the bang: everyone else hears the distant take; the shooter's client plays the close one at once.
        // A suppressor takes the crack down to a cough, and shortens how far the zombies hear it.
        boolean suppressed = Attachments.suppressed(gun);
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), type.shot(suppressed, true), SoundSource.PLAYERS,
                suppressed ? (float) config.suppressedVolume : 1f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        if (type.manual()) {
            st.cycleAt = now + type.cycleAfterShot;     // the empty hull or case comes out when it's worked
        } else if (!type.opens && type != GunType.CROSSBOW) {   // a double keeps its empties till it's opened
            level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASING, SoundSource.PLAYERS,
                    0.4f, 0.9f + player.getRandom().nextFloat() * 0.25f);
        }
        Noise.emit(level, player.position(), stats.shotNoise * (suppressed ? config.suppressedNoise : 1.0));

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int aimLevel = Skills.level(player, Skill.AIMING);
        double spread = (aiming ? stats.aimSpread * Attachments.aimSpreadFactor(gun) : stats.hipSpread * Attachments.hipSpreadFactor(gun))
                * (1 - 0.05 * aimLevel) * Rolls.multiplier(gun, Stat.ACCURACY);
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.003) {
            spread *= aimLevel >= 5 ? 1.3 : 1.6;    // Aiming 5: a steadier hand on the move
        }
        Vec3 aim = jitter(look, spread, player);
        Vec3 muzzle = eye.add(look.scale(0.9)).add(look.cross(new Vec3(0, 1, 0)).normalize().scale(aiming ? 0 : 0.25))
                .add(0, aiming ? -0.08 : -0.22, 0);
        if (config.muzzleSmoke && type != GunType.CROSSBOW) {
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
            if (!suppressed) {
                level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // each pellet (one bullet, or a shotgun's buckshot) flies on its own; the hits on each target add up into
        // one blow, since a target only takes the first of several hits in the same moment
        int pellets = type.pellets();
        double pelletSpread = stats instanceof ZcConfig.ShotgunStats sg ? sg.pelletSpread : 0;
        float power = (float) Rolls.multiplier(gun, Stat.DAMAGE);
        Map<LivingEntity, Hit> hits = new java.util.LinkedHashMap<>();
        boolean ricochet = false;
        boolean bow = type == GunType.CROSSBOW;
        Vec3 boltAt = null;     // where a crossbow's bolt ends up: under what it hit, or stuck where it struck
        Zombie boltIn = null;   // or the zombie it went into, to stay standing in it
        Vec3 boltDir = null;
        for (int i = 0; i < pellets; i++) {
            Vec3 dir = pellets > 1 ? jitter(aim, pelletSpread, player) : aim;
            Vec3 end = eye.add(dir.scale(stats.range));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, stop, new AABB(eye, stop).inflate(1.0),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && e != player,
                    eye.distanceToSqr(stop));
            if (hit != null && hit.getEntity() instanceof LivingEntity target) {
                Pellet struck = pellet(target, eye, dir, stats, power);
                hits.computeIfAbsent(target, t -> new Hit(dir)).add(struck);
                Dismember.pellet(target, eye, dir, struck.tears(), struck.head(), struck.legs(), type);
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
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), p.x, p.y, p.z, pellets > 1 ? 3 : 8,
                        0.05, 0.05, 0.05, 0.15);
                level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, pellets > 1 ? 1 : 2, 0.02, 0.02, 0.02, 0.01);
                if (!ricochet) {
                    ricochet = true;
                    level.playSound(null, p.x, p.y, p.z, ModSounds.BULLET_RICOCHET, SoundSource.PLAYERS, 0.7f,
                            0.9f + player.getRandom().nextFloat() * 0.3f);
                }
                if (bs.is(Blocks.GLASS) || bs.is(Blocks.GLASS_PANE) || bs.getBlock().getDescriptionId().contains("glass")) {
                    level.destroyBlock(block.getBlockPos(), false, player);
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
    private static Pellet pellet(LivingEntity target, Vec3 eye, Vec3 dir, ZcConfig.GunStats stats, float power) {
        Vec3 at = target.getBoundingBox().inflate(0.1).clip(eye, eye.add(dir.scale(stats.range))).orElse(target.position());
        boolean downed = Downed.isLow(target);
        double headY = target.getEyeY() - 0.28;
        boolean head = !downed && at.y >= headY;
        boolean legs = !downed && at.y < target.getY() + target.getBbHeight() * 0.35;
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
        // buckshot up close knocks a zombie back hard; a single bullet barely moves it
        double push = stats instanceof ZcConfig.ShotgunStats sg ? sg.knockback * Math.min(1, 1.5 * hit.count / pellets) : 0.12;
        Shove.push(target, hit.dir.multiply(1, 0, 1).normalize(), push);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                at.x, at.y, at.z, (hit.head ? 22 : 12) + 2 * (hit.count - 1), 0.12, 0.12, 0.12, 0.2);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BULLET_FLESH, SoundSource.PLAYERS, 1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        if (ZombieTypes.isWalker(target) && target.isAlive() && hit.legs) {
            Downed.crippleChance(target, 0.5);
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

    /** The gun has a full-auto setting and is set to it. */
    public static boolean fullAuto(ItemStack gun) {
        GunType type = GunType.of(gun);
        return type != null && type.stats().selectFire && gun.getOrDefault(GunState.FULL_AUTO, false);
    }

    /**
     * When the next full-auto shot is due after one fired now: a steady rate carries on from the last one due, a fresh
     * pull of the trigger starts from now.
     */
    public static double nextAuto(double due, long now, ZcConfig.GunStats stats) {
        double gap = 1200.0 / Math.max(60.0, stats.autoRoundsPerMinute);
        return (now - due > gap || now < due - 2 * gap ? now : due) + gap;
    }

    /** Switches a select-fire gun between semi and full auto, with the selector's click. */
    public static void toggleFireMode(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        GunType type = GunType.of(gun);
        if (type == null || !type.stats().selectFire) {
            return;
        }
        boolean auto = !gun.getOrDefault(GunState.FULL_AUTO, false);
        gun.set(GunState.FULL_AUTO, auto);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON,
                SoundSource.PLAYERS, 0.3f, auto ? 1.5f : 1.3f);
        player.sendOverlayMessage(Component.literal(auto ? "Full auto" : "Semi-auto"));
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
        st.hammerBack = false;      // let down before the gun's opened
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
                    boolean hull = held.ammo() == ModItems.SHOTGUN_SHELL;
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASING,
                            SoundSource.PLAYERS, hull ? 0.35f : 0.4f, (hull ? 0.6f : 0.85f) + player.getRandom().nextFloat() * 0.15f);
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
