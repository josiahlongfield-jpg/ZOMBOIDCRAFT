package com.zomboidcraft.client.operator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.npc.NpcSkin;
import com.zomboidcraft.client.npc.NpcSkinDraw;
import com.zomboidcraft.combat.LocalGuns;
import com.zomboidcraft.combat.LocalHands;
import com.zomboidcraft.combat.throwing.ThrowableItem;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The operator's first person with a Tarkov blade or grenade in hand (config/zomboidcraft/hands, the Ka-Bar, the M48
 * Kukri, the F-1, 2026-11-06): Tarkov's own arms and the weapon, every move its own clip. A blade is drawn (hands_in),
 * idles, swings its two swings in turn as the attack lands (paced to the weapon's swing speed) and is inspected on the
 * inspect key (look). A grenade is drawn, has its pin pulled as the wind-up starts (pin), is held ready (hold) and goes
 * on the throw (throw), the next one drawn after it. The clips are baked from the eye, like the guns'.
 */
public final class OperatorHands {
    private OperatorHands() {
    }

    private static final String IDLE = "idle_armed", DRAW = "hands_in", LOOK = "look";
    private static final String SWING_0 = "swing_0", SWING_1 = "swing_1";
    private static final String PIN = "pin", HOLD = "hold", THROW = "throw";

    private static String drawn, clip;
    private static float clipStart, rate = 1, lastDrawn = -1;
    private static int lastSlot = -1, swings;
    private static boolean swingAsked, winding;
    private static final Deque<String> queue = new ArrayDeque<>();
    private static float[] pos, turn, fromPos, fromTurn;
    private static float fadeStart, fadeLen;
    private static final Object[][] BUFFERS = new Object[2][];
    private static int buffer;

    /** The model for a held Tarkov blade or grenade, or null. */
    static String model(ItemStack stack) {
        LocalHands.Spec spec = stack == null || stack.isEmpty() ? null : LocalHands.of(stack);
        return spec == null ? null : spec.fp;
    }

    /**
     * A full-strength swing of the held blade went (CombatClient): its next swing clip plays, with its own sound.
     * False when it isn't a Tarkov blade drawn here (the usual swing sound plays instead).
     */
    public static boolean swung(ItemStack stack) {
        String model = model(stack);
        if (model == null || NpcSkin.get(model) == null) return false;
        swingAsked = true;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) sound(player, stack, "swing");
        return true;
    }

    /** Draws the operator's arms and the held blade or grenade; false when it isn't one with a model on this PC. */
    public static boolean submit(PoseStack pose, SubmitNodeCollector out, int light, PlayerRenderState state, ItemStack stack) {
        String model = model(stack);
        NpcSkin skin = model == null ? null : NpcSkin.get(model);
        LocalPlayer player = Minecraft.getInstance().player;
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
            clip = null;
        }
        boolean grenade = stack.getItem() instanceof ThrowableItem;

        // brought up: another slot, or not drawn for a moment
        int slot = player.getInventory().getSelectedSlot();
        if (clip == null || slot != lastSlot || lastDrawn < 0 || now - lastDrawn > 0.3f || now < lastDrawn) {
            queue.clear();
            winding = false;
            swingAsked = false;
            play(skin, DRAW, now, 0, 1);
            sound(player, stack, "draw");
        }
        lastSlot = slot;
        lastDrawn = now;

        if (grenade) {
            // the wind-up pulls the pin and holds it ready; letting go throws it, and the next one comes up
            boolean using = player.isUsingItem() && player.getUseItem() == stack;
            if (using && !winding) {
                winding = true;
                queue.clear();
                queue.add(HOLD);
                play(skin, PIN, now, 0.08f, 1);
                sound(player, stack, "pin");
            } else if (!using && winding) {
                winding = false;
                queue.clear();
                queue.add(DRAW);
                play(skin, THROW, now, 0.05f, 1);
                sound(player, stack, "throw");
            }
        } else if (swingAsked) {
            swingAsked = false;
            if (!DRAW.equals(clip) || now - clipStart > 0.2f) {
                String swing = swings++ % 2 == 0 ? SWING_0 : SWING_1;
                NpcSkin.Clip c = skin.clips.get(swing);
                // a quicker weapon swings its clip faster, never slower than Tarkov's own
                float gap = player.getCurrentItemAttackStrengthDelay() / 20f;
                float r = c == null || gap <= 0 ? 1 : Math.max(1, c.seconds() / gap);
                queue.clear();
                play(skin, swing, now, 0.04f, r);
            }
        }
        if (OperatorView.takeInspect() && IDLE.equals(clip) && !winding) play(skin, LOOK, now, 0.2f, 1);

        // a clip run out: the next in the queue, or the idle (the grenade held ready stays held)
        NpcSkin.Clip c = skin.clips.get(clip);
        if (c == null) return false;
        boolean loops = IDLE.equals(clip) || HOLD.equals(clip) && winding;
        if (!loops && (now - clipStart) * rate >= c.seconds()) {
            String next = queue.poll();
            if (next == null) next = IDLE;
            if (DRAW.equals(next)) sound(player, stack, "draw");
            play(skin, next, clipStart + c.seconds() / rate, 0.08f, 1);
            c = skin.clips.get(clip);
            if (c == null) return false;
            loops = IDLE.equals(clip) || HOLD.equals(clip) && winding;
        }
        Operator.sample(c, (now - clipStart) * rate, loops ? Operator.LOOP : Operator.HOLD, pos, turn);
        if (fadeLen > 0 && now - fadeStart < fadeLen) {
            float[] p = fromPos.clone(), t = fromTurn.clone();
            Operator.blend(p, t, pos, turn, Operator.smooth((now - fadeStart) / fadeLen));
            System.arraycopy(p, 0, pos, 0, pos.length);
            System.arraycopy(t, 0, turn, 0, turn.length);
        }

        // from the eye, as baked: none of Minecraft's swing, dip or bob moves Tarkov's arms
        pose.pushPose();
        pose.last().pose().identity();
        pose.last().normal().identity();
        pose.mulPose(TarkovMotion.gun());   // the body's own motion walking and running, as with the guns
        pose.scale(OperatorView.WIDEN, OperatorView.WIDEN, 1);   // at Tarkov's field of view, as the guns are
        org.joml.Matrix4f[] bones = Operator.bones(skin, pos, turn);
        buffer = 1 - buffer;
        if (BUFFERS[buffer] == null || BUFFERS[buffer].length != skin.groups.length * 2) {
            BUFFERS[buffer] = new Object[skin.groups.length * 2];
        }
        NpcSkinDraw.submitGroups(skin.groups, bones, 0, g -> true, g -> skin.texture(g.texture, model), BUFFERS[buffer],
                light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, out);
        pose.popPose();
        return true;
    }

    private static void play(NpcSkin skin, String name, float at, float fade, float speed) {
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
        rate = speed;
    }

    /** One of its own sounds from the Tarkov sound pack (weapon.local/ID/NAME), heard only here. */
    private static void sound(LocalPlayer player, ItemStack stack, String name) {
        LocalHands.Spec spec = LocalHands.of(stack);
        if (spec == null) return;
        String id = spec.sounds != null ? spec.sounds : spec.id;
        SoundEvent e = SoundEvent.createVariableRangeEvent(ZomboidCraft.id(LocalGuns.SOUND_PREFIX
                + id.toLowerCase(java.util.Locale.ROOT) + "/" + name));
        player.level().playLocalSound(player.getX(), player.getEyeY(), player.getZ(), e, SoundSource.PLAYERS, 0.8f, 1f, false);
    }
}
