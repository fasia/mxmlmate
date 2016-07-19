
package org.xmlmate;

import org.evosuite.Properties;
import org.xml.sax.SAXException;
import org.xmlmate.execution.UseCase;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.genetics.XMLTestChromosomeFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;

import traversingXML.*;

import org.xmlmate.execution.*;

public class ORGXMLMateExample {

    public static void main(String[] args) throws IOException, TransformerException, XPathExpressionException, ParserConfigurationException, SAXException {
        // Set the path to your schema file (absolute or relative to pwd)
        XMLProperties.SCHEMA_PATH = "E:/Faezeh/xmlmate1/xmlmate/xmlmate/schemas/musicxml.xsd";
        // choose root element for your xml in the QName format {namespace}localName
        XMLProperties.ROOT_ELEM = "score-partwise";
        /*
        Initialize internal data structures.
        If performance is of the essence, you can instead call
        XMLProperties.parseModel();
        However, you will first need to lift its visibility.
         */
        XMLProperties.initialize();
        // create factory for XML chromosomes
        XMLTestChromosomeFactory factory = new XMLTestChromosomeFactory(XMLProperties.ROOT_ELEM);
        // Use the factory to get xml instances
       // for (int i =0 ; i<100; i++){
        XMLTestChromosome x = factory.getChromosome();
        // create a file to write into
        File outputFile = new File("rrrr.xml");
        // beware to handle any IOExceptions
        x.mutate();
        System.out.println("xml file is mutated.");
        x.writeToFile(outputFile);
        System.out.println("xml file is created.");


        
        //String path = outputFile.getAbsolutePath();
        //System.out.println("it is the path of out put file "+path);
        //execute the xml file
    }

}

/*
Hint: after a while, you might want to disable logging (at least to file).
You can do it by changing the settings in src/xmlmate/resources logback.xml.
*/