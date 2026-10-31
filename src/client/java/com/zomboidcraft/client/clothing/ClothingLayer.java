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

    /** A stack that draws one garment's model for one body part, per garment, part and camo. */
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
        submitGrooming(pose, collector, light, state, player, model, slim);
        for (ClothingSlot slot : ORDER) {
            ItemStack worn = slot.shown(player);
            Garment garment = Clothes.of(worn);
            List<String> parts = garment == null ? null : WornParts.PARTS.get(garment.id());
            if (parts == null) {
                continue;
            }
            for (String part : parts) {
                drawPart(pose, collector, light, state, player, model, slim, garment.id(), part,
                        worn.get(com.zomboidcraft.clothing.ClothingLoot.CAMO));
            }
        }
        submitMedical(pose, collector, light, state, player, model, slim);
    }

    private void drawPart(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Player player,
                          PlayerModel model, boolean slim, String id, String part, @org.jetbrains.annotations.Nullable String camo) {
        if (!WornParts.PARTS.getOrDefault(id, List.of()).contains(part)) {
            return;
        }
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
            return;
        }
        items.updateForLiving(piece, stack(id, part, camo), ItemDisplayContext.NONE, player);
        if (piece.isEmpty()) {
            return;
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

    /**
     * Hair and beard (clothing/Grooming), under whatever's worn: under a hat only what hangs below its band, under a
     * full helmet nothing, and no beard under a mask.
     */
    private void submitGrooming(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state,
                                Player player, PlayerModel model, boolean slim) {
        com.zomboidcraft.clothing.Grooming.Look look = com.zomboidcraft.clothing.Grooming.look(player);
        long now = player.level().getGameTime();
        Garment hat = Clothes.of(ClothingSlot.HAT.shown(player));
        boolean helm = hat != null && hat.covers().contains(com.zomboidcraft.clothing.Spot.NECK);
        if (helm) return;
        String hair = com.zomboidcraft.clothing.Grooming.hair(look, now);
        if (!hair.equals("own") && !hair.equals("bald")) {
            String id = "hair_" + hair + (hat != null ? "_c" : "");
            for (String part : WornParts.PARTS.getOrDefault(id, List.of())) {
                drawPart(pose, collector, light, state, player, model, slim, id, part, look.colour());
            }
        }
        String beard = com.zomboidcraft.clothing.Grooming.beard(look, now);
        if (!beard.equals("none") && Clothes.of(ClothingSlot.FACE.shown(player)) == null) {
            drawPart(pose, collector, light, state, player, model, slim, "beard_" + beard, "head", look.colour());
        }
    }

    /** Whether this layer draws the piece (a rig is a bag too, and the bag layer leaves it to this one). */
    public static boolean draws(ItemStack stack) {
        Garment g = Clothes.of(stack);
        return g != null && WornParts.PARTS.containsKey(g.id());
    }

    /** Medical supports by bit of the worn-medical mask, and the body part each body region draws on. */
    private static final String[] MEDICAL = {"worn_bandage", "worn_gauze", "worn_wrap", "worn_tourniquet", "worn_splint",
            "worn_finger_splint", "worn_moldable_splint", "worn_sling", "worn_collar", "worn_knee_brace"};
    private static final String[] REGION_PART = {"head", "body", "left_arm", "right_arm", "left_leg", "right_leg"};

    /** The worn-medical mask per body region (survival's synced view), or null while there's none. */
    public static java.util.function.Function<Player, int[]> medicalMask = p -> null;

    private void submitMedical(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Player player,
                               PlayerModel model, boolean slim) {
        int[] mask = medicalMask.apply(player);
        if (mask == null) {
            return;
        }
        boolean slingBody = false;
        for (int r = 0; r < Math.min(mask.length, REGION_PART.length); r++) {
            for (int bit = 0; bit < MEDICAL.length; bit++) {
                if ((mask[r] & (1 << bit)) == 0) continue;
                String id = MEDICAL[bit];
                String part = REGION_PART[r];
                if (id.equals("worn_collar")) part = "head";              // a splinted neck or torso: the collar
                if (id.equals("worn_sling")) slingBody = true;
                drawPart(pose, collector, light, state, player, model, slim, id, part, null);
                if (id.equals("worn_collar")) drawPart(pose, collector, light, state, player, model, slim, id, "body", null);
            }
        }
        if (slingBody) drawPart(pose, collector, light, state, player, model, slim, "worn_sling", "body", null);
    }

    /** How a belt widens (x, z) to sit over what's worn at the waist: trousers and shirts, not jackets or coats. */
    private static final float[] BARE = {1f, 1f}, OVER = {(4f + 0.55f) / 4f, (2f + 0.55f) / 2f};

    public static float[] beltOut(Player player) {
        return Clothes.of(ClothingSlot.LEGS.shown(player)) != null || Clothes.of(ClothingSlot.SHIRT.shown(player)) != null ? OVER : BARE;
    }

    private static ItemStack stack(String id, String part, @org.jetbrains.annotations.Nullable String camo) {
        return PART_STACKS.computeIfAbsent(id + "/" + part + (camo == null ? "" : "@" + camo), key -> {
            ItemStack s = new ItemStack(Items.STICK);
            s.set(DataComponents.ITEM_MODEL, ZomboidCraft.id("clothing/" + id + "/" + part));
            if (camo != null) s.set(com.zomboidcraft.clothing.ClothingLoot.CAMO, camo);
            return s;
        });
    }
}
