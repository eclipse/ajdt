/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.ui.tests;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;

/**
 * Main plugin class. Used to determine the filesystem location of the plugin.
 */
public class AspectJTestPlugin extends AbstractUIPlugin {

	/**
	 * shared single instance of the plugin
	 */
	private static AspectJTestPlugin plugin;

	/**
	 * Creates an AspectJTestPlugin instance
	 */
	public AspectJTestPlugin() {
		super();
		plugin = this;
	}

	/**
	 * Return the single default instance of this plugin
	 */
	public static AspectJTestPlugin getDefault() {
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
				+ "workspace" + File.separator + "org.eclipse.ajdt.ui.tests" //$NON-NLS-1$ //$NON-NLS-2$
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

}
