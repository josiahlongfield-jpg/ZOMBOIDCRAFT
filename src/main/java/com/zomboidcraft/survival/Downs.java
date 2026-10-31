package com.zomboidcraft.survival;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Going down instead of dying, in multiplayer only: a hit that would kill you puts you on the ground for a minute
 * (world config survival.downedSeconds), crawling, unable to fight or use anything, while you bleed out. Every
 * hit you take down there costs you time. A friend who gets to you with the interact key and the right kit brings
 * you back: adrenaline fastest, then a defibrillator, a bag-valve mask, an airway kit; a bandage only buys time.
 * Holding a stretcher or wheelchair, the key picks you up to carry instead (sneak to set you down). Alone, or once
 * the zombie infection takes you, you die as before.
 */
public final class Downs {
    /** Synced to everyone, so every client lays the downed player flat and the interact dot finds them. */
    public static final AttachmentType<Boolean> DOWNED = AttachmentRegistry.create(
            ZomboidCraft.id("downed"),
            builder -> builder.initializer(() -> false).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

    public record Help(int target) implements CustomPacketPayload {
        public static final Type<Help> TYPE = new Type<>(ZomboidCraft.id("revive"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Help> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Help::target, Help::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Revive kit, fastest first: ticks at First Aid 0. */
    private static final String[] KITS = {"adrenaline_pen", "portable_defib", "bag_valve_mask", "airway_kit"};
    private static final int[] KIT_TICKS = {40, 120, 160, 200};

    /** Players bleeding out right now, so the killing blow doesn't put them down again. */
    private static final java.util.Set<java.util.UUID> FINISHING = new java.util.HashSet<>();
    private static final java.util.Map<java.util.UUID, Integer> LAST_HIT = new java.util.HashMap<>();

    public static boolean downed(Player player) {
        return player.getAttachedOrElse(DOWNED, false);
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || !mayGoDown(player, source)) return true;
            Ailments.set(player, "downed", Math.max(10, ZcConfig.get().survival.downedSeconds));
            player.setAttached(DOWNED, true);
            player.setHealth(1f);
            player.stopRiding();
            player.sendOverlayMessage(Component.literal("You're down. Someone has to get to you.").withStyle(ChatFormatting.DARK_RED));
            for (ServerPlayer other : ((ServerLevel) player.level()).players()) {
                if (other != player && other.distanceToSqr(player) < 96 * 96) {
                    other.sendOverlayMessage(Component.literal(player.getName().getString() + " is down!").withStyle(ChatFormatting.RED));
                }
            }
            return false;
        });
        // down there, hits cost you time instead of health
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayer player) || !downed(player)) return true;
            if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            // one hit a second counts, however many hands are on you
            Integer last = LAST_HIT.get(player.getUUID());
            if (last != null && player.tickCount - last < 20 && player.tickCount >= last) return false;
            LAST_HIT.put(player.getUUID(), player.tickCount);
            Ailments.set(player, "downed", Math.max(1, Ailments.get(player, "downed") - 15));
            return false;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                downed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        UseItemCallback.EVENT.register((player, level, hand) -> downed(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        PayloadTypeRegistry.serverboundPlay().register(Help.TYPE, Help.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Help.TYPE, (payload, context) -> {
            ServerPlayer helper = context.player();
            helper.level().getServer().execute(() -> {
                if (helper.level().getEntity(payload.target()) instanceof ServerPlayer target && target != helper
                        && downed(target) && !downed(helper) && target.distanceToSqr(helper) < 3.5 * 3.5) {
                    help(helper, target);
                }
            });
        });
    }

    private static boolean mayGoDown(ServerPlayer player, DamageSource source) {
        if (FINISHING.contains(player.getUUID())) return false;
        if (!ZcConfig.get().survival.downed || player.isCreative() || player.isSpectator() || downed(player)) return false;
        if (player.level().getServer().getPlayerCount() < 2) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        // the zombie infection finishing you isn't something a friend can fix
        return !source.is(ModDamageTypes.INFECTION);
    }

    private static void help(ServerPlayer helper, ServerPlayer target) {
        if (MedSpecs.holds(helper, "stretcher") || MedSpecs.holds(helper, "wheelchair")) {
            if (target.getVehicle() == helper) {
                target.stopRiding();
            } else if (!target.startRiding(helper)) {
                helper.sendOverlayMessage(Component.literal("You can't get a grip on them here.").withStyle(ChatFormatting.GRAY));
            }
            return;
        }
        for (int i = 0; i < KITS.length; i++) {
            String kit = KITS[i];
            if (!MedSpecs.carries(helper, kit)) continue;
            TimedActions.start(helper, "revive:" + target.getId(), "Reviving " + target.getName().getString(),
                    MedSpecs.ticks(helper, KIT_TICKS[i]), h -> {
                        if (downed(target) && target.distanceToSqr(h) < 3.5 * 3.5 && MedSpecs.spend(h, kit)) revive(h, target);
                    });
            return;
        }
        if (Ailments.get(target, "stabilised") == 0 && (Treatment.find(helper, Treatment.Kind.BANDAGE) != null)) {
            TimedActions.start(helper, "stabilise:" + target.getId(), "Stopping " + target.getName().getString() + "'s bleeding",
                    MedSpecs.ticks(helper, 80), h -> {
                        var slot = Treatment.find(h, Treatment.Kind.BANDAGE);
                        if (!downed(target) || slot == null) return;
                        slot.getItem().shrink(1);
                        slot.setChanged();
                        Ailments.set(target, "stabilised", 1);
                        Ailments.set(target, "downed", Ailments.get(target, "downed") + 30);
                        h.sendOverlayMessage(Component.literal("That buys them half a minute.").withStyle(ChatFormatting.GRAY));
                    });
            return;
        }
        helper.sendOverlayMessage(Component.literal("You need adrenaline, a defibrillator, a bag-valve mask or an airway kit.")
                .withStyle(ChatFormatting.GRAY));
    }

    private static void revive(ServerPlayer helper, ServerPlayer target) {
        up(target);
        target.setHealth(6f);
        Skills.addXp(helper, Skill.FIRST_AID, 30);
        target.sendOverlayMessage(Component.literal(helper.getName().getString() + " brings you back.").withStyle(ChatFormatting.GREEN));
        helper.sendOverlayMessage(Component.literal("You bring " + target.getName().getString() + " back.").withStyle(ChatFormatting.GREEN));
    }

    private static void up(ServerPlayer player) {
        var m = Ailments.edit(player);
        m.remove("downed");
        m.remove("stabilised");
        Ailments.save(player, m);
        player.setAttached(DOWNED, false);
    }

    /** Once a second for everyone alive (SurvivalEvents): bleeding out on the ground. */
    static void tick(ServerPlayer player, int second) {
        // a stretcher or wheelchair slows whoever's pushing; sneaking sets them down
        if (player.getFirstPassenger() instanceof ServerPlayer carried && downed(carried)) {
            Medicine.effect(player, MobEffects.SLOWNESS, MedSpecs.holds(player, "wheelchair") ? 0 : 1);
            if (player.isShiftKeyDown()) carried.stopRiding();
        }
        int left = Ailments.get(player, "downed");
        if (left == 0) {
            if (downed(player)) player.setAttached(DOWNED, false);
            return;
        }
        if (!downed(player)) player.setAttached(DOWNED, true);
        Medicine.effect(player, MobEffects.SLOWNESS, 3);
        Medicine.effect(player, MobEffects.WEAKNESS, 4);
        if (player.isSprinting()) player.setSprinting(false);
        if (player.isUsingItem()) player.stopUsingItem();
        if (left % 15 == 0 && left > 1) {
            player.sendOverlayMessage(Component.literal(left + " seconds. Hold on.").withStyle(ChatFormatting.DARK_RED));
        }
        if (left <= 1) {
            up(player);
            ServerLevel level = (ServerLevel) player.level();
            FINISHING.add(player.getUUID());
            try {
                player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BLEEDING), Float.MAX_VALUE);
            } finally {
                FINISHING.remove(player.getUUID());
            }
            return;
        }
        Ailments.set(player, "downed", left - 1);
    }

    private Downs() {
    }
}
