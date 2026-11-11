---
name: zomboid-wearable-clothing
description: Clothing thread (2026-10-29) plan for 94 ZomboidCraft wearables: loot + worn models, slot layering choice, batch order, refs request
metadata:
  type: project
  modified: 2026-10-29T02:21:21.031Z
---

Thread "Make wearable clothing" (cmsg_01LvQiYGyHLhwyRNcM6wP1DpC1io1oiTZ2FBtzsHPVGDG3, branch claude/project-thread-9wvcyv) started 2026-10-29 02:18 from the world-loot thread's card.

Scope: the 87 'Clothing' rows in tools/items/loot/roster.py (combat_helmet already exists) + flight_deck_jersey, cranial_helmet, g_suit, survival_vest, pilot_helmet, poncho, fur_coat, bespoke_suit. Leave gen_java OTHERS_CATS/WEARABLES alone; LootSpecs already holds their specs but items aren't registered. Tell the world-loot thread (cmsg_01LvQiYGyHLhwyRNcM6wP1Dp3sPQiPRKoASa3LtUBwRxqS) when each batch is in playtest (place_loot.py GEAR).

Current state of the code: kit screen CharacterPage has 4 clothing slots = vanilla armour slots; player still renders vanilla PlayerModel; worn head items use HeadGearLayer (item models in head pixels, tools/weapons/headgear.py). Style: [[zomboid-clothing-style]].

Plan sent 02:25: batches 1 basics+slot system (14), 2 headwear (19), 3 tops/outer (~27), 4 legs/feet/hands/belts (~27), 5 uniforms/armour/aircrew (~15). Clothes give warmth, bite/scratch protection, armour; belts add belt slots. DECIDED 02:28: 11 Zomboid layer slots.

Refs: asked REFERENCES 02:24; it queued 83 folders refs/items/clothing/<topic>/ (02:21 reply), filling one at a time over several hours (Wikimedia rate limits) after a penthouse batch. Mostly worn/product shots. Start on whatever has landed.

Tech (built 2026-10-29): clothing/ClothingSlot (HAT JACKET LEGS FEET = vanilla armour slots; VEST/BELT = GearStore VEST/BELT; EYES FACE NECK SHIRT HANDS = GearStore 7-11, SIZE 12), Clothes (stats table, totals per Body.Region, armour tick for gear-store pieces), ClothingItems.MADE (registers per batch), ClothingItem (right-click wears). Injuries.zombieWound returns null when clothes stop it. tools/items/gen_clothes.py builds each garment in body-part coords -> loot model + models/item/clothing/<id>/<part>.json + generated client/clothing/WornParts.java; ClothingLayer draws parts (slim arms x0.75). Belt widens over trousers/shirt (WornGearLayer). Local check: scratchpad clothes/render.py (numpy software render). UI thread (cmsg_01LvQiYGyHLhwyRNcM6wP1Dp7gZePrJawC2SWgKAK2146C) builds a Clothing kit page on this API (Josiah's ask 02:29).
Batch 1 preview: https://claude.ai/artifact/W72i2AvsVVtwznqjM5nasf (source scratchpad clothes/template.html + build_page.py). Not yet checked against photos.
Batch 1 IN PLAYTEST 2026-10-29 ~03:05 (merge 345d75f9). Josiah 03:24 said go ahead without his feedback. Batches 2 headwear, 3 tops/outer, 4 legs/feet/hands/neck in playtest by 04:20 and placed by the world-loot thread; batch 5 uniforms/armour/aircrew merged 04:30 (efd6220b). ALL wearables made (91). Not made as clothing because they already exist in ModItems: combat_helmet, plate_carrier (a BagItem carry vest; registering it twice crashes the server), leather/tool/duty belts. Full wardrobe preview: https://claude.ai/artifact/THFbk2CoWgyUanmjS9fhAZ (scratchpad build_page.py). Remaining: recheck all against refs/items/clothing photos when they land; await his feedback. Playtest merges always conflict on en_us.json: take ours, rerun gen_clothes.py.
2026-10-29 07:40: refs rechecked (4 recoloured); shoulders fixed (Garment.shell closes arm/body tops d above skin; flush tops showed the skin's 0.25 outer layer); paint now goes through tools/weapons/toon.py (pieces/cover/outer_edge/style, gen_clothes.paint) per Josiah's toon ask; VEST_D 1.4. 09:25: DONE, clothes use the shared mesh renderer (meshes/clothing/<gid>.zmesh, parts loot + body parts; items/clothing/<gid>/<part>.json type zomboidcraft:mesh; painted ink off, hull outlines). Check worn looks with .github/workflows/clothesshots.yml (pushes to shots/clothes). Cap crown must clear model y 16.25 or hair shows. In playtest 3991e51ca.
