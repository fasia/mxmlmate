package org.xmlmate.genetics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSElementDeclaration;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.SecondaryObjective;
import org.evosuite.localsearch.LocalSearchBudget;
import org.evosuite.localsearch.LocalSearchObjective;
import org.evosuite.testcase.ExecutionResult;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import dk.brics.automaton.Transition;

public class XMLTestSuiteChromosome extends AbstractTestSuiteChromosome<XMLTestChromosome> {

    private static final long serialVersionUID = -6950670819692732483L;
    private static final Logger logger = LoggerFactory.getLogger(XMLTestSuiteChromosome.class);
    private static final List<SecondaryObjective> secondaryObjectives = new ArrayList<>(2);
    private double regexCoverage = 0.0d;
    private double elemCoverage = 0.0d;
    private double attrCoverage = 0.0d;

    public XMLTestSuiteChromosome(ChromosomeFactory<XMLTestChromosome> testChromosomeFactory) {
        super(testChromosomeFactory);
        populate();
    }

    public XMLTestSuiteChromosome(XMLTestSuiteChromosome other) {
        super(other);
        regexCoverage = other.regexCoverage;
        elemCoverage = other.elemCoverage;
        attrCoverage = other.attrCoverage;
        age = other.age;
        setChanged(other.isChanged());
    }

    @Override
    public boolean localSearch(LocalSearchObjective<? extends Chromosome> objective) {
    	logger.debug("Local search on suite");
//    	LocalSearchBudget.getInstance().countLocalSearchOnTest();
        return false;
    }

    public static void addSecondaryObjective(SecondaryObjective objective) {
        secondaryObjectives.add(objective);
    }

	public static void removeSecondaryObjective(SecondaryObjective objective) {
    	for (Iterator<SecondaryObjective> iterator = secondaryObjectives.iterator(); iterator.hasNext();) {
			SecondaryObjective secondaryObjective = iterator.next();
			if (secondaryObjective.getClass().equals(objective.getClass())) {
				logger.debug("Removed secondary objective {}", objective.getClass().getName());
				iterator.remove();
			}
		}
    }

    public double getSchemaRegexCoverage() {
        if (!isChanged())
            return regexCoverage;
        if (XMLProperties.SCHEMA_ALL_TRANSITIONS.size() == 0) {
            regexCoverage = 1.0d;
            return regexCoverage;
        }
        Set<Transition> atDiff = new HashSet<>(XMLProperties.SCHEMA_ALL_TRANSITIONS);
        for (XMLTestChromosome x : tests) {
            Set<Transition> regexTransitions = x.getDocument().getRegexTransitions();
            atDiff.removeAll(regexTransitions);
        }
        regexCoverage = 1.0d - (double) atDiff.size() / (double) XMLProperties.SCHEMA_ALL_TRANSITIONS.size();
        return regexCoverage;
    }

    public double getSchemaElementCoverage() {
        if (!isChanged())
            return elemCoverage;
        Set<XSElementDeclaration> elDiff = new HashSet<>(XMLProperties.SCHEMA_ALL_ELEMENTS);
        for (XMLTestChromosome x : tests)
            elDiff.removeAll(x.getDocument().getElementDeclarations());
        elemCoverage = 1.0d - (double) elDiff.size() / (double) XMLProperties.SCHEMA_ALL_ELEMENTS.size();
        return elemCoverage;
    }

    public double getSchemaAttributeCoverage() {
        if (!isChanged())
            return attrCoverage;
        if (XMLProperties.SCHEMA_ALL_ATTRS.size() == 0) {
            attrCoverage = 1.0d;
            return attrCoverage;
        }
        Set<XSAttributeDeclaration> atDiff = new HashSet<>(XMLProperties.SCHEMA_ALL_ATTRS);
        for (XMLTestChromosome x : tests)
            atDiff.removeAll(x.getDocument().getAttributeDeclarations());
        attrCoverage = 1.0d - (double) atDiff.size() / (double) XMLProperties.SCHEMA_ALL_ATTRS.size();
        return attrCoverage;
    }

    private void populate() {
        // int numTests = Randomness.nextInt(Properties.MIN_INITIAL_TESTS, Properties.MAX_INITIAL_TESTS + 1);
        int numTests = Properties.NUM_TESTS;
        for (int i = 0; i < numTests; i++)
            tests.add(testChromosomeFactory.getChromosome());
        setChanged(true);
    }

    @Override
    public AbstractTestSuiteChromosome<XMLTestChromosome> clone() {
        return new XMLTestSuiteChromosome(this);
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
    public HashSet<Throwable> getAllThrownExceptions() {
        HashSet<Throwable> ex = new HashSet<>();
        for (XMLTestChromosome chrom : tests) {
            ExecutionResult x = chrom.getLastExecutionResult();
            if (null != x)
                ex.addAll(x.getAllThrownExceptions());
        }
        return ex;
    }

    public Collection<Throwable> getAllUniqueThrownExceptions() {
        Map<StackTraceElement, Throwable> uniqueExceptions = new HashMap<>();
        HashSet<Throwable> allExceptions = getAllThrownExceptions();
        for (Throwable t : allExceptions) {
            StackTraceElement[] trace = t.getStackTrace();
            Throwable cause = t.getCause();
            if (null != cause)
                trace = cause.getStackTrace();

            if (null == trace || 0 == trace.length)
                uniqueExceptions.put(null, t); // this loses some precision, but there's not much I can do
            else
                uniqueExceptions.put(trace[0], t);
        }
        return uniqueExceptions.values();
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        for (XMLTestChromosome chrom : tests) {
            str.append(chrom);
            ExecutionResult res = chrom.getLastExecutionResult();
            if (null != res && res.getNumberOfThrownExceptions() > 0) {
                str.append("\t throws ");
                str.append(res.getAllThrownExceptions());
            }
            str.append('\n');
        }
        str.append(tests.size());
        return str.toString();
    }

    public void print() {
        System.out.println("The following XMLs were generated: \n");
        for (XMLTestChromosome chrom : tests) {
            chrom.print();
            System.out.println('\n');
        }
        System.out.println("\n END.");
    }

    public File writeToFiles() {
        File projectDir = new File(XMLProperties.OUTPUT_PATH, XMLProperties.RUN_NAME);
        projectDir.mkdirs();
        int i = 0;
        for (XMLTestChromosome c : tests) {
            File xmlFile = new File(projectDir, String.format("file%d%s", i, XMLProperties.FILE_EXTENSION));
            i += 1;
            try {
                c.writeToFile(xmlFile, true);
            } catch (IOException e) {
                logger.error("Could not create {}", xmlFile.getAbsolutePath());
            }
        }
        return projectDir;
    }

    @Override
    public void mutate() {
        // delete testcases?
        for (Iterator<XMLTestChromosome> iterator = tests.iterator(); iterator.hasNext();) {
            iterator.next();
            if (Randomness.nextDouble() < 0.01) {
            	logger.trace("Removing an xml file");
                iterator.remove();
                setChanged(true);
            }
        }

        // Mutate existing test cases
        for (int i = 0; i < tests.size(); i++) {
            XMLTestChromosome chrom = tests.get(i);
            // mutate 50% of existing testcases
            if (Randomness.nextDouble() < 0.50d) {
                Chromosome backup = chrom.clone();
                chrom.mutate();
                if (XMLProperties.MAX_XML_SIZE > 0 && chrom.size() > XMLProperties.MAX_XML_SIZE)
                    tests.set(i, (XMLTestChromosome) backup);
                else
                    setChanged(true);
            } else if (Randomness.nextDouble() < 0.50d) {
                int j = Randomness.nextInt(tests.size());
                if (j != i) {
                    XMLTestChromosome parent1 = tests.get(i);
                    XMLTestChromosome parent2 = tests.get(j);
                    Chromosome p1clone = parent1.clone();
                    Chromosome p2clone = parent2.clone();
                    new XMLCrossOverFunction().crossOverXMLs(parent1, parent2, new HashSet<XSElementDeclaration>());
                    if (XMLProperties.MAX_XML_SIZE > 0 && parent1.size() > XMLProperties.MAX_XML_SIZE)
                        tests.set(i, (XMLTestChromosome) p1clone);
                    else
                        setChanged(true);
                    if (XMLProperties.MAX_XML_SIZE > 0 && parent2.size() > XMLProperties.MAX_XML_SIZE)
                        tests.set(j, (XMLTestChromosome) p2clone);
                    else
                        setChanged(true);
                }
            }
        }

        // Add new test cases
        for (int count = 1; Randomness.nextDouble() <= StrictMath.pow(Properties.P_TEST_INSERTION, count) && size() < Properties.MAX_SIZE; count++) {
        	logger.trace("Adding new xml file");
            tests.add(testChromosomeFactory.getChromosome());
            setChanged(true);
        }
    }
    

	public int getLargestFitnessFile() {
		double largest = 0d;
		int file = -1;
		for (int i = 0; i < tests.size(); i++) {
			XMLTestChromosome x = tests.get(i);
			if (x.getFitness() > largest) {
				largest = x.getFitness();
				file = i;
			}			
		}
		return file;
	}
	
	public int getSmallestFitnessFile() {
		double smallest = Double.MAX_VALUE;
		int file = -1;
		for (int i = 0; i < tests.size(); i++) {
			XMLTestChromosome x = tests.get(i);
			if (x.getFitness() < smallest) {
				smallest = x.getFitness();
				file = i;
			}			
		}
		return file;
	}
}
