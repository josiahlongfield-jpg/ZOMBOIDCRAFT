package com.zomboidcraft.client.creature;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.creature.HarvestmanEntity;
import com.zomboidcraft.creature.HarvestmanSounds;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How a Harvestman makes itself felt on this client before it's seen. Each one gives off a low hum that carries a fair
 * way and grows as you get nearer, until, close, it cuts out: the silence is the warning. When one has its attention on
 * you from somewhere you can't see it, your view darkens at the edges, slowly, and lets go once you've found it (or it
 * has looked away). And near one the animals and the night's insects go quiet (SoundEngineMixin asks {@link #hush}).
 */
public final class HarvestmanPresence {
    private static final Identifier UNEASE = ZomboidCraft.id("hud/unease");
    /** The hum: heard from this far, at full by FULL, fading out again inside QUIET to nothing at SILENT. */
    private static final double HEARD = 52, FULL = 26, QUIET = 20, SILENT = 11;
    private static final Map<Integer, Hum> HUMS = new HashMap<>();
    private static float unease, hushK;
    private static long started;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HarvestmanPresence::tick);
        HudElementRegistry.addLast(ZomboidCraft.id("harvestman_unease"), HarvestmanPresence::hud);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            HUMS.values().forEach(Hum::end);
            HUMS.clear();
            unease = hushK = 0;
            return;
        }
        double nearest = 1e9;
        boolean uneasy = false;
        for (HarvestmanEntity h : mc.level.getEntitiesOfClass(HarvestmanEntity.class, player.getBoundingBox().inflate(HEARD + 4))) {
            if (!h.isAlive()) {
                continue;
            }
            double d = h.position().distanceTo(player.position());
            nearest = Math.min(nearest, d);
            if (d < HEARD && !HUMS.containsKey(h.getId())) {
                Hum hum = new Hum(h);
                HUMS.put(h.getId(), hum);
                mc.getSoundManager().play(hum);
            }
            if (h.watchingId() == player.getId() && d < 40 && !sees(player, h)) {
                uneasy = true;
            }
        }
        HUMS.values().removeIf(Hum::isStopped);
        // slow to come on (a minute's creeping feeling compressed to a few seconds), quicker to lift
        unease = Mth.approach(unease, uneasy && !player.isSpectator() ? 1f : 0f, uneasy ? 1 / 70f : 1 / 35f);
        float hushWant = nearest < 32 ? 1f : nearest > 44 ? 0f : (float) ((44 - nearest) / 12);
        hushK = Mth.approach(hushK, hushWant, 1 / 50f);
    }

    /** Can this player see it now: looking near enough its way, with nothing in between? */
    private static boolean sees(LocalPlayer player, HarvestmanEntity h) {
        Vec3 eye = player.getEyePosition(), c = h.centre(), to = c.subtract(eye);
        double d = to.length();
        if (d < 1e-3 || player.getLookAngle().dot(to.scale(1 / d)) < Math.cos(Math.toRadians(35))) {
            return false;
        }
        return player.level().clip(new ClipContext(eye, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    /** The edges of your view closing in, breathing slightly. */
    private static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        if (unease < 0.01f) {
            return;
        }
        if (started == 0) {
            started = System.nanoTime();
        }
        float t = (System.nanoTime() - started) / 1e9f;
        float a = unease * (0.62f + 0.08f * Mth.sin(t * 1.3f));
        g.blitSprite(RenderPipelines.GUI_TEXTURED, UNEASE, 0, 0, g.guiWidth(), g.guiHeight(), ARGB.multiplyAlpha(0xFFFFFFFF, a));
    }

    /**
     * How much of a sound is left near a Harvestman: animals and the night's insects fall silent (0 when it's close),
     * everything else is untouched (1).
     */
    public static float hush(SoundInstance sound) {
        if (hushK < 0.01f) {
            return 1f;
        }
        String path = sound.getIdentifier().getPath();
        boolean creature = path.equals("amb.night") || path.contains("bird") || path.contains("cricket") || path.contains("insect")
                || path.startsWith("entity.") && (path.contains("wolf") || path.contains("dog") || path.contains("cat")
                || path.contains("chicken") || path.contains("parrot") || path.contains("fox") || path.contains("bat")
                || path.contains("frog") || path.contains("bee") || path.contains("cow") || path.contains("pig")
                || path.contains("sheep") || path.contains("horse") || path.contains("rabbit") || path.contains("goat"));
        return creature ? 1f - hushK : 1f;
    }

    /** One Harvestman's hum, following it, its loudness worked out here from how far it is. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final HarvestmanEntity h;

        Hum(HarvestmanEntity h) {
            super(HarvestmanSounds.HUM, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.h = h;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.pitch = 0.94f + (h.getId() % 7) * 0.015f;
            this.attenuation = SoundInstance.Attenuation.NONE;
            Vec3 c = h.centre();
            this.x = c.x;
            this.y = c.y;
            this.z = c.z;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (h.isRemoved() || !h.isAlive() || mc.player == null || h.level() != mc.player.level()) {
                stop();
                return;
            }
            Vec3 c = h.centre();
            x = c.x;
            y = c.y;
            z = c.z;
            double d = c.distanceTo(mc.player.getEyePosition());
            if (d > HEARD + 6) {
                stop();
                return;
            }
            double far = Mth.clamp((HEARD - d) / (HEARD - FULL), 0, 1), near = Mth.clamp((d - SILENT) / (QUIET - SILENT), 0, 1);
            // it holds its breath, too, as it rears to strike
            float want = (float) (0.5 * far * near * (1 - h.rear()));
            volume = Mth.approach(volume, Math.max(0.001f, want), 0.03f);
        }

        void end() {
            stop();
        }
    }

    private HarvestmanPresence() {
    }
}
