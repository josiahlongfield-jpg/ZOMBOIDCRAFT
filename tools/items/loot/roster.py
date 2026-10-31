"""The world loot roster: every non-weapon item that can turn up in a container, a car or on a body.

One row per item:  id | Name | rarity | value | kg | where
  rarity  C Common, S Scarce, R Rare, P Prized, G Grail (the weapons thread's five, combat/rolls/Rarity)
  value   barter worth, 1 = a can of beans
  where   place tags (PLACES below); a tag with a suffix .n weights it (2 = twice as likely there)

Weapons, attachments, ammo and gun upkeep (cleaning kits, reloading, holsters, slings) belong to the weapons thread
and are not listed here. Items that already exist in the mod are marked * after the id.

    python3 tools/items/loot/roster.py            # prints counts
"""

PLACES = {
    # shops and services
    'groc': 'Grocery', 'conv': 'Corner store / station kiosk', 'pharm': 'Pharmacy', 'hard': 'Hardware store',
    'gun': 'Gun shop', 'pawn': 'Pawn shop', 'liq': 'Liquor store', 'cloth': 'Clothes shop', 'thrift': 'Thrift store',
    'laund': 'Laundromat', 'diner': 'Diner / restaurant kitchen', 'candy': 'Theatre / stadium concessions',
    'elec': 'Electronics store (planned)', 'books': 'Bookshop / library', 'sport': 'Sports & outdoor store (planned)',
    'auto': 'Auto parts / mechanic (planned)', 'jewel': 'Jeweller (planned)', 'toyshop': 'Toy store (planned)',
    'office': 'Offices (desks, filing)', 'bank': 'Bank (tellers, vault, deposit boxes)', 'post': 'Post office',
    'school': 'School (classrooms, lockers)', 'gym': 'Gym / locker rooms', 'church': 'Church', 'court': 'Courthouse / town hall',
    'hosp': 'Hospital / clinic / ambulance / sick bay (clinic planned)', 'police': 'Police / sheriff', 'fire': 'Fire station',
    'station': 'Train & subway stations (ticket office, lost property, staff lockers)',
    'stadium': 'Stadium (pro shop, offices, locker rooms)',
    # homes, by container
    'pantry': 'Home kitchen cupboards & pantry', 'fridge': 'Home fridge', 'freez': 'Home freezer',
    'bath': 'Bathroom & medicine cabinet', 'bed': 'Bedroom wardrobe & dresser', 'night': 'Nightstand',
    'desk': 'Home desk & bookshelf', 'garage': 'Home garage & tool bench', 'kids': "Kids' room & toy box",
    'attic': 'Boxes, attic & storage', 'trash': 'Bins & trash', 'mansion': 'Mansions (planned): safes, cellars, studies',
    'trailer': 'Trailer homes (planned)',
    # rural, industrial, military
    'farm': 'Farm (barn, shed, farmhouse)', 'vet': 'Vet clinic (planned)', 'till': 'Shop tills & cash drawers', 'camp': 'Campsites & hunting cabins', 'build': 'Construction sites & work trucks',
    'factory': 'Factories & warehouses', 'dock': 'Docks & cargo ships', 'mil': 'Military (base, armoury, carrier)',
    'carrier': 'Aircraft carrier (galley, berths, hangar, sick bay)', 'bunker': 'Bunkers & shelters',
    # vehicles and bodies
    'glove': 'Car glovebox', 'trunk': 'Car trunk', 'pcar': 'Police car', 'amb': 'Ambulance', 'firetruck': 'Fire truck',
    'bus': 'School / city bus', 'worktruck': 'Work truck / van', 'body': 'On zombies (matched to their outfit)',
}

R = {'C': 'Common', 'S': 'Scarce', 'R': 'Rare', 'P': 'Prized', 'G': 'Grail'}

ROSTER = {}

ROSTER['Canned & dry food'] = """
canned_beans | Canned Beans | C | 1 | 0.45 | pantry.3 groc.3 bunker conv trunk
canned_soup | Canned Soup | C | 1 | 0.4 | pantry.3 groc.3 conv bunker
canned_chili | Canned Chili | C | 2 | 0.45 | pantry groc camp bunker
canned_corn | Canned Corn | C | 1 | 0.4 | pantry groc
canned_peas | Canned Peas | C | 1 | 0.4 | pantry groc
canned_carrots | Canned Carrots | C | 1 | 0.4 | pantry groc
canned_tomatoes | Canned Tomatoes | C | 1 | 0.4 | pantry groc diner
canned_peaches | Canned Peaches | C | 2 | 0.45 | pantry groc
canned_pineapple | Canned Pineapple | C | 2 | 0.45 | pantry groc
canned_tuna | Canned Tuna | C | 2 | 0.2 | pantry groc conv
canned_sardines | Canned Sardines | C | 2 | 0.15 | pantry groc dock carrier
canned_ham | Canned Ham | S | 4 | 0.9 | pantry groc bunker
canned_corned_beef | Canned Corned Beef | S | 3 | 0.35 | pantry groc bunker carrier
canned_spaghetti | Canned Spaghetti Rings | C | 1 | 0.4 | pantry groc kids
canned_ravioli | Canned Ravioli | C | 2 | 0.45 | pantry groc
canned_stew | Canned Beef Stew | C | 2 | 0.45 | pantry groc camp
canned_dog_food | Canned Dog Food | C | 1 | 0.4 | pantry groc farm
canned_cat_food | Canned Cat Food | C | 1 | 0.15 | pantry groc
canned_condensed_milk | Condensed Milk | S | 2 | 0.4 | pantry groc bunker
canned_coffee | Can of Ground Coffee | S | 4 | 0.5 | pantry groc diner office
peanut_butter | Peanut Butter | C | 3 | 0.5 | pantry groc school
jam_jar | Jar of Jam | C | 2 | 0.4 | pantry groc
honey_jar | Jar of Honey | S | 4 | 0.5 | pantry groc farm
pickles_jar | Jar of Pickles | C | 1 | 0.9 | pantry groc diner
home_preserves | Home Preserves | S | 3 | 0.6 | farm.3 pantry attic
cereal_box | Box of Cereal | C | 2 | 0.4 | pantry.3 groc.3 kids
oats_box | Rolled Oats | C | 2 | 0.5 | pantry groc camp
pasta_box | Dry Pasta | C | 2 | 0.5 | pantry groc diner
rice_bag | Bag of Rice | C | 3 | 1.0 | pantry groc diner bunker
flour_bag | Bag of Flour | C | 2 | 2.3 | pantry groc diner farm
sugar_bag | Bag of Sugar | C | 2 | 1.0 | pantry groc diner
salt_box | Salt | C | 1 | 0.7 | pantry groc diner
cooking_oil | Cooking Oil | C | 2 | 0.9 | pantry groc diner
crackers_box | Box of Crackers | C | 1 | 0.3 | pantry groc school
instant_noodles | Instant Noodles | C | 1 | 0.1 | pantry groc conv school
mac_and_cheese | Boxed Mac & Cheese | C | 1 | 0.2 | pantry groc kids
powdered_milk | Powdered Milk | S | 3 | 0.5 | pantry groc bunker
dried_beans | Bag of Dried Beans | C | 2 | 0.9 | pantry groc farm
beef_jerky | Beef Jerky | S | 3 | 0.1 | conv groc glove camp
trail_mix | Trail Mix | C | 2 | 0.2 | conv groc camp trunk
mre | Ration Meal Pack | R | 6 | 0.6 | mil.3 carrier.2 bunker.2 body
ration_bar | Emergency Ration Bar | R | 5 | 0.4 | bunker.3 mil dock
survival_bucket | Sealed Survival Food Bucket | P | 30 | 11.0 | bunker.3 mansion
baby_formula | Baby Formula | S | 3 | 0.6 | groc pharm
"""

ROSTER['Fresh, fridge & freezer'] = """
apple | Apple | C | 1 | 0.2 | fridge groc school farm
orange | Orange | C | 1 | 0.2 | fridge groc school
banana | Banana | C | 1 | 0.15 | fridge groc school
lettuce | Head of Lettuce | C | 1 | 0.5 | fridge groc farm
tomato | Tomato | C | 1 | 0.15 | fridge groc farm
potato | Potato | C | 1 | 0.2 | pantry groc farm
onion | Onion | C | 1 | 0.15 | pantry groc farm
carrot | Carrot | C | 1 | 0.1 | fridge groc farm
cabbage | Cabbage | C | 1 | 0.9 | groc farm
eggs_carton | Carton of Eggs | C | 2 | 0.7 | fridge groc farm diner
milk_carton | Carton of Milk | C | 1 | 1.0 | fridge.3 groc diner school
orange_juice | Carton of Orange Juice | C | 1 | 1.0 | fridge groc diner
butter | Butter | C | 1 | 0.25 | fridge groc diner
cheese_block | Block of Cheese | C | 2 | 0.4 | fridge groc
sliced_bread | Loaf of Sliced Bread | C | 1 | 0.6 | pantry groc diner
hot_dogs | Pack of Hot Dogs | C | 2 | 0.45 | fridge groc candy stadium
bacon | Pack of Bacon | C | 2 | 0.45 | fridge groc diner
lunch_meat | Lunch Meat | C | 2 | 0.3 | fridge groc
ground_beef | Ground Beef | C | 2 | 0.5 | fridge freez groc diner
steak | Steak | S | 4 | 0.4 | fridge freez groc diner
chicken_whole | Whole Chicken | C | 3 | 1.6 | freez groc farm
pork_chops | Pork Chops | C | 3 | 0.5 | freez groc
fish_fillets | Frozen Fish Fillets | C | 2 | 0.4 | freez groc dock carrier
frozen_pizza | Frozen Pizza | C | 2 | 0.5 | freez groc
frozen_dinner | TV Dinner | C | 2 | 0.35 | freez groc
ice_cream | Tub of Ice Cream | C | 1 | 0.5 | freez groc candy
venison | Venison | S | 4 | 1.0 | freez camp.3 farm
leftovers | Tub of Leftovers | C | 0 | 0.4 | fridge.3
"""

ROSTER['Snacks & sweets'] = """
chips_bag | Bag of Potato Chips | C | 1 | 0.2 | conv.3 groc candy stadium school
pretzels | Bag of Pretzels | C | 1 | 0.2 | conv groc candy
popcorn_bag | Bag of Popcorn | C | 1 | 0.1 | candy.3 pantry
microwave_popcorn | Microwave Popcorn | C | 1 | 0.1 | pantry groc office
chocolate_bar | Chocolate Bar | C | 1 | 0.05 | conv.3 candy groc glove school
peanut_candy_bar | Peanut Candy Bar | C | 1 | 0.06 | conv candy glove
gum_pack | Pack of Gum | C | 1 | 0.02 | conv candy glove school
hard_candy | Bag of Hard Candy | C | 1 | 0.2 | conv candy kids church
licorice | Licorice Rope | C | 1 | 0.1 | candy conv
cookies_pack | Pack of Cookies | C | 1 | 0.3 | pantry groc conv kids
snack_cakes | Snack Cakes | C | 1 | 0.1 | conv groc school
granola_bar | Granola Bar | C | 1 | 0.05 | conv groc camp gym
crackers_cheese | Cheese Crackers | C | 1 | 0.1 | conv school office
beef_stick | Beef Stick | C | 1 | 0.03 | conv glove
peanuts_jar | Jar of Roasted Peanuts | C | 2 | 0.45 | pantry groc stadium
sunflower_seeds | Sunflower Seeds | C | 1 | 0.1 | conv stadium
marshmallows | Marshmallows | C | 1 | 0.3 | pantry groc camp
candy_box | Box of Theatre Candy | C | 1 | 0.1 | candy.3
nachos_tray | Nacho Tray | C | 1 | 0.3 | candy stadium.3
pie_slice | Slice of Pie | C | 1 | 0.2 | diner.3
donut_box | Box of Donuts | C | 2 | 0.5 | police.3 office diner
birthday_cake | Birthday Cake | S | 2 | 1.5 | fridge
fancy_chocolates | Box of Fancy Chocolates | S | 4 | 0.3 | night mansion pharm
"""

ROSTER['Drinks'] = """
water_bottle | Bottle of Water | C | 2 | 0.55 | conv groc gym office fridge
water_jug | Gallon of Water | S | 5 | 4.0 | groc bunker.3 pantry
cola_can | Can of Cola | C | 1 | 0.37 | conv.3 groc fridge candy stadium
lemon_soda_can | Can of Lemon-Lime Soda | C | 1 | 0.37 | conv groc fridge
root_beer_can | Can of Root Beer | C | 1 | 0.37 | conv groc diner
soda_bottle | Two-Litre Soda | C | 2 | 2.1 | groc pantry
glass_soda | Glass Bottle of Soda | S | 2 | 0.5 | diner conv attic
sports_drink | Sports Drink | C | 2 | 0.6 | conv gym.3 stadium
juice_box | Juice Box | C | 1 | 0.2 | kids school.3 groc
iced_tea | Bottled Iced Tea | C | 1 | 0.5 | conv groc
coffee_thermos | Thermos of Coffee | S | 3 | 1.0 | worktruck build fire office
instant_coffee | Jar of Instant Coffee | C | 2 | 0.2 | pantry office carrier
tea_bags | Box of Tea Bags | C | 1 | 0.1 | pantry office church
cocoa_mix | Hot Cocoa Mix | C | 1 | 0.3 | pantry camp
canned_beer | Can of Beer | C | 2 | 0.37 | fridge.3 liq conv stadium
beer_six_pack | Six-Pack of Beer | C | 9 | 2.2 | liq conv fridge
bottled_beer | Bottle of Beer | C | 2 | 0.5 | liq fridge diner
wine_bottle | Bottle of Wine | S | 6 | 1.2 | liq pantry church
boxed_wine | Boxed Wine | C | 4 | 3.0 | liq pantry trailer
vodka | Bottle of Vodka | S | 8 | 1.1 | liq pantry
whiskey | Bottle of Whiskey | S | 10 | 1.1 | liq desk office
rum | Bottle of Rum | S | 8 | 1.1 | liq dock
moonshine_jar | Jar of Moonshine | R | 10 | 1.0 | farm.3 camp trailer
hip_flask | Hip Flask | S | 6 | 0.3 | night desk glove body
aged_whiskey | 18-Year Single Malt | P | 45 | 1.3 | mansion.3 liq office
vintage_wine | Old Bordeaux, 1961 | G | 160 | 1.3 | mansion.3
champagne | Bottle of Champagne | R | 18 | 1.6 | liq mansion
"""

ROSTER['Kitchen & cooking'] = """
can_opener | Can Opener | C | 3 | 0.15 | pantry.3 diner camp trunk
bottle_opener | Bottle Opener | C | 1 | 0.05 | pantry liq diner
cooking_pot | Cooking Pot | C | 4 | 1.5 | pantry diner camp
saucepan | Saucepan | C | 3 | 0.9 | pantry diner
kettle | Kettle | C | 3 | 1.0 | pantry office
mug | Coffee Mug | C | 1 | 0.35 | pantry office diner
plate | Plate | C | 1 | 0.5 | pantry diner
bowl | Bowl | C | 1 | 0.35 | pantry diner
cutlery | Fork and Spoon | C | 1 | 0.1 | pantry diner school
rolling_pin | Rolling Pin | C | 2 | 0.6 | pantry diner
spatula | Spatula | C | 1 | 0.1 | pantry diner
ladle | Ladle | C | 1 | 0.15 | pantry diner carrier
cutting_board | Cutting Board | C | 2 | 0.8 | pantry diner
tupperware | Food Container | C | 1 | 0.15 | pantry office
water_jug_empty | Empty Water Jug | C | 1 | 0.2 | pantry garage farm
canteen | Canteen | S | 5 | 0.4 | camp mil.2 body
thermos | Empty Thermos | C | 2 | 0.5 | pantry worktruck
camp_stove | Camping Stove | R | 18 | 1.5 | camp.3 garage sport
stove_fuel | Camp Stove Fuel Canister | S | 4 | 0.4 | camp garage sport hard
mess_kit | Mess Kit | S | 5 | 0.5 | camp mil carrier
aluminium_foil | Roll of Foil | C | 1 | 0.3 | pantry diner
plastic_wrap | Plastic Wrap | C | 1 | 0.3 | pantry
paper_towels | Paper Towels | C | 1 | 0.3 | pantry groc office
cast_iron_skillet | Cast-Iron Skillet | S | 6 | 2.3 | pantry farm camp
coffee_pot | Coffee Percolator | S | 4 | 0.9 | camp pantry office
"""

ROSTER['Medical'] = """
bandage* | Bandage | C | 3 | 0.05 | bath pharm hosp amb
sterile_bandage | Sterile Bandage | S | 6 | 0.05 | pharm hosp.3 amb.3 mil
adhesive_bandages | Adhesive Bandages | C | 2 | 0.05 | bath.3 pharm school office glove
gauze_pads | Gauze Pads | C | 3 | 0.05 | pharm hosp amb
rag | Rag | C | 0 | 0.05 | garage laund trash attic
ripped_sheet | Ripped Sheet | C | 0 | 0.05 | bed laund
splint* | Splint | S | 5 | 0.3 | hosp amb fire
arm_sling | Arm Sling | S | 4 | 0.1 | hosp amb pharm
elastic_wrap | Elastic Bandage Wrap | C | 3 | 0.1 | pharm gym.2 bath
medical_tape | Medical Tape | C | 2 | 0.05 | pharm hosp amb
disinfectant | Bottle of Disinfectant | S | 6 | 0.3 | pharm hosp amb bath
rubbing_alcohol | Rubbing Alcohol | C | 4 | 0.5 | bath.3 pharm hosp
peroxide | Hydrogen Peroxide | C | 3 | 0.5 | bath pharm
antiseptic_wipes | Antiseptic Wipes | C | 2 | 0.05 | pharm hosp office glove
suture_kit | Suture Needle & Thread | R | 12 | 0.05 | hosp.3 amb vet mil
tweezers | Tweezers | C | 2 | 0.02 | bath pharm hosp
burn_ointment | Burn Ointment | S | 5 | 0.1 | pharm hosp fire.3 diner
antibiotic_cream | Antibiotic Cream | S | 5 | 0.05 | pharm bath hosp
painkillers | Painkillers | C | 4 | 0.1 | bath.3 pharm office desk
strong_painkillers | Prescription Painkillers | R | 14 | 0.1 | pharm.2 hosp bath
antibiotics | Antibiotics | R | 16 | 0.05 | pharm hosp.3 mil
sleeping_pills | Sleeping Pills | S | 6 | 0.05 | night.3 pharm bath
beta_blockers | Beta Blockers | R | 10 | 0.05 | pharm hosp night
antidepressants | Antidepressants | S | 6 | 0.05 | pharm bath night
vitamins | Vitamins | C | 3 | 0.2 | bath pharm groc gym
cold_medicine | Cold Medicine | C | 3 | 0.25 | bath pharm
antacids | Antacids | C | 1 | 0.1 | bath pharm diner
allergy_pills | Allergy Pills | C | 2 | 0.05 | bath pharm
inhaler | Asthma Inhaler | S | 6 | 0.05 | pharm school body
insulin_kit | Insulin Pen | R | 12 | 0.1 | fridge pharm hosp
thermometer | Thermometer | C | 2 | 0.05 | bath pharm hosp school
cold_pack | Instant Cold Pack | C | 2 | 0.2 | gym.3 pharm stadium amb
blood_pressure_cuff | Blood Pressure Cuff | S | 5 | 0.5 | hosp pharm
stethoscope | Stethoscope | S | 6 | 0.2 | hosp.3 amb body
scalpel | Scalpel | S | 6 | 0.02 | hosp.3 vet
surgical_gloves | Box of Surgical Gloves | C | 2 | 0.2 | hosp amb pharm diner
face_masks | Box of Face Masks | C | 2 | 0.2 | hosp pharm build
first_aid_kit | First Aid Kit | S | 15 | 0.8 | office school.2 trunk station factory
trauma_kit | Paramedic Trauma Bag | R | 40 | 3.0 | amb.3 hosp fire
ifak | Military First Aid Pouch | R | 30 | 0.4 | mil.3 carrier bunker body
tourniquet | Tourniquet | R | 10 | 0.1 | mil amb police
morphine_autoinjector | Morphine Autoinjector | P | 45 | 0.05 | mil.3 carrier bunker
saline_bag | IV Saline Bag | R | 14 | 1.1 | hosp.3 amb
blood_bag | Blood Bag | P | 40 | 0.5 | hosp.3
adrenaline_pen | Adrenaline Pen | R | 18 | 0.05 | pharm school hosp
field_surgery_kit | Field Surgery Kit | G | 120 | 2.5 | mil carrier.2 bunker
portable_defib | Portable Defibrillator | P | 60 | 3.0 | hosp.3 amb stadium office
surgical_set | Surgical Instrument Set | P | 55 | 2.0 | hosp.3 vet
body_bag | Body Bag | C | 3 | 1.5 | hosp amb police.2 mil
vet_meds | Veterinary Antibiotics | S | 10 | 0.2 | farm.3
cotton_swabs | Cotton Swabs | C | 1 | 0.05 | bath pharm hosp
cough_syrup | Cough Syrup | C | 3 | 0.3 | bath pharm
eye_drops | Eye Drops | C | 2 | 0.05 | bath pharm
nasal_spray | Nasal Spray | C | 2 | 0.05 | bath pharm
hydrocortisone | Hydrocortisone Cream | C | 3 | 0.05 | bath pharm
butterfly_closures | Butterfly Closures | C | 3 | 0.02 | bath pharm office
finger_splint | Finger Splint | C | 2 | 0.05 | pharm school
rehydration_salts | Oral Rehydration Salts | C | 4 | 0.1 | pharm hosp mil
glucose_gel | Glucose Gel | C | 3 | 0.05 | pharm school gym
iodine | Iodine Tincture | C | 4 | 0.1 | pharm hosp mil
medical_scissors | Medical Scissors | C | 3 | 0.1 | hosp amb pharm
pill_organiser | Pill Organiser | C | 2 | 0.1 | bath
crutches | Crutches | S | 10 | 2.0 | hosp pharm
walking_cane | Walking Cane | S | 6 | 0.6 | bed thrift hosp
knee_brace | Knee Brace | S | 8 | 0.3 | gym pharm
neck_collar | Neck Collar | S | 12 | 0.3 | amb hosp
moldable_splint | Moldable Splint | S | 12 | 0.2 | amb mil fire
pulse_oximeter | Pulse Oximeter | S | 10 | 0.05 | hosp amb
dental_kit | Dental Kit | S | 10 | 0.3 | hosp pharm
anti_nausea | Anti-Nausea Pills | S | 6 | 0.05 | pharm hosp
local_anaesthetic | Local Anaesthetic | S | 12 | 0.1 | hosp vet
wheelchair | Folding Wheelchair | S | 25 | 12.0 | hosp
hot_water_bottle | Hot Water Bottle | S | 4 | 0.3 | bed bath
hemostatic_gauze | Hemostatic Gauze | R | 25 | 0.05 | mil amb police
chest_seal | Chest Seal | R | 25 | 0.05 | mil amb police
airway_kit | Airway Kit | R | 20 | 0.3 | amb hosp mil
iv_kit | IV Starter Kit | R | 25 | 0.2 | hosp amb
bag_valve_mask | Bag-Valve Mask | R | 22 | 0.5 | amb hosp fire
stretcher | Folding Stretcher | R | 30 | 7.0 | amb hosp mil
oxygen_cylinder | Oxygen Cylinder | R | 35 | 3.5 | hosp amb fire
antivirals | Antiviral Pills | R | 30 | 0.05 | hosp pharm
wound_stapler | Wound Stapler | R | 30 | 0.2 | hosp amb mil
field_medic_manual | Field Medic's Manual | R | 15 | 0.4 | mil hosp
burn_kit | Burn Kit | R | 25 | 0.4 | fire hosp
drug_guide | Physician's Drug Guide | R | 20 | 1.0 | hosp books
trauma_bag | Medic's Trauma Bag | P | 70 | 4.0 | amb fire
ultrasound | Portable Ultrasound | P | 80 | 2.5 | hosp
blood_typing_kit | Blood Typing Kit | P | 45 | 0.3 | hosp
forward_surgical_case | Forward Surgical Case | G | 150 | 6.0 | mil carrier
antique_surgeon_set | Antique Surgeon's Set | G | 180 | 3.0 | mansion
trial_vials | Clinical Trial Vials | G | 200 | 0.2 | hosp
"""

ROSTER['Hygiene & household'] = """
soap_bar | Bar of Soap | C | 1 | 0.1 | bath.3 groc
shampoo | Shampoo | C | 1 | 0.4 | bath groc gym
toothbrush | Toothbrush | C | 1 | 0.02 | bath groc
toothpaste | Toothpaste | C | 1 | 0.1 | bath groc
toilet_paper | Roll of Toilet Paper | C | 2 | 0.1 | bath.3 groc office school
razor | Razor | C | 1 | 0.05 | bath groc
shaving_cream | Shaving Cream | C | 1 | 0.3 | bath
deodorant | Deodorant | C | 1 | 0.1 | bath gym
comb | Comb | C | 0 | 0.02 | bath body
hairbrush | Hairbrush | C | 1 | 0.1 | bath bed
hair_dryer | Hair Dryer | C | 3 | 0.6 | bath bed
perfume | Bottle of Perfume | S | 8 | 0.2 | bed bath mansion pharm
makeup_bag | Makeup Bag | C | 3 | 0.4 | bed bath
nail_clippers | Nail Clippers | C | 1 | 0.02 | bath
bleach | Bottle of Bleach | C | 3 | 1.8 | laund.3 pantry groc
detergent | Laundry Detergent | C | 2 | 2.0 | laund.3 groc
dish_soap | Dish Soap | C | 1 | 0.5 | pantry groc diner
sponge | Sponge | C | 0 | 0.02 | pantry diner
trash_bags | Box of Trash Bags | C | 2 | 0.4 | pantry groc garage
mop_bucket | Mop & Bucket | C | 3 | 2.0 | school church office
broom | Broom | C | 2 | 0.8 | garage school church
air_freshener | Air Freshener | C | 1 | 0.3 | bath glove
sewing_kit | Sewing Kit | S | 5 | 0.1 | bed thrift laund
thread_spool | Spool of Thread | C | 1 | 0.05 | bed thrift laund cloth
safety_pins | Safety Pins | C | 1 | 0.01 | bed laund
bug_spray | Bug Spray | C | 3 | 0.3 | camp garage groc
sunscreen | Sunscreen | C | 2 | 0.2 | bath groc glove stadium
"""

ROSTER['Hand tools'] = """
screwdriver | Screwdriver | C | 3 | 0.15 | garage.3 hard worktruck factory
screwdriver_set | Screwdriver Set | S | 8 | 0.6 | hard garage
pliers | Pliers | C | 3 | 0.3 | garage hard worktruck
wire_cutters | Wire Cutters | S | 5 | 0.3 | hard worktruck build
adjustable_wrench | Adjustable Wrench | C | 4 | 0.4 | garage hard worktruck
socket_set | Socket Set | S | 12 | 2.5 | garage hard auto
tape_measure | Tape Measure | C | 2 | 0.25 | garage hard build
level | Spirit Level | C | 2 | 0.6 | hard build
handsaw | Handsaw | C | 5 | 0.7 | garage hard build farm
hacksaw | Hacksaw | S | 5 | 0.6 | garage hard worktruck
bolt_cutters | Bolt Cutters | R | 18 | 3.5 | hard build police fire
utility_knife | Utility Knife | C | 3 | 0.1 | garage hard office worktruck
whetstone | Whetstone | S | 4 | 0.3 | garage camp farm hard
file_tool | Metal File | C | 2 | 0.3 | garage hard
chisel | Wood Chisel | C | 3 | 0.2 | garage hard
hand_drill | Hand Drill | S | 8 | 1.0 | garage hard attic
power_drill | Cordless Drill | R | 25 | 1.6 | hard.2 build garage
circular_saw | Circular Saw | R | 25 | 4.0 | hard build
staple_gun | Staple Gun | C | 3 | 0.7 | garage hard office
caulk_gun | Caulk Gun | C | 2 | 0.5 | hard build
paint_brush | Paint Brush | C | 1 | 0.1 | garage hard school
paint_can | Can of Paint | C | 3 | 4.0 | garage hard
pry_bar | Small Pry Bar | C | 4 | 0.5 | garage hard build
toolbox | Toolbox | S | 10 | 3.5 | garage.2 worktruck trunk hard
mechanic_toolbox | Mechanic's Tool Chest | R | 40 | 12.0 | auto.3 garage farm
portable_generator | Portable Generator | G | 160 | 25.0 | build.2 farm bunker mansion
welding_mask | Welding Mask | S | 8 | 0.6 | farm garage factory build
welding_rods | Welding Rods | S | 4 | 0.5 | farm factory hard
propane_torch* | Propane Torch | S | 8 | 1.2 | hard garage farm
gas_mask_filter | Respirator Cartridges | S | 5 | 0.2 | hard factory build
padlock | Padlock | C | 3 | 0.3 | hard garage gym school
combination_lock | Combination Lock | C | 2 | 0.2 | gym.3 school hard
lockpick_set | Lockpick Set | R | 20 | 0.1 | pawn police body mansion
multitool | Multitool | R | 18 | 0.25 | camp hard sport mil glove
pocket_knife | Pocket Knife | S | 6 | 0.1 | night glove camp body
fishing_rod | Fishing Rod | S | 8 | 0.6 | camp.3 garage dock sport
tackle_box | Tackle Box | S | 8 | 1.5 | camp garage dock
garden_trowel | Garden Trowel | C | 2 | 0.2 | garage farm
garden_shears | Garden Shears | C | 3 | 0.6 | garage farm
"""

ROSTER['Building & materials'] = """
nails* | Box of Nails | C | 2 | 0.4 | hard garage build.3 farm
screws | Box of Screws | C | 2 | 0.4 | hard garage build
hinges | Door Hinges | C | 2 | 0.3 | hard garage build
duct_tape* | Duct Tape | C | 3 | 0.25 | garage.3 hard trunk worktruck
electrical_tape | Electrical Tape | C | 2 | 0.1 | garage hard worktruck
superglue | Superglue | C | 3 | 0.02 | desk garage hard office
wood_glue* | Wood Glue | C | 2 | 0.3 | garage hard school
rope | Coil of Rope | C | 4 | 1.0 | garage hard farm camp dock fire
paracord | Paracord | S | 4 | 0.2 | camp mil sport
chain | Length of Chain | C | 4 | 2.5 | garage hard farm dock
wire_spool | Spool of Wire | C | 3 | 1.0 | hard garage farm build
zip_ties | Bag of Zip Ties | C | 2 | 0.1 | hard garage worktruck police
sandpaper | Sandpaper | C | 1 | 0.05 | garage hard
plank | Plank | C | 1 | 2.0 | build.3 hard garage farm
two_by_four | Two-by-Four | C | 1 | 3.0 | build.3 hard
plywood_sheet | Sheet of Plywood | C | 4 | 12.0 | build.3 hard
metal_sheet* | Sheet Metal | S | 4 | 3.0 | factory build farm
metal_pipe | Length of Pipe | C | 2 | 1.8 | build factory garage
rebar_rod | Rebar | C | 2 | 2.5 | build.3 factory
scrap_metal* | Scrap Metal | C | 1 | 1.0 | trash factory garage dock
cement_bag | Bag of Cement | C | 3 | 20.0 | build.3 hard farm
empty_sandbag* | Empty Sandbag | C | 1 | 0.2 | mil.3 build police fire
tarp* | Tarp | C | 3 | 1.2 | garage hard camp build farm
plastic_sheeting | Roll of Plastic Sheeting | C | 2 | 2.0 | hard build
glass_pane | Pane of Glass | S | 3 | 4.0 | hard build
barbed_wire_roll | Roll of Barbed Wire | S | 6 | 8.0 | farm.3 mil
fence_posts | Fence Post | C | 1 | 4.0 | farm hard
cinder_block | Cinder Block | C | 1 | 16.0 | build farm
propane_cylinder | Propane Cylinder | S | 10 | 15.0 | garage.2 hard camp farm diner
gas_can | Gas Can | S | 6 | 1.2 | garage.3 trunk farm hard auto
rubber_hose | Rubber Hose | C | 2 | 0.6 | garage.2 hard farm auto trunk
"""

ROSTER['Electrical & electronics'] = """
aa_battery* | AA Batteries | C | 2 | 0.05 | pantry.2 groc conv hard desk
d_battery | D Batteries | C | 3 | 0.3 | pantry groc hard
nine_volt | 9-Volt Battery | C | 2 | 0.05 | pantry groc hard
car_battery | Car Battery | S | 15 | 18.0 | auto.3 garage farm
lantern_battery | 6-Volt Lantern Battery | S | 5 | 0.9 | camp garage hard
extension_cord | Extension Cord | C | 3 | 1.0 | garage hard office
light_bulb | Light Bulb | C | 1 | 0.05 | pantry hard groc
fuse_box | Box of Fuses | C | 2 | 0.1 | hard garage station
jumper_cables | Jumper Cables | S | 6 | 2.0 | trunk.3 garage auto
alarm_clock* | Alarm Clock | C | 3 | 0.4 | night.3 thrift pawn
digital_watch* | Digital Watch | C | 4 | 0.05 | night body pawn conv
calculator | Pocket Calculator | C | 3 | 0.15 | desk office.3 school
wall_phone | Telephone | C | 2 | 1.0 | office pantry thrift
answering_machine | Answering Machine | C | 3 | 1.0 | desk office
transistor_radio | Transistor Radio | C | 5 | 0.4 | pantry garage farm camp thrift
two_way_radio* | Walkie-Talkie | S | 12 | 0.4 | police fire build camp sport
military_radio* | Military Radio | R | 30 | 3.0 | mil.3 carrier bunker
toy_radio* | Toy Walkie-Talkie | C | 2 | 0.2 | kids toyshop
cb_radio | CB Radio | S | 15 | 1.5 | worktruck.3 trunk farm
police_scanner | Police Scanner | R | 18 | 0.5 | elec pawn desk
ham_radio | Ham Radio Set | P | 60 | 9.0 | attic mansion bunker
satellite_phone | Satellite Phone | G | 180 | 0.6 | mil.3 carrier.2 bunker
gps_unit | Military GPS | P | 60 | 0.5 | mil.3 carrier
cassette_player | Portable Cassette Player | S | 12 | 0.3 | kids bed thrift pawn bus elec
headphones | Headphones | C | 3 | 0.1 | kids bed elec
cassette_tape | Cassette Tape | C | 1 | 0.05 | kids bed glove thrift
mixtape | Mixtape | S | 2 | 0.05 | kids glove bus
boombox | Boombox | R | 20 | 3.5 | kids pawn thrift elec
vhs_tape | VHS Tape | C | 1 | 0.25 | desk thrift attic
vcr | VCR | S | 10 | 4.5 | thrift pawn elec
handheld_game | Handheld Game | S | 10 | 0.3 | kids.3 toyshop pawn bus
game_cartridge | Game Cartridge | C | 2 | 0.1 | kids toyshop pawn
instant_camera | Instant Camera | S | 15 | 0.6 | desk attic pawn thrift elec
film_camera | 35 mm Camera | R | 25 | 0.7 | desk pawn attic elec
camcorder | Camcorder | R | 35 | 2.5 | pawn elec attic
film_roll | Roll of Film | C | 2 | 0.03 | desk elec pharm
pager | Pager | S | 5 | 0.1 | office hosp body
portable_tv | Portable TV | R | 25 | 3.0 | pawn elec trailer
car_phone | Car Phone | P | 50 | 2.0 | glove mansion
brick_phone | Brick Mobile Phone | P | 60 | 0.8 | office mansion pawn
generator_parts | Generator Parts | R | 20 | 3.0 | factory farm bunker
electronics_scrap | Electronics Scrap | C | 1 | 0.3 | trash elec factory
circuit_board | Circuit Board | S | 3 | 0.1 | elec factory office
"""

ROSTER['Light & fire'] = """
flashlight | Flashlight | C | 5 | 0.5 | pantry garage glove trunk camp
big_flashlight | Police Flashlight | S | 10 | 1.0 | police.3 pcar fire
headlamp | Headlamp | S | 8 | 0.15 | camp sport build
penlight | Penlight | C | 3 | 0.03 | hosp desk office
lantern_battery_light | Battery Lantern | S | 10 | 1.2 | camp garage bunker
kerosene_lantern | Kerosene Lantern | S | 8 | 1.0 | farm.3 attic camp
kerosene | Can of Kerosene | S | 5 | 1.0 | farm garage hard
candle | Candle | C | 1 | 0.1 | pantry night church.3 attic
church_candle | Church Candle | C | 2 | 0.4 | church.3
matches | Box of Matches | C | 2 | 0.02 | pantry conv diner camp
lighter | Disposable Lighter | C | 2 | 0.02 | conv night glove body
brass_lighter | Brass Flip Lighter | S | 8 | 0.06 | night pawn glove body desk
lighter_fluid | Lighter Fluid | C | 2 | 0.2 | conv garage
road_flare* | Road Flare | S | 4 | 0.2 | trunk.3 pcar firetruck station
flare_gun | Flare Pistol | R | 25 | 0.8 | dock.3 carrier mil
glow_stick | Glow Stick | C | 1 | 0.02 | kids camp mil
firestarter | Fire Starter | S | 4 | 0.05 | camp sport mil
charcoal | Bag of Charcoal | C | 2 | 4.0 | garage groc camp
firewood | Firewood | C | 1 | 2.0 | farm camp
"""

ROSTER['Outdoors, camping & farm'] = """
tent | Tent | R | 25 | 3.5 | camp.3 garage sport attic
sleeping_bag | Sleeping Bag | S | 10 | 1.8 | camp.3 garage attic mil
bedroll | Bedroll | C | 4 | 1.2 | camp mil
compass | Compass | S | 6 | 0.1 | camp desk mil glove
binoculars | Binoculars | R | 20 | 0.8 | camp.2 mil carrier dock police
water_filter | Water Filter | R | 18 | 0.3 | camp sport mil bunker
water_purification_tabs | Water Purification Tablets | S | 6 | 0.02 | camp mil bunker
hand_warmers | Hand Warmers | C | 2 | 0.05 | camp sport glove
emergency_blanket | Emergency Blanket | C | 2 | 0.05 | trunk amb camp bunker
snare_wire | Snare Wire | S | 3 | 0.1 | camp farm
animal_trap | Animal Trap | S | 8 | 2.0 | farm camp
hunting_knife_sheath | Knife Sheath | C | 2 | 0.1 | camp
fishing_lures | Box of Fishing Lures | S | 4 | 0.2 | camp dock garage
bait | Can of Bait Worms | C | 1 | 0.2 | camp dock
decoy_duck | Duck Decoy | C | 2 | 0.5 | camp garage
carrot_seeds* | Carrot Seeds | C | 2 | 0.01 | farm.3 hard garage
beet_seeds* | Beet Seeds | C | 2 | 0.01 | farm.3 hard
seed_potatoes* | Seed Potatoes | C | 2 | 0.5 | farm.3
tomato_seeds | Tomato Seeds | C | 2 | 0.01 | farm hard garage
lettuce_seeds | Lettuce Seeds | C | 2 | 0.01 | farm hard
corn_seeds | Corn Seeds | C | 2 | 0.01 | farm.3
fertilizer | Bag of Fertilizer | C | 3 | 10.0 | farm.3 hard garage
watering_can | Watering Can | C | 2 | 0.8 | garage farm
feed_sack | Sack of Feed | C | 2 | 20.0 | farm.3
egg_basket | Egg Basket | C | 1 | 0.3 | farm
milk_pail | Milk Pail | C | 2 | 1.2 | farm
saddle | Saddle | R | 30 | 9.0 | farm
horseshoe | Horseshoe | C | 1 | 0.5 | farm thrift
plastic_drum* | Plastic Drum | C | 4 | 8.0 | farm factory dock
"""

ROSTER['Clothing'] = """
chest_rig | Chest Rig | S | 25 | 1.2 | mil.3 bunker police
load_bearing_vest | Load-Bearing Vest | S | 22 | 1.4 | mil.3 bunker
medic_vest | Medic Vest | R | 30 | 1.3 | amb.3 mil fire
bandolier | Ammo Bandolier | S | 12 | 0.4 | gun mil farm
heavy_plate_carrier | Heavy Plate Carrier | G | 160 | 9.0 | mil bunker.2
high_cut_helmet | High-Cut Helmet | R | 45 | 1.2 | mil.2 bunker police
steel_pot_helmet | Steel Pot Helmet | S | 15 | 1.4 | mil thrift pawn attic
tanker_helmet | Tanker Helmet | R | 30 | 1.0 | mil
boonie_hat | Boonie Hat | C | 4 | 0.1 | mil.3 camp thrift
patrol_cap | Patrol Cap | C | 4 | 0.1 | mil.3 thrift
beret | Beret | S | 6 | 0.1 | mil cloth thrift
ballistic_glasses | Ballistic Glasses | S | 10 | 0.05 | mil.3 police gun
ballistic_goggles | Ballistic Goggles | S | 14 | 0.2 | mil.3 police
shemagh | Shemagh | C | 5 | 0.2 | mil.3 camp thrift
neck_gaiter | Neck Gaiter | C | 4 | 0.05 | mil sport camp
elbow_pads | Elbow Pads | S | 10 | 0.3 | mil police sport
knee_pads | Knee Pads | S | 10 | 0.4 | mil police build sport
field_shirt | Field Shirt | C | 8 | 0.4 | mil.3 thrift
combat_trousers | Combat Trousers | C | 10 | 0.6 | mil.3 thrift
tactical_boots | Tactical Boots | S | 20 | 1.3 | mil.2 police gun
denim_strips | Denim Strips | C | 1 | 0.05 | laund cloth thrift
leather_strips | Leather Strips | S | 2 | 0.05 | cloth thrift farm
# head
baseball_cap | Baseball Cap | C | 2 | 0.1 | bed cloth thrift stadium body
trucker_cap | Trucker Cap | C | 2 | 0.1 | worktruck farm thrift body
beanie | Beanie | C | 2 | 0.1 | bed cloth thrift sport
cowboy_hat | Cowboy Hat | S | 6 | 0.2 | farm thrift
bucket_hat | Fishing Hat | C | 2 | 0.1 | camp thrift
hard_hat | Hard Hat | C | 4 | 0.4 | build.3 factory body
bike_helmet | Bicycle Helmet | C | 4 | 0.3 | garage kids sport
football_helmet | Football Helmet | S | 10 | 1.5 | gym.3 stadium school
hockey_mask | Hockey Mask | R | 10 | 0.3 | gym attic sport
motorcycle_helmet | Motorcycle Helmet | R | 18 | 1.4 | garage pawn trunk
police_cap | Police Cap | S | 6 | 0.2 | police body
fire_helmet | Firefighter Helmet | R | 18 | 1.4 | fire.3 firetruck body
combat_helmet* | Combat Helmet | R | 30 | 1.5 | mil.3 carrier bunker body
riot_helmet | Riot Helmet | R | 25 | 1.6 | police.3 pcar
gas_mask | Gas Mask | R | 22 | 0.7 | mil.3 police fire bunker
welding_goggles | Goggles | C | 3 | 0.1 | garage factory build
sunglasses | Sunglasses | C | 2 | 0.03 | glove bed cloth
aviator_sunglasses | Aviator Sunglasses | S | 6 | 0.03 | glove pawn carrier
reading_glasses | Reading Glasses | C | 1 | 0.03 | night desk church
bandana | Bandana | C | 1 | 0.05 | bed thrift cloth body
balaclava | Balaclava | S | 4 | 0.1 | sport mil police
surgical_mask_worn | Surgical Mask | C | 1 | 0.01 | hosp pharm body
# torso
t_shirt | T-Shirt | C | 2 | 0.2 | bed.3 cloth thrift laund
band_tshirt | Band T-Shirt | S | 4 | 0.2 | kids bed thrift
tank_top | Tank Top | C | 1 | 0.1 | bed cloth gym
flannel_shirt | Flannel Shirt | C | 3 | 0.4 | bed farm thrift cloth
dress_shirt | Dress Shirt | C | 3 | 0.3 | bed cloth office
polo_shirt | Polo Shirt | C | 3 | 0.25 | bed cloth stadium
hawaiian_shirt | Hawaiian Shirt | S | 4 | 0.25 | thrift bed
sweater | Wool Sweater | C | 4 | 0.6 | bed cloth thrift
hoodie | Hoodie | C | 4 | 0.6 | bed cloth kids
sweatshirt | Varsity Sweatshirt | C | 4 | 0.6 | school gym kids
turtleneck | Turtleneck | C | 3 | 0.4 | bed cloth thrift
scrubs_top | Scrubs Top | C | 3 | 0.2 | hosp.3 laund
dress | Dress | C | 4 | 0.4 | bed cloth thrift
wedding_dress | Wedding Dress | R | 25 | 2.0 | church attic thrift
# outer
denim_jacket | Denim Jacket | C | 6 | 1.0 | bed cloth thrift
leather_jacket | Leather Jacket | S | 15 | 1.8 | bed cloth pawn thrift
bomber_jacket | Flight Jacket | R | 25 | 1.6 | carrier.3 mil thrift
varsity_jacket | Varsity Jacket | S | 10 | 1.4 | school gym kids
windbreaker | Windbreaker | C | 4 | 0.4 | bed cloth sport
parka | Winter Parka | S | 12 | 2.0 | bed cloth sport
raincoat | Raincoat | C | 5 | 0.7 | bed cloth station
trench_coat | Trench Coat | S | 10 | 1.5 | bed thrift office
suit_jacket | Suit Jacket | C | 6 | 0.9 | bed office court church
lab_coat | Lab Coat | S | 6 | 0.6 | hosp school
hi_vis_vest | Hi-Vis Vest | C | 3 | 0.2 | build.3 worktruck station
police_jacket | Police Jacket | S | 12 | 1.5 | police.3 body
fire_coat | Turnout Coat | R | 25 | 3.0 | fire.3 firetruck body
field_jacket | Field Jacket | S | 12 | 1.4 | mil.3 thrift body
flak_vest | Flak Vest | R | 30 | 4.0 | mil carrier
police_vest | Police Body Armour | R | 40 | 2.5 | police.3 pcar body
plate_carrier | Plate Carrier | P | 70 | 6.0 | mil.3 bunker body
ghillie_suit | Ghillie Suit | P | 60 | 3.0 | camp mil
ww2_flight_jacket | Vintage Sheepskin Flight Jacket | G | 170 | 2.5 | attic mansion carrier
# legs
jeans | Jeans | C | 4 | 0.7 | bed.3 cloth thrift laund
work_pants | Work Pants | C | 4 | 0.8 | worktruck build farm
cargo_pants | Cargo Pants | C | 5 | 0.7 | bed cloth sport mil
slacks | Slacks | C | 3 | 0.5 | bed office cloth
sweatpants | Sweatpants | C | 2 | 0.5 | bed gym cloth
shorts | Shorts | C | 2 | 0.3 | bed gym cloth
skirt | Skirt | C | 2 | 0.3 | bed cloth thrift
overalls | Overalls | C | 5 | 1.2 | farm.3 build thrift
scrubs_pants | Scrubs Pants | C | 2 | 0.2 | hosp laund
camo_pants | Camo Trousers | S | 6 | 0.8 | mil.3 sport body
fire_pants | Turnout Pants | R | 20 | 2.5 | fire.3 body
leather_chaps | Leather Chaps | R | 15 | 2.0 | garage pawn thrift
# feet
sneakers | Sneakers | C | 4 | 0.8 | bed cloth thrift gym
running_shoes | Running Shoes | C | 5 | 0.6 | gym sport bed
dress_shoes | Dress Shoes | C | 4 | 1.0 | bed office church
work_boots | Work Boots | C | 8 | 1.8 | build.3 farm worktruck
cowboy_boots | Cowboy Boots | S | 10 | 1.6 | farm thrift
hiking_boots | Hiking Boots | S | 10 | 1.4 | camp sport
rubber_boots | Rubber Boots | C | 4 | 1.6 | farm dock garage
combat_boots | Combat Boots | S | 14 | 1.8 | mil.3 police body
slippers | Slippers | C | 1 | 0.3 | bed hosp
# hands and accessories
work_gloves | Work Gloves | C | 3 | 0.2 | garage.3 build farm worktruck hard
leather_gloves | Leather Gloves | S | 5 | 0.2 | bed cloth pawn
winter_gloves | Winter Gloves | C | 3 | 0.2 | bed cloth sport
rubber_gloves | Rubber Gloves | C | 1 | 0.1 | pantry laund diner
tactical_gloves | Tactical Gloves | R | 12 | 0.2 | police mil body
fire_gloves | Firefighter Gloves | S | 8 | 0.4 | fire.3 body
belt | Leather Belt | C | 2 | 0.2 | bed cloth thrift
duty_belt | Police Duty Belt | R | 15 | 1.0 | police.3 body
tool_belt | Tool Belt | S | 8 | 0.6 | build.3 garage hard
scarf | Scarf | C | 2 | 0.1 | bed cloth thrift
necktie | Necktie | C | 1 | 0.05 | bed office church
"""

ROSTER['Bags & containers'] = """
school_backpack* | School Backpack | C | 6 | 0.6 | kids school.3 bus
hiking_backpack* | Hiking Backpack | S | 15 | 1.2 | camp.3 sport garage
military_rucksack* | Military Rucksack | R | 30 | 2.0 | mil.3 bunker
duffel_bag* | Duffel Bag | S | 10 | 1.0 | gym.2 garage trunk mil
fanny_pack* | Fanny Pack | C | 3 | 0.2 | bed thrift cloth body
purse | Purse | C | 3 | 0.4 | bed cloth body
briefcase | Briefcase | S | 8 | 2.0 | office.3 desk station court body
gym_bag | Gym Bag | C | 5 | 0.6 | gym.3 stadium station
doctors_bag | Doctor's Bag | R | 15 | 1.4 | hosp mansion
plastic_bag | Plastic Shopping Bag | C | 0 | 0.01 | groc.3 trash pantry
paper_bag | Paper Grocery Bag | C | 0 | 0.05 | groc pantry
suitcase | Suitcase | S | 8 | 3.0 | attic bed station trunk
lunchbox | Lunchbox | C | 2 | 0.4 | kids school worktruck
cooler | Picnic Cooler | S | 8 | 2.5 | garage camp trunk
mail_bag | Mail Carrier's Satchel | S | 8 | 0.8 | post.3 body
tool_bag | Tool Bag | S | 10 | 1.0 | worktruck build hard
ammo_pouch_belt | Webbing Belt Kit | R | 18 | 1.2 | mil.3 body
"""

ROSTER['Books, magazines & papers'] = """
# skill books, volumes 1-5 per skill (the UI thread adds the Read action); one row stands for the set
skillbook_carpentry | Carpentry for Beginners, Vols 1-5 | S-P | 8-60 | 0.5 | books hard school desk
skillbook_first_aid | First Aid Handbook, Vols 1-5 | S-P | 8-60 | 0.5 | books hosp school fire
skillbook_blunt | Self-Defence Manual, Vols 1-5 | S-P | 8-60 | 0.5 | books police gym
skillbook_blade | Knife Craft, Vols 1-5 | S-P | 8-60 | 0.5 | books camp sport
skillbook_sprinting | Track & Field Training, Vols 1-5 | S-P | 8-60 | 0.5 | books gym school
skillbook_sneaking | Woodland Stalking, Vols 1-5 | S-P | 8-60 | 0.5 | books camp
skillbook_aiming | Marksmanship Guide, Vols 1-5 | S-P | 8-60 | 0.5 | books gun police mil
skillbook_maintenance | Home Maintenance, Vols 1-5 | S-P | 8-60 | 0.5 | books hard garage
skillbook_masonry | Bricklaying Basics, Vols 1-5 | S-P | 8-60 | 0.5 | books hard build
skillbook_metalworking | Metal Shop, Vols 1-5 | S-P | 8-60 | 0.5 | books factory farm school
skillbook_piloting | Flight Training Manual, Vols 1-5 | R-G | 15-120 | 0.6 | carrier.3 mil books
skillbook_electrical | Basic Electronics and Wiring, Vols 1-5 | S-P | 8-60 | 0.5 | books hard elec school
skillbook_plumbing | Plumbing Made Easy, Vols 1-5 | S-P | 8-60 | 0.5 | books hard build
# recipe magazines: each teaches one recipe or one finish blueprint
mag_carpentry | Handyman Magazine (recipe) | S | 6 | 0.2 | desk hard books conv
mag_mechanics | Car Mechanics Monthly (recipe) | S | 6 | 0.2 | garage auto conv
mag_survival | Survivalist Quarterly (recipe) | R | 10 | 0.2 | bunker camp books
mag_cooking | Home Cooking Magazine (recipe) | S | 6 | 0.2 | pantry groc conv
mag_electronics | Electronics Hobbyist (recipe) | R | 10 | 0.2 | elec desk
mag_farming | Farm Almanac (recipe) | S | 6 | 0.3 | farm.3 books
mag_hunting | Hunting & Trapping (recipe) | S | 6 | 0.2 | camp gun conv
mag_guns | Gun Digest (finish blueprint) | R | 12 | 0.2 | gun.3 pawn conv
# reading for comfort
paperback | Paperback Novel | C | 1 | 0.3 | night.3 books desk station bus
hardback | Hardback Book | C | 2 | 0.8 | desk books mansion
bible | Bible | C | 2 | 0.7 | church.3 night hosp
hymn_book | Hymn Book | C | 1 | 0.4 | church.3
magazine | Magazine | C | 1 | 0.2 | conv.3 bath desk hosp
comic_book | Comic Book | C | 2 | 0.1 | kids.3 conv books
rare_comic | Graded First-Issue Comic | G | 150 | 0.3 | mansion pawn books
first_edition | Signed First Edition | G | 130 | 0.8 | mansion.3 books
newspaper | Newspaper | C | 0 | 0.3 | conv station.3 pantry desk trash
childrens_book | Picture Book | C | 1 | 0.3 | kids.3 school books
textbook | School Textbook | C | 1 | 1.2 | school.3 kids
encyclopedia | Encyclopedia Volume | C | 2 | 1.5 | books desk school
cookbook | Cookbook | C | 2 | 0.8 | pantry books diner
crossword_book | Crossword Book | C | 1 | 0.2 | night conv hosp
playing_cards | Deck of Cards | C | 1 | 0.1 | desk night carrier.2 body
dice | Dice | C | 1 | 0.02 | kids desk
board_game | Board Game | C | 3 | 1.0 | kids attic thrift
# papers and maps
street_map* | Street Map | C | 3 | 0.05 | glove.3 conv station
road_atlas* | Road Atlas | S | 6 | 0.6 | glove trunk conv
county_map | County Map | S | 6 | 0.05 | court glove police
marked_map | Marked Map (shows a cache) | R | 25 | 0.05 | body bunker mil
transit_map | Transit Map | C | 1 | 0.05 | station.3 bus
timetable | Train Timetable | C | 0 | 0.05 | station.3
notebook | Notebook | C | 1 | 0.2 | desk school office
pen | Pen | C | 0 | 0.01 | desk office school
pencil | Pencil | C | 0 | 0.01 | desk school
envelope | Sealed Letter | C | 1 | 0.02 | post.3 desk
parcel | Parcel | S | 4 | 1.5 | post.3 station
journal | Survivor's Journal | R | 10 | 0.3 | body bunker attic
photograph | Family Photo | C | 1 | 0.02 | night desk body
polaroid_photo | Polaroid | C | 1 | 0.02 | desk kids body
id_card | ID Card | C | 0 | 0.01 | body
press_pass | Stadium Press Pass | S | 2 | 0.01 | stadium
case_file | Police Case File | S | 3 | 0.3 | police.3 court
evidence_bag | Evidence Bag | S | 4 | 0.2 | police court
flight_manual | Aircraft Flight Manual | R | 15 | 1.5 | carrier.3 mil
kneeboard | Pilot's Kneeboard | R | 12 | 0.3 | carrier.3
maintenance_cards | Maintenance Card Deck | S | 4 | 0.3 | carrier dock factory
blueprint_paper | Building Blueprints | S | 6 | 0.2 | build office court
"""

ROSTER['Keys, cards & documents'] = """
house_key | House Key | C | 1 | 0.01 | body.3 night glove
key_ring | Ring of Keys | S | 3 | 0.1 | body office school church
padlock_key | Padlock Key | C | 1 | 0.01 | garage gym
safe_combination | Safe Combination (note) | R | 15 | 0.01 | desk mansion bank office
deposit_box_key | Safe Deposit Box Key | R | 20 | 0.01 | bank.3 body desk
locker_key | Locker Key | C | 1 | 0.01 | station.3 gym school
keycard | Security Keycard | R | 18 | 0.01 | office.3 hosp body
military_keycard | Base Access Card | P | 40 | 0.01 | mil.3 carrier body
bunker_key | Bunker Hatch Key | P | 50 | 0.05 | body bunker
vault_key | Bank Vault Master Key | G | 150 | 0.1 | bank.3 body
transit_ticket | Train Ticket | C | 0 | 0.01 | station.3 body
ticket_punch | Ticket Punch | S | 2 | 0.1 | station.3
credit_card | Credit Card | C | 1 | 0.01 | body.3 bed desk
drivers_license | Driver's License | C | 0 | 0.01 | body glove
registration | Vehicle Registration | C | 0 | 0.02 | glove.3
dog_tags | Dog Tags | S | 3 | 0.03 | mil.2 carrier body
police_badge | Police Badge | R | 12 | 0.1 | police.3 body
"""

ROSTER['Valuables & trade goods'] = """
cash_bundle | Bundle of Cash | C | 2 | 0.02 | till.3 night desk body bank
cash_stack | Banded Stack of Cash | S | 8 | 0.1 | bank.3 mansion pawn
coin_roll | Roll of Coins | C | 2 | 0.2 | till bank.3 laund
wallet | Wallet | C | 2 | 0.1 | body.3 night desk
cigarettes | Pack of Cigarettes | C | 4 | 0.03 | conv.3 night glove body liq
cigarette_carton | Carton of Cigarettes | S | 35 | 0.3 | conv liq pantry
cigars | Box of Cigars | R | 20 | 0.4 | mansion liq desk
pipe_tobacco | Pipe & Tobacco | S | 8 | 0.2 | desk thrift
coffee_beans_sack | Sack of Coffee Beans | R | 18 | 5.0 | diner dock
wristwatch | Wristwatch | S | 10 | 0.08 | night body pawn
gold_watch | Gold Wristwatch | P | 60 | 0.12 | mansion pawn.3 jewel bank
pocket_watch | Gold Pocket Watch | P | 55 | 0.12 | mansion pawn jewel attic
wedding_ring | Wedding Ring | S | 15 | 0.01 | body.3 night pawn
silver_necklace | Silver Necklace | S | 12 | 0.03 | night bed pawn
gold_necklace | Gold Chain | R | 30 | 0.05 | night pawn.3 jewel
pearl_necklace | Pearl Necklace | R | 35 | 0.08 | mansion jewel night
earrings | Pair of Earrings | C | 5 | 0.01 | night bed
diamond_ring | Diamond Ring | P | 80 | 0.01 | jewel.3 pawn mansion bank
class_ring | Class Ring | S | 10 | 0.02 | night pawn
jewellery_box | Jewellery Box | R | 25 | 0.8 | bed.2 mansion
silver_coin | Silver Coin | S | 6 | 0.03 | pawn bank desk
coin_collection | Coin Collection Album | R | 40 | 1.0 | desk mansion pawn
silverware_set | Silverware Set | R | 30 | 3.0 | pantry mansion pawn
gold_coin | Gold Coin | P | 70 | 0.03 | bank.3 mansion pawn
gold_bar | Gold Bar | G | 250 | 1.0 | bank.3 mansion
inverted_stamp | Misprinted Airmail Stamp | G | 220 | 0.01 | post mansion
jewelled_egg | Jewelled Egg Ornament | G | 260 | 0.5 | mansion.3
uncut_diamonds | Pouch of Uncut Diamonds | G | 300 | 0.05 | jewel.3 bank mansion
chronometer | Antique Pocket Chronometer | G | 180 | 0.15 | mansion jewel
stamp_collection | Stamp Collection | R | 30 | 0.5 | desk post attic
baseball_cards | Shoebox of Baseball Cards | S | 10 | 1.0 | kids attic pawn
rookie_card | Graded Rookie Card | G | 140 | 0.05 | mansion pawn stadium
signed_ball | Signed Baseball | P | 50 | 0.15 | stadium.3 mansion pawn
championship_ring | Championship Ring | G | 200 | 0.05 | stadium mansion
trophy | Trophy | C | 3 | 1.5 | school gym kids stadium
medal | Military Medal | R | 25 | 0.05 | attic desk mil
war_medal_case | Framed Medal Set | P | 70 | 1.0 | mansion attic court
antique_clock | Antique Mantel Clock | P | 60 | 3.0 | mansion attic thrift
art_print | Framed Print | C | 3 | 2.0 | thrift attic office
oil_painting | Oil Painting | P | 80 | 4.0 | mansion court church
church_chalice | Silver Chalice | P | 60 | 0.6 | church.3
fur_coat | Fur Coat | P | 70 | 4.0 | mansion bed thrift
bespoke_suit | Bespoke Suit | P | 50 | 1.5 | mansion court
"""

ROSTER['Toys, hobbies & personal'] = """
teddy_bear | Teddy Bear | C | 2 | 0.3 | kids.3 bus toyshop
action_figure | Action Figure | C | 2 | 0.1 | kids.3 toyshop
mint_action_figure | Boxed Collectible Figure | P | 50 | 0.3 | toyshop pawn kids
sealed_console | Factory-Sealed Games Console | P | 70 | 3.0 | toyshop elec pawn
doll | Doll | C | 2 | 0.4 | kids.3 toyshop thrift
toy_car | Die-Cast Car | C | 1 | 0.05 | kids toyshop
toy_truck | Toy Truck | C | 2 | 0.6 | kids toyshop
rubiks_cube | Puzzle Cube | C | 2 | 0.1 | kids toyshop desk
yo_yo | Yo-Yo | C | 1 | 0.05 | kids toyshop
jigsaw_puzzle | Jigsaw Puzzle | C | 2 | 0.8 | attic kids thrift
model_kit | Model Plane Kit | S | 5 | 0.5 | kids toyshop
rc_car | Remote-Control Car | R | 15 | 1.5 | kids toyshop
baseball | Baseball | C | 1 | 0.15 | kids gym stadium sport
baseball_glove | Baseball Glove | C | 4 | 0.6 | kids gym sport
football | Football | C | 3 | 0.4 | kids gym school
basketball | Basketball | C | 3 | 0.6 | kids gym school
tennis_racket | Tennis Racket | C | 4 | 0.3 | garage gym sport
skateboard | Skateboard | S | 8 | 2.5 | kids garage
roller_skates | Roller Skates | C | 5 | 1.5 | kids thrift
frisbee | Frisbee | C | 1 | 0.2 | kids garage camp
fishing_trophy | Mounted Fish | S | 6 | 3.0 | camp garage
harmonica | Harmonica | C | 3 | 0.1 | night thrift pawn
acoustic_guitar | Acoustic Guitar | R | 25 | 2.5 | bed pawn thrift
trumpet | Trumpet | R | 25 | 1.2 | school pawn
whistle | Whistle | C | 1 | 0.02 | gym.3 school police
stopwatch | Stopwatch | C | 3 | 0.05 | gym.3 stadium school
pocket_mirror | Pocket Mirror | C | 1 | 0.05 | bed body
umbrella | Umbrella | C | 3 | 0.5 | station.3 bed office trunk
rosary | Rosary | C | 2 | 0.03 | church.3 night body
snow_globe | Snow Globe | C | 2 | 0.4 | kids thrift attic
lucky_rabbit_foot | Rabbit's Foot Keyring | S | 3 | 0.02 | glove body
"""

ROSTER['Vehicle & fuel'] = """
spare_tire | Spare Tyre | S | 10 | 10.0 | trunk.2 auto garage
tire_iron | Tyre Iron | C | 4 | 1.5 | trunk.3 auto garage
car_jack | Car Jack | S | 8 | 4.0 | trunk.3 auto
motor_oil | Quart of Motor Oil | C | 3 | 0.9 | auto.3 garage trunk farm
antifreeze | Jug of Antifreeze | C | 3 | 4.0 | auto garage
brake_fluid | Brake Fluid | C | 2 | 0.4 | auto garage
spark_plugs | Spark Plugs | C | 2 | 0.1 | auto garage
fan_belt | Fan Belt | C | 2 | 0.3 | auto garage
headlight_bulb | Headlight Bulb | C | 2 | 0.1 | auto glove
wiper_blades | Wiper Blades | C | 1 | 0.3 | auto garage
radiator_hose | Radiator Hose | C | 2 | 0.5 | auto
oil_filter | Oil Filter | C | 2 | 0.4 | auto garage
car_wax | Car Wax | C | 1 | 0.3 | garage auto
tow_strap | Tow Strap | S | 6 | 2.5 | trunk worktruck farm
fuel_siphon | Siphon Hose | S | 5 | 0.3 | garage farm trunk
diesel_can | Diesel Can | S | 6 | 1.2 | farm.3 worktruck dock
jet_fuel_drum* | Jet Fuel Drum | R | 30 | 80.0 | carrier mil
hydraulic_fluid | Can of Hydraulic Fluid | S | 5 | 1.0 | carrier dock factory
tie_down_chain | Tie-Down Chain | C | 3 | 4.0 | carrier dock
wheel_chock | Wheel Chock | C | 2 | 3.0 | carrier mil
"""

ROSTER['Military & service'] = """
flight_deck_jersey | Flight Deck Jersey | S | 8 | 0.4 | carrier.3 body
cranial_helmet | Cranial Helmet | S | 10 | 0.6 | carrier.3 body
g_suit | G-Suit | R | 25 | 2.0 | carrier mil
survival_vest | Aircrew Survival Vest | R | 35 | 2.5 | carrier.3 mil
pilot_helmet | Flight Helmet | P | 60 | 1.8 | carrier.3 mil
sound_powered_phone | Sound-Powered Phone | S | 6 | 1.0 | carrier dock
squadron_patch | Squadron Patch | S | 3 | 0.01 | carrier.3 body
signal_mirror | Signal Mirror | S | 3 | 0.05 | mil carrier camp
smoke_canister | Smoke Signal | R | 12 | 0.4 | mil carrier dock
entrenching_tool | Folding Shovel | S | 8 | 1.0 | mil.3 camp
poncho | Rain Poncho | C | 4 | 0.5 | mil camp station
mil_canteen_cup | Canteen Cup | C | 2 | 0.2 | mil camp
field_rations_case | Case of Ration Meals | P | 60 | 7.0 | mil.3 bunker carrier
nvg_gen1* | Night Vision Goggles (old) | R | 35 | 1.0 | mil police bunker
nvg_gen3* | Night Vision Goggles | P | 80 | 0.6 | mil.3 bunker
nvg_head_mount* | NVG Head Mount | R | 20 | 0.4 | mil bunker
comms_headset* | Comms Headset | R | 25 | 0.3 | mil police carrier
handcuffs | Handcuffs | S | 6 | 0.3 | police.3 pcar body
police_radio_belt | Shoulder Radio | R | 15 | 0.4 | police.3 pcar
fire_extinguisher | Fire Extinguisher | C | 5 | 4.0 | office.2 school diner fire.3 station
fire_hose_nozzle | Fire Hose Nozzle | S | 6 | 2.0 | fire.3 firetruck
halligan_tool | Halligan Bar | R | 20 | 5.0 | fire.3 firetruck
scba_tank | Breathing Apparatus | P | 60 | 12.0 | fire.3 firetruck
traffic_cone | Traffic Cone | C | 1 | 1.5 | build worktruck pcar
road_sign | Road Sign | C | 1 | 4.0 | build
camo_netting | Camo Netting | C | 5 | 3.0 | mil.3 sport camp
waterproof_notebook | Waterproof Notebook | C | 2 | 0.1 | mil police desk
signal_panel | Signal Panel | C | 4 | 0.5 | mil.3 carrier
field_wire_spool | Field Wire Spool | C | 3 | 2.5 | mil.3 bunker
tripwire_flare | Tripwire Flare | C | 5 | 0.4 | mil.3 bunker
folding_cot | Folding Cot | C | 8 | 6.0 | mil.3 camp bunker
unit_patch_set | Unit Patch Set | C | 2 | 0.05 | mil thrift attic
angle_flashlight | Angle-Head Flashlight | C | 6 | 0.4 | mil.3 bunker
radio_battery_pack | Radio Battery Pack | S | 8 | 1.0 | mil.3 bunker carrier
lensatic_compass | Lensatic Compass | S | 8 | 0.2 | mil.3 sport camp
topo_map_set | Topographic Map Set | S | 10 | 0.4 | mil.3 camp
ghillie_kit | Ghillie Kit | S | 12 | 2.0 | mil sport gun
periscope | Periscope | S | 15 | 1.5 | mil carrier
military_binoculars | Military Binoculars | S | 18 | 1.0 | mil.3 carrier
water_bladder | Water Bladder | S | 8 | 0.8 | mil.3 camp bunker
ration_heater | Ration Heater | S | 3 | 0.05 | mil.3 bunker carrier
blast_wall_bag | Blast Wall Bag | S | 15 | 12.0 | mil.3 dock
unit_flag | Unit Flag | S | 10 | 0.8 | mil court
survival_field_manual | Survival Field Manual | S | 6 | 0.4 | mil.3 books camp
footlocker | Footlocker | S | 14 | 9.0 | mil.3
field_radio | Field Radio | R | 40 | 7.5 | mil.3 carrier bunker
laser_rangefinder | Laser Rangefinder | R | 35 | 0.8 | mil gun
metal_detector | Metal Detector | R | 30 | 2.5 | mil hard pawn garage
door_ram | Hydraulic Door Ram | R | 30 | 6.0 | police.3 fire mil
quiet_generator | Quiet Field Generator | R | 50 | 60.0 | mil.3 bunker
motion_sensor_kit | Motion Sensor Kit | R | 30 | 2.0 | mil.3 office
tactical_floodlight | Tactical Floodlight | R | 25 | 8.0 | mil police.3
codebook | Codebook | R | 20 | 0.3 | mil bunker.3
checkpoint_overlay | Checkpoint Overlay | R | 25 | 0.1 | mil.3
weapons_locker | Weapons Locker | R | 40 | 40.0 | mil.3 police
parachute | Parachute | R | 20 | 12.0 | carrier.3 mil
grappling_ladder | Grappling Hook and Ladder | R | 20 | 4.0 | mil sport
thermal_monocular | Thermal Monocular | P | 90 | 0.6 | mil.3 police
recon_drone | Recon Drone | P | 100 | 1.5 | mil.3 police
map_table | Map Table | P | 60 | 25.0 | mil.3
satellite_terminal | Satellite Terminal | P | 90 | 6.0 | mil.3 carrier
regimental_drum | Regimental Drum | P | 50 | 5.0 | mil court mansion
bunker_blueprints | Bunker Blueprints | P | 60 | 0.3 | mil bunker.3
shell_casing_lamp | Shell Casing Lamp | P | 45 | 3.0 | mansion pawn mil
ejection_seat | Ejection Seat | G | 220 | 90.0 | carrier mil
launch_keys | Decommissioned Launch Keys | G | 250 | 0.2 | bunker
cipher_machine | Wartime Cipher Machine | G | 300 | 12.0 | mansion.3 mil
"""

ROSTER['Junk & scrap'] = """
tin_can* | Empty Can | C | 0 | 0.05 | trash.3 pantry
glass_bottle* | Empty Bottle | C | 0 | 0.3 | trash.3 pantry liq
broken_glass | Broken Glass | C | 0 | 0.1 | trash
newspaper_bundle | Bundle of Old Newspapers | C | 0 | 2.0 | attic garage trash
cardboard | Flattened Cardboard | C | 0 | 0.3 | trash groc factory
spoiled_food | Spoiled Food | C | 0 | 0.3 | trash.3 fridge
bone_scrap | Bone | C | 0 | 0.1 | trash farm
cigarette_butts | Ashtray Butts | C | 0 | 0.01 | trash conv
fast_food_wrapper | Burger Wrapper | C | 0 | 0.01 | trash glove
broken_radio | Broken Radio | C | 1 | 0.5 | trash attic garage
broken_toy | Broken Toy | C | 0 | 0.2 | trash kids
old_shoe | Odd Shoe | C | 0 | 0.4 | trash
rubber_band_ball | Rubber Band Ball | C | 0 | 0.1 | desk office
paperclips | Box of Paperclips | C | 0 | 0.05 | desk office post
stapler | Stapler | C | 1 | 0.3 | desk office school
coat_hanger | Wire Coat Hanger | C | 0 | 0.05 | bed laund thrift
light_tube | Fluorescent Tube | C | 0 | 0.4 | factory office
pipe_offcut | Pipe Offcut | C | 0 | 0.5 | build factory
wood_scraps | Wood Scraps | C | 0 | 0.5 | build garage farm
rubble_chunks* | Rubble | C | 0 | 2.0 | build
sheet_of_tin | Tin Sign | C | 1 | 1.0 | farm thrift
lost_property_box | Lost Property Box | S | 6 | 2.0 | station.3
"""


def parse():
    """Rows as dicts, in roster order."""
    out = []
    for cat, block in ROSTER.items():
        group = ''
        for line in block.strip().splitlines():
            line = line.strip()
            if not line:
                continue
            if line.startswith('#'):
                group = line[1:].strip()
                continue
            cells = [c.strip() for c in line.split('|')]
            iid, name, rar, val, kg, where = cells
            exists = iid.endswith('*')
            iid = iid.rstrip('*')
            places = []
            for tag in where.split():
                t, _, w = tag.partition('.')
                assert t in PLACES, (iid, t)
                places.append((t, int(w) if w else 1))
            out.append(dict(id=iid, name=name, cat=cat, group=group, rarity=rar, value=val, kg=kg,
                            places=places, exists=exists))
    return out


if __name__ == '__main__':
    rows = parse()
    seen = set()
    for r in rows:
        assert r['id'] not in seen, r['id']
        seen.add(r['id'])
    from collections import Counter
    print(len(rows), 'rows')
    for cat, n in Counter(r['cat'] for r in rows).items():
        print(f'  {cat}: {n}')
    print(Counter(r['rarity'] for r in rows))
    print('new:', sum(not r['exists'] for r in rows))
