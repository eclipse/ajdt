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

public class AddToInpathAction extends AJBuildPathAction implements IObjectActionDelegate {

	public void run(IAction action) {
		if (project == null) {
			return;
		}
		if (cpEntry != null) {
            // add to in path and ensure restrictions are properly set up
            IClasspathEntry newEntry = cpEntry;
            if (shouldAskForClasspathRestrictions(cpEntry)) {
                String restriction = askForClasspathRestrictions(newEntry, fileName, "In path");
                if (restriction != null && restriction.length() > 0) {
                    newEntry = AspectJCorePreferences.updatePathRestrictions(newEntry, restriction, AspectJCorePreferences.INPATH_RESTRICTION_ATTRIBUTE_NAME);
                } else {
                    newEntry = AspectJCorePreferences.ensureHasAttribute(newEntry, AspectJCorePreferences.INPATH_RESTRICTION_ATTRIBUTE_NAME, "");
                }
            }
            newEntry = AspectJCorePreferences.ensureHasAttribute(newEntry, AspectJCorePreferences.INPATH_ATTRIBUTE_NAME, AspectJCorePreferences.INPATH_ATTRIBUTE_NAME);
            AspectJCorePreferences.updateClasspathEntry(project, newEntry);

		} else {
			String jarPath = jarFile.getFullPath().toPortableString();
			AspectJCorePreferences.addToInPath(project,jarPath,IClasspathEntry.CPE_LIBRARY);
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
					jarFile = null;
                    fileName = root.getElementName();
					enable = !AspectJCorePreferences.isOnInpath(cpEntry);
				} else {
					jarFile = getJARFile(selection);
					if (jarFile != null) {
						cpEntry = null;
						project = jarFile.getProject();
						enable = (!AspectJCorePreferences.isOnInpath(
								project, jarFile.getFullPath()
										.toPortableString()) && !checkIfAddingOutjar(project));
	                      fileName = jarFile.getName();
					}
				}
			} catch (JavaModelException ignored) {
			}
			action.setEnabled(enable);
		}
	}

}
