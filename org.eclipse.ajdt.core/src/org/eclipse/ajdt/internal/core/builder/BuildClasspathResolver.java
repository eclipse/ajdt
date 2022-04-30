/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.core.builder;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.compiler.env.AccessRuleSet;
import org.eclipse.jdt.internal.compiler.util.SimpleLookupTable;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaModel;
import org.eclipse.jdt.internal.core.JavaProject;

/**
 * Copied from org.eclipse.jdt.internal.core.builder.NameEnvironment
 * Changes marked with // AspectJ Change
 */
public class BuildClasspathResolver {

	ClasspathMultiDirectory[] sourceLocations;
	ClasspathLocation[] binaryLocations;

    // AspectJ Change Begin
	/**
	 * Resolve the classpath of the given project into a string
	 */
	public String getClasspath(IWorkspaceRoot root,
		IJavaProject javaProject) {
		if (binaryLocations==null) {
			try {
				computeClasspathLocations(root, (JavaProject)javaProject, new SimpleLookupTable());
			} catch (CoreException ignored) {
			}
		}

		StringBuilder classpath = new StringBuilder();
    for (ClasspathLocation binaryLocation : binaryLocations) {
      classpath.append(binaryLocation.toOSString());
      classpath.append(File.pathSeparator);
    }
		return classpath.toString();
	}
	// AspectJ Change End

	/* Some examples of resolved class path entries.
	* Remember to search class path in the order that it was defined.
	*
	* 1a. typical project with no source folders:
	*   /Test[CPE_SOURCE][K_SOURCE] -> D:/eclipse.test/Test
	* 1b. project with source folders:
	*   /Test/src1[CPE_SOURCE][K_SOURCE] -> D:/eclipse.test/Test/src1
	*   /Test/src2[CPE_SOURCE][K_SOURCE] -> D:/eclipse.test/Test/src2
	*  NOTE: These can be in any order & separated by prereq projects or libraries
	* 1c. project external to workspace (only detectable using getLocation()):
	*   /Test/src[CPE_SOURCE][K_SOURCE] -> d:/eclipse.zzz/src
	*  Need to search source folder & output folder
	*
	* 2. zip files:
	*   D:/j9/lib/jclMax/classes.zip[CPE_LIBRARY][K_BINARY][sourcePath:d:/j9/lib/jclMax/source/source.zip]
	*      -> D:/j9/lib/jclMax/classes.zip
	*  ALWAYS want to take the library path as is
	*
	* 3a. prereq project (regardless of whether it has a source or output folder):
	*   /Test[CPE_PROJECT][K_SOURCE] -> D:/eclipse.test/Test
	*  ALWAYS want to append the output folder & ONLY search for .class files
	*/
	private void computeClasspathLocations(
		IWorkspaceRoot root,
		JavaProject javaProject,
		SimpleLookupTable binaryLocationsPerProject) throws CoreException {

		/* Update cycle marker */
		IMarker cycleMarker = javaProject.getCycleMarker();
		if (cycleMarker != null) {
			int severity = JavaCore.ERROR.equals(javaProject.getOption(JavaCore.CORE_CIRCULAR_CLASSPATH, true))
				? IMarker.SEVERITY_ERROR
				: IMarker.SEVERITY_WARNING;
			if (severity != (Integer) cycleMarker.getAttribute(IMarker.SEVERITY))
				cycleMarker.setAttribute(IMarker.SEVERITY, severity);
		}

		IClasspathEntry[] classpathEntries = javaProject.getExpandedClasspath();
		ArrayList<ClasspathMultiDirectory> sLocations = new ArrayList<>(classpathEntries.length);
		ArrayList<ClasspathLocation> bLocations = new ArrayList<>(classpathEntries.length);
    for (IClasspathEntry classpathEntry : classpathEntries) {
      ClasspathEntry entry = (ClasspathEntry) classpathEntry;
      IPath path = entry.getPath();
      Object target = JavaModel.getTarget(path, true);
      if (target == null)
        continue;

      switch (entry.getEntryKind()) {
        case IClasspathEntry.CPE_SOURCE:
          if (!(target instanceof IContainer))
            continue;
          IPath outputPath = entry.getOutputLocation() != null
            ? entry.getOutputLocation()
            : javaProject.getOutputLocation();
          IContainer outputFolder;
          if (outputPath.segmentCount() == 1) {
            outputFolder = javaProject.getProject();
          }
          else {
            outputFolder = root.getFolder(outputPath);
            // AspectJ Change Begin
            // This method can be executing on the wrong thread, where createFolder() will hang, so don't do it!
            // if (!outputFolder.exists())
            //	 createFolder(outputFolder);
            // AspectJ Change End
          }
          sLocations.add(
            // Factory method (copied from JDT Core) always returns ClasspathMultiDirectory,
            // despite more generic return type -> safe cast, but sacred knowledge -> FIXME
            (ClasspathMultiDirectory) ClasspathLocation.forSourceFolder(
              (IContainer) target,
              outputFolder,
              entry.fullInclusionPatternChars(),
              entry.fullExclusionPatternChars()
            )
          );
          continue;

        case IClasspathEntry.CPE_PROJECT:
          if (!(target instanceof IProject))
            continue;
          IProject prereqProject = (IProject) target;
          if (!JavaProject.hasJavaNature(prereqProject))
            continue; // if project doesn't have java nature or is not accessible

          JavaProject prereqJavaProject = (JavaProject) JavaCore.create(prereqProject);
          IClasspathEntry[] prereqClasspathEntries = prereqJavaProject.getRawClasspath();
          ArrayList<IContainer> seen = new ArrayList<>();
          for (IClasspathEntry prereqEntry : prereqClasspathEntries) {
            if (prereqEntry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
              Object prereqTarget = JavaModel.getTarget(prereqEntry.getPath(), true);
              if (!(prereqTarget instanceof IContainer))
                continue;
              IPath prereqOutputPath = prereqEntry.getOutputLocation() != null
                ? prereqEntry.getOutputLocation()
                : prereqJavaProject.getOutputLocation();
              IContainer binaryFolder = prereqOutputPath.segmentCount() == 1
                ? prereqProject
                : root.getFolder(prereqOutputPath);
              if (binaryFolder.exists() && !seen.contains(binaryFolder)) {
                seen.add(binaryFolder);
                ClasspathLocation bLocation =
                  ClasspathLocation.forBinaryFolder(
                    binaryFolder,
                    true,
                    entry.getAccessRuleSet());
                bLocations.add(bLocation);
                if (binaryLocationsPerProject != null) { // normal builder mode
                  ClasspathLocation[] existingLocations = (ClasspathLocation[]) binaryLocationsPerProject.get(prereqProject);
                  if (existingLocations == null) {
                    existingLocations = new ClasspathLocation[] { bLocation };
                  }
                  else {
                    int size = existingLocations.length;
                    System.arraycopy(existingLocations, 0, existingLocations = new ClasspathLocation[size + 1], 0, size);
                    existingLocations[size] = bLocation;
                  }
                  binaryLocationsPerProject.put(prereqProject, existingLocations);
                }
              }
            }
          }
          continue;

        case IClasspathEntry.CPE_LIBRARY:
          if (target instanceof IResource) {
            IResource resource = (IResource) target;
            ClasspathLocation bLocation = null;
            if (resource instanceof IFile) {
              if (!(org.eclipse.jdt.internal.compiler.util.Util.isPotentialZipArchive(path.lastSegment())))
                continue;
              AccessRuleSet accessRuleSet = JavaCore.IGNORE.equals(javaProject.getOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, true))
                ? null
                : entry.getAccessRuleSet();
              bLocation = ClasspathLocation.forLibrary((IFile) resource, accessRuleSet);
            }
            else if (resource instanceof IContainer) {
              AccessRuleSet accessRuleSet = JavaCore.IGNORE.equals(javaProject.getOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, true))
                ? null
                : entry.getAccessRuleSet();
              bLocation = ClasspathLocation.forBinaryFolder((IContainer) target, false, accessRuleSet);   // is library folder not output folder
            }
            bLocations.add(bLocation);
            if (binaryLocationsPerProject != null) { // normal builder mode
              IProject p = resource.getProject(); // can be the project being built
              ClasspathLocation[] existingLocations = (ClasspathLocation[]) binaryLocationsPerProject.get(p);
              if (existingLocations == null) {
                existingLocations = new ClasspathLocation[] { bLocation };
              }
              else {
                int size = existingLocations.length;
                System.arraycopy(existingLocations, 0, existingLocations = new ClasspathLocation[size + 1], 0, size);
                existingLocations[size] = bLocation;
              }
              binaryLocationsPerProject.put(p, existingLocations);
            }
          }
          else if (target instanceof File) {
            if (!(org.eclipse.jdt.internal.compiler.util.Util.isPotentialZipArchive(path.lastSegment())))
              continue;
            AccessRuleSet accessRuleSet = JavaCore.IGNORE.equals(javaProject.getOption(JavaCore.COMPILER_PB_FORBIDDEN_REFERENCE, true))
              ? null
              : entry.getAccessRuleSet();
            bLocations.add(ClasspathLocation.forLibrary(path.toString(), accessRuleSet));
          }
      }
    }

		// now split the classpath locations... place the output folders ahead of the other .class file folders & jars
		ArrayList<ClasspathMultiDirectory> outputFolders = new ArrayList<>(1);
		this.sourceLocations = new ClasspathMultiDirectory[sLocations.size()];
		if (!sLocations.isEmpty()) {
			sLocations.toArray(this.sourceLocations);

			// collect the output folders, skipping duplicates
			next : for (int i = 0, l = sourceLocations.length; i < l; i++) {
				ClasspathMultiDirectory md = sourceLocations[i];
				IPath outputPath = md.binaryFolder.getFullPath();
				for (int j = 0; j < i; j++) { // compare against previously walked source folders
					if (outputPath.equals(sourceLocations[j].binaryFolder.getFullPath())) {
						md.hasIndependentOutputFolder = sourceLocations[j].hasIndependentOutputFolder;
						continue next;
					}
				}
				outputFolders.add(md);

				// also tag each source folder whose output folder is an independent folder & is not also a source folder
        for (ClasspathMultiDirectory sourceLocation : sourceLocations)
          if (outputPath.equals(sourceLocation.sourceFolder.getFullPath()))
            continue next;
				md.hasIndependentOutputFolder = true;
			}
		}

		// combine the output folders with the binary folders & jars... place the output folders before other .class file folders & jars
		this.binaryLocations = new ClasspathLocation[outputFolders.size() + bLocations.size()];
		int index = 0;
    for (ClasspathMultiDirectory outputFolder : outputFolders)
      this.binaryLocations[index++] = outputFolder;
    for (ClasspathLocation bLocation : bLocations)
      this.binaryLocations[index++] = bLocation;
	}

	// AspectJ Change Begin
	// This method can be executing on the wrong thread, where createFolder() will hang, so don't do it!
	//	private void createFolder(IContainer folder) throws CoreException {
	//		if (!folder.exists()) {
	//			createFolder(folder.getParent());
	//			((IFolder) folder).create(true, true, null);
	//		}
	//	}
	// AspectJ Change End

}
