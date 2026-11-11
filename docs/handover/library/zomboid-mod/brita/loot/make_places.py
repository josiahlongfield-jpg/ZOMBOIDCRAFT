#!/usr/bin/env python3
"""
Arsenal's places for the guns we have -> <guns dir>/loot/places.json, which ZomboidCraft's combat/GunLoot reads.

Runs on Josiah's PC only, from the Arsenal (26) GunFighter mod's own files: never commit this file's output, and never
put it in the repo, the friends' jar or CI (Brita/Arsenal data stays local, as the Brita guns do).

    python make_places.py --arsenal "<...>/ARSENAL(26)GunFighter[MOD 2.0]" --guns "<profile>/config/zomboidcraft/guns"

Reads:
  media/sandbox-options.txt                     Arsenal's sandbox defaults (the review: "Arsenal's own default")
  media/lua/server/Items/Brita_06Multi_Distro.lua   its setup block (how each setting becomes a weight) and its lists
  media/scripts/**/*.txt                        each gun's AmmoType and MagazineType
  <guns dir>/*.json                             the Brita guns built for us (their ids and the rounds item they fire)

What it writes, per place (as reviewed on the gun loot check page, 2026-11-02 07:44):
  count   guns one container of that place holds on average with Arsenal's whole set, so a shop isn't bare while we
          have 41 guns (the mod picks that many from the guns we have);
  guns    each of our guns' chance there in Arsenal, its kill switches, the US army region and the crossovers applied;
  extras  Arsenal's chance of each gun's own magazine there, and of the rounds of the guns that fire none of ours.
and rarity: our letter for each gun (C S R P G, the page's column).

How PZ rolls it (ItemPickerJava): a container picks one list from its procList by weightChance (a list with min > 0
is always picked by the first few containers); the list rolls `rolls` times; each item turns up per roll with chance
weight/100. A case or a CCW bag holds one gun, picked by Arsenal's points.
"""
import argparse, glob, json, math, os, re, sys

# our gun ids (the Brita pack's names) that Arsenal's lists call by another name
ALIAS = {'AssaultRifle2': 'M14', 'DSHK': 'DSHK_Fold', 'Ruger_No1_308': 'Ruger_No1_3006', 'HEAVY': 'CheyTac50'}
# the Tarkov guns (Josiah OK'd 2026-11-07 10:26): each takes its Arsenal twin's places and its own letter. Guns sharing a
# twin (ours or Arsenal's) split its chance, so a twin's slot never doubles. The AS VAL and VSK-94 drop to a quarter in
# the game (GunLoot.BUILT_IN_CAN), and no found gun carries a can.
TARKOV = {'tarkov_pm': ('Colt1903', 'S'), 'tarkov_glock17': ('G17', 'C'), 'tarkov_usp': ('HK_USP', 'S'),
          'tarkov_deagle': ('Automag', 'R'), 'tarkov_mp5k': ('MP7', 'R'), 'tarkov_vector': ('MP7', 'P'),
          'tarkov_ak102': ('AK47', 'S'), 'tarkov_m4a1': ('CAR15SMG', 'R'), 'tarkov_mcx': ('G36C', 'R'),
          'tarkov_scarh': ('SCARH', 'P'), 'tarkov_mdr762': ('SCARH', 'P'), 'tarkov_val': ('AAC_Honey', 'P'),
          'tarkov_vsk94': ('AAC_Honey', 'P'), 'tarkov_sr3m': ('AAC_Honey', 'P'), 'tarkov_svds': ('SV_98', 'R'),
          'tarkov_rsass': ('M40A3', 'R'), 'tarkov_sv98': ('SV_98', 'P'), 'tarkov_ak50': ('M82', 'P'),
          'tarkov_m870': ('M870_CQB', 'C'), 'tarkov_aa12': ('SPAS12_Fixed', 'P'), 'tarkov_mp18': ('Ruger_No1_308', 'S'),
          # batch 3 (2026-11-08)
          'tarkov_pl15': ('G17', 'S'), 'tarkov_m1911': ('Colt1911', 'C'), 'tarkov_rhino': ('SW_327', 'R'),
          'tarkov_hk416': ('CAR15SMG', 'R'), 'tarkov_m16': ('CAR15SMG', 'R'), 'tarkov_adar': ('Bush_AR15_MOE', 'S'),
          'tarkov_aug': ('F2000', 'P'), 'tarkov_g36': ('G36C', 'R'), 'tarkov_vepr': ('AK47', 'S')}
ALIAS.update({k: v[0] for k, v in TARKOV.items()})
# a gun file with no ammo of its own fires its anchor's (combat/GunType.ammo)
ANCHOR_AMMO = {'assault_rifle': 'rifle_ammo', 'pistol': 'pistol_ammo', 'smg': 'pistol_ammo', 'machine_pistol': 'pistol_ammo',
               'shotgun': 'shotgun_shell', 'double_barrel': 'shotgun_shell', 'sawn_off': 'shotgun_shell',
               'revolver': 'revolver_ammo', 'hunting_rifle': 'hunting_rifle_ammo', 'battle_rifle': 'hunting_rifle_ammo',
               'precision_rifle': 'hunting_rifle_ammo', 'rimfire': 'rimfire_ammo', 'magnum': 'magnum_ammo',
               'lever_action': 'lever_ammo', 'crossbow': 'crossbow_bolt', 'pocket_pistol': 'pocket_ammo',
               'suppressed_rifle': 'rifle_ammo', 'heavy_sniper': 'heavy_ammo', 'grenade_launcher': 'grenade_round'}
# the rarity column of the check page, as approved
RAR = {'Colt1911': 'C', 'Automag': 'P', 'G17': 'C', 'HK_USP': 'S', 'Colt1903': 'S', 'SW_M500': 'R', 'SW_327': 'R',
       'Colt_Service38': 'S', 'AR57_PDW': 'R', 'AUG9': 'P', 'CAR15SMG': 'R', 'MP7': 'P', 'UZI': 'R', 'M2Carbine': 'S',
       'AAC_Honey': 'P', 'Bush_AR15_MOE': 'S', 'AK47': 'S', 'F2000': 'P', 'G36C': 'P', 'SCARL': 'P', 'AssaultRifle2': 'R',
       'FAL': 'R', 'SCARH': 'P', 'M1': 'S', 'BOAR': 'P', 'M82': 'P', 'M40A3': 'R', 'SV_98': 'P', 'M870_CQB': 'C',
       'UTS15': 'R', 'M1014_Wick': 'R', 'SPAS12_Fixed': 'R', 'Winchester1866': 'S', 'Ruger_No1_308': 'S',
       'American180': 'R', 'L86': 'P', 'M60': 'G', 'PKM': 'G', 'RPG_7': 'G', 'TenPointTurbo_XLT': 'S', 'DSHK': 'G',
       'HEAVY': 'P'}
RAR.update({k: v[1] for k, v in TARKOV.items()})
# our place: (Arsenal's gun list, its ammunition list)
PLACES = {'gunshop': (['GunStoreGuns'], 'GunStoreAmmo'), 'pawn': (['PawnShopGuns', 'PawnShopGunsSpecial'], None),
          'police': (['PoliceGuns'], 'PoliceAmmo'), 'swat': (['A26SWAT_Trunk'], None), 'army': (['ArmyGuns'], 'ArmyAmmo'),
          'security': (['SecurityGuns'], 'SecurityAmmo'), 'hunting': (['HuntGuns'], 'HuntAmmo'),
          'surplus': (['SurplusGuns'], 'SurplusAmmo'), 'storage': (['FirearmWeapons'], None)}
CASES = {'case_pistol': 'Case_Small', 'case_rifle': ('Case_Medium', 'Case_Large', 'Case_Heavy')}


class Env(dict):
    """Arsenal's variables; one never set is 0 (Lua's nil would be an error, so Arsenal sets each it uses)."""
    def __missing__(self, k):
        if k == 'round':
            raise KeyError(k)
        return 0


def lua_expr(expr, env, sandbox):
    e = re.sub(r'SandboxVars\.A26\.(\w+)', lambda m: repr(sandbox.get(m.group(1), 0)), expr)
    e = e.replace('^', '**').replace('~=', '!=')
    e = re.sub(r'\btrue\b', 'True', e)
    e = re.sub(r'\bfalse\b', 'False', e)
    try:
        return eval(e, {'round': lambda x, n=0: round(x, int(n)), '__builtins__': {}}, env)
    except Exception:
        return None


def run(stmts, env, sandbox):
    for st in stmts.split(';'):
        m = re.match(r'\s*(\w+)\s*=\s*(.+?)\s*$', st)
        if m:
            v = lua_expr(m.group(2), env, sandbox)
            if v is not None:
                env[m.group(1)] = v


def setup(lines, sandbox):
    """Arsenal's setup block, as the multiplayer branch (the sandbox settings) runs it."""
    env, mode, chain = Env(), 'on', None
    for raw in lines:
        s = raw.split('--', 1)[0].strip()
        s = re.sub(r'\s+', ' ', s)
        if not s:
            continue
        if s.startswith('require'):
            break
        if s.startswith('if not isServer()'):
            mode = 'skip'           # the single-player branch reads the mod's own options menu
            continue
        if mode == 'skip':
            if s.startswith('else'):
                mode = 'branch'
            continue
        if mode == 'branch' and s == 'end':
            mode = 'on'
            continue
        if s.startswith('local '):
            s = s[6:]
            if '=' not in s:
                continue
        m = re.match(r'^if (.+?) then (.+?)(?: else (.+?))? end$', s)
        if m:
            c = lua_expr(m.group(1), env, sandbox)
            run(m.group(2) if c else (m.group(3) or ''), env, sandbox)
            continue
        m = re.match(r'^(if|elseif) (.+?) then (.+)$', s)
        if m:
            if m.group(1) == 'if':
                chain = False
            if not chain and lua_expr(m.group(2), env, sandbox):
                run(m.group(3), env, sandbox)
                chain = True
            continue
        if s == 'end':
            chain = None
            continue
        m = re.match(r'^(\w+) = (.+)$', s)
        if m:
            v = lua_expr(m.group(2), env, sandbox)
            if v is not None:
                env[m.group(1)] = v
    return env


def tables(lines):
    """Every list Arsenal defines: name -> {rolls, items [(item, expr)]}; plus table.insert additions."""
    out, cur, pending = {}, None, None
    for raw in lines:
        s = raw.split('--', 1)[0].strip().replace('\t', ' ')
        if not s:
            continue
        m = re.match(r'(?:SuburbsDistributions\["(\w+)"\]|VehicleDistributions\.(\w+)|(\w+))\s*=\s*\{', s)
        if m and (m.group(3) or '') not in ('items', 'junk', 'procList'):
            name = m.group(1) or m.group(2) or m.group(3)
            if name.endswith('ProcDistro'):
                continue
            cur = name
            out.setdefault(cur, {'rolls': '1', 'items': []})
            continue
        m = re.match(r'rolls\s*=\s*([^,]+),?', s)
        if m and cur:
            out[cur]['rolls'] = m.group(1).strip()
        m = re.match(r'"Base\.([^"]+)"\s*,\s*(.+?)\s*,?\s*$', s)
        if m and cur:
            out[cur]['items'].append((m.group(1), m.group(2).rstrip(',')))
            continue
        m = re.match(r'table\.insert\((?:(?:SuburbsDistributions|ProceduralDistributions\.list)\["(\w+)"\]|VehicleDistributions\.(\w+))\S*\.items\s*,\s*(.+?)\s*\)\s*;?$', s)
        if m:
            name, arg = m.group(1) or m.group(2), m.group(3)
            if arg.startswith('"Base.'):
                pending = (name, arg.strip('"')[5:])
            elif pending and pending[0] == name:
                out.setdefault(name, {'rolls': '1', 'items': []})['items'].append((pending[1], arg))
                pending = None
    return out


def proc_chances(lines):
    """For each list, the chance a container that can roll it does: 1 if some container must (min > 0), else its share
    of weightChance among that container's lists. The best container counts (ours are the gun fittings)."""
    best, block = {}, []
    for raw in lines:
        s = raw.strip()
        m = re.match(r'\{\s*name\s*=\s*"(\w+)"\s*,\s*min\s*=\s*(\d+)\s*,\s*max\s*=\s*(\d+)\s*,\s*weightChance\s*=\s*(\d+)', s.replace('\t', ' '))
        if m and not s.startswith('--'):
            block.append((m.group(1), int(m.group(2)), int(m.group(4))))
            continue
        if block and (s.startswith('}') or s.startswith('procList')):
            total = sum(w for _, _, w in block) or 1
            for name, mn, w in block:
                best[name] = max(best.get(name, 0), 1.0 if mn > 0 else w / total)
            block = []
    return best


def scripts(arsenal):
    """Each item's AmmoType, MagazineType and whether it's a firearm, from Arsenal's scripts."""
    out = {}
    for f in glob.glob(os.path.join(glob.escape(arsenal), 'media', 'scripts', '**', '*.txt'), recursive=True):
        text = open(f, encoding='latin-1').read()
        for m in re.finditer(r'\bitem\s+(\w+)\s*\{(.*?)\n\s*\}', text, re.S):
            body = m.group(2)
            d = {}
            for k in ('AmmoType', 'MagazineType', 'SubCategory', 'Type'):
                mm = re.search(r'\b' + k + r'\s*=\s*([\w.]+)', body)
                if mm:
                    d[k] = mm.group(1).split('.')[-1]
            out[m.group(1)] = d
    return out


def sandbox_defaults(arsenal):
    path = os.path.join(arsenal, 'media', 'sandbox-options.txt')
    out = {}
    if not os.path.exists(path):
        return None
    text = open(path, encoding='latin-1').read()
    for m in re.finditer(r'option\s+A26\.(\w+)\s*\{(.*?)\}', text, re.S):
        d = re.search(r'default\s*=\s*([^,\s]+)', m.group(2))
        if d:
            v = d.group(1)
            out[m.group(1)] = True if v == 'true' else False if v == 'false' else float(v) if '.' in v else int(v)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--arsenal', required=True)
    ap.add_argument('--guns', required=True)
    ap.add_argument('--set', action='append', default=[], help='A26 option=value, over the defaults')
    ap.add_argument('--out')
    a = ap.parse_args()
    sandbox = sandbox_defaults(a.arsenal)
    if sandbox is None:
        sys.exit('No media/sandbox-options.txt in ' + a.arsenal)
    sandbox['EnumMILRegion'] = 1    # the review: the US army
    for kv in a.set:
        k, v = kv.split('=', 1)
        sandbox[k] = float(v) if '.' in v else int(v)
    lua = open(os.path.join(a.arsenal, 'media', 'lua', 'server', 'Items', 'Brita_06Multi_Distro.lua'), encoding='latin-1').read().split('\n')
    env = setup(lua, sandbox)
    lists = tables(lua)
    chosen = proc_chances(lua)
    items = scripts(a.arsenal)

    ours = {}
    for f in glob.glob(os.path.join(glob.escape(a.guns), '*.json')):
        try:
            g = json.load(open(f, encoding='utf-8'))
        except Exception:
            continue
        if g.get('id') and g.get('anchor'):
            if not g.get('ammo'):
                g['ammo'] = ANCHOR_AMMO.get(g['anchor'])
            ours[g['id']] = g
    ours['HEAVY'] = {'id': 'HEAVY'}
    names = {gid: {gid, ALIAS.get(gid, gid)} for gid in ours}
    # how many of ours stand on each Arsenal gun: they share its chance
    sharing = {}
    for gid in ours:
        sharing[ALIAS.get(gid, gid)] = sharing.get(ALIAS.get(gid, gid), 0) + 1
    share = {gid: 1.0 / sharing[ALIAS.get(gid, gid)] for gid in ours}

    def weight(expr):
        v = lua_expr(expr, env, sandbox)
        return max(0.0, float(v)) if isinstance(v, (int, float)) else 0.0

    def firearm(name):
        d = items.get(name, {})
        return d.get('SubCategory') == 'Firearm' or d.get('Type') == 'Weapon' and 'AmmoType' in d

    def extras_from(lst, q, rolls):
        out = []
        for gid, g in ours.items():
            if gid == 'HEAVY':
                continue
            d = {}
            for n in names[gid]:
                d.update({k: v for k, v in items.get(n, {}).items() if v})
            mag, ammo = d.get('MagazineType'), d.get('AmmoType')
            for it, expr in lists.get(lst, {}).get('items', []):
                p = q * rolls * weight(expr) / 100
                if p <= 0:
                    continue
                if mag and it == mag:
                    out.append({'mag': gid, 'p': round(min(1, p * share[gid]), 5)})
                elif ammo and g.get('ammo') and re.fullmatch(re.escape(ammo) + r'(Box|Carton|Can)?', it):
                    box = 'Box' in it or 'Carton' in it or 'Can' in it
                    lo, hi = ((2, 4) if box else (1, 2)) if 'rocket' in g['ammo'] else ((20, 50) if box else (5, 15))
                    out.append({'item': g['ammo'], 'p': round(min(1, p * share[gid]), 5), 'min': lo, 'max': hi, 'for': gid})
        return out

    places = {}
    for place, (gls, al) in PLACES.items():
        count, mine, ex = 0.0, {}, []
        for gl in gls:
            t = lists.get(gl)
            if not t:
                print('no list', gl)
                continue
            rolls = lua_expr(t['rolls'], env, sandbox) or 1
            # a list no room of Arsenal's names stands in for Zomboid's own of that name, which its gun containers roll
            q = chosen.get(gl, 1.0)
            guns = [(it, weight(e)) for it, e in t['items'] if firearm(it)]
            count += q * rolls * sum(w for _, w in guns) / 100
            for gid in ours:
                p = q * rolls * sum(w for it, w in guns if it in names[gid]) / 100 * share[gid]
                if p > 0:
                    mine[gid] = round(mine.get(gid, 0) + p, 6)
            ex += extras_from(gl, q, rolls)
            print(f'{place:9} {gl:19} q={q:.2f} rolls={rolls}')
        if al and al in lists:
            ex += extras_from(al, chosen.get(al, 1.0), lua_expr(lists[al]['rolls'], env, sandbox) or 1)
        # only the magazines and rounds of guns stocked there: no DShK drums in a US armoury
        ex = [x for x in ex if x.get('mag', x.get('for')) in mine]
        for x in ex:
            x.pop('for', None)
        places[place] = {'count': round(count, 4), 'guns': mine, 'extras': ex}
        print(f'{place:9} count={count:.3f} ours={len(mine)} extras={len(ex)}')

    # gun cases: one gun, by Arsenal's points, the case lists of a kind counted alike
    for place, prefix in CASES.items():
        mine = {}
        cases = [n for n in lists if n.startswith(prefix)]
        for n in cases:
            guns = [(it, weight(e)) for it, e in lists[n]['items'] if firearm(it)]
            total = sum(w for _, w in guns) or 1
            for gid in ours:
                if gid == 'HEAVY':
                    continue      # the page: the Heavy Sniper isn't put away in cases
                p = sum(w for it, w in guns if it in names[gid]) / total / len(cases) * share[gid]
                if p > 0:
                    mine[gid] = mine.get(gid, 0) + p
        places[place] = {'count': 1, 'guns': {k: round(v, 6) for k, v in mine.items()}, 'extras': []}
        print(f'{place:11} {len(cases)} case lists, ours={len(mine)}')

    # a civilian zombie's purse or bum bag: its chance on a body, then one gun by points
    bags = {}
    for raw in lua:
        m = re.search(r'table\.insert\(SuburbsDistributions\["all"\]\["inventory(male|female)"\]\.items,\s*"Base\.(CCW_\w+)"', raw)
        if m:
            pend = m.group(2)
            continue
        m = re.search(r'table\.insert\(SuburbsDistributions\["all"\]\["inventory(male|female)"\]\.items,\s*([^"].*?)\s*\)\s*;', raw)
        if m and 'pend' in dir() and pend:
            bags[pend] = weight(m.group(2)) / 100 / 2     # half the dead are each sex
            pend = None
    mine, count = {}, 0.0
    for bag, chance in bags.items():
        guns = [(it, weight(e)) for it, e in lists.get(bag, {}).get('items', []) if firearm(it)]
        total = sum(w for _, w in guns) or 1
        count += chance
        for gid in ours:
            p = chance * sum(w for it, w in guns if it in names[gid]) / total * share[gid]
            if p > 0:
                mine[gid] = mine.get(gid, 0) + p
    places['ccw'] = {'count': round(count, 5), 'guns': {k: round(v, 7) for k, v in mine.items()}, 'extras': []}
    print(f'ccw        {len(bags)} bags, count={count:.4f}, ours={len(mine)}')

    out = a.out or os.path.join(a.guns, 'loot', 'places.json')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    json.dump({'source': 'Arsenal(26) GunFighter: Brita_06Multi_Distro.lua and sandbox-options.txt defaults, US army region',
               'places': places, 'rarity': {k: v for k, v in RAR.items() if k in ours}}, open(out, 'w'), indent=1)
    print('wrote', out)


main()
