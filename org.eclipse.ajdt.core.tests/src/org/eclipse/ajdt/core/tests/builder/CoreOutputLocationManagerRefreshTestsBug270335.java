/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: SpringSource - initial API and implementation
 *              Andrew Eisenberg
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.builder;

import org.aspectj.ajde.core.AjCompiler;
import org.aspectj.ajde.core.IOutputLocationManager;
import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.core.tests.testutils.Utils;
import org.eclipse.ajdt.internal.core.ajde.CoreBuildMessageHandler;
import org.eclipse.ajdt.internal.core.ajde.CoreBuildProgressMonitor;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerConfiguration;
import org.eclipse.ajdt.internal.core.ajde.CoreCompilerFactory;
import org.eclipse.ajdt.internal.core.ajde.CoreOutputLocationManager;
import org.eclipse.ajdt.internal.core.ajde.FileURICache;
import org.eclipse.ajdt.internal.core.ajde.ICompilerFactory;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;

/**
 * Tests bug 270335 that the CoreOutputLocationManager should
 * be refreshed appropriately when certain configuration changes happen
 *
 * @author andrew
 *
 */
public class CoreOutputLocationManagerRefreshTestsBug270335 extends AJDTCoreTestCase {

    private static class MockCoreOutputLocationManager extends CoreOutputLocationManager {
        private boolean iGotZapped = false;
        public MockCoreOutputLocationManager(IProject project, FileURICache fileCache) {
            super(project, fileCache);
        }

        protected void zapBinFolderToProjectMap() {
            super.zapBinFolderToProjectMap();
            iGotZapped = true;
        }

        boolean zapped() {
            return iGotZapped;
        }

    }


    private class MockCoreCompilerConfiguration extends CoreCompilerConfiguration {

        boolean iGotFlushed = false;

        public MockCoreCompilerConfiguration(IProject project) {
            super(project);
        }

        public IOutputLocationManager getOutputLocationManager() {
            if (locationManager == null) {
                locationManager = new MockCoreOutputLocationManager(project, new FileURICache(project));
            }
            return locationManager;
        }

        public boolean flushOutputLocationManagerIfNecessary(int buildKind) {
            boolean val = super.flushOutputLocationManagerIfNecessary(buildKind);
            iGotFlushed |= val;
            return val;

        }

        boolean flushed() {
          return iGotFlushed;
        }
        void unflush() {
            iGotFlushed = false;
        }
    }

    private class MockCompilerFactory extends CoreCompilerFactory {
        protected AjCompiler createCompiler(IProject project) {
          return new AjCompiler(
                  project.getName(),
                  new MockCoreCompilerConfiguration(project),
                  new CoreBuildProgressMonitor(project),
                  new CoreBuildMessageHandler());
        }
    }


    ICompilerFactory origFactory;

    IProject proj1;
    IProject proj2;

    protected void setUp() throws Exception {
        super.setUp();
        origFactory = AspectJPlugin.getDefault().getCompilerFactory();
        AspectJPlugin.getDefault().setCompilerFactory(new MockCompilerFactory());

        Utils.setAutobuilding(true);

        proj2 = createPredefinedProject("ExportAsJar");
        AspectJCorePreferences.setProjectOutJar(proj2, "export.jar");
        proj2.build(IncrementalProjectBuilder.FULL_BUILD, null);
        proj1 = createPredefinedProject("JarOnInpath");
        waitForAutoBuild();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        AspectJPlugin.getDefault().setCompilerFactory(origFactory);
    }

    /**
     * should cause location manager to be refreshed
     */
    public void testClasspathChanged() throws Exception {
        // first build of proj1 is full build, so should have been flushed, but is a noop since started out as null
        assertTrue(proj1 + "'s OutputLocationManager should have been flushed", hasBeenFlushed());
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped", hasBeenZapped());
        unflush();

        proj1.getFile(".classpath").touch(null);
        waitForAutoBuild();

        assertTrue(proj1 + "'s OutputLocationManager should not have been flushed after change to classpath", hasBeenFlushed());
    }

    /**
     * should cause location manager to be refreshed
     */
    public void testAspectpathChanged() throws Exception {
        // first build of proj1 is full build, so should have been flushed, but is a noop since started out as null
        assertTrue(proj1 + "'s OutputLocationManager should have been flushed", hasBeenFlushed());
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped", hasBeenZapped());
        unflush();
        AspectJCorePreferences.addToAspectPath(proj1, getClasspathEntry());
        waitForAutoBuild();

        assertTrue(proj1 + "'s OutputLocationManager should not have been flushed after change to aspect path", hasBeenFlushed());
    }

    /**
     * should cause location manager to be refreshed
     */
    public void testInpathChanged() throws Exception {
        // first build of proj1 is full build, so should have been flushed, but is a noop since started out as null
        assertTrue(proj1 + "'s OutputLocationManager should have been flushed", hasBeenFlushed());
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped", hasBeenZapped());
        unflush();
        AspectJCorePreferences.removeFromInPath(proj1, getClasspathEntry());
        waitForAutoBuild();

        assertTrue(proj1 + "'s OutputLocationManager should not have been flushed after change to in path", hasBeenFlushed());
    }

    /**
     * should cause location manager to be refreshed
     */
    public void testOutjarChangedChanged() {
        // first build of proj1 is full build, so should have been flushed, but is a noop since started out as null
        assertTrue(proj1 + "'s OutputLocationManager should have been flushed", hasBeenFlushed());
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped", hasBeenZapped());
        unflush();
        AspectJCorePreferences.setProjectOutJar(proj1, "out.jar");
        waitForAutoBuild();

        assertTrue(proj1 + "'s OutputLocationManager should not have been flushed after change to out jar", hasBeenFlushed());
    }

    /**
     * should cause location manager to be refreshed
     */
    public void testOutDestinationsChanged() throws Exception {
        // first build of proj1 is full build, so should have been flushed, but is a noop since started out as null
        assertTrue(proj1 + "'s OutputLocationManager should have been flushed", hasBeenFlushed());
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped", hasBeenZapped());
        unflush();
        JavaCore.create(proj1).setOutputLocation(proj1.getFile("bin2").getFullPath(), null);
        waitForAutoBuild();

        assertTrue(proj1 + "'s OutputLocationManager should not have been flushed after change to out location", hasBeenFlushed());
    }

    /**
     * should *not* cause location manager to be refreshed
     */
    public void testSourceChanged() throws Exception {
        // first build of proj1 is full build, so should have been flushed, but is a noop since started out as null
        assertTrue(proj1 + "'s OutputLocationManager should have been flushed", hasBeenFlushed());
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped", hasBeenZapped());
        unflush();
        proj1.getFile("src/A.aj").touch(null);
        waitForAutoBuild();

        assertFalse(proj1 + "'s OutputLocationManager should not have been flushed after source file change", hasBeenFlushed());
        assertTrue(proj1 + "'s OutputLocationManager's binToProject map should have been zapped after source file change", hasBeenZapped());
    }

    /**
     * should *not* cause location manager to be refreshed
     */
    public void testResourceChanged() throws Exception {
        // first build of proj1 is full build, so should have been flushed, but is a noop since started out as null
        assertTrue(proj1 + "'s OutputLocationManager should have been flushed", hasBeenFlushed());
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped", hasBeenZapped());
        unflush();
        proj1.getFolder("src/A.txt").create(true, true, null);
        waitForAutoBuild();

        assertFalse(proj1 + "'s OutputLocationManager should not have been flushed after resource file change", hasBeenFlushed());
        // resource change never makes it to compiler, so no zapping should happen
        assertFalse(proj1 + "'s OutputLocationManager's binToProject map should not have been zapped after resource file change", hasBeenZapped());
    }


    private boolean hasBeenFlushed() {
        return ((MockCoreCompilerConfiguration) AspectJPlugin.getDefault().getCompilerFactory().getCompilerForProject(proj1).getCompilerConfiguration()).flushed();
    }

    private boolean hasBeenZapped() {
        return ((MockCoreOutputLocationManager) AspectJPlugin.getDefault().getCompilerFactory()
                .getCompilerForProject(proj1).getCompilerConfiguration().getOutputLocationManager()).zapped();
    }
    private void unflush() {
        ((MockCoreCompilerConfiguration) AspectJPlugin.getDefault().getCompilerFactory()
                .getCompilerForProject(proj1).getCompilerConfiguration()).unflush();
    }

    private IClasspathEntry getClasspathEntry() throws Exception {
        IClasspathEntry[] entries = JavaCore.create(proj1).getRawClasspath();

      for (IClasspathEntry entry : entries) {
        if (entry.getPath().toPortableString().endsWith("export.jar")) {
          return entry;
        }
      }

        fail("Couldn't find classpath entry");
        return null;
    }

}
