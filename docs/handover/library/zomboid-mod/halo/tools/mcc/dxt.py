import struct, io
from PIL import Image
def dds(data, w, h, fourcc=b'DXT5'):
    hdr = struct.pack('<4s7I44s2I4s5I5I', b'DDS ', 124, 0x1007 | 0x80000, h, w, len(data), 0, 1, b'\0' * 44,
                      32, 4, fourcc, 0, 0, 0, 0, 0, 0x1000, 0, 0, 0, 0)
    return Image.open(io.BytesIO(hdr + data))
