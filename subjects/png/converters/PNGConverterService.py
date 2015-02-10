import zmq
import msgpack
from os import remove
from xml2pngmulti import convert


def main():
    context = zmq.Context()
    receiver = context.socket(zmq.PULL)
    receiver.connect("tcp://127.0.0.1:5556")
    sender = context.socket(zmq.PUSH)
    sender.connect("tcp://127.0.0.1:5560")
    unpck = msgpack.Unpacker()
    pck = msgpack.Packer(autoreset=False)
    
    input_file = None
    converted = None
    while True:
#         print 'Waiting for inputs...'
        unpck.feed(receiver.recv())
        num = unpck.next()
        input_file = unpck.next()
#         print 'Received input %d >-- %s' % (num, input_file)
        try:
            remove(converted)
        except:
            print 'Could not remove', converted
#         print 'Removed old file', converted
        converted = convert(input_file)
        pck.pack(num)
        pck.pack(converted)
        sender.send(pck.bytes())
        pck.reset()
#         print 'Sent converted files'

if __name__ == '__main__':
    main()
