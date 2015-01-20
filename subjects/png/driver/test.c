#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zmq.h>
#include <unistd.h>

void PIN_SCORE_START() __attribute__((noinline));
void PIN_SCORE_END() __attribute__((noinline));
volatile static unsigned int dummy = 0;

void PIN_SCORE_START() {
	++dummy;
//	std::cout << "PIN_SCORE_START" << std::endl;
}

void PIN_SCORE_END() {
	++dummy;
//	std::cout << "PIN_SCORE_END" << std::endl;
}

//  Convert C string to 0MQ string and send to socket
static int s_send(void *socket, char *string) {
    int size = zmq_send (socket, string, strlen (string), 0);
    return size;
}

//  Receive 0MQ string from socket and convert into C string
//  Caller must free returned string. Returns NULL if the context
//  is being terminated.
static char *s_recv (void *socket) {
    char buffer [256];
    int size = zmq_recv (socket, buffer, 255, 0);
    if (size == -1)
        return NULL;
    if (size > 255)
        size = 255;
    buffer [size] = 0;
    return strdup (buffer);
}

int main(int argc, char *argv[]) {
	void *context = zmq_ctx_new ();
	void *dataIn = zmq_socket (context, ZMQ_PULL);
	void *controlSocket = zmq_socket(context, ZMQ_REQ);

	zmq_connect(dataIn, "tcp://127.0.0.1:5556");
	zmq_connect(controlSocket, "tcp://127.0.0.1:5555");

//	fprintf(stdout,"Sending rdy\n");
	s_send(controlSocket, "rdy");

	for (;;) {
//		fprintf(stdout,"Waiting for tasks...\n");
		char *message = s_recv(dataIn);
//		fprintf(stdout,"Received %s\n", message);
		PIN_SCORE_START();
		char *token = strtok(message, ":");
		while (NULL!=token) {
			fprintf(stdout,"Processing %s\n", token);
//			test_one_file(token, outname);
			sleep(2);
//			fprintf(stdout,"Finished with %s\n", token);
			token = strtok(NULL, ":");
		}
		free(message);
//		fprintf(stdout,"Done\n");
		PIN_SCORE_END();
	}

}
