#include <pin.H>
#include "zmq.hpp"
#include "zhelpers.hpp"
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include <list>
#include <msgpack.hpp>

typedef std::map<ADDRINT, std::list<INT64> > Instructions_T;

/* ================================================================== */
// Global variables 
/* ================================================================== */

static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
static Instructions_T divInstructions;
zmq::context_t context(1);         // Prepare zmq context
zmq::socket_t dataOut(context, ZMQ_PUSH); // Prepare zmq socket


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

VOID SendResults(uint32_t id) {
	std::stringstream buffer;
	msgpack::pack(buffer, id);
	std::map<ADDRINT,INT64> minima;
	std::map<ADDRINT, std::list<INT64> >::iterator itmap;
	for (itmap = divInstructions.begin(); itmap != divInstructions.end(); ++itmap) {
		std::list<INT64>::iterator itli;
		for (itli = itmap->second.begin(); itli != itmap->second.end(); ++itli) {
			INT64 val = std::abs(*itli);
			std::map<ADDRINT, INT64>::iterator itr = minima.find(itmap->first);
			if (itr == minima.end() || val < itr->second)
				minima[itmap->first] = val;
		}
	}
	msgpack::pack(buffer, minima);
	buffer.seekg(0);
	s_send(dataOut, buffer.str());
}

VOID DisposeZMQ() {
	dataOut.close();
	context.close();
}

/* ===================================================================== */
// Analysis routines
/* ===================================================================== */

VOID recordIns(ADDRINT ip, ADDRINT divisor) {
	//divInstructions[ip].push_back((INT64)divisor);
}

VOID resetCounters() {
//	*out << "Resetting PIN counters" << endl;
	// go through divInstructions and clear the lists
	std::map<ADDRINT, std::list<INT64> >::iterator iter;
	for (iter = divInstructions.begin(); iter != divInstructions.end(); ++iter)
		iter->second.clear();
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

VOID Instruction(INS ins, VOID *v) {
    switch (INS_Opcode(ins)) {
    case XED_ICLASS_DIV:
    case XED_ICLASS_IDIV:
    case XED_ICLASS_FDIV: {
    	// divisor is 0th argument
    	// initialize container list on first contact
    	// divInstructions.insert(std::make_pair(INS_Address(ins), new std::list<INT64>()));
    	divInstructions[INS_Address(ins)] = *(new std::list<INT64>);
    	INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns,IARG_INST_PTR, IARG_REG_VALUE, 0, IARG_END);
    	break;
    }
    case XED_ICLASS_DIVPD:
    case XED_ICLASS_DIVPS:
    case XED_ICLASS_DIVSD:
    case XED_ICLASS_DIVSS:
    	// divisor is 1st argument
    	// initialize container list on first contact
    	// divInstructions.insert(std::make_pair(INS_Address(ins), new std::list<INT64>()));
		divInstructions[INS_Address(ins)] = *(new std::list<INT64>);
    	INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns,IARG_INST_PTR, (ADDRINT)*((INT64*)IARG_MEMORYREAD_EA), IARG_END);
    	break;
	// unsupported
    case XED_ICLASS_FIDIV:
    case XED_ICLASS_FDIVP:
    case XED_ICLASS_FDIVR:
    case XED_ICLASS_FDIVRP:
    case XED_ICLASS_FIDIVR:
    case XED_ICLASS_VDIVPD:
    case XED_ICLASS_VDIVPS:
    case XED_ICLASS_VDIVSD:
    case XED_ICLASS_VDIVSS:
		default:
			break;
	}
}

VOID Fini(INT32 code, VOID *v) {
	DisposeZMQ();
	std::map<ADDRINT, std::list<INT64> >::iterator iter;
		for (iter = divInstructions.begin(); iter != divInstructions.end(); ++iter)
			delete &iter->second;
//	OutputResults();
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

	cout << "Connecting socket in PIN tool" << endl;
	dataOut.connect("tcp://127.0.0.1:5557"); // TODO receive as param

	// Register ImageLoad to be called when an image is loaded
	IMG_AddInstrumentFunction(ImageLoad, 0);

	RTN_AddInstrumentFunction(Routine, 0);

	INS_AddInstrumentFunction(Instruction, 0);

	// Register function to be called when the application exits
	PIN_AddFiniFunction(Fini, 0);

	// Start the program, never returns
	PIN_StartProgram();

	return 0;
}

/* ===================================================================== */
/* eof */
/* ===================================================================== */
