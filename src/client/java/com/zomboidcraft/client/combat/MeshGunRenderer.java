package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jetbrains.annotations.Nullable;

/**
 * The "zomboidcraft:mesh_gun" special item model: draws a part of a bought gun ({@link MeshGuns}) where the block
 * model was, under the same "base" model's display transforms, so first person, third person, the ground, item
 * frames and the inventory all show the real mesh.
 *
 * <pre>{"type": "minecraft:special", "base": "zomboidcraft:item/weapon/shotgun/hip/body",
 *  "model": {"type": "zomboidcraft:mesh_gun", "id": "pbr_shotgun", "part": "body"}}</pre>
 */
public final class MeshGunRenderer implements NoDataSpecialModelRenderer {
    private final String id;
    private final String part;

    private MeshGunRenderer(String id, String part) {
        this.id = id;
        this.part = part;
    }

    public static void register() {
        SpecialModelRenderers.ID_MAPPER.put(ZomboidCraft.id("mesh_gun"), Unbaked.MAP_CODEC);
    }

    @Override
    public void submit(ItemDisplayContext context, PoseStack pose, SubmitNodeCollector out, int light, int overlay,
                       boolean foil, int outline) {
        MeshGuns.draw(id, part, pose, out, light);
    }

    /** Where the gun reaches: our guns run from about -8 to 25 pixels along z, round the middle of the block. */
    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        for (int i = 0; i < 8; i++) {
            out.accept(new Vector3f((i & 1) == 0 ? 0.2f : 0.8f, (i & 2) == 0 ? -0.1f : 1.0f, (i & 4) == 0 ? -0.6f : 1.6f));
        }
    }

    public record Unbaked(String id, String part) implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Unbaked::id),
                Codec.STRING.optionalFieldOf("part", "body").forGetter(Unbaked::part)
        ).apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public @Nullable SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new MeshGunRenderer(id, part);
        }
    }
}
