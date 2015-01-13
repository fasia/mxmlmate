package org.xmlmate.execution;

import nu.xom.converters.DOMConverter;
import org.apache.xerces.dom.DOMImplementationImpl;
import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.runtime.Runtime;
import org.evosuite.testcase.DefaultTestCase;
import org.evosuite.testcase.ExecutionResult;
import org.evosuite.testcase.ExecutionTracer;
import org.evosuite.testcase.InterfaceTestRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xmlmate.genetics.XMLTestChromosome;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class XMLNoIOTestRunner implements InterfaceTestRunnable {
    private static final Logger logger = LoggerFactory.getLogger(XMLNoIOTestRunner.class);
    private static final Set<String> knownExceptions = new HashSet<>(10);
    private final Map<Integer, Throwable> exceptionsThrown = new HashMap<>(1,1.0f);
    private final XMLTestChromosome chrom;
    private static Transformer transformer;

    static {
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException ignored) {}
    }

    private int numExceptions;
    private boolean runFinished;
    private ExecutionResult executionResult;

    public XMLNoIOTestRunner(XMLTestChromosome chromosome) {
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

    private InputStream getInputStream() throws TransformerException {
        Document doc = DOMConverter.convert(chrom.getDocument(), DOMImplementationImpl.getDOMImplementation());
        Source xmlSource = new DOMSource(doc);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Result outputTarget = new StreamResult(outputStream);
        transformer.transform(xmlSource, outputTarget);
        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    @Override
    public ExecutionResult call() throws IOException, TransformerException {
        reset();
        // own stuff
        InputStream xmlStream = getInputStream();
        // EvoSuite stuff
        ExecutionTracer.setThread(Thread.currentThread());
        ExecutionTracer.enable();
        TestGenerationContext.getInstance().goingToExecuteSUTCode();
        try {
            Class<?> cls = Properties.getTargetClass();
            Method drive = cls.getMethod("drive", InputStream.class);
            drive.invoke(null, xmlStream);
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
        } catch (NoSuchMethodException e) {
            logger.error("Test subject does not support IO-less testing as it lacks the 'drive' method.", e);
            System.exit(1); // XXX this is an evil hack that ought to be rethought
        } catch (Exception e) {
            logger.error("Could not execute test due to ", e);
        } finally {
            TestGenerationContext.getInstance().doneWithExecuteingSUTCode();
            ExecutionTracer.disable();
            executionResult.setTrace(ExecutionTracer.getExecutionTracer().getTrace());
            executionResult.setThrownExceptions(exceptionsThrown);
            runFinished = true;
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
