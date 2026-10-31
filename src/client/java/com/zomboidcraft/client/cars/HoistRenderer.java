package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarHoist;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Draws a two-post car lift ({@link CarHoist}) from its painted atlas (tools/hoist/paint.py): the posts with
 * their base plates, anchor bolts, hazard-painted feet, lock ladders, maker's plates and caps, the overhead
 * beam with its hydraulic hose, the carriages riding up the posts, four telescoping swing arms reaching from
 * stowed (along the bay) to under the car's lift points with their screw pads, and the first post's control
 * box (green up, red stop, lamp, isolator) and power unit (motor, pump, reservoir, the hand lever).
 *
 * <p>Every face is flat-shaded by which way it faces, as the toon kits are: tops full, sides a step
 * darker, undersides darkest.
 */
public class HoistRenderer extends EntityRenderer<CarHoist, HoistRenderer.State> {
    static final Identifier ATLAS = ZomboidCraft.id("textures/entity/hoist/atlas.png");
    /** Tiles in the atlas, row-major, 8 x 8 (keep in step with tools/hoist/paint.py). */
    private static final int BLUE = 0, STEEL = 1, RUBBER = 2, CHROME = 3, RESERVOIR = 4, BOX = 5, GREEN = 6, RED = 7, LAMP = 8,
            HOSE = 9, HAZARD = 10, PLATE = 11, MOTOR = 12, BASE = 13, KNOB = 14, LADDER = 15, CABLE = 16, STICKER = 17,
            BLUE_DARK = 18, PAD = 19;

    public static class State extends EntityRenderState {
        float height, arms, yaw, reachX, reachZ;
        boolean lampOn;
    }

    public HoistRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(CarHoist hoist, float partialTick) {
        return hoist.getBoundingBox().inflate(2.5, 4.5, 2.5).move(0, 2, 0);
    }

    @Override
    public void extractRenderState(CarHoist hoist, State state, float partialTick) {
        super.extractRenderState(hoist, state, partialTick);
        state.height = hoist.heightO + (hoist.height() - hoist.heightO) * partialTick;
        state.arms = hoist.armsO + (hoist.arms() - hoist.armsO) * partialTick;
        state.yaw = hoist.getYRot();
        state.reachX = hoist.reachX();
        state.reachZ = hoist.reachZ();
        state.lampOn = hoist.height() > 0.01f || hoist.arms() > 0.01f;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(ATLAS), (pose, out) -> new Painter(state, pose, out).draw());
    }

    /** Builds the lift face by face in its own frame (x across the bay, y up, z along it), turned to its yaw. */
    private static final class Painter {
        final State st;
        final PoseStack.Pose pose;
        final VertexConsumer out;
        final float cos, sin;

        Painter(State st, PoseStack.Pose pose, VertexConsumer out) {
            this.st = st;
            this.pose = pose;
            this.out = out;
            double t = -Math.toRadians(st.yaw);
            cos = (float) Math.cos(t);
            sin = (float) Math.sin(t);
        }

        void draw() {
            float h = st.height;
            for (int s = -1; s <= 1; s += 2) {
                float x = s * CarHoist.POST_X, in = -s;
                // the foot: base plate, its four anchor bolts, the hazard band
                box(x, 0.012f, 0, 0.3f, 0.012f, 0.32f, BASE);
                for (int bx = -1; bx <= 1; bx += 2) {
                    for (int bz = -1; bz <= 1; bz += 2) {
                        box(x + bx * 0.24f, 0.04f, bz * 0.26f, 0.025f, 0.02f, 0.025f, STEEL);
                    }
                }
                box(x, 0.16f, 0, CarHoist.POST_W / 2 + 0.006f, 0.13f, CarHoist.POST_D / 2 + 0.006f, HAZARD);
                // the post, the lock ladder up its inside, the cap, the maker's plate and the warning sticker
                box(x, CarHoist.POST_H / 2, 0, CarHoist.POST_W / 2, CarHoist.POST_H / 2, CarHoist.POST_D / 2, BLUE);
                box(x + in * (CarHoist.POST_W / 2 + 0.004f), 1.75f, 0, 0.004f, 1.45f, 0.07f, LADDER);
                box(x, CarHoist.POST_H + 0.02f, 0, CarHoist.POST_W / 2 + 0.02f, 0.02f, CarHoist.POST_D / 2 + 0.02f, STEEL);
                box(x - in * (CarHoist.POST_W / 2 + 0.003f), 2.45f, 0, 0.003f, 0.36f, 0.055f, PLATE);
                box(x - in * (CarHoist.POST_W / 2 + 0.003f), 1.72f, 0.1f, 0.003f, 0.05f, 0.04f, STICKER);
                // the carriage riding the inside, its slide blocks and the lock pawl at its top
                float cx = x + in * (CarHoist.POST_W / 2 + 0.07f);
                box(cx, h + 0.45f, 0, 0.065f, 0.42f, 0.2f, BLUE_DARK);
                box(cx - in * 0.07f, h + 0.2f, 0, 0.008f, 0.08f, 0.16f, STEEL);
                box(cx - in * 0.07f, h + 0.7f, 0, 0.008f, 0.08f, 0.16f, STEEL);
                box(cx - in * 0.05f, h + 0.9f, 0, 0.02f, 0.03f, 0.04f, STEEL);
                // the swing arms, front and back
                for (int zs = -1; zs <= 1; zs += 2) {
                    arm(new Vector3f(cx + in * 0.06f, h + 0.1f, zs * 0.17f), s, zs, h);
                }
            }
            // the overhead beam joining the posts, and the hose along it
            box(0, CarHoist.POST_H + 0.12f, 0, CarHoist.POST_X + 0.15f, 0.09f, 0.1f, BLUE);
            box(0, CarHoist.POST_H + 0.0f, 0.13f, CarHoist.POST_X, 0.016f, 0.016f, HOSE);
            float xa = CarHoist.POST_X;
            box(xa + 0.17f, 1.95f, 0.17f, 0.016f, 1.65f, 0.016f, HOSE);
            controls(xa + CarHoist.POST_W / 2);
        }

        /** One swing arm from its pivot: a sleeve and the tube sliding out of it to the pad, stowed along the bay or reaching in. */
        void arm(Vector3f pivot, int side, int zs, float h) {
            Vector3f stowed = new Vector3f(0, 0, zs * 0.95f);
            Vector3f reach = new Vector3f(side * st.reachX - pivot.x, 0, zs * st.reachZ - pivot.z);
            float a = st.arms * st.arms * (3 - 2 * st.arms);
            // swing the angle, and slide the tube out as it comes round
            double angStow = Math.atan2(stowed.x, stowed.z), angReach = Math.atan2(reach.x, reach.z);
            double d = angReach - angStow;
            while (d > Math.PI) d -= 2 * Math.PI;
            while (d < -Math.PI) d += 2 * Math.PI;
            double ang = angStow + d * a;
            float len = stowed.length() + (reach.length() - stowed.length()) * a;
            Vector3f dir = new Vector3f((float) Math.sin(ang), 0, (float) Math.cos(ang));
            Vector3f up = new Vector3f(0, 1, 0), across = new Vector3f(dir).cross(up).normalize();
            float sleeve = Math.min(0.6f, len * 0.6f);
            obox(new Vector3f(pivot).add(new Vector3f(dir).mul(sleeve / 2)), dir, up, across, sleeve / 2, 0.045f, 0.06f, STEEL);
            obox(new Vector3f(pivot).add(new Vector3f(dir).mul((sleeve + len) / 2)), dir, up, across, (len - sleeve) / 2 + 0.05f,
                    0.035f, 0.045f, BLUE_DARK);
            Vector3f end = new Vector3f(pivot).add(new Vector3f(dir).mul(len));
            // the pivot pin, the screw pedestal and its rubber pad
            box(pivot.x, pivot.y + 0.05f, pivot.z, 0.03f, 0.06f, 0.03f, CHROME);
            box(end.x, end.y + 0.08f, end.z, 0.025f, 0.05f, 0.025f, CHROME);
            box(end.x, end.y + 0.14f, end.z, 0.075f, 0.018f, 0.075f, PAD);
            box(end.x, end.y + 0.162f, end.z, 0.06f, 0.006f, 0.06f, RUBBER);
        }

        /** The control box and the power unit on the first post's outside face (x = face). */
        void controls(float face) {
            // the box, its buttons and the isolator switch
            box(face + 0.06f, 1.35f, -0.05f, 0.055f, 0.16f, 0.11f, BOX);
            box(face + 0.12f, 1.27f, -0.11f, 0.012f, 0.018f, 0.018f, GREEN);
            box(face + 0.13f, 1.27f, -0.05f, 0.022f, 0.026f, 0.026f, RED);
            box(face + 0.12f, 1.27f, 0.01f, 0.012f, 0.016f, 0.016f, st.lampOn ? LAMP : STEEL);
            box(face + 0.125f, 1.44f, 0.03f, 0.016f, 0.035f, 0.035f, RED);
            box(face + 0.06f, 1.12f, -0.05f, 0.012f, 0.07f, 0.012f, CABLE);
            // the motor, the pump block, the reservoir with the oil showing through, and the hand lever
            box(face + 0.1f, 1.02f, 0.12f, 0.085f, 0.13f, 0.085f, MOTOR);
            box(face + 0.1f, 0.86f, 0.12f, 0.09f, 0.035f, 0.09f, STEEL);
            box(face + 0.1f, 0.6f, 0.12f, 0.08f, 0.22f, 0.08f, RESERVOIR);
            Vector3f pivot = new Vector3f(face + 0.17f, 0.86f, 0.18f);
            Vector3f knob = new Vector3f((float) CarHoist.LEVER.x + 0.02f, (float) CarHoist.LEVER.y, (float) CarHoist.LEVER.z + 0.06f);
            Vector3f dir = new Vector3f(knob).sub(pivot);
            float len = dir.length();
            dir.normalize();
            Vector3f side = new Vector3f(0, 0, 1), up = new Vector3f(dir).cross(side).normalize();
            obox(new Vector3f(pivot).add(new Vector3f(dir).mul(len / 2)), dir, up, side, len / 2, 0.008f, 0.008f, CHROME);
            box(knob.x, knob.y, knob.z, 0.022f, 0.022f, 0.022f, KNOB);
            // the power cable down to the floor and off toward the wall
            box(face + 0.02f, 0.5f, -0.12f, 0.01f, 0.5f, 0.01f, CABLE);
            box(face + 0.4f, 0.01f, -0.12f, 0.38f, 0.01f, 0.01f, CABLE);
        }

        /** An axis-lined box in the lift's frame: centre and half sizes. */
        void box(float x, float y, float z, float hx, float hy, float hz, int tile) {
            obox(new Vector3f(x, y, z), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1), hx, hy, hz, tile);
        }

        /** A box about centre c along three unit axes with half sizes along each. */
        void obox(Vector3f c, Vector3f a, Vector3f b, Vector3f d, float ha, float hb, float hd, int tile) {
            Vector3f[] k = new Vector3f[8];
            for (int i = 0; i < 8; i++) {
                k[i] = new Vector3f(c).add(new Vector3f(a).mul((i & 1) == 0 ? -ha : ha))
                        .add(new Vector3f(b).mul((i & 2) == 0 ? -hb : hb))
                        .add(new Vector3f(d).mul((i & 4) == 0 ? -hd : hd));
            }
            int[][] faces = {{0, 2, 6, 4}, {1, 3, 7, 5}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 1, 3, 2}, {4, 5, 7, 6}};
            float u0 = (tile % 8) / 8f + 0.002f, v0 = (tile / 8) / 8f + 0.002f, du = 1 / 8f - 0.004f;
            for (int[] f : faces) {
                Vector3f mid = new Vector3f(k[f[0]]).add(k[f[1]]).add(k[f[2]]).add(k[f[3]]).mul(0.25f);
                Vector3f n = new Vector3f(mid).sub(c).normalize();
                Vector3f e1 = new Vector3f(k[f[1]]).sub(k[f[0]]), e2 = new Vector3f(k[f[2]]).sub(k[f[0]]);
                boolean flip = new Vector3f(e1).cross(e2).dot(n) < 0;
                Vector3f wn = turn(n);
                // toon steps: tops lit, sides a shade down, undersides dark
                float shade = wn.y > 0.5f ? 1f : wn.y < -0.5f ? 0.5f : Math.abs(wn.x) > Math.abs(wn.z) ? 0.8f : 0.68f;
                int g = (int) (shade * 255);
                int colour = 0xFF000000 | g << 16 | g << 8 | g;
                float[][] uv = {{u0, v0 + du}, {u0 + du, v0 + du}, {u0 + du, v0}, {u0, v0}};
                for (int i = 0; i < 4; i++) {
                    Vector3f p = turn(k[f[flip ? 3 - i : i]]);
                    out.addVertex(pose, p.x, p.y, p.z).setColor(colour).setUv(uv[i][0], uv[i][1])
                            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(st.lightCoords).setNormal(pose, wn.x, wn.y, wn.z);
                }
            }
        }

        /** The lift's frame turned to its yaw (as Vec3.yRot does), about its foot. */
        Vector3f turn(Vector3f v) {
            return new Vector3f(v.x * cos + v.z * sin, v.y, v.z * cos - v.x * sin);
        }
    }
}
