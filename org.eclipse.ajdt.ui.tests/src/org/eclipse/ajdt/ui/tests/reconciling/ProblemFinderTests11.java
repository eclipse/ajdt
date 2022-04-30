/*******************************************************************************
 * Copyright (c) 2008 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Andrew Eisenberg - Initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.reconciling;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;

/**
 * Tests AJCompilationUnitProblemFinder and ITDAwareness
 *
 * Tests bug 265557
 *
 * @author andrew
 *
 */
public class ProblemFinderTests11 extends AJDTCoreTestCase {
    List<ICompilationUnit> allCUnits = new ArrayList<>();
    ICompilationUnit errorUnit;
    IProject proj;
    protected void setUp() throws Exception {
        super.setUp();
        setAutobuilding(false);
        proj = createPredefinedProject("Bug265557DeclareSoft"); //$NON-NLS-1$
        proj.build(IncrementalProjectBuilder.FULL_BUILD, null);

        IFolder src = proj.getFolder("src");

        IResourceVisitor visitor = resource -> {
            if (resource.getType() == IResource.FILE &&
                    (resource.getName().endsWith("java") ||
                            resource.getName().endsWith("aj"))) {
                if (resource.getName().equals("ClassWithException2.java")) {
                    errorUnit = createUnit((IFile) resource);
                } else {
                    allCUnits.add(createUnit((IFile) resource));
                }
            }
            return true;
        };
        src.accept(visitor);
        proj.build(IncrementalProjectBuilder.FULL_BUILD, null);

        joinBackgroudActivities();
    }

    private ICompilationUnit createUnit(IFile file) {
        return (ICompilationUnit) AspectJCore.create(file);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        setAutobuilding(true);
    }

    /**
     * Should have one error in this of an unsoftened exception
     */
    public void testProblemFindingErrors() throws Exception {

        HashMap problems = doFind(errorUnit);
        assertEquals("Should have found 1 problem, but instead found " + problems.size() +
                "\n" + MockProblemRequestor.printProblems(problems),
                1, problems.size());

    }

    public void testProblemFindingAll() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (ICompilationUnit element : allCUnits) {
            sb.append(problemFind(element));
        }
        if (sb.length() > 0) {
            fail(sb.toString());
        }
    }

    private String problemFind(ICompilationUnit unit) throws Exception {
        HashMap problems = doFind(unit);
        MockProblemRequestor.filterAllWarningProblems(problems);
        if (MockProblemRequestor.countProblems(problems) > 0) {
            return "Should not have any problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems) + "\n"; //$NON-NLS-1$
        } else {
            return "";
        }
    }
    private HashMap doFind(ICompilationUnit unit)
            throws JavaModelException {
        HashMap problems = new HashMap();
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
