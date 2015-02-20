#!/usr/bin/python
# -*- coding: utf-8 -*-
import sys
import io
from xml.etree.ElementTree import parse
from random import randint
import struct


def xml2pcap(input_file, output_file):
    tree = parse(input_file)
    root = tree.getroot()
    header = root[0]
    snaplen = int(header[4].text)
    with io.open(output_file, mode='wb') as f:
        f.write(struct.pack('=IhhiIII', *([0xa1b2c3d4] + [int(t.text) for t in header])))
        for c in root[1:]:
            incl_len = min(int(c[2].text), snaplen)
            f.write(struct.pack('=IIII%dB' % incl_len, int(c[0].text), int(c[1].text), incl_len, incl_len,
                                *[randint(0, 255) for _ in range(incl_len)]))


if __name__ == '__main__':
    xml2pcap(sys.argv[1], sys.argv[2])