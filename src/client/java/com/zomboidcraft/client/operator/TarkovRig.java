package com.zomboidcraft.client.operator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.combat.TarkovFit;
import com.zomboidcraft.combat.TarkovParts;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * A Tarkov gun as built at the bench (combat/TarkovFit) on its first-person model: where each part sits in a pose,
 * which of the model's own kit meshes still show, and the swapped-in parts drawn from their own models. A part in the
 * kit's own place keeps riding the kit's bone there (so a swapped charging handle, pump or magazine still moves with the
 * clips); anything further out hangs off its parent's slot.
 */
final class TarkovRig {
    private TarkovRig() {
    }

    /** One sight on the gun: the part's path, which of its eye points, the part. */
    record Sight(String path, int aim, TarkovParts.Part part) {
        TarkovParts.Aim eye() {
            return part.aims.get(aim);
        }
    }

    static Matrix4f at(float[] a) {
        return new Matrix4f().translationRotate(a[0], a[1], a[2], new Quaternionf(a[3], a[4], a[5], a[6]).normalize());
    }

    private static Map<String, LocalGuns.KitPart> kit(LocalGuns.Spec spec) {
        Map<String, LocalGuns.KitPart> m = new HashMap<>();
        for (LocalGuns.KitPart k : spec.tarkov.kit) m.put(k.path, k);
        return m;
    }

    /** A bone's own frame in this pose (not its skinning matrix). */
    static Matrix4f bone(float[] pos, float[] turn, int b) {
        return new Matrix4f().translationRotate(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2],
                new Quaternionf(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]));
    }

    /** The frame of the slot holding each part, by path, in the model's space for this pose. */
    static Map<String, Matrix4f> frames(LocalGuns.Spec spec, TarkovFit.Assembly a, NpcSkin skin, float[] pos, float[] turn) {
        Map<String, LocalGuns.KitPart> kit = kit(spec);
        Map<String, Matrix4f> out = new HashMap<>();
        for (TarkovFit.Node n : a.nodes()) {
            Matrix4f f = kitFrame(kit.get(n.path()), skin, pos, turn);
            TarkovFit.Node parent = n.parent() == null ? null : a.at(n.parent());
            boolean onKit = parent == null || parent.kit();
            if (f == null || !onKit) {
                Matrix4f pf = parent == null ? null : out.get(parent.path());
                TarkovParts.Part pp = parent == null ? null : TarkovParts.get(parent.item());
                TarkovParts.Port port = pp == null ? null : pp.slots.get(n.slot());
                f = pf == null || port == null || port.at == null ? f : new Matrix4f(pf).mul(at(port.at));
            }
            if (f != null) out.put(n.path(), f);
        }
        return out;
    }

    /** Where the kit's part at a path has its slot, from its anchor bone; null if the model hasn't that bone. */
    private static @Nullable Matrix4f kitFrame(@Nullable LocalGuns.KitPart k, NpcSkin skin, float[] pos, float[] turn) {
        if (k == null || k.bone == null || k.at == null) return null;
        int b = skin.bone(k.bone);
        return b < 0 ? null : bone(pos, turn, b).mul(at(k.at).invert());
    }

    /** The model's bones whose kit part isn't on the gun as built (swapped out, taken off, or moved). */
    static boolean[] hidden(LocalGuns.Spec spec, TarkovFit.Assembly a, NpcSkin skin) {
        boolean[] hide = new boolean[skin.boneNames.length];
        for (LocalGuns.KitPart k : spec.tarkov.kit) {
            TarkovFit.Node n = a.at(k.path);
            if (n != null && n.kit() || k.bones == null) continue;
            for (String name : k.bones) {
                int b = skin.bone(name);
                if (b >= 0) hide[b] = true;
            }
        }
        return hide;
    }

    /**
     * Lays the kit's flip-up irons flat while a sight is on the gun (Tarkov folds them; Josiah 2026-11-07: the MCX's
     * and SCAR's irons stood behind their holo sights): each fold turns its bone's frame about its hinge in this pose.
     */
    static void fold(LocalGuns.Spec spec, TarkovFit.Assembly a, NpcSkin skin, float[] pos, float[] turn) {
        if (spec.tarkov.folds == null || spec.tarkov.folds.isEmpty() || !TarkovFit.folded(a)) return;
        Quaternionf q = new Quaternionf();
        org.joml.Vector3f t = new org.joml.Vector3f();
        for (LocalGuns.Fold f : spec.tarkov.folds) {
            int b = f.bone == null ? -1 : skin.bone(f.bone);
            if (b < 0 || b * 4 + 3 >= turn.length) continue;
            Matrix4f m = bone(pos, turn, b).mul(f.matrix());
            m.getTranslation(t);
            m.getUnnormalizedRotation(q).normalize();
            pos[b * 3] = t.x;
            pos[b * 3 + 1] = t.y;
            pos[b * 3 + 2] = t.z;
            turn[b * 4] = q.x;
            turn[b * 4 + 1] = q.y;
            turn[b * 4 + 2] = q.z;
            turn[b * 4 + 3] = q.w;
        }
    }

    /** Whether a group of the gun's model shows: arms always, a gun mesh unless its kit part is off. */
    static boolean shows(NpcSkin.Group g, boolean[] hidden) {
        if (hidden == null || !"gun".equals(g.piece) || g.joint == null || g.joint.length == 0) return true;
        int b = g.joint[0];
        return b < 0 || b >= hidden.length || !hidden[b];
    }

    private static final Map<String, Object[][]> BUFFERS = new HashMap<>();
    private static final Map<String, int[]> FLIP = new HashMap<>();

    /**
     * Draws the swapped-in parts (each from its own model, at its slot's frame; {@code pre} puts the model's space where
     * it's drawn, as the gun's bones are). {@code key} keeps each drawer's buffers apart.
     */
    static void draw(TarkovFit.Assembly a, Map<String, Matrix4f> frames, @Nullable Matrix4f pre, String key, int light,
                     int overlay, PoseStack pose, SubmitNodeCollector out) {
        boolean folded = TarkovFit.folded(a);
        for (TarkovFit.Node n : a.nodes()) {
            if (n.kit()) continue;
            TarkovParts.Part p = TarkovParts.get(n.item());
            Matrix4f f = frames.get(n.path());
            if (p == null || p.model == null || f == null) continue;
            NpcSkin part = NpcSkin.get(p.model);
            if (part == null) continue;
            Matrix4f m = pre == null ? new Matrix4f(f) : new Matrix4f(pre).mul(f);
            String bk = key + "|" + n.path() + "|" + p.model;
            Object[][] b = BUFFERS.computeIfAbsent(bk, x -> new Object[2][]);
            int[] flip = FLIP.computeIfAbsent(bk, x -> new int[1]);
            flip[0] = 1 - flip[0];
            if (b[flip[0]] == null || b[flip[0]].length != part.groups.length * 2) b[flip[0]] = new Object[part.groups.length * 2];
            String model = p.model;
            // a flip-up's leaf (its model's second bone) lies folded while a sight is on the gun
            Matrix4f leaf = p.fold != null && folded ? new Matrix4f(m).mul(p.fold.matrix()) : m;
            NpcSkinDraw.submitGroups(part.groups, new Matrix4f[] {m, leaf}, 0, g -> true, g -> part.texture(g.texture, model),
                    b[flip[0]], light, overlay, pose, out);
        }
        if (BUFFERS.size() > 512) {
            BUFFERS.clear();
            FLIP.clear();
        }
    }

    /**
     * The sights on the gun in the order the switch-sight key steps through them: the main optic's eye points, then a
     * second sight (piggyback or offset), then any other sight on it; with no optic, the irons.
     */
    static List<Sight> sights(TarkovFit.Assembly a) {
        List<Sight> out = new ArrayList<>();
        List<String> done = new ArrayList<>();
        for (Attachments.Slot s : new Attachments.Slot[] {Attachments.Slot.OPTIC, Attachments.Slot.PIGGYBACK}) {
            TarkovFit.Node n = a.bySocket().get(s);
            if (n != null) add(out, n);
            if (n != null) done.add(n.path());
        }
        for (TarkovFit.Node n : a.nodes()) {
            TarkovParts.Part p = TarkovParts.get(n.item());
            if (p != null && p.sight() && !done.contains(n.path())) add(out, n);
        }
        if (out.isEmpty()) {
            // on the irons: the rear sight's eye point, else any the gun itself has
            for (TarkovFit.Node n : a.nodes()) {
                TarkovParts.Part p = TarkovParts.get(n.item());
                if (p != null && p.aims != null && !p.aims.isEmpty() && (p.is("IronSight") || p.is("Weapon") || p.is("Receiver"))) {
                    out.add(new Sight(n.path(), 0, p));
                    break;
                }
            }
        }
        return out;
    }

    private static void add(List<Sight> out, TarkovFit.Node n) {
        TarkovParts.Part p = TarkovParts.get(n.item());
        if (p == null || p.aims == null) return;
        for (int i = 0; i < p.aims.size(); i++) out.add(new Sight(n.path(), i, p));
    }

    /** A sight's eye point frame (looking down -z) in the model's space, or null if its part isn't placed. */
    static @Nullable Matrix4f eye(Sight s, Map<String, Matrix4f> frames) {
        Matrix4f f = frames.get(s.path());
        return f == null || s.eye().at == null ? null : new Matrix4f(f).mul(at(s.eye().at));
    }

    /** Where the first-person model's "ads" eye point is now: the kit's main sight's, ridden on its anchor bone. */
    static @Nullable Matrix4f mainEye(LocalGuns.Spec spec, NpcSkin skin, float[] pos, float[] turn) {
        if (spec.tarkov.aimPath == null) return null;
        LocalGuns.KitPart k = kit(spec).get(spec.tarkov.aimPath);
        Matrix4f f = kitFrame(k, skin, pos, turn);
        TarkovParts.Part p = k == null ? null : TarkovParts.get(k.item);
        if (f == null || p == null || p.aims == null || spec.tarkov.aimIndex >= p.aims.size()) return null;
        return f.mul(at(p.aims.get(spec.tarkov.aimIndex).at));
    }

    /** The left hand's hold as built: the fitted foregrip wrapped, else the handguard (or stock, or body) cradled. */
    record Grip(String path, LocalGuns.Hold hold, String gate) {
    }

    static @Nullable Grip grip(LocalGuns.Spec spec, TarkovFit.Assembly a) {
        TarkovFit.Node n = a.bySocket().get(Attachments.Slot.UNDERBARREL);
        TarkovParts.Part p = n == null ? null : TarkovParts.get(n.item());
        LocalGuns.Hold h = p == null ? null : p.hold != null ? p.hold : p.cradle;
        if (h == null) {
            n = null;
            for (String kind : new String[] {"Handguard", "Stock", "Weapon"}) {
                for (TarkovFit.Node m : a.nodes()) {
                    TarkovParts.Part mp = TarkovParts.get(m.item());
                    if (mp == null || !mp.is(kind)) continue;
                    LocalGuns.Hold mh = mp.cradle != null ? mp.cradle : mp.hold;
                    if (mh != null) {
                        n = m;
                        h = mh;
                        break;
                    }
                }
                if (h != null) break;
            }
        }
        if (n == null || h == null || h.palm == null || h.palm.length < 7) return null;
        // judged against the nearest part out from the body that the kit had in its place: it moves with the clips
        // the way the hand does (the 870's pump), wherever on it the new grip sits
        Map<String, LocalGuns.KitPart> kit = kit(spec);
        String gate = n.path();
        while (true) {
            TarkovFit.Node g = a.at(gate);
            TarkovFit.Node parent = g == null || g.parent() == null ? null : a.at(g.parent());
            if (kit.containsKey(gate) && (parent == null || parent.kit())) break;
            if (g == null || g.parent() == null) {
                gate = "";
                break;
            }
            gate = g.parent();
        }
        return new Grip(n.path(), h, gate);
    }
}
