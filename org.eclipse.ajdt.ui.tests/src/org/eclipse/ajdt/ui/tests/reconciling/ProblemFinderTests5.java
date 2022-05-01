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
import java.util.Map;

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitProblemFinder;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.internal.core.AJWorkingCopyOwner;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;

/**
 * Tests AJCompilationUnitProblemFinder
 * <p>
 * Focuses on ITD implementations of interface methods and fields
 * <p>
 * Tests bug 256989
 *
 * @author andrew
 */
public class ProblemFinderTests5 extends AJDTCoreTestCase {
  private final List<ICompilationUnit> allCUnits = new ArrayList<>();

  private IProject proj;

  protected void setUp() throws Exception {
    super.setUp();
    setAutobuilding(false);
    proj = createPredefinedProject("ITDOnInterfaces"); //$NON-NLS-1$
    proj.build(IncrementalProjectBuilder.FULL_BUILD, null);

    allCUnits.add(createUnit("src/p/AClass.java"));
    allCUnits.add(createUnit("src/p/AnAspect.aj"));
    allCUnits.add(createUnit("src/p/AnInterface.java"));
    allCUnits.add(createUnit("src/p/ASubInterface.java"));
    allCUnits.add(createUnit("src/p/ASubInterface2.java"));
    allCUnits.add(createUnit("src/p/ASubInterface2.java"));
    allCUnits.add(createUnit("src/q/AClass.java"));

    joinBackgroudActivities();
  }

  private ICompilationUnit createUnit(String fName) {
    return (ICompilationUnit) AspectJCore.create(proj.getFile(fName));
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    setAutobuilding(true);
  }

  public void testProblemFinding0() throws Exception {
    problemFind((ICompilationUnit) allCUnits.get(0));
  }

  public void testProblemFinding1() throws Exception {
    problemFind((ICompilationUnit) allCUnits.get(1));
  }

  public void testProblemFinding2() throws Exception {
    problemFind((ICompilationUnit) allCUnits.get(2));
  }

  public void testProblemFinding3() throws Exception {
    problemFind((ICompilationUnit) allCUnits.get(3));
  }

  public void testProblemFinding4() throws Exception {
    problemFind((ICompilationUnit) allCUnits.get(4));
  }

  public void testProblemFinding5() throws Exception {
    problemFind((ICompilationUnit) allCUnits.get(5));
  }

  public void testProblemFinding6() throws Exception {
    problemFind((ICompilationUnit) allCUnits.get(6));
  }

  private void problemFind(ICompilationUnit unit) throws Exception {
    HashMap<String, CategorizedProblem[]> problems = new HashMap<>();

    if (unit instanceof AJCompilationUnit) {
      AJCompilationUnitProblemFinder.processAJ(
        (AJCompilationUnit) unit,
        AJWorkingCopyOwner.INSTANCE,
        problems,
        true,
        ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION,
        null)
      ;
    }
    else {
      // Requires JDT Weaving
      CompilationUnitProblemFinder.process(
        (CompilationUnit) unit,
        null,
        DefaultWorkingCopyOwner.PRIMARY,
        problems,
        true,
        ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION,
        null
      );
    }
    MockProblemRequestor.filterAllWarningProblems(problems);
    assertEquals("Should not have any problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems), 0, MockProblemRequestor.countProblems(problems)); //$NON-NLS-1$
  }
}
