/*******************************************************************************
 * Copyright (c) 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg   - iniital version
 *******************************************************************************/
package org.eclipse.contribution.xref.core.tests;

import org.eclipse.core.runtime.IAdaptable;

/**
 *
 * @author Andrew Eisenberg
 * @created Dec 5, 2010
 */
public class AdaptableObject implements IAdaptable {

    private final String o;

    public AdaptableObject() {
        this.o = "";
    }
    public AdaptableObject(String o) {
        this.o = o;
    }

    public Object getAdapter(Class adapter) {
        if (String.class == adapter) {
            return o;
        }
        return null;
    }

    public java.io.Serializable getObject() {
        return o;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((o == null) ? 0 : o.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AdaptableObject other = (AdaptableObject) obj;
        if (o == null) {
          return other.o == null;
        } else
          return o.equals(other.o);
    }

    @Override
    public String toString() {
      String builder = "AdaptableObject [o=" +
                       o +
                       "]";
        return builder;
    }
}
