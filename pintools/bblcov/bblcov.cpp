#include <pin.H>
#include <zmq.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include <msgpack.hpp>

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

void *SendResults(uint32_t id, void *socket) {
	msgpack::sbuffer buffer;
	msgpack::pack(buffer, id);
	msgpack::pack(buffer, false);
	msgpack::pack(buffer, executedBlocks);

	size_t len = buffer.size();
	void *buf = malloc(len);
	if (NULL != buf) {
		memcpy(buf, buffer.data(), len);
		zmq_send_const(socket, buf, len, 0);
//		*out << "Sent " << ret << " bytes." << endl;
	}
	return buf;
}

VOID resetCounters(void *ptr) {
//	*out << "Resetting PIN counters" << endl;
	free(ptr);
	threadCount = 0;
	insCount = 0;
	executedBlocks.clear();
}

/* ===================================================================== */
// Analysis routines
/* ===================================================================== */

VOID PIN_FAST_ANALYSIS_CALL Block(UINT32 instr, ADDRINT addr) {
	if (executedBlocks.insert(addr).second) {
		insCount += instr;
	}
}

/* ===================================================================== */
// Instrumentation callbacks
/* ===================================================================== */

// Pin calls this function every time a new img is loaded
// Note that imgs (including shared libraries) are loaded lazily
VOID ImageLoad(IMG img, VOID *v) {
	*out << "Loading image " << IMG_Name(img) << endl;
	if (IMG_Name(img).find(targetImage) != std::string::npos) {
		*out << "Loaded target image " << IMG_Name(img) << endl;
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
			BBL_InsertCall(bbl, IPOINT_ANYWHERE, (AFUNPTR)Block, IARG_FAST_ANALYSIS_CALL, IARG_UINT32, BBL_NumIns(bbl), IARG_ADDRINT, BBL_Address(bbl), IARG_END);
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

	string fileName = KnobOutputFile.Value();

	if (!fileName.empty()) {
		out = new std::ofstream(fileName.c_str());
	}

	targetImage = KnobTargetImage.Value();

	if (targetImage.empty()) {
		cerr << "Please provide the name of the target image!" << endl;
		return Usage();
	}

	// Register ImageLoad to be called when an image is loaded
	IMG_AddInstrumentFunction(ImageLoad, 0);

	// Register Routine to be called to instrument trace
	TRACE_AddInstrumentFunction(Trace, 0);

	RTN_AddInstrumentFunction(Routine, 0);

	// Register function to be called for every thread before it starts running
	PIN_AddThreadStartFunction(ThreadStart, 0);

	// Start the program, never returns
	PIN_StartProgram();

	return 0;
}

/* ===================================================================== */
/* eof */
/* ===================================================================== */
