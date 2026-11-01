"""Pulls Project Zomboid's own zombie sounds out of its FMOD sound bank, as .ogg files for a local resource pack.

Josiah (2026-11-01): Zomboid's zombie audio replaces ours completely. The audio is Zomboid's, so like the bodies it never
goes in the repo or the jar: this writes a resource pack folder for his own profile's resourcepacks only.

    python3 -I tools/pz/pz_sounds.py ZomboidSound.bank OUTDIR [NAME_FILTER_REGEX]   (lists every sample with -l)

A .bank is a RIFF file holding one FSB5 sample bank (SND chunk). FSB5 Vorbis samples are stored without their three
Vorbis headers; the id and comment headers are rebuilt from the sample's rate and channels, and the setup header is
rebuilt by encoding a header at each quality until its CRC32 matches the one FMOD stored with the sample (FMOD made
it with libvorbis, so the same quality gives the same codebooks). Needs libogg + libvorbis (system libraries).
"""
import ctypes, ctypes.util, io, os, re, struct, sys, zlib

FREQ = {1: 8000, 2: 11000, 3: 11025, 4: 16000, 5: 22050, 6: 24000, 7: 32000, 8: 44100, 9: 48000}


def lib(name):
    path = ctypes.util.find_library(name)
    if not path:
        sys.exit(f'needs lib{name}')
    return ctypes.CDLL(path)


ogg, vorbis, venc = lib('ogg'), lib('vorbis'), lib('vorbisenc')


class Info(ctypes.Structure):
    _fields_ = [('version', ctypes.c_int), ('channels', ctypes.c_int), ('rate', ctypes.c_long),
                ('bu', ctypes.c_long), ('bn', ctypes.c_long), ('bl', ctypes.c_long), ('bw', ctypes.c_long),
                ('codec_setup', ctypes.c_void_p)]


class Comment(ctypes.Structure):
    _fields_ = [('user_comments', ctypes.c_void_p), ('comment_lengths', ctypes.c_void_p), ('comments', ctypes.c_int),
                ('vendor', ctypes.c_char_p)]


class Packet(ctypes.Structure):
    _fields_ = [('packet', ctypes.c_void_p), ('bytes', ctypes.c_long), ('b_o_s', ctypes.c_long),
                ('e_o_s', ctypes.c_long), ('granulepos', ctypes.c_longlong), ('packetno', ctypes.c_longlong)]


class Page(ctypes.Structure):
    _fields_ = [('header', ctypes.c_void_p), ('header_len', ctypes.c_long), ('body', ctypes.c_void_p),
                ('body_len', ctypes.c_long)]


# opaque, generously sized (the real structs are a few hundred bytes)
class Blob(ctypes.Structure):
    _fields_ = [('raw', ctypes.c_ubyte * 4096)]


venc.vorbis_encode_setup_vbr.argtypes = [ctypes.POINTER(Info), ctypes.c_long, ctypes.c_long, ctypes.c_float]
vorbis.vorbis_info_blocksize.restype = ctypes.c_int
vorbis.vorbis_packet_blocksize.restype = ctypes.c_long


def packet(data, n, bos=0, eos=0, gp=0):
    buf = ctypes.create_string_buffer(data, len(data))
    p = Packet(ctypes.cast(buf, ctypes.c_void_p), len(data), bos, eos, gp, n)
    p._buf = buf
    return p


_setups = {}


def setup_for(crc, channels, rate):
    """(blocksize short, long, setup packet) of the libvorbis quality whose setup header has this CRC32."""
    key = (crc, channels, rate)
    if key in _setups:
        return _setups[key]
    for q in range(1, 101):
        info, com, dsp = Info(), Comment(), Blob()
        vorbis.vorbis_info_init(ctypes.byref(info))
        vorbis.vorbis_comment_init(ctypes.byref(com))
        quality = -0.1 + 1.1 * (q - 1) / 99.0
        if venc.vorbis_encode_setup_vbr(ctypes.byref(info), channels, rate, ctypes.c_float(quality)):
            continue
        one = ctypes.c_int(1)
        venc.vorbis_encode_ctl(ctypes.byref(info), 0x41, ctypes.byref(one))   # coupling on
        if venc.vorbis_encode_setup_init(ctypes.byref(info)):
            continue
        vorbis.vorbis_analysis_init(ctypes.byref(dsp), ctypes.byref(info))
        a, b, c = Packet(), Packet(), Packet()
        vorbis.vorbis_analysis_headerout(ctypes.byref(dsp), ctypes.byref(com), ctypes.byref(a), ctypes.byref(b),
                                         ctypes.byref(c))
        setup = ctypes.string_at(c.packet, c.bytes)
        bs = (vorbis.vorbis_info_blocksize(ctypes.byref(info), 0), vorbis.vorbis_info_blocksize(ctypes.byref(info), 1))
        vorbis.vorbis_dsp_clear(ctypes.byref(dsp))
        if zlib.crc32(setup) == crc:
            _setups[key] = (bs[0], bs[1], setup)
            return _setups[key]
    raise ValueError(f'no libvorbis quality makes setup crc {crc} ({channels} ch, {rate} Hz)')


def bits(v, s, n):
    return (v >> s) & ((1 << n) - 1)


def id_header(channels, rate, bs, bl):
    out = bytes([1]) + b'vorbis' + struct.pack('<IBIiii', 0, channels, rate, 0, 0, 0)
    return out + bytes([(bl.bit_length() - 1) << 4 | (bs.bit_length() - 1), 1])


def comment_header():
    vendor = b'zomboidcraft'
    return bytes([3]) + b'vorbis' + struct.pack('<I', len(vendor)) + vendor + struct.pack('<I', 0) + bytes([1])


def to_ogg(data, channels, rate, crc):
    bs, bl, setup = setup_for(crc, channels, rate)
    info, com = Info(), Comment()
    vorbis.vorbis_info_init(ctypes.byref(info))
    vorbis.vorbis_comment_init(ctypes.byref(com))
    st = Blob()
    ogg.ogg_stream_init(ctypes.byref(st), 1)
    out = io.BytesIO()

    def flush(force=False):
        pg = Page()
        fn = ogg.ogg_stream_flush if force else ogg.ogg_stream_pageout
        while fn(ctypes.byref(st), ctypes.byref(pg)):
            out.write(ctypes.string_at(pg.header, pg.header_len) + ctypes.string_at(pg.body, pg.body_len))

    heads = [packet(id_header(channels, rate, bs, bl), 0, bos=1), packet(comment_header(), 1), packet(setup, 2)]
    for h in heads:
        if vorbis.vorbis_synthesis_headerin(ctypes.byref(info), ctypes.byref(com), ctypes.byref(h)):
            raise ValueError('bad rebuilt header')
        ogg.ogg_stream_packetin(ctypes.byref(st), ctypes.byref(h))
    flush(True)
    pos, n, gp, prev = 0, 2, 0, 0
    while pos + 2 <= len(data):
        size = struct.unpack_from('<H', data, pos)[0]
        if not size:
            break
        body = data[pos + 2:pos + 2 + size]
        pos += 2 + size
        n += 1
        last = pos + 2 > len(data) or struct.unpack_from('<H', data, pos)[0] == 0
        p = packet(body, n, eos=1 if last else 0)
        blk = vorbis.vorbis_packet_blocksize(ctypes.byref(info), ctypes.byref(p))
        gp = gp + (blk + prev) // 4 if prev else 0
        prev = blk
        p.granulepos = gp
        ogg.ogg_stream_packetin(ctypes.byref(st), ctypes.byref(p))
        flush()
    flush(True)
    ogg.ogg_stream_clear(ctypes.byref(st))
    return out.getvalue()


def fsb_in(path):
    """Offset of the FSB5 inside a .bank (or 0 for a bare .fsb)."""
    with open(path, 'rb') as f:
        head = f.read(1 << 20)
        i = head.find(b'FSB5')
        if i >= 0:
            return i
        # the SND chunk sits near the end of a big bank: scan for it
        f.seek(0)
        off, chunk = 0, f.read(64 << 20)
        while chunk:
            i = chunk.find(b'FSB5')
            if i >= 0:
                return off + i
            off += len(chunk) - 3
            f.seek(off)
            chunk = f.read(64 << 20)
    raise ValueError('no FSB5 in ' + path)


def samples(path):
    base = fsb_in(path)
    f = open(path, 'rb')
    f.seek(base)
    h = f.read(64)
    ver, num, shs, nts, ds, mode = struct.unpack_from('<IIIIII', h, 4)
    hsize = 60 if ver == 1 else 64
    f.seek(base + hsize)
    sh = io.BytesIO(f.read(shs))
    names = f.read(nts)
    out = []
    for i in range(num):
        raw = struct.unpack('<Q', sh.read(8))[0]
        nxt, freq, ch, off, count = bits(raw, 0, 1), bits(raw, 1, 4), bits(raw, 5, 1) + 1, bits(raw, 6, 28) * 16, bits(raw, 34, 30)
        rate, crc = FREQ.get(freq), None
        while nxt:
            c = struct.unpack('<I', sh.read(4))[0]
            nxt, size, kind = bits(c, 0, 1), bits(c, 1, 24), bits(c, 25, 7)
            body = sh.read(size)
            if kind == 1:
                ch = body[0]
            elif kind == 2:
                rate = struct.unpack_from('<I', body)[0]
            elif kind == 11:
                crc = struct.unpack_from('<I', body)[0]
        out.append(dict(i=i, rate=rate, ch=ch, off=off, count=count, crc=crc))
    if nts:
        offs = struct.unpack_from(f'<{num}I', names)
        for s, o in zip(out, offs):
            s['name'] = names[o:names.index(b'\0', o)].decode('utf-8', 'replace')
    data0 = base + hsize + shs + nts
    for k, s in enumerate(out):
        s['end'] = out[k + 1]['off'] if k + 1 < len(out) else ds
    return f, data0, mode, out


def main():
    a = sys.argv[1:]
    listing = '-l' in a
    a = [x for x in a if x != '-l']
    f, data0, mode, ss = samples(a[0])
    if listing:
        for s in ss:
            print(s['i'], s.get('name'), s['rate'], s['ch'], round(s['count'] / max(s['rate'], 1), 2))
        return
    out = a[1]
    pick = re.compile(a[2], re.I) if len(a) > 2 else None
    os.makedirs(out, exist_ok=True)
    assert mode == 15, f'not Vorbis (mode {mode})'
    done = 0
    for s in ss:
        name = s.get('name') or '%05d' % s['i']
        if pick and not pick.search(name):
            continue
        f.seek(data0 + s['off'])
        try:
            blob = to_ogg(f.read(s['end'] - s['off']), s['ch'], s['rate'], s['crc'])
        except ValueError as e:
            print('skip', name, e)
            continue
        open(os.path.join(out, re.sub(r'[^A-Za-z0-9_.-]', '_', name) + '.ogg'), 'wb').write(blob)
        done += 1
    print(done, 'sounds written to', out)


if __name__ == '__main__':
    main()
