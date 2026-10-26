package com.zomboidcraft.voice;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.gear.RadioItem;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * What a voice sounds like coming out of a radio: the lows and highs cut away (narrower on a toy, wider on a military
 * set), pushed a little into distortion, with a hiss under it. Past the point where the crackle starts the hiss
 * climbs, the voice fades, drops out for a moment now and then and pops crackle through it, until at the edge of the
 * reach there's more static than voice. One filter per talker and listener, since it carries state from one 20 ms
 * packet of samples (48 kHz) to the next.
 */
final class RadioFilter {
    private static final float RATE = 48000f;

    private final Biquad high, low, hissHigh, hissLow;
    private final float drive;
    private final Random random = new Random();
    /** Packets left of the voice dropping out. */
    private int dropout;
    /** The tail of the last crackle. */
    private float pop;

    RadioFilter(@Nullable RadioItem.Type type) {
        float lo = 300, hi = 3000;
        float d = 1.8f;
        if (type == RadioItem.Type.TOY) {
            lo = 450;
            hi = 2500;
            d = 2.6f;
        } else if (type == RadioItem.Type.MILITARY) {
            lo = 250;
            hi = 3400;
            d = 1.2f;
        }
        high = Biquad.highPass(lo);
        low = Biquad.lowPass(hi);
        hissHigh = Biquad.highPass(lo);
        hissLow = Biquad.lowPass(hi);
        drive = d;
    }

    void reset() {
        high.reset();
        low.reset();
        hissHigh.reset();
        hissLow.reset();
        dropout = 0;
        pop = 0;
    }

    /** The voice through the radio, at {@code reach} (0 right beside the talker, 1 at the edge of their reach). */
    short[] process(short[] in, double reach, ZcConfig.Radio cfg) {
        double start = Math.max(0, Math.min(0.99, cfg.crackleStart));
        double edge = Math.max(0, Math.min(1, (reach - start) / (1 - start)));
        float hiss = (float) (Math.max(0, Math.min(0.1, cfg.hiss)) + Math.pow(edge, 1.5) * 0.28) * 2f;
        float gain = (float) (1 - 0.55 * edge * edge);
        if (dropout > 0) {
            dropout--;
            gain *= 0.06f;
        } else if (random.nextDouble() < edge * 0.12) {
            dropout = 1 + random.nextInt(3);
        }
        float squash = (float) Math.tanh(drive);
        short[] out = new short[in.length];
        for (int i = 0; i < in.length; i++) {
            float x = low.step(high.step(in[i] / 32768f));
            x = (float) Math.tanh(x * drive) / squash;
            float n = hissLow.step(hissHigh.step(random.nextFloat() * 2f - 1f));
            if (random.nextDouble() < edge * 0.0015) {
                pop = random.nextBoolean() ? 0.5f : -0.5f;
            }
            float y = x * gain + n * hiss + pop;
            pop *= 0.93f;
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(y * 32767f)));
        }
        return out;
    }

    /** A two-pole filter (the audio cookbook's), stepping one sample at a time. */
    private static final class Biquad {
        private final float b0, b1, b2, a1, a2;
        private float z1, z2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = (float) (b0 / a0);
            this.b1 = (float) (b1 / a0);
            this.b2 = (float) (b2 / a0);
            this.a1 = (float) (a1 / a0);
            this.a2 = (float) (a2 / a0);
        }

        static Biquad lowPass(float hz) {
            double w = 2 * Math.PI * hz / RATE, c = Math.cos(w), alpha = Math.sin(w) / (2 * 0.707);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        static Biquad highPass(float hz) {
            double w = 2 * Math.PI * hz / RATE, c = Math.cos(w), alpha = Math.sin(w) / (2 * 0.707);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha);
        }

        float step(float x) {
            float y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }

        void reset() {
            z1 = 0;
            z2 = 0;
        }
    }
}
