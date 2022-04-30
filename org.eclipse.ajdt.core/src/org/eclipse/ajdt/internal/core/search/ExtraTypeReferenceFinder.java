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

import java.util.List;
import java.util.Map;

import org.aspectj.org.eclipse.jdt.core.dom.AbstractTypePattern;
import org.aspectj.org.eclipse.jdt.core.dom.AnyWithAnnotationTypePattern;
import org.aspectj.org.eclipse.jdt.core.dom.DeclareAnnotationDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.IdentifierTypePattern;
import org.aspectj.org.eclipse.jdt.core.dom.PatternNode;
import org.aspectj.org.eclipse.jdt.core.dom.SimpleName;
import org.aspectj.org.eclipse.jdt.core.dom.TypeCategoryTypePattern;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.DeclareElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.javaelements.PointcutUtilities;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeReferenceMatch;
import org.eclipse.jdt.internal.core.search.matching.TypeReferencePattern;

/**
 * Helps find Type references of a {@link SearchPattern} inside of an {@link AspectElement}
 *
 * @author Andrew Eisenberg
 * @created Aug 6, 2010
 */
public class ExtraTypeReferenceFinder extends AbstractExtraReferenceFinder<TypeReferencePattern> implements IExtraMatchFinder<TypeReferencePattern> {


    protected SearchMatch createITDMatch(IntertypeElement itd, SearchParticipant participant) throws JavaModelException {
        ISourceRange range = itd.getTargetTypeSourceRange();
        return new TypeReferenceMatch(itd, SearchMatch.A_ACCURATE, range.getOffset(), range.getLength(), false, participant, itd.getResource());
    }

    protected boolean isMatch(IntertypeElement itd, TypeReferencePattern pattern) throws JavaModelException {
        char[] targetTypeName = TargetTypeUtils.getName(pattern);
        IType targetType = itd.findTargetType();
        if (targetType != null) {
            return CharOperation.equals(targetTypeName, targetType.getFullyQualifiedName().toCharArray());
        } else {
            return false;
        }
    }

    @Override
    protected DeclareVisitor createDeclareVisitor(char[] contents, DeclareElement decl, SearchParticipant participant,
            TypeReferencePattern pattern) throws JavaModelException {
        return new TypeReferenceDeclareVisitor(participant,
          TargetTypeUtils.getSimpleNameStr(pattern),
          TargetTypeUtils.getQualNameStr(pattern),
          decl, contents);
    }

    static class TypeReferenceDeclareVisitor extends DeclareVisitor {
        private final String searchTypeSimpleName;
        private final String searchQualifier;
        private final String dotSearchTypeSimpleName;
        private final String atSearchTypeSimpleName;
        private final String atSearchTypeQualName;
        public TypeReferenceDeclareVisitor(SearchParticipant participant, String searchTypeSimpleName,
                String searchQualifier, DeclareElement decl, char[] fileContents) throws JavaModelException {
            super(participant, decl, fileContents);
            this.searchTypeSimpleName = searchTypeSimpleName;
            this.searchQualifier = searchQualifier == null ? "" : searchQualifier;
            this.dotSearchTypeSimpleName = "." + searchTypeSimpleName;
            this.atSearchTypeSimpleName = "@" + searchTypeSimpleName;
			this.atSearchTypeQualName = "@"
					+  (this.searchQualifier.length() > 0 ?  this.searchQualifier
							+ "."
							: "") + searchTypeSimpleName;
        }

        @Override
        public boolean visit(IdentifierTypePattern node) {
            findMatchInTypePattern(node);
            return super.visit(node);
        }

        @Override
        public boolean visit(AnyWithAnnotationTypePattern node) {
            findMatchInTypePattern(node);
            return true;
        }

        @Override
        public boolean visit(TypeCategoryTypePattern node) {
            findMatchInTypePattern(node);
            return true;
        }


        /**
         * @param node
         */
        protected void findMatchInTypePattern(AbstractTypePattern node) {
            // We have already checked qualified names at this point,
            // so we can assume a match if the simple names match.
            String detail = node.getTypePatternExpression();
            if (detail != null) {
                if (isSimpleMatch(detail)) {
                    int actualStart = node.getStartPosition() + offset;
                    acceptMatch(new TypeReferenceMatch(decl, SearchMatch.A_ACCURATE,
                            actualStart, node.getLength(), false, participant, decl.getResource()));
                } else if (isComplexTypePattern(detail)) {
                    // must do something more complex
                    findMatchesInComplexPattern(node);
                }
            }
        }



        @Override
        public boolean visit(SimpleName node) {
            if (node.getParent() instanceof DeclareAnnotationDeclaration) {
                String name = node.toString();
                if (name.equals(atSearchTypeSimpleName) || name.equals(atSearchTypeQualName)) {
                    // match found, now search for location
                    // +1 because the length is off by one...missing the '@'
                    int actualStart = node.getStartPosition() + 1 + offset;
                    int actualLength = name.length() - 1;
                    acceptMatch(new TypeReferenceMatch(decl, SearchMatch.A_ACCURATE,
                            actualStart, actualLength, false, participant, decl.getResource()));
                }
            }
            return super.visit(node);
        }

        protected boolean isSimpleMatch(String detail) {
            return searchTypeSimpleName.equals(detail) ||
                    detail.endsWith(dotSearchTypeSimpleName);
        }

        /**
         * Looks for matches in a complex AJ pattern.  Since we don't have bindings here,
         * only look for simple names.  All matches marked as inaccurate
         * @param node
         */
        protected void findMatchesInComplexPattern(PatternNode node) {
            String actualPatternText = getActualText(node);
            Map<String, List<Integer>> matches = PointcutUtilities.findAllIdentifiers(actualPatternText);
            List<Integer> matchLocs = matches.get(searchTypeSimpleName);
            if (matchLocs != null) {
                for (Integer matchLoc : matchLocs) {

                    // make the assumption that if the match is at a simple name, then
                    // this is a type match, but if there is a qual name, then match
                    // on the qualified name
                	boolean isQualified;
                    if (matchLoc > 0 && actualPatternText.charAt(matchLoc - 1) == '.') {
                        // we have a qual name.  Assume no spaces or special chars in there
                        String foundQualifier = findQualifier(actualPatternText, matchLoc);

                        if (! foundQualifier.equals(searchQualifier)) {
                            // qualifiers do not match
                            continue;
                        }

                        // adjust the matchLoc appropriately
                        // since we have assumed no spaces, then we can use the qualifier
                        // as a way to adjust the match location
                        matchLoc -= foundQualifier.length() + 1;
                        isQualified = true;
                    } else {
                    	isQualified = false;
                    }
                    int length = isQualified ? (searchQualifier.length() + ".".length() + searchTypeSimpleName.length()) :
                    	searchTypeSimpleName.length();

                    int start = matchLoc + // end of the name relative to the SignaturePattern's detail
                            node.getStartPosition() + // start of SignaturePattern relative to the declareDeclaration's start
                            offset; // the offset of the declareDeclaration from the start of the file
                    acceptMatch(new TypeReferenceMatch(decl, SearchMatch.A_INACCURATE,
                            start, length, false, participant, decl.getResource()));
                }
            }
        }
    }
}
