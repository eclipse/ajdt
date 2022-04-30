/*******************************************************************************
 * Copyright (c) 2009-2010 SpringSource and others
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.builder;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.ajdt.ui.tests.testutils.TestLogger;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;

public class ITDinSeparateProjects extends UITestCase {

    /**
     * Test to ensure that a dependent project on the aspect path can send an ITD
     * into another project
     */
    public void testBug121810() throws CoreException {
        try {
            TestLogger testLog = new TestLogger();
            AspectJPlugin.getDefault().setAJLogger(testLog);

            IProject project = createPredefinedProject("ProjectWithITD_Bug121810"); //$NON-NLS-1$
            IProject otherProject = createPredefinedProject("DependentProjectWithITD_Bug121810"); //$NON-NLS-1$

            // force the enabling of this warning, which has been disabled earlier
            try {
                JavaCore.create(project).setOption("org.eclipse.jdt.core.compiler.problem.missingSerialVersion", "warning"); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (NullPointerException ignored) {
            }

            project.build(IncrementalProjectBuilder.CLEAN_BUILD, null);
            otherProject.build(IncrementalProjectBuilder.CLEAN_BUILD, null);
            waitForJobsToComplete();

            // now the ITD should have been applied to the java file in ProjectWithITD_Bug121810
            // should get a warning
            // the ITD makes the class serializable should now have a warning on the woven class
            // that says it doesn't have a serialVersionUID field.
            IMarker[] markers = getAllProblemViewMarkers();
            boolean found = false;
          for (IMarker marker : markers) {
            String message = (String) marker.getAttribute(IMarker.MESSAGE);
            if (message != null && message.contains("serialVersionUID")) { //$NON-NLS-1$
              found = true;
              break;
            }
          }
            assertTrue("ITD has not been woven into class in separate project.", found); //$NON-NLS-1$
        } finally {
            AspectJPlugin.getDefault().setAJLogger(null);
        }



    }
}
