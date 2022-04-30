/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman  - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core.tests;

import java.util.List;

import org.aspectj.asm.IRelationship;
import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.contribution.jdt.IsWovenTester;
import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Tests for AspectJCore.create()
 */
public class AJCoreTest extends AJDTCoreTestCase {

	/**
	 * Test that AspectJCore.create() can form appropriate Java elements from a
	 * variety of handle identifiers
	 *
	 * @throws Exception
	 */
	public void testCreateElementFromHandle() throws Exception {

	    if (!IsWovenTester.isWeavingActive()) {
	        fail("This test requires JDT Weaving service to be enabled");
	    }

		createPredefinedProject("TJP Example"); //$NON-NLS-1$
		// each entry in the array contains:
		// <handle> <name of element> <containing resource> <class name of
		// element>
		// note that the elements referred to by the handles need to exist
		// in the workspace
		String[][] testHandles = {
				{ "=TJP Example/src<tjp{Demo.java", "Demo.java", //$NON-NLS-1$ //$NON-NLS-2$
						"Demo.java", "CompilationUnit" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "=TJP Example/src<tjp{Demo.java[Demo~main", "main", //$NON-NLS-1$ //$NON-NLS-2$
						"Demo.java", "SourceMethod" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "=TJP Example/src<tjp{GetInfo.aj", "GetInfo.aj", //$NON-NLS-1$ //$NON-NLS-2$
						"GetInfo.aj", "AJCompilationUnit" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "=TJP Example/src<tjp{GetInfo.aj'GetInfo", "GetInfo", //$NON-NLS-1$ //$NON-NLS-2$
						"GetInfo.aj", "AspectElement" }, //$NON-NLS-1$ //$NON-NLS-2$
				{ "=TJP Example/src<tjp{GetInfo.aj'GetInfo~println", //$NON-NLS-1$
						"println", "GetInfo.aj", "SourceMethod" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				{ "=TJP Example/src<tjp{GetInfo.aj'GetInfo&around", //$NON-NLS-1$
						"around", "GetInfo.aj", "AdviceElement" } }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		compareWithHandles(testHandles);
	}

	/**
	 * Test that AspectJCore.create() can form appropriate Java elements from a
	 * variety of handle identifiers
	 *
	 * @throws Exception
	 */
	public void testCreateElementFromHandle2() throws Exception {
        if (!IsWovenTester.isWeavingActive()) {
            fail("This test requires JDT Weaving service to be enabled");
        }

        createPredefinedProject("Bean Example"); //$NON-NLS-1$
		String methodHandle = "=Bean Example/src<bean{Demo.java[Demo~main~\\[QString;?method-call(void bean.Point.setX(int))!0!0!0!0!I!0!false"; //$NON-NLS-1$

		// each entry in the array contains:
		// <handle> <name of element> <containing resource> <class name of
		// element>
		// note that the elements referred to by the handles need to exist
		// in the workspace
		String[][] testHandles = {
				{ methodHandle, "method-call(void bean.Point.setX(int))", //$NON-NLS-1$
						"Demo.java", "AJCodeElement" }, //$NON-NLS-1$ //$NON-NLS-2$
				{
						"=Bean Example/src<bean{BoundPoint.aj'BoundPoint&around&QPoint;", //$NON-NLS-1$
						"around", "BoundPoint.aj", "AdviceElement" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				{
						"=Bean Example/src<bean{BoundPoint.aj'BoundPoint)Point.hasListeners)QString;", //$NON-NLS-1$
						"Point.hasListeners", "BoundPoint.aj", //$NON-NLS-1$ //$NON-NLS-2$
						"MethodIntertypeElement" }, //$NON-NLS-1$
				{
						"=Bean Example/src<bean{BoundPoint.aj'BoundPoint,Point.support", //$NON-NLS-1$
						"Point.support", "BoundPoint.aj", //$NON-NLS-1$ //$NON-NLS-2$
						"FieldIntertypeElement" }, //$NON-NLS-1$
				{
						"=Bean Example/src<bean{BoundPoint.aj'BoundPoint`declare parents", //$NON-NLS-1$
						"declare parents", "BoundPoint.aj", //$NON-NLS-1$ //$NON-NLS-2$
						"DeclareElement" }, //$NON-NLS-1$
				{
						"=Bean Example/src<bean{BoundPoint.aj'BoundPoint`declare parents!2", //$NON-NLS-1$
						"declare parents", "BoundPoint.aj", //$NON-NLS-1$ //$NON-NLS-2$
						"DeclareElement" } //$NON-NLS-1$

		};
		compareWithHandles(testHandles);
	}

	/**
	 * Test that AspectJCore.create() can form appropriate Java elements from a
	 * variety of handle identifiers
	 *
	 * @throws Exception
	 */
	public void testCreateElementFromHandle3() throws Exception {
        if (!IsWovenTester.isWeavingActive()) {
            fail("This test requires JDT Weaving service to be enabled");
        }

        createPredefinedProject("Spacewar Example"); //$NON-NLS-1$
		// each entry in the array contains:
		// <handle> <name of element> <containing resource> <class name of
		// element>
		// note that the elements referred to by the handles need to exist
		// in the workspace
		String[][] testHandles = { {
				"=Spacewar Example/src<spacewar{Ship.aj[Ship\"helmCommandsCut\"QShip;", "helmCommandsCut", //$NON-NLS-1$ //$NON-NLS-2$
				"Ship.aj", "PointcutElement" }, //$NON-NLS-1$ //$NON-NLS-2$
		};
		compareWithHandles(testHandles);
	}

	/**
	 * Test that going from an IJavaElement to its handle identifier then back
	 * to an IJavaElement using AspectJCore.create() results in a element that
	 * is equivalent to the original (not necessarily identical).
	 *
	 * @throws Exception
	 */
	public void testHandleCreateRoundtrip() throws Exception {
		IProject project = createPredefinedProject14("TJP Example"); //$NON-NLS-1$
		AJRelationshipType[] rels = new AJRelationshipType[] {
				AJRelationshipManager.ADVISED_BY, AJRelationshipManager.ADVISES };
		compareElementsFromRelationships(rels, project);
	}

	/**
	 * Test that going from an IJavaElement to its handle identifier then back
	 * to an IJavaElement using AspectJCore.create() results in a element that
	 * is equivalent to the original (not necessarily identical).
	 *
	 * @throws Exception
	 */
	public void testHandleCreateRoundtrip2() throws Exception {
		IProject project = createPredefinedProject("Bean Example"); //$NON-NLS-1$
		AJRelationshipType[] rels = new AJRelationshipType[] {
				AJRelationshipManager.ADVISED_BY,
				AJRelationshipManager.ADVISES,
				AJRelationshipManager.DECLARED_ON,
				AJRelationshipManager.ASPECT_DECLARATIONS };
		compareElementsFromRelationships(rels, project);
	}

	/**
	 * Test that going from an IJavaElement to its handle identifier then back
	 * to an IJavaElement using AspectJCore.create() results in a element that
	 * is equivalent to the original (not necessarily identical).
	 *
	 * @throws Exception
	 */
	public void testHandleCreateRoundtrip3() throws Exception {
		IProject project = createPredefinedProject("MarkersTest",true); //$NON-NLS-1$
		AJRelationshipType[] rels = new AJRelationshipType[] {
				AJRelationshipManager.ADVISED_BY,
				AJRelationshipManager.ADVISES,
				AJRelationshipManager.DECLARED_ON,
				AJRelationshipManager.ASPECT_DECLARATIONS,
				AJRelationshipManager.MATCHED_BY,
				AJRelationshipManager.MATCHES_DECLARE };
		compareElementsFromRelationships(rels, project);
	}

	/**
	 * Test that going from an IJavaElement to its handle identifier then back
	 * to an IJavaElement using AspectJCore.create() results in a element that
	 * is equivalent to the original (not necessarily identical).
	 *
	 * @throws Exception
	 */
	public void testHandleCreateRoundtrip4() throws Exception {
		IProject project = createPredefinedProject("Spacewar Example"); //$NON-NLS-1$
		AJRelationshipType[] rels = new AJRelationshipType[] {
				AJRelationshipManager.ADVISED_BY,
				AJRelationshipManager.ADVISES,
				AJRelationshipManager.DECLARED_ON,
				AJRelationshipManager.ASPECT_DECLARATIONS,
				AJRelationshipManager.MATCHED_BY,
				AJRelationshipManager.MATCHES_DECLARE };
		compareElementsFromRelationships(rels, project);
	}

	/**
	 * Test that going from an IJavaElement to its handle identifier then back
	 * to an IJavaElement using AspectJCore.create() results in a element that
	 * is equivalent to the original (not necessarily identical).
	 *
	 * @throws Exception
	 */
	public void testHandleCreateRoundtrip5() throws Exception {
		IProject libProject = (IProject) getWorkspaceRoot().findMember(
				"MyAspectLibrary"); //$NON-NLS-1$
		if (libProject == null) {
			libProject = createPredefinedProject14("MyAspectLibrary"); //$NON-NLS-1$
		}
		IProject weaveMeProject = createPredefinedProject("WeaveMe"); //$NON-NLS-1$
		AJRelationshipType[] rels = new AJRelationshipType[] { AJRelationshipManager.ADVISED_BY };
		compareElementsFromRelationships(rels, weaveMeProject);
	}

	/**
	 * Test that going from an IJavaElement to its handle identifier then back
	 * to an IJavaElement using AspectJCore.create() results in a element that
	 * is equivalent to the original (not necessarily identical).
	 *
	 * @throws Exception
	 */
	public void testHandleCreateRoundtripBug94107() throws Exception {
		IProject project = createPredefinedProject("bug94107"); //$NON-NLS-1$
		AJRelationshipType[] rels = new AJRelationshipType[] { AJRelationshipManager.ADVISED_BY };
		compareElementsFromRelationships(rels, project);
	}

	private static String getSimpleClassName(IJavaElement obj) {
		String longName = obj.getClass().getName();
		int index = longName.lastIndexOf('.');
		if (index == -1) {
			return longName;
		}
		return longName.substring(index + 1);
	}

	static void compareElementsFromRelationships(AJRelationshipType[] rels,
			IProject project) throws JavaModelException {
	    IJavaProject jProject = JavaCore.create(project);
        AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForProject(jProject.getProject());

		List<IRelationship> allRels = model.getRelationshipsForProject(rels);
		if (!model.hasModel() || allRels.size() == 0) {
			// if the project or model didn't build properly we'd get no
			// relationships
			// and the test would blindly pass without this check
			fail("No relationships found for project " + project.getName()); //$NON-NLS-1$
		}
    for (IRelationship rel : allRels) {
      HandleTestUtils.checkAJHandle(rel.getSourceHandle(), model);
      for (String handle : rel.getTargets()) {
        HandleTestUtils.checkAJHandle(handle, model);
      }
    }
	}

	private void compareWithHandles(String[][] testHandles) {
    for (String[] testHandle : testHandles) {
      IJavaElement el = AspectJCore.create(testHandle[0]);
      assertEquals(
        "Handle identifier of created element doesn't match original", //$NON-NLS-1$
        testHandle[0], el.getHandleIdentifier());
      assertEquals("Name of created element doesn't match expected", //$NON-NLS-1$
        testHandle[1], el.getElementName());
      assertEquals(
        "Name of created element resource doesn't match expected", //$NON-NLS-1$
        testHandle[2], el.getResource().getName());
      assertEquals("Created element is not of the expected class type", //$NON-NLS-1$
        testHandle[3], getSimpleClassName(el));
    }
	}
}
