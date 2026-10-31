package com.zomboidcraft.clothing;

import static com.zomboidcraft.clothing.ClothingSlot.ARM_GUARDS;
import static com.zomboidcraft.clothing.ClothingSlot.BELT;
import static com.zomboidcraft.clothing.ClothingSlot.EYES;
import static com.zomboidcraft.clothing.ClothingSlot.FACE;
import static com.zomboidcraft.clothing.ClothingSlot.FEET;
import static com.zomboidcraft.clothing.ClothingSlot.HANDS;
import static com.zomboidcraft.clothing.ClothingSlot.HAT;
import static com.zomboidcraft.clothing.ClothingSlot.JACKET;
import static com.zomboidcraft.clothing.ClothingSlot.LEGS;
import static com.zomboidcraft.clothing.ClothingSlot.LEG_GUARDS;
import static com.zomboidcraft.clothing.ClothingSlot.NECK;
import static com.zomboidcraft.clothing.ClothingSlot.SHIRT;
import static com.zomboidcraft.clothing.ClothingSlot.UNDER;
import static com.zomboidcraft.clothing.ClothingSlot.VEST;
import static com.zomboidcraft.clothing.Garment.Cloth.DENIM;
import static com.zomboidcraft.clothing.Garment.Cloth.HARD;
import static com.zomboidcraft.clothing.Garment.Cloth.LEATHER;
import static com.zomboidcraft.clothing.Garment.Cloth.NONE;
import static com.zomboidcraft.clothing.Garment.Cloth.RUBBER;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.survival.Body;
import com.zomboidcraft.survival.Body.Region;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Every wearable and what it does (see {@link Garment}), and what a survivor's clothes add up to. Rows are the
 * clothing of the loot roster (tools/items/loot/roster.py) plus the carrier and mansion wearables; an item that isn't
 * registered yet just isn't found. Numbers are Project Zomboid's (Build 42 scripts, 0 to 100 for the guards): a row
 * reads scratch then bite, as Zomboid's item cards do.
 *
 * Protection stacks the way layers do: a hit on a spot has to get past each piece covering it, outermost first, and
 * the piece that stops it takes the wear (see {@link ClothWear}).
 */
public final class Clothes {
    private static final Map<String, Garment> BY_ID = new LinkedHashMap<>();
    private static final List<G> ROWS = new ArrayList<>();

    /** Outermost first: the order a hit meets them. */
    private static final List<ClothingSlot> OUTSIDE_IN = List.of(HAT, FACE, EYES, NECK, ARM_GUARDS, LEG_GUARDS, VEST, JACKET,
            HANDS, FEET, SHIRT, BELT, LEGS, UNDER);

    static {
        // head: hats and helmets
        c("baseball_cap", HAT, "").warm(0.1f, 0, 0);
        c("trucker_cap", HAT, "").warm(0.1f, 0, 0);
        c("beanie", HAT, "H").warm(0.6f, 0.3f, 0);
        c("cowboy_hat", HAT, "").warm(0.3f, 0.2f, 0.3f).of(LEATHER);
        c("bucket_hat", HAT, "").warm(0.5f, 0.3f, 0.2f);
        c("hard_hat", HAT, "H").guard(80, 70).warm(0.15f, 0.25f, 0.5f).feel(0.1f).of(HARD).armour(1);
        c("bike_helmet", HAT, "H").guard(50, 30).warm(0.25f, 0.15f, 0).of(HARD).armour(1);
        c("football_helmet", HAT, "H").guard(80, 70).warm(0.55f, 0.35f, 0.85f).feel(0.2f).of(HARD).armour(2);
        c("motorcycle_helmet", HAT, "HN").guard(100, 100).warm(1f, 1f, 0.9f).feel(0.3f).of(HARD).armour(2);
        // prized and grail headgear
        c("crusader_great_helm", HAT, "HN").guard(100, 100).bullet(15).warm(0.35f, 0.4f, 0.3f).feel(0.3f).of(HARD).armour(3).lasts(30);
        c("gothic_sallet", HAT, "H").guard(95, 90).bullet(10).warm(0.25f, 0.3f, 0.3f).feel(0.2f).of(HARD).armour(2).lasts(24);
        c("bomb_disposal_helmet", HAT, "HN").guard(100, 100).bullet(60).warm(0.6f, 0.8f, 0.6f).feel(0.35f).slow(0.03f, 0).of(HARD).armour(3).lasts(20);
        c("police_cap", HAT, "").warm(0.1f, 0, 0.2f);
        c("fire_helmet", HAT, "H").guard(80, 70).warm(0.65f, 0.55f, 0.6f).feel(0.15f).of(HARD).armour(2);
        c("combat_helmet", HAT, "H").guard(80, 70).bullet(40).warm(0.25f, 0, 0.2f).feel(0.15f).of(HARD).armour(2);
        c("riot_helmet", HAT, "HN").guard(100, 100).warm(0.45f, 0.65f, 1f).feel(0.3f).of(HARD).armour(2);
        c("cranial_helmet", HAT, "H").guard(50, 30).warm(0.3f, 0.2f, 0).feel(0.1f).of(HARD).armour(1);
        c("pilot_helmet", HAT, "HN").guard(80, 70).warm(0.7f, 0.7f, 1f).feel(0.2f).of(HARD).armour(2);
        c("high_cut_helmet", HAT, "H").guard(90, 80).bullet(60).warm(0.2f, 0, 0.2f).feel(0.15f).of(HARD).armour(2);
        c("steel_pot_helmet", HAT, "H").guard(80, 70).bullet(30).warm(0.2f, 0.1f, 0.3f).feel(0.2f).of(HARD).armour(2);
        c("tanker_helmet", HAT, "H").guard(50, 30).warm(0.6f, 0.5f, 0.2f).feel(0.1f).of(LEATHER).armour(1).lasts(16);
        c("boonie_hat", HAT, "").warm(0.25f, 0.1f, 0.3f);
        c("patrol_cap", HAT, "").warm(0.15f, 0, 0.1f);
        c("beret", HAT, "").warm(0.2f, 0.1f, 0);
        c("fur_ushanka", HAT, "H").guard(10, 0).warm(1f, 0.9f, 0.3f).of(LEATHER);
        c("watch_cap", HAT, "H").warm(0.6f, 0.3f, 0);
        c("headlamp", HAT, "").of(NONE);
        c("shooting_earmuffs", HAT, "").warm(0.1f, 0.1f, 0).of(NONE);
        c("flat_cap", HAT, "").warm(0.25f, 0.15f, 0.1f);
        c("fedora", HAT, "").warm(0.2f, 0.15f, 0.2f);
        c("straw_hat", HAT, "").warm(0.1f, 0, 0.1f);
        c("ranger_hat", HAT, "").warm(0.3f, 0.2f, 0.3f);
        c("hunting_cap", HAT, "").warm(0.2f, 0.15f, 0.1f);
        c("scrap_helmet", HAT, "H").guard(70, 50).warm(0.1f, 0.1f, 0.2f).feel(0.15f).of(HARD).armour(1).lasts(8);
        c("peaked_cap", HAT, "").warm(0.15f, 0, 0.2f);
        c("captains_hat", HAT, "").warm(0.15f, 0, 0.2f);
        c("chefs_hat", HAT, "").warm(0.1f, 0, 0);
        c("surgical_cap", HAT, "").warm(0.1f, 0, 0);
        c("visor_cap", HAT, "").of(NONE);
        c("sweatband", HAT, "").warm(0.05f, 0, 0);
        c("winter_earmuffs", HAT, "").warm(0.3f, 0.3f, 0).of(NONE);
        c("miner_helmet", HAT, "H").guard(80, 70).warm(0.15f, 0.25f, 0.5f).feel(0.1f).of(HARD).armour(1);
        c("batting_helmet", HAT, "H").guard(60, 40).warm(0.25f, 0.2f, 0.3f).feel(0.1f).of(HARD).armour(1);
        c("hockey_helmet", HAT, "H").guard(80, 70).warm(0.3f, 0.2f, 0.2f).feel(0.15f).of(HARD).armour(1);
        c("riding_helmet", HAT, "H").guard(50, 30).warm(0.3f, 0.2f, 0.2f).of(HARD).armour(1);
        c("boxing_headgear", HAT, "H").guard(40, 25).warm(0.4f, 0.3f, 0).feel(0.1f).of(LEATHER);
        c("swat_helmet", HAT, "H").guard(90, 80).bullet(60).warm(0.25f, 0.1f, 0.3f).feel(0.2f).of(HARD).armour(2);
        c("welder_mask", HAT, "H").guard(50, 30).warm(0.2f, 0.2f, 0.2f).feel(0.2f).of(HARD);
        // eyes
        c("welding_goggles", EYES, "").guard(5, 0).of(NONE);
        c("sunglasses", EYES, "").of(NONE);
        c("aviator_sunglasses", EYES, "").of(NONE);
        c("reading_glasses", EYES, "").of(NONE);
        c("ballistic_glasses", EYES, "").guard(5, 0).of(NONE);
        c("ballistic_goggles", EYES, "").guard(10, 0).warm(0, 0.1f, 0).of(NONE);
        c("ski_goggles", EYES, "").warm(0, 0.2f, 0).of(NONE);
        c("safety_goggles", EYES, "").guard(5, 0).of(NONE);
        c("eyepatch", EYES, "").of(NONE);
        c("monocle", EYES, "").of(NONE);
        c("prescription_glasses", EYES, "").of(NONE);
        // face
        c("hockey_mask", FACE, "H").guard(50, 30).warm(0.25f, 0.55f, 0).feel(0.1f).of(HARD);
        c("gas_mask", FACE, "H").guard(50, 30).warm(0.75f, 0.6f, 1f).feel(0.3f).of(RUBBER);
        c("cbrn_mask", FACE, "HN").guard(50, 30).warm(0.8f, 0.8f, 1f).feel(0.35f).of(RUBBER);
        c("scba_mask", FACE, "H").guard(50, 30).warm(0.7f, 0.7f, 1f).feel(0.3f).of(RUBBER).lasts(16);
        c("dust_mask", FACE, "").guard(5, 0).of(NONE);
        c("half_mask_respirator", FACE, "").guard(10, 5).warm(0.1f, 0.1f, 0).feel(0.1f).of(RUBBER);
        c("bandana", FACE, "").guard(5, 0).warm(0.1f, 0, 0);
        c("balaclava", FACE, "HN").guard(5, 0).warm(1f, 1f, 0.5f);
        c("surgical_mask_worn", FACE, "").guard(5, 0);
        // neck
        c("scarf", NECK, "N").guard(10, 0).warm(0.5f, 0.55f, 0);
        c("necktie", NECK, "").of(NONE);
        c("shemagh", NECK, "N").guard(10, 0).warm(0.4f, 0.5f, 0);
        c("neck_gaiter", NECK, "N").guard(5, 0).warm(0.3f, 0.3f, 0);
        c("dog_tags", NECK, "").of(NONE);
        c("hockey_neck_guard", NECK, "N").guard(60, 40).warm(0.2f, 0.1f, 0).of(HARD);
        c("leather_neck_guard", NECK, "N").guard(80, 60).warm(0.2f, 0.2f, 0).feel(0.1f).of(LEATHER).lasts(16);
        c("crusader_cloak", NECK, "").warm(0.65f, 0.75f, 0.35f).slow(0.01f, 0);
        // pads strapped over the sleeves and trousers
        c("elbow_pads", ARM_GUARDS, "e").guard(60, 40).feel(0.05f).of(HARD);
        c("riot_arm_guards", ARM_GUARDS, "A").guard(80, 70).feel(0.1f).slow(0, 0.04f).of(HARD).armour(1);
        c("scrap_arm_guards", ARM_GUARDS, "uf").guard(70, 50).feel(0.1f).slow(0, 0.05f).of(HARD).armour(1).lasts(8);
        c("crusader_pauldrons", ARM_GUARDS, "u").guard(100, 100).bullet(10).feel(0.2f).slow(0, 0.05f).of(HARD).armour(2).lasts(30);
        c("gothic_arm_harness", ARM_GUARDS, "A").guard(95, 90).bullet(10).feel(0.2f).slow(0, 0.05f).of(HARD).armour(2).lasts(24);
        c("football_shoulder_pads", ARM_GUARDS, "Tu").guard(40, 25).feel(0.15f).slow(0, 0.05f).of(HARD).armour(1);
        c("hockey_shoulder_pads", ARM_GUARDS, "Tu").guard(35, 20).feel(0.1f).slow(0, 0.03f).of(HARD).armour(1);
        c("chainmail_sleeve", ARM_GUARDS, "ef").guard(90, 60).feel(0.05f).of(HARD);
        c("knee_pads", LEG_GUARDS, "k").guard(60, 40).feel(0.05f).of(HARD);
        c("riot_leg_guards", LEG_GUARDS, "tks").guard(80, 70).feel(0.1f).slow(0.04f, 0).of(HARD).armour(1);
        c("scrap_leg_guards", LEG_GUARDS, "tks").guard(70, 50).feel(0.1f).slow(0.05f, 0).of(HARD).armour(1).lasts(8);
        c("legwarmers", LEG_GUARDS, "s").warm(0.3f, 0.2f, 0);
        c("crusader_greaves", LEG_GUARDS, "tks").guard(100, 100).bullet(10).feel(0.2f).slow(0.06f, 0).of(HARD).armour(2).lasts(30);
        c("gothic_leg_harness", LEG_GUARDS, "tks").guard(95, 90).bullet(10).feel(0.2f).slow(0.06f, 0).of(HARD).armour(2).lasts(24);
        c("chainsaw_chaps", LEG_GUARDS, "tks").guard(70, 50).warm(0.2f, 0.1f, 0.2f).feel(0.1f).slow(0.03f, 0).lasts(14);
        c("shin_guards", LEG_GUARDS, "ks").guard(60, 40).feel(0.05f).of(HARD);
        c("gaiters", LEG_GUARDS, "s").guard(20, 10).warm(0.2f, 0.4f, 0.8f);
        // under everything
        c("athletic_cup", UNDER, "g").guard(30, 20).of(HARD);
        c("long_johns", UNDER, "TAL").warm(0.6f, 0.3f, 0);
        c("socks", UNDER, "O").warm(0.2f, 0.05f, 0);
        // shirts: the layer next to the skin
        c("t_shirt", SHIRT, "Ta").warm(0.2f, 0.05f, 0);
        c("band_tshirt", SHIRT, "Ta").warm(0.2f, 0.05f, 0);
        c("tank_top", SHIRT, "T").warm(0.15f, 0.05f, 0);
        c("flannel_shirt", SHIRT, "TA").guard(15, 7).warm(0.45f, 0.55f, 0);
        c("dress_shirt", SHIRT, "TA").guard(10, 5).warm(0.35f, 0.3f, 0);
        c("polo_shirt", SHIRT, "Ta").warm(0.2f, 0.05f, 0);
        c("hawaiian_shirt", SHIRT, "Ta").warm(0.2f, 0.05f, 0);
        c("turtleneck", SHIRT, "TAN").guard(10, 0).warm(0.55f, 0.45f, 0);
        c("scrubs_top", SHIRT, "Ta").warm(0.1f, 0.05f, 0);
        c("dress", SHIRT, "Tl").warm(0.2f, 0.15f, 0);
        c("wedding_dress", SHIRT, "TAL").guard(10, 0).warm(0.25f, 0.1f, 0).slow(0.05f, 0);
        c("flight_deck_jersey", SHIRT, "TA").guard(10, 0).warm(0.3f, 0.2f, 0);
        c("field_shirt", SHIRT, "TA").guard(20, 10).warm(0.4f, 0.3f, 0.1f).lasts(12);
        c("combat_shirt", SHIRT, "TA").guard(20, 10).warm(0.25f, 0.15f, 0).lasts(12);
        c("rugby_shirt", SHIRT, "TA").guard(15, 5).warm(0.4f, 0.25f, 0);
        c("security_shirt", SHIRT, "Ta").guard(10, 5).warm(0.25f, 0.1f, 0);
        c("prison_jumpsuit", SHIRT, "TaL").guard(15, 5).warm(0.3f, 0.15f, 0);
        c("pyjama_top", SHIRT, "TA").warm(0.25f, 0.1f, 0);
        c("sports_jersey", SHIRT, "Ta").warm(0.15f, 0.05f, 0);
        c("postal_shirt", SHIRT, "Ta").guard(10, 5).warm(0.25f, 0.1f, 0);
        c("cycling_jersey", SHIRT, "Ta").warm(0.1f, 0.3f, 0);
        c("wetsuit", SHIRT, "TAL").guard(10, 5).warm(0.6f, 0.8f, 1f).feel(0.1f).slow(0.03f, 0).of(RUBBER);
        c("shark_mesh_suit", SHIRT, "TAL").guard(100, 95).warm(0.1f, 0.05f, 0).feel(0.25f).slow(0.05f, 0.03f).of(HARD).lasts(30);
        // jackets, coats and anything worn over the shirt
        c("sweater", JACKET, "TA").guard(10, 0).warm(0.55f, 0.45f, 0);
        c("hoodie", JACKET, "TAH").guard(10, 0).warm(0.6f, 0.25f, 0.1f);
        c("sweatshirt", JACKET, "TA").guard(10, 0).warm(0.55f, 0.25f, 0);
        c("denim_jacket", JACKET, "TA").guard(50, 30).warm(0.45f, 0.3f, 0.45f).of(DENIM);
        c("leather_jacket", JACKET, "TA").guard(40, 20).warm(0.4f, 0.6f, 0.4f).of(LEATHER);
        c("bomber_jacket", JACKET, "TA").guard(50, 30).warm(0.7f, 0.6f, 0.4f).of(LEATHER);
        c("varsity_jacket", JACKET, "TA").guard(30, 10).warm(0.5f, 0.4f, 0.2f);
        c("windbreaker", JACKET, "TA").guard(20, 5).warm(0.25f, 0.8f, 0.5f);
        c("parka", JACKET, "TAH").guard(20, 10).warm(1f, 1f, 0.6f).feel(0.1f).slow(0.02f, 0.03f);
        c("raincoat", JACKET, "TAlH").guard(10, 0).warm(0.15f, 0.8f, 1f).of(RUBBER);
        c("trench_coat", JACKET, "TAl").guard(20, 10).warm(0.65f, 0.5f, 0.5f);
        c("suit_jacket", JACKET, "TA").guard(20, 0).warm(0.55f, 0.45f, 0);
        c("lab_coat", JACKET, "TAl").guard(20, 0).warm(0.35f, 0.25f, 0);
        c("police_jacket", JACKET, "TA").guard(30, 20).warm(0.6f, 0.45f, 0.25f);
        c("fire_coat", JACKET, "TAl").guard(70, 50).warm(0.85f, 0.85f, 0.8f).feel(0.2f).slow(0.05f, 0.05f).armour(1).lasts(16);
        c("field_jacket", JACKET, "TA").guard(50, 30).warm(0.5f, 0.4f, 0.3f).lasts(12);
        c("ghillie_suit", JACKET, "TAL").guard(30, 10).warm(0.7f, 0.45f, 0.35f).feel(0.2f).slow(0.05f, 0);
        c("ww2_flight_jacket", JACKET, "TA").guard(40, 20).warm(0.9f, 0.8f, 0.5f).of(LEATHER).armour(1);
        c("fur_coat", JACKET, "TAl").guard(20, 10).warm(0.95f, 0.5f, 0.6f).slow(0.02f, 0);
        c("bespoke_suit", JACKET, "TAL").guard(20, 0).warm(0.55f, 0.45f, 0);
        c("poncho", JACKET, "TAH").guard(10, 0).warm(0.15f, 0.8f, 1f).of(RUBBER);
        c("wool_commando_sweater", JACKET, "TA").guard(15, 5).warm(0.75f, 0.5f, 0.1f);
        c("fleece_liner", JACKET, "TA").guard(10, 0).warm(0.8f, 0.4f, 0);
        c("wet_weather_jacket", JACKET, "TAN").guard(20, 10).warm(0.3f, 1f, 1f).lasts(12);
        c("flight_coverall", JACKET, "TAL").guard(30, 15).warm(0.45f, 0.4f, 0.2f).lasts(12);
        c("sniper_smock", JACKET, "TAl").guard(40, 20).warm(0.5f, 0.45f, 0.4f).feel(0.1f).lasts(12);
        c("arctic_parka", JACKET, "TAlH").guard(30, 15).warm(1f, 1f, 0.7f).feel(0.15f).slow(0.03f, 0.04f).lasts(12);
        c("cbrn_suit", JACKET, "TAL").guard(50, 30).warm(0.6f, 1f, 1f).feel(0.35f).slow(0.06f, 0.05f).lasts(10);
        c("beekeeper_suit", JACKET, "TAL").guard(20, 5).warm(0.35f, 0.4f, 0.2f).slow(0.03f, 0);
        c("puffer_jacket", JACKET, "TA").guard(20, 10).warm(1f, 0.8f, 0.4f).feel(0.05f).slow(0.02f, 0.02f);
        c("ski_jacket", JACKET, "TA").guard(25, 10).warm(0.85f, 0.9f, 0.8f);
        c("cardigan", JACKET, "TA").guard(10, 0).warm(0.45f, 0.3f, 0);
        c("blazer", JACKET, "TA").guard(20, 5).warm(0.45f, 0.4f, 0);
        c("gilet", JACKET, "T").guard(10, 5).warm(0.6f, 0.5f, 0.2f);
        c("tracksuit_top", JACKET, "TA").guard(10, 0).warm(0.35f, 0.3f, 0.1f);
        c("bathrobe", JACKET, "TAl").warm(0.5f, 0.2f, 0).slow(0.03f, 0);
        c("chef_jacket", JACKET, "TA").guard(15, 5).warm(0.35f, 0.25f, 0);
        c("mechanic_coveralls", JACKET, "TAL").guard(30, 15).warm(0.4f, 0.35f, 0.2f).lasts(12);
        c("sheepskin_jacket", JACKET, "TA").guard(40, 20).warm(1f, 0.8f, 0.5f).feel(0.05f).of(LEATHER);
        c("padded_jacket", JACKET, "TA").guard(30, 15).warm(0.8f, 0.6f, 0.4f).feel(0.05f);
        c("hunting_jacket", JACKET, "TA").guard(30, 15).warm(0.6f, 0.5f, 0.4f);
        c("paramedic_jacket", JACKET, "TA").guard(30, 15).warm(0.5f, 0.45f, 0.3f);
        c("crusader_plate", JACKET, "TA").guard(100, 100).bullet(20).warm(0.4f, 0.3f, 0).feel(0.4f).slow(0.08f, 0.06f).of(HARD).armour(5).lasts(30);
        c("bomb_disposal_suit", JACKET, "TAL").guard(90, 80).bullet(60).warm(1f, 0.8f, 0.5f).feel(0.6f).slow(0.15f, 0.1f).armour(6).lasts(20);
        c("motorcycle_leathers", JACKET, "TAL").guard(60, 40).warm(0.6f, 0.8f, 0.4f).feel(0.1f).of(LEATHER).armour(1).lasts(18);
        c("bodyguard_suit", JACKET, "TAL").guard(40, 20).bullet(60).warm(0.6f, 0.45f, 0).feel(0.1f).armour(3).lasts(16);
        // vests: over the jacket, and armour
        c("hi_vis_vest", VEST, "T").guard(10, 0).warm(0.1f, 0.05f, 0);
        c("flak_vest", VEST, "T").guard(55, 30).bullet(80).warm(0.6f, 0.25f, 0).feel(0.25f).slow(0.03f, 0.03f).armour(4).lasts(20);
        c("police_vest", VEST, "T").guard(55, 30).bullet(100).warm(0.6f, 0.25f, 0).feel(0.25f).slow(0.03f, 0.03f).armour(5).lasts(20);
        c("plate_carrier", VEST, "T").guard(90, 80).bullet(100).warm(0.4f, 0.2f, 0).feel(0.4f).slow(0.08f, 0.05f).of(HARD).armour(7);
        c("survival_vest", VEST, "T").guard(20, 0).warm(0.3f, 0.2f, 0);
        c("catchers_vest", VEST, "Tg").guard(40, 25).warm(0.2f, 0.1f, 0).feel(0.1f).armour(1);
        c("hunting_vest", VEST, "T").guard(5, 0).warm(0.1f, 0.1f, 0);
        c("foreman_vest", VEST, "T").guard(10, 0).warm(0.1f, 0.05f, 0);
        c("waistcoat", VEST, "T").guard(10, 0).warm(0.25f, 0.15f, 0);
        c("leather_biker_vest", VEST, "T").guard(30, 15).warm(0.3f, 0.4f, 0.2f).of(LEATHER);
        c("sheepskin_vest", VEST, "T").guard(15, 5).warm(0.6f, 0.5f, 0.1f).of(LEATHER);
        c("apron", VEST, "Tl").guard(5, 0).warm(0.05f, 0.05f, 0.1f);
        c("leather_apron", VEST, "Tl").guard(40, 20).warm(0.1f, 0.1f, 0.3f).of(LEATHER).lasts(14);
        c("scrap_cuirass", VEST, "T").guard(70, 50).warm(0.2f, 0.2f, 0).feel(0.3f).slow(0.05f, 0.04f).of(HARD).armour(2).lasts(8);
        c("tyre_cuirass", VEST, "T").guard(50, 40).warm(0.3f, 0.4f, 0.4f).feel(0.25f).slow(0.04f, 0.03f).of(RUBBER).armour(1).lasts(10);
        c("crusader_tabard", VEST, "Tl").guard(10, 0).warm(0.2f, 0.25f, 0);
        c("gothic_cuirass", VEST, "Tl").guard(95, 90).bullet(20).warm(0.3f, 0.2f, 0).feel(0.35f).slow(0.07f, 0.05f).of(HARD).armour(4).lasts(24);
        c("scba_set", VEST, "T").guard(10, 5).feel(0.15f).slow(0.06f, 0.02f).lasts(16);
        // rigs carry gear (carry/BagSpec) as well as being worn
        c("chest_rig", VEST, "T").guard(10, 5).warm(0.15f, 0.1f, 0).lasts(14);
        c("load_bearing_vest", VEST, "T").guard(20, 10).warm(0.2f, 0.1f, 0).lasts(14);
        c("medic_vest", VEST, "T").guard(10, 5).warm(0.15f, 0.1f, 0).lasts(12);
        c("bandolier", VEST, "").of(NONE);
        c("heavy_plate_carrier", VEST, "TNug").guard(100, 100).bullet(100).warm(0.6f, 0.3f, 0).feel(0.6f).slow(0.12f, 0.08f).of(HARD).armour(9);
        // gloves
        c("work_gloves", HANDS, "P").guard(30, 15).warm(0.5f, 0.5f, 0).of(LEATHER);
        c("leather_gloves", HANDS, "P").guard(30, 15).warm(0.75f, 0.75f, 0).of(LEATHER);
        c("winter_gloves", HANDS, "P").guard(5, 0).warm(1f, 1f, 0).slow(0, 0.03f);
        c("rubber_gloves", HANDS, "P").guard(5, 0).warm(0.25f, 0.3f, 1f).of(RUBBER);
        c("tactical_gloves", HANDS, "P").guard(80, 70).bullet(10).warm(0.25f, 0.3f, 0);
        c("flight_gloves", HANDS, "Pf").guard(30, 15).warm(0.3f, 0.3f, 0).of(LEATHER);
        c("shooting_gloves", HANDS, "P").guard(20, 10).warm(0.75f, 0.75f, 0.2f);
        c("arctic_mittens", HANDS, "Pf").guard(30, 15).warm(1f, 1f, 0.7f).slow(0, 0.08f).of(LEATHER);
        c("cut_resistant_gloves", HANDS, "P").guard(60, 20).warm(0.2f, 0.1f, 0);
        c("hockey_gloves", HANDS, "Pf").guard(40, 25).warm(0.5f, 0.4f, 0).slow(0, 0.05f);
        c("chainmail_butcher_glove", HANDS, "P").guard(90, 60).of(HARD);
        c("fingerless_gloves", HANDS, "P").guard(10, 0).warm(0.3f, 0.2f, 0);
        c("boxing_gloves", HANDS, "P").guard(20, 10).warm(0.3f, 0.2f, 0).slow(0, 0.1f).of(LEATHER);
        c("crusader_gauntlets", HANDS, "Pf").guard(100, 100).feel(0.1f).slow(0, 0.04f).of(HARD).armour(1).lasts(30);
        c("gothic_gauntlets", HANDS, "Pf").guard(95, 90).feel(0.1f).slow(0, 0.04f).of(HARD).armour(1).lasts(24);
        c("fire_gloves", HANDS, "Pf").guard(70, 50).warm(0.75f, 0.75f, 0.6f).slow(0, 0.05f).of(LEATHER);
        // belts: what they open is in QuickSlots
        c("leather_belt", BELT, "").of(NONE);
        c("tool_belt", BELT, "").of(NONE);
        c("duty_belt", BELT, "").of(NONE);
        // legs
        c("jeans", LEGS, "L").guard(20, 10).warm(0.7f, 0.55f, 0).of(DENIM);
        c("work_pants", LEGS, "L").guard(20, 10).warm(0.45f, 0.3f, 0.45f);
        c("cargo_pants", LEGS, "L").guard(20, 10).warm(0.45f, 0.3f, 0.3f);
        c("slacks", LEGS, "L").guard(10, 0).warm(0.4f, 0.2f, 0);
        c("sweatpants", LEGS, "L").warm(0.3f, 0.15f, 0);
        c("shorts", LEGS, "l").guard(20, 10).warm(0.3f, 0.15f, 0);
        c("skirt", LEGS, "l").warm(0.1f, 0.01f, 0);
        c("overalls", LEGS, "LT").guard(20, 10).warm(0.65f, 0.5f, 0.2f).of(DENIM);
        c("scrubs_pants", LEGS, "L").warm(0.1f, 0.05f, 0);
        c("camo_pants", LEGS, "L").guard(20, 10).warm(0.3f, 0.1f, 0.2f);
        c("combat_trousers", LEGS, "L").guard(30, 15).warm(0.45f, 0.3f, 0.3f).lasts(12);
        c("snow_overwhites", LEGS, "L").guard(10, 0).warm(0.35f, 0.7f, 0.6f);
        c("padded_football_pants", LEGS, "lk").guard(20, 10).warm(0.3f, 0.1f, 0);
        c("hockey_pants", LEGS, "l").guard(30, 15).warm(0.35f, 0.2f, 0.1f).feel(0.05f);
        c("ski_trousers", LEGS, "L").guard(25, 10).warm(0.85f, 0.9f, 0.9f);
        c("tracksuit_bottoms", LEGS, "L").warm(0.35f, 0.2f, 0);
        c("fire_pants", LEGS, "L").guard(30, 20).warm(0.85f, 0.85f, 0.8f).feel(0.15f).slow(0.04f, 0).armour(1).lasts(16);
        c("leather_chaps", LEGS, "L").guard(40, 20).warm(0.65f, 0.6f, 0).of(LEATHER);
        c("g_suit", LEGS, "L").guard(20, 10).warm(0.3f, 0.3f, 0.2f).feel(0.1f);
        c("pyjama_bottoms", LEGS, "L").warm(0.2f, 0.1f, 0);
        c("cycling_shorts", LEGS, "l").warm(0.1f, 0.1f, 0);
        c("fishing_waders", LEGS, "LT").guard(20, 10).warm(0.3f, 0.8f, 1f).slow(0.05f, 0).of(RUBBER);
        // feet
        c("sneakers", FEET, "O").guard(10, 0).warm(0.45f, 0.3f, 0);
        c("running_shoes", FEET, "O").guard(10, 0).warm(0.45f, 0.3f, 0);
        c("dress_shoes", FEET, "O").guard(20, 10).warm(0.25f, 0.25f, 0.25f).of(LEATHER).slow(0.03f, 0);
        c("work_boots", FEET, "B").guard(100, 100).warm(1f, 1f, 0.8f).of(LEATHER).armour(1).lasts(20);
        c("cowboy_boots", FEET, "B").guard(100, 100).warm(0.5f, 0.6f, 0.5f).of(LEATHER).lasts(18);
        c("hiking_boots", FEET, "O").guard(100, 100).warm(0.5f, 0.6f, 0.5f).of(LEATHER).lasts(18);
        c("rubber_boots", FEET, "B").guard(100, 100).warm(0.5f, 1f, 1f).of(RUBBER).slow(0.03f, 0);
        c("combat_boots", FEET, "B").guard(100, 100).warm(1f, 1f, 0.8f).of(LEATHER).armour(1).lasts(20);
        c("tactical_boots", FEET, "B").guard(100, 100).warm(0.7f, 0.7f, 0.5f).of(LEATHER).armour(1).lasts(18);
        c("jungle_boots", FEET, "B").guard(100, 100).warm(0.5f, 0.5f, 0.3f).of(LEATHER).lasts(16);
        c("desert_boots", FEET, "B").guard(100, 100).warm(0.6f, 0.6f, 0.3f).of(LEATHER).lasts(16);
        c("arctic_boots", FEET, "B").guard(60, 40).warm(1f, 1f, 0.8f).of(LEATHER).slow(0.03f, 0);
        c("sandals", FEET, "O").warm(0.05f, 0, 0);
        c("riding_boots", FEET, "B").guard(100, 100).warm(0.5f, 0.5f, 0.4f).of(LEATHER).lasts(18);
        c("bowling_shoes", FEET, "O").guard(10, 0).warm(0.3f, 0.2f, 0).slow(0.02f, 0);
        c("flip_flops", FEET, "O").slow(0.05f, 0);
        c("crusader_sabatons", FEET, "B").guard(100, 100).warm(0.3f, 0.3f, 0.2f).slow(0.04f, 0).of(HARD).armour(1).lasts(30);
        c("gothic_sabatons", FEET, "B").guard(95, 90).warm(0.3f, 0.3f, 0.2f).slow(0.04f, 0).of(HARD).armour(1).lasts(24);
        c("slippers", FEET, "O").warm(0.3f, 0.1f, 0).slow(0.05f, 0);

        for (G row : ROWS) BY_ID.put(row.id, row.build());
        ROWS.clear();
    }

    /** A row of the table, built up a stat at a time. */
    private static final class G {
        final String id;
        final ClothingSlot slot;
        final String code;
        float bite, scratch, bullet, insulation, wind, water, discomfort, run, swing;
        int armour, condition = -1;
        Garment.Cloth cloth = Garment.Cloth.FABRIC;

        G(String id, ClothingSlot slot, String code) {
            this.id = id;
            this.slot = slot;
            this.code = code;
        }

        /** Zomboid's Scratch then Bite defence, 0 to 100. */
        G guard(int scratch, int bite) {
            this.scratch = scratch / 100f;
            this.bite = bite / 100f;
            return this;
        }

        G bullet(int bullet) {
            this.bullet = bullet / 100f;
            return this;
        }

        G warm(float insulation, float wind, float water) {
            this.insulation = insulation;
            this.wind = wind;
            this.water = water;
            return this;
        }

        G feel(float discomfort) {
            this.discomfort = discomfort;
            return this;
        }

        G slow(float run, float swing) {
            this.run = run;
            this.swing = swing;
            return this;
        }

        G of(Garment.Cloth cloth) {
            this.cloth = cloth;
            return this;
        }

        G armour(int armour) {
            this.armour = armour;
            return this;
        }

        G lasts(int condition) {
            this.condition = condition;
            return this;
        }

        Garment build() {
            int lasts = condition > 0 ? condition : switch (cloth) {
                case FABRIC -> 8;
                case DENIM -> 12;
                case LEATHER -> 14;
                case RUBBER -> 10;
                case HARD -> 20;
                case NONE -> 10;
            };
            return new Garment(id, slot, Garment.spots(code), bite, scratch, bullet, insulation, wind, water, discomfort, run,
                    swing, armour, cloth, lasts);
        }
    }

    private static G c(String id, ClothingSlot slot, String code) {
        G row = new G(id, slot, code);
        ROWS.add(row);
        return row;
    }

    /** Every wearable, head to foot in table order. */
    public static List<Garment> all() {
        return List.copyOf(BY_ID.values());
    }

    public static @Nullable Garment byId(String id) {
        return BY_ID.get(id);
    }

    /** What this stack is as clothing, or null if it isn't any. */
    public static @Nullable Garment of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.getNamespace().equals(ZomboidCraft.MOD_ID) ? BY_ID.get(key.getPath()) : null;
    }

    public static @Nullable ClothingSlot slot(ItemStack stack) {
        Garment g = of(stack);
        return g == null ? null : g.slot();
    }

    /** What a piece guards on a spot, counting its wear and any patch there; 0 where it's torn or doesn't reach. */
    public static float guard(Garment g, ClothWear wear, Spot spot, Region region, Threat threat) {
        if (!g.covers().contains(spot) || wear.open(g, spot, region)) return 0;
        return Math.min(1, threat.of(g) + threat.of(wear.patch(spot, region)));
    }

    /** What a hit is, and how hard it is on the cloth that stops it. */
    public enum Threat {
        SCRATCH(1, 0.12f), BITE(2, 0.3f), BULLET(3, 0.5f);

        final int wear;
        final float tear;

        Threat(int wear, float tear) {
            this.wear = wear;
            this.tear = tear;
        }

        float of(Garment g) {
            return switch (this) {
                case SCRATCH -> g.scratch();
                case BITE -> g.bite();
                case BULLET -> g.bullet();
            };
        }

        float of(ClothWear.Patch p) {
            return this == BULLET ? 0 : this == BITE ? p.bite : p.scratch;
        }
    }

    /**
     * A hit on this spot meets the clothes over it, outermost first. Returns whether a piece stopped it; that piece
     * wears down and may tear (soft cloth tears less the tougher it is: denim a little, leather half as often).
     */
    public static boolean stops(Player player, Region region, Spot spot, Threat threat) {
        RandomSource random = player.getRandom();
        for (ClothingSlot slot : OUTSIDE_IN) {
            ItemStack stack = slot.worn(player);
            Garment g = of(stack);
            if (g == null) continue;
            ClothWear wear = ClothWear.of(stack);
            float guard = guard(g, wear, spot, region, threat);
            if (guard <= 0 || random.nextFloat() >= guard) continue;
            float tear = threat.tear * switch (g.cloth()) {
                case DENIM -> 0.75f;
                case LEATHER -> 0.5f;
                default -> 1f;
            };
            stack.set(ClothWear.TYPE, wear.hit(g, spot, region, threat.wear, tear, random));
            return true;
        }
        return false;
    }

    /** Where the last bullet to get through each player's clothes hit them, until Injuries reads it. */
    private static final Map<java.util.UUID, Region> BULLET_HIT = new java.util.HashMap<>();

    /**
     * A bullet meets the clothes over where it hit. Stopped by a vest or a helmet, it lands as a bruise: a quarter of
     * the damage and no wound. Called as the hit is taken (LivingEntityMixin).
     */
    public static float bullet(net.minecraft.server.level.ServerPlayer player, float damage) {
        RandomSource random = player.getRandom();
        Region where = Body.any(random);
        if (stops(player, where, Spot.roll(where, random), Threat.BULLET)) {
            BULLET_HIT.remove(player.getUUID());
            return damage * 0.25f;
        }
        BULLET_HIT.put(player.getUUID(), where);
        return damage;
    }

    /** Where the bullet just taken went in, or null if the clothes stopped it. */
    public static @Nullable Region lastBullet(Player player) {
        return BULLET_HIT.remove(player.getUUID());
    }

    /**
     * What everything worn adds up to.
     *
     * @param bite    per body part, the chance a bite there is stopped (each spot weighted by how often it's hit)
     * @param scratch the same for scratches
     * @param bullet  the same for bullets
     */
    public record Totals(Map<Region, Float> bite, Map<Region, Float> scratch, Map<Region, Float> bullet, int warmth,
                         float wind, float water, float discomfort, float run, float swing, int armour) {
        /** The average guard over the body's six parts, for a single headline number. */
        public float meanBite() {
            return mean(bite);
        }

        public float meanScratch() {
            return mean(scratch);
        }

        private static float mean(Map<Region, Float> map) {
            float sum = 0;
            for (float v : map.values()) sum += v;
            return sum / Region.values().length;
        }
    }

    /** Totals for your own survivor (server, or your own client while the kit is open). */
    public static Totals totals(Player player) {
        return totals(player, false);
    }

    /** @param shown read what the player is seen wearing (other players, on a client) instead of their own store */
    public static Totals totals(Player player, boolean shown) {
        List<ItemStack> worn = new ArrayList<>();
        for (ClothingSlot slot : OUTSIDE_IN) {
            ItemStack stack = shown ? slot.shown(player) : slot.worn(player);
            if (of(stack) != null) worn.add(stack);
        }
        Map<Region, Float> bite = new EnumMap<>(Region.class), scratch = new EnumMap<>(Region.class),
                bullet = new EnumMap<>(Region.class);
        for (Region r : Region.values()) {
            float b = 0, s = 0, u = 0;
            for (Spot spot : Spot.of(r)) {
                float bl = 1, sl = 1, ul = 1;
                for (ItemStack stack : worn) {
                    Garment g = of(stack);
                    ClothWear wear = ClothWear.of(stack);
                    bl *= 1 - guard(g, wear, spot, r, Threat.BITE);
                    sl *= 1 - guard(g, wear, spot, r, Threat.SCRATCH);
                    ul *= 1 - guard(g, wear, spot, r, Threat.BULLET);
                }
                b += spot.weight * (1 - bl);
                s += spot.weight * (1 - sl);
                u += spot.weight * (1 - ul);
            }
            bite.put(r, b);
            scratch.put(r, s);
            bullet.put(r, u);
        }
        float warmth = 0, windLeft = 1, dry = 1, discomfort = 0, run = 0, swing = 0;
        int armour = 0;
        for (ItemStack stack : worn) {
            Garment g = of(stack);
            // a torn piece lets the cold in, the more the more it's torn; a ruined one keeps a fifth of its warmth
            float whole = 1 - ClothWear.of(stack).torn(g) * 0.8f;
            warmth += g.insulation() * 10 * whole;
            windLeft *= 1 - g.wind() * whole;
            dry *= 1 - g.water() * whole;
            discomfort += g.discomfort();
            run += g.run();
            swing += g.swing();
            armour += g.armour();
        }
        return new Totals(Map.copyOf(bite), Map.copyOf(scratch), Map.copyOf(bullet), Math.round(warmth), 1 - windLeft,
                1 - dry, Math.min(1, discomfort), Math.min(0.5f, run), Math.min(0.5f, swing), armour);
    }

    private static final Identifier ARMOUR = ZomboidCraft.id("clothing_armor");
    private static final Identifier RUN = ZomboidCraft.id("clothing_run");
    private static final Identifier SWING = ZomboidCraft.id("clothing_swing");

    /**
     * Gives armour for the clothes kept in the gear store (vests, gloves, masks; pieces in the armour slots carry their
     * own on the item), and slows you for the heavy ones. Run every so often from the carry tick.
     */
    public static void tick(net.minecraft.server.level.ServerPlayer player) {
        int points = 0;
        float run = 0, swing = 0;
        for (ClothingSlot slot : ClothingSlot.ALL) {
            Garment g = of(slot.worn(player));
            if (g == null) continue;
            if (slot.armour == null) points += g.armour();
            run += g.run();
            swing += g.swing();
        }
        modify(player.getAttribute(Attributes.ARMOR), ARMOUR, points, AttributeModifier.Operation.ADD_VALUE);
        modify(player.getAttribute(Attributes.MOVEMENT_SPEED), RUN, -Math.min(0.5f, run), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(player.getAttribute(Attributes.ATTACK_SPEED), SWING, -Math.min(0.5f, swing), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void modify(@Nullable AttributeInstance attribute, Identifier id, double amount, AttributeModifier.Operation op) {
        if (attribute == null) return;
        AttributeModifier current = attribute.getModifier(id);
        if (amount == 0) {
            if (current != null) attribute.removeModifier(id);
        } else if (current == null || current.amount() != amount) {
            attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
        }
    }

    private Clothes() {
    }
}
