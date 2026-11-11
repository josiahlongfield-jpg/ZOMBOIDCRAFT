---
name: zomboid-nature-packs-tech
description: How Josiah's bought tree and grass packs become ZomboidCraft blocks (trees.py, groundcover.py, Cover.java), where the pack originals live, preview tricks
metadata:
  type: reference
  modified: 2026-10-26T16:12:02.079Z
---

Shipped to playtest 2026-10-26 (LANDSCAPE thread, overnight mandate [[zomboid-overnight-mandate]]).

**Pack originals**: Josiah staged 7 packs from his PC's Desktop "additions" folder (the only folder he allowed) into the container at /tmp/claude-0/packs/<pack>/ (ultimate-trees-pack, grass-groundcover-pack-01, kitchen, vintage-office-cabinets-collection, low-poly-office-pack-*, grocery, pack-of-20-buildings). Not in project files or the repo ([[zomboid-bought-assets-plan]]); a new container must re-stage them from his PC (libarchive ctypes wrapper extracts RAR/zip).

**Trees**: `python3 tools/nature/trees.py PACK/x` (~90 s, always run all species; `--only` writes a partial trees.bin). Voxelises the Deezl OBJ (56 objects, metres) into 90 templates (10 species x 3 sizes) in src/main/resources/data/zomboidcraft/trees.bin, textures in tools/nature/tex/tree_*.png, tree_ blocks in tools/city/blocks.json. Block kinds: bark, log (end grain), side (trunk edge, facing), limb_3/5/8/12 (BRANCH kind: CityBlock.Branch with six side booleans), leaves[_colour][_sparse]. Trees.java grows them (random turn/mirror, autumn/sick crowns).

**Leaves and crowns (2026-10-27)**: leaf_tex stamps each broadleaf's spray at SPECIES "stamp" px on the 192 canvas (leaves 6-11 px on the 64 tile); spray background recoloured dark so small stamps don't ring with bright specks. Leaf blocks have "spin" (gen_blocks: blockstate picks one of 8 x/y turns per position). `tree_*_leaves*_tuft` = crossed sparse sprigs with OFFSET. Trees.java gives every placed tree its own ragged crown: template `shell` byte (open sides bitmask, or INNER for one layer in), value-noise bites, sparse edges, sprigs out of open sides (never straight down; that do-while once hung chunk gen: mask must exclude bit 3). Bark crop seed is stable now.

**Where trees may stand (Josiah 2026-10-27 RULE)**: never on roads, paths, car parks, in buildings or through cars; beside roads/in towns they get a tidy soil bed. Kit.plant(Spot...) dry-runs the tree, rejects/shrinks if trunk or any limb below Kit.HEADROOM (5) is over non-soil or hard ground or a car (Kit.parked), digs a brick-edged pit (pit_pavers, bed_soil, optional grate) on sidewalks, round bed in grass. Builders.clearTrees (every place, after build) drops leaves under anything built, below headroom over paving, inside cars; drops cars that hit wood. CountyGen keeps 4 blocks of headroom over county roads and cuts county crowns where a place has blocks/paving. Builders.clearGrass: grass only on soil; weeds only on cracked/mossy/gravel.

**Ground cover**: `python3 tools/nature/groundcover.py PACK/x [--sheet out.png]` cuts 42 gc_ blocks from the pack atlas (boxes in a 1024 view): gc_grass_1-12, gc_grass_dry_1-6, gc_tall[_dry]_1-3_lower/upper, gc_sprig_1-3, flat gc_clover/leaves/twigs_1-3 (facing). county/Cover.java wraps CountyGen's sink and swaps vanilla short/tall/dry grass, half the ferns, 30% of leaf_litter, and city "grass_tuft"/"weeds"/"weeds_tall" by position hash. "offset": true in a spec gives the OFFSET flag (offsetType XZ).

**Textures**: tools/city/nature.py registers every tools/nature/tex PNG with photo_textures.py; leaves and gc_ cards get flat normals and LabPBR SSS (paint_textures Tex.sss, blue 65-255). Then `photo_textures.py ... NAMES` and `gen_blocks.py`.

**Preview**: offline javac of city+county+tools/county, then CountyPreview; set dump origin to [0,40,0] (int16 overflow otherwise) and mesh with `--near x,y,z --radius 70`. Woods test spot: seed 12345 around (1862,-2199).
