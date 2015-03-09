from __future__ import print_function
from sys import argv
import msgpack

import zmq


def main(front, forw, back):
    context = zmq.Context()
    frontend = context.socket(zmq.PULL)
    print('LoadBalancer binding frontend to "%s"' % front)
    frontend.bind(front)
    forward = context.socket(zmq.PUSH)
    print('LoadBalancer connecting forward to "%s"' % forw)
    forward.connect(forw)
    backend = context.socket(zmq.ROUTER)
    print('LoadBalancer binding backend to "%s"' % back)
    backend.bind(back)

    unpk = msgpack.Unpacker()
    pck = msgpack.Packer(autoreset=False)
    workers = []
    awaited = dict()
    poller = zmq.Poller()
    poller.register(backend, zmq.POLLIN)

    while True:
        sockets = dict(poller.poll())
        if backend in sockets:
            worker, reply = backend.recv_multipart()
            if reply == b'DEAD':
                assert worker in awaited
                pck.pack(awaited[worker])
                pck.pack(True)
                forward.send(pck.bytes())
                pck.reset()
                del awaited[worker]
            else:
                if not workers:
                    poller.register(frontend, zmq.POLLIN)
                workers.append(worker)
                if reply != b'RDY':
                    forward.send(reply)
                    del awaited[worker]

        if frontend in sockets:
            # Get next client request, route to last-used worker
            worker = workers.pop(0)
            data = frontend.recv()
            unpk.feed(data)
            num = unpk.next()
            unpk.skip()
            awaited[worker] = int(num)
            backend.send_multipart([worker, data])
            # Don't poll clients if no workers are available
            if not workers:
                poller.unregister(frontend)


if __name__ == '__main__':
    if len(argv) < 4:
        print('Usage: LoadBalancer <frontend> <forward> <backend>')
        exit(1)
    main(argv[1], argv[2], argv[3])
