/**********************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 * ...
 **********************************************************************/
package org.eclipse.ajdt.ui.tests.launching;

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.buildpath.BuildConfigurationUtils;
import org.eclipse.ajdt.internal.launching.AJMainMethodSearchEngine;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;

public class AJMainMethodSearchEngineTest extends UITestCase {

    IProject project;
    IJavaProject jp;
    protected void setUp() throws Exception {
        super.setUp();
        project = createPredefinedProject("Bug261745MainMethodTesting"); //$NON-NLS-1$
        jp = JavaCore.create(project);
    }


    public void testMainInClassWithAspect() throws Exception {
        IFile file = project.getFile("src/f/Main1.java");
        IJavaElement elt = AspectJCore.create(file);
        doSearch(elt, 1);
    }

    public void testMainInAspect() throws Exception {
        IFile file = project.getFile("src/f/Main2.aj");
        IJavaElement elt = AspectJCore.create(file);
        doSearch(elt, 1);
    }

    public void testMainInNestedClassInAJFile() throws Exception {
        IFile file = project.getFile("src/f/Main3.aj");
        IJavaElement elt = AspectJCore.create(file);
        doSearch(elt, 1);
    }

    public void testMainInClass() throws Exception {
        IFile file = project.getFile("src/f/Main4.java");
        IJavaElement elt = AspectJCore.create(file);
        doSearch(elt, 1);
    }

    /**
     * this is one that we can't find
     */
    public void testMainInAspectInJavaFile() throws Exception {
        IFile file = project.getFile("src/f/Main5.java");
        IJavaElement elt = AspectJCore.create(file);
        doSearch(elt, 0);
    }

    public void testMainInProject() throws Exception {
        doSearch(jp, 4);
    }

    public void testMainInPackage() throws Exception {
        IFolder file = project.getFolder("src/f");
        IJavaElement elt = JavaCore.create(file);
        doSearch(elt, 4);
    }

    public void testMainInPackageRoot() throws Exception {
        IFolder file = project.getFolder("src");
        IJavaElement elt = JavaCore.create(file);
        doSearch(elt, 4);
    }





    private void doSearch(IJavaElement elt, int expected) throws Exception {
        AJMainMethodSearchEngine searchEngine = new AJMainMethodSearchEngine();
        IJavaElement[] elements = new IJavaElement[]{elt};
        int constraints = IJavaSearchScope.SOURCES;
        IJavaSearchScope scope = SearchEngine.createJavaSearchScope(elements, constraints);
        IType[] results = searchEngine.searchMainMethodsIncludingAspects(new NullProgressMonitor(), scope, true);
        assertEquals(
            "There should be " + expected + " main method(s) found. Instead found " + results.length, //$NON-NLS-1$
            results.length,
            expected
        );
    }


    /**
     * this is an old test case.  Keep it around even though it doesn't fit with the ones
     * above
     */
	public void testWithTracingExample() throws Exception {
	    IProject project2 = createPredefinedProject("Tracing Example");
	    IJavaProject jp2 = JavaCore.create(project2);
		IFile propertiesFile = project2.getFile("notrace.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		AJMainMethodSearchEngine searchEngine = new AJMainMethodSearchEngine();
		IJavaElement[] elements = new IJavaElement[]{jp2};

		int constraints = IJavaSearchScope.SOURCES;
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(elements, constraints);
		Object[] results = searchEngine.searchMainMethodsIncludingAspects(new NullProgressMonitor(), scope, true);
    assertEquals("There should be one result, found " + results.length, 1, results.length); //$NON-NLS-1$
		propertiesFile = project2.getFile("tracev1.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		waitForJobsToComplete();
		Object[] results2 = searchEngine.searchMainMethodsIncludingAspects(new NullProgressMonitor(), scope, true);
    assertEquals("There should be two results, found " + results2.length, 2, results2.length); //$NON-NLS-1$
	}

}
