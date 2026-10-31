package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.zombie.WalkerModel;
import com.zomboidcraft.client.zombie.WalkerRenderState;
import com.zomboidcraft.combat.StuckBolts;
import com.zomboidcraft.zombie.AttachedWeapons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * The weapon someone left in a zombie (Zomboid's attached weapon, {@link AttachedWeapons}): the item's own model,
 * blade buried in the back, chest or shoulder and the handle standing out, posed with the torso so it rides every
 * lurch and stagger. Drawn on the new body only.
 */
public class BodyWeaponLayer extends RenderLayer<WalkerRenderState, WalkerModel> {
    /** Each blade's length along its model's y, in model pixels: grip end and tip (from its element boxes). */
    private record Blade(float grip, float tip) {
    }

    private static Blade blade(String item) {
        return switch (item) {
            case "machete" -> new Blade(-3.58f, 16.22f);
            case "tomahawk" -> new Blade(-3.68f, 11.82f);
            case "fire_axe" -> new Blade(-4.68f, 23.32f);
            case "katana" -> new Blade(-5.68f, 28.87f);
            default -> new Blade(-2.68f, 8.52f);         // kitchen knife
        };
    }

    /** How deep it's in, as a share of its length from the tip, and how big it's drawn against the body. */
    private static final float BURIED = 0.3f, SCALE = 0.62f;
    /** How far up the torso it stands, from the small of the back to the neck. */
    private static final double UP_TORSO = 0.62;

    private final ItemModelResolver items;
    private final ItemStackRenderState item = new ItemStackRenderState();

    public BodyWeaponLayer(RenderLayerParent<WalkerRenderState, WalkerModel> parent, ItemModelResolver items) {
        super(parent);
        this.items = items;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, WalkerRenderState state, float yRot, float xRot) {
        AttachedWeapons.Stuck stuck = state.bodyWeapon;
        if (stuck == null || state.zbody == null || state.isInvisible) {
            return;
        }
        ItemStack stack = state.bodyWeaponStack;
        if (stack == null || stack.isEmpty()) {
            return;
        }
        float[] m = ZBodyLayer.pose(state.zbody, state.zpose, state.legless);
        // the torso as posed now, in the walker model's space (blocks, y down, front -z)
        Vec3 spine = origin(m, ZMove.SPINE), neck = origin(m, ZMove.NECK);
        Vec3 up = neck.subtract(spine);
        if (up.lengthSqr() < 1e-8) {
            return;
        }
        up = up.normalize();
        Vec3 right = axis(m, ZMove.SPINE, 0);
        right = right.subtract(up.scale(right.dot(up))).normalize();
        Vec3 front = right.cross(up).normalize();
        double radius = StuckBolts.RADIUS[1];
        Vec3 chest = spine.add(neck.subtract(spine).scale(UP_TORSO));
        Vec3 at, in;
        double side = (state.variant & 1) == 0 ? 1 : -1;
        switch (stuck.spot()) {
            case CHEST -> {
                at = chest.add(front.scale(radius)).add(right.scale(0.04 * side));
                in = front.scale(-0.9).add(up.scale(-0.35));            // in from the front, angled down
            }
            case SHOULDER -> {
                at = neck.add(right.scale(0.13 * side)).subtract(up.scale(0.03)).subtract(front.scale(0.03));
                in = up.scale(-0.8).add(right.scale(-0.25 * side)).add(front.scale(0.2));   // chopped in from above
            }
            default -> {
                at = chest.subtract(front.scale(radius)).add(right.scale(0.05 * side));
                in = front.scale(0.85).add(up.scale(-0.4)).add(right.scale(0.15 * side));  // in the back, handle up
            }
        }
        Vec3 y = in.normalize();
        Vec3 x = up.cross(y);
        if (x.lengthSqr() < 1e-6) {
            x = right;
        }
        x = x.normalize();
        Vec3 z = x.cross(y).normalize();
        Blade b = blade(stuck.item());
        float entry = b.tip() - BURIED * (b.tip() - b.grip());
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(new Matrix4f(
                (float) x.x, (float) x.y, (float) x.z, 0,
                (float) y.x, (float) y.y, (float) y.z, 0,
                (float) z.x, (float) z.y, (float) z.z, 0,
                0, 0, 0, 1));
        pose.scale(SCALE, SCALE, SCALE);
        // the model is drawn about its centre (8 px): slide it so the entry point of the blade sits on the body
        pose.translate(0, -(entry - 8f) / 16f, 0);
        items.updateForTopItem(item, stack, ItemDisplayContext.NONE, Minecraft.getInstance().level, null, 0);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }

    private static Vec3 origin(float[] m, int bone) {
        int o = bone * 16;
        return new Vec3(m[o + 12], m[o + 13], m[o + 14]);
    }

    private static Vec3 axis(float[] m, int bone, int column) {
        int o = bone * 16 + column;
        return new Vec3(m[o], m[o + 1], m[o + 2]).normalize();
    }
}
