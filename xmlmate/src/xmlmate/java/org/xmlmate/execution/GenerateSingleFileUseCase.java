package org.xmlmate.execution;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.xml.AwareDocument;
import org.xmlmate.xml.AwareInstantiator;

import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;

public class GenerateSingleFileUseCase implements UseCase {
    private static final Logger logger = LoggerFactory.getLogger(GenerateSingleFileUseCase.class);

    private final File output;

    public GenerateSingleFileUseCase(File outputFile) {
        output = outputFile;
    }

    @Override
    public void run() {
        logger.debug("Creating single XML instance in {}", output);
        AwareDocument document = AwareInstantiator.generate(QName.valueOf(XMLProperties.ROOT_ELEM));
        try {
            new XMLTestChromosome(document).writeToFile(output);
        } catch (IOException e) {
            logger.error("Could not write file '{}' due to {}", output, e.getMessage());
        }
    }
}
