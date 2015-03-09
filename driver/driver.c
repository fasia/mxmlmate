#include <zmq.h>
#include <msgpack.h>

void PIN_SCORE_START(char *ptr) __attribute__((noinline));
void *PIN_SCORE_END(uint32_t id, void *socket) __attribute__((noinline));
volatile static unsigned int dummy = 0;

void PIN_SCORE_START(char *ptr) {
	++dummy;
//	std::cout << "PIN_SCORE_START" << std::endl;
}

void *PIN_SCORE_END(uint32_t id, void *socket) {
	++dummy;
	return NULL;
//	std::cout << "PIN_SCORE_END" << std::endl;
}

/* EXPECTED ARGUMENTS:
 * <endpoint> <identity>
 */
int main(int argc, char *argv[]) {
	if (argc < 3) {
		fprintf(stderr, "Usage: testdriver <endpoint> <identity>\n");
		return 1;
	}
	void *context = zmq_ctx_new();
	void *socket = zmq_socket(context, ZMQ_DEALER);
	char *address = argv[1];
	char *identity = argv[2];
	fprintf(stdout, "Connecting worker %s to socket at %s\n", identity, address);
	zmq_setsockopt(socket, ZMQ_IDENTITY, identity, strlen(identity));
	zmq_connect(socket, address);
	zmq_send_const(socket, "RDY", 3, 0);

	char *outbuffer = NULL;
	msgpack_unpacked unpacked;

	for (;;) {
		zmq_msg_t msg;
		zmq_msg_init(&msg);
		zmq_msg_recv(&msg, socket, 0);
		size_t size = zmq_msg_size(&msg);
		char *inbuffer = (char *) malloc(size + 1);
		if (NULL == inbuffer) {
			fprintf(stderr, "Out of Memory!\n");
			return 1;
		}
		memcpy(inbuffer, zmq_msg_data(&msg), size);
		zmq_msg_close(&msg);
		inbuffer[size] = 0;

		msgpack_unpacked_init(&unpacked);
		size_t off = 0;
		bool res = msgpack_unpack_next(&unpacked, inbuffer, size, &off);
		if (!res) {
			fprintf(stderr,"res == %d\n", res);
			fprintf(stderr,"off == %zu\n", off);
			return 1;
		}
		msgpack_object obj = unpacked.data;
		uint32_t id = (uint32_t) obj.via.u64;

		msgpack_unpacked_destroy(&unpacked);
		res = msgpack_unpack_next(&unpacked, inbuffer, size, &off);
		if (!res) {
			fprintf(stderr, "res == %d\n", res);
			fprintf(stderr, "off == %zu\n", off);
			return 1;
		}
		obj = unpacked.data;
		const char* path = obj.via.str.ptr;

		PIN_SCORE_START(outbuffer);

		// TODO call sut here with path

		msgpack_unpacked_destroy(&unpacked);
		free(inbuffer);
		outbuffer = PIN_SCORE_END(id, socket);
	}
}
