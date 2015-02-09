import zmq
from xml2pngmulti import convert
from os import remove


def main():
    context = zmq.Context()
    receiver = context.socket(zmq.PULL)
    receiver.connect("tcp://127.0.0.1:5556")
    sender = context.socket(zmq.PUSH)
    sender.bind("tcp://127.0.0.1:5560")
    # cache functions for speed
    rec = receiver.recv_string
    snd = sender.send_string
    inputs = []
    converted = []
    while True:
#         print 'Waiting for inputs...'
        inputs[:] = rec().split(':')
#         print 'Received %d inputs' % len(inputs)
        map(remove, converted)
#         print 'Removed old files', converted
        converted[:] = convert(inputs)
#         print 'Produced %d outputs' % len(converted)
        snd(unicode(':'.join(converted)))
#         print 'Sent converted files'

if __name__ == '__main__':
    main()
