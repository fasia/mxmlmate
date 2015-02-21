#include <zmq.h>
#include <msgpack.h>
#include <pcap.h>

void PIN_SCORE_START() __attribute__((noinline));
void PIN_SCORE_END(uint32_t id) __attribute__((noinline));
volatile static unsigned int dummy = 0;

void PIN_SCORE_START() {
	++dummy;
//	std::cout << "PIN_SCORE_START" << std::endl;
}

void PIN_SCORE_END(uint32_t id) {
	++dummy;
//	std::cout << "PIN_SCORE_END" << std::endl;
}

/* EXPECTED ARGUMENTS:
 * tcp port number for incoming zmq connections
 */
#define BUF_SIZE 1024
int main(int argc, char *argv[]) {
	if (argc < 2) {
		fprintf(stderr, "Please provide the port for receiving data!\n");
		return 1;
	}

	void *context = zmq_ctx_new();
	void *dataIn = zmq_socket(context, ZMQ_PULL);

	char address[21] = "tcp://127.0.0.1:";
	int i = 0;
	for (; i < 4 && argv[1][i]; ++i) {
		address[16 + i] = argv[1][i];
	}
	address[16 + i] = 0;
	fprintf(stdout, "Connecting to dataInput at %s\n", address);
	zmq_connect(dataIn, address);

	char buffer[BUF_SIZE];
	msgpack_unpacked msg;
	for (;;) {
//		fprintf(stdout,"Waiting for tasks...\n");
		int size = zmq_recv(dataIn, buffer, BUF_SIZE - 1, 0);
		if (size == -1)
			return 1;
		if (size > BUF_SIZE - 1) {
			fprintf(stderr, "WARNING! Size of a message has exceeded the buffer size of %d!!!\n", BUF_SIZE);
			size = BUF_SIZE - 1;
		}
		buffer[size] = (char) 0;

		msgpack_unpacked_init(&msg);
		size_t off = 0;
		bool res = msgpack_unpack_next(&msg, buffer, size, &off);
		if (!res) {
			fprintf(stderr, "res == %d\n", res);
			fprintf(stderr, "off == %zu\n", off);
			return 1;
		}
		msgpack_object obj = msg.data;
		uint32_t id = (uint32_t) obj.via.u64;

		msgpack_unpacked_destroy(&msg);
		res = msgpack_unpack_next(&msg, buffer, size, &off);
		if (!res) {
			fprintf(stderr, "res == %d\n", res);
			fprintf(stderr, "off == %zu\n", off);
			return 1;
		}
		obj = msg.data;
		const char* path = obj.via.str.ptr;

//		fprintf(stdout, "Processing %s\n", path);

		PIN_SCORE_START();
		struct pcap_pkthdr header;
		char errbuf[PCAP_ERRBUF_SIZE];
		pcap_t *handle = pcap_open_offline(path, errbuf);
		if (NULL != handle) {
			int dlt = pcap_datalink(handle);
			const char *dlt_name = pcap_datalink_val_to_name(dlt);
			if (NULL != dlt_name)
				pcap_datalink_val_to_description(dlt);

			const u_char* packet = NULL;
			while ((packet = pcap_next(handle, &header))) {
				void* buf = malloc(header.caplen);
				if (NULL != buf) {
					memcpy(buf, packet, header.caplen);
					free(buf);
				}
			}
			pcap_close(handle);
		}
//			else fprintf(stderr, "Skipping %s\n", path);
		msgpack_unpacked_destroy(&msg);
		PIN_SCORE_END(id);
	}

}
