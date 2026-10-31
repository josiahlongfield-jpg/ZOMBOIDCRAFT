package com.zomboidcraft.survival;

import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.loot.items.LootItems;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.Body.Part;
import com.zomboidcraft.survival.Body.Region;
import com.zomboidcraft.survival.Body.Wound;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What each medical item does when used (from the hand or the quick-use wheel), as a timed action; and the ones
 * that work just by being carried or held. The bandage and splint keep their own items (Treatment).
 */
public final class MedSpecs {
    public enum Kind {
        /** Wipes a wound clean: no more dirt, and some of the infection. */
        CLEAN,
        /** Covers a wound (bandaged); the arg says how: sterile, small, pack, rag, seal, wrap. */
        DRESS,
        /** Closes a deep wound (stitches, staples) or a small cut (butterfly closures). */
        STITCH,
        /** Pulls out what's lodged; surgery kits also stitch. */
        EXTRACT,
        SPLINT,
        TOURNIQUET,
        /** A pill, drink or shot: arg names it (see {@link #drug}). */
        DRUG,
        /** A cream on one part for a while. */
        SALVE,
        /** Saline or blood through an IV kit. */
        BLOOD,
        /** Reads you (or the room). */
        DIAG,
        TEETH,
        TRIAL,
        GIVE_BLOOD,
        /** A book: once read, its flag stays with you. */
        READ,
        /** Works carried or held; using it only says what it does. */
        PASSIVE,
        /** For getting a downed friend back up (Downs); on yourself, adrenaline is a pick-me-up. */
        REVIVE
    }

    /** ticks: how long it takes at First Aid 0; uses: 0 for one-shot items, else how many uses before it's spent. */
    public record Med(Kind kind, int ticks, int uses, String arg) {
    }

    private static final Map<String, Med> SPECS = new HashMap<>();

    private static void m(String id, Kind kind, int ticks, int uses, String arg) {
        SPECS.put(id, new Med(kind, ticks, uses, arg));
    }

    static {
        // cleaning
        m("disinfectant", Kind.CLEAN, 50, 10, "250");
        m("rubbing_alcohol", Kind.CLEAN, 50, 8, "250");
        m("peroxide", Kind.CLEAN, 50, 8, "300");
        m("iodine", Kind.CLEAN, 50, 10, "300");
        m("antiseptic_wipes", Kind.CLEAN, 40, 0, "150");
        m("cotton_swabs", Kind.CLEAN, 30, 0, "0");
        // dressings
        m("sterile_bandage", Kind.DRESS, 70, 0, "sterile");
        m("adhesive_bandages", Kind.DRESS, 30, 0, "small");
        m("gauze_pads", Kind.DRESS, 60, 0, "pack");
        m("rag", Kind.DRESS, 80, 0, "rag");
        m("ripped_sheet", Kind.DRESS, 80, 0, "rag");
        m("hemostatic_gauze", Kind.DRESS, 50, 0, "hemostatic");
        m("chest_seal", Kind.DRESS, 50, 0, "seal");
        m("elastic_wrap", Kind.DRESS, 60, 0, "wrap");
        // closing
        m("suture_kit", Kind.STITCH, 160, 5, "suture");
        m("wound_stapler", Kind.STITCH, 40, 10, "staple");
        m("butterfly_closures", Kind.STITCH, 40, 0, "butterfly");
        // extracting
        m("tweezers", Kind.EXTRACT, 120, 0, "tweezers");
        m("scalpel", Kind.EXTRACT, 120, 0, "scalpel");
        m("surgical_set", Kind.EXTRACT, 200, 0, "surgery");
        m("antique_surgeon_set", Kind.EXTRACT, 200, 0, "surgery");
        m("forward_surgical_case", Kind.EXTRACT, 300, 0, "field");
        // breaks
        m("moldable_splint", Kind.SPLINT, 100, 10, "any");
        m("finger_splint", Kind.SPLINT, 80, 0, "arm");
        m("neck_collar", Kind.SPLINT, 80, 0, "core");
        m("arm_sling", Kind.SPLINT, 60, 0, "sling");
        m("tourniquet", Kind.TOURNIQUET, 30, 0, "");
        // drugs
        for (String d : new String[]{"painkillers", "strong_painkillers", "morphine_autoinjector", "antibiotics", "vet_meds",
                "sleeping_pills", "beta_blockers", "antidepressants", "vitamins", "cold_medicine", "cough_syrup", "antacids",
                "anti_nausea", "allergy_pills", "nasal_spray", "eye_drops", "inhaler", "antivirals", "local_anaesthetic",
                "rehydration_salts", "glucose_gel", "cold_pack", "oxygen_cylinder"}) {
            m(d, Kind.DRUG, d.equals("morphine_autoinjector") ? 10 : d.equals("oxygen_cylinder") ? 100 : 30,
                    switch (d) {
                        case "nasal_spray" -> 8;
                        case "eye_drops", "cough_syrup", "oxygen_cylinder" -> 5;
                        case "inhaler" -> 6;
                        default -> 0;
                    }, d);
        }
        m("burn_ointment", Kind.SALVE, 40, 0, "burn");
        m("antibiotic_cream", Kind.SALVE, 40, 0, "infection");
        m("hydrocortisone", Kind.SALVE, 40, 0, "skin");
        m("burn_kit", Kind.SALVE, 100, 0, "burnkit");
        m("saline_bag", Kind.BLOOD, 300, 0, "200");
        m("blood_bag", Kind.BLOOD, 400, 0, "500");
        m("thermometer", Kind.DIAG, 60, 0, "temp");
        m("blood_pressure_cuff", Kind.DIAG, 60, 0, "blood");
        m("pulse_oximeter", Kind.DIAG, 30, 0, "oxygen");
        m("stethoscope", Kind.DIAG, 60, 0, "listen");
        m("ultrasound", Kind.DIAG, 100, 0, "scan");
        m("dental_kit", Kind.TEETH, 200, 3, "");
        m("trial_vials", Kind.TRIAL, 30, 0, "");
        m("blood_typing_kit", Kind.GIVE_BLOOD, 400, 10, "");
        m("field_medic_manual", Kind.READ, 400, 0, "Improvised dressings no longer dirty a wound; splints and tourniquets go on faster.");
        m("drug_guide", Kind.READ, 400, 0, "Every drug you take lasts a quarter longer, and stacking painkillers won't make you sick.");
        m("airway_kit", Kind.REVIVE, 200, 0, "");
        m("bag_valve_mask", Kind.REVIVE, 160, 0, "");
        m("adrenaline_pen", Kind.REVIVE, 20, 0, "");
        m("portable_defib", Kind.REVIVE, 120, 10, "");
        for (String p : new String[]{"crutches", "walking_cane", "knee_brace", "medical_scissors", "pill_organiser",
                "trauma_bag", "surgical_gloves", "face_masks", "hot_water_bottle", "iv_kit", "stretcher", "wheelchair"}) {
            m(p, Kind.PASSIVE, 0, p.equals("surgical_gloves") || p.equals("face_masks") ? 10 : p.equals("iv_kit") ? 5 : 0, "");
        }
    }

    public static Med spec(String id) {
        return SPECS.get(id);
    }

    public static boolean has(String id) {
        return SPECS.containsKey(id);
    }

    // ------------------------------------------------------------ carried and held

    private static boolean is(ItemStack stack, String id) {
        Item item = LootItems.item(id);
        return item != null && stack.is(item);
    }

    /** Anywhere in your inventory. */
    public static boolean carries(Player player, String id) {
        Item item = LootItems.item(id);
        return item != null && player.getInventory().contains(new ItemStack(item));
    }

    /** In either hand. */
    public static boolean holds(Player player, String id) {
        return is(player.getMainHandItem(), id) || is(player.getOffhandItem(), id);
    }

    /** Medical scissors cut clothes away and a trauma bag keeps it all to hand; First Aid speeds the rest. */
    static int ticks(Player player, int base) {
        double k = Math.max(0.6, 1 - 0.04 * Skills.level(player, Skill.FIRST_AID));
        if (carries(player, "medical_scissors")) k *= 0.7;
        if (carries(player, "trauma_bag")) k *= 0.75;
        return Math.max(10, (int) Math.round(base * k));
    }

    /** Takes a use from the first matching stack: a durability point, or one off the stack. */
    static boolean spend(Player player, String id) {
        Item item = LootItems.item(id);
        Slot slot = item == null ? null : Treatment.find(player, s -> s.is(item));
        if (slot == null) return false;
        if (player.hasInfiniteMaterials()) return true;
        ItemStack stack = slot.getItem();
        if (stack.isDamageableItem()) {
            stack.setDamageValue(stack.getDamageValue() + 1);
            if (stack.getDamageValue() >= stack.getMaxDamage()) stack.shrink(1);
        } else {
            stack.shrink(1);
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return true;
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------ using one

    /** Starts using a medical item. False when there's nothing for it to do (the reason is shown). */
    public static boolean use(ServerPlayer player, String id) {
        Med med = SPECS.get(id);
        if (med == null) return false;
        Body body = Body.get(player);
        Map<String, Integer> m = Ailments.edit(player);
        return switch (med.kind()) {
            case CLEAN -> onPart(player, id, med, "Cleaning", (r, p) -> p.wounded()
                    && (Medicine.get(m, "dirty", r) > 0 || Medicine.get(m, "rag", r) > 0 || Medicine.get(m, "infect", r) > 0 && !med.arg().equals("0")),
                    "No wound needs cleaning.");
            case DRESS -> onPart(player, id, med, "Dressing", dressNeed(med.arg(), m), "Nothing needs this dressing.");
            case STITCH -> onPart(player, id, med, med.arg().equals("staple") ? "Stapling" : "Closing", (r, p) -> p.wounded()
                    && Medicine.get(m, "stitched", r) == 0 && (med.arg().equals("butterfly")
                    ? Medicine.get(m, "deep", r) == 0 && (p.wound() == Wound.LACERATION || p.wound() == Wound.SCRATCH)
                    : Medicine.get(m, "deep", r) > 0), med.arg().equals("butterfly") ? "No small cut to close." : "No deep wound to close.");
            case EXTRACT -> extract(player, id, med, m);
            case SPLINT -> onPart(player, id, med, med.arg().equals("sling") ? "Fitting a sling to" : "Splinting", (r, p) -> switch (med.arg()) {
                case "arm" -> r.arm() && p.fractured() && !p.splinted();
                case "core" -> (r == Region.HEAD || r == Region.TORSO) && p.fractured() && !p.splinted();
                case "sling" -> r.arm() && p.fractured() && Medicine.get(m, "sling", r) == 0;
                default -> p.fractured() && !p.splinted();
            }, "Nothing broken that this would help.");
            case TOURNIQUET -> onPart(player, id, med, "Tying off", (r, p) -> (r.arm() || r.leg()) && p.bleeding()
                    && Medicine.get(m, "tq", r) == 0, "No limb is bleeding.");
            case SALVE -> onPart(player, id, med, "Treating", (r, p) -> p.wounded() && switch (med.arg()) {
                case "burn", "burnkit" -> p.wound() == Wound.BURN;
                case "skin" -> p.wound() == Wound.SCRATCH || p.wound() == Wound.BITE;
                default -> true;
            }, "Nothing this would help.");
            case DRUG -> whole(player, id, med, drugNeed(med.arg(), m, player));
            case BLOOD -> blood(player, id, med, m);
            case DIAG -> whole(player, id, med, null);
            case TEETH -> whole(player, id, med, m.getOrDefault("tooth", 0) > 0 ? null : "Your teeth are fine.");
            case TRIAL -> whole(player, id, med, SurvivalStats.get(player).infected() ? null : "You have nothing for this to slow.");
            case GIVE_BLOOD -> whole(player, id, med, !carries(player, "iv_kit") ? "You need an IV starter kit."
                    : m.getOrDefault("blood_lost", 0) > 200 ? "You've too little blood to spare." : null);
            case READ -> whole(player, id, med, m.getOrDefault("read:" + id, 0) > 0 ? "You've read this already." : null);
            case REVIVE -> id.equals("adrenaline_pen") ? whole(player, id, med, null) : passive(player, id);
            case PASSIVE -> passive(player, id);
        };
    }

    private static boolean passive(ServerPlayer player, String id) {
        say(player, switch (id) {
            case "crutches" -> "Held, crutches take most of a broken leg's limp away.";
            case "walking_cane" -> "Held, a cane eases a bad leg.";
            case "knee_brace" -> "Carried, it halves the slowdown from a leg injury.";
            case "medical_scissors" -> "Carried, they make every treatment quicker.";
            case "pill_organiser" -> "Carried, every pill you take lasts longer.";
            case "trauma_bag" -> "Carried, every treatment is quicker.";
            case "surgical_gloves" -> "Carried, stitches and surgery never dirty a wound.";
            case "face_masks" -> "Carried, you're less likely to catch a cold and your coughs carry less.";
            case "hot_water_bottle" -> "Carried to bed, it eases pain while you sleep.";
            case "iv_kit" -> "Needed to give a saline or blood bag.";
            case "stretcher", "wheelchair" -> "Hold it and press the interact key on a downed friend to move them.";
            default -> "Use it on a downed friend: hold it and press the interact key on them.";
        });
        return false;
    }

    private static BiPredicate<Region, Part> dressNeed(String how, Map<String, Integer> m) {
        return switch (how) {
            case "small" -> (r, p) -> p.wounded() && !p.bandaged() && Medicine.get(m, "deep", r) == 0 && p.wound() != Wound.BITE;
            case "pack" -> (r, p) -> p.wounded() && Medicine.get(m, "packed", r) == 0 && (Medicine.get(m, "deep", r) > 0 || !p.bandaged());
            case "hemostatic" -> (r, p) -> p.bleeding();
            case "seal" -> (r, p) -> r == Region.TORSO && p.wounded() && Medicine.get(m, "sealed", r) == 0;
            case "wrap" -> (r, p) -> (r.arm() || r.leg()) && !p.fine() && Medicine.get(m, "wrap", r) == 0;
            default -> (r, p) -> p.wounded() && !p.bandaged();
        };
    }

    /** The part a treatment goes to: bleeding first, then the worst. */
    private static Region pick(Body body, BiPredicate<Region, Part> needs) {
        Region best = null;
        int score = -1;
        for (Region r : Region.values()) {
            Part p = body.part(r);
            if (!needs.test(r, p)) continue;
            int s = (p.bleeding() ? 10 : 0) + p.severity() * 2 + p.wound().ordinal();
            if (s > score) {
                score = s;
                best = r;
            }
        }
        return best;
    }

    private static boolean onPart(ServerPlayer player, String id, Med med, String verb, BiPredicate<Region, Part> needs, String none) {
        Region r = pick(Body.get(player), needs);
        if (r == null) {
            say(player, none);
            return false;
        }
        int ticks = ticks(player, med.ticks());
        if ((med.kind() == Kind.SPLINT || med.kind() == Kind.TOURNIQUET) && Ailments.get(player, "read:field_medic_manual") > 0) ticks = ticks * 2 / 3;
        sound(player, 1.1f);
        TimedActions.start(player, "med:" + id + ":" + r, verb + " " + r.label, ticks, p -> {
            if (needs.test(r, Body.get(p).part(r)) && spend(p, id)) finishPart(p, id, med, r);
        });
        return true;
    }

    private static boolean whole(ServerPlayer player, String id, Med med, String why) {
        if (why != null) {
            say(player, why);
            return false;
        }
        String label = switch (med.kind()) {
            case READ -> "Reading";
            case DIAG -> "Checking with the";
            case DRUG -> "Taking";
            default -> "Using";
        };
        TimedActions.start(player, "med:" + id, label + " " + LootItems.item(id).getName(new ItemStack(LootItems.item(id))).getString().toLowerCase(java.util.Locale.ROOT),
                ticks(player, med.ticks()), p -> {
                    if (med.kind() == Kind.DIAG || med.kind() == Kind.READ || spend(p, id)) finishWhole(p, id, med);
                });
        return true;
    }

    private static void sound(ServerPlayer player, float pitch) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BANDAGE, SoundSource.PLAYERS, 0.6f, pitch);
    }

    private static void xp(ServerPlayer player, int amount) {
        Skills.addXp(player, Skill.FIRST_AID, Ailments.get(player, "read:field_medic_manual") > 0 ? amount * 3 / 2 : amount);
    }

    private static void finishPart(ServerPlayer player, String id, Med med, Region r) {
        Body body = Body.get(player);
        Part p = body.part(r);
        Map<String, Integer> m = Ailments.edit(player);
        boolean gloves = carries(player, "surgical_gloves");
        String done;
        switch (med.kind()) {
            case CLEAN -> {
                m.remove(Medicine.k("dirty", r));
                m.remove(Medicine.k("rag", r));
                Medicine.add(m, Medicine.k("infect", r), -Integer.parseInt(med.arg()), Medicine.MAX);
                if (!id.equals("cotton_swabs")) Medicine.add(m, "sting", id.equals("peroxide") ? 30 : 15, 60);
                done = "You clean the wound on your " + r.label + ".";
                xp(player, 5);
            }
            case DRESS -> {
                boolean bandage = true, bleed = p.bleeding();
                switch (med.arg()) {
                    case "sterile" -> m.remove(Medicine.k("dirty", r));
                    case "rag" -> {
                        if (Ailments.get(player, "read:field_medic_manual") == 0) m.put(Medicine.k("rag", r), 1);
                    }
                    case "pack" -> {
                        m.put(Medicine.k("packed", r), 1);
                        // a pad on its own falls away; taped down it's a dressing too
                        bandage = p.bandaged() || spend(player, "medical_tape");
                    }
                    case "hemostatic" -> {
                        m.put(Medicine.k("packed", r), 1);
                        bleed = false;
                        Medicine.add(m, "blood_lost", -50, Medicine.MAX);
                    }
                    case "seal" -> {
                        m.put(Medicine.k("sealed", r), 1);
                        m.put(Medicine.k("packed", r), 1);
                        bleed = false;
                    }
                    case "wrap" -> {
                        m.put(Medicine.k("wrap", r), 1);
                        bandage = p.bandaged();
                    }
                    default -> {
                    }
                }
                if (bandage && p.wounded()) bleed = false;
                body = body.with(r, new Part(p.wound(), p.woundLeft(), bleed, bandage && p.wounded(), p.fractureLeft(), p.splinted()));
                done = "You dress your " + r.label + ".";
                xp(player, 8);
            }
            case STITCH -> {
                m.put(Medicine.k("stitched", r), 1);
                if (!med.arg().equals("staple") && Ailments.get(player, "anaes") == 0) Medicine.add(m, "sting", 40, 60);
                if (!gloves && med.arg().equals("suture") && player.getRandom().nextFloat() < 0.2f) m.put(Medicine.k("dirty", r), 1);
                if (gloves) spend(player, "surgical_gloves");
                body = body.with(r, new Part(p.wound(), p.woundLeft(), false, p.bandaged(), p.fractureLeft(), p.splinted()));
                done = med.arg().equals("butterfly") ? "You close the cut on your " + r.label + "." : "You close the wound on your " + r.label + ".";
                xp(player, med.arg().equals("suture") ? 20 : 8);
            }
            case SPLINT -> {
                if (med.arg().equals("sling")) {
                    m.put(Medicine.k("sling", r), 1);
                    done = "You rest your " + r.label + " in a sling.";
                } else {
                    body = body.with(r, new Part(p.wound(), p.woundLeft(), p.bleeding(), p.bandaged(), p.fractureLeft(), true));
                    m.put(Medicine.k("splintkind", r), switch (med.arg()) {
                        case "arm" -> WornMedical.FINGER_SPLINT;
                        case "core" -> WornMedical.COLLAR;
                        default -> WornMedical.MOLDABLE_SPLINT;
                    });
                    done = "You splint your " + r.label + ".";
                }
                xp(player, 12);
            }
            case TOURNIQUET -> {
                m.put(Medicine.k("tq", r), 600);
                body = body.with(r, new Part(p.wound(), p.woundLeft(), false, p.bandaged(), p.fractureLeft(), p.splinted()));
                done = "You tie off your " + r.label + ". It'll hold for ten minutes.";
                xp(player, 6);
            }
            case SALVE -> {
                switch (med.arg()) {
                    case "burn" -> m.put(Medicine.k("burnsalve", r), 1200);
                    case "burnkit" -> {
                        // every burn at once, dressed
                        for (Region o : Region.values()) {
                            Part q = body.part(o);
                            if (q.wound() == Wound.BURN) {
                                m.put(Medicine.k("burnsalve", o), 2400);
                                body = body.with(o, new Part(q.wound(), q.woundLeft(), false, true, q.fractureLeft(), q.splinted()));
                            }
                        }
                    }
                    case "infection" -> {
                        m.put(Medicine.k("salve", r), 1200);
                        Medicine.add(m, Medicine.k("infect", r), -200, Medicine.MAX);
                    }
                    default -> m.put(Medicine.k("salve", r), 1200);
                }
                done = "You treat your " + r.label + ".";
                xp(player, 5);
            }
            default -> done = null;
        }
        Body.set(player, body);
        Ailments.save(player, m);
        sound(player, 0.95f);
        if (done != null) player.sendOverlayMessage(Component.literal(done));
    }

    // ------------------------------------------------------------ extracting

    private static boolean extract(ServerPlayer player, String id, Med med, Map<String, Integer> m) {
        boolean surgery = med.arg().equals("surgery") || med.arg().equals("field");
        BiPredicate<Region, Part> needs = (r, p) -> p.wounded() && (Medicine.get(m, "lodged", r) > 0
                || surgery && Medicine.get(m, "deep", r) > 0 && Medicine.get(m, "stitched", r) == 0);
        Region r = pick(Body.get(player), needs);
        if (r == null) {
            say(player, surgery ? "Nothing needs surgery." : "Nothing's lodged in a wound.");
            return false;
        }
        sound(player, 0.9f);
        TimedActions.start(player, "med:" + id + ":" + r, (surgery ? "Operating on " : "Digging into ") + r.label,
                ticks(player, med.ticks()), p -> {
                    Map<String, Integer> now = Ailments.edit(p);
                    Body body = Body.get(p);
                    int firstAid = Skills.level(p, Skill.FIRST_AID);
                    Region[] parts = med.arg().equals("field") ? Region.values() : new Region[]{r};
                    boolean any = false;
                    for (Region o : parts) {
                        Part q = body.part(o);
                        if (!q.wounded()) continue;
                        if (Medicine.get(now, "lodged", o) > 0) {
                            float chance = surgery ? 1f : 0.4f + 0.08f * firstAid + (med.arg().equals("scalpel") ? 0.2f : 0f);
                            if (p.getRandom().nextFloat() < chance) {
                                now.remove(Medicine.k("lodged", o));
                                say(p, "You get it out of your " + o.label + ".");
                                Skills.addXp(p, Skill.FIRST_AID, 20);
                            } else {
                                body = body.with(o, new Part(q.wound(), q.woundLeft(), true, false, q.fractureLeft(), q.splinted()));
                                if (!carries(p, "surgical_gloves")) now.put(Medicine.k("dirty", o), 1);
                                say(p, "It slips. You'll have to try again.");
                                Skills.addXp(p, Skill.FIRST_AID, 5);
                            }
                            if (Ailments.get(p, "anaes") == 0) Medicine.add(now, "sting", surgery ? 30 : 50, 60);
                            any = true;
                        }
                        if (surgery && Medicine.get(now, "deep", o) > 0) {
                            now.put(Medicine.k("stitched", o), 1);
                            body = body.with(o, new Part(q.wound(), q.woundLeft(), false, true, q.fractureLeft(), q.splinted()));
                            any = true;
                        }
                    }
                    if (any && carries(p, "surgical_gloves")) spend(p, "surgical_gloves");
                    Body.set(p, body);
                    Ailments.save(p, now);
                });
        return true;
    }

    // ------------------------------------------------------------ blood

    private static boolean blood(ServerPlayer player, String id, Med med, Map<String, Integer> m) {
        int need = id.equals("blood_bag") ? 3 : 1;
        if (!carries(player, "iv_kit")) {
            say(player, "You need an IV starter kit to give this.");
            return false;
        }
        if (Skills.level(player, Skill.FIRST_AID) < need) {
            say(player, "You need First Aid " + need + " to set up a drip.");
            return false;
        }
        if (m.getOrDefault("blood_lost", 0) < 50) {
            say(player, "You haven't lost enough blood to need this.");
            return false;
        }
        TimedActions.start(player, "med:" + id, "Setting up a drip", ticks(player, med.ticks()), p -> {
            if (!spend(p, id) || !spend(p, "iv_kit")) return;
            Map<String, Integer> now = Ailments.edit(p);
            Medicine.add(now, "blood_lost", -Integer.parseInt(med.arg()), Medicine.MAX);
            Ailments.save(p, now);
            p.heal(id.equals("blood_bag") ? 6f : 2f);
            Skills.addXp(p, Skill.FIRST_AID, 15);
            say(p, "The colour comes back to your face.");
        });
        return true;
    }

    // ------------------------------------------------------------ drugs and the rest

    private static String drugNeed(String drug, Map<String, Integer> m, ServerPlayer player) {
        return switch (drug) {
            case "antacids", "anti_nausea" -> m.getOrDefault("nausea", 0) > 0 || drug.equals("anti_nausea") ? null : "Your stomach's fine.";
            case "cold_medicine", "cough_syrup" -> m.getOrDefault("cold", 0) > 0 ? null : "You don't have a cold.";
            case "inhaler", "oxygen_cylinder" -> m.getOrDefault("smoke", 0) > 0 || m.getOrDefault("blood_lost", 0) > 300 ? null : "You're breathing fine.";
            case "rehydration_salts" -> null;
            default -> null;
        };
    }

    /** Drug timers last a quarter longer with a pill organiser to hand, and again once you've read the drug guide. */
    private static int lasts(ServerPlayer player, int seconds) {
        double k = 1;
        if (carries(player, "pill_organiser")) k *= 1.25;
        if (Ailments.get(player, "read:drug_guide") > 0) k *= 1.25;
        return (int) Math.round(seconds * k);
    }

    private static void drug(ServerPlayer player, String drug, Map<String, Integer> m) {
        SurvivalStats stats = SurvivalStats.get(player);
        boolean careful = Ailments.get(player, "read:drug_guide") > 0;
        switch (drug) {
            case "painkillers" -> {
                if (m.getOrDefault("pk", 0) > 0 && !careful) Medicine.add(m, "nausea", 20, 100);
                m.put("pk", lasts(player, 900));
            }
            case "strong_painkillers" -> {
                if (m.getOrDefault("pk2", 0) > 0 && !careful) Medicine.add(m, "nausea", 30, 100);
                m.put("pk2", lasts(player, 900));
                SurvivalStats.set(player, stats.withFatigue(stats.fatigue() + 10));
            }
            case "morphine_autoinjector" -> m.put("morph", lasts(player, 600));
            case "antibiotics" -> m.put("antibio", lasts(player, 600));
            case "vet_meds" -> {
                m.put("antibio", lasts(player, 600));
                Medicine.add(m, "nausea", 25, 100);
            }
            case "sleeping_pills" -> {
                m.put("sleepy", lasts(player, 1200));
                SurvivalStats.set(player, stats.withFatigue(stats.fatigue() + 25));
            }
            case "beta_blockers" -> m.put("beta", lasts(player, 1200));
            case "antidepressants" -> m.put("antidep", lasts(player, 2400));
            case "vitamins" -> m.put("vit", lasts(player, 1800));
            case "cold_medicine" -> {
                m.put("nosneeze", lasts(player, 600));
                Medicine.add(m, "cold", -300, 99999);
            }
            case "cough_syrup" -> {
                Medicine.add(m, "cold", -600, 99999);
                m.put("nocough", lasts(player, 600));
                SurvivalStats.set(player, stats.withFatigue(stats.fatigue() + 8));
            }
            case "antacids" -> Medicine.add(m, "nausea", -30, 100);
            case "anti_nausea" -> {
                m.remove("nausea");
                m.put("settled", lasts(player, 900));
            }
            case "allergy_pills" -> m.put("nosneeze", lasts(player, 1200));
            case "nasal_spray" -> m.put("nosneeze", lasts(player, 900));
            case "eye_drops" -> m.put("eyes", lasts(player, 1200));
            case "inhaler" -> {
                Medicine.add(m, "smoke", -50, 100);
                m.put("nocough", lasts(player, 600));
            }
            case "oxygen_cylinder" -> {
                m.remove("smoke");
                Medicine.add(m, "blood_lost", -60, Medicine.MAX);
            }
            case "antivirals" -> m.put("antiviral", lasts(player, 1200));
            case "local_anaesthetic" -> m.put("anaes", lasts(player, 600));
            case "rehydration_salts" -> {
                m.remove("weak");
                Medicine.add(m, "nausea", -20, 100);
                SurvivalStats.set(player, stats.withThirst(Math.min(100, stats.thirst() + 40)));
            }
            case "glucose_gel" -> {
                SurvivalStats.set(player, stats.withFatigue(Math.max(0, stats.fatigue() - 15)));
                Needs.gain(player, 0.02f, 100f, 25f, 0f, 0f);
            }
            case "cold_pack" -> m.put("coldpack", 600);
            default -> {
            }
        }
    }

    private static void finishWhole(ServerPlayer player, String id, Med med) {
        Map<String, Integer> m = Ailments.edit(player);
        switch (med.kind()) {
            case DRUG -> {
                drug(player, med.arg(), m);
                say(player, "You take the " + LootItems.item(id).getName(new ItemStack(LootItems.item(id))).getString().toLowerCase(java.util.Locale.ROOT) + ".");
            }
            case DIAG -> {
                diagnose(player, med.arg(), m);
                Skills.addXp(player, Skill.FIRST_AID, 2);
            }
            case TEETH -> {
                m.remove("tooth");
                say(player, "You see to the bad tooth. The ache goes.");
            }
            case TRIAL -> {
                // each vial buys a quarter of the time the infection takes, then half that, and so on: never a cure
                int n = m.getOrDefault("trials", 0);
                SurvivalStats stats = SurvivalStats.get(player);
                int back = (int) (SurvivalStats.infectionDuration() * 0.25 / (1 << Math.min(n, 8)));
                SurvivalStats.set(player, stats.withInfection(Math.max(0, stats.infection() - back)));
                m.put("trials", n + 1);
                say(player, n == 0 ? "The vial burns going in. The fever eases, for now." : "It helps less than last time.");
            }
            case GIVE_BLOOD -> {
                if (!spend(player, "iv_kit")) return;
                Medicine.add(m, "blood_lost", 400, Medicine.MAX);
                Item bag = LootItems.item("blood_bag");
                if (bag != null && !player.getInventory().add(new ItemStack(bag))) player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), new ItemStack(bag));
                say(player, "You fill a blood bag. You feel light-headed.");
            }
            case READ -> {
                m.put("read:" + id, 1);
                say(player, med.arg());
                Skills.addXp(player, Skill.FIRST_AID, 30);
            }
            case REVIVE -> {
                // adrenaline on yourself: a burst of energy
                m.put("adren", 60);
                SurvivalStats stats = SurvivalStats.get(player);
                SurvivalStats.set(player, stats.withFatigue(Math.max(0, stats.fatigue() - 30)));
                Medicine.effect(player, net.minecraft.world.effect.MobEffects.SPEED, 0, 1200);
                say(player, "Your heart pounds.");
            }
            default -> {
            }
        }
        Ailments.save(player, m);
    }

    private static void diagnose(ServerPlayer player, String what, Map<String, Integer> m) {
        int lost = m.getOrDefault("blood_lost", 0), fever = m.getOrDefault("fever", 0), smoke = m.getOrDefault("smoke", 0);
        boolean cold = m.getOrDefault("cold", 0) > 0;
        switch (what) {
            case "temp" -> {
                double t = 36.8 + fever * 0.03 + (cold ? 0.5 : 0) + (SurvivalStats.get(player).infectionProgress() > 0.25f ? 1.2 : 0);
                say(player, String.format(java.util.Locale.ROOT, "%.1f°C. %s", t, fever > 0 ? "A wound has gone bad."
                        : cold ? "Just a cold." : t > 37.5 ? "A fever, and no wound to blame." : "Normal."));
            }
            case "blood" -> say(player, lost < 50 ? "Your blood pressure is fine."
                    : "Low blood pressure: you've lost about " + lost * 40 / Medicine.MAX + "% of your blood.");
            case "oxygen" -> say(player, String.format(java.util.Locale.ROOT, "Oxygen %d%%, pulse %d.",
                    Math.max(70, 99 - smoke / 4 - lost / 50), 70 + lost / 12 + fever / 4));
            case "listen" -> {
                int near = player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.Zombie.class,
                        player.getBoundingBox().inflate(10), com.zomboidcraft.zombie.ZombieTypes::isWalker).size();
                say(player, (near == 0 ? "You hear nothing moving nearby." : near == 1 ? "Something's moving close by."
                        : "You hear " + near + " of them shuffling close by.") + (cold || smoke > 20 ? " Your chest rattles." : ""));
            }
            case "scan" -> {
                Body body = Body.get(player);
                boolean any = false;
                for (Region r : Region.values()) {
                    Part p = body.part(r);
                    if (p.fine()) continue;
                    any = true;
                    StringBuilder line = new StringBuilder(Body.describe(r, p));
                    if (Medicine.get(m, "lodged", r) > 0) line.append(", something lodged");
                    if (Medicine.get(m, "deep", r) > 0 && Medicine.get(m, "stitched", r) == 0) line.append(", deep");
                    if (Medicine.get(m, "infect", r) >= Medicine.INFECTED) line.append(", infected");
                    int left = Math.max(p.woundLeft(), p.fractureLeft() / (p.splinted() ? 3 : 1));
                    line.append(": about ").append(Math.max(1, left / 60)).append(" min to heal");
                    player.sendSystemMessage(Component.literal(line.toString()).withStyle(ChatFormatting.GRAY));
                }
                if (!any) say(player, "Nothing hidden. You're whole.");
            }
            default -> {
            }
        }
    }

    private MedSpecs() {
    }
}
