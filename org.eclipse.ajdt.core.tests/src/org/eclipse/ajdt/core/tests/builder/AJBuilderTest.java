/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - initial version
 *               Helen Hawkins   - updated for new ajde interface (bug 148190)
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.builder;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.core.tests.testutils.ReaderInputStream;
import org.eclipse.ajdt.core.tests.testutils.TestLogger;
import org.eclipse.ajdt.core.tests.testutils.Utils;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerConfiguration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class AJBuilderTest extends AJDTCoreTestCase {

	protected void tearDown() throws Exception {
		super.tearDown();
		AspectJPlugin.getDefault().setAJLogger(null);
		Utils.setAutobuilding(true);
	}

	@Override
	protected void setUp() throws Exception {
	    super.setUp();
        // requires a completely clean workspace
        super.cleanWorkspace(true);
	}

	/**
	 * Test for bug 101481 - "clean" build doesn't work and refreshing of output
	 * directory
	 *
	 * @throws Exception
	 */
	public void xtestCleanBuild() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject project = createPredefinedProject("bug101481"); //$NON-NLS-1$
		try {
			Utils.setAutobuilding(false);

			assertFalse("autobuilding should be set to false", Utils //$NON-NLS-1$
					.isAutobuilding());
			assertFalse("project should have no errors", testLog //$NON-NLS-1$
					.containsMessage("error")); //$NON-NLS-1$

			IFolder src = project.getFolder("src"); //$NON-NLS-1$
			if (!src.exists()) {
				src.create(true, true, null);
			}
			IFolder pack = src.getFolder("pack"); //$NON-NLS-1$
			if (!pack.exists()) {
				pack.create(true, true, null);
			}
			IFile c = pack.getFile("C.java"); //$NON-NLS-1$
			assertNotNull("src folder should not be null", src); //$NON-NLS-1$
			assertNotNull("package pack should not be null", pack); //$NON-NLS-1$
			assertNotNull("class c should not be null", c); //$NON-NLS-1$
			assertTrue("java file should exist", c.exists()); //$NON-NLS-1$

			IFolder bin = project.getFolder("bin"); //$NON-NLS-1$
			if (!bin.exists()) {
				bin.create(true, true, null);
			}
			IFolder binPack = bin.getFolder("pack"); //$NON-NLS-1$
			if (!binPack.exists()) {
				binPack.create(true, true, null);
			}
			IFile binC = binPack.getFile("C.class"); //$NON-NLS-1$

			assertTrue("bin directory should contain class file", //$NON-NLS-1$
					outputDirContainsFile(project, "pack", "C.class")); //$NON-NLS-1$ //$NON-NLS-2$
			// testing the refresh output directory part of bug 101481
			assertTrue("class file should exist", binC.exists()); //$NON-NLS-1$

      //$NON-NLS-1$
      String origContents = "package pack; " + System.getProperty("line.separator") + //$NON-NLS-1$
                            "public class C {}"; //$NON-NLS-1$

      // write "blah blah blah" to the class
			// NOTE: we add a comment so that thet class file doesn't get
			// deleted, as we test for it later, but this is a somewhat
			// arbitrary test because the behaviour of AJC is different to the
			// JDT compiler when the source has errors (see bug 102733)
      StringReader sr = new StringReader(//$NON-NLS-1$
        "blah blah blah/*comment*/" + origContents);
			c.setContents(new ReaderInputStream(sr), IResource.FORCE, null);
			sr.close();
			try {Thread.sleep(1000); } catch (Exception ignored) {}
			project.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
			waitForAutoBuild();
			assertTrue("project should have errors, but had:\n" + testLog.getLog(), testLog //$NON-NLS-1$
					.containsMessage("Syntax error on")); //$NON-NLS-1$
			assertTrue("bin directory should contain class file", //$NON-NLS-1$
					outputDirContainsFile(project, "pack", "C.class")); //$NON-NLS-1$ //$NON-NLS-2$
			assertFalse(
					"should not have cleaned the output folder", //$NON-NLS-1$
					testLog
							.containsMessage("Cleared AJDT relationship map for project bug101481")); //$NON-NLS-1$
			int n = testLog
					.numberOfEntriesForMessage("Builder: Tidied output folder(s), removed class files and derived resources"); //$NON-NLS-1$

			binC = binPack.getFile("C.class"); //$NON-NLS-1$
			assertTrue("class file should exist", binC.exists()); //$NON-NLS-1$

			project.build(IncrementalProjectBuilder.CLEAN_BUILD, null);
			// testing the same steps are taken during a clean as they
			// are in the javaBuilder part of bug 101481
      assertEquals("should have cleaned the output folder", 0, bin.members().length);
			assertTrue(
					"should have removed problems and tasks for the project", //$NON-NLS-1$
					testLog.containsMessage("Removed problems and tasks for project")); //$NON-NLS-1$
			assertEquals(
					"should have cleaned output folder " + (n + 1) + "times", //$NON-NLS-1$ //$NON-NLS-2$
					n + 1,
					testLog.numberOfEntriesForMessage("Builder: Tidied output folder(s), removed class files and derived resources")); //$NON-NLS-1$
			assertFalse("bin directory should not contain class file", //$NON-NLS-1$
					outputDirContainsFile(project, "pack", "C.class")); //$NON-NLS-1$ //$NON-NLS-2$

			// testing the refresh output dir after a clean (without doing
			// a build) part of bug 101481
			binC = binPack.getFile("C.class"); //$NON-NLS-1$
			assertFalse("class file should not exist", binC.exists()); //$NON-NLS-1$

		} finally {
			Utils.setAutobuilding(true);
		}
	}

	private boolean outputDirContainsFile(IProject project, String packageName,
			String fileName) throws JavaModelException {
		IJavaProject javaProject = JavaCore.create(project);
		IPath workspaceRelativeOutputPath = javaProject.getOutputLocation();

		String realOutputLocation;
		if (workspaceRelativeOutputPath.segmentCount() == 1) { // project
			// root
			realOutputLocation = javaProject.getResource().getLocation()
					.toOSString();
		} else {
			IFolder out = ResourcesPlugin.getWorkspace().getRoot().getFolder(
					workspaceRelativeOutputPath);
			realOutputLocation = out.getLocation().toOSString();
		}

		File outputDir = new File(realOutputLocation + File.separator
				+ packageName);
		if (outputDir.exists()) {
    		File[] outputFiles = outputDir.listFiles();
      for (File outputFile : outputFiles) {
        if (outputFile.getName().equals(fileName)) {
          return true;
        }
      }
		}
		return false;
	}

	public void testMultipleOutputFolders() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject project = createPredefinedProject("MultipleOutputFolders"); //$NON-NLS-1$
		// check class files end up in correct output folder
		IFolder bin = project.getFolder("bin"); //$NON-NLS-1$
		if (!bin.exists()) {
			bin.create(true, true, null);
		}
		IFolder binPack = bin.getFolder("p1"); //$NON-NLS-1$
		if (!binPack.exists()) {
			binPack.create(true, true, null);
		}
		IFile binC = binPack.getFile("Demo.class"); //$NON-NLS-1$
		assertTrue("class file Demo.class should exist in bin", binC.exists()); //$NON-NLS-1$

		IFolder bin2 = project.getFolder("bin2"); //$NON-NLS-1$
		if (!bin2.exists()) {
			bin2.create(true, true, null);
		}
		IFolder bin2Pack = bin2.getFolder("p2"); //$NON-NLS-1$
		if (!bin2Pack.exists()) {
			bin2Pack.create(true, true, null);
		}
		IFile bin2C = bin2Pack.getFile("GetInfo.class"); //$NON-NLS-1$
		assertTrue(
				"class file GetInfo.class should exist in bin2", bin2C.exists()); //$NON-NLS-1$

		binC = binPack.getFile("GetInfo.class"); //$NON-NLS-1$
		assertFalse(
				"class file GetInfo.class should NOT exist in bin", binC.exists()); //$NON-NLS-1$

		binC = bin2Pack.getFile("Demo.class"); //$NON-NLS-1$
		assertFalse(
				"class file Demo.class should NOT exist in bin2", binC.exists()); //$NON-NLS-1$

		// now test that cleaning the project cleans both folders
		Utils.setAutobuilding(false);
		project.build(IncrementalProjectBuilder.CLEAN_BUILD, null);

		bin2.refreshLocal(IResource.DEPTH_INFINITE, null);
    assertEquals("should not have any files in the bin2 folder", 0, bin2.members().length);

		bin.refreshLocal(IResource.DEPTH_INFINITE, null);
    assertEquals("should not have any files in the bin2 folder", 0, bin.members().length);
	}

	public void testBug153682() throws Exception {
        Utils.setAutobuilding(false);
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject project = createPredefinedProject("bug153682"); //$NON-NLS-1$
        getWorkspace().build(IncrementalProjectBuilder.FULL_BUILD, null);

		// check class files end up in correct output folder
		IFolder bin = project.getFolder("bin"); //$NON-NLS-1$
		if (!bin.exists()) {
			bin.create(true, true, null);
		}
		IFolder binPack = bin.getFolder("foo"); //$NON-NLS-1$
		if (!binPack.exists()) {
			binPack.create(true, true, null);
		}
		IFile binC = binPack.getFile("Test.class"); //$NON-NLS-1$
		assertTrue("class file Test.class should exist in bin", binC.exists()); //$NON-NLS-1$

		binC = project.getFile("Test.class"); //$NON-NLS-1$
		assertFalse(
				"class file Test.class should NOT exist in project root", binC.exists()); //$NON-NLS-1$

		project.build(IncrementalProjectBuilder.CLEAN_BUILD, null);

    assertEquals("should have have cleaned the output folder", 0, bin.members().length);

	}

	public void testIncrementalBuildWithSrcFolder() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject project = createPredefinedProject("TJP Example"); //$NON-NLS-1$
		assertFalse("project should have no errors", testLog //$NON-NLS-1$
				.containsMessage("error")); //$NON-NLS-1$

		IFolder src = project.getFolder("src"); //$NON-NLS-1$
		if (!src.exists()) {
			src.create(true, true, null);
		}
		IFolder pack = src.getFolder("tjp"); //$NON-NLS-1$
		if (!pack.exists()) {
			pack.create(true, true, null);
		}
		IFile c = pack.getFile("Demo.java"); //$NON-NLS-1$
		assertNotNull("src folder should not be null", src); //$NON-NLS-1$
		assertNotNull("package tjp should not be null", pack); //$NON-NLS-1$
		assertNotNull("class Demo should not be null", c); //$NON-NLS-1$
		assertTrue("java file should exist", c.exists()); //$NON-NLS-1$

		IFolder bin = project.getFolder("bin"); //$NON-NLS-1$
		if (!bin.exists()) {
			bin.create(true, true, null);
		}
		IFolder binPack = bin.getFolder("tjp"); //$NON-NLS-1$
		if (!binPack.exists()) {
			binPack.create(true, true, null);
		}
		IFile binC = binPack.getFile("Demo.class"); //$NON-NLS-1$
		assertTrue("class file should exist", binC.exists()); //$NON-NLS-1$

		String rep = testLog
				.getMostRecentMatchingMessage("AspectJ reports build successful"); //$NON-NLS-1$
		System.out.println("rep: " + rep); //$NON-NLS-1$

		// add a comment to the class
		StringReader sr = new StringReader("/* blah blah blah */"); //$NON-NLS-1$
		c.appendContents(new ReaderInputStream(sr), IResource.FORCE, null);

		waitForAutoBuild();

		assertTrue("Successful build should have occurred", testLog //$NON-NLS-1$
				.containsMessage("AspectJ reports build successful")); //$NON-NLS-1$

		rep = testLog
				.getMostRecentMatchingMessage("AspectJ reports build successful"); //$NON-NLS-1$
		assertNotNull("Successful build should have been reported", rep); //$NON-NLS-1$

		assertTrue(
				"The build should have been an incremental one", wasIncrementalBuild(rep)); //$NON-NLS-1$
	}

	public void testIncrementalBuildWithoutSrcFolder() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject project = createPredefinedProject("bug102652"); //$NON-NLS-1$
		assertFalse("project should have no errors", testLog //$NON-NLS-1$
				.containsMessage("error")); //$NON-NLS-1$

		IFolder pack = project.getFolder("tjp"); //$NON-NLS-1$
		if (!pack.exists()) {
			pack.create(true, true, null);
		}
		IFile c = pack.getFile("Demo.java"); //$NON-NLS-1$
		assertNotNull("package tjp should not be null", pack); //$NON-NLS-1$
		assertNotNull("class Demo should not be null", c); //$NON-NLS-1$
		assertTrue("java file should exist", c.exists()); //$NON-NLS-1$

		IFile binC = pack.getFile("Demo.class"); //$NON-NLS-1$
		assertTrue("class file should exist", binC.exists()); //$NON-NLS-1$

		// add a comment to the class
		StringReader sr = new StringReader("/* blah blah blah */"); //$NON-NLS-1$
		c.appendContents(new ReaderInputStream(sr), IResource.FORCE, null);

		waitForAutoBuild();

		assertTrue("Successful build should have occurred", testLog //$NON-NLS-1$
				.containsMessage("AspectJ reports build successful")); //$NON-NLS-1$

		String rep = testLog
				.getMostRecentMatchingMessage("AspectJ reports build successful"); //$NON-NLS-1$
		assertNotNull("Successful build should have been reported", rep); //$NON-NLS-1$
		assertTrue(
				"The build should have been an incremental one", wasIncrementalBuild(rep)); //$NON-NLS-1$
	}

	/**
	 * Bug 74174 changing a txt file inside a source folder triggers a build
	 * (either full or incremental) when it doesn't need to
	 */
	public void testBug74174() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject project = createPredefinedProject("bug99133b"); //$NON-NLS-1$
		assertFalse("project should have no errors", testLog //$NON-NLS-1$
				.containsMessage("error")); //$NON-NLS-1$
		IFile f = project.getFile("src/p/anotherTest.txt"); //$NON-NLS-1$
		assertNotNull("file test.txt should not be null", f); //$NON-NLS-1$

		if (!f.exists()) {
			f.create(new ByteArrayInputStream(new byte[0]), true, null);
		}
		waitForAutoBuild();
		waitForAutoBuild();
		assertTrue("text file should exist", f.exists()); //$NON-NLS-1$

		IFile binF = project.getFile("bin/p/anotherTest.txt"); //$NON-NLS-1$
		assertNotNull("file test.txt should not be null", binF); //$NON-NLS-1$
		assertTrue("text file should exist", binF.exists()); //$NON-NLS-1$

		int numberOfBuildsRun = testLog.getNumberOfBuildsRun();

		// add text to the file
		StringReader sr = new StringReader("more blah blah blah"); //$NON-NLS-1$
		f.appendContents(new ReaderInputStream(sr), IResource.FORCE, null);

		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();

		// check that we have gone through the AJBuilder.build(..) method
		// and that there are no errors reported
		assertEquals(
				"The number of builds should be " + (numberOfBuildsRun + 1), numberOfBuildsRun + 1, testLog.getNumberOfBuildsRun()); //$NON-NLS-1$
		List<String> buildLog = testLog.getPreviousBuildEntry(1);

		// This is the message AJDT put's out when it decides not
		// to do a build. It thinks there are no src changes in the
		// current project.
		// NOTE: this will fail if we decide that AJDT can't make these
		// sorts of decisions and pass everything down to the compiler
		// (who can).
		assertTrue(
				"AJDT should have found no source file changes and decided not to build", //$NON-NLS-1$
				listContainsString(buildLog, "build: Examined delta - no " + //$NON-NLS-1$
						"source file or classpath changes for project bug99133b")); //$NON-NLS-1$
		assertFalse("There should be no errors in the build log", //$NON-NLS-1$
				listContainsString(buildLog, "error")); //$NON-NLS-1$

		// by checking that we don't have the following messages in the
		// log (and the previous checking for no errors) we know that
		// AJDT has exited the build method before calling the compiler.
		// NOTE: these will fail if we decide that AJDT can't make these
		// sorts of decisions and pass everything down to the compiler
		// (who can).
		boolean inc = listContainsString(buildLog,
				"AspectJ reports build successful, build was: INCREMENTAL"); //$NON-NLS-1$
		assertFalse("AJDT should have returned from the build without " + //$NON-NLS-1$
				"going through the compiler, therefore AspectJ shouldn't " + //$NON-NLS-1$
				"report that an incremental build happened", inc); //$NON-NLS-1$
		boolean full = listContainsString(buildLog,
				"AspectJ reports build successful, build was: FULL"); //$NON-NLS-1$
		assertFalse("AJDT should have returned from the build without " + //$NON-NLS-1$
				"going through the compiler, therefore AspectJ shouldn't " + //$NON-NLS-1$
				"report that a full build happened", full); //$NON-NLS-1$

		BufferedReader br1 = new BufferedReader(new InputStreamReader(binF
				.getContents()));

		String line1 = br1.readLine();
		assertEquals(
				"file in bin directory should contain \"more blah blah blah\"", //$NON-NLS-1$
				"more blah blah blah", line1); //$NON-NLS-1$
		br1.close();
	}

	/**
	 * Bug 98215 - regression of bug 74174 changing a txt file outside of a
	 * source file triggers a build (either full or incremental) when it doesn't
	 * need to
	 */
	public void testBug98125() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject project = createPredefinedProject("bug99133b"); //$NON-NLS-1$
		assertFalse("project should have no errors", testLog //$NON-NLS-1$
				.containsMessage("error")); //$NON-NLS-1$
		IFile f = project.getFile("test.txt"); //$NON-NLS-1$
		assertNotNull("file test.txt should not be null", f); //$NON-NLS-1$
		assertTrue("text file should exist", f.exists()); //$NON-NLS-1$

		int numberOfBuildsRun = testLog.getNumberOfBuildsRun();

		// add more text to the file
		StringReader sr = new StringReader("more blah blah blah"); //$NON-NLS-1$
		f.appendContents(new ReaderInputStream(sr), IResource.FORCE, null);

		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();

		// check that we have gone through the AJBuilder.build(..) method
		// and that there are no errors reported
		assertEquals(
				"The number of builds should be " + (numberOfBuildsRun + 1), numberOfBuildsRun + 1, testLog.getNumberOfBuildsRun()); //$NON-NLS-1$
		List<String> buildLog = testLog.getPreviousBuildEntry(1);

		// This is the message AJDT put's out when it decides not
		// to do a build. It thinks there are no src changes in the
		// current project.
		// NOTE: this will fail if we decide that AJDT can't make these
		// sorts of decisions and pass everything down to the compiler
		// (who can).
		assertTrue(
				"AJDT should have found no source file changes and decided not to build", //$NON-NLS-1$
				listContainsString(buildLog, "build: Examined delta - no " + //$NON-NLS-1$
						"source file or classpath changes for project bug99133b")); //$NON-NLS-1$
		assertFalse("There should be no errors in the build log", //$NON-NLS-1$
				listContainsString(buildLog, "error")); //$NON-NLS-1$

		// by checking that we don't have the following messages in the
		// log (and the previous checking for no errors) we know that
		// AJDT has exited the build method before calling the compiler.
		// NOTE: these will fail if we decide that AJDT can't make these
		// sorts of decisions and pass everything down to the compiler
		// (who can).
		boolean inc = listContainsString(buildLog,
				"AspectJ reports build successful, build was: INCREMENTAL"); //$NON-NLS-1$
		assertFalse("AJDT should have returned from the build without " + //$NON-NLS-1$
				"going through the compiler, therefore AspectJ shouldn't " + //$NON-NLS-1$
				"report that an incremental build happened", inc); //$NON-NLS-1$
		boolean full = listContainsString(buildLog,
				"AspectJ reports build successful, build was: FULL"); //$NON-NLS-1$
		assertFalse("AJDT should have returned from the build without " + //$NON-NLS-1$
				"going through the compiler, therefore AspectJ shouldn't " + //$NON-NLS-1$
				"report that a full build happened", full); //$NON-NLS-1$
	}

	/**
	 * Differs from testBug98215 in that it has two depedent projects A
	 * depending on B and the text file which changes is in B. In this case,
	 * AJDT should return for project B that there are no src folder changes and
	 * it doesn't do a build. However, it should do an incremental build of
	 * project A. (both A and B are aspectj projects)
	 */
	public void testBug98125WithDependingProjects() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		IProject pB = createPredefinedProject("bug99133b"); //$NON-NLS-1$
		waitForAutoBuild();
		waitForAutoBuild();
		createPredefinedProject("bug99133a"); //$NON-NLS-1$
		waitForAutoBuild();
		waitForAutoBuild();
		assertFalse("project should have no errors", testLog //$NON-NLS-1$
				.containsMessage("error")); //$NON-NLS-1$
		IFile f = pB.getFile("test.txt"); //$NON-NLS-1$
		assertNotNull("file test.txt should not be null", f); //$NON-NLS-1$
		assertTrue("text file should exist", f.exists()); //$NON-NLS-1$

		int numberOfBuildsRun = testLog.getNumberOfBuildsRun();

		// add more text to the file
		StringReader sr = new StringReader("more blah blah blah"); //$NON-NLS-1$
		f.appendContents(new ReaderInputStream(sr), IResource.FORCE, null);

		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();
		waitForAutoBuild();

		// check that we have gone through the AJBuilder.build(..) method
		// and that there are no errors reported
		assertEquals(
				"The number of builds should be " + (numberOfBuildsRun + 2), numberOfBuildsRun + 2, testLog.getNumberOfBuildsRun()); //$NON-NLS-1$
		assertFalse("There should be no errors in the build log", //$NON-NLS-1$
				testLog.containsMessage("error")); //$NON-NLS-1$

		List<String> buildLogB = testLog.getPreviousBuildEntry(2);
		assertTrue("Should have tried to build project bug99133b", //$NON-NLS-1$
				listContainsString(buildLogB, "bug99133b")); //$NON-NLS-1$

		// This is the message AJDT put's out when it decides not
		// to do a build. It thinks there are no src changes in the
		// current project.
		// NOTE: this will fail if we decide that AJDT can't make these
		// sorts of decisions and pass everything down to the compiler
		// (who can).
		assertTrue(
				"AJDT should have found no source file changes and decided not to build", //$NON-NLS-1$
				listContainsString(buildLogB, "build: Examined delta - no " + //$NON-NLS-1$
						"source file or classpath changes for project bug99133b")); //$NON-NLS-1$

		// by checking that we don't have the following messages in the
		// log (and the previous checking for no errors) we know that
		// AJDT has exited the build method before calling the compiler.
		// NOTE: these will fail if we decide that AJDT can't make these
		// sorts of decisions and pass everything down to the compiler
		// (who can).
		boolean inc = listContainsString(buildLogB,
				"AspectJ reports build successful, build was: INCREMENTAL"); //$NON-NLS-1$
		assertFalse("AJDT should have returned from the build without " + //$NON-NLS-1$
				"going through the compiler, therefore AspectJ shouldn't " + //$NON-NLS-1$
				"report that an incremental build happened", inc); //$NON-NLS-1$
		boolean full = listContainsString(buildLogB,
				"AspectJ reports build successful, build was: FULL"); //$NON-NLS-1$
		assertFalse("AJDT should have returned from the build without " + //$NON-NLS-1$
				"going through the compiler, therefore AspectJ shouldn't " + //$NON-NLS-1$
				"report that a full build happened", full); //$NON-NLS-1$

		List<String> buildLogA = testLog.getPreviousBuildEntry(1);
		assertTrue("Should have caused a build of project bug99133a", //$NON-NLS-1$
				listContainsString(buildLogA, "bug99133a")); //$NON-NLS-1$

		boolean incA = listContainsString(buildLogA,
				"AspectJ reports build successful, build was: INCREMENTAL"); //$NON-NLS-1$
		assertFalse("AJDT should not have called the compiler, and therefore " //$NON-NLS-1$
				+ "AspectJ should not have reported that an " //$NON-NLS-1$
				+ "incremental build happened", incA); //$NON-NLS-1$
		boolean fullA = listContainsString(buildLogA,
				"AspectJ reports build successful, build was: FULL"); //$NON-NLS-1$
		assertFalse("AspectJ should have reported an incremental build " //$NON-NLS-1$
				+ "occurred, not a FULL build", fullA); //$NON-NLS-1$
	}

	/**
	 * Test for by 107027 - we didn't flush the classpath after building when
	 * just running with the core plugin
	 */
	public void testBug107027() throws Exception {
		TestLogger testLog = new TestLogger();
		AspectJPlugin.getDefault().setAJLogger(testLog);
		createPredefinedProject("bug99133a"); //$NON-NLS-1$
		waitForAutoBuild();
		IProject bean = createPredefinedProject("Bean Example"); //$NON-NLS-1$
		waitForAutoBuild();
		CoreCompilerConfiguration adapter = (CoreCompilerConfiguration) AspectJPlugin.
			getDefault().getCompilerFactory().getCompilerForProject(bean).getCompilerConfiguration();
		// expect the classpath to mention the "Bean Example" if everything
		// has been flushed correctly. Otherwise it will just contain
		// the flushed version which is the classpath for project bug99133a.
		assertTrue("classpath should have been flushed but wasn't", adapter //$NON-NLS-1$
      .getClasspath().contains("Bean")); //$NON-NLS-1$
	}

	// bug 161739 don't copy resources excluded by a pattern
	public void testBug161739() throws Exception {
		IProject project = createPredefinedProject("bug161739"); //$NON-NLS-1$
		IFolder src = project.getFolder("src"); //$NON-NLS-1$
		assertTrue("couldn't find src folder", src.exists()); //$NON-NLS-1$
		IFolder bin = project.getFolder("bin"); //$NON-NLS-1$
		assertTrue("bin folder should have been created", bin.exists()); //$NON-NLS-1$
		IFolder testBin = bin.getFolder("test"); //$NON-NLS-1$
		assertTrue("bin/test folder should have been created", testBin.exists()); //$NON-NLS-1$
		IFile hello = testBin.getFile("Hello.class"); //$NON-NLS-1$
		assertTrue(
				"bin/test/Hello.class should have been created", hello.exists()); //$NON-NLS-1$

		// create folders which should be copied to bin
		IFolder notExcluded = src.getFolder("ABCD"); //$NON-NLS-1$
		assertFalse("folder should not exist yet: " + notExcluded, notExcluded //$NON-NLS-1$
				.exists());
		notExcluded.create(true, true, null);
		assertTrue(
				"folder should now exist: " + notExcluded, notExcluded.exists()); //$NON-NLS-1$

		IFolder test = src.getFolder("test"); //$NON-NLS-1$
		assertTrue("couldn't find src/test folder", test.exists()); //$NON-NLS-1$
		IFolder notExcluded2 = test.getFolder("ABCD"); //$NON-NLS-1$
		assertFalse("folder should not exist yet: " + notExcluded2, notExcluded2 //$NON-NLS-1$
				.exists());
		notExcluded2.create(true, true, null);
		assertTrue(
				"folder should now exist: " + notExcluded2, notExcluded2.exists()); //$NON-NLS-1$

		// create folders which should not be copied to bin (they are excluded)
		IFolder excluded = src.getFolder("SCCS"); //$NON-NLS-1$
		assertFalse(
				"folder should not exist yet: " + excluded, excluded.exists()); //$NON-NLS-1$
		excluded.create(true, true, null);
		assertTrue("folder should now exist: " + excluded, excluded.exists()); //$NON-NLS-1$

		IFolder excluded2 = test.getFolder("SCCS"); //$NON-NLS-1$
		assertFalse(
				"folder should not exist yet: " + excluded2, excluded2.exists()); //$NON-NLS-1$
		excluded2.create(true, true, null);
		assertTrue("folder should now exist: " + excluded2, excluded2.exists()); //$NON-NLS-1$

		waitForAutoBuild();

		// check that the expected folders were copied to bin
		IFolder notExcludedBin = bin.getFolder("ABCD"); //$NON-NLS-1$
		assertTrue(
				"folder should have been copied to bin: " + notExcludedBin, //$NON-NLS-1$
				notExcludedBin.exists());
		IFolder notExcludedBin2 = testBin.getFolder("ABCD"); //$NON-NLS-1$
		assertTrue(
				"folder should have been copied to bin: " + notExcludedBin2, //$NON-NLS-1$
				notExcludedBin2.exists());

		// check that the excluded folders were not copied to bin
		IFolder excludedBin = bin.getFolder("SCCS"); //$NON-NLS-1$
		assertFalse(
				"excluded folder should NOT have been copied to bin: " + excludedBin, //$NON-NLS-1$
				excludedBin.exists());
		IFolder excludedBin2 = testBin.getFolder("SCCS"); //$NON-NLS-1$
		assertFalse(
				"excluded folder should NOT have been copied to bin: " + excludedBin2, //$NON-NLS-1$
				excludedBin2.exists());

		// now test files instead of folders

		// create a file that should be copied
		IFile notExcludedFile = notExcluded2.getFile("hello.txt");  //$NON-NLS-1$
		notExcludedFile.create(new ReaderInputStream(new StringReader("test")), true, null); //$NON-NLS-1$

		// create a file that should be not copied
		IFile excludedFile = excluded2.getFile("hello.txt"); //$NON-NLS-1$
		excludedFile.create(new ReaderInputStream(new StringReader("test")), true, null); //$NON-NLS-1$

		project.build(IncrementalProjectBuilder.CLEAN_BUILD, null);
		waitForAutoBuild();

		// check that the expected file was copied to bin
		IFile notExcludedFileBin = notExcludedBin2.getFile("hello.txt");//$NON-NLS-1$
		assertTrue(
				"file should have been copied to bin: " + notExcludedFileBin, //$NON-NLS-1$
				notExcludedFileBin.exists());

		// check that the excluded file was not copied to bin
		// - only need to check for the directory - if that is not created (as
		// expected) then the file couldn't have been created either
		excludedBin = testBin.getFolder("SCCS"); //$NON-NLS-1$
		assertFalse(
				"excluded folder should NOT have been copied to bin: " + excludedBin, //$NON-NLS-1$
				excludedBin.exists());
	}

	private boolean wasIncrementalBuild(String msg) {
		return msg.toLowerCase().contains("was: incremental"); //$NON-NLS-1$
	}

	private boolean listContainsString(List<String> l, String msg) {
    for (String logEntry : l) {
      if (logEntry.contains(msg)) {
        return true;
      }
    }
		return false;
	}
}
