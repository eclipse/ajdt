/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sian January  - initial version
 *******************************************************************************/

package org.eclipse.ajdt.internal.launching;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.internal.resources.ResourceStatus;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.launching.LaunchingMessages;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IRuntimeClasspathEntry;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.JavaLaunchDelegate;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.Version;

public class LTWApplicationLaunchConfigurationDelegate
		extends JavaLaunchDelegate {

	private static final String classLoaderOption = "-Djava.system.class.loader"; //$NON-NLS-1$
	private static final String ajClasspathOption = "-Daj.class.path"; //$NON-NLS-1$


	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {

		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		monitor.beginTask(NLS.bind("{0}...", new String[]{configuration.getName()}), 3); //$NON-NLS-1$
		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}

		generateAOPConfigFiles(configuration);

		monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Verifying_launch_attributes____1);

		String mainTypeName = verifyMainTypeName(configuration);
		IVMRunner runner = getVMRunner(configuration, mode);
		boolean isJava5OrLater = isJava5OrLater(configuration);

		File workingDir = verifyWorkingDirectory(configuration);
		String workingDirName = null;
		if (workingDir != null) {
			workingDirName = workingDir.getAbsolutePath();
		}

		// Environment variables
		String[] envp= getEnvironment(configuration);

		// Classpath
		String[] classpath = getClasspath(configuration);

		String[] ltwClasspath;
		try {
		    ltwClasspath = getLTWClasspath(classpath, isJava5OrLater);
		}
    catch (IOException e) {
        throw new CoreException(new ResourceStatus(IStatus.ERROR, null, UIMessages.LTW_error_launching, e));
    }

    // Program & VM args
		String pgmArgs = getProgramArguments(configuration);
		String vmArgs = getVMArguments(configuration);
		vmArgs = addExtraVMArgs(vmArgs, classpath, ltwClasspath[0], isJava5OrLater);  // weaver is always first entry in ltwclassath arroy
		ExecutionArguments execArgs = new ExecutionArguments(vmArgs, pgmArgs);

		// VM-specific attributes
		Map<String, Object> vmAttributesMap = getVMSpecificAttributesMap(configuration);


		// Create VM config
		VMRunnerConfiguration runConfig = new VMRunnerConfiguration(mainTypeName, ltwClasspath);
		runConfig.setProgramArguments(execArgs.getProgramArgumentsArray());
		runConfig.setEnvironment(envp);
		runConfig.setVMArguments(execArgs.getVMArgumentsArray());
		runConfig.setWorkingDirectory(workingDirName);
		runConfig.setVMSpecificAttributesMap(vmAttributesMap);

		// Bootpath
		runConfig.setBootClassPath(getBootpath(configuration));

		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}

		// stop in main
		prepareStopInMain(configuration);

		// done the verification phase
		monitor.worked(1);

		monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Creating_source_locator____2);
		// set the default source locator if required
		setDefaultSourceLocator(launch, configuration);
		monitor.worked(1);

		// Launch the configuration - 1 unit of work
		runner.run(runConfig, launch, monitor);

		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}

		monitor.done();
	}

    public boolean isJava5OrLater(ILaunchConfiguration configuration) throws CoreException {
        IVMInstall install = getVMInstall(configuration);
        if (install instanceof IVMInstall2) {
            String version = ((IVMInstall2) install).getJavaVersion();
            if (version != null) {
                // expecting n.n.n-identifier
                // see http://java.sun.com/j2se/versioning_naming.html
                int dashIndex = version.indexOf('-');
                if (dashIndex >= 0) {
                    version = version.substring(0, dashIndex);
                }
                Version osgiVersion = new Version(version);
                return osgiVersion.getMinor() > 4;
            }
        }
        return false;
    }

	/**
	 * Generate aop-ajc.xml files for any projects on the LTW aspectpath
	 * @param configuration
	 * @throws CoreException
	 */
	private void generateAOPConfigFiles(ILaunchConfiguration configuration) throws CoreException {
		IProject[] workspaceProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		IRuntimeClasspathEntry[] aspectPathEntries = getAspectPathEntries(configuration);
    for (IRuntimeClasspathEntry aspectPathEntry : aspectPathEntries) {
      String location = aspectPathEntry.getLocation();
      for (IProject project : workspaceProjects) {
        if (project.isOpen()) {
          IRuntimeClasspathEntry entry = JavaRuntime.newProjectRuntimeClasspathEntry(JavaCore.create(project));
          String projectLocation = entry.getLocation();
          if (projectLocation != null && projectLocation.equals(location)) {
            LTWUtils.generateLTWConfigFile(JavaCore.create(project));
          }
        }
      }
    }
		// Generate aop-ajc.xml file for main project if it's an AspectJ project
		String projectName = configuration.getAttribute(
				IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME,
				(String) null);
		if (projectName != null && !projectName.trim().equals("")) { //$NON-NLS-1$
			IProject project = AspectJPlugin.getWorkspace().getRoot()
					.getProject(projectName);
			if(project.isOpen() && project.hasNature(AspectJPlugin.ID_NATURE)) {
				LTWUtils.generateLTWConfigFile(JavaCore.create(project));
			}
		}
	}

	/**
	 * Override getClasspath so that we also include the load time weaving
	 * aspectpath
	 */
	public String[] getClasspath(ILaunchConfiguration configuration)
			throws CoreException {
		IRuntimeClasspathEntry[] entries = JavaRuntime
				.computeUnresolvedRuntimeClasspath(configuration);
		IRuntimeClasspathEntry[] aspectPathEntries = getAspectPathEntries(configuration);
		entries = JavaRuntime.resolveRuntimeClasspath(entries, configuration);
		List<String> userEntries = new ArrayList<>(entries.length + aspectPathEntries.length);
    for (IRuntimeClasspathEntry entry : entries) {
      if (entry.getClasspathProperty() == IRuntimeClasspathEntry.USER_CLASSES) {
        String location = entry.getLocation();
        if (location != null) {
          userEntries.add(location);
        }
      }
    }
    for (IRuntimeClasspathEntry aspectPathEntry : aspectPathEntries) {
      String location = aspectPathEntry.getLocation();
      if (location != null) {
        userEntries.add(location);
      }
    }
		return userEntries.toArray(new String[0]);
	}

	/**
	 * Get the load time weaving aspectpath from the given configuration
	 * @param configuration
	 * @return
	 * @throws CoreException
	 */
	private IRuntimeClasspathEntry[] getAspectPathEntries(ILaunchConfiguration configuration) throws CoreException {
		List<String> entries = configuration.getAttribute(LTWAspectPathTab.ATTR_ASPECTPATH, Collections.emptyList());
		// That would be nice:
		//   return entries.stream().map(entry -> JavaRuntime.newRuntimeClasspathEntry(entry)).toArray();
		// But in order to preserve the checked exception, we need to iterate manually.
		IRuntimeClasspathEntry[] runtimeClasspathEntries = new IRuntimeClasspathEntry[entries.size()];
		int i = 0;
		for (String entry : entries)
			runtimeClasspathEntries[i++] = JavaRuntime.newRuntimeClasspathEntry(entry);
		return runtimeClasspathEntries;
	}

	private String[] getLTWClasspath(String[] classpath, boolean isJava5OrLater) throws IOException {
		File resolvedaspectjWeaverJar = FileLocator.getBundleFile(Platform.getBundle(AspectJPlugin.WEAVER_PLUGIN_ID));
		if (!resolvedaspectjWeaverJar.getName().endsWith(".jar")) {
			// runtime workbench
			resolvedaspectjWeaverJar = new File(resolvedaspectjWeaverJar, "classes");
		}
		File resolvedaspectjRTJar = FileLocator.getBundleFile(Platform.getBundle(AspectJPlugin.RUNTIME_PLUGIN_ID));
		if (!resolvedaspectjRTJar.getName().endsWith(".jar")) {
			// runtime workbench
			resolvedaspectjRTJar = new File(resolvedaspectjRTJar, "classes");
		}
		String weaverPath = new Path(resolvedaspectjWeaverJar.getCanonicalPath()).toOSString();
		String rtPath = new Path(resolvedaspectjRTJar.getCanonicalPath()).toOSString();
		List<String> fullPath = new ArrayList<>();
		fullPath.add(weaverPath);
		fullPath.add(rtPath);
		if (isJava5OrLater)
			fullPath.addAll(Arrays.asList(classpath));
		return fullPath.toArray(new String[0]);
	}

	private String addExtraVMArgs(String vmArgs, String[] ajClasspath, String pathToWeaver, boolean isJava5OrLater) {
		StringBuilder sb = new StringBuilder(vmArgs);
		if (!isJava5OrLater) {
    		sb.append(' ');
    		sb.append(classLoaderOption);
    		sb.append('=');
    		sb.append("org.aspectj.weaver.loadtime.WeavingURLClassLoader"); //$NON-NLS-1$
		}
		sb.append(' ');
		sb.append(ajClasspathOption);
		sb.append('=');
		sb.append('\"');
		for (int i = 0; i < ajClasspath.length; i++) {
			if(i != 0) {
				sb.append(File.pathSeparator);
			}
			sb.append(ajClasspath[i]);
		}
		sb.append('\"');

		if (isJava5OrLater) {
		    sb.append(" \"-javaagent:").append(pathToWeaver).append("\"");
		}
		return sb.toString();
	}
}
