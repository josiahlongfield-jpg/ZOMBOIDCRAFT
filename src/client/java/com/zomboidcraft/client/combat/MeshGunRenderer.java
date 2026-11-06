package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.mixin.client.SpecialModelRenderersAccessor;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The "zomboidcraft:mesh" special item model: draws a part of a packed mesh ({@link MeshGuns}: the bought guns, and
 * the loot) where the block model was, under the same "base" model's display transforms, so first person, third
 * person, the ground, item frames and the inventory all show the real mesh.
 *
 * <pre>{"type": "minecraft:special", "base": "zomboidcraft:item/weapon/shotgun/hip/body",
 *  "model": {"type": "zomboidcraft:mesh", "id": "pbr_shotgun", "part": "body"}}</pre>
 */
public final class MeshGunRenderer implements net.minecraft.client.renderer.special.SpecialModelRenderer<String> {
    private final String id;
    private final String part;
    private final List<Float> offset;
    private final float roll, pitch, scale;
    private final @org.jetbrains.annotations.Nullable net.minecraft.resources.Identifier texture;

    private MeshGunRenderer(String id, String part, List<Float> offset, float roll, float pitch, float scale, String texture) {
        this.id = id;
        this.part = part;
        this.offset = offset;
        this.roll = roll;
        this.pitch = pitch;
        this.scale = scale;
        this.texture = texture.isEmpty() ? null : net.minecraft.resources.Identifier.parse(texture);
    }

    public static void register() {
        SpecialModelRenderersAccessor.zomboidcraft$types().put(ZomboidCraft.id("mesh"), Unbaked.MAP_CODEC);
    }

    /** A gun kept on this computer (combat/LocalGuns) draws its own mesh in place of ours. */
    @Override
    public @org.jetbrains.annotations.Nullable String extractArgument(net.minecraft.world.item.ItemStack stack) {
        String local = stack.get(com.zomboidcraft.combat.LocalGuns.ID);
        if (local != null && MeshGuns.hasLocal(local)) {
            return LocalAttachments.argument(stack, local);
        }
        // a sight or grip kept on this computer: its own mesh, at the middle of the block
        String part = com.zomboidcraft.combat.LocalParts.of(stack);
        return part != null && com.zomboidcraft.combat.LocalParts.get(part) != null ? "part:" + part : null;
    }

    @Override
    public void submit(@org.jetbrains.annotations.Nullable String local, PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        if (local != null && local.startsWith("part:")) {
            String mesh = LocalAttachments.mesh(local.substring(5));
            float[] b = MeshGuns.bounds(mesh);
            if (id.startsWith("att_") && b != null) {
                // in our part's place and to our part's size, centre on centre: the item model's display (a red dot's
                // is scaled up ~3x in a slot) is made for ours, so a bigger one overflowed the slot as a blank box
                float[] o = MeshGuns.bounds(id);
                if (o == null) {
                    o = new float[] {-0.08f, 0, -0.08f, 0.08f, 0.16f, 0.08f};
                }
                float reach = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
                float ours = Math.max(o[3] - o[0], Math.max(o[4] - o[1], o[5] - o[2]));
                float s = ours / Math.max(0.005f, reach);
                pose.pushPose();
                if (!offset.isEmpty()) {
                    pose.translate(offset.get(0) / 16, offset.get(1) / 16, offset.get(2) / 16);
                }
                pose.translate((o[0] + o[3]) / 2, (o[1] + o[4]) / 2, (o[2] + o[5]) / 2);
                pose.scale(s, s, s);
                pose.translate(-(b[0] + b[3]) / 2, -(b[1] + b[4]) / 2, -(b[2] + b[5]) / 2);
                MeshGuns.draw(mesh, "body", pose, out, light, null);
                pose.popPose();
            }
            return;
        }
        String optic = null, grip = null;
        if (local != null) {
            String[] bits = local.split("\\|", -1);
            local = bits[0];
            optic = bits.length > 1 && !bits[1].isEmpty() ? bits[1] : null;
            grip = bits.length > 2 && !bits[2].isEmpty() ? bits[2] : null;
        }
        if (local != null) {
            // our gun's mesh becomes its mesh (part by part: one it hasn't got draws nothing); our attachments don't
            // go on it (its own parts are packed with it); anything else (a loaded bolt) draws as it is
            com.zomboidcraft.combat.LocalGuns.Spec spec = com.zomboidcraft.combat.LocalGuns.get(local);
            if (spec != null && id.equals(spec.anchorMesh)) {
                // where our part sits (a magazine on its way out, a swung cylinder) its part sits too
                submit(LocalAttachments.gunMesh(local, optic), null, pose, out, light);
                if ("body".equals(part)) {
                    LocalAttachments.drawParts(local, optic, grip, pose, out, light);
                }
                if ("body".equals(part) && spec.irons != null && optic == null) {
                    // iron sights it came without: our flip-up pair, up, on its rail
                    for (com.zomboidcraft.combat.LocalGuns.Part p : spec.irons) {
                        if (p == null || p.mesh == null || p.at == null || p.at.length < 3) {
                            continue;
                        }
                        pose.pushPose();
                        pose.translate(p.at[0] / 16, p.at[1] / 16, p.at[2] / 16);
                        MeshGuns.draw(p.mesh, "body", pose, out, light, null);
                        pose.popPose();
                    }
                }
                return;
            }
            if (spec != null && (id.startsWith("att_") || id.startsWith("own_"))) {
                return;
            }
        }
        submit(pose, out, light, overlay, foil, outline);
    }

    public void submit(PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        submit(id, texture, pose, out, light);
    }

    private void submit(String mesh, @org.jetbrains.annotations.Nullable net.minecraft.resources.Identifier paint,
                        PoseStack pose, SubmitNodeCollector out, int light) {
        if (offset.isEmpty() && roll == 0 && pitch == 0 && scale == 1) {
            MeshGuns.draw(mesh, part, pose, out, light, paint);
            return;
        }
        pose.pushPose();
        if (!offset.isEmpty()) {
            pose.translate(offset.get(0) / 16, offset.get(1) / 16, offset.get(2) / 16);
        }
        if (roll != 0) {
            pose.rotate(Axis.ZP.rotationDegrees(roll));
        }
        if (pitch != 0) {
            pose.rotate(Axis.XP.rotationDegrees(pitch));
        }
        if (scale != 1) {
            pose.scale(scale, scale, scale);
        }
        MeshGuns.draw(mesh, part, pose, out, light, paint);
        pose.popPose();
    }

    /** Where the mesh reaches, from its file; a gun-sized box round the middle of the block if it can't be read. */
    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        float[] b = MeshGuns.bounds(id);
        if (b == null) {
            b = new float[] {0.2f, -0.1f, -0.6f, 0.8f, 1.0f, 1.6f};
        }
        // an attachment's mesh sits round its clamp at the origin: moved to its mount (its turn is left out), and
        // sized as drawn (a round shrunk onto its carton): a dropped item stands on the bottom of these, so unscaled
        // ones sank the rounds through the floor
        float dx = offset.isEmpty() ? 0 : offset.get(0) / 16, dy = offset.isEmpty() ? 0 : offset.get(1) / 16,
                dz = offset.isEmpty() ? 0 : offset.get(2) / 16;
        for (int i = 0; i < 8; i++) {
            out.accept(new Vector3f(b[(i & 1) * 3] * scale + dx, b[1 + (i >> 1 & 1) * 3] * scale + dy,
                    b[2 + (i >> 2 & 1) * 3] * scale + dz));
        }
    }

    /**
     * offset: item pixels to move the part by, for a piece our block gun draws somewhere else (a revolver's cylinder
     * swung out), or an attachment's mount point on the gun (attachment meshes are packed with their clamp at the
     * origin). Then roll (degrees about z: 90 puts a top-rail part on the left rail, 180 under the barrel), pitch
     * (degrees about x: a flip-up sight folded flat) and scale. texture: a texture to paint it with instead of the one
     * packed in the mesh, laid out the same (a garment's camo: tools/items/gen_clothes.py).
     */
    public record Unbaked(String id, String part, List<Float> offset, float roll, float pitch, float scale, String texture)
            implements SpecialModelRenderer.Unbaked<String> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Unbaked::id),
                Codec.STRING.optionalFieldOf("part", "body").forGetter(Unbaked::part),
                Codec.FLOAT.listOf(3, 3).optionalFieldOf("offset", List.of()).forGetter(Unbaked::offset),
                Codec.FLOAT.optionalFieldOf("roll", 0f).forGetter(Unbaked::roll),
                Codec.FLOAT.optionalFieldOf("pitch", 0f).forGetter(Unbaked::pitch),
                Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(Unbaked::scale),
                Codec.STRING.optionalFieldOf("texture", "").forGetter(Unbaked::texture)
        ).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<String> bake(SpecialModelRenderer.BakingContext context) {
            return new MeshGunRenderer(id, part, offset, roll, pitch, scale, texture);
        }
    }
}
