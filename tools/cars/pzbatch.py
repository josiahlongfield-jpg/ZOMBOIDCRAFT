#!/usr/bin/env python3
"""Builds converted Workshop cars with the edits recorded in pzcars.json.

    python3 tools/cars/pzbatch.py SCRIPTS_ROOT ASSETS_ROOT OUT [ID ...]

SCRIPTS_ROOT holds <workshop id>/<mod>/... script folders, ASSETS_ROOT the same layout with models and textures.
A mod's B42 scripts are its highest 42.x folder plus common/; B41 ("build": "B41") uses media/.
"""
import json
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def ver(n):
    try:
        return tuple(int(x) for x in n.split('.'))
    except ValueError:
        return (0,)


def script_dirs(root, mod, build):
    base = os.path.join(root, mod)
    if build == 'B41':
        return [os.path.join(base, 'media', 'scripts')]
    b42 = sorted((d for d in os.listdir(base) if d.startswith('42')), key=ver)
    out = [os.path.join(base, b42[-1], 'media', 'scripts')] if b42 else []
    return out + [os.path.join(base, 'common', 'media', 'scripts')]


def main():
    scripts, assets, out, *only = sys.argv[1:]
    cfg = json.load(open(os.path.join(HERE, 'pzcars.json')))
    for cid, car in cfg['cars'].items():
        if only and cid not in only:
            continue
        c = dict(cfg['defaults'], **car)
        dirs = script_dirs(scripts, c['mod'], c['build'])
        for lib in c['libs']:
            dirs += [d for d in script_dirs(scripts, lib, 'B42') if os.path.isdir(d)]
        cmd = [sys.executable, os.path.join(HERE, 'pzcar.py'), c['vehicle'], out, '--id', cid,
               '--skin', str(c['skin']), '--weight', str(c['weight']), '--tyre', str(c['tyre']),
               '--scripts', *[d for d in dirs if os.path.isdir(d)], '--assets', assets]
        cmd += ['--body', str(c['body'])] if c.get('body') else ['--world', str(c['world'])]
        print(' '.join(cmd[1:4]), flush=True)
        subprocess.run(cmd, check=True)


if __name__ == '__main__':
    main()
