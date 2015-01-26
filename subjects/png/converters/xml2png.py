#!/usr/bin/python
import xml.etree.ElementTree as ET
import io
import binascii
import zlib
import sys

def hexStringToByteArray(hx):
    res = bytearray()
    if not hx: return res
    for i in range(0, len(hx), 2):
        res.append(int(hx[i: i + 2], 16))
    return res

def txtToInt(txt, mod=0xFFFFFFFF):
    if not txt: 
        return 0
    num = 0 
    ord0 = ord('0')
    for i in txt:
        num = num * 10
        num = num + ord(i) - ord0
    # num = abs(num)    
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

def parseIHDRData(elem):
    ret = bytearray()
    width, height, bitDepth, colorType, compressionMethod, filterMethod, interlaceMethod = elem.getchildren()    
    
    ret.extend(intToCharBigEndian(txtToInt(width.text)))
    ret.extend(intToCharBigEndian(txtToInt(height.text)))        
    ret.extend(chr(txtToInt(bitDepth.text, 0xFF)))
    ret.extend(chr(txtToInt(colorType.text, 0xFF)))        
    ret.extend(chr(txtToInt(compressionMethod.text, 0xFF)))
    ret.extend(chr(txtToInt(filterMethod.text, 0xFF)))
    ret.extend(chr(txtToInt(interlaceMethod.text, 0xFF)))
    return ret
    
    
def parsecHRMData(elem):
    ret = bytearray()
    whiteX, whiteY, redX, redY, greenX, greenY, blueX, blueY = elem.getchildren()
    
    ret.extend(intToCharBigEndian(txtToInt(whiteX.text)))
    ret.extend(intToCharBigEndian(txtToInt(whiteY.text)))    
    ret.extend(intToCharBigEndian(txtToInt(redX.text)))
    ret.extend(intToCharBigEndian(txtToInt(redY.text)))
    ret.extend(intToCharBigEndian(txtToInt(greenX.text)))
    ret.extend(intToCharBigEndian(txtToInt(greenY.text)))
    ret.extend(intToCharBigEndian(txtToInt(blueX.text)))
    ret.extend(intToCharBigEndian(txtToInt(blueY.text)))
    return ret
    
def parsegAMAData(elem):
    ret = bytearray()
    imageGama = elem.getchildren()[0]     
    ret.extend(intToCharBigEndian(txtToInt(imageGama.text)))
    return ret
    
def parseiCCPData(elem):
    ret = bytearray()
    profileName, nullSeparator, compressionMethod, compressedProfile = elem.getchildren()
    ret.extend(profileName.text)
    ret.extend(chr(txtToInt(nullSeparator.text, 0xFF)))
    ret.extend(chr(txtToInt(compressionMethod.text, 0xFF)))
    if compressedProfile.text:
        ret.extend(hexStringToByteArray(compressedProfile.text))
    return ret
    
def parsesRGBData(elem):
    ret = bytearray()    
    renderingIntent = elem.getchildren()[0]    
    ret.extend(chr(txtToInt(renderingIntent.text, 0xFF)))
    return ret


def parsesBITData(elem):
    ret = bytearray()    
    # elem.getchildren()[0]
    tag = elem.getchildren()[0].tag.split('}')[1]
    elem = elem.getchildren()[0]
    
    if tag == 'sBITColType0':
        sGrayBits = elem.getchildren()[0]
        ret.extend(chr(txtToInt(sGrayBits.text, 0xFF)))
        
    elif tag == 'sBITColType23':
        sRedBits, sGreenBits, sBlueBits = elem.getchildren()
        ret.extend(chr(txtToInt(sRedBits.text, 0xFF)))
        ret.extend(chr(txtToInt(sGreenBits.text, 0xFF)))
        ret.extend(chr(txtToInt(sBlueBits.text, 0xFF)))
        
    elif tag == 'sBITColType4':
        sGreyBits, sAlphaBits = elem.getchildren()
        ret.extend(chr(txtToInt(sGreyBits.text, 0xFF)))
        ret.extend(chr(txtToInt(sAlphaBits.text, 0xFF)))        
            
    elif tag == 'sBITColType6':
        sRedBits, sGreenBits, sBlueBits, sAlphaBits = elem.getchildren()
        ret.extend(chr(txtToInt(sRedBits.text, 0xFF)))
        ret.extend(chr(txtToInt(sGreenBits.text, 0xFF)))
        ret.extend(chr(txtToInt(sBlueBits.text, 0xFF)))
        ret.extend(chr(txtToInt(sAlphaBits.text, 0xFF)))
    
    return ret

def parsePLTEData(elem):
    ret = bytearray()    
    elemChildren = elem.getchildren()
    for i in elemChildren:
        ret.extend(chr(txtToInt(i.text, 0xFF)))
    return ret
    
def parsebKGDData(elem):
    ret = bytearray()    
    # elem.getchildren()[0]
    tag = elem.getchildren()[0].tag.split('}')[1]
    elem = elem.getchildren()[0]
    
    if tag == 'bKGDColType04':
        bKGDGreyscale = elem.getchildren()[0]
        ret.extend(chr(txtToInt(bKGDGreyscale.text, 0xFF)))
        
    elif tag == 'bKGDColType26':
        bKGDRed, bKGDGreen, bKGDBlue = elem.getchildren()
        ret.extend(chr(txtToInt(bKGDRed.text, 0xFF)))
        ret.extend(chr(txtToInt(bKGDGreen.text, 0xFF)))
        ret.extend(chr(txtToInt(bKGDBlue.text, 0xFF)))
        
    elif tag == 'bKGDColType3':
        bKGDPaletteIndex = elem.getchildren()[0]
        ret.extend(chr(txtToInt(bKGDPaletteIndex.text, 0xFF)))
    
    return ret

def parsehISTData(elem):
    ret = bytearray()
    frequency = elem.getchildren()[0]
    ret.extend(shortToCharBigEndian(txtToInt(frequency.text, 0xFFFF)))
    return ret
    
def parsetRNSData(elem):
    ret = bytearray()    
    # elem.getchildren()[0]
    tag = elem.getchildren()[0].tag.split('}')[1]
    elem = elem.getchildren()[0]
    
    if tag == 'tRNSColType0':
        tRNSGrey = elem.getchildren()[0]
        ret.extend(chr(txtToInt(tRNSGrey.text, 0xFF)))
        
    elif tag == 'tRNSColType2':
        tRNSRed, tRNSGreen, tRNSBlue = elem.getchildren()
        ret.extend(chr(txtToInt(tRNSRed.text, 0xFF)))
        ret.extend(chr(txtToInt(tRNSGreen.text, 0xFF)))
        ret.extend(chr(txtToInt(tRNSBlue.text, 0xFF)))
        
    elif tag == 'tRNSColType3':
        bKGDPaletteIndex = elem.getchildren()[0]
        ret.extend(chr(txtToInt(bKGDPaletteIndex.text, 0xFF)))
    
    return ret
    
def parsepHYsData(elem):
    ret = bytearray()
    elemChildren = elem.getchildren()
    i = 0    
    while i < len(elemChildren):
        ret.extend(intToCharBigEndian(txtToInt(elemChildren[i].text)))
        
        if i + 1 >= len(elemChildren): break
        ret.extend(intToCharBigEndian(txtToInt(elemChildren[i + 1].text)))
        
        if i + 2 >= len(elemChildren): break
        ret.extend(chr(txtToInt(elemChildren[i + 2].text, 0xFF)))
        
        i = i + 3
    return ret
    
def parsesPLTData(elem):
    ret = bytearray()
    
    elemChildren = elem.getchildren()
    paletteName = elemChildren[0]
    nullSeparator = elemChildren[1]
    sampleDepth = elemChildren[2]
    
    ret.extend(paletteName.text)
    ret.extend(chr(txtToInt(nullSeparator.text, 0xFF)))    
    ret.extend(chr(txtToInt(sampleDepth.text, 0xFF)))
    
    sampleDepthNumber = txtToInt(sampleDepth.text, 0xFF)   
    
    i = 3
    while i < len(elemChildren):
        if sampleDepthNumber == 16:
            redDPP = elemChildren[i]
            ret.extend(shortToCharBigEndian(txtToInt(redDPP.text, 0xFFFF)))
            
            i = i + 2
            if i >= len(elemChildren): break            
            greenDPP = elemChildren[i]
            ret.extend(shortToCharBigEndian(txtToInt(greenDPP.text, 0xFFFF)))
            
            i = i + 2
            if i >= len(elemChildren): break            
            blueDPP = elemChildren[i]
            ret.extend(shortToCharBigEndian(txtToInt(blueDPP.text, 0xFFFF)))
            
            i = i + 2
            if i >= len(elemChildren): break            
            alphaDPP = elemChildren[i]
            ret.extend(shortToCharBigEndian(txtToInt(alphaDPP.text, 0xFFFF)))
            
            i = i + 2
            if i >= len(elemChildren): break            
            frequencyDPP = elemChildren[i]
            ret.extend(shortToCharBigEndian(txtToInt(frequencyDPP.text, 0xFFFF)))            
        else:
            redDPP = elemChildren[i]
            ret.extend(chr(txtToInt(redDPP.text, 0xFF)))
            
            i = i + 1
            if i >= len(elemChildren): break            
            greenDPP = elemChildren[i]
            ret.extend(chr(txtToInt(greenDPP.text, 0xFF)))
            
            i = i + 1
            if i >= len(elemChildren): break            
            blueDPP = elemChildren[i]
            ret.extend(chr(txtToInt(blueDPP.text, 0xFF)))
            
            i = i + 1
            if i >= len(elemChildren): break            
            alphaDPP = elemChildren[i]
            ret.extend(chr(txtToInt(alphaDPP.text, 0xFF)))
            
            i = i + 1
            if i >= len(elemChildren): break            
            frequencyDPP = elemChildren[i]
            ret.extend(shortToCharBigEndian(txtToInt(frequencyDPP.text, 0xFFFF)))
        i = i + 2
        
    return ret
    
    

def parsetIMEData(elem):
    ret = bytearray()
    year, month, day, hour, minute, second = elem.getchildren()
    ret.extend(shortToCharBigEndian(txtToInt(year.text, 0xFFFF)))
    ret.extend(chr(txtToInt(month.text, 0xFF)))
    ret.extend(chr(txtToInt(day.text, 0xFF)))
    ret.extend(chr(txtToInt(hour.text, 0xFF)))
    ret.extend(chr(txtToInt(minute.text, 0xFF)))
    ret.extend(chr(txtToInt(second.text, 0xFF)))    
    
    return ret
    
def parseiTXtData(elem):
    ret = bytearray()
    keyword, nullSeparator1, compressionFlag, compressionMethod, langTag, nullSeparator2, translatedKeyword, nullSeparator3, text = elem.getchildren()
    
    ret.extend(keyword.text)
    ret.extend(chr(txtToInt(nullSeparator1.text, 0xFF)))
    ret.extend(chr(txtToInt(compressionFlag.text, 0xFF)))
    ret.extend(chr(txtToInt(compressionMethod.text, 0xFF)))
    if langTag.text:    
        ret.extend(langTag.text)
    ret.extend(chr(txtToInt(nullSeparator2.text, 0xFF)))
    if translatedKeyword.text:
        ret.extend(translatedKeyword.text)
    ret.extend(chr(txtToInt(nullSeparator3.text, 0xFF)))
    if text.text:        
        ret.extend(hexStringToByteArray(text.text))
    
    return ret
    
    
def parsetEXtData(elem):
    ret = bytearray()
    keyword, nullSeparator, text = elem.getchildren()
    
    ret.extend(keyword.text)
    ret.extend(chr(txtToInt(nullSeparator.text, 0xFF)))
    if text.text:
        ret.extend(text.text)    
    
    return ret

def parsezTXtData(elem):
    ret = bytearray()
    keyword, nullSeparator, compressionMethod, compressedText = elem.getchildren()
    
    ret.extend(keyword.text)
    ret.extend(chr(txtToInt(nullSeparator.text, 0xFF)))    
    ret.extend(chr(txtToInt(compressionMethod.text, 0xFF)))
    if compressedText.text:
        ret.extend(hexStringToByteArray(compressedText.text))
    
    return ret

def parseIEndData(elem):
    pass
    
def parseChunkData(elem):
    ret = bytearray()
    if elem and elem.text:
        ret.extend(hexStringToByteArray(elem.text.strip()))
    
    return ret
    
def parseIDATData(elem):
    ret = bytearray()
        
    compressedText = zlib.compress(str(hexStringToByteArray(elem.text)))
    ret.extend(compressedText)
    # ret.extend(elem.text)
    
    return ret

def parseSignature(elem):    
    ret = bytearray()
    
    for i in range(0, len(elem.text), 2):        
        ret.append(int(elem.text[i:i + 2], 16)) 
    
    return ret

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

def parseTopLevel(topLevelElement):
    childs = topLevelElement.getchildren()
    chunk_size = childs[0].text.strip()    
    chunk_type = childs[1].text.strip()
    if len(childs) == 4:
        chunk_data_elem = childs[2]
        chunk_crc = childs[3].text.strip()
    else:
        chunk_data_elem = None
        chunk_crc = childs[2].text.strip()
    
    retData = bytearray()
    retData.extend(intToCharBigEndian(txtToInt(chunk_size)))
    retData.extend(chunk_type)
    
    tag = topLevelElement.tag.split('}')[1]
    
    if tag == 'IHDR':        
        retData.extend(parseIHDRData(chunk_data_elem))
    elif tag == 'cHRM':
        retData.extend(parsecHRMData(chunk_data_elem))
    elif tag == 'gAMA':
        retData.extend(parsegAMAData(chunk_data_elem))
    elif tag == 'iCCP':
        retData.extend(parseiCCPData(chunk_data_elem))
    elif tag == 'sRGB':
        retData.extend(parsesRGBData(chunk_data_elem))
    elif tag == 'sBIT':
        retData.extend(parsesBITData(chunk_data_elem))
    elif tag == 'PLTE':
        retData.extend(parsePLTEData(chunk_data_elem))
    elif tag == 'bKGD':
        retData.extend(parsebKGDData(chunk_data_elem))
    elif tag == 'hIST':
        retData.extend(parsehISTData(chunk_data_elem))
    elif tag == 'tRNS':
        retData.extend(parsetRNSData(chunk_data_elem))
    elif tag == 'pHYs':
        retData.extend(parsepHYsData(chunk_data_elem))
    elif tag == 'sPLT':
        retData.extend(parsesPLTData(chunk_data_elem))
    elif tag == 'tIME':
        retData.extend(parsetIMEData(chunk_data_elem))
    elif tag == 'iTXt':
        retData.extend(parseiTXtData(chunk_data_elem))
    elif tag == 'tEXt':
        retData.extend(parsetEXtData(chunk_data_elem))
    elif tag == 'zTXt':
        retData.extend(parsezTXtData(chunk_data_elem))
    elif tag == 'IEND':
        pass
    elif tag == 'Chunk':
        retData.extend(parseChunkData(chunk_data_elem))
    elif tag == 'IDAT':
        retData.extend(parseIDATData(chunk_data_elem))
    
    retData[0:4] = intToCharBigEndian(len(retData) - 8)  # setting correct length
    
    # retData.extend(intToCharBigEndian(txtToInt(chunk_crc)))
    retData.extend(intToCharBigEndian(getCRC(retData[4:len(retData)])))
    return retData

def xml2png(pathToXML, pathToPNG):
    # f = codecs.open(pathToXML, 'r', 'utf-8')
            
    tree = ET.parse(pathToXML)
    
    pngData = bytearray()
    # with io.open(pathToXML, mode='rb') as fp:
    #    tree = ET.parse(fp)    
    #    for i in tree.iter():
    #        i.text = unicode(i.text).encode('utf8')
   
    signature = tree.find('.//{http://www.example.org/PNGSchema}Signature')
    pngData.extend(parseSignature(signature))
    chunks = tree.find('.//{http://www.example.org/PNGSchema}Chunks')
    for chunk in chunks:
        pngData.extend(parseTopLevel(chunk))
                        
    with io.open(pathToPNG, mode='wb') as fw:
        fw.write(pngData)    
    
# xml2png('/home/gmaisuradze/Desktop/EclipseWorkspace/XMLExamples/MySchemas/PNGSchema.xml', '/home/gmaisuradze/Desktop/EclipseWorkspace/XMLExamples/MySchemas/PNGSchema.xsd')
# xml2png('/home/gmaisuradze/Desktop/testPNG1.xml', 'result.png')
# xml2png('/home/gmaisuradze/Desktop/ftp0n2c08.xml', '/home/gmaisuradze/Desktop/ftp0n2c08my.png')

if __name__ == '__main__':
    # xml2png(sys.argv[1], sys.argv[2])
    xml2png('/home/gmaisuradze/Desktop/EclipseWorkspace/xmlmate/xmlmate/filename.xml', 'filename.png')  # sys.argv[2]
