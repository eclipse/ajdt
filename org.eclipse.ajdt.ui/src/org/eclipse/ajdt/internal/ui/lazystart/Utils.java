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
package org.eclipse.ajdt.internal.ui.lazystart;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

/**
 * Useful utilities and constants (which do not trigger bundle activation)
 *
 */
public class Utils {

	// the id of this plugin
	public static final String PLUGIN_ID = "org.eclipse.ajdt.ui"; //$NON-NLS-1$

	public static final String ID_NATURE = PLUGIN_ID + ".ajnature"; //$NON-NLS-1$

	public static boolean isBundleActive() {
		return (Platform.getBundle(PLUGIN_ID).getState() == Bundle.ACTIVE);
	}

	public static boolean isAJProject(IProject project) {
		if((project!=null) && project.isOpen()) {
			try {
				if (project.hasNature(ID_NATURE)) {
					return true;
				}
			} catch (CoreException ignored) {
			}
		}
		return false;
	}

}
