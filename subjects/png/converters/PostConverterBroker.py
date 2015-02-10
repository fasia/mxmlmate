import zmq 
 
 
def main(): 
    context = zmq.Context() 
    frontend = context.socket(zmq.PULL) 
    frontend.bind("tcp://127.0.0.1:5560") 
    backend = context.socket(zmq.PUSH) 
    backend.bind("tcp://127.0.0.1:5570")
 
    zmq.device(zmq.QUEUE, frontend, backend) 
 
    frontend.close() 
    backend.close() 
    context.close() 
 
if __name__ == '__main__': 
    main()