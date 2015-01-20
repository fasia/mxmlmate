import xml.etree.ElementTree as ET
# from lxml import etree as ET
import io
import binascii
import zlib
import struct

class Chunk:    
    def __init__(self, size=0, chunk_type='nONE', data=''):
        self.size = size
        self.chunk_type = chunk_type
        self.data = data
        self.crc = self.getCRC()
        
    def extractFromData(self, pngData, index):        
        self.size = bytesToBigEndianInt(pngData[index:index + 4])
        self.chunk_type = pngData[index + 4:index + 8]        
        self.data = bytearray(pngData[index + 8:index + self.size + 8])        
        self.crc = bytesToBigEndianInt(pngData[index + 8 + self.size:index + 8 + self.size + 4])
        
    def getCRC(self):        
        crc = 0
        crc = binascii.crc32(self.chunk_type, crc)
        crc = binascii.crc32(self.data, crc)     
        return crc & 0xffffffff
    
    def updateCRC(self):
        self.crc = self.getCRC()

def isIntAlready(dt):
    try:
        dt += 1
        return True
    except TypeError:
        return False

def bytesToBigEndianInt(dt, mod=0xFFFFFFFF):
    if isIntAlready(dt): return dt & mod
     
    num = 0
    for i in dt:
        num = num << 8
        num = num + i
    return num & mod

def shortToCharLittleEndian(num):    
    rt = shortToCharBigEndian(num)
    rt.reverse()
    return rt
    
def shortToCharBigEndian(num):
    return [chr((num >> 8) & 0xFF), chr(num & 0xFF)]

def intToCharLittleEndian(num):    
    rt = intToCharBigEndian(num)
    rt.reverse()
    return rt
    
def intToCharBigEndian(num):
    return [chr((num >> 24) & 0xFF), chr((num >> 16) & 0xFF), chr((num >> 8) & 0xFF), chr(num & 0xFF)]

def extractIHDRData(elem, data):   
    # <tns:width>0</tns:width>
    # <tns:height>0</tns:height>
    # <tns:bitDepth>0</tns:bitDepth>
    # <tns:colorType>0</tns:colorType>
    # <tns:compressionMethod>0</tns:compressionMethod>
    # <tns:filterMethod>0</tns:filterMethod>
    # <tns:interlaceMethod>0</tns:interlaceMethod>
    width = ET.SubElement(elem, getTag('width'))
    width.text = str(bytesToBigEndianInt(data[0:4], 0xFFFFFFFF))  # data[0:4]
    
    height = ET.SubElement(elem, getTag('height'))
    height.text = str(bytesToBigEndianInt(data[4:8], 0xFFFFFFFF))  # data[4:8]
    
    bitDepth = ET.SubElement(elem, getTag('bitDepth'))    
    bitDepth.text = str(bytesToBigEndianInt(data[8], 0xFF))
        
    colorType = ET.SubElement(elem, getTag('colorType'))
    colorType.text = str(bytesToBigEndianInt(data[9], 0xFF))
    
    compressionMethod = ET.SubElement(elem, getTag('compressionMethod'))
    compressionMethod.text = str(bytesToBigEndianInt(data[10], 0xFF))
    
    filterMethod = ET.SubElement(elem, getTag('filterMethod'))
    filterMethod.text = str(bytesToBigEndianInt(data[11], 0xFF))
    
    interlaceMethod = ET.SubElement(elem, getTag('interlaceMethod'))
    interlaceMethod.text = str(bytesToBigEndianInt(data[12], 0xFF))
    
    
def extractcHRMData(elem, data):    
    # <element name="whiteX" type="unsignedInt"></element>
    # <element name="whiteY" type="unsignedInt"></element>
    # <element name="redX" type="unsignedInt"></element>
    # <element name="redY" type="unsignedInt"></element>
    # <element name="greenX" type="unsignedInt"></element>
    # <element name="greenY" type="unsignedInt"></element>
    # <element name="blueX" type="unsignedInt"></element>
    # <element name="blueY" type="unsignedInt"></element>
    nms = ["whiteX", "whiteY", "redX", "redY", "greenX", "greenY", "blueX", "blueY"]
        
    for i in range(0, len(data), 4):
        el = ET.SubElement(elem, getTag(nms[(i / 4) % 8]))
        if i + 4 > len(data):
            el.text = str(bytesToBigEndianInt(data[i:], 0xFFFFFFFF))  # data[i:]
            break        
        el.text = str(bytesToBigEndianInt(data[i:i + 4], 0xFFFFFFFF))  # data[i:i + 4]
    
            
    
def extractgAMAData(elem, data):
    imageGamma = ET.SubElement(elem, getTag('imageGamma'))
    imageGamma.text = str(bytesToBigEndianInt(data[0], 0xFF))
    
def extractiCCPData(elem, data):
    for i in range(len(data)):
        if data[i] == 0:
            break
    if i > len(data) - 3:  # there was no null separator
        i = len(data) - 3
    
    for p in data:
        print p,
    print ''
    
    profileName = ET.SubElement(elem, getTag('profileName'))
    profileName.text = str(data[:i])
    
    nullSeparator = ET.SubElement(elem, getTag('nullSeparator'))
    nullSeparator.text = str(bytesToBigEndianInt(data[i], 0xFF))
    
    CompressionMethod = ET.SubElement(elem, getTag('CompressionMethod'))
    CompressionMethod.text = str(bytesToBigEndianInt(data[i + 1], 0xFF))
        
    compressedProfile = ET.SubElement(elem, getTag('compressedProfile'))    
    compressedProfile.text = str(data[i + 2:])
    
def extractsRGBData(elem, data):
    renderingIntent = ET.SubElement(elem, getTag('renderingIntent'))
    renderingIntent.text = str(bytesToBigEndianInt(data[0], 0xFF))


def extractsBITData(elem, data):
    sBITChunkDataTypeCol0 = ET.SubElement(elem, getTag('sBITColType0'))
    for i in data:
        sGrayBits = ET.SubElement(sBITChunkDataTypeCol0, getTag('sGrayBits'))
        sGrayBits.text = str(bytesToBigEndianInt(i, 0xFF))
#     ret = bytearray()    
#     # elem.getchildren()[0]
#     tag = elem.getchildren()[0].tag.split('}')[1]
#     elem = elem.getchildren()[0]
#     
#     if tag == 'sBITColType0':
#         sGrayBits = elem.getchildren()[0]
#         ret.extend(chr(txtToInt(sGrayBits.text, 0xFF)))
#         
#     elif tag == 'sBITColType23':
#         sRedBits, sGreenBits, sBlueBits = elem.getchildren()
#         ret.extend(chr(txtToInt(sRedBits.text, 0xFF)))
#         ret.extend(chr(txtToInt(sGreenBits.text, 0xFF)))
#         ret.extend(chr(txtToInt(sBlueBits.text, 0xFF)))
#         
#     elif tag == 'sBITColType4':
#         sGreyBits, sAlphaBits = elem.getchildren()
#         ret.extend(chr(txtToInt(sGreyBits.text, 0xFF)))
#         ret.extend(chr(txtToInt(sAlphaBits.text, 0xFF)))        
#             
#     elif tag == 'sBITColType6':
#         sRedBits, sGreenBits, sBlueBits, sAlphaBits = elem.getchildren()
#         ret.extend(chr(txtToInt(sRedBits.text, 0xFF)))
#         ret.extend(chr(txtToInt(sGreenBits.text, 0xFF)))
#         ret.extend(chr(txtToInt(sBlueBits.text, 0xFF)))
#         ret.extend(chr(txtToInt(sAlphaBits.text, 0xFF)))
#     
#     return ret

def extractPLTEData(elem, data):
    cols = ['red', 'green', 'blue']    
    for i in range(len(data)):
        tp = ET.SubElement(elem, getTag(cols[i % 3]))
        tp.text = str(bytesToBigEndianInt(data[i], 0xFF))
    
def extractbKGDData(elem, data):
    if len(data) == 3:
        bKGDColType26 = ET.SubElement(elem, getTag('bKGDColType26'))
        bKGDRed = ET.SubElement(bKGDColType26, getTag('bKGDRed'))
        bKGDRed.text = str(bytesToBigEndianInt(data[0], 0xFF))
        bKGDGreen = ET.SubElement(bKGDColType26, getTag('bKGDGreen'))
        bKGDGreen.text = str(bytesToBigEndianInt(data[1], 0xFF))
        bKGDBlue = ET.SubElement(bKGDColType26, getTag('bKGDBlue'))
        bKGDBlue.text = str(bytesToBigEndianInt(data[2], 0xFF))
    else:
        bKGDColType04 = ET.SubElement(elem, getTag('bKGDColType04'))
        bKGDGreyscale = ET.SubElement(bKGDColType04, getTag('bKGDGreyscale'))
        bKGDGreyscale.text = str(bytesToBigEndianInt(data[0], 0xFF))

def extracthISTData(elem, data):
    for i in range(0, len(data), 2):
        frequency = ET.SubElement(elem, getTag('frequency'))
        if i + 1 == len(data):
            frequency.text = str(bytesToBigEndianInt(data[i], 0xFF))
            break
        frequency.text = str(bytesToBigEndianInt(data[i:i + 2], 0xFFFF))
        
    
    
def extracttRNSData(elem, data):
    # <tns:tRNSColType0>
    #    <tns:tRNSGrey>0</tns:tRNSGrey>
    # </tns:tRNSColType0>    
    if len(data) == 3:
        tRNSColType2 = ET.SubElement(elem, getTag('tRNSColType2'))
        tRNSRed = ET.SubElement(tRNSColType2, getTag('tRNSRed'))
        tRNSRed.text = str(bytesToBigEndianInt(data[0], 0xFF))
        tRNSGreen = ET.SubElement(tRNSColType2, getTag('tRNSGreen'))
        tRNSGreen.text = str(bytesToBigEndianInt(data[1], 0xFF))
        tRNSBlue = ET.SubElement(tRNSColType2, getTag('tRNSBlue'))
        tRNSBlue.text = str(bytesToBigEndianInt(data[2], 0xFF))
    else:
        tRNSColType0 = ET.SubElement(elem, getTag('tRNSColType0'))
        tRNSGrey = ET.SubElement(tRNSColType0, getTag('tRNSGrey'))
        tRNSGrey.text = str(bytesToBigEndianInt(data[0], 0xFF))
    
def extractpHYsData(elem, data):    
#     <sequence minOccurs="1" maxOccurs="unbounded">
#             <element name="pixelsPerUnitX" type="unsignedInt"></element>
#             <element name="pixelsPerUnitY" type="unsignedInt"></element>
#             <element name="unit">
#                 <annotation>
#                     <documentation>0 - unit is unknown
#                         1 - unit is the metre
#                     </documentation>
#                 </annotation>
#                 <simpleType>
#                     <restriction base="unsignedByte">
#                         <enumeration value="0"></enumeration>
#                         <enumeration value="1"></enumeration>
#                     </restriction>
#                 </simpleType>
#             </element>
#         </sequence>    
    i = 0
    while i < len(data):
        pixelsPerUnitX = ET.SubElement(elem, getTag('pixelsPerUnitX'))
        pixelsPerUnitY = ET.SubElement(elem, getTag('pixelsPerUnitY'))
        unit = ET.SubElement(elem, getTag('unit'))
        
        if i + 4 > len(data):                        
            pixelsPerUnitX.text = str(bytesToBigEndianInt(data[i:], 0xFFFFFFFF))
            break
        pixelsPerUnitX.text = str(bytesToBigEndianInt(data[i:i + 4], 0xFFFFFFFF))
        
        i = i + 4
        if i + 4 > len(data):                        
            pixelsPerUnitY.text = str(bytesToBigEndianInt(data[i:], 0xFFFFFFFF))
            break
        pixelsPerUnitY.text = str(bytesToBigEndianInt(data[i:i + 4], 0xFFFFFFFF))
        
        i = i + 4
        if i >= len(data):
            break        
        unit.text = str(bytesToBigEndianInt(data[i], 0xFF))
    
    
def extractsPLTData(elem, data):
    # <tns:paletteName>tns:paletteName</tns:paletteName>
    # <tns:nullSeparator>0</tns:nullSeparator>
    # <tns:sampleDepth>8</tns:sampleDepth>
    for i in range(len(data)):
        if data[i] == 0:
            break
    if i > len(data) - 2:  # there was no null separator
        i = len(data) - 2
    
    paletteName = ET.SubElement(elem, getTag('paletteName'))
    paletteName.text = str(data[0:i])
    
    nullSeparator = ET.SubElement(elem, getTag('nullSeparator'))
    nullSeparator.text = str(bytesToBigEndianInt(data[i], 0xFF))
    
    sampleDepth = ET.SubElement(elem, getTag('sampleDepth'))
    sampleDepth.text = str(bytesToBigEndianInt(data[i + 1], 0xFF))
    
    lnData = len(data)
    j = i + 2
    while j < lnData:
        if data[i + 1] == 16:
            # <element name="redDpp16" type="unsignedShort"></element>
            # <element name="greenDpp16" type="unsignedShort"></element>
            # <element name="blueDpp16" type="unsignedShort"></element>
            # <element name="alphaDpp16" type="unsignedShort"></element>
            # <element name="frequencyDpp16" type="unsignedShort"></element>
            redDpp16 = ET.SubElement(elem, getTag('redDpp16'))
            greenDpp16 = ET.SubElement(elem, getTag('greenDpp16'))
            blueDpp16 = ET.SubElement(elem, getTag('blueDpp16'))
            alphaDpp16 = ET.SubElement(elem, getTag('alphaDpp16'))
            frequencyDpp16 = ET.SubElement(elem, getTag('frequencyDpp16'))
            
            if lnData < j + 1:
                redDpp16.text = str(bytesToBigEndianInt(data[j], 0xFF)) 
                break
            redDpp16.text = str(bytesToBigEndianInt(data[j:j + 2], 0xFFFF))
            
            j = j + 2
            if lnData < j + 1: 
                if lnData == j:
                    greenDpp16.text = str(bytesToBigEndianInt(data[j], 0xFF))
                break
            greenDpp16.text = str(bytesToBigEndianInt(data[j:j + 2], 0xFFFF))
            
            j = j + 2            
            if lnData < j + 1: 
                if lnData == j:
                    greenDpp16.text = str(bytesToBigEndianInt(data[j], 0xFF))
                break
            blueDpp16.text = str(bytesToBigEndianInt(data[j:j + 2], 0xFFFF))
            
            j = j + 2            
            if lnData < j + 1: 
                if lnData == j:
                    greenDpp16.text = str(bytesToBigEndianInt(data[j], 0xFF))
                break            
            alphaDpp16.text = str(bytesToBigEndianInt(data[j:j + 2], 0xFFFF))
            
            j = j + 2            
            if lnData < j + 1: 
                if lnData == j:
                    greenDpp16.text = str(bytesToBigEndianInt(data[j], 0xFF))
                break
            frequencyDpp16.text = str(bytesToBigEndianInt(data[j:j + 2], 0xFFFF))
            j = j + 2
            
        else:            
            # <element name="redDpp8" type="byte"></element>
            # <element name="greenDpp8" type="byte"></element>
            # <element name="blueDpp8" type="byte"></element>
            # <element name="alphaDpp8" type="byte"></element>
            # <element name="frequencyDpp8" type="unsignedShort"></element>
            redDpp8 = ET.SubElement(elem, getTag('redDpp8'))
            greenDpp8 = ET.SubElement(elem, getTag('greenDpp8'))
            blueDpp8 = ET.SubElement(elem, getTag('blueDpp8'))
            alphaDpp8 = ET.SubElement(elem, getTag('alphaDpp8'))
            frequencyDpp8 = ET.SubElement(elem, getTag('frequencyDpp8'))
            
            redDpp8.text = str(bytesToBigEndianInt(data[j], 0xFF))
            
            j = j + 1            
            if lnData <= j: break
            greenDpp8.text = str(bytesToBigEndianInt(data[j], 0xFF))
            
            j = j + 1            
            if lnData <= j: break
            blueDpp8.text = str(bytesToBigEndianInt(data[j], 0xFF))
            
            j = j + 1            
            if lnData <= j: break
            alphaDpp8.text = str(bytesToBigEndianInt(data[j], 0xFF))
            
            j = j + 1            
            if lnData <= j + 1: 
                if lnData == j:
                    greenDpp16.text = str(bytesToBigEndianInt(data[j], 0xFF))
                break
            frequencyDpp8.text = str(bytesToBigEndianInt(data[j:j + 2], 0xFFFF))
            j = j + 2
    
    

def extracttIMEData(elem, data):
    # <tns:year>0</tns:year>
    # <tns:month>1</tns:month>
    # <tns:day>1</tns:day>
    # <tns:hour>0</tns:hour>
    # <tns:minute>0</tns:minute>
    # <tns:second>0</tns:second>
    year = ET.SubElement(elem, getTag('year'))
    year.text = str(bytesToBigEndianInt(data[0:2], 0xFFFF))
    
    month = ET.SubElement(elem, getTag('month'))
    month.text = str(bytesToBigEndianInt(data[2], 0xFF))
    
    day = ET.SubElement(elem, getTag('day'))
    day.text = str(bytesToBigEndianInt(data[3], 0xFF))
    
    hour = ET.SubElement(elem, getTag('hour'))
    hour.text = str(bytesToBigEndianInt(data[4], 0xFF))
    
    minute = ET.SubElement(elem, getTag('minute'))
    minute.text = str(bytesToBigEndianInt(data[5], 0xFF))
    
    second = ET.SubElement(elem, getTag('second'))
    second.text = str(bytesToBigEndianInt(data[6], 0xFF))
    
def extractiTXtData(elem, data):
    # <tns:keyword>Title</tns:keyword>
    # <tns:nullSeparator>0</tns:nullSeparator>
    # <tns:compressionFlag>0</tns:compressionFlag>
    # <tns:compressionMethod>0</tns:compressionMethod>
    # <tns:langTag>tns:langTag</tns:langTag>
    # <tns:nullSeparator>0</tns:nullSeparator>
    # <tns:translatedKeyword>tns:translatedKeyword</tns:translatedKeyword>
    # <tns:nullSeparator>0</tns:nullSeparator>
    # <tns:text>tns:text</tns:text>
    for i in range(len(data)):
        if data[i] == 0:
            break
    if i > len(data) - 8:  # there was no null separator
        i = len(data) - 8
    
    keyword = ET.SubElement(elem, getTag('keyword'))
    keyword.text = str(data[:i])
    
    nullSeparator = ET.SubElement(elem, getTag('nullSeparator'))
    nullSeparator.text = str(bytesToBigEndianInt(data[i], 0xFF))
        
    compressionFlag = ET.SubElement(elem, getTag('compressionFlag'))
    compressionFlag.text = str(bytesToBigEndianInt(data[i + 1], 0xFF))
    
    compressionMethod = ET.SubElement(elem, getTag('compressionMethod'))
    compressionMethod.text = str(bytesToBigEndianInt(data[i + 2], 0xFF))
    
    for j in range(i + 3, len(data), 1):
        if data[j] == 0:
            break
    if j > len(data) - 4:
        j = len(data) - 4
    
    langTag = ET.SubElement(elem, getTag('langTag'))
    langTag.text = str(data[i + 3:j])
    
    nullSeparator1 = ET.SubElement(elem, getTag('nullSeparator'))
    nullSeparator1.text = str(bytesToBigEndianInt(data[j], 0xFF))
    
    
    for k in range(j + 1, len(data), 1):
        if data[k] == 0:
            break
    if k > len(data) - 2:
        k = len(data) - 2
    
    translatedKeyword = ET.SubElement(elem, getTag('translatedKeyword'))
    translatedKeyword.text = str(data[j + 1:k])
    
    nullSeparator2 = ET.SubElement(elem, getTag('nullSeparator'))
    nullSeparator2.text = str(bytesToBigEndianInt(data[k], 0xFF))    
    
    text = ET.SubElement(elem, getTag('text'))
    text.text = str(data[k + 1:])
    
    
def extracttEXtData(elem, data):
    # <tns:keyword>Title</tns:keyword>
    # <tns:nullSeparator>0</tns:nullSeparator>
    # <tns:text>tns:text</tns:text>
    for i in range(len(data)):
        if data[i] == 0:
            break
    if i > len(data) - 2:  # there was no null separator
        i = len(data) - 2
    
    keyword = ET.SubElement(elem, getTag('keyword'))
    keyword.text = str(data[:i])
    
    nullSeparator = ET.SubElement(elem, getTag('nullSeparator'))
    nullSeparator.text = str(bytesToBigEndianInt(data[i], 0xFF))    
    
    text = ET.SubElement(elem, getTag('text'))
    text.text = str(data[i + 1:])

def extractzTXtData(elem, data):
    # <tns:keyword>Title</tns:keyword>
    # <tns:nullSeparator>0</tns:nullSeparator>
    # <tns:compressionMethod>0</tns:compressionMethod>
    # <tns:compressedText>tns:compressedText</tns:compressedText>
    for i in range(len(data)):
        if data[i] == 0:
            break
    if i > len(data) - 3:  # there was no null separator
        i = len(data) - 3
    
    keyword = ET.SubElement(elem, getTag('keyword'))
    keyword.text = str(data[:i])
    
    nullSeparator = ET.SubElement(elem, getTag('nullSeparator'))
    nullSeparator.text = str(bytesToBigEndianInt(data[i], 0xFF))
    
    compressionMethod = ET.SubElement(elem, getTag('compressionMethod'))
    compressionMethod.text = str(bytesToBigEndianInt(data[i + 1], 0xFF))
    
    compressedText = ET.SubElement(elem, getTag('compressedText'))
    compressedText.text = str(data[i + 2:])

def extractIEndData(elem, data):
    pass
    
def extractChunkData(elem, data):
    elem.text = str(data)
    
def extractIDATData(elem, data):    
    elem.text = zlib.decompress(str(data))

def extractSignature(pngData, root):    
    sg = pngData[0:8]
    
    signature = ET.SubElement(root, getTag('Signature'))
    signature.text = hex(bytesToBigEndianInt(sg, 0xFFFFFFFFFFFFFFFF))

def getCRC(data):
    # chars = getCharsFromInt32(self.size)
    crc = 0
    # for i in range(4):
    #    crc = binascii.crc32(chars[i], crc)
    # crc = binascii.crc32(self.chunk_type, crc)
    crc = binascii.crc32(data, crc)
    # crc = zlib.crc32(str(self.chunk_type), crc)
    # crc = zlib.crc32(str(self.data), crc)
    # for i in self.data:
    #    crc = binascii.crc32(chr(i), crc)        
    return crc & 0xffffffff

def extractTopLevel(topElement, chunk):
    tag = chunk.chunk_type
    
    if chunk.chunk_type in ['IHDR', 'cHRM', 'gAMA', 'iCCP', 'sRGB', 'sBIT', 'PLTE', 'bKGD', 'hIST', 'tRNS', 'pHYs', 'sPLT', 'tIME', 'iTXt', 'tEXt', 'zTXt', 'IEND', 'IDAT']:
        chunkType = chunk.chunk_type
    else:
        chunkType = 'Chunk' 
    
    chunkElem = ET.SubElement(topElement, getTag(chunkType))
    
    sizeElem = ET.SubElement(chunkElem, getTag('size'))
    sizeElem.text = str(chunk.size)
    
    chunkTypeElem = ET.SubElement(chunkElem, getTag('type'))
    chunkTypeElem.text = str(chunk.chunk_type)
    
    if chunk.chunk_type != 'IEND':
        chunkDataElem = ET.SubElement(chunkElem, getTag('data'))    
    
    chunkCrcElem = ET.SubElement(chunkElem, getTag('crc'))
    chunkCrcElem.text = str(chunk.crc)
    
    if tag == 'IHDR':
        extractIHDRData(chunkDataElem, chunk.data)
    elif tag == 'cHRM':
        extractcHRMData(chunkDataElem, chunk.data)
    elif tag == 'gAMA':
        extractgAMAData(chunkDataElem, chunk.data)
    elif tag == 'iCCP':
        extractiCCPData(chunkDataElem, chunk.data)
    elif tag == 'sRGB':
        extractsRGBData(chunkDataElem, chunk.data)
    elif tag == 'sBIT':
        extractsBITData(chunkDataElem, chunk.data)
    elif tag == 'PLTE':
        extractPLTEData(chunkDataElem, chunk.data)
    elif tag == 'bKGD':
        extractbKGDData(chunkDataElem, chunk.data)
    elif tag == 'hIST':
        extracthISTData(chunkDataElem, chunk.data)
    elif tag == 'tRNS':
        extracttRNSData(chunkDataElem, chunk.data)
    elif tag == 'pHYs':
        extractpHYsData(chunkDataElem, chunk.data)
    elif tag == 'sPLT':
        extractsPLTData(chunkDataElem, chunk.data)
    elif tag == 'tIME':
        extracttIMEData(chunkDataElem, chunk.data)
    elif tag == 'iTXt':
        extractiTXtData(chunkDataElem, chunk.data)
    elif tag == 'tEXt':
        extracttEXtData(chunkDataElem, chunk.data)
    elif tag == 'zTXt':
        extractzTXtData(chunkDataElem, chunk.data)
    elif tag == 'IEND':
        return
    elif tag == 'Chunk':
        extractChunkData(chunkDataElem, chunk.data)
    elif tag == 'IDAT':
        extractIDATData(chunkDataElem, chunk.data)
    

def extractChunks(pngData):
    chunks = []    
    pngChunks = pngData[8:]
    index = 0
    while index < len(pngChunks):
        chunk = Chunk()
        chunk.extractFromData(pngChunks, index)
        chunks.append(chunk)
        index = index + chunk.size + 12
    
    return chunks
    

def getTag(tag):
    if XML_NAMESPACE:
        return str(XML_NAMESPACE + tag)
    return tag

XML_NAMESPACE = '{http://www.example.org/PNGSchema}'
def pngToxml(pathToPNG, pathToXML):
    pngFile = io.open(pathToPNG, mode='rb')
    pngData = bytearray(pngFile.read())
    pngFile.close()
    
    ET.register_namespace('tns', XML_NAMESPACE)  # some name
    
    # build a tree structure
    root = ET.Element(getTag('PNG'))
    # root.set('xmlns:xsi', 'http://www.w3.org/2001/XMLSchema-instance')
    
    extractSignature(pngData, root)
    
    chunks = extractChunks(pngData)
    
    chunksElement = ET.SubElement(root, getTag('Chunks'))
    for chunk in chunks:
        extractTopLevel(chunksElement, chunk)
    
    #print root.getchildren()[1].getchildren()[15]
    #print ET.tostring(root)
    ET.ElementTree(root).write(pathToXML)
    # with io.open(pathToXML, mode='wb') as fw:
    #    fw.write(ET.tostring(root, 'UTF8'))
    
# xml2png('/home/gmaisuradze/Desktop/EclipseWorkspace/XMLExamples/MySchemas/PNGSchema.xml', '/home/gmaisuradze/Desktop/EclipseWorkspace/XMLExamples/MySchemas/PNGSchema.xsd')

pngToxml('/home/gmaisuradze/Desktop/result.png', '/home/gmaisuradze/Desktop/testPNG2.xml')

