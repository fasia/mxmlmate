import zmq
import msgpack
from os import remove
from xml2pcapmulti import convert


def main():
    context = zmq.Context()
    receiver = context.socket(zmq.PULL)
    receiver.connect("tcp://127.0.0.1:5556")
    sender = context.socket(zmq.PUSH)
    sender.connect("tcp://127.0.0.1:5560")
    unpck = msgpack.Unpacker()
    pck = msgpack.Packer(autoreset=False)

    converted = {}
    while True:
        # print 'Waiting for inputs...'
        unpck.feed(receiver.recv())
        num = unpck.next()
        input_file = unpck.next()
        # print 'Received input %d >-- %s' % (num, input_file)
        try:
            remove(converted.get(num))
        except:
            print 'Could not remove', converted.get(num)
        # print 'Removed old file', converted
        try:
            result = convert(input_file)
        except Exception, e:
            # record anyway to remove the empty file next time
            result = input_file.replace('.xml', '.pcap')  
            print 'Could not convert', input_file, 'because of', e
        converted[num] = result
        pck.pack(num)
        pck.pack(result)
        sender.send(pck.bytes())
        pck.reset()
        # print 'Sent converted files'

if __name__ == '__main__':
    main()