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
import org.eclipse.ajdt.internal.core.AJWorkingCopyOwner;
import org.eclipse.ajdt.ui.tests.UITestCase;
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
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.views.log.AbstractEntry;
import org.eclipse.ui.internal.views.log.LogEntry;
import org.eclipse.ui.internal.views.log.LogView;

/**
 * Tests AJCompilationUnitProblemFinder and ITDAwareness
 * <p>
 * Various issues found during proofreading AspectJ In Action 2 Edition
 *
 * @author andrew
 */
public class ProblemFinderTests10 extends UITestCase {
  List<ICompilationUnit> allCUnits = new ArrayList<>();
  ICompilationUnit errorUnit;
  IProject proj;

  protected void setUp() throws Exception {
    super.setUp();
    proj = createPredefinedProject("AJIA 2nd Edition"); //$NON-NLS-1$
    waitForJobsToComplete();

    IFolder src = proj.getFolder("src");

    IResourceVisitor visitor = resource -> {
      if (resource.getType() == IResource.FILE &&
          (resource.getName().endsWith("java") ||
           resource.getName().endsWith("aj")))
      {
        if (resource.getName().equals("ErrorClass.aj")) {
          errorUnit = createUnit((IFile) resource);
        }
        else {
          allCUnits.add(createUnit((IFile) resource));
        }
      }
      return true;
    };
    src.accept(visitor);

    waitForJobsToComplete();
    setAutobuilding(false);

  }

  private ICompilationUnit createUnit(IFile file) {
    return (ICompilationUnit) AspectJCore.create(file);
  }

  protected void tearDown() throws Exception {
    super.tearDown();
    setAutobuilding(true);
  }

  /**
   * ensure no exceptions are thrown when processing this odd file
   */
  public void testProblemFindingErrors() throws Exception {
    IViewPart view = Workbench.getInstance().getActiveWorkbenchWindow()
      .getActivePage().getActivePart().getSite().getPage().showView(
        "org.eclipse.pde.runtime.LogView"); //$NON-NLS-1$
    if (view instanceof LogView) {
      LogView logView = (LogView) view;
      int logCount = logView.getElements().length;

      doFind(errorUnit);

      int newLogCount = logView.getElements().length;
      if (newLogCount != logCount) {
        StringBuilder sb = new StringBuilder();
        AbstractEntry[] entries = logView.getElements();
        for (int i = logCount; i < entries.length; i++) {
          AbstractEntry entry = entries[i];
          if (entry instanceof LogEntry) {
            LogEntry logEntry = (LogEntry) entry;
            sb.append(logEntry.getMessage());
          }
          else {
            sb.append("\n\t").append(entry);
          }
          fail("Should not have thrown any exceptions while problem finding.  Instead found:" + sb);
        }
      }
    }
    else {
      fail("Could not find error log view");
    }

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
    Map<String, CategorizedProblem[]> problems = doFind(unit);
    MockProblemRequestor.filterAllWarningProblems(problems);
    if (MockProblemRequestor.countProblems(problems) > 0) {
      return "Should not have any problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems) + "\n"; //$NON-NLS-1$
    }
    else {
      return "";
    }
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
