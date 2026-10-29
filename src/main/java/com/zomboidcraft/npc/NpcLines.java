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
        CLEAR(20 * 30, "That's the last of them.", "Clear. I think.", "Quiet again.");

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
        if (!(npc.level() instanceof ServerLevel level) || !npc.isAlive() && kind != Kind.HURT) {
            return false;
        }
        long now = level.getGameTime();
        Long prev = last.get(kind);
        if (now - lastAny < QUIET || prev != null && now - prev < kind.wait) {
            return false;
        }
        String line = kind.lines.get(npc.getRandom().nextInt(kind.lines.size()));
        if (line.equals(lastLine) && kind.lines.size() > 1) {
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
