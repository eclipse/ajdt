/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

/**
 * Tests for BuildConfig
 */
public class BuildConfigTest extends AJDTCoreTestCase {

	/**
	 * Test that getIncludedSourceFiles returns the complete set of source files
	 * in a project
	 *
	 * @throws Exception
	 */
	public void testGetIncludedSourceFiles() throws Exception {
		IProject project = createPredefinedProject("TJP Example"); //$NON-NLS-1$
		String expected1 = "Demo.java"; //$NON-NLS-1$
		String expected2 = "GetInfo.aj"; //$NON-NLS-1$
		Set<IFile> files = BuildConfig.getIncludedSourceFiles(project);
		assertNotNull("getIncludedSourceFiles return null", files); //$NON-NLS-1$
		assertEquals(
				"getIncludedSourceFiles returned incorrect number of files", //$NON-NLS-1$
				2, files.size());
		Iterator<IFile> iter = files.iterator();
		String name1 = iter.next().getName();
		String name2 = iter.next().getName();
		if (!(name1.equals(expected1) || name2.equals(expected1))) {
			fail("Didn't find " + expected1 + " in list of source files"); //$NON-NLS-1$//$NON-NLS-2$
		}
		if (!(name1.equals(expected2) || name2.equals(expected2))) {
			fail("Didn't find " + expected2 + " in list of source files"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	/**
	 * Test that getIncludedSourceFiles returns the complete set of source files
	 * in a project
	 *
	 * @throws Exception
	 */
	public void testGetIncludedSourceFilesBug153597() throws Exception {
		IProject project = createPredefinedProject("bug153597"); //$NON-NLS-1$
		Set<IFile> files = BuildConfig.getIncludedSourceFiles(project);
		assertNotNull(
				"BuildConfig.getIncludedSourceFiles should not return null", files); //$NON-NLS-1$
		List<IFile> asList = new ArrayList<>(files);
		asList.sort((o1, o2) -> {
      String s1 = o1.getProjectRelativePath()
        .toPortableString();
      String s2 = o2.getProjectRelativePath()
        .toPortableString();
      return s1.compareTo(s2);
    });

		assertTrue(
				"BuildConfig.getIncludedSourceFiles should have returned at least one item", files.size() >= 1); //$NON-NLS-1$
		IFile f1 = asList.get(0);
		String s1 = f1.getProjectRelativePath().toPortableString();
		assertEquals(
				"BuildConfig.getIncludedSourceFiles should have returned s2/B.java", "s2/B.java", s1); //$NON-NLS-1$ //$NON-NLS-2$

		assertTrue(
				"BuildConfig.getIncludedSourceFiles should have returned two or more items", files.size() >= 2); //$NON-NLS-1$
		IFile f2 = asList.get(1);
		String s2 = f2.getProjectRelativePath().toPortableString();
		assertEquals(
				"BuildConfig.getIncludedSourceFiles should have returned src/A.java", "src/A.java", s2); //$NON-NLS-1$ //$NON-NLS-2$

		assertEquals(
				"BuildConfig.getIncludedSourceFiles should have returned exactly 2 files", 2, files.size()); //$NON-NLS-1$

	}

	/**
	 * Test that isIncluded returns true for source files on the build path,
	 * false otherwise
	 *
	 * @throws Exception
	 */
	public void testIsIncluded() throws Exception {
		IProject project = createPredefinedProject("TJP Example"); //$NON-NLS-1$
		IResource demo = project.findMember("src/tjp/Demo.java"); //$NON-NLS-1$
		assertNotNull("Couldn't find Demo.java file", demo); //$NON-NLS-1$
		assertTrue("Demo.java should be included", BuildConfig //$NON-NLS-1$
				.isIncluded(demo));
		IResource getinfo = project.findMember("src/tjp/GetInfo.aj"); //$NON-NLS-1$
		assertNotNull("Couldn't find GetInfo.aj file", getinfo); //$NON-NLS-1$
		assertTrue("GetInfo.aj should be included", BuildConfig //$NON-NLS-1$
				.isIncluded(getinfo));
		IResource ajprop = project.findMember("build.ajproperties"); //$NON-NLS-1$
		assertNotNull("Couldn't find build.ajproperties file", ajprop); //$NON-NLS-1$
		assertFalse("build.ajproperties should NOT be included", //$NON-NLS-1$
				BuildConfig.isIncluded(ajprop));
	}
}
