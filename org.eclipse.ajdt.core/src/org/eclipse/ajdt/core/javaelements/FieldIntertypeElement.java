/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kris De Volder, Andrew Eisenberg - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.javaelements;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceFieldWithChildrenInfo;

public class FieldIntertypeElement extends IntertypeElement implements IField {

	/**
	 * A 'safe' dummy to provide as the parameters object for the superclass. Fields don't
	 * really have parameters.
	 */
	private static final String[] dummyParameters = new String[0];

	public FieldIntertypeElement(JavaElement parent, String name) {
		super(parent, name, dummyParameters);
	}

    /**
     * @see JavaElement#getHandleMemento()
     */
    public char getHandleMementoDelimiter() {
        return AspectElement.JEM_ITD_FIELD;
    }

    @Override
    public IMember createMockDeclaration(IType parent) {
        try {
            final IntertypeElementInfo info = (IntertypeElementInfo) getElementInfo();
          return new SourceField((JavaElement) parent, getTargetName()) {
              protected Object createElementInfo() {
                  /* AJDT 1.7 */
                  ITDSourceFieldElementInfo newInfo = new ITDSourceFieldElementInfo(FieldIntertypeElement.this, info.getChildren());
                  newInfo.setFlags(CompilationUnitTools.getPublicModifierCode(info));
                  newInfo.setNameSourceEnd(info.getNameSourceEnd());
                  newInfo.setNameSourceStart(info.getNameSourceStart());
                  newInfo.setTypeName(getQualifiedReturnTypeName(info));
                  newInfo.setSourceRangeStart(info.getSourceRange().getOffset());
                  newInfo.setSourceRangeEnd(info.getSourceRange().getOffset() + info.getSourceRange().getLength());
                  return newInfo;
              }
              public boolean exists() {
                  return true;
              }
          };
        } catch (JavaModelException ignored) {
        }
        return null;
    }

    /**
     * @author andrew
     * just expose all the protected setter methods
     */
                        /* AJDT 1.7 */
    private static class ITDSourceFieldElementInfo extends SourceFieldWithChildrenInfo implements IIntertypeInfo {
        IntertypeElement original;

                        /* AJDT 1.7 */
        public ITDSourceFieldElementInfo(IntertypeElement original, IJavaElement[] children) {
            super(children);
            this.original = original;
        }

        public IntertypeElement getOriginal() {
            return original;
        }

        protected void setFlags(int flags) {
            super.setFlags(flags);
        }
        protected void setTypeName(char[] typeName) {
            super.setTypeName(typeName);
        }
        protected void setNameSourceEnd(int end) {
            super.setNameSourceEnd(end);
        }
        protected void setNameSourceStart(int start) {
            super.setNameSourceStart(start);
        }
        protected void setSourceRangeEnd(int end) {
            super.setSourceRangeEnd(end);
        }
        protected void setSourceRangeStart(int start) {
            super.setSourceRangeStart(start);
        }
    }

    public Object getConstant() throws JavaModelException {
        return null;
    }

    public String getTypeSignature() throws JavaModelException {
        return super.getReturnType();
    }

    public boolean isEnumConstant() throws JavaModelException {
        return false;
    }

    public boolean isRecordComponent() throws JavaModelException {
      // TODO Auto-generated method stub
      return false;
    }
}
