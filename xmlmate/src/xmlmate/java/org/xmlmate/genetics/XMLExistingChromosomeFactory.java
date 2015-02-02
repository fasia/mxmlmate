package org.xmlmate.genetics;

import nu.xom.Builder;
import nu.xom.canonical.Canonicalizer;
import org.apache.xerces.parsers.StandardParserConfiguration;
import org.apache.xerces.util.DefaultErrorHandler;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.apache.xerces.xni.parser.XMLParseException;
import org.apache.xerces.xni.parser.XMLParserConfiguration;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;
import org.xmlmate.xml.AwareDocument;
import org.xmlmate.xml.AwareElement;
import org.xmlmate.xml.AwareNodeFactory;
import org.xmlmate.xml.SchemaPatcher;

import java.io.*;
import java.util.HashMap;

public class XMLExistingChromosomeFactory extends XMLTestChromosomeFactory {
    private static final Logger logger = LoggerFactory.getLogger(XMLExistingChromosomeFactory.class);
    private static final long serialVersionUID = 4007249179669349055L;
    private final XMLParserConfiguration parser;
    private final HashMap<String, XMLTestChromosome> chromosomeCache = new HashMap<>();
    private final FileFilter ff = new FileFilter() {
        @Override
        public boolean accept(File p) {
            return p.isFile(); // && p.getName().endsWith(Properties.XML_FILE_EXTENSION)
        }
    };
    private final NOPErrorHandler nopHandler = new NOPErrorHandler();
    private int currentFileIndex = -1;
    private final File f;

    public XMLExistingChromosomeFactory(File samplesFile, String root) {
        super(root);
        f = samplesFile;
        assert null != f && f.exists();
        if (f.isDirectory()) {
            File[] children = f.listFiles(ff);
            if (null == children || children.length == 0)
                throw new IllegalArgumentException("Empty directory " + samplesFile);
        }
        parser = new StandardParserConfiguration();
        parser.setProperty("http://apache.org/xml/properties/internal/grammar-pool", XMLProperties.grammarPool);
        parser.setFeature("http://xml.org/sax/features/validation", true);
        parser.setFeature("http://apache.org/xml/features/validation/schema", true);
        parser.setFeature("http://apache.org/xml/features/validation/schema-full-checking", true);
    }

    @Override
    public XMLTestChromosome getChromosome() {
        // TODO rework to be more efficient, but beware dynamic file system changes
        if (f.exists())
            if (f.isFile())
                return genChromosome(f);
            else if (f.isDirectory()) {
                File[] files = f.listFiles(ff);
                if (null != files && files.length > 0)
                    return genChromosome(files[++currentFileIndex % files.length]);
                // return genChromosome(files[Randomness.nextInt(files.length)]);
                // otherwise fall through to super.implementation
            }
        logger.warn("No samples found at {}. Creating a fresh chromosome instead!", f.getPath());
        return super.getChromosome();
    }

    /**
     * Generates a chromosome from an existing file.
     */
    private XMLTestChromosome genChromosome(File file) {
        XMLTestChromosome chrom;
        if (null != (chrom = chromosomeCache.get(file.getPath()))) {
            XMLTestChromosome clone = new XMLTestChromosome(chrom); // breed a fresh clone
            if (Randomness.nextDouble() < 0.10D) // simulate natural impurities in the cloning process
                clone.mutate();
            return clone;
        }

        // Phase 1: Create XML Tree
        AwareNodeFactory fac = new AwareNodeFactory();
        Builder xomBuilder = new Builder(fac);
        AwareDocument doc;
        try {
            doc = (AwareDocument) xomBuilder.build(file);
        } catch (Exception e) {
            logger.warn("Could not make a chromosome from {}\nCreating a fresh chromosome instead!", file.getName());
            return super.getChromosome();
        }

        // Construct a pipe to feed the parsed document into the validating parser
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            new Canonicalizer(out).write(doc);
        } catch (IOException e1) {
            logger.error("Error in stream construction: {}", e1.getMessage());
            logger.warn("Could not make a chromosome from {}\nCreating a fresh chromosome instead!", file.getName());
            return super.getChromosome();
        }

        // Phase 2: Instrument the tree with schema info
        AwareElement root = (AwareElement) doc.getRootElement();
        SchemaPatcher sp = new SchemaPatcher(doc);
        parser.setDocumentHandler(sp);
        parser.setErrorHandler(nopHandler);
        // String uri = file.toURI().toString();
        // XMLInputSource source = new XMLInputSource(null, uri, uri);
        XMLInputSource source = new XMLInputSource(null, null, null, new ByteArrayInputStream(out.toByteArray()), "UTF-8");
        try {
            parser.parse(source);
        } catch (Exception e) {
            logger.warn("Could not make a chromosome from {} because of {}\nCreating a fresh chromosome instead!", file.getName(), e.getMessage());
            return super.getChromosome();
        }

        // Phase 3: register all tree components in a fresh element map
        doc.resetEleMap();
        root.register(doc.getEleMap());
        //
        chrom = new XMLTestChromosome(doc);
        chromosomeCache.put(file.getPath(), chrom);
        // do not mutate when encountering for the first time
        return new XMLTestChromosome(chrom);
    }

    private static class NOPErrorHandler extends DefaultErrorHandler {
        @Override
        public void error(String domain, String key, XMLParseException ex) {
            try {
                super.error(domain, key, ex);
            } catch (Exception e) {
            }
        }

        @Override
        public void warning(String domain, String key, XMLParseException ex) {
            try {
                super.warning(domain, key, ex);
            } catch (Exception e) {
            }
        }

        @Override
        public void fatalError(String domain, String key, XMLParseException ex) {
            try {
                super.fatalError(domain, key, ex);
            } catch (Exception e) {
            }
        }
    }

}
