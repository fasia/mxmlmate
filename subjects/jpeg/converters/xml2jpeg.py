#!/usr/bin/python
# -*- coding: utf-8 -*-
import io
from xml.etree.ElementTree import parse
import struct
from array import array
import random
from bitarray import bitarray
from itertools import count
from collections import defaultdict
from bitarray import bitarray
from heapq import heappush, heappop



ns = {'jfif': 'http://xmlmate.org/schemas/jfif'}


def random_data(length, seed):
    if seed is not None:
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
    components = sof.findall('./jfif:qdt-selector', ns)
    assert not components or len(components) == 2
    print(type(components))  # FIXME remove
    num = 3 if components else 1
    length = 8 + 3 * num
    f.write(struct.pack('>HHBHHBBBB',
                        0xFFC0,  # FFC0
                        length,  # length
                        8,  # bit depth
                        int(sof[0].text),  # height
                        int(sof[1].text),  # width
                        num,  # num components
                        0,    # Y component id
                        int(sof[2][0]),  # Hi+Vi
                        int(sof[2][0])   # DQT selector
                        ))
    for i, component in enumerate(components, start=1):
        f.write(struct.pack('>BBB',
                            i,  # identifier
                            0x11,  # Hi+Vi
                            int(component[0].text)  # DQT selector
                            ))


def write_dht(dht, f):
    pass
    # FIXME
    # bitseed = int(dht[1].text)
    # bits = bytearray(huffman(bitseed))
    # nhv = sum(bits)
    # length = 19 + nhv
    # f.write(struct.pack('>HHB',
    #                     0xFFC4,  # FFC4
    #                     length,  # length
    #                     int(dht[0].text)  # class + destination
    #                     ))
    # f.write(array('B', bits))
    # nhvseed = int(dht[2].text)
    # f.write(random_data(nhv, nhvseed))


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
    # ensure height divisible by 8
    height = ((height + 7) // 8) * 8
    sof[0].text = str(height)
    width = int(sof[1].text)
    # ensure width divisible by 8
    width = ((width + 7) // 8) * 8
    sof[1].text = str(width)
    subsampling = int(sof.find('./jfif:hivi', ns).text)
    hi = subsampling >> 4
    vi = subsampling & 0xF0
    dhts = root.findall('./jfif:dht', ns)
    assert dhts
    sos = root.find('./jfif:sos', ns)
    assert sos is not None
    data_seed = root.find('./jfif:data-seed', ns)
    assert data_seed is not None

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
        # Precompute the image data
        num_Ys = width * height // 64
        num_CbCrs = num_Ys // (hi * vi)

        random.seed(data_seed)
        Ydc_codes = [random.randrange(12) for _ in range(num_Ys)]
        Ydc_bits = [k and bitarray(bin(random.getrandbits(k))[2:].zfill(k)) for k in Ydc_codes]
        # TODO compute huffman for Ydc_codes
        # TODO add stuff bytes


        # DHTs
        for dht in dhts:
            write_dht(dht, f)
        # SOS
        write_sos(sos, f)
        # DATA
        # f.write(coded_image_data)
        f.write(struct.pack('>H', 0xFFD9))  # EOI


def huff_code(freq):
    """
    Given a dictionary mapping symbols to their frequency,
    return the Huffman code in the form of
    a dictionary mapping the symbols to bitarrays.
    """
    minheap = []
    for s in freq:
        heappush(minheap, (freq[s], s))

    while len(minheap) > 1:
        right, left = heappop(minheap), heappop(minheap)
        parent = (left[0] + right[0], left, right)
        heappush(minheap, parent)

    # Now minheap[0] is the root node of the Huffman tree

    def traverse(tree, prefix=bitarray()):
        if len(tree) == 2:
            result[tree[1]] = prefix
        else:
            traverse(tree[1], prefix + bitarray([0]))
            traverse(tree[2], prefix + bitarray([1]))

    result = {}
    traverse(minheap[0])
    return result

def freq_string(s):
    """
    Given a string, return a dictionary
    mapping characters to thier frequency.
    """
    res = defaultdict(int)
    for c in s:
        res[c] += 1
    return res


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
