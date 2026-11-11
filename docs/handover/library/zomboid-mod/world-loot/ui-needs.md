# What the kit screen needs from the world loot roster (UI thread, 2026-10-27)

All references are to branch `playtest`.

## Rarity
- The kit tiles and hover card already show five tiers with set colours: Common (bone), Scarce (olive), Rare (steel blue), Prized (oxblood), Grail (brass). Use exactly these names.
- Today `client/kit/KitItems.rarity()` reads the weapons thread's `combat.rolls` by reflection, so only rolled weapons and gear get a colour. Everything else shows Common.
- Ask: give every roster item a base rarity, and expose one public server-side method, e.g. `ItemInfo.rarity(ItemStack)`. It returns the rolled rarity when the stack has a roll, otherwise the item's base rarity. I'll point the kit at it.

## Value
- There is no value or currency yet. Use one integer "value" per item in a single unit (barter worth, 1 = a can of beans).
- The card shows it on the weight line ("Weight 0.3 kg · Value 12"). The Supplies page can total carried value per container.

## Weight
- `carry/Weights.java` exists. It has an EXACT map (`mod("name", kg)`) plus guesses for anything not listed: food 0.3, tools by tag, blocks 0.4, otherwise 0.1 kg.
- Every new item needs a real weight. Best is one data table for the roster (JSON or a generated Java table) that Weights reads, rather than hundreds of `mod()` lines.

## Fields per item
id, display name, category, weight (kg), base rarity, value, max stack, durability/condition (tools and weapons), spoilage (food: fresh days / stale days / rotten), a one-line description for the card, and where-found tags (building type + container).

## Categories
- The Supplies page tabs are: Weapons, Medical, Food & drink, Ammunition, Gun parts, Clothing & bags, Tools, Materials.
- `KitItems.category()` sorts by Java class today. Please put each item in an item tag `zomboidcraft:category/<name>` and I'll switch the kit to read the tags.
- New tabs worth adding if the roster has them: Books & magazines, Electronics, Valuables (trade goods), Keys & documents.

## Bench repairs (combat/Repairs.java)
- Uses today: duct_tape, wood_glue (melee), gun_oil, gun_parts (guns).
- Worth adding: superglue, electrical tape, whetstone (blades), gun cleaning kit, screws, spring kit.

## Base building (Build page)
- Uses: planks, nails, metal sheets, empty_sandbag, scrap_metal, rubble_chunks, propane_torch.
- Loot should carry: nails and screws by box, hinges, wire, sheet metal, propane tanks, welding rods, sandbags, tarps.

## Skills and books
- Skills in code: Carpentry, First Aid, Blunt, Blade, Sprinting, Sneaking, Aiming, Maintenance, Masonry, Metalworking, Piloting.
- Zomboid-style skill books, volumes 1-5 per skill, would multiply XP. Magazines would teach one recipe or finish blueprint.
- The UI would add a timed "Read" action and a "Known" mark. I'd build that once the items exist.

## Medical
- Code today: BandageItem and SplintItem only.
- Body.Wound: SCRATCH, LACERATION, BITE, BURN, plus fractures and bleeding.
- Items wanted:
  - sterile bandage, ripped sheet / rag (dirty: infection chance), adhesive bandages (scratches only)
  - disinfectant / rubbing alcohol, antiseptic wipes
  - suture needle + thread (lacerations), tweezers (glass / shrapnel)
  - burn ointment, gauze pads
  - painkillers, antibiotics, beta blockers (panic), vitamins
  - arm sling, first aid kit (a small container)
- Each new treatment needs Health page and Treatment code, which the UI thread will add once the items exist. Names stay generic, with no brand names.

## Rule reminder
Every item model starts from reference photos in /mnt/project-files/zomboid-mod/refs/ (REFERENCES thread).
