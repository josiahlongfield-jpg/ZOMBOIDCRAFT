"""Minimal DirectX .x (text) reader for Project Zomboid models and animations."""
import re

TOK = re.compile(r'"[^"]*"|<[^>]*>|[{};,]|[-+]?(?:\d+\.\d*|\.\d+|\d+)(?:[eE][-+]?\d+)?|[A-Za-z_][A-Za-z0-9_\-\.]*')


class Obj:
    def __init__(self, kind, name):
        self.kind, self.name = kind, name
        self.nums, self.strs, self.kids, self.refs = [], [], [], []

    def child(self, kind):
        return [k for k in self.kids if k.kind == kind]

    def __repr__(self):
        return f"<{self.kind} {self.name} n={len(self.nums)} kids={len(self.kids)}>"


def parse(path):
    text = open(path, encoding='latin-1').read()
    text = text.split('\n', 1)[1]  # drop xof header
    toks = TOK.findall(text)
    i = 0
    root = Obj('root', None)
    stack = [root]
    n = len(toks)
    while i < n:
        t = toks[i]
        cur = stack[-1]
        if t == 'template':
            # skip template block
            while toks[i] != '{':
                i += 1
            depth = 0
            while True:
                if toks[i] == '{':
                    depth += 1
                elif toks[i] == '}':
                    depth -= 1
                    if depth == 0:
                        break
                i += 1
            i += 1
            continue
        if t == '}':
            stack.pop()
            i += 1
            continue
        if t == '{':
            # reference { name }
            if i + 2 < n and toks[i + 2] == '}':
                cur.refs.append(toks[i + 1])
                i += 3
                continue
            o = Obj('anon', None)
            cur.kids.append(o)
            stack.append(o)
            i += 1
            continue
        if t in (';', ','):
            i += 1
            continue
        if t[0] == '"':
            cur.strs.append(t[1:-1])
            i += 1
            continue
        if t[0] == '<':
            i += 1
            continue
        if t[0].isalpha() or t[0] == '_':
            # object: Kind [Name] {
            if i + 1 < n and toks[i + 1] == '{':
                o = Obj(t, None)
                i += 2
            elif i + 2 < n and toks[i + 2] == '{':
                o = Obj(t, toks[i + 1])
                i += 3
            else:
                raise ValueError(f'unexpected ident {t} at {i}: {toks[i-3:i+4]}')
            cur.kids.append(o)
            stack.append(o)
            continue
        cur.nums.append(float(t))
        i += 1
    return root


def walk(o, kind=None):
    for k in o.kids:
        if kind is None or k.kind == kind:
            yield k
        yield from walk(k, kind)
