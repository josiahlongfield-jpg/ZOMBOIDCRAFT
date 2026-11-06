package com.zomboidcraft.client.combat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.combat.LocalParts;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The local guns' sights and grips from the posing tool (Josiah 2026-11-03): drawn on the gun where he placed them
 * (the gun's own iron sights come off while a sight is on), lit reticles glowing in red dots and holos, a 1x sight
 * drawn shortened along its line when the eye is closer than 15 cm (so its picture is the one it gives at 15 cm,
 * as in the tool), and a scope's eyepiece size and own reticle for the zoomed picture (ScopeView).
 */
public final class LocalAttachments {
    /** While the first-person gun is drawn (RifleView): the 1x sight shortening applies only then. */
    static boolean firstPerson;
    private static float shrink = 1;
    /** This frame's scope in the eye: its eyepiece's share of the screen's height, and its reticle (or null). */
    static float lens = -1;
    static @Nullable Identifier reticle;
    private static final float RED_EYE = 0.15f;
    private static final int GLOW = 0xF000F0;
    private static final Map<String, Identifier> RETICLES = new HashMap<>();

    /** The mesh ids a local part is drawn from. */
    static String mesh(String part) {
        return MeshGuns.LOCAL + "parts/" + part;
    }

    /** What the item renderer is handed for a local gun: its id, its fitted sight and grip ("" for none). */
    static String argument(ItemStack stack, String local) {
        String o = LocalParts.fitted(stack, com.zomboidcraft.combat.Attachments.Slot.OPTIC);
        String g = LocalParts.fitted(stack, com.zomboidcraft.combat.Attachments.Slot.UNDERBARREL);
        return local + "|" + (o == null ? "" : o) + "|" + (g == null ? "" : g);
    }

    /** The gun's mesh: its no-irons copy while a sight is on (when this computer has one). */
    static String gunMesh(String local, @Nullable String optic) {
        return optic != null && MeshGuns.hasLocal(local + "_noirons") ? MeshGuns.LOCAL + local + "_noirons" : MeshGuns.LOCAL + local;
    }

    /** The sight and grip on the gun, the pose at the item model's corner. */
    static void drawParts(String local, @Nullable String optic, @Nullable String grip, PoseStack pose, SubmitNodeCollector out, int light) {
        GunPoses.Att att = GunPoses.att(local);
        if (att == null) return;
        for (String part : new String[]{optic, grip}) {
            if (part == null) continue;
            float[] at = part == optic ? mount(att.optics().get(part)) : mount(att.grips().get(part));
            LocalParts.Meta m = LocalParts.get(part);
            if (at == null || m == null) continue;
            pose.pushPose();
            pose.translate(at[0] / 16, at[1] / 16, at[2] / 16);
            pose.scale(att.k(), att.k(), att.k());
            if (part == optic && firstPerson && shrink < 1) {
                // squashed along the sight line about its back face
                pose.translate(0, 0, m.back);
                pose.scale(1, 1, shrink);
                pose.translate(0, 0, -m.back);
            }
            MeshGuns.draw(mesh(part), "body", pose, out, light, null);
            for (String lit : m.lit) {
                MeshGuns.draw(mesh(lit), "body", pose, out, GLOW, null);
            }
            pose.popPose();
        }
    }

    private static float @Nullable [] mount(@Nullable Object p) {
        if (p instanceof GunPoses.OpticPose o) return o.mount();
        if (p instanceof GunPoses.GripPose g) return g.mount();
        return null;
    }

    /** The whole local gun drawn (a posed third-person hold): its mesh, its irons with no sight on, its sight and grip. */
    public static void drawWhole(ItemStack stack, PoseStack pose, SubmitNodeCollector out, int light) {
        String local = stack.get(LocalGuns.ID);
        if (local == null) return;
        String optic = LocalParts.fitted(stack, com.zomboidcraft.combat.Attachments.Slot.OPTIC);
        String grip = LocalParts.fitted(stack, com.zomboidcraft.combat.Attachments.Slot.UNDERBARREL);
        MeshGuns.drawLocal(gunMesh(local, optic), optic == null ? local : null, pose, out, light);
        drawParts(local, optic, grip, pose, out, light);
    }

    /**
     * Each first-person frame, from RifleView: with a sight of his on, how far her eye is from it on the sights pose,
     * which sets the 1x sight's shortening, or the scope's eyepiece size and reticle.
     */
    static void aiming(ItemStack gun, @Nullable GunPoses.Gun posed, @Nullable String optic, @Nullable RifleView.Pose sights, float a) {
        shrink = 1;
        lens = -1;
        reticle = null;
        String local = gun.get(LocalGuns.ID);
        GunPoses.Att att = GunPoses.att(local);
        LocalParts.Meta m = LocalParts.get(optic);
        GunPoses.OpticPose op = att == null || optic == null ? null : att.optics().get(optic);
        if (posed == null || sights == null || m == null || op == null) return;
        float k16 = att.k() * 16;
        Vector3f eye = new Vector3f(-GunRigs.ARM[0], -GunRigs.ARM[1], -GunRigs.ARM[2]);
        float[] mp = op.mount();
        if (m.mag > 1 && m.eyepiece != null) {
            Vector3f c = sights.point(new Vector3f(mp[0], mp[1] + m.line * k16, mp[2] + m.eyepiece[0] * k16));
            float r = m.eyepiece[1] * att.k() * sights.scale(), d = Math.max(0.01f, c.distance(eye));
            lens = (float) (r / (d * Math.tan(Math.toRadians(35))));
            reticle = reticle(optic);
        } else {
            Vector3f back = sights.point(new Vector3f(mp[0], mp[1] + m.line * k16, mp[2] + m.back * k16));
            float d = back.distance(eye);
            if (d < RED_EYE) shrink = 1 + (d / RED_EYE - 1) * Math.max(0, Math.min(1, a));
        }
    }

    /**
     * The fitted scope's own reticle, for the scope view, straight from the fitted sight: the held gun isn't always
     * drawn while looking through it, so aiming() may not have run (Josiah saw the plain duplex in his scope, 2026-11-04).
     */
    static @Nullable Identifier reticleOf(ItemStack gun) {
        String optic = LocalParts.fitted(gun, com.zomboidcraft.combat.Attachments.Slot.OPTIC);
        LocalParts.Meta m = LocalParts.get(optic);
        return m == null || m.mag <= 1 ? null : reticle(optic);
    }

    /** A scope's own reticle picture, read once from its file. */
    private static @Nullable Identifier reticle(String optic) {
        return RETICLES.computeIfAbsent(optic, id -> {
            Path f = LocalParts.DIR.resolve(id + "_reticle.png");
            if (!Files.isRegularFile(f)) return null;
            try (var in = Files.newInputStream(f)) {
                NativeImage img = NativeImage.read(in);
                Identifier name = ZomboidCraft.id("dynamic/reticle/" + id.toLowerCase(java.util.Locale.ROOT));
                Minecraft.getInstance().getTextureManager().register(name, new DynamicTexture(() -> "ZomboidCraft reticle " + id, img));
                return name;
            } catch (java.io.IOException | RuntimeException e) {
                ZomboidCraft.LOGGER.error("Couldn't read reticle {}", f, e);
                return null;
            }
        });
    }

    private LocalAttachments() {
    }
}
