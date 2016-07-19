package org.xmlmate.util;

import net.sf.corn.cps.CPResourceFilter;
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
