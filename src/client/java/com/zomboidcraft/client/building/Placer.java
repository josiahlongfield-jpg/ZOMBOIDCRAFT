package com.zomboidcraft.client.building;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.building.Build;
import com.zomboidcraft.building.Builder;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Placing something from the Build page: a see-through green copy of it where you're looking (red where it can't
 * go), left click to build it there, right click to stop. You stay in placing until you stop, so a row of sandbags or
 * a window's four boards is click, click, click.
 */
public final class Placer {
    private static final Identifier GHOST = ZomboidCraft.id("textures/item/weapon/ghost.png");
    private static final int FULL_BRIGHT = 0xF000F0;

    private static @Nullable Build building;
    private static @Nullable Build.Spot spot;
    private static @Nullable BlockPos wrongAt;
    private static @Nullable BlockHitResult aimed;
    private static @Nullable String problem;
    private static int said;
    /** Builds asked for since the game started (the UI test reads it). */
    public static int asked;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(Placer::tick);
        // a left click while placing builds instead of swinging, mining or shooting; ahead of the gun's own handler
        Identifier first = ZomboidCraft.id("placer");
        ClientPreAttackCallback.EVENT.addPhaseOrdering(first, Event.DEFAULT_PHASE);
        ClientPreAttackCallback.EVENT.register(first, (client, player, clicks) -> {
            if (building == null) return false;
            if (clicks > 0) place(client);
            return true;
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(Placer::draw);
    }

    public static void start(Build build) {
        building = build;
        said = 0;
    }

    public static void stop() {
        building = null;
        spot = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendOverlayMessage(Component.empty());
    }

    public static @Nullable Build building() {
        return building;
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (building == null) return;
        if (player == null || mc.level == null || mc.gui.screen() != null || !player.isAlive()) {
            stop();
            return;
        }
        Build build = building;
        look(mc, player, build);
        boolean place = false;
        while (mc.options.keyAttack.consumeClick()) place = true;
        boolean cancel = false;
        while (mc.options.keyUse.consumeClick()) cancel = true;
        if (cancel) {
            stop();
            return;
        }
        if (place) place(mc);
        if (said > 0) {
            said--;
            return;
        }
        String line = build.label + ":  LMB build  ·  RMB done" + (problem != null ? "  ·  " + problem : "");
        player.sendOverlayMessage(Component.literal(line).withStyle(problem == null ? ChatFormatting.WHITE : ChatFormatting.GRAY));
    }

    /** Where the build would go from what the player is looking at, and what's stopping it. */
    private static void look(Minecraft mc, LocalPlayer player, Build build) {
        spot = null;
        wrongAt = null;
        problem = null;
        HitResult hit = mc.hitResult;
        aimed = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                && player.getEyePosition().distanceToSqr(hit.getLocation()) <= Builder.REACH * Builder.REACH ? b : null;
        if (aimed == null) {
            problem = "Look at where it should go.";
        } else {
            Build.Spot s = build.spot(mc.level, aimed.getBlockPos(), aimed.getDirection(), player.getDirection());
            if (s.ok()) {
                spot = s;
            } else {
                problem = s.problem();
                wrongAt = aimed.getBlockPos().relative(aimed.getDirection());
            }
        }
        String missing = build.missing(player);
        if (missing != null) problem = missing;
    }

    private static void place(Minecraft mc) {
        LocalPlayer player = mc.player;
        Build build = building;
        if (player == null || build == null || mc.level == null) return;
        look(mc, player, build);
        if (problem == null && spot != null && aimed != null) {
            asked++;
            ClientPlayNetworking.send(new Builder.Request(build.ordinal(), aimed.getBlockPos(), aimed.getDirection().get3DDataValue(),
                    player.getDirection().get3DDataValue()));
        } else if (problem != null) {
            player.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.GRAY));
            said = 40;
        }
    }

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (building == null || mc.level == null) return;
        CameraRenderState cam = context.levelState().cameraRenderState;
        if (cam == null || cam.pos == null) return;
        List<AABB> boxes;
        BlockPos at;
        int colour;
        if (spot != null && spot.ok()) {
            at = spot.pos();
            boxes = spot.state().getShape(mc.level, at).toAabbs();
            colour = 0xFFFFFFFF;
            if (boxes.isEmpty()) boxes = List.of(new AABB(0, 0, 0, 1, 0.3, 1));
        } else if (wrongAt != null) {
            at = wrongAt;
            boxes = List.of(new AABB(0.02, 0.02, 0.02, 0.98, 0.98, 0.98));
            colour = 0xFFFF5040;
        } else {
            return;
        }
        Vec3 c = cam.pos;
        PoseStack stack = context.poseStack();
        stack.pushPose();
        stack.translate((float) (at.getX() - c.x), (float) (at.getY() - c.y), (float) (at.getZ() - c.z));
        List<AABB> drawn = boxes;
        int tint = colour;
        context.submitNodeCollector().submitCustomGeometry(stack, RenderTypes.entityTranslucent(GHOST),
                (pose, out) -> {
                    for (AABB box : drawn) box(pose, out, box.inflate(0.002), tint);
                });
        stack.popPose();
    }

    private static void box(PoseStack.Pose pose, VertexConsumer out, AABB b, int colour) {
        float x0 = (float) b.minX, y0 = (float) b.minY, z0 = (float) b.minZ, x1 = (float) b.maxX, y1 = (float) b.maxY, z1 = (float) b.maxZ;
        quad(pose, out, colour, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(pose, out, colour, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
        quad(pose, out, colour, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(pose, out, colour, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, out, colour, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, out, colour, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, int colour, float nx, float ny, float nz, float... p) {
        float[] u = {0, 1, 1, 0}, v = {1, 1, 0, 0};
        for (int k = 0; k < 4; k++) {
            out.addVertex(pose, p[k * 3], p[k * 3 + 1], p[k * 3 + 2]).setColor(colour).setUv(u[k], v[k])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        }
    }

    private Placer() {
    }
}
