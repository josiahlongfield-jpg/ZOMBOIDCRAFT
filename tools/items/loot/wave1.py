"""Wave 1 game data: what eating or drinking each item does and how fast it goes off.

    id: 'hunger sat thirst | fresh rot | flags'
  hunger, sat   vanilla food points (a bread loaf is 5 / 6.0); 0 0 for drinks that don't fill you
  thirst        how much of the thirst meter it puts back (100 = full)
  fresh, rot    days until it turns stale, and until it is rotten (0 0 = keeps). For 'cold' food these are the days
                it lasts in a fridge; out of one it goes off COLD_OUT times faster, and a freezer all but stops it
  flags         drink (drinking, not eating), cold (needs the fridge), raw (can make you sick until cooked),
                always (can be had on a full stomach), can / bottle / jar / carton (what's left when it's finished)

Items not listed here (medical, hygiene) are plain items until their treatments land (UI thread, Treatment code).
"""

COLD_OUT = 3.0          # how much faster fridge food goes off left out
FRIDGE = 0.5            # non-cold food in a fridge keeps twice as long
FREEZER = 0.03

FOOD = {
    # canned and dry: keeps for ever, the can is left over
    'canned_beans': '6 7.2 4 | 0 0 | can',
    'canned_soup': '5 5.0 12 | 0 0 | can',
    'canned_chili': '7 8.4 3 | 0 0 | can',
    'canned_corn': '4 4.0 4 | 0 0 | can',
    'canned_peas': '4 4.0 4 | 0 0 | can',
    'canned_carrots': '4 4.0 4 | 0 0 | can',
    'canned_tomatoes': '3 3.0 8 | 0 0 | can',
    'canned_peaches': '4 3.6 10 | 0 0 | can',
    'canned_pineapple': '4 3.6 10 | 0 0 | can',
    'canned_tuna': '5 7.0 0 | 0 0 | can',
    'canned_sardines': '4 6.0 0 | 0 0 | can',
    'canned_ham': '10 14.0 0 | 0 0 | can',
    'canned_corned_beef': '8 11.0 0 | 0 0 | can',
    'canned_spaghetti': '5 5.0 4 | 0 0 | can',
    'canned_ravioli': '6 6.0 4 | 0 0 | can',
    'canned_stew': '7 8.4 6 | 0 0 | can',
    'canned_dog_food': '4 3.0 2 | 0 0 | can',
    'canned_cat_food': '2 1.6 1 | 0 0 | can',
    'canned_condensed_milk': '5 4.0 6 | 0 0 | can',
    'canned_coffee': '0 0 0 | 0 0 |',
    'peanut_butter': '8 9.6 -6 | 0 0 | jar',
    'jam_jar': '5 3.0 0 | 0 0 | jar',
    'honey_jar': '6 4.8 0 | 0 0 | jar',
    'pickles_jar': '2 1.2 4 | 0 0 | jar',
    'home_preserves': '5 4.0 2 | 0 0 | jar',
    'cereal_box': '6 6.0 -3 | 0 0 |',
    'oats_box': '6 7.2 -3 | 0 0 |',
    'pasta_box': '4 3.0 -4 | 0 0 |',
    'rice_bag': '5 4.0 -4 | 0 0 |',
    'flour_bag': '2 1.0 -6 | 0 0 |',
    'sugar_bag': '2 0.4 -4 | 0 0 |',
    'salt_box': '0 0 0 | 0 0 |',
    'cooking_oil': '2 2.0 -2 | 0 0 | bottle',
    'crackers_box': '4 3.6 -4 | 0 0 |',
    'instant_noodles': '4 3.0 -2 | 0 0 |',
    'mac_and_cheese': '5 4.0 -2 | 0 0 |',
    'powdered_milk': '4 3.0 -2 | 0 0 |',
    'dried_beans': '3 2.0 -4 | 0 0 |',
    'beef_jerky': '5 6.0 -6 | 0 0 |',
    'trail_mix': '4 4.8 -3 | 0 0 |',
    'mre': '12 16.0 4 | 0 0 |',
    'ration_bar': '10 14.0 -4 | 0 0 |',
    'survival_bucket': '20 20.0 0 | 0 0 |',
    'baby_formula': '3 2.0 6 | 0 0 |',
    # fresh
    'apple': '4 2.4 6 | 6 12 |',
    'orange': '4 2.4 10 | 6 12 |',
    'banana': '4 2.4 2 | 3 6 |',
    'lettuce': '2 1.0 6 | 3 6 |',
    'tomato': '2 1.2 6 | 4 8 |',
    'potato': '2 1.0 0 | 20 40 | raw',
    'onion': '1 0.6 0 | 20 40 |',
    'carrot': '3 2.4 2 | 8 16 |',
    'cabbage': '3 2.0 4 | 8 16 |',
    'eggs_carton': '6 6.0 0 | 10 20 | cold raw',
    'milk_carton': '4 3.0 25 | 4 7 | cold drink carton',
    'orange_juice': '3 1.8 30 | 5 9 | cold drink carton',
    'butter': '3 2.0 -2 | 14 28 | cold',
    'cheese_block': '6 7.2 -2 | 10 20 | cold',
    'sliced_bread': '5 6.0 -3 | 4 8 |',
    'hot_dogs': '6 6.0 0 | 5 9 | cold',
    'bacon': '6 7.2 0 | 4 7 | cold raw',
    'lunch_meat': '5 6.0 0 | 4 7 | cold',
    'ground_beef': '6 7.2 0 | 2 4 | cold raw',
    'steak': '8 12.8 0 | 2 4 | cold raw',
    'chicken_whole': '12 14.0 0 | 2 4 | cold raw',
    'pork_chops': '8 12.8 0 | 2 4 | cold raw',
    'fish_fillets': '6 7.2 0 | 2 4 | cold raw',
    'frozen_pizza': '8 9.6 -2 | 1 3 | cold',
    'frozen_dinner': '7 8.4 0 | 1 3 | cold',
    'ice_cream': '4 2.4 6 | 1 2 | cold always',
    'venison': '8 12.8 0 | 2 4 | cold raw',
    'leftovers': '5 5.0 2 | 2 4 | cold',
    # snacks
    'chips_bag': '3 1.8 -3 | 0 0 |',
    'pretzels': '3 1.8 -4 | 0 0 |',
    'popcorn_bag': '2 1.2 -3 | 0 0 |',
    'microwave_popcorn': '2 1.2 -3 | 0 0 |',
    'chocolate_bar': '3 1.8 -1 | 0 0 | always',
    'peanut_candy_bar': '3 2.4 -1 | 0 0 | always',
    'gum_pack': '0 0 2 | 0 0 | always',
    'hard_candy': '1 0.2 1 | 0 0 | always',
    'licorice': '2 0.6 -1 | 0 0 | always',
    'cookies_pack': '4 2.4 -2 | 0 0 |',
    'snack_cakes': '3 1.8 -1 | 0 0 |',
    'granola_bar': '3 3.6 -1 | 0 0 |',
    'crackers_cheese': '3 2.4 -2 | 0 0 |',
    'beef_stick': '2 2.4 -2 | 0 0 |',
    'peanuts_jar': '6 7.2 -4 | 0 0 | jar',
    'sunflower_seeds': '2 1.6 -3 | 0 0 |',
    'marshmallows': '2 0.8 -1 | 0 0 |',
    'candy_box': '2 0.8 -1 | 0 0 | always',
    'nachos_tray': '4 3.0 -3 | 2 4 |',
    'pie_slice': '4 3.2 0 | 2 4 |',
    'donut_box': '6 3.6 -2 | 3 6 |',
    'birthday_cake': '10 6.0 0 | 2 4 | cold',
    'fancy_chocolates': '4 2.4 0 | 0 0 | always',
    # drinks
    'water_bottle': '0 0 40 | 0 0 | drink always bottle',
    'water_jug': '0 0 100 | 0 0 | drink always',
    'cola_can': '1 0.4 25 | 0 0 | drink always drinkcan',
    'lemon_soda_can': '1 0.4 25 | 0 0 | drink always drinkcan',
    'root_beer_can': '1 0.4 25 | 0 0 | drink always drinkcan',
    'soda_bottle': '2 0.8 60 | 0 0 | drink always',
    'glass_soda': '1 0.4 25 | 0 0 | drink always bottle',
    'sports_drink': '1 0.4 40 | 0 0 | drink always bottle',
    'juice_box': '1 0.6 15 | 0 0 | drink always',
    'iced_tea': '1 0.4 30 | 0 0 | drink always bottle',
    'coffee_thermos': '0 0 25 | 0 0 | drink always',
    'instant_coffee': '0 0 0 | 0 0 |',
    'tea_bags': '0 0 0 | 0 0 |',
    'cocoa_mix': '0 0 0 | 0 0 |',
    'canned_beer': '1 0.4 15 | 0 0 | drink always drinkcan',
    'beer_six_pack': '0 0 0 | 0 0 |',
    'bottled_beer': '1 0.4 15 | 0 0 | drink always bottle',
    'wine_bottle': '1 0.4 5 | 0 0 | drink always bottle',
    'boxed_wine': '1 0.4 5 | 0 0 | drink always',
    'vodka': '0 0 -10 | 0 0 | drink always bottle',
    'whiskey': '0 0 -10 | 0 0 | drink always bottle',
    'rum': '0 0 -10 | 0 0 | drink always bottle',
    'moonshine_jar': '0 0 -15 | 0 0 | drink always jar',
    'hip_flask': '0 0 -5 | 0 0 | drink always',
    'aged_whiskey': '0 0 -10 | 0 0 | drink always bottle',
    'vintage_wine': '1 0.4 5 | 0 0 | drink always bottle',
    'champagne': '1 0.4 8 | 0 0 | drink always bottle',
}

# multi-serving packs split rather than eaten whole (opened in the hand: six-pack into six cans)
SPLITS = {'beer_six_pack': ('canned_beer', 6)}
# alcohol: drunk effects (nausea at the third)
ALCOHOL = {'canned_beer': 1, 'bottled_beer': 1, 'wine_bottle': 2, 'boxed_wine': 2, 'vodka': 3, 'whiskey': 3,
           'rum': 3, 'moonshine_jar': 4, 'hip_flask': 2, 'aged_whiskey': 3, 'vintage_wine': 2, 'champagne': 2}

WAVE1 = ('Canned & dry food', 'Fresh, fridge & freezer', 'Snacks & sweets', 'Drinks', 'Medical', 'Hygiene & household')

# leftovers that become their own items in wave 1 (junk wave rows, built now because the food needs them)
REMAINDERS = {'can': 'empty_can', 'drinkcan': 'empty_soda_can', 'bottle': 'empty_bottle', 'jar': 'empty_jar',
              'carton': None}


def parse(spec):
    nums, days, flags = [s.strip() for s in spec.split('|')]
    hunger, sat, thirst = nums.split()
    fresh, rot = days.split()
    return dict(hunger=int(hunger), sat=float(sat), thirst=float(thirst), fresh=float(fresh), rot=float(rot),
                flags=set(flags.split()))
