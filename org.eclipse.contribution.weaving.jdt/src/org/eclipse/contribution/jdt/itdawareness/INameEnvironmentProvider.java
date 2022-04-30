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
package org.eclipse.contribution.jdt.itdawareness;

import java.util.HashMap;

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
 * This class should only be implemented from within the AJDT framework
 * @author andrew
 * @created Nov 22, 2008
 *
 * @noimplement
 *
 */
public interface INameEnvironmentProvider {
    SearchableEnvironment getNameEnvironment(JavaProject project,
            WorkingCopyOwner owner);

    SearchableEnvironment getNameEnvironment(JavaProject project,
            ICompilationUnit[] workingCopies);

    ISourceType transformSourceTypeInfo(ISourceType info);

    CompilationUnitDeclaration problemFind(
            CompilationUnit unitElement,
            SourceElementParser parer,
            WorkingCopyOwner workingCopyOwner,
            HashMap problems,
            boolean creatingAST,
            int reconcileFlags,
            IProgressMonitor monitor) throws JavaModelException;

    boolean shouldFindProblems(CompilationUnit unitElement);

}
