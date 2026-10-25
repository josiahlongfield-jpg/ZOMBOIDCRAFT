package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.CombatNet;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.Grabs;
import com.zomboidcraft.combat.MeleeWeaponItem;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.combat.Weapons;
import com.zomboidcraft.combat.WeaponSpecs;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;

/**
 * The player's side of combat: R reloads, V shoves, left-click fires the rifle (with its kick
 * and its bang heard at once, before the server answers), jump struggles out of a grab, and an
 * empty endurance bar stops you sprinting.
 */
public final class CombatClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZomboidCraft.id("zomboidcraft"));
    public static final KeyMapping RELOAD = new KeyMapping("key.zomboidcraft.reload", InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping SHOVE = new KeyMapping("key.zomboidcraft.shove", InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping RAIL = new KeyMapping("key.zomboidcraft.rail", InputConstants.KEY_L, CATEGORY);

    private static long lastShot = -100;
    /** Muzzle climb still to apply next tick, and how much of it eases back afterwards. */
    private static float kickPending;
    private static float recoverLeft;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(RELOAD);
        KeyMappingHelper.registerKeyMapping(SHOVE);
        KeyMappingHelper.registerKeyMapping(RAIL);
        ClientPreAttackCallback.EVENT.register((client, player, clicks) -> {
            ItemStack hand = player.getMainHandItem();
            if (hand.is(ModItems.ASSAULT_RIFLE)) {
                if (clicks > 0 && ZcConfig.get().guns.enabled) {
                    pullTrigger(client, player, hand);
                }
                return true;    // the rifle never punches or digs
            }
            if (clicks > 0 && hand.getItem() instanceof MeleeWeaponItem && player.getAttackStrengthScale(0f) > 0.9f) {
                WeaponSpecs.WeaponSpec spec = Weapons.spec(hand);
                boolean heavy = spec != null && (spec.twoHanded() || spec.kind() == WeaponSpecs.Kind.HEAVY_BLUNT);
                client.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                        heavy ? ModSounds.SWING_HEAVY : ModSounds.SWING, SoundSource.PLAYERS, 0.6f,
                        0.9f + player.getRandom().nextFloat() * 0.2f, false);
            }
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(CombatClient::tick);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        while (RELOAD.consumeClick()) {
            if (player.getMainHandItem().is(ModItems.ASSAULT_RIFLE)) {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.RELOAD));
            }
        }
        while (SHOVE.consumeClick()) {
            ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.SHOVE));
        }
        while (RAIL.consumeClick()) {
            if (player.getMainHandItem().is(ModItems.ASSAULT_RIFLE)) {
                ClientPlayNetworking.send(new CombatNet.Action(CombatNet.Action.RAIL));
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
        recoil(player);
    }

    private static void pullTrigger(Minecraft client, LocalPlayer player, ItemStack gun) {
        long now = client.level.getGameTime();
        ZcConfig.Guns config = ZcConfig.get().guns;
        if (now - lastShot < config.fireDelayTicks) {
            return;
        }
        lastShot = now;
        boolean aiming = player.isUsingItem() && player.getUseItem().is(ModItems.ASSAULT_RIFLE);
        ClientPlayNetworking.send(new CombatNet.Fire(aiming));
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        if (!g.mag() || g.rounds() <= 0 || g.jammed() || reloading(gun)) {
            return;     // the server answers with the dry click
        }
        boolean suppressed = Attachments.suppressed(gun);
        client.level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(),
                suppressed ? ModSounds.RIFLE_SHOT_SUPPRESSED : ModSounds.RIFLE_SHOT, SoundSource.PLAYERS,
                suppressed ? (float) config.suppressedVolume : 1.4f, 0.95f + player.getRandom().nextFloat() * 0.1f, false);
        if (!suppressed && config.muzzleFlash) {
            RifleView.fired(player);
        }
        float kick = (aiming ? 1.1f : 2.0f) * (float) (config.recoil * Attachments.recoilFactor(gun));
        float now65 = kick * 0.65f;
        player.setXRot(Mth.clamp(player.getXRot() - now65, -90f, 90f));
        player.setYRot(player.getYRot() + (player.getRandom().nextFloat() - 0.5f) * kick * 0.5f);
        kickPending += kick - now65;
        recoverLeft += kick * 0.4f;
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
        CustomModelData data = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        return data != null && !data.floats().isEmpty() && data.floats().get(0) > 0;
    }

    private CombatClient() {
    }
}
