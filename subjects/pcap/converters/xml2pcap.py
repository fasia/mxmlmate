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
    snaplen = int(header[5].text)
    print('snaplen', snaplen)
    buf = bytearray()
    buf.extend(struct.pack('=I', 0xa1b2c3d4))  # magic
    buf.extend(struct.pack('=h', (int(header[1].text))))  # major
    buf.extend(struct.pack('=h', (int(header[2].text))))  # minor

    # buf.extend(struct.pack('=I', (int(header[3].text))))  # thiszone
    buf.extend(struct.pack('=i', (int(header[3].text))))  # thiszone

    buf.extend(struct.pack('=I', (int(header[4].text))))  # sigfigs
    buf.extend(struct.pack('=I', (int(header[5].text))))  # snaplen
    buf.extend(struct.pack('=I', (int(header[6].text))))  # data link type
    for c in root[1:]:
        incl_len = min(int(c[2].text), snaplen)
        print('incl_len', incl_len)
        buf.extend(struct.pack('=I', int(c[0].text)))  # ts_sec
        buf.extend(struct.pack('=I', int(c[1].text)))  # ts_usec
        buf.extend(struct.pack('=I', incl_len))        # incl_len
        buf.extend(struct.pack('=I', incl_len))        # orig_len
        buf.extend(struct.pack('='+'B'*incl_len, *[randint(0, 255) for _ in range(incl_len)]))  # data
    with io.open(output_file, mode='wb') as fw:
        fw.write(buf)


if __name__ == '__main__':
    xml2pcap(sys.argv[1], sys.argv[2])