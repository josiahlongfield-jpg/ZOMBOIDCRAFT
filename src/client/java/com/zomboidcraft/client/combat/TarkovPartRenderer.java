package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import com.zomboidcraft.combat.TarkovParts;
import com.zomboidcraft.mixin.client.SpecialModelRenderersAccessor;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The "zomboidcraft:tarkov_part" special item model: a Tarkov part in hand, in a slot or on the ground draws its own
 * model (config/zomboidcraft/npc/tkp_ID.zsk), turned muzzle-right and up-up, fitted into the middle of the block.
 */
public final class TarkovPartRenderer implements SpecialModelRenderer<String> {
    /** How much of the block the part fills along its longest side, and where its middle sits. */
    private static final float FILL = 0.72f, MID_Y = 0.5f;
    /** Tarkov's part frame (its muzzle way -y, up +z) into the item's (muzzle +x, up +y). */
    private static final Matrix4f TURN = new Matrix4f(
            0, 0, -1, 0,
            -1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 0, 1);
    private static final Map<String, float[]> BOUNDS = new HashMap<>();
    private static final Map<String, Object[][]> BUFFERS = new HashMap<>();
    private static final Matrix4f[] ONE = {new Matrix4f()};

    public static void register() {
        SpecialModelRenderersAccessor.zomboidcraft$types().put(ZomboidCraft.id("tarkov_part"), Unbaked.MAP_CODEC);
    }

    @Override
    public @Nullable String extractArgument(ItemStack stack) {
        TarkovParts.Part p = TarkovParts.part(stack);
        return p == null ? null : p.model;
    }

    @Override
    public void submit(@Nullable String model, PoseStack pose, SubmitNodeCollector out, int light, int overlay, boolean foil, int outline) {
        NpcSkin skin = model == null ? null : NpcSkin.get(model);
        if (skin == null) return;
        float[] b = BOUNDS.computeIfAbsent(model, m -> bounds(skin));
        float reach = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        float s = FILL / Math.max(0.005f, reach);
        pose.pushPose();
        pose.translate(0.5f, MID_Y, 0.5f);
        pose.scale(s, s, s);
        pose.translate(-(b[0] + b[3]) / 2, -(b[1] + b[4]) / 2, -(b[2] + b[5]) / 2);
        pose.mulPose(TURN);
        Object[][] buf = BUFFERS.computeIfAbsent(model, m -> new Object[1][]);
        if (buf[0] == null || buf[0].length != skin.groups.length * 2) buf[0] = new Object[skin.groups.length * 2];
        NpcSkinDraw.submitGroups(skin.groups, ONE, 0, g -> true, g -> skin.texture(g.texture, model), buf[0], light,
                OverlayTexture.NO_OVERLAY, pose, out);
        pose.popPose();
    }

    /** Where the part reaches once turned the item's way: min x, y, z, max x, y, z. */
    private static float[] bounds(NpcSkin skin) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        Vector3f v = new Vector3f();
        for (NpcSkin.Group g : skin.groups) {
            for (int i = 0; i + 2 < g.pos.length; i += 3) {
                TURN.transformPosition(g.pos[i], g.pos[i + 1], g.pos[i + 2], v);
                b[0] = Math.min(b[0], v.x);
                b[1] = Math.min(b[1], v.y);
                b[2] = Math.min(b[2], v.z);
                b[3] = Math.max(b[3], v.x);
                b[4] = Math.max(b[4], v.y);
                b[5] = Math.max(b[5], v.z);
            }
        }
        if (b[0] > b[3]) return new float[] {-0.05f, -0.05f, -0.05f, 0.05f, 0.05f, 0.05f};
        return b;
    }

    @Override
    public void getExtents(Consumer<Vector3fc> out) {
        float lo = 0.5f - FILL / 2, hi = 0.5f + FILL / 2;
        for (int i = 0; i < 8; i++) {
            out.accept(new Vector3f((i & 1) == 0 ? lo : hi, (i & 2) == 0 ? MID_Y - FILL / 2 : MID_Y + FILL / 2, (i & 4) == 0 ? lo : hi));
        }
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked<String> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<String> bake(SpecialModelRenderer.BakingContext context) {
            return new TarkovPartRenderer();
        }
    }
}
