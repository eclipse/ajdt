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
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;

/**
 * Tests AJCompilationUnitProblemFinder
 * <p>
 * Ensures that when there are errors in a comp unit, there
 * no spurious errors in the editor
 * <p>
 * Tests bug 246393
 *
 * @author andrew
 */
public class ProblemFinderTests8 extends AJDTCoreTestCase {
  private final List<ICompilationUnit> allCUnits = new ArrayList<>();

  private IProject proj;

  protected void setUp() throws Exception {
    super.setUp();
    proj = createPredefinedProject("AJProblemsBug246393"); //$NON-NLS-1$
    joinBackgroudActivities();

    allCUnits.add(createUnit("src/ajfiles/AnAspect.aj"));
    allCUnits.add(createUnit("src/ajfiles/C2.aj"));
    allCUnits.add(createUnit("src/ajfiles/Concrete.aj"));
    allCUnits.add(createUnit("src/ajfiles/Interface.aj"));
    allCUnits.add(createUnit("src/javafiles/AnAspect.aj"));
    allCUnits.add(createUnit("src/javafiles/C2.java"));
    allCUnits.add(createUnit("src/javafiles/Concrete.java"));
    allCUnits.add(createUnit("src/javafiles/Interface.java"));

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
    problemFind1Error(allCUnits.get(1));
  }

  public void testProblemFinding2() throws Exception {
    problemFind(allCUnits.get(2));
  }

  public void testProblemFinding3() throws Exception {
    problemFind(allCUnits.get(3));
  }

  public void testProblemFinding4() throws Exception {
    problemFind(allCUnits.get(4));
  }

  public void testProblemFinding5() throws Exception {
    problemFind1Error(allCUnits.get(5));
  }

  public void testProblemFinding6() throws Exception {
    problemFind(allCUnits.get(6));
  }

  public void testProblemFinding7() throws Exception {
    problemFind(allCUnits.get(7));
  }

  private void problemFind1Error(ICompilationUnit unit) throws Exception {
    Map<String, CategorizedProblem[]> problems = doFind(unit);
    MockProblemRequestor.filterAllWarningProblems(problems);
    assertEquals("Should one have one problem in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems), 1, MockProblemRequestor.countProblems(problems)); //$NON-NLS-1$

  }

  private void problemFind(ICompilationUnit unit) throws Exception {
    Map<String, CategorizedProblem[]> problems = doFind(unit);
    MockProblemRequestor.filterAllWarningProblems(problems);
    assertEquals("Should not have any problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems), 0, MockProblemRequestor.countProblems(problems)); //$NON-NLS-1$
  }

  private Map<String, CategorizedProblem[]> doFind(ICompilationUnit unit)
    throws JavaModelException
  {
    HashMap<String, CategorizedProblem[]> problems = new HashMap<>();
    if (unit instanceof AJCompilationUnit) {
      AJCompilationUnitProblemFinder.processAJ(
        (AJCompilationUnit) unit,
        AJWorkingCopyOwner.INSTANCE,
        problems,
        true,
        ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION,
        null
      );
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
    return problems;
  }

}
