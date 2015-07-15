#!/usr/bin/python
# -*- coding: utf-8 -*-
import io
from xml.etree.ElementTree import parse
import struct
from array import array
from builtins import range, map
import random
from bitarray import bitarray
from itertools import count

ns = {'jfif': 'http://xmlmate.org/schemas/jfif'}


def random_data(length, seed):
    random.seed(seed)
    return bytearray(random.getrandbits(8) for _ in range(length))


def write_jfif(jfif, f):
    x_thumbnail = int(jfif[5].text)
    y_thumbnail = int(jfif[6].text)
    thumbnail_size = x_thumbnail * y_thumbnail * 3
    segment_length = 16 + thumbnail_size
    # JFIF-APP0
    f.write(struct.pack('>HHBBBBBBBBHHBB',
                        0xFFE0,  # APP0
                        segment_length,  # segment_length
                        0x4A, 0x46, 0x49, 0x46, 0x00,  # JFIF identifier
                        int(jfif[0].text),  # version major
                        int(jfif[1].text),  # version minor
                        int(jfif[2].text),  # density units
                        int(jfif[3].text),  # Xdensity
                        int(jfif[4].text),  # Ydensity
                        x_thumbnail,  # Xthumbnail
                        y_thumbnail  # Ythumbnail
                        ))
    seed = int(jfif[7].text)
    f.write(random_data(thumbnail_size, seed))  # thumbnail data


def write_jfxx(jfxx, f):
    x_thumbnail = int(jfxx[0][0].text)
    y_thumbnail = int(jfxx[0][1].text)
    seed = int(jfxx[0][2].text)
    thumbnail_size = x_thumbnail * y_thumbnail
    palette = jfxx[0].find('./jfif:palette', ns)
    segment_length = 10 + (thumbnail_size * 3 if palette is None else thumbnail_size + 768)
    thumbnail_format = 0x11 if palette is not None else 0x13
    # JFXX-APP0
    f.write(struct.pack('>HHBBBBBBBB',
                        0xFFE0,  # APP0
                        segment_length,  # length
                        0x4A, 0x46, 0x58, 0x58, 0x00,  # JFXX identifier
                        thumbnail_format,  # thumbnail format
                        x_thumbnail,  # Xthumbnail
                        y_thumbnail  # Ythumbnail
                        ))
    if palette is not None:
        palette_values = palette.iter()
        next(palette_values)  # skip the palette value itself
        f.write(array('B', map(lambda x: int(x.text), palette_values)))
        f.write(random_data(thumbnail_size, seed))
    else:
        f.write(random_data(thumbnail_size * 3, seed))


def write_dqt(dqt, f, num):
    data = dqt.iter()
    next(data)
    f.write(struct.pack('>HHB',
                        0xFFDB,  # FFDB
                        0x43,  # length = 67
                        num  # destination in [0,3]
                        ))
    f.write(array('B', map(lambda x: int(x.text), data)))  # 64 bytes of data


def write_sof(sof, f):
    components = sof.findall('./jfif:component', ns)
    num = len(components)
    assert 1 <= num <= 3
    length = 8 + 3 * num
    f.write(struct.pack('>HHBHHB',
                        0xFFC0,  # FFC0
                        length,  # length
                        8,  # bit depth
                        int(sof[0].text),  # height
                        int(sof[1].text),  # width
                        num  # num components
                        ))
    for component in components:
        f.write(struct.pack('>BBB',
                            int(component[0].text),  # identifier
                            int(component[1].text),  # Hi+Vi
                            int(component[2].text)  # DQT selector
                            ))


def huffman(seed):
    random.seed(seed)
    vals = []
    for i in range(1, 17):
        ul = 2 ** min(i, 8)
        ul = min(ul, ul - sum(map(lambda xy: xy[1] * 2 ** (i - xy[0]), enumerate(vals, start=1))))
        x = random.randint(0, max(0, ul))
        # print('i=%d \t%d <= %d' % (i, x, ul))
        vals.append(x)
    return vals


def write_dht(dht, f):
    bitseed = int(dht[1].text)
    bits = bytearray(huffman(bitseed))
    nhv = sum(bits)
    length = 19 + nhv
    f.write(struct.pack('>HHB',
                        0xFFC4,  # FFC4
                        length,  # length
                        int(dht[0].text)  # class + destination
                        ))
    f.write(array('B', bits))
    nhvseed = int(dht[2].text)
    f.write(random_data(nhv, nhvseed))


def write_sos(sos, f):
    components = sos.findall('./jfif:component', ns)
    num = len(components)
    assert 1 <= num <= 3
    length = 6 + 2 * num
    f.write(struct.pack('>HHB',
                        0xFFDA,  # FFDA
                        length,  # length
                        num  # num components
                        ))
    for component in components:
        f.write(struct.pack('>BB',
                            int(component[0].text),  # identifier
                            int(component[1].text)  # DC + AC
                            ))
    f.write(struct.pack('>BBB', 0, 63, 0))


def xml2jpeg(input_file, output_file):
    tree = parse(input_file)
    root = tree.getroot()
    jfif = root.find('./jfif:jfif-app0', ns)
    assert jfif is not None
    sof = root.find('./jfif:sof', ns)
    assert sof is not None
    height = int(sof[0].text)
    width = int(sof[1].text)
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
        # f.write(random_data(1920*1080, 5))  # TODO
        f.write(struct.pack('>H', 0xFFD9))  # EOI


if __name__ == '__main__':
    # while True:
    #     seed = random.randrange(2**32)
    #     if not sum(list(huffman(seed))[:4]):
    #         print(seed)
    #         break

    # huff = huffman(13)
    # nhvs = random_data(sum(huff), 37)
    # print(huff)
    # print(list(map(str, nhvs)))

    # d = {}
    # c = count()
    # for p, n in enumerate(huff, start=1):
    #     for h in range(n):
    #         k = nhvs[h]
    #         v = bitarray(bin(next(c))[2:].zfill(p), endian='big')
    #         d[k] = v
    # print(d)

    from sys import argv
    xml2jpeg(argv[1], argv[2])
