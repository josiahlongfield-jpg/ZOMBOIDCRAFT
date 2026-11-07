package com.zomboidcraft.building.base;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;

/**
 * One claimed base, Zomboid's safehouse (PZ review s17, base plan pick 3): the box round the building its owner
 * claimed, which grows as they build onto it, and the squad the owner has let in. Only the owner and the squad can
 * change anything inside it. Each player has one base (moving it to another building lets the old one go); an
 * outpost is a claim of its own kind, out on the hunt (rooms plan R5).
 */
public record Claim(int id, UUID owner, String ownerName, List<UUID> squad, List<String> squadNames, BlockPos min, BlockPos max, String kind) {
    public static final String BASE = "base", OUTPOST = "outpost";

    public static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(Claim::id),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Claim::owner),
            Codec.STRING.fieldOf("owner_name").forGetter(Claim::ownerName),
            UUIDUtil.CODEC.listOf().fieldOf("squad").forGetter(Claim::squad),
            Codec.STRING.listOf().fieldOf("squad_names").forGetter(Claim::squadNames),
            BlockPos.CODEC.fieldOf("min").forGetter(Claim::min),
            BlockPos.CODEC.fieldOf("max").forGetter(Claim::max),
            Codec.STRING.fieldOf("kind").forGetter(Claim::kind)
    ).apply(i, Claim::new));

    public boolean contains(BlockPos p) {
        return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    /** How far a spot is outside the box, in blocks (0 inside). */
    public int distance(BlockPos p) {
        int dx = Math.max(0, Math.max(min.getX() - p.getX(), p.getX() - max.getX()));
        int dy = Math.max(0, Math.max(min.getY() - p.getY(), p.getY() - max.getY()));
        int dz = Math.max(0, Math.max(min.getZ() - p.getZ(), p.getZ() - max.getZ()));
        return Math.max(dx, Math.max(dy, dz));
    }

    public boolean overlaps(BlockPos lo, BlockPos hi) {
        return lo.getX() <= max.getX() && hi.getX() >= min.getX() && lo.getY() <= max.getY() && hi.getY() >= min.getY()
                && lo.getZ() <= max.getZ() && hi.getZ() >= min.getZ();
    }

    /** The owner or one of the squad. */
    public boolean member(UUID who) {
        return owner.equals(who) || squad.contains(who);
    }

    public boolean member(Player player) {
        return member(player.getUUID());
    }

    public Claim withBox(BlockPos lo, BlockPos hi) {
        return new Claim(id, owner, ownerName, squad, squadNames, lo, hi, kind);
    }

    /** The box grown to take in {@code p} and a block round it. */
    public Claim grownTo(BlockPos p) {
        BlockPos lo = new BlockPos(Math.min(min.getX(), p.getX() - 1), Math.min(min.getY(), p.getY() - 1), Math.min(min.getZ(), p.getZ() - 1));
        BlockPos hi = new BlockPos(Math.max(max.getX(), p.getX() + 1), Math.max(max.getY(), p.getY() + 1), Math.max(max.getZ(), p.getZ() + 1));
        return withBox(lo, hi);
    }

    public Claim withSquad(UUID who, String name, boolean in) {
        List<UUID> ids = new ArrayList<>(squad);
        List<String> names = new ArrayList<>(squadNames);
        int at = ids.indexOf(who);
        if (at >= 0) {
            ids.remove(at);
            names.remove(Math.min(at, names.size() - 1));
        }
        if (in) {
            ids.add(who);
            names.add(name);
        }
        return new Claim(id, owner, ownerName, List.copyOf(ids), List.copyOf(names), min, max, kind);
    }

    /** Floor area of the box, for the base page. */
    public int width() {
        return max.getX() - min.getX() + 1;
    }

    public int depth() {
        return max.getZ() - min.getZ() + 1;
    }
}
