/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January    - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core.tests.newbuildconfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.List;
import java.util.Set;

import org.aspectj.asm.IProgramElement;
import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.core.buildpath.BuildConfigurationUtils;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.core.tests.HandleTestUtils;
import org.eclipse.ajdt.core.tests.testutils.ReaderInputStream;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;

public class BuildConfigurationTest2 extends AJDTCoreTestCase {


	public void testExcludeAllWithNoPackages() throws Exception {
		IProject project = createPredefinedProject("WithoutSourceFolder"); //$NON-NLS-1$
		waitForAutoBuild();
		checkIncluded(project, 2);
		IFile propertiesFile = project.getFile("none.ajproperties"); //$NON-NLS-1$
		propertiesFile.create(new ReaderInputStream(new StringReader("src.includes = /\n" + //$NON-NLS-1$
				"src.excludes = A.aj,\\\n" +  //$NON-NLS-1$
                "C.java")), true, null); //$NON-NLS-1$
		waitForAutoBuild();
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());

		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForAutoBuild();
		checkIncluded(project, 0);

		IFile newPropertiesFile = project.getFile("none2.ajproperties"); //$NON-NLS-1$
		BuildConfigurationUtils.saveBuildConfiguration(newPropertiesFile);
		newPropertiesFile.refreshLocal(1, null);
		waitForAutoBuild();
		compareFiles(propertiesFile, newPropertiesFile);
	}

	public void testExcludeAllWithPackages() throws Exception {
		IProject project = createPredefinedProject("Figures Demo"); //$NON-NLS-1$
		waitForAutoBuild();
		checkIncluded(project, 13);
		IFile propertiesFile = project.getFile("none.ajproperties"); //$NON-NLS-1$
		propertiesFile.create(new ReaderInputStream(new StringReader("src.includes = /\n" + //$NON-NLS-1$
				"src.excludes = figures/")), true, null); //$NON-NLS-1$
		waitForAutoBuild();
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());

		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForAutoBuild();
		checkIncluded(project, 0);

		IFile newPropertiesFile = project.getFile("none2.ajproperties"); //$NON-NLS-1$
		BuildConfigurationUtils.saveBuildConfiguration(newPropertiesFile);
		newPropertiesFile.refreshLocal(1, null);
		waitForAutoBuild();

		compareFiles(propertiesFile, newPropertiesFile);
	}

	public void testExcludeSomeWithPackages() throws Exception {
		IProject project = createPredefinedProject("Figures Demo"); //$NON-NLS-1$
		waitForAutoBuild();
		checkIncluded(project, 13);
		IFile propertiesFile = project.getFile("build.ajproperties"); //$NON-NLS-1$
		assertNotNull(propertiesFile);
		assertTrue(propertiesFile.exists());

		BuildConfigurationUtils.applyBuildConfiguration(propertiesFile);
		waitForAutoBuild();
		checkIncluded(project, 12);

		IFile newPropertiesFile = project.getFile("build2.ajproperties"); //$NON-NLS-1$
		BuildConfigurationUtils.saveBuildConfiguration(newPropertiesFile);
		newPropertiesFile.refreshLocal(1, null);
		waitForAutoBuild();

		compareFiles(propertiesFile, newPropertiesFile);
	}

	private void checkIncluded(IProject project, int numFiles) throws Exception {
		Set<IFile> included = BuildConfig.getIncludedSourceFiles(project);
		assertEquals(numFiles, included.size());

    for (IFile file : included) {
      checkModel(file);
    }
	}

    private void checkModel(IFile file) throws Exception {
        AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForProject(file.getProject());
        IJavaElement unit = JavaCore.create(file);
        if (model.hasProgramElement(unit)) {
            List<String> accumulatedErrors = HandleTestUtils.checkJavaHandle(unit.getHandleIdentifier(), model);
            IProgramElement ipe = model.javaElementToProgramElement(unit);
            HandleTestUtils.checkAJHandle(ipe.getHandleIdentifier(), model);

            if (accumulatedErrors.size() > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append("Found errors in comparing elements:\n");
              for (String msg : accumulatedErrors) {
                sb.append(msg).append("\n");
              }
                fail(sb.toString());
            }
        }
    }


	private void compareFiles(IFile propertiesFile, IFile newPropertiesFile) throws CoreException, IOException {
    try (BufferedReader br = new BufferedReader(new InputStreamReader(propertiesFile.getContents())); BufferedReader br2 = new BufferedReader(new InputStreamReader(newPropertiesFile.getContents()))) {
      String line1 = br.readLine();
      String line2 = br2.readLine();
      while (line1 != null && line2 != null) {
        assertEquals(line1.trim(), line2.trim());
        line1 = br.readLine();
        line2 = br2.readLine();
      }
    }
	}
}
