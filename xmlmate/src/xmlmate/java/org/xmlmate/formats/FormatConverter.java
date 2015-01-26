package org.xmlmate.formats;

import java.io.IOException;

public interface FormatConverter {
	String convert(String xml, String output) throws IOException;
}
