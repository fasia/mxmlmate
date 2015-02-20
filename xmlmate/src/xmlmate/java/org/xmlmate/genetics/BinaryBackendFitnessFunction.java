package org.xmlmate.genetics;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.evosuite.ga.FitnessFunction;
import org.evosuite.utils.Randomness;
import org.msgpack.MessagePack;
import org.msgpack.packer.BufferPacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import com.google.common.base.Stopwatch;

public abstract class BinaryBackendFitnessFunction extends FitnessFunction<XMLTestSuiteChromosome> {
    private static final Logger logger = LoggerFactory.getLogger(BinaryBackendFitnessFunction.class);
    private final Context context;
    private final Socket dataOut;
    private final Socket coverageIn;
    private final MessagePack msg = new MessagePack();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static final Stopwatch evaluationClock = Stopwatch.createUnstarted();

    public BinaryBackendFitnessFunction() {
	// bind socket for receiving coverage results
	context = ZMQ.context(1);
	dataOut = context.socket(ZMQ.PUSH);
	coverageIn = context.socket(ZMQ.PULL);
	// XXX make the addresses parameters to the program and pass them on to the PIN tool?
	dataOut.bind("tcp://127.0.0.1:5556");
	coverageIn.bind("tcp://127.0.0.1:5557");
    }

    public final void freeSockets() {
	dataOut.close();
	coverageIn.close();
	context.term();
    }

    public final void startAndReadyWorkers() {
	int defaultDelay = 30;
	System.out.println(MessageFormat.format("Press Enter when all workers and converters are ready. (Will autostart in {0} seconds.)", defaultDelay));
	
	Future<?> futureInput = executor.submit(new Runnable() {
	    @Override
	    public void run() {
		try (Scanner in = new Scanner(System.in)) {
		    in.nextLine();
		}
	    }
	});
	try {
	    futureInput.get(defaultDelay, TimeUnit.SECONDS);
	} catch (InterruptedException | ExecutionException e) {
	    e.printStackTrace();
	    System.out.println("Something went wrong while waiting for startup. Starting anyway.");
	} catch (TimeoutException e) {
	    System.out.println("Autostarting");
	}
    }

    protected final Map<Integer, File> sendChangedChromosomes(List<XMLTestChromosome> chromosomes) {
	Map<Integer, File> awaited = new HashMap<Integer, File>();

	for (int i = 0; i < chromosomes.size(); i++) {
	    XMLTestChromosome x = chromosomes.get(i);
	    if (!x.isChanged())
		continue;
	    File f = new File(XMLProperties.OUTPUT_PATH, String.format("%d-%d%s", System.currentTimeMillis(), Randomness.nextShort(), XMLProperties.FILE_EXTENSION));
	    try {
		File outputFile = x.writeToFile(f);
		String path = outputFile.getAbsolutePath();

		BufferPacker packer = msg.createBufferPacker();
		packer.write(i);
		packer.write(path);

		logger.trace("Sending task {}:{}", i, path);
		dataOut.send(packer.toByteArray());
		awaited.put(i, outputFile);
	    } catch (IOException e) {
		logger.error("Could not send chromosome {} out for evaluation!", i);
	    }
	}
	return awaited;
    }

    protected final ByteBuffer receiveResult() {
	return ByteBuffer.wrap(coverageIn.recv());
    }
}
