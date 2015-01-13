package org.xmlmate.execution;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.runtime.Runtime;
import org.evosuite.testcase.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.genetics.XMLTestChromosome;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XMLTestRunner implements InterfaceTestRunnable {
    private static final Logger logger = LoggerFactory.getLogger(XMLTestRunner.class);
    private static final Set<String> knownExceptions = new HashSet<>(10);
    private final Map<Integer, Throwable> exceptionsThrown = new HashMap<>(1,1.0f);
    private final XMLTestChromosome chrom;
    private int numExceptions;
    private boolean runFinished;
    private ExecutionResult executionResult;

    public XMLTestRunner(XMLTestChromosome chromosome) {
        reset();
        chrom = chromosome;
    }

    private void reset() {
        numExceptions = 0;
        exceptionsThrown.clear();
        runFinished = false;
        executionResult = new ExecutionResult(new DefaultTestCase(), null);
        Runtime.resetSingleton();

        ExecutionTracer executionTracer = ExecutionTracer.getExecutionTracer();
        executionTracer.clear();
        ExecutionTracer.setCheckCallerThread(false); // uncomment this line to monitor ALL the threads
        executionResult.setTrace(executionTracer.getTrace());
    }

    @Override
    public ExecutionResult call() throws IOException {
        reset();
        // own stuff
        File xmlFile = chrom.writeToFile();
        assert null != xmlFile;
        String path = xmlFile.getAbsolutePath();

        // EvoSuite stuff
        ExecutionTracer.setThread(Thread.currentThread());
        ExecutionTracer.enable();
        TestGenerationContext.getInstance().goingToExecuteSUTCode();
        try {
            Class<?> cls = Properties.getTargetClass();
            Method main = cls.getMethod("main", String[].class);
            main.invoke(null, new Object[]{new String[]{path}});
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            exceptionsThrown.put(numExceptions, cause);
            numExceptions += 1;
            String message = cause.getMessage();
            String exceptionName = cause.getClass().getSimpleName();
            if (knownExceptions.add(exceptionName)) {
                if (null == message)
                    logger.info("Testcase introduced {}", exceptionName);
                else
                    logger.info("Testcase introduced {}: {}", exceptionName, message);
            } else {
                if (null == message)
                    logger.debug("Testcase caused {}", exceptionName);
                else
                    logger.debug("Testcase caused {}: {}", exceptionName, message);
            }
        } catch (Exception e) {
            logger.error("Could not execute test due to ", e);
        } finally {
            TestGenerationContext.getInstance().doneWithExecuteingSUTCode();
            ExecutionTracer.disable();
            executionResult.setTrace(ExecutionTracer.getExecutionTracer().getTrace());
            executionResult.setThrownExceptions(exceptionsThrown);
            runFinished = true;
            if (!xmlFile.delete()) {
                logger.warn("Could not delete temporary file {}", xmlFile.getAbsolutePath());
                xmlFile.deleteOnExit();
            }
        }
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
