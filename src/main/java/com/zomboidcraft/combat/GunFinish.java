package com.zomboidcraft.combat;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * The finish a gun came in: a plain working gun, or a better one (colour case hardening and figured walnut, an engraved
 * coin finish and exhibition walnut; nickel and rosewood, gold-inlaid royal blue). Each gun lists its own, plainest first, with how often each turns up (in /zc
 * config under finishes). A found gun's finish is rolled the first time a player has it; the item model shows the
 * finish by the model data string after the part keys (see Attachments.writeModelData).
 */
public final class GunFinish {
    /** The finish's key, as the item model and the generator name it. */
    public static final DataComponentType<String> TYPE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
            ZomboidCraft.id("finish"), DataComponentType.<String>builder()
                    .persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

    /** One finish: its key, how rare it is (0 plain, 1 better, 2 fine), and its weight in the settings. */
    public record Finish(String key, int grade, ToIntFunction<ZcConfig.Finishes> weight) {
    }

    private static final List<Finish> DOUBLE_BARREL = List.of(
            new Finish("field", 0, f -> f.doubleBarrelField),
            new Finish("case_hardened", 1, f -> f.doubleBarrelCaseHardened),
            new Finish("engraved", 2, f -> f.doubleBarrelEngraved));
    private static final List<Finish> REVOLVER = List.of(
            new Finish("service", 0, f -> f.revolverService),
            new Finish("nickel", 1, f -> f.revolverNickel),
            new Finish("engraved", 2, f -> f.revolverEngraved));
    private static final List<Finish> MAGNUM = List.of(
            new Finish("blued", 0, f -> f.magnumBlued),
            new Finish("nickel", 1, f -> f.magnumNickel));

    /** The finishes a gun comes in, the plainest first; none for a gun that has only the one look. */
    public static List<Finish> of(GunType type) {
        return switch (type) {
            case DOUBLE_BARREL -> DOUBLE_BARREL;
            case REVOLVER -> REVOLVER;
            case MAGNUM -> MAGNUM;
            default -> List.of();
        };
    }

    /** The gun's finish key, or null for a gun that doesn't come in finishes (or hasn't had one rolled). */
    public static String key(ItemStack gun) {
        GunType type = GunType.of(gun);
        if (type == null || of(type).isEmpty()) {
            return null;
        }
        String key = gun.get(TYPE);
        return key != null ? key : of(type).get(0).key();
    }

    /** Gives a gun with no finish yet one, by the settings' odds. Returns true if it did. */
    public static boolean roll(ItemStack gun, RandomSource random) {
        GunType type = GunType.of(gun);
        if (type == null || gun.has(TYPE) || of(type).isEmpty()) {
            return false;
        }
        List<Finish> list = of(type);
        ZcConfig.Finishes cfg = ZcConfig.get().finishes;
        int total = 0;
        for (Finish f : list) {
            total += Math.max(0, f.weight().applyAsInt(cfg));
        }
        Finish pick = list.get(0);
        if (total > 0) {
            int r = random.nextInt(total);
            for (Finish f : list) {
                r -= Math.max(0, f.weight().applyAsInt(cfg));
                if (r < 0) {
                    pick = f;
                    break;
                }
            }
        }
        set(gun, pick.key());
        return true;
    }

    public static void set(ItemStack gun, String key) {
        gun.set(TYPE, key);
        Attachments.refreshModel(gun);
    }

    /** The tooltip line: the finish's name, coloured by how rare it is; empty for a gun that has only the one look. */
    public static Component line(ItemStack gun) {
        GunType type = GunType.of(gun);
        String key = key(gun);
        if (type == null || key == null) {
            return Component.empty();
        }
        int grade = of(type).stream().filter(f -> f.key().equals(key)).mapToInt(Finish::grade).findFirst().orElse(0);
        ChatFormatting colour = grade == 0 ? ChatFormatting.GRAY : grade == 1 ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        return Component.literal("Finish: ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("finish.zomboidcraft." + type.id() + "." + key).withStyle(colour));
    }

    public static void register() {
        // loading the class registers the component
    }

    private GunFinish() {
    }
}
