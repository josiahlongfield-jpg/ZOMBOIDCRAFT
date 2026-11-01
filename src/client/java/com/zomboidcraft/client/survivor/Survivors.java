package com.zomboidcraft.client.survivor;

import com.zomboidcraft.client.cars.CarHands;
import com.zomboidcraft.client.combat.HoldPoses;
import com.zomboidcraft.clothing.Grooming;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.RifleItem;
import com.zomboidcraft.combat.Weapons;
import com.zomboidcraft.combat.WeaponSpecs;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.character.Appearance;
import com.zomboidcraft.world.cars.CarProp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Players drawn as Zomboid survivors (the look from {@link Appearance}, the packs from {@link SurvivorPack}): Zomboid's
 * moves picked from what each player is doing, the head turned where they look, and the hands put on whatever they
 * hold. Minecraft's own player model is still posed (other parts of the game read it) but hidden; this takes its place.
 * <p>
 * Held things stay Minecraft's: with a gun or a two-handed weapon up, the arm poses that fit the weapon models
 * (combat/HoldPoses) say where the weapon is and her hands reach for it; with anything else the vanilla arm is moved
 * so the item sits in her hand. A PC without the packs, or a player who hasn't made a look, keeps the usual model.
 */
public final class Survivors {
    /** The look the appearance page is trying on the local player (null when it's shut). */
    public record Preview(Appearance.Look look, String hair, String beard, String colour) {
    }

    public static Preview preview;

    /** Vanilla's player model: 0.9375 of a block to the model's 1, its origin 1.501 blocks over the feet. */
    private static final float K = 1f / 0.9375f, ORIGIN = 1.501f;
    /** A seated player's hips sit this high over their feet (vanilla's hip pivot, in metres). */
    private static final float SEAT = 0.70f;
    private static final float MAX_YAW = 75, MAX_PITCH = 60;

    /** Per player: the animator and what it last made. */
    static final class Entry {
        final SurvivorAnimator anim = new SurvivorAnimator();
        SurvivorPack pack;
        float[] pos, turn;
        float age = Float.NaN, swingWas;
        long seen;
        final Object[][] buffers = new Object[2][];
        int buffer;
        float submitAge = Float.NaN;
        float air, still, fidgetAt = 12;
        boolean[] upper;
    }

    /** What the layer draws for the render state just posed. */
    static final class Drawn {
        AvatarRenderState state;
        Entry entry;
        SurvivorPack pack;
        Matrix4f[] skin;
        Identifier body, hairTex, beardTex;
        String hairPiece, beardPiece;
        /** The garments' meshes drawn, by piece, with their textures. */
        Map<String, Identifier> clothes = Map.of();
        float deathTurn;
    }

    private static final Map<Integer, Entry> ENTRIES = new HashMap<>();
    private static Drawn last;
    /** The vanilla models whose parts are hidden (and arms moved) for a survivor just drawn. */
    private static final java.util.Set<PlayerModel> HIDDEN = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private Survivors() {
    }

    /** Whether this render state is a Zomboid survivor, drawn by SurvivorLayer. */
    public static boolean drawn(AvatarRenderState state) {
        return last != null && last.state == state;
    }

    static Drawn take(AvatarRenderState state) {
        return drawn(state) ? last : null;
    }

    /** What a survivor wears this frame: her pack, her composed skin, and her clothes' meshes with their textures. */
    record Outfit(SurvivorPack pack, Identifier body, Map<String, Identifier> clothes) {
    }

    /** This player's survivor skin and clothes, or null (no look, no pack, or the skin still being made). */
    static Outfit outfit(Player player) {
        Minecraft mc = Minecraft.getInstance();
        Appearance.Look look = lookOf(player);
        if (look == null) return null;
        SurvivorPack pack = SurvivorPack.get(look.sex());
        if (pack == null) return null;
        String hair, beard, colour;
        if (preview != null && player == mc.player) {
            hair = preview.hair();
            beard = preview.beard();
            colour = preview.colour();
        } else {
            Grooming.Look g = Grooming.look(player);
            long now = player.level().getGameTime();
            hair = Grooming.hair(g, now);
            beard = Grooming.beard(g, now);
            colour = g.colour();
        }
        SurvivorClothes.Dress dress = SurvivorClothes.dress(pack, player);
        List<SurvivorTextures.Layer> layers = layers(pack, look, hair, beard, colour);
        layers.addAll(dress.body());
        Identifier body = SurvivorTextures.body(pack, layers);
        if (body == null) return null;
        Map<String, Identifier> clothes = new HashMap<>();
        for (SurvivorClothes.Piece c : dress.pieces()) clothes.put(c.piece(), c.texture());
        return new Outfit(pack, body, clothes);
    }

    /** Whether the player this render state is for is drawn as a survivor. */
    public static boolean drawsAsSurvivor(AvatarRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getEntity(state.id) instanceof Player player && !player.isSpectator() && active(player);
    }

    /** Whether this player is drawn as a survivor (they have a look, and this PC has its pack). */
    public static boolean active(Player player) {
        Appearance.Look look = lookOf(player);
        return look != null && SurvivorPack.get(look.sex()) != null;
    }

    static Appearance.Look lookOf(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (preview != null && player == mc.player) return preview.look();
        return Appearance.of(player);
    }

    // ------------------------------------------------------------------ posing (PlayerModelMixin)

    /**
     * Before the weapon holds: her pose for this frame, and the vanilla shoulders moved onto hers, so the arm angles
     * worked out there carry the weapon from her shoulders. Returns whether she's drawn.
     */
    public static boolean before(PlayerModel model, AvatarRenderState state) {
        if (last != null && last.state == state) last = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(state.id) instanceof Player player) || player.isSpectator()) return false;
        Appearance.Look look = lookOf(player);
        if (look == null) return false;
        SurvivorPack pack = SurvivorPack.get(look.sex());
        if (pack == null) return false;

        String hair, beard, colour, hairCut, beardCut;
        if (preview != null && player == mc.player) {
            hair = hairCut = preview.hair();
            beard = beardCut = preview.beard();
            colour = preview.colour();
        } else {
            Grooming.Look g = Grooming.look(player);
            long now = player.level().getGameTime();
            hair = Grooming.hair(g, now);
            beard = Grooming.beard(g, now);
            colour = g.colour();
            hairCut = g.hair();
            beardCut = g.beard();
        }
        SurvivorClothes.Dress dress = SurvivorClothes.dress(pack, player);
        List<SurvivorTextures.Layer> layers = layers(pack, look, hair, beard, colour);
        layers.addAll(dress.body());
        Identifier body = SurvivorTextures.body(pack, layers);
        if (body == null) {
            // a moment while her skin is made: nothing drawn meanwhile, never the Minecraft model
            HIDDEN.add(model);
            hide(model, true);
            return false;
        }
        SurvivorPack.Style hs = pack.hairFor(hair, hairCut), bs = look.female() ? null : pack.beardFor(beard, beardCut);
        // a hat, helmet or hood replaces the hair outright, so none comes through it; a full mask hides the beard too
        if (dress.hairHidden()) hs = null;
        if (dress.hat() != null && dress.hat().endsWith("nobeard")) bs = null;

        Entry e = ENTRIES.computeIfAbsent(state.id, id -> new Entry());
        e.seen = System.currentTimeMillis();
        if (e.pack != pack) {
            e.pack = pack;
            e.pos = null;
            e.buffers[0] = e.buffers[1] = null;
            e.upper = upperBody(pack);
        }
        if (e.pos == null || state.ageInTicks != e.age) {
            float dt = Float.isNaN(e.age) ? 0 : Math.max(0, Math.min(0.25f, (state.ageInTicks - e.age) / 20f));
            e.age = state.ageInTicks;
            choose(e, pack, player, state, dt);
            e.anim.advance(pack, dt);
            int n = pack.skin.boneNames.length;
            if (e.pos == null) {
                e.pos = new float[n * 3];
                e.turn = new float[n * 4];
            }
            e.anim.pose(pack, e.upper, e.pos, e.turn);
            seat(e, pack, player);
        }
        if (ENTRIES.size() > 64) ENTRIES.values().removeIf(x -> System.currentTimeMillis() - x.seen > 10_000);

        Drawn d = new Drawn();
        d.state = state;
        d.entry = e;
        d.pack = pack;
        d.body = body;
        d.hairPiece = hs == null ? null : hs.piece();
        d.beardPiece = bs == null ? null : bs.piece();
        d.hairTex = hs == null ? null : texOf(pack, hs.piece(), colour);
        d.beardTex = bs == null ? null : texOf(pack, bs.piece(), colour);
        d.clothes = new HashMap<>();
        for (SurvivorClothes.Piece c : dress.pieces()) d.clothes.put(c.piece(), c.texture());
        d.deathTurn = state.deathTime > 0 ? Math.min(1, (float) Math.sqrt((state.deathTime - 1) / 20f * 1.6f)) * 90f : 0;
        last = d;

        // this frame's working pose: the clip's, with the head turned and (below) the hands put on what they hold
        float[] pos = e.pos.clone(), turn = e.turn.clone();
        look(pack, pos, turn, state);
        work = new float[][]{pos, turn};
        // the vanilla shoulders onto hers
        for (int s = 0; s < 2; s++) {
            int b = pack.upper[s];
            if (b < 0) continue;
            ModelPart arm = s == 0 ? model.rightArm : model.leftArm;
            arm.x = pos[b * 3] * K * 16;
            arm.y = (ORIGIN - pos[b * 3 + 1] * K) * 16;
            arm.z = -pos[b * 3 + 2] * K * 16;
        }
        HIDDEN.add(model);
        return true;
    }

    private static float[][] work;

    /**
     * After the weapon holds and car hands: her hands go where they put the vanilla hands (a weapon, a wheel), or else
     * the vanilla arms go where her hands are, so held items show in them. Then the vanilla model is hidden.
     */
    public static void after(PlayerModel model, AvatarRenderState state) {
        Drawn d = last;
        if (d == null || d.state != state || work == null) return;
        SurvivorPack pack = d.pack;
        float[] pos = work[0], turn = work[1];
        boolean slim = state.skin != null && state.skin.model() == PlayerModelType.SLIM;
        boolean leftMain = state.mainArm == HumanoidArm.LEFT;
        boolean[] reached = new boolean[2];
        Minecraft mc = Minecraft.getInstance();
        boolean longGun = HoldPoses.posed && mc.level != null && mc.level.getEntity(state.id) instanceof Player holder
                && kind(holder.getMainHandItem()).equals("rifle");
        if (longGun) {
            // Zomboid's rifle stance leads: the gun is laid between her hands as the clip holds them (grip in the
            // shooting hand, barrel toward the other), the vanilla arm is put under it, and the other hand takes the
            // foregrip where the gun really has it
            int gun = leftMain ? 1 : 0;
            ModelPart arm = gun == 0 ? model.rightArm : model.leftArm;
            Matrix4f was = itemFrame(arm, gun == 1, slim);
            Matrix4f back = new Matrix4f(was).invert();
            Vector3f support = back.transformPosition(new Vector3f(HoldPoses.supportAt).div(16));
            Vector3f haveDir = new Vector3f(support).normalize();
            Vector3f haveUp = back.transformDirection(new Vector3f(0, -1, 0)).normalize();   // model y runs down
            Vector3f grip = gripAt(pack, pos, turn, gun), other = gripAt(pack, pos, turn, 1 - gun);
            Vector3f g = toModel(grip.x, grip.y, grip.z), o = toModel(other.x, other.y, other.z);
            Vector3f wantDir = new Vector3f(o).sub(g);
            if (support.lengthSquared() > 1e-6f && wantDir.lengthSquared() > 1e-6f) {
                wantDir.normalize();
                Quaternionf r = new Quaternionf().rotationTo(haveDir, wantDir);
                // then turned about the barrel so the sights stay as near up as they can
                Vector3f up = r.transform(new Vector3f(haveUp));
                Vector3f wantUp = new Vector3f(0, -1, 0).sub(new Vector3f(wantDir).mul(-wantDir.y));
                Vector3f upFlat = new Vector3f(up).sub(new Vector3f(wantDir).mul(up.dot(wantDir)));
                if (wantUp.lengthSquared() > 1e-6f && upFlat.lengthSquared() > 1e-6f) {
                    wantUp.normalize();
                    upFlat.normalize();
                    float ang = (float) Math.atan2(new Vector3f(upFlat).cross(wantUp).dot(wantDir), upFlat.dot(wantUp));
                    r.premul(new Quaternionf().rotationAxis(ang, wantDir));
                }
                Matrix4f item = new Matrix4f().translation(g).rotate(r);
                Matrix4f armFrame = new Matrix4f(item).mul(inHand(gun == 1).invert());
                float shift = slim ? (gun == 1 ? -0.5f : 0.5f) : 0;
                arm.x = armFrame.m30() * 16 - shift;
                arm.y = armFrame.m31() * 16;
                arm.z = armFrame.m32() * 16;
                Vector3f e = new Matrix3f(armFrame).getEulerAnglesZYX(new Vector3f());
                arm.setRotation(e.x, e.y, e.z);
                Vector3f sp = item.transformPosition(new Vector3f(support));
                reachPoint(pack, pos, turn, 1 - gun, fromModel(sp.x, sp.y, sp.z));
                reached[0] = reached[1] = true;
            }
        } else if (CarHands.posed > 0) {
            for (int s = CarHands.posed == 2 ? 0 : 1; s < 2; s++) {
                ModelPart arm = s == 0 ? model.rightArm : model.leftArm;
                Matrix4f item = itemFrame(arm, s == 1, slim);
                reachPoint(pack, pos, turn, s, fromModel(item.m30(), item.m31(), item.m32()));
                reached[s] = true;
            }
        }
        // every hand not reaching for something carries the vanilla arm with it, so an item in it shows there
        for (int s = 0; s < 2; s++) {
            if (reached[s]) continue;
            armToHand(pack, pos, turn, s, s == 0 ? model.rightArm : model.leftArm, slim);
        }
        d.skin = skinOf(pack, pos, turn);
        work = null;
        hide(model, true);
    }

    /**
     * Minecraft's own parts hidden while a survivor is drawn over them; shown again (and the arms put back) before the
     * model is posed for the next player drawn with it.
     */
    public static void hide(PlayerModel model, boolean hidden) {
        if (!hidden) {
            if (!HIDDEN.remove(model)) return;
            model.rightArm.resetPose();
            model.leftArm.resetPose();
        }
        model.head.visible = model.body.visible = model.leftArm.visible = model.rightArm.visible = !hidden;
        model.leftLeg.visible = model.rightLeg.visible = !hidden;
    }

    /** The texture for a hair or beard piece: its group's texture tinted. */
    private static Identifier texOf(SurvivorPack pack, String piece, String colour) {
        for (com.zomboidcraft.client.npc.NpcSkin.Group g : pack.skin.groups) {
            if (g.piece.equals(piece)) return SurvivorTextures.hair(pack, g.texture, colour);
        }
        return null;
    }

    /** Skin, face, body hair, stubble (when it's showing), face paint, eyes, lips, and the underwear last. */
    static List<SurvivorTextures.Layer> layers(SurvivorPack pack, Appearance.Look look, String hair, String beard, String colour) {
        List<SurvivorTextures.Layer> out = new ArrayList<>();
        SurvivorPack.Pack p = pack.pack(look.pack());
        out.add(new SurvivorTextures.Layer(pack.skinKey(p, look.tone(), look.build(), look.hairy()), -1));
        String face = pack.faceKey(p, look.face(), look.tone());
        if (face == null && p != null && pack.faces.get(p.faces()) != null && !pack.faces.get(p.faces()).names().isEmpty()) {
            face = pack.faceKey(p, pack.faces.get(p.faces()).names().get(0), look.tone());
        }
        if (face != null) out.add(new SurvivorTextures.Layer(face, -1));
        int tint = SurvivorTextures.rgb(colour);
        if (!look.bodyHair().isEmpty() && pack.skin.raw("bodyhair/" + look.bodyHair()) != null) {
            out.add(new SurvivorTextures.Layer("bodyhair/" + look.bodyHair(), tint));
        }
        if (!look.female() && SurvivorPack.length(false, beard) == 1) {
            String s = stubble(pack, look.beardStubble(), "Beard");
            if (s != null) out.add(new SurvivorTextures.Layer("stubble/" + s, tint));
        }
        if (SurvivorPack.length(true, hair) == 1) {
            String s = stubble(pack, look.headStubble(), "Hair");
            if (s != null) out.add(new SurvivorTextures.Layer("stubble/" + s, tint));
        }
        for (String m : new String[]{look.paint(), look.eyes(), look.lips()}) {
            if (!m.isEmpty() && pack.skin.raw("makeup/" + m) != null) out.add(new SurvivorTextures.Layer("makeup/" + m, -1));
        }
        if (pack.skin.raw("uw") != null) out.add(new SurvivorTextures.Layer("uw", -1));
        return out;
    }

    /** The stubble chosen (if it's the right kind and there), else the pack's first of that kind. */
    static String stubble(SurvivorPack pack, String chosen, String kind) {
        String first = null;
        for (SurvivorPack.Option o : pack.stubble) {
            if (!o.id().contains(kind)) continue;
            if (o.id().equals(chosen)) return chosen;
            if (first == null) first = o.id();
        }
        return first;
    }

    // ------------------------------------------------------------------ which moves

    private static void choose(Entry e, SurvivorPack pack, Player player, AvatarRenderState state, float dt) {
        SurvivorAnimator a = e.anim;
        if (state.deathTime > 0 || player.isDeadOrDying()) {
            a.base(pack, "idle", 1);
            a.act(pack, "die", 1, false, true);
            return;
        }
        if ("die".equals(a.action())) a.stopAction(pack);
        if (state.hasPose(Pose.SLEEPING) || state.isVisuallySwimming || state.isFallFlying) {
            a.base(pack, "idle", 1);   // Minecraft lays them down or flat itself
            return;
        }
        if (player.isPassenger()) {
            boolean drives = player.getVehicle() instanceof CarProp car && car.getControllingPassenger() == player;
            boolean gunner = com.zomboidcraft.world.cars.Turret.isGunner(player);
            a.base(pack, gunner ? "rifle_aim" : drives ? "drive" : "passenger", 1);
            return;
        }
        double dx = player.getX() - player.xo, dy = player.getY() - player.yo, dz = player.getZ() - player.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz) * 20f;
        if (player.onClimbable() && !player.onGround()) {
            a.base(pack, "climb", Math.min(1.5f, (float) Math.abs(dy) * 20f / 1.2f));
            return;
        }
        e.air = player.onGround() ? 0 : e.air + dt;
        if (e.air > 0.35f && dy < 0) {
            a.base(pack, "fall", 1);
            return;
        }
        if (player.onGround() && "fall".equals(a.base()) && dt > 0) a.act(pack, "land", 1.4f, false, false);

        ItemStack main = player.getMainHandItem();
        String kind = kind(main);
        boolean aiming = false;
        if (main.getItem() instanceof RifleItem) {
            aiming = state.isUsingItem && state.useItemHand == InteractionHand.MAIN_HAND || HoldPoses.raised(player);
        }
        float yaw = (float) Math.toRadians(player.yBodyRot);
        float ahead = (float) (-dx * Math.sin(yaw) + dz * Math.cos(yaw));
        boolean back = speed > 0.3f && ahead < 0 && Math.abs(ahead) * 20f > speed * 0.5f;
        boolean crouch = state.isCrouching;
        boolean sprint = player.isSprinting();

        // a hurt leg limps, the worse side; a bare break limps hard
        int legL = 0, legR = 0;
        if (player == Minecraft.getInstance().player) {
            Body b = Body.get(player);
            for (Body.Region r : new Body.Region[]{Body.Region.LEFT_THIGH, Body.Region.LEFT_SHIN, Body.Region.LEFT_FOOT}) legL = Math.max(legL, b.part(r).severity());
            for (Body.Region r : new Body.Region[]{Body.Region.RIGHT_THIGH, Body.Region.RIGHT_SHIN, Body.Region.RIGHT_FOOT}) legR = Math.max(legR, b.part(r).severity());
        }
        int limp = Math.max(legL, legR);
        String side = legL >= legR ? "l" : "r";

        String clip;
        if (speed < 0.25f) {
            clip = crouch ? "sneak" : switch (kind) {
                case "rifle", "pistol" -> kind + (aiming ? "_aim" : "_idle");
                case "bat", "heavy" -> kind + "_idle";
                case "spear" -> "spear_aim";
                case "one", "knife" -> "one_idle";
                default -> limp >= 2 ? "idle_hurt" : "idle";
            };
        } else if (crouch) {
            clip = speed > 2.2f ? "sneak_run" : "sneak_walk";
        } else if (aiming) {
            clip = kind + (back ? "_aim_back" : "_aim_walk");
        } else if (back) {
            clip = "walk_back";
        } else if (sprint || speed > 4.6f) {
            clip = limp >= 2 ? "run_limp" : kind.equals("rifle") || kind.equals("pistol") ? kind + "_sprint" : "sprint";
        } else if (speed > 2.4f) {
            clip = limp >= 2 ? "run_limp" : switch (kind) {
                case "rifle", "pistol", "bat" -> kind + "_run";
                default -> "run";
            };
        } else {
            clip = limp >= 3 ? "limp_heavy_" + side : limp >= 2 ? "limp_" + side : switch (kind) {
                case "rifle", "pistol", "bat", "heavy" -> kind + "_walk";
                case "one", "knife" -> "one_walk";
                default -> "walk";
            };
        }
        float rate = 1;
        float v = a.natural(pack, clip);
        if (v > 0.1f) rate = Math.max(0.5f, Math.min(2f, speed / v));
        a.base(pack, clip, rate);

        // once-off moves over it
        float swing = state.swingAnimation;
        if (swing > 0 && e.swingWas == 0) {
            String hit = switch (kind) {
                case "knife" -> "knife_stab";
                case "one" -> "one_swing";
                case "bat" -> "bat_swing";
                case "heavy" -> "heavy_swing";
                case "spear" -> "spear_stab";
                case "rifle", "pistol" -> "shove";
                default -> main.isEmpty() ? "punch" : "one_swing";
            };
            a.act(pack, hit, hit.equals("heavy_swing") ? 2.2f : 1.5f, true, false);
        }
        e.swingWas = swing;
        net.minecraft.world.item.component.Consumable food = player.isUsingItem()
                ? player.getUseItem().get(net.minecraft.core.component.DataComponents.CONSUMABLE) : null;
        if (food != null) {
            a.act(pack, food.animation() == net.minecraft.world.item.ItemUseAnimation.DRINK ? "drink" : "eat", 1.2f, true, false);
        } else if ("eat".equals(a.action()) || "drink".equals(a.action())) {
            a.stopAction(pack);
        }
        // Zomboid's fidgets: stood still with empty hands a while, she shifts her weight or looks about
        if (speed < 0.25f && kind.isEmpty() && !crouch && !a.busy() && limp < 2) {
            e.still += dt;
            if (e.still > e.fidgetAt) {
                a.act(pack, "idle_" + (1 + (int) (Math.random() * 5)), 1, false, false);
                e.still = 0;
                e.fidgetAt = 10 + (float) Math.random() * 14;
            }
        } else {
            e.still = 0;
            if (speed >= 0.25f && a.action() != null && a.action().startsWith("idle_")) a.stopAction(pack);
        }
    }

    /** How a held thing is carried: "rifle" or "pistol" for guns, "bat", "heavy" or "spear" in two hands, "one" or "knife". */
    static String kind(ItemStack stack) {
        if (stack.isEmpty()) return "";
        if (stack.getItem() instanceof RifleItem gun) {
            GunType t = gun.type();
            return switch (t) {
                case PISTOL, POCKET_PISTOL, MACHINE_PISTOL, REVOLVER, MAGNUM -> "pistol";
                default -> "rifle";
            };
        }
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        if (spec == null) return "";
        if (spec.kind() == WeaponSpecs.Kind.SPEAR) return "spear";
        if (spec.twoHanded()) return spec.kind() == WeaponSpecs.Kind.HEAVY_BLUNT || spec.kind() == WeaponSpecs.Kind.AXE ? "heavy" : "bat";
        return spec.kind() == WeaponSpecs.Kind.SHORT_BLADE ? "knife" : "one";
    }

    /** The bones from the chest up (and the hand props): what an upper-body action moves. */
    static boolean[] upperBody(SurvivorPack pack) {
        int n = pack.skin.boneNames.length;
        boolean[] up = new boolean[n];
        for (int b = 0; b < n; b++) {
            String name = pack.skin.boneNames[b];
            if (name.startsWith("Bip01_Prop")) up[b] = true;
            for (int p = b; p >= 0 && !up[b]; p = pack.skin.parent[p]) {
                if (p == pack.spine1) up[b] = true;
            }
        }
        return up;
    }

    /** In a seat, her hips go on it whatever the clip's own height. */
    private static void seat(Entry e, SurvivorPack pack, Player player) {
        if (!player.isPassenger() || pack.pelvis < 0 || com.zomboidcraft.world.cars.Turret.isGunner(player)) return;
        int p = pack.pelvis;
        float ox = pack.pelvisRest.x - e.pos[p * 3], oy = SEAT - e.pos[p * 3 + 1], oz = pack.pelvisRest.z - e.pos[p * 3 + 2];
        for (int b = 0; b < e.pos.length / 3; b++) {
            e.pos[b * 3] += ox;
            e.pos[b * 3 + 1] += oy;
            e.pos[b * 3 + 2] += oz;
        }
    }

    // ------------------------------------------------------------------ head and hands

    /** Her neck and head turned where the player looks (two parts in five the neck, the rest the head). */
    private static void look(SurvivorPack pack, float[] pos, float[] turn, AvatarRenderState state) {
        if (pack.neck < 0 || pack.head < 0) return;
        float yaw = Math.max(-MAX_YAW, Math.min(MAX_YAW, state.yRot)), pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, state.xRot));
        if (state.hasPose(Pose.SLEEPING)) return;
        rotateBelow(pack, pos, turn, pack.neck, new Quaternionf().rotateY((float) Math.toRadians(-yaw * 0.4f)).rotateX((float) Math.toRadians(pitch * 0.4f)));
        rotateBelow(pack, pos, turn, pack.head, new Quaternionf().rotateY((float) Math.toRadians(-yaw * 0.6f)).rotateX((float) Math.toRadians(pitch * 0.6f)));
    }

    /** Turns a bone and everything below it about the bone, in model space. */
    static void rotateBelow(SurvivorPack pack, float[] pos, float[] turn, int bone, Quaternionf r) {
        float px = pos[bone * 3], py = pos[bone * 3 + 1], pz = pos[bone * 3 + 2];
        Vector3f d = new Vector3f();
        Quaternionf q = new Quaternionf();
        for (int b = 0; b < pos.length / 3; b++) {
            if (!below(pack, b, bone)) continue;
            d.set(pos[b * 3] - px, pos[b * 3 + 1] - py, pos[b * 3 + 2] - pz);
            r.transform(d);
            pos[b * 3] = px + d.x;
            pos[b * 3 + 1] = py + d.y;
            pos[b * 3 + 2] = pz + d.z;
            q.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            new Quaternionf(r).mul(q, q).normalize();
            turn[b * 4] = q.x;
            turn[b * 4 + 1] = q.y;
            turn[b * 4 + 2] = q.z;
            turn[b * 4 + 3] = q.w;
        }
    }

    private static boolean below(SurvivorPack pack, int bone, int of) {
        for (int p = bone; p >= 0; p = pack.skin.parent[p]) {
            if (p == of) return true;
        }
        return false;
    }

    /** Her model space (metres, facing +z) to vanilla's model space (blocks, y down, facing -z) and back. */
    private static Vector3f toModel(float x, float y, float z) {
        return new Vector3f(x * K, ORIGIN - y * K, -z * K);
    }

    private static Vector3f fromModel(float x, float y, float z) {
        return new Vector3f(x / K, (ORIGIN - y) / K, -z / K);
    }

    /** Her space turned into vanilla's: a half turn about x. */
    private static final Quaternionf FLIP = new Quaternionf(1, 0, 0, 0);

    /** Where vanilla's ItemInHandLayer puts a held item, from the arm: X -90, Y 180, then a little down the hand. */
    private static Matrix4f inHand(boolean left) {
        return new Matrix4f().rotationX((float) Math.toRadians(-90)).rotateY((float) Math.PI)
                .translate((left ? -1 : 1) / 16f, 0.125f, -0.625f);
    }

    /** The item frame a vanilla arm holds things in (vanilla model space, blocks). */
    private static Matrix4f itemFrame(ModelPart arm, boolean left, boolean slim) {
        float shift = slim ? (left ? -0.5f : 0.5f) : 0;
        return new Matrix4f().translation((arm.x + shift) / 16f, arm.y / 16f, arm.z / 16f)
                .rotate(new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot)).mul(inHand(left));
    }

    /** Where her hand's grip is this frame (her space). */
    static Vector3f gripAt(SurvivorPack pack, float[] pos, float[] turn, int s) {
        int h = pack.hand[s];
        if (h < 0) return new Vector3f();
        Quaternionf hq = new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]);
        return hq.transform(new Vector3f(pack.grip[s])).add(pos[h * 3], pos[h * 3 + 1], pos[h * 3 + 2]);
    }

    /** The vanilla arm put where her hand holds things, so whatever it holds is drawn in her hand. */
    private static void armToHand(SurvivorPack pack, float[] pos, float[] turn, int s, ModelPart arm, boolean slim) {
        int h = pack.hand[s];
        if (h < 0) return;
        Quaternionf hq = new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]);
        Vector3f at = hq.transform(new Vector3f(pack.grip[s])).add(pos[h * 3], pos[h * 3 + 1], pos[h * 3 + 2]);
        Quaternionf item = new Quaternionf(FLIP).mul(hq).mul(pack.gripTurn[s]);
        Vector3f v = toModel(at.x, at.y, at.z);
        Matrix4f armFrame = new Matrix4f().translation(v).rotate(item).mul(inHand(s == 1).invert());
        float shift = slim ? (s == 1 ? -0.5f : 0.5f) : 0;
        arm.x = armFrame.m30() * 16 - shift;
        arm.y = armFrame.m31() * 16;
        arm.z = armFrame.m32() * 16;
        Vector3f e = new Matrix3f(armFrame).getEulerAnglesZYX(new Vector3f());
        arm.setRotation(e.x, e.y, e.z);
    }

    /** Her hand's grip brought to a point (her space), the wrist kept as the clip has it. */
    static void reachPoint(SurvivorPack pack, float[] pos, float[] turn, int s, Vector3f point) {
        int h = pack.hand[s];
        if (h < 0) return;
        Quaternionf hq = new Quaternionf(turn[h * 4], turn[h * 4 + 1], turn[h * 4 + 2], turn[h * 4 + 3]);
        reach(pack, pos, turn, s, new Vector3f(point).sub(hq.transform(new Vector3f(pack.grip[s]))));
    }

    /**
     * Two-bone reach: the shoulder stays, the elbow bends in the plane the clip already bends it in, and the wrist lands
     * on the target (as near as the arm's length allows).
     */
    static void reach(SurvivorPack pack, float[] pos, float[] turn, int s, Vector3f target) {
        reach(pack, pos, turn, s, target, null);
    }

    /** The same reach, with the elbow bending toward pole (her space) when one is given. */
    static void reach(SurvivorPack pack, float[] pos, float[] turn, int s, Vector3f target, Vector3f pole) {
        int u = pack.upper[s], f = pack.fore[s], h = pack.hand[s];
        if (u < 0 || f < 0 || h < 0) return;
        Vector3f sh = vec(pos, u), el = vec(pos, f), wr = vec(pos, h);
        float a = sh.distance(el), b = el.distance(wr);
        Vector3f toT = new Vector3f(target).sub(sh);
        float d = Math.max(Math.abs(a - b) + 1e-3f, Math.min(a + b - 1e-3f, toT.length()));
        if (toT.lengthSquared() < 1e-8f) return;
        Vector3f dir = new Vector3f(toT).normalize();
        Vector3f bend = pole != null ? new Vector3f(pole).sub(sh) : new Vector3f(el).sub(sh);
        bend.sub(new Vector3f(dir).mul(bend.dot(dir)));
        if (bend.lengthSquared() < 1e-8f) bend.set(s == 0 ? -0.3f : 0.3f, -1, -0.2f).sub(new Vector3f(dir).mul(dir.dot(0, -1, 0)));
        bend.normalize();
        float cos = (a * a + d * d - b * b) / (2 * a * d);
        cos = Math.max(-1, Math.min(1, cos));
        float sin = (float) Math.sqrt(1 - cos * cos);
        Vector3f elbow = new Vector3f(sh).add(new Vector3f(dir).mul(a * cos)).add(new Vector3f(bend).mul(a * sin));
        Quaternionf r1 = new Quaternionf().rotationTo(new Vector3f(el).sub(sh).normalize(), new Vector3f(elbow).sub(sh).normalize());
        rotateBelow(pack, pos, turn, u, r1);
        Vector3f wr1 = vec(pos, h), el1 = vec(pos, f);
        Vector3f wantWrist = new Vector3f(sh).add(new Vector3f(dir).mul(d));
        Quaternionf r2 = new Quaternionf().rotationTo(new Vector3f(wr1).sub(el1).normalize(), new Vector3f(wantWrist).sub(el1).normalize());
        rotateBelow(pack, pos, turn, f, r2);
    }

    private static Vector3f vec(float[] pos, int b) {
        return new Vector3f(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2]);
    }

    /** Her torso measured once per pack (her bind pose, metres): where the neck is, and the chest and waist bands. */
    private record Torso(float neckY, float pelvisY, float[] chest, float[] waist, float[] head) {
    }

    private static final Map<SurvivorPack, Torso> TORSOS = new java.util.WeakHashMap<>();

    /** Her body's centre (x, z), width and depth at height y, from the body mesh's vertices near it. */
    private static float[] band(SurvivorPack pack, float y) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (com.zomboidcraft.client.npc.NpcSkin.Group g : pack.skin.groups) {
            if (!g.piece.equals("body")) continue;
            for (int i = 0; i < g.vertices; i++) {
                if (Math.abs(g.pos[i * 3 + 1] - y) > 0.025f) continue;
                float x = g.pos[i * 3], z = g.pos[i * 3 + 2];
                if (Math.abs(x) > 0.3f) continue;   // not the hands hanging beside her
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
        }
        if (minX > maxX) return new float[]{0, 0, 0.3f, 0.2f};
        return new float[]{(minX + maxX) / 2, (minZ + maxZ) / 2, maxX - minX, maxZ - minZ};
    }

    private static Torso torso(SurvivorPack pack) {
        Vector3f neck = pack.bind[pack.neck].getTranslation(new Vector3f());
        Vector3f pelvis = pack.bind[pack.pelvis].getTranslation(new Vector3f());
        float drop = neck.y - pelvis.y;
        // Minecraft's chest is 5 px under the neck and its belt line 11, of a 12 px torso
        return new Torso(neck.y, pelvis.y, band(pack, neck.y - drop * 5 / 12f), band(pack, neck.y - drop * 11 / 12f),
                band(pack, neck.y + 0.12f));
    }

    /**
     * The frame Minecraft's body part gives worn gear (blocks, y down, from the neck, a Minecraft-sized torso) moved
     * onto a survivor this frame: her chest (part 0, following her upper spine), her waist (1, her pelvis) or her
     * right thigh (2), sized to her; or Minecraft's head part (3: from the neck, an 8 px head) onto her head. wrap: the piece goes round her (a belt) and takes her width and depth; otherwise
     * it keeps its shape. Null when this state isn't a survivor drawn.
     */
    public static Matrix4f gearFrame(AvatarRenderState state, int part, boolean wrap) {
        Drawn d = take(state);
        if (d == null || d.skin == null) return null;
        SurvivorPack pack = d.pack;
        int bone = part == 0 ? pack.spine1 : part == 1 ? pack.pelvis : part == 2 ? pack.skin.bone("Bip01_R_Thigh") : pack.head;
        if (bone < 0 || pack.neck < 0 || pack.pelvis < 0) return null;
        Torso t = TORSOS.computeIfAbsent(pack, Survivors::torso);
        if (part == 3) {
            // sized by her head's height (neck to crown) against Minecraft's 8 px, so a helmet sits round her skull
            float h = Math.max(0.15f, pack.skin.top - t.neckY()) * K / 0.5f;
            return new Matrix4f().translation(0, ORIGIN, 0).scale(K, -K, -K).mul(d.skin[bone])
                    .translate(t.head()[0], t.neckY(), t.head()[1]).scale(h / K, -h / K, -h / K);
        }
        float[] b = part == 0 ? t.chest() : t.waist();
        float sy = (t.neckY() - t.pelvisY()) * K / 0.75f, sx = b[2] * K / 0.5f, sz = b[3] * K / 0.25f;
        if (!wrap) sx = sy = sz = (sy + sz) / 2;
        return new Matrix4f().translation(0, ORIGIN, 0).scale(K, -K, -K).mul(d.skin[bone])
                .translate(b[0], t.neckY(), b[1]).scale(sx / K, -sy / K, -sz / K);
    }

    /** Each bone's pose times its inverse bind: what the skinning wants. */
    static Matrix4f[] skinOf(SurvivorPack pack, float[] pos, float[] turn) {
        int n = pack.skin.boneNames.length;
        Matrix4f[] out = new Matrix4f[n];
        Quaternionf q = new Quaternionf();
        for (int b = 0; b < n; b++) {
            q.set(turn[b * 4], turn[b * 4 + 1], turn[b * 4 + 2], turn[b * 4 + 3]);
            out[b] = new Matrix4f().translationRotate(pos[b * 3], pos[b * 3 + 1], pos[b * 3 + 2], q).mul(pack.skin.inverse[b]);
        }
        return out;
    }

    /** The geometry buffers for this submit: a second set when the same player is drawn twice in one frame. */
    static Object[] buffers(Drawn d) {
        Entry e = d.entry;
        if (e.submitAge == d.state.ageInTicks) e.buffer = 1 - e.buffer;
        e.submitAge = d.state.ageInTicks;
        if (e.buffers[e.buffer] == null) e.buffers[e.buffer] = new Object[d.pack.skin.groups.length * 2];
        return e.buffers[e.buffer];
    }
}
