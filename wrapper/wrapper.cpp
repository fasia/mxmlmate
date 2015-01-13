#include "zmq.hpp"
#include "zhelpers.hpp"
#include <string>
#include <vector>
#include <iostream>
#include <sstream>
#include <cstdlib>
#include <unistd.h>

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

}

void PIN_SCORE_END() {

}

int main() {

	// Prepare our context and socket
	zmq::context_t context(1);
	zmq::socket_t controlSocket(context, ZMQ_REQ);
	zmq::socket_t dataIn(context, ZMQ_PULL);

	dataIn.connect("tcp://127.0.0.1:5556");
	controlSocket.connect("tcp://127.0.0.1:5555");

	// send rdy
	zmq::message_t readiness(3);
	memcpy((void *) readiness.data(), "rdy", 3);
	controlSocket.send(readiness);

	while (true) {
		// Wait for data from xmlmate
		std::string data = s_recv(dataIn);
		std::vector<std::string> items = split(data, ':');
//		std::cout << "Received " << items.size() << " work items" << std::endl;
		PIN_SCORE_START();
		// process data
		for (std::vector<std::string>::iterator it = items.begin(); it != items.end(); ++it) {
			// TODO call driver here
		}
		PIN_SCORE_END();
	}

	// unreachable
	controlSocket.close();
	dataIn.close();
	context.close();
	return 0;
}
