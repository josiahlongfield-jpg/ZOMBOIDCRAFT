package com.zomboidcraft.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.ModDamageTypes;
import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
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
 * in, charging handle) that also drives the reload item models.
 *
 * The rifle's custom model data carries two numbers for its item model: [0] the reload step
 * (1 magazine out, 2 magazine going in, 3 charging handle) and [1] the muzzle flash, plus the
 * attachment keys {@link Attachments} writes.
 */
public final class Guns {
    private static final int MAG_OUT_AT = 0;
    private static final int MAG_IN_AT = 16;
    private static final int CHARGE_AT = 28;
    private static final int RELOAD_DONE = 36;
    private static final int FLASH_TICKS = 2;

    private static final class State {
        long lastShot = -100;
        int reloadTick = -1;
        boolean needsCharge;
        int flashLeft;
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
        if (!config.enabled || !gun.is(ModItems.ASSAULT_RIFLE) || player.isSpectator()) {
            return;
        }
        State st = state(player);
        long now = player.level().getGameTime();
        if (st.reloadTick >= 0 || now - st.lastShot < config.fireDelayTicks) {
            return;
        }
        st.lastShot = now;
        ServerLevel level = (ServerLevel) player.level();
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        if (g.jammed() || !g.mag() || g.rounds() <= 0) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.RIFLE_DRY, SoundSource.PLAYERS, 0.8f, 1f);
            if (g.jammed()) {
                player.sendOverlayMessage(Component.literal("Jammed! Press R to clear it.").withStyle(ChatFormatting.RED));
            }
            return;
        }
        // a worn gun can jam instead of firing
        float wear = gun.isDamageableItem() ? (float) gun.getDamageValue() / gun.getMaxDamage() : 0;
        if (config.jams && player.getRandom().nextFloat() < 0.08f * wear * wear) {
            gun.set(GunState.TYPE, g.withJam(true));
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.RIFLE_DRY, SoundSource.PLAYERS, 0.8f, 0.8f);
            player.sendOverlayMessage(Component.literal("Jammed! Press R to clear it.").withStyle(ChatFormatting.RED));
            return;
        }
        gun.set(GunState.TYPE, g.withRounds(g.rounds() - 1));
        gun.hurtAndBreak(1, player, net.minecraft.world.InteractionHand.MAIN_HAND);
        st.flashLeft = FLASH_TICKS;
        setModelData(gun, 0, 1);

        // the bang: everyone else hears the distant take; the shooter's client plays the close one at once
        level.playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.RIFLE_SHOT_DISTANT, SoundSource.PLAYERS,
                1f, 0.95f + player.getRandom().nextFloat() * 0.1f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.CASING, SoundSource.PLAYERS,
                0.4f, 0.9f + player.getRandom().nextFloat() * 0.25f);
        Noise.emit(level, player.position(), config.shotNoise);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        int aimLevel = Skills.level(player, Skill.AIMING);
        double spread = (aiming ? config.aimSpread * Attachments.aimSpreadFactor(gun) : config.hipSpread) * (1 - 0.05 * aimLevel);
        if (player.getDeltaMovement().horizontalDistanceSqr() > 0.003) {
            spread *= 1.6;
        }
        Vec3 dir = jitter(look, spread, player);
        Vec3 muzzle = eye.add(look.scale(0.9)).add(look.cross(new Vec3(0, 1, 0)).normalize().scale(aiming ? 0 : 0.25))
                .add(0, aiming ? -0.08 : -0.22, 0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.01);
        level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.0, 0.0, 0.0, 0.0);

        Vec3 end = eye.add(dir.scale(config.range));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && e != player,
                eye.distanceToSqr(stop));
        if (hit != null && hit.getEntity() instanceof LivingEntity target) {
            hitEntity(level, player, target, eye, dir);
            Skills.addXp(player, Skill.AIMING, 2);
        } else if (block.getType() != HitResult.Type.MISS) {
            BlockState bs = level.getBlockState(block.getBlockPos());
            Vec3 p = block.getLocation();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), p.x, p.y, p.z, 8, 0.05, 0.05, 0.05, 0.15);
            level.sendParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.01);
            level.playSound(null, p.x, p.y, p.z, ModSounds.BULLET_RICOCHET, SoundSource.PLAYERS, 0.7f,
                    0.9f + player.getRandom().nextFloat() * 0.3f);
            if (bs.is(Blocks.GLASS) || bs.is(Blocks.GLASS_PANE) || bs.getBlock().getDescriptionId().contains("glass")) {
                level.destroyBlock(block.getBlockPos(), false, player);
            }
        }
    }

    private static void hitEntity(ServerLevel level, ServerPlayer player, LivingEntity target, Vec3 eye, Vec3 dir) {
        ZcConfig.Guns config = ZcConfig.get().guns;
        Vec3 at = target.getBoundingBox().inflate(0.1).clip(eye, eye.add(dir.scale(config.range))).orElse(target.position());
        boolean downed = Downed.isLow(target);
        double headY = target.getEyeY() - 0.28;
        boolean head = !downed && at.y >= headY;
        boolean legs = !downed && at.y < target.getY() + target.getBbHeight() * 0.35;
        float damage = (float) (config.damage * (head ? config.headshotMultiplier : legs ? 0.7 : 1));
        target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BULLET, player), damage);
        Shove.push(target, dir.multiply(1, 0, 1).normalize(), 0.12);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                at.x, at.y, at.z, head ? 22 : 12, 0.12, 0.12, 0.12, 0.2);
        level.playSound(null, at.x, at.y, at.z, ModSounds.BULLET_FLESH, SoundSource.PLAYERS, 1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        if (ZombieTypes.isWalker(target) && target.isAlive() && legs) {
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

    // ------------------------------------------------------------------ reloading

    public static void reload(ServerPlayer player) {
        ItemStack gun = player.getMainHandItem();
        if (!gun.is(ModItems.ASSAULT_RIFLE)) {
            return;
        }
        State st = state(player);
        if (st.reloadTick >= 0) {
            return;
        }
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        if (g.jammed()) {
            // clear the jam: work the charging handle, losing the stuck round
            gun.set(GunState.TYPE, new GunState(g.mag(), Math.max(0, g.rounds() - 1), false));
            st.reloadTick = CHARGE_AT;
            st.needsCharge = false;
            setModelData(gun, 3, 0);
            sound(player, ModSounds.RIFLE_BOLT);
            return;
        }
        int best = bestMagazine(player);
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
        st.reloadTick = MAG_OUT_AT;
        st.needsCharge = !g.mag() || g.rounds() == 0;
        setModelData(gun, 1, 0);
        // the old magazine comes out with whatever is left in it
        if (g.mag()) {
            ItemStack old = new ItemStack(ModItems.RIFLE_MAGAZINE);
            old.set(GunState.MAG_ROUNDS, g.rounds());
            if (!player.getInventory().add(old)) {
                player.spawnAtLocation(level(player), old);
            }
            gun.set(GunState.TYPE, new GunState(false, 0, false));
        }
        sound(player, ModSounds.RIFLE_MAG_OUT);
    }

    private static int bestMagazine(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int best = -1, most = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.RIFLE_MAGAZINE) && MagazineItem.rounds(s) > most) {
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
            if (st.flashLeft > 0 && --st.flashLeft == 0 && gun.is(ModItems.ASSAULT_RIFLE)) {
                setModelData(gun, -1, 0);
            }
            if (st.reloadTick < 0) {
                continue;
            }
            if (!gun.is(ModItems.ASSAULT_RIFLE)) {
                st.reloadTick = -1;     // put away mid-reload: the magazine stays out
                continue;
            }
            st.reloadTick++;
            if (st.reloadTick == MAG_IN_AT) {
                insertMagazine(player, gun);
                setModelData(gun, 2, 0);
                sound(player, ModSounds.RIFLE_MAG_IN);
            } else if (st.reloadTick == CHARGE_AT && st.needsCharge) {
                setModelData(gun, 3, 0);
                sound(player, ModSounds.RIFLE_BOLT);
            } else if (st.reloadTick == CHARGE_AT && !st.needsCharge && gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).mag()) {
                setModelData(gun, 0, 0);
            } else if (st.reloadTick >= RELOAD_DONE) {
                setModelData(gun, 0, 0);
                st.reloadTick = -1;
            }
        }
    }

    private static void insertMagazine(ServerPlayer player, ItemStack gun) {
        int best = bestMagazine(player);
        int rounds;
        if (best >= 0) {
            ItemStack mag = player.getInventory().getItem(best);
            rounds = MagazineItem.rounds(mag);
            mag.shrink(1);
        } else if (player.hasInfiniteMaterials()) {
            rounds = RifleItem.capacity();
        } else {
            return;
        }
        gun.set(GunState.TYPE, new GunState(true, rounds, false));
        Skills.addXp(player, Skill.AIMING, 1);
    }

    /** Sets the reload step (or -1 to keep it) and the flash in the gun's custom model data, keeping its attachments. */
    private static void setModelData(ItemStack gun, int step, int flash) {
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
