#!/usr/bin/python
# -*- coding: utf-8 -*-
from xml2pcap import xml2pcap
from sys import argv


def convert(input_file):
    o = input_file.replace('.xml', '.pcap')
    xml2pcap(input_file, o)
    return o


def convertAll(inputs=None):
    if inputs is None:
        inputs = []
    outputs = []
    for i in inputs:
        outputs.append(convert(i))
    return outputs


if __name__ == '__main__':
    convertAll(argv[1:])