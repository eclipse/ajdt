/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Andrew Eisenberg - Initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.problemfinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitProblemFinder;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.internal.core.AJWorkingCopyOwner;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;

/**
 * Tests AJCompilationUnitProblemFinder and ITDAwareness
 *
 * Tests that No arg constructors can be ITD-ed into
 * classes that use the default constructor.  See bug 280385
 *
 * @author andrew
 *
 */
public class ProblemFinderTests14 extends AJDTCoreTestCase {
    List<ICompilationUnit> allCUnits = new ArrayList<>();
    IProject proj;
    protected void setUp() throws Exception {
        super.setUp();
        proj = createPredefinedProject("Bug280385NoArgConstructors"); //$NON-NLS-1$
        joinBackgroudActivities();

        IFolder src = proj.getFolder("src");

        IResourceVisitor visitor = resource -> {
            if (resource.getType() == IResource.FILE &&
                    (resource.getName().endsWith("java") ||
                            resource.getName().endsWith("aj"))) {
                allCUnits.add(createUnit((IFile) resource));
            }
            return true;
        };
        src.accept(visitor);

        joinBackgroudActivities();
        setAutobuilding(false);

    }

    private ICompilationUnit createUnit(IFile file) {
        return (ICompilationUnit) AspectJCore.create(file);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        setAutobuilding(true);
    }

    public void testProblemFindingAll() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (ICompilationUnit allCUnit : allCUnits) {
            sb.append(problemFind(allCUnit));
        }
        if (sb.length() > 0) {
            fail(sb.toString());
        }
    }

    private String problemFind(ICompilationUnit unit) throws Exception {
        Map<String, CategorizedProblem[]> problems = new HashMap<>();
        MockProblemRequestor.filterAllWarningProblems(problems);
        if (MockProblemRequestor.countProblems(problems) > 0) {
            return "Should not have any problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems) + "\n"; //$NON-NLS-1$
        } else {
            return "";
        }
    }
    private Map<String, CategorizedProblem[]> doFind(ICompilationUnit unit) throws JavaModelException {
        // CompilationUnitProblemFinder.process explicitly declares a HashMap parameter, so we need to accomodate that
        HashMap<String, CategorizedProblem[]> problems = new HashMap<>();
        if (unit instanceof AJCompilationUnit) {
            AJCompilationUnitProblemFinder.processAJ((AJCompilationUnit) unit,
                    AJWorkingCopyOwner.INSTANCE, problems, true,
                    ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION, null);
        } else {
            // Requires JDT Weaving
            CompilationUnitProblemFinder.process((CompilationUnit) unit, null,
                    DefaultWorkingCopyOwner.PRIMARY, problems, true,
                    ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION, null);
        }
        return problems;
    }

}
