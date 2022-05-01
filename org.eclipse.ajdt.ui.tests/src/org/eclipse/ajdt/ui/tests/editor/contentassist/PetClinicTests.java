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
package org.eclipse.ajdt.ui.tests.editor.contentassist;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.aspectj.asm.IProgramElement.Accessibility;
import org.aspectj.asm.internal.ProgramElement;
import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.model.AJModelChecker;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.parserbridge.AJCompilationUnitProblemFinder;
import org.eclipse.ajdt.internal.core.AJWorkingCopyOwner;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.ajdt.ui.tests.reconciling.MockProblemRequestor;
import org.eclipse.ajdt.ui.tests.testutils.TestLogger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.internal.core.CompilationUnit;
import org.eclipse.jdt.internal.core.CompilationUnitProblemFinder;
import org.eclipse.jdt.internal.core.DefaultWorkingCopyOwner;

/**
 * Tests the Pet Clinic project
 *
 * @author Andrew Eisenberg
 */
public class PetClinicTests extends UITestCase {

  IProject petClinicProject;
  List<ICompilationUnit> allCUnits = new ArrayList<>();
  ICompilationUnit ownerUnit;
  String ownerUnitContents;
  ICompilationUnit entityAspect;
  String entityAspectContents;
  ICompilationUnit ownerController;
  String ownerControllerContents;

  protected void setUp() throws Exception {
    super.setUp();
    petClinicProject = createPredefinedProject("petclinic2");

    IFolder src = petClinicProject.getFolder("src");

    IResourceVisitor visitor = resource -> {
      if (resource.getType() == IResource.FILE &&
          (resource.getName().endsWith("java") ||
           resource.getName().endsWith("aj")))
      {
        ICompilationUnit unit = createUnit((IFile) resource);
        unit.becomeWorkingCopy(null);
        allCUnits.add(unit);
        if (unit.getElementName().equals("Owner.java")) {
          ownerUnit = unit;
          ownerUnitContents = new String(((CompilationUnit) ownerUnit).getContents());
        }
        if (unit.getElementName().equals("AbstractPerson_Roo_Entity_Itd.aj")) {
          entityAspect = unit;
          entityAspectContents = new String(((CompilationUnit) entityAspect).getContents());
        }
        if (unit.getElementName().equals("OwnerController.java")) {
          ownerController = unit;
          ownerControllerContents = new String(((CompilationUnit) ownerController).getContents());
        }
      }
      return true;
    };
    src.accept(visitor);

    waitForJobsToComplete();
    setAutobuilding(false);

  }

  protected void tearDown() throws Exception {
    super.tearDown();
    setAutobuilding(true);
  }

  private ICompilationUnit createUnit(IFile file) {
    return (ICompilationUnit) AspectJCore.create(file);
  }

  public void testModelCheck() {
    TestLogger logger = new TestLogger();
    AspectJPlugin.getDefault().setAJLogger(logger);
    AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForProject(petClinicProject);
    AJModelChecker.doModelCheckIfRequired(model);
    boolean success = logger.containsMessage("Crosscutting model sanity checked with no problems");
    if (!success) {
      fail("Model check for petclinic failed\n" + logger.getLogMessages());
    }

  }

  public void testContentAssist() throws Exception {
    MockCompletionRequestor requestor = new MockCompletionRequestor();
    // check that itds are inserted
    int offset = ownerUnitContents.indexOf("this.getAddres") + "this.getAddres".length();
    ownerUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

    assertEquals("Should have 1 proposal, but found:\n" + requestor, 1, requestor.accepted.size());

    CompletionProposal completionProposal = requestor.accepted.get(0);
    assertEquals("Signature of proposal should have been the 'Owner.getAddress()' method\n" + completionProposal,
      "getAddress", new String(completionProposal.getName()));
    assertEquals("Completion start is wrong", offset - "getAddres".length(), completionProposal.getReplaceStart());
  }

  /**
   * Ensure that no files have no errors or warnings
   */
  public void testReconciling() throws Exception {
    StringBuilder sb = new StringBuilder();
    for (ICompilationUnit allCUnit : allCUnits)
      sb.append(problemFind(allCUnit));
    if (sb.length() > 0)
      fail(sb.toString());
  }

  /**
   * Tests that ITDs that are not visible in the current
   * scope do not appear as content assist proposals
   */
  public void testPrivateContentAssistShouldAppear() throws Exception {
    MockCompletionRequestor requestor = new MockCompletionRequestor();
    // check that itds are inserted
    int offset = entityAspectContents.indexOf("this.") + "this.".length();
    entityAspect.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

    // should see the id proposal.  It is private, but declared in this aspect
    CompletionProposal idProposal = null;
    for (CompletionProposal proposal : requestor.accepted) {
      if (new String(proposal.getName()).equals("id")) {
        idProposal = proposal;
      }
    }
    assertNotNull("Should have found the 'id' proposal because it is private, but declared in this aspect", idProposal);
    assertSame("Proposal should be marked as Package Protected", ProgramElement.genAccessibility(idProposal.getFlags()), Accessibility.PRIVATE);
  }

  public void testPrivateContentAssistShouldNotAppear() throws Exception {
    MockCompletionRequestor requestor = new MockCompletionRequestor();
    // check that itds are inserted
    int offset = ownerUnitContents.indexOf("this.") + "this.".length();
    ownerUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

    // should *not* see the *id* proposal.  It is private in an ITD
    for (CompletionProposal proposal : requestor.accepted) {
      if (new String(proposal.getName()).equals("id")) {
        fail("Should not have found the 'id' completion proposal.  It is from an ITD that is declared private.");
      }
    }
  }

  public void testPackageProtectedContentAssistShouldAppear() throws Exception {
    MockCompletionRequestor requestor = new MockCompletionRequestor();
    // check that itds are inserted
    int offset = ownerUnitContents.indexOf("this.") + "this.".length();
    ownerUnit.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

    // should see the entityManager proposal.  It is package protected in an ITD in the same package
    CompletionProposal entityManagerProposal = null;
    for (CompletionProposal proposal : requestor.accepted) {
      if (new String(proposal.getName()).equals("entityManager")) {
        entityManagerProposal = proposal;
      }
    }
    assertNotNull("Should have found the 'entityManager' proposal because it is package protected", entityManagerProposal);
    assertSame("Proposal should be marked as Package Protected", ProgramElement.genAccessibility(entityManagerProposal.getFlags()), Accessibility.PACKAGE);
  }

  public void testPackageProtectedContentAssistShouldNotAppear() throws Exception {
    MockCompletionRequestor requestor = new MockCompletionRequestor();
    // check that itds are inserted
    int offset = ownerControllerContents.indexOf("new Owner().") + "new Owner().".length();
    ownerController.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

    // should *not* see the *id* proposal.  It is private in an ITD
    for (CompletionProposal proposal : requestor.accepted) {
      if (new String(proposal.getName()).equals("entityManager")) {
        fail("Should not have found the 'entityManager' completion proposal.  It is from an ITD that is declared pcakage protected.");
      }
    }
  }

  public void testPublicContentAssistShouldAppear() throws Exception {
    MockCompletionRequestor requestor = new MockCompletionRequestor();
    // check that itds are inserted
    int offset = ownerControllerContents.indexOf("new Owner().") + "new Owner().".length();
    ownerController.codeComplete(offset, requestor, AJWorkingCopyOwner.INSTANCE);

    // should see the entityManager proposal.  It is package protected in an ITD in the same package
    CompletionProposal getAddressProposal = null;
    for (CompletionProposal proposal : requestor.accepted) {
      if (new String(proposal.getName()).equals("getAddress")) {
        getAddressProposal = proposal;
      }
    }
    assertNotNull("Should have found the 'getAddress' proposal because it is public", getAddressProposal);
    assertSame("Proposal should be marked as Public", ProgramElement.genAccessibility(getAddressProposal.getFlags()), Accessibility.PUBLIC);
  }

  private String problemFind(ICompilationUnit unit) throws Exception {
    Map<String, CategorizedProblem[]> problems = doFind(unit);
    if (MockProblemRequestor.countProblems(problems) > 0)
      return "Should not have any problems in " + unit + " but found:\n" + MockProblemRequestor.printProblems(problems) + "\n"; //$NON-NLS-1$
    else
      return "";
  }

  private HashMap<String, CategorizedProblem[]> doFind(ICompilationUnit unit) throws JavaModelException {
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
