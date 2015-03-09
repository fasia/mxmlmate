from __future__ import print_function

import msgpack
from os import remove
from sys import argv
import zmq

from xml2pcapmulti import convert


def main(endpoint):
    context = zmq.Context()
    socket = context.socket(zmq.DEALER)
    print('Converter connecting to "%s".' % endpoint)
    socket.connect(endpoint)
    
    unpck = msgpack.Unpacker()
    pck = msgpack.Packer(autoreset=False)
    converted = {}
    socket.send(b'RDY')
    while True:
        # print('Waiting for inputs...')
        unpck.feed(socket.recv())
        num = unpck.next()
        input_file = unpck.next()
        # print('Received input %d >-- %s' % (num, input_file))
        try:
            remove(converted.get(num))
        except:
            print('Could not remove %s' % converted.get(num))
        # print('Removed old file %s' % converted)
        try:
            result = convert(input_file)
        except:
            # record anyway to remove the empty file next time
            result = input_file.replace('.xml', '.pcap')  
            print('Could not convert %s' % input_file)
        converted[num] = result
        pck.pack(num)
        pck.pack(result)
        socket.send(pck.bytes())
        pck.reset()
        # print('Sent converted files')

if __name__ == '__main__':
    if len(argv) < 2:
        endpointAddress = 'ipc://converters.pipe'
        print('WARNING! No endpoint given! Falling back to default "%s".' % endpointAddress)
    else:
        endpointAddress = argv[1]
    main(endpointAddress)