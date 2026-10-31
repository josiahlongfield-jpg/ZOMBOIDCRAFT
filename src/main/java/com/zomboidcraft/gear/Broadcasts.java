package com.zomboidcraft.gear;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.creature.HarvestmanSounds;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The outbreak on the radio, as Zomboid tells it: stations you tune to, whose words come up as captions with static
 * under them (no voices, Josiah's pick). WGHN news grows more frightened over the first nine days and then dies
 * mid-sentence; the Automated Emergency Broadcast System loops after that until its battery gives out; the military
 * frequency gives orders and then a door code; four hams each tell a story over a few weeks. The scripts are
 * data/zomboidcraft/broadcasts.json.
 *
 * Stations sit after a radio's talk channels when you step through them, on channel numbers from {@link #FIRST}, so
 * they never meet a talk channel. Everything is worked out from the overworld's game time, so everyone tuned to a
 * station reads the same line at the same moment, and nothing needs saving.
 */
public final class Broadcasts {
    /** A line for the caption box. */
    public record Caption(String station, int colour, String text) implements CustomPacketPayload {
        public static final Type<Caption> TYPE = new Type<>(ZomboidCraft.id("broadcast_caption"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Caption> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Caption::station, ByteBufCodecs.INT, Caption::colour,
                ByteBufCodecs.STRING_UTF8, Caption::text, Caption::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    record Segment(int from, int to, int gap, boolean aebs, List<String> lines) {
    }

    public record Station(String id, String name, int colour, Set<RadioItem.Type> radios, List<Segment> segments) {
        Segment at(long day) {
            Segment best = null;
            for (Segment s : segments) {
                if (day >= s.from() && day <= s.to() && (best == null || s.from() > best.from())) {
                    best = s;
                }
            }
            return best;
        }
    }

    /** The first station's channel number; talk channels stay well below it. */
    public static final int FIRST = 1001;

    private static List<Station> stations = List.of();

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Caption.TYPE, Caption.CODEC);
        stations = load();
        ServerTickEvents.END_SERVER_TICK.register(Broadcasts::tick);
    }

    // ---- stations and channels ----

    /** The station on a channel, or null for a talk channel. */
    public static Station station(int channel) {
        int i = channel - FIRST;
        return i >= 0 && i < stations.size() ? stations.get(i) : null;
    }

    /** The station channels a radio picks up, in order. None when broadcasts are off. */
    public static List<Integer> channels(RadioItem.Type type) {
        List<Integer> out = new ArrayList<>();
        if (!ZcConfig.get().broadcast.enabled) {
            return out;
        }
        for (int i = 0; i < stations.size(); i++) {
            if (stations.get(i).radios().contains(type)) {
                out.add(FIRST + i);
            }
        }
        return out;
    }

    /** "Channel 4 of 22", or a station's name. */
    public static String label(int channel, RadioItem.Type type) {
        Station s = station(channel);
        return s != null ? s.name() : "Channel " + channel + " of " + type.channels();
    }

    // ---- playing ----

    private static void tick(MinecraftServer server) {
        ZcConfig.Broadcast cfg = ZcConfig.get().broadcast;
        if (!cfg.enabled || stations.isEmpty()) {
            return;
        }
        long time = server.overworld().getGameTime();
        int lineTicks = Math.max(20, (int) Math.round(cfg.lineSeconds * 20));
        if (time % lineTicks != 0) {
            return;
        }
        long day = time / 24000 + cfg.dayOffset;
        long slot = time / lineTicks;
        for (int i = 0; i < stations.size(); i++) {
            Station station = stations.get(i);
            Segment seg = station.at(day);
            if (seg == null || seg.lines().isEmpty() || seg.aebs() && day > cfg.aebsLastDay) {
                continue;
            }
            long cycle = seg.lines().size() + Math.max(1, (seg.gap() * 20L + lineTicks - 1) / lineTicks);
            // each station's loop starts at its own point, so they don't all speak at once
            long at = Math.floorMod(slot + i * 7L, cycle);
            if (at < seg.lines().size()) {
                play(server, FIRST + i, station, seg.lines().get((int) at));
            }
        }
    }

    private static void play(MinecraftServer server, int channel, Station station, String line) {
        List<SoundEvent> sounds = new ArrayList<>();
        String text = line;
        for (String[] tag : new String[][]{{"[EAS]", "eas"}, {"[EOM]", "eom"}, {"[HUM]", "hum"}, {"[CUT]", "cut"}}) {
            if (text.contains(tag[0])) {
                text = text.replace(tag[0], "");
                switch (tag[1]) {
                    case "eas" -> sounds.add(ModSounds.RADIO_EAS_ATTENTION);
                    case "eom" -> sounds.add(ModSounds.RADIO_EAS_EOM);
                    case "hum" -> sounds.add(HarvestmanSounds.HUM);
                    default -> sounds.add(ModSounds.RADIO_SQUELCH);
                }
            }
        }
        text = text.strip();
        if (!text.isEmpty()) {
            sounds.add(0, ModSounds.RADIO_STATIC);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !tunedTo(player, channel, station)) {
                continue;
            }
            if (!text.isEmpty()) {
                ServerPlayNetworking.send(player, new Caption(station.name(), station.colour(), text));
            }
            for (SoundEvent sound : sounds) {
                Radios.fromRadio(player, sound, sound == ModSounds.RADIO_STATIC ? 0.35f : 0.8f);
            }
        }
    }

    private static boolean tunedTo(ServerPlayer player, int channel, Station station) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof RadioItem radio && Radios.live(stack) && Radios.tuning(stack).channel() == channel
                    && station.radios().contains(radio.type())) {
                return true;
            }
        }
        return false;
    }

    // ---- the scripts ----

    private static List<Station> load() {
        List<Station> out = new ArrayList<>();
        try (InputStream in = Broadcasts.class.getResourceAsStream("/data/zomboidcraft/broadcasts.json")) {
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("stations")) {
                JsonObject o = e.getAsJsonObject();
                Set<RadioItem.Type> radios = EnumSet.noneOf(RadioItem.Type.class);
                for (JsonElement r : o.getAsJsonArray("radios")) {
                    radios.add(RadioItem.Type.valueOf(r.getAsString().toUpperCase(Locale.ROOT)));
                }
                List<Segment> segments = new ArrayList<>();
                for (JsonElement se : o.getAsJsonArray("segments")) {
                    JsonObject s = se.getAsJsonObject();
                    List<String> lines = new ArrayList<>();
                    s.getAsJsonArray("lines").forEach(l -> lines.add(l.getAsString()));
                    segments.add(new Segment(s.get("from").getAsInt(), s.get("to").getAsInt(), s.get("gap").getAsInt(),
                            s.has("until") && s.get("until").getAsString().equals("aebs"), List.copyOf(lines)));
                }
                out.add(new Station(o.get("id").getAsString(), o.get("name").getAsString(),
                        Integer.parseInt(o.get("colour").getAsString().substring(1), 16), radios, List.copyOf(segments)));
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read the broadcasts", e);
        }
        return List.copyOf(out);
    }

    private Broadcasts() {
    }
}
