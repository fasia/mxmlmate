package org.xmlmate.genetics;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dk.brics.automaton.Transition;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSElementDeclaration;
import org.evosuite.Properties;
import org.evosuite.ga.*;
import org.evosuite.localsearch.LocalSearchObjective;
import org.evosuite.testcase.ExecutionResult;
import org.evosuite.testsuite.AbstractTestSuiteChromosome;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class XMLTestSuiteChromosome extends AbstractTestSuiteChromosome<XMLTestChromosome> {

    private static final long serialVersionUID = -6950670819692732483L;
    private static final Logger logger = LoggerFactory.getLogger(XMLTestSuiteChromosome.class);
    private static final List<SecondaryObjective> secondaryObjectives = new ArrayList<>(2);
    public static final SelectionFunction<XMLTestChromosome> selectionFunction = new TournamentSelection<>();
    public static boolean fitnessMaximization = false;
    private double regexCoverage = 0.0d;
    private double elemCoverage = 0.0d;
    private double attrCoverage = 0.0d;
    public static Stopwatch mutationClock = Stopwatch.createUnstarted();

    private static final XMLCrossOverFunction crossoverFunction = new XMLCrossOverFunction();
    private static ExecutorService mutatorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactoryBuilder().setNameFormat("Mutator %d").build());
    private static ExecutorService xoverService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactoryBuilder().setNameFormat("Xover %d").build());

    private static class Mutation implements Callable<List<XMLTestChromosome>> {

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

    private static class Crossover implements Callable<List<XMLTestChromosome>> {
        private final static ExecutorService cloneService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactoryBuilder().setNameFormat("Cloner %d").build());

        private final XMLTestChromosome parent1;
        private final XMLTestChromosome parent2;

        public Crossover(XMLTestChromosome parent1, XMLTestChromosome parent2) {
            this.parent1 = parent1;
            this.parent2 = parent2;
        }

        @Override
        public List<XMLTestChromosome> call() throws Exception {
            Future<XMLTestChromosome> futureOffspring1 = cloneService.submit(new Clone(parent1));
            Future<XMLTestChromosome> futureOffspring2 = cloneService.submit(new Clone(parent2));
            XMLTestChromosome offspring1 = futureOffspring1.get();
            XMLTestChromosome offspring2 = futureOffspring2.get();
            crossoverFunction.crossOverXMLs(offspring1, offspring2, new HashSet<XSElementDeclaration>());
            return ImmutableList.of(offspring1.size() <= XMLProperties.MAX_XML_SIZE ? offspring1 : parent1, offspring2.size() <= XMLProperties.MAX_XML_SIZE ? offspring2 : parent2);
        }

    }

    private static class Clone implements Callable<XMLTestChromosome> {
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

    @Override
    public boolean localSearch(LocalSearchObjective<? extends Chromosome> objective) {
        logger.trace("Local search on suite");
        double oldFitness = getFitness();
        boolean changed = false;

        // store clones of tests in case of an unfavorable mutation outcome
        ArrayList<XMLTestChromosome> savedClones = new ArrayList<>(tests.size());
        List<Future<XMLTestChromosome>> taskResults = new ArrayList<Future<XMLTestChromosome>>(tests.size());
        for (XMLTestChromosome xmlTestChromosome : tests)
            taskResults.add(mutatorService.submit(new Clone(xmlTestChromosome)));
        try {
            for (Future<XMLTestChromosome> futureResult : taskResults)
                savedClones.add(futureResult.get());
        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        for (XMLTestChromosome x : tests) {
            changed |= x.localSearch(objective);
        }
        if (!changed)
            return false;

        @SuppressWarnings("unchecked")
        FitnessFunction<XMLTestSuiteChromosome> fitnessFunction = (FitnessFunction<XMLTestSuiteChromosome>) objective.getFitnessFunction();

        // note that this also updates the current fitness
        double delta = fitnessFunction.getFitness(this) - oldFitness;
        boolean better = fitnessFunction.isMaximizationFunction() ? delta > 0 : delta < 0;
        if (!better) { // restore if no improvement
            setFitness(oldFitness);
            tests = savedClones;
        }
        return better;
    }

    public static void addSecondaryObjective(SecondaryObjective objective) {
        logger.debug("Added secondary objective {}", objective.getClass().getName());
        secondaryObjectives.add(objective);
    }

    public static void removeSecondaryObjective(SecondaryObjective objective) {
        for (Iterator<SecondaryObjective> iterator = secondaryObjectives.iterator(); iterator.hasNext(); ) {
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
        assert !isChanged() : "Cannot shallowly clone changed suites!";
        XMLTestSuiteChromosome inst = new XMLTestSuiteChromosome();
        inst.testChromosomeFactory = testChromosomeFactory;
        inst.age = age;
        inst.coverage = coverage;
        inst.setFitness(getFitness());
        inst.regexCoverage = regexCoverage;
        inst.elemCoverage = elemCoverage;
        inst.attrCoverage = attrCoverage;
        inst.tests = new ArrayList<>(tests); // shallowly copy the xml trees
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

    private static final Comparator<XMLTestChromosome> xmlCompare = new Comparator<XMLTestChromosome>() {

        @Override
        public int compare(XMLTestChromosome o1, XMLTestChromosome o2) {
            int diff = (int) o1.getFitness() - (int) o2.getFitness();
            return fitnessMaximization ? -diff : diff;
        }
    };

    @Override
    public void mutate() {
        mutationClock.start();
        Collections.sort(tests, xmlCompare);
        List<XMLTestChromosome> newTests = new ArrayList<>(tests.size());
        // elite
        newTests.add(tests.get(0));


        // delete test cases?
        ListIterator<XMLTestChromosome> li = tests.listIterator(tests.size());
        while(li.hasPrevious()) {
            li.previous();
            if (Randomness.nextDouble() < 0.01 && tests.size() > 2) {
                logger.trace("Removing an xml file");
                li.remove();
                setChanged(true);
            }
        }

        int oldSize = tests.size() - 1; // -1 for elite

        List<Future<List<XMLTestChromosome>>> taskResults = new LinkedList<>();
        int chosen = 0;
        for (int i = 0; i < oldSize; ++i) {
            if ((Randomness.nextDouble() < 0.2d) && (chosen <= oldSize - 2)) { // 20% crossover
                int index1 = selectionFunction.getIndex(tests);
                int index2 = selectionFunction.getIndex(tests);
                chosen += 2;
                taskResults.add(xoverService.submit(new Crossover(tests.get(index1), tests.get(index2))));
                i += 1;
            }
            if ((Randomness.nextDouble() < 0.30d) && (chosen < oldSize)) { // 30% mutation
                int index = selectionFunction.getIndex(tests);
                chosen += 1;
                taskResults.add(mutatorService.submit(new Mutation(tests.get(index))));
            }
        }

        // fill up unchanged
        newTests.addAll(selectionFunction.select(tests, oldSize - chosen));

        try {
            for (Future<List<XMLTestChromosome>> futureTest : taskResults)
                for (XMLTestChromosome x : futureTest.get())
                    newTests.add(x);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Exception while evaluating a test", e);
        }
        tests = newTests;
        setChanged(true);

        // Add new test cases
        for (int count = 1; (Randomness.nextDouble() <= StrictMath.pow(Properties.P_TEST_INSERTION, count)) && (size() < Properties.MAX_SIZE); count++) {
            logger.trace("Adding new xml file. (Current size is {})", size());
            tests.add(testChromosomeFactory.getChromosome());
            setChanged(true);
        }
        mutationClock.stop();
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
