/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Steve Young - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.visualiser;

import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.contribution.visualiser.core.ProviderManager;
import org.eclipse.contribution.visualiser.jdtImpl.JDTMember;
import org.eclipse.contribution.visualiser.interfaces.IMember;
import org.eclipse.contribution.visualiser.interfaces.IGroup;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;

/**
 * Test the content provider used by the AJDT the Visualiser.
 *
 * While this test class drives most of the important methods of <code>AJDTContentProvider</code>,
 * it also depends on the workbench infrastructure to inialise classes correctly. So, rather
 * than creating an instance of <code>AJDTContentProvider</code> directly, it was necessary to
 * activate the Visualiser perspective via the workbench, then retrieve the active instance
 * of <code>AJDTContentProvider</code> from the <code>ProviderManager</code> class.
 *
 * The following methods have been excluded from testing, as no meaningful test could be
 * performed:
 *
 * {@link org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider#reset()}
 * Sets internal variables to null, thus cleaning state, but accessor methods cannot
 * check this, as they would trigger said internal variables to be re-initialised to
 * non-null values.
 *
 * {@link org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider#processMouseClick()}
 * No discernable chanage of state to measure.
 */
public class AJDTContentProviderTest extends UITestCase {

	private static final String VISUALISER_PERSPECTIVE_ID = "org.eclipse.ajdt.ui.visualiser.AspectVisualizationPerspective"; //$NON-NLS-1$
	private IWorkbench workbench = null;
	private IWorkbenchPage visualiserPerspective = null;
	private AJDTContentProvider ajdtContentProvider = null;
	private final String testProjectName = "Simple AJ Project"; //$NON-NLS-1$
	private IProject testProject = null;
	private IJavaProject javaTestProject = null;

	// NB This count includes the default package (so 3 == p1, p2 and default)
	private static final int PROJECT_PACKAGE_COUNT = 3;
	private static final int PROJECT_MEMBER_COUNT = 4;

	private IPackageFragment defaultPackage = null;
	private static final String DEFAULT_PACKAGE_NAME = "";  // default package //$NON-NLS-1$
	private static final int DEFAULT_PACKAGE_MEMBER_COUNT = 2;

	private IPackageFragment packageOne = null;
	private static final String PACKAGE_ONE_NAME = "p1"; //$NON-NLS-1$
	private static final String PACKAGE_ONE_MEMBER_NAME = "Main"; //$NON-NLS-1$
	private static final int PACKAGE_ONE_MEMBER_COUNT = 1;

	private IPackageFragment packageTwo = null;
	private static final String PACKAGE_TWO_NAME = "p2"; //$NON-NLS-1$
	private static final String PACKAGE_TWO_MEMBER_NAME = "Aspect"; //$NON-NLS-1$
	private static final int PACKAGE_TWO_MEMBER_COUNT = 1;

	// Any valid Java element (used to drive methods expecting one as an arg)
	private IJavaElement someJavaElement = null;


	public AJDTContentProviderTest(String string) {
		super(string);
	}

	/*
	 * Setup the visualiser perspective, test project, packages and members therein
	 * which other tests depend on.
	 *
	 * @see org.eclipse.ajdt.ui.tests.UITestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();

		/*
		 * Show the perspective first, so is gets initialised properly and
		 * we don't get that java.lang.ClassCircularityError:
		 */
		workbench = PlatformUI.getWorkbench();
		try {
			// Check what perspectives are open to start with
			workbench.getActiveWorkbenchWindow();

			// Keep a reference to the opened perspective page so we can close
			// it afterwards
			visualiserPerspective = workbench.showPerspective(
					VISUALISER_PERSPECTIVE_ID, workbench
							.getActiveWorkbenchWindow());
		} catch (WorkbenchException wbe) {
			wbe.printStackTrace();
			fail("WorkbenchException opening perspective"); //$NON-NLS-1$
		}

		// Obtain the instance with which to work
		ajdtContentProvider = (AJDTContentProvider) ProviderManager.getCurrent().getContentProvider();

		/*
		 * Create a project, a package and some members for the various tests to
		 * use. NB This must exist as a valid eclipse project under:
		 *
		 * org.eclipse.ajdt.ui.tests\workspace
		 *
		 * Re-use an existing test project.
		 */
		testProject = createPredefinedProject(testProjectName);
		javaTestProject = JavaCore.create(testProject);

		/*
		 * This is required in order to gurantee that AJDT "knows about all the .aj files"
		 * as some tests depend on this.
		 */
		AJCompilationUnitManager.INSTANCE.initCompilationUnits(AspectJPlugin.getWorkspace());


		// It seems to be protocol to call this method at this point...
		waitForJobsToComplete();

		/*
		 *  "A package fragment is a portion of the workspace corresponding to an entire package,
		 *  or to a portion thereof."
		 */
		IPackageFragmentRoot[] packageFragmentRoots = javaTestProject.getPackageFragmentRoots();
		defaultPackage = packageFragmentRoots[0].getPackageFragment(DEFAULT_PACKAGE_NAME);
		packageOne = packageFragmentRoots[0].getPackageFragment(PACKAGE_ONE_NAME);
		packageTwo = packageFragmentRoots[0].getPackageFragment(PACKAGE_TWO_NAME);

		someJavaElement = packageOne.getChildren()[0];
	}

	protected void tearDown() throws Exception {
		super.tearDown();

		/*
		 * Calling:
		 *
		 *    visualiserPerspective.close()
		 *
		 * results in no perspective being open, thus skuppering subsequent tests (!)
		 *
		 * Use:
		 *
		 *    closePerspective(visualiserPerspective.getPerspective(), false, false);
		 *
		 * the last arg indicating that the "page" should not be closed.
		 *
		 * -spyoung
		 */
		visualiserPerspective.closePerspective(visualiserPerspective.getPerspective(), false, false);
	}

	/**
	 * Detailed test method for
	 * {@link org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)}.
	 */
	public void testSelectionChanged() {
		ajdtContentProvider.selectionChanged(null, new MockStructuredSelection(someJavaElement));
		assertNotNull("currentProject has not been set", ajdtContentProvider.getCurrentProject()); //$NON-NLS-1$

		assertEquals("getCurrentProject() did not return the expect project instance", javaTestProject, //$NON-NLS-1$
				ajdtContentProvider.getCurrentProject());
	}

	/*
	 * Simple mock class used to drive the selectionChanged method.
	 */
	private static class MockStructuredSelection implements IStructuredSelection {

		private Object elementToSelect;

		private MockStructuredSelection(Object elementToSelect){
			this.elementToSelect = elementToSelect;
		}

		// Return a valid Java element from a test project
		public Object getFirstElement() {
			return elementToSelect;
		}

		public Iterator iterator() {
			return null;
		}

		public int size() {
			return 0;
		}

		public Object[] toArray() {
			return null;
		}

		public List toList() {
			return null;
		}

		public boolean isEmpty() {
			return false;
		}
	}

	/**
	 * More detailed test method for (also drives {@link org.eclipse.ajdt.internal.ui.
	 * visualiser.AJDTContentProvider#selectionChanged(org.eclipse.ui.IWorkbenchPart,
	 * org.eclipse.jface.viewers.ISelection)} for non-trivial test cases.
	 * {@link org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider#getAllMembers()}.
	 */
	public void testGetAllMembers() {
		List<IMember> members = ajdtContentProvider.getAllMembers();
		assertNotNull("Members list should not be null", members); //$NON-NLS-1$

		// Select the project - should cause getAllMembers to return all elements in all packages
		ajdtContentProvider.selectionChanged(null, new MockStructuredSelection(javaTestProject));
		List<IMember> projectMembers = ajdtContentProvider.getAllMembers();
		assertEquals("Wrong number of project members", PROJECT_MEMBER_COUNT, projectMembers.size()); //$NON-NLS-1$


		System.out.println("Commented out part of AJDTContentProviderTest.testGetAllMembers() because of sporadic failures on build server.");
		// Select the first package - should cause getAllMembers to return all elements in that packages
		ajdtContentProvider.selectionChanged(null, new MockStructuredSelection(defaultPackage));
		List<IMember> packageMembers = ajdtContentProvider.getAllMembers();
		assertEquals("Wrong number of package members", DEFAULT_PACKAGE_MEMBER_COUNT, packageMembers.size()); //$NON-NLS-1$
	}

	/**
	 * More detailed test method for (also drives {@link org.eclipse.ajdt.internal.ui.
	 * visualiser.AJDTContentProvider#selectionChanged(org.eclipse.ui.IWorkbenchPart,
	 * org.eclipse.jface.viewers.ISelection)} for non-trivial test cases.
	 * {@link org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider#getAllGroups()}.
	 *
	 * NB This method does not appear to be invoked by the workbench over the course of
	 * typical usage. Tests are provided for completeness/coverage.
	 */
	public void testGetAllGroups() {
		List<IGroup> allGroups = ajdtContentProvider.getAllGroups();
		assertNotNull("Groups list should not be null", allGroups); //$NON-NLS-1$

		// Select the project - should cause getAllGroups to return all packages
		ajdtContentProvider.selectionChanged(null, new MockStructuredSelection(javaTestProject));
		List<IGroup> projectGroups = ajdtContentProvider.getAllGroups();
		assertEquals("Wrong number of projects for package", PROJECT_PACKAGE_COUNT, projectGroups.size()); //$NON-NLS-1$
	}

	/**
	 * More detailed test method for
	 * {@link org.eclipse.ajdt.internal.ui.visualiser.AJDTContentProvider#getMembersForPackage(org.eclipse.jdt.core.IPackageFragment)}.
	 */
	public void testGetMembersForPackage() {
		List membersForPackageOne = ajdtContentProvider.getMembersForPackage(packageOne);
		assertNotNull("MembersForPackage 1 list should not be null", membersForPackageOne); //$NON-NLS-1$

		JDTMember packageOneMember = (JDTMember) membersForPackageOne.get(0);
		assertEquals("Wrong package member for " + packageOne.getElementName(), PACKAGE_ONE_MEMBER_NAME, packageOneMember.getName()); //$NON-NLS-1$
		assertEquals("Wrong number of members for " + packageOne.getElementName(), PACKAGE_ONE_MEMBER_COUNT, membersForPackageOne.size()); //$NON-NLS-1$

		// Try with another package
		List membersForPackageTwo = ajdtContentProvider.getMembersForPackage(packageTwo);
		assertNotNull("MembersForPackage list should not be null", membersForPackageTwo); //$NON-NLS-1$

		JDTMember packageTwoMember = (JDTMember) membersForPackageTwo.get(0);
		assertEquals("Wrong package member for " + packageTwo.getElementName(), PACKAGE_TWO_MEMBER_NAME, packageTwoMember.getName()); //$NON-NLS-1$
		assertEquals("Wrong number of members for " + packageTwo.getElementName(), PACKAGE_TWO_MEMBER_COUNT, membersForPackageTwo.size()); //$NON-NLS-1$
	}

}
