package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Impacts;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a shot landing looks like, on this computer ({@link Impacts} says where): a bullet hole in the block with a
 * puff of its dust and chips off it (sparks off metal), and on flesh a spray of blood drops that fly on along the
 * shot, fall, and spot whatever they land on, with a splash on the wall or floor behind. Holes and splashes are flat
 * marks laid on the block face, kept to the block, gone when the block changes or their time's up.
 */
public final class ImpactFx {
    private static final Identifier TEXTURE = ZomboidCraft.id("textures/effect/impacts.png");
    /** The texture's cells: two holes, two splashes, solid blood. */
    private static final int CELLS = 5, HOLE = 0, SPLASH = 2, SOLID = 4;
    private static final float GRAVITY = 24f, DRAG = 0.6f;
    private static final int MAX_DROPS = 400;
    private static final Random RANDOM = new Random();

    /** A mark on a block face: centred at x,y,z on it, half wide, cell and flips for variety. */
    private static final class Decal {
        BlockPos pos;
        BlockState state;
        Direction face;
        double x, y, z;
        float half;
        int cell;
        boolean flipU, flipV;
        float age, life;
        float lift;
    }

    /** A drop of blood in the air. */
    private static final class Drop {
        double x, y, z, vx, vy, vz;
        float size, age;
        boolean marks;
    }

    private static final List<Decal> DECALS = new ArrayList<>();
    private static final List<Drop> DROPS = new ArrayList<>();
    private static ClientLevel level;
    private static long lastFrame;
    private static int stack;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Impacts.Impact.TYPE, (payload, context) ->
                context.client().execute(() -> receive(payload)));
        LevelRenderEvents.COLLECT_SUBMITS.register(ImpactFx::draw);
    }

    private static void receive(Impacts.Impact impact) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl == null) {
            return;
        }
        ZcConfig.Guns config = ZcConfig.get().guns;
        Vec3 p = impact.pos(), d = impact.dir().lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : impact.dir().normalize();
        switch (impact.kind()) {
            case Impacts.HOLE -> {
                if (!config.bulletHoles) {
                    return;
                }
                Direction face = Direction.from3DDataValue(impact.face());
                BlockState state = Block.stateById(impact.block());
                Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                com.zomboidcraft.combat.Surface surface = com.zomboidcraft.combat.Surface.of(state);
                if (surface == com.zomboidcraft.combat.Surface.CAR) {
                    // a car's body is an unseen block round the model: sparks and a puff, no hole hanging in the air
                    for (int i = 0; i < 6; i++) {
                        Vec3 v = n.scale(0.2).add(jitter(0.25));
                        lvl.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, v.x, v.y, v.z);
                    }
                    lvl.addParticle(ParticleTypes.SMOKE, p.x + n.x * 0.05, p.y + n.y * 0.05, p.z + n.z * 0.05, 0, 0.01, 0);
                    return;
                }
                // chips of the block thrown back out, a puff of its dust hanging there, sparks off metal
                int chips = Math.round(6 * impact.size());
                for (int i = 0; i < chips; i++) {
                    Vec3 v = n.scale(0.15 + RANDOM.nextDouble() * 0.15).add(jitter(0.12));
                    lvl.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), p.x, p.y, p.z, v.x, v.y, v.z);
                }
                int colour = state.getMapColor(lvl, BlockPos.containing(p.subtract(n.scale(0.01)))).col;
                for (int i = 0; i < Math.round(5 * impact.size()); i++) {
                    Vec3 at = p.add(n.scale(0.05 + RANDOM.nextDouble() * 0.15)).add(jitter(0.08));
                    lvl.addParticle(new DustParticleOptions(colour, 0.9f + RANDOM.nextFloat() * 0.8f), at.x, at.y, at.z,
                            n.x * 0.02, n.y * 0.02 + 0.01, n.z * 0.02);
                }
                lvl.addParticle(ParticleTypes.SMOKE, p.x + n.x * 0.05, p.y + n.y * 0.05, p.z + n.z * 0.05, 0, 0.01, 0);
                if (surface == com.zomboidcraft.combat.Surface.METAL) {
                    for (int i = 0; i < 4; i++) {
                        Vec3 v = n.scale(0.2).add(jitter(0.2));
                        lvl.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, v.x, v.y, v.z);
                    }
                }
                decal(lvl, p, face, state, (float) (0.055 + 0.03 * impact.size()) * (0.9f + RANDOM.nextFloat() * 0.2f),
                        HOLE + RANDOM.nextInt(2), (float) config.bulletHoleSeconds);
            }
            case Impacts.BLOOD -> {
                if (!config.bloodSplatter) {
                    return;
                }
                // most of it goes on out along the shot, a little back toward the gun
                int count = Math.round(10 * impact.size());
                for (int i = 0; i < count && DROPS.size() < MAX_DROPS; i++) {
                    boolean back = i % 5 == 4;
                    Vec3 v = d.scale(back ? -(0.8 + RANDOM.nextDouble()) : 2.0 + RANDOM.nextDouble() * 3.5)
                            .add(jitter(1.6)).add(0, 0.6 + RANDOM.nextDouble(), 0);
                    Drop drop = new Drop();
                    drop.x = p.x;
                    drop.y = p.y;
                    drop.z = p.z;
                    drop.vx = v.x;
                    drop.vy = v.y;
                    drop.vz = v.z;
                    drop.size = 0.018f + RANDOM.nextFloat() * 0.03f;
                    drop.marks = RANDOM.nextInt(3) == 0;
                    DROPS.add(drop);
                }
                for (int i = 0; i < Math.round(4 * impact.size()); i++) {     // a red mist where it went in
                    Vec3 at = p.add(jitter(0.12));
                    lvl.addParticle(new DustParticleOptions(0x5E0707, 1.2f + RANDOM.nextFloat()), at.x, at.y, at.z, 0, 0, 0);
                }
            }
            case Impacts.SPLASH -> {
                if (!config.bloodSplatter) {
                    return;
                }
                Direction face = Direction.from3DDataValue(impact.face());
                decal(lvl, p, face, Block.stateById(impact.block()),
                        (float) Math.min(0.75, 0.16 * impact.size()) * (0.8f + RANDOM.nextFloat() * 0.4f),
                        SPLASH + RANDOM.nextInt(2), (float) config.bloodSeconds);
            }
            default -> {
            }
        }
    }

    private static Vec3 jitter(double r) {
        return new Vec3((RANDOM.nextDouble() - 0.5) * r, (RANDOM.nextDouble() - 0.5) * r, (RANDOM.nextDouble() - 0.5) * r);
    }

    private static void decal(ClientLevel lvl, Vec3 p, Direction face, BlockState state, float half, int cell, float life) {
        if (life <= 0) {
            return;
        }
        Decal dc = new Decal();
        dc.face = face;
        dc.pos = BlockPos.containing(p.x - face.getStepX() * 0.01, p.y - face.getStepY() * 0.01, p.z - face.getStepZ() * 0.01);
        dc.state = lvl.getBlockState(dc.pos);
        if (dc.state.isAir()) {
            return;
        }
        dc.x = p.x;
        dc.y = p.y;
        dc.z = p.z;
        dc.half = half;
        dc.cell = cell;
        dc.flipU = RANDOM.nextBoolean();
        dc.flipV = RANDOM.nextBoolean();
        dc.life = life * (0.9f + RANDOM.nextFloat() * 0.2f);
        // each new mark sits a hair above the last, so overlapping ones don't flicker
        dc.lift = 0.002f + 0.0004f * (stack++ % 10);
        DECALS.add(dc);
        int max = Math.max(0, ZcConfig.get().guns.maxDecals);
        while (DECALS.size() > max) {
            DECALS.remove(0);
        }
    }

    // ------------------------------------------------------------------ every frame

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            DECALS.clear();
            DROPS.clear();
            level = mc.level;
        }
        CameraRenderState cam = context.levelState().cameraRenderState;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        if (level == null || cam == null || cam.pos == null || DECALS.isEmpty() && DROPS.isEmpty()) {
            return;
        }
        if (mc.isPaused()) {
            dt = 0f;
        }
        stepDrops(dt);
        Vec3 c = cam.pos;
        PoseStack stack = context.poseStack();
        float step = dt;
        context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityTranslucent(TEXTURE), (pose, out) -> {
            for (int i = DECALS.size() - 1; i >= 0; i--) {
                Decal dc = DECALS.get(i);
                dc.age += step;
                if (dc.age > dc.life || level.getBlockState(dc.pos) != dc.state) {
                    DECALS.remove(i);
                    continue;
                }
                if (c.distanceToSqr(dc.x, dc.y, dc.z) < 96 * 96) {
                    drawDecal(out, pose, dc, c);
                }
            }
            for (Drop drop : DROPS) {
                drawDrop(out, pose, drop, c);
            }
        });
    }

    private static void stepDrops(float dt) {
        if (dt <= 0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (int i = DROPS.size() - 1; i >= 0; i--) {
            Drop d = DROPS.get(i);
            d.age += dt;
            d.vy -= GRAVITY * dt;
            float keep = (float) Math.pow(DRAG, dt);
            d.vx *= keep;
            d.vy *= keep;
            d.vz *= keep;
            Vec3 from = new Vec3(d.x, d.y, d.z), to = from.add(d.vx * dt, d.vy * dt, d.vz * dt);
            BlockHitResult hit = mc.player == null ? null
                    : level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (hit != null && hit.getType() != HitResult.Type.MISS) {
                // it spots whatever it lands on
                if (d.marks && ZcConfig.get().guns.bloodSplatter) {
                    decal(level, hit.getLocation(), hit.getDirection(), level.getBlockState(hit.getBlockPos()),
                            d.size * (2.5f + RANDOM.nextFloat() * 2f), SPLASH + RANDOM.nextInt(2),
                            (float) ZcConfig.get().guns.bloodSeconds);
                }
                DROPS.remove(i);
                continue;
            }
            d.x = to.x;
            d.y = to.y;
            d.z = to.z;
            if (d.age > 3f) {
                DROPS.remove(i);
            }
        }
    }

    /** The mark as a quad on its face, trimmed to the block so it never hangs out over an edge. */
    private static void drawDecal(VertexConsumer out, PoseStack.Pose pose, Decal dc, Vec3 c) {
        Direction f = dc.face;
        // the face's two directions across: (a, b), and where the block's own square on them begins
        Direction.Axis axis = f.getAxis();
        double ca, cb, ba, bb;
        if (axis == Direction.Axis.Y) {
            ca = dc.x; cb = dc.z; ba = dc.pos.getX(); bb = dc.pos.getZ();
        } else if (axis == Direction.Axis.X) {
            ca = dc.z; cb = dc.y; ba = dc.pos.getZ(); bb = dc.pos.getY();
        } else {
            ca = dc.x; cb = dc.y; ba = dc.pos.getX(); bb = dc.pos.getY();
        }
        double a0 = Math.max(ca - dc.half, ba), a1 = Math.min(ca + dc.half, ba + 1);
        double b0 = Math.max(cb - dc.half, bb), b1 = Math.min(cb + dc.half, bb + 1);
        if (a1 <= a0 || b1 <= b0) {
            return;
        }
        // texture across the cell, matching the trim
        float cellU = dc.cell / (float) CELLS, cellW = 1f / CELLS;
        float ua0 = (float) ((a0 - (ca - dc.half)) / (2 * dc.half)), ua1 = (float) ((a1 - (ca - dc.half)) / (2 * dc.half));
        float vb0 = (float) ((b0 - (cb - dc.half)) / (2 * dc.half)), vb1 = (float) ((b1 - (cb - dc.half)) / (2 * dc.half));
        if (dc.flipU) {
            ua0 = 1 - ua0;
            ua1 = 1 - ua1;
        }
        if (dc.flipV) {
            vb0 = 1 - vb0;
            vb1 = 1 - vb1;
        }
        float fade = Math.min(1f, (dc.life - dc.age) / 4f);
        int colour = (int) (fade * 255) << 24 | 0xFFFFFF;
        BlockPos lightAt = dc.pos.relative(f);
        int light = level.getBrightness(LightLayer.BLOCK, lightAt) << 4 | level.getBrightness(LightLayer.SKY, lightAt) << 20;
        double lx = f.getStepX() * dc.lift, ly = f.getStepY() * dc.lift, lz = f.getStepZ() * dc.lift;
        double[][] corners = {{a0, b0}, {a1, b0}, {a1, b1}, {a0, b1}};
        float[][] uvs = {{ua0, vb0}, {ua1, vb0}, {ua1, vb1}, {ua0, vb1}};
        Vec3[] at = new Vec3[4];
        for (int k = 0; k < 4; k++) {
            double a = corners[k][0], b = corners[k][1];
            Vec3 w = axis == Direction.Axis.Y ? new Vec3(a, dc.y, b)
                    : axis == Direction.Axis.X ? new Vec3(dc.x, b, a) : new Vec3(a, b, dc.z);
            at[k] = w.add(lx - c.x, ly - c.y, lz - c.z);
        }
        // wound to face out of the block
        Vec3 n = new Vec3(f.getStepX(), f.getStepY(), f.getStepZ());
        int[] order = at[1].subtract(at[0]).cross(at[2].subtract(at[0])).dot(n) >= 0 ? new int[] {0, 1, 2, 3} : new int[] {3, 2, 1, 0};
        for (int k : order) {
            out.addVertex(pose, (float) at[k].x, (float) at[k].y, (float) at[k].z).setColor(colour)
                    .setUv(cellU + uvs[k][0] * cellW, uvs[k][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                    .setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
        }
    }

    /** A drop in flight: a tiny cube of blood. */
    private static void drawDrop(VertexConsumer out, PoseStack.Pose pose, Drop d, Vec3 c) {
        BlockPos at = BlockPos.containing(d.x, d.y, d.z);
        int light = level.getBrightness(LightLayer.BLOCK, at) << 4 | level.getBrightness(LightLayer.SKY, at) << 20;
        float u = (SOLID + 0.5f) / CELLS;
        double s = d.size;
        double x = d.x - c.x, y = d.y - c.y, z = d.z - c.z;
        for (Direction f : Direction.values()) {
            Vec3 n = new Vec3(f.getStepX(), f.getStepY(), f.getStepZ());
            Vec3 t1 = f.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 t2 = n.cross(t1);
            Vec3 mid = new Vec3(x, y, z).add(n.scale(s));
            Vec3[] q = {mid.add(t1.scale(-s)).add(t2.scale(-s)), mid.add(t1.scale(s)).add(t2.scale(-s)),
                    mid.add(t1.scale(s)).add(t2.scale(s)), mid.add(t1.scale(-s)).add(t2.scale(s))};
            boolean flip = q[1].subtract(q[0]).cross(q[2].subtract(q[0])).dot(n) < 0;
            for (int k = 0; k < 4; k++) {
                Vec3 v = q[flip ? 3 - k : k];
                out.addVertex(pose, (float) v.x, (float) v.y, (float) v.z).setColor(-1).setUv(u, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) n.x, (float) n.y, (float) n.z);
            }
        }
    }

    private ImpactFx() {
    }
}
