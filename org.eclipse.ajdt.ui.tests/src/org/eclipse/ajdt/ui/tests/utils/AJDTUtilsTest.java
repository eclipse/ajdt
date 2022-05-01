/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.internal.core.ajde.FileURICache;
import org.eclipse.ajdt.internal.ui.preferences.AspectJPreferences;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.pde.core.plugin.IPluginImport;
import org.eclipse.pde.core.plugin.IPluginModel;
import org.eclipse.pde.internal.ui.editor.plugin.ManifestEditor;

/**
 * @author hawkinsh
 *
 */
public class AJDTUtilsTest extends UITestCase {

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
        // avoid prompting for depency removal.
        AspectJPreferences.setAskPDEAutoRemoveImport(false);
        // automatically remove import from classpath
        AspectJPreferences.setDoPDEAutoRemoveImport(true);
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

//This test disabled because it fails because Eclipse 4.4 no longer supports 'pre-OSGI' plugins.
// See https://wiki.eclipse.org/Equinox/Luna_Framework (Removal of Old Style Plugin Support)
//	/**
//	 * Occasional failures on build server try rerunning the test
//	 */
//	public void testAddAndRemoveAspectJNatureWithPluginProject()
//			throws Exception {
//	    Runnable run = new Runnable() {
//            public void run() {
//                setUpPluginEnvironment();
//                try {
//                    IProject testPluginProject = createPredefinedProject("Hello World Java Plugin"); //$NON-NLS-1$
//                    waitForJobsToComplete();
//                    assertFalse("Plugin project shouldn't have AspectJ nature", //$NON-NLS-1$
//                            AspectJPlugin.isAJProject(testPluginProject.getProject()));
//                    assertFalse("Plugin should not import AJDE plugin", //$NON-NLS-1$
//                            hasDependencyOnAJDE(testPluginProject));
//                    AspectJUIPlugin.convertToAspectJProject(testPluginProject.getProject());
//                    waitForJobsToComplete();
//                    assertTrue("Plugin project should now have AspectJ nature", //$NON-NLS-1$
//                            AspectJPlugin.isAJProject(testPluginProject.getProject()));
//                    assertTrue("Plugin should now import AJDE plugin", //$NON-NLS-1$
//                            hasDependencyOnAJDE(testPluginProject));
//                    AspectJUIPlugin.convertFromAspectJProject(testPluginProject.getProject());
//                    waitForJobsToComplete();
//                    assertFalse("Plugin should not import AJDE plugin", //$NON-NLS-1$
//                            hasDependencyOnAJDE(testPluginProject));
//                    assertFalse("Plugin project shouldn't have AspectJ nature", //$NON-NLS-1$
//                            AspectJPlugin.isAJProject(testPluginProject.getProject()));
//                } catch (CoreException e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    resetPluginEnvironment();
//                }
//            }
//        };
//
//        int numTries = 0;
//        while (true) {
//            try {
//                run.run();
//                break;
//            } catch (AssertionFailedError e) {
//                if (numTries >= 4) {
//                    throw e;
//                }
//                numTries++;
//                System.out.println("Failed...trying again.");
//            }
//        }
//	}

	// bug 137922: test with a bundle project with has no plugin.xml
	public void testAddAndRemoveAspectJNatureWithBundleProject()
			throws Exception {
		setUpPluginEnvironment();
		IProject testPluginProject = createPredefinedProject("Hello World Java Bundle"); //$NON-NLS-1$
		waitForJobsToComplete();
		assertFalse("Plugin project shouldn't have AspectJ nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testPluginProject.getProject()));
		assertFalse("Plugin should not import AJDE plugin", //$NON-NLS-1$
				hasDependencyOnAJDE(testPluginProject));
		AspectJUIPlugin.convertToAspectJProject(testPluginProject.getProject());
		waitForJobsToComplete();
		waitForJobsToComplete();
		assertTrue("Plugin project should now have AspectJ nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testPluginProject.getProject()));
		assertTrue("Plugin should now import AJDE plugin", //$NON-NLS-1$
				hasDependencyOnAJDE(testPluginProject));
		AspectJUIPlugin.convertFromAspectJProject(testPluginProject
				.getProject());
		waitForJobsToComplete();
		waitForJobsToComplete();

		assertFalse("Plugin should not import AJDE plugin", //$NON-NLS-1$
				hasDependencyOnAJDE(testPluginProject));
		assertFalse("Plugin project shouldn't have AspectJ nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testPluginProject.getProject()));
		resetPluginEnvironment();
	}

	public void testAddAndRemoveAspectJNature() throws CoreException {
		IProject testProject = createPredefinedProject("project.java.Y"); //$NON-NLS-1$
		IJavaProject jY = JavaCore.create(testProject);
		waitForJobsToComplete();

		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.convertToAspectJProject(testProject.getProject());
		assertTrue("Java project should now have AspectJ Nature", AspectJPlugin //$NON-NLS-1$
				.isAJProject(testProject.getProject()));
		assertTrue("Build path should now contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.convertFromAspectJProject(testProject.getProject());
		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar",hasAjrtOnBuildPath(jY)); //$NON-NLS-1$
	}

	/**
	 * Tests fix for 129553: removing aj nature should exclude .aj files
	 * @throws CoreException
	 */
	public void testAddAndRemoveWithExcludes() throws CoreException {
		IProject project = createPredefinedProject("MultipleSourceFoldersWithAspects"); //$NON-NLS-1$
		assertTrue("Project should have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(project.getProject()));

		IResource a1 = project.findMember("src/pack/A1.aj"); //$NON-NLS-1$
		assertNotNull("Couldn't find A1.aj",a1); //$NON-NLS-1$
		IResource class1 = project.findMember("src/Class1.java"); //$NON-NLS-1$
		assertNotNull("Couldn't find Class1.java",class1); //$NON-NLS-1$
		IResource class2 = project.findMember("src/pack/Class2.java"); //$NON-NLS-1$
		assertNotNull("Couldn't find Class2.java",class2); //$NON-NLS-1$
		IResource a2 = project.findMember("src2/pack/A2.aj"); //$NON-NLS-1$
		assertNotNull("Couldn't find A2.aj",a2); //$NON-NLS-1$
		IResource class3 = project.findMember("src2/pack/Class3.java"); //$NON-NLS-1$
		assertNotNull("Couldn't find Class3.java",class3); //$NON-NLS-1$

		// everything should be included
		assertTrue("A1.aj should be included",BuildConfig.isIncluded(a1)); //$NON-NLS-1$
		assertTrue("Class1.java should be included",BuildConfig.isIncluded(class1)); //$NON-NLS-1$
		assertTrue("Class2.java should be included",BuildConfig.isIncluded(class2)); //$NON-NLS-1$
		assertTrue("A2.aj should be included",BuildConfig.isIncluded(a2)); //$NON-NLS-1$
		assertTrue("Class3.java should be included",BuildConfig.isIncluded(class3)); //$NON-NLS-1$

		AspectJUIPlugin.convertFromAspectJProject(project.getProject());

		// removing nature should exclude .aj files
		assertFalse("A1.aj should be excluded",BuildConfig.isIncluded(a1)); //$NON-NLS-1$
		assertTrue("Class1.java should be included",BuildConfig.isIncluded(class1)); //$NON-NLS-1$
		assertTrue("Class2.java should be included",BuildConfig.isIncluded(class2)); //$NON-NLS-1$
		assertFalse("A2.aj should be excluded",BuildConfig.isIncluded(a2)); //$NON-NLS-1$
		assertTrue("Class3.java should be included",BuildConfig.isIncluded(class3)); //$NON-NLS-1$

		AspectJUIPlugin.convertToAspectJProject(project.getProject());

		// everything should now be included again
		assertTrue("A1.aj should be included",BuildConfig.isIncluded(a1)); //$NON-NLS-1$
		assertTrue("Class1.java should be included",BuildConfig.isIncluded(class1)); //$NON-NLS-1$
		assertTrue("Class2.java should be included",BuildConfig.isIncluded(class2)); //$NON-NLS-1$
		assertTrue("A2.aj should be included",BuildConfig.isIncluded(a2)); //$NON-NLS-1$
		assertTrue("Class3.java should be included",BuildConfig.isIncluded(class3)); //$NON-NLS-1$
	}

	/**
	 * Tests fix for 129553: removing aj nature should exclude .aj files
	 * @throws CoreException
	 */
	public void testAddAndRemoveWithExcludes2() throws CoreException {
		IProject project = createPredefinedProject("WithoutSourceFolder"); //$NON-NLS-1$
		assertTrue("Project should have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(project.getProject()));

		IResource a = project.findMember("A.aj"); //$NON-NLS-1$
		assertNotNull("Couldn't find A.aj",a); //$NON-NLS-1$
		IResource c = project.findMember("C.java"); //$NON-NLS-1$
		assertNotNull("Couldn't find C.java",c); //$NON-NLS-1$

		AspectJUIPlugin.convertFromAspectJProject(project.getProject());

		// removing nature should exclude .aj files
		assertFalse("A.aj should be excluded",BuildConfig.isIncluded(a)); //$NON-NLS-1$
		assertTrue("C.java should be included",BuildConfig.isIncluded(c)); //$NON-NLS-1$

		AspectJUIPlugin.convertToAspectJProject(project.getProject());

		// everything should now be included again
		assertTrue("A.aj should be included",BuildConfig.isIncluded(a)); //$NON-NLS-1$
		assertTrue("C.java should be included",BuildConfig.isIncluded(c)); //$NON-NLS-1$
	}

	/**
	 * Test for bug 93532 - NPE when add aspectj nature to a plugin project
	 * which doesn't have a plugin.xml file.
	 *
	 * @throws Exception
	 */
	public void testBug93532() throws Exception {
		IProject testProject = createPredefinedProject("bug93532"); //$NON-NLS-1$
		IJavaProject jY = JavaCore.create(testProject);
		waitForJobsToComplete();

		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.convertToAspectJProject(testProject.getProject());
		assertTrue("Java project should now have AspectJ Nature", AspectJPlugin //$NON-NLS-1$
				.isAJProject(testProject.getProject()));
		assertTrue("Build path should now contain aspectjrt.jar", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.convertFromAspectJProject(testProject.getProject());
		assertFalse("Java project should not have AspectJ Nature", //$NON-NLS-1$
				AspectJPlugin.isAJProject(testProject.getProject()));
		assertFalse("Build path shouldn't contain aspectjrt.jar",hasAjrtOnBuildPath(jY)); //$NON-NLS-1$
	}

//This test disabled because it fails because Eclipse 4.4 no longer supports 'pre-OSGI' plugins.
// See https://wiki.eclipse.org/Equinox/Luna_Framework (Removal of Old Style Plugin Support)
//	/**
//	 * This tests whether you get back the manifest editor for the project you
//	 * require.
//	 *
//	 */
//	public void testGetPDEManifestEditor() throws Exception {
//		setUpPluginEnvironment();
//		// know that the plugin id of this is HelloWorld
//		IProject projectA1 = createPredefinedProject("Hello World Java Plugin"); //$NON-NLS-1$
//		waitForJobsToComplete();
//
//		// know that the plugin id for this is PluginWithView
//		IProject projectA2 = createPredefinedProject("PluginWithView"); //$NON-NLS-1$
//		waitForJobsToComplete();
//
//		assertTrue("projectA1 should have manifest editor for project A1", //$NON-NLS-1$
//				AJDTUtils.getAndPrepareToChangePDEModel(projectA1.getProject())
//						.getPartName().equals("HelloWorld")); //$NON-NLS-1$
//		assertTrue("projectA2 should have manifest editor for project A2", //$NON-NLS-1$
//				AJDTUtils.getAndPrepareToChangePDEModel(projectA2.getProject())
//						.getPartName().equals("PluginWithView")); //$NON-NLS-1$
//		resetPluginEnvironment();
//	}

	// Do not delete this test - if we ever change the way we deal with
	// project dependencies, then need this test
	// We now longer change project dependencies in this way, so test removed
//	public void testChangeProjectToClassDependencies() throws Exception {
//		JavaTestProject jtp1 = new JavaTestProject("JavaTestProject1");
//		Utils.waitForJobsToComplete();
//		JavaTestProject jtp2 = new JavaTestProject("JavaTestProject2");
//		Utils.waitForJobsToComplete();
//		// this ensures a src folder is created.
//		jtp2.getSourceFolder();
//		Utils.waitForJobsToComplete();
//		ProjectDependenciesUtils.addProjectDependency(jtp1.getJavaProject(),
//				jtp2.getProject());
//		Utils.waitForJobsToComplete();
//		assertTrue("test project 1 has a project dependency on test project 2",
//				checkDependencyType(jtp1.getJavaProject(), jtp2.getProject())
//						.equals("project"));
//		AJDTUtils.changeProjectDependencies(jtp2.getProject());
//		Utils.waitForJobsToComplete();
//		assertTrue(
//				"test project 1 has a class folder dependency on test project 2",
//				checkDependencyType(jtp1.getJavaProject(), jtp2.getProject())
//						.equals("classfolder"));
//		jtp1.dispose();
//		jtp2.dispose();
//	}

	public void testAddAndRemoveAjrtToBuildPath() throws Exception {
		IProject projectY = createPredefinedProject("project.java.Y"); //$NON-NLS-1$
		IJavaProject jY = JavaCore.create(projectY);
		waitForJobsToComplete();

		assertFalse("project.java.Y should not have ajrt on build path", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
		AspectJUIPlugin.addAjrtToBuildPath(projectY);
		waitForJobsToComplete();

		assertTrue("project.java.Y should have ajrt on build path", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));

		AspectJUIPlugin.removeAjrtFromBuildPath(projectY);
		waitForJobsToComplete();
		assertFalse("project.java.Y should not have ajrt on build path", //$NON-NLS-1$
				hasAjrtOnBuildPath(jY));
	}

	private boolean hasAjrtOnBuildPath(IJavaProject javaProject) {
		try {
			IClasspathEntry[] originalCP = javaProject.getRawClasspath();
      for (IClasspathEntry iClasspathEntry : originalCP) {
        if (iClasspathEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
          IPath path = iClasspathEntry.getPath();
          if (path.segment(0).equals(AspectJPlugin.ASPECTJRT_CONTAINER)) {
            return true;
          }
        }
      }
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
		return false;
	}

	// private void addImportToPDEModel(IPluginModel model, String importId)
	// throws CoreException {
	//
	// IPluginImport importNode = model.getPluginFactory().createImport();
	// importNode.setId(importId);
	// model.getPluginBase().getImports();
	// model.getPluginBase().add(importNode);
	//
	// IFile manifestFile = (IFile) model.getUnderlyingResource();
	// manifestFile.refreshLocal(IResource.DEPTH_INFINITE, null);
	// Utils.waitForJobsToComplete();
	// }

	private boolean hasDependencyOnAJDE(IProject project) {
		ManifestEditor manEd = AJDTUtils
				.getAndPrepareToChangePDEModel(project);
		if (manEd == null) {
			return false;
		}
		IPluginModel model = (IPluginModel) manEd.getAggregateModel();
		IPluginImport[] imports = model.getPluginBase().getImports();

    for (IPluginImport importObj : imports) {
      if (importObj.getId().equals(AspectJPlugin.RUNTIME_PLUGIN_ID)) {
        return true;
      }
    }
		return false;
	}

	public static class MyJobChangeListener implements IJobChangeListener {

		private final List<Job> scheduledBuilds = new ArrayList<>();

		public void aboutToRun(IJobChangeEvent event) {
		}

		public void awake(IJobChangeEvent event) {
		}

		public void done(IJobChangeEvent event) {
			if (event.getJob().getPriority() == Job.BUILD) {
				System.out.println(">> finished a build"); //$NON-NLS-1$
				scheduledBuilds.remove(event.getJob());
			}

		}

		public void running(IJobChangeEvent event) {
		}

		public void scheduled(IJobChangeEvent event) {
			if (event.getJob().getPriority() == Job.BUILD) {
				System.out.println(">> scheduled a build"); //$NON-NLS-1$
				scheduledBuilds.add(event.getJob());
			}
		}

		public void sleeping(IJobChangeEvent event) {
		}

		public boolean buildsAreScheduled() {
			return !(scheduledBuilds.isEmpty());
		}

	}

	/* bug 82258 */
	public void testCaseInsensitiveDriveLetters() throws Exception {
		IProject project = createPredefinedProject("Hello World Project"); //$NON-NLS-1$
		FileURICache fileCache = new FileURICache(project);
		// create two paths, one where the drive letter (if there is one) has a
		// different case to the other
		String fullpath1 = project.getLocation().toOSString() + File.separator
				+ "src" + File.separator + "HelloWorld.java"; //$NON-NLS-1$ //$NON-NLS-2$
		String fullpath2;

		// check for windows style drive letter
		if ((fullpath1.charAt(1) == ':')
				&& (((fullpath1.charAt(0) >= 'a') && (fullpath1.charAt(0) <= 'z')) || ((fullpath1
						.charAt(0) >= 'A') && (fullpath1.charAt(0) <= 'Z')))) {
			if (Character.isUpperCase(fullpath1.charAt(0))) {
				fullpath2 = Character.toLowerCase(fullpath1.charAt(0))
						+ fullpath1.substring(1);
			} else {
				fullpath2 = Character.toUpperCase(fullpath1.charAt(0))
						+ fullpath1.substring(1);
			}
		} else {
			fullpath2 = fullpath1;
		}

		// now make sure both versions of the path cause the resource to be
		// found
		IResource res1 = fileCache.findResource(fullpath1, project);
		assertNotNull(
				"Regression of bug 82258: handling of windows-style drive letters", res1); //$NON-NLS-1$

		IResource res2 = fileCache.findResource(fullpath2, project);
		assertNotNull(
				"Regression of bug 82258: handling of windows-style drive letters", res2); //$NON-NLS-1$

	}

	/**
	 * Bug 82341
	 */
	public void testCaseInsensitive() throws Exception {
		IProject project = createPredefinedProject("Hello World Project"); //$NON-NLS-1$
        FileURICache fileCache = new FileURICache(project);

        // create two paths, one where the drive letter (if there is one) has a
		// different case to the other
		String fullpath1 = project.getLocation().toOSString() + File.separator
				+ "src" + File.separator + "HelloWorld.java"; //$NON-NLS-1$ //$NON-NLS-2$
		String fullpath2;

		// if on windows then change the case
		if ((fullpath1.charAt(1) == ':')) {
			fullpath2 = project.getLocation().toOSString().toUpperCase()
					+ File.separator
					+ "src" + File.separator + "HelloWorld.java"; //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			fullpath2 = fullpath1;
		}
		// now make sure both the resources can be found
		IResource res1 = fileCache.findResource(fullpath1, project);
		assertNotNull("Regression of bug 82341", res1); //$NON-NLS-1$

		IResource res2 = fileCache.findResource(fullpath2, project);
		assertNotNull("Regression of bug 82341", res2); //$NON-NLS-1$
	}

	public void testCaseInsensitiveNoSrcFolder() throws Exception {
		IProject project = createPredefinedProject("WithoutSourceFolder"); //$NON-NLS-1$
        FileURICache fileCache = new FileURICache(project);

		// create two paths, one where the drive letter (if there is one) has a
		// different case to the other
		String fullpath1 = project.getLocation().toOSString() + File.separator
				+ "C.java"; //$NON-NLS-1$
		String fullpath2;

		// if on windows then change the case
		if ((fullpath1.charAt(1) == ':')) {
			fullpath2 = project.getLocation().toOSString().toUpperCase()
					+ File.separator + "C.java"; //$NON-NLS-1$
		} else {
			fullpath2 = fullpath1;
		}
		// now make sure both the resources can be found
		IResource res1 = fileCache.findResource(fullpath1, project);
		assertNotNull("Regression of bug 82341", res1); //$NON-NLS-1$

		IResource res2 = fileCache.findResource(fullpath2, project);
		assertNotNull("Regression of bug 82341", res2); //$NON-NLS-1$

	}

}
