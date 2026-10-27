package com.zomboidcraft.loot.items;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * How far along a perishable item is, Zomboid style: fresh, then stale, then rotten. Age is kept in days and brought up
 * to date whenever someone has the item in front of them (in their inventory, or in a container they have open), at
 * the pace of where it has been since: fridge food left out goes off COLD_OUT times faster, other food keeps twice as
 * long in a fridge, and a freezer all but stops the clock.
 *
 * @param age    days old, at the moment `seen`
 * @param seen   game time it was last brought up to date
 * @param where  OUT, FRIDGE or FREEZER: where it has been since `seen`
 * @param fresh  days until stale (for cold food, days in a fridge)
 * @param rot    days until rotten
 * @param cold   needs a fridge
 * @param opened a can that has been opened (it shows in the name, and the can now goes off)
 */
public record Spoil(float age, long seen, int where, float fresh, float rot, boolean cold, boolean opened) {
    public static final int OUT = 0, FRIDGE = 1, FREEZER = 2;
    public static final int FRESH = 0, STALE = 1, ROTTEN = 2;
    public static final float COLD_OUT = 3f, FRIDGE_KEEPS = 0.5f, FREEZER_KEEPS = 0.03f;
    /** A day is a Minecraft day. */
    public static final float DAY = 24000f;
    /** An opened can lasts about as long as leftovers. */
    public static final float OPEN_FRESH = 2f, OPEN_ROT = 4f;

    public static final Codec<Spoil> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("age").forGetter(Spoil::age),
            Codec.LONG.fieldOf("seen").forGetter(Spoil::seen),
            Codec.INT.optionalFieldOf("where", OUT).forGetter(Spoil::where),
            Codec.FLOAT.fieldOf("fresh").forGetter(Spoil::fresh),
            Codec.FLOAT.fieldOf("rot").forGetter(Spoil::rot),
            Codec.BOOL.optionalFieldOf("cold", false).forGetter(Spoil::cold),
            Codec.BOOL.optionalFieldOf("opened", false).forGetter(Spoil::opened)
    ).apply(i, Spoil::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, Spoil> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Spoil::age,
            ByteBufCodecs.VAR_LONG, Spoil::seen,
            ByteBufCodecs.VAR_INT, Spoil::where,
            ByteBufCodecs.FLOAT, Spoil::fresh,
            ByteBufCodecs.FLOAT, Spoil::rot,
            ByteBufCodecs.BOOL, Spoil::cold,
            ByteBufCodecs.BOOL, Spoil::opened,
            Spoil::new);

    public static final DataComponentType<Spoil> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("spoil"), DataComponentType.<Spoil>builder()
                    .persistent(CODEC).networkSynchronized(STREAM_CODEC).ignoreSwapAnimation().build());

    /** How fast the clock runs where it has been. */
    public static float pace(int where, boolean cold) {
        return switch (where) {
            case FREEZER -> FREEZER_KEEPS;
            case FRIDGE -> cold ? 1f : FRIDGE_KEEPS;
            default -> cold ? COLD_OUT : 1f;
        };
    }

    public int state() {
        return age >= rot ? ROTTEN : age >= fresh ? STALE : FRESH;
    }

    /** Brought up to now, and from now on kept where `next` says. */
    public Spoil at(long now, int next) {
        float days = Math.max(0, now - seen) / DAY * pace(where, cold);
        return new Spoil(age + days, now, next, fresh, rot, cold, opened);
    }

    /** The clock a stack starts with, or null when it never goes off. */
    public static @Nullable Spoil start(ItemStack stack, long now, int where, float age) {
        LootSpec spec = LootItems.spec(stack);
        if (spec == null || spec.food() == null || !spec.food().perishes()) {
            return null;
        }
        LootSpec.Food food = spec.food();
        return new Spoil(age, now, where, food.fresh(), food.rot(), food.is(LootSpec.Food.COLD), false);
    }

    /**
     * Brings a stack's clock up to now (starting one if it should have one) and renames it when it turns. Returns
     * true when the stack changed.
     */
    public static boolean update(ItemStack stack, long now, int where) {
        if (stack.isEmpty()) {
            return false;
        }
        Spoil spoil = stack.get(TYPE);
        if (spoil == null) {
            spoil = start(stack, now, where, 0f);
            if (spoil == null) {
                return false;
            }
            stack.set(TYPE, spoil);
            name(stack, spoil);
            return true;
        }
        int before = spoil.state();
        Spoil next = spoil.at(now, where);
        // write only when it turns or changes place, so stacks aren't rewritten every half-second
        if (next.state() != before || next.where() != spoil.where()) {
            stack.set(TYPE, next);
            name(stack, next);
            return true;
        }
        return false;
    }

    /** "Stale Milk", "Rotten Opened Canned Beans". */
    public static void name(ItemStack stack, Spoil spoil) {
        Component base = stack.getItem().components().get(DataComponents.ITEM_NAME);
        if (base == null) {
            return;
        }
        Component name = spoil.opened() ? Component.literal("Opened ").append(base) : base.copy();
        stack.set(DataComponents.ITEM_NAME, switch (spoil.state()) {
            case STALE -> Component.literal("Stale ").append(name);
            case ROTTEN -> Component.literal("Rotten ").append(name);
            default -> name;
        });
    }

    public static void register() {
        // loading the class registers the component
    }
}
