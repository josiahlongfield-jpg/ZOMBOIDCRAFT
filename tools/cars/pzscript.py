"""Reads Project Zomboid's script files (media/scripts/**.txt) into nested blocks.

    from pzscript import Scripts
    s = Scripts(['.../42.20/media/scripts', '.../common/media/scripts', ...])   # later dirs never override earlier
    v = s.vehicle('89defender110')        # the vehicle with its templates merged in
    v.kv['mass'], v.blocks('wheel'), v.parts['DoorFrontLeft'].blocks('model')
    s.models['89defenderBase'].kv['mesh']

Every block is `keyword [name] { key = value, ... nested blocks ... }`. Values are kept as strings.
"""
import glob
import os
import re

TOKEN = re.compile(r'/\*.*?\*/|//[^\n]*|\{|\}|[^{}\n]+', re.S)


class Block:
    def __init__(self, kind, name):
        self.kind, self.name = kind, name
        self.kv = {}
        self.items = []      # (key, value) in order, repeats kept (template = ... many times)
        self.kids = []

    def blocks(self, kind):
        return [k for k in self.kids if k.kind == kind]

    def block(self, kind, name=None):
        for k in self.kids:
            if k.kind == kind and (name is None or k.name == name):
                return k
        return None

    def __repr__(self):
        return f'<{self.kind} {self.name}>'


def parse_text(text):
    root = Block('', '')
    stack = [root]
    pending = None
    for m in TOKEN.finditer(text):
        t = m.group(0)
        if t.startswith('/*') or t.startswith('//'):
            continue
        if t == '{':
            words = (pending or '').split()
            b = Block(words[0] if words else '', ' '.join(words[1:]))
            stack[-1].kids.append(b)
            stack.append(b)
            pending = None
        elif t == '}':
            if pending:
                _kv(stack[-1], pending)
                pending = None
            if len(stack) > 1:
                stack.pop()
        else:
            for piece in t.split(','):
                piece = piece.strip()
                if not piece:
                    continue
                if '=' in piece:
                    if pending:
                        pending = None
                    _kv(stack[-1], piece)
                else:
                    pending = piece
    return root


def _kv(b, piece):
    if '=' not in piece:
        return
    k, v = [x.strip() for x in piece.split('=', 1)]
    b.items.append((k, v))
    b.kv.setdefault(k, v)


def floats(s, n=None):
    v = [float(x.rstrip('fF')) for x in s.split()]
    return v if n is None else (v + [0.0] * n)[:n]


class Vehicle:
    def __init__(self, name):
        self.name = name
        self.kv = {}
        self.kids = []
        self.parts = {}

    def blocks(self, kind):
        return [k for k in self.kids if k.kind == kind]

    def block(self, kind, name=None):
        for k in self.kids:
            if k.kind == kind and (name is None or k.name == name):
                return k
        return None


class Scripts:
    def __init__(self, dirs):
        self.models, self.vehicles, self.templates = {}, {}, {}
        for d in dirs:
            for f in sorted(glob.glob(os.path.join(d, '**', '*.txt'), recursive=True)):
                root = parse_text(open(f, encoding='utf-8', errors='ignore').read())
                for mod in root.kids:
                    for b in mod.kids:
                        if b.kind == 'model':
                            self.models.setdefault(b.name, b)
                        elif b.kind == 'vehicle':
                            self.vehicles.setdefault(b.name, b)
                        elif b.kind == 'template' and b.name.startswith('vehicle '):
                            self.templates.setdefault(b.name.split(None, 1)[1], b)

    def _merge(self, v, b, depth=0):
        for k, val in b.items:
            if k in ('template', 'template!') and depth < 8:
                name, _, only = val.partition('/')
                t = self.templates.get(name)
                if t is None:
                    continue
                if only:
                    for p in t.blocks('part'):
                        if p.name == only or (only.endswith('*') and p.name.startswith(only[:-1])):
                            self._part(v, p)
                else:
                    self._merge(v, t, depth + 1)
            else:
                v.kv[k] = val
        for kid in b.kids:
            if kid.kind == 'part':
                self._part(v, kid)
            else:
                v.kids.append(kid)

    def _part(self, v, p):
        name = p.name
        if name.endswith('*'):
            return
        old = v.parts.get(name)
        if old is None:
            v.parts[name] = p
            return
        # a later block for the same part adds to (and overrides) the earlier one
        merged = Block('part', name)
        merged.items = old.items + p.items
        merged.kv = dict(old.kv)
        merged.kv.update(p.kv)
        merged.kids = old.kids + p.kids
        v.parts[name] = merged

    def vehicle(self, name):
        b = self.vehicles[name]
        v = Vehicle(name)
        self._merge(v, b)
        return v
