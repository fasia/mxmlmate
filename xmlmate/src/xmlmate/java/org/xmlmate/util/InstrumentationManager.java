package org.xmlmate.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstrumentationManager {
    private final Matcher includeMatcher;
    private final Matcher excludeMatcher;

    public InstrumentationManager(String includePackage, String... excludePackages) {
        includeMatcher = generatePackageRegex(includePackage);
        excludeMatcher = generatePackageRegex(excludePackages);
    }

    /**
     * creates a regex of the form ^(org.sut.*)|(com.sut.*)...
     */
    private static Matcher generatePackageRegex(String... packs) {
        if (null == packs || 0 == packs.length)
            return Pattern.compile("").matcher("");
        StringBuilder reg = new StringBuilder("^");
        for (String p : packs) {
            reg.append('(');
            reg.append(Pattern.quote(p.replaceAll("\\.*$", "")));
            reg.append(".*)|");
        }
        String res = reg.toString();
        if (!res.isEmpty())
            res = res.substring(0, res.length() - 1);
        return Pattern.compile(res).matcher("");
    }

    public boolean shouldInstrument(String name) {
        return includeMatcher.reset(name).matches() && !excludeMatcher.reset(name).matches();
    }

}
