#!/usr/bin/python
# -*- coding: utf-8 -*-
import sys
import io
from xml.etree.ElementTree import parse
import struct
from array import array
import os

ns = {'jfif': 'http://xmlmate.org/schemas/jfif'}


def random_data(length):
    urandom = os.urandom
    return bytearray(urandom(length)).replace(b'0xFF', b'0xFE')


def write_jfif(jfif, f):
    x_thumbnail = int(jfif[5].text)
    y_thumbnail = int(jfif[6].text)
    thumbnail_size = x_thumbnail * y_thumbnail * 3
    segment_length = 16 + thumbnail_size
    # JFIF-APP0
    f.write(struct.pack('>HHBBBBBBBBHHBB',
                        0xFFE0,                        # APP0
                        segment_length,                # segment_length
                        0x4A, 0x46, 0x49, 0x46, 0x00,  # JFIF identifier
                        int(jfif[0].text),             # version major
                        int(jfif[1].text),             # version minor
                        int(jfif[2].text),             # density units
                        int(jfif[3].text),             # Xdensity
                        int(jfif[4].text),             # Ydensity
                        x_thumbnail,                   # Xthumbnail
                        y_thumbnail                    # Ythumbnail
                        ))
    f.write(random_data(thumbnail_size))               # thumbnail data


def write_jfxx(jfxx, f):
    x_thumbnail = int(jfxx[0][0].text)
    y_thumbnail = int(jfxx[0][1].text)
    thumbnail_size = x_thumbnail * y_thumbnail
    palette = jfxx[0].find('./jfif:palette', ns)
    segment_length = 10 + (thumbnail_size * 3 if palette is None else thumbnail_size + 768)
    thumbnail_format = 0x11 if palette is not None else 0x13
    # JFXX-APP0
    f.write(struct.pack('>HHBBBBBBBB',
                        0xFFE0,                        # APP0
                        segment_length,                # length
                        0x4A, 0x46, 0x58, 0x58, 0x00,  # JFXX identifier
                        thumbnail_format,              # thumbnail format
                        x_thumbnail,                   # Xthumbnail
                        y_thumbnail                    # Ythumbnail
                        ))
    if palette is not None:
        palette_values = palette.iter()
        next(palette_values)  # skip the palette value itself
        f.write(array('B', map(lambda x: int(x.text), palette_values)))
        f.write(random_data(thumbnail_size))
    else:
        f.write(random_data(thumbnail_size * 3))


def write_dqt(dqt, f, num):
    data = dqt.iter()
    next(data)
    f.write(struct.pack('>HHB',
                        0xFFDB,  # FFDB
                        0x43,    # length = 67
                        num      # destination in [1,4]
                        ))
    f.write(array('B', map(lambda x: int(x.text), data)))  # 64 bytes of data


def write_sof(sof, f):
    components = sof.findall('./jfif:component', ns)
    num = len(components)
    assert 1 <= num <= 3
    length = 8 + 3 * num
    f.write(struct.pack('>HHBHHB',
                        0xFFC0,            # FFC0
                        length,            # length
                        8,                 # bit depth
                        int(sof[0].text),  # width
                        int(sof[1].text),  # height
                        num                # num components
                        ))
    for component in components:
        f.write(struct.pack('>BBB',
                            int(component[0].text),  # identifier
                            int(component[1].text),  # Hi+Vi
                            int(component[2].text)   # DQT selector
                            ))


def write_dht(dht, f):
    bit_items = list(dht)[1:]
    assert len(bit_items) == 16
    bits = [min(int(b.text), 2**(i+1)-1) for (i, b) in enumerate(bit_items)]  # constrain BITS[i] < 2^(i+1)
    nhv = sum(bits)
    length = 19 + nhv
    f.write(struct.pack('>HHB',
                        0xFFC4,           # FFC4
                        length,           # length
                        int(dht[0].text)  # class + destination
                        ))
    f.write(array('B', bits))
    f.write(random_data(nhv))  # FIXME probably need to generate unique bytes


def write_sos(sos, f):
    components = sos.findall('./jfif:component', ns)
    num = len(components)
    assert 1 <= num <= 3
    length = 6 + 2 * num
    f.write(struct.pack('>HHB',
                        0xFFDA,  # FFDA
                        length,  # length
                        num      # num components
                        ))
    for component in components:
        f.write(struct.pack('>BB',
                            int(component[0].text),  # identifier
                            int(component[1].text)   # DC + AC
                            ))
    f.write(struct.pack('>BBB', 0, 63, 0))


def xml2jpeg(input_file, output_file):
    tree = parse(input_file)
    root = tree.getroot()
    jfif = root.find('./jfif:jfif-app0', ns)
    assert jfif is not None
    sof = root.find('./jfif:sof', ns)
    assert sof is not None
    width = int(sof[0].text)
    height = int(sof[1].text)
    dhts = root.findall('./jfif:dht', ns)
    assert dhts
    sos = root.find('./jfif:sos', ns)
    assert sos is not None

    with io.open(output_file, mode='wb') as f:
        f.write(struct.pack('>H', 0xFFD8))  # SOI
        # JFIF-APP0
        write_jfif(jfif, f)
        # JFXX-APP0
        jfxx = root.find('./jfif:jfxx-app0', ns)
        if jfxx is not None:
            write_jfxx(jfxx, f)
        # DQTs
        dqts = root.findall('./jfif:dqt', ns)
        for num, dqt in enumerate(dqts):
            write_dqt(dqt, f, num)
        # SOF
        write_sof(sof, f)
        # DHTs
        for dht in dhts:
            write_dht(dht, f)
        write_sos(sos, f)
        f.write(random_data(32))
        f.write(struct.pack('>H', 0xFFD9))  # EOI


if __name__ == '__main__':
    xml2jpeg(sys.argv[1], sys.argv[2])
