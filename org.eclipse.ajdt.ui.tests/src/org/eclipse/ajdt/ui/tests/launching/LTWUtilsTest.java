/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins and Sian January - initial version
 *******************************************************************************/

package org.eclipse.ajdt.ui.tests.launching;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.buildpath.BuildConfigurationUtils;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.internal.launching.LTWUtils;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.JarPackageFragmentRoot;

public class LTWUtilsTest extends UITestCase{

	private final String line0Start = "<?xml version=\"1.0\""; //$NON-NLS-1$
	private final String aspectjBegin = "<aspectj>"; //$NON-NLS-1$
	private final String aspectjEnd = "</aspectj>"; //$NON-NLS-1$
	private final String aspectsBegin = "\t<aspects>"; //$NON-NLS-1$
	private final String aspectsEnd = "\t</aspects>"; //$NON-NLS-1$
	private final String aspects = "\t<aspects/>"; //$NON-NLS-1$

	// abstract aspects (which we don't want to include)
	public void testGetAspects() throws Exception {
		IProject project = createPredefinedProject("Tracing Example"); //$NON-NLS-1$
		waitForJobsToComplete();
		IFile propertiesFile = project.getFile("tracelib.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		IPackageFragmentRoot[] roots = jp.getAllPackageFragmentRoots();
		List<IPackageFragmentRoot> srcRoots = new ArrayList<>();
		for (IPackageFragmentRoot root : roots) {
			if (!(root instanceof JarPackageFragmentRoot))
				srcRoots.add(root);
		}
		assertEquals("There should be one src directory",1,srcRoots.size()); //$NON-NLS-1$
		List<IType> aspects = LTWUtils.getAspects((IPackageFragmentRoot)srcRoots.get(0));
		assertEquals("There should be two aspects",2,aspects.size()); //$NON-NLS-1$
		String aspectOne = ((AspectElement)aspects.get(0)).getElementName();
		String aspectTwo = ((AspectElement)aspects.get(1)).getElementName();
		boolean foundOne = false;
		boolean foundTwo = false;
		if (aspectOne.equals("TraceMyClasses") || aspectTwo.equals("TraceMyClasses")) {
			foundOne = true;
		}
		if (aspectOne.equals("AbstractTrace") || aspectTwo.equals("AbstractTrace")) {
			foundTwo = true;
		}
		if (!foundOne) {
			fail("Expected to find TraceMyClasses:"+aspectOne+","+aspectTwo);
		}
		if (!foundTwo) {
			fail("Expected to find AbstractTrace:"+aspectOne+","+aspectTwo);
		}
	}

	// multiple source folders with no aspects
	public void testGenerateLTWConfigFile1() throws Exception {
		IProject project = createPredefinedProject("MultipleSourceFolders"); //$NON-NLS-1$
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r1 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION); //$NON-NLS-1$
		IResource r2 = jp.getProject().findMember("src2/" + LTWUtils.AOP_XML_LOCATION); //$NON-NLS-1$

		assertNull("aop-ajc.xml shouldn't exist in src directory because there are no aspects",r1); //$NON-NLS-1$
		assertNull("aop-ajc.xml shouldn't exist in src2 directory because there are no aspects",r2); //$NON-NLS-1$
	}

	// one source folder with aspects
	public void testGenerateLTWConfigFile2() throws Exception {
		IProject project = createPredefinedProject("Bean Example"); //$NON-NLS-1$
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r1 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION);		 //$NON-NLS-1$
		assertNotNull("aop-ajc.xml should exist in src directory because there are aspects",r1); //$NON-NLS-1$

		IFile file = (IFile)r1;
		String[] expectedLines = new String[]{
				line0Start,
				aspectjBegin,
				aspectsBegin,
				"\t\t<aspect name=\"bean.BoundPoint\"/>", //$NON-NLS-1$
				aspectsEnd,
				aspectjEnd};

		compareFileContentsWithExpected(file, expectedLines);

		waitForJobsToComplete();

		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r2 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION);		 //$NON-NLS-1$
		assertNotNull("aop-ajc.xml should exist in src directory because there are aspects",r2); //$NON-NLS-1$

		compareFileContentsWithExpected((IFile)r2, expectedLines);
	}

	// no src folders with aspects
	public void testGenerateLTWConfigFile3() throws Exception {
		IProject project = createPredefinedProject("WithoutSourceFolder"); //$NON-NLS-1$
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r1 = jp.getProject().findMember(LTWUtils.AOP_XML_LOCATION);
		assertNotNull("aop-ajc.xml should exist in project directory because there are aspects",r1); //$NON-NLS-1$

		IFile file = (IFile)r1;
		String[] expectedLines = new String[]{
				line0Start,
				aspectjBegin,
				aspectsBegin,
				"\t\t<aspect name=\"A\"/>", //$NON-NLS-1$
				aspectsEnd,
				aspectjEnd};

		compareFileContentsWithExpected(file, expectedLines);

	}

	// no source folders, no aspects
	public void testGenerateLTWConfigFile4() throws Exception {
		IProject project = createPredefinedProject("Simple Project"); //$NON-NLS-1$
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r1 = jp.getProject().findMember(LTWUtils.AOP_XML_LOCATION);
		assertNull("aop-ajc.xml should not exist in the project directory because there are no aspects",r1); //$NON-NLS-1$

	}

	// multiple source folders with aspects
	public void testGenerateLTWConfigFile5() throws Exception {
		IProject project = createPredefinedProject("MultipleSourceFoldersWithAspects"); //$NON-NLS-1$
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r1 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION); //$NON-NLS-1$
		IResource r2 = jp.getProject().findMember("src2/" + LTWUtils.AOP_XML_LOCATION); //$NON-NLS-1$

		assertNotNull("aop-ajc.xml should exist in src directory because there are aspects",r1); //$NON-NLS-1$
		assertNotNull("aop-ajc.xml should exist in src2 directory because there are aspects",r2); //$NON-NLS-1$

		IFile file = (IFile)r1;
		String[] expectedLines = new String[]{
				line0Start,
				aspectjBegin,
				aspectsBegin,
				"\t\t<aspect name=\"pack.A1\"/>", //$NON-NLS-1$
				aspectsEnd,
				aspectjEnd};

		compareFileContentsWithExpected(file, expectedLines);

		IFile file2 = (IFile)r2;
		String[] expectedLines2 = new String[]{
				line0Start,
				aspectjBegin,
				aspectsBegin,
				"\t\t<aspect name=\"pack.A2\"/>", //$NON-NLS-1$
				aspectsEnd,
				aspectjEnd};

		compareFileContentsWithExpected(file2, expectedLines2);
	}

	// abstract aspects (which we don't want to include)
	public void testGenerateLTWConfigFile6() throws Exception {
		IProject project = createPredefinedProject("Tracing Example"); //$NON-NLS-1$
		waitForJobsToComplete();
		IFile propertiesFile = project.getFile("tracelib.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		waitForJobsToComplete();
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r1 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION); //$NON-NLS-1$
		assertNotNull("aop-ajc.xml should exist in src directory because there are aspects",r1); //$NON-NLS-1$

		IFile file = (IFile)r1;
		String[] expectedLines = new String[]{
				line0Start,
				aspectjBegin,
				aspectsBegin,
				"\t\t<aspect name=\"tracing.lib.AbstractTrace\"/>", //$NON-NLS-1$
				"\t\t<aspect name=\"tracing.lib.TraceMyClasses\"/>", //$NON-NLS-1$
				aspectsEnd,
				aspectjEnd};

		compareFileContentsWithExpected(file, expectedLines);

		// activate a different build config to see if aop-ajc.xml updates correctly
		propertiesFile = project.getFile("tracev1.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		waitForJobsToComplete();
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		r1 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION); //$NON-NLS-1$
		assertNotNull("aop-ajc.xml should exist in src directory because there are aspects",r1); //$NON-NLS-1$

		file = (IFile)r1;
		expectedLines = new String[]{
				line0Start,
				aspectjBegin,
				aspectsBegin,
				"\t\t<aspect name=\"tracing.version1.TraceMyClasses\"/>", //$NON-NLS-1$
				aspectsEnd,
				aspectjEnd};

		compareFileContentsWithExpected(file, expectedLines);

		// activate a config where no aspects are included. This should
		// clear the "aspects" part of the aop-ajc.xml file.
		propertiesFile = project.getFile("notrace.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());
		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForJobsToComplete();
		waitForJobsToComplete();
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		r1 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION); //$NON-NLS-1$
		assertNotNull("aop-ajc.xml should exist in src directory because there are aspects",r1); //$NON-NLS-1$

		file = (IFile)r1;
		expectedLines = new String[]{
				line0Start,
				aspectjBegin,
				aspects,
				aspectjEnd};

		compareFileContentsWithExpected(file, expectedLines);

	}

	// one source folder with aspects and aop-ajc.xml file which
	// contains other contents
	public void testGenerateLTWConfigFile7() throws Exception {
		IProject project = createPredefinedProject("project.with.aop-ajc.xml.file"); //$NON-NLS-1$
		waitForJobsToComplete();
		IJavaProject jp = JavaCore.create(project);
		LTWUtils.generateLTWConfigFile(jp);
		waitForJobsToComplete();

		IResource r1 = jp.getProject().findMember("src/" + LTWUtils.AOP_XML_LOCATION);		 //$NON-NLS-1$
		assertNotNull("aop-ajc.xml should exist in src directory because there are aspects",r1); //$NON-NLS-1$

		IFile file = (IFile)r1;
		String[] expectedLines = new String[]{
				line0Start,
				aspectjBegin,
				"\t<weaver options=\"-verbose -showWeaveInfo\"/>", //$NON-NLS-1$
				aspectsBegin,
				"\t\t<aspect name=\"bean.BoundPoint\"/>", //$NON-NLS-1$
				aspectsEnd,
				"<!-- this is a comment -->", //$NON-NLS-1$
				aspectjEnd};

		file.refreshLocal(1, null);
		compareFileContentsWithExpected(file, expectedLines);
	}


//	private void printFileContents(IFile file) throws IOException, CoreException {
//		BufferedReader br = new BufferedReader(new InputStreamReader(file.getContents()));
//		String line = br.readLine();
//		System.out.println("----------"); //$NON-NLS-1$
//		while (line != null) {
//			System.out.println("line: " + line); //$NON-NLS-1$
//			line = br.readLine();
//		}
//		br.close();
//	}

	private void compareFileContentsWithExpected(IFile file, String[] expectedLines) throws CoreException, IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(file
				.getContents()));
		String line = br.readLine();
		int counter = 0;
		while (line != null) {
			if (counter == 0) {
				if (!line.startsWith(expectedLines[0])) {
				 fail("expected line 1 to start with " + expectedLines[counter]  //$NON-NLS-1$
						+ (counter+1) + ", found " + line); //$NON-NLS-1$
				}
			} else if ((expectedLines.length <= counter)
					|| !line.equals(expectedLines[counter]) ) {
				br.close();
				fail("expected " + expectedLines[counter] + " on line "  //$NON-NLS-1$ //$NON-NLS-2$
						+ (counter+1) + ", found " + line); //$NON-NLS-1$
			}
			counter++;
			line = br.readLine();
		}
		br.close();
	}
}
