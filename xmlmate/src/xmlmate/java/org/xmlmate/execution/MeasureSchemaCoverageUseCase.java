package org.xmlmate.execution;

import org.evosuite.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.xmlmate.genetics.XMLExistingChromosomeFactory;
import org.xmlmate.genetics.XMLTestSuiteChromosome;
import org.xmlmate.genetics.XMLTestSuiteChromosomeFactory;

import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MeasureSchemaCoverageUseCase implements UseCase {
    private static final Logger logger = LoggerFactory.getLogger(MeasureSchemaCoverageUseCase.class);
    private final File top;

    public MeasureSchemaCoverageUseCase(File top) {
        this.top = top;
    }

    @Override
    public void run() {
        logger.debug("Measuring schema coverage of suites in {}", top);
        if (!top.exists()) {
            logger.error("Directory {} does not exist!", top);
            return;
        }
        File[] dirs = top.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });
        File logfile = new File(top, new SimpleDateFormat("dd_MM_yyyy hh_mm' schema coverage.txt'").format(new Date()));
        try (FileWriter fw = new FileWriter(logfile.getAbsoluteFile(), true)) {
            fw.write("elems\tattrs\ttrans\n");
            double els = 0.0;
            double attrs = 0.0;
            double trans = 0.0;
            double num = 0.0d;
            for (File dir : dirs) {
                XMLExistingChromosomeFactory exFactor = new XMLExistingChromosomeFactory(dir.getAbsoluteFile(), XMLProperties.ROOT_ELEM);
                File[] files = dir.listFiles();
                if (null == files) continue;
                Properties.NUM_TESTS = files.length;
                num += 1.0d;
                XMLTestSuiteChromosomeFactory factory = new XMLTestSuiteChromosomeFactory(exFactor);
                XMLTestSuiteChromosome x = factory.getChromosome();
                double schemaElementCoverage = x.getSchemaElementCoverage();
                els += schemaElementCoverage;
                double schemaAttributeCoverage = x.getSchemaAttributeCoverage();
                attrs += schemaAttributeCoverage;
                double schemaRegexCoverage = x.getSchemaRegexCoverage();
                trans += schemaRegexCoverage;
                String schemaCoverage = String
                    .format(Locale.US, "%.2f,\t%.2f,\t%.2f,\n", schemaElementCoverage, schemaAttributeCoverage, schemaRegexCoverage);
                fw.write(schemaCoverage);
                logger.info("{} in {}", schemaCoverage, dir);
            }
            fw.write("----\t----\t----\n");
            fw.write(String.format(Locale.US, "%.2f\t%.2f\t%.2f\n", els / num, attrs / num, trans / num));
            fw.write(String.format("\nelements:    %d", XMLProperties.SCHEMA_ALL_ELEMENTS.size()));
            fw.write(String.format("\nattributes:  %d", XMLProperties.SCHEMA_ALL_ATTRS.size()));
            fw.write(String.format("\ntransitions: %d", XMLProperties.SCHEMA_ALL_TRANSITIONS.size()));
            fw.flush();
        } catch (IOException e) {
            logger.error("IO error on result file because of {}", e.getMessage());
        }
    }
}
