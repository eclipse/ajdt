/*******************************************************************************
 * Copyright (c) 2009, 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.core.codeconversion;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.aspectj.org.eclipse.jdt.core.dom.AjASTVisitor;
import org.aspectj.org.eclipse.jdt.core.dom.AnyWithAnnotationTypePattern;
import org.aspectj.org.eclipse.jdt.core.dom.BodyDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.DeclareAnnotationDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.DeclareParentsDeclaration;
import org.aspectj.org.eclipse.jdt.core.dom.IdentifierTypePattern;
import org.aspectj.org.eclipse.jdt.core.dom.PatternNode;
import org.aspectj.org.eclipse.jdt.core.dom.SignaturePattern;
import org.aspectj.org.eclipse.jdt.core.dom.SimpleName;
import org.aspectj.org.eclipse.jdt.core.dom.TypeCategoryTypePattern;
import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.javaelements.PointcutUtilities;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.internal.core.search.indexing.AbstractIndexer;
import org.eclipse.jdt.internal.core.search.indexing.SourceIndexer;
import org.eclipse.jdt.internal.core.search.indexing.SourceIndexerRequestor;

import org.eclipse.jdt.internal.compiler.ast.ASTNode;

/**
 * @author Andrew Eisenberg
 * @created May 25, 2010
 *
 */
public class AJSourceIndexerRequestor extends SourceIndexerRequestor {

    private final SourceIndexer indexer;

    public AJSourceIndexerRequestor(SourceIndexer indexer) {
        super(indexer);
        this.indexer = indexer;
    }


    /**
     * Here, we must index special AJ declarations like ITDs and declare parents/annotations
     */
    @Override
    public void enterField(FieldInfo fieldInfo) {
        super.enterField(fieldInfo);
        try {
            char[] fieldName = fieldInfo.name;
            char[] fieldType = fieldInfo.type;
            int last = CharOperation.lastIndexOf('$',fieldName) + 1;
            if (maybeDeclare(fieldName, fieldType)) {
                try {
                    char[] contents = getContents();
                    BodyDeclaration node = PointcutUtilities.createSingleBodyDeclarationNode(fieldInfo.declarationStart, getSourceEnd(fieldInfo), contents);
                    if (node instanceof DeclareParentsDeclaration) {

                        // found it!
                        DeclareParentsDeclaration declare = (DeclareParentsDeclaration) node;

                        // Visit the children
						AjASTVisitor typePatternVisitor = new AjASTVisitor() {

                            protected void index(String tokenString) {
                                char[][] tokens = tokenize(tokenString);
                                for (char[] token : tokens) {
                                    // must accept an uknown reference since we don't really know if this is a type, method, or field reference
                                    // source position is wrong, but this is ok.
                                    AJSourceIndexerRequestor.super
                                        .acceptUnknownReference(token, 0);
                                }

//                                AJSourceIndexerRequestor.super
//                                        .acceptAnnotationTypeReference(tokens,
//                                                node.getStartPosition(),
//                                                node.getStartPosition() + node.getLength());
                            }

                            public boolean visit(IdentifierTypePattern node) {
							    index(node.getTypePatternExpression());
								return true;
							}

                            @Override
							public boolean visit(AnyWithAnnotationTypePattern node) {
                                index(node.getTypePatternExpression());
								return true;
							}

                            @Override
							public boolean visit(TypeCategoryTypePattern node) {
                                index(node.getTypePatternExpression());
								return true;
							}

                            @Override
                            public boolean visit(SignaturePattern node) {
                                index(node.getDetail());
                                return true;
                            }

							//TODO: Add more as needed. Extract visitor to file if too large

						};

						declare.accept(typePatternVisitor);

                    } else if (node instanceof DeclareAnnotationDeclaration) {
                        // found it!
                        DeclareAnnotationDeclaration declare = (DeclareAnnotationDeclaration) node;
                        SimpleName annotationName = declare.getAnnotationName();
                        // index the annotation name
                        if (annotationName != null) {
                            String annotationStr = annotationName.toString();
                            if (annotationStr.startsWith("@")) {
                                annotationStr = annotationStr.substring(1);
                            }
                            char[][] splitChars = CharOperation.splitOn('.', annotationStr.toCharArray());
                            super.acceptTypeReference(splitChars, annotationName.getStartPosition(), annotationName.getStartPosition() + annotationName.getLength());
                        }

                        PatternNode targetPattern = declare.getPatternNode();

                        if (targetPattern instanceof IdentifierTypePattern) {
                            String detail = ((IdentifierTypePattern) targetPattern).getTypePatternExpression();
                            char[][] tokens = detail != null ? CharOperation.splitOn('.', detail.toCharArray()) : null;
                            super.acceptTypeReference(tokens, targetPattern.getStartPosition(), targetPattern.getStartPosition() + targetPattern.getLength());
                        } else if (targetPattern instanceof SignaturePattern) {
                            char[][] tokens = tokenize(((SignaturePattern) targetPattern).getDetail());
                            for (char[] token : tokens) {
                                // must accept an uknown reference since we don't really know if this is a type, method, or field reference
                                // source position is wrong, but this is ok.
                                super.acceptUnknownReference(token, targetPattern.getStartPosition());
                            }
                        }

                    }
                } catch (Exception e) {
                    // lots of things can go wrong, so surround in a big try-catch block and log to the console
                }
            } else if (maybeITD(fieldName, last)) {
                // assume this is an itd
                char[][] splits = CharOperation.splitAndTrimOn('$', fieldName);


                // should be array of length 2 at least.  Last element is the realMethodName
                // one before that is the simple name of the type
                // if more than length 2, then the rest are package names
                int length = splits.length;

                this.indexer.addFieldDeclaration(fieldInfo.type, splits[splits.length-1]);


                if (length > 1) {
                    // remove the last segment
                    char[][] newSplits = new char[splits.length-1][];
                    System.arraycopy(splits, 0, newSplits, 0, splits.length-1);

                    super.acceptUnknownReference(newSplits, fieldInfo.nameSourceStart, fieldInfo.nameSourceEnd - splits[length-1].length -1);
                }
            }
        } catch (Exception ignored) {
        }
    }


	private int getSourceEnd(FieldInfo fieldInfo) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
//		return fieldInfo.node.sourceEnd;
		Field f = fieldInfo.getClass().getDeclaredField("node");

		ASTNode node = (ASTNode) f.get(fieldInfo);
		return node.sourceEnd;
	}


    /**
     * @return
     */
    private char[] getContents() {
        SearchDocument searchDocument = (SearchDocument) ReflectionUtils.getPrivateField(AbstractIndexer.class, "document", indexer);
        if (searchDocument != null) {
            return searchDocument.getCharContents();
        } else {
            return new char[0];
        }
    }


    private char[][] tokenize(String detail) {
        if (detail == null) {
            return CharOperation.NO_CHAR_CHAR;
        }

        Map<String, List<Integer>> allIds = PointcutUtilities.findAllIdentifiers(detail);
        char[][] tokens = new char[allIds.size()][];
        int i = 0;
        for (String token : allIds.keySet()) {
            tokens[i++] = token.toCharArray();
        }
        return tokens;
    }


    private boolean maybeDeclare(char[] fieldName, char[] fieldType) {
        return CharOperation.equals("declare".toCharArray(), fieldType) &&
               (CharOperation.equals("parents".toCharArray(), fieldName) ||
            		   CharOperation.equals("$type".toCharArray(), fieldName) ||
            		   CharOperation.equals("$method".toCharArray(), fieldName) ||
            		   CharOperation.equals("$constructor".toCharArray(), fieldName) ||
            		   CharOperation.equals("$field".toCharArray(), fieldName)
            		   );
    }


    /**
     * @param fieldName
     * @param lastDollar
     * @return
     */
    private boolean maybeITD(char[] fieldName, int lastDollar) {
        // use > 1 because $ at the beginning will probably mean declare annotation
        // @type --> $type, @field --> $field, etc
        return lastDollar > 1 && lastDollar < fieldName.length;
    }

    @Override
    public void enterMethod(MethodInfo methodInfo) {
        super.enterMethod(methodInfo);
        try {
            char[] methodName = methodInfo.name;
            int last = CharOperation.lastIndexOf('$',methodName) + 1;
            if (maybeITD(methodName, last)) {
                // assume this is an itd
                char[] realMethodName = CharOperation.subarray(methodName, last, methodName.length);
                boolean isConstructor = false;
                if (CharOperation.equals("new".toCharArray(), realMethodName)) {
                    isConstructor = true;
                } else {
                    this.indexer.addMethodDeclaration(realMethodName, methodInfo.parameterTypes, methodInfo.returnType, methodInfo.exceptionTypes);
                }

                // now index the type
                if (last > 1) {
                    char[][] splits = CharOperation.splitAndTrimOn('$', methodName);

                    // should be array of length 2 at least.  Last element is the realMethodName
                    // one before that is the simple name of the type
                    // if more than length 2, then the rest are package names
                    int length = splits.length;

                    if (length > 1) {
                        // remove the last segment
                        char[][] newSplits = new char[splits.length-1][];
                        System.arraycopy(splits, 0, newSplits, 0, splits.length-1);

                        super.acceptUnknownReference(newSplits, methodInfo.nameSourceStart, methodInfo.nameSourceEnd - splits[length-1].length -1);
                        if (isConstructor) {
                            int argCount = methodInfo.parameterTypes == null ? 0 : methodInfo.parameterTypes.length;
                            this.indexer.addConstructorDeclaration(splits[length-2],
                                    argCount, null, methodInfo.parameterTypes, methodInfo.parameterNames,
                                    methodInfo.modifiers, methodInfo.declaringPackageName, methodInfo.declaringTypeModifiers,
                                    methodInfo.exceptionTypes, methodInfo.extraFlags);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
