/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January - copied to AJDT
 *******************************************************************************/
package org.eclipse.ajdt.core.reconcile;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitProblemFinder;
import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SafeRunner;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.CompilationParticipant;
import org.eclipse.jdt.core.compiler.ReconcileContext;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaElementDelta;
import org.eclipse.jdt.internal.core.JavaElementDeltaBuilder;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.JavaModelOperation;
import org.eclipse.jdt.internal.core.JavaModelStatus;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.ReconcileWorkingCopyOperation;
import org.eclipse.jdt.internal.core.util.Messages;
import org.eclipse.jdt.internal.core.util.Util;

/**
 * Mostly copied from ReconcileWorkingCopyOperation in order to use the
 * AJCompilationUnitProblemFinder.  Changes marked with "// AspectJ Change".
 */
public class AJReconcileWorkingCopyOperation extends
		ReconcileWorkingCopyOperation {


    public static boolean PERF = false;

    public int astLevel;
    public boolean resolveBindings;
    public HashMap<String, CategorizedProblem[]> problems;
    public int reconcileFlags;
    WorkingCopyOwner workingCopyOwner;
    public org.eclipse.jdt.core.dom.CompilationUnit ast;
    public JavaElementDeltaBuilder deltaBuilder;
    public boolean requestorIsActive;


	/**
	 * @param workingCopy
	 * @param creatAST
	 * @param astLevel
	 * @param forceProblemDetection
	 * @param workingCopyOwner
	 */
	public AJReconcileWorkingCopyOperation(IJavaElement workingCopy,
			int astLevel, int reconcileFlags,
			WorkingCopyOwner workingCopyOwner) {
		super(workingCopy, astLevel, reconcileFlags, workingCopyOwner);
		this.astLevel = astLevel;
		this.workingCopyOwner = workingCopyOwner;
		this.reconcileFlags = reconcileFlags;
	}

	/**
	 * @exception JavaModelException if setting the source
	 * 	of the original compilation unit fails
	 */
	protected void executeOperation() throws JavaModelException {
	    checkCanceled();


	    try {
	        beginTask(Messages.element_reconciling, 2);

	        CompilationUnit workingCopy = getWorkingCopy();
	        boolean wasConsistent = workingCopy.isConsistent();

	        // check is problem requestor is active
            IProblemRequestor problemRequestor = workingCopy.getPerWorkingCopyInfo();
            if (problemRequestor != null)
                problemRequestor =  ((JavaModelManager.PerWorkingCopyInfo)problemRequestor).getProblemRequestor();
            boolean defaultRequestorIsActive = problemRequestor != null && problemRequestor.isActive();
            IProblemRequestor ownerProblemRequestor = this.workingCopyOwner.getProblemRequestor(workingCopy);
            boolean ownerRequestorIsActive = ownerProblemRequestor != null && ownerProblemRequestor != problemRequestor && ownerProblemRequestor.isActive();
            this.requestorIsActive = defaultRequestorIsActive || ownerRequestorIsActive;

            // create the delta builder (this remembers the current content of the cu)
            this.deltaBuilder = new JavaElementDeltaBuilder(workingCopy);

            // make working copy consistent if needed and compute AST if needed
            makeConsistent(workingCopy);

            // notify reconcile participants only if working copy was not consistent or if forcing problem detection
            // (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=177319)
            if (!wasConsistent || ((this.reconcileFlags & ICompilationUnit.FORCE_PROBLEM_DETECTION) != 0)) {
                notifyParticipants(workingCopy);

                // recreate ast if one participant reset it
                if (this.ast == null)
                    makeConsistent(workingCopy);
            }

            // report problems
            if (this.problems != null && (((this.reconcileFlags & ICompilationUnit.FORCE_PROBLEM_DETECTION) != 0) || !wasConsistent)) {
                if (defaultRequestorIsActive) {
                    reportProblems(workingCopy, problemRequestor);
                }
                if (ownerRequestorIsActive) {
                    reportProblems(workingCopy, ownerProblemRequestor);
                }
            }

            // register the deltas
            // AspectJ Change Begin
            JavaElementDelta delta = getDelta(deltaBuilder);
            if (delta != null) {
                addReconcileDelta(workingCopy, delta);
            }
            // AspectJ Change End
        } finally {
            done();
        }
	}

    // AspectJ Change Begin
	// nasty hack: the delta field of JavaElementDeltaBuilder is not visible
	// as we're in a different package, so we must access it via reflection
	private JavaElementDelta getDelta(JavaElementDeltaBuilder deltaBuilder) {
		Field deltaField;
		try {
			deltaField = JavaElementDeltaBuilder.class.getDeclaredField("delta"); //$NON-NLS-1$
			deltaField.setAccessible(true);
			Object o = deltaField.get(deltaBuilder);
			return (JavaElementDelta)o;
		} catch (SecurityException | IllegalAccessException | IllegalArgumentException | NoSuchFieldException ignored) {
		}
    return null;
	}
    // AspectJ Change End


	   /**
     * Report working copy problems to a given requestor.
     *
     * @param workingCopy
     * @param problemRequestor
     */
    private void reportProblems(CompilationUnit workingCopy, IProblemRequestor problemRequestor) {
        try {
            problemRequestor.beginReporting();
          for (CategorizedProblem[] categorizedProblems : this.problems.values()) {
            if (categorizedProblems == null)
              continue;
            for (CategorizedProblem problem : categorizedProblems) {
              if (JavaModelManager.VERBOSE) {
                AJLog.log(AJLog.PARSER, "PROBLEM FOUND while reconciling : " + //$NON-NLS-1$
                                        problem.getMessage());
              }
              if (this.progressMonitor != null && this.progressMonitor.isCanceled())
                break;
              problemRequestor.acceptProblem(problem);
            }
          }
        } finally {
            problemRequestor.endReporting();
        }
    }



	/**
	 * Returns the working copy this operation is working on.
	 */
	protected CompilationUnit getWorkingCopy() {
		return (CompilationUnit)getElementToProcess();
	}
	/**
	 * @see JavaModelOperation#isReadOnly
	 */
	public boolean isReadOnly() {
		return true;
	}



	/*
     * Makes the given working copy consistent, computes the delta and computes an AST if needed.
     * Returns the AST.
     */
    public org.eclipse.jdt.core.dom.CompilationUnit makeConsistent(CompilationUnit workingCopy) throws JavaModelException {
        if (!workingCopy.isConsistent()) {
            // make working copy consistent
            if (this.problems == null) this.problems = new HashMap<>();
            this.resolveBindings = this.requestorIsActive;
            this.ast = workingCopy.makeConsistent(this.astLevel, this.resolveBindings, reconcileFlags, this.problems, this.progressMonitor);
            this.deltaBuilder.buildDeltas();
            if (this.ast != null && this.deltaBuilder.delta != null)
                this.deltaBuilder.delta.changedAST(this.ast);
            return this.ast;
        }
        if (this.ast != null)
            return this.ast; // no need to recompute AST if known already

        CompilationUnitDeclaration unit = null;
        try {
            JavaModelManager.getJavaModelManager().abortOnMissingSource.set(Boolean.TRUE);
            CompilationUnit source ;
            if (workingCopy instanceof AJCompilationUnit) {
                source = ((AJCompilationUnit) workingCopy).ajCloneCachingContents();
            } else {
                source = workingCopy.cloneCachingContents();
            }
            // find problems if needed
            if (JavaProject.hasJavaNature(workingCopy.getJavaProject().getProject())
                    && (this.reconcileFlags & ICompilationUnit.FORCE_PROBLEM_DETECTION) != 0) {
                this.resolveBindings = this.requestorIsActive;
                if (this.problems == null)
                    this.problems = new HashMap<>();
                unit =
                    AJCompilationUnitProblemFinder.processAJ(
                        source,
                        this.workingCopyOwner,
                        this.problems,
                        this.astLevel != ICompilationUnit.NO_AST/*creating AST if level is not NO_AST */,
                        reconcileFlags,
                        this.progressMonitor);
                if (this.progressMonitor != null) this.progressMonitor.worked(1);
            }

            // create AST if needed
            if (this.astLevel != ICompilationUnit.NO_AST
                    && unit !=null/*unit is null if working copy is consistent && (problem detection not forced || non-Java project) -> don't create AST as per API*/) {
                Map<String, String> options = workingCopy.getJavaProject().getOptions(true);
                // convert AST
                this.ast =
                    AST.convertCompilationUnit(
                        this.astLevel,
                        unit,
                        options,
                        this.resolveBindings,
                        source,
                        reconcileFlags,
                        this.progressMonitor);
                if (this.ast != null) {
                    if (this.deltaBuilder.delta == null) {
                        this.deltaBuilder.delta = new JavaElementDelta(workingCopy);
                    }
                    this.deltaBuilder.delta.changedAST(this.ast);
                }
                if (this.progressMonitor != null) this.progressMonitor.worked(1);
            }
        } catch (JavaModelException e) {
            if (JavaProject.hasJavaNature(workingCopy.getJavaProject().getProject()))
                throw e;
            // else JavaProject has lost its nature (or most likely was closed/deleted) while reconciling -> ignore
            // (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=100919)
        } finally {
            JavaModelManager.getJavaModelManager().abortOnMissingSource.set(null);
            if (unit != null) {
                unit.cleanUp();
            }
        }
        return this.ast;
    }

    private void notifyParticipants(final CompilationUnit workingCopy) {
        IJavaProject javaProject = getWorkingCopy().getJavaProject();
        CompilationParticipant[] participants = JavaModelManager.getJavaModelManager().compilationParticipants.getCompilationParticipants(javaProject);
        if (participants == null) return;

        final ReconcileContext context = new ReconcileContext(this, workingCopy); // AspectJ change
      for (final CompilationParticipant participant : participants) {
        SafeRunner.run(new ISafeRunnable() {
          public void handleException(Throwable exception) {
            if (exception instanceof Error) {
              throw (Error) exception; // errors are not supposed to be caught
            }
            else if (exception instanceof OperationCanceledException)
              throw (OperationCanceledException) exception;
            else if (exception instanceof UnsupportedOperationException) {
              // might want to disable participant as it tried to modify the buffer of the working copy being reconciled
              Util.log(exception, "Reconcile participant attempted to modify the buffer of the working copy being reconciled"); //$NON-NLS-1$
            }
            else
              Util.log(exception, "Exception occurred in reconcile participant"); //$NON-NLS-1$
          }

          public void run() {
            participant.reconcile(context);
          }
        });
      }
    }


	protected IJavaModelStatus verify() {
		IJavaModelStatus status = super.verify();
		if (!status.isOK()) {
			return status;
		}
		CompilationUnit workingCopy = getWorkingCopy();
		if (!workingCopy.isWorkingCopy()) {
			return new JavaModelStatus(IJavaModelStatusConstants.ELEMENT_DOES_NOT_EXIST, workingCopy); //was destroyed
		}
		return status;
	}


}
