/*******************************************************************************
 * Copyright (c) 2008 SpringSource and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: SpringSource - initial API and implementation
 *              Andrew Eisenberg
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.builder;

import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.core.tests.testutils.Utils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourceAttributes;
import org.eclipse.core.runtime.Path;

/**
 * Tests that check to see if read only resources are
 * properly copied over and can be deleted and changed.
 *
 * @author andrew
 *
 */
public class ReadOnlyBug261007 extends AJDTCoreTestCase {
    protected void setUp() throws Exception {
        super.setUp();
        Utils.setAutobuilding(false);
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        Utils.setAutobuilding(true);
    }


    public void testReadOnlyResources() throws Exception {
        ResourceAttributes ra;
        IFile readonly = null;
        IFolder readonlyF = null;
        IFile readonly2 = null;
        try {
            IProject proj = createPredefinedProject("ReadOnlyBug261007");
            IFolder src = proj.getFolder("src");


            // make read only
            readonly = src.getFile(new Path("ReadOnly.xml"));
            readonly.touch(null);
            ra = readonly.getResourceAttributes();
            ra.setReadOnly(true);
            readonly.setResourceAttributes(ra);

            readonlyF = src.getFolder(new Path("readonly"));
            readonlyF.touch(null);
            ra = readonlyF.getResourceAttributes();
            ra.setReadOnly(true);
            readonlyF.setResourceAttributes(ra);

            readonly2 = src.getFile(new Path("readonly/ReadOnly2.xml"));
            readonly2.touch(null);
            ra = readonly2.getResourceAttributes();
            ra.setReadOnly(true);
            readonly2.setResourceAttributes(ra);

            proj.build(IncrementalProjectBuilder.FULL_BUILD, null);

            checkFileIsReadOnly(src, "ReadOnly.xml");
            checkFolderIsReadOnly(src, "readonly");
            checkFileIsReadOnly(src, "readonly/ReadOnly2.xml");

            IFolder out = proj.getFolder("bin");
            checkFileIsNotReadOnly(out, "ReadOnly.xml");
            checkFolderIsNotReadOnly(out, "readonly");
            checkFileIsNotReadOnly(out, "readonly/ReadOnly2.xml");

            IMarker[] markers = out.findMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
            assertEquals("Should not have any markers on the output folders", 0, markers.length);

            ra = readonly.getResourceAttributes();
            ra.setReadOnly(false);
            readonly.setResourceAttributes(ra);
            readonly.touch(null);

            ra = readonlyF.getResourceAttributes();
            ra.setReadOnly(false);
            readonlyF.setResourceAttributes(ra);
            readonlyF.touch(null);

            ra = readonly2.getResourceAttributes();
            ra.setReadOnly(false);
            readonly2.setResourceAttributes(ra);
            readonly2.touch(null);

            proj.build(IncrementalProjectBuilder.FULL_BUILD, null);

            checkFileIsNotReadOnly(src, "ReadOnly.xml");
            checkFolderIsNotReadOnly(src, "readonly");
            checkFileIsNotReadOnly(src, "readonly/ReadOnly2.xml");

            checkFileIsNotReadOnly(out, "ReadOnly.xml");
            checkFolderIsNotReadOnly(out, "readonly");
            checkFileIsNotReadOnly(out, "readonly/ReadOnly2.xml");
        } finally {
            try {
                // ensure files are set to read only even if test finishes early
                ra = readonly.getResourceAttributes();
                ra.setReadOnly(false);
                readonly.setResourceAttributes(ra);

                ra = readonlyF.getResourceAttributes();
                ra.setReadOnly(false);
                readonlyF.setResourceAttributes(ra);

                ra = readonly2.getResourceAttributes();
                ra.setReadOnly(false);
                readonly2.setResourceAttributes(ra);
            } catch (NullPointerException ignored) {
            }
        }

    }


    void checkFileIsReadOnly(IContainer container, String fName) {
        IFile file = container.getFile(new Path(fName));
        assertTrue(file + " should exist", file.exists());
        assertTrue(file + " should be read only", file.isReadOnly());
    }
    void checkFileIsNotReadOnly(IContainer container, String fName) {
        IFile file = container.getFile(new Path(fName));
        assertTrue(file + " should exist", file.exists());
        assertFalse(file + " should not be read only", file.isReadOnly());
    }

    void checkFolderIsReadOnly(IContainer container, String fName) {
        IFolder folder = container.getFolder(new Path(fName));
        assertTrue(folder + " should exist", folder.exists());
        assertTrue(folder + " should be read only", folder.getResourceAttributes().isReadOnly());
    }
    void checkFolderIsNotReadOnly(IContainer container, String fName) {
        IFolder folder = container.getFolder(new Path(fName));
        assertTrue(folder + " should exist", folder.exists());
        assertFalse(folder + " should not be read only", folder.getResourceAttributes().isReadOnly());
    }







    void checkFileIsDerived(IContainer container, String fName) {
        IFile file = container.getFile(new Path(fName));
        assertTrue(file + " should exist", file.exists());
        assertTrue(file + " should be derived", file.isDerived());
    }
    void checkFileIsNotDerived(IContainer container, String fName) {
        IFile file = container.getFile(new Path(fName));
        assertTrue(file + " should exist", file.exists());
        assertFalse(file + " should not be derived", file.isDerived());
    }
    void checkFileNoExist(IContainer container, String fName) {
        IFile file = container.getFile(new Path(fName));
        assertFalse(file + " should not exist", file.exists());
    }
    void checkFolderIsDerived(IContainer container, String fName) {
        IFolder folder = container.getFolder(new Path(fName));
        assertTrue(folder + " should exist", folder.exists());
        assertTrue(folder + " should be derived", folder.isDerived());
    }
    void checkFolderIsNotDerived(IContainer container, String fName) {
        IFolder folder = container.getFolder(new Path(fName));
        assertTrue(folder + " should exist", folder.exists());
        assertFalse(folder + " should not be derived", folder.isDerived());
    }
    void checkProjectIsNotDerived(IProject proj, String string) {
        assertTrue(proj + " should exist", proj.exists());
        assertFalse(proj + " should not be derived", proj.isDerived());
    }

    void checkFolderNoExist(IContainer container, String fName) {
        IFolder folder = container.getFolder(new Path(fName));
        assertFalse(folder + " should not exist", folder.exists());
    }
}
