/*******************************************************************************
 * Copyright (c) 2009 SpringSourceand others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.refactoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.ajdt.internal.ui.refactoring.PushInRefactoring;
import org.eclipse.ajdt.internal.ui.refactoring.PushInRefactoringAction;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModelMarker;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.PerformRefactoringOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.internal.Workbench;
import org.eclipse.ui.internal.views.log.AbstractEntry;
import org.eclipse.ui.internal.views.log.LogEntry;
import org.eclipse.ui.internal.views.log.LogView;

/**
 *
 * @author andrew
 *
 * Tests pushin refactoring
 *
 * Bug 274608
 *
 */
public class PushinRefactoringTests extends UITestCase {

    // gives us access to the findAllITDs method
    static class MockAction extends PushInRefactoringAction {
        @Override
        protected List<IMember> findAllITDs(IJavaElement[] selection)
                throws JavaModelException {
            return super.findAllITDs(selection);
        }
    }

    IJavaProject pushinJavaProj;
    IProject pushinProj;
    PushInRefactoring refactoring;
    MockAction action;

    protected void setUp() throws Exception {
        super.setUp();
        pushinProj = createPredefinedProject("PushinRefactoringTest");
        pushinJavaProj = JavaCore.create(pushinProj);
        refactoring = new PushInRefactoring();
        action = new MockAction();
    }

    public void testSimpleITDsNoDelete() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin"));
        List<IMember> itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);

        IFile classFile = pushinProj.getFile("src/pushin/OtherClass.java");
        IFile aspectFile = pushinProj.getFile("src/pushin/OtherAspect.aj");
        checkFileForContents(classFile, "void foo(List<Integer> y)");
        checkFileForNoContents(aspectFile, "void OtherClass.foo(List<Integer> y)");
    }

    public void testITDsWillDelete() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin2"));
        List<IMember> itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);
        lookForAJFiles(frag.getResource());
        IFile classFile = pushinProj.getFile("src/pushin2/B.java");
        checkFileForContents(classFile, "int x = 9;");
        checkFileForContents(classFile, "int y = 9;");
    }

    public void testDeclareAnnotation() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin3"));
        List<IMember> itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);

        IFile aspectFile = pushinProj.getFile("src/pushin3/A.aj");
        IFile classFile = pushinProj.getFile("src/pushin3/B.java");
        checkFileForContents(classFile, "int y = 9;");
        checkFileForContents(classFile, "@Deprecated");
        checkFileForNoContents(aspectFile, "int B.y = 9;");
        checkFileForNoContents(aspectFile, "@Deprecated");
        checkFileForContents(aspectFile, "int y;");
    }

    public void testDeclareAnnotationBig() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin4"));
        List<IMember> itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);
        IFile aspectFileA = pushinProj.getFile("src/pushin4/A.aj");
        IFile classFileB = pushinProj.getFile("src/pushin4/B.java");
        IFile classFileC = pushinProj.getFile("src/pushin4/C.java");
        IFile aspectFileD = pushinProj.getFile("src/pushin4/D.aj");
        IFile aspectFileE = pushinProj.getFile("src/pushin4/E.aj");

        checkFileForContents(aspectFileA, "int y;");
        checkFileForNoContents(aspectFileA, "int B.y = 9;");
        checkFileForNoContents(aspectFileA, "int C.y = 9;");
        checkFileForNoContents(aspectFileA, "@Deprecated");

        checkFileForContents(classFileB, "int y = 9;");
        checkFileForContents(classFileB, "int z = 9;");
        checkFileForContents(classFileB, "@Deprecated");

        checkFileForContents(classFileC, "int y = 9;");
        checkFileForContents(classFileC, "int z = 9;");

        checkFileForContents(aspectFileD, "int y;");
        checkFileForNoContents(aspectFileD, "int B.z = 9;");
        checkFileForNoContents(aspectFileD, "int C.z = 9;");
        checkFileForNoContents(aspectFileD, "@Deprecated");

        lookForAJFiles(aspectFileE);
    }

    public void testDeclareParents() throws Exception {
        IPackageFragment frag = (IPackageFragment) JavaCore.create(pushinProj.getFolder("src/pushin5"));
        List<IMember> itds = action.findAllITDs(
                new IJavaElement[] { frag });
        doRefactoringAndInitialCheck(pushinJavaProj, itds);

        IFile aClass = pushinProj.getFile("src/pushin5/AClass.java");
        IFile aClassWithExtends = pushinProj.getFile("src/pushin5/AClassWithExtends.java");
        IFile aClassWithImplements = pushinProj.getFile("src/pushin5/AClassWithImplements.java");
        IFile aClassWithImplementsAndExtends = pushinProj.getFile("src/pushin5/AClassWithImplementsAndExtends.java");
        IFile anInterface = pushinProj.getFile("src/pushin5/AnInterface.java");
        IFile anInterfaceWithExtends = pushinProj.getFile("src/pushin5/AnInterfaceWithExtends.java");
        IFile errorNoImport = pushinProj.getFile("src/pushin5/ErrorNoImport.java");
        IFile noErrorFullyQualified = pushinProj.getFile("src/pushin5/NoErrorFullyQualified.java");

        IFile anAspect = pushinProj.getFile("src/pushin5/AnAspect.aj");

        checkFileForContents(aClass, "I");
        checkFileForContents(aClass, "J");
        checkFileForContents(aClass, "H");

        checkFileForContents(aClassWithExtends, "I");
        checkFileForContents(aClassWithExtends, "J");

        checkFileForContents(aClassWithImplements, "I");
        checkFileForContents(aClassWithImplements, "J");

        checkFileForContents(aClassWithImplementsAndExtends, "I");
        checkFileForContents(aClassWithImplementsAndExtends, "J");

        checkFileForContents(anInterface, "I");
        checkFileForContents(anInterface, "J");

        checkFileForContents(anInterfaceWithExtends, "I");
        checkFileForContents(anInterfaceWithExtends, "J");

        checkFileForContents(errorNoImport, "OtherClass");

        checkFileForContents(noErrorFullyQualified, "Serializable");

        checkFileForNoContents(anAspect, "AClass");
        checkFileForNoContents(anAspect, "AnInterface");
        checkFileForNoContents(anAspect, "NoErrorFullyQualified");
        checkFileForNoContents(anAspect, "ErrorNoImport");
    }

    /// Failing on build server.
    public void _testPetClinic() throws Exception {
        // test that the test clinic project works with push in

        IJavaProject petClinic = JavaCore.create(createPredefinedProject("petclinic2"));
        List<IMember> itds = action.findAllITDs(new IJavaElement[] { petClinic });
        doRefactoringAndInitialCheck(petClinic, itds);

        // no aspect files left over
        lookForAJFiles(petClinic.getProject());
    }

    // Test Bug 283657 when an ITD method and ITD field have the same
    // name, push in refactoring should still work
    public void testBug283657() throws Exception {
        IJavaProject refactoringProj = JavaCore.create(createPredefinedProject("Bug283657Refactoring"));
        List<IMember> itds = action.findAllITDs(new IJavaElement[] { refactoringProj });
        assertEquals("Should have found 2 ITDs in project", 2, itds.size());
        doRefactoringAndInitialCheck(refactoringProj, itds);

        IType type = refactoringProj.findType("Target");
        assertTrue("Should have found method 'foo'", type.getMethod("foo", new String[0]).exists());
        assertTrue("Should have found field 'foo'", type.getField("foo").exists());
    }

    // Test that generic types can have ITDs pushed in.
    public void testBug321065() throws Exception {
        IJavaProject refactoringProj = JavaCore.create(createPredefinedProject("DefaultEmptyProject"));
        ICompilationUnit aspectUnit = createCompilationUnitAndPackage("p", "A.aj", "package p;\n aspect A {\nint C<T>.x;\npublic void I<T>.x() { } }", refactoringProj);
        ICompilationUnit classUnit = createCompilationUnitAndPackage("p", "C.java", "package p;\n class C<T> { }", refactoringProj);
        ICompilationUnit interfaceUnit = createCompilationUnitAndPackage("p", "I.java", "package p;\n interface I<T> { }", refactoringProj);
        ICompilationUnit subUnit = createCompilationUnitAndPackage("p", "S.java", "package p;\n class S<P> implements I<P> { }", refactoringProj);

        assertNoProblems(refactoringProj.getProject());
        List<IMember> itds = action.findAllITDs(new IJavaElement[] { refactoringProj });
        assertEquals("Should have found 2 ITDs in project", 2, itds.size());
        doRefactoringAndInitialCheck(refactoringProj, itds);
        assertNoProblems(refactoringProj.getProject());

        assertFalse(aspectUnit.getElementName() + " should have been deleted", aspectUnit.exists());
        checkFileForContents(classUnit, "int x;");
        checkFileForContents(interfaceUnit, "public void x();");
        checkFileForContents(subUnit, "public void x() { }");
    }

    private void lookForAJFiles(IResource resource) throws CoreException {
        if (resource.getType() == IResource.FILE) {
            if (resource.getFileExtension().equals("aj")) {
                assertFalse("Should not have any aj files left after refactoring, but found: " +
                                resource.getFullPath(), resource.exists());
            }
            return;
        }

        IResourceVisitor visitor = resource1 -> {
            if (resource1.getType() == IResource.FILE) {
                if (resource1.getFileExtension().equals("aj")) {
                    fail("Should not have any aj files left after refactoring, but found: " +
                         resource1.getFullPath());
                }
            }
            return false;
        };
        resource.accept(visitor);
    }

    private void doRefactoringAndInitialCheck(IJavaProject jProject, List<IMember> itds)
            throws PartInitException, Exception, CoreException {
        refactoring.setITDs(itds);
        LogView logView = (LogView) Workbench.getInstance().getActiveWorkbenchWindow().getActivePage().getActivePart().getSite().getPage().showView("org.eclipse.pde.runtime.LogView"); //$NON-NLS-1$
        AbstractEntry[] logs = logView.getElements();
        int numErrors = logs.length;

        // do the refactoring
        try {
            executeRefactoring(refactoring);
        } catch (CoreException e) {
            if (e.getStatus().getCode() == IResourceStatus.OUT_OF_SYNC_LOCAL) {
                pushinJavaProj.getProject().refreshLocal(IResource.DEPTH_INFINITE, null);
                executeRefactoring(refactoring);
            }
        }

        // no errors on log
        logs = logView.getElements();
        if (numErrors != logs.length) {
            StringBuilder sb = new StringBuilder();
            for (int i = numErrors; i < logs.length; i++) {
                LogEntry entry = (LogEntry) logs[i];
                sb.append(entry.getMessage()).append("\n").append(entry.getStack()).append("\n");
            }
            fail("Should not have any extra log entries after refactoring petclinic:\n" + sb);
        }

        // no compile errors
        IMarker[] problems = jProject.getProject().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE);
        if (problems.length > 0) {
            StringBuilder sb = new StringBuilder();
          for (IMarker marker : problems) {
            String msg = (String) marker.getAttribute(IMarker.MESSAGE);

            // ignore missing imports
            // ignore duplicate resource warnings
            if ((Integer) marker.getAttribute(IMarker.SEVERITY) >= IMarker.SEVERITY_WARNING
                && !msg.contains("import")
                && !msg.contains("duplicate resource:"))
            {
              sb.append(marker.getResource().getFullPath()).append(": ");
              sb.append(msg).append("\n");
            }
          }
            if (sb.length() > 0) {
                fail("Should not have any compile errors after pushin refactoring:\n" + sb);
            }
        }
    }

    private void checkFileForContents(ICompilationUnit unit, String toCheck) throws Exception {
        checkFileForContents((IFile) unit.getResource(), toCheck);
    }

    private void checkFileForContents(IFile file, String toCheck) throws Exception {
        String contents = getContents(file);
        int index = contents.indexOf(toCheck);
        if (index == -1) {
            fail("Should have found '" + toCheck + "' in '" + file.getFullPath() + "'");
        }
    }

    private void checkFileForNoContents(IFile file, String toCheck) throws Exception {
        String contents = getContents(file);
        int index = contents.indexOf(toCheck);
        if (index != -1) {
            fail("Should not have found '" + toCheck + "' in '" + file.getFullPath() + "'");
        }
    }

    private String getContents(IFile file) throws CoreException, IOException {
      try (
          InputStream is = file.getContents();
          BufferedReader br = new BufferedReader(new InputStreamReader(is))
      ) {
          StringBuilder buffer = new StringBuilder();
          char[] readBuffer = new char[2048];
          int n = br.read(readBuffer);
          while (n > 0) {
              buffer.append(readBuffer, 0, n);
              n = br.read(readBuffer);
          }
          return buffer.toString();
      }
    }

    // borrowed from JDT Refactoring tests
    protected void executeRefactoring(Refactoring refactoring) throws Exception {
        PerformRefactoringOperation operation= new PerformRefactoringOperation(refactoring, CheckConditionsOperation.ALL_CONDITIONS);
        waitForJobsToComplete();
        performDummySearch(JavaModelManager.getJavaModelManager().getJavaModel());
        // Flush the undo manager to not count any already existing undo objects
        // into the heap consumption
        RefactoringCore.getUndoManager().flush();
        System.gc();
        pushinJavaProj.getProject().getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
        try {
            ResourcesPlugin.getWorkspace().run(operation, null);
        } catch (CoreException ce) {
            if (ce.getStatus().getCode() == IResourceStatus.OUT_OF_SYNC_LOCAL) {
                // try again
                pushinJavaProj.getProject().getWorkspace().getRoot().refreshLocal(IResource.DEPTH_INFINITE, null);
                ResourcesPlugin.getWorkspace().run(operation, null);
            }
        }
        waitForJobsToComplete();
        assertEquals("Expecting OK status, but found " + operation.getConditionStatus(),  RefactoringStatus.OK, operation.getConditionStatus().getSeverity());
        assertTrue("Expecting OK status, but found " + operation.getValidationStatus(), operation.getValidationStatus().isOK());
        RefactoringCore.getUndoManager().flush();
        System.gc();
    }
}
