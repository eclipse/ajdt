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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ajdt.core.AJLog;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.RuntimeClasspathEntry;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.JavaRuntime;

/**
 * Utilities for managing AspectJ launch configurations
 */
public class LaunchConfigurationManagementUtils {

	/**
	 * Update the aspect path for launch configurations relating to the given
	 * project
	 *
	 * @param project -
	 *            the project
	 * @param existingAspectPathEntries -
	 *            current aspect path
	 * @param newAspectPathEntries -
	 *            new aspect path
	 */
	public static void updateAspectPaths(
		IJavaProject project,
		List<CPListElement> existingAspectPathEntries,
		List<CPListElement> newAspectPathEntries
	) {
		try {
			String projectName = project.getElementName();
			List<ILaunchConfiguration> configs = getLaunchConfigsForProject(projectName);
			for (ILaunchConfiguration configuration : configs) {
				List<IRuntimeClasspathEntry> entriesAsList = Arrays.asList(
					JavaRuntime.computeUnresolvedRuntimeClasspath(configuration)
				);
				for (CPListElement existingAspectPathEntry : existingAspectPathEntries) {
					IClasspathEntry entryToRemove = existingAspectPathEntry.getClasspathEntry();
					entriesAsList.removeIf(entry ->
						entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES &&
						entryToRemove.equals(entry.getClasspathEntry())
					);
				}
				for (CPListElement newAspectPathEntry : newAspectPathEntries) {
					IClasspathEntry newEntry = newAspectPathEntry.getClasspathEntry();
					if (newEntry.getEntryKind() != IClasspathEntry.CPE_CONTAINER)
						entriesAsList.add(new RuntimeClasspathEntry(newEntry));
					else {
						IClasspathContainer container = JavaCore.getClasspathContainer(newEntry.getPath(), project);
						if (container != null) {
							IClasspathEntry[] containerEntries = container.getClasspathEntries();
							for (IClasspathEntry containerEntry : containerEntries)
								entriesAsList.add(new RuntimeClasspathEntry(containerEntry));
						}
					}
				}
				IRuntimeClasspathEntry[] updatedEntries = entriesAsList.toArray(new IRuntimeClasspathEntry[0]);
				updateConfigurationClasspath(configuration, updatedEntries);
			}
		}
		catch (CoreException cEx) {
			AJLog.log(cEx.getMessage());
		}

	}

	/**
	 * Update the outjar for launch configurations relating to the given
	 * project
	 *
	 * @param project -
	 *            the project
	 * @param oldOutJar -
	 *            current output jar, or null if none exists
	 * @param newOutJar -
	 *            new output jar or null if none exists
	 */
	public static void updateOutJar(IJavaProject project, IClasspathEntry oldOutJar, IClasspathEntry newOutJar) {
		try {
			String projectName = project.getElementName();
			List<ILaunchConfiguration> configs = getLaunchConfigsForProject(projectName);
			for (ILaunchConfiguration config : configs) {
				IRuntimeClasspathEntry[] entries = JavaRuntime.computeUnresolvedRuntimeClasspath(config);
				List<IRuntimeClasspathEntry> entriesAsList = Arrays.asList(entries);
				entriesAsList.removeIf(entry ->
					entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES &&
					oldOutJar.equals(entry.getClasspathEntry())
				);
				if (newOutJar != null)
					entriesAsList.add(new RuntimeClasspathEntry(newOutJar));
				IRuntimeClasspathEntry[] updatedEntries = entriesAsList.toArray(new IRuntimeClasspathEntry[0]);
				updateConfigurationClasspath(config, updatedEntries);
			}
		}
		catch (CoreException cEx) {
			AJLog.log(cEx.getMessage());
		}
	}

	/**
	 * Update and save a new classpath for the given launch configuration
	 *
	 * @param configuration
	 * @param updatedEntries
	 */
	private static void updateConfigurationClasspath(
		ILaunchConfiguration configuration,
		IRuntimeClasspathEntry[] classpath
	) {
		ILaunchConfigurationWorkingCopy wc;
		try {
			wc = configuration.getWorkingCopy();
			wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
			try {
				List<String> mementos = new ArrayList<>(classpath.length);
				for (IRuntimeClasspathEntry entry : classpath)
					mementos.add(entry.getMemento());
				wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, mementos);
			}
			catch (CoreException e) {
				AJLog.log(e.getMessage());
			}
			wc.doSave();
		}
		catch (CoreException e1) {
			AJLog.log(e1.getMessage());
		}
	}

	/**
	 * Get all the launch configurations for types in the given project
	 *
	 * @param projectName
	 */
	private static List<ILaunchConfiguration> getLaunchConfigsForProject(String projectName) {
		ILaunchConfigurationType configType = AspectJApplicationLaunchShortcut.getAJConfigurationType();
		List<ILaunchConfiguration> candidateConfigs = new ArrayList<>();
		try {
			ILaunchConfiguration[] configs = DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations(configType);
			candidateConfigs = new ArrayList<>(configs.length);
			for (ILaunchConfiguration config : configs) {
				if (
					config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, "") //$NON-NLS-1$
						.equals(projectName)
				) {
					candidateConfigs.add(config);
				}
			}
		}
		catch (CoreException cEx) {
			AJLog.log(cEx.getMessage());
		}
		return candidateConfigs;
	}

}
