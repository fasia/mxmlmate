#include <pin.H>
#include <zmq.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include <map>
#include <tr1/tuple>
#include <msgpack.hpp>

/*
 allocations = set {(rip,start,size)}
 accesses = map {returnIP -> max distance}

 try for
     [..........x...]
 with
     start <= x <= start + size
 and
     std::abs(start + size/2 - x) as big as possible

 */

// (rip, start, size)
typedef std::tr1::tuple<ADDRINT, ADDRINT, ADDRINT> Allocation_T;

struct AllocationCompare {
    //overlapping ranges are considered equivalent
    bool operator()(const Allocation_T& lhv, const Allocation_T& rhv) const {
    	// start1+size1 < start2
        return std::tr1::get<1>(lhv) + std::tr1::get<2>(lhv) < std::tr1::get<1>(rhv);
    }
};
typedef std::set<Allocation_T, AllocationCompare> Allocations_T;

// ^ adapted from http://stackoverflow.com/a/8561876

typedef std::map<ADDRINT, ADDRINT> Acessess_T;

/* ================================================================== */
// Global variables 
/* ================================================================== */

static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
static Allocations_T allocations; // stores all currently allocated memory ranges
static Acessess_T accesses; // maps return site of a malloc call to maximum distance from its buffer

static Allocation_T *find_allocation(ADDRINT addr) {
    Allocations_T::const_iterator it = allocations.find(Allocation_T(0, addr, addr));
    if (it == allocations.end())
    	return NULL;
    return (Allocation_T*)&(*it);
}

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
	accesses.clear();
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

VOID FreeBefore(ADDRINT start) {
	Allocations_T::iterator it = allocations.find(Allocation_T(0, start, 0));
	if (it != allocations.end()) {
		// cout << "Freeing buffer at " << start << endl;
		allocations.erase(it);
	}
}

VOID *MallocWrapper(CONTEXT * ctxt, AFUNPTR pf_malloc, size_t size, ADDRINT rip) {
	ADDRINT start;
	PIN_CallApplicationFunction(ctxt, PIN_ThreadId(), CALLINGSTD_DEFAULT, pf_malloc, PIN_PARG(ADDRINT), &start, PIN_PARG(size_t), size, PIN_PARG_END());
	if (rip >= imgLow || rip <= imgHigh) {
		// cout << "malloc(" << size << ") => " << start << endl;
		//if (NULL == start) count towards fitness;
		allocations.insert(Allocation_T(rip, start, size));
	}
	return (VOID *) start;
}

VOID *CallocWrapper(CONTEXT * ctxt, AFUNPTR pf_calloc, size_t nmemb, size_t size, ADDRINT rip) {
	ADDRINT start;
	PIN_CallApplicationFunction(ctxt, PIN_ThreadId(), CALLINGSTD_DEFAULT, pf_calloc, PIN_PARG(ADDRINT), &start, PIN_PARG(size_t), nmemb, PIN_PARG(size_t), size, PIN_PARG_END());
	if (rip >= imgLow || rip <= imgHigh) {
		//if (NULL == start) count towards fitness;
		allocations.insert(Allocation_T(rip, start, nmemb * size));
	}
	return (VOID *)start;
}

VOID *ReallocWrapper(CONTEXT * ctxt, AFUNPTR pf_realloc, ADDRINT p, size_t size, ADDRINT rip) {
	ADDRINT start;
	PIN_CallApplicationFunction(ctxt, PIN_ThreadId(), CALLINGSTD_DEFAULT, pf_realloc, PIN_PARG(ADDRINT), &start, PIN_PARG(ADDRINT), p, PIN_PARG(size_t), size, PIN_PARG_END());
	if (rip >= imgLow || rip <= imgHigh) {
			//if (NULL == start) count towards fitness;
			Allocations_T::iterator it = allocations.find(Allocation_T(rip, p, size));
			if (it != allocations.end())
				allocations.erase(it);
			if (size > 0)
				allocations.insert(Allocation_T(rip, start, size));
	}
	return (VOID *)start;
}

VOID RecordMemAccess(ADDRINT ea) {
	Allocation_T *alloc = find_allocation(ea);
	// alloc cannot be NULL because find_allocation(ea) was used as a precondition

	INT64 middle = (INT64)(std::tr1::get<1>(*alloc) + std::tr1::get<2>(*alloc)/2);
	ADDRINT dist = std::abs(middle - (INT64)ea);

	ADDRINT rip = std::tr1::get<0>(*alloc);
	Acessess_T::const_iterator itr = accesses.find(rip);
	if (itr == accesses.end() || itr->second < dist) {
		// cout << rip << " +> " << dist << endl;
		accesses[rip] = dist;
	}
}

/* ===================================================================== */
// Instrumentation callbacks
/* ===================================================================== */

// Pin calls this function every time a new img is loaded
// Note that imgs (including shared libraries) are loaded lazily
VOID ImageLoad(IMG img, VOID *v) {
	std::string img_name = IMG_Name(img);
	cout << "Loading image " << img_name << endl;

	if (img_name.find(targetImage) != std::string::npos) {
		cout << "Loaded target image " << IMG_Name(img) << endl;
		imgLow = IMG_LowAddress(img);
		imgHigh = IMG_HighAddress(img);
	} else if (img_name.find("ld-linux") != std::string::npos)
		return;

	RTN startRtn = RTN_FindByName(img, "PIN_SCORE_START");
	if (RTN_Valid(startRtn)) {
		cout << "PIN_SCORE_START found" << endl;
		RTN_Replace(startRtn, (AFUNPTR) resetCounters);
	}

	RTN stopRtn = RTN_FindByName(img, "PIN_SCORE_END");
	if (RTN_Valid(stopRtn)) {
		cout << "PIN_SCORE_END found" << endl;
		RTN_Replace(stopRtn, (AFUNPTR) SendResults);
	}

	// Find the free() function.
	RTN freeRtn = RTN_FindByName(img, "free");
	if (RTN_Valid(freeRtn)) {
		cout << "free found" << endl;
		RTN_Open(freeRtn);
		RTN_InsertCall(freeRtn, IPOINT_BEFORE, (AFUNPTR)FreeBefore, IARG_FUNCARG_ENTRYPOINT_VALUE, 0, IARG_RETURN_IP, IARG_END);
		RTN_Close(freeRtn);
	}

	// Patch malloc (wrapper)
	RTN mallocRtn = RTN_FindByName(img, "malloc");
	if (RTN_Valid(mallocRtn)) {
		cout << "malloc found" << endl;
		PROTO protoMalloc = PROTO_Allocate(PIN_PARG(void *), CALLINGSTD_DEFAULT, "malloc", PIN_PARG(size_t), PIN_PARG(ADDRINT), PIN_PARG_END());
		RTN_ReplaceSignature(mallocRtn, (AFUNPTR)MallocWrapper, IARG_PROTOTYPE, protoMalloc, IARG_CONST_CONTEXT, IARG_ORIG_FUNCPTR, IARG_FUNCARG_ENTRYPOINT_VALUE, 0, IARG_RETURN_IP, IARG_END);
	}

	// Patch calloc (wrapper)
	RTN callocRtn = RTN_FindByName(img, "calloc");
	if (RTN_Valid(callocRtn)) {
		cout << "calloc found" << endl;
		PROTO protoCalloc = PROTO_Allocate(PIN_PARG(void *), CALLINGSTD_DEFAULT, "calloc", PIN_PARG(size_t), PIN_PARG(size_t), PIN_PARG(ADDRINT), PIN_PARG_END());
		RTN_ReplaceSignature(callocRtn, AFUNPTR(CallocWrapper), IARG_PROTOTYPE, protoCalloc, IARG_CONST_CONTEXT, IARG_ORIG_FUNCPTR, IARG_FUNCARG_ENTRYPOINT_VALUE, 0, IARG_FUNCARG_ENTRYPOINT_VALUE, 1, IARG_RETURN_IP, IARG_END);
	}

	// Patch realloc (wrapper)
	RTN reallocRtn = RTN_FindByName(img, "realloc");
	if (RTN_Valid(reallocRtn)) {
		cout << "realloc found" << endl;
		PROTO protoRealloc = PROTO_Allocate(PIN_PARG(void *), CALLINGSTD_DEFAULT, "realloc", PIN_PARG(void *), PIN_PARG(size_t), PIN_PARG(ADDRINT), PIN_PARG_END());
		RTN_ReplaceSignature(reallocRtn, AFUNPTR(ReallocWrapper), IARG_PROTOTYPE, protoRealloc, IARG_CONST_CONTEXT, IARG_ORIG_FUNCPTR, IARG_FUNCARG_ENTRYPOINT_VALUE, 0, IARG_FUNCARG_ENTRYPOINT_VALUE, 1, IARG_RETURN_IP, IARG_END);
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
					INS_InsertIfPredicatedCall(ins, IPOINT_BEFORE, (AFUNPTR) find_allocation, IARG_MEMORYOP_EA, memOp, IARG_END);
					INS_InsertThenCall(ins, IPOINT_BEFORE, (AFUNPTR) RecordMemAccess, IARG_MEMORYOP_EA, memOp, IARG_END);
				}
			}
		}
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

// Start the program, never returns
	PIN_StartProgram();

	return 0;
}

/* ===================================================================== */
/* eof */
/* ===================================================================== */
