package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * Injuries by body part, Zomboid's seventeen (see {@link Region}), each with at most one wound (a scratch, a
 * laceration, a bite or a burn) and a fracture. Wounds can bleed until bandaged or clotted; fractures heal far
 * faster splinted. Saved with the player and synced to their own client for the HUD's figure.
 */
public record Body(List<Part> parts) {
    /**
     * Zomboid's seventeen body parts, head to toe. Each sits in a coarse {@link Zone} (a limb, the head, the torso) for
     * whatever only cares which limb it is: a slowed leg, a shaky arm, which sleeve of a jacket.
     */
    public enum Region {
        HEAD("head", Zone.HEAD), NECK("neck", Zone.HEAD),
        UPPER_TORSO("upper torso", Zone.TORSO), LOWER_TORSO("lower torso", Zone.TORSO), GROIN("groin", Zone.TORSO),
        LEFT_UPPER_ARM("left upper arm", Zone.LEFT_ARM), RIGHT_UPPER_ARM("right upper arm", Zone.RIGHT_ARM),
        LEFT_FOREARM("left forearm", Zone.LEFT_ARM), RIGHT_FOREARM("right forearm", Zone.RIGHT_ARM),
        LEFT_HAND("left hand", Zone.LEFT_ARM), RIGHT_HAND("right hand", Zone.RIGHT_ARM),
        LEFT_THIGH("left thigh", Zone.LEFT_LEG), RIGHT_THIGH("right thigh", Zone.RIGHT_LEG),
        LEFT_SHIN("left shin", Zone.LEFT_LEG), RIGHT_SHIN("right shin", Zone.RIGHT_LEG),
        LEFT_FOOT("left foot", Zone.LEFT_LEG), RIGHT_FOOT("right foot", Zone.RIGHT_LEG);

        public final String label;
        public final Zone zone;

        Region(String label, Zone zone) {
            this.label = label;
            this.zone = zone;
        }

        public Zone zone() {
            return zone;
        }

        public boolean arm() {
            return zone == Zone.LEFT_ARM || zone == Zone.RIGHT_ARM;
        }

        public boolean leg() {
            return zone == Zone.LEFT_LEG || zone == Zone.RIGHT_LEG;
        }

        public boolean left() {
            return zone == Zone.LEFT_ARM || zone == Zone.LEFT_LEG;
        }

        /** The same part on the other side, or itself on the midline. */
        public Region mirror() {
            if (zone == Zone.HEAD || zone == Zone.TORSO) return this;
            return values()[left() ? ordinal() + 1 : ordinal() - 1];
        }
    }

    /** The six blocks of the Minecraft body each part belongs to, and the part that stands in for the whole of one. */
    public enum Zone {
        HEAD("head"), TORSO("torso"), LEFT_ARM("left arm"), RIGHT_ARM("right arm"), LEFT_LEG("left leg"), RIGHT_LEG("right leg");

        public final String label;

        Zone(String label) {
            this.label = label;
        }

        public boolean arm() {
            return this == LEFT_ARM || this == RIGHT_ARM;
        }

        public boolean leg() {
            return this == LEFT_LEG || this == RIGHT_LEG;
        }

        /** Its parts, head to toe. */
        public List<Region> parts() {
            List<Region> out = new ArrayList<>(3);
            for (Region r : Region.values()) if (r.zone == this) out.add(r);
            return out;
        }

        /** Where a hit on the zone as a whole lands, or what an old six-part save becomes. */
        public Region main() {
            return switch (this) {
                case HEAD -> Region.HEAD;
                case TORSO -> Region.UPPER_TORSO;
                case LEFT_ARM -> Region.LEFT_FOREARM;
                case RIGHT_ARM -> Region.RIGHT_FOREARM;
                case LEFT_LEG -> Region.LEFT_SHIN;
                case RIGHT_LEG -> Region.RIGHT_SHIN;
            };
        }
    }

    /** A wound's kind, mildest first, with how long it takes to heal (seconds) and how readily it clots. */
    public enum Wound {
        NONE(0, 0), SCRATCH(600, 2f), BURN(1500, 0f), LACERATION(1200, 1f), BITE(1800, 0.5f);

        public final int heal;
        public final float clot;

        Wound(int heal, float clot) {
            this.heal = heal;
            this.clot = clot;
        }

        public String word() {
            return switch (this) {
                case NONE -> "";
                case SCRATCH -> "Scratched";
                case BURN -> "Burnt";
                case LACERATION -> "Cut";
                case BITE -> "Bitten";
            };
        }
    }

    /** Seconds a fracture takes to knit with a splint; three times that without. */
    public static final int FRACTURE_HEAL = 3600;

    /**
     * One part's state. {@code woundLeft} and {@code fractureLeft} count down in seconds; a fracture is there while
     * fractureLeft is above zero.
     */
    public record Part(Wound wound, int woundLeft, boolean bleeding, boolean bandaged, int fractureLeft, boolean splinted) {
        public static final Part FINE = new Part(Wound.NONE, 0, false, false, 0, false);

        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.xmap(s -> Wound.valueOf(s.toUpperCase(Locale.ROOT)), w -> w.name().toLowerCase(Locale.ROOT))
                        .optionalFieldOf("wound", Wound.NONE).forGetter(Part::wound),
                Codec.INT.optionalFieldOf("wound_left", 0).forGetter(Part::woundLeft),
                Codec.BOOL.optionalFieldOf("bleeding", false).forGetter(Part::bleeding),
                Codec.BOOL.optionalFieldOf("bandaged", false).forGetter(Part::bandaged),
                Codec.INT.optionalFieldOf("fracture_left", 0).forGetter(Part::fractureLeft),
                Codec.BOOL.optionalFieldOf("splinted", false).forGetter(Part::splinted)
        ).apply(i, Part::new));

        public static final StreamCodec<io.netty.buffer.ByteBuf, Part> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(n -> Wound.values()[n], Wound::ordinal), Part::wound,
                ByteBufCodecs.VAR_INT, Part::woundLeft,
                ByteBufCodecs.BOOL, Part::bleeding,
                ByteBufCodecs.BOOL, Part::bandaged,
                ByteBufCodecs.VAR_INT, Part::fractureLeft,
                ByteBufCodecs.BOOL, Part::splinted,
                Part::new);

        public boolean wounded() {
            return wound != Wound.NONE;
        }

        public boolean fractured() {
            return fractureLeft > 0;
        }

        public boolean fine() {
            return !wounded() && !fractured();
        }

        /** 0 fine, 1 a scratch or burn or anything treated, 2 an open cut or bite or a splinted break, 3 a bare break. */
        public int severity() {
            if (fractured()) return splinted ? 2 : 3;
            if (!wounded()) return 0;
            if (bandaged) return 1;
            return wound == Wound.LACERATION || wound == Wound.BITE ? 2 : 1;
        }
    }

    public static final Body FRESH = new Body(java.util.Collections.nCopies(Region.values().length, Part.FINE));

    public static final Codec<Body> CODEC = Part.CODEC.listOf().xmap(Body::of, Body::parts);
    public static final StreamCodec<io.netty.buffer.ByteBuf, Body> STREAM_CODEC =
            Part.STREAM_CODEC.apply(ByteBufCodecs.list()).map(Body::of, Body::parts);

    public static final AttachmentType<Body> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("body"),
            builder -> builder.persistent(CODEC).initializer(() -> FRESH)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    /** Seventeen parts whatever was saved; a save from the six-part body puts each part's state on its zone's main part. */
    private static Body of(List<Part> saved) {
        List<Part> parts = new ArrayList<>(FRESH.parts);
        if (saved.size() == Zone.values().length) {
            for (Zone z : Zone.values()) parts.set(z.main().ordinal(), saved.get(z.ordinal()));
        } else {
            for (int i = 0; i < Math.min(parts.size(), saved.size()); i++) parts.set(i, saved.get(i));
        }
        return new Body(List.copyOf(parts));
    }

    public static Body get(Player player) {
        return player.getAttachedOrElse(TYPE, FRESH);
    }

    public static void set(Player player, Body body) {
        if (!body.equals(player.getAttached(TYPE))) player.setAttached(TYPE, body);
    }

    public Part part(Region region) {
        return parts.get(region.ordinal());
    }

    /** The worst-off part of a zone, standing for the limb as a whole (FINE when all of it is). */
    public Part zone(Zone zone) {
        Part worst = Part.FINE;
        for (Region r : zone.parts()) {
            Part p = part(r);
            if (p.severity() > worst.severity() || worst.fine() && !p.fine()) worst = p;
        }
        return worst;
    }

    /** Whether any part of the zone is broken. */
    public boolean broken(Zone zone) {
        for (Region r : zone.parts()) if (part(r).fractured()) return true;
        return false;
    }

    public Body with(Region region, Part part) {
        List<Part> copy = new ArrayList<>(parts);
        copy.set(region.ordinal(), part);
        return new Body(List.copyOf(copy));
    }

    public int bleeding() {
        int n = 0;
        for (Part p : parts) if (p.bleeding()) n++;
        return n;
    }

    public boolean injured() {
        for (Part p : parts) if (!p.fine()) return true;
        return false;
    }

    /** The part hurting most, or null when you're whole. */
    public Region worst() {
        Region worst = null;
        int most = 0;
        for (Region r : Region.values()) {
            int s = part(r).severity();
            if (s > most) {
                most = s;
                worst = r;
            }
        }
        return worst;
    }

    /** How the worst injury reads: "Broken left leg", "Bitten right arm", "Bandaged torso". */
    public static String describe(Region region, Part part) {
        if (part.fractured()) return (part.splinted() ? "Splinted " : "Broken ") + region.label;
        if (part.bandaged()) return "Bandaged " + region.label;
        return part.wound().word() + " " + region.label;
    }

    /**
     * Where a zombie's hands and teeth land, after Zomboid: mostly the hands, forearms and upper arms raised to fend it
     * off, then the upper body, the head, and the neck (rare, and the worst place to be bitten). A crawler on the floor
     * goes for the legs instead.
     */
    public static Region zombieTarget(RandomSource random) {
        return zombieTarget(random, false);
    }

    public static Region zombieTarget(RandomSource random, boolean crawler) {
        float[] weights = crawler ? CRAWLER_HITS : ZOMBIE_HITS;
        float total = 0;
        for (float w : weights) total += w;
        float r = random.nextFloat() * total;
        for (Region region : Region.values()) {
            r -= weights[region.ordinal()];
            if (r < 0) return region;
        }
        return Region.UPPER_TORSO;
    }

    /** Relative odds per part (Region order): standing zombies, and crawlers at your ankles. */
    private static final float[] ZOMBIE_HITS = {
            6, 3,          // head, neck
            10, 6, 1,      // upper torso, lower torso, groin
            9, 9, 13, 13, 12, 12, // upper arms, forearms, hands
            1, 1, 1, 1, 0.5f, 0.5f};
    private static final float[] CRAWLER_HITS = {
            0, 0,
            0, 1, 1,
            0, 0, 1, 1, 2, 2,
            6, 6, 12, 12, 10, 10};

    /** Any part, evenly. */
    public static Region any(RandomSource random) {
        return Region.values()[random.nextInt(Region.values().length)];
    }

    /** A random part of one zone. */
    public static Region any(Zone zone, RandomSource random) {
        List<Region> parts = zone.parts();
        return parts.get(random.nextInt(parts.size()));
    }

    /** Opens a wound on a part; a worse wound replaces a milder one, the same kind starts healing over. */
    public Body wound(Region region, Wound wound, boolean bleeding) {
        Part p = part(region);
        Wound kept = p.wound().ordinal() > wound.ordinal() ? p.wound() : wound;
        return with(region, new Part(kept, kept.heal, p.bleeding() || bleeding, false, p.fractureLeft(), p.splinted()));
    }

    public Body fracture(Region region) {
        Part p = part(region);
        return with(region, new Part(p.wound(), p.woundLeft(), p.bleeding(), p.bandaged(), FRACTURE_HEAL * 3, false));
    }

    public static void register() {
        // loading the class registers the attachment
    }
}
