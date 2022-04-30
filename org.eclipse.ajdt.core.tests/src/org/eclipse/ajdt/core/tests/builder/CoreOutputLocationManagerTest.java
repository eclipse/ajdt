/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Matt Chapman   - initial version
 *               Helen Hawkins - updated for new ajde interface (bug 148190)
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.builder;

import java.io.File;

import org.eclipse.ajdt.core.tests.AJDTCoreTestCase;
import org.eclipse.ajdt.internal.core.ajde.CoreOutputLocationManager;
import org.eclipse.ajdt.internal.core.ajde.FileURICache;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

public class CoreOutputLocationManagerTest extends AJDTCoreTestCase {

	public void testOutputLocationManager() throws Exception {
		IProject project = createPredefinedProject("MultipleOutputFolders"); //$NON-NLS-1$
	    FileURICache fileCache = new FileURICache(project);
		CoreOutputLocationManager om = new CoreOutputLocationManager(project, fileCache);
		om.buildStarting();

		IFile class1 = (IFile) project.findMember("src/p1/Class1.java"); //$NON-NLS-1$
		File file1 = class1.getLocation().toFile();
		File out1 = om.getOutputLocationForClass(file1);
		assertTrue("Output location for " + class1 //$NON-NLS-1$
				+ " should end in bin. Got: " + out1, out1.toString() //$NON-NLS-1$
				.endsWith("bin")); //$NON-NLS-1$

		IFile class2 = (IFile) project.findMember("src2/p2/Class2.java"); //$NON-NLS-1$
		File file2 = class2.getLocation().toFile();
		File out2 = om.getOutputLocationForClass(file2);
		assertTrue("Output location for " + class2 //$NON-NLS-1$
				+ " should end in bin2. Got: " + out2, out2.toString() //$NON-NLS-1$
				.endsWith("bin2")); //$NON-NLS-1$

		IFile class3 = (IFile) project.findMember("src2/p2/GetInfo.aj"); //$NON-NLS-1$
		File file3 = class3.getLocation().toFile();
		File out3 = om.getOutputLocationForClass(file3);
		assertTrue("Output location for " + class3 //$NON-NLS-1$
				+ " should end in bin2. Got: " + out3, out3.toString() //$NON-NLS-1$
				.endsWith("bin2")); //$NON-NLS-1$
		om.buildComplete();
	}

	public void testOutputLocationManagerBug153682() throws Exception {
		IProject project = createPredefinedProject("bug153682"); //$NON-NLS-1$
        FileURICache fileCache = new FileURICache(project);
		CoreOutputLocationManager om = new CoreOutputLocationManager(project, fileCache);
		om.buildStarting();
		IFile class1 = (IFile) project.findMember("foo/Test.java"); //$NON-NLS-1$
		File file1 = class1.getLocation().toFile();
		File out1 = om.getOutputLocationForClass(file1);
		assertTrue("Output location for " + class1 //$NON-NLS-1$
				+ " should end in bin. Got: " + out1, out1.toString() //$NON-NLS-1$
				.endsWith("bin")); //$NON-NLS-1$

		IFile class2 = (IFile) project.findMember("foo/test.properties"); //$NON-NLS-1$
		File file2 = class2.getLocation().toFile();
		File out2 = om.getOutputLocationForResource(file2);
		assertTrue("Output location for " + class2 //$NON-NLS-1$
				+ " should end in bin. Got: " + out2, out2.toString() //$NON-NLS-1$
				.endsWith("bin")); //$NON-NLS-1$
		om.buildComplete();
	}

	public void testOutputLocationManagerBug160846() throws Exception {
		IProject project = createPredefinedProject("bug160846"); //$NON-NLS-1$
        FileURICache fileCache = new FileURICache(project);
		CoreOutputLocationManager om = new CoreOutputLocationManager(project, fileCache);
		om.buildStarting();
		IFile class1 = (IFile) project.findMember("src/java/org/noco/aj/MainClass.java"); //$NON-NLS-1$
		File file1 = class1.getLocation().toFile();
		File out1 = om.getOutputLocationForClass(file1);
		assertTrue("Output location for " + class1 //$NON-NLS-1$
				+ " should end in classes. Got: " + out1, out1.toString() //$NON-NLS-1$
				.endsWith("classes")); //$NON-NLS-1$

		IFile class2 = (IFile) project.findMember("test/java/org/noco/aj/MainClassTest.java"); //$NON-NLS-1$
		File file2 = class2.getLocation().toFile();
		File out2 = om.getOutputLocationForResource(file2);
		assertTrue("Output location for " + class2 //$NON-NLS-1$
				+ " should end in test-classes. Got: " + out2, out2.toString() //$NON-NLS-1$
				.endsWith("test-classes")); //$NON-NLS-1$
		om.buildComplete();
	}

	public void testInpathOutLocation() throws Exception {
	    IProject project1 = createPredefinedProject("ExportAsJar"); //$NON-NLS-1$
	    IProject project2 = createPredefinedProject("JarOnInpath"); //$NON-NLS-1$
        FileURICache fileCache = new FileURICache(project2);
	    CoreOutputLocationManager om = new CoreOutputLocationManager(project2, fileCache);
	    om.buildStarting();
	    IFile class1 = (IFile) project1.findMember("export.jar"); //$NON-NLS-1$
	    File file1 = class1.getLocation().toFile();
	    File out1 = om.getOutputLocationForClass(file1);
        assertTrue("Output location for " + class1 //$NON-NLS-1$
                + " should end in InpathOut. Got: " + out1, out1.toString() //$NON-NLS-1$
                .endsWith("InpathOut")); //$NON-NLS-1$
        om.buildComplete();
	}


	static class MockCoreOutputLocationManager extends CoreOutputLocationManager {

	    public MockCoreOutputLocationManager(IProject project, FileURICache fileCache) {
            super(project, fileCache);
        }

        // make accessible in this test
	    protected IProject findDeclaringProject(File outputFolder) {
	        return super.findDeclaringProject(outputFolder);
	    }
	}

	/**
	 * tests {@link CoreOutputLocationManagaer#findDeclaringProject }
	 */
	public void testFindDeclaringProject() throws Exception {
        IProject base = createPredefinedProject("FindDeclaringProjectBase");
        IProject level1 = createPredefinedProject("FindDeclaringProjectLevel1");
        IProject level2 = createPredefinedProject("FindDeclaringProjectLevel2");
        createVariable(base);
        createContainer(base);

        MockCoreOutputLocationManager com = new MockCoreOutputLocationManager(base, new FileURICache(base));

        checkFileForDeclaringProject(base.getFolder("bin"), com, base);
        checkFileForDeclaringProject(base.getFolder("bin2"), com, base);
        checkFileForDeclaringProject(base.getFolder("binaryFolder"), com, base);
        checkFileForDeclaringProject(base.getFile("myJar.jar"), com, null);  // jar files are not included
        checkFileForDeclaringProject(base.getFile("myJarVar.jar"), com, null);  // jar files are not included
        checkFileForDeclaringProject(base.getFile("myJarContainer.jar"), com, null);  // jar files are not included
        checkFileForDeclaringProject(level1.getFolder("bin"), com, level1);
        checkFileForDeclaringProject(level1.getFolder("bin2"), com, level1);
        checkFileForDeclaringProject(level1.getFolder("binaryFolder"), com, level1);
        checkFileForDeclaringProject(level1.getFolder("notExported"), com, null);
        checkFileForDeclaringProject(level1.getFile("myJar.jar"), com, null);  // jar files are not included
        checkFileForDeclaringProject(level2.getFolder("bin"), com, level2);
	}

	/**
	 * tests bug 279497 when a required project uses root as the source folder
	 * an IllegalArgumentException was being thrown
	 */
	public void testFindDeclaringProjectWithSrcAsRoot() throws Exception {
        IProject base = createPredefinedProject("Bug279497AJ");
        IProject required = createPredefinedProject("Bug279497RootAsSourceFolder");

        MockCoreOutputLocationManager com = new MockCoreOutputLocationManager(base, new FileURICache(base));
        checkFileForDeclaringProject(required, com, required);
    }

	void checkFileForDeclaringProject(IResource resource, MockCoreOutputLocationManager com, IProject expected) {
	    File file = new File(resource.getLocationURI());
	    IProject actual = com.findDeclaringProject(file);
	    assertEquals("wrong declaring project found for " + resource, expected, actual);
	}


    private void createVariable(IProject base) throws JavaModelException {
        JavaCore.setClasspathVariable("DECLARING_PARENT_VAR",
                base.getFile("myJarVar.jar").getFullPath(), null); //$NON-NLS-1$
    }

    private IClasspathContainer createContainer(IProject base) throws JavaModelException {
        final IClasspathEntry entry =
            JavaCore.newLibraryEntry(
                    base.getFile("myJarContainer.jar").getFullPath(),
                    null, null);

        IClasspathContainer container = new IClasspathContainer() {
            public IClasspathEntry[] getClasspathEntries() {
                return new IClasspathEntry[] { entry };
            }
            public String getDescription() {
                return "org.eclipse.jdt.USER_LIBRARY/DECLARING_PROJECT_CONTAINER"; //$NON-NLS-1$
            }
            public int getKind() {
                  return IClasspathContainer.K_APPLICATION;
            }
            public IPath getPath() {
                return new Path("org.eclipse.jdt.USER_LIBRARY/DECLARING_PROJECT_CONTAINER"); //$NON-NLS-1$
            }
        };
        JavaCore.setClasspathContainer(container.getPath(), new IJavaProject[] { JavaCore.create(base) },
                new IClasspathContainer[] { container }, null);
        return container;
    }
}
