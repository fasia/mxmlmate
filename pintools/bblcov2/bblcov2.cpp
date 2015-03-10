#include <pin.H>
#include <zmq.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include <map>
#include <msgpack.hpp>

typedef std::map<ADDRINT, std::set<ADDRINT> > BlockChain_T;

/* ================================================================== */
// Global variables 
/* ================================================================== */

static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
static BlockChain_T reachedBlocks;
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

void *SendResults(uint32_t id, void *socket) {
	msgpack::sbuffer buffer;
	msgpack::pack(buffer, id);
	msgpack::pack(buffer, false);
	msgpack::pack(buffer, reachedBlocks);

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
	BlockChain_T::iterator iter;
	for (iter = reachedBlocks.begin(); iter != reachedBlocks.end(); ++iter)
			iter->second.clear();
	reachedBlocks.clear();
}

/* ===================================================================== */
// Analysis routines
/* ===================================================================== */

VOID record(std::set<ADDRINT> *set, ADDRINT dest) {
	set->insert(dest);
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

			INS ins = BBL_InsTail(bbl);
			if (INS_IsBranchOrCall(ins)) {
				std::set<ADDRINT> *set = &reachedBlocks[addr];
				INS_InsertCall(ins, IPOINT_TAKEN_BRANCH, (AFUNPTR)record, IARG_PTR, set, IARG_BRANCH_TARGET_ADDR, IARG_END);
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

	// Start the program, never returns
	PIN_StartProgram();

	return 0;
}

/* ===================================================================== */
/* eof */
/* ===================================================================== */
