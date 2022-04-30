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
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;

/**
 * Tests AJCompilationUnitProblemFinder
 *
 * Privileged aspects should not show errors on otherwise inaccessible member references
 *
 * Tests bug 262404
 * @author andrew
 *
 */
public class ProblemFinderTests7 extends AJDTCoreTestCase {
    private final List<ICompilationUnit> allCUnits = new ArrayList<>();


    private IProject proj;
    protected void setUp() throws Exception {
        super.setUp();
        proj = createPredefinedProject("PrivilegedAspectReconciling"); //$NON-NLS-1$
        joinBackgroudActivities();

        allCUnits.add(createUnit("src/HasPrivateMembers.java"));
        allCUnits.add(createUnit("src/IsPrivileged.aj"));
        allCUnits.add(createUnit("src/IsPrivilegedWithError.aj"));

        joinBackgroudActivities();
        setAutobuilding(false);

    }
    private ICompilationUnit createUnit(String fName) {
        return (ICompilationUnit) AspectJCore.create(proj.getFile(fName));
    }
    protected void tearDown() throws Exception {
        super.tearDown();
        setAutobuilding(true);
    }

    public void testProblemFinding0() throws Exception {
        problemFind(allCUnits.get(0));
    }

    public void testProblemFinding1() throws Exception {
        problemFind(allCUnits.get(1));
    }

    public void testProblemFinding2() throws Exception {
        problemFind2Errors((AJCompilationUnit) allCUnits.get(2));
    }

    private void problemFind2Errors(AJCompilationUnit unit) throws Exception {
        Map<String, CategorizedProblem[]> problems = new HashMap<>();
        AJCompilationUnitProblemFinder.processAJ(unit,
                AJWorkingCopyOwner.INSTANCE, problems, true,
                ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION, null);


        MockProblemRequestor.filterAllWarningProblems(problems);
        assertEquals("Should have two problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems), 2, MockProblemRequestor.countProblems(problems)); //$NON-NLS-1$
    }

    private void problemFind(ICompilationUnit unit) throws Exception {
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

        MockProblemRequestor.filterAllWarningProblems(problems);
        assertEquals("Should not have any problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems), 0, MockProblemRequestor.countProblems(problems)); //$NON-NLS-1$
    }

}
