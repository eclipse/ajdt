/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - iniital version
 ******************************************************************************/
package org.eclipse.ajdt.core.tests.testutils;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.runtime.CoreException;

/**
 * Useful operations when writing tests.
 */
public class Utils {

	public static void setAutobuilding(boolean autobuild) throws CoreException {
		IWorkspaceDescription workspaceDesc = AspectJPlugin.getWorkspace().getDescription();
		workspaceDesc.setAutoBuilding(autobuild);
		AspectJPlugin.getWorkspace().setDescription(workspaceDesc);

	}

	public static boolean isAutobuilding() {
	    return AspectJPlugin.getWorkspace().getDescription().isAutoBuilding();
	}

    public synchronized static void sleep(int millis) {
        try {
            Utils.class.wait(millis);
        } catch (InterruptedException ignored) {
        }
    }
}
