#!/usr/bin/env python3
"""Assemble a handover pack for one project. Usage: build_pack.py en|zc OUTDIR"""
import os, re, sys, shutil, csv, glob

S = os.path.dirname(os.path.abspath(__file__))
MEM = '/tmp/claude/memory/team/silo'
LIB = os.path.join(S, 'lib')            # local copy of Library text files (same relative paths)
PROJ, OUT = sys.argv[1], sys.argv[2]
NAME = {'en': 'Endless Night: Minecraft', 'zc': 'ZomboidCraft'}[PROJ]
OTHER = {'en': 'ZomboidCraft', 'zc': 'Endless Night: Minecraft'}[PROJ]
os.makedirs(OUT, exist_ok=True)

# ---------------- memory classification ----------------
EN_CORE = re.compile(r'^(endless-night|reach-|zomboid-reach|zomboid-halo|zomboid-cortana|zomboid-character-screens|'
                     r'zomboid-main-menu|zomboid-armory|zomboid-endless-night|zomboid-purge|zomboid-refs-endless|'
                     r'tarkov-covenant|full-detail)')
RULE = re.compile(r'(rule|preferences|declined|josiah-|approve-mockups|keep-working|overnight-mandate|'
                  r'no-self-resolve|switch|voice-accents|-direction$|playtest-build)')
SHARED = re.compile(r'(tarkov-|pz-zombie|zbody|hitbox|corpses|dismember|zombie|population|movement-v2|window-climb|'
                    r'chest-hole|characters-direction|enemies-direction|hordes|injuries|camera|survivor-|sound|bullet|'
                    r'warthog|hud|ui-|interact|inventory-ui|kit-screen|guns-tech|reload|irons|iron-sights|scope|weather|'
                    r'nature|world-events|commands|world-systems|playtest|build-notes|photon|render-distance|vram|profiler|'
                    r'performance|crowd|server-capacity|keep-working|pc-storage|no-vanilla|no-wikimedia|self-resolve|'
                    r'resolve-rule|approve-mockups|video-preview|design-palette|photo-reference|consistent-detail|josiah-|'
                    r'eyes-clear|mesh-|match-zomboid|blender|arms-posing|mannequin|skin-style|refs-collection|asset-intake|'
                    r'texture-library|npc-models|npc-voice|woman-model|zomboid-pz-|shion|voice-accents|minecraft-zomboid-mod|'
                    r'mod-status|grip|gear-attachments|opening-guns|rail-lights|nightvision|spider7|throwables|loot-rarity|'
                    r'old-guns|gun-art-style|factory-irons|wave1|wave2|brita)')

def classify(stem):
    if stem == 'MEMORY':
        return 'INDEX'
    if PROJ == 'en':
        if EN_CORE.search(stem): return 'CORE'
        if SHARED.search(stem) or RULE.search(stem): return 'SHARED'
        return 'BACKGROUND (county world: removed from Endless Night, do not rebuild unless Josiah asks)'
    else:
        if EN_CORE.search(stem):
            if RULE.search(stem): return 'SHARED'
            return 'BACKGROUND (moved to the Endless Night: Minecraft project)'
        if SHARED.search(stem) or RULE.search(stem): return 'SHARED'
        return 'CORE'

def desc(path):
    with open(path, encoding='utf-8', errors='replace') as f:
        for line in f:
            if line.startswith('description:'):
                return line[12:].strip()
    return ''

memdir = os.path.join(OUT, 'memory'); os.makedirs(memdir, exist_ok=True)
mem_rows = []
for p in sorted(glob.glob(os.path.join(MEM, '*.md'))):
    stem = os.path.basename(p)[:-3]
    shutil.copy2(p, os.path.join(memdir, os.path.basename(p)))
    mem_rows.append((stem, classify(stem), desc(p)))

with open(os.path.join(memdir, 'INDEX.md'), 'w') as f:
    f.write(f'# Memory notes for {NAME} (all {len(mem_rows)-1} notes, verbatim)\n\n')
    f.write('Every memory note from the old project is copied here unchanged. Class:\n'
            '- **CORE**: this project\'s own work and decisions.\n'
            '- **SHARED**: rules and systems that apply to this project too (read them).\n'
            f'- **BACKGROUND**: belongs to the other project ({OTHER}) or to removed systems; kept so nothing is lost.\n\n'
            '`MEMORY.md` is the old project\'s own index, verbatim. `[[name]]` links point to `name.md` in this folder.\n\n')
    for cls in ['CORE', 'SHARED']:
        f.write(f'## {cls}\n\n| Note | What it holds |\n|---|---|\n')
        for s, c, d in mem_rows:
            if c == cls: f.write(f'| [{s}]({s}.md) | {d.replace("|", "/")} |\n')
        f.write('\n')
    f.write('## BACKGROUND\n\n| Note | Why background | What it holds |\n|---|---|---|\n')
    for s, c, d in mem_rows:
        if c.startswith('BACKGROUND'):
            f.write(f'| [{s}]({s}.md) | {c[12:-1]} | {d.replace("|", "/")} |\n')

# ---------------- RULES.md (verbatim) ----------------
def body(stem):
    return open(os.path.join(MEM, stem + '.md'), encoding='utf-8', errors='replace').read()

memidx = body('MEMORY')
rules = [s for s, c, d in mem_rows if c != 'INDEX' and RULE.search(s) and not c.startswith('BACKGROUND')]
extra_rules = ['josiah-preferences', 'full-detail-previews-rule', 'zomboid-pc-storage-rule', 'zomboid-keep-working-offline',
               'zomboid-approve-mockups-first', 'zomboid-video-preview-rule', 'zomboid-no-vanilla-rule',
               'zomboid-no-wikimedia-rule', 'zomboid-photo-reference-rule', 'zomboid-consistent-detail-rule',
               'zomboid-design-palette', 'zomboid-commands-thread', 'zomboid-playtest-build',
               'tarkov-suppressors-irons', 'tarkov-grip-holds-tech', 'tarkov-sight-switch-tech', 'tarkov-melee-only',
               'tarkov-ammo-counter-direction', 'zomboid-reload-rules', 'zomboid-arms-posing-history']
if PROJ == 'en':
    extra_rules += ['endless-night-switch', 'endless-night-no-minecraft-effects', 'zomboid-reach-whole-world-quality-rule',
                    'zomboid-reach-realism-direction', 'zomboid-reach-terrain-rule', 'zomboid-reach-layout-question',
                    'zomboid-reach-backdrops-direction', 'zomboid-reach-sky-direction', 'zomboid-reach-places-direction',
                    'zomboid-reach-priority-order', 'zomboid-purge-direction', 'endless-night-new-repo']
else:
    extra_rules += ['josiah-world-art-direction', 'zomboid-building-detail-direction', 'zomboid-landscape-direction',
                    'zomboid-overnight-mandate', 'zomboid-landscape-pause-after-list', 'zomboid-gun-art-style',
                    'zomboid-clothing-style']
order = []
for s in extra_rules + rules:
    if s not in order and os.path.exists(os.path.join(MEM, s + '.md')):
        order.append(s)
with open(os.path.join(OUT, 'RULES.md'), 'w') as f:
    f.write(f'# Josiah\'s rules and directions for {NAME} (verbatim)\n\n'
            'Copied word for word from the old project\'s memory. Nothing here is summarised. Where two notes differ,\n'
            'the later date wins; STRICT means he said it must never be broken.\n\n'
            '## 1. The old project index, verbatim (its "Standing rules" section applies to every project)\n\n')
    f.write('```markdown\n' + memidx + '\n```\n\n## 2. Every rule / direction note, verbatim\n\n')
    for s in order:
        f.write(f'---\n\n### {s}.md\n\n' + body(s).strip() + '\n\n')
    f.write('---\n\nOther directions and tech notes are in `memory/` (see memory/INDEX.md).\n')

# ---------------- Library ----------------
EN_LIB = re.compile(r'^(endless-night/|zomboid-mod/(halo|tarkov|purge|hud-ui|ui|inventory-ui|commands|plan|perf|performance|'
                    r'photon|zombie-behaviour|survivor|characters|pz-extract|posing-tool|skin-template|npc|audio-previews|'
                    r'sound-raw|reference|friend-install|art-brief|blockbench|clothing)/)')
def lib_owner(rel):
    if rel.startswith('endless-night/') or rel.startswith('halo-warthog'):
        return 'EN'
    if rel.startswith('jets.zip') or rel.startswith('weapons.zip') or rel.startswith('minecraft-pack/'):
        return 'ZC'
    if rel.startswith('uploads/'):
        return 'BOTH'
    if EN_LIB.match(rel):
        return 'BOTH'
    return 'ZC'

manifest = []
with open(os.path.join(S, 'library.tsv')) as f:
    for line in f:
        size, date, path = line.rstrip('\n').split('\t', 2)
        rel = path[2:] if path.startswith('./') else path
        manifest.append((rel, int(size), date, lib_owner(rel)))
mine = 'EN' if PROJ == 'en' else 'ZC'
copied = 0
libout = os.path.join(OUT, 'library')
for rel, size, date, owner in manifest:
    if owner not in (mine, 'BOTH'):
        continue
    src = os.path.join(LIB, rel)
    if os.path.isfile(src):
        dst = os.path.join(libout, rel)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(src, dst); copied += 1
# uploaded text files (no extension) -> .txt
upl = os.path.join(S, 'uploads_text')
if os.path.isdir(upl) and PROJ == 'zc':
    d = os.path.join(libout, 'uploads', 'hearth-text'); os.makedirs(d, exist_ok=True)
    for n in os.listdir(upl):
        shutil.copy2(os.path.join(upl, n), os.path.join(d, n + '.txt')); copied += 1
with open(os.path.join(OUT, 'LIBRARY-MANIFEST.tsv'), 'w') as f:
    f.write('path_in_old_library\tbytes\tmodified\towner(EN/ZC/BOTH)\tcopied_into_this_repo\n')
    for rel, size, date, owner in manifest:
        here = os.path.isfile(os.path.join(libout, rel))
        f.write(f'{rel}\t{size}\t{date}\t{owner}\t{"yes: library/"+rel if here else "no"}\n')

# ---------------- PREVIEWS.md ----------------
titles = {}
for line in open(os.path.join(S, 'common', 'titles.tsv')):
    i, t, d = line.rstrip('\n').split('\t'); titles[i] = (t, d)
URL = re.compile(r'claude\.ai/artifact/([A-Za-z0-9]{20,24})')
STAT = re.compile(r'(approved|APPROVED|picked|PICKED|chose|declined|rejected|superseded|SUPERSEDED|pending|awaiting|waiting|'
                  r'OK\b|go-ahead|go ahead|dropped|paused|PAUSED)')
hits = {}
def scan(path, label):
    try:
        txt = open(path, encoding='utf-8', errors='replace').read()
    except Exception:
        return
    for line in txt.splitlines():
        for m in URL.finditer(line):
            hits.setdefault(m.group(1), []).append((label, line.strip()))
for s, c, d in mem_rows:
    scan(os.path.join(MEM, s + '.md'), f'memory/{s}.md')
for root, _, files in os.walk(LIB):
    for n in files:
        if n.endswith(('.md', '.txt', '.json', '.html')):
            p = os.path.join(root, n); scan(p, 'library/' + os.path.relpath(p, LIB))
for i in titles:
    hits.setdefault(i, [])
def proj_of(i):
    labs = ' '.join(l for l, _ in hits[i]) + ' ' + titles.get(i, ('', ''))[0]
    en = bool(re.search(r'(reach|halo|cortana|covenant|endless|spartan|armory|mcc|purge|noble|forge|infected|tarkov|unreal)', labs, re.I))
    return en
with open(os.path.join(OUT, 'PREVIEWS.md'), 'w') as f:
    f.write(f'# Every preview and plan page from the old project ({len(hits)} links)\n\n'
            'Each link is a private claude.ai page Josiah can open. For each one: its title (where known), the notes that\n'
            'mention it, and the words around it that say what it was for and whether he approved it. Pages listed first are\n'
            f'the ones that matter most to {NAME}; the rest are kept so nothing is lost.\n\n'
            '"Status words near it" are approval/pending words found next to the link. When in doubt, the memory note\'s text rules.\n\n')
    f.write(open(os.path.join(S, PROJ, 'PREVIEWS-key.md')).read())
    def write_block(ids, head):
        f.write(f'## {head}\n\n')
        for i in ids:
            t, d = titles.get(i, ('(title not listed: see context)', ''))
            ctx = hits[i]
            st = sorted(set(m.group(1).lower() for _, l in ctx for m in STAT.finditer(l)))
            f.write(f'### [{t}](https://claude.ai/artifact/{i})\n')
            if d: f.write(f'- Last updated: {d}\n')
            if st: f.write(f'- Status words near it: **{", ".join(st)}**\n')
            seen = set()
            for lab, l in ctx[:6]:
                key = (lab, l[:200])
                if key in seen: continue
                seen.add(key)
                l2 = l if len(l) < 700 else l[:700] + ' ...'
                f.write(f'- `{lab}`: {l2}\n')
            if len(ctx) > 6: f.write(f'- ... and {len(ctx)-6} more mentions (grep the id in memory/ and library/)\n')
            f.write('\n')
    ids = sorted(hits, key=lambda i: (titles.get(i, ('', '0'))[1] == '', i))
    mineids = [i for i in ids if proj_of(i) == (PROJ == 'en')]
    otherids = [i for i in ids if i not in mineids]
    write_block(mineids, f'{NAME}')
    write_block(otherids, f'Other pages (mostly {OTHER} history)')

# ---------------- PC-LAYOUT.md ----------------
pcl = os.path.join(S, 'pc-listing.tsv')
shutil.copy2(pcl, os.path.join(OUT, 'PC-LISTING.tsv'))
paths = {}
PATH = re.compile(r'(C:\\[^\s`\'"),;]+)')
for s, c, d in mem_rows:
    for line in body(s).splitlines():
        for m in PATH.finditer(line):
            pth = m.group(1).rstrip('.').rstrip(':')
            paths.setdefault(pth, set()).add(s)
with open(os.path.join(OUT, 'PC-LAYOUT.md'), 'w') as f:
    f.write(open(os.path.join(S, 'common', 'PC-LAYOUT-intro.md')).read())
    f.write('\n\n## Every PC path mentioned in memory (and which notes mention it)\n\n')
    for pth in sorted(paths):
        f.write(f'- `{pth}` — {", ".join(sorted(paths[pth]))}\n')

# ---------------- DECLINED appendix ----------------
DEC = re.compile(r"(declin|reject|dropped|DROPPED|don'?t want|dont want|not needed|SHELVED|shelved|\bNO\b|\bnever\b|NEVER|"
                 r"removed|REMOVED|cut\b|skip|SKIP|hold off|superseded|SUPERSEDED|forget)", re.I)
with open(os.path.join(OUT, 'DECLINED.md'), 'w') as f:
    f.write(open(os.path.join(S, 'common', 'DECLINED.md')).read() + '\n')
    for s, c, d in mem_rows:
        if c == 'INDEX': continue
        lines = [l.strip() for l in body(s).splitlines() if DEC.search(l) and not l.startswith(('name:', 'description:'))]
        if lines:
            f.write(f'\n### {s}.md ({c.split(" ")[0]})\n')
            for l in lines:
                f.write(f'- {l[:600]}{" ..." if len(l) > 600 else ""}\n')

# ---------------- common docs ----------------
for n in ['ASSETS-METHOD.md', 'THREADS.md', 'BUILD-AND-SETUP.md']:
    shutil.copy2(os.path.join(S, 'common', n), os.path.join(OUT, n))
shutil.copy2(os.path.join(S, PROJ, 'START-HERE.md'), os.path.join(OUT, 'START-HERE.md'))
shutil.copy2(os.path.join(S, PROJ, 'FIRST-MESSAGE.md'), os.path.join(OUT, 'FIRST-MESSAGE.md'))
with open(os.path.join(OUT, 'README.md'), 'w') as f:
    f.write(f'# Handover pack: {NAME}\n\nStart with [START-HERE.md](START-HERE.md).\n')

# ---------------- CROSSCHECK.md ----------------
threads = re.findall(r'^\| ([^|]+?) \| ([^|]+?) \|', open(os.path.join(S, 'common', 'THREADS.md')).read(), re.M)
threads = [t for t in threads if t[0] not in ('Thread', '---')]
with open(os.path.join(OUT, 'CROSSCHECK.md'), 'w') as f:
    f.write(f'# Cross-check: is everything covered? ({NAME})\n\n')
    f.write(f'## Memory notes: {len(mem_rows)-1} of {len(mem_rows)-1} copied verbatim into memory/\n\n')
    f.write('| Note | Copied | Class | Also quoted in |\n|---|---|---|---|\n')
    rules_set = set(order)
    for s, c, d in mem_rows:
        if c == 'INDEX': continue
        ok = os.path.exists(os.path.join(memdir, s + '.md'))
        also = []
        if s in rules_set: also.append('RULES.md')
        if any(lab == f'memory/{s}.md' for v in hits.values() for lab, _ in v): also.append('PREVIEWS.md')
        f.write(f'| {s} | {"yes" if ok else "MISSING"} | {c.split(" ")[0]} | {", ".join(also)} |\n')
    f.write(f'\n## Threads: {len(threads)} of 48 listed in THREADS.md\n\n| Thread | Goes to |\n|---|---|\n')
    for t, g in threads:
        f.write(f'| {t} | {g} |\n')
    f.write(f'\n## Library\n\n- {len(manifest)} Library files listed in LIBRARY-MANIFEST.tsv (every file, binaries included).\n'
            f'- {copied} text files copied into library/ for this project.\n'
            '- Binaries (models, textures, videos, zips, previews) are NOT in git: see LIBRARY-MANIFEST.tsv for where they are.\n'
            '  Most were made from files on his PC and also exist under C:\\Dev\\Zomboidcraft-Desktop\\additions (PC-LISTING.tsv).\n'
            f'\n## Preview links\n\n- {len(hits)} distinct preview/plan links in PREVIEWS.md.\n')
print(PROJ, 'memory', len(mem_rows), 'rules', len(order), 'lib copied', copied, 'previews', len(hits), 'threads', len(threads))
