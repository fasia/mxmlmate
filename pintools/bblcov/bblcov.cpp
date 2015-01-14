#include <pin.H>
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include "zmq.hpp"

typedef std::set<ADDRINT> Blocks_T;

/* ================================================================== */
// Global variables 
/* ================================================================== */

UINT64 insCount = 0;        //number of executed instructions
UINT64 threadCount = 0;     //total number of threads, including main thread
static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
static Blocks_T executedBlocks;
zmq::context_t context(1);         // Prepare zmq context
zmq::socket_t socket(context, ZMQ_PUSH); // Prepare zmq socket


std::ostream * out = &cerr;

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

VOID OutputResults() {
	*out << "===============================================" << endl;
	*out << "bblcov analysis results: " << endl;
	*out << "Number of instructions: " << insCount << endl;
	*out << "Number of basic blocks: " << executedBlocks.size() << endl;
//	*out << "Number of basic blocks executions: " << bblCount << endl;
	*out << "Number of threads: " << threadCount << endl;
	*out << "===============================================" << endl;
}

VOID SendResults() {
	*out << "Sending results: " << executedBlocks.size() << endl;
	std::stringstream out;
	out << executedBlocks.size();
	std::string s = out.str();

	zmq::message_t reply2(s.length());
	memcpy((void *) reply2.data(), s.c_str(), s.length());
	socket.send(reply2);
}

VOID DisposeZMQ() {
	socket.close();
	context.close();
}

/* ===================================================================== */
// Analysis routines
/* ===================================================================== */

VOID PIN_FAST_ANALYSIS_CALL Block(UINT32 instr, ADDRINT addr) {
	if (executedBlocks.insert(addr).second) {
		insCount += instr;
	}
}

VOID resetCounters() {
	*out << "Resetting PIN counters" << endl;
	threadCount = 0;
	insCount = 0;
	executedBlocks.clear();
}

/* ===================================================================== */
// Instrumentation callbacks
/* ===================================================================== */

// Pin calls this function every time a new img is loaded
// Note that imgs (including shared libraries) are loaded lazily
VOID ImageLoad(IMG img, VOID *v) {
	*out << "Loading target image " << IMG_Name(img) << endl;
	if (targetImage == IMG_Name(img)) {
		imgLow = IMG_LowAddress(img);
		imgHigh = IMG_HighAddress(img);
	}
}

VOID Trace(TRACE trace, VOID *v) {
	if (imgLow) {
		for(BBL bbl = TRACE_BblHead(trace); BBL_Valid(bbl); bbl = BBL_Next(bbl))
		{
			ADDRINT addr = BBL_Address(bbl);
			if (addr < imgLow || addr > imgHigh)
				continue;

			BBL_InsertCall(bbl, IPOINT_ANYWHERE, (AFUNPTR)Block,
					IARG_FAST_ANALYSIS_CALL, IARG_UINT32, BBL_NumIns(bbl), IARG_ADDRINT, BBL_Address(bbl), IARG_END);
		}
	}
}

/*!
 * Increase counter of threads in the application.
 * This function is called for every thread created by the application when it is
 * about to start running (including the root thread).
 * @param[in]   threadIndex     ID assigned by PIN to the new thread
 * @param[in]   ctxt            initial register state for the new thread
 * @param[in]   flags           thread creation flags (OS specific)
 * @param[in]   v               value specified by the tool in the 
 *                              PIN_AddThreadStartFunction function call
 */
VOID ThreadStart(THREADID threadIndex, CONTEXT *ctxt, INT32 flags, VOID *v) {
	threadCount++;
}

VOID Routine(RTN rtn, VOID *v) {
	cerr << "Routine " << RTN_Name(rtn) << endl;
	if ("PIN_SCORE_START" == RTN_Name(rtn)) {
		RTN_Open(rtn);
		RTN_InsertCall(rtn, IPOINT_ANYWHERE, (AFUNPTR)resetCounters, 0);
		RTN_Close(rtn);
	} else if ("PIN_SCORE_END" == RTN_Name(rtn)) {
		RTN_Open(rtn);
		RTN_InsertCall(rtn, IPOINT_ANYWHERE, (AFUNPTR)SendResults, 0);
		RTN_Close(rtn);
	}
}

VOID Fini(INT32 code, VOID *v) {
	DisposeZMQ();
}

BOOL FollowChild(CHILD_PROCESS childProcess, VOID * userData) {
    return TRUE; // FIXME this seems to cause a problem
}

/*!
 * The main procedure of the tool.
 * This function is called when the application image is loaded but not yet started.
 * @param[in]   argc            total number of elements in the argv array
 * @param[in]   argv            array of command line arguments, 
 *                              including pin -t <toolname> -- ...
 */
int main(int argc, char *argv[]) {
	// Initialize PIN library. Print help message if -h(elp) is specified
	// in the command line or the command line is invalid
	if (PIN_Init(argc, argv)) {
		return Usage();
	}

	string fileName = KnobOutputFile.Value();

	if (!fileName.empty()) {
		out = new std::ofstream(fileName.c_str());
	}

	targetImage = KnobTargetImage.Value();

	if (targetImage.empty()) {
		cerr << "Please provide the name of the target image!" << endl;
		return Usage();
	}

	socket.connect("tcp://127.0.0.1:5557"); // TODO receive as param

	// Register ImageLoad to be called when an image is loaded
	IMG_AddInstrumentFunction(ImageLoad, 0);

	// Register Routine to be called to instrument trace
	TRACE_AddInstrumentFunction(Trace, 0);

	RTN_AddInstrumentFunction(Routine, 0);

	// Register function to be called for every thread before it starts running
	PIN_AddThreadStartFunction(ThreadStart, 0);

	// Register function to be called when the application exits
	PIN_AddFiniFunction(Fini, 0);

	PIN_AddFollowChildProcessFunction(FollowChild, 0);

	// Start the program, never returns
	PIN_StartProgram();

	return 0;
}

/* ===================================================================== */
/* eof */
/* ===================================================================== */
