package com.zomboidcraft.client.zombie.body;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.ZombieTypes;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * How much of each zombie gets drawn, so a horde stays smooth: every clothing detail on only the nearest few,
 * most of it on the next lot, and the main shapes on the rest. Each body is a couple of thousand quads, built
 * afresh every frame, against vanilla's forty, so it is the count near you that decides how far detail reaches,
 * not only the distance. Worked out each tick from where the zombies stand, and from how big they look: through a
 * 4x scope a zombie 80 blocks off is drawn as one 20 blocks off would be.
 *
 * <p>Detail levels: 0 everything, 1 without the smallest pieces, 2 without the thin edges of the clothes, 3 the
 * body and only the biggest pieces. A shader pack's shadow pass gets level 3 (2 up close): a shadow shows no seams.
 */
public final class ZDetail {
    public static final int FULL = 0, MID = 1, LOW = 2, SIMPLE = 3;

    /** Squared distances to the camera within which levels 0 and 1 are drawn, this tick. */
    private static double fullSq = 16 * 16, midSq = 40 * 40, spriteSq = Double.MAX_VALUE;
    private static double[] dist = new double[256];
    /** How much the view is magnified (squared): 1 normally, the scope's power through one. */
    private static double zoomSq = 1;

    /** What was drawn since the last readout (main pass only): zombies at full, most and baked detail, and quads. */
    private static final int[] drawn = new int[4];
    private static long quads;
    /** Figures in the crowd past the real zombies (CrowdFx), last frame. */
    private static int crowd;
    private static int readoutTicks;

    private static boolean irisAsked;
    private static Object iris;
    private static Method shadowPass;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ZDetail::tick);
    }

    private static void tick(Minecraft mc) {
        Entity viewer = mc.getCameraEntity();
        if (mc.level == null || viewer == null) {
            return;
        }
        ZcConfig.Performance perf = ZcConfig.get().performance;
        double detail = Math.max(1.0, ZcConfig.get().look.zombieDetailDistance);
        Vec3 eye = viewer.getEyePosition();  // near enough the camera, even over the shoulder
        // through a scope or down the sights a zombie looks as near as it would without the zoom, so it gets the
        // detail it would have there: what decides is how big it is on screen
        float fov = mc.gameRenderer.mainCamera().getFov();
        double zoom = fov > 1 ? Math.max(1.0, mc.options.fov().get() / (double) fov) : 1.0;
        zoomSq = zoom * zoom;
        int n = 0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (ZombieTypes.isWalker(e)) {
                if (n == dist.length) {
                    dist = Arrays.copyOf(dist, n * 2);
                }
                dist[n++] = e.distanceToSqr(eye) / zoomSq;
            }
        }
        Arrays.sort(dist, 0, n);
        if (++readoutTicks >= 20) {
            readoutTicks = 0;
            if (perf.showStats && mc.player != null) {
                // a second's worth, shared out over the frames drawn in it
                double frames = Math.max(1, mc.getFps());
                mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(String.format(
                        "%d fps · zombies loaded %d · drawn full %d, most %d, baked %d, pictures %d, crowd %d · %,d pieces a frame",
                        mc.getFps(), n, Math.round(drawn[0] / frames), Math.round(drawn[1] / frames),
                        Math.round(drawn[2] / frames), Math.round(drawn[3] / frames), crowd, Math.round(quads / frames))));
            }
            Arrays.fill(drawn, 0);
            quads = 0;
        }
        fullSq = Math.min(detail * detail, nth(n, perf.fullDetailZombies));
        midSq = Math.min(detail * detail * 6.25, nth(n, perf.midDetailZombies));
        spriteSq = perf.spriteZombies ? Math.max(perf.spriteMinDistance * perf.spriteMinDistance, nth(n, perf.meshZombies))
                : Double.MAX_VALUE;
    }

    /** Whether a zombie this far (squared) from the camera is drawn as a picture (ZSprites), in either pass. */
    public static boolean sprite(double distanceSq) {
        return distanceSq / zoomSq >= spriteSq;
    }

    /** Just past the k-th nearest: everything nearer is inside it. */
    private static double nth(int n, int k) {
        if (k <= 0) {
            return 0;
        }
        return k >= n ? Double.MAX_VALUE : dist[k - 1] + 1e-3;
    }

    /** The level for a zombie this far (squared) from the camera, in the pass being drawn now. */
    public static int level(double distanceSq) {
        ZcConfig.Performance perf = ZcConfig.get().performance;
        distanceSq /= zoomSq;
        int level = distanceSq < fullSq ? FULL : distanceSq < midSq ? MID
                : distanceSq < perf.simpleZombieDistance * perf.simpleZombieDistance ? LOW : SIMPLE;
        if (perf.simpleZombieShadows && shadowPass()) {
            level = Math.max(level, distanceSq < fullSq ? LOW : SIMPLE);
        }
        return level;
    }

    /** Counts one zombie drawn in the main pass (0 full, 1 most, 2 baked or less, 3 a picture), for the stats readout. */
    public static void drew(int level, int pieces) {
        if (!shadowPass()) {
            drawn[Math.clamp(level, 0, 3)]++;
            quads += pieces;
        }
    }

    /** How many crowd figures (CrowdFx) were drawn this frame, for the stats readout. */
    public static void crowd(int figures) {
        crowd = figures;
    }

    /** Whether a shader pack (through Iris) is drawing its shadow map right now; false without Iris. */
    public static boolean shadowPass() {
        if (!irisAsked) {
            irisAsked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                iris = api.getMethod("getInstance").invoke(null);
                shadowPass = api.getMethod("isRenderingShadowPass");
            } catch (ReflectiveOperationException | LinkageError e) {
                shadowPass = null;
            }
        }
        if (shadowPass == null) {
            return false;
        }
        try {
            return (boolean) shadowPass.invoke(iris);
        } catch (ReflectiveOperationException | RuntimeException e) {
            shadowPass = null;
            return false;
        }
    }

    private ZDetail() {
    }
}
