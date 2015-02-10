#!/usr/bin/python
# -*- coding: utf-8 -*-
from xml2png import xml2png
from sys import argv

def convert(inputFile):
    o = inputFile.replace('.xml', '.png')
    xml2png(inputFile, o)
    return o

def convertAll(inputs = None):
    if inputs is None:
        inputs = []
    outputs = []
    for i in inputs:
        outputs.append(convert(i))
    return outputs

if __name__ == '__main__':
    convertAll(argv[1:])