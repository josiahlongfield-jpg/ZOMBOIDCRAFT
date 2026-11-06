"""Wave 4 stage 5: the 100 base decor pieces (Josiah's approved list, doc 6b6d542d), and what each one does.

Writes data/zomboidcraft/decor.json for com.zomboidcraft.decor.Decor. Roster rows (models, loot placement) are
added per batch once REFERENCES' photos for the batch are in (refs/items/decor/<id>/), never before.

Columns: id | rarity | value | kg | found in (place tags; '-' for pieces that only stand in buildings) | looks |
comfort | flags | play | noise | light
  comfort  what it adds to the room it stands in (rarity's base; a centrepiece more)
  flags    P needs power to work, B big (moved with a tool by building/Moving), L gives light when working,
           T toggled on and off from the G menu
  play     boredom it takes off per go from the G menu (0: not playable); stress eases a third as much
  noise    blocks the dead hear it from while it's on (0: quiet)
  light    light level while working
  special  what else it does (Decor.java's SPECIAL handlers), or '-'

    python3 tools/items/loot/decor.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'src/main/resources/data/zomboidcraft/decor.json'

DECOR = """
# lighting and neon (20)
string_lights | C | 4 | 0.4 | bed hard | 8 | 1 | PLT | 0 | 0 | 8 | -
fibre_optic_lamp | C | 6 | 0.8 | kids thrift | 6 | 2 | PLT | 0 | 0 | 5 | -
lava_lamp | S | 15 | 1.5 | bed thrift | 12 | 3 | PLT | 0 | 0 | 6 | -
neon_word_sign | S | 20 | 3.0 | liq diner | 30 | 3 | PLT | 0 | 0 | 10 | -
neon_beer_sign | S | 25 | 3.0 | liq | 12 | 3 | PLT | 0 | 0 | 10 | -
disco_ball | S | 18 | 2.0 | - | 3 | 2 | PLT | 0 | 0 | 7 | spots
black_light | S | 10 | 0.6 | bed toyshop | 2 | 1 | PLT | 0 | 0 | 4 | blacklight
strobe_light | S | 12 | 0.8 | - | 2 | 0 | PLT | 0 | 24 | 9 | lure
plasma_ball | R | 25 | 1.2 | elec school | 3 | 3 | PLT | 0 | 0 | 5 | calm
neon_wall_shape | R | 40 | 4.0 | - | 20 | 6 | PLT | 0 | 0 | 11 | -
neon_tube_kit | R | 30 | 2.0 | hard | 8 | 1 | PL | 0 | 0 | 9 | tubes
rotating_beacon | R | 22 | 2.5 | garage fire police | 4 | 0 | PLT | 0 | 0 | 12 | beacon
marquee_bulb_letters | R | 35 | 1.5 | - | 26 | 2 | PLT | 0 | 0 | 9 | letters
edison_chandelier | R | 30 | 6.0 | liq diner | 3 | 4 | PLT | 0 | 0 | 13 | -
aquarium | R | 35 | 30.0 | office | 5 | 5 | PLB | 0 | 0 | 6 | calm
neon_arrow_sign | P | 70 | 25.0 | - | 4 | 4 | PLBT | 0 | 0 | 15 | -
tiffany_lamp | P | 60 | 4.0 | mansion pawn | 6 | 6 | PLT | 0 | 0 | 10 | -
light_up_dance_floor | P | 80 | 12.0 | - | 4 | 4 | PLB | 0 | 0 | 8 | step_alarm
gas_pump_globe | P | 70 | 3.0 | pawn | 10 | 5 | PLT | 0 | 0 | 9 | -
searchlight | P | 90 | 45.0 | - | 1 | 0 | PLBT | 0 | 48 | 15 | sweep
# games and leisure (18)
dartboard | C | 6 | 1.5 | liq garage bed | 4 | 1 | - | 12 | 0 | 0 | aim_xp
pool_cue | C | 4 | 0.5 | liq | 4 | 0 | - | 0 | 0 | 0 | cue
chess_set | C | 5 | 1.0 | bed school books | 6 | 1 | - | 15 | 0 | 0 | -
foosball_table | S | 40 | 45.0 | - | 4 | 2 | B | 20 | 6 | 0 | -
ping_pong_table | S | 35 | 60.0 | - | 2 | 2 | B | 20 | 6 | 0 | -
poker_chip_case | S | 12 | 2.5 | bed liq pawn | 3 | 1 | - | 12 | 0 | 0 | -
bowling_ball_pins | S | 15 | 7.0 | - | 8 | 1 | - | 8 | 10 | 0 | roll
punching_bag | S | 20 | 30.0 | gym garage | 3 | 1 | B | 10 | 6 | 0 | train
arcade_cabinet | R | 80 | 110.0 | - | 24 | 4 | PBT | 30 | 8 | 6 | -
pool_table | R | 90 | 300.0 | - | 6 | 4 | B | 25 | 4 | 0 | needs_cue
air_hockey_table | R | 70 | 90.0 | - | 3 | 3 | PB | 25 | 8 | 4 | -
poker_table | R | 60 | 60.0 | - | 2 | 3 | B | 15 | 0 | 0 | -
home_console | R | 40 | 3.0 | bed elec pawn | 6 | 2 | P | 25 | 4 | 0 | needs_tv
cocktail_arcade | P | 100 | 70.0 | - | 8 | 5 | PBT | 30 | 6 | 5 | -
pinball_machine | P | 120 | 110.0 | - | 12 | 5 | PBT | 35 | 14 | 6 | -
claw_machine | P | 90 | 120.0 | - | 4 | 4 | PBT | 15 | 6 | 7 | prize_toy
skee_ball_lane | P | 85 | 150.0 | - | 2 | 4 | PBT | 25 | 10 | 5 | tickets
slot_machine | P | 110 | 80.0 | - | 6 | 4 | PBT | 15 | 8 | 6 | payout
# furniture and comfort (18)
bean_bag | C | 8 | 4.0 | bed | 8 | 2 | - | 0 | 0 | 0 | seat
bar_stools | C | 6 | 6.0 | - | 5 | 1 | - | 0 | 0 | 0 | seat
hand_truck | S | 20 | 14.0 | factory hard dock | 1 | 0 | - | 0 | 0 | 0 | hand_truck
recliner | S | 30 | 40.0 | thrift | 6 | 4 | B | 0 | 0 | 0 | rest_fast
hammock | S | 15 | 3.0 | bed camp | 4 | 3 | - | 0 | 0 | 0 | bed
velvet_rope_stands | S | 12 | 12.0 | bank | 3 | 1 | - | 0 | 0 | 0 | -
mounted_deer_head | S | 25 | 8.0 | camp liq | 4 | 2 | - | 0 | 0 | 0 | -
waterbed | R | 50 | 90.0 | - | 2 | 5 | B | 0 | 0 | 0 | bed_best
barber_chair | R | 45 | 70.0 | - | 2 | 3 | B | 0 | 0 | 0 | haircut
diner_booth | R | 40 | 60.0 | - | 4 | 3 | B | 0 | 0 | 0 | seat
home_bar | R | 50 | 70.0 | - | 3 | 4 | B | 0 | 0 | 0 | drinks_store
wood_stove | R | 45 | 120.0 | hard | 3 | 3 | B | 0 | 0 | 0 | stove_heat
persian_rug | R | 40 | 12.0 | mansion pawn | 8 | 4 | - | 0 | 0 | 0 | muffle
vending_machine | R | 60 | 250.0 | - | 6 | 2 | PB | 0 | 0 | 6 | cold_store
massage_chair | P | 80 | 80.0 | - | 2 | 5 | PB | 0 | 0 | 0 | massage
grandfather_clock | P | 90 | 60.0 | mansion | 3 | 6 | B | 0 | 12 | 0 | chime
stuffed_bear | P | 100 | 150.0 | - | 2 | 8 | B | 0 | 0 | 0 | -
theatre_throne | G | 150 | 45.0 | - | 1 | 12 | B | 0 | 0 | 0 | seat
# wall art and display (16)
band_poster | C | 2 | 0.1 | bed | 40 | 1 | - | 0 | 0 | 0 | glows
movie_poster | C | 2 | 0.1 | bed | 40 | 1 | - | 0 | 0 | 0 | -
wall_calendar | C | 1 | 0.2 | bed office desk | 12 | 1 | - | 0 | 0 | 0 | days
pennant_set | C | 3 | 0.2 | school stadium | 12 | 1 | - | 0 | 0 | 0 | -
tin_sign | S | 8 | 0.8 | garage diner pawn | 30 | 2 | - | 0 | 0 | 0 | -
licence_plate_set | S | 10 | 3.0 | garage auto | 20 | 2 | - | 0 | 0 | 0 | -
neon_clock | S | 15 | 2.0 | diner garage | 6 | 2 | PL | 0 | 0 | 7 | time
butterfly_display | S | 12 | 1.0 | bed | 4 | 2 | - | 0 | 0 | 0 | -
spray_paint_kit | S | 6 | 1.5 | hard | 12 | 0 | - | 0 | 0 | 0 | paint
framed_gold_record | R | 40 | 2.0 | mansion | 6 | 4 | - | 0 | 0 | 0 | -
mounted_swordfish | R | 35 | 10.0 | dock liq | 3 | 3 | - | 0 | 0 | 0 | -
antique_map | R | 30 | 0.5 | books mansion | 4 | 3 | - | 0 | 0 | 0 | back_road
stuffed_owl | R | 25 | 1.5 | mansion pawn | 2 | 3 | - | 0 | 0 | 0 | -
signed_jersey_frame | P | 70 | 4.0 | stadium mansion | 10 | 5 | - | 0 | 0 | 0 | -
stained_glass_panel | P | 60 | 8.0 | church pawn | 6 | 5 | - | 0 | 0 | 0 | coloured_light
film_prop_display | G | 200 | 20.0 | mansion | 6 | 10 | - | 0 | 0 | 0 | -
# music and media (14)
vinyl_record | C | 3 | 0.2 | bed thrift | 60 | 0 | - | 0 | 0 | 0 | record
record_player | S | 25 | 5.0 | bed thrift | 4 | 2 | PT | 20 | 10 | 0 | needs_record
karaoke_machine | S | 20 | 6.0 | liq bed | 2 | 1 | PT | 25 | 30 | 0 | -
big_crt_tv | S | 30 | 35.0 | bed | 3 | 2 | PBT | 20 | 8 | 5 | vhs
accordion | S | 20 | 8.0 | thrift bed | 2 | 1 | - | 20 | 16 | 0 | -
stereo_system | R | 40 | 12.0 | bed elec | 3 | 3 | PT | 25 | 36 | 0 | -
guitar_amp | R | 60 | 25.0 | bed | 8 | 3 | PT | 30 | 30 | 0 | -
drum_kit | R | 70 | 40.0 | school | 4 | 3 | B | 30 | 32 | 0 | -
dj_turntables | R | 50 | 15.0 | - | 2 | 3 | PT | 25 | 30 | 0 | needs_record
projector_screen | R | 45 | 20.0 | school | 2 | 3 | PT | 35 | 4 | 6 | vhs
jukebox | P | 120 | 110.0 | - | 4 | 6 | PBT | 25 | 36 | 6 | -
upright_piano | P | 100 | 200.0 | - | 3 | 6 | B | 35 | 20 | 0 | -
gramophone | P | 60 | 8.0 | mansion pawn | 2 | 4 | T | 20 | 10 | 0 | needs_record
reel_to_reel | P | 55 | 18.0 | - | 1 | 3 | PT | 15 | 6 | 0 | old_broadcast
# collectibles and oddities (14)
bobblehead | C | 3 | 0.3 | stadium office glove | 20 | 1 | - | 0 | 0 | 0 | -
mannequin | C | 5 | 9.0 | cloth | 4 | 0 | - | 0 | 0 | 0 | dress_decoy
cardboard_cutout | C | 3 | 2.0 | - | 10 | 1 | - | 0 | 0 | 0 | decoy
lucky_cat | C | 4 | 0.8 | diner | 3 | 1 | - | 0 | 0 | 0 | -
garden_gnome | C | 3 | 2.0 | farm hard | 12 | 1 | - | 0 | 0 | 0 | -
ship_in_bottle | S | 12 | 1.0 | dock pawn | 6 | 2 | - | 0 | 0 | 0 | -
desk_globe | S | 15 | 2.5 | books school office | 3 | 2 | - | 0 | 0 | 0 | -
gumball_machine | S | 15 | 8.0 | diner laund | 4 | 2 | - | 0 | 0 | 0 | sweets_store
model_train_set | R | 45 | 6.0 | toyshop bed | 4 | 4 | PT | 15 | 0 | 0 | -
diving_helmet | P | 90 | 15.0 | dock pawn | 1 | 6 | - | 0 | 0 | 0 | -
fossil | P | 70 | 6.0 | - | 4 | 6 | - | 0 | 0 | 0 | -
carousel_horse | G | 180 | 40.0 | - | 6 | 10 | B | 0 | 0 | 0 | -
suit_of_armour | G | 220 | 30.0 | mansion | 2 | 10 | B | 0 | 0 | 0 | -
meteorite | G | 250 | 4.0 | - | 1 | 8 | - | 0 | 0 | 0 | -
"""

NAMES = {
    'string_lights': 'String Lights', 'fibre_optic_lamp': 'Fibre Optic Lamp', 'lava_lamp': 'Lava Lamp',
    'neon_word_sign': 'Neon Word Sign', 'neon_beer_sign': 'Neon Beer Sign', 'disco_ball': 'Disco Ball',
    'black_light': 'Black Light', 'strobe_light': 'Strobe Light', 'plasma_ball': 'Plasma Ball',
    'neon_wall_shape': 'Neon Wall Shape', 'neon_tube_kit': 'Neon Tube Kit', 'rotating_beacon': 'Rotating Beacon',
    'marquee_bulb_letters': 'Marquee Bulb Letters', 'edison_chandelier': 'Edison Bulb Chandelier',
    'aquarium': 'Aquarium', 'neon_arrow_sign': 'Neon Arrow Sign', 'tiffany_lamp': 'Tiffany-Style Lamp',
    'light_up_dance_floor': 'Light-Up Dance Floor', 'gas_pump_globe': 'Gas Pump Globe', 'searchlight': 'Searchlight',
    'dartboard': 'Dartboard', 'pool_cue': 'Pool Cue', 'chess_set': 'Chess Set', 'foosball_table': 'Foosball Table',
    'ping_pong_table': 'Ping Pong Table', 'poker_chip_case': 'Poker Chip Case',
    'bowling_ball_pins': 'Bowling Ball and Pins', 'punching_bag': 'Punching Bag', 'arcade_cabinet': 'Arcade Cabinet',
    'pool_table': 'Pool Table', 'air_hockey_table': 'Air Hockey Table', 'poker_table': 'Poker Table',
    'home_console': 'Home Console Bundle', 'cocktail_arcade': 'Cocktail Arcade Table',
    'pinball_machine': 'Pinball Machine', 'claw_machine': 'Claw Machine', 'skee_ball_lane': 'Alley Roll Lane',
    'slot_machine': 'Slot Machine', 'bean_bag': 'Bean Bag', 'bar_stools': 'Bar Stools', 'hand_truck': 'Hand Truck',
    'recliner': 'Recliner', 'hammock': 'Hammock', 'velvet_rope_stands': 'Velvet Rope Stands',
    'mounted_deer_head': 'Mounted Deer Head', 'waterbed': 'Waterbed', 'barber_chair': 'Barber Chair',
    'diner_booth': 'Diner Booth', 'home_bar': 'Home Bar', 'wood_stove': 'Wood Stove', 'persian_rug': 'Persian Rug',
    'vending_machine': 'Vending Machine', 'massage_chair': 'Massage Chair', 'grandfather_clock': 'Grandfather Clock',
    'stuffed_bear': 'Stuffed Bear', 'theatre_throne': 'Theatre Throne', 'band_poster': 'Band Poster',
    'movie_poster': 'Movie Poster', 'wall_calendar': 'Wall Calendar', 'pennant_set': 'Pennant Set',
    'tin_sign': 'Tin Sign', 'licence_plate_set': 'Licence Plate Collection', 'neon_clock': 'Neon Clock',
    'butterfly_display': 'Butterfly Display', 'spray_paint_kit': 'Spray Paint Kit',
    'framed_gold_record': 'Framed Gold Record', 'mounted_swordfish': 'Mounted Swordfish',
    'antique_map': 'Antique Map', 'stuffed_owl': 'Stuffed Owl', 'signed_jersey_frame': 'Signed Jersey Frame',
    'stained_glass_panel': 'Stained Glass Panel', 'film_prop_display': 'Film Prop Display',
    'vinyl_record': 'Vinyl Record', 'record_player': 'Record Player', 'karaoke_machine': 'Karaoke Machine',
    'big_crt_tv': 'Big CRT TV', 'accordion': 'Accordion', 'stereo_system': 'Stereo System',
    'guitar_amp': 'Electric Guitar and Amp', 'drum_kit': 'Drum Kit', 'dj_turntables': 'DJ Turntables',
    'projector_screen': 'Projector and Screen', 'jukebox': 'Jukebox', 'upright_piano': 'Upright Piano',
    'gramophone': 'Gramophone', 'reel_to_reel': 'Reel-to-Reel Deck', 'bobblehead': 'Bobblehead',
    'mannequin': 'Mannequin', 'cardboard_cutout': 'Cardboard Cutout', 'lucky_cat': 'Lucky Cat Statue',
    'garden_gnome': 'Garden Gnome', 'ship_in_bottle': 'Ship in a Bottle', 'desk_globe': 'Desk Globe',
    'gumball_machine': 'Gumball Machine', 'model_train_set': 'Model Train Set', 'diving_helmet': 'Diving Helmet',
    'fossil': 'Fossil', 'carousel_horse': 'Carousel Horse', 'suit_of_armour': 'Suit of Armour',
    'meteorite': 'Meteorite Fragment',
}


def rows():
    out = []
    for line in DECOR.strip().splitlines():
        if line.startswith('#'):
            continue
        f = [c.strip() for c in line.split('|')]
        out.append(dict(id=f[0], name=NAMES[f[0]], rarity=f[1], value=int(f[2]), kg=float(f[3]),
                        tags=[] if f[4] == '-' else f[4].split(), looks=int(f[5]), comfort=int(f[6]),
                        power='P' in f[7], big='B' in f[7], light_source='L' in f[7], toggle='T' in f[7],
                        play=int(f[8]), noise=int(f[9]), light=int(f[10]), special=None if f[11] == '-' else f[11]))
    return out


def main():
    rs = rows()
    assert len(rs) == 100, len(rs)
    assert len({r['id'] for r in rs}) == 100
    OUT.write_text(json.dumps({r['id']: {k: v for k, v in r.items() if k not in ('id', 'tags', 'value')}
                               for r in rs}, indent=1) + '\n')
    print(f'{len(rs)} decor pieces -> {OUT.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
