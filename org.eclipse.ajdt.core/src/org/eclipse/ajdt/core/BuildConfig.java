/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.util.Util;

public class BuildConfig {

	private static final Map<IProject, Set<IFile>> projectsToIncludedSourceFiles = new WeakHashMap<>();

	/**
	 * Returns all of the currently included source files in a project
	 * This list is cached and reset every build (or on request by calling flushIncludedSourceFileCache)
	 * @param project
	 * @return a list of IFiles
	 */
	public static Set<IFile> getIncludedSourceFiles(IProject project) {
		if(projectsToIncludedSourceFiles.get(project) instanceof List) {
			return projectsToIncludedSourceFiles.get(project);
		}
		Set<IFile> sourceFiles = new HashSet<>();
		try {
			IJavaProject jp = JavaCore.create(project);
			IClasspathEntry[] cpes = jp.getRawClasspath();
      for (IClasspathEntry cpe : cpes) {
        if ((cpe instanceof ClasspathEntry) &&
            (cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE))
        {
          ClasspathEntry cp = (ClasspathEntry) cpe;
          char[][] incl = cp.fullInclusionPatternChars();
          char[][] excl = cp.fullExclusionPatternChars();
          IPath path = cpe.getPath();
          IResource res = project.findMember(path
            .removeFirstSegments(1));
          if ((res != null) && (res instanceof IContainer)) {
            List<IFile> l = allFiles((IContainer) res);
            for (IFile file : l) {
              if (!Util.isExcluded(file, incl, excl)) {
                sourceFiles.add(file);
              }
            }
          }
        }
      }
		} catch (JavaModelException ignored) {
		}
		projectsToIncludedSourceFiles.put(project, sourceFiles);
		return sourceFiles;
	}



	/**
	 * Experimental version of above that uses a set, not a list
	 * @param project
	 * @return
	 */
	public static Set<IFile> getIncludedSourceFilesSet(IProject project) {
        if (projectsToIncludedSourceFiles.get(project) instanceof List) {
            return projectsToIncludedSourceFiles.get(project);
        }
        Set<IFile> sourceFiles = new HashSet<>();
        try {
            IJavaProject jp = JavaCore.create(project);
            IClasspathEntry[] cpes = jp.getRawClasspath();
          for (IClasspathEntry cpe : cpes) {
            if ((cpe instanceof ClasspathEntry)
                && (cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE))
            {
              ClasspathEntry cp = (ClasspathEntry) cpe;
              char[][] incl = cp.fullInclusionPatternChars();
              char[][] excl = cp.fullExclusionPatternChars();
              IPath path = cpe.getPath();
              IResource res = project.findMember(path
                .removeFirstSegments(1));
              if ((res != null) && (res instanceof IContainer)) {
                List<IFile> l = allFiles((IContainer) res);
                for (IFile file : l) {
                  if (!Util.isExcluded(file, incl, excl)) {
                    sourceFiles.add(file);
                  }
                }
              }
            }
          }
        } catch (JavaModelException ignored) {
        }
        projectsToIncludedSourceFiles.put(project, sourceFiles);
        return sourceFiles;
    }


	/**
	 * Invalidate the list of included source files for a project
	 * @param project
	 */
	public static void flushIncludedSourceFileCache(IProject project) {
		projectsToIncludedSourceFiles.remove(project);
	}

	/**
	 * Find out whether a file is included.  This does NOT use the cached version,
	 * so if you are calling it a lot and don't need the most up-to date information
	 * it would be better to use getIncludedSourceFiles(file.getProject()).contains(file) instead.
	 * @param file
	 * @return
	 */
	public static boolean isIncluded(IResource file) {
		IJavaProject jp = JavaCore.create(file.getProject());
		return jp.isOnClasspath(file);
	}

	//return a list of all IFiles in the given folder, including all
	// sub-folders
	private static List<IFile> allFiles(IContainer folder) {
		final List<IFile> contents = new ArrayList<>();
		try {
			folder.accept(res -> {
        if (res.getType() == IResource.FILE
            && JavaCore.isJavaLikeFileName((res
                .getName()))) {
          contents.add((IFile) res);
        }
        return true;
      });
		} catch (CoreException ignored) {
		}
		return contents;
	}
}
