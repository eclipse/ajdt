/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.contribution.weaving.jdt.tests.itdawareness;

import java.util.HashMap;

import org.eclipse.contribution.jdt.itdawareness.INameEnvironmentProvider;
import org.eclipse.contribution.jdt.preferences.WeavableProjectListener;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.internal.compiler.SourceElementParser;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.env.ISourceType;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.SearchableEnvironment;

/**
 * @author Andrew Eisenberg
 * @created Jan 30, 2009
 *
 */
public class MockNameEnvironmentProvider implements INameEnvironmentProvider {

    boolean problemFindingDone = false;
    boolean shouldFindProblemsDone = false;
    boolean transformSourceTypeInfoDone = false;

    public SearchableEnvironment getNameEnvironment(JavaProject project,
            WorkingCopyOwner owner) {
        return null;
    }

    public SearchableEnvironment getNameEnvironment(JavaProject project,
            ICompilationUnit[] workingCopies) {
        return null;
    }

    public CompilationUnitDeclaration problemFind(CompilationUnit unitElement,
            SourceElementParser parer, WorkingCopyOwner workingCopyOwner,
            HashMap problems, boolean creatingAST, int reconcileFlags,
            IProgressMonitor monitor) throws JavaModelException {
        problemFindingDone = true;
        return null;
    }

    public boolean shouldFindProblems(CompilationUnit unitElement) {
        if (WeavableProjectListener.getInstance().isWeavableProject(unitElement.getJavaProject().getProject())) {
            shouldFindProblemsDone = true;
            return true;
        } else {
            return false;
        }
    }

    public ISourceType transformSourceTypeInfo(ISourceType info) {
        transformSourceTypeInfoDone = true;
        return info;
    }

}
