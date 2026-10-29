package com.zomboidcraft.combat.throwing;

import com.zomboidcraft.ZcConfig;

/**
 * The things you can throw and what each does when it lands. Numbers live in ZcConfig.Throwing.
 *
 * @param speed      share of a full throw's speed: heavy things don't go as far
 * @param bounce     how much speed it keeps off a hard landing (0 stops dead or breaks)
 * @param fused      the fuse burns (or the pin is out) from the moment the wind-up starts
 * @param keeps      found again where it comes to rest, to pick up and throw again
 */
public enum ThrowKind {
    BOTTLE("glass_bottle", 1.0, 0.0, false, false),
    CAN("tin_can", 1.0, 0.35, false, true),
    BRICK("brick", 0.75, 0.15, false, true),
    FIRECRACKERS("firecrackers", 0.95, 0.2, false, false),
    FLARE("road_flare", 0.95, 0.25, false, false),
    ALARM_CLOCK("alarm_clock", 0.85, 0.2, false, true),
    MOLOTOV("molotov", 0.9, 0.0, false, false),
    SMOKE("smoke_grenade", 0.85, 0.25, false, false),
    FLASHBANG("flashbang", 0.85, 0.25, true, false),
    FRAG("frag_grenade", 0.85, 0.25, true, false);

    public final String id;
    public final double speed, bounce;
    public final boolean fused, keeps;

    ThrowKind(String id, double speed, double bounce, boolean fused, boolean keeps) {
        this.id = id;
        this.speed = speed;
        this.bounce = bounce;
        this.fused = fused;
        this.keeps = keeps;
    }

    /** Breaks the moment it hits anything. */
    public boolean shatters() {
        return this == BOTTLE || this == MOLOTOV;
    }

    /** Ticks from the pin coming out (or the fuse lit) to the bang; 0 for those that don't go off by themselves. */
    public int fuse() {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        return switch (this) {
            case FLASHBANG -> c.flashbangFuse;
            case FRAG -> c.fragFuse;
            default -> 0;
        };
    }

    /** How far zombies hear it land or go off. */
    public double noise() {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        return switch (this) {
            case BOTTLE -> c.bottleNoise;
            case CAN -> c.canNoise;
            case BRICK -> c.brickNoise;
            case FIRECRACKERS -> c.firecrackerNoise;
            case FLARE -> c.flareNoise;
            case ALARM_CLOCK -> c.alarmNoise;
            case MOLOTOV -> c.molotovNoise;
            case SMOKE -> c.smokeNoise;
            case FLASHBANG -> c.flashbangNoise;
            case FRAG -> c.fragNoise;
        };
    }

    /** What a direct hit does to whoever it strikes. */
    public double hitDamage() {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        return switch (this) {
            case BOTTLE, MOLOTOV -> c.bottleDamage;
            case BRICK -> c.brickDamage;
            default -> c.canDamage;
        };
    }
}
