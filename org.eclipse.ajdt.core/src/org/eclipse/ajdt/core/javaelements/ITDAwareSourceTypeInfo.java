/*******************************************************************************
 * Copyright (c) 2008, 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.javaelements;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IProgramElement.Kind;
import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.core.CompilationUnitElementInfo;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceFieldElementInfo;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceMethodElementInfo;
import org.eclipse.jdt.internal.core.SourceMethodInfo;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.SourceTypeElementInfo;

/**
 * This class provides element info for SourceTypes that know about
 * which inter-type declarations are declared on them.
 *
 * @author andrew
 */
public class ITDAwareSourceTypeInfo extends SourceTypeElementInfo {

    private final static class ITDAwareSourceType extends SourceType {
        final ITDAwareSourceTypeInfo info;
        private ITDAwareSourceType(JavaElement parent, String name, ITDAwareSourceTypeInfo info) {
            super(parent, name);
            this.info = info;
        }

        public Object getElementInfo() throws JavaModelException {
            return info;
        }
    }
    private final static class ITDAwareAspectType extends AspectElement {
        final ITDAwareSourceTypeInfo info;
        private ITDAwareAspectType(JavaElement parent, String name, ITDAwareSourceTypeInfo info) {
            super(parent, name);
            this.info = info;
        }

        public Object getElementInfo() throws JavaModelException {
            return info;
        }
    }
    private final class ITIT extends SourceType {
        final Object info;
        private ITIT(JavaElement parent, IType actualType, IProgramElement ajElement) throws JavaModelException {
            super(parent, actualType.getElementName());
            this.info = createInfo(actualType, ajElement);
        }

        @SuppressWarnings("unchecked")
        public Object createInfo(IType actualType, IProgramElement ajElement) throws JavaModelException {
            Object elementInfo = ((JavaElement) actualType).getElementInfo();
            if (elementInfo instanceof SourceTypeElementInfo) {
                SourceTypeElementInfo origInfo = (SourceTypeElementInfo) elementInfo;
                SourceTypeElementInfo newInfo = new SourceTypeElementInfo();
                ReflectionUtils.setPrivateField(SourceTypeElementInfo.class, "handle", newInfo, origInfo.getHandle());
                ReflectionUtils.setPrivateField(SourceTypeElementInfo.class, "superclassName", newInfo, origInfo.getSuperclassName());
                ReflectionUtils.setPrivateField(SourceTypeElementInfo.class, "superInterfaceNames", newInfo, origInfo.getInterfaceNames());
                ReflectionUtils.setPrivateField(SourceTypeElementInfo.class, "children", newInfo, convertChildren(origInfo.getChildren(), ajElement.getChildren()));
                // MemberElementInfo is package protected, so we need to access using Class.forName
                try {
                    ReflectionUtils.setPrivateField((Class<? super SourceTypeElementInfo>) Class.forName("org.eclipse.jdt.internal.core.MemberElementInfo"), "flags", newInfo, origInfo.getModifiers());
                } catch (ClassNotFoundException ignored) {
                }
                elementInfo = newInfo;
            }
            return elementInfo;
        }

        private IJavaElement[] convertChildren(IJavaElement[] children, List<IProgramElement> ipes) {
            IJavaElement[] newChildren = new IJavaElement[children.length];
            for (int i = 0; i < children.length; i++) {
                char[] typeName;
                String[] paramTypes;
                if (!children[i].isReadOnly()) {
                    if (i < ipes.size()) {
                        typeName = ipes.get(i).getCorrespondingType(true).toCharArray();
                        List<char[]> parameterSignatures = ipes.get(i).getParameterSignatures();
                        if (parameterSignatures != null) {
                            paramTypes = new String[parameterSignatures.size()];
                            int j = 0;
                            for (char[] paramArr : parameterSignatures) {
                                paramTypes[j++] = String.valueOf(paramArr).replace('/', '.');
                            }
                        } else {
                            paramTypes = new String[0];
                        }
                    } else {
                        typeName = "java.lang.Object".toCharArray();
                        paramTypes = new String[0];
                    }
                } else {
                    throw new IllegalArgumentException(children[i].getHandleIdentifier() +
                            " should not be read only");
                }
                switch (children[i].getElementType()) {
                    case IJavaElement.FIELD:
                        newChildren[i] = new ITITField((SourceField) children[i], typeName);
                        break;
                    case IJavaElement.METHOD:
                        newChildren[i] = new ITITMethod((SourceMethod) children[i], typeName, paramTypes);
                        break;
                    case IJavaElement.TYPE:
                    case IJavaElement.INITIALIZER:
                        // not allowed
                    default:
                        throw new IllegalArgumentException(children[i].getHandleIdentifier());
                }
            }
            return newChildren;
        }

        public Object getElementInfo() throws JavaModelException {
            return info;
        }
        @Override
        public boolean exists() {
            return super.exists();
        }
    }

    private static final class ITITMethod extends SourceMethod {
        private final SourceMethod orig;
        private final char[] fullTypeName;
        private ITITSourceMethodInfo thisInfo;

        protected ITITMethod(SourceMethod orig, char[] fullTypeName, String[] parameterTypes) {
            super(orig.getParent(), orig.getElementName(), parameterTypes);
            this.orig = orig;
            this.fullTypeName = fullTypeName;
        }

        @Override
        public Object getElementInfo() {
            if (thisInfo != null) {
                return thisInfo;
            }
            try {
                SourceMethodElementInfo info = (SourceMethodElementInfo) orig.getElementInfo();
                thisInfo = new ITITSourceMethodInfo();
                thisInfo.setReturnType(fullTypeName);
                thisInfo.setFlags(info.getModifiers());
                thisInfo.setNameSourceStart(info.getNameSourceStart());
                thisInfo.setNameSourceEnd(info.getNameSourceEnd());
                thisInfo.setSourceRangeStart(info.getDeclarationSourceStart());
                thisInfo.setSourceRangeEnd(info.getDeclarationSourceEnd());
                thisInfo.setArgumentNames(info.getArgumentNames());
                thisInfo.setArguments((ILocalVariable[]) ReflectionUtils.getPrivateField(SourceMethodElementInfo.class, "arguments", info));
                // not handling type parameters for now.
            } catch (Exception ignored) {
            }
            return thisInfo;
        }


    }
    private static final class ITITField extends SourceField {

        private final SourceField orig;
        private final char[] fullTypeName;
        private ITITSourceFieldElementInfo thisInfo;

        protected ITITField(SourceField orig, char[] fullTypeName) {
            super(orig.getParent(), orig.getElementName());
            this.orig = orig;
            this.fullTypeName = fullTypeName;
        }

        @Override
        public Object getElementInfo() {
            if (thisInfo != null) {
                return thisInfo;
            }
            try {
                SourceFieldElementInfo info = (SourceFieldElementInfo) orig.getElementInfo();
                thisInfo = new ITITSourceFieldElementInfo();
                thisInfo.setTypeName(fullTypeName);
                thisInfo.setFlags(info.getModifiers());
                thisInfo.setNameSourceStart(info.getNameSourceStart());
                thisInfo.setNameSourceEnd(info.getNameSourceEnd());
                thisInfo.setSourceRangeStart(info.getDeclarationSourceStart());
                thisInfo.setSourceRangeEnd(info.getDeclarationSourceEnd());
            } catch (Exception ignored) {
            }
            return thisInfo;
        }
    }

    static class ITITSourceMethodInfo extends SourceMethodInfo {

        @Override
        protected void setReturnType(char[] type) {
            super.setReturnType(type);
        }

        @Override
        protected void setArgumentNames(char[][] names) {
            super.setArgumentNames(names);
        }

        @Override
        protected void setNameSourceEnd(int end) {
            super.setNameSourceEnd(end);
        }

        @Override
        protected void setNameSourceStart(int start) {
            super.setNameSourceStart(start);
        }

        @Override
        protected void setFlags(int flags) {
            super.setFlags(flags);
        }

        @Override
        protected void setSourceRangeEnd(int end) {
            super.setSourceRangeEnd(end);
        }

        @Override
        protected void setSourceRangeStart(int start) {
            super.setSourceRangeStart(start);
        }

        protected void setArguments(ILocalVariable[] arguments) {
            this.arguments = arguments;
        }
    }

    static class ITITSourceFieldElementInfo extends SourceFieldElementInfo {

        @Override
        protected void setTypeName(char[] typeName) {
            super.setTypeName(typeName);
        }

        @Override
        protected void setNameSourceEnd(int end) {
            super.setNameSourceEnd(end);
        }

        @Override
        protected void setNameSourceStart(int start) {
            super.setNameSourceStart(start);
        }

        @Override
        protected void setFlags(int flags) {
            super.setFlags(flags);
        }

        @Override
        protected void setSourceRangeEnd(int end) {
            super.setSourceRangeEnd(end);
        }

        @Override
        protected void setSourceRangeStart(int start) {
            super.setSourceRangeStart(start);
        }

    }


    public ITDAwareSourceTypeInfo(SourceType type) throws JavaModelException {
        this((ISourceType) type.getElementInfo(), type);
    }

    public ITDAwareSourceTypeInfo(ISourceType toCopy, SourceType type) {
        this.handle = createITDAwareType(type, this);

        this.setFlags(toCopy.getModifiers());
        this.setSuperclassName(toCopy.getSuperclassName());
        this.setSuperInterfaceNames(toCopy.getInterfaceNames());
        this.setNameSourceEnd(toCopy.getNameSourceEnd());
        this.setNameSourceStart(toCopy.getNameSourceStart());
        this.setSourceRangeEnd(toCopy.getDeclarationSourceEnd());
        this.setSourceRangeStart(toCopy.getDeclarationSourceStart());
        try {
            ITypeParameter[] parameters = type.getTypeParameters();
            if (parameters != null) {
                this.typeParameters = new ITypeParameter[parameters.length];
                System.arraycopy(parameters, 0, this.typeParameters, 0, parameters.length);
            }
        } catch (JavaModelException ignored) {
        }

        IJavaElement[] children = augmentChildrenAndHierarchy(type);
        if (children != null) {
            this.setChildren(children);
        }

        try {
            // this ensures that itd aware content assist
            // still works when there are large numbers ofannotations
            Object info = ((JavaElement) handle.getCompilationUnit()).getElementInfo();
            if (info != null && info instanceof CompilationUnitElementInfo) {
                ((CompilationUnitElementInfo) info).annotationNumber = 0;
            }
        } catch (JavaModelException ignored) {
        }
    }

    private IJavaElement[] augmentChildrenAndHierarchy(SourceType type) {
        try {
            IJavaElement[] origChildren = type.getChildren();
            IJavaElement[] newChildren = new IJavaElement[origChildren.length];
            boolean hasChanges = false;
            // recur through original children
            // ensure that children are also ITD aware
            for (int i = 0; i < origChildren.length; i++) {
                if (origChildren[i].getElementType() == IJavaElement.TYPE) {
                    final SourceType innerType = (SourceType) origChildren[i];
                    if (!(innerType instanceof ITDAwareSourceType)) {
                        ITDAwareSourceTypeInfo innerInfo = new ITDAwareSourceTypeInfo(innerType);
                        newChildren[i] = createITDAwareType(innerType, innerInfo);
                        hasChanges = true;
                        continue;
                    }
                }
                newChildren[i] = origChildren[i];
            }



            List<IJavaElement> augmentedChildren = getITDs(type);
            if (type instanceof AspectElement) {
                augmentedChildren.add(createAspectOf((AspectElement) this.handle));
                augmentedChildren.add(createHasAspect((AspectElement) this.handle));
                augmentedChildren.add(createGetWithinTypeName((AspectElement) this.handle));
            }
            if (augmentedChildren.size() > 0 || hasChanges) {
                IJavaElement[] allChildren = new IJavaElement[origChildren.length + augmentedChildren.size()];
                System.arraycopy(newChildren, 0, allChildren, 0, newChildren.length);
                int i = origChildren.length;
                for (IJavaElement elt : augmentedChildren) {
                    allChildren[i++] = elt;
                }
                return allChildren;
            } else {
                return origChildren;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private List<IJavaElement> getITDs(SourceType type) throws JavaModelException {
        AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(type);
        if (model.hasModel()) {
            List<IJavaElement> itds = new ArrayList<>();
            List<IJavaElement> rels = model.getRelationshipsForElement(type, AJRelationshipManager.ASPECT_DECLARATIONS);

            List<IMethod> childMethods = null;

            for (IJavaElement ije : rels) {
                if (ije instanceof IntertypeElement) {
                    IntertypeElement elt = (IntertypeElement) ije;
                    IMember member = elt.createMockDeclaration(type);
                    // null if the ITD doesn't exist in the AspectJ hierarchy
                    // will happen if the Java side has partial compilation
                    // and aspectj side does not
                    if (member != null) {

                        // should not add this ITD if it is a duplicate
                        // of another ITD
                        if (!isAlreadyAnITD(itds, member)) {
                            continue;
                        }

                        itds.add(member);

                        // additional processing for interfaces
                        if (handle.isInterface()) {

                            if (member.getElementType() == IJavaElement.FIELD) {
                                // Bug 262969
                                // Interfaces can't have fields, so ignore
                                // Reconciling errors occur if an ITD field is
                                // referenced outside of the aspect that declares them,
                                // but only if the declared type of the object is an interface.
                                itds.remove(member);
                            } else if (member.getElementType() == IJavaElement.METHOD) {
                                // now look to see if this ITD a method that provides
                                // a default implementation for an interface method
                                // use IMethod.isSimilar
                                if (childMethods == null) {
                                    childMethods = (List<IMethod>) type.getChildrenOfType(IJavaElement.METHOD);
                                }
                                for (IMethod method : childMethods) {
                                    if (method.isSimilar((IMethod) member)) {
                                        itds.remove(member);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } else if (ije instanceof DeclareElement) {
                    DeclareElement elt = (DeclareElement) ije;

                    // use createElementInfo, not getElementInfo because
                    // we don't want it cached
                    DeclareElementInfo info = (DeclareElementInfo) elt.createElementInfo();
                    if (info == null || info.getAJKind() != Kind.DECLARE_PARENTS) {
                        continue;
                    }

                    char[][] newSupers = info.getTypes();
                    augmentHierarchy(newSupers);
                } else if (ije instanceof AspectElement || ije instanceof BinaryAspectElement) {
                    // likely a declare parents instantiated in a concrete aspect, but declared in a super aspect
                    IProgramElement ipe = model.javaElementToProgramElement(ije);
                    Map<String, List<String>> declareParentsMap = ipe.getDeclareParentsMap();
                    if (declareParentsMap != null) {
                        augmentHierarchy(declareParentsMap.get(type.getFullyQualifiedName()));
                    }
                } else if (ije instanceof IType) {
                    // an ITIT
                    itds.add(new ITIT(type, (IType) ije, model.javaElementToProgramElement(ije)));
                }
            }
            return itds;
        }
        return new LinkedList<>();
    }

    private void augmentHierarchy(List<String> newSupers) {
        if (newSupers != null) {
            char[][] newSupersArr = new char[newSupers.size()][];
            int i = 0;
            for (String newSuper : newSupers) {
                newSupersArr[i++] = newSuper.toCharArray();
            }
            augmentHierarchy(newSupersArr);
        }
    }

    /**
     * @param newSupers
     */
    private void augmentHierarchy(char[][] newSupers) {
        if (newSupers != null) {
            for (char[] newSuper : newSupers) {
                if (isClass(newSuper)) {
                    this.setSuperclassName(newSuper);
                } else {
                    // assume an interface
                    char[][] origInterfaces = this.getInterfaceNames();
                    char[][] newInterfaces;
                    if (origInterfaces == null) {
                        newInterfaces = new char[][] { newSuper };
                    } else {
                        newInterfaces = new char[origInterfaces.length + 1][];
                        System.arraycopy(origInterfaces, 0, newInterfaces, 0, origInterfaces.length);
                        newInterfaces[origInterfaces.length] = newSuper;
                    }
                    setSuperInterfaceNames(newInterfaces);
                }
            }
        }
    }

    private boolean isAlreadyAnITD(List<IJavaElement> itds, IMember member) {
        boolean shouldAdd = true;

        if (member.getElementType() == IJavaElement.FIELD) {
            for (IJavaElement itdElt : itds) {
                if (itdElt instanceof IField) {
                    IField itdField = (IField) itdElt;
                    if (member.getElementName().equals(itdField.getElementName())) {
                        // may not be same type, but we want to avoid conflicts, so remove
                        shouldAdd = false;
                        break;
                    }
                }
            }
        } else if (member.getElementType() == IJavaElement.METHOD) {
            for (IJavaElement itdElt : itds) {
                if (itdElt instanceof IMethod) {
                    IMethod itdMethod = (IMethod) itdElt;
                    if (itdMethod.isSimilar((IMethod) member)) {
                        shouldAdd = false;
                        break;
                    }
                }
            }
        }
        return shouldAdd;
    }


    /**
     * create the aspectOf method for
     */
    private SourceMethod createAspectOf(AspectElement parent) {
        return new SourceMethod(
          parent,
                "aspectOf",
                new String[0]) {
            protected Object createElementInfo() {
                return new SourceMethodInfo() {
                    @Override
                    public int getModifiers() {
                        return Flags.AccPublic | Flags.AccStatic;
                    }

                    @Override
                    public char[] getReturnTypeName() {
                        return parent.getElementName().toCharArray();
                    }
                };
            }
            public boolean exists() {
                return true;
            }
        };

    }

    private SourceMethod createHasAspect(AspectElement parent) {
        return new SourceMethod(
          parent,
                "hasAspect",
                new String[0]) {
            protected Object createElementInfo() {
                return new SourceMethodInfo() {
                    @Override
                    public int getModifiers() {
                        return Flags.AccPublic | Flags.AccStatic;
                    }

                    @Override
                    public char[] getReturnTypeName() {
                        return "boolean".toCharArray();
                    }
                };
            }
            public boolean exists() {
                return true;
            }
        };

    }
    private SourceMethod createGetWithinTypeName(AspectElement parent) {
        return new SourceMethod(
          parent,
                "getWithinTypeName",
                new String[0]) {
            protected Object createElementInfo() {
                return new SourceMethodInfo() {
                    @Override
                    public int getModifiers() {
                        return Flags.AccPublic;
                    }

                    @Override
                    public char[] getReturnTypeName() {
                        return "String".toCharArray();
                    }
                };
            }
            public boolean exists() {
                return true;
            }
        };

    }

    /**
     * True if a class false if an interface or doesn't exist
     * @param qualifiedName
     * @return
     */
    private boolean isClass(char[] qualifiedName) {
        try {
            int genericsIndex = CharOperation.indexOf('<', qualifiedName);
            if (genericsIndex >= 0) {
                qualifiedName = CharOperation.subarray(qualifiedName, 0, genericsIndex);
            }
            IType type = this.handle.getJavaProject().findType(String.valueOf(qualifiedName), (IProgressMonitor) null);
            return type != null && type.isClass();
        } catch (JavaModelException e) {
            return false;
        }
    }

    private SourceType createITDAwareType(SourceType type, ITDAwareSourceTypeInfo info) {
        if (type instanceof AspectElement) {
            return new ITDAwareAspectType(type.getParent(), type.getElementName(), info);
        } else {
            return new ITDAwareSourceType(type.getParent(), type.getElementName(), info);
        }
    }

    public IJavaElement[] getChildren() {
        return super.getChildren();
    }

                        /* AJDT 1.7 */
    public void setChildren(IJavaElement[] children) {
        this.children = children;
    }

}
