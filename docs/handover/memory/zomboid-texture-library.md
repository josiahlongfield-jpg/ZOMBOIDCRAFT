---
name: zomboid-texture-library
description: Where ZomboidCraft's CC0 PBR texture library lives, how it is organized, and how to add more
metadata:
  type: reference
  modified: 2026-10-29T09:36:00Z
---

The library is in project files at zomboid-mod/cc0/. There is one folder per asset ID. CamelCase folders come from ambientCG (1K-PNG zips, with *.blend/usdc/tres/mtlx stripped). snake_case folders come from Poly Haven (1k png maps). Every folder has a LICENSE.txt (CC0). zomboid-mod/cc0/index.md lists the assets by map area: port, suburbs, house interiors, military, small town, countryside, downtown, skyscraper lobby/offices/stairwells, hospital, police, subway, grime decals, plants. These areas match Josiah's reference/areas-concepts-a/b.png.

Built 2026-10-24/25: about 570 sets. Josiah: "spare no details", lean into a moody, battle-scarred apocalypse look.

How to add more:
- ambientCG API: https://ambientcg.com/api/v2/full_json?type=Material|Atlas|Decal&q=..&include=downloadData. Atlas/Decal sets have Opacity maps.
- Poly Haven: api.polyhaven.com/files/<id>. dl.polyhaven.org returns 403 without a User-Agent header.

Neither site has CC0 broken glass, scorched/charred wood, trash bags or blood, so those must be hand-made.
Related: [[josiah-world-art-direction]], [[zomboid-mod-status]].
Storage (2026-10-25): the library is 8.5 GB, and project files were 9.0/10 GB. The OpenGL normals were deleted at Josiah's request; only NormalDX / nor_dx remain (library now 6.1 GB). 2026-10-29 09:35 (Josiah: "trim everything it doesnt need", LANDSCAPE confirmed): deleted every NormalDX/nor_dx/nor_gl, AmbientOcclusion/ao, ARM, bump, spec, anisotropy and Scattering map (1143 files); library now 2.7 GB. What remains is what photo_textures.py load() reads: Color/diff, Displacement/disp (source of height+normals), Roughness/rough, Metalness/metal, Opacity/alpha, plus Emission, masks, JPG-only sets and thumbnails. Never drop whole sets: kit files pick them by name.
