from __future__ import print_function

import subprocess
from sys import argv
import zmq


def main(endpoint, identity, command):
    context = zmq.Context()

    while True:
        print('Long live the worker "%s"' % identity)
        subprocess.call(command + [endpoint, identity])
        print('The worker "%s" died' % identity)
        socket = context.socket(zmq.DEALER)
        socket.setsockopt(zmq.IDENTITY, identity)
        socket.connect(endpoint)
        socket.send(b'DEAD')
        socket.close()

if __name__ == '__main__':
    if len(argv) < 4:
        print('Usage: Lifeguard <endpoint> <identity> <command>+')
        exit(1)
    main(argv[1], argv[2], argv[3:])
