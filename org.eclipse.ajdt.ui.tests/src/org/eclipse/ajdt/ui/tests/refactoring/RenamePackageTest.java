/*******************************************************************************
 * Copyright (c) 2008 SpringSourceand others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Andrew Eisenberg - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests.refactoring;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenamePackageProcessor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.ResourceChangeChecker;
import org.eclipse.ltk.core.refactoring.participants.ValidateEditChecker;

public class RenamePackageTest extends UITestCase {

    protected void setUp() throws Exception {
        super.setUp();
        if (!AspectJPlugin.USING_CU_PROVIDER) {
            fail("Must be using JDT Weaving");
        }
    }

    public void testRenamePackage() throws Exception {
        IProject proj = createPredefinedProject("Bug 254431"); //$NON-NLS-1$
        IJavaProject jProj = JavaCore.create(proj);
        IPackageFragmentRoot packRoot = jProj.getPackageFragmentRoot(proj.getFolder("src")); //$NON-NLS-1$
        IPackageFragment frag = packRoot.getPackageFragment("ajdt.renamepackagebug1"); //$NON-NLS-1$
        assertTrue("Package fragment " + frag.getElementName() + " should exist", frag.exists()); //$NON-NLS-1$ //$NON-NLS-2$
        RenamePackageProcessor processor = new RenamePackageProcessor(frag);
        processor.setNewElementName("ajdt.renamed"); //$NON-NLS-1$
        IProgressMonitor pm = new NullProgressMonitor();
        CheckConditionsContext context = new CheckConditionsContext();
        context.add(new ValidateEditChecker(context));
        context.add(new ResourceChangeChecker());
        processor.checkInitialConditions(pm);
        processor.checkFinalConditions(pm, context);
        Change change = processor.createChange(pm);
        change.perform(pm);

        waitForJobsToComplete();
        IPackageFragment newFrag = packRoot.getPackageFragment("ajdt.renamed"); //$NON-NLS-1$
        assertTrue("Package fragment " + newFrag.getElementName() + " should exist after a rename", newFrag.exists()); //$NON-NLS-1$ //$NON-NLS-2$

        IFile ajFile = proj.getFile("src/ajdt/renamed/A.aj"); //$NON-NLS-1$
        assertTrue("A.aj should exist after its package has been renamed", ajFile.exists()); //$NON-NLS-1$
        IMarker[] markers = ajFile.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
        if (markers.length > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("No markers should have been found, but the following markers were found on A.aj:\n"); //$NON-NLS-1$
          for (IMarker marker : markers) {
            sb.append("\t").append(marker.getAttribute(IMarker.MESSAGE)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
          }
            assertEquals(sb.toString(), 0, markers.length);
        }

        IFile javaFile = proj.getFile("src/ajdt/renamepackagebug2/B.aj"); //$NON-NLS-1$
        assertTrue("B.aj should exist", javaFile.exists()); //$NON-NLS-1$
        markers = javaFile.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
        if (markers.length > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("No markers should have been found, but the following markers were found on B.aj:\n"); //$NON-NLS-1$
          for (IMarker marker : markers) {
            sb.append("\t").append(marker.getAttribute(IMarker.MESSAGE)).append("\n");  //$NON-NLS-1$//$NON-NLS-2$
          }
            assertEquals(sb.toString(), 0, markers.length);
        }
    }
}
