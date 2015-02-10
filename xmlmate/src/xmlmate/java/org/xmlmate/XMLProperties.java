package org.xmlmate;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.Transition;
import nu.xom.Element;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.impl.xs.util.XSGrammarPool;
import org.apache.xerces.xs.XSAttributeDeclaration;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSModel;
import org.evosuite.Properties;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.execution.*;
import org.xmlmate.genetics.BasicBlockCoverageFitnessFunction;
import org.xmlmate.genetics.MemoryAccessFitnessFunction;
import org.xmlmate.genetics.SingletonMemoryAccessFitnessFunction;
import org.xmlmate.genetics.XMLExistingChromosomeFactory;
import org.xmlmate.genetics.XMLTestChromosome;
import org.xmlmate.genetics.XMLTestChromosomeFactory;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;
import org.xmlmate.util.InstrumentationManager;
import org.xmlmate.xml.AwareElement;
import org.xmlmate.xml.AwareInstantiator;
import org.xmlmate.xml.metrics.SchemaAllVisitor;
import org.xmlmate.xml.metrics.SchemaRegexVisitor;
import org.xmlmate.xml.metrics.SchemaTraverser;

import javax.xml.namespace.QName;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("PublicField")
public class XMLProperties {
    // ******************** static system components ********************
    private static final Logger logger = LoggerFactory.getLogger(XMLProperties.class);
    public static final String DEL_NS = "http://delete.me/";
    public static final XSGrammarPool grammarPool = new XSGrammarPool();
    public static final Set<XSElementDeclaration> SCHEMA_ALL_ELEMENTS = new HashSet<>(30);
    public static final Set<XSAttributeDeclaration> SCHEMA_ALL_ATTRS = new HashSet<>(80);
    public static final Set<Transition> SCHEMA_ALL_TRANSITIONS = new HashSet<>(500);
    public static InstrumentationManager instrManager;
    public static String RUN_NAME = new SimpleDateFormat(" dd.MM.yyyy HH_mm").format(new Date());
    public static String RUN_PARAMS = "";

    // ******************** static evolution params ********************
    // the sum of the following probabilities must not exceed 1
    public static final double FILE_MUTATION_PROB = 0.40d;
    public static final double FILE_CROSSOVER_PROB = 0.40d;
    public static final double FILE_REPLACEMENT_PROB = 0.05d;
    public static final double FILE_DELETION_PROB = 0.02d;

    static {
        assert FILE_MUTATION_PROB + FILE_CROSSOVER_PROB + FILE_REPLACEMENT_PROB + FILE_DELETION_PROB <= 1d;
    }

    // consider meta-heuristics
    public static int OPTIONAL_DEPTH = 5;
    public static int MAX_XML_SIZE = 800;
    public static int MIN_STRING_LENGTH = 1;
    public static int MAX_STRING_LENGTH = 50;
    public static int MAX_ELEMENTS_GENERATED = 5;
    public static int MIN_LIST_ITEMS_GENERATED = 1;
    public static int MAX_LIST_ITEMS_GENERATED = 7;

    public static String TARGET_NAMESPACE = null;
    public static String ROOT_ELEM = null;
    public static String SCHEMA_PATH = null;
    public static XSModel SCHEMA_INSTANCE = null;
    public static File OUTPUT_PATH = new File("out");
    public static String FILE_EXTENSION = "xml";
    public static int GLOBAL_TIMEOUT = 300;

    // ******************** initialization routines ********************

    private XMLProperties() {
        // prohibit creation
    }

    private static Options createOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "Show this message.");
        options.addOption("s", "schema", true, "Location of the schema file.");
        options.addOption("c", "class", true, "Fully qualified name of the class containing the main method.");
        options.addOption("p", "prefix", true, "Prefix of the classes to be instrumented.");
        int possibleExcludes = 3; // is this enough?
        Option exclude = new Option("i", "ignore-packages", true, "Packages to exclude from instrumenting. Max " + possibleExcludes);
        exclude.setArgs(possibleExcludes);
        options.addOption(exclude);
        options.addOption("x", "extension", true, "File extension of the generated files. Default is " + FILE_EXTENSION);
        options.addOption("single", true, "Generate a single xml instance into the given file.");
        options.addOption("measure", true, "Measure the schema coverage of all suites in the given directory.");
        options.addOption("schemaCoverage", false, "Use the schema coverage as sole fitness function.");
        options.addOption("hybridCoverage", false, "Use the hybrid coverage as fitness function maximizing both branch and schema coverages.");
        int maxTargetBinaryArgs = 5;
        Option bblCoverage = new Option("bblCoverage",true,"Use PIN instrumentation framework and measure basic block coverage. Followed by working directory, path to PIN, path to PINtool, and path to target binary and any arguments. (max "+maxTargetBinaryArgs+")");
        bblCoverage.setArgs(4 + maxTargetBinaryArgs);
        options.addOption(bblCoverage);
        options.addOption("memCoverage", false, "Use with PIN to maximize memory accesses.");
        options.addOption("r", "root", true, "Root element of the xml tree.");
        options.addOption("a", "samples", true, "Path to samples (file or folder). If given, root must also be given.");
        options.addOption("t", "timeout", true, "Termination time in sec. Default is " + GLOBAL_TIMEOUT);
        options.addOption("tt", "test-timeout", true, "Test case timeout in msec. Default is " + Properties.TIMEOUT);
        options.addOption("population", true, "The population size. default is " + Properties.POPULATION);
        options.addOption("maxfiles", true, "Maximum number of files per suite. Default is " + Properties.MAX_SIZE);
        options.addOption("maxsize", true, "Maximum number of elements per xml file. Default is " + MAX_XML_SIZE);
        options.addOption("elite", true, "Size of elite. must be in [0,population). Default is " + Properties.ELITE);
        options.addOption("out", true, "Relative path to temporary folder for program output. Default is " + OUTPUT_PATH);
        options.addOption("depth", true, "Maximum recursion depth for optional xml elements. Default is " + OPTIONAL_DEPTH);
        options.addOption("seed", true, "Seed for the randomness. If not given, one will be auto-generated.");
        return options;
    }

    private static int getIntOrDefault(CommandLine line, String opt, int def) {
        return getIntOrDefault(line, opt, def, 1);
    }

    private static int getIntOrDefault(CommandLine line, String opt, int def, int min) {
        String optionString = opt.toLowerCase();
        if (line.hasOption(optionString))
            try {
                int intValue = Integer.parseInt(line.getOptionValue(optionString));
                if (intValue >= min)
                    return intValue;
                else
                    logger.warn("{} must be >= {}! Falling back to {}", opt, min, def);
            } catch (NumberFormatException e) {
                logger.warn("{} size invalid! Falling back to {}", opt, def);
            }
        else
            logger.info("{} parameter not given. Using default value of {}", opt, def);
        return def;
    }

    private static boolean hasRequiredParams(CommandLine line) {
        if (!line.hasOption("schema")) {
            logger.error("No path to schema provided!");
            return false;
        }
        if (line.hasOption("bblCoverage")) {
        	if (line.hasOption("prefix"))
        		logger.warn("prefix matching option not yet implemented for bblCoverage use case!"); // TODO implement this option
        	if (line.hasOption("ignore-packages"))
        		logger.warn("ignore-packages matching option not yet implemented for bblCoverage use case!"); // TODO implement this option
			if (line.hasOption("class")) {
				logger.error("You may not use the -class option with -bblCoverage!");
				return false;
			}
			if (line.getOptionValues("bblCoverage").length < 2) {
				logger.error("Not enough values provided with the -bblCoverage option!");
				return false;
			}
			return true;
		}
        if (line.hasOption("memCoverage")) {
        	// TODO add more warnings and sanity checks
        	if (line.hasOption("class")) {
        		logger.error("You may not use the -class options with the -memCoverage option!");
        		return false;
        	}
        	return true;
        }
        boolean hasRoot = line.hasOption("root");
        if (line.hasOption("class") && line.hasOption("prefix"))
            return !line.hasOption("samples") || hasRoot;
        return hasRoot && (line.hasOption("single") || line.hasOption("measure") || line.hasOption("schemaCoverage"));
    }

    private static void printHelpAndQuit(Options options) {
        new HelpFormatter().printHelp("XMLMate", options);
        System.exit(1);
    }

    public static String getRunParams() {
        // TODO use a stringbuilder
//        String.format("schema:%s root:%s prefix:%s driver:%s", SCHEMA_PATH, ROOT_ELEM, Properties.TARGET_CLASS_PREFIX, Properties.TARGET_CLASS);
        return RUN_PARAMS;
    }

    public static UseCase determineUseCase(String... args) {
        RUN_PARAMS = StringUtils.join(args, ' '); // TODO format actually used values for all (relevant) params + seed
        Options options = createOptions();
        CommandLineParser parser = new GnuParser();
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
        } catch (ParseException e) {
            logger.error("Could not parse the command line arguments because of {}", e.getMessage());
            printHelpAndQuit(options);
        }
        assert null != line;
        // ******************** System params ********************
        // check necessary parameters
        if (line.hasOption("help") || !hasRequiredParams(line)) {
            printHelpAndQuit(options);
        }
        // check schema file
        File schemaFile = new File(line.getOptionValue("schema"));
        if (!schemaFile.exists() || !schemaFile.isFile()) {
            logger.error("Could not find the schema at {}", schemaFile);
            System.exit(1);
        }
        SCHEMA_PATH = schemaFile.toURI().toString();
        RUN_NAME = FilenameUtils.getBaseName(SCHEMA_PATH) + RUN_NAME;

        //check seed
        if (line.hasOption("seed")) {
            Properties.RANDOM_SEED = Long.parseLong(line.getOptionValue("seed"));
            Randomness.getInstance();
        } else {
            Randomness.getInstance();
            RUN_PARAMS += " -seed " + Randomness.getSeed();
        }

        // file extension
        if (line.hasOption("extension")) {
            String ext = line.getOptionValue("extension");
            if (Pattern.matches("[\\w\\.\\-]+", ext))
                FILE_EXTENSION = ext;
            else
                logger.warn("Illegal file extension! Falling back to {}", FILE_EXTENSION);
        }
        FILE_EXTENSION = FILE_EXTENSION.replaceFirst("^\\.*", "."); // make sure it starts with a single dot
        // the next two are needed in case of "single" or "measure" mode
        // max tree size
        MAX_XML_SIZE = getIntOrDefault(line, "Maxsize", MAX_XML_SIZE);
        // optional depth
        OPTIONAL_DEPTH = getIntOrDefault(line, "Depth", OPTIONAL_DEPTH);

        File samples = null;
        // root element and its dependents
        if (line.hasOption("root")) {
            ROOT_ELEM = line.getOptionValue("root");
            Matcher matcher = Pattern.compile("(\\{(?<prefix>.+)\\})?(?<elem>.+)").matcher(ROOT_ELEM);
            if (matcher.matches())
                TARGET_NAMESPACE = matcher.group("prefix");
            // check single
            if (line.hasOption("single")) {
                RUN_NAME = "single " + RUN_NAME;
                return new GenerateSingleFileUseCase(new File(line.getOptionValue("single")));
            }
            // check measure
            if (line.hasOption("measure")) {
                RUN_NAME = "measure " + RUN_NAME;
                return new MeasureSchemaCoverageUseCase(new File(line.getOptionValue("measure")));
            }
            // check samples
            if (line.hasOption("samples")) {
                String samplesPath = line.getOptionValue("samples");
                File sampleFile = new File(samplesPath);
                if (sampleFile.exists()) {
                    RUN_NAME = "(samples) " + RUN_NAME;
                    samples = sampleFile;
                } else
                    logger.warn("No samples found in '{}'! Falling back to random generation mode.", samplesPath);
            }
        } else
            logger.warn("No root element specified. Will resort to guessing.");

        if (line.hasOption("class")) {
        	if (line.hasOption("bblCoverage")) {
        		String errorMessage = "Options -class and -bblCoverage are incompatible!";
        		logger.error(errorMessage);
        		throw new RuntimeException(errorMessage);
        	}
        		
            // check driver class
            Properties.TARGET_CLASS = line.getOptionValue("class");
            // check prefixes
            Properties.PROJECT_PREFIX = Properties.TARGET_CLASS_PREFIX = line.getOptionValue("prefix");
            instrManager = new InstrumentationManager(Properties.TARGET_CLASS_PREFIX, line.getOptionValues("ignore-packages"));
            RUN_NAME = Properties.TARGET_CLASS_PREFIX + ' ' + RUN_NAME;
        }

        // output folder
        if (line.hasOption("out"))
            OUTPUT_PATH = new File(line.getOptionValue("out"));
        else
            logger.info("No output directory given. Falling back to \"{}\"", OUTPUT_PATH);
        if ((!OUTPUT_PATH.exists() || !OUTPUT_PATH.mkdirs()) && !OUTPUT_PATH.isDirectory()) {
            logger.error("Cannot create output directory \"{}\"", OUTPUT_PATH.getAbsoluteFile());
            System.exit(1);
        }
        // ******************** Evolutionary params ********************
        // population
        Properties.POPULATION = getIntOrDefault(line, "Population", Properties.POPULATION);
        // elite
        Properties.ELITE = getIntOrDefault(line, "Elite", Properties.ELITE, 0);
        // max files
        Properties.MAX_SIZE = getIntOrDefault(line, "Maxfiles", Properties.MAX_SIZE);
        // per file timeout
        Properties.TIMEOUT = getIntOrDefault(line, "Test-timeout", Properties.TIMEOUT);
        // global timeout
        GLOBAL_TIMEOUT = getIntOrDefault(line, "Timeout", GLOBAL_TIMEOUT);
        // check timeout sanity
        if (Properties.TIMEOUT * Properties.POPULATION * Properties.MAX_SIZE > GLOBAL_TIMEOUT * 10)
            logger.warn("Unreasonable timeout settings!");
        // construct a XML instance factory
        ChromosomeFactory<XMLTestChromosome> testFactory = null == samples ? new XMLTestChromosomeFactory(ROOT_ELEM) : new XMLExistingChromosomeFactory(samples, ROOT_ELEM);
        XMLTestSuiteChromosomeFactory factory = new XMLTestSuiteChromosomeFactory(testFactory);

        // check schema coverage guidance mode
        if (line.hasOption("schemaCoverage")) {
            RUN_NAME = "schemaCoverage " + RUN_NAME;
            return new EvolveSchemaCoverageUseCase(factory);
        }
        if (line.hasOption("hybridCoverage")) {
            RUN_NAME = "hybridCoverage " + RUN_NAME;
            return new EvolveHybridCoverageUseCase(factory);
        }
        if (line.hasOption("bblCoverage")) {
        	RUN_NAME = "bblCoverage " + RUN_NAME;
        	String[] paths = line.getOptionValues("bblCoverage");
        	List<String> commands = new LinkedList<>(Arrays.asList(paths));
        	String workDirPath = commands.remove(0);
        	assert commands.size() > 0;
        	File workDir = new File(workDirPath);
        	assert workDir.isDirectory();
        	return new BinaryBackendUseCase(factory, new BasicBlockCoverageFitnessFunction());
        }
        if (line.hasOption("memCoverage")) {
			RUN_NAME = "memCoverage " + RUN_NAME;
			if (Properties.POPULATION == 1) 
				return new SingletonPopulationBackendUseCase(factory, new SingletonMemoryAccessFitnessFunction());
			return new BinaryBackendUseCase(factory, new MemoryAccessFitnessFunction());
        }
        RUN_NAME = "branchCoverage " + RUN_NAME;
        return new EvolveBranchCoverageUseCase(factory);
    }

    private static void parseModel() {
        logger.info("Parsing schema...");
        XMLSchemaLoader loader = new XMLSchemaLoader();
        loader.setProperty("http://apache.org/xml/properties/internal/grammar-pool", grammarPool);
        long start = System.currentTimeMillis();
        SCHEMA_INSTANCE = loader.loadURI(SCHEMA_PATH);
        if (null == SCHEMA_INSTANCE) {
            logger.error("Error: Could not load the schema!");
            System.exit(1);
        }
        logger.info("Schema parsed in {} sec.", (System.currentTimeMillis() - start) / 1000d);
    }

    private static void prebuildAutomata() {
        logger.info("Prebuilding automata...");
        Automaton.setAllowMutate(false);
        Automaton.setMinimizeAlways(true);
        long start = System.currentTimeMillis();
        XMLTestChromosomeFactory factory = new XMLTestChromosomeFactory(ROOT_ELEM);
        for (int i = 0; i < 100; i++)
            factory.getChromosome(); // this will instantiate and cache some automata lazily
        logger.info("Done in {} sec.", (System.currentTimeMillis() - start) / 1000d);
    }

    private static void traverseSchema() {
        logger.info("Collecting schema metrics...");
        long start = System.currentTimeMillis();
        SchemaTraverser traverser = new SchemaTraverser();
        Element root = AwareInstantiator.generate(QName.valueOf(ROOT_ELEM)).getRootElement();
        traverser.traverse(((AwareElement) root).getDecl(), new SchemaAllVisitor(), new SchemaRegexVisitor());
        logger.info("Done in {} sec.", (System.currentTimeMillis() - start) / 1000d);
    }

    public static void initialize() {
        logger.info("Initializing...");
        parseModel();
        prebuildAutomata();
        traverseSchema();
        logger.info("Initialization complete");
    }

}
