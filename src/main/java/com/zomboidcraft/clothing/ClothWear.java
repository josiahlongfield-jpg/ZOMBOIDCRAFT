package com.zomboidcraft.clothing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.Body.Region;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * How worn a piece of clothing is, Zomboid style. Every hit it stops wears it down; some tear a hole in the spot that
 * was hit, and a hole guards nothing until it's patched. A patch (a strip of fabric, denim or leather, sewn on with a
 * needle and thread) closes a hole and adds a little guard of its own. A piece with no wear left is ruined: it still
 * keeps some warmth, but every spot is open.
 *
 * @param worn    hits taken, of the garment's condition
 * @param holes   one bit per place (Spot.place)
 * @param patches two bits per place: 0 none, then the Patch kinds
 */
public record ClothWear(int worn, int holes, long patches) {
    public static final ClothWear NEW = new ClothWear(0, 0, 0L);

    /** What a patch is cut from, weakest first: the guard it adds to its spot, Zomboid's numbers. */
    public enum Patch {
        NONE(0f, 0f, ""), FABRIC(0.02f, 0.05f, "fabric"), DENIM(0.05f, 0.1f, "denim"), LEATHER(0.1f, 0.1f, "leather");

        public final float bite, scratch;
        public final String label;

        Patch(float bite, float scratch, String label) {
            this.bite = bite;
            this.scratch = scratch;
            this.label = label;
        }
    }

    public static final Codec<ClothWear> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("worn", 0).forGetter(ClothWear::worn),
            Codec.INT.optionalFieldOf("holes", 0).forGetter(ClothWear::holes),
            Codec.LONG.optionalFieldOf("patches", 0L).forGetter(ClothWear::patches)
    ).apply(i, ClothWear::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ClothWear> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ClothWear::worn,
            ByteBufCodecs.VAR_INT, ClothWear::holes,
            ByteBufCodecs.VAR_LONG, ClothWear::patches,
            ClothWear::new);

    public static final DataComponentType<ClothWear> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("cloth_wear"), DataComponentType.<ClothWear>builder()
                    .persistent(CODEC).networkSynchronized(STREAM_CODEC).build());

    public static void register() {
        // loads the class, which registers the component
    }

    public static ClothWear of(ItemStack stack) {
        return stack.getOrDefault(TYPE, NEW);
    }

    public boolean hole(Spot spot, Region region) {
        return (holes >> spot.place(region) & 1) != 0;
    }

    public Patch patch(Spot spot, Region region) {
        return Patch.values()[(int) (patches >> (spot.place(region) * 2) & 3)];
    }

    public boolean ruined(Garment g) {
        return worn >= g.condition();
    }

    /** Whether this spot guards anything: not torn, and the piece not ruined. */
    public boolean open(Garment g, Spot spot, Region region) {
        return ruined(g) || hole(spot, region);
    }

    /** How many of the places the piece covers are torn open, 0 to 1. */
    public float torn(Garment g) {
        if (ruined(g)) return 1f;
        int places = 0, open = 0;
        for (Spot s : g.covers()) {
            for (Region r : s.regions()) {
                places++;
                if (hole(s, r)) open++;
            }
        }
        return places == 0 ? 0 : open / (float) places;
    }

    public int holeCount() {
        return Integer.bitCount(holes);
    }

    public int patchCount() {
        int n = 0;
        for (int p = 0; p < Spot.PLACES; p++) if ((patches >> (p * 2) & 3) != 0) n++;
        return n;
    }

    /** The share of its condition left, 0 to 1. */
    public float left(Garment g) {
        return Math.max(0, 1 - worn / (float) Math.max(1, g.condition()));
    }

    /**
     * The piece after stopping a hit on this spot: it wears down, and soft cloth may tear there (bites tear more, and
     * a bullet always bites deep into what stopped it).
     */
    public ClothWear hit(Garment g, Spot spot, Region region, int wear, float tear, RandomSource random) {
        int nowWorn = Math.min(g.condition(), worn + wear);
        int nowHoles = holes;
        if (g.cloth() != Garment.Cloth.HARD && g.cloth() != Garment.Cloth.NONE && random.nextFloat() < tear) {
            nowHoles |= 1 << spot.place(region);
        }
        return new ClothWear(nowWorn, nowHoles, patches);
    }

    /** A patch sewn over this spot, closing its hole. Patching mends a little of the wear too. */
    public ClothWear patched(Spot spot, Region region, Patch kind) {
        int place = spot.place(region);
        long cleared = patches & ~(3L << (place * 2));
        return new ClothWear(Math.max(0, worn - 1), holes & ~(1 << place), cleared | ((long) kind.ordinal() << (place * 2)));
    }
}
