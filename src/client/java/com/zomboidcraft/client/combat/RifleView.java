package com.zomboidcraft.client.combat;

import static com.zomboidcraft.client.combat.RifleRigData.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.GunState;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The rifle in first person, posed by the mod on top of its item model: it eases up to the sights and back, cants
 * and settles through a reload while the old magazine drops away, the off hand fetches a new one and seats it and
 * works the charging handle on an empty gun, and the player's own arms hold it (see {@link RifleArms}).
 *
 * The item model still picks a pose for each reload step, so third person and other views stay right. Here the
 * pose stack the game draws it with is corrected from the pose the model applies to the one the animation wants.
 * The steps come from the server (Guns), and the animation runs on these same timings between them.
 */
public final class RifleView {
    // reload timeline, in ticks (Guns uses the same numbers)
    private static final float MAG_IN = 16, CHARGE = 22, JAM_START = 18;
    private static final float END_TACTICAL = 25, END_CHARGE = 34;
    private static final float AIM_TICKS = 3.5f;
    /** Where the off hand goes to fetch a new magazine: below the bottom of the screen, in the hand frame. */
    private static final Vector3f POUCH = new Vector3f(-0.55f, -0.50f, 0.10f);
    /** How the gun is turned (degrees, about the grip) and moved (blocks) to work the charging handle. */
    private static final float[] CHARGE_TURN = {20, 25, 0};
    private static final Vector3f CHARGE_PUSH = new Vector3f(-0.08f, 0.10f, -0.30f);

    private static int lastStep;
    private static boolean hadMag;
    private static boolean active, charge, jam, oldMag;
    private static float clockStart;
    private static float aim, aimO;
    /** How far onto the sights the gun was drawn last frame (0 to 1): the bob and sway ease with it. */
    private static float aimShown;
    /** The laser's lens in the hand camera when the gun was last drawn, for the beam's start. */
    private static Vector3f laserAt;

    private static final ItemStackRenderState LOOSE_MAG = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_HANDLE = new ItemStackRenderState();
    private static ItemStack looseMag, looseHandle;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RifleView::tick);
    }

    // ------------------------------------------------------------------ following the server's steps

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        ItemStack gun = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (!gun.is(ModItems.ASSAULT_RIFLE)) {
            active = false;
            lastStep = 0;
            aim = aimO = 0;
            hadMag = false;
            return;
        }
        float raw = step(gun);
        int step = (int) raw;
        int age = player.tickCount;
        if (step != lastStep) {
            if (step == 1) {
                start(age, 0);
                charge = raw > 1.25f;    // the server marks a reload onto an empty chamber with 1.5
                jam = false;
                oldMag = hadMag;
            } else if (step == 2) {
                if (active) {
                    resync(age, MAG_IN);
                } else {
                    start(age, MAG_IN);
                    charge = jam = oldMag = false;
                }
            } else if (step == 3) {
                if (active && lastStep != 0) {
                    resync(age, CHARGE);
                } else {
                    start(age, JAM_START);     // clearing a jam: just the charging handle
                    charge = jam = true;
                    oldMag = false;
                }
            }
            lastStep = step;
        }
        if (active && age - clockStart > (charge ? END_CHARGE : END_TACTICAL)) {
            active = false;
        }
        hadMag = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).mag();
        aimO = aim;
        boolean aiming = step == 0 && player.isUsingItem() && player.getUseItem().is(ModItems.ASSAULT_RIFLE);
        aim = Mth.clamp(aim + (aiming ? 1f : -1f) / AIM_TICKS, 0f, 1f);
    }

    private static void start(int age, float t) {
        active = true;
        clockStart = age - t;
    }

    private static void resync(int age, float t) {
        if (Math.abs(age - clockStart - t) > 1.5f) {
            clockStart = age - t;
        }
    }

    private static float step(ItemStack gun) {
        CustomModelData data = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.floats().isEmpty() ? 0 : data.floats().get(0);
    }

    private static String optic(ItemStack gun) {
        CustomModelData data = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        List<String> keys = data == null ? List.of() : data.strings();
        return keys.isEmpty() ? "none" : keys.get(0);
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws the held rifle in place of the plain item render: the gun with its pose corrected, the loose reload
     * pieces, the arms. Returns false (and draws nothing) when the game should draw it as usual.
     */
    public static boolean submit(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector out, int light,
                                 PlayerRenderState state, ItemStack gun, Runnable drawGun) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !gun.is(ModItems.ASSAULT_RIFLE) || player.getMainArm() != HumanoidArm.RIGHT
                || state.avatarRenderState == null) {
            return false;
        }
        ZcConfig config = ZcConfig.get();
        float age = state.avatarRenderState.ageInTicks;
        int step = (int) step(gun);
        boolean using = player.isUsingItem() && player.getUseItem() == gun;
        String optic = optic(gun);

        // the pose the item model applies, which the correction starts from
        Pose shown;
        if (step >= 3) {
            shown = hip(RELOAD_POSE.get("charge")[0], RELOAD_POSE.get("charge")[1], 0);
        } else if (step >= 1) {
            shown = hip(RELOAD_POSE.get("mag_out")[0], RELOAD_POSE.get("mag_out")[1], 0);
        } else if (using) {
            shown = sights(optic);
            if (shown == null) {
                aimShown = 1;       // through the scope the game draws the scope picture instead of the gun
                laserAt = null;
                return true;
            }
        } else {
            shown = hip(0, 0, 0);
        }

        // the pose the animation wants
        float t = active ? age - clockStart : -1;
        boolean animate = config.guns.reloadAnimation && active;
        float a = smooth(Mth.lerp(partial(age), aimO, aim));
        Pose sights = sights(optic);
        if (sights == null) {
            a = 0;
        }
        Pose want = animate ? reloadPose(t) : step >= 1 ? shown : hip(0, 0, 0);
        if (a > 0 && step == 0) {
            want = want.lerp(sights, a);
        }
        aimShown = step == 0 ? a : 0;

        pose.pushPose();
        breathe(pose, age, aimShown, (float) config.guns.aimSway);
        // the gun: undo the model's pose, apply the wanted one
        pose.pushPose();
        want.apply(pose);
        shown.unapply(pose);
        drawGun.run();
        pose.popPose();

        laserAt = want.point(LASER).add(ARM[0], ARM[1], ARM[2]);
        if (animate) {
            drawLoose(pose, out, light, player, want, t, step);
        } else if (step == 3) {
            drawHandle(pose, out, light, player, want, 0);    // the model leaves the handle to us in this step
        }
        if (config.look.firstPersonArms && !state.avatarRenderState.isInvisible) {
            boolean foregrip = optionalKey(gun, 2).equals("foregrip");
            Hand left = animate ? leftHand(want, t, foregrip) : support(want, foregrip, a);
            Hand right = new Hand(want.point(GRIP), lerp(RIGHT_HIP, RIGHT_ADS, a), RIGHT_HINT);
            RifleArms.draw(pose, out, light, player, state.avatarRenderState, right,
                    player.getOffhandItem().isEmpty() ? left : null, want.scale / HIP_SCALE * 0.6f);
        }
        pose.popPose();
        return true;
    }

    /** The slow drift of a held breath while aiming, so the sights are never quite dead still. */
    private static void breathe(PoseStack pose, float age, float aimed, float sway) {
        float amount = aimed * sway;
        if (amount <= 0) {
            return;
        }
        float pitch = Mth.sin(age * 0.071f) * 0.9f * amount, yaw = Mth.sin(age * 0.047f + 1.3f) * 0.6f * amount;
        // turn about the eye: the hand frame sits at ARM from it
        pose.translate(-ARM[0], -ARM[1], -ARM[2]);
        pose.rotate(new Quaternionf().rotationXYZ(pitch * Mth.DEG_TO_RAD, yaw * Mth.DEG_TO_RAD, 0));
        pose.translate(ARM[0], ARM[1], ARM[2]);
    }

    /** The old magazine falling away, the new one coming up and in, and the charging handle being worked. */
    private static void drawLoose(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                  float t, int step) {
        if (oldMag && t < 10) {
            // it slides out of the well, then falls clear of the screen, turning as it goes
            float slide = 4.5f * easeIn(t, 0.5f, 2.5f);
            float fall = Math.max(0, t - 2.5f);
            Vector3f centre = gun.point(new Vector3f(MAG_CENTRE[0], MAG_CENTRE[1] - slide, MAG_CENTRE[2]));
            pose.pushPose();
            pose.translate(0, -(0.03f * fall * fall + 0.04f * fall), 0);
            pose.translate(centre.x, centre.y, centre.z);
            pose.rotate(new Quaternionf().rotationXYZ(-7f * fall * Mth.DEG_TO_RAD, 0, 4f * fall * Mth.DEG_TO_RAD));
            pose.translate(-centre.x, -centre.y, -centre.z);
            drawPiece(pose, out, light, player, gun, magLocal(-slide, 0), true);
            pose.popPose();
        }
        if (!jam && step < 2 && t >= 9) {
            pose.pushPose();
            drawPiece(pose, out, light, player, gun, newMag(t), true);
            pose.popPose();
        }
        if (step == 3) {
            drawHandle(pose, out, light, player, gun, handlePull(t));
        }
    }

    private static void drawHandle(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun, float pull) {
        pose.pushPose();
        drawPiece(pose, out, light, player, gun, new Matrix4f().translation(0, 0, pull), false);
        pose.popPose();
    }

    /** Draws a loose piece of the gun (drawn in the gun's own model space), moved by local (item pixels). */
    private static void drawPiece(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                  Matrix4f local, boolean mag) {
        Minecraft mc = Minecraft.getInstance();
        if (looseMag == null) {
            looseMag = new ItemStack(ModItems.RIFLE_MAGAZINE);
            looseMag.set(DataComponents.ITEM_MODEL, ZomboidCraft.id("assault_rifle_loose_mag"));
            looseHandle = new ItemStack(ModItems.ASSAULT_RIFLE);
            looseHandle.set(DataComponents.ITEM_MODEL, ZomboidCraft.id("assault_rifle_loose_charging_handle"));
        }
        ItemStackRenderState state = mag ? LOOSE_MAG : LOOSE_HANDLE;
        mc.getItemModelResolver().updateForTopItem(state, mag ? looseMag : looseHandle, ItemDisplayContext.FIXED,
                player.level(), player, 0);
        gun.apply(pose);
        // item pixels to the model's own unit cube, the move, and back (the item render takes off half a block)
        pose.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f unit = new Matrix4f().scaling(1 / 16f).mul(local).scale(16f);
        pose.mulPose(unit);
        pose.translate(0.5f, 0.5f, 0.5f);
        state.submit(pose, out, light, OverlayTexture.NO_OVERLAY, 0);
    }

    /** A magazine moved along the well (negative is down out of it) and rocked forward about the well's mouth. */
    private static Matrix4f magLocal(float along, float rock) {
        return new Matrix4f().translation(MAG_TOP[0], MAG_TOP[1] + along, MAG_TOP[2])
                .rotateX(rock * Mth.DEG_TO_RAD)
                .translate(-MAG_TOP[0], -MAG_TOP[1], -MAG_TOP[2]);
    }

    /** The new magazine: up from below the screen, slowing as it nears the well, rocked in and seated at MAG_IN. */
    private static Matrix4f newMag(float t) {
        if (t < 15) {
            float u = Mth.clamp((t - 9) / 6f, 0, 1);
            float out = 1 - (1 - u) * (1 - u) * (1 - u);
            return magLocal(Mth.lerp(out, -30f, -3.5f), Mth.lerp(out, 22f, 7f));
        }
        float u = smooth(Mth.clamp((t - 15) / (MAG_IN - 15), 0, 1));
        return magLocal(Mth.lerp(u, -3.5f, 0f), Mth.lerp(u, 7f, 0f));
    }

    /** Item pixels the charging handle is back: drawn back, then let go so it snaps home. */
    private static float handlePull(float t) {
        if (t < CHARGE) {
            return 0;
        }
        if (t < 24.6f) {
            return HANDLE_TRAVEL * smooth((t - CHARGE) / (24.6f - CHARGE));
        }
        return HANDLE_TRAVEL * (1 - easeIn(t, 24.6f, 25.2f));
    }

    // ------------------------------------------------------------------ poses

    /** The gun through the reload: canted for the magazine, over the other way for the charging handle. */
    private static Pose reloadPose(float t) {
        float out, chg;
        if (jam) {
            out = 0;
            chg = smooth01(t, JAM_START, CHARGE) * (1 - smooth01(t, 26, 33));
        } else {
            float in = smooth01(t, 0, 5);
            if (charge) {
                float over = smooth01(t, 17, CHARGE);
                out = in * (1 - over);
                chg = over * (1 - smooth01(t, 26, 33));
            } else {
                out = in * (1 - smooth01(t, 17, 24));
                chg = 0;
            }
        }
        float[] o = RELOAD_POSE.get("mag_out"), c = RELOAD_POSE.get("charge");
        // the magazine slapped home and the handle let go each jolt the muzzle up a little
        float kick = bump(t, MAG_IN, 2.5f) * 3f + (charge ? bump(t, 24.6f, 2.5f) * 2f : 0);
        Pose gun = hip(o[0] * out + c[0] * chg, o[1] * out + c[1] * chg, kick);
        if (chg <= 0) {
            return gun;
        }
        // for the charging handle the gun comes up and out, muzzle high and to the left, so the hand on the back
        // of the receiver doesn't hide the whole gun: turned about the grip, then pushed away
        Vector3f grip = gun.point(GRIP);
        Quaternionf q = new Quaternionf().rotationXYZ(CHARGE_TURN[0] * chg * Mth.DEG_TO_RAD,
                CHARGE_TURN[1] * chg * Mth.DEG_TO_RAD, CHARGE_TURN[2] * chg * Mth.DEG_TO_RAD).mul(gun.q());
        Pose turned = new Pose(new Vector3f(gun.t()), q, gun.scale());
        turned.t().add(grip.sub(turned.point(GRIP))).add(new Vector3f(CHARGE_PUSH).mul(chg));
        return turned;
    }

    /** The hip pose turned by a cant (and a muzzle kick) about the grip, and dropped: the same sums as the model's. */
    private static Pose hip(float cant, float drop, float kick) {
        Quaternionf q = new Quaternionf().rotationXYZ((HIP_TURN[0] + cant * 0.6f + kick) * Mth.DEG_TO_RAD,
                (HIP_TURN[1] + cant * 0.3f) * Mth.DEG_TO_RAD, (HIP_TURN[2] - cant) * Mth.DEG_TO_RAD);
        Vector3f grip = q.transform(new Vector3f(GRIP[0] - 8, GRIP[1] - 8, GRIP[2] - 8)).mul(HIP_SCALE / 16f);
        Vector3f t = new Vector3f(HIP_GRIP[0] - ARM[0], HIP_GRIP[1] - drop - ARM[1], HIP_GRIP[2] - ARM[2]).sub(grip);
        return new Pose(t, q, HIP_SCALE);
    }

    /** The pose on the sights for an optic, or null when the gun isn't drawn (the scope). */
    private static Pose sights(String optic) {
        float[] d = switch (optic) {
            case "red_dot" -> ADS_RED_DOT;
            case "holo" -> ADS_HOLO;
            case "scope_4x" -> null;
            default -> ADS_IRONS;
        };
        return d == null ? null : new Pose(new Vector3f(d[0] / 16, d[1] / 16, d[2] / 16), new Quaternionf(), d[3]);
    }

    /** A display transform: translate (blocks), turn, uniform scale. */
    record Pose(Vector3f t, Quaternionf q, float scale) {
        void apply(PoseStack pose) {
            pose.translate(t.x, t.y, t.z);
            pose.rotate(q);
            pose.scale(scale, scale, scale);
        }

        void unapply(PoseStack pose) {
            pose.scale(1 / scale, 1 / scale, 1 / scale);
            pose.rotate(new Quaternionf(q).conjugate());
            pose.translate(-t.x, -t.y, -t.z);
        }

        Pose lerp(Pose o, float a) {
            return new Pose(new Vector3f(t).lerp(o.t, a), new Quaternionf(q).slerp(o.q, a), Mth.lerp(a, scale, o.scale));
        }

        /** An item-pixel point of the gun, in the hand frame. */
        Vector3f point(float[] px) {
            return point(new Vector3f(px[0], px[1], px[2]));
        }

        Vector3f point(Vector3f px) {
            Vector3f p = new Vector3f(px).div(16).sub(0.5f, 0.5f, 0.5f).mul(scale);
            return q.transform(p).add(t);
        }
    }

    // ------------------------------------------------------------------ hands

    /** A hand: where it holds (hand frame), the way its forearm runs back from there, and where its palm faces. */
    record Hand(Vector3f at, Vector3f elbow, Vector3f palm) {
        Hand lerp(Hand o, float a) {
            return new Hand(new Vector3f(at).lerp(o.at, a), RifleView.lerp(elbow, o.elbow, a), RifleView.lerp(palm, o.palm, a));
        }
    }

    private static final Vector3f RIGHT_HIP = new Vector3f(0.35f, -0.55f, 0.75f);
    private static final Vector3f RIGHT_ADS = new Vector3f(0.55f, -0.6f, 0.6f);
    private static final Vector3f RIGHT_HINT = new Vector3f(-1f, 0f, 0f);
    private static final Vector3f LEFT_HIP = new Vector3f(-0.25f, -0.55f, 0.8f);
    private static final Vector3f LEFT_ADS = new Vector3f(-0.3f, -0.65f, 0.7f);
    private static final Vector3f LEFT_GRIP = new Vector3f(-0.3f, -0.7f, 0.65f);
    private static final Vector3f PALM_UP = new Vector3f(-0.2f, -1f, 0f);
    private static final Vector3f PALM_IN = new Vector3f(-1f, -0.2f, 0f);

    /** The off hand on the handguard (palm up under it) or round the foregrip. */
    private static Hand support(Pose gun, boolean foregrip, float aimed) {
        if (foregrip) {
            return new Hand(gun.point(FOREGRIP), LEFT_GRIP, PALM_IN);
        }
        return new Hand(gun.point(SUPPORT), lerp(LEFT_HIP, LEFT_ADS, aimed), PALM_UP);
    }

    private static Hand onMag(Pose gun, Matrix4f mag) {
        Vector3f hold = mag.transformPosition(new Vector3f(MAG_HOLD[0], MAG_HOLD[1], MAG_HOLD[2]));
        return new Hand(gun.point(hold), new Vector3f(-0.1f, -0.9f, 0.42f), new Vector3f(-0.5f, -1f, 0f));
    }

    private static Hand onHandle(Pose gun, float pull) {
        return new Hand(gun.point(new Vector3f(HANDLE[0], HANDLE[1], HANDLE[2] + pull)),
                new Vector3f(-0.4f, -0.85f, 0.25f), new Vector3f(-1f, -0.4f, 0f));
    }

    private static Hand pouch() {
        return new Hand(new Vector3f(POUCH), new Vector3f(-0.2f, -0.75f, 0.6f), PALM_UP);
    }

    /** Where the off hand is through the reload. */
    private static Hand leftHand(Pose gun, float t, boolean foregrip) {
        Hand rest = support(gun, foregrip, 0);
        if (!jam && t < 9) {
            return rest.lerp(pouch(), smooth01(t, 0, 5));      // off to fetch the new magazine
        }
        Matrix4f seated = newMag(Math.min(t, MAG_IN));
        if (!jam && t < 17.5f) {
            return onMag(gun, seated);                          // bringing it up and pushing it home
        }
        if (!charge) {
            return onMag(gun, seated).lerp(rest, smooth01(t, 17.5f, 23));
        }
        Hand from = jam ? rest : onMag(gun, seated);
        float reachStart = jam ? JAM_START : 17.5f;
        if (t < CHARGE) {
            return from.lerp(onHandle(gun, 0), smooth01(t, reachStart, CHARGE));
        }
        if (t < 25.6f) {
            return onHandle(gun, t < 24.6f ? handlePull(t) : HANDLE_TRAVEL);   // it lets go at full travel
        }
        return onHandle(gun, HANDLE_TRAVEL).lerp(rest, smooth01(t, 25.6f, 31));
    }

    // ------------------------------------------------------------------ what other code asks

    /** How much of the walking bob and look sway to keep this frame: all of it at the hip, aimSway on the sights. */
    public static float bobScale() {
        float sway = (float) ZcConfig.get().guns.aimSway;
        return 1 - (1 - Mth.clamp(sway, 0, 1)) * aimShown;
    }

    /** The laser's lens in the hand camera (x right, y up, z back), or null when the gun isn't drawn. */
    public static Vector3f laserInCamera() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType().isFirstPerson() && mc.player != null
                && mc.player.getMainHandItem().is(ModItems.ASSAULT_RIFLE) ? laserAt : null;
    }

    // ------------------------------------------------------------------ helpers

    private static String optionalKey(ItemStack gun, int slot) {
        CustomModelData data = gun.get(DataComponents.CUSTOM_MODEL_DATA);
        return data == null || data.strings().size() <= slot ? "none" : data.strings().get(slot);
    }

    private static float partial(float age) {
        return age - Mth.floor(age);
    }

    static Vector3f lerp(Vector3f a, Vector3f b, float t) {
        return new Vector3f(a).lerp(b, t);
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static float smooth01(float t, float t0, float t1) {
        return smooth((t - t0) / (t1 - t0));
    }

    private static float easeIn(float t, float t0, float t1) {
        float x = Mth.clamp((t - t0) / (t1 - t0), 0, 1);
        return x * x;
    }

    /** A quick jolt that settles: 0 before t0, up and back to 0 over the next dur ticks. */
    private static float bump(float t, float t0, float dur) {
        float x = (t - t0) / dur;
        return x <= 0 || x >= 1 ? 0 : Mth.sin(x * Mth.PI) * (1 - x);
    }

    private RifleView() {
    }
}
