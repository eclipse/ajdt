/*******************************************************************************
 * Copyright (c) 2004, 2005, 2008 SpringSource, IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser   - initial implementation
 *     Andrew Eisenberg - update for Eclipse 3.5
 *******************************************************************************/
package org.eclipse.ajdt.core.parserbridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.aspectj.ajdt.internal.compiler.ast.AdviceDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.DeclareDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.InterTypeConstructorDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.InterTypeDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.InterTypeFieldDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.InterTypeMethodDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.PointcutDeclaration;
import org.aspectj.asm.IProgramElement;
import org.aspectj.org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.aspectj.org.eclipse.jdt.core.compiler.IProblem;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.parser.Parser;
import org.aspectj.weaver.patterns.DeclareAnnotation;
import org.aspectj.weaver.patterns.DeclareErrorOrWarning;
import org.aspectj.weaver.patterns.DeclareParents;
import org.aspectj.weaver.patterns.DeclarePrecedence;
import org.eclipse.ajdt.core.ReflectionUtils;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitInfo;
import org.eclipse.ajdt.core.javaelements.AdviceElement;
import org.eclipse.ajdt.core.javaelements.AdviceElementInfo;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.AspectElementInfo;
import org.eclipse.ajdt.core.javaelements.AspectJMemberElement;
import org.eclipse.ajdt.core.javaelements.AspectJMemberElementInfo;
import org.eclipse.ajdt.core.javaelements.CompilationUnitTools;
import org.eclipse.ajdt.core.javaelements.DeclareElement;
import org.eclipse.ajdt.core.javaelements.DeclareElementInfo;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElementInfo;
import org.eclipse.ajdt.core.javaelements.PointcutElement;
import org.eclipse.ajdt.core.javaelements.PointcutElementInfo;
import org.eclipse.ajdt.internal.core.parserbridge.CompilerASTNodeCompatibilityWrapper;
import org.eclipse.ajdt.internal.core.parserbridge.IAspectSourceElementRequestor;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.ast.Annotation;
import org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.ArrayInitializer;
import org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ArrayReference;
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference;
import org.eclipse.jdt.internal.compiler.ast.CharLiteral;
import org.eclipse.jdt.internal.compiler.ast.ClassLiteralAccess;
import org.eclipse.jdt.internal.compiler.ast.DoubleLiteral;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FalseLiteral;
import org.eclipse.jdt.internal.compiler.ast.FieldReference;
import org.eclipse.jdt.internal.compiler.ast.FloatLiteral;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.ast.IntLiteral;
import org.eclipse.jdt.internal.compiler.ast.JavadocArrayQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.JavadocArraySingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.JavadocImplicitTypeReference;
import org.eclipse.jdt.internal.compiler.ast.JavadocQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.JavadocSingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.LongLiteral;
import org.eclipse.jdt.internal.compiler.ast.MarkerAnnotation;
import org.eclipse.jdt.internal.compiler.ast.MemberValuePair;
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation;
import org.eclipse.jdt.internal.compiler.ast.NullLiteral;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.StringLiteral;
import org.eclipse.jdt.internal.compiler.ast.StringLiteralConcatenation;
import org.eclipse.jdt.internal.compiler.ast.TrueLiteral;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.ast.Wildcard;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.core.AnnotatableInfo;
import org.eclipse.jdt.internal.core.CompilationUnitStructureRequestor;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.JavaElementInfo;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.LocalVariable;
import org.eclipse.jdt.internal.core.NamedMember;
import org.eclipse.jdt.internal.core.PackageDeclaration;
import org.eclipse.jdt.internal.core.SourceMethodElementInfo;

/**
 * This class can be used as a source requestor for the JDT parser *OR*
 * the AspectJ parser.  That is why we need to use so many fully qualified names
 *
 * @author Luzius Meisser
 * @author Andrew Eisenberg
 */
public class AJCompilationUnitStructureRequestor extends
        CompilationUnitStructureRequestor implements IAspectSourceElementRequestor {


    private static final char[] VOID = new char[]{'v', 'o','i', 'd'};


    private final Stack<org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[]> typeParameterStack = new Stack<>();

    public AJCompilationUnitStructureRequestor(ICompilationUnit unit, AJCompilationUnitInfo unitInfo, Map newElements) {
        super(unit, unitInfo, newElements);
    }

    public void setParser(Parser parser){
        CompilerOptions options = new CompilerOptions();
        ProblemReporter probrep = new ProblemReporter(null, options, null);
        org.eclipse.jdt.internal.compiler.parser.Parser otherParser =
            new org.eclipse.jdt.internal.compiler.parser.Parser(probrep, false);
        setJDTParser(otherParser);
    }

    private void setJDTParser(org.eclipse.jdt.internal.compiler.parser.Parser parser) {
        this.parser = parser;
    }

    public void setSource(char[] source) {
        this.parser.scanner.source = source;
    }

    public void exitCompilationUnit(int declarationEnd) {
        super.exitCompilationUnit(declarationEnd);

        // not keeping track of annotations
        // and this ensures that itd aware content assist
        // still works when there are large numbers ofannotations
        // see bug 268620
        this.unitInfo.annotationNumber = 0;

    }


    /**
     * Common processing for AJ method infos and JDT method infos
     */
    /*
     * a little kludgy here.  super type creates JavaElementInfo on the exitMethod
     * this type creates JavaElementInfos on the enterMethod
     */
    public void enterMethod(
            int declarationStart,
            int modifiers,
            char[] returnType,
            char[] name,
            int nameSourceStart,
            int nameSourceEnd,
            char[][] parameterTypes,
            char[][] parameterNames,
            char[][] exceptionTypes,
            boolean isConstructor,
            boolean isAnnotation,
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] typeParameters,
            AbstractMethodDeclaration methodDeclaration) {

        if (methodDeclaration instanceof AdviceDeclaration){
            enterAdvice(declarationStart, modifiers, returnType, name, nameSourceStart, nameSourceEnd, parameterTypes, parameterNames, exceptionTypes, (AdviceDeclaration)methodDeclaration);
            return;
        }

        if (methodDeclaration instanceof PointcutDeclaration){
            enterPointcut(declarationStart, modifiers, returnType, name, nameSourceStart, nameSourceEnd, parameterTypes, parameterNames, exceptionTypes, (PointcutDeclaration)methodDeclaration);
            return;
        }

        if (methodDeclaration instanceof DeclareDeclaration){
            enterDeclare(declarationStart, modifiers, returnType, name, nameSourceStart, nameSourceEnd, parameterTypes, parameterNames, exceptionTypes, (DeclareDeclaration)methodDeclaration);
            return;
        }

        if (methodDeclaration instanceof InterTypeDeclaration){
            typeParameterStack.push(typeParameters);
            enterInterTypeDeclaration(declarationStart, modifiers, returnType, name, nameSourceStart, nameSourceEnd, parameterTypes, parameterNames, exceptionTypes, (InterTypeDeclaration)methodDeclaration);
            return;
        }

        org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo mi =
            new org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo();
        mi.declarationStart = declarationStart;
        mi.modifiers = modifiers;
        mi.name = name;
        mi.nameSourceStart = nameSourceStart;
        mi.nameSourceEnd = nameSourceEnd;
        mi.parameterNames = parameterNames;
        mi.parameterTypes = parameterTypes;
        mi.exceptionTypes = exceptionTypes;
        mi.returnType = returnType;
        mi.isConstructor = isConstructor;
        mi.isAnnotation = isAnnotation;
        mi.typeParameters = convertToJDTTypeParameters(typeParameters);
        if (methodDeclaration != null) {
            try {
                mi.annotations = convertToJDTAnnotations(methodDeclaration.annotations);
            } catch (NullPointerException e) {
                // ensure that this annotation handling code does not break existing functionality
                // catch and log the exception
            }
        }
        super.enterMethod(mi);
    }

    @Override
    public void enterMethod(org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo mi) {
        enterMethod(mi.declarationStart,
                    mi.modifiers,
                    mi.returnType,
                    mi.name,
                    mi.nameSourceStart,
                    mi.nameSourceEnd,
                    mi.parameterTypes,
                    mi.parameterNames,
                    mi.exceptionTypes,
                    mi.isConstructor,
                    mi.isAnnotation,
                    convertToAJTypeParameters(mi.typeParameters),
                    null);
    }

    public void enterMethod(org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo mi) {
        enterMethod(mi.declarationStart,
                    mi.modifiers,
                    mi.returnType,
                    mi.name,
                    mi.nameSourceStart,
                    mi.nameSourceEnd,
                    mi.parameterTypes,
                    mi.parameterNames,
                    mi.exceptionTypes,
                    mi.isConstructor,
                    mi.isAnnotation,
                    mi.typeParameters,
                    null);
    }

    public void enterMethod(org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo mi,AbstractMethodDeclaration mdecl) {
        enterMethod(mi.declarationStart,
                    mi.modifiers,
                    mi.returnType,
                    mi.name,
                    mi.nameSourceStart,
                    mi.nameSourceEnd,
                    mi.parameterTypes,
                    mi.parameterNames,
                    mi.exceptionTypes,
                    mi.isConstructor,
                    mi.isAnnotation,
                    convertToAJTypeParameters(mi.typeParameters),
                    mdecl);
    }

    public void enterMethod(org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo mi,AbstractMethodDeclaration mdecl) {
        enterMethod(mi.declarationStart,
                    mi.modifiers,
                    mi.returnType,
                    mi.name,
                    mi.nameSourceStart,
                    mi.nameSourceEnd,
                    mi.parameterTypes,
                    mi.parameterNames,
                    mi.exceptionTypes,
                    mi.isConstructor,
                    mi.isAnnotation,
                    mi.typeParameters,
                    mdecl);
    }
    /* default */ static String[] convertTypeNamesToSigsCopy(char[][] typeNames) {
        if (typeNames == null)
            return CharOperation.NO_STRINGS;
        int n = typeNames.length;
        if (n == 0)
            return CharOperation.NO_STRINGS;
        String[] typeSigs = new String[n];
        for (int i = 0; i < n; ++i) {
            typeSigs[i] = Signature.createTypeSignature(typeNames[i], false);
        }
        return typeSigs;
    }

    public void enterAdvice(
            int declarationStart,
            int modifiers,
            char[] returnType,
            char[] name,
            int nameSourceStart,
            int nameSourceEnd,
            char[][] parameterTypes,
            char[][] parameterNames,
            char[][] exceptionTypes,
            AdviceDeclaration decl) {

        Object parentInfo = this.infoStack.peek();
        JavaElement parentHandle= (JavaElement) this.handleStack.peek();
        AdviceElement handle;

        // translate nulls to empty arrays
        if (parameterTypes == null) {
            parameterTypes= CharOperation.NO_CHAR_CHAR;
        }
        if (parameterNames == null) {
            parameterNames= CharOperation.NO_CHAR_CHAR;
        }
        if (exceptionTypes == null) {
            exceptionTypes= CharOperation.NO_CHAR_CHAR;
        }

        String nameString = decl.kind.getName();
        String[] parameterTypeSigs = convertTypeNamesToSigsCopy(parameterTypes);
        handle = new AdviceElement(parentHandle, nameString, parameterTypeSigs);

        resolveDuplicates(handle);

        AdviceElementInfo info = new AdviceElementInfo();
        info.setAJKind(IProgramElement.Kind.ADVICE);
        IProgramElement.ExtraInformation extraInfo = new IProgramElement.ExtraInformation();
        info.setAJExtraInfo(extraInfo);
        extraInfo.setExtraAdviceInformation(decl.kind.getName());

        info.setSourceRangeStart(declarationStart);
        info.setName(nameString.toCharArray());
        info.setNameSourceStart(nameSourceStart);
        info.setNameSourceEnd(nameSourceEnd);
        info.setFlags(modifiers);
        info.setArgumentNames(parameterNames);
        //info.setArgumentTypeNames(parameterTypes);
        info.setReturnType(returnType == null ? VOID : returnType);
        info.setExceptionTypeNames(exceptionTypes);

        addToChildren(parentInfo, handle);
        this.newElements.put(handle, info);
        this.infoStack.push(info);
        this.handleStack.push(handle);
    }

    public void enterInterTypeDeclaration(
            int declarationStart,
            int modifiers,
            char[] returnType,
            char[] name,
            int nameSourceStart,
            int nameSourceEnd,
            char[][] parameterTypes,
            char[][] parameterNames,
            char[][] exceptionTypes,
            InterTypeDeclaration decl) {

        nameSourceEnd = nameSourceStart + decl.getDeclaredSelector().length - 1;

        Object parentInfo = this.infoStack.peek();
        JavaElement parentHandle= (JavaElement) this.handleStack.peek();
        IntertypeElement handle;

        // translate nulls to empty arrays
        if (parameterTypes == null) {
            parameterTypes= CharOperation.NO_CHAR_CHAR;
        }
        if (parameterNames == null) {
            parameterNames= CharOperation.NO_CHAR_CHAR;
        }
        if (exceptionTypes == null) {
            exceptionTypes= CharOperation.NO_CHAR_CHAR;
        }

        String nameString = concat(decl.getOnType().getTypeName()) + "." + new String(decl.getDeclaredSelector()); //$NON-NLS-1$

        String[] parameterTypeSigs = convertTypeNamesToSigsCopy(parameterTypes);
        handle = IntertypeElement.create(IntertypeElement.getJemDelimter(decl), parentHandle, nameString, parameterTypeSigs);

        resolveDuplicates(handle);

        IntertypeElementInfo info = new IntertypeElementInfo();

        if (decl instanceof InterTypeFieldDeclaration)
            info.setAJKind(IProgramElement.Kind.INTER_TYPE_FIELD);
        else if (decl instanceof InterTypeMethodDeclaration)
            info.setAJKind(IProgramElement.Kind.INTER_TYPE_METHOD);
        else if (decl instanceof InterTypeConstructorDeclaration)
            info.setAJKind(IProgramElement.Kind.INTER_TYPE_CONSTRUCTOR);
        else
            info.setAJKind(IProgramElement.Kind.INTER_TYPE_PARENT);

        // Fix for 116846 - incorrect icons for itds - use declaredModifiers instead
        info.setAJAccessibility(CompilationUnitTools.getAccessibilityFromModifierCode(decl.declaredModifiers));
        info.setAJModifiers(CompilationUnitTools.getModifiersFromModifierCode(decl.declaredModifiers));

        info.setSourceRangeStart(declarationStart);
      info.setName(nameString.toCharArray());
        info.setNameSourceStart(nameSourceStart);
        info.setNameSourceEnd(nameSourceEnd);

        if (decl.getOnType() != null) {
            info.setTargetTypeStart(decl.getOnType().sourceStart);
            info.setTargetTypeEnd(decl.getOnType().sourceEnd+1);
        }

        info.setTargetType(concat(decl.getOnType().getTypeName()).toCharArray());
        info.setFlags(modifiers);
        info.setDeclaredModifiers(decl.declaredModifiers);
        info.setArgumentNames(parameterNames);
        //info.setArgumentTypeNames(parameterTypes);
        info.setReturnType(returnType == null ? VOID : returnType);
        info.setExceptionTypeNames(exceptionTypes);
        try {
            info.setAnnotations(createJDTAnnotations(decl.annotations, info, handle));
        } catch (Exception e) {
            // ensure that this annotation handling code does not break existing functionality
            // catch and log the exception
        }

        addToChildren(parentInfo, handle);
        this.newElements.put(handle, info);
        this.infoStack.push(info);
        this.handleStack.push(handle);
    }


    private IAnnotation[] createJDTAnnotations(
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.Annotation[] ajAnnotations, AnnotatableInfo parentInfo, JavaElement parentHandle) {
        Annotation[] jdtAnnotations = convertToJDTAnnotations(ajAnnotations);
        IAnnotation[] realAnnotations = org.eclipse.jdt.internal.core.Annotation.NO_ANNOTATIONS;
        if (jdtAnnotations != null) {
            realAnnotations = new IAnnotation[jdtAnnotations.length];
            for (int i = 0; i < jdtAnnotations.length; i++) {
                org.eclipse.jdt.internal.compiler.ast.Annotation annotation = jdtAnnotations[i];
                realAnnotations[i] = acceptAnnotation(annotation, parentInfo, parentHandle);
            }
        }
        return realAnnotations;
    }

    private String concat(char[][] declName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < declName.length; i++) {
            String namePart = new String(declName[i]);
            sb.append(namePart);
            if (i < declName.length-1) {
                sb.append(".");
            }
        }
        return sb.toString();
    }

    public void enterDeclare(
            int declarationStart,
            int modifiers,
            char[] returnType,
            char[] name,
            int nameSourceStart,
            int nameSourceEnd,
            char[][] parameterTypes,
            char[][] parameterNames,
            char[][] exceptionTypes,
            DeclareDeclaration decl) {

        nameSourceStart += 8;
        nameSourceEnd = nameSourceStart;
        Object parentInfo = this.infoStack.peek();
        JavaElement parentHandle= (JavaElement) this.handleStack.peek();
        DeclareElement handle;

        DeclareElementInfo info = new DeclareElementInfo();

        if (decl.declareDecl instanceof DeclareErrorOrWarning){
            if (((DeclareErrorOrWarning)decl.declareDecl).isError()){
                info.setAJKind(IProgramElement.Kind.DECLARE_ERROR);
                nameSourceEnd += 4;
            }else{
                info.setAJKind(IProgramElement.Kind.DECLARE_WARNING);
                nameSourceEnd += 6;
            }
        } else if (decl.declareDecl instanceof DeclareParents){
            info.setAJKind(IProgramElement.Kind.DECLARE_PARENTS);
            nameSourceEnd += 6;
        } else if (decl.declareDecl instanceof DeclarePrecedence){
            info.setAJKind(IProgramElement.Kind.DECLARE_PRECEDENCE);
            nameSourceEnd += 9;
        } else if (decl.declareDecl instanceof DeclareAnnotation){
            DeclareAnnotation anno = (DeclareAnnotation)decl.declareDecl;
            if (anno.isDeclareAtConstuctor()) {
                info.setAJKind(IProgramElement.Kind.DECLARE_ANNOTATION_AT_CONSTRUCTOR);
                nameSourceEnd += "@constructor".length()-1; //$NON-NLS-1$
            } else if (anno.isDeclareAtField()) {
                info.setAJKind(IProgramElement.Kind.DECLARE_ANNOTATION_AT_FIELD);
                nameSourceEnd += "@field".length()-1; //$NON-NLS-1$
            } else if (anno.isDeclareAtMethod()) {
                info.setAJKind(IProgramElement.Kind.DECLARE_ANNOTATION_AT_METHOD);
                nameSourceEnd += "@method".length()-1; //$NON-NLS-1$
            } else if (anno.isDeclareAtType()) {
                info.setAJKind(IProgramElement.Kind.DECLARE_ANNOTATION_AT_TYPE);
                nameSourceEnd += "@type".length()-1; //$NON-NLS-1$
            }

            info.setAnnotationRemover(anno.isRemover());
        } else {
            //assume declare soft
            info.setAJKind(IProgramElement.Kind.DECLARE_SOFT);
            nameSourceEnd += 3;
        }
        String nameString = info.getAJKind().toString();


        // translate nulls to empty arrays
        if (parameterTypes == null) {
            parameterTypes= CharOperation.NO_CHAR_CHAR;
        }
        if (parameterNames == null) {
            parameterNames= CharOperation.NO_CHAR_CHAR;
        }
        if (exceptionTypes == null) {
            exceptionTypes= CharOperation.NO_CHAR_CHAR;
        }

        String[] parameterTypeSigs = convertTypeNamesToSigsCopy(parameterTypes);

        handle = new DeclareElement(parentHandle, nameString, parameterTypeSigs);

        resolveDuplicates(handle);

        info.setSourceRangeStart(declarationStart);
      info.setName(nameString.toCharArray());
        info.setNameSourceStart(nameSourceStart);
        info.setNameSourceEnd(nameSourceEnd);
        info.setFlags(modifiers);
        info.setArgumentNames(parameterNames);
        //info.setArgumentTypeNames(parameterTypes);
        info.setReturnType(returnType == null ? VOID : returnType);
        info.setExceptionTypeNames(exceptionTypes);

        addToChildren(parentInfo, handle);
        this.newElements.put(handle, info);
        this.infoStack.push(info);
        this.handleStack.push(handle);
    }

    public void enterPointcut(
            int declarationStart,
            int modifiers,
            char[] returnType,
            char[] name,
            int nameSourceStart,
            int nameSourceEnd,
            char[][] parameterTypes,
            char[][] parameterNames,
            char[][] exceptionTypes,
            PointcutDeclaration decl) {

        Object parentInfo = this.infoStack.peek();
        JavaElement parentHandle= (JavaElement) this.handleStack.peek();
        PointcutElement handle;

        // translate nulls to empty arrays
        if (parameterTypes == null) {
            parameterTypes= CharOperation.NO_CHAR_CHAR;
        }
        if (parameterNames == null) {
            parameterNames= CharOperation.NO_CHAR_CHAR;
        }
        if (exceptionTypes == null) {
            exceptionTypes= CharOperation.NO_CHAR_CHAR;
        }

        String[] parameterTypeSigs = convertTypeNamesToSigsCopy(parameterTypes);
        handle = new PointcutElement(parentHandle, new String(name), parameterTypeSigs);

        resolveDuplicates(handle);

        PointcutElementInfo info = new PointcutElementInfo();

        info.setAJKind(IProgramElement.Kind.POINTCUT);
        info.setAJAccessibility(CompilationUnitTools.getAccessibilityFromModifierCode(decl.modifiers));
        info.setAJModifiers(CompilationUnitTools.getModifiersFromModifierCode(decl.modifiers));

        info.setSourceRangeStart(declarationStart);
        info.setSourceRangeEnd(decl.sourceEnd+1);
      info.setName(name);
        info.setNameSourceStart(nameSourceStart);
        info.setNameSourceEnd(nameSourceEnd);
        info.setFlags(modifiers);
        info.setArgumentNames(parameterNames);
        //info.setArgumentTypeNames(parameterTypes);
        info.setReturnType(returnType == null ? VOID : returnType);
        info.setExceptionTypeNames(exceptionTypes);

        addToChildren(parentInfo, handle);
        this.newElements.put(handle, info);
        this.infoStack.push(info);
        this.handleStack.push(handle);
    }


    public void acceptProblem(CategorizedProblem problem) {
        if ((problem.getID() & IProblem.Syntax) != 0){
            this.hasSyntaxErrors = true;
        }
    }

    private void addToChildren(Object parentInfo, JavaElement handle) {
        ArrayList childrenList = (ArrayList) this.children.get(parentInfo);
        if (childrenList == null)
            this.children.put(parentInfo, childrenList = new ArrayList());
        childrenList.add(handle);
    }

    public void enterConstructor(org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo) {
        org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo mi =
            new org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo();
        mi.declarationStart = methodInfo.declarationStart;
        mi.modifiers = methodInfo.modifiers;
        mi.name = methodInfo.name;
        mi.nameSourceStart = methodInfo.nameSourceStart;
        mi.nameSourceEnd = methodInfo.nameSourceEnd;
        mi.parameterNames = methodInfo.parameterNames;
        mi.parameterTypes = methodInfo.parameterTypes;
        mi.exceptionTypes = methodInfo.exceptionTypes;
        mi.isConstructor = true;
        enterConstructor(mi);
    }

    public void enterField(org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.FieldInfo fieldInfo) {
        org.eclipse.jdt.internal.compiler.ISourceElementRequestor.FieldInfo fi =
            new org.eclipse.jdt.internal.compiler.ISourceElementRequestor.FieldInfo();
        fi.declarationStart = fieldInfo.declarationStart;
        fi.modifiers = fieldInfo.modifiers;
        fi.type = fieldInfo.type;
        fi.name = fieldInfo.name;
        fi.nameSourceStart = fieldInfo.nameSourceStart;
        fi.nameSourceEnd = fieldInfo.nameSourceEnd;
        enterField(fi);
    }


    /**
     * Enter Type from AJ side w/ Aspect information
     */
    public void enterType(org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo,
            boolean isAspect, boolean isPrivilegedAspect) {
        enterType(typeInfo.declarationStart,
                  typeInfo.modifiers,
                  typeInfo.name,
                  typeInfo.nameSourceStart,
                  typeInfo.nameSourceEnd,
                  typeInfo.superclass,
                  typeInfo.superinterfaces,
                  convertToJDTTypeParameters(typeInfo.typeParameters),
                  isAspect,
                  isPrivilegedAspect);
    }

    /**
     * Enter Type from AJ side
     */
    public void enterType(org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo) {
        enterType(typeInfo.declarationStart,
                  typeInfo.modifiers,
                  typeInfo.name,
                  typeInfo.nameSourceStart,
                  typeInfo.nameSourceEnd,
                  typeInfo.superclass,
                  typeInfo.superinterfaces,
                  convertToJDTTypeParameters(typeInfo.typeParameters),
                  false, false);
    }

    /**
     * XXX This should override something
     */
    public void enterType(org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo,
            boolean isAspect, boolean isPrivilegedAspect) {
        enterType(typeInfo.declarationStart,
                  typeInfo.modifiers,
                  typeInfo.name,
                  typeInfo.nameSourceStart,
                  typeInfo.nameSourceEnd,
                  typeInfo.superclass,
                  typeInfo.superinterfaces,
                  typeInfo.typeParameters,
                  isAspect,
                  isPrivilegedAspect);
    }

    /**
     * enter type from JDT side
     */
    @Override
    public void enterType(org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo) {
        enterType(typeInfo.declarationStart,
                  typeInfo.modifiers,
                  typeInfo.name,
                  typeInfo.nameSourceStart,
                  typeInfo.nameSourceEnd,
                  typeInfo.superclass,
                  typeInfo.superinterfaces,
                  typeInfo.typeParameters,
                  false,
                  false);
    }

    /**
     * Common processing for both AJ and JDT types
     */
    protected void enterType(
            int declarationStart,
            int modifiers,
            char[] name,
            int nameSourceStart,
            int nameSourceEnd,
            char[] superclass,
            char[][] superinterfaces,
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] tpInfo,
            boolean isAspect,
            boolean isPrivilegedAspect) {

        AspectTypeInfo typeInfo =
            new AspectTypeInfo();
        typeInfo.declarationStart = declarationStart;
        typeInfo.modifiers = modifiers;
        typeInfo.name = name;
        typeInfo.nameSourceStart = nameSourceStart;
        typeInfo.nameSourceEnd = nameSourceEnd;
        typeInfo.superclass = superclass;
        typeInfo.superinterfaces = superinterfaces;
        typeInfo.typeParameters = tpInfo;
        typeInfo.isAspect = isAspect;
        typeInfo.isPrivilegedAspect = isPrivilegedAspect;

        if (!isAspect) {
            super.enterType(typeInfo);
        } else {
            JavaElement parentHandle= (JavaElement) this.handleStack.peek();
            Object parentInfo = this.infoStack.peek();

            String nameString= new String(name);
            AspectElement handle = new AspectElement(parentHandle, nameString);
            resolveDuplicates(handle);
            this.infoStack.push(typeInfo);
            this.handleStack.push(handle);

            if (parentHandle.getElementType() == IJavaElement.TYPE) {
                ((org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo) parentInfo).
                    childrenCategories.put(handle, typeInfo.categories);
            }
            addToChildren(parentInfo, handle);
        }

    }

    @Override
    public void exitType(int declarationEnd) {
        Object handle = this.handleStack.peek();
        if (handle instanceof AspectElement) {
            AspectElement aspectHandle = (AspectElement) handle;

            AspectTypeInfo typeInfo = (AspectTypeInfo) this.infoStack.peek();
            AspectElementInfo info = createAspectElementInfo(typeInfo, aspectHandle);
            info.setSourceRangeEnd(declarationEnd);
            info.setChildren(getChildren(typeInfo));

            this.handleStack.pop();
            this.infoStack.pop();
        } else {
            super.exitType(declarationEnd);
        }

    }

    private AspectElementInfo createAspectElementInfo (AspectTypeInfo typeInfo, AspectElement handle) {
        AspectElementInfo info =
            typeInfo.anonymousMember ?
                new AspectElementInfo() {
                    public boolean isAnonymousMember() {
                        return true;
                    }
                } :
            new AspectElementInfo();

        // AJ pieces
        info.setAJKind(IProgramElement.Kind.ASPECT);
        info.setAJAccessibility(CompilationUnitTools.getAccessibilityFromModifierCode(typeInfo.modifiers));
        info.setAJModifiers(CompilationUnitTools.getModifiersFromModifierCode(typeInfo.modifiers));
        info.setPrivileged(typeInfo.isPrivilegedAspect);

        // JDT pieces - copied from super
        info.setHandle(handle);
        info.setSourceRangeStart(typeInfo.declarationStart);
        info.setFlags(typeInfo.modifiers);
        info.setNameSourceStart(typeInfo.nameSourceStart);
        info.setNameSourceEnd(typeInfo.nameSourceEnd);
        JavaModelManager manager = JavaModelManager.getJavaModelManager();
        char[] superclass = typeInfo.superclass;
        info.setSuperclassName(superclass == null ? null : manager.intern(superclass));
        char[][] superinterfaces = typeInfo.superinterfaces;
        for (int i = 0, length = superinterfaces == null ? 0 : superinterfaces.length; i < length; i++)
            superinterfaces[i] = manager.intern(superinterfaces[i]);
        info.setSuperInterfaceNames(superinterfaces);
        info.addCategories(handle, typeInfo.categories);
        this.newElements.put(handle, info);

        if (typeInfo.typeParameters != null) {
            for (int i = 0, length = typeInfo.typeParameters.length; i < length; i++) {
                org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo typeParameterInfo = typeInfo.typeParameters[i];
                acceptTypeParameter(typeParameterInfo, info);
            }
        }
        if (typeInfo.annotations != null) {
            int length = typeInfo.annotations.length;
            this.unitInfo.annotationNumber += length;
            for (int i = 0; i < length; i++) {
                org.eclipse.jdt.internal.compiler.ast.Annotation annotation = typeInfo.annotations[i];
                acceptAnnotation(annotation, info, handle);
            }
        }
        if (typeInfo.childrenCategories != null) {
          for (Map.Entry<IJavaElement, char[][]> entry : (Iterable<Map.Entry<IJavaElement, char[][]>>) typeInfo.childrenCategories.entrySet()) {
            info.addCategories(entry.getKey(), entry.getValue());
          }
        }
        return info;
    }

    /**
     * XXX This should override something
     */
    public void acceptImport(int declarationStart, int declarationEnd, char[] name, boolean onDemand, int modifiers) {
		super.acceptImport(declarationStart, declarationEnd, declarationStart + "import ".length(), declarationEnd, CharOperation.splitOn('.', name), onDemand, modifiers);
    }

	public void acceptImport(int declarationStart, int declarationEnd,
            char[][] tokens, boolean onDemand, int modifiers) {
	    super.acceptImport(declarationStart, declarationEnd, declarationStart + "import ".length(), declarationEnd, tokens, onDemand, modifiers);

    }

    /**
     * use {@link #acceptPackage(ImportReference)} instead
     * @deprecated
     */
    public void acceptPackage(int declarationStart, int declarationEnd, char[] name) {

        // AJDT 1.6 ADE---copied from CompilationUnitStructureProvider.acceptPackage(ImportReference)
        JavaElementInfo parentInfo = (JavaElementInfo) this.infoStack.peek();
        JavaElement parentHandle= (JavaElement) this.handleStack.peek();
        PackageDeclaration handle = null;

        if (parentHandle.getElementType() == IJavaElement.COMPILATION_UNIT) {
            handle = createPackageDeclaration(parentHandle, new String(name));
        }
        else {
            Assert.isTrue(false); // Should not happen
        }
        resolveDuplicates(handle);

        AJAnnotatableInfo info = new AJAnnotatableInfo() ;
        info.setSourceRangeStart(declarationStart);
        info.setSourceRangeEnd(declarationEnd);

        addToChildren(parentInfo, handle);
        // Semantically a Map<PackageDeclaration, AJAnnotatableInfo>, but declared as raw type in JDT
        //noinspection unchecked
        this.newElements.put(handle, info);
    }

    public void exitConstructor(int declarationEnd) {
        // must ensure that we call exitMethod defined in this class so that constructor arguments are
        // properly initialized
        this.exitMethod(declarationEnd, (org.aspectj.org.eclipse.jdt.internal.compiler.ast.Expression) null);
    }


    /* AJDT 1.7 */
    /*
     * a little kludgy here.  super type creates JavaElementInfo on the exitMethod
     * this type creates JavaElementInfos on the enterMethod
     */
    public void exitMethod(int declarationEnd, org.aspectj.org.eclipse.jdt.internal.compiler.ast.Expression defaultValue) {
        NamedMember handle = (NamedMember) this.handleStack.peek();
        if (! (handle instanceof AspectJMemberElement)) {
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo = (org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo) this.infoStack.peek();
            // by passing null to the super method, we do not keep track of default annotation values
            super.exitMethod(declarationEnd, null);
            if (handle instanceof IMethod && methodInfo.node == null && methodInfo.parameterNames != null && methodInfo.parameterNames.length > 0) {
                // need to create method parameters since they weren't created in Super
                ILocalVariable[] methodParameters = createMethodParameters(handle, ((IMethod) handle).getParameterTypes(), methodInfo.parameterNames, methodInfo.declarationStart);
                ReflectionUtils.setPrivateField(SourceMethodElementInfo.class, "arguments", (SourceMethodElementInfo) newElements.get(handle), methodParameters);
            }
            return;
        }

        this.handleStack.pop();

        AspectJMemberElementInfo info = (AspectJMemberElementInfo) this.infoStack.pop();
        info.setSourceRangeEnd(declarationEnd);
        info.setChildren(getChildren(info));
        // only ITDs have type parameters
        if (handle instanceof IntertypeElement) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] typeParameters =
              this.typeParameterStack.pop();
            if (typeParameters != null) {
                org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] jdtTypeParameters = convertToJDTTypeParameters(typeParameters);
              for (org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo jdtTypeParameter : jdtTypeParameters) {
                acceptTypeParameter(jdtTypeParameter, info);
              }
            }
            // JDT requires that the arguments field is set.  We can't create the arguments exactly
            // since we don't have full slocs and we don't have annotations, but we can get pretty close
            info.setArguments(createMethodParameters(handle, ((AspectJMemberElement) handle).getParameterTypes(), info.getArgumentNames(), info.getDeclarationSourceStart()));
        }
    }

    //@Override
    public void exitRecordComponent(int declarationEnd, int declarationSourceEnd) {
        System.out.println("exitRecordComponent");
    }

    private ILocalVariable[] createMethodParameters(JavaElement handle, String[] parameterTypes, char[][] argumentNames, int reasonableOffset) {
        if (argumentNames == null) {
            return null;
        }
        if (argumentNames.length == 0) {
            return LocalVariable.NO_LOCAL_VARIABLES;
        }
        LocalVariable[] result = new LocalVariable[argumentNames.length];
        for(int i = 0; i < argumentNames.length; i++) {
            // we don't know the slocs, so just make something up that is vaguely reasonable
            result[i] = new LocalVariable(handle, String.valueOf(
                    argumentNames[i]), reasonableOffset,
                    reasonableOffset+1, reasonableOffset,
                    reasonableOffset+1, parameterTypes[i],
                    new org.eclipse.jdt.internal.compiler.ast.Annotation[0],
                    Flags.AccDefault, true);
        }

        return result;
    }

//    private LocalVariable[] acceptMethodParameters(Argument[] arguments, JavaElement methodHandle, char[][] argumentTypeNames) {
//        if (arguments == null) return null;
//        LocalVariable[] result = new LocalVariable[arguments.length];
//        Annotation[][] paramAnnotations = new Annotation[arguments.length][];
//        for(int i = 0; i < arguments.length; i++) {
//            Argument argument = arguments[i];
//            ITDParameterInfo localVarInfo = new ITDParameterInfo();
//            localVarInfo.setSourceRangeStart(argument.declarationSourceStart);
//            localVarInfo.setSourceRangeEnd(argument.declarationSourceStart);
//            localVarInfo.setNameSourceStart(argument.sourceStart);
//            localVarInfo.setNameSourceEnd(argument.sourceEnd);
//
//            String paramTypeSig = JavaModelManager.getJavaModelManager().intern(Signature.createTypeSignature(argumentTypeNames[i], false));
//            result[i] = new LocalVariable(
//                    methodHandle,
//                    new String(argument.name),
//                    argument.declarationSourceStart,
//                    argument.declarationSourceEnd,
//                    argument.sourceStart,
//                    argument.sourceEnd,
//                    paramTypeSig,
//                    argument.annotations,
//                    argument.modifiers,
//                    true);
//            this.newElements.put(result[i], localVarInfo);
//            this.infoStack.push(localVarInfo);
//            this.handleStack.push(result[i]);
//            if (argument.annotations != null) {
//                paramAnnotations[i] = new Annotation[argument.annotations.length];
//                for (int  j = 0; j < argument.annotations.length; j++ ) {
//                    org.eclipse.jdt.internal.compiler.ast.Annotation annotation = argument.annotations[j];
//                    acceptAnnotation(annotation, localVarInfo, result[i]);
//                }
//            }
//            this.infoStack.pop();
//            this.handleStack.pop();
//        }
//        return result;
//    }

    // copied from super so that children Map is accessible
    private IJavaElement[] getChildren(Object info) {
        //noinspection unchecked
        ArrayList<IJavaElement> childrenList = (ArrayList<IJavaElement>) this.children.get(info);
        if (childrenList != null) {
            return childrenList.toArray(new IJavaElement[0]);
        }
        return NO_ELEMENTS;
    }
    protected static final JavaElement[] NO_ELEMENTS = new JavaElement[0];
    /* AJDT 1.7 end */

    /**
     * @since 1.6
     */
    public void acceptPackage(
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.ImportReference ir) {
        ImportReference dup = new ImportReference(ir.tokens,ir.sourcePositions,(ir.bits & org.aspectj.org.eclipse.jdt.internal.compiler.ast.ASTNode.OnDemand)!=0,ir.modifiers);
        dup.declarationSourceStart = ir.declarationSourceStart;
        dup.declarationSourceEnd = ir.declarationSourceEnd;
        super.acceptPackage(dup);
    }


    // unused
    private Annotation[] convertToJDTAnnotations(
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.Annotation[] ajAnnotations) {
        if (ajAnnotations == null) {
            return null;
        }
        Annotation[] jdtAnnotations = new Annotation[ajAnnotations.length];
        for (int i = 0; i < ajAnnotations.length; i++) {
            jdtAnnotations[i] = convertToJDTAnnotation(ajAnnotations[i]);
        }
        return jdtAnnotations;
    }

    private Annotation convertToJDTAnnotation(org.aspectj.org.eclipse.jdt.internal.compiler.ast.Annotation ajAnnotation) {
        Annotation jdtAnnotation = null;
        if (ajAnnotation != null) {
            if (ajAnnotation instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.MarkerAnnotation) {
                jdtAnnotation = new MarkerAnnotation(convertToJDTTypeReference(ajAnnotation.type), ajAnnotation.sourceStart);
            } else if (ajAnnotation instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.NormalAnnotation) {
                org.aspectj.org.eclipse.jdt.internal.compiler.ast.NormalAnnotation castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.NormalAnnotation) ajAnnotation;
                NormalAnnotation castedJDT = new NormalAnnotation(convertToJDTTypeReference(castedAJ.type), castedAJ.sourceStart);
                if (castedAJ.memberValuePairs != null) {
                    castedJDT.memberValuePairs = new MemberValuePair[castedAJ.memberValuePairs.length];
                    for (int j = 0; j < castedAJ.memberValuePairs.length; j++) {
                        org.aspectj.org.eclipse.jdt.internal.compiler.ast.MemberValuePair ajMVP = castedAJ.memberValuePairs[j];
                        if (ajMVP != null) {
                            MemberValuePair jdtMVP = new MemberValuePair(ajMVP.name, ajMVP.sourceStart, ajMVP.sourceEnd, convertToJDTExpression(ajMVP.value));
                            jdtMVP.bits = ajMVP.bits;
                            castedJDT.memberValuePairs[j] = jdtMVP;
                        }
                    }
                }
                jdtAnnotation = castedJDT;
            } else { // SingleMemberAnnotation
                org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation) ajAnnotation;
                SingleMemberAnnotation castedJDT = new SingleMemberAnnotation(convertToJDTTypeReference(castedAJ.type), castedAJ.sourceStart);
                castedJDT.memberValue = convertToJDTExpression(castedAJ.memberValue);

                jdtAnnotation = castedJDT;
            }
            jdtAnnotation.sourceEnd = ajAnnotation.sourceEnd;
            jdtAnnotation.declarationSourceEnd = ajAnnotation.declarationSourceEnd;
            jdtAnnotation.implicitConversion = ajAnnotation.implicitConversion;
            jdtAnnotation.bits = ajAnnotation.bits;
            jdtAnnotation.statementEnd = ajAnnotation.statementEnd;
        }
        return jdtAnnotation;
    }

    /**
     * Only handle certain kinds of expressions
     * all others will be returned as null
     *
     * String constants
     * int constants
     * arrays
     * ClassRefs
     * Enum ref
     * Annotation ref
     *
     * @param ajExpr
     * @return jdtExpr
     */
    private Expression convertToJDTExpression(
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.Expression ajExpr) {
        if (ajExpr == null) {
            return null;
        }
        Expression jdtExpr = null;
        if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.StringLiteralConcatenation) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.StringLiteralConcatenation castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.StringLiteralConcatenation) ajExpr;
            StringLiteralConcatenation castedJDT = new StringLiteralConcatenation((StringLiteral) convertToJDTExpression(castedAJ.literals[0]), (StringLiteral) convertToJDTExpression(castedAJ.literals[1]));
            for (int i = 2; i < castedAJ.literals.length; i++) {
                // may not be able to handle non-string constants here
                castedJDT.extendsWith((StringLiteral) convertToJDTExpression(castedAJ.literals[i]));
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.CharLiteral) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.CharLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.CharLiteral) ajExpr;
          jdtExpr = new CharLiteral(castedAJ.source(), castedAJ.sourceStart, castedAJ.sourceEnd);
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.DoubleLiteral) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.DoubleLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.DoubleLiteral) ajExpr;
          jdtExpr = new DoubleLiteral(castedAJ.source(), castedAJ.sourceStart, castedAJ.sourceEnd);
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.FloatLiteral) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.FloatLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.FloatLiteral) ajExpr;
          jdtExpr = new FloatLiteral(castedAJ.source(), castedAJ.sourceStart, castedAJ.sourceEnd);
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteralMinValue) {
            // ECLIPSE 3.7.1 --- must use reflection since constructors have changed
            // ORIG
//            IntLiteralMinValue castedJDT = new IntLiteralMinValue();
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteralMinValue castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteralMinValue) ajExpr;
            IntLiteral castedJDT = null;
            try {
                castedJDT = CompilerASTNodeCompatibilityWrapper.createJDTIntLiteralMinValue(castedAJ);
            } catch (Exception ignored) {
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteral) {
            // ECLIPSE 3.7.1 --- must use reflection since constructors have changed
            // ORIG
//            IntLiteralMinValue castedJDT = new IntLiteralMinValue();
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.IntLiteral) ajExpr;
            IntLiteral castedJDT = null;
            try {
                castedJDT = CompilerASTNodeCompatibilityWrapper.createJDTIntLiteral(castedAJ);
            } catch (Exception ignored) {
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.FalseLiteral) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.FalseLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.FalseLiteral) ajExpr;
            FalseLiteral castedJDT = null;
            try {
                castedJDT = CompilerASTNodeCompatibilityWrapper.createJDTFalseLiteral(castedAJ);
            } catch (Exception ignored) {
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.NullLiteral) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.NullLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.NullLiteral) ajExpr;
            NullLiteral castedJDT = null;
            try {
                castedJDT = CompilerASTNodeCompatibilityWrapper.createJDTNullLiteral(castedAJ);
            } catch (Exception ignored) {
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.TrueLiteral) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.TrueLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.TrueLiteral) ajExpr;
            TrueLiteral castedJDT = null;
            try {
                castedJDT = CompilerASTNodeCompatibilityWrapper.createJDTTrueLiteral(castedAJ);
            } catch (Exception ignored) {
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.LongLiteralMinValue) {
            // ECLIPSE 3.7.1 --- must use reflection since constructors have changed
            // ORIG
//            IntLiteralMinValue castedJDT = new IntLiteralMinValue();
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.LongLiteralMinValue castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.LongLiteralMinValue) ajExpr;
            LongLiteral castedJDT = null;
            try {
                castedJDT = CompilerASTNodeCompatibilityWrapper.createJDTLongLiteralMinValue(castedAJ);
            } catch (Exception ignored) {
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.LongLiteral) {
            // ECLIPSE 3.7.1 --- must use reflection since constructors have changed
            // ORIG
//            IntLiteralMinValue castedJDT = new IntLiteralMinValue();
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.LongLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.LongLiteral) ajExpr;
            LongLiteral castedJDT = null;
            try {
                castedJDT = CompilerASTNodeCompatibilityWrapper.createJDTLongLiteral(castedAJ);
            } catch (Exception ignored) {
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.StringLiteral) {
            // note that here we capture both StringLiteral and ExtendedStringLiteral
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.StringLiteral castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.StringLiteral) ajExpr;
            // can we get away with no line number?
          jdtExpr = new StringLiteral(castedAJ.source(), castedAJ.sourceStart, castedAJ.sourceEnd, 0);
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayInitializer) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayInitializer castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayInitializer) ajExpr;
            ArrayInitializer castedJDT = new ArrayInitializer();
            if (castedAJ.expressions != null) {
                castedJDT.expressions = new Expression[castedAJ.expressions.length];
                for (int i = 0; i < castedJDT.expressions.length; i++) {
                    castedJDT.expressions[i] = convertToJDTExpression(castedAJ.expressions[i]);
                }
            }
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayAllocationExpression) ajExpr;
            ArrayAllocationExpression castedJDT = new ArrayAllocationExpression();
            castedJDT.type = convertToJDTTypeReference(castedAJ.type);
            if (castedAJ.dimensions != null) {
                castedJDT.dimensions = new Expression[castedAJ.dimensions.length];
                for (int i = 0; i < castedJDT.dimensions.length; i++) {
                    castedJDT.dimensions[i] = convertToJDTExpression(castedAJ.dimensions[i]);
                }
            }
            castedJDT.initializer = (ArrayInitializer) convertToJDTExpression(castedAJ.initializer);
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldReference) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.FieldReference) ajExpr;
            FieldReference castedJDT = new FieldReference(castedAJ.token, toPos(castedAJ.sourceStart, castedAJ.sourceEnd));
            castedJDT.nameSourcePosition = castedAJ.nameSourcePosition;
            castedJDT.receiver = convertToJDTExpression(castedAJ.receiver);
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayReference) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayReference) ajExpr;
          jdtExpr = new ArrayReference(convertToJDTExpression(castedAJ.receiver), convertToJDTExpression(castedAJ.position));
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference) ajExpr;
            QualifiedNameReference castedJDT = new QualifiedNameReference(castedAJ.tokens, castedAJ.sourcePositions, castedAJ.sourceStart, castedAJ.sourceEnd);
            castedJDT.indexOfFirstFieldBinding = castedAJ.indexOfFirstFieldBinding;
            jdtExpr = castedJDT;
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleNameReference) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleNameReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleNameReference) ajExpr;
          jdtExpr = new SingleNameReference(castedAJ.token, toPos(castedAJ.sourceStart, castedAJ.sourceEnd));
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeReference) {
            jdtExpr = convertToJDTTypeReference((org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeReference) ajExpr);
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ClassLiteralAccess) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.ClassLiteralAccess castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ClassLiteralAccess) ajExpr;
          jdtExpr = new ClassLiteralAccess(castedAJ.sourceEnd, convertToJDTTypeReference(castedAJ.type));
        } else if (ajExpr instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.Annotation) {
            jdtExpr = convertToJDTAnnotation((org.aspectj.org.eclipse.jdt.internal.compiler.ast.Annotation) ajExpr);
        }


        if (jdtExpr != null) {
            // now fill in other fields
            jdtExpr.bits = ajExpr.bits;
            jdtExpr.implicitConversion = ajExpr.implicitConversion;
            jdtExpr.sourceStart = ajExpr.sourceStart;
            jdtExpr.sourceEnd = ajExpr.sourceEnd;
            jdtExpr.statementEnd = ajExpr.statementEnd;
        }
        return jdtExpr;
    }

    /**
     * Recursively converts from an aj type reference to a JDT type reference.
     * This class is not involved with Content assist and code select, so the CompletionOn* and SelectionOn* variants of
     * type references should not make it here.
     * @param ajRef
     * @return jdtRef
     */
    private TypeReference convertToJDTTypeReference(org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeReference ajRef) {
        if (ajRef == null) {
            return null;
        }
        TypeReference jdtRef = null;
        if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocImplicitTypeReference) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocImplicitTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocImplicitTypeReference) ajRef;
            jdtRef = new JavadocImplicitTypeReference(castedAJ.token, castedAJ.sourceStart);
        } else if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference) {
            if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference) {
                if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocArrayQualifiedTypeReference) {
                    org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocArrayQualifiedTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocArrayQualifiedTypeReference) ajRef;
                    jdtRef = new JavadocArrayQualifiedTypeReference(new JavadocQualifiedTypeReference(castedAJ.tokens, castedAJ.sourcePositions, castedAJ.tagSourceStart, castedAJ.tagSourceEnd), castedAJ.dimensions());
                } else if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference) {
                    org.aspectj.org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference) ajRef;
                    jdtRef = new ParameterizedQualifiedTypeReference(castedAJ.tokens, convertDoubleArray(castedAJ.typeArguments), castedAJ.dimensions(), castedAJ.sourcePositions);
                } else {  // assume vanilla ArrayQualifiedTypeReference
                    org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayQualifiedTypeReference) ajRef;
                    jdtRef = new ArrayQualifiedTypeReference(castedAJ.tokens, castedAJ.dimensions(), castedAJ.sourcePositions);
                }
            } else if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocQualifiedTypeReference) {
                org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocQualifiedTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocQualifiedTypeReference) ajRef;
                jdtRef = new JavadocQualifiedTypeReference(castedAJ.tokens, castedAJ.sourcePositions, castedAJ.tagSourceStart, castedAJ.tagSourceEnd);
            } else { // assume vanilla QualifiedTypeReference
                org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference) ajRef;
                jdtRef = new QualifiedTypeReference(castedAJ.tokens, castedAJ.sourcePositions);
            }
        } else if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleTypeReference) {
            if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference) {
                if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocArraySingleTypeReference) {
                    org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocArraySingleTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocArraySingleTypeReference) ajRef;
                    jdtRef = new JavadocArraySingleTypeReference(castedAJ.token, castedAJ.dimensions, toPos(castedAJ.sourceStart, castedAJ.sourceEnd));
                } else if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference) {
                    org.aspectj.org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference) ajRef;
                    jdtRef = new ParameterizedSingleTypeReference(castedAJ.token, convertSingleArray(castedAJ.typeArguments), castedAJ.dimensions, toPos(castedAJ.sourceStart, castedAJ.sourceEnd));
                } else {  // assume vanilla ArrayTypeReference
                    org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference) ajRef;
                    jdtRef = new ArrayTypeReference(castedAJ.token, castedAJ.dimensions, toPos(castedAJ.sourceStart, castedAJ.sourceEnd));
                }
            } else if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocSingleTypeReference) {
                org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocSingleTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.JavadocSingleTypeReference) ajRef;
                jdtRef = new JavadocSingleTypeReference(castedAJ.token, toPos(castedAJ.sourceStart, castedAJ.sourceEnd), castedAJ.tagSourceStart, castedAJ.tagSourceEnd);
            } else {  // assume vanilla SingleTypeReference
                org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleTypeReference castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.SingleTypeReference) ajRef;
                jdtRef = new SingleTypeReference(castedAJ.token, toPos(castedAJ.sourceStart, castedAJ.sourceEnd));
            }
        } else if (ajRef instanceof org.aspectj.org.eclipse.jdt.internal.compiler.ast.Wildcard) {
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.Wildcard castedAJ = (org.aspectj.org.eclipse.jdt.internal.compiler.ast.Wildcard) ajRef;
            Wildcard castedJDT = new Wildcard(castedAJ.kind);
            castedJDT.bound = convertToJDTTypeReference(castedAJ.bound);
            jdtRef = castedJDT;
        }

        Assert.isNotNull(jdtRef, "Conversion to JDT type reference failed.  Original AJ type reference is: '" + ajRef + "' and class '" + ajRef.getClass() + "'");

        // now fill in the rest of the shared fields.  Not all of them will be applicable in all cases
        jdtRef.bits = ajRef.bits;
        jdtRef.implicitConversion = ajRef.implicitConversion;
        jdtRef.statementEnd = ajRef.statementEnd;

        return jdtRef;
    }

    private TypeReference[][] convertDoubleArray(
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeReference[][] ajTypeArguments) {
        if (ajTypeArguments == null) {
            return null;
        }
        TypeReference[][] jdtTypeArguments = new TypeReference[ajTypeArguments.length][];
        for (int i = 0; i < jdtTypeArguments.length; i++) {
            jdtTypeArguments[i] = convertSingleArray(ajTypeArguments[i]);
        }
        return jdtTypeArguments;
    }

    private TypeReference[] convertSingleArray(
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.TypeReference[] ajTypeArguments) {
        if (ajTypeArguments != null) {
            TypeReference[] jdtTypeArguments = new TypeReference[ajTypeArguments.length];
            for (int j = 0; j < jdtTypeArguments.length; j++) {
                jdtTypeArguments[j] = convertToJDTTypeReference(ajTypeArguments[j]);
            }
            return jdtTypeArguments;
        } else {
            return null;
        }
    }

    private static final long NON_EXISTENT_POSITION = toPos(-1, -2);

    private static long toPos(long start, long end) {
        if (start == 0 && end <= 0) {
            return NON_EXISTENT_POSITION;
        }
        return ((start << 32) | end);
    }
    private static long[] toPoss(long[] starts, long[] ends) {
        if (starts == null || ends == null) {
            return null;
        }
        // can safely assume start and end are same length
        long[] newPoss = new long[starts.length];
        for (int i = 0; i < newPoss.length; i++) {
            newPoss[i] = toPos(starts[i], ends[i]);
        }
        return newPoss;
    }



    private org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] convertToJDTTypeParameters(
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] ajTypeParams) {
        org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] jdtTypeParams =
            new org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[ajTypeParams == null ? 0 : ajTypeParams.length];

        for (int i = 0; i < jdtTypeParams.length; i++) {
            jdtTypeParams[i] = new org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo();
            jdtTypeParams[i].declarationStart = ajTypeParams[i].declarationStart;
            jdtTypeParams[i].declarationEnd = ajTypeParams[i].declarationEnd;
            jdtTypeParams[i].name = ajTypeParams[i].name;
            jdtTypeParams[i].nameSourceStart = ajTypeParams[i].nameSourceStart;
            jdtTypeParams[i].nameSourceEnd = ajTypeParams[i].nameSourceEnd;
            jdtTypeParams[i].bounds = ajTypeParams[i].bounds;

        }
        return jdtTypeParams;
    }

    private org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] convertToAJTypeParameters(
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] jdtTypeParams) {
        org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] ajTypeParams =
            new org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[jdtTypeParams == null ? 0 : jdtTypeParams.length];

        for (int i = 0; i < ajTypeParams.length; i++) {
            ajTypeParams[i] = new org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo();
            ajTypeParams[i].declarationStart = jdtTypeParams[i].declarationStart;
            ajTypeParams[i].declarationEnd = jdtTypeParams[i].declarationEnd;
            ajTypeParams[i].name = jdtTypeParams[i].name;
            ajTypeParams[i].nameSourceStart = jdtTypeParams[i].nameSourceStart;
            ajTypeParams[i].nameSourceEnd = jdtTypeParams[i].nameSourceEnd;
            ajTypeParams[i].bounds = jdtTypeParams[i].bounds;

        }
        return ajTypeParams;
    }
}
