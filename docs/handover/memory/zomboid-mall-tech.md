---
name: zomboid-mall-tech
description: Grayhaven Mall builder (Mall*.java, ml_ kit in tools/city/mall.py), sign tile order, preview coords and access-check results
metadata:
  type: project
---

Built 2026-10-28 by LANDSCAPE (variant "mall" in LandmarkSite, 293x213 lot). Files in world/county:
Mall (rows, court, glass roof, entrances), MallWings (quad cinema at the north, food court at the south),
MallAnchor (Harlan's brick, Bellmont), MallShop (unit fit-out, brands, mannequins, grilles),
MallParking (front/back lots, two 3-level decks: ramps of garage_ramp_half, Stairwell towers),
MallSigns (brand, name, pylon, directory, banner).

ml_ kit: tools/city/mall.py (registered in photo_textures.py after fitout). BRANDS dict = shop
channel-letter signs (4x1, key = Trade name lowercased, "lease" for vacant); NAMES = big signs
(mall 12x2, harlans/bellmont 12x3, cinema 6x2, food 6x1). Lit variants are _on.
Sign tile order: tile 0 = viewer's left; facing north puts tile 0 at high x, south low x,
east high z, west low z; row 0 = top (MallSigns.at()).

Preview: `SitePreview DIR landmark mall 293 213 SEED 8`; dump coords = site +8 in x/z, ground y 12;
mesh/camera coords = site x/z, y + 12. Do NOT include CitySpecs in the offline javac.
Access checks 2026-10-28 (seeds 1-3): 0 blocked doors, 0 blocked treads, cinema + food court roofs
reached; leftover "(jump)" pockets are rack tops. Shops.cases() till now skips back-room doors.
DETAIL PASS 2026-10-30 (from refs city/mall-*): Mall.terrazzo(x,z) draws rose/slate rings round the
court and panels along the concourse (ml_terrazzo_rose/slate); Mall.plant() is the shared planter
filler (mu_palm stacks, ml_ficus_lo/hi, ferns), no Trees.grow; red cr_strut_red trusses every 8 x
under the glass roof (Harbour.member); MallWings food court uses ml_table_unit; cinema front is a
glazed box (steel_mullion, NOT tx_column which renders striped) between brick towers; fountain is
Round.wall rd_twr drums (basin r5.4, bowl r2.4, cup r1.2); entrance canopies 6 deep on red posts
with bollards. mall.py main() keeps blocks.json order (append only). Preview: plan/ml/go.sh NAME mall 293 213 1 160.
