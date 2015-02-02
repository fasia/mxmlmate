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

static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
zmq::context_t context(1);         // Prepare zmq context
zmq::socket_t socket(context, ZMQ_PUSH); // Prepare zmq socket
std::stringstream out;
BUFFER_ID bufId;
#define NUM_BUF_PAGES 1024
/*
 * Record of memory references.  Rather than having two separate
 * buffers for reads and writes, we just use one struct that includes a
 * flag for type.
 */
struct MEMREF {
	ADDRINT pc;
	ADDRINT ea;
	UINT32 size;
	BOOL read;
};

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
	socket.close();
	context.close();
}

VOID resetCounters() {
	// clean up stream
	out.str(std::string());
	out.clear();
}

VOID SendResults() {
	std::string s = out.str();
	zmq::message_t reply2(s.length());
	memcpy((void *) reply2.data(), s.c_str(), s.length());
	socket.send(reply2);
}

/**************************************************************************
 *
 *  Callback Routines
 *
 **************************************************************************/

VOID *BufferFull(BUFFER_ID id, THREADID tid, const CONTEXT *ctxt, VOID *buf, UINT64 numElements, VOID *v) {
	struct MEMREF *reference = (struct MEMREF*) buf;
	for (UINT64 i = 0; i < numElements; i++, reference++) {
		if (reference->ea != 0)
			out << reference->pc << (reference->read?"r":"w") << reference->ea << endl;
	}
	return buf;
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
			UINT32 memoryOperands = INS_MemoryOperandCount(ins);

			for (UINT32 memOp = 0; memOp < memoryOperands; memOp++) {
				UINT32 refSize = INS_MemoryOperandSize(ins, memOp);

				// Note that if the operand is both read and written we log it once for each.
				if (INS_MemoryOperandIsRead(ins, memOp)) {
					INS_InsertFillBuffer(ins, IPOINT_BEFORE, bufId,
							IARG_INST_PTR, offsetof(struct MEMREF, pc),
							IARG_MEMORYOP_EA, memOp,
							offsetof(struct MEMREF, ea), IARG_UINT32, refSize,
							offsetof(struct MEMREF, size), IARG_BOOL, TRUE,
							offsetof(struct MEMREF, read),
							IARG_END);
				}

				if (INS_MemoryOperandIsWritten(ins, memOp)) {
					INS_InsertFillBuffer(ins, IPOINT_BEFORE, bufId,
							IARG_INST_PTR, offsetof(struct MEMREF, pc),
							IARG_MEMORYOP_EA, memOp,
							offsetof(struct MEMREF, ea), IARG_UINT32, refSize,
							offsetof(struct MEMREF, size), IARG_BOOL, FALSE,
							offsetof(struct MEMREF, read),
							IARG_END);
				}
			}
		}
	}
}

VOID ThreadStart(THREADID threadIndex, CONTEXT *ctxt, INT32 flags, VOID *v) {

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

    bufId = PIN_DefineTraceBuffer(sizeof(struct MEMREF), NUM_BUF_PAGES, BufferFull, 0);

    if(bufId == BUFFER_ID_INVALID) {
        cerr << "Error: could not allocate initial buffer" << endl;
        return 1;
    }

	cout << "Connecting socket in PIN tool" << endl;
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

// Start the program, never returns
	PIN_StartProgram();

	return 0;
}

/* ===================================================================== */
/* eof */
/* ===================================================================== */
