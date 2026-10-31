package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Keys and clicks the client sends: fire, reload, shove, struggle. The server decides what happens. */
public final class CombatNet {
    /** Pull the trigger, while aiming or from the hip. */
    public record Fire(boolean aiming) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = new Type<>(ZomboidCraft.id("fire"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Fire::aiming, Fire::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One of the action keys: reload, shove, a struggle tap, the light, laser, magnifier or fire mode switch. */
    public record Action(int action) implements CustomPacketPayload {
        public static final int RELOAD = 0;
        public static final int SHOVE = 1;
        public static final int STRUGGLE = 2;
        public static final int RAIL = 3;
        public static final int LASER = 4;
        /** Flip the night vision on your headgear down or up. */
        public static final int NVG = 5;
        /** The radio's talk key pressed and let go. */
        public static final int RADIO_DOWN = 6;
        public static final int RADIO_UP = 7;
        /** Switch the gun between semi and full auto. */
        public static final int FIRE_MODE = 8;
        /** The camera went over the shoulder, or came back to first person: others see the gun raised or low. */
        public static final int SHOULDER_ON = 9;
        public static final int SHOULDER_OFF = 10;
        /** Swing the magnifier in behind the sight or aside. */
        public static final int MAGNIFIER = 11;
        /** The quick-use wheel let go over a belt slot: QUICK_USE + slot. */
        public static final int QUICK_USE = 16;
        public static final Type<Action> TYPE = new Type<>(ZomboidCraft.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Action::action, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Fire.TYPE, Fire.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Fire.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                com.zomboidcraft.survival.TimedActions.cancel(player, "You stop to shoot.");
                Guns.fire(player, payload.aiming());
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (payload.action() == Action.RELOAD || payload.action() == Action.SHOVE) {
                    com.zomboidcraft.survival.TimedActions.cancel(player, null);
                }
                switch (payload.action()) {
                    case Action.RELOAD -> Guns.reload(player);
                    case Action.SHOVE -> Shove.shove(player);
                    case Action.STRUGGLE -> Grabs.struggle(player);
                    case Action.RAIL -> Guns.toggleLight(player);
                    case Action.LASER -> Guns.toggleLaser(player);
                    case Action.FIRE_MODE -> Guns.toggleFireMode(player);
                    case Action.MAGNIFIER -> Guns.flipMagnifier(player);
                    case Action.SHOULDER_ON -> GunRaise.shoulder(player, true);
                    case Action.SHOULDER_OFF -> GunRaise.shoulder(player, false);
                    case Action.NVG -> com.zomboidcraft.gear.Headgear.flip(player);
                    case Action.RADIO_DOWN -> com.zomboidcraft.gear.Radios.key(player, true);
                    case Action.RADIO_UP -> com.zomboidcraft.gear.Radios.key(player, false);
                    default -> {
                        int slot = payload.action() - Action.QUICK_USE;
                        if (slot >= 0 && slot < net.minecraft.world.entity.player.Inventory.getSelectionSize()) {
                            com.zomboidcraft.survival.QuickUse.use(player, slot);
                        }
                    }
                }
            });
        });
    }

    private CombatNet() {
    }
}
