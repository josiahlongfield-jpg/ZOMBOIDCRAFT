package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.sound.EarSound;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.rolls.Rolls;
import com.zomboidcraft.combat.rolls.Stat;
import com.zomboidcraft.combat.CombatNet;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.Grabs;
import com.zomboidcraft.combat.Guns;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.combat.Weapons;
import com.zomboidcraft.combat.WeaponSpecs;
import com.zomboidcraft.combat.GunType;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The player's side of combat: R reloads, V shoves, left-click fires a gun (with its kick
 * and its bang heard at once, before the server answers; held down on full auto it keeps firing),
 * X switches a select-fire gun between semi and full auto, jump struggles out of a grab, and an
 * empty endurance bar stops you sprinting.
 */
public final class CombatClient {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZomboidCraft.id("zomboidcraft"));
    public static final KeyMapping RELOAD = new KeyMapping("key.zomboidcraft.reload", InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping SHOVE = new KeyMapping("key.zomboidcraft.shove", InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping RAIL = new KeyMapping("key.zomboidcraft.rail", InputConstants.KEY_L, CATEGORY);
    public static final KeyMapping LASER = new KeyMapping("key.zomboidcraft.laser", InputConstants.KEY_J, CATEGORY);
    public static final KeyMapping FIRE_MODE = new KeyMapping("key.zomboidcraft.fire_mode", InputConstants.KEY_X, CATEGORY);
    public static final KeyMapping SHOULDER = new KeyMapping("key.zomboidcraft.shoulder", InputConstants.KEY_O, CATEGORY);

    private static long lastShot = -100;
    /**
     * Full auto: when the next shot is due on the wall clock (milliseconds), and the rounds this hold of the trigger
     * can still fire. Checked every frame, not every tick, so the shots come evenly spaced at any rate (on ticks,
     * 800 a minute came out a tick, then two, then one...).
     */
    private static double autoNextMs = -1e18;
    private static int burstLeft = -1;
    /** The spread's bloom as of bloomAt (wall clock ns), for the gun it was fired with: kept in step with the server's. */
    private static double bloom;
    private static long bloomAt;
    private static GunType bloomGun;
    /** Muzzle climb still to apply next tick, and how much of it eases back afterwards. */
    private static float kickPending;
    private static float recoverLeft;
    /** When this player last fired (wall clock), and the over-the-shoulder flag the server last heard. */
    private static long shotMs = Long.MIN_VALUE / 2;
    private static Boolean shoulderSent;
    private static Object shoulderPlayer;
    /** The camera went to first person for a 4x scope aimed over the shoulder, and goes back when it's lowered. */
    private static boolean scopeFromShoulder;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(RELOAD);
        KeyMappingHelper.registerKeyMapping(SHOVE);
        KeyMappingHelper.registerKeyMapping(RAIL);
        KeyMappingHelper.registerKeyMapping(LASER);
        KeyMappingHelper.registerKeyMapping(FIRE_MODE);
        KeyMappingHelper.registerKeyMapping(SHOULDER);
        ClientPreAttackCallback.EVENT.register((client, player, clicks) -> {
            ItemStack hand = player.getMainHandItem();
            if (!com.zomboidcraft.world.cars.CarProp.mayUse(player, hand)) {
                return true;    // driving: a hand on the wheel, nothing but a handgun
            }
            if (GunType.isGun(hand)) {
                if (clicks > 0 && ZcConfig.get().guns.enabled) {
                    pullTrigger(client, player, hand, false);
                }
                return true;    // a gun never punches or digs
            }
            if (clicks > 0 && hand.getItem() instanceof MeleeWeaponItem && player.getAttackStrengthScale(0f) > 0.9f) {
                WeaponSpecs.WeaponSpec spec = Weapons.spec(hand);
                boolean heavy = spec != null && (spec.twoHanded() || spec.kind() == WeaponSpecs.Kind.HEAVY_BLUNT);
                EarSound.play(heavy ? ModSounds.SWING_HEAVY : ModSounds.SWING, SoundSource.PLAYERS, 0.6f,
                        0.9f + player.getRandom().nextFloat() * 0.2f);
            }
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(CombatClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(HoldPoses::faceLook);
        RaisedProperty.register();
        MeshGunRenderer.register();
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> autoFire(Minecraft.getInstance()));
    }

    /** Every frame: full auto keeps firing while the trigger's held, each shot as it falls due. */
    private static void autoFire(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        ItemStack hand = player.getMainHandItem();
        if (!client.options.keyAttack.isDown() || client.gui.screen() != null || client.isPaused()
                || com.zomboidcraft.client.building.Placer.building() != null) {
            burstLeft = -1;
        } else if (Guns.fullAuto(hand) && ZcConfig.get().guns.enabled && !player.isSpectator()) {
            pullTrigger(client, player, hand, true);
        }
    }

    /** This player's own gun is raised: over the shoulder, or fired a moment ago. Sooner than waiting on the server. */
    static boolean raisedHere() {
        return com.zomboidcraft.client.camera.ShoulderCam.active()
                || System.currentTimeMillis() - shotMs < ZcConfig.get().guns.raiseAfterShot * 1000;
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        boolean scoping = player.isUsingItem() && GunType.isGun(player.getUseItem()) && Attachments.scoped(player.getUseItem());
        if (scoping && !scopeFromShoulder && ZcConfig.get().look.scopeFromShoulder
                && com.zomboidcraft.client.camera.ShoulderCam.active()) {
            scopeFromShoulder = true;
            client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        } else if (!scoping && scopeFromShoulder) {
            scopeFromShoulder = false;
            if (client.options.getCameraType().isFirstPerson()) {
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            }
        }
        boolean shoulder = com.zomboidcraft.client.camera.ShoulderCam.active();
        if (shoulderPlayer != player || shoulderSent == null || shoulderSent != shoulder) {
            // others see the gun raised while you look over your shoulder, low in first person (GunRaise)
            shoulderPlayer = player;
            shoulderSent = shoulder;
            ClientPlayNetworking.send(new CombatNet.Action(shoulder ? CombatNet.Action.SHOULDER_ON : CombatNet.Action.SHOULDER_OFF));
        }
        while (RELOAD.consumeClick()) {
            if (GunType.isGun(player.getMainHandItem())) {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.RELOAD));
            }
        }
        while (SHOVE.consumeClick()) {
            if (player.getVehicle() instanceof com.zomboidcraft.world.cars.CarProp car && !car.aircraft()) {
                // sat in a car there's no shoving anyone: the key winds your window down or up
                ClientPlayNetworking.send(new com.zomboidcraft.world.cars.CarNet.Use(com.zomboidcraft.world.cars.CarNet.WINDOW, 0, 0, 0));
            } else {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.SHOVE));
            }
        }
        while (RAIL.consumeClick()) {
            if (com.zomboidcraft.client.cars.CarClient.driving(player)) {
                com.zomboidcraft.client.cars.CarClient.headlights();     // at the wheel the key works the headlights
            } else if (GunType.isGun(player.getMainHandItem())) {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.RAIL));
            }
        }
        while (LASER.consumeClick()) {
            if (GunType.isGun(player.getMainHandItem())) {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.LASER));
            }
        }
        while (FIRE_MODE.consumeClick()) {
            GunType type = GunType.of(player.getMainHandItem());
            if (type != null && type.stats().selectFire) {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.FIRE_MODE));
            }
        }
        Integer grabbed = player.getAttached(Grabs.GRABBED);
        if (grabbed != null && grabbed > 0) {
            while (client.options.keyJump.consumeClick()) {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.STRUGGLE));
            }
        }
        if (player.isSprinting() && ZcConfig.get().combat.stamina && !player.isCreative()
                && player.getAttachedOrElse(Stamina.TYPE, Stamina.FULL) < 10) {
            player.setSprinting(false);
        }
        while (SHOULDER.consumeClick()) {
            com.zomboidcraft.client.camera.ShoulderCam.swapShoulder();
        }
        // a broken leg with no splint won't take a sprint
        if (player.isSprinting() && !player.isCreative() && com.zomboidcraft.survival.Injuries.cantSprint(player)) {
            player.setSprinting(false);
        }
        recoil(player);
    }

    /**
     * The trigger pulled (held: still held down on full auto). Full auto fires on its rate, evenly spaced on the
     * wall clock, and stops when the rounds there were as the trigger went down are spent, without waiting
     * on the server to say the magazine's empty.
     */
    private static void pullTrigger(Minecraft client, LocalPlayer player, ItemStack gun, boolean held) {
        long now = client.level.getGameTime();
        ZcConfig.Guns config = ZcConfig.get().guns;
        // each world keeps its own clock, so a last shot "after" now was fired in another world
        GunType type = GunType.of(gun);
        ZcConfig.GunStats stats = type.stats();
        boolean auto = Guns.fullAuto(gun);
        double ms = System.nanoTime() / 1e6;
        if (auto ? ms < autoNextMs : now >= lastShot && now - lastShot < type.fireDelay()) {
            return;
        }
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        // a tube gun being loaded goes off at once when there's a shell chambered (step 1; 1.5 is onto an empty one);
        // a gun open to load has to be shut first
        boolean busy = reloading(gun) && !(type.tube && !type.opens && step(gun) == 1f);
        boolean dry = !g.mag() || g.rounds() <= 0 || g.jammed() || busy;
        if (auto && burstLeft < 0) {
            burstLeft = dry ? 0 : g.rounds();
        }
        if (held && (dry || burstLeft <= 0)) {
            return;     // one dry click for the pull, not one for every round it would have fired
        }
        lastShot = now;
        if (auto) {
            // a steady rate carries on from the shot that was due (so a late frame doesn't stretch the next gap), a
            // fresh pull starts from now
            double gap = 60000.0 / Math.max(60.0, stats.autoRpm);
            autoNextMs = (ms - autoNextMs > gap ? ms : autoNextMs) + gap;
            burstLeft--;
        }
        boolean aiming = player.isUsingItem() && GunType.isGun(player.getUseItem());
        ClientPlayNetworking.send(new CombatNet.Fire(aiming));
        shotMs = System.currentTimeMillis();
        if (dry) {
            return;     // the server answers with the dry click
        }
        // the spread blooms as the server's does (Guns.fire), so the crosshair's ring is where the next shot can go
        bloom = Guns.bloomed(bloom(type), auto, stats);
        bloomAt = System.nanoTime();
        boolean suppressed = Attachments.suppressed(gun);
        // at your ears, so it doesn't stay behind where you fired as you strafe
        // on full auto the pitch barely varies, so the string of shots sounds even
        float vary = auto ? 0.02f : 0.1f;
        EarSound.play(type.shot(suppressed, false), SoundSource.PLAYERS,
                suppressed ? (float) config.suppressedVolume : 1.4f,
                (suppressed ? (float) stats.suppressedPitch : 1f) * (1f - vary / 2 + player.getRandom().nextFloat() * vary));
        // the case comes out with the shot, here, rather than when the server's tick gets to it
        if (!type.manual() && !type.opens && type != GunType.CROSSBOW) {
            EarSound.play(Guns.casing(type), SoundSource.PLAYERS, 0.4f, 0.9f + player.getRandom().nextFloat() * 0.25f);
        }
        RifleView.fired(player, config.muzzleFlash);
        float kick = (aiming ? 1.1f : 2.0f) * (float) (stats.recoil * Attachments.recoilFactor(gun)
                * Rolls.multiplier(gun, Stat.STABILITY) * (auto ? stats.autoRecoil : 1.0));
        float now65 = kick * 0.65f;
        player.setXRot(Mth.clamp(player.getXRot() - now65, -90f, 90f));
        player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * kick * 0.5f);
        kickPending += kick - now65;
        recoverLeft += kick * 0.4f;
    }

    /** How far the spread has bloomed open right now with this gun (degrees). */
    public static double bloom(GunType type) {
        if (type != bloomGun) {
            bloomGun = type;
            bloom = 0;
        }
        return Guns.bloomNow(bloom, (System.nanoTime() - bloomAt) / 1e9, type.stats());
    }

    /** The rest of the kick lands next tick, then the muzzle settles part of the way back. */
    private static void recoil(LocalPlayer player) {
        if (kickPending > 0) {
            player.setXRot(Mth.clamp(player.getXRot() - kickPending, -90f, 90f));
            kickPending = 0;
        } else if (recoverLeft > 0.01f) {
            float step = Math.max(0.05f, recoverLeft * 0.25f);
            step = Math.min(step, recoverLeft);
            player.setXRot(Mth.clamp(player.getXRot() + step, -90f, 90f));
            recoverLeft -= step;
        } else {
            recoverLeft = 0;
        }
    }

    /** Mid-reload, the server puts the reload step in the gun's model data. */
    public static boolean reloading(ItemStack gun) {
        return step(gun) > 0;
    }

    private static float step(ItemStack gun) {
        CustomModelData data = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.floats().isEmpty() ? 0 : data.floats().get(0);
    }

    private CombatClient() {
    }
}
