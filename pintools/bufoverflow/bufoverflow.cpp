#include <pin.H>
#include <zmq.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include <map>
#include <msgpack.hpp>

/*
 TODO need mappings
 allocations = (start,end) -> returnIP
 accesses = returnIP -> max distance

 try for
 [..........x...]
 with
     start <= x <= end
 <=> x - start <= size
 and
     math::abs(size/2 - x) as big as possible


 send out accesses
 */


typedef std::pair<ADDRINT, ADDRINT> Range;

struct RangeCompare {
    //overlapping ranges are considered equivalent
    bool operator()(const Range& lhv, const Range& rhv) const {
        return lhv.second < rhv.first;
    }
};
typedef std::set<Range, RangeCompare> Allocations_T;

// ^ adapted from http://stackoverflow.com/a/8561876

Range *find_range(const Allocations_T& ranges, ADDRINT value) {
    Allocations_T::const_iterator it = ranges.find(Range(value, value));
    if (it == ranges.end())
    	return NULL;
    return (Range*)&(*it);
}

bool in_range(const Allocations_T& ranges, ADDRINT value) {
	return find_range(ranges, value) != NULL;
}
typedef std::map<ADDRINT, ADDRINT> Acessess_T;

/* ================================================================== */
// Global variables 
/* ================================================================== */

static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
static Allocations_T allocations; // stores all currently allocated memory ranges
static Acessess_T accesses; // maps return site of a malloc call to maximum distance from its buffer

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


VOID resetCounters(void *ptr) {
	free(ptr);
	allocations.clear();
}

void *SendResults(uint32_t id, void *socket) {
	msgpack::sbuffer buffer;
	msgpack::pack(buffer, id);
	msgpack::pack(buffer, false);
	msgpack::pack(buffer, accesses);

	size_t len = buffer.size();
	void *buf = malloc(len);
	if (NULL != buf) {
		memcpy(buf, buffer.data(), len);
		zmq_send_const(socket, buf, len, 0);
//		cerr << "Sent " << ret << " bytes." << endl;
	}
	return buf;
}

/* ===================================================================== */
// Analysis callbacks
/* ===================================================================== */

VOID Arg1Before(ADDRINT size, ADDRINT retIP) {
    // record retIP -> size
	// cout << retIP << " <-- " << size << endl;
}

VOID MallocAfter(ADDRINT ret, ADDRINT retIP) {
	// retrieve stored size to calculate
	// cout << retIP << " --> " << ret << endl;
}

VOID RecordMemAccess(VOID *ip, ADDRINT *ea) {
	// TODO
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
	RTN mallocRtn = RTN_FindByName(img, "malloc");
	if (RTN_Valid(mallocRtn)) {
		cout << "malloc found" << endl;
		RTN_Open(mallocRtn);
		RTN_InsertCall(mallocRtn, IPOINT_BEFORE, (AFUNPTR)Arg1Before, IARG_FUNCARG_ENTRYPOINT_VALUE, 0, IARG_RETURN_IP, IARG_END);
		RTN_InsertCall(mallocRtn, IPOINT_AFTER, (AFUNPTR)MallocAfter, IARG_FUNCRET_EXITPOINT_VALUE, IARG_RETURN_IP, IARG_END);
		RTN_Close(mallocRtn);
	}
	// TODO add support for calloc
	// TODO add support for realloc

	// Find the free() function.
	RTN freeRtn = RTN_FindByName(img, "free");
	if (RTN_Valid(freeRtn)) {
		cout << "free found" << endl;
		RTN_Open(freeRtn);
		RTN_InsertCall(freeRtn, IPOINT_BEFORE, (AFUNPTR)Arg1Before, IARG_FUNCARG_ENTRYPOINT_VALUE, 0, IARG_RETURN_IP, IARG_END);
		RTN_Close(freeRtn);
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
						INS_InsertPredicatedCall(ins, IPOINT_BEFORE, (AFUNPTR) RecordMemAccess, IARG_INST_PTR, IARG_MEMORYOP_EA, memOp, IARG_END);
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
