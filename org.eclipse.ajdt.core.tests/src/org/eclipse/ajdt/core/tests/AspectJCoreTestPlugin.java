/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - iniital version
 ******************************************************************************/
package org.eclipse.ajdt.core.tests;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;

/**
 * @author Helen Hawkins
 */
public class AspectJCoreTestPlugin extends Plugin {

	/**
	 * shared single instance of the plugin
	 */
	private static AspectJCoreTestPlugin plugin;

	/**
	 * Creates an AspectJCoreTestPlugin instance
	 */
	public AspectJCoreTestPlugin() {
		super();
		plugin = this;
	}

	/**
	 * Return the single default instance of this plugin
	 */
	public static AspectJCoreTestPlugin getDefault() {
		return plugin;
	}

	/**
	 * Return the resolved filesystem location of this plugin
	 * @return the location as a String
	 */
	public static String getPluginDir() {
		String pluginDir=null;
		URL loc = plugin.getBundle().getEntry("/"); //$NON-NLS-1$
		try {
			URL resolved = FileLocator.resolve(loc);
			pluginDir = resolved.getFile();
		} catch (IOException ignored) {}
		if ((pluginDir==null) || (pluginDir.length()==0)) {
			return System.getProperty("user.dir") + File.separator //$NON-NLS-1$
				+ "workspace" + File.separator + "org.eclipse.ajdt.core.tests" //$NON-NLS-1$ //$NON-NLS-2$
				+ File.separator;
		}
		return pluginDir;
	}

	public static String getPluginId() {
		return getDefault().getBundle().getSymbolicName();
	}

	/**
	 * Report exception to Error Log
	 */
	public static void log (Throwable e) {
		if (e instanceof InvocationTargetException)
			e = ((InvocationTargetException) e).getTargetException();
		IStatus status;
		if (e instanceof CoreException)
			status = ((CoreException) e).getStatus();
		else
			status =
				new Status(IStatus.ERROR, getPluginId(), IStatus.OK, e.getMessage(), e);
		getDefault().getLog().log(status);
	}

	public static void logInfo(String message) {
	    IStatus status =
	            new Status(IStatus.INFO, getPluginId(), IStatus.OK, message, null);
	    getDefault().getLog().log(status);
	}
}
