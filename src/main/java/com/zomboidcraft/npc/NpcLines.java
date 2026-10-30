package com.zomboidcraft.npc;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * What a companion says, and when. Each line goes to the chat of players near her, her mouth moves for as long as it
 * would take to say (NpcEntity.talk, drawn by the client), and she won't repeat herself: each kind of line has its
 * own wait, and any line keeps her quiet a few seconds.
 */
public final class NpcLines {
    public enum Kind {
        FOLLOW(0, "Right behind you.", "Lead the way.", "Okay. I'm with you.", "Coming."),
        WAIT(0, "I'll hold here.", "Don't be long.", "Waiting. Hurry back.", "I'll be right here."),
        SPOTTED(20 * 25, "Zombies!", "Got movement!", "They're coming!", "Contact!", "Here they come."),
        FLEE(20 * 15, "Too close, too close!", "Back off!", "I need space!", "Get away from me!"),
        RELOAD(20 * 12, "Reloading!", "Changing mags!", "Cover me, reloading!"),
        HURT(20 * 6, "Ah! I'm hit!", "They got me!", "I'm bleeding!", "Ow, damn it!"),
        GUN(0, "I'll take this.", "This'll do.", "Now we're talking."),
        CLEAR(20 * 30, "That's the last of them.", "Clear. I think.", "Quiet again."),
        // her needs and feelings (NpcMind), said now and then while they last
        HUNGRY(20 * 180, "I'm starving. When did we last eat?", "My stomach's eating itself.", "We need food. Soon.",
                "I'd kill for a can of beans right now."),
        THIRSTY(20 * 150, "I need water.", "My mouth's like sandpaper.", "Is there anything to drink?", "Water. Please."),
        TIRED(20 * 200, "I can barely keep my eyes open.", "Can we stop somewhere? Just for a bit.", "I'm running on empty.",
                "If I sit down I'm not getting up."),
        PAIN(20 * 90, "It hurts. It really hurts.", "I need something for this.", "I'm okay. I'm not okay.", "Bandages. Anything."),
        SCARED(20 * 120, "I don't like this.", "Something's wrong here.", "Stay close. Please.", "Did you hear that?"),
        PANIC(20 * 20, "No no no no!", "We have to go, NOW!", "I can't, I can't!", "Run!"),
        WIRED(20 * 120, "My hands won't stop shaking.", "That was too close.", "My heart's going a mile a minute.",
                "Okay. Okay. Breathe."),
        LONELY(20 * 240, "Hello? Anyone?", "It's too quiet on my own.", "Where did you go?"),
        MISSED(20 * 300, "There you are. I thought you'd left me.", "Don't leave me alone that long again.",
                "Took your time.", "I was starting to worry."),
        LOW(20 * 300, "What's even the point anymore?", "I keep thinking about before.", "We're not getting out of this, are we?"),
        GOOD(20 * 400, "You know, we might actually make it.", "Not a bad day, all things considered.", "I'm glad I'm not doing this alone."),
        EAT(20 * 20, "Oh, thank god.", "Mm. Better.", "That hit the spot.", "Don't tell me what's in it."),
        // her work and her day (NpcWork): the words come with the call (sayLine)
        WORK(20 * 20), WORK_DONE(20 * 25), LEVEL(0), ALARM(0), SLEEPY(20 * 60), WAKE(20 * 60), NIGHTMARE(0);

        final int wait;
        final List<String> lines;

        Kind(int wait, String... lines) {
            this.wait = wait;
            this.lines = List.of(lines);
        }
    }

    /** Ticks after any line before she'll say another, and how far she's heard. */
    private static final int QUIET = 20 * 3;
    private static final double HEARD = 24;

    final Map<Kind, Long> last = new EnumMap<>(Kind.class);
    long lastAny = -1_000_000;
    String lastLine = "";

    /** Says a line of this kind if she isn't keeping quiet; returns whether she did. */
    boolean say(NpcEntity npc, Kind kind) {
        return kind.lines.isEmpty() ? false : sayLine(npc, kind, null);
    }

    /** Says this line under this kind's wait (null: one of the kind's own lines). */
    boolean sayLine(NpcEntity npc, Kind kind, String said) {
        if (!(npc.level() instanceof ServerLevel level) || !npc.isAlive() && kind != Kind.HURT) {
            return false;
        }
        long now = level.getGameTime();
        Long prev = last.get(kind);
        if (kind != Kind.ALARM && now - lastAny < QUIET || prev != null && now - prev < kind.wait) {
            return false;
        }
        String line = said != null ? said : kind.lines.get(npc.getRandom().nextInt(kind.lines.size()));
        if (said == null && line.equals(lastLine) && kind.lines.size() > 1) {
            line = kind.lines.get((kind.lines.indexOf(line) + 1) % kind.lines.size());
        }
        last.put(kind, now);
        lastAny = now;
        lastLine = line;
        Component text = Component.literal("<" + npc.speakerName() + "> ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(line).withStyle(ChatFormatting.WHITE));
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(npc) < HEARD * HEARD) {
                p.sendSystemMessage(text);
            }
        }
        // a little over a tenth of a second a syllable or so: long enough to read as speech
        npc.talk(10 + line.length() * 2);
        return true;
    }
}
