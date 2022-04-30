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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.aspectj.asm.IProgramElement.Kind;
import org.aspectj.org.eclipse.jdt.core.dom.AST;
import org.aspectj.org.eclipse.jdt.core.dom.ASTParser;
import org.aspectj.org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.BodyDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.CompilationUnit;
import org.aspectj.org.eclipse.jdt.core.dom.InterTypeFieldDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.InterTypeMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.lookup.ModuleBinding;
import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.search.FieldDeclarationMatch;
import org.eclipse.jdt.core.search.MethodDeclarationMatch;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.core.hierarchy.HierarchyResolver;
import org.eclipse.jdt.internal.core.search.matching.ConstructorPattern;
import org.eclipse.jdt.internal.core.search.matching.FieldPattern;
import org.eclipse.jdt.internal.core.search.matching.MethodPattern;
import org.eclipse.jdt.internal.core.search.matching.PossibleMatch;
import org.eclipse.jdt.internal.core.search.matching.VariablePattern;

/**
 * Helps find ITD references and declarations of a {@link SearchPattern} inside of an {@link AspectElement}
 *
 * @author Andrew Eisenberg
 * @created Aug 6, 2010
 */
public class ExtraITDFinder implements IExtraMatchFinder<SearchPattern> {

    public List<SearchMatch> findExtraMatches(PossibleMatch match,
            SearchPattern pattern, HierarchyResolver resolver)
            throws JavaModelException {

        List<SearchMatch> extraMatches = new ArrayList<>();
        if ((match.openable instanceof AJCompilationUnit)) {
            boolean findDeclarations = findDeclarations(pattern);
            boolean findReferences = findReferences(pattern);

            AJCompilationUnit unit = (AJCompilationUnit) match.openable;
            List<IntertypeElement> allRelevantItds = findRelevantITDs(pattern,
                    resolver, unit);
            if (allRelevantItds.size() > 0) {
                if (findReferences) {
                   extraMatches.addAll(findExtraReferenceMatches(unit, allRelevantItds, pattern, match));
                }

                // for method and constructor patterns, also look for declarations
                // can't do this for field patterns because there would be a class cast exception
                // when trying to cast a IntertypeDeclaration to an IField.
                // Note that IntertypeDeclaration happens to implement IMethod.
                if (findDeclarations) {
                    extraMatches.addAll(findExtraDeclarationMatches(unit, allRelevantItds, pattern, match));
                }
            }
        }
        return extraMatches;
    }

    private List<IntertypeElement> findRelevantITDs(SearchPattern pattern,
            HierarchyResolver resolver, AJCompilationUnit unit)
            throws JavaModelException {
        List<IntertypeElement> allItds = getAllItds(unit);
        if (allItds.size() == 0) {
            return Collections.emptyList();
        }

        // find target type
        char[] targetTypeName = null;
        if (pattern instanceof FieldPattern) {
            targetTypeName = TargetTypeUtils.getName(TargetTypeUtils.getQualName((FieldPattern) pattern), TargetTypeUtils.getSimpleName((FieldPattern) pattern));
        } else if (pattern instanceof MethodPattern) {
            targetTypeName = TargetTypeUtils.getName(((MethodPattern) pattern).declaringQualification, ((MethodPattern) pattern).declaringSimpleName);
        }

        // if target type is not known, then this means that a target
        // type is not specified and therefore do not remove any
        // potential matches.
        if (targetTypeName != null && targetTypeName.length > 0) {
            if (resolver != null) {
                resolver.setFocusType(CharOperation.splitOn('.', targetTypeName));
            }

            for (Iterator<IntertypeElement> itdIter = allItds.iterator(); itdIter.hasNext();) {
                IntertypeElement itd = itdIter.next();
                if (!isSubtypeOfSearchPattern(targetTypeName, itd, resolver)) {
                    itdIter.remove();
                }
            }
        }
        return allItds;
    }

    private boolean findReferences(SearchPattern pattern) {
        if (pattern instanceof MethodPattern) {
            return (Boolean) ReflectionUtils.getPrivateField(MethodPattern.class, "findReferences", (MethodPattern) pattern);
        } else if (pattern instanceof FieldPattern) {
            // note that findReferences is actually declared in VariablePattern
            return (Boolean) ReflectionUtils.getPrivateField(VariablePattern.class, "findReferences", (VariablePattern)pattern);
        } else if (pattern instanceof ConstructorPattern) {
            return (Boolean) ReflectionUtils.getPrivateField(ConstructorPattern.class, "findReferences", (ConstructorPattern) pattern);
        } else {
            return false;
        }
    }

    private boolean findDeclarations(SearchPattern pattern) {
            if (pattern instanceof MethodPattern) {
                return (Boolean) ReflectionUtils.getPrivateField(MethodPattern.class, "findDeclarations", (MethodPattern) pattern);
            } else if (pattern instanceof FieldPattern) {
                // note that findDeclarations is actually declared in VariablePattern
                return (Boolean) ReflectionUtils.getPrivateField(VariablePattern.class, "findDeclarations", (VariablePattern)pattern);
            } else if (pattern instanceof ConstructorPattern) {
                return (Boolean) ReflectionUtils.getPrivateField(ConstructorPattern.class, "findDeclarations", (ConstructorPattern) pattern);
            } else {
                return false;
            }
        }

    /**
     * Perform searching inside of ITDs if appropriate.
     * Two situations that we care about:
     * 1. field reference pattern inside of ITD.
     * 2. method reference pattern inside of ITD
     *
     * If the {@link PossibleMatch} is an {@link AJCompilationUnit}, then
     * look to see if it has any ITDs whose target type is of the type of the search
     * pattern.  And if so, then delegate for further possible matching matching.
     * @throws JavaModelException
     */
    private List<SearchMatch> findExtraReferenceMatches(AJCompilationUnit unit, List<IntertypeElement> allRelevantItds, SearchPattern pattern, PossibleMatch match)
            throws JavaModelException {
        CompilationUnit ajDomUnit = getDom(unit);
      return walkITDs(ajDomUnit, allRelevantItds, pattern, match);
    }

    /**
     * Return all declaration matches that are ITDs of the proper type or in the type hierarchy of the expected type
     * @throws JavaModelException
     */
    private List<SearchMatch> findExtraDeclarationMatches(
            AJCompilationUnit unit, List<IntertypeElement> allRelevantItds, SearchPattern pattern, PossibleMatch match) throws JavaModelException {

        List<SearchMatch> extraDeclarationMatches = new ArrayList<>();
        // At this point, we know that the itds passed in have the same declaring type or are a subtype of the target type
        // So, just need to check selector and parameters
        // it is too time consuming to get the qualified parameters of the types, so
        // just match on simple names.
        if (pattern instanceof MethodPattern) {
            MethodPattern methPatt = (MethodPattern) pattern;
            char[] selector = methPatt.selector;
            char[][] simpleParamTypes = methPatt.parameterSimpleNames;

            for (IntertypeElement itd : allRelevantItds) {
                if (itd.getAJKind() == Kind.INTER_TYPE_METHOD &&
                        CharOperation.equals(selector, itd.getTargetName().toCharArray())) {
                    char[][] itdSimpleParamNames = extractSimpleParamNames(itd);
                    if (CharOperation.equals(simpleParamTypes, itdSimpleParamNames)) {
                        ISourceRange sourceRange = itd.getNameRange();
                        extraDeclarationMatches.add(new MethodDeclarationMatch(itd, SearchMatch.A_ACCURATE, sourceRange.getOffset(), sourceRange.getLength(),
                                match.document.getParticipant(), itd.getCompilationUnit().getResource()));
                    }
                }
            }
        } else if (pattern instanceof ConstructorPattern) {
            ConstructorPattern consPatt = (ConstructorPattern) pattern;
            // must match the exact type
            char[] targetTypeName = TargetTypeUtils.getName(consPatt.declaringQualification, consPatt.declaringSimpleName);
            char[][] simpleParamTypes = consPatt.parameterSimpleNames;
            for (IntertypeElement itd : allRelevantItds) {
                if (itd.getAJKind() == Kind.INTER_TYPE_CONSTRUCTOR &&
                        targetTypeName != null && CharOperation.equals(targetTypeName, fullyQualifiedTargetTypeName(itd))) {
                    char[][] itdSimpleParamNames = extractSimpleParamNames(itd);
                    if (CharOperation.equals(simpleParamTypes, itdSimpleParamNames)) {
                        ISourceRange sourceRange = itd.getNameRange();
                        extraDeclarationMatches.add(new MethodDeclarationMatch(itd, SearchMatch.A_ACCURATE, sourceRange.getOffset(), sourceRange.getLength(),
                                match.document.getParticipant(), itd.getCompilationUnit().getResource()));
                    }
                }
            }
        } else if (pattern instanceof FieldPattern) {
            FieldPattern fieldPatt = (FieldPattern) pattern;
            char[] targetTypeName = TargetTypeUtils.getName(TargetTypeUtils.getQualName(fieldPatt), TargetTypeUtils.getSimpleName(fieldPatt));
            char[] fieldName = fieldPatt.getIndexKey();
            for (IntertypeElement itd : allRelevantItds) {
                if (itd.getAJKind() == Kind.INTER_TYPE_FIELD && CharOperation.equals(fieldName, itd.getTargetName().toCharArray()) &&
                        // must match the exact type, but only if a type exists
                        (targetTypeName == null || CharOperation.equals(targetTypeName, fullyQualifiedTargetTypeName(itd)))) {
                    ISourceRange sourceRange = itd.getNameRange();
                    extraDeclarationMatches.add(new FieldDeclarationMatch(itd, SearchMatch.A_ACCURATE, sourceRange.getOffset(), sourceRange.getLength(),
                            match.document.getParticipant(), itd.getCompilationUnit().getResource()));
                }
            }
        }
        return extraDeclarationMatches;
    }

    private char[][] extractSimpleParamNames(IntertypeElement itd) {
        String[] parameterTypes = itd.getParameterTypes();
        if (parameterTypes == null) {
            return new char[0][];
        }
        char[][] simpleNames = new char[parameterTypes.length][];
        for (int i = 0; i < parameterTypes.length; i++) {
            try {
                simpleNames[i] = Signature.getSignatureSimpleName(Signature.getTypeErasure(parameterTypes[i].toCharArray()));
            } catch (Exception ignored) {
            }
        }
        return simpleNames;
    }

    private boolean isSubtypeOfSearchPattern(char[] targetTypeName,
            IntertypeElement itd, HierarchyResolver resolver) throws JavaModelException {
        char[] itdTargetTypeName = fullyQualifiedTargetTypeName(itd);
        if (CharOperation.equals(targetTypeName, itdTargetTypeName)) {
            return true;
        }
        if (resolver != null) {
        	LookupEnvironment env =  ((LookupEnvironment) ReflectionUtils.getPrivateField(HierarchyResolver.class, "lookupEnvironment", resolver));
            ReferenceBinding targetBinding =
                   env.askForType(CharOperation.splitOn('.', itdTargetTypeName),env.getModule(ModuleBinding.ANY));
            if (targetBinding != null) {
                return resolver.subOrSuperOfFocus(targetBinding);
            }
        }
        return false;
    }

    private char[] fullyQualifiedTargetTypeName(IntertypeElement itd) {
        IType targetType = itd.findTargetType();
      return targetType == null ? new char[0] : targetType.getFullyQualifiedName().replace('$', '.').toCharArray();
    }

    private List<SearchMatch> walkITDs(CompilationUnit ajDomUnit,
            List<IntertypeElement> allItds, SearchPattern pattern,
            PossibleMatch possibleMatch) throws JavaModelException {

        List<SearchMatch> allExtraMatches = new LinkedList<>();
        for (IntertypeElement itd : allItds) {
            BodyDeclaration decl = findITDInDom(ajDomUnit, itd);
            // we only care about ITD methods
            if (decl instanceof InterTypeMethodDeclaration) {
                allExtraMatches.addAll(findPatternInITD((InterTypeMethodDeclaration) decl, itd, pattern, possibleMatch));
            }

        }
        return allExtraMatches;
    }

    private Collection<SearchMatch> findPatternInITD(
                InterTypeMethodDeclaration decl, IntertypeElement itd,
                SearchPattern pattern, PossibleMatch possibleMatch) {
            ITDReferenceVisitor visitor = new ITDReferenceVisitor(itd,
                    pattern, possibleMatch.document.getParticipant());
      // tentative matches are found elsewhere
    //        matches.addAll(visitor.getTentativeMatches());
            return visitor.doVisit(decl);
        }

    private BodyDeclaration findITDInDom(CompilationUnit ajDomUnit,
            IntertypeElement itd) throws JavaModelException {
        List<AbstractTypeDeclaration> types = ajDomUnit.types();
        for (AbstractTypeDeclaration type : types) {
            BodyDeclaration maybeDecl = findITDInDom(type, itd);
            if (maybeDecl != null) return maybeDecl;
        }
        return null;
    }

    private BodyDeclaration findITDInDom(AbstractTypeDeclaration type,
            IntertypeElement itd) throws JavaModelException {
        List<BodyDeclaration> decls = type.bodyDeclarations();
        for (BodyDeclaration decl : decls) {
            BodyDeclaration maybeDecl = null;
            if (decl instanceof AbstractTypeDeclaration) {
                maybeDecl = findITDInDom((AbstractTypeDeclaration) decl, itd);
            } else if (decl instanceof InterTypeMethodDeclaration || decl instanceof InterTypeFieldDeclaration) {
                int domStart = decl.getStartPosition();
                int domEnd = domStart + decl.getLength();
                ISourceRange sourceRange = itd.getSourceRange();
                int eltStart = sourceRange.getOffset();
                int eltEnd = eltStart + sourceRange.getLength();

                // return this decl if one contains the other
                if ((domStart <= eltStart && domEnd >= eltEnd) ||
                        (eltStart <= domStart && eltEnd >= domEnd)) {
                    maybeDecl = decl;
                }
            }
            if (maybeDecl != null) {
                return maybeDecl;
            }
        }
        return null;
    }

    private CompilationUnit getDom(AJCompilationUnit unit) throws JavaModelException {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        unit.requestOriginalContentMode();
        parser.setSource(unit.getContents());
        parser.setCompilerOptions(unit.getJavaProject().getOptions(true));
        unit.discardOriginalContentMode();
        return (CompilationUnit) parser.createAST(new NullProgressMonitor());
    }

    private List<IntertypeElement> getAllItds(IParent parent) throws JavaModelException {
        IJavaElement[] children = parent.getChildren();
        List<IntertypeElement> allItds = new LinkedList<>();

        for (IJavaElement elt : children) {
            if (elt instanceof IntertypeElement) {
                allItds.add((IntertypeElement) elt);
            } else if (elt.getElementType() == IJavaElement.TYPE) {
                allItds.addAll(getAllItds((IParent) elt));
            }
        }
        return allItds;
    }
}
