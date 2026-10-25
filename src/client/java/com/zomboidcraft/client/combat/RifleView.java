package com.zomboidcraft.client.combat;

import static com.zomboidcraft.client.combat.GunRigs.ARM;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunState;
import com.zomboidcraft.combat.GunType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * Guns in first person, posed by the mod on top of their item models: a gun eases up to the sights and back, cants
 * and settles through a reload while the old magazine drops away, the off hand fetches a new one and seats it and
 * works the charging handle (a pistol's slide) on an empty gun, and the player's own arms hold it (see
 * {@link RifleArms}). A pistol's slide runs back with every shot and locks open on the last one. A pump shotgun
 * is canted over to load, and the off hand pushes its shells into the loading port one at a time, then rides the
 * pump to work it after each shot. Each gun's numbers are its {@link GunRig}.
 *
 * The item model still picks a pose for each reload step, so third person and other views stay right. Here the
 * pose stack the game draws it with is corrected from the pose the model applies to the one the animation wants.
 * The steps come from the server (Guns), and the animation runs on the same timings between them.
 */
public final class RifleView {
    // The animation is timed on the assault rifle's reload, in ticks (its GunType has the same numbers); another
    // gun's steps are stretched onto these (see anim).
    private static final float MAG_IN = 16, CHARGE = 22, JAM_START = 18;
    private static final float END_TACTICAL = 25, END_CHARGE = 34;
    /** When the hand lets go of the handle (or slide), and when it's home. */
    private static final float RELEASE = 24.6f, HOME = 25.2f;
    private static final float AIM_TICKS = 3.5f;
    /** Where the off hand goes to fetch a new magazine: below the bottom of the screen, in the hand frame. */
    private static final Vector3f POUCH = new Vector3f(-0.55f, -0.50f, 0.10f);

    /** The gun in hand and its rig, as of the last tick or frame. */
    private static GunType type = GunType.ASSAULT_RIFLE;
    private static GunRig rig = GunRigs.ALL.get(GunType.ASSAULT_RIFLE.id());
    private static GunType held;
    private static int lastStep;
    private static boolean hadMag;
    private static boolean active, charge, jam, oldMag;
    private static float clockStart;
    private static float aim, aimO;
    /** How far onto the sights the gun was drawn last frame (0 to 1): the bob and sway ease with it. */
    private static float aimShown;
    /** The laser's lens in the hand camera when the gun was last drawn, for the beam's start. */
    private static Vector3f laserAt;
    /** A tube gun's loading: when it stopped, and when the pump was taken up after it (ages, or -1); shells in it. */
    private static float stopAt = -1, pumpFrom = -1;
    private static int shells;

    private static final ItemStackRenderState LOOSE_MAG = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_HANDLE = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_FLASH = new ItemStackRenderState();
    /** Each gun's loose pieces: its magazine (a tube gun's: a shell), its handle, slide or pump, its flash. */
    private static final Map<GunType, ItemStack[]> LOOSE = new EnumMap<>(GunType.class);

    /** The last shot: its tick, then its first frame; whether it flashed, and whether it was the last round. */
    private static final float FLASH_LIFE = 1.6f;
    private static int shotTick = -100;
    private static boolean shotWaiting, shotFlash, shotEmptied;
    private static float shotAt = -100, flashRoll, flashSize;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(RifleView::tick);
    }

    // ------------------------------------------------------------------ following the server's steps

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        ItemStack gun = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        GunType now = GunType.of(gun);
        if (now != held) {
            held = now;
            active = false;
            lastStep = 0;
            aim = aimO = 0;
            hadMag = false;
            shotEmptied = false;
        }
        if (now == null) {
            return;
        }
        use(now);
        float raw = step(gun);
        int step = (int) raw;
        int age = player.tickCount;
        if (type.tube) {
            tickTube(gun, raw, step, age);
        } else {
            tickMagazine(raw, step, age);
        }
        hadMag = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).mag();
        aimO = aim;
        boolean aiming = step == 0 && player.isUsingItem() && GunType.isGun(player.getUseItem());
        aim = Mth.clamp(aim + (aiming ? 1f : -1f) / AIM_TICKS, 0f, 1f);
    }

    private static void tickMagazine(float raw, int step, int age) {
        if (step != lastStep) {
            if (step == 1) {
                start(age, 0);
                charge = raw > 1.25f;    // the server marks a reload onto an empty chamber with 1.5
                jam = false;
                oldMag = hadMag;
                shotEmptied = false;
            } else if (step == 2) {
                if (active) {
                    resync(age, type.magIn);
                } else {
                    start(age, type.magIn);
                    charge = jam = oldMag = false;
                }
            } else if (step == 3) {
                if (active && lastStep != 0) {
                    resync(age, type.charge);
                } else {
                    start(age, type.jamClear);     // clearing a jam: just the charging handle
                    charge = jam = true;
                    oldMag = false;
                }
            }
            lastStep = step;
        }
        if (active && age - clockStart > (charge ? type.chargeDone : type.tacticalDone)) {
            active = false;
        }
    }

    /**
     * A tube gun: step 1 while shells go in (1.5 onto an empty chamber), then 0 as it comes down, or 3 while the
     * pump is worked to chamber one. The hand's pushes keep to the shells the server puts in.
     */
    private static void tickTube(ItemStack gun, float raw, int step, int age) {
        int rounds = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds();
        if (step != lastStep) {
            if (step == 1) {
                start(age, 0);
                charge = raw > 1.25f;
                stopAt = pumpFrom = -1;
            } else if (active && stopAt < 0) {
                stopAt = age;           // full, out of shells, or the trigger pulled
            }
            if (step == 3) {
                if (!active) {
                    start(age, 0);      // only the pump: never canted over
                    stopAt = age;
                }
                pumpFrom = age;
            }
            lastStep = step;
        }
        if (active && stopAt < 0 && rounds > shells) {
            int k = Math.max(1, Math.round((age - clockStart - type.raise) / type.perShell));
            resync(age, type.raise + k * type.perShell);
        }
        shells = rounds;
        if (active && stopAt >= 0 && age - stopAt > (pumpFrom >= 0 ? PUMP_DONE : LOWER) + 1) {
            active = false;
        }
    }

    private static void use(GunType gun) {
        if (gun != type) {
            type = gun;
            rig = GunRigs.ALL.get(gun.id());
        }
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

    /** Ticks into this gun's reload to ticks into the animation: the same moves, stretched between its own steps. */
    private static float anim(float t) {
        if (jam) {
            return piecewise(t, new float[] {type.jamClear, type.charge, type.chargeDone}, new float[] {JAM_START, CHARGE, END_CHARGE});
        }
        if (charge) {
            return piecewise(t, new float[] {0, type.magIn, type.charge, type.chargeDone}, new float[] {0, MAG_IN, CHARGE, END_CHARGE});
        }
        return piecewise(t, new float[] {0, type.magIn, type.tacticalDone}, new float[] {0, MAG_IN, END_TACTICAL});
    }

    private static float piecewise(float t, float[] from, float[] to) {
        int i = 1;
        while (i < from.length - 1 && t > from[i]) {
            i++;
        }
        return Mth.lerp((t - from[i - 1]) / (from[i] - from[i - 1]), to[i - 1], to[i]);
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
     * Draws the held gun in place of the plain item render: the gun with its pose corrected, its handle or slide,
     * the loose reload pieces, the arms. Returns false (and draws nothing) when the game should draw it as usual.
     */
    public static boolean submit(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector out, int light,
                                 PlayerRenderState state, ItemStack gun, Runnable drawGun) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        GunType gunType = GunType.of(gun);
        if (player == null || gunType == null || player.getMainArm() != HumanoidArm.RIGHT
                || state.avatarRenderState == null) {
            return false;
        }
        use(gunType);
        ZcConfig config = ZcConfig.get();
        float age = state.avatarRenderState.ageInTicks;
        int step = (int) step(gun);
        boolean using = player.isUsingItem() && player.getUseItem() == gun;
        String optic = optic(gun);
        if (shotWaiting) {
            shotWaiting = false;
            if (player.tickCount - shotTick <= 2) {
                shotAt = age;
                flashRoll = player.getRandom().nextFloat() * (float) (Math.PI * 2);
                flashSize = 0.85f + player.getRandom().nextFloat() * 0.3f;
            }
        }

        // the pose the item model applies, which the correction starts from
        Pose shown;
        if (step >= 3) {
            shown = hip(rig.charge()[0], rig.charge()[1], 0);
        } else if (step >= 1) {
            shown = hip(rig.magOut()[0], rig.magOut()[1], 0);
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

        // the pose the animation wants (a tube gun's goes by age)
        float t = active ? rig.pump() ? age - clockStart : anim(age - clockStart) : -1;
        boolean animate = config.guns.reloadAnimation && active;
        float a = smooth(Mth.lerp(partial(age), aimO, aim));
        Pose sights = sights(optic);
        if (sights == null) {
            a = 0;
        }
        Pose want = animate ? rig.pump() ? tubePose(age) : reloadPose(t) : step >= 1 ? shown : hip(0, 0, 0);
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

        // the model leaves the handle, slide or pump to us in first person
        float pulled = pull(gun, t, animate, age);
        drawHandle(pose, out, light, player, want, pulled);
        drawFlash(pose, out, player, want, gun, age);
        laserAt = want.point(rig.laser()).add(ARM[0], ARM[1], ARM[2]);
        if (animate) {
            drawLoose(pose, out, light, player, want, t, step, age);
        }
        if (config.look.firstPersonArms && !state.avatarRenderState.isInvisible) {
            boolean foregrip = optionalKey(gun, 2).equals("foregrip");
            float pump = rig.pump() ? pulled : 0;
            Hand left = !animate ? support(want, foregrip, a, pump)
                    : rig.pump() ? tubeHand(want, age, pump) : leftHand(want, t, foregrip);
            Hand right = new Hand(want.point(rig.grip()), lerp(RIGHT_HIP, RIGHT_ADS, a), RIGHT_HINT);
            RifleArms.draw(pose, out, light, player, state.avatarRenderState, right,
                    player.getOffhandItem().isEmpty() ? left : null, want.scale / rig.hipScale() * 0.6f);
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

    /** The old magazine falling away, and the new one coming up and in; or a tube gun's shell. */
    private static void drawLoose(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                  float t, int step, float age) {
        if (rig.pump()) {
            float u = stopAt < 0 ? shellTime(age) : -1;
            if (u >= FETCHED) {
                pose.pushPose();
                drawPiece(pose, out, light, player, gun, shellLocal(u, SHELL_FWD), LOOSE_MAG);
                pose.popPose();
            }
            return;
        }
        if (oldMag && t < 10) {
            // it slides out of the well, then falls clear of the screen, turning as it goes
            float slide = 4.5f * easeIn(t, 0.5f, 2.5f);
            float fall = Math.max(0, t - 2.5f);
            float[] c = rig.magCentre(), axis = rig.magAxis();
            Vector3f centre = gun.point(new Vector3f(c[0] + axis[0] * slide, c[1] + axis[1] * slide, c[2] + axis[2] * slide));
            pose.pushPose();
            pose.translate(0, -(0.03f * fall * fall + 0.04f * fall), 0);
            pose.translate(centre.x, centre.y, centre.z);
            pose.rotate(new Quaternionf().rotationXYZ(-7f * fall * Mth.DEG_TO_RAD, 0, 4f * fall * Mth.DEG_TO_RAD));
            pose.translate(-centre.x, -centre.y, -centre.z);
            drawPiece(pose, out, light, player, gun, magLocal(-slide, 0), LOOSE_MAG);
            pose.popPose();
        }
        if (!jam && step < 2 && t >= 9) {
            pose.pushPose();
            drawPiece(pose, out, light, player, gun, newMag(t), LOOSE_MAG);
            pose.popPose();
        }
    }

    private static void drawHandle(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun, float pull) {
        pose.pushPose();
        drawPiece(pose, out, light, player, gun, new Matrix4f().translation(0, 0, pull), LOOSE_HANDLE);
        pose.popPose();
    }

    /**
     * The trigger was pulled and the gun went off: the slide runs back, and the flash (unless suppressed or off)
     * shows from the next frame drawn.
     */
    public static void fired(LocalPlayer player, boolean flash) {
        shotTick = player.tickCount;
        shotWaiting = true;
        shotFlash = flash;
        shotEmptied = player.getMainHandItem().getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds() <= 1;
    }

    /**
     * A light muzzle flash for a moment after each shot, starting the frame the shot is heard: full size for half
     * its life, then shrinking away, turned and sized a little differently each time.
     */
    private static void drawFlash(PoseStack pose, SubmitNodeCollector out, LocalPlayer player, Pose gun, ItemStack stack,
                                  float age) {
        float since = age - shotAt;
        if (!shotFlash || since < 0 || since >= FLASH_LIFE || Attachments.suppressed(stack)) {
            return;
        }
        float half = FLASH_LIFE / 2;
        float size = flashSize * (since < half ? 1 : Mth.lerp((since - half) / half, 1f, 0.4f));
        float[] m = rig.muzzle();
        Matrix4f local = new Matrix4f().translation(m[0], m[1], m[2]).rotateZ(flashRoll).scale(size).translate(-m[0], -m[1], -m[2]);
        pose.pushPose();
        drawPiece(pose, out, FULL_BRIGHT, player, gun, local, LOOSE_FLASH);
        pose.popPose();
    }

    private static final int FULL_BRIGHT = 0xF000F0;

    /** Draws a loose piece of the gun (drawn in the gun's own model space), moved by local (item pixels). */
    private static void drawPiece(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                  Matrix4f local, ItemStackRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack[] loose = LOOSE.computeIfAbsent(type, g -> new ItemStack[] {
                g.magazine() == null ? piece(new ItemStack(g.ammo()), g.id() + "_loose_shell")
                        : piece(new ItemStack(g.magazine()), g.id() + "_loose_mag"),
                piece(new ItemStack(g.item()), g.id() + "_loose_charging_handle"),
                piece(new ItemStack(g.item()), g.id() + "_loose_flash")});
        ItemStack stack = state == LOOSE_MAG ? loose[0] : state == LOOSE_HANDLE ? loose[1] : loose[2];
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.FIXED, player.level(), player, 0);
        gun.apply(pose);
        // item pixels to the model's own unit cube, the move, and back (the item render takes off half a block)
        pose.translate(-0.5f, -0.5f, -0.5f);
        Matrix4f unit = new Matrix4f().scaling(1 / 16f).mul(local).scale(16f);
        pose.mulPose(unit);
        pose.translate(0.5f, 0.5f, 0.5f);
        state.submit(pose, out, light, OverlayTexture.NO_OVERLAY, 0);
    }

    private static ItemStack piece(ItemStack stack, String model) {
        stack.set(DataComponents.ITEM_MODEL, ZomboidCraft.id(model));
        return stack;
    }

    /** A magazine moved along the well (negative is out of it) and rocked forward about the well's mouth. */
    private static Matrix4f magLocal(float along, float rock) {
        float[] top = rig.magTop(), axis = rig.magAxis();
        return new Matrix4f().translation(top[0] - axis[0] * along, top[1] - axis[1] * along, top[2] - axis[2] * along)
                .rotateX(rock * Mth.DEG_TO_RAD)
                .translate(-top[0], -top[1], -top[2]);
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

    /** Item pixels the charging handle is back while it's worked: drawn back, then let go so it snaps home. */
    private static float handlePull(float t) {
        if (t < CHARGE) {
            return 0;
        }
        if (t < RELEASE) {
            return rig.handleTravel() * smooth((t - CHARGE) / (RELEASE - CHARGE));
        }
        return rig.handleTravel() * (1 - easeIn(t, RELEASE, HOME));
    }

    /**
     * Item pixels the handle (or slide) is back this frame: worked through a reload or a jam; on a pistol, locked
     * open on an empty gun until the slide is racked, and run back by each shot.
     */
    private static float pull(ItemStack gun, float t, boolean animate, float age) {
        if (rig.pump()) {
            // worked after each shot, and to chamber a shell after loading onto an empty gun
            float shot = pumpStroke(age - shotAt - (type.pumpAfterShot - 0.5f));
            return animate && pumpFrom >= 0 ? Math.max(shot, pumpStroke(age - pumpFrom - PUMP_START)) : shot;
        }
        float travel = rig.handleTravel();
        if (animate && charge) {
            if (jam || !rig.locksBack()) {
                return handlePull(t);
            }
            // racked from where it's locked: back a touch further, then let go
            return t < RELEASE ? travel * (1 + 0.08f * smooth01(t, CHARGE, RELEASE)) : travel * (1 - easeIn(t, RELEASE, HOME));
        }
        if (animate) {
            return 0;
        }
        GunState g = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        float since = age - shotAt;
        boolean fresh = since >= 0 && since < 10;
        if (rig.locksBack() && (g.rounds() == 0 && !g.jammed() || shotEmptied && fresh)) {
            return travel * (fresh && since < 0.35f ? smooth(since / 0.35f) : 1);
        }
        if (rig.cycles() && since >= 0 && since < 1.2f) {
            return travel * (since < 0.35f ? smooth(since / 0.35f) : 1 - smooth((since - 0.35f) / 0.85f));
        }
        return 0;
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
        float[] o = rig.magOut(), c = rig.charge();
        // the magazine slapped home and the handle let go each jolt the muzzle up a little
        float kick = bump(t, MAG_IN, 2.5f) * 3f + (charge ? bump(t, RELEASE, 2.5f) * 2f : 0);
        return worked(hip(o[0] * out + c[0] * chg, o[1] * out + c[1] * chg, kick), chg);
    }

    /**
     * To work the handle the gun comes up and out, muzzle high and to the left, so the hand on the back of it
     * doesn't hide the whole gun: turned about the grip, then pushed away, chg of the way.
     */
    private static Pose worked(Pose gun, float chg) {
        if (chg <= 0) {
            return gun;
        }
        float[] turn = rig.chargeTurn(), push = rig.chargePush();
        Vector3f grip = gun.point(rig.grip());
        Quaternionf q = new Quaternionf().rotationXYZ(turn[0] * chg * Mth.DEG_TO_RAD, turn[1] * chg * Mth.DEG_TO_RAD,
                turn[2] * chg * Mth.DEG_TO_RAD).mul(gun.q());
        Pose turned = new Pose(new Vector3f(gun.t()), q, gun.scale());
        turned.t().add(grip.sub(turned.point(rig.grip()))).add(new Vector3f(push[0], push[1], push[2]).mul(chg));
        return turned;
    }

    /** The hip pose turned by a cant (and a muzzle kick) about the grip, and dropped: the same sums as the model's. */
    private static Pose hip(float cant, float drop, float kick) {
        float[] turn = rig.hipTurn(), at = rig.hipGrip(), g = rig.grip();
        Quaternionf q = new Quaternionf().rotationXYZ((turn[0] + cant * 0.6f + kick) * Mth.DEG_TO_RAD,
                (turn[1] + cant * 0.3f) * Mth.DEG_TO_RAD, (turn[2] - cant) * Mth.DEG_TO_RAD);
        Vector3f grip = q.transform(new Vector3f(g[0] - 8, g[1] - 8, g[2] - 8)).mul(rig.hipScale() / 16f);
        Vector3f t = new Vector3f(at[0] - ARM[0], at[1] - drop - ARM[1], at[2] - ARM[2]).sub(grip);
        return new Pose(t, q, rig.hipScale());
    }

    // ------------------------------------------------------------------ a tube gun's loading and pump

    // Each shell takes perShell ticks after the gun is up (raise): the hand goes down for it (to FETCHED), brings
    // it up under the loading port (UNDER), pushes it up in (UP, IN) and thumbs it forward into the tube, where it
    // is at perShell, the tick the server puts it in.
    private static final float FETCHED = 3.5f, UNDER = 7.5f, UP = 8.5f, IN = 9.2f;
    /** Item pixels a shell goes up from under the port to the tube's line, and forward into the tube; how far the thumb follows. */
    private static final float SHELL_UP = 0.78f, SHELL_FWD = 3.2f, THUMB_FWD = 1.6f;
    /** A tube gun comes down from loading over LOWER ticks; the pump after it is taken up for PUMP_START ticks, then worked. */
    private static final float LOWER = 5, PUMP_START = 3, PUMP_DONE = 9.5f;
    /** The pump's stroke: smartly back, then forward to close. */
    private static final float PUMP_BACK = 1.5f, PUMP_FORWARD = 1.5f;

    /** Ticks into the current shell (0 to perShell), or -1 while the gun is still coming up for the first. */
    private static float shellTime(float age) {
        float s = age - clockStart - type.raise;
        return s < 0 ? -1 : s - Mth.floor(s / type.perShell) * type.perShell;
    }

    /** How far over for loading the gun is (0 to 1): up as the loading starts, down when it stops. */
    private static float loadAmount(float age) {
        float up = smooth01((stopAt < 0 ? age : Math.min(age, stopAt)) - clockStart, 0, type.raise - 1);
        return stopAt < 0 ? up : up * (1 - smooth01(age - stopAt, 0, pumpFrom >= 0 ? PUMP_START : LOWER));
    }

    /** The gun loading: canted so the port shows, jolted as each shell goes home; then over to work the pump. */
    private static Pose tubePose(float age) {
        float load = loadAmount(age);
        float p = pumpFrom < 0 ? -1 : age - pumpFrom;
        float chg = p < 0 ? 0 : smooth01(p, 0, PUMP_START) * (1 - smooth01(p, PUMP_START + 3.5f, PUMP_DONE));
        float u = stopAt < 0 ? shellTime(age) : -1;
        float kick = (u >= 0 ? bump(u, UP, type.perShell - UP) * 1.5f : 0)
                + (p < 0 ? 0 : bump(p, PUMP_START + PUMP_BACK + PUMP_FORWARD, 2.5f) * 1.5f);
        float[] o = rig.magOut(), c = rig.charge();
        return worked(hip(o[0] * load + c[0] * chg, o[1] * load + c[1] * chg, kick), chg);
    }

    /**
     * A shell u ticks into its go: from below the screen to under the port, rocked nose up about its head
     * (magCentre), then up into the tube's line and forward into it, at most fwd pixels.
     */
    private static Matrix4f shellLocal(float u, float fwd) {
        float drop, rock, in = 0, along = 0;
        if (u < UNDER) {
            float v = Mth.clamp((u - FETCHED) / (UNDER - FETCHED), 0, 1);
            float out = 1 - (1 - v) * (1 - v) * (1 - v);
            drop = Mth.lerp(out, 24f, 1.2f);
            rock = Mth.lerp(out, 30f, 12f);
        } else {
            float v = smooth01(u, UNDER, UP);
            drop = 1.2f * (1 - v);
            rock = 12f * (1 - v);
            in = SHELL_UP * smooth01(u, UP, IN);
            along = Math.min(fwd, SHELL_FWD * smooth01(u, UP + 0.3f, type.perShell));
        }
        float[] h = rig.magCentre();
        return new Matrix4f().translation(0, in - drop, -along)
                .translate(h[0], h[1], h[2]).rotateX(rock * Mth.DEG_TO_RAD).translate(-h[0], -h[1], -h[2]);
    }

    /** Item pixels the pump is back, p ticks into a stroke. */
    private static float pumpStroke(float p) {
        if (p <= 0 || p >= PUMP_BACK + PUMP_FORWARD) {
            return 0;
        }
        return rig.handleTravel() * (p < PUMP_BACK ? smooth(p / PUMP_BACK) : 1 - smooth((p - PUMP_BACK) / PUMP_FORWARD));
    }

    /** The pose on the sights for an optic, or null when the gun isn't drawn (the scope). */
    private static Pose sights(String optic) {
        if (optic.equals("scope_4x")) {
            return null;
        }
        float[] d = rig.sights().getOrDefault(optic, rig.sights().get("irons"));
        return new Pose(new Vector3f(d[0] / 16, d[1] / 16, d[2] / 16), new Quaternionf(), d[3]);
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
    /** The off hand wrapped round a pistol's grip: its forearm runs back and down to the left. */
    private static final Vector3f LEFT_WRAP_HIP = new Vector3f(-0.5f, -0.55f, 0.65f);
    private static final Vector3f LEFT_WRAP_ADS = new Vector3f(-0.4f, -0.5f, 0.75f);
    private static final Vector3f PALM_UP = new Vector3f(-0.2f, -1f, 0f);
    private static final Vector3f PALM_IN = new Vector3f(-1f, -0.2f, 0f);

    /**
     * The off hand on the handguard (palm up under it), round the foregrip, or round a pistol's grip; on a pump,
     * riding it pump pixels back.
     */
    private static Hand support(Pose gun, boolean foregrip, float aimed, float pump) {
        if (rig.supportGrip()) {
            return new Hand(gun.point(rig.support()), lerp(LEFT_WRAP_HIP, LEFT_WRAP_ADS, aimed), PALM_IN);
        }
        if (foregrip) {
            return new Hand(gun.point(rig.foregrip()), LEFT_GRIP, PALM_IN);
        }
        float[] s = rig.support();
        return new Hand(gun.point(new Vector3f(s[0], s[1], s[2] + pump)), lerp(LEFT_HIP, LEFT_ADS, aimed), PALM_UP);
    }

    private static Hand onMag(Pose gun, Matrix4f mag) {
        float[] h = rig.magHold();
        Vector3f hold = mag.transformPosition(new Vector3f(h[0], h[1], h[2]));
        return new Hand(gun.point(hold), new Vector3f(-0.1f, -0.9f, 0.42f), new Vector3f(-0.5f, -1f, 0f));
    }

    private static Hand onHandle(Pose gun, float pull) {
        float[] h = rig.handle();
        Vector3f at = gun.point(new Vector3f(h[0], h[1], h[2] + pull));
        // a pistol's slide is pinched from above, the forearm running down and left rather than at the eye (so it
        // doesn't fill the screen); the rifle's handle is pulled from beside the receiver
        return rig.supportGrip() ? new Hand(at, new Vector3f(-0.6f, -0.8f, 0f), new Vector3f(0f, -1f, 0f))
                : new Hand(at, new Vector3f(-0.4f, -0.85f, 0.25f), new Vector3f(-1f, -0.4f, 0f));
    }

    private static Hand pouch() {
        return new Hand(new Vector3f(POUCH), new Vector3f(-0.2f, -0.75f, 0.6f), PALM_UP);
    }

    /** The hand under a shell, its thumb behind the head, u ticks into the shell's go. */
    private static Hand onShell(Pose gun, float u) {
        float[] h = rig.magHold();
        Vector3f hold = shellLocal(u, THUMB_FWD).transformPosition(new Vector3f(h[0], h[1], h[2]));
        return new Hand(gun.point(hold), new Vector3f(-0.1f, -0.9f, 0.42f), new Vector3f(-0.5f, -1f, 0f));
    }

    /** The off hand loading a tube gun: down for each shell and up to push it in. */
    private static Hand loadHand(Pose gun, float age) {
        float u = shellTime(age);
        if (u < 0) {
            return support(gun, false, 0, 0).lerp(pouch(), smooth01(age - clockStart, 0, type.raise));
        }
        if (u >= FETCHED) {
            return onShell(gun, u);
        }
        boolean first = age - clockStart - type.raise < type.perShell;
        return first ? pouch() : onShell(gun, type.perShell).lerp(pouch(), smooth01(u, 0, FETCHED));
    }

    /** The off hand through a tube gun's loading, then back onto the pump (pump pixels back) when it stops. */
    private static Hand tubeHand(Pose gun, float age, float pump) {
        if (stopAt < 0) {
            return loadHand(gun, age);
        }
        float back = pumpFrom >= 0 ? PUMP_START : LOWER - 1;
        return loadHand(gun, stopAt).lerp(support(gun, false, 0, pump), smooth01(age - stopAt, 0, back));
    }

    /** Where the off hand is through the reload. */
    private static Hand leftHand(Pose gun, float t, boolean foregrip) {
        Hand rest = support(gun, foregrip, 0, 0);
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
        // a slide locked open is taken hold of where it sits, all the way back; the hand follows pull()
        boolean locked = rig.locksBack() && !jam;
        float grab = locked ? rig.handleTravel() : 0;
        Hand from = jam ? rest : onMag(gun, seated);
        float reachStart = jam ? JAM_START : 17.5f;
        if (t < CHARGE) {
            return from.lerp(onHandle(gun, grab), smooth01(t, reachStart, CHARGE));
        }
        float back = rig.handleTravel() * (locked ? 1.08f : 1f);
        if (t < RELEASE + 1) {
            // it lets go at full travel, and the hand stays there a moment
            return onHandle(gun, t < RELEASE ? Mth.lerp(smooth01(t, CHARGE, RELEASE), grab, back) : back);
        }
        return onHandle(gun, back).lerp(rest, smooth01(t, RELEASE + 1, 31));
    }

    // ------------------------------------------------------------------ what other code asks

    /** How much of the walking bob and look sway to keep this frame: all of it at the hip, aimSway on the sights. */
    public static float bobScale() {
        float sway = (float) ZcConfig.get().guns.aimSway;
        return 1 - (1 - Mth.clamp(sway, 0, 1)) * aimShown;
    }

    /**
     * How much of the walking bob's sideways and up-down shift to keep: on the sights almost none (aimSway squared),
     * since a sight a few inches from the eye shifted by it points well off what it was on.
     */
    public static float bobShiftScale() {
        float sway = Mth.clamp((float) ZcConfig.get().guns.aimSway, 0, 1);
        return 1 - (1 - sway * sway) * aimShown;
    }

    /** The laser's lens in the hand camera (x right, y up, z back), or null when the gun isn't drawn. */
    public static Vector3f laserInCamera() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType().isFirstPerson() && mc.player != null
                && GunType.isGun(mc.player.getMainHandItem()) ? laserAt : null;
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
