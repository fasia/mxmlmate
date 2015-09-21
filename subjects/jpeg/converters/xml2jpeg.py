#!/usr/bin/python
# -*- coding: utf-8 -*-
from xml.etree.ElementTree import parse
import struct
from array import array
from random import Random
from collections import defaultdict, OrderedDict
from itertools import chain, islice
from bitarray import bitarray

ns = {'jfif': 'http://xmlmate.org/schemas/jfif'}


def random_data(length, seed):
    if length > 100000:
        print 'WARNING! Trying to non-lazily instantiate a large bytearray of %d elements.' % length
    r = Random(seed)
    return bytearray(r.getrandbits(8) for _ in xrange(length))



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


def write_sof(sof, ids, f):
    components = sof.findall('./jfif:qdt-selector', ns)
    assert not components or len(components) == 2
    num = 3 if components else 1
    length = 8 + 3 * num
    f.write(struct.pack('>HHBHHBBBB',
                        0xFFC0,                     # FFC0
                        length,                     # length
                        8,                          # bit depth
                        int(sof[0].text),           # height
                        int(sof[1].text),           # width
                        num,                        # num components
                        ids[0],                     # Y component id
                        int(sof[2][0].text),        # Hi+Vi
                        int(sof[2][1].text)         # DQT selector
                        ))
    for i, component in enumerate(components, start=1):
        f.write(struct.pack('>BBB',
                            ids[i],                 # identifier
                            0x11,                   # Hi+Vi
                            int(component[0].text)  # DQT selector
                            ))


huffman_luminance_dc = {x: y for x, y in enumerate(map(bitarray,
                                                       ('00', '010', '011', '100', '101', '110', '1110', '11110',
                                                        '111110', '1111110', '11111110', '111111110')))}

huffman_luminance_ac_values = (0x01, 0x02, 0x03, 0x00, 0x04, 0x11, 0x05, 0x12, 0x21, 0x31, 0x41, 0x06, 0x13, 0x51, 0x61,
                               0x07, 0x22, 0x71, 0x14, 0x32, 0x81, 0x91, 0xA1, 0x08, 0x23, 0x42, 0xB1, 0xC1, 0x15, 0x52,
                               0xD1, 0xF0, 0x24, 0x33, 0x62, 0x72, 0x82, 0x09, 0x0A, 0x16, 0x17, 0x18, 0x19, 0x1A, 0x25,
                               0x26, 0x27, 0x28, 0x29, 0x2A, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3A, 0x43, 0x44, 0x45,
                               0x46, 0x47, 0x48, 0x49, 0x4A, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5A, 0x63, 0x64,
                               0x65, 0x66, 0x67, 0x68, 0x69, 0x6A, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79, 0x7A, 0x83,
                               0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8A, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99,
                               0x9A, 0xA2, 0xA3, 0xA4, 0xA5, 0xA6, 0xA7, 0xA8, 0xA9, 0xAA, 0xB2, 0xB3, 0xB4, 0xB5, 0xB6,
                               0xB7, 0xB8, 0xB9, 0xBA, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0xCA, 0xD2, 0xD3,
                               0xD4, 0xD5, 0xD6, 0xD7, 0xD8, 0xD9, 0xDA, 0xE1, 0xE2, 0xE3, 0xE4, 0xE5, 0xE6, 0xE7, 0xE8,
                               0xE9, 0xEA, 0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0xFA)


huffman_luminance_ac_prefixes = ('00', '01', '100', '1010', '1011', '1100', '11010', '11011', '11100', '111010',
                                 '111011', '1111000', '1111001', '1111010', '1111011', '11111000', '11111001',
                                 '11111010', '111110110', '111110111', '111111000', '111111001', '111111010',
                                 '1111110110', '1111110111', '1111111000', '1111111001', '1111111010', '11111110110',
                                 '11111110111', '11111111000', '11111111001', '111111110100', '111111110101',
                                 '111111110110', '111111110111', '111111111000000', '1111111110000010',
                                 '1111111110000011', '1111111110000100', '1111111110000101', '1111111110000110',
                                 '1111111110000111', '1111111110001000', '1111111110001001', '1111111110001010',
                                 '1111111110001011', '1111111110001100', '1111111110001101', '1111111110001110',
                                 '1111111110001111', '1111111110010000', '1111111110010001', '1111111110010010',
                                 '1111111110010011', '1111111110010100', '1111111110010101', '1111111110010110',
                                 '1111111110010111', '1111111110011000', '1111111110011001', '1111111110011010',
                                 '1111111110011011', '1111111110011100', '1111111110011101', '1111111110011110',
                                 '1111111110011111', '1111111110100000', '1111111110100001', '1111111110100010',
                                 '1111111110100011', '1111111110100100', '1111111110100101', '1111111110100110',
                                 '1111111110100111', '1111111110101000', '1111111110101001', '1111111110101010',
                                 '1111111110101011', '1111111110101100', '1111111110101101', '1111111110101110',
                                 '1111111110101111', '1111111110110000', '1111111110110001', '1111111110110010',
                                 '1111111110110011', '1111111110110100', '1111111110110101', '1111111110110110',
                                 '1111111110110111', '1111111110111000', '1111111110111001', '1111111110111010',
                                 '1111111110111011', '1111111110111100', '1111111110111101', '1111111110111110',
                                 '1111111110111111', '1111111111000000', '1111111111000001', '1111111111000010',
                                 '1111111111000011', '1111111111000100', '1111111111000101', '1111111111000110',
                                 '1111111111000111', '1111111111001000', '1111111111001001', '1111111111001010',
                                 '1111111111001011', '1111111111001100', '1111111111001101', '1111111111001110',
                                 '1111111111001111', '1111111111010000', '1111111111010001', '1111111111010010',
                                 '1111111111010011', '1111111111010100', '1111111111010101', '1111111111010110',
                                 '1111111111010111', '1111111111011000', '1111111111011001', '1111111111011010',
                                 '1111111111011011', '1111111111011100', '1111111111011101', '1111111111011110',
                                 '1111111111011111', '1111111111100000', '1111111111100001', '1111111111100010',
                                 '1111111111100011', '1111111111100100', '1111111111100101', '1111111111100110',
                                 '1111111111100111', '1111111111101000', '1111111111101001', '1111111111101010',
                                 '1111111111101011', '1111111111101100', '1111111111101101', '1111111111101110',
                                 '1111111111101111', '1111111111110000', '1111111111110001', '1111111111110010',
                                 '1111111111110011', '1111111111110100', '1111111111110101', '1111111111110110',
                                 '1111111111110111', '1111111111111000', '1111111111111001', '1111111111111010',
                                 '1111111111111011', '1111111111111100', '1111111111111101', '1111111111111110')

huffman_luminance_ac = OrderedDict(zip(huffman_luminance_ac_values, map(bitarray, huffman_luminance_ac_prefixes)))

huffman_chrominance_dc = {x: y for x, y in enumerate(map(bitarray,
                                                         ('00', '01', '10', '110', '1110', '11110', '111110', '1111110',
                                                          '11111110', '111111110', '1111111110', '11111111110')))}


def write_dht(huff_dict, cls_dst, f):
    length = 19 + len(huff_dict)
    f.write(struct.pack('>HHB',
                        0xFFC4,  # FFC4
                        length,  # length
                        cls_dst  # class + destination
                        ))
    # calculate number of prefixes of each length
    freqs = freq_string(map(lambda x: x.length(), huff_dict.itervalues()))
    f.write(array('B', [freqs[p] for p in xrange(1, 17)]))
    # stably order by the corresponding value
    sorted1 = sorted(huff_dict.items(), key=lambda t: (t[1].length(), int(t[1].to01(), 2)))
    f.write(array('B', zip(*sorted1)[0]))


def write_sos(sos, ids, f):
    components = sos.findall('./jfif:dc-ac', ns)
    num = len(components)
    assert 1 <= num <= 3
    length = 6 + 2 * num
    f.write(struct.pack('>HHB',
                        0xFFDA,  # FFDA
                        length,  # length
                        num      # num components
                        ))
    for i, component in enumerate(components):
        f.write(struct.pack('>BB',
                            ids[i],              # identifier
                            int(component.text)  # DC + AC
                            ))
    f.write(struct.pack('>BBB', 0, 63, 0))


generate_bits = lambda r, k: bitarray(bin(r.getrandbits(k))[2:].zfill(k), endian='big') if k else bitarray(endian='big')

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
    # sof[0].text = str(height)
    width = int(sof[1].text)
    # ensure width divisible by 8
    width = ((width + 7) // 8) * 8
    # sof[1].text = str(width)
    subsampling = int(sof.find('./jfif:Y/jfif:hivi', ns).text)
    hi = subsampling >> 4
    vi = subsampling & 0x0F
    sos = root.find('./jfif:sos', ns)
    assert sos is not None
    data_seed = root.find('./jfif:data-seed', ns)
    assert data_seed is not None
    data_seed = int(data_seed.text)
    component_ids = map(lambda x: int(x.text), root.findall('./jfif:xmlmate-component-id', ns))
    assert len(component_ids) == 3

    with open(output_file, mode='wb') as f:
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
        write_sof(sof, component_ids, f)

        # Precompute the image data
        num_Ys = width * height // 64
        r = Random(data_seed)
        Ydc_lengths = (r.randrange(12) for _ in xrange(num_Ys))
        Yac_lengths = (r.choice(huffman_luminance_ac_values) for _ in xrange(num_Ys * 63))

        # write huffman table for Y_dc
        write_dht(huffman_luminance_dc, 0, f)  # 0x00 = DC, ID0

        # write huffman table for Y_ac
        write_dht(huffman_luminance_ac, 0x10, f)  # 0x10 = AC, ID0

        # num_CbCrs = num_Ys // (hi * vi)
        # TODO write huffman table for CbCr_dc

        # TODO write huffman table for CbCr_ac

        # SOS
        write_sos(sos, component_ids, f)
        # DATA
        data = bitarray(endian='big')
        for mcu, ydc_len, yac_lens in zip(xrange(num_Ys), Ydc_lengths, chunks(63, Yac_lengths)):
            yac_lens = list(yac_lens)  # it's only 63 elements, so it's ok
            ydc_data = generate_bits(r, ydc_len)
            yac_data = (generate_bits(r, k & 0x0F) for k in yac_lens)
            data.extend(huffman_luminance_dc[ydc_len])
            data.extend(ydc_data)
            ac_values = 63
            for l, b in zip(yac_lens, yac_data):
                assert b.length() == l & 0x0F
                assert ac_values >= 0
                if 0 == ac_values:
                    break
                else:
                    runlength = min((l & 0xF0) >> 4, ac_values - 1)  # cap maximum runlength
                    l = l & 0x0F | runlength << 4
                    try:
                        data.extend(huffman_luminance_ac[l])
                    except KeyError:  # XXX fix this hacky workaround some time
                        l = 0x00
                        data.extend(huffman_luminance_ac[l])
                    if l == 0x00:
                        ac_values = 0
                        break
                    else:
                        data.extend(b)
                        ac_values -= (runlength + 1)
            assert 0 == ac_values
            # TODO chromaticity + subsampling
        # add stuff bytes
        f.write(stuff_bytes(bytearray(data.tobytes())))
        f.write(struct.pack('>H', 0xFFD9))  # EOI


def chunks(n, iterable):
    iterable = iter(iterable)
    while True:
        yield chain([next(iterable)], islice(iterable, n-1))

def freq_string(s):
    """
    Given a string, return a dictionary
    mapping characters to their frequency.
    """
    res = defaultdict(int)
    for c in s:
        res[c] += 1
    return res

def stuff_bytes(b):
    t = bytearray()
    for x in b:
        t.append(x)
        if x == 0xff:
            t.append(0x00)
    return t

if __name__ == '__main__':
    from sys import argv
    xml2jpeg(argv[1], argv[2])
