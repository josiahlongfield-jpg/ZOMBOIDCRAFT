# Usage: python3 tools/sandbox/gen_sandbox.py pz-sandbox-options.json src/main/resources/data/zomboidcraft/sandbox/zomboid.json
"""Turns the PinDrop B42 sandbox list into the mod's resource: data/zomboidcraft/sandbox/zomboid.json."""
import json, re, sys
src, out = sys.argv[1], sys.argv[2]
d = json.load(open(src))
GROUPS = {'Zombie population': 'population', 'Zombie lore': 'lore', 'Time and start': 'time',
          'Water, power and world events': 'utilities', 'Loot': 'loot', 'Nature, weather and farming': 'nature',
          'Character, health and food': 'character', 'Skills, XP and reading': 'skills', 'Vehicles and fuel': 'vehicles',
          'Firearms': 'firearms', 'Animals and rats': 'animals', 'Map, basements and clean-up': 'map'}
PRESETS = {'Apocalypse': 'apocalypse', 'Rising': 'rising', 'Outbreak': 'outbreak', 'Extinction': 'extinction',
           'Six Months Later': 'six_months_later'}

OWNERS = {'population': 'ENTITY', 'lore': 'ENTITY', 'time': 'COMMANDS', 'utilities': 'LANDSCAPE', 'loot': 'LOOT',
          'nature': 'SURVIVAL', 'character': 'SURVIVAL', 'skills': 'SURVIVAL', 'vehicles': 'VEHICLES',
          'firearms': 'WEAPONS', 'animals': 'SURVIVAL', 'map': 'LANDSCAPE'}
BY_ID = [(r'^(DayNightCycle|NightDarkness|NightLength|ClimateCycle|FogCycle|Temperature|Rain|MaxFogIntensity|'
          r'MaxRainFxIntensity|EnableSnowOnGround)$', 'WEATHER'), (r'^FireSpread$', 'WEAPONS'),
         (r'^TimeSinceApo$', 'LOOT'), (r'^Water', 'SURVIVAL'), (r'^(Generator|AllowExteriorGenerator|LightBulb)', 'BASES'),
         (r'^Helicopter$', 'ENTITY'), (r'^(MetaEvent|SleepingEvent)$', 'AUDIO'), (r'^SirenShutoffHours$', 'VEHICLES'),
         (r'^Erosion', 'NONE (no erosion for now)'), (r'^Map\.', 'UI'), (r'^(HoursForCorpseRemoval|DecayingCorpse)', 'ENTITY')]

def owner(e):
    for pat, who in BY_ID:
        if re.search(pat, e['id']):
            return who
    return OWNERS[e['group']]

def snake(s):
    return re.sub(r'[^a-z0-9]+', '_', s.lower()).strip('_')

opts = []
for o in d['options']:
    r = o['range'].strip()
    e = {'id': o['sandboxVar'].replace(' (?)', ''), 'group': GROUPS[o['group']], 'label': o['label'],
         'tooltip': ' '.join(o['tooltip'].split())}
    vals = o['presets'] if 'presets' in o else {'Apocalypse': o['default']}
    if r == 'On / Off':
        e['kind'] = 'toggle'
        conv = lambda v: 'true' if v.strip().lower() == 'on' else 'false'
    elif r.endswith('choices'):
        e['kind'] = 'choice'
        e['count'] = int(r.split()[0])
        conv = snake
    elif r == 'text':
        e['kind'] = 'text'
        conv = lambda v: '' if v.startswith('(') or v.endswith('items') else v
    else:
        e['kind'] = 'number'
        m = re.fullmatch(r'(-?[\d.]+) – (-?[\d.]+)', r)
        if m:
            e['min'], e['max'] = float(m.group(1)), float(m.group(2))
        elif r.endswith('or more'):
            e['min'], e['max'] = float(r.split()[0]), 1e9
        else:  # the page printed no range: span what the presets use
            nums = [float(v) for v in vals.values()]
            e['min'], e['max'] = min(nums + [1]), max(nums + [4])
        conv = lambda v: v.strip()
    seen_vals = {PRESETS[k]: conv(v) for k, v in vals.items()}
    e['default'] = seen_vals['apocalypse']
    if e['kind'] == 'choice':
        seen = []
        for v in seen_vals.values():
            if v not in seen:
                seen.append(v)
        e['choices'] = seen
    e['owner'] = owner(e)
    if not o['idExactCase']:
        e['idChecked'] = False
    opts.append(e)
json.dump({'source': d['meta']['source'], 'build': d['meta']['build'], 'options': opts}, open(out, 'w'), indent=1)
print(len(opts))
