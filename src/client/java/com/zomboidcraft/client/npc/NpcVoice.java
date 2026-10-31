package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.audio.SoundBuffer;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.npc.NpcNet;
import com.zomboidcraft.npc.Npcs;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.sound.sampled.AudioFormat;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;

/**
 * A character's own voice: the clips beside her lines.txt (config/zomboidcraft/npc/MODEL/voice/CLIP.ogg, made by
 * tools/npc/voice/voice.py) played from her as she says each line, on the Voice/Speech slider, with her jaw opening
 * on the loud parts. The words show in chat as subtitles (look.npcSubtitles). A line with no clip on this PC just shows.
 *
 * The clips aren't in any resource pack, so they go through the game's sound engine under made-up names
 * (zomboidcraft:npcvoice/MODEL/CLIP/MODIFIED) that SoundBufferLibraryMixin loads from the file instead. The file's
 * modified time is in the name, so a clip made again plays the new take.
 */
public final class NpcVoice {
    /** Loudness steps a second, for the jaw. */
    private static final int ENV_RATE = 30;
    /** How far (blocks) she's heard, as the server sends the line (NpcLines.HEARD). */
    private static final int REACH = 24;

    /** Each clip's loudness, 0..1 per 1/30 s, worked out as it's loaded. */
    private static final Map<String, float[]> ENVELOPES = new ConcurrentHashMap<>();
    /** Who's talking: entity id to what she's saying. */
    private static final Map<Integer, Speaking> SPEAKING = new HashMap<>();

    private static final class Speaking {
        final Clip clip;
        final String key;
        long started = -1;

        Speaking(Clip clip, String key) {
            this.clip = clip;
            this.key = key;
        }
    }

    private NpcVoice() {
    }

    static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NpcNet.Voice.TYPE, (payload, context) ->
                context.client().execute(() -> heard(payload)));
        ClientTickEvents.END_CLIENT_TICK.register(NpcVoice::tick);
    }

    private static void heard(NpcNet.Voice v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (ZcConfig.get().look.npcSubtitles && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("<" + v.speaker() + "> ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(v.text()).withStyle(ChatFormatting.WHITE)));
        }
        Speaking old = SPEAKING.remove(v.npc());
        if (old != null) {
            mc.getSoundManager().stop(old.clip);
        }
        Entity speaker = mc.level.getEntity(v.npc());
        Path file = file(v.model(), v.clip());
        if (speaker == null || file == null) {
            return;
        }
        long modified;
        try {
            modified = Files.getLastModifiedTime(file).toMillis();
        } catch (java.io.IOException e) {
            return;
        }
        String model = v.model().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        Identifier id = ZomboidCraft.id("npcvoice/" + model + "/" + v.clip() + "/" + modified);
        Clip clip = new Clip(id, speaker);
        SPEAKING.put(v.npc(), new Speaking(clip, Sound.SOUND_LISTER.idToFile(id).toString()));
        mc.getSoundManager().play(clip);
    }

    /** Her clip on this PC, or null: config/zomboidcraft/npc/MODEL/voice/CLIP.ogg. */
    static Path file(String model, String clip) {
        if (!clip.matches("[a-z0-9_]+") || model.contains("..") || model.contains("/") || model.contains("\\")) {
            return null;
        }
        Path p = Npcs.localFolder().resolve(model).resolve("voice").resolve(clip + ".ogg");
        return Files.isRegularFile(p) ? p : null;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) {
            SPEAKING.clear();
            return;
        }
        long now = Util.getMillis();
        SPEAKING.values().removeIf(s -> {
            boolean active = mc.getSoundManager().isActive(s.clip);
            if (active && s.started < 0) {
                s.started = now;
            }
            // done (or never started, a few seconds on): her mouth goes back to the game's own timing
            return s.clip.isStopped() || !active && (s.started >= 0 || s.clip.age++ > 100);
        });
    }

    /**
     * How open her mouth should be for the clip she's playing (0..1), or -1 if she isn't playing one (the game's
     * talking rhythm then, from how long the line would take to say).
     */
    public static float level(int entityId) {
        Speaking s = SPEAKING.get(entityId);
        if (s == null) {
            return -1;
        }
        float[] env = ENVELOPES.get(s.key);
        if (env == null || s.started < 0) {
            return 0;
        }
        float t = (Util.getMillis() - s.started) / 1000f * ENV_RATE;
        int i = (int) t;
        if (i >= env.length - 1) {
            return i >= env.length ? 0 : env[i];
        }
        return env[i] + (env[i + 1] - env[i]) * (t - i);
    }

    // ---- loading, for SoundBufferLibraryMixin ----

    /** Whether this sound file name is one of ours (sounds/npcvoice/MODEL/CLIP/MODIFIED.ogg). */
    public static boolean ours(Identifier path) {
        return path.getNamespace().equals(ZomboidCraft.MOD_ID) && path.getPath().startsWith("sounds/npcvoice/");
    }

    /** Decodes the clip's file into a buffer for the sound engine, and works out its loudness for the jaw on the way. */
    public static CompletableFuture<SoundBuffer> load(Identifier path) {
        return CompletableFuture.supplyAsync(() -> {
            String[] parts = path.getPath().split("/");
            Path file = parts.length >= 5 ? file(parts[2], parts[3]) : null;
            if (file == null) {
                throw new IllegalStateException("No voice clip for " + path);
            }
            try (InputStream in = Files.newInputStream(file); JOrbisAudioStream ogg = new JOrbisAudioStream(in)) {
                ByteBuffer pcm = ogg.readAll();
                AudioFormat format = ogg.getFormat();
                ENVELOPES.put(path.toString(), envelope(pcm.duplicate().order(ByteOrder.LITTLE_ENDIAN), format));
                return new SoundBuffer(pcm, format);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Couldn't read " + file + ": " + e, e);
            }
        }, Util.nonCriticalIoPool());
    }

    /** Loudness per 1/30 s, scaled so her loud syllables open her mouth fully and the quiet between words closes it. */
    static float[] envelope(ByteBuffer pcm, AudioFormat format) {
        int channels = Math.max(1, format.getChannels());
        int bytes = Math.max(1, format.getSampleSizeInBits() / 8);
        int frames = pcm.remaining() / (bytes * channels);
        int step = Math.max(1, Math.round(format.getSampleRate() / ENV_RATE));
        float[] env = new float[(frames + step - 1) / step];
        for (int w = 0; w < env.length; w++) {
            double sum = 0;
            int n = 0;
            for (int f = w * step; f < Math.min(frames, (w + 1) * step); f++) {
                int at = pcm.position() + f * bytes * channels;
                float s = bytes == 2 ? pcm.getShort(at) / 32768f : (pcm.get(at) & 0xff) / 128f - 1f;
                sum += s * s;
                n++;
            }
            env[w] = n == 0 ? 0 : (float) Math.sqrt(sum / n);
        }
        float[] sorted = env.clone();
        java.util.Arrays.sort(sorted);
        float loud = Math.max(1e-4f, sorted[(int) (sorted.length * 0.9f)]);
        for (int w = 0; w < env.length; w++) {
            float v = Math.min(1f, env[w] / loud);
            env[w] = v < 0.12f ? 0 : (float) Math.sqrt(v);
        }
        return env;
    }

    /** Her clip, following her as she moves. Resolves to a sound of its own rather than one from sounds.json. */
    static final class Clip extends AbstractTickableSoundInstance {
        private final Entity speaker;
        int age;

        Clip(Identifier id, Entity speaker) {
            super(SoundEvent.createVariableRangeEvent(id), SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.speaker = speaker;
            this.volume = 1f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            follow();
        }

        @Override
        public WeighedSoundEvents getOrResolve(SoundManager manager) {
            sound = new Sound(identifier, ConstantFloat.of(1f), ConstantFloat.of(1f), 1, Sound.Type.FILE, false, false, REACH);
            soundEvent = new WeighedSoundEvents(identifier, null);
            return soundEvent;
        }

        @Override
        public void tick() {
            if (speaker.isRemoved()) {
                stop();
                return;
            }
            follow();
        }

        private void follow() {
            x = speaker.getX();
            y = speaker.getEyeY();
            z = speaker.getZ();
        }
    }
}
