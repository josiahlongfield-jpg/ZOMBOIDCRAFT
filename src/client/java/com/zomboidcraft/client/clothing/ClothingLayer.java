package com.zomboidcraft.client.clothing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.clothing.Clothes;
import com.zomboidcraft.clothing.ClothingSlot;
import com.zomboidcraft.clothing.Garment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Draws a player's clothes: each garment's worn model for every body part it covers (tools/items/gen_clothes.py),
 * posed with that part so sleeves swing with the arms and trouser legs with the legs. The models are built in the
 * part's own pixels round its pivot, so they only need the part's transform; on slim arms they're squeezed to fit.
 *
 * Helmets and head mounts with goggles are left to HeadGearLayer.
 */
public class ClothingLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    /** Inner layers first, so what's over them is drawn after (it doesn't matter to the depth test, only for reading). */
    private static final List<ClothingSlot> ORDER = List.of(ClothingSlot.UNDER, ClothingSlot.SHIRT, ClothingSlot.LEGS,
            ClothingSlot.LEG_GUARDS, ClothingSlot.FEET, ClothingSlot.HANDS, ClothingSlot.JACKET, ClothingSlot.ARM_GUARDS,
            ClothingSlot.VEST, ClothingSlot.NECK, ClothingSlot.FACE, ClothingSlot.EYES, ClothingSlot.HAT);

    /** A stack that draws one garment's model for one body part, per garment and part. */
    private static final Map<String, ItemStack> PART_STACKS = new HashMap<>();

    private final ItemModelResolver items;
    private final ItemStackRenderState piece = new ItemStackRenderState();

    public ClothingLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, ItemModelResolver items) {
        super(parent);
        this.items = items;
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        Minecraft mc = Minecraft.getInstance();
        if (state.isInvisible || mc.level == null || !(mc.level.getEntity(state.id) instanceof Player player)) {
            return;
        }
        boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
        PlayerModel model = getParentModel();
        for (ClothingSlot slot : ORDER) {
            ItemStack worn = slot.shown(player);
            Garment garment = Clothes.of(worn);
            List<String> parts = garment == null ? null : WornParts.PARTS.get(garment.id());
            if (parts == null) {
                continue;
            }
            for (String part : parts) {
                ModelPart on = switch (part) {
                    case "head" -> model.head;
                    case "body" -> model.body;
                    case "right_arm" -> model.rightArm;
                    case "left_arm" -> model.leftArm;
                    case "right_leg" -> model.rightLeg;
                    case "left_leg" -> model.leftLeg;
                    default -> null;
                };
                if (on == null || !on.visible) {
                    continue;
                }
                items.updateForLiving(piece, stack(garment.id(), part), ItemDisplayContext.NONE, player);
                if (piece.isEmpty()) {
                    continue;
                }
                pose.pushPose();
                on.translateAndRotate(pose);
                if (slim && part.endsWith("_arm")) {
                    // a slim arm is 3 px wide, its middle half a pixel nearer the body than a wide arm's
                    pose.translate((part.startsWith("right") ? 0.25f : -0.25f) / 16f, 0, 0);
                    pose.scale(0.75f, 1f, 1f);
                }
                // model space is y up with its front at +z; the body's is y down, front at -z
                pose.scale(1f, -1f, -1f);
                piece.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
                pose.popPose();
            }
        }
    }

    /** How a belt widens (x, z) to sit over what's worn at the waist: trousers and shirts, not jackets or coats. */
    private static final float[] BARE = {1f, 1f}, OVER = {(4f + 0.55f) / 4f, (2f + 0.55f) / 2f};

    public static float[] beltOut(Player player) {
        return Clothes.of(ClothingSlot.LEGS.shown(player)) != null || Clothes.of(ClothingSlot.SHIRT.shown(player)) != null ? OVER : BARE;
    }

    private static ItemStack stack(String id, String part) {
        return PART_STACKS.computeIfAbsent(id + "/" + part, key -> {
            ItemStack s = new ItemStack(Items.STICK);
            s.set(DataComponents.ITEM_MODEL, ZomboidCraft.id("clothing/" + key));
            return s;
        });
    }
}
