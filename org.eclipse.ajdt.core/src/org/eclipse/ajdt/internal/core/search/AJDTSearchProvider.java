/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.core.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.codeconversion.ConversionOptions;
import org.eclipse.ajdt.core.codeconversion.ITDAwareLookupEnvironment;
import org.eclipse.ajdt.core.codeconversion.ITDAwareNameEnvironment;
import org.eclipse.ajdt.core.codeconversion.JavaCompatibleBuffer;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElementInfo;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.contribution.jdt.itdawareness.ISearchProvider;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.hierarchy.HierarchyResolver;
import org.eclipse.jdt.internal.core.search.matching.ConstructorPattern;
import org.eclipse.jdt.internal.core.search.matching.FieldPattern;
import org.eclipse.jdt.internal.core.search.matching.MethodPattern;
import org.eclipse.jdt.internal.core.search.matching.OrPattern;
import org.eclipse.jdt.internal.core.search.matching.PackageReferencePattern;
import org.eclipse.jdt.internal.core.search.matching.PossibleMatch;
import org.eclipse.jdt.internal.core.search.matching.TypeReferencePattern;

/**
 *
 * @author Andrew Eisenberg
 * @created Mar 8, 2010
 */
public class AJDTSearchProvider implements ISearchProvider {


    public IntertypeElement findITDGetter(IField field) {
        return findITDAccessor(field, true);
    }

    public IntertypeElement findITDSetter(IField field) {
        return findITDAccessor(field, false);
    }

    private IntertypeElement findITDAccessor(IField field, boolean getter) {
        AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(field);
        List<IJavaElement> rels = model.getRelationshipsForElement(field.getDeclaringType(), AJRelationshipManager.ASPECT_DECLARATIONS);
        for (IJavaElement elt : rels) {
            if (elt instanceof IntertypeElement) {
                IntertypeElement itd = (IntertypeElement) elt;
                if (isAccessorITDName(itd.getElementName(),
                        field.getElementName(),
                        field.getDeclaringType().getElementName(),
                        field.getDeclaringType().getFullyQualifiedName(),
                        getter) &&
                        checkParameters(itd, field, getter) &&
                        checkReturnType(itd, field, getter)) {
                    return itd;
                }
            }
        }
        return null;
    }

    private boolean checkReturnType(IntertypeElement itd, IField field,
            boolean getter) {
        try {
            if (getter) {
                    return itd.getReturnType().equals(field.getTypeSignature()) ||
                        field.getTypeSignature().equals(String.valueOf(itd.getQualifiedReturnType()));
            } else {
                return itd.getReturnType().equals(Signature.SIG_VOID);
            }
        } catch (JavaModelException ignored) {
        }
        return false;
    }

    private boolean checkParameters(IntertypeElement itd, IField field,
            boolean getter) {
        String[] parameterTypes = itd.getParameterTypes();
        if (getter) {
            return parameterTypes == null ||
                    parameterTypes.length == 0;
        } else {
            try {
                if (parameterTypes != null &&
                    parameterTypes.length == 1) {
                    String typeSignature = field.getTypeSignature();
                    if (parameterTypes[0].equals(typeSignature)) {
                        return true;
                    }
                    // now try fully qualified, and ensure that the type is unbound
                    String itdParamSignature = itd.getQualifiedParameterTypes()[0];
                    int arrayCount = Signature.getArrayCount(itdParamSignature);
                    if (itdParamSignature.charAt(arrayCount) == 'L') {
                        itdParamSignature = itdParamSignature.substring(0, arrayCount) + 'Q' + itdParamSignature.substring(arrayCount+1);
                    }
                    return typeSignature.equals(itdParamSignature);
                }
            } catch (JavaModelException ignored) {
            }
        }
        return false;
    }

    private boolean isAccessorITDName(String itdName, String fieldName,
            String declaringTypeName, String declaringFullyQualifedName, boolean getter) {
        String prefix = getter ? "get" : "set";
        // there might be a package fragment that starts with 'is'
        int lastDot = itdName.lastIndexOf('.');
        if (getter && lastDot >= 0 && itdName.indexOf(".is", lastDot) > 0) {
            prefix = "is";
        }
        String suffix = prefix + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String accessorName = declaringTypeName + "." + suffix;
        if (itdName.equals(accessorName)) {
            return true;
        }
        accessorName = declaringFullyQualifedName + "." + suffix;
        return itdName.equals(accessorName);
    }

    public IJavaElement convertJavaElement(IJavaElement origElement) {
        if (origElement instanceof IntertypeElement) {
            try {
                char[] targetTypeName = ((IntertypeElementInfo) ((IntertypeElement) origElement).getElementInfo()).getTargetType();
                if (targetTypeName != null) {
                    AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(origElement);
                    List<IJavaElement> rels = model.getRelationshipsForElement(origElement, AJRelationshipManager.DECLARED_ON);
                    if (rels.size() > 0 && rels.get(0) instanceof IType) {
                        IType targetType = (IType) rels.get(0);
                        IJavaElement newElement = ((IntertypeElement) origElement).createMockDeclaration(targetType);
                        if (newElement != null) {
                            return newElement;
                        }
                    }
                }
            } catch (JavaModelException ignored) {
            }
        }
        return origElement;
    }

    public LookupEnvironment createLookupEnvironment(
            LookupEnvironment orig,
            ICompilationUnit[] workingCopies, JavaProject project) {
        try {
            ITDAwareNameEnvironment env = new ITDAwareNameEnvironment(project, workingCopies);
            return new ITDAwareLookupEnvironment(orig, env);
        } catch (JavaModelException ignored) {
        }
        return orig;
    }


    private IntertypeElement findMatchingITD(AspectElement parent,
            IMethod possibleTest, String itdName) throws JavaModelException {
        IntertypeElement[] allITDs = parent.getITDs();
        for (IntertypeElement itd : allITDs) {
            if (itdName.equals(itd.getElementName()) && Arrays.equals(itd.getParameterTypes(), possibleTest.getParameterTypes())) {
                return itd;
            }
        }
        return null;
    }

    /**
     * convert ITD matches into the target types
     * Or ignore otherwise
     * @throws JavaModelException
     */
    public IJavaElement filterJUnit4TestMatch(IJavaElement possibleTest) throws JavaModelException {
        // the results are returned as ResolvedSourceMethod, not an ITD
        // so must do a little work to get to the real ITD
        if (! (possibleTest instanceof IMethod)) {
            return possibleTest;
        }
        IJavaElement parent = possibleTest.getAncestor(IJavaElement.TYPE);
        if (parent instanceof AspectElement) {
            String itdName = possibleTest.getElementName().replace('$', '.');
            IntertypeElement matchingITD = findMatchingITD((AspectElement) parent, (IMethod) possibleTest, itdName);
            if (matchingITD != null) {
                return matchingITD.createMockDeclaration();
            }
        }
        return possibleTest;
    }

    /**
     * This method finds extra matches where the search pattern refers to an intertype declaration.
     */
    public List<SearchMatch> findExtraMatches(PossibleMatch match, SearchPattern pattern, HierarchyResolver resolver) throws JavaModelException {
        List<SearchMatch> extraMatches;
        if (match.openable instanceof AJCompilationUnit) {
            // original content mode is discarded after the matches have been processed
            ((AJCompilationUnit) match.openable).requestOriginalContentMode();
        }
        if (pattern instanceof OrPattern) {
            extraMatches = new ArrayList<>();
            SearchPattern[] patterns = (SearchPattern[]) ReflectionUtils.getPrivateField(OrPattern.class, "patterns", (OrPattern) pattern);
            for (SearchPattern orPattern : patterns) {
                extraMatches.addAll(findExtraMatches(match, orPattern, resolver));
            }
        } else {
            IExtraMatchFinder<SearchPattern> finder = (IExtraMatchFinder<SearchPattern>) getExtraMatchFinder(match, pattern);
            extraMatches = finder.findExtraMatches(match, pattern, resolver);
        }
        return extraMatches;
    }

    public void matchProcessed(PossibleMatch match) {
        if (match.openable instanceof AJCompilationUnit) {
            // in this callback, we know that the matches have been processed.
            // so original contents can be discarded
            try {
                ((AJCompilationUnit) match.openable).discardOriginalContentMode();
            } catch (JavaModelException ignored) {
            }
        }
    }

    /**
     * @param pattern
     * @return
     */
    private IExtraMatchFinder<? extends SearchPattern> getExtraMatchFinder(PossibleMatch match, SearchPattern pattern) {
        if ((match.openable instanceof AJCompilationUnit)) {
            if (pattern instanceof MethodPattern || pattern instanceof ConstructorPattern || pattern instanceof FieldPattern) {
                return new ExtraITDFinder();
            } else if (pattern instanceof TypeReferencePattern) {
                return new ExtraTypeReferenceFinder();
            } else if (pattern instanceof PackageReferencePattern) {
                return new ExtraPackageReferenceFinder();
            }
        }
        return new NullMatchFinder();
    }

    public boolean isInteresting(IOpenable elt) {
        return elt instanceof AJCompilationUnit;
    }

    public char[] findSource(IOpenable elt) {
        if (elt instanceof AJCompilationUnit) {
            try {
                IBuffer buf = elt.getBuffer();
                if (buf instanceof JavaCompatibleBuffer) {
                    JavaCompatibleBuffer convertingBuf = (JavaCompatibleBuffer) buf;
                    ConversionOptions orig = convertingBuf.getConversionOptions();
                    convertingBuf.setConversionOptions(ConversionOptions.CONSTANT_SIZE);
                    char[] contents = convertingBuf.getCharacters();
                    convertingBuf.setConversionOptions(orig);
                    return contents;
                }
            } catch (JavaModelException ignored) {
            }

            // couldn't get the buffer for some reason, but we should still
            // return the converted contents as this is better than returning the original contents
            return ((AJCompilationUnit) elt).getContents();
        } else {
            // should return null here, so contents will be gotten in the normal way.
            return null;
        }
    }

}
