/*******************************************************************************
 * Copyright (c) 2009 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial API and implementation
 *******************************************************************************/

package org.eclipse.contribution.weaving.jdt.tests.preferences;

import org.eclipse.contribution.jdt.preferences.JDTWeavingPreferences;
import org.eclipse.contribution.jdt.preferences.WeavableProjectListener;
import org.eclipse.contribution.weaving.jdt.tests.MockNature;
import org.eclipse.contribution.weaving.jdt.tests.WeavingTestCase;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

/**
 * @author Andrew Eisenberg
 * @created Jan 20, 2009
 *
 * Tests the enablement and disablement of the weaving service as best we can
 */
public class WeavingServiceEnablementTests extends WeavingTestCase {
    static class MockWeavableProjectListener extends WeavableProjectListener {
        boolean weavableProjectFound = false;
//        @Override
//        public void handleEvent(LifecycleEvent event) throws CoreException {
//            if (event.kind == LifecycleEvent.PRE_PROJECT_OPEN ||
//                    event.kind == LifecycleEvent.PRE_PROJECT_CREATE) {
//                if (event.resource instanceof IProject) {
//                    if (isWeavableProject((IProject) event.resource)) {
//                        weavableProjectFound = true;
//                    }
//                }
//            }
//        }
        @Override
        protected void askToEnableWeaving() {
            weavableProjectFound = true;
        }
    }

    /**
     * Tests that weaving is asked to be turned on when a weavable nature is
     * added and when a project that has a weavable nature is opened
     */
    public void testAskToEnableWeaving() throws Exception {
        WeavableProjectListener orig = WeavableProjectListener.getInstance();
        MockWeavableProjectListener mock = new MockWeavableProjectListener();
        WeavableProjectListener.setInstance(mock);
        JDTWeavingPreferences.setAskToEnableWeaving(false);
        ((Workspace) ResourcesPlugin.getWorkspace()).addLifecycleListener(mock);
        try {
            IProject newProj = ResourcesPlugin.getWorkspace().getRoot().getProject("NewProject");
            newProj.create(null);

            assertFalse("Weavable project should not have been found", mock.weavableProjectFound);
            newProj.close(null);
            newProj.open(null);
            assertFalse("Weavable project should not have been found", mock.weavableProjectFound);

            addNature(newProj);
            assertTrue("Weavable project was not found", mock.weavableProjectFound);

            mock.weavableProjectFound = false;
            newProj.close(null);
            newProj.open(null);
            assertTrue("Weavable project was not found", mock.weavableProjectFound);
        } finally {
            JDTWeavingPreferences.setAskToEnableWeaving(true);
            WeavableProjectListener.setInstance(orig);
        }
    }

    private void addNature(IProject project) throws CoreException {
        IProjectDescription description = project.getDescription();
        String[] newNatures = new String[1];
        newNatures[0] = MockNature.ID_NATURE;
        description.setNatureIds(newNatures);
        project.setDescription(description, null);
    }

}
