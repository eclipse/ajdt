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

import java.util.ArrayList;
import java.util.List;

import org.aspectj.asm.IProgramElement;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.SourceConstructorWithChildrenInfo;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceMethodWithChildrenInfo;

public class MethodIntertypeElement extends IntertypeElement
/* implements IMethod (already implemented by AspectJMemberElement */
{

    public MethodIntertypeElement(JavaElement parent, String name,
            String[] parameterTypes) {
        super(parent, name, parameterTypes);
    }

    /**
     * @see JavaElement#getHandleMemento()
     */
    protected char getHandleMementoDelimiter() {
        return AspectElement.JEM_ITD_METHOD;
    }

    @Override
    protected Object createElementInfo() {
      //      if (result.getAJKind()!=Kind.INTER_TYPE_FIELD) {
//          throw new JavaModelException("Element exists, but is not a field: "+this);
//      }
        return super.createElementInfo();
    }

    public IMember createMockDeclaration(IType parent) {
        try {
            final IntertypeElementInfo info = (IntertypeElementInfo) getElementInfo();
            boolean isConstructor = info.getAJKind() == IProgramElement.Kind.INTER_TYPE_CONSTRUCTOR;
            boolean isMethod = info.getAJKind() == IProgramElement.Kind.INTER_TYPE_METHOD;

            if (isConstructor) {
              return new SourceMethod(
                      (JavaElement) parent,
                      parent.getElementName(),
                      MethodIntertypeElement.this.getQualifiedParameterTypes()) {
                  protected Object createElementInfo() {
                      /* AJDT 1.7 */
                      ITDSourceConstructorElementInfo newInfo = new ITDSourceConstructorElementInfo(MethodIntertypeElement.this, info.getChildren());
                      newInfo.setFlags(CompilationUnitTools.getPublicModifierCode(info));
                      newInfo.setNameSourceEnd(info.getNameSourceEnd());
                      newInfo.setNameSourceStart(info.getNameSourceStart());
                      newInfo.setArgumentNames(info.getArgumentNames(), getParamNum());
                      newInfo.setSourceRangeStart(info.getSourceRange().getOffset());
                      newInfo.setSourceRangeEnd(info.getSourceRange().getOffset() + info.getSourceRange().getLength());
                      newInfo.setTypeParameters(createTypeParameters(null));
                      newInfo.setArguments(info.getArguments());
                      return newInfo;

                  }
                  public boolean exists() {
                      return true;
                  }
              };
            } else if (isMethod) {
              return new SourceMethod(
                      (JavaElement) parent,
                      getTargetName(),
                      MethodIntertypeElement.this.getQualifiedParameterTypes()) {
                  protected Object createElementInfo() {
                      /* AJDT 1.7 */
                      ITDSourceMethodElementInfo newInfo = new ITDSourceMethodElementInfo(MethodIntertypeElement.this, info.getChildren());
                      newInfo.setReturnType(getQualifiedReturnTypeName(info));
                      newInfo.setFlags(CompilationUnitTools.getPublicModifierCode(info));
                      newInfo.setNameSourceEnd(info.getNameSourceEnd());
                      newInfo.setNameSourceStart(info.getNameSourceStart());
                      newInfo.setArgumentNames(info.getArgumentNames(), getParamNum());
                      newInfo.setSourceRangeStart(info.getSourceRange().getOffset());
                      newInfo.setSourceRangeEnd(info.getSourceRange().getOffset() + info.getSourceRange().getLength());
                      newInfo.setTypeParameters(createTypeParameters(null));
                      newInfo.setArguments(info.getArguments());
                      return newInfo;

                  }
                  public boolean exists() {
                      return true;
                  }
              };
            }
        } catch (JavaModelException ignored) {
        }
        return null;
    }

    /* AJDT 1.7 */
    private static class ITDSourceMethodElementInfo extends SourceMethodWithChildrenInfo implements IIntertypeInfo {

        IntertypeElement original;

                        /* AJDT 1.7 */
        public ITDSourceMethodElementInfo(IntertypeElement original, IJavaElement[] children) {
            super(children);
            this.original = original;
        }

        public IntertypeElement getOriginal() {
            return original;
        }

        protected void setReturnType(char[] type) {
            super.setReturnType(type);
        }

        public void setArguments(ILocalVariable[] arguments) {
            this.arguments = arguments;
        }

        protected void setArgumentNames(char[][] names, Integer min) {
            if (min == null) {
                super.setArgumentNames(null);
            } else {
                List<char[]> newNames;
                int minValue = min;
                newNames = new ArrayList<>(minValue);
                for (int i = 0; i < minValue; i++) {
                    if (names != null && i < names.length) {
                        newNames.add(names[i]);
                    } else {
                        newNames.add(("arg" + i).toCharArray());
                    }
                }
                super.setArgumentNames(newNames.toArray(new char[newNames.size()][]));
            }
        }

        public void setTypeParameters(ITypeParameter[] typeParameters) {
            this.typeParameters = typeParameters;
        }


        protected void setExceptionTypeNames(char[][] types) {
            super.setExceptionTypeNames(types);
        }

        protected void setFlags(int flags) {
            super.setFlags(flags);
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

                        /* AJDT 1.7 */
   private static class ITDSourceConstructorElementInfo extends SourceConstructorWithChildrenInfo implements IIntertypeInfo {

        IntertypeElement original;

                        /* AJDT 1.7 */
        public ITDSourceConstructorElementInfo(IntertypeElement original, IJavaElement[] children) {
            super(children);
            this.original = original;
        }

        public IntertypeElement getOriginal() {
            return original;
        }

        public void setArguments(ILocalVariable[] arguments) {
            this.arguments = arguments;
        }

        public void setTypeParameters(ITypeParameter[] typeParameters) {
            this.typeParameters = typeParameters;
        }

        protected void setArgumentNames(char[][] names, Integer min) {
            if (min == null) {
                super.setArgumentNames(null);
            } else {
                List<char[]> newNames;
                int minValue = min;
                newNames = new ArrayList<>(minValue);
                for (int i = 0; i < minValue; i++) {
                    if (names != null && i < names.length) {
                        newNames.add(names[i]);
                    } else {
                        newNames.add(("arg" + i).toCharArray());
                    }
                }
                super.setArgumentNames(newNames.toArray(new char[newNames.size()][]));
            }
        }

        protected void setExceptionTypeNames(char[][] types) {
            super.setExceptionTypeNames(types);
        }

        protected void setFlags(int flags) {
            super.setFlags(flags);
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
}
