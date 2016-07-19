
package org.xmlmate;

import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.genetics.XMLTestChromosomeFactory;

import java.io.File;
import java.io.IOException;

public class XMLMateExample {

    public static void main(String[] args) throws IOException {
        // Set the path to your schema file (absolute or relative to pwd)
        XMLProperties.SCHEMA_PATH = "E:/Faezeh/xmlmate/xmlmate/xmlmate/schemas/musicxml.xsd";
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
        XMLTestChromosome x = factory.getChromosome();


        // create a file to write into
        File outputFile = new File("E:/Faezeh/xmlmate/xmlmate/desired.xml");
        // beware to handle any IOExceptions
        x.mutate();
        System.out.print("xml file is created.");
        x.writeToFile(outputFile);
    }

}

/*
Hint: after a while, you might want to disable logging (at least to file).
You can do it by changing the settings in src/xmlmate/resources logback.xml.
*/