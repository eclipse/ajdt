/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthew Ford - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.buildpath;

import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;

public class UpdateAspectpathRestriction extends AJBuildPathAction implements IObjectActionDelegate {

	public void run(IAction action) {
		if (project == null) {
			return;
		}
		if (cpEntry != null) {
		    // update the restrictions on this classpath element
		    // although the element probably already is on the aspect/in path, we can ensure it's on, just in case
            IClasspathEntry newEntry = cpEntry;
            if (shouldAskForClasspathRestrictions(cpEntry)) {
                String currRestriction = AspectJCorePreferences.getRestriction(cpEntry, AspectJCorePreferences.ASPECTPATH_RESTRICTION_ATTRIBUTE_NAME);
                String restriction = askForClasspathRestrictions(newEntry, currRestriction, "Aspect path");
                if (restriction != null) {
                    newEntry = AspectJCorePreferences.updatePathRestrictions(newEntry, restriction, AspectJCorePreferences.ASPECTPATH_RESTRICTION_ATTRIBUTE_NAME);
                } else {
                    newEntry = AspectJCorePreferences.ensureHasAttribute(newEntry, AspectJCorePreferences.ASPECTPATH_RESTRICTION_ATTRIBUTE_NAME, "");
                }
            }
            newEntry = AspectJCorePreferences.ensureHasAttribute(newEntry, AspectJCorePreferences.ASPECTPATH_ATTRIBUTE_NAME, AspectJCorePreferences.ASPECTPATH_ATTRIBUTE_NAME);
            AspectJCorePreferences.updateClasspathEntry(project, newEntry);
		}
		AJDTUtils.refreshPackageExplorer();
	}

	public void selectionChanged(IAction action, ISelection sel) {
		boolean enable = false;
		if (sel instanceof IStructuredSelection) {
			IStructuredSelection selection = (IStructuredSelection) sel;
			Object element = selection.getFirstElement();
			try {
				if (element instanceof IPackageFragmentRoot) {
					IPackageFragmentRoot root = (IPackageFragmentRoot)element;
					project = root.getJavaProject().getProject();
					cpEntry = root.getRawClasspathEntry();
					if (cpEntry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
                        fileName = root.getElementName();
                        enable = AspectJCorePreferences.isOnAspectpath(cpEntry);
                    } else {
                        fileName = null;
                        cpEntry = null;
                        project = null;
                        enable = false;
                    }
                } else {
                    enable = false;
                }
			} catch (JavaModelException ignored) {
			}
			action.setEnabled(enable);
		}
	}

}
