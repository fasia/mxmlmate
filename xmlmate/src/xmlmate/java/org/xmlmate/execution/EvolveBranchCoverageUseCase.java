package org.xmlmate.execution;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import net.sf.corn.cps.CPScanner;
import net.sf.corn.cps.ClassFilter;
import net.sf.corn.cps.CombinedFilter;

import org.evosuite.Properties;
import org.evosuite.TestGenerationContext;
import org.evosuite.coverage.exception.ExceptionCoverageSuiteFitness;
import org.evosuite.ga.Chromosome;
import org.evosuite.ga.CrossOverFunction;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.MaxSizeBloatControl;
import org.evosuite.ga.MinimizeSizeSecondaryObjective;
import org.evosuite.ga.SelectionFunction;
import org.evosuite.ga.SteadyStateGA;
import org.evosuite.ga.TournamentSelection;
import org.evosuite.ga.stoppingconditions.MaxTimeStoppingCondition;
import org.evosuite.ga.stoppingconditions.StoppingCondition;
import org.evosuite.ga.stoppingconditions.ZeroFitnessStoppingCondition;
import org.evosuite.testsuite.MinimizeTotalLengthSecondaryObjective;
import org.evosuite.testsuite.RelativeSuiteLengthBloatControl;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.xmlmate.genetics.SingletonPopulationGA;
import org.xmlmate.genetics.SingletonPopulationMemoryAccessFitnessFunction;
import org.xmlmate.genetics.XMLCrossOverFunction;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;
import org.xmlmate.monitoring.BothCoveragesMonitor;
import org.xmlmate.monitoring.EventRecounter;
import org.xmlmate.util.ClassFilterAdapter;

public class EvolveBranchCoverageUseCase implements UseCase {
    private static final Logger logger = LoggerFactory.getLogger(EvolveBranchCoverageUseCase.class);
    private final XMLTestSuiteChromosomeFactory factory;

    public EvolveBranchCoverageUseCase(XMLTestSuiteChromosomeFactory factory) {
        this.factory = factory;
    }

    protected FitnessFunction chooseFitnessFunction() {
        return new ExceptionCoverageSuiteFitness();
    }

    protected void initialize() {
    	// manage classes
    	loadClasses();    	
    }
    
	protected void setupGAAdditions(SteadyStateGA<XMLTestSuiteChromosome> ga) {
        // selection function
        SelectionFunction<XMLTestSuiteChromosome> selectionFunction = new TournamentSelection<>();
        selectionFunction.setMaximize(false);
        ga.setSelectionFunction(selectionFunction);
        // monitoring
		ga.addListener(new BothCoveragesMonitor());
        ga.addListener(new EventRecounter());
	}
    
    @Override
    public void run() {
    	initialize();
        SteadyStateGA<XMLTestSuiteChromosome> ga = chooseGA(factory);

        // fitness function
        FitnessFunction fitnessFunction = chooseFitnessFunction();
        ga.setFitnessFunction(fitnessFunction);
        
        // stopping conditions
        ZeroFitnessStoppingCondition zeroStop = new ZeroFitnessStoppingCondition();
		ga.setStoppingCondition(zeroStop); // this resets default stopping conditions
        if (fitnessFunction.isMaximizationFunction())
        	ga.removeStoppingCondition(zeroStop); // zero fitness stopping condition would stop the search immediately 
        StoppingCondition stoppingCondition = new MaxTimeStoppingCondition();
        stoppingCondition.setLimit(XMLProperties.GLOBAL_TIMEOUT);
        ga.addStoppingCondition(stoppingCondition);

        //secondary objectives
        XMLTestSuiteChromosome.addSecondaryObjective(new MinimizeSizeSecondaryObjective());
        XMLTestSuiteChromosome.addSecondaryObjective(new MinimizeTotalLengthSecondaryObjective());

        XMLTestChromosome.addSecondaryObjective(new MinimizeSizeSecondaryObjective());

        // crossover function
        CrossOverFunction crossoverFunction = new XMLCrossOverFunction();
        ga.setCrossOverFunction(crossoverFunction);

        // bloat control
        ga.addBloatControl(new RelativeSuiteLengthBloatControl());
        ga.addBloatControl(new MaxSizeBloatControl());

        // progress monitors
        setupGAAdditions(ga);

        try {
            System.setErr(new PrintStream(new FileOutputStream("/dev/null")));
        } catch (FileNotFoundException ignored) {
        }
        // start actual search
        ga.generateSolution();

        freeResources();
        
        // report
        Chromosome solution = ga.getBestIndividual();
        int gaAge = ga.getAge();
        logger.info("Search finished with fitness {} after {} generations.", solution.getFitness(), gaAge);
        writeSolution((XMLTestSuiteChromosome) solution, gaAge);

    	logger.debug("Cumulative time spent cloning:    {}.", XMLTestSuiteChromosome.cloningClock);
    	logger.debug("Cumulative time spent mutating:   {}.", XMLTestSuiteChromosome.mutationClock);
    	if (Properties.POPULATION == 1)
			logger.debug("Cumulative time spent evaluating: {}.", SingletonPopulationMemoryAccessFitnessFunction.evaluationClock);
        
        // for some reason the process doesn't terminate on its own
        System.exit(0);
    }

	protected SteadyStateGA<XMLTestSuiteChromosome> chooseGA(XMLTestSuiteChromosomeFactory fac) {
		return new SteadyStateGA<>(fac);
	}

	protected void freeResources() {
		// nop
	}

	private static void loadClasses() {
        logger.info("Scanning classes...");
        List<Class<?>> classes = CPScanner.scanClasses(new CombinedFilter().
                appendFilter(new ClassFilter().packageName(Properties.TARGET_CLASS_PREFIX + '*'))
                .appendFilter(new ClassFilterAdapter())
                .combineWithAnd());
        for (Class<?> c : classes) {
            logger.debug("Adding class {}", c.getName());
            try {
                Class.forName(c.getName(), true, TestGenerationContext.getInstance().getClassLoaderForSUT());
            } catch (ClassNotFoundException | LinkageError e) {
                logger.warn("Could not load class {} due to ", c, e);
            }
        }
        logger.info("Found {} matching classes.", classes.size());
        if (classes.isEmpty())
            System.exit(1);
    }

    private static void writeSolution(XMLTestSuiteChromosome solution, int gaAge) {
        File resultFolder = solution.writeToFiles();
        File f = new File(resultFolder, "info.txt");
        try (BufferedWriter bw = new BufferedWriter(new PrintWriter(f)); PrintWriter pw = new PrintWriter(bw)) {
            bw.write("XMLMate parameters: ");
            bw.write(XMLProperties.getRunParams());
            bw.newLine();
            Collection<Throwable> exceptions = solution.getAllUniqueThrownExceptions();
            if (!exceptions.isEmpty()) {
                bw.write("Produced exceptions:");
                bw.newLine();
                for (Throwable e : exceptions) {
                    e.printStackTrace(pw);
                    bw.newLine();
                }
                bw.newLine();
            }
            bw.write(String.format("Result's generation: %d", solution.getAge()));
            bw.newLine();
            bw.write("Overall generations: " + gaAge);
            bw.newLine();
            bw.write(String.format(Locale.US, "Fitness: %.4f", solution.getFitness()));
            bw.newLine();
            bw.write(String.format(Locale.US, "Coverage: %.4f", solution.getCoverage()));
            bw.newLine();
            bw.write(String.format("Seed: %d", Randomness.getSeed()));
            bw.newLine();
            bw.write(String.format("Largest fitness file is %d", solution.getLargestFitnessFile()));
            bw.newLine();
            bw.write(String.format("Smallest fitness file is %d", solution.getSmallestFitnessFile()));
            bw.newLine();
        } catch (IOException e) {
            logger.error("Error while writing results!", e);
        }
    }
}
