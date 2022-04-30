/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core.javaelements;

import org.aspectj.ajdt.internal.compiler.lookup.EclipseSourceLocation;
import org.aspectj.asm.IProgramElement;
import org.aspectj.bridge.ISourceLocation;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.LocalVariable;
import org.eclipse.jdt.internal.core.util.Util;

/**
 *
 * @author mchapman
 */
public class AJCodeElement extends LocalVariable implements IAJCodeElement {
	private final String name;

	private int startLine;

	public AJCodeElement(JavaElement parent, String name) {
		super(parent,name,0,0,0,0,"I", new org.eclipse.jdt.internal.compiler.ast.Annotation[0], 0, false); //$NON-NLS-1$
		this.name=name;
	}

    public AJCodeElement(JavaElement parent, String name, int occurrence) {
        this(parent,name);
        this.occurrenceCount = occurrence;
    }



	public ISourceRange getNameRange() {
		if (nameStart==0) {
			initializeLocations();
		}
		return new SourceRange(this.nameStart, this.nameEnd-this.nameStart+1);
	}

	public int hashCode() {
		return Util.combineHashCodes(name.hashCode(),occurrenceCount);
	}

	public boolean equals(Object o) {
		if (!(o instanceof AJCodeElement)) {
			return super.equals(o);
		}
		AJCodeElement ajce = (AJCodeElement)o;
		return super.equals(o) && (occurrenceCount == ajce.occurrenceCount);
	}

	public void initializeLocations() {
	    // try the easy way:
        IProgramElement ipe =
            AJProjectModelFactory.getInstance().getModelForJavaElement(this).javaElementToProgramElement(this);
        ISourceLocation sloc = ipe.getSourceLocation();
        if (sloc != null) {
            startLine = sloc.getLine();

            nameStart = sloc.getOffset();
            if (sloc instanceof EclipseSourceLocation) {
                EclipseSourceLocation esloc = (EclipseSourceLocation) sloc;
                nameEnd = esloc.getEndPos();
            }
        }

        // sometimes the start and end values are not set...so do it the hard way
        // so calculate it from the line
        if (nameStart <= 0 || nameEnd <= 0) {
            try {
                IOpenable openable = this.getParent().getOpenableParent();
                IBuffer buffer;
                if (openable instanceof AJCompilationUnit) {
                    AJCompilationUnit ajCompUnit = (AJCompilationUnit) openable;
                    ajCompUnit.requestOriginalContentMode();
                    buffer = openable.getBuffer();
                    ajCompUnit.discardOriginalContentMode();
                } else {
                    buffer = openable.getBuffer();
                }
                String source = buffer.getContents();

                int lines = 0;
    			for (int i = 0; i < source.length(); i++) {
    				if (source.charAt(i) == '\n') {
    				    lines++;
    					if (lines == startLine-1) {
    					    // starting remove white space
    					    i++;
    					    while (i < source.length() && (Character.isWhitespace(source.charAt(i))
    					            && source.charAt(i) != '\n')) {
    					        i++;
    					    }
    						nameStart=i;
    						break;
    					}
    				}
    			}

    			for (int i = nameStart+1; i < source.length(); i++) {
    			    if (source.charAt(i) == '\n' || source.charAt(i) ==';') {
    			        nameEnd = i-1;
    			        break;
    			    }
    			}

    			nameStart = Math.min(nameStart,nameEnd);
    		} catch (JavaModelException ignored) {
    		}
	    }
	}

	/**
	 * @return Returns the line in the file of this AJCodeElement.
	 */
	public int getLine() {
	    return startLine;
	}

	/**
	 * @return Returns the name for this AJCodeElement
	 */
	public String getName() {
		return name;
	}

	protected char getHandleMementoDelimiter() {
		return AspectElement.JEM_CODEELEMENT;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.core.JavaElement#getChildren()
	 */
	// Workaround for bug 94401 - JavaElement expects the parent
	// to be an openable, but it is not for an AJCodeElement
	public IJavaElement[] getChildren() {
		return JavaElement.NO_ELEMENTS;
	}

}
