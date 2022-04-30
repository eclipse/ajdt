/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.xref;

import java.util.List;

import org.aspectj.asm.IRelationship;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.core.model.AJRelationshipType;
import org.eclipse.ajdt.internal.builder.AJNode;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.contribution.xref.core.XReferenceAdapter;
import org.eclipse.contribution.xref.internal.ui.providers.TreeObject;
import org.eclipse.contribution.xref.internal.ui.providers.TreeParent;
import org.eclipse.contribution.xref.internal.ui.providers.XReferenceContentProvider;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

public class XReferenceViewContentsTest extends UITestCase {

	/**
	 * Test for bug 111189 - the xrefs not being added to the
	 * tree if there is advice on methods inside inner classes.
	 */
	public void testBug111189() throws CoreException {
		IProject project = createPredefinedProject("bug111189"); //$NON-NLS-1$
		waitForJobsToComplete();

		XReferenceContentProvider viewContentProvider =
			new XReferenceContentProvider();

		AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForProject(project);
		waitForJobsToComplete();

		AJRelationshipType[] rels = new AJRelationshipType[]{
				AJRelationshipManager.MATCHES_DECLARE,
				AJRelationshipManager.MATCHED_BY};

		List<IRelationship>/*IRelationship*/ listOfRels = model.getRelationshipsForProject(rels);
    assertFalse("there should be some relationships", listOfRels.isEmpty()); //$NON-NLS-1$

		IJavaProject jProject = JavaCore.create(project);
		IJavaElement source = jProject.findType("pack.Test");

		XReferenceAdapter xra = new XReferenceAdapter(source);
		viewContentProvider.inputChanged(null,null,xra);
		Object[] elements = viewContentProvider.getElements(xra);
		assertEquals("There should be one source of xref",1, elements.length); //$NON-NLS-1$
		assertEquals(source, ((TreeParent) elements[0]).getData());

		TreeParent testMethod = null;
		TreeParent innerClassC1 = null;

		Object[] children = viewContentProvider.getChildren(elements[0]);
		// if there aren't two children here then the recursion is
		// wrong in the XReferenceContentProvider.addChildren(..) method
		// and this is a regression of bug 111189
		assertEquals("There should be two children",2, children.length); //$NON-NLS-1$
    for (Object child : children) {
      TreeParent t = (TreeParent) child;
      IJavaElement x = (IJavaElement) t.getData();
      if (x.getElementName().equals("testMethod")) { //$NON-NLS-1$
        testMethod = t;
      }
      else {
        innerClassC1 = t;
      }
    }

		// Check that all the children have been added to the testMethod node
		Object[] c1 = viewContentProvider.getChildren(testMethod);
		assertEquals("testMethod should have 1 child",1,c1.length); //$NON-NLS-1$
        assertTrue("matches declare child should exist", ((IJavaElement) ((TreeObject) c1[0]).getData()).exists());
		Object[] c2 = viewContentProvider.getChildren(c1[0]);
        assertEquals("named innerclass C should have 1 child",1,c2.length); //$NON-NLS-1$
        assertTrue("matches declare child should exist", ((IJavaElement) ((TreeObject) c2[0]).getData()).exists());
		Object[] c3 = viewContentProvider.getChildren(c2[0]);
		assertEquals("method m() of named innerclass C should have 1 child",1,c3.length); //$NON-NLS-1$
    assertEquals("matches declare child should exist", "matches declare", ((TreeObject) c3[0]).getName());
		Object[] c4 = viewContentProvider.getChildren(c3[0]);
		assertEquals("matches declare should have 1 child",1,c4.length); //$NON-NLS-1$
        assertTrue("matches declare child should exist", ((AJNode) ((TreeObject) c4[0]).getData()).getJavaElement().exists());

		// Check that all the children have been added to the innerclass node
		Object[] d1 = viewContentProvider.getChildren(innerClassC1);
		assertEquals("inner class should have 1 child",1,d1.length); //$NON-NLS-1$
        assertTrue("matches declare child should exist", ((IJavaElement) ((TreeObject) d1[0]).getData()).exists());
		Object[] d2 = viewContentProvider.getChildren(d1[0]);
		// if there aren't two children then this is a regression of bug 111189
		//  - the recursion in XReferenceContentProvider.addChildren(..) method
		assertEquals("method m() of inner class should have 2 children",2,d2.length); //$NON-NLS-1$
    assertEquals("matches declare child should exist", "matches declare", ((TreeObject) d2[0]).getName());
        assertTrue("matches declare child should exist", ((IJavaElement) ((TreeObject) d2[1]).getData()).exists());
		// there are two children of m(): 'matches declare' and
		// 'method-call(.....)'
		TreeParent matchesDeclare = null;
		TreeParent methodCall = null;
    for (Object o : d2) {
      TreeParent t = (TreeParent) o;
      if (t.getData() == null) {
        matchesDeclare = t;
      }
      else {
        methodCall = t;
      }
    }
		Object[] d3 = viewContentProvider.getChildren(matchesDeclare);
		assertEquals("matches declare should have one child",1,d3.length); //$NON-NLS-1$
		assertTrue("matches declare child should exist", ((AJNode) ((TreeObject) d3[0]).getData()).getJavaElement().exists());
		Object[] d4 = viewContentProvider.getChildren(methodCall);
		assertEquals("method-call should have one child",1,d4.length); //$NON-NLS-1$
    assertEquals("matches declare child should exist", "matches declare", ((TreeObject) d4[0]).getName());
		Object[] d5 = viewContentProvider.getChildren(d4[0]);
		assertEquals("matches declare should have one child",1,d5.length); //$NON-NLS-1$
        assertTrue("matches declare child should exist", ((AJNode) ((TreeObject) d5[0]).getData()).getJavaElement().exists());
	}


}
