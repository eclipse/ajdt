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
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.ArchiveFileFilter;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IWorkbenchPart;

public abstract class AJBuildPathAction {

	protected IFile jarFile;

	protected String fileName;

	protected IClasspathEntry cpEntry;

	protected IProject project;

	public AJBuildPathAction() {
		super();
	}

	private static IFile getCandidate(IAdaptable element) throws JavaModelException {
		IResource resource = element.getAdapter(IResource.class);
		if (!(resource instanceof IFile)
				|| !ArchiveFileFilter.isArchivePath(resource.getFullPath(),true))
			return null;

		IJavaProject project = JavaCore.create(resource.getProject());
		if (project != null
				&& project.exists()
				&& (project.findPackageFragmentRoot(resource.getFullPath()) == null))
			return (IFile) resource;
		return null;
	}

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
	}

	protected static IFile getJARFile(IStructuredSelection selection) throws JavaModelException {
		if (selection.size() != 1) {
			return null;
		}
		Object element = selection.getFirstElement();
		if (element instanceof IAdaptable) {
			IFile file = getCandidate((IAdaptable) element);
      return file;
		}
		return null;
	}

	protected boolean checkIfAddingOutjar(IProject project) {

		String inpath = jarFile.getFullPath().toPortableString();
		String outJar = AspectJCorePreferences.getProjectOutJar(project);
    return outJar.length() > 0 && (inpath.contains(outJar));
  }

	protected boolean shouldAskForClasspathRestrictions(IClasspathEntry entry) {
	    return entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER;
	}

	protected String askForClasspathRestrictions(IClasspathEntry entry, String jarName, String pathKind) {
	    String message = "Do you want to add a restriction to the " + pathKind +"?\n" +
	            "Currently, you are adding " + entry.getPath() + " to the " + pathKind + ".\n" +
	            "By adding a restriction, you can restrict elements on the " + pathKind + "\n" +
	            "in this entry to those whose names match the restriction path\n\n" +
	            "You may add a comma separated list of entries.";

	    InputDialog dialog = new InputDialog(null, "Add Classpath restriction?", message, jarName, new JarListValidator());
	    int res = dialog.open();
	    if (res == InputDialog.OK) {
	        return dialog.getValue();
	    } else {
	        return null;
	    }
	}


	/**
	 * Must be a comma separated set of java identifiers
	 * @author Andrew Eisenberg
	 *
	 */
	private static class JarListValidator implements IInputValidator {

        public String isValid(String newText) {
            if (newText.length() == 0) {
                // an empty string is OK
                return null;
            }
            String[] splits = newText.split(",");
          for (String split : splits) {
            String val = split;
            val = val.trim();
            if (val.length() == 0) {
              return "Invalid jar fragment name";
            }
            char[] array = val.toCharArray();
            for (char c : array) {
              if (!Character.isUnicodeIdentifierPart(c) && c != '.' && c != '-') {
                return "'" + c + "' not allowed.";
              }
            }
          }
            return null;
        }
	}
}
