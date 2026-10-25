package com.zomboidcraft.client.carry;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Carry;
import com.zomboidcraft.carry.GearStore;
import java.util.List;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws worn bags on a player: packs on the back, the plate carrier round the chest, the fanny pack at the front of
 * the belt. The bag's own item model is posed on the body, so it moves with it when crouching or swimming.
 */
public class WornGearLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /**
     * Where each bag sits, in body pixels from the neck: scale on each axis, then down and back (negative is toward
     * the chest). {@code front} turns it to face forward. Worked out from the model bounds in tools/items/gen_bags.py.
     */
    record Fit(float sx, float sy, float sz, float down, float back, boolean front) {
    }

    private static final Map<String, Fit> FITS = Map.of(
            "school_backpack", new Fit(1f, 1f, 1f, 5.35f, 5.2f, false),
            "hiking_backpack", new Fit(1f, 1f, 1f, 4.65f, 5.55f, false),
            "military_rucksack", new Fit(0.85f, 0.85f, 0.85f, 4.53f, 5.78f, false),
            "plate_carrier", new Fit(0.95f, 1f, 1f, 5.1f, -0.75f, true),
            "fanny_pack", new Fit(0.6f, 0.85f, 0.85f, 10.9f, -3.73f, true));
    private static final Fit BACK = new Fit(1f, 1f, 1f, 5.3f, 5.5f, false);
    private static final Fit CHEST = new Fit(1f, 1f, 1f, 5f, -0.75f, true);
    private static final Fit WAIST = new Fit(0.7f, 0.85f, 0.85f, 10.9f, -3.7f, true);

    private final ItemModelResolver items;
    private final ItemStackRenderState item = new ItemStackRenderState();

    public WornGearLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ItemModelResolver items) {
        super(parent);
        this.items = items;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (state.isInvisible || mc.level == null || !(mc.level.getEntity(state.id) instanceof Player player)) {
            return;
        }
        List<ItemStack> worn = player.getAttachedOrElse(Carry.WORN, List.of());
        for (int i = 0; i < Math.min(worn.size(), GearStore.SLING); i++) {
            ItemStack stack = worn.get(i);
            BagSpec spec = BagItem.spec(stack);
            if (spec == null) {
                continue;
            }
            Fit fit = FITS.getOrDefault(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath(),
                    i == GearStore.BACK ? BACK : i == GearStore.VEST ? CHEST : WAIST);
            pose.pushPose();
            getParentModel().body.translateAndRotate(pose);
            pose.translate(0, fit.down() / 16f, fit.back() / 16f);
            if (fit.front()) {
                pose.mulPose(Axis.YP.rotationDegrees(180));
            }
            // model space has y down; this turns the item model upright with its front facing away from the body
            pose.scale(-fit.sx(), -fit.sy(), fit.sz());
            items.updateForLiving(item, stack, ItemDisplayContext.NONE, player);
            item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
    }
}
