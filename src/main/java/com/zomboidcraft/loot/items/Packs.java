package com.zomboidcraft.loot.items;

import java.util.List;
import java.util.Map;

/**
 * What comes in a sealed pack or kit when it's opened, Zomboid style: a first aid kit holds the basics, a paramedic's
 * bag the serious kit, a six-pack six cans. Each line: item, least, most, and the chance it's there at all.
 */
public final class Packs {
    public record Line(String item, int min, int max, float chance) {
    }

    private static Line l(String item, int min, int max, float chance) {
        return new Line(item, min, max, chance);
    }

    public static final Map<String, List<Line>> CONTENTS = Map.of(
            "beer_six_pack", List.of(l("canned_beer", 6, 6, 1f)),
            "survival_bucket", List.of(l("mre", 3, 5, 1f), l("ration_bar", 3, 5, 1f), l("powdered_milk", 1, 1, 0.8f),
                    l("oats_box", 1, 2, 0.8f), l("water_purification_tabs", 1, 1, 0.5f)),
            "first_aid_kit", List.of(l("bandage", 2, 3, 1f), l("adhesive_bandages", 1, 1, 0.9f),
                    l("antiseptic_wipes", 1, 1, 0.8f), l("gauze_pads", 1, 1, 0.7f), l("medical_tape", 1, 1, 0.7f),
                    l("painkillers", 1, 1, 0.4f), l("tweezers", 1, 1, 0.4f), l("burn_ointment", 1, 1, 0.3f),
                    l("cold_pack", 1, 1, 0.3f)),
            "trauma_kit", List.of(l("sterile_bandage", 2, 4, 1f), l("gauze_pads", 1, 2, 1f), l("medical_tape", 1, 1, 1f),
                    l("tourniquet", 1, 1, 0.8f), l("splint", 1, 1, 0.6f), l("surgical_gloves", 1, 1, 0.9f),
                    l("disinfectant", 1, 1, 0.7f), l("saline_bag", 1, 1, 0.35f), l("scalpel", 1, 1, 0.3f),
                    l("adrenaline_pen", 1, 1, 0.15f)),
            "ifak", List.of(l("sterile_bandage", 1, 2, 1f), l("tourniquet", 1, 1, 0.9f), l("gauze_pads", 1, 1, 0.9f),
                    l("medical_tape", 1, 1, 0.7f), l("painkillers", 1, 1, 0.5f)),
            "field_surgery_kit", List.of(l("suture_kit", 2, 3, 1f), l("scalpel", 1, 1, 1f), l("tweezers", 1, 1, 1f),
                    l("surgical_gloves", 1, 1, 1f), l("disinfectant", 1, 2, 1f), l("sterile_bandage", 2, 4, 1f),
                    l("antibiotics", 1, 1, 0.6f), l("morphine_autoinjector", 1, 1, 0.4f)));

    public static boolean has(String id) {
        return CONTENTS.containsKey(id);
    }

    private Packs() {
    }
}
