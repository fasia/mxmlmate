package org.xmlmate.util;

import net.sf.corn.cps.CPResourceFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlmate.XMLProperties;

public class ClassFilterAdapter implements CPResourceFilter {

    @Override
    public boolean accept(Object subject) {
        String canonicalName = ((Class<?>) subject).getCanonicalName();
        return XMLProperties.instrManager.shouldInstrument(canonicalName);
    }

    @Override
    public boolean filterable(Object subject) {
        return subject instanceof Class<?>;
    }
}
