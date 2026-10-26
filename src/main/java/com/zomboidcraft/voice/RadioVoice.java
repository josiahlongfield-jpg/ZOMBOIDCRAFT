package com.zomboidcraft.voice;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.gear.Radios;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.PlayerDisconnectedEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Carries voices over the radios, through Simple Voice Chat's plugin API. The voice mod loads this (the "voicechat"
 * entrypoint in fabric.mod.json) only when it's installed, so nothing else in ZomboidCraft touches its classes.
 *
 * Every bit of a player's voice reaches the server as a microphone packet on the voice mod's own thread. If they're
 * talking on a radio ({@link Radios.Spot#talking}), it's decoded once, run through each listener's radio filter
 * (band-limited, hiss, breaking up near the edge of the reach: {@link RadioFilter}) and sent on: into a headset as a
 * sound only that listener hears, or out of their radio's speaker as a sound at them that everyone near hears.
 */
public class RadioVoice implements VoicechatPlugin {
    /** The volume slider in the voice mod's menu that turns radios up or down. */
    private static final String CATEGORY = "zc_radio";
    /** How long a talker or a listener's stream can go quiet before it's thrown away, in milliseconds. */
    private static final long IDLE = 30_000;

    /** Each talker's decoder: one per talker, since their voice arrives once and goes to everyone. */
    private final Map<UUID, Decoder> decoders = new ConcurrentHashMap<>();
    /** Each talker-to-listener stream: its filter and encoder, which both carry state from one packet to the next. */
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();
    private long lastSweep;

    private record Decoder(OpusDecoder opus, long[] last) {
    }

    private record Stream(UUID channel, RadioFilter filter, OpusEncoder opus, long[] last) {
    }

    @Override
    public String getPluginId() {
        return ZomboidCraft.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration events) {
        events.registerEvent(VoicechatServerStartedEvent.class, this::started);
        events.registerEvent(MicrophonePacketEvent.class, this::microphone);
        events.registerEvent(PlayerDisconnectedEvent.class, e -> forget(e.getPlayerUuid()));
    }

    private void started(VoicechatServerStartedEvent event) {
        VoicechatServerApi api = event.getVoicechat();
        VolumeCategory radios = api.volumeCategoryBuilder().setId(CATEGORY).setName("Radios")
                .setDescription("Voices coming over ZomboidCraft radios").setIcon(icon()).build();
        api.registerVolumeCategory(radios);
        ZomboidCraft.LOGGER.info("ZomboidCraft radios are carrying voices through Simple Voice Chat");
    }

    private void microphone(MicrophonePacketEvent event) {
        VoicechatConnection from = event.getSenderConnection();
        if (from == null || from.getPlayer() == null) {
            return;
        }
        Map<UUID, Radios.Spot> spots = Radios.spots();
        Radios.Spot talker = spots.get(from.getPlayer().getUuid());
        if (talker == null || talker.talking() < 0) {
            return;
        }
        VoicechatServerApi api = event.getVoicechat();
        MicrophonePacket packet = event.getPacket();
        long now = System.currentTimeMillis();
        short[] voice = decode(api, talker.id(), packet.getOpusEncodedData(), now);
        ZcConfig.Radio cfg = ZcConfig.get().radio;
        float speaker = (float) Math.max(1, cfg.speakerRange);
        for (Radios.Spot listener : spots.values()) {
            if (listener == talker || !listener.level().equals(talker.level()) || !listener.hears(talker.talking())) {
                continue;
            }
            double reach = talker.distanceTo(listener) / talker.range();
            if (reach > 1) {
                continue;
            }
            Stream stream = stream(api, talker, listener, now);
            byte[] out = voice == null ? new byte[0] : stream.opus().encode(stream.filter().process(voice, reach, cfg));
            if (listener.headset()) {
                VoicechatConnection to = api.getConnectionOf(listener.id());
                if (to != null) {
                    api.sendStaticSoundPacketTo(to, packet.staticSoundPacketBuilder().opusEncodedData(out)
                            .channelId(stream.channel()).category(CATEGORY).build());
                }
                continue;
            }
            // out of the listener's speaker: a sound at them for everyone close enough, except the talker (who'd hear
            // themselves a moment late)
            EntitySoundPacket loud = packet.entitySoundPacketBuilder().entityUuid(listener.id()).distance(speaker)
                    .opusEncodedData(out).channelId(stream.channel()).category(CATEGORY).build();
            for (Radios.Spot near : spots.values()) {
                if (near == talker || !near.level().equals(listener.level()) || near.distanceTo(listener) > speaker) {
                    continue;
                }
                VoicechatConnection to = api.getConnectionOf(near.id());
                if (to != null) {
                    api.sendEntitySoundPacketTo(to, loud);
                }
            }
            if (voice != null) {
                Radios.speakerPlayed(listener.id());
            }
        }
        if (now - lastSweep > 5000) {
            sweep(now);
        }
    }

    /** The talker's voice as samples, or null for the empty packet that ends a stretch of talking. */
    private short[] decode(VoicechatServerApi api, UUID talker, byte[] opus, long now) {
        Decoder d = decoders.computeIfAbsent(talker, k -> new Decoder(api.createDecoder(), new long[] {now}));
        if (now - d.last()[0] > 500) {
            d.opus().resetState();
        }
        d.last()[0] = now;
        if (opus == null || opus.length == 0) {
            return null;
        }
        return d.opus().decode(opus);
    }

    private Stream stream(VoicechatServerApi api, Radios.Spot talker, Radios.Spot listener, long now) {
        String key = talker.id() + ">" + listener.id();
        Stream s = streams.computeIfAbsent(key, k -> new Stream(
                UUID.nameUUIDFromBytes(("zomboidcraft radio " + k).getBytes(StandardCharsets.UTF_8)),
                new RadioFilter(talker.talkType()), api.createEncoder(OpusEncoderMode.VOIP), new long[] {now}));
        if (now - s.last()[0] > 500) {
            s.opus().resetState();
            s.filter().reset();
        }
        s.last()[0] = now;
        return s;
    }

    /** Lets go of decoders and streams nobody has used for a while. */
    private void sweep(long now) {
        lastSweep = now;
        decoders.entrySet().removeIf(e -> {
            boolean idle = now - e.getValue().last()[0] > IDLE;
            if (idle) {
                e.getValue().opus().close();
            }
            return idle;
        });
        streams.entrySet().removeIf(e -> {
            boolean idle = now - e.getValue().last()[0] > IDLE;
            if (idle) {
                e.getValue().opus().close();
            }
            return idle;
        });
    }

    private void forget(UUID player) {
        Decoder d = decoders.remove(player);
        if (d != null) {
            d.opus().close();
        }
        String id = player.toString();
        streams.entrySet().removeIf(e -> {
            boolean theirs = e.getKey().startsWith(id) || e.getKey().endsWith(id);
            if (theirs) {
                e.getValue().opus().close();
            }
            return theirs;
        });
    }

    /** The volume slider's icon: a little handheld radio, 16 by 16. */
    private static int[][] icon() {
        String[] rows = {
                "................",
                "....#...........",
                "....#...........",
                "....#...........",
                "....#..##.......",
                "...########.....",
                "...#oooooo#.....",
                "...#o####o#.....",
                "...#oooooo#.....",
                "...#.-.-.-#.....",
                "...#.-.-.-#.....",
                "...#.-.-.-#.....",
                "...#......#.....",
                "...########.....",
                "................",
                "................",
        };
        int[][] icon = new int[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                icon[x][y] = switch (rows[y].charAt(x)) {
                    case '#' -> 0xFFE8E4DA;
                    case 'o' -> 0xFFB37B2C;
                    case '-' -> 0xFF8A8678;
                    case '.' -> x > 3 && x < 10 && y > 5 && y < 13 ? 0xFF2A2B2D : 0;      // the body between the lines
                    default -> 0;
                };
            }
        }
        return icon;
    }
}
