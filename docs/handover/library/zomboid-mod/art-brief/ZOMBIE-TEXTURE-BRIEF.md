# ZomboidCraft zombie texture brief

Give the art AI two things: **this whole brief** and **zombie-template.png**. Make one image per outfit: start with the main prompt, then add that outfit's line from the list below.

---

## Main prompt (paste this every time)

I'm making textures for a blocky zombie character in a Minecraft-style game. Attached is a template showing the character from four sides: FRONT, BACK, LEFT SIDE and RIGHT SIDE. Each grey rectangle is one block of the body. The head is a cube, and the body, arms and legs are rectangular boxes.

Paint the zombie described below onto the template. The image will be cut up and wrapped onto a 3D model, so follow these rules exactly:

1. **Keep the layout identical.** Same canvas size (2048 × 1216), same positions, same rectangle sizes. Fill each grey rectangle edge to edge, and paint nothing outside them. Keep the magenta (#FF00FF) background everywhere else.
2. **Flat, straight-on views with no perspective.** Each rectangle is a flat surface seen head-on, like a texture sheet or a paper cut-out, not a 3D render.
3. **Flat, even lighting.** No shadows, no highlights, no rim light, no glow, no depth of field, no vignette. Paint only the true surface colours (albedo). The game adds lighting later. Dirt, grime, stains and painted fabric folds are fine.
4. **No outlines or borders** around the rectangles or body parts. Remove the grey colour and the labels completely.
5. **Same character in all four views.** Clothes, wounds and blood should match up across views. Something on the front of the left arm in FRONT shows on the same arm in LEFT SIDE.
6. **The face goes on the FRONT head square only**, filling it. The back of the head is hair or scalp. The side views show ears and the sides of the hair.
7. **No hat brims, cap peaks or backpacks sticking out.** Paint only the part of a hat that sits on the head (the crown and band) inside the head square. Brims and backpacks are added as separate pieces.
8. **Left and right are the zombie's own.** In FRONT, the zombie's right arm is on the viewer's left. In LEFT SIDE the zombie faces left, and in RIGHT SIDE it faces right.
9. **Style:** realistic, gritty, detailed painted textures, like a high-end horror game, but kept readable at small size. Decayed grey-green dead skin, sunken dark eye sockets, dull clouded eyes, a gaping mouth with stained broken teeth. Blood soaked into the clothes rather than sitting on top, dried blood darker and browner. Torn, filthy clothing.
10. **Keep the gore moderate:** dirty and bloodied, with some blood on the chin, chest and hands. Avoid huge wounds or exposed bones; heavier damage versions are made separately.
11. Arms hang straight down at the sides, and the hands are the bottom part of each arm rectangle (skin, bloody fingers). Shoes or bare feet are the bottom part of each leg rectangle.
12. **Fine detail, sharp edges.** Paint at least 32 small pixels across the face (not 8 to 10 big blocks like a classic Minecraft skin), with crisp edges and no blur or soft upscaling.

Outfit for this image:

---

## Outfit lines (add one after the main prompt)

- **civilian:** Ordinary man or woman. Faded plain T-shirt (white, grey, red, blue or green), blue jeans, worn brown or black shoes. Short or long messy hair.
- **hoodie:** Dark grey or charcoal hoodie with the hood UP covering the top, back and sides of the head, with the face visible in the hood opening. Kangaroo pocket, drawstrings, dark jeans, black sneakers.
- **office:** White or pale blue dress shirt with a collar and a loosened red or navy tie, dark grey trousers, black belt, black leather shoes. Neat hair gone messy.
- **police:** Navy police uniform shirt with a gold badge on the left chest, shoulder patches, a name tag and buttons. Black duty belt with a silver buckle, navy trousers, black boots. Navy peaked police cap crown with a gold badge on the front (no peak).
- **nurse:** Light blue hospital scrubs, a V-neck top with a chest pocket and pen, matching scrub trousers, white shoes. Grey or brown hair.
- **doctor:** Long white lab coat over a pale blue shirt with a dark tie, a black stethoscope around the neck, a pen in the coat pocket, dark trousers, brown shoes.
- **construction:** Grey T-shirt under a bright orange high-visibility vest with silver reflective stripes across the chest and over the shoulders. Blue jeans, tan work boots. Yellow hard hat crown (no brim).
- **firefighter:** Tan firefighter turnout coat and trousers with yellow and silver reflective bands at the chest, waist, arms and knees, plus coat clasps. Black boots. Black fire helmet crown with a gold shield badge on the front (no brim).
- **farmer:** Red and black plaid flannel shirt, blue denim overalls with straps and brass buttons, brown work boots. Woven straw hat crown with a brown band (no brim).
- **prisoner:** Bright orange prison jumpsuit with a front zip and a white number patch on the chest and back. White slip-on shoes. Short hair.
- **soldier:** Woodland camouflage military uniform (green, brown, black, khaki), a load-bearing harness with straps and pouches, a webbing belt, black combat boots. Camo-covered combat helmet crown over the top and down over the ears.
- **survivor:** Worn camo jacket with the backpack shoulder straps visible on the front, a zip, dark grey cargo trousers, brown boots. Camo bush-hat crown (no brim). The back of the jacket can be plain, since the backpack covers it.
- **security:** Light blue security guard shirt with a silver badge, shoulder patches, a radio clipped on the chest and a dark tie. Black trousers, black belt, black shoes. Black peaked security cap crown with a silver badge (no peak).
- **jogger:** Black track jacket with white stripes down the sleeves and a zip, black running shorts with white side stripes, bare legs below the knee, white running shoes.
- **patient:** White hospital gown with a small blue dot pattern, gown sleeves to the elbow, bare arms, white hospital socks or slippers. Messy hair.

---

## Optional extras

- **Skin tones:** one version per outfit is enough; Claude makes the pale, olive and dark versions from it. If you'd like to control them yourself, make extra versions and say "pale, grey-green skin", "olive skin" or "dark brown skin".
- **Women and men:** mention it in the outfit line if you want a specific one. Mixing them across outfits is good.

## Sending them back

- Keep each image at 2048 × 1216 and save it as PNG.
- Name each file after the outfit, e.g. `police.png`, `farmer.png`.
- Upload them to Claude in the project. Claude will cut them up, fit them onto the model, add the shine and bump maps and the light/medium/heavy/bones-showing damage versions, and send a preview before shipping.

## Quick check before sending

- The magenta background is still there and nothing is painted outside the rectangles.
- There are no shadows, outlines or labels.
- The face is on the FRONT head square and fills it.
- The views match each other (same clothes, same blood in the same places).
