package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.world.cars.ShopTool;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws the auto shop's floor tools ({@link ShopTool}) from the car lift's atlas, in each tool's own frame (its
 * working side toward -z for the jack and crane, +z for the machines):
 * <ul>
 * <li>the trolley jack (refs garage-interior 05/06): red side frames on steel casters, the lifting arm swinging
 * up to its saddle under the car's sill, the black handle, the instruction label and release knob;</li>
 * <li>the engine crane: yellow, two splayed legs on casters, the post, a boom reaching forward with its ram,
 * the chain and hook;</li>
 * <li>the tyre machine: a grey cabinet with its ribbed turntable and clamps, the column and mount head, the
 * bead breaker paddle and the foot pedals;</li>
 * <li>the parts washer: a green tub of dirty solvent on its drum, the lid propped open, the tap and a brush.</li>
 * </ul>
 */
public class ShopToolRenderer extends EntityRenderer<ShopTool, ShopToolRenderer.State> {
    private static final int STEEL = 1, RUBBER = 2, CHROME = 3, HOSE = 9, HAZARD = 10, MOTOR = 12, KNOB = 14, CABLE = 16,
            JACK_RED = 20, CRANE_YELLOW = 21, WASHER_GREEN = 22, SOLVENT = 23, TREAD = 24, MACHINE_GREY = 25, TURNTABLE = 26,
            JACK_LABEL = 27, BRUSH = 28;

    public static class State extends EntityRenderState {
        ShopTool.Kind kind = ShopTool.Kind.JACK;
        float yaw, saddle;
    }

    public ShopToolRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.4f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(ShopTool tool, float partialTick) {
        return tool.getBoundingBox().inflate(1.6, 2.2, 1.6).move(0, 1, 0);
    }

    @Override
    public void extractRenderState(ShopTool tool, State state, float partialTick) {
        super.extractRenderState(tool, state, partialTick);
        state.kind = tool.kind();
        state.yaw = tool.getYRot();
        state.saddle = tool.saddleO + (tool.saddle() - tool.saddleO) * partialTick;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(HoistRenderer.ATLAS),
                (pose, out) -> new Painter(state, pose, out).draw());
    }

    private static final class Painter extends AtlasBoxes {
        final State st;

        Painter(State st, PoseStack.Pose pose, VertexConsumer out) {
            super(pose, out, st.lightCoords, st.yaw);
            this.st = st;
        }

        void draw() {
            switch (st.kind) {
                case JACK -> jack();
                case CRANE -> crane();
                case TYRE_MACHINE -> tyreMachine();
                case WASHER -> washer();
            }
        }

        /** A caster: a black wheel on a steel fork. */
        void wheel(float x, float z, float r, float w) {
            box(x, r, z, w, r, r, RUBBER);
            box(x, r, z, w + 0.006f, r * 0.35f, r * 0.35f, CHROME);
        }

        /** A rod from a to b, square in section. */
        void rod(Vector3f a, Vector3f b, float half, int tile) {
            Vector3f d = new Vector3f(b).sub(a);
            float len = d.length();
            d.normalize();
            Vector3f side = Math.abs(d.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0).cross(d).normalize();
            Vector3f up = new Vector3f(d).cross(side).normalize();
            obox(new Vector3f(a).add(b).mul(0.5f), d, up, side, len / 2, half, half, tile);
        }

        void jack() {
            // two side frames, low at the back, on four casters; the saddle end toward +z (under the car)
            for (int s = -1; s <= 1; s += 2) {
                box(s * 0.15f, 0.11f, 0.02f, 0.012f, 0.06f, 0.36f, JACK_RED);
                box(s * 0.15f, 0.16f, -0.24f, 0.012f, 0.03f, 0.1f, JACK_RED);
                wheel(s * 0.17f, 0.3f, 0.05f, 0.02f);
                wheel(s * 0.14f, -0.3f, 0.04f, 0.02f);
            }
            box(0, 0.06f, 0.3f, 0.16f, 0.012f, 0.012f, STEEL);           // the front axle
            box(0, 0.17f, -0.1f, 0.14f, 0.05f, 0.18f, JACK_RED);         // the pump body between the frames
            box(0, 0.225f, -0.1f, 0.1f, 0.004f, 0.12f, JACK_LABEL);       // its instruction label
            box(0.1f, 0.13f, -0.33f, 0.025f, 0.025f, 0.025f, KNOB);      // the release valve's knob
            // the lifting arm from its pivot at the back of the frames up to the saddle
            Vector3f pivot = new Vector3f(0, 0.14f, -0.05f);
            float sy = Math.max(0.14f, st.saddle - 0.04f);
            Vector3f saddle = new Vector3f(0, sy, 0.34f);
            for (int s = -1; s <= 1; s += 2) {
                rod(new Vector3f(pivot).add(s * 0.1f, 0, 0), new Vector3f(saddle).add(s * 0.06f, -0.02f, 0), 0.02f, JACK_RED);
            }
            box(0, sy - 0.02f, 0.34f, 0.07f, 0.02f, 0.05f, MOTOR);        // the saddle's stem
            box(0, sy + 0.015f, 0.34f, 0.065f, 0.015f, 0.065f, RUBBER);   // and its cup
            box(0, sy + 0.032f, 0.34f, 0.04f, 0.004f, 0.04f, STEEL);
            // the handle: a socket at the back and a black bar angled up toward whoever works it
            box(0, 0.2f, -0.33f, 0.04f, 0.04f, 0.04f, JACK_RED);
            rod(new Vector3f(0, 0.22f, -0.35f), new Vector3f(0, 0.62f, -0.78f), 0.016f, MOTOR);
            box(0, 0.63f, -0.79f, 0.09f, 0.02f, 0.02f, MOTOR);
        }

        void crane() {
            // two legs splayed forward under the car's nose, a cross bar at the back, six casters
            for (int s = -1; s <= 1; s += 2) {
                rod(new Vector3f(s * 0.32f, 0.12f, -0.2f), new Vector3f(s * 0.42f, 0.1f, 1.25f), 0.04f, CRANE_YELLOW);
                wheel(s * 0.42f, 1.22f, 0.05f, 0.025f);
                wheel(s * 0.36f, 0.4f, 0.05f, 0.025f);
                wheel(s * 0.32f, -0.22f, 0.06f, 0.025f);
            }
            box(0, 0.14f, -0.22f, 0.36f, 0.05f, 0.05f, CRANE_YELLOW);
            box(0, 0.2f, -0.22f, 0.12f, 0.004f, 0.05f, HAZARD);
            // the post and its gusset
            box(0, 0.75f, -0.2f, 0.06f, 0.6f, 0.06f, CRANE_YELLOW);
            rod(new Vector3f(0, 0.2f, 0.05f), new Vector3f(0, 0.55f, -0.16f), 0.03f, CRANE_YELLOW);
            // the boom over the engine bay, and the ram that lifts it
            Vector3f top = new Vector3f(0, 1.36f, -0.2f), tip = new Vector3f(0, 1.62f, 1.05f);
            rod(top, tip, 0.05f, CRANE_YELLOW);
            box(0, 1.36f, -0.2f, 0.075f, 0.05f, 0.05f, STEEL);
            Vector3f ramFoot = new Vector3f(0, 0.55f, -0.12f), ramTop = new Vector3f(0, 1.48f, 0.42f);
            rod(ramFoot, new Vector3f(ramFoot).lerp(ramTop, 0.55f), 0.04f, MOTOR);
            rod(new Vector3f(ramFoot).lerp(ramTop, 0.5f), ramTop, 0.022f, CHROME);
            box(0.07f, 0.62f, -0.1f, 0.012f, 0.1f, 0.012f, MOTOR);      // the pump handle
            // the chain and hook down from the tip
            for (int i = 0; i < 7; i++) {
                box(0, tip.y - 0.06f - i * 0.06f, tip.z, i % 2 == 0 ? 0.012f : 0.006f, 0.03f, i % 2 == 0 ? 0.006f : 0.012f, STEEL);
            }
            box(0, tip.y - 0.5f, tip.z, 0.012f, 0.04f, 0.03f, CHROME);
            box(0, tip.y - 0.55f, tip.z + 0.02f, 0.012f, 0.012f, 0.025f, CHROME);
            // the push bar at the back
            rod(new Vector3f(-0.2f, 0.95f, -0.27f), new Vector3f(0.2f, 0.95f, -0.27f), 0.018f, MOTOR);
        }

        void tyreMachine() {
            // the cabinet, its front toward +z
            box(0, 0.4f, 0, 0.42f, 0.4f, 0.36f, MACHINE_GREY);
            box(0, 0.02f, 0, 0.44f, 0.02f, 0.38f, STEEL);
            // the ribbed turntable and its four clamps
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 4;
                Vector3f d = new Vector3f((float) Math.sin(a), 0, (float) Math.cos(a));
                obox(new Vector3f(0, 0.83f, 0), d, new Vector3f(0, 1, 0), new Vector3f(d).cross(new Vector3f(0, 1, 0)), 0.3f, 0.03f, 0.125f,
                        TURNTABLE);
            }
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                box((float) Math.sin(a) * 0.24f, 0.88f, (float) Math.cos(a) * 0.24f, 0.03f, 0.025f, 0.03f, CHROME);
            }
            // a tyre on the table, half mounted
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                Vector3f d = new Vector3f((float) Math.sin(a), 0, (float) Math.cos(a));
                Vector3f c = new Vector3f(d).mul(0.27f).add(0, 0.95f, 0);
                obox(c, new Vector3f(d).cross(new Vector3f(0, 1, 0)), new Vector3f(0, 1, 0), d, 0.115f, 0.09f, 0.05f, TREAD);
            }
            // the column at the back, its swing arm and the mount head over the tyre's bead
            box(0.05f, 1.1f, -0.3f, 0.06f, 0.32f, 0.06f, MACHINE_GREY);
            box(0.05f, 1.43f, -0.15f, 0.05f, 0.05f, 0.2f, MACHINE_GREY);
            box(0.05f, 1.2f, 0.03f, 0.025f, 0.22f, 0.025f, CHROME);
            box(0.05f, 1.0f, 0.06f, 0.04f, 0.03f, 0.05f, MOTOR);
            // the bead breaker paddle on the right side, and the foot pedals
            rod(new Vector3f(0.44f, 0.75f, -0.1f), new Vector3f(0.7f, 0.25f, 0.1f), 0.025f, MACHINE_GREY);
            box(0.72f, 0.24f, 0.12f, 0.02f, 0.08f, 0.1f, STEEL);
            for (int i = -1; i <= 1; i++) {
                box(i * 0.12f, 0.08f, 0.42f, 0.04f, 0.015f, 0.07f, MOTOR);
            }
            // the air gauge and its hose
            box(-0.3f, 0.78f, 0.37f, 0.05f, 0.05f, 0.01f, STEEL);
            box(-0.3f, 0.78f, 0.38f, 0.035f, 0.035f, 0.005f, JACK_LABEL);
            box(-0.43f, 0.5f, 0.2f, 0.012f, 0.3f, 0.012f, HOSE);
        }

        void washer() {
            // the drum the tub sits on, the tub of dirty solvent, its front toward +z
            box(0, 0.3f, 0, 0.28f, 0.3f, 0.28f, WASHER_GREEN);
            for (int i = 0; i < 3; i++) {
                box(0, 0.12f + i * 0.2f, 0, 0.29f, 0.012f, 0.29f, STEEL);    // the drum's rolling hoops
            }
            box(0, 0.78f, 0.02f, 0.46f, 0.15f, 0.32f, WASHER_GREEN);
            box(0, 0.86f, 0.02f, 0.42f, 0.01f, 0.28f, SOLVENT);
            box(0, 0.93f, 0.33f, 0.46f, 0.012f, 0.012f, STEEL);            // the rim at the front
            // the lid propped open at the back, and its fusible link
            obox(new Vector3f(0, 1.2f, -0.36f), new Vector3f(1, 0, 0), new Vector3f(0, 0.94f, -0.34f).normalize(),
                    new Vector3f(0, 0.34f, 0.94f).normalize(), 0.46f, 0.3f, 0.012f, WASHER_GREEN);
            box(0.38f, 1.0f, -0.3f, 0.008f, 0.08f, 0.008f, CHROME);
            // the tap over the tub with its hose down into the drum, and a brush left in the solvent
            box(-0.25f, 1.05f, -0.2f, 0.015f, 0.12f, 0.015f, CHROME);
            box(-0.25f, 1.16f, -0.12f, 0.015f, 0.015f, 0.08f, CHROME);
            box(-0.4f, 0.55f, -0.29f, 0.012f, 0.4f, 0.012f, HOSE);
            rod(new Vector3f(0.18f, 0.86f, 0.1f), new Vector3f(0.3f, 1.08f, 0.2f), 0.015f, BRUSH);
            box(0.17f, 0.85f, 0.09f, 0.03f, 0.02f, 0.05f, CABLE);
            box(0, 0.6f, 0.285f, 0.12f, 0.08f, 0.004f, JACK_LABEL);        // the solvent warning label
        }
    }
}
