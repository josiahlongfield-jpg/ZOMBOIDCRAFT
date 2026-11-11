# Makes Rebecca's body skin picture: her pale skin colour with the tattoo picture laid on top.
# Run it from the rebecca folder:  python make_skin.py
from PIL import Image

SKIN = (226, 242, 246)  # her pale blue-white skin, as red, green, blue (0-255); change it to tint her

tattoo = Image.open('textures/Becca Tattoo(2).png').convert('RGBA')
skin = Image.new('RGBA', tattoo.size, SKIN + (255,))
skin.alpha_composite(tattoo)              # the tattoo's see-through parts show the skin colour
skin.convert('RGB').save('textures/rebecca_skin.png')
print('saved textures/rebecca_skin.png', skin.size)
