/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January - copied to AJDT and adapted to enable AspectJ problems
 *                    to be found
 ******************************************************************************/
package org.eclipse.ajdt.core.parserbridge;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.aspectj.ajdt.internal.compiler.ast.AdviceDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.DeclareDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.InterTypeDeclaration;
import org.aspectj.ajdt.internal.compiler.ast.PointcutDeclaration;
import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IRelationship;
import org.aspectj.org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.aspectj.org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.codeconversion.ITDAwareLookupEnvironment;
import org.eclipse.ajdt.core.codeconversion.ITDAwareNameEnvironment;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitInfo;
import org.eclipse.ajdt.core.javaelements.AdviceElement;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.AspectJMemberElement;
import org.eclipse.ajdt.core.javaelements.DeclareElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.javaelements.PointcutElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.ajdt.internal.core.parserbridge.IAspectSourceElementRequestor;
import org.eclipse.ajdt.internal.core.ras.NoFFDC;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.ICompilerRequestor;
import org.eclipse.jdt.internal.compiler.IErrorHandlingPolicy;
import org.eclipse.jdt.internal.compiler.IProblemFactory;
import org.eclipse.jdt.internal.compiler.ISourceElementRequestor;
import org.eclipse.jdt.internal.compiler.SourceElementParser;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.ImportReference;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.AbortCompilation;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.core.AnnotatableInfo;
import org.eclipse.jdt.internal.core.Annotation;
import org.eclipse.jdt.internal.core.CancelableProblemFactory;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.ImportContainer;
import org.eclipse.jdt.internal.core.ImportDeclaration;
import org.eclipse.jdt.internal.core.Initializer;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jdt.internal.core.JavaElementInfo;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.MemberValuePair;
import org.eclipse.jdt.internal.core.NameLookup;
import org.eclipse.jdt.internal.core.PackageDeclaration;
import org.eclipse.jdt.internal.core.SourceField;
import org.eclipse.jdt.internal.core.SourceMethod;
import org.eclipse.jdt.internal.core.SourceRefElement;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.internal.core.TypeParameter;
import org.eclipse.jdt.internal.core.util.CommentRecorderParser;

/**
 * Problem finder for AspectJ problems
 * Mostly copied from CompilationUnitProblemFinder
 * Changes Marked "// AspectJ Change"
 *
 * Responsible for resolving types inside a compilation unit being reconciled,
 * reporting the discovered problems to a given IProblemRequestor.
 */
public class AJCompilationUnitProblemFinder extends
		CompilationUnitProblemFinder implements NoFFDC {

    /**
     * Reconciling flag saying that this CU is a java CU in
     * an AJ editor.  Means that we need to be more liberal
     * with problem removal
     * since all of the AJ contents have been transformed to
     * Java
     */
    public final static int JAVA_FILE_IN_AJ_EDITOR = 0x000008;


	private final CompilationUnit cu; // AspectJ Change

	/**
	 * @param environment
	 * @param policy
	 * @param settings
	 * @param requestor
	 * @param problemFactory
	 */
	public AJCompilationUnitProblemFinder(
	        INameEnvironment environment,
			IErrorHandlingPolicy policy,
			CompilerOptions compilerOptions,
			ICompilerRequestor requestor,
			IProblemFactory problemFactory,
			CompilationUnit cu) { // AspectJ Change
		super(environment, policy, compilerOptions, requestor, problemFactory);
		this.cu = cu; // AspectJ Change
		initializeParser();

		// begin AspectJ Change
		// the custom lookup environment will insert mock ITD elements
		lookupEnvironment =
		    new ITDAwareLookupEnvironment(lookupEnvironment, environment);
        // end AspectJ Change
	}

	/* (non-Javadoc)
	 * Use the AspectJ parser
	 * @see org.eclipse.jdt.internal.compiler.Compiler#initializeParser()
	 */
	@Override
    public void initializeParser() {
		// AspectJ Change Begin
	    if (cu != null) {  // wait until object is initialized to initialize parser
             try {
            	 Object elementInfo = cu.getElementInfo();
                if (elementInfo instanceof AJCompilationUnitInfo) {
            	     AJCompilationUnit ajcu = (AJCompilationUnit) cu;
            	     ajcu.discardOriginalContentMode();
            		 this.parser = new AJSourceElementParser2(
            				 new NullRequestor(), new DefaultProblemFactory(), getCompilerOptions(cu), true /* parse local declarations */,false);
            		 ajcu.requestOriginalContentMode();
            	 } else {
            	     // use a SourceElementParser to ensure that local declarations are parsed even when diet
                     this.parser = new SourceElementParser(new NullRequestor(), new DefaultProblemFactory(),
                             getCompilerOptions(cu), true, false);
            	 }

    		} catch (JavaModelException ignored) {
    		}
	    }
	    // AspectJ Change End
	}

    private CompilerOptions getCompilerOptions(IJavaElement elt) {
        IJavaProject project = elt.getJavaProject();
        Map<String, String> options = project == null ? JavaCore.getOptions() : project.getOptions(true);
        return new CompilerOptions(options);
    }

	public static CompilationUnitDeclaration processAJ(
	        CompilationUnit unitElement, // AspectJ Change
	        WorkingCopyOwner workingCopyOwner,
	        Map<String, CategorizedProblem[]> problems,
	        boolean creatingAST,
	        int reconcileFlags,
	        IProgressMonitor monitor)
	        throws JavaModelException {
        return processAJ(unitElement, null/*use default Parser*/, workingCopyOwner, problems, creatingAST, reconcileFlags, monitor);

	}

	public static CompilationUnitDeclaration processAJ( // AspectJ Change
            CompilationUnit unitElement, // AspectJ Change
	        CommentRecorderParser parser, // AspectJ Change
	        WorkingCopyOwner workingCopyOwner,
	        Map<String, CategorizedProblem[]> problems,
	        boolean creatingAST,
	        int reconcileFlags,
	        IProgressMonitor monitor)
	        throws JavaModelException {


	    boolean isJavaFileInAJEditor = (reconcileFlags & JAVA_FILE_IN_AJ_EDITOR) != 0;

	    JavaProject project = unitElement.getJavaProject();
	    ITDAwareNameEnvironment environment = null;
        CancelableProblemFactory problemFactory = null;
        AJCompilationUnitProblemFinder problemFinder = null; // AspectJ Change
        try {

            // AspectJ Change begin
            // use an ITDAware environment to ensure that ITDs are included for source types
            environment = new ITDAwareNameEnvironment(project,
                    workingCopyOwner, monitor);
            // AspectJ Change end

            problemFactory = new CancelableProblemFactory(monitor);
            problemFinder = new AJCompilationUnitProblemFinder( // AspectJ Change
                    environment,
                    getHandlingPolicy(),
                    getCompilerOptions(project.getOptions(true), creatingAST, ((reconcileFlags & ICompilationUnit.ENABLE_STATEMENTS_RECOVERY) != 0)),
                    getRequestor(), problemFactory, unitElement);
            CompilationUnitDeclaration unit = null;

            // AspectJ Change begin
            // the parser should be a SourceElementParser or AJSourceElementParser2.
            // this ensures that a diet parse can be done, while at the same time
            // all declarations be reported
            if (parser != null) {
                problemFinder.parser = parser;
            }
            try {
                if (problemFinder.parser instanceof SourceElementParser) {
                    unit = ((SourceElementParser) problemFinder.parser).parseCompilationUnit(
                            unitElement, true/* full parse */, monitor);
                    problemFinder.resolve(unit, unitElement,
                            true, // verify methods
                            true, // analyze code
                            true); // generate code
                } else if (problemFinder.parser instanceof AJSourceElementParser2) {
                    unit = ((AJSourceElementParser2) problemFinder.parser).parseCompilationUnit(
                            unitElement, true/* full parse */, monitor);
                    problemFinder.resolve(unit, unitElement,
                            true, // verify methods
                            true, // analyze code
                            true); // generate code
                } else {
                    unit = problemFinder.resolve(unitElement,
                            true, // verify methods
                            true, // analyze code
                            true); // generate code
                }

            } catch (AbortCompilation e) {
                problemFinder.handleInternalException(e, unit);
            }

            // revert the compilation units that have ITDs in them
            ((ITDAwareLookupEnvironment) problemFinder.lookupEnvironment).revertCompilationUnits();
            // AspectJ Change end

            CompilationResult unitResult = unit.compilationResult;
            CategorizedProblem[] unitProblems = unitResult.getProblems();
            int length = unitProblems == null ? 0 : unitProblems.length;
            if (length > 0) {
                // AspectJ Change begin
                // filter out spurious problems
                CategorizedProblem[] categorizedProblems = new CategorizedProblem[length];
                System.arraycopy(unitProblems, 0, categorizedProblems, 0,
                        length);
                categorizedProblems = removeAJNonProblems(categorizedProblems, unitElement, isJavaFileInAJEditor);
                if (categorizedProblems.length > 0) {
                    problems.put(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, categorizedProblems);
                }
                // AspectJ Change end
            }
            unitProblems = unitResult.getTasks();
            length = unitProblems == null ? 0 : unitProblems.length;
            if (length > 0) {
                CategorizedProblem[] categorizedProblems = new CategorizedProblem[length];
                System.arraycopy(unitProblems, 0, categorizedProblems, 0,
                        length);
                problems.put(IJavaModelMarker.TASK_MARKER, categorizedProblems);
            }
            if (NameLookup.VERBOSE) {
                AJLog.log(Thread.currentThread()
                        + " TIME SPENT in NameLoopkup#seekTypesInSourcePackage: " + environment.nameLookup.timeSpentInSeekTypesInSourcePackage + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
                AJLog.log(Thread.currentThread()
                        + " TIME SPENT in NameLoopkup#seekTypesInBinaryPackage: " + environment.nameLookup.timeSpentInSeekTypesInBinaryPackage + "ms"); //$NON-NLS-1$ //$NON-NLS-2$
            }
            return unit;
        } catch (OperationCanceledException e) {
            // catch this exception so as to not enter the
            // catch(RuntimeException e) below
            throw e;
        } catch (RuntimeException e) {
            String message = handleException(unitElement, environment, e);
            throw new JavaModelException(new RuntimeException(message, e),
                    IJavaModelStatusConstants.COMPILER_FAILURE);

        } finally {
            if (environment != null)
                environment.setMonitor(null); // AJDT 3.6 // don't hold a reference to this
                                            // external object
            if (problemFactory != null)
                problemFactory.monitor = null; // don't hold a reference to this
                                               // external object
            // NB: unit.cleanUp() is done by caller
            if (problemFinder != null && !creatingAST)
                problemFinder.lookupEnvironment.reset();
        }
	}

    private static String handleException(CompilationUnit unitElement,
            ITDAwareNameEnvironment environment, RuntimeException e)
            throws JavaModelException {
        AJLog.log("Exception occurred during problem detection:");
        AJLog.log(e.getClass().getName() + ": " + e.getMessage());
        StackTraceElement[] trace = e.getStackTrace();
      for (StackTraceElement traceElement : trace) {
        AJLog.log(traceElement.toString());
      }
        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            AJLog.log("Caused by:");
            AJLog.log(cause.getClass().getName() + ": " + cause.getMessage());
            trace = cause.getStackTrace();
          for (StackTraceElement stackTraceElement : trace) {
            AJLog.log(stackTraceElement.toString());
          }
        }

        String lineDelimiter = org.eclipse.jdt.internal.compiler.util.Util.LINE_SEPARATOR;
        StringBuilder message = new StringBuilder(
                "All Source code being worked on:"); //$NON-NLS-1$
        message.append(lineDelimiter);
        message.append("----------------------------------- SOURCE BEGIN -------------------------------------"); //$NON-NLS-1$
        message.append(lineDelimiter);
        if (unitElement instanceof AJCompilationUnit) {
            ((AJCompilationUnit) unitElement).requestOriginalContentMode();
        }
        message.append(unitElement.getSource());
        if (unitElement instanceof AJCompilationUnit) {
            ((AJCompilationUnit) unitElement).discardOriginalContentMode();
        }
        message.append(lineDelimiter);
        message.append("----------------------------------- SOURCE END -------------------------------------"); //$NON-NLS-1$


        message.append("----------------------------------- WORKING COPIES -------------------------------------"); //$NON-NLS-1$
        if (environment != null) {
            ICompilationUnit[] workingCopies = environment.getWorkingCopies();
            if (workingCopies != null) {
              for (ICompilationUnit workingCopy : workingCopies) {
                message.append("----------------------------------- WORKING COPY SOURCE BEGIN -------------------------------------"); //$NON-NLS-1$
                message.append(lineDelimiter);
                if (workingCopy instanceof AJCompilationUnit) {
                  ((AJCompilationUnit) workingCopy).requestOriginalContentMode();
                }
                message.append(workingCopy.getSource());
                if (workingCopy instanceof AJCompilationUnit) {
                  ((AJCompilationUnit) workingCopy).discardOriginalContentMode();
                }
                message.append(lineDelimiter);
                message.append("----------------------------------- WORKING COPY SOURCE END -------------------------------------"); //$NON-NLS-1$
              }
            }
        } else {
            message.append("none");
        }
        message.append("----------------------------------- WORKING COPIES END -------------------------------------"); //$NON-NLS-1$



        AJLog.log(message.toString());
        return message.toString();
    }


	// AspectJ Change to the end---removes spurious problems

	/**
	 * removes all problems that have come from
	 * valid ITDs
	 *
	 * Not quite right...this will assume that all ITDs apply to all
	 * types declared here.  So, it may erroneously remove errors.
	 *
	 * @param categorizedProblems
	 * @return
	 */
	private static CategorizedProblem[] removeAJNonProblems(
            CategorizedProblem[] categorizedProblems, CompilationUnit unit, boolean isJavaFileInAJEditor) {

	    AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(unit);
	    boolean hasModel = model.hasModel();

        List<CategorizedProblem> newProblems = new LinkedList<>();
    for (CategorizedProblem categorizedProblem : categorizedProblems) {
      // determine if this problem should be filtered
      if (isARealProblem(categorizedProblem, unit, model, hasModel, isJavaFileInAJEditor)) {
        newProblems.add(categorizedProblem);
      }
    }
        categorizedProblems = newProblems.toArray(new CategorizedProblem[0]);
        return categorizedProblems;
     }

	// be eager about what we discard.  If unsure
	// it is better to discard.  because the real errors will show up when a compile happens
    public static boolean isARealProblem(
            CategorizedProblem categorizedProblem, CompilationUnit unit, AJProjectModelFacade model, boolean hasModel, boolean isJavaFileInAJEditor) {

        int numArgs = categorizedProblem.getArguments() == null ?
                0 : categorizedProblem.getArguments().length;
        String firstArg = numArgs > 0 ? categorizedProblem.getArguments()[0] : null;
        String secondArg = numArgs > 1 ? categorizedProblem.getArguments()[1] : null;

        int id = categorizedProblem.getID();

        if (!hasModel &&
            (id == IProblem.UndefinedType ||
             id == IProblem.UndefinedName ||
             id == IProblem.UnresolvedVariable || // AJDT 3.6
             id == IProblem.UndefinedField ||
             id == IProblem.UndefinedMethod ||
             id == IProblem.UndefinedConstructor ||
             id == IProblem.IllegalCast ||
             id == IProblem.AbstractMethodMustBeImplemented)  // anonymous interface with ITDs implementing abstract method
             ) {
            // if there is no model, don't take any chances.
            // everything that might be an ITD reference is ignored
            return false;
        }

        if (categorizedProblem.getSourceStart() == 0) {
            // a place for all problems that don't have source locations
            // because they come from ITDs
            return false;
        }

        if(numArgs > 0 &&
                id == IProblem.UndefinedMethod &&
                (extraAspectMethods.contains(firstArg)) || extraAspectMethods.contains(secondArg)) {
            // probably hasAspect or aspectOf
            return false;
        }

        if (numArgs > 1 &&
                (id == IProblem.DuplicateField ||
                 id == IProblem.DuplicateMethod) &&
                (aspectMemberNames.contains(firstArg) ||
                 aspectMemberNames.contains(secondArg)) ||
                 declareAnnotationKinds.contains(firstArg) ||
                 declareAnnotationKinds.contains(secondArg)) {
            // declare statement if more than one exist in a file
            // or around advice if more than one of the same kind exists in the aspect
            return false;
        }

        if (numArgs > 1 &&
                id == IProblem.DuplicateMethod &&
                isTranslatedAdviceName(firstArg, secondArg)) {
            // more than one before or after advice exists
            // in same file with same number and types of arguments
            // as per bug 318132, before and after names are translated
            // to 'b' and 'a' respectively
            return false;
        }

        if (numArgs == 0 &&
        		id == IProblem.MissingReturnType) {
            // ITD constructors don't have return types
            // check the name to see if there is a $ in it
            String problemRegion = extractProblemRegion(categorizedProblem, unit);
            if (problemRegion.contains("$")) {
                return false;
            }
            String[] parts = problemRegion.split("\\(");
            String name = parts[0].trim();
            if (aspectMemberNames.contains(name)) {
                // advice---before or after
                return false;
            }
        }

        if (numArgs == 0 && id == IProblem.InvalidExplicitConstructorCall) {
            // ITD constructor making explicit this() call.
            // lots of potential for false negatives
            return false;
        }

        if (numArgs == 0 && id == IProblem.MethodRequiresBody) {
            // Likely to be a pointcut definition
            return false;
        }

        if (numArgs == 2 && id == IProblem.ParsingErrorInsertToComplete &&
                firstArg.equals(";") && secondArg.equals("FieldDeclaration")) {
            // might be a declare statement
            String problemRegion = extractProblemRegion(categorizedProblem, unit);
            if (aspectMemberNames.contains(problemRegion)) {
                return false;
            }
        }

        // this one is not used any more since the '@' is being removed from the text
//        if (numArgs == 1 &&
//                id == IProblem.ParsingErrorDeleteToken &&
//                firstArg.equals("@")) {
//            // likely to be declare annotation declaration
//            // declare @type, declare @constructor, declare @method, declare @field
//            String problemRegion = extractNextJavaIdentifier(unit, categorizedProblem.getSourceEnd());
//            if (declareAnnotationKinds.contains(problemRegion)) {
//                return false;
//            }
//        }

        if (numArgs == 1 && id == IProblem.UndefinedType && declareAnnotationKinds.contains(firstArg)) {
            // alternate error of declare annotations
            return false;
        }



        if (numArgs == 1 && id == IProblem.UndefinedType && firstArg.equals("declare")) {
            // from a declare declaration
            return false;
        }

        if (numArgs == 1 && id == IProblem.UndefinedType && firstArg.equals("pointcut")) {
            // from a pointcut declaration
            return false;
        }

        if (numArgs > 0 &&
                (id == IProblem.UndefinedName ||
                 id == IProblem.UnresolvedVariable || // AJDT 3.6
                 id == IProblem.UndefinedField ||
                 id == IProblem.UndefinedMethod ||
                 id == IProblem.UndefinedType ||
                 id == IProblem.UndefinedConstructor)
                &&
                isITDName(categorizedProblem, unit, model, isJavaFileInAJEditor)) {
            // a reference inside an aspect to an ITD that it declares
            return false;
        }


        if (hasModel && id == IProblem.ShouldReturnValue &&
                categorizedProblem.getSourceStart() == 0 &&
                categorizedProblem.getSourceEnd() == 0) {
            // from an inserted ITD that has already been removed
            // this problem comes because the bodies of the ITDs inserted by ITDInserter
            // are always empty even when there should be a return value
            return false;
        }

        if (hasModel && (
                id == IProblem.NotVisibleType ||
                id == IProblem.MethodReducesVisibility
                ) &&
                categorizedProblem.getSourceStart() == 0) {
            // declare parents type that is not visible by current
            // type.  this is fine as long as it is visible
            // in the scope of the declare parents declaration.
            return false;
        }

        if ((id == IProblem.NotVisibleConstructor ||
            id == IProblem.NotVisibleField ||
            id == IProblem.NotVisibleMethod ||
            id == IProblem.NotVisibleType) &&
            isPrivilegedAspect(categorizedProblem, unit, isJavaFileInAJEditor)) {

            // a privileged aspect should be able to see all private/protected members
            return false;
        }

        try {

            if (numArgs > 1 &&
                    id == IProblem.DuplicateMethod &&
                    simpleNamesEquals(firstArg, secondArg)) {
                // bug 280385
                // no arg constructor ITD when the target type
                // has an implicit no arg constructor

                IJavaElement elt = unit.getElementAt(categorizedProblem.getSourceStart());
                // here, check to see if the method name is the same as the
                // type name. If so, then look for the default constructor,
                // if none exists, then we can ignore this problem
                if (elt.getElementType() == IJavaElement.TYPE) {
                    IType type = (IType) elt;
                    if (type.getElementName().equals(firstArg)) {
                        IMethod method = type.getMethod(type.getElementName(), new String[0]);
                        if (!method.exists()) {
                            return false;
                        }
                    }
                }
            }

            if (id == IProblem.ReturnTypeMismatch 	 &&
                    numArgs == 2 &&
                    typeAtPositionIsArg(categorizedProblem, unit, firstArg)) {
                if (findLastSegment(getITDTargetType(categorizedProblem, unit, isJavaFileInAJEditor)).equals(findLastSegment(secondArg))) {
                    // bug 284358
                    // this problem occurs when 'this' is returned from an ITD method
                    // the resolver thinks there is a type mismath because it was
                    // expecting the aspect type (argument 1) instead of the ITD type
                    // (argument 2)
                    return false;
                }

                if (insideITD(categorizedProblem, unit, isJavaFileInAJEditor) &&
                        isThisExpression(categorizedProblem, unit)) {
                    // Bug 361170
                    // Likely a this expresion that is casted to a super type of the ITD
                    return false;
                }
            }

            if (numArgs > 0 &&
                    (id == IProblem.UndefinedMethod ||
                     id == IProblem.UndefinedName ||
                     id == IProblem.UnresolvedVariable) && // AJDT 3.6
                   (adviceBodyNames.contains(firstArg) || adviceBodyNames.contains(secondArg) ) &&
                   insideAdvice(categorizedProblem, unit)) {
                // proceed/thisJoinPoint... statement
                return false;
            }

            if (numArgs == 1 && (
                    id == IProblem.ParsingErrorDeleteToken ||
                    id == IProblem.ParsingErrorDeleteTokens
                    ) &&
                    aspectMemberNames.contains(firstArg) &&
                    insideITD(categorizedProblem, unit, isJavaFileInAJEditor)) {
                // the implements or extends clause of a declare statement
                return false;
            }

            if (id == IProblem.ParameterMismatch &&
                    insideITD(categorizedProblem, unit, isJavaFileInAJEditor)) {
                // Probably a reference to 'this' inside an ITD
                // compiler thinks 'this' refers to the containing aspect
                // not the target type
                return false;
            }

            if (id == IProblem.AbstractMethodInAbstractClass &&
                    insideITD(categorizedProblem, unit, isJavaFileInAJEditor)) {
                // an abstract method ITD inside a concrete aspect
                // ITDs are allowed to be abstract if the target
                // type is an abstract class, but problem finder does not know this
                return false;
            }

            if (id == IProblem.IllegalAbstractModifierCombinationForMethod &&
                    insideITD(categorizedProblem, unit, isJavaFileInAJEditor)) {
                // private abstract itd in aspect
                return false;
            }

            if (id == IProblem.UnusedPrivateField &&
                    (insideITD(categorizedProblem, unit, isJavaFileInAJEditor) ||
                            getITDNames(unit, model).size() > 0)) {
                // private itd is said to be unused, even if it is really used elsewhere
                // also, if this type has some ITDs, then we really don't know if it is used in the
                // ITDs, so just be safe and ignore this problem
                return false;
            }

            if (numArgs > 0 &&
                    (id == IProblem.UndefinedName ||
                     id == IProblem.UnresolvedVariable || // AJDT 3.6
                     id == IProblem.UndefinedField ||
                     id == IProblem.UndefinedMethod ||
                     id == IProblem.UndefinedType ||
                     id == IProblem.UndefinedConstructor)
                    &&
                    insideITD(categorizedProblem, unit, isJavaFileInAJEditor)) {
                // likely to be a reference inside an ITD to a name in the target type
                // also will erroneously filter out truly undefined names
                return false;
            }

            if (numArgs > 0 &&
                    (id == IProblem.UndefinedType ||
                     id == IProblem.InternalTypeNameProvided
                    )  &&
                    firstArg.indexOf('$') != -1) {
                // based on previous test, we are not inside of an ITD,
                // so we may be defining a field or variable with a
                // type of an inner class using a '.'.
                // the AspectsConvertingParser converts this '.' into a '$'
                // ignore.

                return false;
            }

            if (id == IProblem.NonStaticAccessToStaticField
                    && isITDName(categorizedProblem, unit, model, isJavaFileInAJEditor)) {
                // this is a reference to an ITD field on an interface
                // compiler thinks that all fields in interfaces are static final
                return false;
            }

            if ((id == IProblem.UnhandledException ||
                    id == IProblem.UnhandledExceptionInImplicitConstructorCall ||
                    id == IProblem.UnhandledExceptionInDefaultConstructor) &&
                    (!model.hasModel() || isSoftened(categorizedProblem, unit, model, isJavaFileInAJEditor))) {
                return false;
            }

            if (id == IProblem.UninitializedBlankFinalField &&
                    unit.getElementAt(categorizedProblem.getSourceStart()) == null) {
                // likely to be inserted dummy fields for organize imports
                // this only happens when the last declaration is an interface
                // these dummy fields are implicitly converted to public static final
                return false;
            }

            if (id == IProblem.AbstractMethodsInConcreteClass &&
                    isAspect(categorizedProblem, unit, isJavaFileInAJEditor)) {
                /* AJDT 1.7 */
                // an aspect that has an abstract ITD will have this problem
                // in this case it is a spurious problem.  Filter it
                // unfortunately, this also means filtering real problems
                // where concrete aspects have abstract methods
                // new for 1.7
                return false;
            }

            if (id == IProblem.JavadocMissingReturnTag
                    && insidePointcut(categorizedProblem, unit)) {
                // pointcuts are parsed as methods with 'pointcut'
                // as the return type
                // when JavaDoc checking is set, the parser thinks that
                // 'pointcut' should have its own javadoc tag
                return false;
            }

            if (numArgs == 1 && id == IProblem.ShouldReturnValue &&
                    firstArg.equals("int") &&
                    insideAdvice(categorizedProblem, unit)) {
                // Bug 318132: after keyword is changed to 'int a' to avoid throwing exceptions while
                // evaluating variables during debug
                return false;
            }

            if ((id == IProblem.InvalidTypeForCollection ||
                    id == IProblem.InvalidTypeForCollectionTarget14 ||
                    id == IProblem.IncompatibleTypesInConditionalOperator ||
                    id == IProblem.IllegalCast) &&
                    insideITD(categorizedProblem, unit, isJavaFileInAJEditor) &&
                    // I wish there were a more precise way of doing this.  Need to
                    // look for a 'this' expression.
                    extractProblemRegion(categorizedProblem, unit).contains("this")) {

                // Bug 347021
                // a 'this' expression in an ITD refers to the target type, not the aspect.
                // these problems here indicate that the aspect type is being used instead
                // of the target type.
                return false;
            }

        } catch (JavaModelException ignored) {
        }

        if (id == IProblem.AbstractMethodMustBeImplemented &&
                isITDName(categorizedProblem, unit, model, isJavaFileInAJEditor)) {
            // a type implements an interface with an ITD method on it
            return false;
        }

      // this one is very tricky and rare.
      // there is a abstract method ITD defined on a supertype
      // since this type was altered using AspectConvertingParser,
      // the implementation of this abstract method is not necessarily there
      return id != IProblem.AbstractMethodMustBeImplemented ||
             (hasModel && !isAbstractITD(categorizedProblem, model, unit, isJavaFileInAJEditor));
    }

    /**
     * checks that the selection of the categorizedProblem is 'this'
     * @param categorizedProblem
     * @param unit
     * @return
     */
    private static boolean isThisExpression(
            CategorizedProblem p, CompilationUnit unit) {
        char[] contents = unit.getContents();
        return p.getSourceEnd() < contents.length && p.getSourceEnd() - p.getSourceStart() == 3 &&
                contents[p.getSourceStart()] == 't' && contents[p.getSourceStart()+1] == 'h' && contents[p.getSourceStart()+2] == 'i' && contents[p.getSourceStart()+3] == 's';
    }

    /**
     * Check to see if the name is a translated advice name
     * In bug 318132, before and after advice names have
     * been translated to 'b' and 'a' respectively
     * @param firstArg
     * @param secondArg
     * @return
     */
    private static boolean isTranslatedAdviceName(String firstArg, String secondArg) {
        return firstArg.equals("a") || firstArg.equals("b") || secondArg.equals("a") || secondArg.equals("b");
    }

    /**
     * Checks if the type name specified by the current problem is equal to
     * the typename passed in.  Note that the type name passed in may be fully qualified,
     * but the type name specified by the current problem will not be.  So,
     * only compare by qualified names.
     */
    private static boolean typeAtPositionIsArg(
            CategorizedProblem categorizedProblem, CompilationUnit unit,
            String typeName) throws JavaModelException {
        String typeNameAtPosition = getTypeNameAtPosition(categorizedProblem, unit);
        String lastSegment = findLastSegment(typeName);
        return typeNameAtPosition.equals(lastSegment);
    }

    /**
     * @param firstArg
     * @return
     */
    private static String findLastSegment(String firstArg) {
        String[] splits = firstArg.split("\\.");
      return splits[splits.length - 1];
    }

    private static boolean simpleNamesEquals(String firstArg, String secondArg) {
        if (firstArg.equals(secondArg)) {
            return true;
        }

        String[] firstArgSplit = firstArg.split("\\.");
        String[] secondArgSplit = secondArg.split("\\.");

        String newFirst = firstArgSplit[firstArgSplit.length-1];
        String newSecond = secondArgSplit[secondArgSplit.length-1];

        return newFirst.equals(newSecond);
    }

    /*
     * Returns the name of the aspect at the problem position
     */
    private static String getTypeNameAtPosition(
            CategorizedProblem categorizedProblem, CompilationUnit unit) throws JavaModelException {
        IJavaElement elt = unit.getElementAt(categorizedProblem.getSourceStart());
        IType type = elt != null ? (IType) elt.getAncestor(IJavaElement.TYPE) : null;
        if (type == null) {
            // just return the name of the CU
            int dotIndex = unit.getElementName().indexOf('.');
            if (dotIndex > 0) {
                return unit.getElementName().substring(0, dotIndex);
            } else {
                return unit.getElementName();
            }
        }
        return type.getElementName();
    }

    private static boolean insideAdvice(CategorizedProblem categorizedProblem,
            CompilationUnit unit) throws JavaModelException {
        IJavaElement candidate = unit.getElementAt(categorizedProblem.getSourceStart());
        while (candidate != null && !(candidate instanceof ICompilationUnit)) {
            if (candidate instanceof AdviceElement) {
                return true;
            }
            candidate = candidate.getParent();
        }
        return false;
    }

    private static boolean insidePointcut(CategorizedProblem categorizedProblem,
            CompilationUnit unit) throws JavaModelException {
        IJavaElement candidate = unit.getElementAt(categorizedProblem.getSourceStart());
        while (candidate != null && !(candidate instanceof ICompilationUnit)) {
            if (candidate instanceof PointcutElement) {
                return true;
            }
            candidate = candidate.getParent();
        }
        return false;
    }


    private static boolean isITDName(CategorizedProblem problem, CompilationUnit unit, AJProjectModelFacade model, boolean isJavaFileInAJEditor) {
        if (isJavaFileInAJEditor) {
            // we don't know...be safe and
            // let compiler do the errors
            return true;
        }
        Set<String> itdNames = getITDNames(unit, model);
        String[] args = problem.getArguments();
        if (args != null) {
          for (String arg : args) {
            String[] split = arg.split("\\.");
            String name = split.length > 1 ? split[split.length - 1] : arg;
            if (itdNames.contains(name)) {
              return true;
            }
          }
        }
        return false;
    }

    private static boolean isSoftened(CategorizedProblem problem, CompilationUnit unit, AJProjectModelFacade model,
            boolean isJavaFileInAJEditor) throws JavaModelException {
        if (isJavaFileInAJEditor) {
            // we don't know...be safe and
            // let compiler do the errors
            return true;
        }
        IJavaElement elt = unit.getElementAt(problem.getSourceStart());
        List<IJavaElement> softens = model.getRelationshipsForElement(elt, AJRelationshipManager.SOFTENED_BY, true);
        return softens.size() > 0;
    }

    // would be good if we can cache this value somehow.
    // expensive to compute
    private static Set <String> getITDNames(CompilationUnit unit, AJProjectModelFacade model) {
        Set<String> names = new HashSet<>();
        Map<Integer, List<IRelationship>> relsMap = model.getRelationshipsForFile(unit, new AJRelationshipType[] { AJRelationshipManager.DECLARED_ON, AJRelationshipManager.ASPECT_DECLARATIONS } );
      for (List<IRelationship> rels : relsMap.values()) {
        for (IRelationship rel : rels) {
          IProgramElement[] ipes;
          if (rel.getName().equals(AJRelationshipManager.DECLARED_ON.getDisplayName())) {
            ipes = new IProgramElement[1];
            ipes[0] = model.getProgramElement(rel.getSourceHandle());
          }
          else {
            List<String> targets = rel.getTargets();
            ipes = new IProgramElement[targets.size()];
            for (int i = 0; i < ipes.length; i++) {
              ipes[i] = model.getProgramElement(targets.get(i));
            }
          }
          for (IProgramElement ipe : ipes) {
            String longName = ipe.getName();
            String[] splits = longName.split("\\.");
            // ignore constructors
            if (splits.length > 1 && splits[splits.length - 1].equals(splits[splits.length - 2])) {
              continue;
            }
            names.add(splits[splits.length - 1]);
          }
        }
      }
        return names;
    }

    private static boolean isAbstractITD(CategorizedProblem categorizedProblem,
            AJProjectModelFacade model, CompilationUnit unit, boolean isJavaFileInAJEditor) {
        if (isJavaFileInAJEditor) {
            // we don't know...be safe and
            // let compiler do the errors
            return true;
        }

        // first arg is the method name
        // then come the method params
        // then the implementing type
        // finally, the this type
        String[] args = categorizedProblem.getArguments();
        if (args.length < 3) {
            return false;
        }

        String methodName = args[0];
//        String[] methodParams = new String[args.length-3];
        String implementingTypeName = args[args.length-2];
//        String thisType = args[args.length-1];

        try {
            IType type = unit.getJavaProject().findType(implementingTypeName);
            if (type == null) {
                return false;
            }

            List<IJavaElement> itds = model.getRelationshipsForElement(type, AJRelationshipManager.ASPECT_DECLARATIONS);
          for (Object itd : itds) {
            AspectJMemberElement ajElt = (AspectJMemberElement) itd;
            if (ajElt.getElementName().endsWith("." + methodName)) {
              return true;  // close enough...can also compare args and type variables
            }
          }
        } catch (JavaModelException ignored) {
        }


        return false;
    }

    /* AJDT 1.7 */
    private static boolean isAspect(CategorizedProblem problem, CompilationUnit unit, boolean isJavaFileInAJEditor) {
        if (isJavaFileInAJEditor) {
            // we don't know...be safe and
            // let compiler do the errors
            return true;
        }

        if (unit instanceof AJCompilationUnit) {
            try {
                IJavaElement elt = unit.getElementAt(problem.getSourceStart());
                if (elt != null) {
	                IType type = (IType) elt.getAncestor(IJavaElement.TYPE);
    	            return type != null && type instanceof AspectElement;
    	        }
            } catch (JavaModelException ignored) {
            }
        }
        return false;
    }


    private static boolean isPrivilegedAspect(CategorizedProblem problem, CompilationUnit unit, boolean isJavaFileInAJEditor) {
        if (isJavaFileInAJEditor) {
            // we don't know...be safe and
            // let compiler do the errors
            return true;
        }

        if (unit instanceof AJCompilationUnit) {
            try {
                IJavaElement elt = unit.getElementAt(problem.getSourceStart());
                if (elt != null) {
                    IType type = (IType) elt.getAncestor(IJavaElement.TYPE);
                    if (type != null && type instanceof AspectElement) {
                        AspectElement aspectType = (AspectElement) type;
                        return aspectType.isPrivileged();
                    }
                }
            } catch (JavaModelException ignored) {
            }
        }
        return false;
    }

    private static boolean insideITD(CategorizedProblem categorizedProblem,
            CompilationUnit unit, boolean isJavaFileInAJEditor) throws JavaModelException {
        if (isJavaFileInAJEditor) {
            // we don't know...be safe and
            // let compiler do the errors
            return true;
        }
        IJavaElement elementAt = unit.getElementAt(categorizedProblem.getSourceStart());
        return elementAt instanceof IntertypeElement ||
               elementAt instanceof DeclareElement;
    }

    private static String getITDTargetType(CategorizedProblem categorizedProblem,
            CompilationUnit unit, boolean isJavaFileInAJEditor) throws JavaModelException {
        if (isJavaFileInAJEditor) {
            // we don't know...be safe and
            // let compiler do the errors
            return "";
        }
        IJavaElement elementAt = unit.getElementAt(categorizedProblem.getSourceStart());
        if (elementAt instanceof IntertypeElement) {
            IntertypeElement itd = (IntertypeElement) elementAt;
            return new String(itd.getTargetType());
        }
        return "";
    }

    private static String extractProblemRegion(
            CategorizedProblem categorizedProblem, CompilationUnit unit) {
        char[] contents = unit.getContents();
        StringBuilder sb = new StringBuilder();
        for (int i = categorizedProblem.getSourceStart();
                i < categorizedProblem.getSourceEnd()+1 && i < contents.length; i++) {
            sb.append(contents[i]);
        }
        return sb.toString();
    }

    private static String extractNextJavaIdentifier(CompilationUnit unit, int start) {
        char[] contents = unit.getContents();
        StringBuilder sb = new StringBuilder();
        int next = start;
        while (! Character.isJavaIdentifierStart(contents[next]) &&
                next < contents.length) {
            next++;
        }
        while (Character.isJavaIdentifierPart(contents[next]) &&
                next < contents.length) {
            sb.append(contents[next++]);
        }
        return sb.toString();
    }

    static final Set<String> aspectMemberNames = new HashSet<>();
    static {
        aspectMemberNames.add("parents");
        aspectMemberNames.add("declare");
        aspectMemberNames.add("after");
        aspectMemberNames.add("around");
        aspectMemberNames.add("before");
        aspectMemberNames.add("soft");
        aspectMemberNames.add("error");
        aspectMemberNames.add("warning");
        aspectMemberNames.add("pointcut");
        aspectMemberNames.add("implements");
        aspectMemberNames.add("extends");
        aspectMemberNames.add("privileged");
    }

    static final Set<String> adviceBodyNames = new HashSet<>();
    static {
        adviceBodyNames.add("proceed");
        adviceBodyNames.add("thisJoinPoint");
        adviceBodyNames.add("thisJoinPointStaticPart");
        adviceBodyNames.add("thisEnclosingJoinPointStaticPart");
    }

    static final Set<String> extraAspectMethods = new HashSet<>();
    static {
        extraAspectMethods.add("hasAspect");
        extraAspectMethods.add("aspectOf");
        extraAspectMethods.add("getWithinTypeName");
    }

    static final Set<String> declareAnnotationKinds = new HashSet<>();
    static {
        declareAnnotationKinds.add("$constructor");
        declareAnnotationKinds.add("$field");
        declareAnnotationKinds.add("$method");
        declareAnnotationKinds.add("$type");
    }
}


class NullRequestor extends AJCompilationUnitStructureRequestor implements ISourceElementRequestor, IAspectSourceElementRequestor {

    public NullRequestor() {
        super(null, null, null);
    }

    @Override
    public void acceptImport(int declarationStart, int declarationEnd,
            char[] name, boolean onDemand, int modifiers) {
    }

    @Override
    public void enterMethod(
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo mi,
            AbstractMethodDeclaration mdecl) {
    }

    @Override
    protected void enterType(int declarationStart, int modifiers, char[] name,
            int nameSourceStart, int nameSourceEnd, char[] superclass,
            char[][] superinterfaces, org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] tpInfo,
            boolean isAspect, boolean isPrivilegedAspect) {
    }

    @Override
    public void enterType(org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo, boolean isAspect,
            boolean isPrivilegedAspect) { }

    @Override
    public void setParser(Parser parser) { }

    @Override
    public void setSource(char[] source) { }

    @Override
    protected Annotation createAnnotation(JavaElement parent, String name) {
        return null;
    }

    @Override
    protected ImportContainer createImportContainer(ICompilationUnit parent) {
        return null;
    }

    @Override
    protected ImportDeclaration createImportDeclaration(ImportContainer parent,
            String name, boolean onDemand) {
        return null;
    }

    @Override
    protected Initializer createInitializer(JavaElement parent) {
        return null;
    }

    @Override
    protected PackageDeclaration createPackageDeclaration(JavaElement parent,
            String name) {
        return null;
    }

    @Override
    protected TypeParameter createTypeParameter(JavaElement parent, String name) {
        return null;
    }

    @Override
    protected Object getMemberValue(MemberValuePair memberValuePair,
            Expression expression) {
        return null;
    }

    @Override
    protected IMemberValuePair getMemberValuePair(
            org.eclipse.jdt.internal.compiler.ast.MemberValuePair memberValuePair) {
        return null;
    }

    @Override
    protected IMemberValuePair[] getMemberValuePairs(
            org.eclipse.jdt.internal.compiler.ast.MemberValuePair[] memberValuePairs) {
        return null;
    }

    @Override
    protected void resolveDuplicates(SourceRefElement handle) {
    }

    @Override
    public void acceptAnnotationTypeReference(char[] annotation,
            int sourcePosition) { }

    @Override
    public void acceptAnnotationTypeReference(char[][] annotation,
            int sourceStart, int sourceEnd) { }

    @Override
    public void acceptConstructorReference(char[] typeName, int argCount,
            int sourcePosition) { }

    @Override
    public void acceptFieldReference(char[] fieldName, int sourcePosition) { }

    @Override
    public void acceptImport(int declarationStart, int declarationEnd,
            char[][] tokens, boolean onDemand, int modifiers) { }

    @Override
    public void acceptLineSeparatorPositions(int[] positions) { }

    @Override
    public void acceptMethodReference(char[] methodName, int argCount,
            int sourcePosition) { }

    @Override
    public void acceptPackage(ImportReference importReference) { }

    @Override
    public void acceptProblem(CategorizedProblem problem) { }

    @Override
    public void acceptTypeReference(char[] typeName, int sourcePosition) { }

    @Override
    public void acceptTypeReference(char[][] typeName, int sourceStart,
            int sourceEnd) { }

    @Override
    public void acceptUnknownReference(char[] name, int sourcePosition) { }

    @Override
    public void acceptUnknownReference(char[][] name, int sourceStart,
            int sourceEnd) { }

    @Override
    public void enterInitializer(int declarationStart, int modifiers) { }

    @Override
    public void enterType(org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo) { }

    @Override
    public void exitConstructor(int declarationEnd) { }

    @Override
    public void exitField(int initializationStart, int declarationEnd,
            int declarationSourceEnd) { }

    @Override
    public void exitInitializer(int declarationEnd) { }

    @Override
    public void exitMethod(int declarationEnd, Expression defaultValue) { }

    @Override
    public void exitType(int declarationEnd) { }

    @Override
    public void enterType(org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo) { }

    @Override
    public void acceptPackage(org.aspectj.org.eclipse.jdt.internal.compiler.ast.ImportReference ir) { }

    @Override
    public void enterAdvice(int declarationStart, int modifiers,
            char[] returnType, char[] name, int nameSourceStart,
            int nameSourceEnd, char[][] parameterTypes,
            char[][] parameterNames, char[][] exceptionTypes,
            AdviceDeclaration decl) { }

    @Override
    public void enterDeclare(int declarationStart, int modifiers,
            char[] returnType, char[] name, int nameSourceStart,
            int nameSourceEnd, char[][] parameterTypes,
            char[][] parameterNames, char[][] exceptionTypes,
            DeclareDeclaration decl) { }

    @Override
    public void enterInterTypeDeclaration(int declarationStart, int modifiers,
            char[] returnType, char[] name, int nameSourceStart,
            int nameSourceEnd, char[][] parameterTypes,
            char[][] parameterNames, char[][] exceptionTypes,
            InterTypeDeclaration decl) { }

    @Override
    public void enterMethod(
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo,
            AbstractMethodDeclaration decl) { }

    @Override
    public void enterPointcut(int declarationStart, int modifiers,
            char[] returnType, char[] name, int nameSourceStart,
            int nameSourceEnd, char[][] parameterTypes,
            char[][] parameterNames, char[][] exceptionTypes,
            PointcutDeclaration decl) { }

    @Override
    public void enterType(
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo,
            boolean isAspect, boolean isPrivilegedAspect) { }

    @Override
    public void acceptPackage(int declarationStart, int declarationEnd,
            char[] name) { }

    @Override
    public void acceptProblem(
            org.aspectj.org.eclipse.jdt.core.compiler.CategorizedProblem problem) { }

    @Override
    public void enterConstructor(
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo) { }

    @Override
    public void enterField(
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.FieldInfo fieldInfo) { }

    @Override
    public void enterMethod(
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo) { }

    @Override
    public void enterConstructor(
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo) { }

    @Override
    public void enterField(org.eclipse.jdt.internal.compiler.ISourceElementRequestor.FieldInfo fieldInfo) { }

    @Override
    public void enterMethod(
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo) { }

    @Override
    protected SourceField createField(JavaElement parent, org.eclipse.jdt.internal.compiler.ISourceElementRequestor.FieldInfo fieldInfo) {
        return null;
    }

    @Override
    public void enterMethod(int declarationStart, int modifiers,
            char[] returnType, char[] name, int nameSourceStart,
            int nameSourceEnd, char[][] parameterTypes,
            char[][] parameterNames, char[][] exceptionTypes,
            boolean isConstructor, boolean isAnnotation,
            org.aspectj.org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo[] typeParameters,
            AbstractMethodDeclaration methodDeclaration) {
    }

    @Override
    public void acceptImport(int declarationStart, int declarationEnd,
            int nameSourceStart, int nameSourceEnd, char[][] tokens,
            boolean onDemand, int modifiers) {
    }

    @Override
    protected SourceMethod createMethodHandle(JavaElement parent,
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.MethodInfo methodInfo) {
        return super.createMethodHandle(parent, methodInfo);
    }

    @Override
    protected SourceType createTypeHandle(JavaElement parent, org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeInfo typeInfo) {
        return super.createTypeHandle(parent, typeInfo);
    }

    @Override
    protected IAnnotation acceptAnnotation(
            org.eclipse.jdt.internal.compiler.ast.Annotation annotation,
            AnnotatableInfo parentInfo, JavaElement parentHandle) {
        return null;
    }

    @Override
    protected void acceptTypeParameter(
            org.eclipse.jdt.internal.compiler.ISourceElementRequestor.TypeParameterInfo typeParameterInfo,
            JavaElementInfo parentInfo) {
    }

    @Override
    public void exitCompilationUnit(int declarationEnd) {
    }

    @Override
    public void exitMethod(
            int declarationEnd,
            org.aspectj.org.eclipse.jdt.internal.compiler.ast.Expression defaultValue) {
    }

    @Override
    public void enterCompilationUnit() {
    }
}
