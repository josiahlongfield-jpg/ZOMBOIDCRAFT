#!/usr/bin/env python3
"""Turns the county plan (JSON from plan_dump.js) into the table the world generator reads.

    node tools/county/plan_dump.js grayhaven-plan-draft3.html /tmp/plan.json
    python3 tools/county/plan_export.py /tmp/plan.json

Writes src/main/resources/data/zomboidcraft/county_plan.txt, one record per line:

    place ID KIND TYPE:VARIANT|new X Z FLAGS NAME...   a named place (FLAGS: sub, rail, huge or -)
    river ID WIDTH x,z x,z ...                          a river's smoothed centre line
    area NAME x,z x,z ...                               a closed outline (sea, isle, core, sprawl, ...)
    line NAME x,z x,z ...                               an open line (wall, fence, navy)
    lake ID X Z RX RZ                                   an oval lake
    island ID X Z RX RZ cliff|low                       an oval island in the sea
    urban ID RADIUS                                     a town's built-up radius
    road ID CLASS BLOWN|- x,z x,z ...                   a road's smoothed line (CLASS i, us, ky, loc)
    rail ID x,z x,z ...                                 a closed rail loop, smoothed
    subway ID x,z x,z ...                               the closed subway loop, smoothed
    stops ID place place ...                            the places a line stops at, in order
    ship X Z ANGLE LENGTH                               a ship moored or adrift offshore
    pier AT x,z x,z WIDTH                               a pier, relative to the place AT

Every outline is smoothed the way the plan page draws it, so the world matches the map.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/county_plan.txt')


def smooth(pts, n, closed):
    """Catmull-Rom through the points, n steps a segment: the plan page's smooth()."""
    if len(pts) < 3:
        return list(pts)
    L = len(pts)

    def at(i):
        return pts[(i + L) % L] if closed else pts[max(0, min(L - 1, i))]

    out = []
    for i in range(L if closed else L - 1):
        p0, p1, p2, p3 = at(i - 1), at(i), at(i + 1), at(i + 2)
        for j in range(n):
            t = j / n
            t2, t3 = t * t, t * t * t
            out.append([0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                               + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in (0, 1)])
    if not closed:
        out.append(pts[-1])
    return out


def fmt(pts):
    return ' '.join('%g,%g' % (round(x, 1), round(z, 1)) for x, z in pts)


def main():
    plan = json.load(open(sys.argv[1]))
    where = {p['id']: [p['x'], p['z']] for p in plan['P']}

    def resolve(pts):
        return [where[v] if isinstance(v, str) else v for v in pts]

    lines = ['# Grayhaven County plan, written by tools/county/plan_export.py from the plan page. Do not edit by hand.']
    for p in plan['P']:
        gen = p.get('gen', '')
        if gen.startswith('new'):
            kind = 'new'
        else:
            t, _, v = gen.partition(' · ')
            kind = t.strip() + ':' + v.split()[0].strip()
        flags = [f for f in ('sub', 'rail') if p.get(f)]
        if 'huge' in gen:
            flags.append('huge')
        lines.append('place %s %s %s %g %g %s %s' % (p['id'], p['kind'], kind, p['x'], p['z'], ','.join(flags) or '-', p['name']))
    for r in plan['RIVERS']:
        lines.append('river %s %g %s' % (r['id'], r['w'], fmt(smooth(r['pts'], 10, False))))
    lines.append('area sea ' + fmt(plan['SEA_S']))
    lines.append('area isle ' + fmt(plan['ISLE']))
    lines.append('area core ' + fmt(plan['CORE']))
    lines.append('area sprawl ' + fmt(smooth(plan['SPRAWL'], 6, True)))
    lines.append('area mountains ' + fmt(smooth(plan['MOUNTAINS'], 6, True)))
    lines.append('area marsh ' + fmt(smooth(plan['MARSH'], 6, True)))
    for i, f in enumerate(plan['FOREST']):
        lines.append('area forest%d %s' % (i, fmt(smooth(f, 5, True))))
    for i, f in enumerate(plan['FARM']):
        lines.append('area farm%d %s' % (i, fmt(smooth(f, 5, True))))
    for m in plan['MIL_AREAS']:
        lines.append('area mil-%s %s' % (m['id'], fmt(m['pts'])))
    lines.append('line wall ' + fmt(plan['WALL_S']))
    lines.append('line fence ' + fmt(plan['FENCE_LINE']))
    lines.append('line navy ' + fmt(plan['NAVY_LINE']))
    for l in plan['LAKES']:
        lines.append('lake %s %g %g %g %g' % (l['id'], l['x'], l['z'], l['rx'], l['rz']))
    for i in plan['ISLANDS']:
        lines.append('island %s %g %g %g %g %s' % (i['id'], i['x'], i['z'], i['rx'], i['rz'], 'cliff' if i.get('cliff') else 'low'))
    for t, r in plan['TOWNS_URBAN']:
        lines.append('urban %s %g' % (t, r))
    for r in plan['ROADS']:
        blown = r.get('blownAt')
        lines.append('road %s %s %s %s' % (r['id'], r['cls'], '%g' % blown if blown is not None else '-', fmt(smooth(resolve(r['pts']), 10, False))))
    for kind, table in (('rail', plan['RAIL']), ('subway', plan['SUBWAY'])):
        for r in table:
            s = smooth(resolve(r['pts']), 10, True)
            lines.append('%s %s %s' % (kind, r['id'], fmt(s)))
            lines.append('stops %s %s' % (r['id'], ' '.join(v for v in r['pts'] if isinstance(v, str))))
    for x, z, a, length in plan['SHIPS']:
        lines.append('ship %g %g %g %g' % (x, z, a, length))
    for p in plan['PIERS']:
        (ax, az), (bx, bz), w = p['s']
        lines.append('pier %s %g,%g %g,%g %g' % (p['at'], ax, az, bx, bz, w))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, 'w') as f:
        f.write('\n'.join(lines) + '\n')
    print('wrote', OUT, len(lines), 'lines', os.path.getsize(OUT), 'bytes')


if __name__ == '__main__':
    main()
