"""Builds the zombie packs (pz_zombie_f.zsk, pz_zombie_m.zsk) the game draws zombies with, from Project Zomboid's own
files on this PC. Nothing from Zomboid is in the repo or the jar: run this on (a copy of) the files and put the two .zsk
files in config/zomboidcraft/npc/ next to the survivor packs.

    python3 -I tools/pz/zombie_pack.py PZ_MEDIA WORKSHOP_108600 OUT_DIR [f|m ...]

What's in a pack, all Zomboid's own:
- the body the survivors use (Tomb's Player Body mesh, which shares Zomboid's UV layout, so Zomboid's skins fit it);
- Zomboid's zombie skins, {F,M}_ZedBody01-04, each fresh and at its three rot levels, and the underwear layer the
  survivor packs lay over every skin (no zombie is ever drawn nude, whatever Zomboid's own outfit says);
- Zomboid's hair and beards;
- every clothingItem the outfits below can put on a zombie (survivor_pack's clothes(), so they're painted the same way);
- Zomboid's outfits (media/clothing/clothing.xml: each item's chance and the items it can be instead) and the outfit
  pools our zombie outfits are drawn from (zombie_outfits.json names Zomboid's zones and outfits; the zone chances are
  read from media/lua/shared/NPCs/ZombiesZoneDefinition.lua here);
- Zomboid's zombie moves (media/anims_X/Zombie).

The catalog (texture key "catalog") holds: sex, height, skins, hair, beard, clothes (as the survivor catalog),
outfits {name: {items: [{p, any: [clothingItem]}], top, pants}} and pools {our outfit: [[Zomboid outfit, weight]]}.
"""
import glob, json, os, re, sys, tempfile
import xml.etree.ElementTree as ET
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import survivor_pack as sp  # noqa: E402

OUTFITS_MAP = os.path.join(os.path.dirname(os.path.dirname(HERE)), 'src/main/resources/assets/zomboidcraft/survivor/zombie_outfits.json')
# outfits a zombie is never drawn in, whatever the zone: nudity (Josiah: no nudity)
NEVER = {'Naked'}

# game name: Zomboid clip (anims_X/Zombie). The game picks among the numbered walks and sprints per zombie, as Zomboid does.
CLIPS = {
    'idle': 'Zombie_Idle', 'walk1': 'Zombie_Walk', 'walk2': 'Zombie_Walk2', 'walk3': 'Zombie_Walk3', 'walk4': 'Zombie_Walk4',
    'walk5': 'Zombie_Walk5', 'walk_fast': 'Zombie_Walk5_Faster', 'stumble': 'Zombie_WalkStumble', 'walk_trees': 'Zombie_WalkTrees',
    'sprint1': 'Zombie_Sprint', 'sprint2': 'Zombie_Sprint02', 'sprint3': 'Zombie_Sprint03', 'sprint5': 'Zombie_Sprint05',
    'sprint6': 'Zombie_Sprint06',
    'turn_alert': 'Zombie_Turn00_Alerted', 'turn_l90': 'Zombie_TurnL90_Alerted', 'turn_r90': 'Zombie_TurnR90_Alerted',
    'turn_l180': 'Zombie_TurnL180_Alerted', 'turn_r180': 'Zombie_TurnR180_Alerted',
    'lunge': 'Zombie_Lunge', 'lunge_idle': 'Zombie_Idle_Lunge', 'lunge_high': 'Zombie_Idle_LungeHigh', 'lunge_low': 'Zombie_Idle_LungeLow',
    'bite': 'Zombie_Bite', 'bite_start': 'Zombie_Bite_Start', 'bite_success': 'Zombie_Bite_Success', 'bite_fail': 'Zombie_Bite_Fail',
    'bite_defended': 'Zombie_Bite_Defended', 'bite_low': 'Zombie_BiteLow_Success',
    'eat': 'Zombie_IdleEating', 'eat_in': 'Zombie_IdleToEating', 'eat_out': 'Zombie_EatingToIdle', 'eat_knees': 'Zombie_IdleEating_OnKnees',
    'door': 'Zombie_Door', 'door_bang': 'Zombie_DoorBang', 'door_claw': 'Zombie_DoorClaw', 'door_shoulder': 'Zombie_DoorShoulder',
    'fence_claw': 'Zombie_FenceClaw', 'window_lunge': 'Zombie_ClimbWindowLunge',
    'climb_window': 'Zombie_ClimbWindow', 'climb_window_in': 'Zombie_ClimbWindowStart', 'climb_window_out': 'Zombie_ClimbWindowEnd',
    'climb_fence': 'Zombie_ClimbFence_PullDown', 'climb_wall': 'Zombie_ClimbLowWall',
    'crawl': 'Zombie_Crawl', 'crawl2': 'Zombie_Crawl02', 'crawl_idle': 'Zombie_CrawlIdle', 'crawl_lunge': 'Zombie_CrawlLunge',
    'crawl2_lunge': 'Zombie_Crawl02Lunge', 'crawl_turn_l': 'Zombie_CrawlTurnL', 'crawl_turn_r': 'Zombie_CrawlTurnR',
    'crawl_bite': 'Zombie_CrawlLunge_Success', 'crawl_hit': 'Zombie_Crawler_HitReact',
    'crawl_under': 'Zombie_CrawlUnder', 'crawl_under_in': 'Zombie_CrawlUnder_In', 'crawl_under_out': 'Zombie_CrawlUnder_Out',
    'floor_back': 'Zombie_Idle_FloorOnBack', 'floor_front': 'Zombie_Idle_FloorOnFront',
    'floor_hit_back': 'Zombie_HitReact_FloorOnBack', 'floor_hit_front': 'Zombie_HitReact_FloorOnFront',
    'get_up': 'Zombie_GetUp', 'get_up_front': 'Zombie_GetUpFront', 'dead_to_crawl': 'Zombie_DeadToCrawl',
    'front_to_crawl': 'Zombie_FloorFrontToCrawl', 'fall': 'Zombie_FallIdle', 'land': 'Zombie_FallLanding_Small',
    'land_heavy': 'Zombie_FallLanding_Heavy', 'land_dead': 'Zombie_FallLanding_Death', 'stair_fall': 'Zombie_StairFall',
    'die': 'Zombie_Death', 'die_behind': 'Zombie_DeathBehind', 'dead': 'Zombie_EndDeath',
    'die_head_l': 'Zombie_HitDeath_HeadLeft', 'die_head_r': 'Zombie_HitDeath_HeadRight', 'die_head_top': 'Zombie_HitDeath_HeadTop',
    'die_head_l_b': 'Zombie_HitDeath_HeadLBehind', 'die_head_r_b': 'Zombie_HitDeath_HeadRBehind',
    'die_head_top_b': 'Zombie_HitDeath_HeadTopBehind', 'die_knife': 'Zombie_HitDeath_Knife01', 'die_knife_b': 'Zombie_HitDeath_KnifeBehind',
    'die_spear': 'Zombie_HitDeath_Spear01', 'die_spear_b': 'Zombie_HitDeath_Spear01Behind', 'die_uppercut': 'Zombie_HitDeath_Uppercut',
    'die_uppercut_b': 'Zombie_HitDeath_UppercutBehind', 'die_shot_head_fwd': 'Zombie_ShotHeadFallFwd',
    'die_shot_head_bwd': 'Zombie_ShotHeadFallBwd', 'die_shot_head_b': 'Zombie_ShotHeadBehind_FallFwd',
    'hit_head_l': 'Zombie_HitReact_HeadLeft', 'hit_head_r': 'Zombie_HitReact_HeadRight', 'hit_head_top': 'Zombie_HitReact_HeadTop',
    'hit_head_l_b': 'Zombie_HitReact_HeadLBehind', 'hit_head_r_b': 'Zombie_HitReact_HeadRBehind',
    'hit_head_top_b': 'Zombie_HitReact_HeadTopBehind', 'hit_uppercut': 'Zombie_HitReact_Uppercut',
    'hit_uppercut_b': 'Zombie_HitReact_UppercutBehind',
    'shot_belly': 'Zombie_ShotBelly', 'shot_belly_step': 'Zombie_ShotBellyStep', 'shot_chest_l': 'Zombie_ShotChestL',
    'shot_chest_r': 'Zombie_ShotChestR', 'shot_shoulder_l': 'Zombie_ShotShoulder_L', 'shot_shoulder_r': 'Zombie_ShotShoulder_R',
    'shot_leg_l': 'Zombie_ShotLeg_L', 'shot_leg_r': 'Zombie_ShotLeg_R',
    'shot_fall_chest_l': 'Zombie_ShotChestStepFall_L', 'shot_fall_chest_r': 'Zombie_ShotChestStepFall_R',
    'shot_fall_leg_l': 'Zombie_ShotLegFall_L', 'shot_fall_leg_r': 'Zombie_ShotLegFall_R',
    'shot_fall_shoulder_l': 'Zombie_ShotShoulderFall_L', 'shot_fall_shoulder_r': 'Zombie_ShotShoulderFall_R',
    'stagger_l': 'Zombie_ShotShoulderStagger_L', 'stagger_r': 'Zombie_ShotShoulderStagger_R',
    'shove': 'Zombie_ShoveStagger_2m', 'shove_fall': 'Zombie_ShoveStaggerFall_2m', 'shove_l': 'Zombie_ShoveStaggerLeft_Small',
    'shove_r': 'Zombie_ShoveStaggerRight_Small', 'stagger_back': 'Zombie_StaggerBack', 'stagger_back_fall': 'Zombie_StaggerBackFall',
    'pushed': 'Zombie_PushedFwd_FromBehind', 'pushed_over': 'Zombie_PushedFwdOver_FromBehind',
    'wall': 'Zombie_IdleAgainstWall', 'wall_dead': 'Zombie_DeadAgainstWall', 'wall_up': 'Zombie_IdleAgainstWall_GetUp',
    'knees_in': 'Zombie_IdleTo_OnKnees', 'knees_out': 'Zombie_OnKnees_ToIdle',
}


def zones(path):
    """Zomboid's ZombiesZoneDefinition: zone -> its entries (name, chance, gender, room, mandatory...)."""
    t = re.sub(r'--[^\n]*', '', open(path, encoding='utf-8-sig').read())
    out = {}
    kv = lambda s: dict(re.findall(r'(\w+)\s*=\s*"?([^",\n}]*)"?', s))
    for m in re.finditer(r'^ZombiesZoneDefinition\.(\w+)\s*=\s*\{(.*?)^\}', t, re.S | re.M):
        out[m.group(1)] = [d for d in (kv(e.group(1)) for e in re.finditer(r'\{([^{}]*)\}', m.group(2))) if 'name' in d]
    for m in re.finditer(r'table\.insert\(ZombiesZoneDefinition\.(\w+),\s*\{([^{}]*)\}\)', t):
        out.setdefault(m.group(1), []).append(kv(m.group(2)))
    return out


def outfits(media, files):
    """clothing.xml: per sex, outfit -> its items (chance, and the clothingItems it may be), m_Top, m_Pants."""
    guid = {}
    for f in ET.parse(files.get('fileGuidTable.xml')).getroot():
        p, g = f.findtext('path'), f.findtext('guid')
        if p and g:
            guid[g] = os.path.basename(p.replace('\\', '/'))[:-4]
    out = {'f': {}, 'm': {}}
    for o in ET.parse(files.get('clothing/clothing.xml')).getroot():
        sex = 'f' if o.tag == 'm_FemaleOutfits' else 'm'
        items = []
        for it in o.findall('m_items'):
            alts = [it.findtext('itemGUID')] + [s.findtext('itemGUID') for s in it.findall('subItems')]
            names = [guid[a] for a in alts if a in guid]
            if names:
                items.append({'p': float(it.findtext('probability') or 1), 'any': names})
        out[sex][o.findtext('m_Name')] = {'items': items, 'top': o.findtext('m_Top') == 'true',
                                          'pants': o.findtext('m_Pants') == 'true'}
    return out


def pools(zone_defs, sex, outs):
    """Our outfits as weighted Zomboid outfits for this sex (Zomboid's own chances; a zone's leftover chance goes to the
    Default zone where the map says fill, as Zomboid does). Room-only entries and NEVER are left out."""
    F = 'female' if sex == 'f' else 'male'
    def entries(zone):
        out = []
        for e in zone_defs.get(zone, []):
            if e.get('room') or e['name'] in NEVER or e.get('gender', F) != F or e['name'] not in outs:
                continue
            c = float(e.get('chance') or 0)
            if c > 0:
                out.append([e['name'], c])
        return out
    res = {}
    for ours, v in json.load(open(OUTFITS_MAP, encoding='utf-8')).items():
        if ours.startswith('_'):
            continue
        got = []
        if 'zone' in v:
            got += entries(v['zone'])
        if v.get('fill') and v.get('zone') != 'Default':
            left = max(0.0, 100 - sum(c for _, c in got))
            d = entries('Default'); tot = sum(c for _, c in d) or 1
            got += [[n, c * left / tot] for n, c in d]
        for n, w in v.get('outfits', {}).items():
            if n in outs and n not in NEVER:
                got.append([n, float(w)])
        if not got:  # nothing for this sex (a men-only zone for a woman, say): the Default zone
            got = entries('Default')
        res[ours] = [[n, round(c, 4)] for n, c in got]
    return res


def x_meshes(path):
    """survivor_pack's .x reader, also taking meshes Zomboid saved without normals (flat ones are worked out) or uvs."""
    root = sp.xfile.parse(path)
    res = []
    for me in sp.xfile.walk(root, 'Mesh'):
        nums = me.nums
        nv = int(nums[0]); pos = np.array(nums[1:1 + 3 * nv]).reshape(nv, 3); p = 1 + 3 * nv
        nf = int(nums[p]); p += 1; faces = []
        for _ in range(nf):
            c = int(nums[p]); faces.append([int(x) for x in nums[p + 1:p + 1 + c]]); p += 1 + c
        nn_ = me.child('MeshNormals')
        if nn_:
            nrm = nn_[0]; nn = int(nrm.nums[0])
            nvec = np.array(nrm.nums[1:1 + 3 * nn]).reshape(nn, 3); q = 1 + 3 * nn
            nfn = int(nrm.nums[q]); q += 1; nfaces = []
            for _ in range(nfn):
                c = int(nrm.nums[q]); nfaces.append([int(x) for x in nrm.nums[q + 1:q + 1 + c]]); q += 1 + c
        else:
            # no normals saved: each vertex gets the average of the faces it's on
            acc = np.zeros((nv, 3))
            for f in faces:
                for t in range(1, len(f) - 1):
                    a, b, c = pos[f[0]], pos[f[t]], pos[f[t + 1]]
                    n = np.cross(b - a, c - a)
                    for v in (f[0], f[t], f[t + 1]):
                        acc[v] += n
            nvec = acc / (np.linalg.norm(acc, axis=1, keepdims=True) + 1e-9)
            nfaces = faces
        tc_ = me.child('MeshTextureCoords')
        uv = np.array(tc_[0].nums[1:1 + 2 * int(tc_[0].nums[0])]).reshape(-1, 2) if tc_ else np.zeros((nv, 2))
        W = [dict() for _ in range(nv)]; off = {}
        for sw in me.child('SkinWeights'):
            b = sw.strs[0]; k = int(sw.nums[0])
            idx = [int(x) for x in sw.nums[1:1 + k]]; wts = sw.nums[1 + k:1 + 2 * k]
            off[b] = np.array(sw.nums[1 + 2 * k:1 + 2 * k + 16]).reshape(4, 4).T
            for a, w in zip(idx, wts):
                W[a][b] = W[a].get(b, 0) + w
        corners = []
        for f, fn in zip(faces, nfaces):
            for t in range(1, len(f) - 1):
                for c in (0, t, t + 1):
                    corners.append((f[c], fn[c]))
        if not corners:
            continue
        P = np.array([pos[v] for v, _ in corners]); N = np.array([nvec[n] for _, n in corners])
        T = np.array([uv[v] if v < len(uv) else (0, 0) for v, _ in corners]); WW = [W[v] for v, _ in corners]
        res.append(sp.weld(me.name, P, N, T, WW, off))
    return root, res


def loadable(files, F, name):
    """Whether a clothingItem's model (if it has one) reads: Zomboid ships a few broken or empty ones, left out."""
    px = files.get(f'clothing/clothingItems/{name}.xml')
    if not px:
        return False
    it = sp.item_xml(px)
    path = sp.x_model(files, it['female'] if F else it['male']) or sp.x_model(files, it['male'] if F else it['female'])
    if not path:
        return True
    try:
        return bool(x_meshes(path)[1])
    except Exception as e:  # noqa: BLE001
        print(f'  {name}: model {os.path.basename(path)} unreadable ({e}), left out')
        return False


def build(sex, media, ws, out_dir):
    F = sex == 'f'
    G = 'Female' if F else 'Male'
    roots = [r for r in os.environ.get('PZ_MEDIA_EXTRA', '').split(os.pathsep) if r]
    files = sp.Files([media, *roots])
    print(f'{G} zombies:')
    skel = sp.Skeleton(files.get(f'models_X/Skinned/{G}Body.x'))
    male = skel if not F else sp.Skeleton(files.get('models_X/Skinned/MaleBody.x'))
    xoff = {n: skel.off[i] for i, n in enumerate(skel.names)}

    groups = []
    mods = lambda mid, sub: f'{ws}/{mid}/mods/{sub}'
    fbx = sorted(glob.glob(mods(sp.TOMB, "Tomb's Player Body") + '/42*/media/models_X/Skinned/' + G + 'Body.fbx')) \
        or glob.glob(mods(sp.TOMB, "Tomb's Player Body") + '/media/models_X/Skinned/' + G + 'Body.fbx')
    body, fit = sp.fbx_mesh(fbx[-1], xoff=skel.body['off'])
    tomb_tex = mods(sp.TOMB, "Tomb's Player Body - Textures") + '/media/textures/Body'
    alpha = np.asarray(Image.open(f'{tomb_tex}/{G}Body01.png').convert('RGBA'))[..., 3]
    h, w = alpha.shape
    corners = body['uv'][body['tris']]
    pts = np.concatenate([corners, corners.mean(1, keepdims=True)], 1)
    seen = alpha[(pts[..., 1].clip(0, 0.9999) * h).astype(int), (pts[..., 0].clip(0, 0.9999) * w).astype(int)] > 64
    body['tris'] = body['tris'][seen.sum(1) >= 2]
    groups.append(('body', 'body', 'skin', 0, sp.to_game(body, skel, bone_off=xoff)))

    cat = {'sex': sex, 'height': 0, 'zombie': True, 'packs': [], 'faces': {}, 'hair': [], 'beard': [], 'stubble': [],
           'bodyhair': [], 'makeup': {'eyes': [], 'lips': [], 'face': []}, 'clips': [], 'skins': []}
    tex = {}

    # hair and beards: Zomboid's own (hairStyles.xml), every one Zomboid lets a character have
    hair_tex = {}
    hair_files = {os.path.basename(p).lower()[:-2]: p for p in glob.glob(os.path.dirname(files.get(f'models_X/Skinned/{G}Body.x')) + '/Hair/*.x')}
    for r in roots:
        hair_files.update({os.path.basename(p).lower()[:-2]: p for p in glob.glob(f'{r}/models_X/Skinned/Hair/*.x')})
    for e in ET.parse(files.get('hairStyles/hairStyles.xml')).getroot():
        if e.tag != G.lower() or not e.findtext('model'):
            continue
        name = e.findtext('name')
        model = e.findtext('model').replace('\\', '/').rsplit('/', 1)[1]
        low = model.lower()
        tail = low.split('hair_', 1)[1] if 'hair_' in low else low
        path = hair_files.get(low) or hair_files.get(('kate_hair_' if F else 'bob_hair_') + tail)
        if not path:
            continue
        _, ms = sp.x_meshes(path)
        hid = 'hair:' + name
        texname = e.findtext('texture')
        hair_tex[texname] = True
        groups.append((os.path.basename(path), hid, 'hairtex/' + texname, 3, sp.to_game(ms[0], skel)))
        cat['hair'].append({'id': name, 'name': sp.pretty(name), 'piece': hid, 'goth': False,
                            'level': int(e.findtext('level') or 1), 'trim': [], 'grow': False,
                            'choose': e.find('noChoose') is None,
                            'alt': {a.get('category'): a.get('style') for a in e.findall('alternate')}})
    if not F:
        beard_dir = os.path.dirname(files.get(f'models_X/Skinned/{G}Body.x')) + '/Beards'
        beard_files = {os.path.basename(p).lower()[:-2]: p for p in glob.glob(beard_dir + '/*.x')}
        for e in ET.parse(files.get('hairStyles/beardStyles.xml')).getroot():
            model = (e.findtext('model') or '').replace('\\', '/').rsplit('/', 1)[-1]
            path = beard_files.get(model.lower())
            if not path:
                continue
            _, ms = sp.x_meshes(path)
            name = e.findtext('name'); hid = 'beard:' + name
            hair_tex[e.findtext('texture')] = True
            groups.append((os.path.basename(path), hid, 'hairtex/' + e.findtext('texture'), 3, sp.to_game(ms[0], skel)))
            cat['beard'].append({'id': name, 'name': sp.pretty(name), 'piece': hid, 'level': int(e.findtext('level') or 1),
                                 'trim': [], 'grow': False})
    for t in hair_tex:
        p = files.get(f'textures/{t}.png')
        if p:
            tex['hairtex/' + t] = sp.png(Image.open(p).convert('RGBA'))

    # Zomboid's outfits and our pools of them
    outs = outfits(media, files)[sex]
    zone_defs = zones(files.get('lua/shared/NPCs/ZombiesZoneDefinition.lua'))
    cat['pools'] = pools(zone_defs, sex, outs)
    used = {n for pool in cat['pools'].values() for n, _ in pool}
    cat['outfits'] = {n: outs[n] for n in sorted(used)}
    items = sorted({i for o in cat['outfits'].values() for it in o['items'] for i in it['any']})
    print(f'  {len(used)} Zomboid outfits in {len(cat["pools"])} pools, {len(items)} clothingItems')

    # their clothes, painted as the survivors' are (survivor_pack reads its list from CLOTHES_MAP)
    sp.x_meshes = x_meshes
    items = [i for i in items if loadable(files, F, i)]
    with tempfile.NamedTemporaryFile('w', suffix='.json', delete=False) as tf:
        json.dump({'zombie': items}, tf)
    keep, sp.CLOTHES_MAP = sp.CLOTHES_MAP, tf.name
    try:
        sp.clothes(F, media, skel, groups, tex, cat)
    finally:
        sp.CLOTHES_MAP = keep
        os.unlink(tf.name)

    # skins: the four zombie bodies, fresh and at each rot level, padded; the underwear layer over all of them
    tex['uw'] = sp.png(sp.black_set(f'{tomb_tex}/{G}Body01.png', media, G))
    for n in range(1, 5):
        for lv, suf in enumerate(('', '_level1', '_level2', '_level3')):
            p = files.get(f'textures/Body/{G[0]}_ZedBody{n:02d}{suf}.png')
            if not p:
                continue
            key = f'zed/{n}/{lv}'
            tex[key] = sp.png(sp.pad(sp.limit(Image.open(p).convert('RGBA'))))
            cat['skins'].append(key)
    print(f'  {len(cat["skins"])} zombie skins')

    clips = {}
    hip = skel.hip() / male.hip()
    for name, src in CLIPS.items():
        p = files.get(f'anims_X/Zombie/{src}.x')
        if not p:
            print(f'  missing clip {src}'); continue
        clips[name] = sp.clip(p, skel, hip)
        cat['clips'].append(name)
    top = max(g[4]['pos'][:, 1].max() for g in groups if g[1] == 'body')
    cat['height'] = round(float(top), 3)
    tex['catalog'] = json.dumps(cat, indent=1).encode('utf-8')
    sp.write(f'pz_zombie_{sex}', groups, tex, skel, clips, out_dir)


if __name__ == '__main__':
    media, ws, out_dir = sys.argv[1:4]
    for sx in (sys.argv[4:] or ['f', 'm']):
        build(sx, media.rstrip('/'), ws.rstrip('/'), out_dir)
