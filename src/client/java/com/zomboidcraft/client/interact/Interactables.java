package com.zomboidcraft.client.interact;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.client.cars.CarLights;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The small green dot on whatever you could use right now: a car's door handle, a glovebox, a call button, a
 * door, a cupboard or crate. It only shows up close (about two blocks) on the thing right under the crosshair,
 * so walking past things lights nothing; it's how the game says "you can use this", in place of key hints.
 * <p>
 * Anything a thread adds that you can use should show it: blocks through {@link #blocks}, anything else
 * (a part of an entity, a spot inside a vehicle) through {@link #finder}. Cars draw their own dots on the car
 * so they move with it ({@link com.zomboidcraft.client.cars.CarClient}).
 */
public final class Interactables {
    /** Where a dot goes, in the world, and how big its bright middle is (metres; 0 for the usual size). */
    public record Spot(Vec3 at, float size) {
    }

    /** Finds a dot for the local player this tick, or returns null. */
    public interface Finder {
        Spot find(Minecraft client, LocalPlayer player);
    }

    /** How close a block has to be (eye to where the crosshair meets it), and the usual dot's size. */
    public static final double REACH = 2.0;
    public static final float SIZE = 0.025f;

    private static final List<Finder> FINDERS = new ArrayList<>();
    private static final List<Predicate<BlockState>> BLOCKS = new ArrayList<>();
    /** Whether something else (a car's own dot) is lit this tick, so no second dot shows. */
    private static final List<java.util.function.BooleanSupplier> CLAIMS = new ArrayList<>();
    private static Spot spot;

    /** Blocks matching this get the dot when you're close and looking at them. */
    public static void blocks(Predicate<BlockState> usable) {
        BLOCKS.add(usable);
    }

    /** A source of dots that aren't on a block. Asked in order, before blocks; the first answer wins. */
    public static void finder(Finder finder) {
        FINDERS.add(finder);
    }

    /** Something that draws its own dot: while it says true, none is drawn here. */
    public static void drawsOwn(java.util.function.BooleanSupplier lit) {
        CLAIMS.add(lit);
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Interactables::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(Interactables::draw);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        spot = null;
        if (player == null || client.level == null || client.gui.screen() != null || player.isSpectator()) {
            return;
        }
        for (java.util.function.BooleanSupplier lit : CLAIMS) {
            if (lit.getAsBoolean()) {
                return;
            }
        }
        for (Finder finder : FINDERS) {
            Spot s = finder.find(client, player);
            if (s != null) {
                spot = s;
                return;
            }
        }
        if (player.isPassenger()) {
            return;
        }
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK
                || hit.getLocation().distanceTo(player.getEyePosition()) > REACH) {
            return;
        }
        BlockPos pos = b.getBlockPos();
        BlockState state = client.level.getBlockState(pos);
        if (usable(client, pos, state)) {
            spot = new Spot(where(client, pos, state, b.getDirection()), SIZE);
        }
    }

    /** Whether this block does something when used: one of the registered kinds, a door or switch, or a container. */
    private static boolean usable(Minecraft client, BlockPos pos, BlockState state) {
        for (Predicate<BlockState> p : BLOCKS) {
            if (p.test(state)) {
                return true;
            }
        }
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
                || state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock) {
            return true;
        }
        return client.level.getBlockEntity(pos) instanceof Container || state.getMenuProvider(client.level, pos) != null;
    }

    /**
     * Where on the block the dot sits: the middle of the face you're looking at (of the block's own shape,
     * not its whole cube), a door's at handle height.
     */
    private static Vec3 where(Minecraft client, BlockPos pos, BlockState state, Direction face) {
        VoxelShape shape = state.getShape(client.level, pos);
        AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        double x = (box.minX + box.maxX) / 2, y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2;
        switch (face) {
            case EAST -> x = box.maxX;
            case WEST -> x = box.minX;
            case UP -> y = box.maxY;
            case DOWN -> y = box.minY;
            case SOUTH -> z = box.maxZ;
            case NORTH -> z = box.minZ;
        }
        if (state.getBlock() instanceof DoorBlock && state.hasProperty(DoorBlock.HALF)) {
            // a handle's about a metre up: the top of the bottom half
            y = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 0.95 : -0.05;
        }
        return Vec3.atLowerCornerOf(pos).add(x, y, z);
    }

    private static final float[] GREEN = {0.3f, 1f, 0.35f};

    private static void draw(LevelRenderContext context) {
        Spot s = spot;
        if (s == null || context.levelState().cameraRenderState == null) {
            return;
        }
        Vec3 cam = context.levelState().cameraRenderState.pos;
        float x = (float) (s.at().x - cam.x), y = (float) (s.at().y - cam.y), z = (float) (s.at().z - cam.z);
        float size = s.size() > 0 ? s.size() : SIZE;
        boolean shaders = CarLights.shaderPackOn();
        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), CarLights.glowType(shaders),
                (pose, out) -> dot(x, y, z, -x, -y, -z, size, shaders, pose, out));
    }

    /**
     * Draws a dot at (x, y, z) facing back along (tx, ty, tz) toward the camera: a bright core in a soft glow,
     * pulled a few centimetres toward the camera so it isn't lost in what it's on.
     */
    public static void dot(float x, float y, float z, float tx, float ty, float tz, float size, boolean shaders,
                           PoseStack.Pose pose, VertexConsumer out) {
        if (com.zomboidcraft.client.camera.CinematicView.clean()) {
            return;  // filming: no dots anywhere, cars' own included
        }
        float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (len < 0.05f) {
            return;
        }
        tx /= len;
        ty /= len;
        tz /= len;
        float cx = x + tx * 0.03f, cy = y + ty * 0.03f, cz = z + tz * 0.03f;
        halo(cx, cy, cz, tx, ty, tz, size * 2.2f, pose, out, CarLights.shade(GREEN, 0.55f, shaders));
        halo(cx, cy, cz, tx, ty, tz, size, pose, out, CarLights.shade(GREEN, 1f, shaders));
    }

    private static void halo(float cx, float cy, float cz, float tx, float ty, float tz, float half, PoseStack.Pose pose,
                             VertexConsumer out, int colour) {
        // right = t x up, then up = right x t; wound so the quad faces t
        float rx = -tz, rz = tx, rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        float ux = -rz * ty, uy = rz * tx - rx * tz, uz = rx * ty;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        rx *= half;
        rz *= half;
        ux *= half / ul;
        uy *= half / ul;
        uz *= half / ul;
        float[][] corners = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
        for (float[] k : corners) {
            out.addVertex(pose, cx + rx * k[0] + ux * k[1], cy + uy * k[1], cz + rz * k[0] + uz * k[1])
                    .setColor(colour)
                    .setUv(k[2], k[3])
                    .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(CarLights.FULL_BRIGHT)
                    .setNormal(pose, tx, ty, tz);
        }
    }

    private Interactables() {
    }
}
