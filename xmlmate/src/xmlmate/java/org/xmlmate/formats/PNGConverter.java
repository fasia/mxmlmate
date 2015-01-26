package org.xmlmate.formats;

import java.io.File;
import java.io.IOException;

public class PNGConverter implements FormatConverter {

	@Override
	public String convert(String xml, String output) {
		ProcessBuilder pb = new ProcessBuilder(
				"/home/nikolas/lunaworkspace/xmlmate/subjects/png/converters/xml2png.py",
				xml, output + ".png");
		pb.inheritIO();
		Process process;
		try {
			process = pb.start();
			process.waitFor();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			File file = new File(xml);
			if (!file.delete())
				file.deleteOnExit();
		}
		return output + ".png";
	}

}
