package com.zomboidcraft.combat.rolls;

import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.WeaponSpecs;
import com.zomboidcraft.combat.Weapons;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * How rare a weapon model is, which decides where it can turn up. Each model has one place on the ladder, and the name
 * takes the colour in its tooltip; the model itself never glows. The names are how collectors talk ("Grail" is the
 * piece they hunt for years), the colours real materials: bone, olive, steel blue, oxblood, brass.
 */
public enum Rarity {
    COMMON("Common", 0xE8DFC8),
    SCARCE("Scarce", 0xA8BC6E),
    RARE("Rare", 0x7FAEDB),
    PRIZED("Prized", 0xD0675E),
    GRAIL("Grail", 0xE2B84C);

    private final String label;
    private final int rgb;

    Rarity(String label, int rgb) {
        this.label = label;
        this.rgb = rgb;
    }

    public String label() {
        return label;
    }

    public int rgb() {
        return rgb;
    }

    /** The weapon's rarity, or null for anything that isn't a weapon. */
    public static @Nullable Rarity of(ItemStack stack) {
        GunType gun = GunType.of(stack);
        if (gun != null) {
            return of(gun);
        }
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        return spec == null ? null : of(spec);
    }

    public static Rarity of(GunType type) {
        return switch (type) {
            case PISTOL, SHOTGUN, RIMFIRE -> COMMON;
            case REVOLVER, DOUBLE_BARREL, HUNTING_RIFLE, SAWN_OFF -> SCARCE;
            case ASSAULT_RIFLE, MAGNUM -> RARE;
            case SMG -> PRIZED;
        };
    }

    public static Rarity of(WeaponSpecs.WeaponSpec spec) {
        return switch (spec.name()) {
            case "machete", "fire_axe", "sledgehammer", "spiked_bat" -> SCARCE;
            case "katana" -> RARE;
            default -> COMMON;
        };
    }
}
