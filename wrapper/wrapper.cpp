#include "zmq.hpp"
#include "zhelpers.hpp"
#include <string>
#include <vector>
#include <iostream>
#include <sstream>
#include <cstdlib>
#include <unistd.h>
#include <sys/wait.h>

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
	std::cout << "PIN_SCORE_START" << std::endl;
}

void PIN_SCORE_END() {
	std::cout << "PIN_SCORE_END" << std::endl;
}

/* EXPECTED ARGUMENTS:
 * controlSocket transport address // TODO
 * dataIn transport address // TODO
 * path to driver
 */
int main(int argc, char* argv[]) {
	assert(argc > 3);
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
		char **args = new char*[items.size()+argc-2];
		int i = 0;
		for(i = 0; i < argc-3; ++i) {
			args[i] = argv[i+3];
		}
		for (std::vector<std::string>::iterator it = items.begin(); it != items.end(); ++it) {
			args[i] = const_cast<char*>(it->c_str());
			i += 1;
		}
		args[i] = NULL;

		pid_t pid = fork();
		switch (pid) {
		case -1:
			// error forking
			std::cerr << "ERROR: Could not fork driver process!" << std::endl;
			delete args;
			return 1;
		case 0:
			// child process
			PIN_SCORE_START();
			execv(argv[3], args); // call driver here
			return 0;
		default:
			// parent process
			int status;
			waitpid(pid, &status, 0);
//			std::cout << "driver exited with code " << status << std::endl;
			delete args;
			PIN_SCORE_END();
			break;
		}
	}

	// unreachable
	controlSocket.close();
	dataIn.close();
	context.close();
	return 0;
}
