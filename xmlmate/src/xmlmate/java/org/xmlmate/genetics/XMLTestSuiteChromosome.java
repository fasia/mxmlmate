package org.xmlmate.genetics;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSElementDeclaration;
import org.evosuite.Properties;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.SecondaryObjective;
import org.evosuite.localsearch.LocalSearchObjective;
import org.evosuite.testcase.ExecutionResult;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import com.google.common.collect.ImmutableList;

import dk.brics.automaton.Transition;

public class XMLTestSuiteChromosome extends AbstractTestSuiteChromosome<XMLTestChromosome> {

	private static final long serialVersionUID = -6950670819692732483L;
    private static final Logger logger = LoggerFactory.getLogger(XMLTestSuiteChromosome.class);
    private static final List<SecondaryObjective> secondaryObjectives = new ArrayList<>(2);
    private double regexCoverage = 0.0d;
    private double elemCoverage = 0.0d;
    private double attrCoverage = 0.0d;
        
    private static final XMLCrossOverFunction crossoverFunction = new XMLCrossOverFunction();
    private static ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private class Mutation implements Callable<List<XMLTestChromosome>> {
    	private final XMLTestChromosome original;
		public Mutation(XMLTestChromosome original) {
			this.original = original;
		}
    	
		@Override
		public List<XMLTestChromosome> call() throws Exception {
			XMLTestChromosome clone = new XMLTestChromosome(original);
			clone.mutate();
			if (clone.isChanged() && clone.size() <= XMLProperties.MAX_XML_SIZE)
				return Collections.singletonList(clone);
			return Collections.singletonList(original);
		}
    	
    }
    
    private class Crossover implements Callable<List<XMLTestChromosome>> {
    	
    	private final XMLTestChromosome parent1;
    	private final XMLTestChromosome parent2;

		public Crossover(XMLTestChromosome parent1, XMLTestChromosome parent2) {
			this.parent1 = parent1;
			this.parent2 = parent2;
		}

		@Override
		public List<XMLTestChromosome> call() throws Exception {
			XMLTestChromosome offspring1 = new XMLTestChromosome(parent1);
			XMLTestChromosome offspring2 = new XMLTestChromosome(parent2);
			crossoverFunction.crossOverXMLs(offspring1, offspring2, new HashSet<XSElementDeclaration>());
			return ImmutableList.of(
					offspring1.size() <= XMLProperties.MAX_XML_SIZE ? offspring1: parent1, 
					offspring2.size() <= XMLProperties.MAX_XML_SIZE ? offspring2: parent2);
		}
    	
    }
    
    private class Clone implements Callable<XMLTestChromosome> {
    	private final XMLTestChromosome original;
    	public Clone(XMLTestChromosome original) {
    		this.original = original;
		}
    	
		@Override
		public XMLTestChromosome call() throws Exception {
			return new XMLTestChromosome(original);
		}
    	
    }
    
    // private constructor used for parallel cloning
    private XMLTestSuiteChromosome() {
		
	}
    
    public XMLTestSuiteChromosome(ChromosomeFactory<XMLTestChromosome> testChromosomeFactory) {
        super(testChromosomeFactory);
        populate();
    }

    /*
    public XMLTestSuiteChromosome(XMLTestSuiteChromosome other) {
        super(other);
        regexCoverage = other.regexCoverage;
        elemCoverage = other.elemCoverage;
        attrCoverage = other.attrCoverage;
        age = other.age;
        setChanged(other.isChanged());
    }
    */

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
        XMLTestSuiteChromosome inst = new XMLTestSuiteChromosome();
        inst.testChromosomeFactory = testChromosomeFactory;
        inst.age = age;
        inst.coverage = coverage;
        inst.setFitness(getFitness());
        inst.regexCoverage = regexCoverage;
        inst.elemCoverage = elemCoverage;
        inst.attrCoverage = attrCoverage;
        List<Callable<XMLTestChromosome>> tasks = new ArrayList<>(tests.size());
        for (XMLTestChromosome xmlTestChromosome : tests) {
			tasks.add(new Clone(xmlTestChromosome));
		}
		try {
			List<Future<XMLTestChromosome>> taskResults = executor.invokeAll(tasks);
			for (Future<XMLTestChromosome> futureResult : taskResults)
				inst.addTest(futureResult.get());
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        inst.setChanged(isChanged());
        return inst;
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
//    	Stopwatch mutationWatch = Stopwatch.createStarted();
        // delete testcases?
        for (Iterator<XMLTestChromosome> iterator = tests.iterator(); iterator.hasNext();) {
            iterator.next();
            if (Randomness.nextDouble() < 0.01) {
            	logger.trace("Removing an xml file");
                iterator.remove();
                setChanged(true);
            }
        }
        
        int oldSize = tests.size();
        
		Randomness.shuffle(tests);
		
		List<Callable<List<XMLTestChromosome>>> tasks = new LinkedList<>();
		List<XMLTestChromosome> newTests = new ArrayList<>(oldSize);
		for (int i = 0; i < oldSize;) {
			XMLTestChromosome p1 = tests.get(i);
			if (Randomness.nextDouble() <= 0.50d && i + 1 < oldSize) { // 50% crossover
				XMLTestChromosome p2 = tests.get(i + 1);
				tasks.add(new Crossover(p1, p2));
				i += 2;
			} else { // 50% mutation
				tasks.add(new Mutation(p1));
				i += 1;
			} 
		}
		try {
			List<Future<List<XMLTestChromosome>>> taskResults = executor.invokeAll(tasks);
			for (Future<List<XMLTestChromosome>> futureTest : taskResults)
				for (XMLTestChromosome x : futureTest.get())
					newTests.add(x);
		} catch (InterruptedException | ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		tests = newTests;
		setChanged(true);
		
        // Add new test cases
        for (int count = 1; Randomness.nextDouble() <= StrictMath.pow(Properties.P_TEST_INSERTION, count) && size() < Properties.MAX_SIZE; count++) {
        	logger.trace("Adding new xml file");
            tests.add(testChromosomeFactory.getChromosome());
            setChanged(true);
        }
        
//        mutationWatch.stop();
//        mutationTime += mutationWatch.elapsed(TimeUnit.SECONDS);
//        logger.info("Mutating took {}", mutationWatch);
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
