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
package org.eclipse.contribution.jdt.debug;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.debug.core.IJavaBreakpoint;
import org.eclipse.jdt.debug.core.IJavaBreakpointListener;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaStackFrame;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.eclipse.jdt.debug.eval.IEvaluationListener;
import org.eclipse.jdt.internal.debug.core.breakpoints.ConditionalBreakpointHandler;

import com.sun.jdi.Location;

/**
 * @author Andrew Eisenberg
 * @created Oct 28, 2010
 * Provides hooks for debug support
 */
public interface IDebugProvider {


    /**
     * Perform an evaluation in the debug provider
     * @param snippet code snippet to evaluate
     * @param object 'this' object
     * @param frame current stack frame
     * @param listener the listener that requests the evaluation results
     * @param javaProject
     */
    void performEvaluation(String snippet, IJavaObject object,
            IJavaStackFrame frame, IEvaluationListener listener, IJavaProject javaProject,
            int evaluationDetail, boolean hitBreakpoints);

    /**
     * @param frame current stack fram where evaluation is occurring
     * @return true iff this stack frame should be evaluated by the debug provider
     * rather than the regular evaluator
     */
    boolean shouldPerformEvaluation(IJavaStackFrame frame);

    /**
     * @param location current debug location
     * @return true iff the debug provider determines that an extra step into should be performed
     */
    boolean shouldPerformExtraStep(Location location);


    /**
     * @return the extra step filters to be added to any step request
     * @return
     */
    String[] augmentStepFilters(String[] origStepFilters);

    /**
     * This method is executed when a conditional breakpoint is hit on
     * an interesting stack frame.  The DebugProvider will evaluate the condition
     * in the context of the target language.
     *
     * @param thread The current thread
     * @param breakpoint The breakpoint that is hit.
     * @param handler the original {@link ConditionalBreakpointHandler}
     * @return {@link IJavaBreakpointListener#SUSPEND} if the condition evaluates to true or there is a problem
     * or {@link IJavaBreakpointListener#DONT_SUSPEND} if the condition evaluates to false.
     */
    int conditionalBreakpointHit(IJavaThread thread, IJavaBreakpoint breakpoint, ConditionalBreakpointHandler handler);

    /**
     * @return true iff the standard way of looking for interesting launches should be overridden
     * and all launches should take advantage of extended debugging support.
     */
    boolean isAlwaysInteretingLaunch();
}
