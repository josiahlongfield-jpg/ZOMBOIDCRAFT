package com.zomboidcraft.client.operator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.combat.RailLights;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a player as the Tarkov operator ({@link Operator}) where the hidden vanilla model stands: body, head, helmet and
 * rig, posed by Tarkov's own clips, and the kitted M4A1 on his rifle bone while he holds it. Turned out of vanilla's
 * model space (its flip, its 0.9375 scale, its origin up at the hips) into his own metres, feet on the ground, as the
 * survivor layer does.
 */
public final class OperatorLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public OperatorLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new OperatorLayer(avatar));
            }
        });
    }

    @Override
    public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(state.id) instanceof Player player) || !Operator.active(player)) return;
        NpcSkin skin = Operator.skin();
        Matrix4f[] bones = Operator.pose(skin, state, player);
        if (bones == null) return;
        ItemStack held = player.getMainHandItem();
        boolean rifle = Operator.holdsM4(held);
        String model = rifle ? null : Operator.fpModel(held);
        if (rifle || model != null) rail(skin, bones, held, state);
        pose.pushPose();
        pose.translate(0, 1.501f, 0);
        pose.scale(1 / 0.9375f, 1 / 0.9375f, 1 / 0.9375f);
        pose.scale(-1, -1, 1);
        if (state.deathTime > 0) {
            float turn = Math.min(1, (float) Math.sqrt((state.deathTime - 1) / 20f * 1.6f)) * 90f;
            pose.mulPose(new Matrix4f().rotationZ((float) Math.toRadians(-turn)));
        }
        pose.mulPose(new Matrix4f().rotationY((float) Math.PI));
        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
        NpcSkinDraw.submitGroups(skin.groups, bones, 0, g -> !g.piece.equals("gun") || rifle,
                g -> skin.texture(g.texture, Operator.MODEL), Operator.buffers(state, skin), light, overlay, pose, collector);
        // any other Tarkov gun: its own pieces from its first-person model, on his weapon bone
        NpcSkin gun = model == null ? null : NpcSkin.get(model);
        int weapon = skin.bone("weapon");
        Matrix4f[] gunBones = gun == null || weapon < 0 ? null : Operator.held(gun, bones[weapon]);
        if (gunBones != null) {
            // a Tarkov gun as built at the bench: its swapped-out kit pieces hidden, its fitted parts drawn
            LocalGuns.Spec spec = LocalGuns.of(held);
            com.zomboidcraft.combat.TarkovFit.Assembly built = com.zomboidcraft.combat.TarkovFit.tarkov(spec)
                    ? com.zomboidcraft.combat.TarkovFit.of(held) : null;
            boolean[] hidden = built == null ? null : TarkovRig.hidden(spec, built, gun);
            if (built != null && spec.tarkov.folds != null && !spec.tarkov.folds.isEmpty()) {
                // its flip-up irons folded under a sight, as in first person
                float[][] fp = Operator.heldPose(gun);
                if (fp != null) {
                    TarkovRig.fold(spec, built, gun, fp[0], fp[1]);
                    gunBones = Operator.bones(gun, fp[0], fp[1]);
                    for (Matrix4f m : gunBones) new Matrix4f(bones[weapon]).mul(m, m);
                }
            }
            NpcSkinDraw.submitGroups(gun.groups, gunBones, 0, g -> g.piece.equals("gun") && TarkovRig.shows(g, hidden),
                    g -> gun.texture(g.texture, model), gunBuffers(state.id, gun), light, overlay, pose, collector);
            float[][] hp = built == null ? null : Operator.heldPose(gun);
            if (hp != null) {
                java.util.Map<String, Matrix4f> frames = TarkovRig.frames(spec, built, gun, hp[0], hp[1]);
                TarkovRig.draw(built, frames, bones[weapon], "tp" + state.id, light, overlay, pose, collector);
                tarkovRail(built, frames, bones[weapon], held, state);
            }
        }
        pose.popPose();
    }

    private static final java.util.Map<Integer, Object[][]> GUN_BUFFERS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, int[]> GUN_FLIP = new java.util.HashMap<>();

    /** Double-buffered skinning buffers for one player's held gun (sized to that gun's model). */
    private static Object[] gunBuffers(int id, NpcSkin gun) {
        Object[][] b = GUN_BUFFERS.computeIfAbsent(id, k -> new Object[2][]);
        int[] flip = GUN_FLIP.computeIfAbsent(id, k -> new int[1]);
        flip[0] = 1 - flip[0];
        if (b[flip[0]] == null || b[flip[0]].length != gun.groups.length * 2) b[flip[0]] = new Object[gun.groups.length * 2];
        if (GUN_BUFFERS.size() > 64) {
            GUN_BUFFERS.clear();
            GUN_FLIP.clear();
        }
        return b[flip[0]];
    }

    /**
     * Where his rifle's laser and light lenses are, for RailLights to shine them from (in the player model's pixels:
     * vanilla's model space, y down, facing -z, the way it reads the posed arms' rifles).
     */
    private static void rail(NpcSkin skin, Matrix4f[] bones, ItemStack gun, AvatarRenderState state) {
        LocalGuns.Spec spec = LocalGuns.of(gun);
        int weapon = skin.bone("weapon");
        if (spec == null || spec.rail == null || com.zomboidcraft.combat.TarkovFit.tarkov(spec) || weapon < 0 || !(Attachments.lightOn(gun) || Attachments.laserOn(gun))) return;
        Vector3f laser = lens(bones[weapon], spec.rail.laser), light = lens(bones[weapon], spec.rail.light);
        if (laser == null) laser = light;
        if (light == null) light = laser;
        if (laser != null) RailLights.seen(state, laser, light);
    }

    /** The same for a Tarkov gun as built: whatever fitted device has a laser or light, where it sits on the gun. */
    private static void tarkovRail(com.zomboidcraft.combat.TarkovFit.Assembly built, java.util.Map<String, Matrix4f> frames,
                                   Matrix4f weapon, ItemStack gun, AvatarRenderState state) {
        if (!(Attachments.lightOn(gun) || Attachments.laserOn(gun))) return;
        Vector3f laser = null, light = null;
        for (com.zomboidcraft.combat.TarkovFit.Node n : built.nodes()) {
            com.zomboidcraft.combat.TarkovParts.Part p = com.zomboidcraft.combat.TarkovParts.get(n.item());
            Matrix4f f = frames.get(n.path());
            if (p == null || f == null) continue;
            Matrix4f at = new Matrix4f(weapon).mul(f);
            if (laser == null && p.laser != null && p.laser.at != null) laser = pixels(at.transformPosition(new Vector3f(p.laser.at[0], p.laser.at[1], p.laser.at[2])));
            if (light == null && p.light != null && p.light.at != null) light = pixels(at.transformPosition(new Vector3f(p.light.at[0], p.light.at[1], p.light.at[2])));
        }
        if (laser == null) laser = light;
        if (light == null) light = laser;
        if (laser != null) RailLights.seen(state, laser, light);
    }

    /** His metres (feet on the ground, facing +z) into vanilla's model pixels, the way submit draws him. */
    private static Vector3f pixels(Vector3f m) {
        return new Vector3f(m.x / 0.9375f, 1.501f - m.y / 0.9375f, -m.z / 0.9375f).mul(16);
    }

    private static Vector3f lens(Matrix4f weapon, LocalGuns.Device d) {
        if (d == null || d.third == null) return null;
        // his metres (feet on the ground, facing +z) into vanilla's model pixels, the way submit draws him
        Vector3f m = weapon.transformPosition(new Vector3f(d.third[0], d.third[1], d.third[2]));
        return new Vector3f(m.x / 0.9375f, 1.501f - m.y / 0.9375f, -m.z / 0.9375f).mul(16);
    }
}
