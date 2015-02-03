#include <pin.H>
#include "zmq.hpp"
#include "zhelpers.hpp"
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include <msgpack.hpp>

typedef std::set<ADDRINT> Addrs_T;

/* ================================================================== */
// Global variables 
/* ================================================================== */

static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
zmq::context_t context(1);         // Prepare zmq context
zmq::socket_t dataOut(context, ZMQ_PUSH); // Prepare zmq socket
static Addrs_T accessedAddresses;


/* ===================================================================== */
// Command line switches
/* ===================================================================== */

KNOB<string> KnobOutputFile(KNOB_MODE_WRITEONCE, "pintool", "o", "",
		"specify file name for bblcov output");

KNOB<BOOL> KnobCount(KNOB_MODE_WRITEONCE, "pintool", "count", "1",
		"count instructions, basic blocks and threads in the application");

KNOB<string> KnobTargetImage(KNOB_MODE_WRITEONCE, "pintool", "target", "",
		"specify the name of the targeted image");

/* ===================================================================== */
// Utilities
/* ===================================================================== */

INT32 Usage() {
	cerr << "This tool prints out the number of dynamically executed " << endl
			<< "instructions, basic blocks and threads in the application."
			<< endl << endl;
	cerr << KNOB_BASE::StringKnobSummary() << endl;
	return -1;
}

VOID DisposeZMQ() {
	dataOut.close();
	context.close();
}

VOID resetCounters() {
	accessedAddresses.clear();
}

VOID SendResults() {
	std::stringstream buffer;
	msgpack::pack(buffer, accessedAddresses);
	buffer.seekg(0);
	std::cout << "sending buffer" << std::endl;
	s_send(dataOut, buffer.str());
	std::cout << "buffer sent" << std::endl;
}

/* ===================================================================== */
// Analysis callbacks
/* ===================================================================== */

VOID RecordMemRead(VOID *ip, VOID *ea) {
	accessedAddresses.insert((uint64_t)ea);
}

/* ===================================================================== */
// Instrumentation callbacks
/* ===================================================================== */

// Pin calls this function every time a new img is loaded
// Note that imgs (including shared libraries) are loaded lazily
VOID ImageLoad(IMG img, VOID *v) {
	cout << "Loading image " << IMG_Name(img) << endl;
	if (IMG_Name(img).find(targetImage) != std::string::npos) {
		cout << "Loaded target image " << IMG_Name(img) << endl;
		imgLow = IMG_LowAddress(img);
		imgHigh = IMG_HighAddress(img);
	}
}

VOID Trace(TRACE trace, VOID *v) {
	if (0 == imgLow)
		return;
	for (BBL bbl = TRACE_BblHead(trace); BBL_Valid(bbl); bbl = BBL_Next(bbl)) {
		ADDRINT addr = BBL_Address(bbl);
		if (addr < imgLow || addr > imgHigh)
			continue;
		for (INS ins = BBL_InsHead(bbl); INS_Valid(ins); ins = INS_Next(ins)) {
			if (INS_IsMemoryRead(ins) || INS_IsMemoryWrite(ins)) {
				UINT32 memoryOperands = INS_MemoryOperandCount(ins);
				for (UINT32 memOp = 0; memOp < memoryOperands; memOp++) {
						INS_InsertPredicatedCall(ins, IPOINT_BEFORE, (AFUNPTR) RecordMemRead, IARG_INST_PTR, IARG_MEMORYOP_EA, memOp, IARG_END);
				}
			}
		}
	}
}

VOID Routine(RTN rtn, VOID *v) {
	if (RTN_Name(rtn).find("PIN_SCORE_START") != std::string::npos) {
		cout << "Detected PIN_SCORE_START" << endl;
		RTN_Open(rtn);
		RTN_Replace(rtn, (AFUNPTR) resetCounters);
		RTN_Close(rtn);
	} else if (RTN_Name(rtn).find("PIN_SCORE_END") != std::string::npos) {
		cout << "Detected PIN_SCORE_END" << endl;
		RTN_Open(rtn);
		RTN_Replace(rtn, (AFUNPTR) SendResults);
		RTN_Close(rtn);
	}
}

VOID Fini(INT32 code, VOID *v) {
	DisposeZMQ();
}

/*!
 * The main procedure of the tool.
 * This function is called when the application image is loaded but not yet started.
 * @param[in]   argc            total number of elements in the argv array
 * @param[in]   argv            array of command line arguments, 
 *                              including pin -t <toolname> -- ...
 */
int main(int argc, char *argv[]) {

	PIN_InitSymbols();

// Initialize PIN library. Print help message if -h(elp) is specified
// in the command line or the command line is invalid
	if (PIN_Init(argc, argv)) {
		return Usage();
	}

	targetImage = KnobTargetImage.Value();

	if (targetImage.empty()) {
		cerr << "Please provide the name of the target image!" << endl;
		return Usage();
	}

	cout << "Connecting socket in PIN tool" << endl;
	dataOut.connect("tcp://127.0.0.1:5557"); // TODO receive as param

// Register ImageLoad to be called when an image is loaded
	IMG_AddInstrumentFunction(ImageLoad, 0);

// Register Routine to be called to instrument trace
	TRACE_AddInstrumentFunction(Trace, 0);

	RTN_AddInstrumentFunction(Routine, 0);

// Register function to be called when the application exits
	PIN_AddFiniFunction(Fini, 0);

// Start the program, never returns
	PIN_StartProgram();

	return 0;
}

/* ===================================================================== */
/* eof */
/* ===================================================================== */
