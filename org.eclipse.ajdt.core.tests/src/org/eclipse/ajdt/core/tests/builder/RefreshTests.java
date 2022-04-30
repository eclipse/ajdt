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

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.builder.AJBuilder;
import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.core.tests.testutils.Utils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;

/**
 * Tests that check to see if resources are properly copied over
 * on build and deleted on clean
 *
 * Also, that all build artifacts are marked as derived
 * @author andrew
 *
 * THESE TESTS ARE NOT RUN YET
 *
 * There are a number of bugs preventing this test case from passing.
 * These tests are looking for the fact that *exactly* the files
 * we change are refreshed.  This is being blocked by bugs:
 * 269578
 * 269576
 * 269572
 *
 * Instead, should use RefreshTestsImprecise to show that at least
 * the ones we are expecting have been refreshed
 *
 */
public class RefreshTests extends AJDTCoreTestCase {

    static class DeltaListener implements IResourceChangeListener {

        List<IResource> changed = new ArrayList<>();

        void resetChanged() {
            changed.clear();
        }

        List<IResource> getChanged() {
            return changed;
        }

        public void resourceChanged(IResourceChangeEvent event) {
            try {
                event.getDelta().accept(delta -> {
                    if (delta.getAffectedChildren().length > 0) {
                        return true;
                    } else {
                        changed.add(delta.getResource());
                        return false;
                    }
                });
            } catch (CoreException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private DeltaListener listener;
    private IProject proj;

    protected void setUp() throws Exception {
        super.setUp();
        proj = createPredefinedProject("CopyDerived1");
        Utils.setAutobuilding(false);
        listener = new DeltaListener();
        ResourcesPlugin.getWorkspace().addResourceChangeListener(listener);
        AJBuilder.addStateListener();
    }

    protected void tearDown() throws Exception {
        AJBuilder.removeStateListener();
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener);
        proj.refreshLocal(IResource.DEPTH_INFINITE, null);
        super.tearDown();
        Utils.setAutobuilding(true);
    }

    // to test add remove, change
    // a resource, a source file and a source folder

    public void testChangeSource() throws Exception {
        proj.getFile("src/Nothing2.aj").touch(null);
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/Nothing2.aj", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/Nothing2.class", (filesChanged.get(1)).getFullPath().toString());
    }

    // Why is this touching all?
    public void testAddSource() throws Exception {
        createFile("src/Nothing9.aj", "public aspect Nothing9 { }");
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/Nothing9.aj", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/Nothing9.class", (filesChanged.get(1)).getFullPath().toString());
    }

    // fails, but expected.  Not being notified of deletions
    public void testRemoveSource() throws Exception {
        proj.getFile("src/Nothing2.aj").delete(true, null);
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/Nothing2.aj", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/Nothing2.class", (filesChanged.get(1)).getFullPath().toString());
    }

    // fails, Why is package1/Nothing.class being compiled as well?
    public void testChangeJavaSourceFAIL() throws Exception {
        createFile("src/Nothing.java", "public class Nothing { int x; }" );
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/Nothing.java", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/Nothing.class", (filesChanged.get(1)).getFullPath().toString());
    }


    // Why is this not touching the class file?
    public void testChangeJavaSource() throws Exception {
        createFile("src/package1/Nothing.java", "package package1; public class Nothing { int x; }" );
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/package1/Nothing.java", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/package1/Nothing.class", (filesChanged.get(1)).getFullPath().toString());
    }

    public void testAddJavaSource() throws Exception {
        createFile("src/Nothing3.java", "public aspect Nothing3 { }");
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/Nothing3.java", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/Nothing3.class", (filesChanged.get(1)).getFullPath().toString());
    }
    // fails, but expected.  Not being notified of deletions
    public void testRemoveJavaSource() throws Exception {
        proj.getFile("src/Nothing.java").delete(true, null);
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/Nothing.java", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/Nothing.class", (filesChanged.get(1)).getFullPath().toString());
    }


    public void testChangeResource() throws Exception {
        proj.getFile("src/file.txt").touch(null);
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/file.txt", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/file.txt", (filesChanged.get(1)).getFullPath().toString());
    }
    public void testAddResource() throws Exception {
        createFile("src/file2.txt", "nothing");
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/file2.txt", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/file2.txt", (filesChanged.get(1)).getFullPath().toString());
    }
    public void testRemoveResource() throws Exception {
        proj.getFile("src/file.txt").delete(true, null);
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/file.txt", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/file.txt", (filesChanged.get(1)).getFullPath().toString());
    }


    public void testChangeFolder() throws Exception {
        proj.getFolder("src/package1").touch(null);
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Touching a folder with no resource changes should not trigger a build, or any resource deltas", 0, filesChanged.size());

    }
    public void testAddFolder() throws Exception {
        createFolder("src/package2");
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 2 files changed " + filesChanged, 2, filesChanged.size());
        assertEquals("/CopyDerived1/src/package2", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/package2", (filesChanged.get(1)).getFullPath().toString());
    }

    // fails, but expected.  Not being notified of deletions
    public void testRemoveFolder() throws Exception {
        proj.getFolder("src/package1").delete(true, null);
        Utils.sleep(1000);
        proj.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IResource> filesChanged = listener.getChanged();
        assertEquals("Should have had 6 files changed " + filesChanged, 6, filesChanged.size());
        assertEquals("/CopyDerived1/src/package1/file.txt", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/package1/file.txt", (filesChanged.get(1)).getFullPath().toString());
        assertEquals("/CopyDerived1/src/package1/Nothing.java", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/package1/Nothing.class", (filesChanged.get(1)).getFullPath().toString());
        assertEquals("/CopyDerived1/src/package1/Nothing2.aj", (filesChanged.get(0)).getFullPath().toString());
        assertEquals("/CopyDerived1/bin/package1/Nothing2.class", (filesChanged.get(1)).getFullPath().toString());
    }


    private void createFolder(String path) throws CoreException {
        proj.getFolder(path).create(true, true, null);
    }

    private IFile createFile(String path, String content) throws CoreException {
        ByteArrayInputStream source = new ByteArrayInputStream(content.getBytes());
        IFile newFile = proj.getFile(path);
        if (newFile.exists()) {
            newFile.setContents(source, IResource.FORCE, null);
        } else {
            newFile.create(source, true, null);
        }
        return newFile;
    }

}
