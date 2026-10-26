package com.zomboidcraft.gear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Radios. Hold the talk key with a radio in your hand (or anywhere on you, wearing a comms headset) and everyone with a
 * radio switched on and set to the same channel, inside your radio's reach, hears you: from their radio's speaker,
 * which the players and zombies round them hear too, or through their headset, which only they hear.
 *
 * The voices themselves go through the voice chat mod (voice/RadioVoice, loaded only when it's installed). It runs on
 * its own thread, so each server tick leaves it a snapshot of where everyone is and what their radios are doing
 * ({@link #spots}). The clicks and the burst of static when someone keys up and lets go are ordinary game sounds, so
 * they work without the voice mod too.
 *
 * A radio's channel and whether it's on are its {@link #TUNING}; its battery is {@link Headgear#CHARGE}, in seconds,
 * like the night vision's.
 */
public final class Radios {
    public record Tuning(int channel, boolean on) {
        public static final Codec<Tuning> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("channel", 1).forGetter(Tuning::channel),
                Codec.BOOL.optionalFieldOf("on", false).forGetter(Tuning::on)).apply(i, Tuning::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tuning> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tuning::channel, ByteBufCodecs.BOOL, Tuning::on, Tuning::new);
    }

    /** A radio nobody has touched: channel 1, switched off. */
    public static final Tuning DEFAULT = new Tuning(1, false);

    public static final DataComponentType<Tuning> TUNING = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("radio"), DataComponentType.<Tuning>builder()
                    .persistent(Tuning.CODEC).networkSynchronized(Tuning.STREAM_CODEC).build());

    /**
     * Where a player was at the last server tick and what their radios were doing: the channels they hear, whether
     * that's through a headset, and the channel they're talking on (-1 when they aren't) with the reach and kind of
     * the radio they're talking on.
     */
    public record Spot(UUID id, ResourceKey<Level> level, double x, double y, double z, int[] listening, boolean headset,
                       int talking, double range, RadioItem.Type talkType) {
        public boolean hears(int channel) {
            for (int c : listening) {
                if (c == channel) {
                    return true;
                }
            }
            return false;
        }

        public double distanceTo(Spot other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private static final int[] NONE = new int[0];

    /** Everyone online, as of the last server tick. Replaced whole each tick, so the voice thread can read it. */
    private static volatile Map<UUID, Spot> spots = Map.of();
    /** Who's holding the talk key down with a radio to talk on. */
    private static final Set<UUID> KEYED = ConcurrentHashMap.newKeySet();
    /** Seconds each player has talked since the battery was last written. */
    private static final Map<UUID, Integer> TALKED = new HashMap<>();
    /** When each player's speaker last drew the zombies, so a long call doesn't call every tick. */
    private static final Map<UUID, Long> LAST_NOISE = new ConcurrentHashMap<>();
    private static volatile MinecraftServer server;
    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Radios::tick);
    }

    public static Map<UUID, Spot> spots() {
        return spots;
    }

    // ---- reading and changing radios ----

    public static Tuning tuning(ItemStack radio) {
        return radio.getOrDefault(TUNING, DEFAULT);
    }

    /** A fresh AA's life in a switched-on radio, in seconds. */
    public static int fullCharge() {
        return Math.max(1, (int) Math.round(ZcConfig.get().radio.batteryMinutes * 60));
    }

    /** Seconds left; a radio nobody has had yet counts as full. */
    public static int charge(ItemStack radio) {
        return radio.getOrDefault(Headgear.CHARGE, fullCharge());
    }

    public static float chargeFraction(ItemStack radio) {
        return Mth.clamp(charge(radio) / (float) fullCharge(), 0f, 1f);
    }

    public static boolean hasPower(ItemStack radio) {
        return !ZcConfig.get().radio.batteries || charge(radio) > 0;
    }

    /** A radio that's switched on with battery left. */
    public static boolean live(ItemStack stack) {
        return stack.getItem() instanceof RadioItem && tuning(stack).on() && hasPower(stack);
    }

    public static String chargeText(ItemStack radio) {
        int left = charge(radio);
        if (left <= 0) {
            return "Battery dead";
        }
        return "Battery " + Math.round(chargeFraction(radio) * 100) + "%, "
                + (left >= 5400 ? Math.round(left / 360f) / 10f + " h left" : left >= 90 ? Math.round(left / 60f) + " min left" : left + " s left");
    }

    /**
     * The radio a player talks on: a live one in either hand, or, wearing a comms headset (or with holdToTalk off), any
     * live radio they carry. Empty if there's none. Used on the client too, to know when to open the mic.
     */
    public static ItemStack talkRadio(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (live(stack)) {
                return stack;
            }
        }
        if (Headgear.headset(player.getItemBySlot(EquipmentSlot.HEAD)) != null || !ZcConfig.get().radio.holdToTalk) {
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (live(inventory.getItem(i))) {
                    return inventory.getItem(i);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Why there's nothing to talk on, for the action bar. */
    private static String why(Player player) {
        boolean headset = Headgear.headset(player.getItemBySlot(EquipmentSlot.HEAD)) != null;
        ItemStack held = player.getMainHandItem().getItem() instanceof RadioItem ? player.getMainHandItem() : player.getOffhandItem();
        if (!(held.getItem() instanceof RadioItem) && headset) {
            held = firstRadio(player);
        }
        if (!(held.getItem() instanceof RadioItem)) {
            return headset || !ZcConfig.get().radio.holdToTalk ? "You've no radio on you." : "Hold a radio to talk on it, or wear a comms headset.";
        }
        if (!tuning(held).on()) {
            return "Your radio's switched off. Right-click it in your inventory to turn it on.";
        }
        return "Your radio's battery is dead.";
    }

    private static ItemStack firstRadio(Player player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof RadioItem) {
                return inventory.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    /** Right-click with a radio: the next channel up, or down, round to the other end. */
    public static void stepChannel(ServerPlayer player, ItemStack radio, int step) {
        if (!(radio.getItem() instanceof RadioItem item)) {
            return;
        }
        Tuning t = tuning(radio);
        int channels = item.type().channels();
        int channel = Math.floorMod(Math.min(t.channel(), channels) - 1 + step, channels) + 1;
        radio.set(TUNING, new Tuning(channel, t.on()));
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.RADIO_CHANNEL,
                SoundSource.PLAYERS, 0.45f, t.on() ? 1f : 0.9f);
        player.sendOverlayMessage(Component.literal("Channel " + channel + " of " + channels
                + (t.on() ? "" : ". It's switched off: right-click it in your inventory to turn it on.")).withStyle(ChatFormatting.GRAY));
    }

    /** Right-click a radio in the inventory with nothing: switches it on or off. */
    public static void togglePower(ItemStack radio, Player player) {
        Tuning t = tuning(radio);
        boolean on = !t.on();
        radio.set(TUNING, new Tuning(t.channel(), on));
        player.playSound(on ? ModSounds.RADIO_POWER_ON : ModSounds.RADIO_POWER_OFF, 0.6f, 1f);
        if (player instanceof ServerPlayer sp) {
            sp.sendOverlayMessage(Component.literal(!on ? "Radio off." : hasPower(radio)
                    ? "Radio on, channel " + t.channel() + "." : "Radio on, but the battery's dead.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Puts a fresh battery in a radio, using one from {@code battery}. The old one is thrown away. */
    public static boolean swapBattery(ItemStack radio, ItemStack battery, Player player) {
        if (!(radio.getItem() instanceof RadioItem) || !(battery.getItem() instanceof BatteryItem)) {
            return false;
        }
        radio.set(Headgear.CHARGE, fullCharge());
        battery.consume(1, player);
        player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.5f, 1.6f);
        return true;
    }

    // ---- talking ----

    /** The talk key went down or came up. */
    public static void key(ServerPlayer player, boolean down) {
        UUID id = player.getUUID();
        if (!down) {
            if (KEYED.remove(id)) {
                ItemStack radio = talkRadio(player);
                if (!radio.isEmpty()) {
                    // the burst of static on everyone else's radio as you let go
                    others(player, radio, ModSounds.RADIO_SQUELCH);
                }
            }
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        ItemStack radio = talkRadio(player);
        if (radio.isEmpty()) {
            player.sendOverlayMessage(Component.literal(why(player)).withStyle(ChatFormatting.GRAY));
            return;
        }
        KEYED.add(id);
        others(player, radio, ModSounds.RADIO_KEY_UP);
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.RADIO_PTT, SoundSource.PLAYERS, 0.35f, 1f);
        player.sendOverlayMessage(Component.literal("Talking on channel " + tuning(radio).channel()).withStyle(ChatFormatting.GRAY));
    }

    /** Plays a radio sound on every other radio that hears this one: from its speaker, or in its owner's headset. */
    private static void others(ServerPlayer from, ItemStack radio, SoundEvent sound) {
        if (!(radio.getItem() instanceof RadioItem item)) {
            return;
        }
        int channel = tuning(radio).channel();
        double range = item.type().range();
        for (ServerPlayer to : from.level().getServer().getPlayerList().getPlayers()) {
            if (to == from || to.level() != from.level() || to.isSpectator() || to.distanceTo(from) > range || !hears(to, channel)) {
                continue;
            }
            fromRadio(to, sound, 0.8f);
        }
    }

    private static boolean hears(Player player, int channel) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (live(stack) && tuning(stack).channel() == channel) {
                return true;
            }
        }
        return false;
    }

    /** A sound out of a player's radio: into their headset if they wear one, otherwise out loud where they stand. */
    private static void fromRadio(ServerPlayer to, SoundEvent sound, float volume) {
        if (Headgear.headset(to.getItemBySlot(EquipmentSlot.HEAD)) != null) {
            to.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                    to.getX(), to.getEyeY(), to.getZ(), volume * 0.6f, 1f, to.getRandom().nextLong()));
            return;
        }
        float loud = (float) Math.max(0.1, ZcConfig.get().radio.speakerRange / 16.0) * volume;
        to.level().playSound(null, to.getX(), to.getY() + 1.0, to.getZ(), sound, SoundSource.PLAYERS, loud, 1f);
        speakerPlayed(to.getUUID());
    }

    /**
     * A radio's speaker just played something: the zombies round it hear, at most once a second. Safe to call from the
     * voice chat thread.
     */
    public static void speakerPlayed(UUID listener) {
        long now = System.currentTimeMillis();
        Long last = LAST_NOISE.get(listener);
        if (last != null && now - last < 1000) {
            return;
        }
        LAST_NOISE.put(listener, now);
        MinecraftServer srv = server;
        double radius = ZcConfig.get().radio.speakerNoise;
        if (srv == null || radius <= 0) {
            return;
        }
        srv.execute(() -> {
            ServerPlayer player = srv.getPlayerList().getPlayer(listener);
            if (player != null) {
                Noise.emit((ServerLevel) player.level(), player.position(), radius);
            }
        });
    }

    // ---- every tick ----

    private static void tick(MinecraftServer srv) {
        server = srv;
        ticks++;
        ZcConfig.Radio cfg = ZcConfig.get().radio;
        boolean second = ticks % 20 == 0;
        int every = Math.max(1, cfg.saveEverySeconds);
        boolean write = ticks % (20 * every) == 0;
        Map<UUID, Spot> next = new HashMap<>();
        for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            ItemStack talk = KEYED.contains(id) ? talkRadio(player) : ItemStack.EMPTY;
            if (talk.isEmpty() && KEYED.remove(id)) {
                player.sendOverlayMessage(Component.literal(why(player)).withStyle(ChatFormatting.GRAY));
            }
            List<Integer> listening = new ArrayList<>();
            Inventory inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (live(stack) && !listening.contains(tuning(stack).channel())) {
                    listening.add(tuning(stack).channel());
                }
            }
            boolean spectator = player.isSpectator();
            RadioItem.Type type = talk.getItem() instanceof RadioItem r ? r.type() : null;
            next.put(id, new Spot(id, player.level().dimension(), player.getX(), player.getEyeY(), player.getZ(),
                    spectator ? NONE : listening.stream().mapToInt(Integer::intValue).toArray(),
                    Headgear.headset(player.getItemBySlot(EquipmentSlot.HEAD)) != null,
                    type == null || spectator ? -1 : tuning(talk).channel(), type == null ? 0 : type.range(), type));
            if (cfg.batteries && !player.isCreative()) {
                if (second && type != null) {
                    TALKED.merge(id, 1, Integer::sum);
                }
                if (write) {
                    drain(player, every, TALKED.getOrDefault(id, 0), cfg);
                    TALKED.remove(id);
                }
            }
            if (second) {
                settleFound(player);
            }
        }
        spots = Map.copyOf(next);
        if (ticks % 200 == 0) {
            KEYED.retainAll(next.keySet());
            TALKED.keySet().retainAll(next.keySet());
            LAST_NOISE.keySet().retainAll(next.keySet());
        }
    }

    /** Takes the battery used since the last write off every switched-on radio a player carries. */
    private static void drain(ServerPlayer player, int seconds, int talked, ZcConfig.Radio cfg) {
        ItemStack talk = talkRadio(player);
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!live(stack)) {
                continue;
            }
            int used = seconds + (stack == talk ? (int) Math.round(talked * Math.max(0, cfg.talkDrain - 1)) : 0);
            int left = Math.max(0, charge(stack) - used);
            stack.set(Headgear.CHARGE, left);
            inventory.setChanged();
            if (left == 0) {
                player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.RADIO_POWER_OFF,
                        SoundSource.PLAYERS, 0.4f, 0.8f);
                player.sendOverlayMessage(Component.literal("Your " + stack.getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
                        + "'s battery has died.").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    /** Radios found in the world have had a battery sitting in them: part used. */
    private static void settleFound(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof RadioItem && !stack.has(Headgear.CHARGE)) {
                stack.set(Headgear.CHARGE, foundCharge(player));
                inventory.setChanged();
            }
        }
    }

    private static int foundCharge(ServerPlayer player) {
        if (player.isCreative()) {
            return fullCharge();
        }
        ZcConfig.Radio cfg = ZcConfig.get().radio;
        double lo = Mth.clamp(Math.min(cfg.foundChargeMin, cfg.foundChargeMax), 0, 1);
        double hi = Mth.clamp(Math.max(cfg.foundChargeMin, cfg.foundChargeMax), 0, 1);
        return (int) Math.round(fullCharge() * (lo + player.getRandom().nextDouble() * (hi - lo)));
    }

    private Radios() {
    }
}
