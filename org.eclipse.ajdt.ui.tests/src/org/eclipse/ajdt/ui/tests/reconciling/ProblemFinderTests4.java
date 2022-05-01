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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitProblemFinder;
import org.eclipse.ajdt.internal.core.AJWorkingCopyOwner;
import org.eclipse.ajdt.internal.ui.editor.AspectJEditor;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;

/**
 * Tests AJCompilationUnitProblemFinder
 * <p>
 * Tests bug 256989
 *
 * @author andrew
 */
public class ProblemFinderTests4 extends UITestCase {
  private AJCompilationUnit inAspectFileCU;
  private CompilationUnit inJavaFileCU;
  private AJCompilationUnit inSwitchFileCU;
  private AJCompilationUnit inAspectPacakgeCU;
  IFile inJavaFile;
  private IProject proj;

  protected void setUp() throws Exception {
    IFile inAspectFile;
    IFile inSwitchFile;
    IFile inAspectPackageFile;

    super.setUp();
    proj = createPredefinedProject("bug256989"); //$NON-NLS-1$
    waitForJobsToComplete();
    setAutobuilding(false);
    inAspectFile = proj.getFile("src/none/AspectWithThis.aj"); //$NON-NLS-1$
    inAspectFileCU = new AJCompilationUnit(inAspectFile);

    inJavaFile = proj.getFile("src/none/AspectWithThisJava.java"); //$NON-NLS-1$
    inJavaFileCU = (CompilationUnit) JavaCore.create(inJavaFile);

    inSwitchFile = proj.getFile("src/none/AspectWithSwitch.aj"); //$NON-NLS-1$
    inSwitchFileCU = new AJCompilationUnit(inSwitchFile);

    inAspectPackageFile = proj.getFile("src/bug265977/aspect/Bug265977.aj"); //$NON-NLS-1$
    inAspectPacakgeCU = new AJCompilationUnit(inAspectPackageFile);
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    setAutobuilding(true);
  }

  public void testAspectFile() throws Exception {
    Map<String, CategorizedProblem[]> problems = new HashMap<>();
    AJCompilationUnitProblemFinder.processAJ(
      inAspectFileCU,
      AJWorkingCopyOwner.INSTANCE,
      problems,
      true,
      ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION,
      null
    );

    MockProblemRequestor.filterAllWarningProblems(problems);
    assertEquals("Should not have any problems in " + inAspectFileCU + " but found:\n" + MockProblemRequestor.printProblems(problems), 0, problems.size()); //$NON-NLS-1$
  }

  // Requires JDT Weaving
  public void testJavaFile() throws Exception {

    // Open in AJ Editor in order to force code transformation
    final IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
    IFileEditorInput input = new FileEditorInput(inJavaFile);
    IDE.openEditor(page,
      input, AspectJEditor.ASPECTJ_EDITOR_ID);

    HashMap<String, CategorizedProblem[]> problems = new HashMap<>();
    CompilationUnitProblemFinder.process(
      inJavaFileCU,
      AJWorkingCopyOwner.INSTANCE,
      problems,
      true,
      ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION,
      null
    );

    MockProblemRequestor.filterAllWarningProblems(problems);
    assertEquals("Should not have any problems in " + inJavaFileCU + " but found:\n" + MockProblemRequestor.printProblems(problems), 0, problems.size()); //$NON-NLS-1$
  }

  // tests for switch statements, bug 258685
  public void testSwitchStatement() throws Exception {
    Map<String, CategorizedProblem[]> problems = new HashMap<>();
    AJCompilationUnitProblemFinder.processAJ(
      inSwitchFileCU,
      AJWorkingCopyOwner.INSTANCE,
      problems,
      true,
      ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION,
      null
    );

    MockProblemRequestor.filterAllWarningProblems(problems);
    assertEquals("Should not have any problems in " + inSwitchFileCU + " but found:\n" + MockProblemRequestor.printProblems(problems), 0, problems.size()); //$NON-NLS-1$
  }

  // tests for aspect in package declaration, bug 265977
  public void testAspectInPackageDeclaration() throws Exception {
    Map<String, CategorizedProblem[]> problems = new HashMap<>();
    AJCompilationUnitProblemFinder.processAJ(
      inAspectPacakgeCU,
      AJWorkingCopyOwner.INSTANCE,
      problems,
      true,
      ICompilationUnit.ENABLE_BINDINGS_RECOVERY | ICompilationUnit.ENABLE_STATEMENTS_RECOVERY | ICompilationUnit.FORCE_PROBLEM_DETECTION,
      null
    );

    MockProblemRequestor.filterAllWarningProblems(problems);
    assertEquals("Should not have any problems in " + inAspectPacakgeCU + " but found:\n" + MockProblemRequestor.printProblems(problems), 0, problems.size()); //$NON-NLS-1$
  }

}
