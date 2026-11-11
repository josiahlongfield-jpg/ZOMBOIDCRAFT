import re, html, base64, sys
md = open('attributes.md').read()
img = base64.b64encode(open('diagram.png','rb').read()).decode()
def inl(s):
    s = html.unescape(s).replace('\\]', ']').replace('\\[', '[')
    s = html.escape(s, quote=False)
    s = re.sub(r'`([^`]+)`', r'<code>\1</code>', s)
    s = re.sub(r'\*\*([^*]+)\*\*', r'<strong>\1</strong>', s)
    s = re.sub(r'(?<![\w*])\*([^*]+)\*(?!\w)', r'<em>\1</em>', s)
    s = re.sub(r'\[([^\]]+)\]\((https?://[^)]+)\)', r'<a href="\2">\1</a>', s)
    return s
out = []; lines = md.split('\n'); i = 0
while i < len(lines):
    l = lines[i]
    if not l.strip(): i += 1; continue
    m = re.match(r'(#{1,3}) (.*)', l)
    if m:
        n = len(m.group(1)); out.append(f'<h{n}>{inl(m.group(2))}</h{n}>'); i += 1
        if n == 1 and i+2 < len(lines) and '@' in lines[i+1]:
            out.append(f'<p class="by">{inl(lines[i+1])}</p>'); i += 2
        continue
    if 'embedded content' in l:
        out.append(f'<figure><img src="data:image/png;base64,{img}"><figcaption>How it thinks: senses, memory, needs and traits feed one choice</figcaption></figure>'); i += 1; continue
    if l.startswith('|'):
        rows = []
        while i < len(lines) and lines[i].startswith('|'):
            rows.append([c.strip() for c in lines[i].strip().strip('|').split('|')]); i += 1
        t = '<table><thead><tr>' + ''.join(f'<th>{inl(c)}</th>' for c in rows[0]) + '</tr></thead><tbody>'
        for r in rows[2:]: t += '<tr>' + ''.join(f'<td>{inl(c)}</td>' for c in r) + '</tr>'
        out.append(t + '</tbody></table>'); continue
    if re.match(r'- ', l):
        items = []
        while i < len(lines) and lines[i].startswith('- '): items.append(lines[i][2:]); i += 1
        out.append('<ul>' + ''.join(f'<li>{inl(x)}</li>' for x in items) + '</ul>'); continue
    if re.match(r'\d+\. ', l):
        items = []; start = int(l.split('.')[0])
        while i < len(lines) and re.match(r'\d+\. ', lines[i]): items.append(re.sub(r'^\d+\. ', '', lines[i])); i += 1
        out.append(f'<ol start="{start}">' + ''.join(f'<li>{inl(x)}</li>' for x in items) + '</ol>'); continue
    para = []
    while i < len(lines) and lines[i].strip() and not re.match(r'(#|\||- |\d+\. )', lines[i]): para.append(lines[i]); i += 1
    out.append(f'<p>{inl(" ".join(para))}</p>')
css = """
@page { size: A4; margin: 18mm 16mm 18mm 16mm; }
body { font-family: 'DejaVu Sans', 'Liberation Sans', Arial, sans-serif; font-size: 10.5pt; line-height: 1.5; color: #1d1b19; background: #fff; }
h1 { font-size: 24pt; margin: 0 0 2pt; letter-spacing: -0.3pt; color: #5a1410; }
.by { color: #6b6560; margin: 0 0 14pt; font-size: 9.5pt; border-bottom: 2px solid #5a1410; padding-bottom: 8pt; }
h2 { font-size: 15pt; color: #5a1410; margin: 20pt 0 6pt; border-bottom: 1px solid #ddd5cc; padding-bottom: 3pt; break-after: avoid; }
h3 { font-size: 11.5pt; margin: 14pt 0 4pt; break-after: avoid; }
p { margin: 0 0 8pt; }
table { width: 100%; border-collapse: collapse; margin: 4pt 0 12pt; font-size: 9.5pt; break-inside: auto; }
tr { break-inside: avoid; }
th { background: #2a2420; color: #f4ede6; text-align: left; padding: 5pt 7pt; font-weight: 600; }
td { border-bottom: 1px solid #e4ddd5; padding: 5pt 7pt; vertical-align: top; }
tbody tr:nth-child(even) td { background: #f7f3ef; }
ul, ol { margin: 0 0 10pt; padding-left: 18pt; } li { margin-bottom: 3pt; }
code { font-family: 'DejaVu Sans Mono', monospace; font-size: 9pt; background: #f1ebe5; padding: 1pt 4pt; border-radius: 3pt; }
strong { color: #2a2420; }
figure { margin: 6pt 0 14pt; break-inside: avoid; text-align: center; }
figure img { max-width: 100%; border: 1px solid #ddd5cc; border-radius: 6pt; }
figcaption { font-size: 8.5pt; color: #6b6560; margin-top: 4pt; }
"""
open('attributes.html','w').write(f'<!doctype html><html><head><meta charset="utf-8"><title>Harvestman: attributes</title><style>{css}</style></head><body>{"".join(out)}</body></html>')
print('html ok', len(out))
