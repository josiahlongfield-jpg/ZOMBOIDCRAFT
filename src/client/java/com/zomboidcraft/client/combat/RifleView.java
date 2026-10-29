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
 * pump to work it after each shot. A bolt gun's shooting hand leaves the stock after each shot to work the bolt (lift,
 * back, home, down) and goes back to it, and does the same after a fresh magazine on an empty gun. A double-barrel is
 * broken open to load, its empties thrown clear, a shell pushed into each chamber, and snapped shut. Each gun's numbers
 * are its {@link GunRig}.
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
    private static final float AIM_TICKS = 3.5f, POINT_TICKS = 4f;
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
    /** A tube gun's loading: when it stopped, and when the pump was taken up after it (ages, or -1); shells in it. */
    private static float stopAt = -1, pumpFrom = -1;
    private static int shells;
    /** A gun that opens to load: the rounds in it when it was opened (the rest of its chambers held empties). */
    private static int shellsAtOpen;
    /**
     * A revolver's cylinder: how far it's turned (degrees, counter-clockwise from behind, always a whole number of
     * chambers at rest), and how far it was when it was swung out to load. It's kept turning on from shot to shot and
     * load to load, so it never jumps back.
     */
    private static float cylAngle, cylOpen;
    /**
     * How far the gun is turned to point its rail at what's under the crosshair (0 to 1, eased): at the hip with its
     * light or laser on, so the beam runs along the barrel to the crosshair.
     */
    private static float point, pointO;
    /**
     * How far off what's under the crosshair is (blocks), from RailLights' look each frame, eased so the gun swings
     * smoothly when something steps under the crosshair; and when it was last eased (nanoseconds).
     */
    private static float aimDistance = 16;
    private static long aimEased;
    /** The hand's lag behind a turning look this frame (degrees about x and y), as the hand renderer turns it. */
    private static float swayX, swayY;
    /** The gun's rail as last drawn (see rail()), and when (nanoseconds). */
    private static Rail rail;
    private static long railDrawn;

    private static final ItemStackRenderState LOOSE_MAG = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_HANDLE = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_FLASH = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_QUIET_FLASH = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_CASE = new ItemStackRenderState();
    private static final ItemStackRenderState LOOSE_SLIDE = new ItemStackRenderState();
    /**
     * Each gun's loose pieces: its magazine (a tube gun's: a shell), its handle, slide or pump, its flash, its smaller
     * flash through a suppressor, a revolver's empty case, and a lever gun's bolt.
     */
    private static final Map<GunType, ItemStack[]> LOOSE = new EnumMap<>(GunType.class);

    /** The last shot: its tick, then its first frame; whether it flashed, and whether it was the last round. */
    private static final float FLASH_LIFE = 1.6f, QUIET_FLASH_LIFE = 1.4f;
    private static int shotTick = -100;
    /** Which chamber the last shot was fired from (a double's right barrel is 0, its left 1). */
    private static int shotChamber;
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
            shotAt = -100;          // a shot from the last gun mustn't work this one's bolt or pump
            point = pointO = 0;
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
        pointO = point;
        boolean lit = Attachments.lightOn(gun) || Attachments.laserOn(gun);
        point = Mth.clamp(point + (lit && step == 0 && !active ? 1f : -1f) / POINT_TICKS, 0f, 1f);
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
                shellsAtOpen = rounds;
                cylOpen = cylAngle;
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
        float done = rig.opens() ? closeAt() + LOWER : pumpFrom >= 0 ? PUMP_DONE : LOWER;
        if (active && stopAt >= 0 && age - stopAt > done + 1) {
            if (rig.cylinder()) {
                cylAngle = (loadAngle(age) % 360 + 360) % 360;     // it stays turned as it was loaded
            }
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
            // a bolt is worked at its own pace (in step with its recording), so its end isn't squeezed to the rifle's
            float end = rig.bolt() ? CHARGE + type.chargeDone - type.charge : END_CHARGE;
            return piecewise(t, new float[] {0, type.magIn, type.charge, type.chargeDone}, new float[] {0, MAG_IN, CHARGE, end});
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
        int magnifier = Attachments.Slot.MAGNIFIER.ordinal();
        if (keys.size() > magnifier && !keys.get(magnifier).equals(Attachments.EMPTY_KEY)) {
            return "magnified";     // looked through like a scope, whatever sits in front of it
        }
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
                if (rig.cylinder()) {
                    cylAngle = ((cylAngle - rig.chamberStep()) % 360 + 360) % 360;    // turned on to the next chamber
                }
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
                rail = null;
                return true;
            }
        } else {
            shown = hip(0, 0, 0);
        }

        // the pose the animation wants (a tube gun's goes by age)
        float t = active ? type.tube ? age - clockStart : anim(age - clockStart) : -1;
        boolean animate = config.guns.reloadAnimation && active;
        float a = smooth(Mth.lerp(partial(age), aimO, aim));
        Pose sights = sights(optic);
        if (sights == null) {
            a = 0;
        }
        Pose want = animate ? rig.opens() ? openPose(age) : rig.pump() || rig.lever() || rig.crossbow() ? tubePose(age) : reloadPose(t)
                : step >= 1 ? shown : hip(0, 0, 0);
        // a bolt worked after a shot: the gun rolled port up and turned in a little while the hand is on it
        float bolt = rig.bolt() ? boltTime(t, animate, age) : -1;
        float work = bolt >= 0 && !animate && step == 0 ? boltWork(bolt) : 0;
        if (work > 0) {
            float[] c = rig.charge();
            want = worked(hip(c[0] * work, c[1] * work, bump(bolt, BOLT_HOME, 2.5f) * 1.2f), work);
        }
        float p = smooth(Mth.lerp(partial(age), pointO, point));
        if (p > 0) {
            // the laser meets what's under the crosshair; the light alone meets the middle of the view a set way
            // ahead, so it doesn't swing as near and far things pass under the crosshair
            boolean laser = Attachments.laserOn(gun);
            want = pointed(want, p, laser ? rig.laser() : rig.light(),
                    laser ? aimDistance : (float) Mth.clamp(config.guns.lightZero, 1.5, 64));
        }
        if (a > 0 && step == 0) {
            want = want.lerp(sights, a);
            if (work > 0) {
                // on the sights it comes off the cheek a little, so the bolt clears the eye
                want = turned(want, BOLT_ADS_TURN, BOLT_ADS_PUSH, work * a);
            }
        }
        aimShown = step == 0 ? a : 0;

        pose.pushPose();
        // hurt arms shake the sights: up to three times the sway with both broken
        var me = net.minecraft.client.Minecraft.getInstance().player;
        float shake = me == null ? 0 : com.zomboidcraft.survival.Injuries.armShake(me);
        Quaternionf breath = breath(age, aimShown, (float) (config.guns.aimSway * (1 + 2 * shake)
                * com.zomboidcraft.combat.rolls.Rolls.multiplier(gun, com.zomboidcraft.combat.rolls.Stat.HANDLING)));
        breathe(pose, breath);
        // the gun: undo the model's pose, apply the wanted one
        pose.pushPose();
        want.apply(pose);
        shown.unapply(pose);
        drawGun.run();
        pose.popPose();

        // the model leaves the handle, slide or pump to us in first person
        float pulled = pull(gun, t, animate, age);
        float lever = rig.lever() ? leverAmount(animate, age) : 0;
        drawHandle(pose, out, light, player, want, bolt >= 0 ? boltLocal(bolt)
                : rig.cylinder() ? openLocal(animate ? openAmount(age) : 0).mul(cylTurn(animate ? loadAngle(age) : restAngle(age)))
                : rig.opens() ? openLocal(animate ? openAmount(age) : 0) : rig.lever() ? openLocal(lever)
                : rig.crossbow() ? stringLocal(cocked(gun, animate, age)) : new Matrix4f().translation(0, 0, pulled));
        if (rig.lever()) {
            // the bolt rides back over the hammer as the lever goes down
            pose.pushPose();
            drawPiece(pose, out, light, player, want, new Matrix4f().translation(0, 0, lever * rig.handleTravel()), LOOSE_SLIDE);
            pose.popPose();
        }
        if (rig.hammer().length == 3) {
            // an exposed hammer: it strikes with each shot (see hammerAmount)
            float[] h = rig.hammer();
            Matrix4f local = new Matrix4f().translation(h[0], h[1], h[2])
                    .rotateX(rig.hammerCock() * hammerAmount(player, gun, animate) * Mth.DEG_TO_RAD).translate(-h[0], -h[1], -h[2]);
            pose.pushPose();
            drawPiece(pose, out, light, player, want, local, LOOSE_SLIDE);
            pose.popPose();
        }
        drawFlash(pose, out, player, want, gun, age);
        Quaternionf turn = new Quaternionf().rotationX(swayX * Mth.DEG_TO_RAD).rotateY(swayY * Mth.DEG_TO_RAD).mul(breath);
        rail = new Rail(eye(want, turn, rig.laser()), eye(want, turn, rig.light()),
                turn.transform(want.q().transform(new Vector3f(0, 0, -1))), Math.max(p, aimShown));
        railDrawn = System.nanoTime();
        if (animate) {
            drawLoose(pose, out, light, player, want, t, step, age);
        }
        if (config.look.firstPersonArms && !state.avatarRenderState.isInvisible) {
            boolean foregrip = optionalKey(gun, 2).equals("foregrip");
            float pump = rig.pump() ? pulled : 0;
            Hand left = !animate ? support(want, foregrip, a, pump) : rig.opens() ? openHand(want, age)
                    : rig.pump() || rig.lever() || rig.crossbow() ? tubeHand(want, age, pump) : leftHand(want, t, foregrip);
            Hand right = rig.wristGrip() ? new Hand(want.point(rig.grip()), lerp(RIGHT_HIP, RIGHT_WRIST_ADS, a), lerp(RIGHT_HINT, RIGHT_WRIST_HINT, a))
                    : new Hand(want.point(rig.grip()), lerp(RIGHT_HIP, RIGHT_ADS, a), RIGHT_HINT);
            if (bolt >= 0) {
                right = boltHand(want, bolt, right);
            }
            if (lever > 0) {
                // the fingers are through the loop: the hand goes down and forward with it
                right = new Hand(want.point(openLocal(lever).transformPosition(new Vector3f(rig.grip()[0], rig.grip()[1],
                        rig.grip()[2]))), right.elbow(), right.palm());
            }
            if (animate && rig.cylinder()) {
                right = right.lerp(new Hand(right.at(), RIGHT_LOAD, RIGHT_HINT), openAmount(age));
            }
            RifleArms.draw(pose, out, light, player, state.avatarRenderState, right,
                    player.getOffhandItem().isEmpty() && !com.zomboidcraft.client.cars.CarHands.leftOnWheel(player) ? left : null,
                    want.scale / rig.hipScale() * 0.6f);
        }
        pose.popPose();
        return true;
    }

    /** The slow drift of a held breath while aiming, so the sights are never quite dead still: a turn about the eye. */
    private static Quaternionf breath(float age, float aimed, float sway) {
        float amount = aimed * sway;
        if (amount <= 0) {
            return new Quaternionf();
        }
        float pitch = Mth.sin(age * 0.071f) * 0.9f * amount, yaw = Mth.sin(age * 0.047f + 1.3f) * 0.6f * amount;
        return new Quaternionf().rotationXYZ(pitch * Mth.DEG_TO_RAD, yaw * Mth.DEG_TO_RAD, 0);
    }

    private static void breathe(PoseStack pose, Quaternionf breath) {
        // turn about the eye: the hand frame sits at ARM from it
        pose.translate(-ARM[0], -ARM[1], -ARM[2]);
        pose.rotate(breath);
        pose.translate(ARM[0], ARM[1], ARM[2]);
    }

    /** An item-pixel point of the gun from the eye (x right, y up, z back), turned by the hand's sway and breath. */
    private static Vector3f eye(Pose gun, Quaternionf turn, float[] px) {
        return turn.transform(gun.point(px).add(ARM[0], ARM[1], ARM[2]));
    }

    /** The old magazine falling away, and the new one coming up and in; or a tube gun's shell. */
    private static void drawLoose(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                  float t, int step, float age) {
        if (rig.opens()) {
            drawChambers(pose, out, light, player, gun, age);
            return;
        }
        if (rig.pump() || rig.lever() || rig.crossbow()) {
            float u = stopAt < 0 ? shellTime(age) : -1;
            if (u >= FETCHED) {
                pose.pushPose();
                drawPiece(pose, out, light, player, gun, roundLocal(u, SHELL_FWD), LOOSE_MAG);
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

    private static void drawHandle(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                   Matrix4f local) {
        pose.pushPose();
        drawPiece(pose, out, light, player, gun, local, LOOSE_HANDLE);
        pose.popPose();
    }

    /**
     * The trigger was pulled and the gun went off: the slide runs back, and the flash (unless suppressed or off)
     * shows from the next frame drawn.
     */
    public static void fired(LocalPlayer player, boolean flash) {
        hammerShot = System.nanoTime();
        hammer = 0;
        shotTick = player.tickCount;
        shotWaiting = true;
        shotFlash = flash;
        int rounds = player.getMainHandItem().getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds();
        shotEmptied = rounds <= 1;
        shotChamber = Math.max(0, type.capacity() - rounds);     // the lowest loaded chamber goes first
    }

    /**
     * A light muzzle flash for a moment after each shot, starting the frame the shot is heard: full size for half
     * its life, then shrinking away, turned and sized a little differently each time. Through a suppressor it's a
     * smaller, slightly shorter one at the end of the can.
     */
    private static void drawFlash(PoseStack pose, SubmitNodeCollector out, LocalPlayer player, Pose gun, ItemStack stack,
                                  float age) {
        float since = age - shotAt;
        boolean quiet = Attachments.suppressed(stack);
        float life = quiet ? QUIET_FLASH_LIFE : FLASH_LIFE;
        if (!shotFlash || since < 0 || since >= life || rig.crossbow()) {      // a crossbow has no flash
            return;
        }
        float half = life / 2;
        float size = flashSize * (since < half ? 1 : Mth.lerp((since - half) / half, 1f, 0.4f));
        float[] m = quiet ? rig.quietMuzzle() : rig.muzzle();
        // a double's flash is from whichever barrel fired
        float barrel = rig.opens() && !rig.cylinder() ? shotChamber * rig.chamberStep() : 0;
        Matrix4f local = new Matrix4f().translation(m[0] + barrel, m[1], m[2]).rotateZ(flashRoll).scale(size).translate(-m[0], -m[1], -m[2]);
        pose.pushPose();
        drawPiece(pose, out, FULL_BRIGHT, player, gun, local, quiet ? LOOSE_QUIET_FLASH : LOOSE_FLASH);
        pose.popPose();
    }

    private static final int FULL_BRIGHT = 0xF000F0;

    /** The hammer: how far back it is (0 down, 1 full cock), and when the last shot let it fall. */
    private static float hammer;
    private static long hammerShot, hammerFrame;
    /** A revolver's double-action strike on each shot: drawn back, then falling, all inside the flash (s). */
    private static final float STRIKE_BACK = 0.03f, STRIKE_FALL = 0.025f;
    /** A pistol's: falls on the shot, then the slide runs back over it and leaves it cocked as it returns (s). */
    private static final float HAMMER_FALL = 0.05f, SLIDE_HAMMER_WAIT = 0.05f, SLIDE_HAMMER_COCKING = 0.03f;

    /**
     * How far back an exposed hammer is this frame. A revolver's rests down; each shot the trigger snaps it back
     * and lets it fall onto the round, done before the flash is. A pistol's sits cocked while it's loaded,
     * slams forward with the shot and is pushed back by the slide.
     */
    private static float hammerAmount(LocalPlayer player, ItemStack gun, boolean reloading) {
        long now = System.nanoTime();
        float dt = hammerFrame == 0 ? 0f : Math.min(0.1f, (now - hammerFrame) / 1e9f);
        hammerFrame = now;
        float since = (now - hammerShot) / 1e9f;
        if (!rig.cycles()) {
            hammer = 0f;
            if (hammerShot == 0 || since >= STRIKE_BACK + STRIKE_FALL) {
                return 0f;
            }
            return since < STRIKE_BACK ? since / STRIKE_BACK : 1f - (since - STRIKE_BACK) / STRIKE_FALL;
        }
        if (hammerShot != 0 && since < HAMMER_FALL) {
            return 1f - since / HAMMER_FALL;
        }
        boolean loaded = gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds() > 0;
        boolean cock = loaded && !reloading && (hammerShot == 0 || since > SLIDE_HAMMER_WAIT);
        hammer = cock ? Math.min(1f, hammer + dt / SLIDE_HAMMER_COCKING) : Math.max(0f, hammer - dt / HAMMER_FALL);
        return hammer;
    }

    /** Draws a loose piece of the gun (drawn in the gun's own model space), moved by local (item pixels). */
    private static void drawPiece(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                  Matrix4f local, ItemStackRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack[] loose = LOOSE.computeIfAbsent(type, g -> new ItemStack[] {
                g.magazine() == null ? piece(new ItemStack(g.ammo()), g.id() + "_loose_shell")
                        : piece(new ItemStack(g.magazine()), g.id() + "_loose_mag"),
                piece(new ItemStack(g.item()), g.id() + "_loose_charging_handle"),
                piece(new ItemStack(g.item()), g.id() + "_loose_flash"),
                piece(new ItemStack(g.item()), g.id() + "_loose_flash_quiet"),
                piece(new ItemStack(g.ammo()), g.id() + "_loose_case"),
                piece(new ItemStack(g.item()), g.id() + "_loose_slide")});
        ItemStack stack = state == LOOSE_MAG ? loose[0] : state == LOOSE_HANDLE ? loose[1] : state == LOOSE_FLASH ? loose[2]
                : state == LOOSE_CASE ? loose[4] : state == LOOSE_SLIDE ? loose[5] : loose[3];
        if (state == LOOSE_HANDLE || state == LOOSE_SLIDE) {
            // the handle wears the gun's finish, which its model picks by the gun's model data
            CustomModelData data = player.getMainHandItem().get(DataComponents.CUSTOM_MODEL_DATA);
            if (data != null) {
                stack.set(DataComponents.CUSTOM_MODEL_DATA, data);
            } else {
                stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            }
        }
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
        if (rig.bolt() || rig.lever()) {
            return 0;       // a bolt turns as well as runs back (see boltLocal), a lever swings (see leverAmount)
        }
        if (rig.pump()) {
            // worked after each shot, and to chamber a shell after loading onto an empty gun
            float shot = pumpStroke(age - shotAt - (type.cycleAfterShot - 0.5f));
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
            if (charge && rig.bolt()) {
                out = in * (1 - smooth01(t, 17, CHARGE));
                chg = boltWork(t - BOLT_RELOAD);
            } else if (charge) {
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
        float kick = bump(t, MAG_IN, 2.5f) * 3f
                + (!charge ? 0 : rig.bolt() ? bump(t - BOLT_RELOAD, BOLT_HOME, 2.5f) * 1.2f : bump(t, RELEASE, 2.5f) * 2f);
        return worked(hip(o[0] * out + c[0] * chg, o[1] * out + c[1] * chg, kick), chg);
    }

    /**
     * To work the handle the gun comes up and out, muzzle high and to the left, so the hand on the back of it
     * doesn't hide the whole gun: turned about the grip, then pushed away, chg of the way.
     */
    private static Pose worked(Pose gun, float chg) {
        return turned(gun, rig.chargeTurn(), rig.chargePush(), chg);
    }

    /** The gun turned about the grip (degrees) and then pushed (blocks), k of the way. */
    private static Pose turned(Pose gun, float[] turn, float[] push, float k) {
        if (k <= 0) {
            return gun;
        }
        Vector3f grip = gun.point(rig.grip());
        Quaternionf q = new Quaternionf().rotationXYZ(turn[0] * k * Mth.DEG_TO_RAD, turn[1] * k * Mth.DEG_TO_RAD,
                turn[2] * k * Mth.DEG_TO_RAD).mul(gun.q());
        Pose turned = new Pose(new Vector3f(gun.t()), q, gun.scale());
        turned.t().add(grip.sub(turned.point(rig.grip()))).add(new Vector3f(push[0], push[1], push[2]).mul(k));
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

    // ------------------------------------------------------------------ a bolt gun's bolt

    // Working the bolt, in ticks: the shooting hand reaches the knob (BOLT_REACH), lifts it (to BOLT_UP), runs it back
    // (BOLT_PULL to BOLT_BACK), pushes it home (BOLT_PUSH to BOLT_HOME), turns it down (BOLT_TURN to BOLT_DOWN) and
    // goes back to the stock (BOLT_DONE). The server's bolt sound starts with the lift (cycleAfterShot after a shot, or
    // at the charge step of a reload), and each move lands on its noise in the recording: the lift's click at 40 ms,
    // the slide back to the stop at 270, forward to the round chambering at 540, the lock-down click at 760.
    private static final float BOLT_REACH = 3, BOLT_UP = 4.2f, BOLT_PULL = 6.4f, BOLT_BACK = 8.4f, BOLT_PUSH = 11.6f,
            BOLT_HOME = 13.9f, BOLT_TURN = 16.8f, BOLT_DOWN = 18.2f, BOLT_DONE = 21f;
    /** When the hand sets off for the bolt in a reload onto an empty gun, in the animation's ticks. */
    private static final float BOLT_RELOAD = CHARGE - BOLT_REACH;
    /** On the sights, the gun comes off the cheek while the bolt is worked: turned (degrees) and pushed (blocks). */
    private static final float[] BOLT_ADS_TURN = {6f, 4f, -10f}, BOLT_ADS_PUSH = {0.04f, -0.16f, -0.32f};
    /** The hand on the knob: its forearm out and back to the right, the palm toward the gun. */
    private static final Vector3f BOLT_ELBOW = new Vector3f(0.75f, -0.45f, 0.5f);
    private static final Vector3f BOLT_PALM = new Vector3f(-1f, -0.3f, 0f);

    /** Ticks into working the bolt, or -1 when it isn't being worked: after a fresh magazine on an empty gun, or a shot. */
    private static float boltTime(float t, boolean animate, float age) {
        float u = animate ? charge ? t - BOLT_RELOAD : -1 : age - shotAt - (type.cycleAfterShot - BOLT_REACH);
        return u >= 0 && u < BOLT_DONE ? u : -1;
    }

    /** How far the gun is turned for the bolt (0 to 1): while the hand is on it. */
    private static float boltWork(float u) {
        return u < 0 ? 0 : smooth01(u, 0, BOLT_REACH) * (1 - smooth01(u, BOLT_DOWN, BOLT_DONE));
    }

    /** The bolt u ticks into its working: turned up about the bore, then run back, home, and turned down. */
    private static Matrix4f boltLocal(float u) {
        float lift = u < BOLT_HOME ? smooth01(u, BOLT_REACH, BOLT_UP) : 1 - smooth01(u, BOLT_TURN, BOLT_DOWN);
        float back = u < BOLT_BACK ? smooth01(u, BOLT_PULL, BOLT_BACK) : 1 - smooth01(u, BOLT_PUSH, BOLT_HOME);
        float[] ax = rig.boltAxis();
        return new Matrix4f().translation(0, 0, back * rig.handleTravel())
                .translate(ax[0], ax[1], ax[2]).rotateZ(lift * rig.boltLift() * Mth.DEG_TO_RAD).translate(-ax[0], -ax[1], -ax[2]);
    }

    /** The shooting hand: off the stock to the knob, with it through the bolt's working, and back. */
    private static Hand boltHand(Pose gun, float u, Hand stock) {
        float[] h = rig.handle();
        Hand knob = new Hand(gun.point(boltLocal(u).transformPosition(new Vector3f(h[0], h[1], h[2]))), BOLT_ELBOW, BOLT_PALM);
        if (u < BOLT_REACH) {
            return stock.lerp(knob, smooth01(u, 0, BOLT_REACH));
        }
        return u < BOLT_DOWN ? knob : knob.lerp(stock, smooth01(u, BOLT_DOWN, BOLT_DONE));
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
    /** A lever's stroke: down, then up to lock (ticks). */
    private static final float LEVER_DOWN = 2.0f, LEVER_UP = 2.2f;
    /**
     * A lever gun's round at the gate: how far its back is swung out (degrees), and how far it's tucked in toward the
     * tube once it's through (item pixels).
     */
    private static final float GATE_YAW = 28f, GATE_TUCK = 0.2f;
    /**
     * A crossbow: how far back the string is when the stretch is done, before its last snap into the latch (the draw
     * keeps time with the cock recording, GunType.COCK_AT); how long the string takes to fly forward with a shot; how
     * flat it lies let go (its depth, of cocked); and how far ahead of the latch (item pixels) a bolt is laid on the
     * rail before it's slid back.
     */
    private static final float STRETCH = 0.9f, STRING_FLY = 0.5f, STRING_SLACK = 0.04f, BOLT_AHEAD = 3.0f;

    /** Ticks into the current shell (0 to perShell), or -1 while the gun is still coming up for the first. */
    private static float shellTime(float age) {
        float s = age - clockStart - type.raise;
        return s < 0 ? -1 : s - Mth.floor(s / type.perShell) * type.perShell;
    }

    /** How far over for loading the gun is (0 to 1): up as the loading starts, down when it stops. */
    private static float loadAmount(float age) {
        // a crossbow is tipped down quickly to be cocked, the rest are brought up over the whole raise
        float up = smooth01((stopAt < 0 ? age : Math.min(age, stopAt)) - clockStart, 0,
                rig.crossbow() ? GunType.COCK_AT : type.raise - 1);
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
        Pose canted = hip(o[0] * load + c[0] * chg, o[1] * load + c[1] * chg, kick);
        return worked(turned(canted, rig.loadTurn(), rig.loadPush(), load), chg);
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

    /**
     * A round u ticks into its go: a pump gun's up through the port under it (shellLocal); a lever gun's in
     * through the gate on its right (gateLocal).
     */
    private static Matrix4f roundLocal(float u, float fwd) {
        return rig.lever() ? gateLocal(u, fwd) : rig.crossbow() ? railLocal(u) : shellLocal(u, fwd);
    }

    /**
     * A crossbow's bolt u ticks into its go: from below the screen up over the rail, nose up (about its nock, magCentre)
     * and BOLT_AHEAD pixels ahead of the latch; laid down on the rail, then slid back until its nock clicks into the
     * latch.
     */
    private static Matrix4f railLocal(float u) {
        float drop, rock, ahead = BOLT_AHEAD;
        if (u < UNDER) {
            float v = Mth.clamp((u - FETCHED) / (UNDER - FETCHED), 0, 1);
            float out = 1 - (1 - v) * (1 - v) * (1 - v);
            drop = Mth.lerp(out, 24f, 1.5f);
            rock = Mth.lerp(out, 25f, 6f);
        } else {
            float v = smooth01(u, UNDER, UP);
            drop = 1.5f * (1 - v);
            rock = 6f * (1 - v);
            ahead = BOLT_AHEAD * (1 - smooth01(u, UP, type.perShell));
        }
        float[] h = rig.magCentre();
        return new Matrix4f().translation(0, -drop, -ahead)
                .translate(h[0], h[1], h[2]).rotateX(rock * Mth.DEG_TO_RAD).translate(-h[0], -h[1], -h[2]);
    }

    /**
     * How far back a crossbow's string is (0 let go, 1 cocked): drawn back to the latch early in the reload, let fly
     * by a shot, cocked while there's a bolt on.
     */
    private static float cocked(ItemStack gun, boolean animate, float age) {
        if (animate) {
            return draw(age - clockStart);
        }
        float since = age - shotAt;
        if (since >= 0 && since < STRING_FLY) {
            return 1 - smooth(since / STRING_FLY);
        }
        return gun.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG).rounds() > 0 ? 1 : 0;
    }

    /**
     * How far back a crossbow's string is s ticks into the reload, in step with the cock recording: held while the
     * hand takes up the slack, stretched steadily with the creak, then snapped home on the seating hit.
     */
    private static float draw(float s) {
        float t = s - GunType.COCK_AT;
        if (t < GunType.COCK_STRETCHED) {
            float v = Mth.clamp((t - GunType.COCK_CREAK) / (GunType.COCK_STRETCHED - GunType.COCK_CREAK), 0, 1);
            return STRETCH * (v + smooth(v)) / 2;
        }
        float v = Mth.clamp((t - GunType.COCK_STRETCHED) / (GunType.COCK_SEAT - GunType.COCK_STRETCHED), 0, 1);
        return STRETCH + (1 - STRETCH) * v * v;
    }

    /** The string k of the way cocked: drawn cocked, so let go it's flattened onto its slack line (the hinge's z). */
    private static Matrix4f stringLocal(float k) {
        float z = rig.hinge()[2];
        return new Matrix4f().translation(0, 0, z).scale(1, 1, Mth.lerp(k, STRING_SLACK, 1f)).translate(0, 0, -z);
    }

    /** The off hand on the string's centre, palm down over it, the string k of the way cocked. */
    private static Hand onString(Pose gun, float k) {
        float[] h = rig.handle();
        Vector3f at = gun.point(stringLocal(k).transformPosition(new Vector3f(h[0], h[1], h[2])));
        return new Hand(at, new Vector3f(-0.25f, -0.75f, 0.55f), new Vector3f(0f, -1f, 0f));
    }

    /** The off hand cocking a crossbow: off the foregrip to the string, then drawing it back to the latch. */
    private static Hand cockHand(Pose gun, float age) {
        float s = age - clockStart;
        if (s < GunType.COCK_AT) {
            return support(gun, false, 0, 0).lerp(onString(gun, 0), smooth01(s, 0, GunType.COCK_AT));
        }
        return onString(gun, draw(s));
    }

    /**
     * A lever gun's round u ticks into its go: from below the screen up beside the gate, its back swung out and nose
     * up (both about the nose, magCentre); then its nose put to the gate, and pushed forward through it, straightening
     * and tucking in toward the tube as it goes (at most fwd pixels).
     */
    private static Matrix4f gateLocal(float u, float fwd) {
        float drop, rock, yaw, along = 0;
        if (u < UNDER) {
            float v = Mth.clamp((u - FETCHED) / (UNDER - FETCHED), 0, 1);
            float out = 1 - (1 - v) * (1 - v) * (1 - v);
            drop = Mth.lerp(out, 24f, 1.2f);
            rock = Mth.lerp(out, 30f, 10f);
            yaw = Mth.lerp(out, 45f, GATE_YAW);
        } else {
            float v = smooth01(u, UNDER, UP);
            drop = 1.2f * (1 - v);
            rock = 10f * (1 - v);
            along = Math.min(fwd, SHELL_FWD * smooth01(u, UP, type.perShell));
            yaw = GATE_YAW * (1 - smooth01(u, UP, IN + 0.6f));
        }
        float tuck = GATE_TUCK * along / SHELL_FWD;
        float[] h = rig.magCentre();
        return new Matrix4f().translation(-tuck, -drop, -along)
                .translate(h[0], h[1], h[2]).rotateY(yaw * Mth.DEG_TO_RAD).rotateX(rock * Mth.DEG_TO_RAD)
                .translate(-h[0], -h[1], -h[2]);
    }

    /**
     * How far down a lever gun's lever is (0 to 1): swung down and back up after each shot, and to chamber a round
     * after loading onto an empty gun.
     */
    private static float leverAmount(boolean animate, float age) {
        float shot = leverStroke(age - shotAt - (type.cycleAfterShot - 0.5f));
        return animate && pumpFrom >= 0 ? Math.max(shot, leverStroke(age - pumpFrom - PUMP_START)) : shot;
    }

    /** How far down the lever is, p ticks into a stroke: smartly down, a beat, and back up to lock. */
    private static float leverStroke(float p) {
        if (p <= 0 || p >= LEVER_DOWN + LEVER_UP) {
            return 0;
        }
        return p < LEVER_DOWN ? smooth(p / LEVER_DOWN) : 1 - smooth((p - LEVER_DOWN) / LEVER_UP);
    }

    /** Item pixels the pump is back, p ticks into a stroke. */
    private static float pumpStroke(float p) {
        if (p <= 0 || p >= PUMP_BACK + PUMP_FORWARD) {
            return 0;
        }
        return rig.handleTravel() * (p < PUMP_BACK ? smooth(p / PUMP_BACK) : 1 - smooth((p - PUMP_BACK) / PUMP_FORWARD));
    }

    /**
     * The gun turned about the grip, p of the way, so the line from a rail lens along the barrel runs through the
     * middle of the view a distance ahead of the eye: near things turn the muzzle in further.
     */
    private static Pose pointed(Pose gun, float p, float[] lens, float distance) {
        Vector3f target = new Vector3f(-ARM[0], -ARM[1], -Math.max(distance, 1.5f) - ARM[2]);
        Vector3f grip = gun.point(rig.grip());
        Vector3f bore = gun.q().transform(new Vector3f(0, 0, -1));
        Pose turned = gun;
        for (int i = 0; i < 2; i++) {        // the lens moves a little as the gun turns: once more from there
            Vector3f want = new Vector3f(target).sub(turned.point(lens));
            Quaternionf fix = new Quaternionf().slerp(new Quaternionf().rotationTo(bore, want), p);
            turned = new Pose(new Vector3f(gun.t()), fix.mul(gun.q()), gun.scale());
            turned.t().add(new Vector3f(grip).sub(turned.point(rig.grip())));
        }
        return turned;
    }

    /** The pose on the sights for an optic, or null when the gun isn't drawn (a scope, or the magnifier). */
    private static Pose sights(String optic) {
        if (optic.equals("magnified") || !optic.equals(Attachments.EMPTY_KEY) && Attachments.overlay(optic)) {
            return null;
        }
        float[] d = rig.sights().getOrDefault(optic, rig.sights().get("irons"));
        // a bead gun is tipped up a little so the bead lands on the eye line
        return new Pose(new Vector3f(d[0] / 16, d[1] / 16, d[2] / 16), new Quaternionf().rotationX(d.length > 4 ? d[4] * Mth.DEG_TO_RAD : 0), d[3]);
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
    /** Holding a stock's wrist on the sights: the forearm comes up from below, the elbow down and out. */
    private static final Vector3f RIGHT_WRIST_ADS = new Vector3f(0.3f, -0.95f, -0.1f);
    private static final Vector3f RIGHT_WRIST_HINT = new Vector3f(-0.7f, 0.7f, 0f);
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
        Vector3f hold = roundLocal(u, THUMB_FWD).transformPosition(new Vector3f(h[0], h[1], h[2]));
        return new Hand(gun.point(hold), new Vector3f(-0.1f, -0.9f, 0.42f), new Vector3f(-0.5f, -1f, 0f));
    }

    /** The off hand loading a tube gun: down for each shell and up to push it in. */
    private static Hand loadHand(Pose gun, float age) {
        float u = shellTime(age);
        if (u < 0) {
            return rig.crossbow() ? cockHand(gun, age)
                    : support(gun, false, 0, 0).lerp(pouch(), smooth01(age - clockStart, 0, type.raise));
        }
        if (u >= FETCHED) {
            return onShell(gun, u);
        }
        boolean first = age - clockStart - type.raise < type.perShell;
        if (first && rig.crossbow()) {
            return onString(gun, 1).lerp(pouch(), smooth01(u, 0, FETCHED));     // off the cocked string for the bolt
        }
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
        if (!charge || rig.bolt()) {
            return onMag(gun, seated).lerp(rest, smooth01(t, 17.5f, 23));     // a bolt is the shooting hand's
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

    // ------------------------------------------------------------------ a gun that opens to load

    // Opening, in ticks from the start: the lever thrown at OPEN_START, the barrels down by OPEN_DONE, the empties
    // thrown back out of the chambers from EJECT_AT; the off hand rides the forend down, then leaves it for the
    // first shell at LET_GO. Each shell then goes like a tube gun's (shellTime), but up behind its chamber and pushed in
    // along the bore. When the loading stops the hand goes back to the forend and swings it shut at closeAt().
    private static final float OPEN_START = 0.8f, OPEN_DONE = 3.2f, EJECT_AT = 3.0f, EJECT_LIFE = 7f, LET_GO = 4.5f;
    /** Item pixels behind its chamber a shell (a revolver's shorter round) lines up before it's pushed home. */
    private static final float BEHIND = 2.6f, CYL_BEHIND = 1.5f;

    /** Ticks after the loading stopped that it's snapped shut: halfway through the server's close. */
    private static float closeAt() {
        return 1 + type.close / 2f;
    }

    /** How far open it is (0 to 1): thrown open as the loading starts, swung shut after it stops. */
    private static float openAmount(float age) {
        float s = age - clockStart;
        float open = smooth01(s, OPEN_START, OPEN_DONE) + bump(s, OPEN_DONE - 0.3f, 2.5f) * 0.08f;
        return stopAt < 0 ? open : open * (1 - smooth01(age - stopAt, closeAt() - 1.4f, closeAt()));
    }

    /** The handle (a double's barrels and forend) turned open k of the way about the hinge. */
    private static Matrix4f openLocal(float k) {
        float[] h = rig.hinge(), ax = rig.openAxis();
        return new Matrix4f().translation(h[0], h[1], h[2])
                .rotate(rig.openAngle() * k * Mth.DEG_TO_RAD, ax[0], ax[1], ax[2]).translate(-h[0], -h[1], -h[2]);
    }

    /** From the first chamber (where the loose round sits home) to chamber j: side by side along x. */
    private static Matrix4f chamberLocal(int j) {
        return new Matrix4f().translation(j * rig.chamberStep(), 0, 0);
    }

    /**
     * A shell u ticks into its go, from home in its chamber: from below the screen up behind the chamber, nose up and
     * rocked about its head, then lined up on the bore and pushed home.
     */
    private static Matrix4f roundPath(float u) {
        float[] p = pathOf(u);
        float[] h = rig.magCentre();
        return new Matrix4f().translation(0, -p[0], p[2])
                .translate(h[0], h[1], h[2]).rotateX(p[1] * Mth.DEG_TO_RAD).translate(-h[0], -h[1], -h[2]);
    }

    /** A shell's way in, u ticks in: how far below its chamber (pixels), how rocked (degrees), how far behind it. */
    private static float[] pathOf(float u) {
        float drop, rock, back;
        float behind = rig.cylinder() ? CYL_BEHIND : BEHIND;
        if (u < UNDER) {
            float v = Mth.clamp((u - FETCHED) / (UNDER - FETCHED), 0, 1);
            float o = 1 - (1 - v) * (1 - v) * (1 - v);
            drop = Mth.lerp(o, 24f, 1.0f);
            rock = Mth.lerp(o, 35f, 10f);
            back = Mth.lerp(o, 6f, behind + 0.6f);
        } else if (u < UP) {
            float v = smooth01(u, UNDER, UP);
            drop = 1.0f * (1 - v);
            rock = 10f * (1 - v);
            back = Mth.lerp(v, behind + 0.6f, behind);
        } else {
            drop = rock = 0;
            back = behind * (1 - smooth01(u, UP + 0.2f, type.perShell - 0.3f));
        }
        return new float[] {drop, rock, back};
    }

    /** The gun opened to load: canted and brought up so the breech shows, jolted as it's snapped shut, then down. */
    private static Pose openPose(float age) {
        float s = age - clockStart;
        float up = smooth01(stopAt < 0 ? s : Math.min(age, stopAt) - clockStart, 0, type.raise - 1);
        float load = stopAt < 0 ? up : up * (1 - smooth01(age - stopAt, closeAt(), closeAt() + LOWER));
        float kick = bump(s, OPEN_START, 2f) * 1.5f + (stopAt < 0 ? 0 : bump(age - stopAt, closeAt(), 2.5f) * 3f);
        float[] o = rig.magOut();
        return turned(hip(o[0] * load, o[1] * load, kick), rig.loadTurn(), rig.loadPush(), load);
    }

    /** The shells in the open chambers, the one going in, and the empties thrown out as it opened. */
    private static void drawChambers(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                     float age) {
        if (rig.cylinder()) {
            drawCylinder(pose, out, light, player, gun, age);
            return;
        }
        float k = openAmount(age);
        if (k <= 0.001f) {
            return;     // shut: they're out of sight in the chambers
        }
        Matrix4f open = openLocal(k);
        int cap = type.capacity();
        float ej = age - clockStart - EJECT_AT;
        if (ej >= 0 && ej < EJECT_LIFE) {
            float slide = 4.5f * (1 - (1 - Math.min(1, ej / 1.4f)) * (1 - Math.min(1, ej / 1.4f)));
            float fall = Math.max(0, ej - 0.8f);
            float[] c = rig.magCentre();
            for (int j = 0; j < cap - shellsAtOpen; j++) {
                Matrix4f local = new Matrix4f(open).mul(chamberLocal(j)).translate(0, 0, slide);
                Vector3f centre = gun.point(local.transformPosition(new Vector3f(c[0], c[1], c[2] - 1f)));
                pose.pushPose();
                // thrown up and back past the shoulder, tumbling
                pose.translate(0.03f * fall * (j == 0 ? 1 : -0.4f), 0.05f * Math.min(1, ej) - (0.035f * fall * fall + 0.02f * fall), 0.04f * fall);
                pose.translate(centre.x, centre.y, centre.z);
                pose.rotate(new Quaternionf().rotationXYZ(-30f * fall * Mth.DEG_TO_RAD, 0, (j == 0 ? 12f : -12f) * fall * Mth.DEG_TO_RAD));
                pose.translate(-centre.x, -centre.y, -centre.z);
                drawPiece(pose, out, light, player, gun, local, LOOSE_MAG);
                pose.popPose();
            }
        }
        for (int j = Math.max(0, cap - shells); j < cap; j++) {
            pose.pushPose();
            drawPiece(pose, out, light, player, gun, new Matrix4f(open).mul(chamberLocal(j)), LOOSE_MAG);
            pose.popPose();
        }
        float u = stopAt < 0 ? shellTime(age) : -1;
        if (u >= FETCHED && shells < cap) {
            pose.pushPose();
            drawPiece(pose, out, light, player, gun, new Matrix4f(open).mul(chamberLocal(cap - 1 - shells)).mul(roundPath(u)), LOOSE_MAG);
            pose.popPose();
        }
    }

    /** The off hand on the forend (riding it open), off for each shell and pushing it home, back to swing it shut. */
    private static Hand openHand(Pose gun, float age) {
        Hand forend = onForend(gun, openAmount(age));
        if (stopAt >= 0) {
            float back = closeAt() - 1.6f;
            Hand shut = openHand(gun, stopAt, forend).lerp(forend, smooth01(age - stopAt, 0, back));
            // a revolver's hand leaves the cylinder for the grip again once it's shut
            return rig.cylinder() ? shut.lerp(support(gun, false, 0, 0), smooth01(age - stopAt, closeAt(), closeAt() + LOWER - 1)) : shut;
        }
        return openHand(gun, age, forend);
    }

    private static Hand openHand(Pose gun, float age, Hand forend) {
        float s = age - clockStart, u = shellTime(age);
        int cap = type.capacity();
        if (u < 0) {
            if (rig.cylinder()) {
                // off the grip onto the cylinder to swing it out, there while the rod's pushed, then off for a round
                Hand on = support(gun, false, 0, 0).lerp(forend, smooth01(s, 0, OPEN_START));
                return on.lerp(pouch(), smooth01(s, CYL_LET_GO, type.raise));
            }
            return forend.lerp(pouch(), smooth01(s, LET_GO, type.raise));
        }
        Hand onShell = onRound(gun, u, rig.cylinder() ? shells : cap - 1 - Math.min(shells, cap - 1));
        if (u >= FETCHED) {
            return onShell;
        }
        boolean first = s - type.raise < type.perShell;
        return first ? pouch() : onRound(gun, type.perShell, rig.cylinder() ? shells - 1 : Math.min(cap - 1, cap - shells))
                .lerp(pouch(), smooth01(u, 0, FETCHED));
    }

    /** The off hand under the forend of a gun open k of the way; a revolver's fingers on its cylinder's side. */
    private static Hand onForend(Pose gun, float k) {
        if (rig.cylinder()) {
            float[] h = rig.handle();
            return new Hand(gun.point(openLocal(k).transformPosition(new Vector3f(h[0], h[1], h[2]))), LEFT_HIP, PALM_IN);
        }
        float[] sp = rig.support();
        return new Hand(gun.point(openLocal(k).transformPosition(new Vector3f(sp[0], sp[1], sp[2]))), LEFT_HIP, PALM_UP);
    }

    /** The hand under a shell's back end, u ticks into its go into chamber j, the arm coming up from below. */
    private static Hand onRound(Pose gun, float u, int j) {
        if (rig.cylinder()) {
            CylRound r = cylRound(gun, openAmount(clockStart + type.raise), u);
            return new Hand(new Vector3f(r.centre()).add(r.dx(), r.dy(), 0).add(CYL_HAND), CYL_ELBOW, CYL_PALM);
        }
        float[] h = rig.magHold();
        Matrix4f m = openLocal(openAmount(clockStart + type.raise)).mul(roundAt(Math.max(0, j))).mul(roundPath(u));
        return new Hand(gun.point(m.transformPosition(new Vector3f(h[0], h[1], h[2]))), new Vector3f(-0.3f, -0.95f, 0f),
                new Vector3f(0.2f, -1f, 0.3f));
    }

    /**
     * Where a round goes into chamber j while it's open: a double's chambers side by side; a revolver's is always
     * the chamber turned to the loading spot.
     */
    private static Matrix4f roundAt(int j) {
        return rig.cylinder() ? cylTurn(cylOpen + LOAD_SPOT) : chamberLocal(j);
    }

    // ------------------------------------------------------------------ a revolver's cylinder

    // After a shot the cylinder turns on a chamber over SPIN ticks. Swung out, the rod is pushed at CYL_EJECT_AT and the
    // empties slide out the back and fall; the hand lets go of the cylinder at CYL_LET_GO. For each round the thumb
    // first turns the next empty chamber round to LOAD_SPOT (degrees from the top chamber's place, counter-clockwise
    // from behind, a whole number of chambers) over TURN ticks while the hand fetches it.
    private static final float SPIN = 1.5f, CYL_EJECT_AT = 4.5f, CYL_LET_GO = 6f, TURN = 2f, LOAD_SPOT = -60f;
    // Each round comes up from the lower left of the screen (FROM_LEFT of the way across for each block it rises) on the
    // off hand's fist, which stays CYL_HAND (blocks, in view) off its back end so the round shows above it, the arm
    // reaching up from below; the shooting forearm drops to RIGHT_LOAD while it's swung out, clear of the cylinder.
    private static final float FROM_LEFT = 0.5f;
    private static final Vector3f CYL_HAND = new Vector3f(-0.02f, -0.075f, -0.01f), CYL_ELBOW = new Vector3f(-0.45f, -0.85f, 0.1f),
            CYL_PALM = new Vector3f(0.2f, -1f, 0.3f), RIGHT_LOAD = new Vector3f(0.45f, -0.85f, 0.25f);

    /** A revolver's round on its way in: the chamber it goes into, where its back end is in view, and its offset. */
    private record CylRound(Matrix4f seat, Vector3f centre, float dx, float dy, float rock) {
    }

    /** The round u ticks into its go, the cylinder open k: lined up behind the loading spot's chamber, brought up. */
    private static CylRound cylRound(Pose gun, float k, float u) {
        float[] p = pathOf(u);
        float[] c = rig.magCentre();
        Matrix4f seat = openLocal(k).mul(roundAt(0)).translate(0, 0, p[2]);
        Vector3f centre = gun.point(seat.transformPosition(new Vector3f(c[0], c[1], c[2])));
        float drop = p[0] / 16 * gun.scale();
        return new CylRound(seat, centre, -drop * FROM_LEFT, -drop, p[1]);
    }

    /** Turned deg degrees about the cylinder's axis (counter-clockwise from behind). */
    private static Matrix4f cylTurn(float deg) {
        float[] c = rig.cylAxis();
        return new Matrix4f().translation(c[0], c[1], c[2]).rotateZ(deg * Mth.DEG_TO_RAD).translate(-c[0], -c[1], -c[2]);
    }

    /** The cylinder shut: as it was left, still turning on to the next chamber for a moment after a shot. */
    private static float restAngle(float age) {
        return cylAngle + rig.chamberStep() * (1 - smooth01(age - shotAt, 0, SPIN));
    }

    /**
     * The cylinder while it's loaded: rounds in it when it opened sit in the chambers from the top round (each next
     * one a chamberStep on); each new round goes in after them, its chamber turned to LOAD_SPOT first.
     */
    private static float loadAngle(float age) {
        float at = spotChamber(stopAt < 0 ? age : stopAt);
        if (stopAt >= 0) {
            at = Mth.lerp(smooth01(age - stopAt, 0, 1.5f), at, Math.round(at));     // settled on a chamber
        }
        return cylOpen + LOAD_SPOT - at * rig.chamberStep();
    }

    /** Which chamber (counted on from the top round's) is at the loading spot at t: turned to each next one in turn. */
    private static float spotChamber(float t) {
        float at = LOAD_SPOT / rig.chamberStep();
        float u = shellTime(t);
        if (u >= 0) {
            boolean first = t - clockStart - type.raise < type.perShell;
            float from = first ? at : shells - 1;
            float to = shells + 6 * Math.round((from - shells) / 6f);       // the short way round
            at = Mth.lerp(smooth01(u, 0, TURN), from, to);
        }
        return at;
    }

    /** Swung out: the empties pushed out the back and falling, the rounds in the chambers, the one going in. */
    private static void drawCylinder(PoseStack pose, SubmitNodeCollector out, int light, LocalPlayer player, Pose gun,
                                     float age) {
        float k = openAmount(age);
        if (k <= 0.001f) {
            return;     // shut: they're out of sight in the chambers
        }
        Matrix4f open = openLocal(k).mul(cylTurn(loadAngle(age)));
        int cap = type.capacity();
        float ej = age - clockStart - CYL_EJECT_AT;
        if (ej < EJECT_LIFE) {
            float e = Math.max(0, Math.min(1, ej));
            float slide = 1.3f * (1 - (1 - e) * (1 - e)), fall = Math.max(0, ej - 0.7f);
            float[] c = rig.magCentre();
            for (int j = shellsAtOpen; j < cap; j++) {
                Matrix4f local = new Matrix4f(open).mul(cylTurn(j * rig.chamberStep())).translate(0, 0, slide);
                pose.pushPose();
                if (fall > 0) {
                    // out of the chambers and down, spreading and tumbling
                    Vector3f centre = gun.point(local.transformPosition(new Vector3f(c[0], c[1], c[2] - 0.5f)));
                    float spread = (j - (cap + shellsAtOpen) / 2f) * 0.012f;
                    pose.translate(spread * fall, -(0.035f * fall * fall + 0.01f * fall), 0.01f * fall);
                    pose.translate(centre.x, centre.y, centre.z);
                    pose.rotate(new Quaternionf().rotationXYZ((20f + j * 7f) * fall * Mth.DEG_TO_RAD, 0,
                            (j % 2 == 0 ? 15f : -15f) * fall * Mth.DEG_TO_RAD));
                    pose.translate(-centre.x, -centre.y, -centre.z);
                }
                drawPiece(pose, out, light, player, gun, local, LOOSE_CASE);
                pose.popPose();
            }
        }
        for (int j = 0; j < Math.min(shells, cap); j++) {
            pose.pushPose();
            drawPiece(pose, out, light, player, gun, new Matrix4f(open).mul(cylTurn(j * rig.chamberStep())), LOOSE_MAG);
            pose.popPose();
        }
        float u = stopAt < 0 ? shellTime(age) : -1;
        if (u >= FETCHED && shells < cap) {
            CylRound r = cylRound(gun, k, u);
            Vector3f c = r.centre();
            pose.pushPose();
            pose.translate(c.x + r.dx(), c.y + r.dy(), c.z);
            pose.rotate(new Quaternionf().rotationX(-r.rock() * Mth.DEG_TO_RAD));
            pose.translate(-c.x, -c.y, -c.z);
            drawPiece(pose, out, light, player, gun, r.seat(), LOOSE_MAG);
            pose.popPose();
        }
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

    /**
     * Where the held gun's rail is, from the eye (x right, y up, z back, as the hands are drawn: at the hand
     * camera's field of view): the laser's and the light's lenses, the way the barrel points, and how far the gun is
     * pointed at what's under the crosshair (1 = it is, on the sights or pointed from the hip; 0 = busy). Null when
     * the gun isn't drawn in first person.
     */
    public static Rail rail() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options.getCameraType().isFirstPerson() && mc.player != null && GunType.isGun(mc.player.getMainHandItem())
                && System.nanoTime() - railDrawn < 200_000_000L ? rail : null;
    }

    public record Rail(Vector3f laser, Vector3f light, Vector3f bore, float onTarget) {
    }

    /** How far off what's under the crosshair is this frame, so a pointed gun's rail lines up on it. */
    public static void aimDistance(float blocks) {
        long now = System.nanoTime();
        float k = 1 - (float) Math.exp(-Math.min(now - aimEased, 200_000_000L) / 1e9 / 0.08);
        aimEased = now;
        // eased in steps of the distance, not blocks: 2 to 20 swings as quickly as 20 to 200 would
        float from = (float) Math.log(Math.max(aimDistance, 0.5f)), to = (float) Math.log(Math.max(blocks, 0.5f));
        aimDistance = (float) Math.exp(from + (to - from) * k);
    }

    /** The hand's lag behind the look this frame (the hand renderer's own sums, after the steadier aim). */
    public static void sway(float x, float y) {
        swayX = x;
        swayY = y;
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
