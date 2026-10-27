"""Crafting, after Project Zomboid: what each recipe makes, what it uses up, which tools it needs (kept), the skill and
level, and what teaches it if it isn't known from the start. Ids are roster ids (or weapons-thread ids where noted).

    (makes, uses up, tools, skill and level, learned from, note)

A tool listed as 'a | b' means either. Draft for Josiah's review before any of it is coded.
"""

RECIPES = {
    'Cooking': [
        ('Boiled water (in any pot, kettle or bottle)', 'water (tap, rain, river)', 'cooking_pot | saucepan | kettle | mil_canteen_cup', '-', '', 'on a stove, campfire or camp stove; safe to drink'),
        ('Coffee (mug)', 'instant_coffee | canned_coffee, boiled water', 'mug | coffee_pot', '-', '', 'cuts tiredness'),
        ('Tea (mug)', 'tea_bags, boiled water', 'mug', '-', '', ''),
        ('Hot cocoa (mug)', 'cocoa_mix, boiled water | milk', 'mug', '-', '', ''),
        ('Pot of stew', 'water, any 2-6 of: meat, vegetables, canned food', 'cooking_pot', 'Cooking 0', '', 'serves several; better with salt'),
        ('Pot of soup', 'water, any 2-6 of: vegetables, canned soup, pasta, rice', 'cooking_pot', 'Cooking 0', '', ''),
        ('Pot of rice / pasta', 'rice_bag | pasta_box (part), water', 'cooking_pot | saucepan', 'Cooking 0', '', ''),
        ('Fried meat or fish', 'steak | pork_chops | fish_fillets | bacon | ground_beef, cooking_oil | butter', 'cast_iron_skillet + spatula', 'Cooking 1', '', 'raw meat cooked removes the sickness risk'),
        ('Bread dough', 'flour_bag (part), water, salt_box (part)', 'bowl', 'Cooking 2', 'cookbook | mag_cooking', ''),
        ('Loaf of bread', 'bread dough', 'oven | camp oven', 'Cooking 2', 'cookbook | mag_cooking', ''),
        ('Pie', 'flour_bag (part), butter, sugar_bag (part), fruit x3', 'rolling_pin, bowl', 'Cooking 4', 'cookbook', ''),
        ('Sandwich', 'sliced_bread, any filling (lunch_meat, cheese_block, peanut_butter, jam_jar)', 'kitchen_knife (WEAPONS)', '-', '', ''),
        ('Jerky', 'raw meat or venison, salt_box (part)', 'kitchen_knife, cutting_board', 'Cooking 3', 'mag_cooking', 'keeps for ever'),
        ('Jarred vegetables', 'vegetables x5, empty_jar, sugar_bag | salt_box (part), boiled water', 'cooking_pot', 'Cooking 3', 'mag_cooking', 'keeps a year'),
        ('Butchered chicken', 'chicken_whole', 'kitchen_knife | hunting knife', 'Cooking 0', '', '4 portions'),
        ('Toasted marshmallows', 'marshmallows', 'stick, campfire', '-', '', 'mood boost'),
    ],
    'First Aid': [
        ('Ripped sheets (x2-4)', 'any cotton clothing | bed sheet', '- (scissors faster)', '-', '', 'dirty bandages'),
        ('Sterilised bandage', 'rag | ripped_sheet, boiled water | disinfectant | rubbing_alcohol', 'cooking_pot (if boiling)', 'First Aid 0', '', 'becomes a sterile_bandage'),
        ('Splint', 'plank | two_by_four | stick, rag | ripped_sheet | medical_tape', '-', 'First Aid 0', '', 'treats a fracture'),
        ('Arm sling', 'ripped_sheet x2, safety_pins', '-', 'First Aid 1', '', ''),
        ('Disinfected wound', 'disinfectant | rubbing_alcohol | peroxide | antiseptic_wipes | spirits', '-', 'First Aid 0', '', 'stops infection taking hold'),
        ('Stitched wound', 'suture_kit (part) | thread_spool (needle + thread)', 'tweezers (helps)', 'First Aid 2', '', 'closes deep wounds'),
        ('Bullet or glass removed', '-', 'tweezers | scalpel | surgical_set', 'First Aid 1 (bullet 3)', '', 'surgical_set: no failure'),
        ('Field transfusion', 'blood_bag | saline_bag', 'surgical_gloves', 'First Aid 5', 'skillbook_first_aid vol 3', 'restores lost blood'),
        ('Herbal poultice', 'plantain / comfrey (foraged), boiled water', 'bowl', 'Foraging 2', 'mag_survival', 'treats wounds like antibiotic cream'),
    ],
    'Tailoring': [
        ('Patched clothing', 'rag | ripped_sheet | denim or leather strips, thread_spool (part)', 'sewing_kit (needle)', 'Tailoring 0', '', 'repairs holes; leather/denim patch adds protection'),
        ('Leather strips', 'leather clothing | old_shoe', 'scissors | pocket_knife', '-', '', ''),
        ('Sheet rope', 'ripped_sheet x2 | bed sheet x2', '-', '-', '', 'climb out of windows'),
    ],
    'Carpentry': [
        ('Planks (x3)', 'log', 'handsaw | circular_saw', '-', '', ''),
        ('Window barricade', 'plank, nails x2', 'hammer (WEAPONS)', 'Carpentry 0', '', 'up to 4 planks per window'),
        ('Plywood barricade', 'plywood_sheet, nails x4', 'hammer', 'Carpentry 2', '', 'one sheet covers a window'),
        ('Wooden wall frame', 'two_by_four x2, nails x2', 'hammer', 'Carpentry 2', '', ''),
        ('Wooden wall', 'wall frame, plank x2, nails x4', 'hammer, saw', 'Carpentry 3', '', 'level makes it stronger'),
        ('Wooden door', 'plank x4, hinges x2, nails x4, doorknob', 'hammer, saw', 'Carpentry 4', '', ''),
        ('Crate', 'plank x3, nails x3', 'hammer, saw', 'Carpentry 1', '', 'storage'),
        ('Log wall', 'log x4, rope | sheet rope x2', '-', 'Carpentry 0', 'mag_carpentry', ''),
        ('Rain collector', 'plank x4, nails x4, tarp | plastic_sheeting', 'hammer', 'Carpentry 4', '', 'or a plastic_drum'),
        ('Spiked barricade', 'plank x2, nails x10', 'hammer', 'Carpentry 3', 'mag_carpentry', 'hurts zombies that bang on it'),
    ],
    'Metalworking': [
        ('Metal sheet', 'scrap_metal x4 | sheet_of_tin x2', 'propane_torch, welding_mask', 'Metalworking 1', '', ''),
        ('Metal barricade', 'metal_sheet | metal bars x3, welding_rods (part)', 'propane_torch, welding_mask', 'Metalworking 1', '', 'strongest window cover'),
        ('Metal wall', 'metal_sheet x2, metal_pipe x2, welding_rods (part)', 'propane_torch, welding_mask', 'Metalworking 4', '', ''),
        ('Metal crate', 'metal_sheet x2, welding_rods (part)', 'propane_torch, welding_mask', 'Metalworking 3', '', ''),
        ('Scrap armour plate', 'metal_sheet, leather strips, welding_rods (part)', 'propane_torch, welding_mask, hammer', 'Metalworking 5', 'skillbook_metalworking vol 2', 'worn over clothes (PLAYERS)'),
    ],
    'Masonry': [
        ('Cinder block wall', 'cinder_block x4, cement_bag (part), water', 'trowel', 'Masonry 2', '', ''),
        ('Sandbag', 'empty_sandbag, dirt (dig)', 'shovel (WEAPONS) | entrenching_tool', '-', '', 'stack into walls; stops bullets'),
    ],
    'Electrical': [
        ('Electronics scrap', 'any radio, phone, clock, watch, calculator, TV, pager', 'screwdriver', '-', '', 'gives electronics_scrap, sometimes a circuit_board'),
        ('Timer', 'electronics_scrap x2, alarm_clock | digital_watch', 'screwdriver', 'Electrical 1', 'mag_electronics', 'sets off a noise maker or bomb later'),
        ('Noise maker', 'alarm_clock | toy_radio | boombox, electronics_scrap', 'screwdriver', 'Electrical 1', 'mag_electronics', 'thrown or placed lure'),
        ('Remote trigger', 'two_way_radio | toy_radio, electronics_scrap x2, circuit_board', 'screwdriver', 'Electrical 3', 'mag_electronics', 'sets off traps from a distance'),
        ('Base alarm', 'electronics_scrap x2, wire_spool (part), nine_volt', 'screwdriver, pliers', 'Electrical 2', 'mag_electronics', 'sounds when a door or window is opened'),
        ('Repaired radio', 'broken_radio, electronics_scrap, circuit_board', 'screwdriver', 'Electrical 2', '', ''),
        ('Generator hooked up', 'portable_generator, extension_cord', '-', 'Electrical 3', 'skillbook electrical (planned) | instructions', 'powers a building'),
        ('Generator repaired', 'generator_parts | electronics_scrap x4', 'screwdriver, adjustable_wrench', 'Electrical 2', '', ''),
        ('Light wired', 'light_bulb, wire_spool (part)', 'screwdriver', 'Electrical 1', '', 'on generator power'),
    ],
    'Mechanics': [
        ('Wheel changed', 'spare_tire', 'car_jack, tire_iron', 'Mechanics 1', '', ''),
        ('Engine serviced', 'motor_oil, oil_filter, spark_plugs', 'socket_set | mechanic_toolbox', 'Mechanics 3', 'mag_mechanics (per car model)', 'raises engine condition'),
        ('Car part repaired (doors, hood, tank, radiator...)', 'metal_sheet | radiator_hose | fan_belt | brake_fluid', 'adjustable_wrench | mechanic_toolbox, propane_torch for bodywork', 'Mechanics 2-5', 'mag_mechanics', ''),
        ('Car hot-wired', 'electronics_scrap', 'screwdriver', 'Electrical 1 + Mechanics 2', '', 'starts a car without its key (car_key never loot)'),
        ('Fuel siphoned', 'gas_can | water_jug_empty', 'fuel_siphon | rubber hose', '-', '', 'from parked cars'),
        ('Aircraft part repaired', 'hydraulic_fluid, metal_sheet', 'mechanic_toolbox', 'Mechanics 5', 'maintenance_cards', 'AIRCRAFT thread'),
    ],
    'Survival, Trapping, Fishing, Farming': [
        ('Campfire', 'firewood | planks | sticks x4, tinder (paper, newspaper, cardboard)', 'matches | lighter | firestarter', '-', '', ''),
        ('Fire by friction', 'stick x2, tinder', '-', 'Survival', 'mag_survival', 'when out of matches'),
        ('Water filter', 'empty water bottle, charcoal, sand, cloth', 'pocket_knife', 'Survival', 'mag_survival', 'cleans river water'),
        ('Snare', 'snare_wire | paracord, stick x2', '-', 'Trapping 1', '', 'small game'),
        ('Box trap', 'plank x2, nails x2, wire', 'hammer', 'Trapping 2', 'mag_hunting', ''),
        ('Spear', 'stick | broom handle, kitchen knife | rebar_rod, duct_tape | rope', '-', '-', '', 'WEAPONS owns weapon recipes'),
        ('Fishing rod (crafted)', 'stick, thread_spool | paracord, bone hook (bone_scrap)', 'pocket_knife', 'Fishing 0', 'mag_survival', ''),
        ('Bait', 'worms (dig with garden_trowel) | spoiled_food | canned cat/dog food', '-', '-', '', ''),
        ('Fertilizer (compost)', 'spoiled_food x5', 'compost bin', 'Farming 1', 'mag_farming', ''),
        ('Pesticide', 'water, dish_soap | tobacco', 'spray bottle', 'Farming 2', 'mag_farming', 'saves crops from blight'),
        ('Seed potatoes', 'potato', 'kitchen_knife', 'Farming 0', '', 'cut a potato to plant'),
        ('Noise-maker tripwire', 'tin_can | empty_soda_can x3, string | wire_spool', '-', 'Trapping 0', '', 'rattles when walked into'),
    ],
    'Scrap and repair': [
        ('Weapon repaired', 'duct_tape | superglue | wood_glue | electrical_tape', '-', 'Maintenance', '', 'WEAPONS uses these for melee upkeep'),
        ('Bag repaired', 'duct_tape | thread_spool', 'sewing_kit', 'Tailoring 1', '', ''),
        ('Dismantled furniture', 'furniture', 'handsaw | hammer | screwdriver', 'Carpentry', '', 'gives planks, nails, scrap'),
        ('Dismantled metal item', 'metal furniture, signs, appliances', 'propane_torch + welding_mask | hacksaw', 'Metalworking', '', 'gives scrap_metal, metal_sheet, pipe'),
    ],
}


def rows():
    for skill, recs in RECIPES.items():
        for r in recs:
            yield skill, r
