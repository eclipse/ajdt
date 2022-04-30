/**********************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 * ...
 **********************************************************************/

package org.eclipse.ajdt.internal.launching;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.debug.ui.classpath.ClasspathEntry;
import org.eclipse.jdt.internal.debug.ui.classpath.IClasspathEntry;
import org.eclipse.jdt.internal.launching.RuntimeClasspathEntry;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;

/**
 * Utitlites for manipulating the runtime classpaths for launch configurations
 */
public class LaunchConfigurationClasspathUtils {

	/**
	 * Returns the classpath entries currently specified by the given model
	 */
	public static IRuntimeClasspathEntry[] getCurrentClasspath(AJClasspathModel fModel) {
		IClasspathEntry[] boot = fModel.getEntries(AJClasspathModel.BOOTSTRAP);
		IClasspathEntry[] user = fModel.getEntries(AJClasspathModel.USER);
		IClasspathEntry[] aspectPath = fModel.getEntries(AJClasspathModel.ASPECTPATH);
		IClasspathEntry[] outJar = fModel.getEntries(AJClasspathModel.OUTJAR);
		List<IRuntimeClasspathEntry> entries = new ArrayList<>(boot.length + user.length + aspectPath.length);

		IClasspathEntry bootEntry;
		IRuntimeClasspathEntry entry;
		for (IClasspathEntry value : boot) {
			bootEntry = value;
			entry = null;
			if (bootEntry instanceof ClasspathEntry)
				entry = ((ClasspathEntry) bootEntry).getDelegate();
			else if (bootEntry instanceof IRuntimeClasspathEntry)
				entry = (IRuntimeClasspathEntry) value;
			if (entry != null) {
				if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES)
					entry.setClasspathProperty(IRuntimeClasspathEntry.BOOTSTRAP_CLASSES);
				entries.add(entry);
			}
		}

		IClasspathEntry userEntry;
		for (IClasspathEntry classpathEntry : user) {
			userEntry = classpathEntry;
			entry = null;
			if (userEntry instanceof ClasspathEntry)
				entry = ((ClasspathEntry) userEntry).getDelegate();
			else if (userEntry instanceof IRuntimeClasspathEntry)
				entry = (IRuntimeClasspathEntry) classpathEntry;
			if (entry != null) {
				entry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);
				entries.add(entry);
			}
		}

		IClasspathEntry aspectEntry;
		for (IClasspathEntry iClasspathEntry : aspectPath) {
			aspectEntry = iClasspathEntry;
			entry = null;
			if (aspectEntry instanceof ClasspathEntry)
				entry = ((ClasspathEntry) aspectEntry).getDelegate();
			else if (aspectEntry instanceof IRuntimeClasspathEntry)
				entry = (IRuntimeClasspathEntry) iClasspathEntry;
			if (entry != null) {
				entry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);
				entries.add(entry);
			}
		}

		IClasspathEntry outJarEntry;
		for (int i = 0; i < outJar.length; i++) {
			outJarEntry = outJar[i];
			entry = null;
			if (outJarEntry instanceof ClasspathEntry)
				entry = ((ClasspathEntry) outJarEntry).getDelegate();
			else if (outJarEntry instanceof IRuntimeClasspathEntry)
				entry = (IRuntimeClasspathEntry) aspectPath[i];
			if (entry != null) {
				entry.setClasspathProperty(IRuntimeClasspathEntry.USER_CLASSES);
				entries.add(entry);
			}
		}

		return entries.toArray(new IRuntimeClasspathEntry[0]);
	}

	/**
	 * Create an AJClasspath model from the given configuration
	 */
	public static AJClasspathModel createClasspathModel(
			ILaunchConfiguration configuration) throws CoreException {
		AJClasspathModel fModel = new AJClasspathModel();
		IRuntimeClasspathEntry[] entries = JavaRuntime
				.computeUnresolvedRuntimeClasspath(configuration);
		IRuntimeClasspathEntry entry;

		String projectName = configuration.getAttribute(
				IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME,
				(String) null);
		IRuntimeClasspathEntry[] aspectEntries = null;
		IRuntimeClasspathEntry outJar = null;
		if (projectName != null && !projectName.trim().equals("")) { //$NON-NLS-1$
			IProject project = AspectJPlugin.getWorkspace().getRoot()
					.getProject(projectName);
			aspectEntries = LaunchConfigurationClasspathUtils
					.getAspectpath(project);
			outJar = LaunchConfigurationClasspathUtils.getOutJar(project);
		}
		if (aspectEntries != null) {
      for (IRuntimeClasspathEntry aspectEntry : aspectEntries) {
        fModel.addEntry(AJClasspathModel.ASPECTPATH, aspectEntry);
      }
		} else {
			aspectEntries = new IRuntimeClasspathEntry[0];
		}
		if (outJar != null) { // Add the outjar to the classpath model
			fModel.addEntry(AJClasspathModel.OUTJAR, outJar);
		}
    for (IRuntimeClasspathEntry iRuntimeClasspathEntry : entries) {
      entry = iRuntimeClasspathEntry;
      switch (entry.getClasspathProperty()) {
        case IRuntimeClasspathEntry.USER_CLASSES:
          boolean isAspectPathEntry = false;
          boolean isOutJarEntry = false;
          for (IRuntimeClasspathEntry aspectEntry : aspectEntries) {
            if (aspectEntry.equals(entry)) {
              isAspectPathEntry = true;
              break;
            }
          }
          if (outJar != null && outJar.equals(entry)) {
            isOutJarEntry = true;
          }
          if (!isAspectPathEntry && !isOutJarEntry) {
            fModel.addEntry(AJClasspathModel.USER, entry);
          }
          break;
        default:
          fModel.addEntry(AJClasspathModel.BOOTSTRAP, entry);
          break;
      }
    }
		return fModel;
	}

	/**
	 * @param project
	 * @return
	 */
	private static IRuntimeClasspathEntry getOutJar(IProject project) {
		String outjar = AspectJCorePreferences.getProjectOutJar(project);
		if(outjar == null || outjar.equals("")) { //$NON-NLS-1$
			return null;
		}
		org.eclipse.jdt.core.IClasspathEntry entry = new org.eclipse.jdt.internal.core.ClasspathEntry(
				IPackageFragmentRoot.K_BINARY, // content kind
				org.eclipse.jdt.core.IClasspathEntry.CPE_LIBRARY, // entry kind
				new Path(project.getName() + '/' + outjar).makeAbsolute(), // path
				new IPath[] {}, // inclusion patterns
				new IPath[] {}, // exclusion patterns
				null, // src attachment path
				null, // src attachment root path
				null, // output location
				false, // is exported ?
				null, //accessRules
				false, //combine access rules?
				new IClasspathAttribute[0] // extra attributes?
        		);
		return new RuntimeClasspathEntry(entry);
	}

	/**
	 * Get the AspectPath for a project
	 */
	public static IRuntimeClasspathEntry[] getAspectpath(IProject project) {
		List<IRuntimeClasspathEntry> result = new ArrayList<>();
		String[] v = AspectJCorePreferences.getResolvedProjectAspectPath(project);
		if (v == null)
			return null;
		String paths = v[0];
		String cKinds = v[1];
		String eKinds = v[2];
		if ((paths != null && paths.length() > 0)
				&& (cKinds != null && cKinds.length() > 0)
				&& (eKinds != null && eKinds.length() > 0))
		{
			StringTokenizer sTokPaths = new StringTokenizer(paths, File.pathSeparator);
			StringTokenizer sTokCKinds = new StringTokenizer(cKinds, File.pathSeparator);
			StringTokenizer sTokEKinds = new StringTokenizer(eKinds, File.pathSeparator);
			if (
				sTokPaths.countTokens() == sTokCKinds.countTokens() &&
				sTokPaths.countTokens() == sTokEKinds.countTokens()
			)
			{
				while (sTokPaths.hasMoreTokens()) {
					org.eclipse.jdt.core.IClasspathEntry entry = new org.eclipse.jdt.internal.core.ClasspathEntry(
						Integer.parseInt(sTokCKinds.nextToken()), // content kind
						Integer.parseInt(sTokEKinds.nextToken()), // entry kind
						new Path(sTokPaths.nextToken()), // path
						new IPath[] {}, // inclusion patterns
						new IPath[] {}, // exclusion patterns
						null, // src attachment path
						null, // src attachment root path
						null, // output location
						false, // is exported ?
						null, //accessRules
						false, //combine access rules?
						new IClasspathAttribute[0] // extra attributes?
					);
					result.add(new RuntimeClasspathEntry(entry));
				}// end while
			}// end if string token counts tally
		}// end if we have something valid to work with

		return result.size() > 0
			? result.toArray(new IRuntimeClasspathEntry[0])
			: null;
	}

	/**
	 * Returns whether the specified classpath is equivalent to the default
	 * classpath for this configuration.
	 *
	 * @param classpath
	 *            classpath to compare to default
	 * @param configuration
	 *            original configuration
	 * @return whether the specified classpath is equivalent to the default
	 *         classpath for this configuration
	 */
	public static boolean isDefaultClasspath(
			IRuntimeClasspathEntry[] classpath,
			ILaunchConfiguration configuration) {
		try {
			ILaunchConfigurationWorkingCopy wc = configuration.getWorkingCopy();
			wc.setAttribute(
					IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH,
					true);
			IRuntimeClasspathEntry[] entries = JavaRuntime
					.computeUnresolvedRuntimeClasspath(wc);
			if (classpath.length == entries.length) {
				for (int i = 0; i < entries.length; i++) {
					IRuntimeClasspathEntry entry = entries[i];
					if (!entry.equals(classpath[i])) {
						return false;
					}
				}
				return true;
			}
			return false;
		} catch (CoreException e) {
			return false;
		}
	}

	/**
	 * Updates the classpath for a launch configuration to ensure that it
	 * contains the aspectpath and the outjar. NB. Will not add the aspect
	 * path or outjar a second time if the classpath already contains it.
	 */
	public static void addAspectPathAndOutJarToClasspath(ILaunchConfiguration configuration) {
		ILaunchConfigurationWorkingCopy wc;
		try {
			wc = configuration.getWorkingCopy();
			AJClasspathModel model = createClasspathModel(configuration);
			IRuntimeClasspathEntry[] classpath = getCurrentClasspath(model);
			boolean def = isDefaultClasspath(classpath, wc);
			if (def) {
				wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, (String) null);
				wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, (String) null);
			}
			else {
				wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
				try {
					List<String> mementos = new ArrayList<>(classpath.length);
					for (IRuntimeClasspathEntry entry : classpath)
						mementos.add(entry.getMemento());
					wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, mementos);
				}
				catch (CoreException ignored) { }
				wc.doSave();
			}
		}
		catch (CoreException ignored) { }
	}

}
