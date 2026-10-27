package com.zomboidcraft.zombie.population;

import com.zomboidcraft.world.county.Place;

/**
 * How many dead each place starts with, by what it is and how big that kind is, and how many of them are indoors.
 * The numbers are the table Josiah approved on 2026-10-27 ("Zombie population: how many, where"); the census shares
 * each out over the place's real floor space, so a tall tower gets far more than a car park.
 */
public final class Budgets {
    /** A place's dead: how many, and the share of them indoors. */
    public record Budget(int total, double indoors) {
    }

    /** The open country: dead per standing spot outside every place (about ten per 512 x 512), more on the roads. */
    public static final double WILD = 10.0 / (512 * 512);
    public static final double WILD_ROAD = 5;
    public static final double WILD_OFF_ROAD = 0.7;

    /**
     * Grayhaven island, the county's biggest landmark: one pool shared by every place on it, whatever its type,
     * over all their floor space together (Population.island), so a skyscraper gets its share and a park its few.
     */
    public static final Budget ISLAND = new Budget(20000, 0.85);

    public static Budget of(Place p) {
        String v = p.variant;
        return switch (p.type.name()) {
            case "DOWNTOWN" -> new Budget(switch (v) {
                case "metro" -> 3000;
                case "city" -> 2000;
                default -> 1400;
            }, 0.85);
            case "TOWN" -> new Budget(switch (v) {
                case "county_seat" -> 450;
                case "mill_town" -> 320;
                case "main_street" -> 250;
                default -> 80;
            }, 0.6);
            case "SUBURB" -> new Budget(switch (v) {
                case "estates" -> 240;
                case "old_grid" -> 220;
                case "cul_de_sac" -> 200;
                default -> 90;
            }, 0.65);
            case "PORT" -> new Budget(switch (v) {
                case "naval" -> 400;
                case "container" -> 200;
                case "industrial" -> 150;
                default -> 60;
            }, 0.45);
            case "MILITARY" -> new Budget(switch (v) {
                case "quarantine" -> 500;
                case "army_base" -> 300;
                default -> 100;
            }, 0.5);
            case "FARM" -> new Budget(switch (v) {
                case "homestead" -> 5;
                case "orchard" -> 8;
                default -> 12;
            }, 0.5);
            case "ROADSIDE" -> new Budget(switch (v) {
                case "school" -> 80;
                case "motel" -> 30;
                case "church", "trailer_lot" -> 25;
                case "warehouse" -> 20;
                case "diner" -> 15;
                default -> 10;
            }, 0.6);
            // the stadium: a FEMA shelter that fell, soldiers and patients by the hundred
            case "LANDMARK" -> new Budget(v.equals("stadium") ? 1200 : 150, 0.7);
            default -> new Budget(4, 0.3);
        };
    }

    /** Places where whole houses stand empty, the family gone (emptyHouses). */
    public static boolean houses(Place p) {
        String t = p.type.name();
        return t.equals("TOWN") || t.equals("SUBURB") || t.equals("FARM");
    }

    private Budgets() {
    }
}
