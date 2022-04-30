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

import org.eclipse.ajdt.core.AspectJCore;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.ui.tests.UITestCase;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.ui.actions.OrganizeImportsAction;

public class OrganizeImportsTest extends UITestCase {

    protected void setUp() throws Exception {
        super.setUp();
        if (!AspectJPlugin.USING_CU_PROVIDER) {
            fail("Must be using JDT Weaving");
        }
    }

    /**
     * Should not remove the aspect in the imports statement
     */
    public void testBug188845() throws Exception {
        IProject proj = createPredefinedProject("Bug 188845"); //$NON-NLS-1$
        IFile concrete = proj.getFile("src/tmp/b/ConcreteAspect.aj"); //$NON-NLS-1$
        ICompilationUnit cu = (ICompilationUnit) AspectJCore.create(concrete);

        JavaEditor editor = (JavaEditor) EditorUtility.openInEditor(cu);

        assertEquals("Should start off with 2 import statements", 2, cu.getImportContainer().getChildren().length); //$NON-NLS-1$
        OrganizeImportsAction action = new OrganizeImportsAction(editor);
        action.run(cu);
        editor.doSave(null);

        // hmmm...why do I need to do a full build here?  That's odd
        // If I don't, then I am getting some error markers.
        proj.build(org.eclipse.core.resources.IncrementalProjectBuilder.FULL_BUILD, null);

        waitForJobsToComplete();
        assertEquals("Should have only 1 import statement after reorganizing", 1, cu.getImportContainer().getChildren().length); //$NON-NLS-1$

        IMarker[] markers = concrete.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
        if (markers.length > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("No markers should have been found, but the following markers were found on A.aj:\n"); //$NON-NLS-1$
          for (IMarker marker : markers) {
            sb.append("\t").append(marker.getAttribute(IMarker.MESSAGE)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
          }
            assertEquals(sb.toString(), 0, markers.length);
        }

    }

    /**
     * should add the LinkedList import statement in the proper location (not above the package declaration)
     */
    public void testBug236352() throws Exception {
        IProject proj = createPredefinedProject("Bug 188845"); //$NON-NLS-1$
        IFile concrete = proj.getFile("src/tmp/b/ConcreteAspect2.aj"); //$NON-NLS-1$
        ICompilationUnit cu = (ICompilationUnit) AspectJCore.create(concrete);

        JavaEditor editor = (JavaEditor) EditorUtility.openInEditor(cu);

        assertEquals("Should start off with 2 import statements", 2, cu.getImportContainer().getChildren().length); //$NON-NLS-1$
        OrganizeImportsAction action = new OrganizeImportsAction(editor);
        action.run(cu);
        editor.doSave(null);

        waitForJobsToComplete();
        assertEquals("Should have 3 import statements after reorganizing", 3, cu.getImportContainer().getChildren().length); //$NON-NLS-1$

        IMarker[] markers = concrete.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
        if (markers.length > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("No markers should have been found, but the following markers were found on ConcreteAspect2.aj:\n"); //$NON-NLS-1$
          for (IMarker marker : markers) {
            sb.append("\t").append(marker.getAttribute(IMarker.MESSAGE)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
          }
            assertEquals(sb.toString(), 0, markers.length);
        }
    }

    /**
     * static inner classes enclosed in an aspect should be imported with the enclosing aspect around it.
     */
    public void testBug106589() throws Exception {
        IProject proj = createPredefinedProject("Bug 188845"); //$NON-NLS-1$
        IFile importer = proj.getFile("src/bug106589importer/Importer.java"); //$NON-NLS-1$
        ICompilationUnit cu = (ICompilationUnit) AspectJCore.create(importer);

        IMarker[] markers = importer.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
        if (markers.length > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("Should start with 1 error, but the following markers were found on Importer.java:\n"); //$NON-NLS-1$
          for (IMarker marker : markers) {
            sb.append("\t").append(marker.getAttribute(IMarker.MESSAGE)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
          }
            assertEquals(sb.toString(), 0, markers.length);
        }

        JavaEditor editor = (JavaEditor) EditorUtility.openInEditor(cu);

        try {
            IImportContainer container = cu.getImportContainer();
            container.getChildren();
            fail("Should not start off with an import container, but instead found:\n " + container); //$NON-NLS-1$
        } catch (JavaModelException e) {
            // expected
        }

        OrganizeImportsAction action = new OrganizeImportsAction(editor);
        action.run(cu);
        editor.doSave(null);

        // hmmm...why do I need to do a full build here?  That's odd
        // If I don't, then I am getting some error markers.
        proj.build(org.eclipse.core.resources.IncrementalProjectBuilder.FULL_BUILD, null);

        waitForJobsToComplete();
        assertEquals("Should have 1 import statements after reorganizing", 1, cu.getImportContainer().getChildren().length); //$NON-NLS-1$

        markers = importer.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
        if (markers.length > 0) {
            StringBuilder sb = new StringBuilder();
            sb.append("No markers should have been found, but the following markers were found on Importer.java:\n"); //$NON-NLS-1$
          for (IMarker marker : markers) {
            sb.append("\t").append(marker.getAttribute(IMarker.MESSAGE)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
          }
            assertEquals(sb.toString(), 0, markers.length);
        }
    }


}
