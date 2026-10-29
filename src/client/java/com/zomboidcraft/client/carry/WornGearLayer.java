package com.zomboidcraft.client.carry;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.phys.AABB;

/**
 * Draws worn bags on a player: packs on the back, the plate carrier round the chest, the fanny pack at the front of
 * the belt, and the weapon on the sling across the back. The item's own model is posed on the body, so it moves with
 * it when crouching or swimming.
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
            "fanny_pack", new Fit(0.6f, 0.85f, 0.85f, 10.9f, -3.73f, true),
            // the satchel hangs low on the back from its shoulder strap; the pouch sits at the belt's front
            "mail_bag", new Fit(0.8f, 0.8f, 0.8f, 6.2f, 3.4f, false),
            "ammo_pouch_belt", new Fit(0.62f, 0.85f, 0.85f, 10.9f, -3.73f, true));
    private static final Fit BACK = new Fit(1f, 1f, 1f, 5.3f, 5.5f, false);
    private static final Fit CHEST = new Fit(1f, 1f, 1f, 5f, -0.75f, true);
    private static final Fit WAIST = new Fit(0.7f, 0.85f, 0.85f, 10.9f, -3.7f, true);

    /** Worn pieces with a body-scale model; anything else in those slots isn't drawn on you. */
    private static final java.util.Set<net.minecraft.world.item.Item> BODY_SCALE = java.util.Set.of(
            com.zomboidcraft.ModItems.LEATHER_BELT, com.zomboidcraft.ModItems.TOOL_BELT, com.zomboidcraft.ModItems.DUTY_BELT,
            com.zomboidcraft.ModItems.HIP_HOLSTER, com.zomboidcraft.ModItems.SHOULDER_HOLSTER,
            com.zomboidcraft.ModItems.THIGH_HOLSTER, com.zomboidcraft.ModItems.KNIFE_SHEATH,
            com.zomboidcraft.ModItems.MACHETE_SHEATH);

    /** Where the right leg hangs from, in body pixels: the thigh rig is drawn on the leg so it swings with it. */
    private static final float LEG_X = -1.9f, LEG_Y = 12f;

    /** How far down the body a belt's bottom edge sits, less the 0.8 px its model is built below centre. */
    private static final float BELT_DOWN = 11.7f - 0.8f;

    /** How the slung weapon lies across the back, in degrees from level. */
    private static final float SLING_ANGLE = 55f;

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
        float packOut = 2f / 16f;
        for (int i = 0; i < Math.min(worn.size(), GearStore.BELT); i++) {
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
            // model space has y down; these turn the item model upright, its front facing away from the back or
            // (turned half round) forward from the chest
            if (fit.front()) {
                pose.scale(fit.sx(), -fit.sy(), -fit.sz());
            } else {
                pose.scale(-fit.sx(), -fit.sy(), fit.sz());
            }
            items.updateForLiving(item, stack, ItemDisplayContext.NONE, player);
            if (i == GearStore.BACK) {
                // how far out the pack's back face sits, for a slung weapon to lie over it
                AABB box = item.getModelBoundingBox();
                packOut = Math.max(packOut, fit.back() / 16f + (float) box.maxZ * fit.sz());
            }
            item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        // the belt, holster and sheath are built at body scale round the waist (tools/items/gen_belts.py,
        // gen_holsters.py): they only need moving down to it
        // over trousers or a shirt tail the belt sits out on the cloth (clothing/ClothingLayer's waistband)
        float[] out = com.zomboidcraft.client.clothing.ClothingLayer.beltOut(player);
        for (int i = GearStore.BELT; i <= GearStore.SHEATH && i < worn.size(); i++) {
            if (!BODY_SCALE.contains(worn.get(i).getItem())) continue;
            boolean thigh = worn.get(i).is(com.zomboidcraft.ModItems.THIGH_HOLSTER);
            pose.pushPose();
            onPart(pose, thigh);
            pose.translate(0, BELT_DOWN / 16f, 0);
            pose.scale(1f, -1f, -1f);
            if (!thigh) pose.scale(out[0], 1f, out[1]);
            items.updateForLiving(item, worn.get(i), ItemDisplayContext.NONE, player);
            item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        if (worn.size() > Carry.WORN_SHEATH) {
            if (!worn.get(Carry.WORN_BACK).isEmpty()) {
                sling(pose, collector, light, state, player, worn.get(Carry.WORN_BACK), packOut);
            }
            ItemStack holster = worn.get(GearStore.HOLSTER), sheath = worn.get(GearStore.SHEATH);
            // the handgun sits down in its holster's pouch (gen_holsters.py), its grip out of the top
            if (holster.is(com.zomboidcraft.ModItems.SHOULDER_HOLSTER)) {
                hip(pose, collector, light, state, player, worn.get(Carry.WORN_HOLSTER), -1, false, 4.1f, 6.5f, -2.55f, 5.5f);
            } else if (holster.is(com.zomboidcraft.ModItems.THIGH_HOLSTER)) {
                hip(pose, collector, light, state, player, worn.get(Carry.WORN_HOLSTER), -1, true, -3.8f, 16.2f, -0.1f, 6.5f);
            } else {
                hip(pose, collector, light, state, player, worn.get(Carry.WORN_HOLSTER), -1, false, -5.1f, 11.5f, -0.4f, 6.5f);
            }
            boolean machete = sheath.is(com.zomboidcraft.ModItems.MACHETE_SHEATH);
            // a knife cross-draw on the front of the left hip, a machete behind it (gen_holsters.py)
            hip(pose, collector, light, state, player, worn.get(Carry.WORN_SHEATH), 1, false, machete ? 2.6f : 2.8f,
                    machete ? 12.6f : 12.7f, machete ? 2.9f : -2.9f, machete ? 10f : 7.5f);
        }
    }

    /** Moves to the body, or to the right leg in the body's frame (so a piece built round the waist lines up). */
    private void onPart(PoseStack pose, boolean leg) {
        if (leg) {
            getParentModel().rightLeg.translateAndRotate(pose);
            pose.translate(-LEG_X / 16f, -LEG_Y / 16f, 0);
        } else {
            getParentModel().body.translateAndRotate(pose);
        }
    }

    /**
     * A holstered handgun or a sheathed blade (side -1 or 1), hanging grip up at (x, y, z) body pixels, {@code size}
     * pixels long.
     */
    private void hip(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Player player, ItemStack stack,
                     int side, boolean leg, float x, float y, float z, float size) {
        if (stack.isEmpty()) return;
        items.updateForLiving(item, stack, ItemDisplayContext.NONE, player);
        if (item.isEmpty()) return;
        AABB box = item.getModelBoundingBox();
        boolean alongZ = box.getZsize() >= Math.max(box.getXsize(), box.getYsize());
        double length = alongZ ? box.getZsize() : Math.max(box.getXsize(), box.getYsize());
        if (length <= 0) return;
        float k = (float) (size / 16f / length);
        pose.pushPose();
        onPart(pose, leg);
        pose.translate(x / 16f, y / 16f, z / 16f);
        pose.scale(-k, -k, k);
        // guns point their muzzle down the thigh; a blade's tip hangs down
        if (alongZ) {
            pose.mulPose(new org.joml.Matrix4f().rotationX((float) (Math.PI / 2)));
            pose.mulPose(new org.joml.Matrix4f().rotationZ((float) (side * Math.PI / 2)));
        } else if (box.getYsize() < box.getXsize()) {
            pose.mulPose(new org.joml.Matrix4f().rotationZ((float) (Math.PI / 2)));
        }
        if (side > 0 && !alongZ && box.getYsize() >= box.getXsize()) {
            // held models stand handle down; turn a blade over so its handle stands out of the sheath
            pose.mulPose(new org.joml.Matrix4f().rotationZ((float) Math.PI));
        }
        pose.translate(-(box.minX + box.maxX) / 2, -(box.minY + box.maxY) / 2, -(box.minZ + box.maxZ) / 2);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }

    /**
     * The slung weapon, flat across the back from hip to shoulder (over the pack, if one is worn), sized to the body:
     * a long gun about as long as the torso is tall and a half, melee a little shorter.
     */
    private void sling(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, Player player, ItemStack stack,
                       float packOut) {
        items.updateForLiving(item, stack, ItemDisplayContext.NONE, player);
        if (item.isEmpty()) return;
        AABB box = item.getModelBoundingBox();
        // gun models run along z (barrel forward); laid on the back that becomes the long side across it
        boolean alongZ = box.getZsize() >= Math.max(box.getXsize(), box.getYsize());
        double length = alongZ ? box.getZsize() : Math.max(box.getXsize(), box.getYsize());
        double thick = alongZ ? box.getXsize() : box.getZsize();
        if (length <= 0) return;
        boolean gun = stack.getItem() instanceof com.zomboidcraft.combat.RifleItem;
        float k = (float) ((gun ? 18 : 14) / 16f / length);
        float out = packOut + (float) thick * k / 2 + 0.2f / 16f;
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        pose.translate(0, 6.5f / 16f, out);
        // tall models (a bat on its handle) stand along y: either way the long side runs corner to corner
        float angle = !alongZ && box.getYsize() > box.getXsize() ? SLING_ANGLE - 90 : SLING_ANGLE;
        // a gun hangs muzzle up over the shoulder, stock down by the hip
        if (alongZ) angle += 180;
        pose.mulPose(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(angle)));
        pose.scale(-k, -k, k);
        if (alongZ) pose.mulPose(new org.joml.Matrix4f().rotationY((float) (Math.PI / 2)));
        pose.translate(-(box.minX + box.maxX) / 2, -(box.minY + box.maxY) / 2, -(box.minZ + box.maxZ) / 2);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        pose.popPose();
    }
}
