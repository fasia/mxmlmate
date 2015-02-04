#!/usr/bin/python
# -*- coding: utf-8 -*-
from xml2png import xml2png
from sys import argv

def convert(inputs = None):
    if inputs is None:
        inputs = []
    outputs = []
    for i in inputs:
        o = i.replace('.xml', '.png')
        xml2png(i, o)
        outputs.append(o)
    return outputs

if __name__ == '__main__':
    convert(argv[1:])