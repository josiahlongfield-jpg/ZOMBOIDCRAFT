package com.zomboidcraft.client.camera;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.GunType;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Third person played over the shoulder, on foot: the camera close behind your eyes and off to one side, and turned
 * to look at the very point your eyes' line meets, so the middle of the screen is exactly what a shot, a swing or a
 * click will hit. It pulls in closer while you aim a gun, swaps shoulder on a key (easing across), and comes in
 * short of walls so it never looks through them.
 */
public final class ShoulderCam {
    /** How far the aim line is looked along for the point the camera turns to (blocks). */
    private static final double REACH = 96;
    /** Kept this far off a wall the camera is pushed in by (blocks). */
    private static final double WALL_GAP = 0.2;

    /** 1 over the right shoulder, -1 the left; side eases toward it. */
    private static int shoulder = 1;
    private static double side = 1, aim, aimDist = 20;
    private static long lastFrame;

    /** The camera's place and the way it looks (degrees, Minecraft's yaw and pitch). */
    public record View(Vec3 pos, float yRot, float xRot) {
    }

    /** The over-the-shoulder view is the one in use now: third person from behind, on foot, switched on. */
    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return ZcConfig.get().look.shoulderCam && mc.player != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                && !mc.player.isPassenger() && !mc.player.isSleeping();
    }

    /** Takes the camera for the shared rig (CameraRig), behind the aircraft's. */
    public static void register() {
        CameraRig.register(50, (camera, entity, partialTick, out) -> {
            View v = view(entity, partialTick);
            if (v == null) {
                return false;
            }
            // Minecraft's own turn for a camera at this yaw and pitch
            out.rotation.rotationYXZ((float) (Math.PI - Math.toRadians(v.yRot())), (float) -Math.toRadians(v.xRot()), 0f);
            out.position = v.pos();
            return true;
        });
    }

    public static void swapShoulder() {
        shoulder = -shoulder;
    }

    /** Where the camera goes this frame for the entity it follows, or null to leave Minecraft's. */
    public static View view(Entity entity, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || entity != mc.player || mc.level == null) {
            return null;
        }
        LocalPlayer player = mc.player;
        ZcConfig.Look look = ZcConfig.get().look;
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        boolean aiming = player.isUsingItem() && GunType.isGun(player.getUseItem());
        aim += ((aiming ? 1 : 0) - aim) * Math.min(1, dt * 10);
        side += (shoulder - side) * Math.min(1, dt * 8);

        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 fwd = player.getViewVector(partialTick);
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? Vec3.directionFromRotation(0, player.getViewYRot(partialTick) + 90) : right.normalize();
        double back = lerp(look.shoulderBack, look.shoulderAimBack, aim);
        double across = lerp(look.shoulderSide, look.shoulderAimSide, aim) * side;
        double rise = lerp(look.shoulderUp, look.shoulderAimUp, aim);
        // the shoulder first (so a wall at your side stops it there), then back from it; raised straight up, so it
        // looks down on you whichever way you look
        Vec3 shoulderAt = clip(mc, entity, eye, eye.add(right.scale(across)).add(0, rise, 0));
        Vec3 pos = clip(mc, entity, shoulderAt, shoulderAt.subtract(fwd.scale(back)));

        // the point the eyes' line meets: what the middle of the screen must show. Eased, so looking past an edge
        // doesn't jolt the view
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(fwd.scale(REACH)), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, entity));
        double dist = hit.getType() == HitResult.Type.MISS ? REACH : Math.max(0.5, hit.getLocation().distanceTo(eye));
        // anything standing on that line too, however far: the camera sits off to the side, so if it looked at the
        // wall behind a zombie the head in the middle of the screen wouldn't be on the line the shots take
        AABB along = entity.getBoundingBox().expandTowards(fwd.scale(dist)).inflate(1);
        EntityHitResult on = ProjectileUtil.getEntityHitResult(entity, eye, eye.add(fwd.scale(dist)), along,
                e -> !e.isSpectator() && e.isPickable(), dist * dist);
        if (on != null) {
            dist = Math.max(0.5, on.getLocation().distanceTo(eye));
        }
        // eased, quicker coming nearer than going away, so a zombie stepping into the line is centred at once
        double rate = dist < aimDist ? 40 : 14;
        aimDist = dt == 0 ? dist : aimDist + (dist - aimDist) * Math.min(1, dt * rate);
        Vec3 to = eye.add(fwd.scale(aimDist)).subtract(pos);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        float yRot = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        float xRot = (float) Math.toDegrees(-Math.atan2(to.y, flat));
        return new View(pos, yRot, xRot);
    }

    /** From a to b, or short of the first block in the way. */
    private static Vec3 clip(Minecraft mc, Entity entity, Vec3 a, Vec3 b) {
        BlockHitResult hit = mc.level.clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) {
            return b;
        }
        Vec3 d = b.subtract(a);
        double len = d.length();
        double keep = Math.max(0, hit.getLocation().distanceTo(a) - WALL_GAP);
        return len < 1e-6 ? a : a.add(d.scale(keep / len));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private ShoulderCam() {
    }
}
