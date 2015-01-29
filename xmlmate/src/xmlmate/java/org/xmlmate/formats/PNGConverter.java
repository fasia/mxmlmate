package org.xmlmate.formats;

import java.io.IOException;

public class PNGConverter implements FormatConverter {

	@Override
	public String convert(String xml, String output) throws IOException {
		ProcessBuilder pb = new ProcessBuilder("python",
				"/home/nikolas/lunaworkspace/xmlmate/subjects/png/converters/xml2png.py",
				xml, output + ".png");
		pb.inheritIO();
		Process process;
		try {
			process = pb.start();
			int ret = process.waitFor();
			if (0 != ret)
				throw new IOException("Converter returned " + ret);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return output + ".png";
	}

}
