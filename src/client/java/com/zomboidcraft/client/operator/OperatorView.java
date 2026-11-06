package com.zomboidcraft.client.operator;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The operator's first person with the kitted M4A1 (config/zomboidcraft/npc/operator_m4a1.zsk): Tarkov's own arms and
 * rifle, every move Tarkov's own clip (Josiah 2026-11-06: "we use every single option tarkov has to offer"). Drawing
 * up (hands_in), the idle, each shot (fire), the reload (mag_out_0, mag_in_0, and reload_charge_0 onto an empty gun,
 * which the game's reload is paced to: tarkov_m4a1.json "reload"), the inspect (look, on its own key) and aiming down
 * the EOTech (the rig moved so the sight's own eye point sits on the eye line, the pack's "ads").
 * The clips are baked in view space (metres, x right, y up, looking down -z), so the rig is drawn from the eye.
 */
public final class OperatorView {
    private OperatorView() {
    }

    public static final String MODEL = "operator_m4a1";
    public static final KeyMapping INSPECT = new KeyMapping("key.zomboidcraft.inspect", InputConstants.KEY_PERIOD,
            com.zomboidcraft.client.combat.CombatClient.CATEGORY);
    /** Vanilla's first-person arm offset from the eye (GunRigs.ARM): the pose RifleView is handed sits there. */
    private static final float[] ARM = {0.56f, -0.52f, -0.72f};

    public static void register() {
        KeyMappingHelper.registerKeyMapping(INSPECT);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (INSPECT.consumeClick()) {
                inspectAsked = true;
            }
        });
    }

    private static final String IDLE = "idle_armed", FIRE = "fire", DRAW = "hands_in", LOOK = "look";
    private static final String OUT = "mag_out_0", IN = "mag_in_0", CHARGE = "reload_charge_0";
    /** A gun's own empty reload, when its model has one (the PM: the slide locked back). */
    private static final String EMPTY_OUT = "empty_out", EMPTY_IN = "empty_in", EMPTY_CHARGE = "empty_charge";
    /** The first-person model drawn last (another gun drawn starts over from its draw). */
    private static String drawn;
    /** The holographic sight's mesh (its bone in the pack), and how far ahead of the eye its sight sits when aiming. */
    private static final String SCOPE = "scope_all_eotech_exps3_LOD0";
    private static final float EYE = 0.10f;
    /**
     * The reticle, drawn on the sight's glass (0.2 m out from its eye point, just this side of the lens) so it only
     * ever shows in the window: the EOTech's texture spans 2.54 degrees there, so the ring is the real sight's 68 MOA
     * (another gun's sight gives its own span, LocalGuns.Spec.reticleMoa).
     */
    private static final float RETICLE_AT = 0.195f;

    private static String clip = null;
    private static float clipStart;
    private static final Deque<String> queue = new ArrayDeque<>();
    private static boolean reloading;
    private static float lastDrawn = -1;
    private static int lastSlot = -1;
    private static float lastStep;
    private static boolean shot, inspectAsked;
    private static float aim, aimAt = -1;
    private static float[] pos, turn, fromPos, fromTurn, tmpPos, tmpTurn;
    private static float fadeStart, fadeLen;
    private static final Object[][] BUFFERS = new Object[2][];
    private static int buffer;

    /** A shot went off (CombatClient, through RifleView.fired): the recoil clip plays from its start. */
    public static void fired() {
        shot = true;
    }

    /**
     * Draws the operator's arms and rifle in place of RifleView's, from the pose it is handed (the vanilla arm frame).
     * False when the held gun isn't the operator's rifle or this PC hasn't got its file.
     */
    public static boolean submit(PoseStack pose, SubmitNodeCollector out, int light, PlayerRenderState state, ItemStack gun) {
        String model = Operator.fpModel(gun);
        if (model == null) return false;
        NpcSkin skin = NpcSkin.get(model);
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (skin == null || player == null || state.avatarRenderState == null) return false;
        if (state.avatarRenderState.isInvisible) return true;
        float now = state.avatarRenderState.ageInTicks / 20f;
        int n = skin.boneNames.length;
        if (pos == null || pos.length != n * 3 || !model.equals(drawn)) {
            drawn = model;
            pos = new float[n * 3];
            turn = new float[n * 4];
            fromPos = new float[n * 3];
            fromTurn = new float[n * 4];
            tmpPos = new float[n * 3];
            tmpTurn = new float[n * 4];
            clip = null;
        }

        // brought up: a new pick of the rifle (another slot, or not drawn for a moment)
        int slot = player.getInventory().getSelectedSlot();
        if (clip == null || slot != lastSlot || lastDrawn < 0 || now - lastDrawn > 0.3f || now < lastDrawn) {
            queue.clear();
            reloading = false;
            play(skin, DRAW, now, 0);
            aim = 0;
            sound(player, gun, "draw");
        }
        lastSlot = slot;
        lastDrawn = now;

        // the game's reload: its first step starts Tarkov's reload; an empty gun's ends with the charging handle
        float step = step(gun);
        if (lastStep == 0 && step >= 1 && !reloading) {
            reloading = true;
            queue.clear();
            boolean empty = step > 1.25f && step < 2;
            if (empty && skin.clips.containsKey(EMPTY_OUT)) {
                // a gun with its own empty reload (the PM's slide locked back: out, in, the slide let go)
                queue.add(EMPTY_IN);
                queue.add(EMPTY_CHARGE);
                play(skin, EMPTY_OUT, now, 0.12f);
            } else {
                queue.add(IN);
                if (empty) queue.add(CHARGE);
                play(skin, OUT, now, 0.12f);
            }
        } else if (step == 0 && reloading && remaining(skin, now) > 0.35f) {
            // called off (or done well early): back to the idle
            reloading = false;
            queue.clear();
            play(skin, IDLE, now, 0.2f);
        }
        lastStep = step;

        if (shot) {
            shot = false;
            if (!reloading && !DRAW.equals(clip)) play(skin, FIRE, now, 0.03f);
        }
        if (inspectAsked) {
            inspectAsked = false;
            if (!reloading && (IDLE.equals(clip) || FIRE.equals(clip))) play(skin, LOOK, now, 0.2f);
        }

        // a clip run out: the next in the queue, or the idle
        NpcSkin.Clip c = skin.clips.get(clip);
        if (c == null) return false;
        if (!IDLE.equals(clip) && now - clipStart >= c.seconds()) {
            String next = queue.poll();
            if (next == null) {
                reloading = false;
                next = IDLE;
            }
            play(skin, next, clipStart + c.seconds(), next.equals(IDLE) ? 0.15f : 0.05f);
            c = skin.clips.get(clip);
            if (c == null) return false;
        }
        Operator.sample(c, now - clipStart, IDLE.equals(clip) ? Operator.LOOP : Operator.HOLD, pos, turn);
        if (fadeLen > 0 && now - fadeStart < fadeLen) {
            System.arraycopy(fromPos, 0, tmpPos, 0, tmpPos.length);
            System.arraycopy(fromTurn, 0, tmpTurn, 0, tmpTurn.length);
            Operator.blend(tmpPos, tmpTurn, pos, turn, Operator.smooth((now - fadeStart) / fadeLen));
            System.arraycopy(tmpPos, 0, pos, 0, pos.length);
            System.arraycopy(tmpTurn, 0, turn, 0, turn.length);
        }

        // on the sights while aiming, unless the hands are busy
        boolean using = player.isUsingItem() && player.getUseItem() == gun;
        boolean free = IDLE.equals(clip) || FIRE.equals(clip);
        float dt = aimAt < 0 ? 0 : Math.max(0, Math.min(0.1f, now - aimAt));
        aimAt = now;
        aim = Math.max(0, Math.min(1, aim + (using && free ? dt : -dt) / 0.18f));

        pose.pushPose();
        pose.translate(-ARM[0], -ARM[1], -ARM[2]);
        // Tarkov's clips hold the rifle canted up off the eye line; the whole rig is turned about the sight so the
        // sight looks straight down the middle of the screen (Josiah 2026-11-06: it must point at the crosshair). Aiming
        // then slides it, unturned, until the sight sits on the eye (the pack's "ads": the sight 10 cm ahead of the eye)
        NpcSkin.Clip ads = skin.clips.get("ads");
        Matrix4f sightAt = null, hold = new Matrix4f();
        if (ads != null) {
            Quaternionf q = new Quaternionf(ads.turn()[0], ads.turn()[1], ads.turn()[2], ads.turn()[3]);
            Matrix4f onEye = new Matrix4f().translationRotate(ads.at()[0], ads.at()[1], ads.at()[2], q);
            // the sight in the rig (eye frame) at rest, and the eye-line turn about it
            sightAt = new Matrix4f(onEye).invert().translate(0, 0, -EYE);
            Vector3f p = sightAt.getTranslation(new Vector3f());
            Vector3f hip = new Vector3f(p).sub(q.transform(new Vector3f(p)));
            Vector3f t = hip.lerp(new Vector3f(ads.at()[0], ads.at()[1], ads.at()[2]), Operator.smooth(aim), new Vector3f());
            hold.translationRotate(t.x, t.y, t.z, q);
            pose.mulPose(hold);
        }
        rail(skin, gun, hold, free && !reloading);
        Matrix4f[] bones = Operator.bones(skin, pos, turn);
        buffer = 1 - buffer;
        if (BUFFERS[buffer] == null || BUFFERS[buffer].length != skin.groups.length * 2) {
            BUFFERS[buffer] = new Object[skin.groups.length * 2];
        }
        NpcSkinDraw.submitGroups(skin.groups, bones, 0, g -> true, g -> skin.texture(g.texture, model), BUFFERS[buffer],
                light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, out);
        LocalGuns.Spec spec = LocalGuns.of(gun);
        int scope = skin.bone(spec != null && spec.sight != null ? spec.sight : SCOPE);
        float lit = Operator.smooth((aim - 0.5f) / 0.5f);
        if (sightAt != null && scope >= 0 && lit > 0) {
            float moa = spec != null && spec.fp != null ? spec.reticleMoa : 152.4f;
            reticle(skin, model, new Matrix4f(bones[scope]).mul(sightAt), lit, moa, pose, out);
        }
        pose.popPose();
        return true;
    }

    /** The sight's own red reticle, glowing, on its line of sight (sight frame: x right, y up, looking down -z). */
    private static void reticle(NpcSkin skin, String model, Matrix4f sight, float lit, float moa, PoseStack pose,
                                SubmitNodeCollector out) {
        net.minecraft.resources.Identifier tex = skin.texture("reticle", model);
        if (tex == null) return;
        boolean shaders = com.zomboidcraft.client.combat.RailLights.shaderPackOn();
        int c = Math.round(255 * lit);
        int colour = 0xFF000000 | c << 16 | c << 8 | c;
        float h = (float) (Math.toRadians(moa / 60.0) * RETICLE_AT) / 2, z = -RETICLE_AT;
        pose.pushPose();
        pose.mulPose(sight);
        out.submitCustomGeometry(pose, shaders ? net.minecraft.client.renderer.rendertype.RenderTypes.beaconBeam(tex, true)
                : net.minecraft.client.renderer.rendertype.RenderTypes.eyes(tex), (p, v) -> {
            float[][] corners = {{-h, -h, 0, 1}, {h, -h, 1, 1}, {h, h, 1, 0}, {-h, h, 0, 0}};
            for (float[] k : corners) {
                v.addVertex(p, k[0], k[1], z).setColor(colour).setUv(k[2], k[3])
                        .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                        .setNormal(p, 0, 0, 1);
            }
        });
        pose.popPose();
    }

    /**
     * Where the rifle's own laser and light lenses are this frame, from the eye as the rig is drawn, for RailLights: on
     * whatever is under the crosshair while the hands are free, out along the barrel while they're busy.
     */
    private static void rail(NpcSkin skin, ItemStack gun, Matrix4f hold, boolean free) {
        LocalGuns.Spec spec = LocalGuns.of(gun);
        LocalGuns.Rail rail = spec == null ? null : spec.rail;
        if (rail == null) {
            com.zomboidcraft.client.combat.RifleView.operatorRail(null);
            return;
        }
        Vector3f laser = lens(skin, hold, rail.laser), light = lens(skin, hold, rail.light);
        LocalGuns.Device way = rail.laser != null ? rail.laser : rail.light;
        Vector3f bore = way == null || way.dir == null ? new Vector3f(0, 0, -1) : along(skin, hold, way);
        if (laser == null) laser = light != null ? light : new Vector3f();
        if (light == null) light = laser;
        com.zomboidcraft.client.combat.RifleView.operatorRail(
                new com.zomboidcraft.client.combat.RifleView.Rail(laser, light, bore, free ? 1 : 0));
    }

    /** A lens on a bone of the rig, from the eye. */
    private static Vector3f lens(NpcSkin skin, Matrix4f hold, LocalGuns.Device d) {
        int b = d == null || d.at == null ? -1 : skin.bone(d.bone);
        if (b < 0) return null;
        return boneNow(b, hold).transformPosition(new Vector3f(d.at[0], d.at[1], d.at[2]));
    }

    /** The way a lens shines, from the eye. */
    private static Vector3f along(NpcSkin skin, Matrix4f hold, LocalGuns.Device d) {
        int b = skin.bone(d.bone);
        if (b < 0) return new Vector3f(0, 0, -1);
        return boneNow(b, hold).transformDirection(new Vector3f(d.dir[0], d.dir[1], d.dir[2])).normalize();
    }

    /** A bone of the rig as drawn this frame (its own frame, not its skinning matrix), from the eye. */
    private static Matrix4f boneNow(int b, Matrix4f hold) {
        Quaternionf q = new Quaternionf(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
        return new Matrix4f(hold).mul(new Matrix4f().translationRotate(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2], q));
    }

    /** How far onto the sights the operator is (0 at the hip, 1 on the sights). */
    public static float aimShown() {
        return Operator.smooth(aim);
    }

    /** Whether the operator's rifle is in hand and drawn by Tarkov's clips (Minecraft's bob and hand lag are left off). */
    public static boolean steady() {
        LocalPlayer player = Minecraft.getInstance().player;
        String model = player == null ? null : Operator.fpModel(player.getMainHandItem());
        return model != null && NpcSkin.get(model) != null;
    }

    /** Whether the operator's first person is drawing the rifle now (RifleView leaves its own arms off). */
    public static boolean busy() {
        return reloading;
    }

    private static void play(NpcSkin skin, String name, float at, float fade) {
        if (!skin.clips.containsKey(name)) name = IDLE;
        if (clip != null && fade > 0) {
            System.arraycopy(pos, 0, fromPos, 0, pos.length);
            System.arraycopy(turn, 0, fromTurn, 0, turn.length);
            fadeStart = at;
            fadeLen = fade;
        } else {
            fadeLen = 0;
        }
        clip = name;
        clipStart = at;
    }

    /** Seconds left of the clips still to play. */
    private static float remaining(NpcSkin skin, float now) {
        NpcSkin.Clip c = skin.clips.get(clip);
        float left = c == null ? 0 : Math.max(0, c.seconds() - (now - clipStart));
        for (String q : queue) {
            NpcSkin.Clip k = skin.clips.get(q);
            if (k != null) left += k.seconds();
        }
        return left;
    }

    private static float step(ItemStack gun) {
        CustomModelData data = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.floats().isEmpty() ? 0 : data.floats().get(0);
    }

    /** A sound of the rifle's own from the Tarkov sound pack (weapon.local/tarkov_m4a1/NAME), heard only here. */
    private static void sound(LocalPlayer player, ItemStack gun, String name) {
        LocalGuns.Spec spec = LocalGuns.of(gun);
        String id = spec != null && spec.id != null ? spec.id : Operator.GUN;
        SoundEvent e = SoundEvent.createVariableRangeEvent(ZomboidCraft.id(com.zomboidcraft.combat.LocalGuns.SOUND_PREFIX
                + id.toLowerCase(java.util.Locale.ROOT) + "/" + name));
        player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), e, SoundSource.PLAYERS, 0.8f, 1f, false);
    }
}
