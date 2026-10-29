package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.mixin.client.SpecialModelRenderersAccessor;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
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
public final class MeshGunRenderer implements NoDataSpecialModelRenderer {
    private final String id;
    private final String part;
    private final List<Float> offset;

    private MeshGunRenderer(String id, String part, List<Float> offset) {
        this.id = id;
        this.part = part;
        this.offset = offset;
    }

    public static void register() {
        SpecialModelRenderersAccessor.zomboidcraft$types().put(ZomboidCraft.id("mesh"), Unbaked.MAP_CODEC);
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        if (offset.isEmpty()) {
            MeshGuns.draw(id, part, pose, out, light);
            return;
        }
        pose.pushPose();
        pose.translate(offset.get(0) / 16, offset.get(1) / 16, offset.get(2) / 16);
        MeshGuns.draw(id, part, pose, out, light);
        pose.popPose();
    }

    /** Where the mesh reaches, from its file; a gun-sized box round the middle of the block if it can't be read. */
    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        float[] b = MeshGuns.bounds(id);
        if (b == null) {
            b = new float[] {0.2f, -0.1f, -0.6f, 0.8f, 1.0f, 1.6f};
        }
        for (int i = 0; i < 8; i++) {
            out.accept(new Vector3f(b[(i & 1) * 3], b[1 + (i >> 1 & 1) * 3], b[2 + (i >> 2 & 1) * 3]));
        }
    }

    /** offset: item pixels to move the part by, for a piece our block gun draws somewhere else (a revolver's cylinder swung out). */
    public record Unbaked(String id, String part, List<Float> offset) implements SpecialModelRenderer.Unbaked<Void> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Unbaked::id),
                Codec.STRING.optionalFieldOf("part", "body").forGetter(Unbaked::part),
                Codec.FLOAT.listOf(3, 3).optionalFieldOf("offset", List.of()).forGetter(Unbaked::offset)
        ).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            return new MeshGunRenderer(id, part, offset);
        }
    }
}
