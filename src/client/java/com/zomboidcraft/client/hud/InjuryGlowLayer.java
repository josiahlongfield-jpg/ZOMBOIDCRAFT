package com.zomboidcraft.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.Body;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * A red glow round each injured part of the HUD's figure. It is drawn from the figure's own posed model parts, so
 * it turns and sways with the figure: soft shells a little larger than the part, brighter the worse the injury,
 * pulsing while it bleeds. Only the HUD's figure gets it.
 */
public class InjuryGlowLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier WHITE = ZomboidCraft.id("textures/effect/injury_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Each part's box in model pixels from its pivot: x, y, z, then width, height, depth. */
    private static final float[][] BOXES = {
            {-4, -8, -4, 8, 8, 8},   // head
            {-4, 0, -2, 8, 12, 4},   // torso
            {-1, -2, -2, 4, 12, 4},  // left arm
            {-3, -2, -2, 4, 12, 4},  // right arm
            {-2, 0, -2, 4, 12, 4},   // left leg
            {-2, 0, -2, 4, 12, 4}};  // right leg
    /** Shells outward from the part: how far (pixels) and how strong. */
    private static final float[][] SHELLS = {{0.35f, 0.30f}, {0.8f, 0.18f}, {1.35f, 0.09f}};

    /** Set while the HUD draws its figure, so the render state made then is the one to mark. */
    static boolean capturing;
    private static EntityRenderState figure;

    public InjuryGlowLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    /** Called with every inventory-style render state as it's made. */
    public static void noted(EntityRenderState state) {
        if (capturing) figure = state;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (state != figure || mc.player == null) return;
        Body body = Body.get(mc.player);
        if (!body.injured()) return;
        PlayerModel model = getParentModel();
        ModelPart[] parts = {model.head, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
        float beat = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI * 2 / 1.1));
        RenderType type = RenderTypes.entityTranslucentEmissive(WHITE);
        for (Body.Region region : Body.Region.values()) {
            Body.Part part = body.part(region);
            int severity = part.severity();
            if (severity == 0) continue;
            float strength = severity == 1 ? 0.45f : severity == 2 ? 0.75f : 1f;
            if (part.bleeding()) strength *= 0.6f + 0.4f * beat;
            float[] box = BOXES[region.ordinal()];
            pose.pushPose();
            parts[region.ordinal()].translateAndRotate(pose);
            float s = strength;
            collector.submitCustomGeometry(pose, type, (p, out) -> {
                for (float[] shell : SHELLS) {
                    int alpha = Math.round(255 * shell[1] * s);
                    box(p, out, box, shell[0], (alpha << 24) | 0xFF2A12);
                }
            });
            pose.popPose();
        }
    }

    /** A box grown by {@code grow} pixels each way, in model pixels. */
    private static void box(PoseStack.Pose pose, VertexConsumer out, float[] b, float grow, int colour) {
        float x0 = (b[0] - grow) / 16f, y0 = (b[1] - grow) / 16f, z0 = (b[2] - grow) / 16f;
        float x1 = (b[0] + b[3] + grow) / 16f, y1 = (b[1] + b[4] + grow) / 16f, z1 = (b[2] + b[5] + grow) / 16f;
        quad(pose, out, colour, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        quad(pose, out, colour, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
        quad(pose, out, colour, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
        quad(pose, out, colour, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
        quad(pose, out, colour, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
        quad(pose, out, colour, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, int colour,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
        float[][] v = {{ax, ay, az, 0, 0}, {bx, by, bz, 1, 0}, {cx, cy, cz, 1, 1}, {dx, dy, dz, 0, 1}};
        for (float[] c : v) {
            out.addVertex(pose, c[0], c[1], c[2]).setColor(colour).setUv(c[3], c[4]).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT).setNormal(pose, nx, ny, nz);
        }
    }
}
