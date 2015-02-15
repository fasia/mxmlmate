package org.xmlmate.genetics;

import nu.xom.Serializer;
import nu.xom.canonical.Canonicalizer;

import org.apache.commons.io.FilenameUtils;
import org.apache.xerces.xs.XSElementDeclaration;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ConstructionFailedException;
import org.evosuite.ga.SecondaryObjective;
import org.evosuite.localsearch.LocalSearchBudget;
import org.evosuite.localsearch.LocalSearchObjective;
import org.evosuite.testcase.*;
import org.evosuite.testsuite.TestSuiteFitnessFunction;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.xmlmate.execution.XMLTestRunner;
import org.xmlmate.formats.FormatConverter;
import org.xmlmate.xml.AwareDocument;
import org.xmlmate.xml.AwareElement;
import org.xmlmate.xml.AwareInstantiator;

import javax.xml.namespace.QName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

public class XMLTestChromosome extends ExecutableChromosome {
    private static final long serialVersionUID = 436444840380164621L;
    private static final Logger logger = LoggerFactory.getLogger(XMLTestChromosome.class);
    private static final ExecutorService executor = Executors.newSingleThreadExecutor(TestCaseExecutor.getInstance());
    private static final List<SecondaryObjective> secondaryObjectives = new ArrayList<>();
    private static FormatConverter converter;
    private AwareDocument doc = null;

    public XMLTestChromosome(String rootElem) {
        QName root = QName.valueOf(rootElem);
        doc = AwareInstantiator.generate(root);
        setChanged(true);
    }

    public XMLTestChromosome(AwareDocument document) {
        doc = document;
        setChanged(true);
    }

    public XMLTestChromosome() {
        doc = AwareInstantiator.generate();
        setChanged(true);
    }

    public XMLTestChromosome(XMLTestChromosome other) {
        doc = new AwareDocument(other.doc); // copy the doc deeply
        age = other.age;
        setChanged(other.isChanged());
        copyCachedResults(other);
    }

    public static void addSecondaryObjective(SecondaryObjective objective) {
        secondaryObjectives.add(objective);
    }

    public static void removeSecondaryObjective(SecondaryObjective objective) {
        secondaryObjectives.remove(objective);
    }

    public static void setConverter(FormatConverter newConverter) {
        logger.info("Setting format converter {}", newConverter.getClass().getName());
        converter = newConverter;
    }

    public File writeToFile() throws IOException {
        return writeToFile(null);
    }

    public File writeToFile(File f) throws IOException {
        return writeToFile(f, false);
    }

    public File writeToFile(File f, boolean keepOriginal) throws IOException {
        if (null == f)
            f = new File(XMLProperties.OUTPUT_PATH, System.currentTimeMillis() + "_" + Randomness.nextShort() + XMLProperties.FILE_EXTENSION);

        File output = f;
        boolean convert = null != converter;
        try (FileOutputStream fop = new FileOutputStream(f)) {

            if (keepOriginal) {
                Serializer serializer = new Serializer(fop, "UTF-8");
                serializer.setIndent(4);
                serializer.write(doc);
            } else {
                Canonicalizer canonicalizer = new Canonicalizer(fop, Canonicalizer.EXCLUSIVE_XML_CANONICALIZATION);
                canonicalizer.write(doc);
            }

            if (convert) {
                output = new File(converter.convert(f.getAbsolutePath(), FilenameUtils.removeExtension(f.getAbsolutePath())));
                if (!keepOriginal && !f.delete())
                    f.deleteOnExit();
            }

        }
        return output;
    }

    @Override
    public void mutate() {
        if (doc.mutate()) {
            setChanged(true);
            clearCachedResults();
        }
    }

    @Override
    public ExecutionResult executeForFitnessFunction(TestSuiteFitnessFunction testSuiteFitnessFunction) {
        TimeoutHandler<ExecutionResult> handler = new TimeoutHandler<>();
        InterfaceTestRunnable callable = new XMLTestRunner(this);
//        InterfaceTestRunnable callable = new XMLNoIOTestRunner(this);
        try {
            return handler.execute(callable, executor, Properties.TIMEOUT, Properties.CPU_TIMEOUT);
        } catch (TimeoutException e) {
            logger.warn("Timed out on executing test chromosome for fitness function!");
            return null;
        } catch (Exception e) {
            logger.warn("Exception on executing test chromosome for fitness function: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof XMLTestChromosome))
            return false;
        XMLTestChromosome other = (XMLTestChromosome) obj;
        if (size() != other.size())
            return false; // too slow?
        return doc.equals(other.doc);
        // solve differently?
        // timestamps? - imprecise
        // serialized output - inefficient
        // after onMutate false?
    }

    @Override
    public int hashCode() {
        return doc.hashCode();
    }

    @Override
    public int size() {
        int acc = 0;
        for (Set<AwareElement> next : getEleMap().values())
            acc += next.size();
        return acc;
    }

    @Override
    public int compareSecondaryObjective(Chromosome o) {
        int objective = 0;
        int cmp = 0;
        while (0 == cmp && objective < secondaryObjectives.size()) {
            SecondaryObjective so = secondaryObjectives.get(objective);
            objective += 1;
            if (null == so)
                return 0;
            cmp = so.compareChromosomes(this, o);
        }
        return cmp;
    }

    @Override
    public Chromosome clone() {
        return new XMLTestChromosome(this);
    }

    @Override
    public void crossOver(Chromosome other, int position1, int position2) throws ConstructionFailedException {
        throw new UnsupportedOperationException("XMLTestChromosome doesn't support cross-over at arbitrary positions");
    }

    @Override
    public boolean localSearch(LocalSearchObjective<? extends Chromosome> objective) {
    	logger.trace("Local search on test");
        if (doc.smallNumericMutation()) {
            setChanged(true);
            LocalSearchBudget.getInstance().countLocalSearchOnTest();
        }
        return isChanged();
    }

    @Override
    protected void copyCachedResults(ExecutableChromosome other) {
        ExecutionResult result = other.getLastExecutionResult();
        if (result != null)
            lastExecutionResult = result.clone();
    }

    public Map<XSElementDeclaration, Set<AwareElement>> getEleMap() {
        return doc.getEleMap();
    }

    @Override
    public String toString() {
        return doc.phpPrint();
    }

    public void print() {
        Canonicalizer canonicalizer = new Canonicalizer(System.out, Canonicalizer.EXCLUSIVE_XML_CANONICALIZATION);
        try {
            canonicalizer.write(doc);
        } catch (IOException e) {
            logger.error("Error on canonicalizing: {}", e.getMessage());
        }
    }

    public AwareDocument getDocument() {
        return doc;
    }

}
