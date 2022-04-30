/*******************************************************************************
 * Copyright (c) 2004, 2010 IBM Corporation SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Luzius Meisser   - initial implementation
 *     Andrew Eisenberg - adapted for use with JDT Weaving
 *******************************************************************************/
package org.eclipse.ajdt.core.codeconversion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IProgramElement.Accessibility;
import org.aspectj.org.eclipse.jdt.core.compiler.CharOperation;
import org.aspectj.org.eclipse.jdt.core.compiler.InvalidInputException;
import org.aspectj.org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.aspectj.org.eclipse.jdt.internal.compiler.parser.TerminalTokens;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.internal.core.ras.NoFFDC;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;

/**
 * The purpose of this parser is to convert AspectJ code into similar Java code
 * which allows us to reuse for example for jdt formatting or comment
 * generation.
 *
 * Depending on the ConversionOptions it gets called with, it does: - replace
 * the keyword "aspect" by "class " - replace all the '.'s in intertype
 * declarations by an '$'s to make them look like an ordinary declarations. -
 * erase the keywords "returning", "throwing", "privileged", "issingleton" e.g.:
 * "after() throwing (Exception e)" -> "after( Exception e)" - erase pointcut
 * designators (includes "percflow" & co.) - add dummy references to all erased
 * class references to end of buffer to make "organize imports" work correctly -
 * add a reference to the target class inside intertype method declarations to
 * simulate the context switch necessary to get proper code completion.
 * (A detailed description of how code completion works in AJDT can be found in
 * bug 74419.)
 *
 * Restrictions: - class names inside pointcut designators must begin with a
 * capital letter to be recognised as such
 *
 *
 * @author Luzius Meisser
 */
public class AspectsConvertingParser implements TerminalTokens, NoFFDC {

    private static final String IMPLEMENTS = "implements";

    private static final String EXTENDS = "extends";

    private static final char[] throwing = "throwing".toCharArray(); //$NON-NLS-1$

    private static final char[] returning = "returning".toCharArray(); //$NON-NLS-1$

    private static final char[] percflow = "percflow".toCharArray(); //$NON-NLS-1$

    private static final char[] percflowbelow = "percflowbelow".toCharArray(); //$NON-NLS-1$

    private static final char[] perthis = "perthis".toCharArray(); //$NON-NLS-1$

    private static final char[] pertarget = "pertarget".toCharArray(); //$NON-NLS-1$

    private static final char[] issingleton = "issingleton".toCharArray(); //$NON-NLS-1$

    private static final char[] pertypewithin = "pertypewithin".toCharArray(); //$NON-NLS-1$

    private static final char[] classs = "class ".toCharArray(); //$NON-NLS-1$

    private static final char[] privileged = "          ".toCharArray(); //$NON-NLS-1$

    public static final String ITD_INSERTED_IDENTIFIER = "___ITD_INSERTED_IDENTIFIER___"; //$NON-NLS-1$

    // used to replace declare declarations
    private static final char[] intt = "int    ".toCharArray(); //$NON-NLS-1$

    private final static char[] tjpRefs2 = "\n\torg.aspectj.lang.JoinPoint thisJoinPoint;\n\torg.aspectj.lang.JoinPoint.StaticPart thisJoinPointStaticPart;\n\torg.aspectj.lang.JoinPoint.StaticPart thisEnclosingJoinPointStaticPart;\n" //$NON-NLS-1$
    .toCharArray();

    private final static char[] endThrow = new char[] { '(', ':' };


    public static class Replacement {
        //the position in the original char[]
        public int posBefore;

        //the position in the new char[], or -1 if not yet applied
        public int posAfter;

        //the number of chars that get replaced
        public int length;

        //the content to be inserted
        public char[] text;

        //the number of additional chars (lengthAdded == text.length - length)
        public int lengthAdded;

        public Replacement(int pos, int length, char[] text) {
            this.posBefore = pos;
            this.posAfter = -1;
            this.length = length;
            this.text = text;
            lengthAdded = text.length - length;
        }

    }

    public AspectsConvertingParser(char[] content) {
        this.content = content;
        this.typeReferences = new HashSet<>();
        this.usedIdentifiers = new HashSet<>();
        replacements = new ArrayList<>(5);
    }

    public char[] content;

    private final Set<String> typeReferences;

    private final Set<String> usedIdentifiers;

    private ConversionOptions options;

    /**
     * can be null.  used for determining ITDs
     */
    private ICompilationUnit unit;

    //list of replacements
    //by convetion: sorted by posBefore in ascending order
    private final ArrayList<Replacement> replacements;

    protected Scanner scanner;

    /**
     * keeps track of being after the ':' of a pointcut declaration
     */
    private boolean inPointcutDesignator;

    /**
     * keeps track of being in an aspect body
     */
    private boolean inAspect;

    /**
     * keeps track of being in the declaration of an aspect
     * (ie- before the first '{')
     */
    private boolean inAspectDeclaration;

    private boolean inClassDeclaration;

    private boolean inInterfaceDeclaration;

    private boolean inEnumDeclaration;

    private Set<String> knownTypeParameters;

    /**
     * keeps track of being in the right hand side of a declaration
     *
     * Note- thos will *not* catch complex RHS assignments where the RHS contains an inner type
     * ie- inRHS will evaluate to false after the first ';' of a complex assignment
     * What I really need is an RHS stack, but that is complicated and I will only add if necessary
     */
    private boolean inRHS;

    private int posColon;


    public void setUnit(ICompilationUnit unit) {
        this.unit = unit;
    }


    //returns a list of Insertions to let the client now what has been inserted
    //into the buffer so he can translate positions from the old into the new
    //buffer
    public ArrayList<Replacement> convert(ConversionOptions options) {
        this.options = options;
        boolean insertIntertypeDeclarations = options
                .isThisJoinPointReferencesEnabled();
        boolean addReferencesForOrganizeImports = options
                .isDummyTypeReferencesForOrganizeImportsEnabled();
        boolean isSimulateContextSwitchNecessary = (options.getTargetType() != null);


        Stack<Boolean> inAspectBodyStack = new Stack<>();
        Stack<Boolean> inTypeBodyStack = new Stack<>();

        scanner = new Scanner();
        scanner.setSource(content);

        inPointcutDesignator = false;
        inAspect = false;
        inAspectDeclaration = false;
        inClassDeclaration = false;
        inInterfaceDeclaration = false;
        inEnumDeclaration = false;
        inRHS = false;

        // Bug 93248: Count question marks so as to ignore colons that are part of conditional statements
        int questionMarkCount = 0;

        // count paren levels so that we know if we are in a intertype declaration name or not
        int parenLevel = 0;

        // Bug 110751: Ignore colons that are part of enhanced "for" loop in Java 5
        boolean inFor = false;

        // Bug 258685: case statements should not be confused with pointcuts
        boolean inCase = false;

        // bug 265977 the aspect keyword is OK if it is in a package declaration
        boolean inPackageDecl = false;

        // bug 273691 class does not signify a start of a class declation if
        // it is after a dot
        boolean afterDot = false;

        // Bug 282948: keep track of type params because question marks inside of
        // type params do not signify the start of a ternary conditional statement
        // (ie- question mark count)
        int typeParamDepth = 0;

        // Bug 384422 keep track of being after commas and parens so that we can distinguish between the 'throwing' and 'returning' keywards
        // and annotation members
        boolean afterOpenParen = false;
        boolean afterComma = false;

        char[] currentTypeName = null;

        replacements.clear();
        typeReferences.clear();
        usedIdentifiers.clear();

        int tok = 1;
        int prevTok;
        int pos;
        int typeDeclStart = 0;
        char[] text = null;
        char[] prevText;

        while (true) {
            prevTok = tok;
            prevText = text;
            try {
                tok = scanner.getNextToken();
            } catch (InvalidInputException e) {
                continue;
            }
            if (tok == TokenNameEOF)
                break;

            text = scanner.getCurrentIdentifierSource();

            switch (tok) {
            case TokenNameIdentifier:
                if (!inAspect && !inTypeDeclaration())
                    break;

                // FIXADE Hmmm...here we should be including enclosing types
              if (inTypeDeclaration() && !inPointcutDesignator) {

                    // only do this if we are not adding ITDs
                    if (inAspectDeclaration && !insertIntertypeDeclarations) {
                        if (CharOperation.equals(percflow, text)) {
                            startPointcutDesignator();
                        } else if (CharOperation.equals(percflowbelow, text)) {
                            startPointcutDesignator();
                        } else if (CharOperation.equals(perthis, text)) {
                            startPointcutDesignator();
                        } else if (CharOperation.equals(pertarget, text)) {
                            startPointcutDesignator();
                        } else if (CharOperation.equals(issingleton, text)) {
                            startPointcutDesignator();
                        } else if (CharOperation.equals(pertypewithin, text)) {
                            startPointcutDesignator();
                        }
                    }
                    // store the type name if not already found
                    if (currentTypeName == null) {
                        currentTypeName = text;
                    }
                }

                if (!afterComma && !afterOpenParen && (CharOperation.equals(throwing, text) || CharOperation.equals(returning, text))) {
                    consumeRetOrThrow();
                } else if (inPointcutDesignator
                           && Character.isUpperCase(text[0])  // Assume all types start with upercase
                           && (content[scanner.getCurrentTokenStartPosition()-1]!='.')  // can ignore if looks fully qualified
                           && (content[scanner.getCurrentTokenStartPosition()-1]!='*')) // can ignore if looks like a wild core
                {
                    typeReferences.add(String.valueOf(text));
                }

                if (isSimulateContextSwitchNecessary) {
                    usedIdentifiers.add(new String(text));
                }
                break;
            case TokenNamefor:
                inFor = true;
                break;

            case TokenNameLPAREN:
                parenLevel++;
                inCase = false;
                afterOpenParen = true;
                break;

            case TokenNameCOMMA:
                afterComma = true;
                break;

            case TokenNameRPAREN:
                inFor = false;
                inCase = false;
                parenLevel--;
                // Bug 301268: just in case there is a variable with a capital first letter before a '<'
                // reset the typeParamDepth
                typeParamDepth = 0;
                break;

            case TokenNameCOLON:
                if (inFor) {
                    break;
                } else if (questionMarkCount > 0) {
                    questionMarkCount--;
                    break;
                } else if (inCase) {
                    inCase = false;
                    break;
                }
                startPointcutDesignator();
                break;

            case TokenNameEQUAL:
                if (parenLevel == 0) {
                    // can have problems if inside an annotation, there is an '='.
                    // do not set flag in this case. OK to ignore because we do not
                    // process ITDs when we are inside parens anyway.
                    inRHS = true;
                }
                break;

            case TokenNameQUESTION:
                // bug 282948: only update question mark count if not in type parameter
                if (typeParamDepth == 0) {
                    questionMarkCount++;
                }
                break;

            case TokenNameSEMICOLON:
                if (inPointcutDesignator) {
                    endPointcutDesignator();
                    if (options.isKeepPointcuts()) {
                        addReplacement(scanner.getCurrentTokenStartPosition(),
                                1, new char[] { '}' });
                    }
                }
                inRHS = false;  // may be triggering this too early in case this is part of a complex assignment
                inPackageDecl = false;
                break;

            case TokenNamedefault:
            case TokenNamecase:
                inCase = true;
                break;

            case TokenNameDOT:
                afterDot = true;
                if (!inAspect) {
                    break;
                } else if (inPointcutDesignator) {
                    break;
                } else if (parenLevel > 0) {
                    // don't want to convert '.' to '$' inside the parameter declaration
                    break;
                }

                if (!inRHS &&
                        !inAspectBodyStack.empty() &&
                        inAspectBodyStack.peek() == Boolean.TRUE) {
                    processPotentialIntertypeDeclaration();
                }
                break;

            case TokenNameLBRACE:
                if (inPointcutDesignator && parenLevel == 0) {  // Bug 296044: make sure that lbrace is not inside of a declare declaration
                    endPointcutDesignator();
                    if (options.isKeepPointcuts()) {
                        addReplacement(scanner.getCurrentTokenStartPosition(),
                                1, new char[] { ' ' });
                    }

                    //must be start of advice body -> insert tjp reference
                    if (insertIntertypeDeclarations && !inTypeDeclaration()) {
                        addReplacement(scanner.getCurrentTokenStartPosition() + 1, 0,
                                tjpRefs2);
                    }
                }

                // determine if we should add intertype declarations
                if (insertIntertypeDeclarations && inTypeDeclaration()) {

                    char[] implementsExtends = createImplementExtendsITDs(currentTypeName);
                    if (implementsExtends != null) {
                        addReplacement(typeDeclStart, scanner.getCurrentTokenStartPosition()-typeDeclStart, implementsExtends);
                    } else {
                        // not able to add ITDs since no compilation unit available
                        // may have to replace "aspect" with "class " since we didn't do that before

                        if (hasWordAtPosition("aspect", typeDeclStart)) {
                            addReplacement(typeDeclStart, classs.length, classs);
                        }
                    }
                    char[] interTypeDecls = getInterTypeDecls(currentTypeName);
                    if (interTypeDecls.length > 0) {
                        addReplacement(scanner.getCurrentTokenStartPosition() + 1, 0, interTypeDecls);
                    }
                }

                if (addReferencesForOrganizeImports) {
                    // must determine what the type parameters are so that we don't add those references
                    storeTypeParameters(currentTypeName);
                }

                if (inTypeDeclaration()) {
                    inTypeBodyStack.push(Boolean.TRUE);
                } else {
                    inTypeBodyStack.push(Boolean.FALSE);
                }

                if (inAspectDeclaration) {
                    inAspectDeclaration = false;
                    inAspectBodyStack.push(Boolean.TRUE);
                } else {
                    inAspectBodyStack.push(Boolean.FALSE);
                }


                inCase = false;
                inClassDeclaration = false;
                inInterfaceDeclaration = false;
                inEnumDeclaration = false;
                currentTypeName = null;

                break;

            case TokenNameafter:
                // Bug 318132: ensure that the debugger does not raise exceptions when stepping through aspects with advice
                if (! inTypeBodyStack.isEmpty() && inTypeBodyStack.peek()) {
                    addReplacement(scanner.getCurrentTokenStartPosition(), "after".length(), "int a".toCharArray());
                }
                break;
            case TokenNamebefore:
                // Bug 318132: ensure that the debugger does not raise exceptions when stepping through aspects with advice
                if (! inTypeBodyStack.isEmpty() && inTypeBodyStack.peek()) {
                    addReplacement(scanner.getCurrentTokenStartPosition(), "before".length(), "void b".toCharArray());
                }
                break;
            case TokenNameRBRACE:
                if (inPointcutDesignator && parenLevel == 0) {  // Bug 296044: make sure that lbrace is not inside of a declare declaration
                    // bug 129367: if we've hit a } here, we must be
                    // in the middle of an unterminated pointcut
                    endPointcutDesignator();
                }
                if (!inAspectBodyStack.empty()) {
                    inAspectBodyStack.pop();
                }
                if (!inTypeBodyStack.empty()) {
                    inTypeBodyStack.pop();
                }
                inCase = false;
                break;
            case TokenNameaspect:
                if (inPackageDecl) {
                    // aspect keyword is OK in package decls
                    break;
                }

                inAspect = true;
                inAspectDeclaration = true;
                typeDeclStart = pos = scanner.getCurrentTokenStartPosition();

                // only do this if not inserting ITDs
                // if inserting ITDs, this replacement occurs in createImplementExtendsITDs()
                if (!insertIntertypeDeclarations) {
                    addReplacement(pos, classs.length, classs);
                }

                break;

            case TokenNamedeclare:
                // check to see if we need to remove declare declarations
                // so that import references aren't confused
                if (addReferencesForOrganizeImports) {
                    if (!inAspectBodyStack.isEmpty() && inAspectBodyStack.peek().equals(Boolean.TRUE)) {
                        addReplacement(scanner.getCurrentTokenStartPosition(), intt.length, intt);
                    }
                }

                break;

            case TokenNameclass:
                if (!afterDot) {  // bug 273691
                    typeDeclStart = scanner.getCurrentTokenStartPosition();
                    inClassDeclaration = true;
                }
                break;

            case TokenNameinterface:  // interface and @interface
                typeDeclStart = scanner.getCurrentTokenStartPosition();
                inInterfaceDeclaration = true;
                break;

            case TokenNameenum:  // interface and @interface
                typeDeclStart = scanner.getCurrentTokenStartPosition();
                inEnumDeclaration = true;
                break;

            case TokenNameprivileged:
                pos = scanner.getCurrentTokenStartPosition();
                addReplacement(pos, privileged.length, privileged);
                break;

            case TokenNamepackage:
                inPackageDecl = true;
                break;

            case TokenNameLESS:
                // bug 282948 check to see if inside type parameter
                // bug 301268 type parameters can also occur in local variable declarations
                // if the previous token looks like a type name (ie- identifier that starts with caps)
                // also bump up typeParamDepth.  Note that this will still fail
                // when there is a var name that starts with caps on the LHS of a '<' comparison
                if ((!inTypeBodyStack.isEmpty() && inTypeBodyStack.peek() == Boolean.TRUE) ||
                        tokenLooksLikeTypeName(prevTok, prevText)) {
                    typeParamDepth++;
                }
                break;
            case TokenNameGREATER:
                if (typeParamDepth > 0) {
                    typeParamDepth--;
                }
                break;
            case TokenNameRIGHT_SHIFT:
                if (typeParamDepth > 0) {
                    typeParamDepth-=2;
                }
                break;
            case TokenNameUNSIGNED_RIGHT_SHIFT:
                if (typeParamDepth > 0) {
                    typeParamDepth-=3;
                }
                break;

            case TokenNameAT:
                // peek at the next token and see if it is
                // part of a declare annotation.  If so,
                // remove the '@'
                if (isDeclareAnnotationStart(pos = scanner.getCurrentTokenStartPosition())) {
                    addReplacement(pos, 1, "$".toCharArray());
                }
                break;
            }

            if (tok != TokenNameDOT) {
                afterDot = false;
            }
            if (tok != TokenNameLPAREN) {
                afterOpenParen = false;
            }
            if (tok != TokenNameCOMMA) {
                afterComma = false;
            }
        }

        if (inPointcutDesignator) {
            // bug 129367: if we've hit the end of the buffer, we must
            // be in the middle of an unterminated pointcut
            endPointcutDesignator();
        }

        if (addReferencesForOrganizeImports) {
            addReferences();
        }

        if (isSimulateContextSwitchNecessary) {
            simulateContextSwitch(options.getCodeCompletePosition(), options
                    .getTargetType());
        }

        applyReplacements();

        // uncomment to see the transformed source code
//        System.out.println("----------\nConversion:");
//        System.out.println(String.valueOf(content));
//        System.out.println("----------");

        return replacements;
    }

    /**
     * @param i
     * @return true if this is the start of @type, @field, @method, or @constructor
     */
    private boolean isDeclareAnnotationStart(int i) {
        if (content[i] != '@') {
            return false;
        }
        i++;
        switch (content[i]) {
            case 't':
                return content.length >= i+"type".length() && content[++i] == 'y' && content[++i] == 'p' && content[++i] == 'e';
            case 'f':
                return content.length >= i+"field".length() && content[++i] == 'i' && content[++i] == 'e' && content[++i] == 'l' && content[++i] == 'd';
            case 'm':
                return content.length >= i+"method".length() && content[++i] == 'e' && content[++i] == 't' && content[++i] == 'h' && content[++i] == 'o' && content[++i] == 'd';
            case 'c':
                return content.length >= i+"constructor".length() && content[++i] == 'o' && content[++i] == 'n' && content[++i] == 's' && content[++i] == 't' && content[++i] == 'r' && content[++i] == 'u' && content[++i] == 'c' && content[++i] == 't' && content[++i] == 'o' && content[++i] == 'r';
        }

        return false;
    }


    /**
     * @param currentTypeName
     */
    private void storeTypeParameters(char[] typeName) {
        if (unit != null && typeName != null && typeName.length > 0) {
            IType type = getHandle(String.valueOf(typeName));
            if (type.exists() && type instanceof AspectElement) {
                try {
                    ITypeParameter[] typeParameters = type.getTypeParameters();
                    if (typeParameters != null && typeParameters.length > 0) {
                        if (knownTypeParameters == null) {
                            knownTypeParameters = new HashSet<>();
                        }
                        for (ITypeParameter parameter : typeParameters) {
                            knownTypeParameters.add(parameter.getElementName());
                        }
                    }
                } catch (JavaModelException ignored) {
                }
            }
        }
    }


    private boolean tokenLooksLikeTypeName(int token, char[] text) {
        return token == TokenNameIdentifier && text != null && text.length > 0 && Character.isUpperCase(text[0]);
    }


    /**
     * @param typeName name of the type
     * @return new type declaration string to replace the original
     * that contains all of the types that are declared parents of this one
     * returns null if could not find the super types and super interfaces
     */
    public char[] createImplementExtendsITDs(char[] typeName) {
        if (unit != null && typeName != null && typeName.length > 0) {
            IType type = getHandle(String.valueOf(typeName));
            if (type.exists()) {
                List<List<String>> declares = getDeclareExtendsImplements(type);
                if (declares.get(0).isEmpty() && declares.get(1).isEmpty()) {
                    // nothing to do
                    return null;
                }

                try {
                    StringBuilder sb = new StringBuilder();
                    sb.append(type.isInterface() ? "interface " : "class ");
                    sb.append(typeName);

                    ITypeParameter[] tParams = type.getTypeParameters();
                    if (tParams != null && tParams.length > 0) {
                        sb.append(" <");
                        for (int i = 0; i < tParams.length; i++) {
                            if (i > 0) {
                                sb.append(", ");
                            }
                            sb.append(tParams[i].getSource());
                        }
                        sb.append("> ");
                    }

                    List<String> declareExtends = declares.get(0);
                    List<String> declareImplements = declares.get(1);
                    if (type.isClass()) {
                        String superClass = type.getSuperclassName();
                        if (declareExtends.size() > 0) {
                            superClass = declareExtends.get(0);
                            superClass = superClass.replace('$', '.');
                        }
                        if (superClass != null) {
                            sb.append(" " + EXTENDS + " ").append(superClass);
                        }
                    }

                    String[] superInterfaces = type.getSuperInterfaceNames();
                    List<String> interfaceParents = type.isClass() ? declareImplements : declareExtends;
                  interfaceParents.addAll(Arrays.asList(superInterfaces));

                    if (interfaceParents.size() > 0) {

                        if (type.isClass()) {
                            sb.append(" " + IMPLEMENTS);
                        } else {
                            sb.append(" " + EXTENDS);
                        }

                        for (Iterator<String> interfaceIter = interfaceParents.iterator(); interfaceIter
                                .hasNext();) {
                            String interName = interfaceIter.next();
                            interName = interName.replace('$', '.');
                            sb.append(" ").append(interName);
                            if (interfaceIter.hasNext()) {
                                sb.append(",");
                            }
                        }
                    }
                    sb.append(" ");
                    return sb.toString().toCharArray();
                } catch (JavaModelException e) {
                    AspectJPlugin.getDefault().getLog().log(new Status(Status.ERROR, AspectJPlugin.PLUGIN_ID, e.getMessage(), e));
                    return null;
                }
            }
        }
        return null;
    }


    // copied from ITDInserter...make a utility method?
    private IType getHandle(String typeName) {
        try {
            IType type = getHandleFromChild(typeName, unit);
            if (type != null) {
                return type;
            }
        } catch (JavaModelException ignored) {
        }
        // this type may not exist
        return unit.getType(typeName);
    }

    private IType getHandleFromChild(String typeName, IParent parent)
            throws JavaModelException {
        IJavaElement[] children = parent.getChildren();
      for (IJavaElement iJavaElement : children) {
        if (iJavaElement.getElementType() == IJavaElement.TYPE &&
            typeName.equals(iJavaElement.getElementName()))
        {
          return (IType) iJavaElement;
        }
      }
      for (IJavaElement child : children) {
        if (child.getElementType() == IJavaElement.TYPE) {
          IType type = getHandleFromChild(typeName, (IParent) child);
          if (type != null) {
            return type;
          }
        }
      }
        return null;
    }




    /**
     * @param type type to look for declare extends on
     * @return list of all declare extends that apply to this type
     * in fully qualified strings
     */
    protected List<List<String>> getDeclareExtendsImplements(IType type) {
        List<String> declareExtends = new ArrayList<>();
        List<String> declareImplements = new ArrayList<>();
        if (type != null  && type.exists()) {
            AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(type);
            if (model.hasModel()) {
                List<IJavaElement> rels = model.getRelationshipsForElement(type, AJRelationshipManager.ASPECT_DECLARATIONS);
                for (IJavaElement je : rels) {
                    IProgramElement pe = model.javaElementToProgramElement(je);
                    List<String> parentTypes = null;
                    if (pe.getKind() == IProgramElement.Kind.DECLARE_PARENTS) {
                        parentTypes = pe.getParentTypes();
                    } else if (pe.getKind() == IProgramElement.Kind.ASPECT) {
                        // could be a concrete aspect that instantiates a declare parents relationship from an abstact aspect
                        Map<String, List<String>> parents = pe.getDeclareParentsMap();
                        if (parents != null) {
                            parentTypes = parents.get(type.getFullyQualifiedName());
                        }
                    }

                    if (parentTypes != null) {
                        IJavaProject project = type.getJavaProject();

                        // bug 273914---must determine if these are interfaces or classes
                        for (String parentType : parentTypes) {
                            IType parentTypeElt;
                            try {
                                parentTypeElt = project.findType(parentType);
                            } catch (JavaModelException e) {
                                parentTypeElt = null;
                            }
                            boolean parentIsClass;
                            boolean typeIsClass;
                            try {
                                parentIsClass = parentTypeElt == null || parentTypeElt.isClass();
                                typeIsClass = type.isClass();
                            } catch (JavaModelException e) {
                                parentIsClass = true;
                                typeIsClass = true;
                            }
                            if (parentIsClass && typeIsClass) {
                                declareExtends.add(parentType);
                            } else if (!parentIsClass && typeIsClass) {
                                declareImplements.add(parentType);
                            } else if (!parentIsClass && !typeIsClass) {
                                declareExtends.add(parentType);
                            } else if (parentIsClass && !typeIsClass) {
                                // error, but handle gracefully
                                declareExtends.add(parentType);
                            }
                        }
                    }
                }
            }
        }
        return Arrays.asList(declareExtends, declareImplements);
    }

    /**
     * returns reasonable text for all ITDs and ITITs
     * @param currentTypeName
     * @return
     */
    protected char[] getInterTypeDecls(char[] currentTypeName) {
        if (unit != null && currentTypeName != null && currentTypeName.length > 0) {
            AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(unit);
            if (model.hasModel()) {
                IType type = getHandle(new String(currentTypeName));
                if (type.exists()) {
                    List<IJavaElement> rels = model.getRelationshipsForElement(type, AJRelationshipManager.ASPECT_DECLARATIONS);
                    StringBuffer sb = new StringBuffer("\n\t");
                    for (IJavaElement je : rels) {
                        IProgramElement declareElt = model.javaElementToProgramElement(je);
                        if (declareElt != null && declareElt.getParent() != null && declareElt.getKind().isInterTypeMember()) { // checks to see if this element is valid
                            // should be fully qualified type and simple name

                            int lastDot = declareElt.getName().lastIndexOf('.');
                            String name = declareElt.getName().substring(lastDot+1);

                            if (declareElt.getKind() == IProgramElement.Kind.INTER_TYPE_FIELD) {
                                createITDFieldText(sb, declareElt, name);
                            } else if (declareElt.getKind() == IProgramElement.Kind.INTER_TYPE_METHOD ||
                                       declareElt.getKind() == IProgramElement.Kind.INTER_TYPE_CONSTRUCTOR) {
                                createITDMethodText(currentTypeName, sb, declareElt, name);
                            }
                        } else if (declareElt.getKind() == IProgramElement.Kind.CLASS) {
                        	// Intertype Inner type
                        	// add the declaration and all fields/methhods
                        	createITITText(sb, declareElt);
                        }
                    }
                    return sb.toString().toCharArray();
                }
            }
        }
        return new char[0];
    }


    /**
     * @param sb
     * @param declareElt
     */
    protected void createITITText(StringBuffer sb, IProgramElement declareElt) {
        sb.append("\n\tstatic class ").append(declareElt.getName()).append(" {\n");
        List<IProgramElement> children = declareElt.getChildren();
        for (IProgramElement child : children) {
            sb.append("\t\tpublic static ");
            sb.append(child.getCorrespondingType(true)).append(" ");
            sb.append(child.getName());
            if (child.getKind() == IProgramElement.Kind.FIELD) {
            	sb.append(";\n");
            } else {
            	sb.append("(");
                List<String> names = child.getParameterNames();
                List<char[]> types = child.getParameterTypes();
                if (types != null && names != null) {
                    for (Iterator<?> typeIter = types.iterator(), nameIter = names.iterator();
                         typeIter.hasNext();) {
                        String paramType = String.valueOf((char[]) typeIter.next());
                        String paramName = (String) nameIter.next();
                        sb.append(paramType).append(" ").append(paramName);
                        if (typeIter.hasNext()) {
                            sb.append(", ");
                        }
                    }
                }
                sb.append(") { }\n");
            }
        }
        sb.append("\t}\n");
    }


    /**
     * @param currentTypeName
     * @param sb
     * @param declareElt
     * @param name
     */
    protected void createITDMethodText(char[] currentTypeName, StringBuffer sb,
            IProgramElement declareElt, String name) {
        sb.append(getAccessibilityString(declareElt));
        for (IProgramElement.Modifiers modifier : declareElt.getModifiers()) {
            sb.append(modifier).append(" ");
        }
        // need to add a return statement?
        if (declareElt.getKind() == IProgramElement.Kind.INTER_TYPE_METHOD) {
            sb.append(declareElt.getCorrespondingType(true)).append(" ").append(name);
        } else {
            sb.append(currentTypeName);
        }
        sb.append("(");
        List<String> names = declareElt.getParameterNames();
        List<char[]> types = declareElt.getParameterTypes();
        if (types != null && names != null) {
            for (Iterator<?> typeIter = types.iterator(), nameIter = names.iterator();
                 typeIter.hasNext();) {
                String paramType = String.valueOf((char[]) typeIter.next());
                String paramName = (String) nameIter.next();
                sb.append(paramType).append(" ").append(paramName);
                if (typeIter.hasNext()) {
                    sb.append(", ");
                }
            }
        }
        sb.append(") { }\n");
    }


    /**
     * @param sb
     * @param declareElt
     * @param name
     */
    protected void createITDFieldText(StringBuffer sb,
            IProgramElement declareElt, String name) {
        for (IProgramElement.Modifiers modifier : declareElt.getModifiers()) {
            sb.append(modifier).append(" ");
        }
        sb.append(declareElt.getCorrespondingType(true)).append(" ").append(name).append(";\n\t");
    }


    private String getAccessibilityString(IProgramElement declareElt) {
        return (declareElt.getAccessibility() != Accessibility.PACKAGE ? declareElt.getAccessibility().toString() : "") + " ";
    }

    private boolean inTypeDeclaration() {
        return (inAspectDeclaration ||
                 inClassDeclaration ||
                 inInterfaceDeclaration ||
                 inEnumDeclaration);
    }

    /**
     * Inserts a reference to targetType at the given position. Thanks to this,
     * we can simulate the context switch necessary in intertype method
     * declarations.
     *
     * Transformations: - Insertion of local variable 'TargetType thiz' (or, if
     * thiz is already used, a number is added to thiz to make it unique) at
     * start of method mody
     *  - if code completion on code like 'this.methodcall().more...', 'this
     * gets replaced by thiz
     *  - if code completion on code like 'methodcall().more...', 'thiz.' gets
     * added in front.
     *
     * How the correct place for insertion is found: -
     *
     *
     */
    private void simulateContextSwitch(int position, char[] targetType) {
        int pos = findInsertionPosition(position - 1) + 1;
        //if code completion on 'this' -> overwrite the this keyword
        int len = 0;
        boolean dotRequired = true;
        if ((content[pos] == 't') && (content[pos + 1] == 'h')
                && (content[pos + 2] == 'i') && (content[pos + 3] == 's')
                && !Character.isJavaIdentifierPart(content[pos + 4])) {
            len = 4;
            dotRequired = false;
        }

        String contextSwitchIdentifier = findFreeIdentifier();
        char[] toInsert = (new String(targetType) + ' ' + contextSwitchIdentifier + "; " + contextSwitchIdentifier +
                (dotRequired ? "." : "")).toCharArray();
        addReplacement(pos, len, toInsert);
    }

    /**
     * @return An unused identifier
     */
    private String findFreeIdentifier() {
        int i = 0;
        String ident = ITD_INSERTED_IDENTIFIER + i;
        while (usedIdentifiers.contains(ident)) {
            i++;
            ident = ITD_INSERTED_IDENTIFIER + i;
        }
        usedIdentifiers.add(ident);
        return ident;
    }

    /**
     * @param pos -
     *            a code position
     * @return the position that defines the context of the current one at the
     *         highest level
     *
     * e.g. ' this.doSomthing().get' with pos on the last 't' returns the
     * position of the char before the first 't'
     */
    private int findInsertionPosition(int pos) {
        char ch = content[pos];
        int currentPos = pos;

        if (Character.isWhitespace(ch)) {
            currentPos = findPreviousNonSpace(pos);
            if (currentPos == -1)
                return pos;

            ch = content[currentPos];
            if (ch == '.')
                return findInsertionPosition(--currentPos);
            return pos;
        }

        if (Character.isJavaIdentifierPart(ch)) {
            while (Character.isJavaIdentifierPart(ch)) {
                currentPos--;
                ch = content[currentPos];
            }
            return findInsertionPosition(currentPos);
        }

        if (ch == '.') {
            return findInsertionPosition(--pos);
        }

        if (ch == ')') {
            currentPos--;
            int bracketCounter = 1;
            while (currentPos >= 0) {
                ch = content[currentPos];
                if (bracketCounter == 0)
                    break;
                if (ch == ')')
                    bracketCounter++;
                if (ch == '(') {
                    bracketCounter--;
                    if (bracketCounter < 0)
                        return -1;
                }
                currentPos--;
            }
            return findInsertionPosition(currentPos);
        }

        return pos;
    }

    private void applyReplacements() {
        Iterator<Replacement> iter = replacements.listIterator();
        int offset = 0;
        while (iter.hasNext()) {
            Replacement ins = iter.next();
            ins.posAfter = ins.posBefore + offset;
            replace(ins.posAfter, ins.length, ins.text);
            offset += ins.lengthAdded;
        }
    }

    private void replace(int pos, int origLength, char[] text) {
        if (origLength != text.length) {
            int lengthDiff = text.length - origLength;
            int oldEnd = pos + origLength;
            int newEnd = pos + text.length;
            char[] temp = new char[content.length + lengthDiff];

            System.arraycopy(content, 0, temp, 0, pos);
            System.arraycopy(content, oldEnd, temp, newEnd, content.length
                    - oldEnd);
            content = temp;
        }
        System.arraycopy(text, 0, content, pos, text.length);
    }

    private void startPointcutDesignator() {
        if (inPointcutDesignator)
            return;
        inPointcutDesignator = true;
        posColon = scanner.getCurrentTokenStartPosition();
    }

    /**
     *
     */
    private void endPointcutDesignator() {
        inPointcutDesignator = false;
        if (options.isKeepPointcuts()) {
            addReplacement(posColon, 1, new char[] { '{' });
        } else {
            int posSemi = scanner.getCurrentTokenStartPosition();
            int len = posSemi - posColon;
            char[] empty = new char[len];
          Arrays.fill(empty, ' ');
            addReplacement(posColon, len, empty);
        }
    }


    // Doesn't handle comments
    private void processPotentialIntertypeDeclaration() {
        // find the start of the declaration
        // by searching backwards

        //pos points to the final '.'
        int pos = scanner.getCurrentTokenStartPosition();
        int iter = pos;
        iter--;
        while (iter >= 0) {

            // handles type parameter
            if (content[iter] == '>') {
                int genericsDepth = 1;
                iter--;
                while (iter >= 0 && genericsDepth > 0) {
                    if (content[iter] == '>') {
                        genericsDepth++;
                    } else if (content[iter] == '<') {
                        genericsDepth--;
                    }
                    iter--;
                }
            } else if (content[iter] == '<') {
                // we are looking at a qualified type within
                // a type parameter...return
                return;
            } if (Character.isWhitespace(content[iter])) {
                iter--;
            } else if (Character.isJavaIdentifierPart(content[iter])) {
                // seems like we are in an ITD
                break;
            } else {
                return;
            }
        }
        if (iter < 0) {
            return;
        }

        // now nameStart refers to the end of the name just before the last '.'
        // eg- it refers to 'p' in
        // p1.p2.Flop  <? extends Baz>.doSomething()

        // next continue iterating back to find the start of the word.
        // if upper case, then assume it is an ITD
        while (iter >= 0) {
            if (Character.isJavaIdentifierPart(content[iter])) {
                iter--;
            } else {
                break;
            }
        }
        if (iter < 0 || ! Character.isUpperCase(content[iter+1])) {
            return;
        }

        int nameStart = iter+1;

        // continue iterating back to find the last part of the qualification
        outer:
        while(true) {

            // find the previous '.'
            while (iter >= 0) {
                if (Character.isWhitespace(content[iter])) {
                    iter--;
                } else if (content[iter] == '.') {
                    nameStart = iter;
                    break;
                } else {
                    // something else was found
                    // we are done
                    break outer;
                }
            }

            iter--;
            // find end of previous name
            while (iter >= 0) {
                if (Character.isWhitespace(content[iter])) {
                    iter--;
                } else if (Character.isJavaIdentifierPart(content[iter])) {
                    nameStart = iter;
                    break;
                } else {
                    break outer;
                }
            }

            // find previous start of name
            iter--;
            while (iter >= 0) {
                if (Character.isJavaIdentifierPart(content[iter])) {
                    iter--;
                } else {
                    nameStart = iter+1;
                    break;
                }
            }
        }

        // last thing to do is to eat up whitespace after the '.'
        int nameEnd = pos+1;
        while (nameEnd < content.length) {
            if (!Character.isWhitespace(content[nameEnd])) {
                break;
            }
            nameEnd++;
        }

        char[] itdName = new char[nameEnd - nameStart];
        System.arraycopy(content, nameStart, itdName, 0, itdName.length);

        // now that we have the name, store the type name (and generics), if not fully qualified
        char[][] splits = CharOperation.splitOn('.', itdName);
        if (splits.length == 2) {
            typeReferences.add(String.valueOf(splits[0]));
        }

        // now that we have the entire length of the name, replace everything that is not
        // a valid Java identifier part with '$'
        for (int i = 0; i < itdName.length; i++) {
            if (! Character.isJavaIdentifierPart(itdName[i])) {
                itdName[i] = '$';
            }
        }

        //if requested, add ajc$ in front of intertype declaration
        //e.g. "public int Circle$x;" -> "public int ajc$Circle$x;"
        if (options.isAddAjcTagToIntertypesEnabled()) {
            addReplacement(nameStart, 0, "ajc$".toCharArray()); //$NON-NLS-1$
        }
        addReplacement(nameStart, itdName.length, itdName);
    }

    private boolean hasWordAtPosition(String string, int pos) {
        char[] word = string.toCharArray();
        for (int i = 0; i < word.length; i++) {
            if (word[i] != content[pos+i] ) {
                return false;
            }
        }
        return true;
    }


    public int findPrevious(char ch, int pos) {
        while (pos >= 0) {
            if (content[pos] == ch)
                return pos;
            pos--;
        }
        return -1;
    }

    public int findPreviousWhitespaceOr(char ch, int pos) {
        while (pos >= 0) {
            if (content[pos] == ch || Character.isWhitespace(content[pos])) {
                    return pos;
            }
            pos--;
        }
        return -1;
    }



    public int findPrevious(char[] chs, int pos) {
        while (pos >= 0) {
          for (char ch : chs) {
            if (content[pos] == ch)
              return pos;
          }
            pos--;
        }
        return -1;
    }

    public int findPreviousSpace(int pos) {
        while (pos >= 0) {
            if (Character.isWhitespace(content[pos]))
                return pos;
            pos--;
        }
        return -1;
    }

    public int findPreviousNonSpace(int pos) {
        while (pos >= 0) {
            if (!Character.isWhitespace(content[pos]))
                return pos;
            pos--;
        }
        return -1;
    }

    public int findNextNonSpace(int pos) {
        while (pos < content.length) {
            if (!Character.isWhitespace(content[pos]))
                return pos;
            pos++;
        }
        return -1;
    }

    public int findNext(char[] chs, int pos) {
        while (pos < content.length) {
          for (char ch : chs) {
            if (content[pos] == ch)
              return pos;
          }
            pos++;
        }
        return -1;
    }

    private void consumeRetOrThrow() {
        int pos = scanner.getCurrentTokenStartPosition();
        char[] content = scanner.source;

        int end = findNext(endThrow, pos);
        if (end == -1)
            return;

        char[] temp;
        if (content[end] == endThrow[0]) {
            pos = findPrevious(')', pos);
            if (pos == -1)
                return;
            int advicebracket = findPrevious('(', pos);
            if (advicebracket == -1)
                return;
            temp = new char[end - pos + 1];
            if (bracketsContainSomething(advicebracket)
                    && bracketsContainSomething(end))
                temp[0] = ',';
            else
                temp[0] = ' ';
            for (int i = 1; i < temp.length; i++) {
                temp[i] = ' ';
            }
        } else {
            temp = new char[end - pos];
          Arrays.fill(temp, ' ');
        }
        addReplacement(pos, temp.length, temp);
    }

    /**
     * @param end
     * @return
     */
    private boolean bracketsContainSomething(int start) {
        while (++start < content.length) {
            if (content[start] == ')')
                return false;
            if (Character.isJavaIdentifierPart(content[start]))
                return true;
        }
        return false;
    }

    private int findLast(char ch) {
        int pos = content.length;
        while (--pos >= 0) {
            if (content[pos] == ch)
                break;
        }
        return pos;
    }

    //adds references to all used type -> organize imports will work
    private void addReferences() {
        if (typeReferences == null) {
            return;
        }

        int pos = findLast('}');
        if (pos < 0)
            return;
        StringBuilder temp = new StringBuilder(typeReferences.size() * 10);
      for (String ref : typeReferences) {
        if (knownTypeParameters != null && knownTypeParameters.contains(ref)) {
          // don't add type parameters since they do not need to be imported
          continue;
        }
        temp.append(ref).append(" ").append(findFreeIdentifier()); //$NON-NLS-1$
        temp.append(";\n");
      }
        char[] decls = new char[temp.length()];
        temp.getChars(0, decls.length, decls, 0);
        addReplacement(pos, 0, decls);
    }

    //adds a replacement to list
    //pre: list sorted, post: list sorted
    private void addReplacement(int pos, int length, char[] text) {
        int last = replacements.size() - 1;
        while (last >= 0) {
            if (replacements.get(last).posBefore < pos)
                break;
            last--;
        }
        replacements.add(last + 1, new Replacement(pos, length, text));
    }

    public static boolean conflictsWithAJEdit(int offset, int length,
            ArrayList<Replacement> replacements) {
        Replacement ins;
      for (Replacement replacement : replacements) {
        ins = replacement;
        if ((offset >= ins.posAfter) && (offset < ins.posAfter + ins.length)) {
          return true;
        }
        if ((offset < ins.posAfter) && (offset + length > ins.posAfter)) {
          return true;
        }
      }
        return false;
    }

    //translates a position from after to before changes
    //if the char at that position did not exist before, it returns the
    // position before the inserted area
    public static int translatePositionToBeforeChanges(int posAfter,
            ArrayList<Replacement> replacements) {
        Replacement ins;
        int offset = 0, i;

        for (i = 0; i < replacements.size(); i++) {
            ins = replacements.get(i);
            if (ins.posAfter > posAfter)
                break;
            offset += ins.lengthAdded;
        }
        if (i > 0) {
            ins = replacements.get(i - 1);
            if (ins.posAfter + ins.text.length > posAfter) {
                //diff must be > 0
                int diff = posAfter - ins.posAfter;
                if (diff > ins.length)
                    //we are in inserted area -> return pos directly before
                    // that area
                    offset += diff - ins.length;
            }
        }

        return Math.max(posAfter - offset, 0);
    }

    //translates a position from before to after changes
    public static int translatePositionToAfterChanges(int posBefore,
            ArrayList<Replacement> replacements) {
      for (Replacement ins : replacements) {
        if (ins.posAfter <= posBefore)
          posBefore += ins.lengthAdded;
        else
          return posBefore;
      }
        return posBefore;
    }

}
