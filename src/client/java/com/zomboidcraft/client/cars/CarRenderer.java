package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Draws a car's 3D model, each triangle sent as a quad with its last corner doubled: the body,
 * boot lids swung open while it's searched, wheels rolled and steered, then the glass see-through.
 * Cars further off are drawn from coarser copies of the mesh ({@link CarLod}), with solid
 * windows past a point.
 */
public class CarRenderer extends EntityRenderer<CarProp, CarRenderState> {
    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public CarRenderState createRenderState() {
        return new CarRenderState();
    }

    @Override
    public void extractRenderState(CarProp car, CarRenderState state, float partialTick) {
        super.extractRenderState(car, state, partialTick);
        state.model = car.model();
        state.variant = car.variant();
        // turned smoothly between ticks, or a car you're steering judders against your smooth view
        state.yaw = car.getYRot(partialTick);
        // from the driver's seat the windows are left out: shaders fog see-through glass right in front of you
        Minecraft mc = Minecraft.getInstance();
        state.smashed = car.smashed();
        state.look = car.variantIndex();
        state.panes = car.aircraft() ? null : car.panes();
        if (state.wound.length != car.wound.length) {
            state.wound = new float[car.wound.length];
        }
        for (int i = 0; i < state.wound.length && i < car.woundO.length; i++) {
            state.wound[i] = car.woundO[i] + (car.wound[i] - car.woundO[i]) * partialTick;
        }
        state.inside = mc.player != null && car.hasPassenger(mc.player) && mc.options.getCameraType().isFirstPerson();
        Entity eye = mc.getCameraEntity();
        // a jet with its engines running is drawn whole three times further off, so its moving surfaces show
        double far = car.aircraft() && car.flight().rpm > 0.3f ? 9 : 1;
        state.lod = eye == null || car.hasPassenger(eye) ? 0 : CarLod.level(car.distanceToSqr(eye) / far);
        float t = car.lidOpenO + (car.lidOpen - car.lidOpenO) * partialTick;
        state.open = t * t * (3 - 2 * t);
        state.rolled = car.rolledO + (car.rolled - car.rolledO) * partialTick;
        state.steer = car.steerO + (car.steer - car.steerO) * partialTick;
        state.lights = CarLights.on(car);
        state.dash = car.engineOn() && !"burnt".equals(car.variant());
        state.braking = state.dash && car.braking > 0;
        state.reversing = state.dash && car.reversing > 0;
        boolean lit = state.lights || state.dash;
        state.dark = lit && mc.level != null ? CarLights.darkness(mc.level, car.blockPosition().above()) : 0;
        state.cockpit = CockpitGlow.powered(car, state.model, state.variant);
        state.liveCockpit = car.aircraft() && mc.player != null && car.hasPassenger(mc.player)
                && ("usable".equals(state.variant) || "abandoned".equals(state.variant)) ? car : null;
        state.cockpitDark = state.cockpit >= 0 && mc.level != null ? CarLights.darkness(mc.level, car.blockPosition().above(2)) : 0;
        state.shaders = (lit || state.cockpit >= 0) && CarLights.shaderPackOn();
        CarClient.Target target = CarClient.target(car);
        state.highlight = target == null ? null : target.at();
        state.highlightSize = target == null ? 0 : target.size();
        state.wheelTurn = CarHands.wheelTurn(car, partialTick);
        CarModels.Model m = CarModels.get(car.model());
        boolean driving = state.inside && car.seatOf(mc.player) == 0 && m != null && m.steering != null && m.seats != null;
        state.arms = driving ? CarHands.onWheel(mc.player) : CarHands.NONE;
        state.driverEye = driving ? m.seats[0] : null;
        state.air = car.aircraft();
        net.minecraft.world.entity.player.Player gunner = m != null && m.turret != null ? com.zomboidcraft.world.cars.Turret.gunner(car) : null;
        state.turretYaw = gunner == null ? 0 : com.zomboidcraft.world.cars.Turret.yaw(gunner.getYRot(partialTick), state.yaw);
        state.turretTilt = gunner == null ? 0 : com.zomboidcraft.world.cars.Turret.pitch(m.turret, gunner.getXRot(partialTick));
        state.barrels = car.barrelsO + (car.barrels - car.barrelsO) * partialTick;
        state.turretFiring = car.turretFiring();
        state.aircraft = state.air ? car : null;
        state.broken = state.air && car.broken();
        if (state.air) {
            com.zomboidcraft.world.cars.Flight f = car.flight();
            f.qO.slerp(f.q, partialTick, state.attitude);
            state.pivot = car.pivot();
            state.rotor = car.rotorO + (car.rotor - car.rotorO) * partialTick;
        }
    }

    /** A flyby still drawn from far off (FarFlybys) hides the real one there, which trails a little behind it. */
    @Override
    public boolean shouldRender(CarProp car, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z, float partialTick) {
        return !FarFlybys.covers(car) && super.shouldRender(car, frustum, x, y, z, partialTick);
    }

    /** The whole car, not the entity's small box, decides whether it's on screen. */
    @Override
    protected AABB getBoundingBoxForCulling(CarProp car, float partialTick) {
        CarModels.Model m = CarModels.get(car.model());
        double r = m == null ? 3 : Math.hypot(m.size[0], m.size[2]) / 2;
        double h = m == null ? 2 : m.size[1];
        return new AABB(car.getX() - r, car.getY(), car.getZ() - r, car.getX() + r, car.getY() + h, car.getZ() + r);
    }

    @Override
    public void submit(CarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        // the shadow map only needs a car's outline: a near car casts it from a coarser copy (the profile showed every
        // parked car drawn twice at full detail), unless you're sitting in it
        int lod = state.lod;
        if (lod < SHADOW_LOD && !state.inside && com.zomboidcraft.client.zombie.body.ZDetail.shadowPass()) {
            state.lod = SHADOW_LOD;
        }
        try {
            draw(state, poseStack, collector, camera);
        } finally {
            state.lod = lod;
        }
    }

    /** The coarsest copy a car casts its shadow from ({@link CarLod}: 0.16 m grid, close enough not to show). */
    private static final int SHADOW_LOD = 2;

    private void draw(CarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        CarAssets.Ready car;
        if (state.broken) {
            CarAssets.Broken broken = CarAssets.broken(state.model, state.variant);
            car = broken == null ? null : broken.remains();
        } else {
            car = CarAssets.get(state.model, state.variant, state.lod);
        }
        if (car == null) {
            return;
        }
        int light = state.lightCoords;
        poseStack.pushPose();
        if (state.air) {
            poseStack.translate(0, state.pivot, 0);
            poseStack.mulPose(new org.joml.Matrix4f().rotation(state.attitude));
            poseStack.translate(0, -state.pivot, 0);
        } else {
            poseStack.rotateDegrees(Axis.YP, -state.yaw);
        }
        Turn[] lids = new Turn[car.lids().size()];
        for (int i = 0; i < lids.length; i++) {
            lids[i] = lid(car.lids().get(i), state.open);
        }
        Turn[] wheels = new Turn[car.wheels().size()];
        for (int i = 0; i < wheels.length; i++) {
            wheels[i] = wheel(car.wheels().get(i), state.rolled, state.steer);
        }
        Turn[] rotors = new Turn[car.rotors().size()];
        for (int i = 0; i < rotors.length; i++) {
            // the tail rotor turns about five times for each turn of the main one
            rotors[i] = spin(car.rotors().get(i), i == 0 ? state.rotor : state.rotor * 4.9);
        }
        Turn[] turret = turret(car.turret(), state);
        boolean solidGlass = state.lod >= CarLod.SOLID_GLASS && !state.smashed;
        CarAssets.Ready full = !state.lights && !state.dash ? null : state.lod == 0 ? car : CarAssets.get(state.model, state.variant, 0);
        CarLamps.Lamps lamps = full == null ? null : CarLamps.get(state.model, state.variant, full);
        float[] glow = lamps != null && state.dash && state.lod == 0 && state.dark > 0.05f ? cabinGlow(lamps, state.dark) : null;
        CockpitGlow.Glow cockpit = state.cockpit >= 0 && state.lod <= 1 ? CockpitGlow.get(state.model) : null;
        if (glow == null && cockpit != null && state.lod == 0) {
            glow = CockpitGlow.cabin(cockpit, state.cockpit, state.cockpitDark);
        }
        float[] cabin = glow;
        java.util.BitSet panel = lamps != null && state.dash && state.lod == 0 ? lamps.panel() : null;
        CarModels.Model model = CarModels.get(state.model);
        // a car's steering wheel turns with the steering; an aircraft's own stick with the pilot's hand
        java.util.BitSet steering = state.lod != 0 ? null : state.air
                ? state.liveCockpit == null ? null : AirControls.meshStick(car.mesh(), state.model)
                : CarSteering.of(car.mesh(), model);
        Turn steer = steering == null ? null : state.air ? AirControls.meshTurn(state.liveCockpit, state.model)
                : state.wheelTurn == 0 ? null : steer(model.steering, state.wheelTurn);
        // a jet's control surfaces, swung on their hinges
        AirSurfaces.Set surfaces = state.aircraft != null && state.lod == 0 && !state.broken ? AirSurfaces.of(state.model, car.mesh()) : null;
        Turn[] surface = surfaces == null ? null : AirSurfaces.turns(state.aircraft, state.model, surfaces);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(car.texture()), (pose, out) -> {
            each(car, lids, wheels, rotors, turret, (mesh, turn) -> {
                if (mesh == car.mesh() && (panel != null && !panel.isEmpty() || steer != null || surface != null)) {
                    // the backlit dials and displays at full brightness, the steering wheel turned, the surfaces on their hinges, the rest as usual
                    int t = 0;
                    while (t < mesh.opaque()) {
                        boolean lit = panel != null && panel.get(t), wheel = steer != null && steering.get(t);
                        int end = Math.min(change(panel, lit, t, mesh.opaque()), change(steer == null ? null : steering, wheel, t, mesh.opaque()));
                        int on = surface == null ? -1 : surfaces.part()[t];
                        if (surface != null) {
                            end = Math.min(end, surfaces.end()[t]);
                        }
                        Turn moved = wheel ? steer : on >= 0 && surface[on] != null ? surface[on] : turn;
                        draw(mesh, t, end, moved, pose, out, lit ? CarLights.FULL_BRIGHT : light, -1, lit ? null : cabin);
                        t = end;
                    }
                } else {
                    draw(mesh, 0, mesh.opaque(), turn, pose, out, light, -1, mesh == car.mesh() ? cabin : null);
                }
                if (solidGlass) {
                    draw(mesh, mesh.opaque(), mesh.triangles(), turn, pose, out, light, FAR_GLASS, null);
                }
            });
        });
        // close up each window's its own: shattered ones gone, wound-down ones in the door, and their marks on them,
        // drawn from inside too (the dust on the glass says it's there)
        CarGlassDraw.Set windows = CarGlassDraw.of(state, car, model);
        if (!state.inside && !state.smashed && !solidGlass) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(car.texture()), (pose, out) -> {
                each(car, lids, wheels, rotors, turret, (mesh, turn) -> {
                    if (windows != null) {
                        CarGlassDraw.glass(windows, mesh, turn, pose, out, light, GLASS);
                    } else {
                        draw(mesh, mesh.opaque(), mesh.triangles(), turn, pose, out, light, GLASS, null);
                    }
                });
            });
        }
        if (windows != null && !state.smashed) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(CarGlassDraw.MARKS), (pose, out) ->
                    each(car, lids, wheels, rotors, turret, (mesh, turn) -> CarGlassDraw.marks(windows, mesh, turn, pose, out, light)));
        }
        // the instrument panel drawn sharp over a blurred one, in the aircraft you're in, powered or not
        CockpitPanel.Panel facePanel = state.liveCockpit != null && state.lod == 0
                ? CockpitPanel.get(state.model, CockpitGlow.get(state.model), CockpitDisplays.eye(state.model)) : null;
        if (facePanel != null) {
            CockpitPages.Data data = CockpitDisplays.data(state.liveCockpit, state.attitude, System.currentTimeMillis(), false);
            CockpitPanel.submit(facePanel, data, poseStack, collector, light, state.cockpitDark, cockpit != null, state.shaders);
        }
        if (cockpit != null) {
            int[] colours = CockpitGlow.colours(cockpit, state.cockpit, state.cockpitDark, state.shaders);
            // the aircraft you're in shows the flight live on its screens and its head-up display
            CockpitDisplays.Set live = state.liveCockpit != null && state.cockpit >= 0 ? CockpitDisplays.of(state.model, cockpit) : null;
            if (live != null) {
                CockpitDisplays.paint(state.liveCockpit, live, state.attitude, CockpitDisplays.eye(state.model));
                for (CockpitDisplays.Screen s : live.screens()) {
                    int colour = colours[s.group];
                    collector.submitCustomGeometry(poseStack, CockpitDisplays.type(s, state.shaders),
                            (pose, out) -> CockpitDisplays.draw(s, colour, pose, out));
                }
            }
            if (live != null && CockpitBezels.has(state.model)) {
                CockpitBezels.submit(state.model, live, colours[0], poseStack, collector, light, state.shaders);
            }
            java.util.BitSet skip = live == null ? null : live.groups();
            java.util.BitSet bezels = CockpitBezels.covered(state.model, cockpit, live);
            java.util.BitSet covered = facePanel == null ? bezels : facePanel.covered;
            collector.submitCustomGeometry(poseStack, CockpitGlow.type(cockpit, state.shaders),
                    (pose, out) -> CockpitGlow.draw(cockpit, colours, skip, covered, pose, out));
        }
        // the camera, in the car's own frame, for the glow round each lamp and the green highlight
        double dx = camera.pos.x - state.x, dz = camera.pos.z - state.z, r = Math.toRadians(state.yaw);
        float[] eye = {(float) (dx * Math.cos(r) + dz * Math.sin(r)), (float) (camera.pos.y - state.y),
                (float) (-dx * Math.sin(r) + dz * Math.cos(r))};
        if (lamps != null) {
            collector.submitCustomGeometry(poseStack, CarLights.glowType(state.shaders),
                    (pose, out) -> lamps(state, lamps, full, lids, eye, pose, out));
        }
        if (state.highlight != null) {
            float[] h = state.highlight;
            float size = state.highlightSize;
            // a shader pack's on or not, whatever the car's lamps are doing (state.shaders is only set for lit cars)
            boolean shaders = CarLights.shaderPackOn();
            collector.submitCustomGeometry(poseStack, com.zomboidcraft.client.interact.Interactables.type(shaders),
                    (pose, out) -> highlight(h, size, eye, shaders, pose, out));
        }
        if (state.arms != CarHands.NONE && state.driverEye != null && model != null && model.steering != null) {
            CarArms.draw(poseStack, collector, light, state.driverEye, model.steering, state.wheelTurn, state.arms);
        }
        if (state.liveCockpit != null && state.lod == 0) {
            // stick and throttle, and the pilot's hands on them
            AirControls.draw(state.liveCockpit, state.model, CockpitDisplays.eye(state.model),
                    state.inside && Minecraft.getInstance().player != null && state.liveCockpit.seatOf(Minecraft.getInstance().player) == 0, poseStack, collector, light);
        }
        poseStack.popPose();
    }

    /** Lamp colours: a warm headlight, tail and brake red, reversing white, and the dash's green. */
    private static final float[] HEAD = {1f, 0.93f, 0.78f}, RED = {1f, 0.1f, 0.06f}, WHITE = {1f, 1f, 1f}, DASH = {0.4f, 1f, 0.6f};

    /**
     * The lit lamps: each lens redrawn glowing just in front of itself, then a soft glow round each lamp
     * facing you, in the dark. Tail lights are dim until the brakes go on; reversing lights only in reverse.
     */
    private static void lamps(CarRenderState state, CarLamps.Lamps lamps, CarAssets.Ready full, Turn[] lids, float[] eye,
                              PoseStack.Pose pose, VertexConsumer out) {
        float day = 0.5f + 0.5f * state.dark;
        for (CarLamps.Run run : lamps.runs()) {
            float[] rgb;
            float strength;
            switch (run.kind()) {
                case CarLamps.HEAD -> { rgb = HEAD; strength = state.lights ? 0.9f * day : 0; }
                case CarLamps.TAIL -> { rgb = RED; strength = state.braking ? 1f : state.lights ? 0.45f * day : 0; }
                case CarLamps.REVERSE -> { rgb = WHITE; strength = state.reversing ? 0.9f : 0; }
                default -> { rgb = DASH; strength = state.dash ? 0.75f * day : 0; }
            }
            if (strength <= 0) {
                continue;
            }
            Turn turn = run.mesh() > 0 && run.mesh() - 1 < lids.length ? lids[run.mesh() - 1] : null;
            glowing(CarLamps.mesh(full, run.mesh()), run.start(), run.start() + run.count(), turn, pose, out,
                    CarLights.shade(rgb, strength, state.shaders));
        }
        if (state.dark < 0.03f) {
            return;
        }
        for (CarLamps.Spot spot : lamps.spots()) {
            float[] rgb;
            float strength, size;
            switch (spot.kind()) {
                case CarLamps.HEAD -> { rgb = HEAD; strength = state.lights ? 0.5f : 0; size = 1.6f; }
                case CarLamps.TAIL -> { rgb = RED; strength = state.braking ? 0.65f : state.lights ? 0.3f : 0; size = state.braking ? 1.5f : 1.1f; }
                case CarLamps.REVERSE -> { rgb = WHITE; strength = state.reversing ? 0.4f : 0; size = 1.2f; }
                default -> { continue; }
            }
            float cx = spot.x() + spot.nx() * 0.06f, cy = spot.y() + spot.ny() * 0.06f, cz = spot.z() + spot.nz() * 0.06f;
            float tx = eye[0] - cx, ty = eye[1] - cy, tz = eye[2] - cz;
            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (strength <= 0 || len < 0.3f) {
                continue;
            }
            tx /= len; ty /= len; tz /= len;
            float facing = tx * spot.nx() + ty * spot.ny() + tz * spot.nz();
            if (facing < 0.05f) {
                continue;       // seen from behind the lamp
            }
            halo(cx, cy, cz, tx, ty, tz, spot.size() * size, pose, out,
                    CarLights.shade(rgb, strength * state.dark * (float) Math.sqrt(facing), state.shaders));
        }
    }

    /** The interact key's target lit green: the same small dot as on everything else you can use. */
    private static void highlight(float[] h, float size, float[] eye, boolean shaders, PoseStack.Pose pose, VertexConsumer out) {
        com.zomboidcraft.client.interact.Interactables.dot(h[0], h[1], h[2], eye[0] - h[0], eye[1] - h[1], eye[2] - h[2], size,
                shaders, pose, out);
    }

    /** Where a run of triangles that are (or aren't) in a set ends, from t; to for no set. */
    private static int change(java.util.BitSet set, boolean in, int t, int to) {
        if (set == null) {
            return to;
        }
        int next = in ? set.nextClearBit(t) : set.nextSetBit(t);
        return next < 0 ? to : Math.min(next, to);
    }

    /** The steering wheel turned about its column by angle radians. */
    private static Turn steer(float[] wheel, float angle) {
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        return (v, normal) -> CarSteering.turn(v, normal, wheel, cos, sin);
    }

    /** Triangles from to end redrawn a few millimetres proud of the car, in one flat glowing colour. */
    private static void glowing(CarAssets.Mesh mesh, int from, int end, Turn turn, PoseStack.Pose pose, VertexConsumer out, int colour) {
        float[] p = mesh.pos(), n = mesh.normal();
        float[] v = new float[3], m = new float[3];
        for (int t = from; t < end; t++) {
            m[0] = n[t * 3]; m[1] = n[t * 3 + 1]; m[2] = n[t * 3 + 2];
            if (turn != null) {
                turn.apply(m, true);
            }
            for (int c = 0; c < 4; c++) {
                int i = t * 3 + Math.min(c, 2);
                v[0] = p[i * 3]; v[1] = p[i * 3 + 1]; v[2] = p[i * 3 + 2];
                if (turn != null) {
                    turn.apply(v, false);
                }
                out.addVertex(pose, v[0] + m[0] * 0.004f, v[1] + m[1] * 0.004f, v[2] + m[2] * 0.004f)
                        .setColor(colour)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(CarLights.FULL_BRIGHT)
                        .setNormal(pose, m[0], m[1], m[2]);
            }
        }
    }

    /** A soft disc of light at c, half-size half, turned to face the way t (toward the camera). */
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
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(CarLights.FULL_BRIGHT)
                    .setNormal(pose, tx, ty, tz);
        }
    }

    /**
     * Where the dash's glow comes from, a little back from the dials toward the driver, and how strong
     * it is: {x, y, z, strength}.
     */
    private static float[] cabinGlow(CarLamps.Lamps lamps, float dark) {
        CarLamps.Spot dash = lamps.first(CarLamps.DASH);
        if (dash == null) {
            return null;
        }
        return new float[]{dash.x() + dash.nx() * 0.3f, dash.y() + dash.ny() * 0.3f, dash.z() + dash.nz() * 0.3f, dark};
    }

    /** Glass keeps its colour from the texture but lets most of the light through. */
    private static final int GLASS = 0x80FFFFFF;
    /** Far off, glass is drawn solid, darkened to look like the dim inside seen through it. */
    private static final int FAR_GLASS = 0xFF5A6068;

    /** Moves a point (or, with normal set, a direction) of a moving part into place, in place. */
    interface Turn {
        void apply(float[] v, boolean normal);
    }

    private static void each(CarAssets.Ready car, Turn[] lids, Turn[] wheels, Turn[] rotors, Turn[] turret,
                             java.util.function.BiConsumer<CarAssets.Mesh, Turn> part) {
        part.accept(car.mesh(), null);
        for (int i = 0; i < turret.length; i++) {
            part.accept(car.turret().get(i).mesh(), turret[i]);
        }
        for (int i = 0; i < rotors.length; i++) {
            part.accept(car.rotors().get(i).mesh(), rotors[i]);
        }
        for (int i = 0; i < lids.length; i++) {
            part.accept(car.lids().get(i).mesh(), lids[i]);
        }
        for (int i = 0; i < wheels.length; i++) {
            part.accept(car.wheels().get(i).mesh(), wheels[i]);
        }
    }

    /**
     * A mounted gun's parts moved (CarAssets.Ready.turret): the post turned on its upright axis, the gun tilted on the
     * post and turned with it, the barrels spun in the gun, tilted and turned with it.
     */
    private static Turn[] turret(java.util.List<CarAssets.Rotor> parts, CarRenderState state) {
        Turn[] out = new Turn[parts.size()];
        Turn yaw = null, tilt = null;
        for (int i = 0; i < out.length; i++) {
            CarAssets.Rotor r = parts.get(i);
            if (i == 0) {
                yaw = state.turretYaw == 0 ? null : spin(r, state.turretYaw);
                out[i] = yaw;
            } else if (i == 1) {
                Turn own = state.turretTilt == 0 ? null : spin(r, state.turretTilt), post = yaw;
                tilt = then(own, post);
                out[i] = tilt;
            } else {
                out[i] = then(spin(r, state.barrels), tilt);
            }
        }
        return out;
    }

    /** first, then second (either may be null: nothing moves). */
    private static Turn then(Turn first, Turn second) {
        if (first == null || second == null) {
            return first == null ? second : first;
        }
        return (v, normal) -> {
            first.apply(v, normal);
            second.apply(v, normal);
        };
    }

    /** A lid turned about its hinge by open (0 to 1) of its full swing. */
    private static Turn lid(CarAssets.Lid lid, float open) {
        float a = lid.angle() * open;
        if (a == 0) {
            return null;
        }
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        return (v, normal) -> {
            if (normal) {
                rotate(v, lid.yAxis(), cos, sin);
                return;
            }
            v[0] -= lid.px(); v[1] -= lid.py(); v[2] -= lid.pz();
            rotate(v, lid.yAxis(), cos, sin);
            v[0] += lid.px(); v[1] += lid.py(); v[2] += lid.pz();
        };
    }

    /** A rotor turned by angle radians about its axis (Rodrigues' rotation). */
    private static Turn spin(CarAssets.Rotor r, double angle) {
        float c = (float) Math.cos(angle), sn = (float) Math.sin(angle);
        float kx = r.ax(), ky = r.ay(), kz = r.az();
        return (v, normal) -> {
            if (!normal) {
                v[0] -= r.px(); v[1] -= r.py(); v[2] -= r.pz();
            }
            float dot = kx * v[0] + ky * v[1] + kz * v[2];
            float cx = ky * v[2] - kz * v[1], cy = kz * v[0] - kx * v[2], cz = kx * v[1] - ky * v[0];
            float x = v[0] * c + cx * sn + kx * dot * (1 - c);
            float y = v[1] * c + cy * sn + ky * dot * (1 - c);
            float z = v[2] * c + cz * sn + kz * dot * (1 - c);
            v[0] = x; v[1] = y; v[2] = z;
            if (!normal) {
                v[0] += r.px(); v[1] += r.py(); v[2] += r.pz();
            }
        };
    }

    /** A wheel rolled on by rolled metres and, if it's a front wheel, steered by steer radians (left +). */
    private static Turn wheel(CarAssets.Wheel w, double rolled, float steer) {
        double spin = (rolled / Math.max(0.1f, w.radius())) % (Math.PI * 2);
        float sc = (float) Math.cos(spin), ss = (float) Math.sin(spin);
        float s = w.steers() ? steer : 0;
        float tc = (float) Math.cos(s), ts = (float) Math.sin(s);
        return (v, normal) -> {
            if (!normal) {
                v[0] -= w.cx(); v[1] -= w.cy(); v[2] -= w.cz();
            }
            rotate(v, false, sc, ss);
            if (s != 0) {
                rotate(v, true, tc, ts);
            }
            if (!normal) {
                v[0] += w.cx(); v[1] += w.cy(); v[2] += w.cz();
            }
        };
    }

    /**
     * Triangles from to end of a mesh as quads (last corner doubled), moved by turn if it has one; with a
     * glow ({x, y, z, strength}, from {@link #cabinGlow}), the inside round the dash is lit by it.
     */
    /** A whole loose piece (crash debris): its solid triangles, or with glass set its see-through ones. */
    static void drawPiece(CarAssets.Mesh mesh, PoseStack.Pose pose, VertexConsumer out, int light, boolean glass) {
        if (glass) {
            draw(mesh, mesh.opaque(), mesh.triangles(), null, pose, out, light, GLASS, null);
        } else {
            draw(mesh, 0, mesh.opaque(), null, pose, out, light, -1, null);
        }
    }

    private static void draw(CarAssets.Mesh mesh, int from, int end, Turn turn, PoseStack.Pose pose, VertexConsumer out, int light, int colour,
                             float[] glow) {
        float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
        int block = (light >> 4) & 0xF, sky = (light >> 20) & 0xF;
        float[] v = new float[3], m = new float[3];
        for (int t = from; t < end; t++) {
            m[0] = n[t * 3]; m[1] = n[t * 3 + 1]; m[2] = n[t * 3 + 2];
            if (turn != null) {
                turn.apply(m, true);
            }
            for (int c = 0; c < 4; c++) {
                int i = t * 3 + Math.min(c, 2);
                v[0] = p[i * 3]; v[1] = p[i * 3 + 1]; v[2] = p[i * 3 + 2];
                if (turn != null) {
                    turn.apply(v, false);
                }
                int lit = light;
                if (glow != null) {
                    float gx = glow[0] - v[0], gy = glow[1] - v[1], gz = glow[2] - v[2];
                    float d = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                    // faces turned toward the dash, near it: the cabin, not the outside of the body
                    if (d < 1.5f && gx * m[0] + gy * m[1] + gz * m[2] > 0) {
                        int b = Math.round(glow[3] * 9 * (1 - d / 1.5f));
                        if (b > block) {
                            lit = sky << 20 | b << 4;
                        }
                    }
                }
                out.addVertex(pose, v[0], v[1], v[2])
                        .setColor(colour)
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(lit)
                        .setNormal(pose, m[0], m[1], m[2]);
            }
        }
    }

    /** Same turns as tools/cars/wreck.py rot_x and rot_y. */
    private static void rotate(float[] v, boolean yAxis, float cos, float sin) {
        if (yAxis) {
            float x = v[0] * cos + v[2] * sin, z = -v[0] * sin + v[2] * cos;
            v[0] = x;
            v[2] = z;
        } else {
            float y = v[1] * cos - v[2] * sin, z = v[1] * sin + v[2] * cos;
            v[1] = y;
            v[2] = z;
        }
    }
}
