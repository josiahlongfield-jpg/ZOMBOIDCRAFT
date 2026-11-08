package com.zomboidcraft.client.operator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.combat.TarkovFit;
import com.zomboidcraft.combat.TarkovParts;
import com.zomboidcraft.mixin.client.SpecialModelRenderersAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * A Tarkov gun's own picture in a slot, on the ground and in a frame (Josiah 2026-11-08: the hotbar showed the old guns
 * they ride on, the Tarkov gun only once in hand): the gun as built, from its first-person model's "held" pose with its
 * bench parts on, side on with the muzzle right and the sights up, fitted to the slot. The gun items ask the
 * "zomboidcraft:tarkov_gun" condition first; in the hands the operator's first and third person draw it as before.
 */
public final class TarkovGunIcon implements SpecialModelRenderer<TarkovGunIcon.Arg> {
    /** How much of the slot the gun reaches along its longer side. */
    private static final float FILL = 0.94f;
    /** A sight's eye frame (x the gun's right, y up, looking down -z) turned so the muzzle points +x and its right side faces you. */
    private static final Matrix4f SIDE_ON = new Matrix4f(
            0, 0, 1, 0,
            0, 1, 0, 0,
            -1, 0, 0, 0,
            0, 0, 0, 1);
    /** Tarkov's own part frame (muzzle -y, up +z) as an eye frame. */
    private static final Matrix4f TARKOV_EYE = new Matrix4f(
            -1, 0, 0, 0,
            0, 0, 1, 0,
            0, 1, 0, 0,
            0, 0, 0, 1);

    /** What the picture is of: the model, the gun's file, and the gun as built (null for a gun without a bench kit). */
    public record Arg(String model, String spec, TarkovFit.@Nullable Assembly built) {
    }

    /** Where the gun's model space goes in the block, and its picture's height over its width. */
    private record Shape(Matrix4f place, float aspect) {
    }

    private static final Map<Arg, Shape> SHAPES = new HashMap<>();
    private static final Map<Arg, Object[][]> BUFFERS = new HashMap<>();
    private static final Map<Arg, int[]> FLIP = new HashMap<>();

    public static void register() {
        SpecialModelRenderersAccessor.zomboidcraft$types().put(ZomboidCraft.id("tarkov_gun"), Unbaked.MAP_CODEC);
        ConditionalItemModelProperties.ID_MAPPER.put(ZomboidCraft.id("tarkov_gun"), Shows.MAP_CODEC);
    }

    /** The picture a stack shows, or null when it isn't a Tarkov gun or its model isn't on this computer (yet). */
    public static @Nullable Arg arg(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.get(LocalGuns.ID) == null) return null;
        LocalGuns.Spec spec = LocalGuns.of(stack);
        String model = spec == null ? null : Operator.fpModel(stack);
        NpcSkin gun = model == null ? null : NpcSkin.get(model);
        if (gun == null || gun.clips.get("held") == null) return null;
        TarkovFit.Assembly built = TarkovFit.tarkov(spec) ? TarkovFit.of(stack) : null;
        return new Arg(model, spec.id, built);
    }

    /** The gun's picture's height over its width in its square icon, or 0 when it has none of its own. */
    public static float aspect(ItemStack stack) {
        Arg a = arg(stack);
        Shape s = a == null ? null : shape(a);
        return s == null ? 0 : s.aspect;
    }

    @Override
    public @Nullable Arg extractArgument(ItemStack stack) {
        return arg(stack);
    }

    @Override
    public void submit(@Nullable Arg a, PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        Shape shape = a == null ? null : shape(a);
        NpcSkin gun = shape == null ? null : NpcSkin.get(a.model);
        LocalGuns.Spec spec = gun == null ? null : LocalGuns.get(a.spec);
        if (spec == null) return;
        Pose p = pose(spec, gun, a.built);
        pose.pushPose();
        pose.mulPose(shape.place);
        Object[][] b = BUFFERS.computeIfAbsent(a, x -> new Object[2][]);
        int[] flip = FLIP.computeIfAbsent(a, x -> new int[1]);
        flip[0] = 1 - flip[0];
        if (b[flip[0]] == null || b[flip[0]].length != gun.groups.length * 2) b[flip[0]] = new Object[gun.groups.length * 2];
        NpcSkinDraw.submitGroups(gun.groups, p.bones, 0, g -> "gun".equals(g.piece) && TarkovRig.shows(g, p.hidden),
                g -> gun.texture(g.texture, a.model), b[flip[0]], light, OverlayTexture.NO_OVERLAY, pose, out);
        if (a.built != null && p.frames != null) {
            TarkovRig.draw(a.built, p.frames, null, "icon|" + a.spec, light, OverlayTexture.NO_OVERLAY, pose, out);
        }
        pose.popPose();
        if (BUFFERS.size() > 128) {
            BUFFERS.clear();
            FLIP.clear();
        }
    }

    /** The gun's pieces in its "held" pose: their skinning matrices, which kit pieces are off, and its parts' frames. */
    private record Pose(Matrix4f[] bones, boolean[] hidden, @Nullable Map<String, Matrix4f> frames,
                        float[] pos, float[] turn) {
    }

    private static Pose pose(LocalGuns.Spec spec, NpcSkin gun, TarkovFit.@Nullable Assembly built) {
        float[][] fp = Operator.heldPose(gun);
        float[][] hp = Operator.heldPose(gun);
        boolean[] hidden = null;
        Map<String, Matrix4f> frames = null;
        if (built != null) {
            // its flip-up irons folded under a sight, as in the hands
            TarkovRig.fold(spec, built, gun, fp[0], fp[1]);
            hidden = TarkovRig.hidden(spec, built, gun);
            frames = TarkovRig.frames(spec, built, gun, hp[0], hp[1]);
        }
        return new Pose(Operator.bones(gun, fp[0], fp[1]), hidden, frames, hp[0], hp[1]);
    }

    private static @Nullable Shape shape(Arg a) {
        Shape s = SHAPES.get(a);
        if (s != null) return s;
        NpcSkin gun = NpcSkin.get(a.model);
        LocalGuns.Spec spec = LocalGuns.get(a.spec);
        if (gun == null || spec == null) return null;
        Pose p = pose(spec, gun, a.built);
        // turned side on by its main sight's eye frame (the way it is looked down), else by its body's own frame
        Matrix4f eye = spec.tarkov != null && spec.tarkov.kit != null ? TarkovRig.mainEye(spec, gun, p.pos, p.turn) : null;
        if (eye == null) {
            for (int i = 0; i < gun.boneNames.length; i++) {
                if (gun.boneNames[i].startsWith("weapon_")) {
                    eye = TarkovRig.bone(p.pos, p.turn, i).mul(TARKOV_EYE);
                    break;
                }
            }
        }
        Matrix4f turn = new Matrix4f(SIDE_ON);
        if (eye != null) {
            Quaternionf q = eye.getUnnormalizedRotation(new Quaternionf()).normalize();
            turn.rotate(q.conjugate());
        }
        float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector3f v = new Vector3f(), w = new Vector3f();
        Matrix4f m = new Matrix4f();
        for (NpcSkin.Group g : gun.groups) {
            if (g.level != 0 || !"gun".equals(g.piece) || !TarkovRig.shows(g, p.hidden)) continue;
            for (int i = 0; i < g.vertices; i++) {
                v.set(0);
                float total = 0;
                for (int k = 0; k < 4; k++) {
                    float wt = g.weight[i * 4 + k];
                    int j = g.joint[i * 4 + k] & 0xFF;
                    if (wt <= 0 || j < 0 || j >= p.bones.length) continue;
                    p.bones[j].transformPosition(g.pos[i * 3], g.pos[i * 3 + 1], g.pos[i * 3 + 2], w);
                    v.fma(wt, w);
                    total += wt;
                }
                if (total <= 0) continue;
                grow(box, turn.transformPosition(v.div(total)));
            }
        }
        if (a.built != null && p.frames != null) {
            for (TarkovFit.Node n : a.built.nodes()) {
                if (n.kit()) continue;
                TarkovParts.Part part = TarkovParts.get(n.item());
                Matrix4f f = p.frames.get(n.path());
                NpcSkin ps = part == null || part.model == null || f == null ? null : NpcSkin.get(part.model);
                if (ps == null) continue;
                m.set(turn).mul(f);
                for (NpcSkin.Group g : ps.groups) {
                    if (g.level != 0) continue;
                    for (int i = 0; i < g.vertices; i++) grow(box, m.transformPosition(g.pos[i * 3], g.pos[i * 3 + 1], g.pos[i * 3 + 2], v));
                }
            }
        }
        if (box[0] > box[3]) return null;
        float dx = box[3] - box[0], dy = box[4] - box[1];
        float k = FILL / Math.max(0.005f, Math.max(dx, dy));
        Matrix4f place = new Matrix4f().translate(0.5f, 0.5f, 0.5f).scale(k)
                .translate(-(box[0] + box[3]) / 2, -(box[1] + box[4]) / 2, -(box[2] + box[5]) / 2).mul(turn);
        s = new Shape(place, dy / Math.max(0.005f, dx));
        if (SHAPES.size() > 256) SHAPES.clear();
        SHAPES.put(a, s);
        return s;
    }

    private static void grow(float[] b, Vector3f v) {
        b[0] = Math.min(b[0], v.x);
        b[1] = Math.min(b[1], v.y);
        b[2] = Math.min(b[2], v.z);
        b[3] = Math.max(b[3], v.x);
        b[4] = Math.max(b[4], v.y);
        b[5] = Math.max(b[5], v.z);
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        float lo = 0.5f - FILL / 2, hi = 0.5f + FILL / 2;
        for (int i = 0; i < 8; i++) {
            out.accept(new Vector3f((i & 1) == 0 ? lo : hi, (i & 2) == 0 ? lo : hi, (i & 4) == 0 ? lo : hi));
        }
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Arg> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Arg> bake(SpecialModelRenderer.BakingContext context) {
            return new TarkovGunIcon();
        }
    }

    /**
     * The "zomboidcraft:tarkov_gun" condition: a Tarkov gun whose model is on this computer, in a slot, on the ground or
     * in a frame. In the hands it stays the gun item's own model (the operator draws the Tarkov gun there).
     */
    public record Shows() implements ConditionalItemModelProperty {
        public static final MapCodec<Shows> MAP_CODEC = MapCodec.unit(new Shows());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                           ItemDisplayContext context) {
            if (context != ItemDisplayContext.GUI && context != ItemDisplayContext.GROUND && context != ItemDisplayContext.FIXED) {
                return false;
            }
            return arg(stack) != null;
        }

        @Override
        public MapCodec<Shows> type() {
            return MAP_CODEC;
        }
    }
}
