package org.evosuite.statistics.backend;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.evosuite.ga.Chromosome;
import org.evosuite.statistics.OutputVariable;
import org.evosuite.utils.ReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This statistics backend writes all (selected) output variables to a CSV file
 * 
 * @author gordon
 *
 */
public class CSVStatisticsBackend implements StatisticsBackend {

	private static Logger logger = LoggerFactory.getLogger(CSVStatisticsBackend.class);
	
	/**
	 * Retrieve header with variable names
	 * @param data
	 * @return
	 */
	private String getCSVHeader(Map<String, OutputVariable<?>> data) {
		StringBuilder r = new StringBuilder();
		Iterator<Entry<String, OutputVariable<?>>> it = data.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, OutputVariable<?>> e = (Entry<String, OutputVariable<?>>)it.next();
			r.append(e.getKey());
			if (it.hasNext())
				r.append(",");
		}
		return r.toString();
	}


	/**
	 * Retrieve one line of data 
	 * @param data
	 * @return
	 */
	private String getCSVData(Map<String, OutputVariable<?>> data) {
		StringBuilder r = new StringBuilder();
		Iterator<Entry<String, OutputVariable<?>>> it = data.entrySet().iterator();
		while (it.hasNext()) {
			Entry<String, OutputVariable<?>> e = (Entry<String, OutputVariable<?>>)it.next();
			r.append(e.getValue().getValue());
			if (it.hasNext())
				r.append(",");
		}
		return r.toString();
	}

	@Override
	public void writeData(Chromosome result, Map<String, OutputVariable<?>> data) {
		// Write to evosuite-report/statistics.csv
		try {
			File outputDir = ReportGenerator.getReportDir();			
			File f = new File(outputDir.getAbsolutePath() + File.separator + "statistics.csv");
			BufferedWriter out = new BufferedWriter(new FileWriter(f, true));
			if (f.length() == 0L) {
				out.write(getCSVHeader(data) + "\n");
			}
			out.write(getCSVData(data) + "\n");
			out.close();

		} catch (IOException e) {
            logger.warn("Error while writing statistics: {}", e.getMessage());
		}
	}


}
