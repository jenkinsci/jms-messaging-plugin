package com.redhat.utils;

import java.util.Enumeration;
import java.util.Objects;
import java.util.Properties;
import java.util.Vector;

/**
 * <a href="OrderedProperties.java.html"><b><i>View Source</i></b></a>
 *
 * @author Brian Wing Shun Chan
 */
public class OrderedProperties extends Properties {

    private final Vector _names;

    public OrderedProperties() {
        super();

        _names = new Vector();
    }

    public Enumeration propertyNames() {
        return _names.elements();
    }

    public Object put(Object key, Object value) {
        _names.remove(key);

        _names.add(key);

        return super.put(key, value);
    }

    public Object remove(Object key) {
        _names.remove(key);

        return super.remove(key);
    }

    @Override
    public boolean equals(Object that) {
        if (!super.equals(that)) {
            return false;
        }

        OrderedProperties thatp = (OrderedProperties) that;
        return (this._names != null ? this._names.equals(thatp._names) : thatp._names == null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_names);
    }
}
