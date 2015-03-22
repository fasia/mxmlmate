#include <pin.H>
#include <zmq.h>
#include <iostream>
#include <fstream>
#include <sstream>
#include <stdlib.h>
#include <set>
#include <list>
#include <map>
#include <msgpack.hpp>

typedef std::map<ADDRINT, std::list<std::pair<INT64, INT64> > > Instructions_T;

/* ================================================================== */
// Global variables 
/* ================================================================== */

static string targetImage = "";	//name of the targeted binary image
static ADDRINT imgHigh = 0;		//low end of target image
static ADDRINT imgLow = 0;			//high end of binary image
static Instructions_T instructions;
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
	std::map<ADDRINT, std::pair<INT64, INT64> > maxima;
	Instructions_T::iterator itmap;

	for (itmap = instructions.begin(); itmap != instructions.end(); ++itmap) {
		std::list<std::pair<INT64, INT64> >::iterator itli;
		for (itli = itmap->second.begin(); itli != itmap->second.end(); ++itli) {
			// the absolute values here cause noise in case of ADD instructions
//			INT64 first = std::abs(itli->first) >> 1;
//			INT64 second = std::abs(itli->second) >> 1;
			INT64 first = (itli->first) >> 1;
			INT64 second = (itli->second) >> 1;
			INT64 sum = first + second;
			std::map<ADDRINT, std::pair<INT64, INT64> >::iterator itr = maxima.find(itmap->first);
			if (itr == maxima.end() || sum > itr->second.first + itr->second.second)
				maxima[itmap->first] = std::make_pair(first, second);
		}
	}
	msgpack::pack(buffer, maxima);

	size_t len = buffer.size();
	void *buf = malloc(len);
	if (NULL != buf) {
		memcpy(buf, buffer.data(), len);
		zmq_send_const(socket, buf, len, 0);
//		*out << "Sent " << ret << " bytes." << endl;
	}
	return buf;
}

/* ===================================================================== */
// Analysis routines
/* ===================================================================== */

VOID recordIns(ADDRINT ip, INT64 factor1, INT64 factor2) {
//	cout << factor1 << " * " << factor2 << endl;
	instructions[ip].push_back(std::make_pair(factor1,factor2));
}

VOID recordIns2(ADDRINT ip, INT64 factor1, UINT64 *addr) {
//	cout << factor1 << " ** " << *addr << endl;
	instructions[ip].push_back(std::make_pair(factor1,*addr));
}

VOID resetCounters(void *ptr) {
//	*out << "Resetting PIN counters" << endl;
	free(ptr);
	// go through divInstructions and clear the lists
	Instructions_T::iterator iter;
	for (iter = instructions.begin(); iter != instructions.end(); ++iter)
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
}

VOID debugPrint(INS ins) {
	cout << INS_Mnemonic(ins) << " (" << INS_Opcode(ins) << ") @ " << INS_Address(ins) << endl;
	unsigned int i;
	for (i = 0; i < INS_OperandCount(ins); ++i) {
		cout << "op" << i;
		if (INS_OperandIsReg(ins,i))
			cout << " r";
		if (INS_OperandIsImplicit(ins,i))
			cout << " i";
		if (INS_OperandIsImmediate(ins,i))
			cout << " I";
		if (INS_OperandIsMemory(ins,i))
			cout << " m";
		if (INS_OperandIsFixedMemop(ins,i))
			cout << " M";
		cout << " (" << REG_StringShort(INS_RegR(ins, i)) << ") " << REG_StringShort(INS_RegW(ins, i)) << endl;
	}
}

VOID Instruction(INS ins, VOID *v) {
	if (INS_Address(ins) < imgLow || INS_Address(ins) > imgHigh)
		return;

    switch (INS_Opcode(ins)) {
    case XED_ICLASS_MUL:
    {
    	if (INS_OperandIsReg(ins, 0)) {
    		INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns, IARG_INST_PTR,
    				IARG_REG_VALUE, INS_OperandReg(ins, 0), IARG_REG_VALUE, INS_OperandReg(ins, 1), IARG_END);
    	} else if (INS_OperandIsMemory(ins, 0)) {
    		INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns2, IARG_INST_PTR,
    				IARG_REG_VALUE, INS_OperandReg(ins, 1), IARG_MEMORYREAD_EA, IARG_END);
    	}
		break;
	}
    case XED_ICLASS_IMUL:
    {
    	// initialize container list on first contact
    	instructions[INS_Address(ins)] = *(new std::list<std::pair<INT64, INT64> >);
    	int offset = 0;
    	switch (INS_OperandCount(ins)) {
    		case 4:
			case 3:
				// op1 x op2
				offset = 1;
			default:
				// op0 x op1
				// first arg is always register or memory
				int first = offset;
				int second = offset + 1;
				if (INS_OperandIsReg(ins, first)) {
					// op1 is register
					if (INS_OperandIsReg(ins, second)) {
						// op2 is register
//						cout << "REG * REG" << endl;
						INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns, IARG_INST_PTR,
								IARG_REG_VALUE, INS_OperandReg(ins, first), IARG_REG_VALUE, INS_OperandReg(ins, second), IARG_END);
					} else if (INS_OperandIsMemory(ins, second)) {
						// op2 is memory
						cout << "REG * MEM" << endl;
						INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns2, IARG_INST_PTR,
								IARG_REG_VALUE, INS_OperandReg(ins, second), IARG_MEMORYREAD_EA, IARG_END);
					} else if (INS_OperandIsImmediate(ins, second)) {
						// op2 is immediate
						cout << "REG * IMM" << endl;
						INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns, IARG_INST_PTR,
								IARG_REG_VALUE, INS_OperandReg(ins, first), IARG_ADDRINT, INS_OperandImmediate(ins, second), IARG_END);
					}
					// op2 is unknown type
				} else if (INS_OperandIsMemory(ins, first)) {
					// op1 is memory
					if (INS_OperandIsReg(ins, second)) {
						// op2 is register
//						cout << "MEM * REG" << endl;
						INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns2, IARG_INST_PTR,
								IARG_REG_VALUE, INS_OperandReg(ins, second), IARG_MEMORYREAD_EA, IARG_END);
					} else if (INS_OperandIsImmediate(ins, second)) {
						// op2 is immediate
						cout << "MEM * IMM" << endl;
						INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns2, IARG_INST_PTR,
								IARG_ADDRINT, INS_OperandImmediate(ins, second), IARG_MEMORYREAD_EA, IARG_END);
					} else if (INS_OperandIsMemory(ins, second)) {
						// op2 is memory - should be impossible
						cout << "MEM * MEM" << endl;
					}
				}
				break;
		}
    	break;
    }
    case XED_ICLASS_ADD:
    {
    	if (INS_OperandIsReg(ins, 0)) {
    		if (INS_OperandIsReg(ins, 1))
    			INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns, IARG_INST_PTR, IARG_REG_VALUE, INS_OperandReg(ins, 0), IARG_REG_VALUE, INS_OperandReg(ins, 1), IARG_END);
    		else if (INS_OperandIsImmediate(ins, 1))
    			INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns, IARG_INST_PTR, IARG_REG_VALUE, INS_OperandReg(ins, 0), IARG_ADDRINT, INS_OperandImmediate(ins, 1), IARG_END);
    		else if (INS_OperandIsMemory(ins, 1))
    			INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns2, IARG_INST_PTR, IARG_REG_VALUE, INS_OperandReg(ins, 0), IARG_MEMORYREAD_EA, IARG_END);
    	} else if (INS_OperandIsMemory(ins, 0)) {
    		if (INS_OperandIsReg(ins, 1))
				INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns2, IARG_INST_PTR, IARG_REG_VALUE, INS_OperandReg(ins, 1), IARG_MEMORYREAD_EA, IARG_END);
			else if (INS_OperandIsImmediate(ins, 1))
				INS_InsertCall(ins,IPOINT_BEFORE,(AFUNPTR)recordIns2, IARG_INST_PTR, IARG_ADDRINT, INS_OperandImmediate(ins, 1), IARG_MEMORYREAD_EA, IARG_END);
    	}
    	break;
    }
	default:
		break;
	}
}

VOID Fini(INT32 code, VOID *v) {
	*out << "Freeing resources" << endl;
	Instructions_T::iterator iter;
	for (iter = instructions.begin(); iter != instructions.end(); ++iter)
		delete &iter->second;
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
