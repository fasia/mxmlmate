
package org.xmlmate;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.testcase.ExecutionTracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xmlmate.execution.UseCase;
import org.xmlmate.execution.XMLTestRunner;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.genetics.XMLTestChromosomeFactory;
import org.xmlmate.xml.AwareDocument;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import traversingXML.*;

import org.xmlmate.execution.*;

public class NoGenAlgorithmXMLMateExample {
	private static final Logger logger = LoggerFactory
			.getLogger(NoGenAlgorithmXMLMateExample.class);
	private static final Set<String> knownExceptions = new HashSet<>(10);
	private final static Map<Integer, Throwable> exceptionsThrown = new HashMap<>(1,
			1.0f);
	private static int numExceptions;
	static int allMuts=0;


	public static void main(String[] args) throws IOException, TransformerException, XPathExpressionException, ParserConfigurationException, SAXException {
		int number=0;
		//long current_time = System.currentTimeMillis();
		long start_time = System.currentTimeMillis();
		// Set the path to your schema file (absolute or relative to pwd)
		XMLProperties.SCHEMA_PATH = "E:/Faezeh/xmlmate1/xmlmate/xmlmate/schemas/musicxml.xsd";
		// choose root element for your xml in the QName format {namespace}localName
		XMLProperties.ROOT_ELEM = "score-partwise";
		XMLProperties.initialize();
		XMLProperties.determineUseCase(args);
		// create factory for XML chromosomes
		XMLTestChromosomeFactory factory = new XMLTestChromosomeFactory(XMLProperties.ROOT_ELEM);
		// Use the factory to get xml instances
		// loop until we find 1000 alive mutants
		while((System.currentTimeMillis()-start_time)/1000<600){
			allMuts++;
			XMLTestChromosome x = factory.getChromosome();
			// create a file to write into
			File outputFile = new File("example.xml");
			x.mutate();
			x.writeToFile(outputFile);
			String path = outputFile.getAbsolutePath();
			boolean mut = ismutated(outputFile);
			//execute the xml file
			if (mut){

				try{
					Class<?> cls = Properties.getTargetClass();
					System.out.println("it is cls "+cls);
					//Method main = cls.getMethod("main", String[].class);
					// main.invoke(null, new Object[]{new String[]{path}});

					Method drive = cls.getMethod("main", String[].class);

					System.out.println(" we got: "+drive);
					drive.invoke(null, new Object[]{new String[]{path}});
					number++;
				}
				catch (InvocationTargetException e) {
					Throwable cause = e.getCause();
					exceptionsThrown.put(numExceptions, cause);
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
					logger.error("Could not execute test due to ", e);
				} //end of catch (Exception e)
			}//end if
		}//end while loop
		logger.info("the number of total mutants: {}"+allMuts);
		logger.info("the number of alive mutants: {}"+number);
		
	}//end main

	private static boolean ismutated(File outputFile) {

		return AwareDocument.mutated;
	}


}

/*
Hint: after a while, you might want to disable logging (at least to file).
You can do it by changing the settings in src/xmlmate/resources logback.xml.
 */