#include "zmq.hpp"
#include "zhelpers.hpp"
#include <string>
#include <vector>
#include <sstream>
#include <cstdlib>
#include <libxml/parser.h>
#include <libxml/tree.h>

void PIN_SCORE_START() __attribute__((noinline));
void PIN_SCORE_END() __attribute__((noinline));
volatile static unsigned int dummy = 0;

std::vector<std::string> &split(const std::string &s, char delim,
		std::vector<std::string> &elems) {
	std::stringstream ss(s);
	std::string item;
	while (std::getline(ss, item, delim)) {
		elems.push_back(item);
	}
	return elems;
}

std::vector<std::string> split(const std::string &s, char delim) {
	std::vector<std::string> elems;
	split(s, delim, elems);
	return elems;
}

void PIN_SCORE_START() {
	++dummy;
//	std::cout << "PIN_SCORE_START" << std::endl;
}

void PIN_SCORE_END() {
	++dummy;
//	std::cout << "PIN_SCORE_END" << std::endl;
}

/* EXPECTED ARGUMENTS:
 * controlSocket transport address // TODO
 * dataIn transport address // TODO
 */
int main(int argc, char* argv[]) {
	assert(argc > 2);
	// Prepare our context and socket
	zmq::context_t context(1);
	zmq::socket_t controlSocket(context, ZMQ_REQ);
	zmq::socket_t dataIn(context, ZMQ_PULL);

	// TODO receive transport addresses via parameter
	dataIn.connect("tcp://127.0.0.1:5556");
	controlSocket.connect("tcp://127.0.0.1:5555");

	// send rdy
	zmq::message_t readiness(3);
	memcpy((void *) readiness.data(), "rdy", 3);
	controlSocket.send(readiness);

	while (true) {
		std::cout << "Wrapper waiting for tasks" << std::endl;
		// Wait for data from xmlmate
		std::string data = s_recv(dataIn);
		std::vector<std::string> items = split(data, ':');
//		std::cout << "Received " << items.size() << " work items" << std::endl;

		PIN_SCORE_START();
		/*
		 * this initialize the library and check potential ABI mismatches
		 * between the version it was compiled for and the actual shared
		 * library used.
		 */
		LIBXML_TEST_VERSION
		for (std::vector<std::string>::const_iterator i = items.begin(); i != items.end(); ++i) {
			xmlDocPtr doc; /* the resulting document tree */
			doc = xmlReadFile(i->c_str(), NULL, 0);
			if (doc == NULL) {
				fprintf(stderr, "Failed to parse %s\n", i->c_str());
			}
			xmlFreeDoc(doc);
		}
		/*
		 * Cleanup function for the XML library.
		 */
		xmlCleanupParser();
		/*
		 * this is to debug memory for regression tests
		 */
		xmlMemoryDump();
		PIN_SCORE_END();
	}

	// unreachable
	controlSocket.close();
	dataIn.close();
	context.close();
	return 0;
}
