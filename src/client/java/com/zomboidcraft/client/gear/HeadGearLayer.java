package com.zomboidcraft.client.gear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zomboidcraft.gear.HeadGearItem;
import com.zomboidcraft.gear.Headgear;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a player's helmet or head mount and the goggles on it, swinging the goggles up and down on their pivot when
 * they're flipped. (The headgear's item model draws nothing on a player's head for vanilla's own head layer; on
 * zombies and armour stands it draws the helmet with the goggles fixed where they are.)
 *
 * The models are built in head pixels from the middle of the head (tools/weapons/headgear.py): one item pixel is one
 * head pixel, so after vanilla's head transform for worn items the goggles turn about the mount's pivot block.
 */
public class HeadGearLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** The pivot the goggles turn on, in head pixels (headgear.py PIVOT), and how far up they go (UP). */
    private static final float PIVOT_Y = 2.3f / 16f, PIVOT_Z = -5.62f / 16f, UP = 90f;
    /** How long the flip takes, in milliseconds. */
    private static final float FLIP_MS = 300f;

    /** Each player's flip, 0 down to 1 up, and when it was last drawn. */
    private static final Map<Integer, double[]> FLIPS = new HashMap<>();

    private final ItemModelResolver items;
    private final ItemStackRenderState headgear = new ItemStackRenderState();
    private final ItemStackRenderState goggles = new ItemStackRenderState();

    public HeadGearLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ItemModelResolver items) {
        super(parent);
        this.items = items;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (state.isInvisible || mc.level == null || !(mc.level.getEntity(state.id) instanceof Player player)) {
            return;
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof HeadGearItem)) {
            return;
        }
        pose.pushPose();
        getParentModel().head.translateAndRotate(pose);
        // vanilla's worn-item head transform with the item models' head scale: one item pixel to one head pixel
        pose.translate(0f, -0.25f, 0f);
        pose.scale(-1f, -1f, 1f);
        items.updateForLiving(headgear, head, ItemDisplayContext.NONE, player);
        headgear.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        Headgear.Mounted m = Headgear.mounted(head);
        if (m != null) {
            float up = flip(state.id, m.up());
            float eased = up * up * (3f - 2f * up);
            pose.translate(0f, PIVOT_Y, PIVOT_Z);
            pose.mulPose(Axis.XP.rotationDegrees(UP * eased));
            pose.translate(0f, -PIVOT_Y, -PIVOT_Z);
            items.updateForLiving(goggles, m.goggles(), ItemDisplayContext.NONE, player);
            goggles.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        }
        pose.popPose();
    }

    /** Moves this player's flip toward where the goggles are now; the first time they're seen it starts there. */
    private static float flip(int id, boolean up) {
        long now = System.currentTimeMillis();
        float target = up ? 1f : 0f;
        double[] f = FLIPS.get(id);
        if (f == null) {
            if (FLIPS.size() > 256) {
                FLIPS.clear();
            }
            FLIPS.put(id, new double[] {target, now});
            return target;
        }
        double step = Math.min(1.0, (now - f[1]) / FLIP_MS);
        f[0] = target > f[0] ? Math.min(target, f[0] + step) : Math.max(target, f[0] - step);
        f[1] = now;
        return Mth.clamp((float) f[0], 0f, 1f);
    }
}
