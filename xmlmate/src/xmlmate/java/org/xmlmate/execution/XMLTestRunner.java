package org.xmlmate.execution;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.runtime.Runtime;
import org.evosuite.testcase.DefaultTestCase;
import org.evosuite.testcase.ExecutionResult;
import org.evosuite.testcase.ExecutionTracer;
import org.evosuite.testcase.InterfaceTestRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.xml.AwareDocument;
import org.xmlmate.xml.AwareElement;

import freedots.musicxml.Print;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//faezeh
import java.util.logging.ConsoleHandler;
import java.util.regex.Matcher;
import java.io.Console;

public class XMLTestRunner implements InterfaceTestRunnable {
	private static final Logger logger = LoggerFactory
			.getLogger(XMLTestRunner.class);
	private static final Set<String> knownExceptions = new HashSet<>(10);
	private final Map<Integer, Throwable> exceptionsThrown = new HashMap<>(1,
			1.0f);
	private final XMLTestChromosome chrom;
	private int numExceptions;
	private boolean runFinished;
	private ExecutionResult executionResult;
	

	// faezeh
	public static int totalmut;

	public XMLTestRunner(XMLTestChromosome chromosome) {
		reset();
		chrom = chromosome;
	}

	private void reset() {
		// faezeh
		numExceptions = 0;
		
		exceptionsThrown.clear();
		runFinished = false;
		executionResult = new ExecutionResult(new DefaultTestCase(), null);
		Runtime.resetSingleton();

		ExecutionTracer executionTracer = ExecutionTracer.getExecutionTracer();
		executionTracer.clear();
		ExecutionTracer.setCheckCallerThread(false); // uncomment this line to
														// monitor ALL the
														// threads
		executionResult.setTrace(executionTracer.getTrace());
	}

/*	// Faezeh
	private boolean ismutated(XMLTestChromosome chromosome) {
		
		 * if (isit==true) { logger.info("is it mutated"); } else{
		 * logger.info("is not mutated"); }
		 
		return AwareDocument.mutated;

	}*/

	@Override
	public ExecutionResult call() throws IOException {
		reset();
		// own stuff
		// logger.info("it is a new run!");
		File xmlFile = chrom.writeToFile();
		assert null != xmlFile;
		String path = xmlFile.getAbsolutePath();
		// Faezeh
		//boolean mut = ismutated(chrom);
		
		// EvoSuite stuff
		ExecutionTracer.setThread(Thread.currentThread());
		ExecutionTracer.enable();
		TestGenerationContext.getInstance().goingToExecuteSUTCode();
		//faezeh : if the chromosome is mutated by mutation opertaors, then execute the
		//if (chrom.isMutated()) {
			try {
				Class<?> cls = Properties.getTargetClass();
				Method main = cls.getMethod("main", String[].class);
				main.invoke(null, new Object[] { new String[] { path } });
				//faezeh
				if (chrom.isMutated()){
					totalmut++;
					logger.info("total mut number is now {}",totalmut);
					logger.info("//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////");
					// if mutated and passed the test (alive mut) then it should get 0. otherwise, 100
					chrom.setAlive(true);
				}
				} catch (InvocationTargetException e) {
				Throwable cause = e.getCause();
				exceptionsThrown.put(numExceptions, cause);
				numExceptions += 1;
				String message = cause.getMessage();
				String exceptionName = cause.getClass().getSimpleName();
				if (knownExceptions.add(exceptionName)) {
					if (null == message)
						logger.debug("Testcase introduced {}", exceptionName);
					else
						logger.debug("Testcase introduced {}: {}",
								exceptionName, message);
				} else {
					if (null == message)
						logger.debug("Testcase caused {}", exceptionName);
					else
						logger.debug("Testcase caused {}: {}", exceptionName,
								message);
				}
			} catch (Exception e) {
				numExceptions += 1;
				logger.error("Could not execute test due to ", e);
			} finally {
				TestGenerationContext.getInstance().doneWithExecuteingSUTCode();
				ExecutionTracer.disable();
				executionResult.setTrace(ExecutionTracer.getExecutionTracer()
						.getTrace());
				executionResult.setThrownExceptions(exceptionsThrown);
				runFinished = true;

				
				
				
				if (!xmlFile.delete()) {
					logger.warn("Could not delete temporary file {}",
							xmlFile.getAbsolutePath());
					xmlFile.deleteOnExit();
				}//end if 
			}//end finally
		//} // end if

		return executionResult;

	}

	@Override
	public Map<Integer, Throwable> getExceptionsThrown() {
		return new HashMap<>(exceptionsThrown);
	}

	@Override
	public boolean isRunFinished() {
		return runFinished;
	}
}
