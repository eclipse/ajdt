/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Sian January - initial implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.buildpath;

import org.eclipse.ajdt.core.AJProperties;
import org.eclipse.ajdt.core.buildpath.BuildConfigurationUtils;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.internal.utils.AJDTUtils;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;

/**
 * This action is triggered from a popup menu on a project
 * file. It saves the current exclusions.
 */
public class SaveBCAction implements IWorkbenchWindowActionDelegate {


	private IProject currentProject;


	/**
	 *  Executed when button clicked or popup menu "Select this build file" clicked
	 */
	public void run(IAction action) {
		if(currentProject != null) {
			String title = UIMessages.BCDialog_SaveBuildConfigurationAs_title;
			String msg = UIMessages.BCDialog_SaveBuildConfigurationAs_message;
			String defaultFileName = UIMessages.BCDialog_SaveBuildConfigurationAs_default;
			String fileName = AJDTUtils.getFreeFileName(
					currentProject, defaultFileName,
					AJProperties.EXTENSION);

			IInputValidator validator = input -> {
                IStatus status = PDEPlugin.getWorkspace().validateName(input + "." + AJProperties.EXTENSION, IResource.FILE); // $NON-NLS-1$ //$NON-NLS-1$
                if (!status.isOK()) {
                    return status.getMessage();
                }
        return null;
      };

			InputDialog md = new InputDialog(AspectJUIPlugin.getDefault().getActiveWorkbenchWindow().getShell(), title, msg, fileName,
					validator);
			md.setBlockOnOpen(true);
			if (md.open() == Window.OK) {
				if (md.getValue() != null) {

					String newName = md.getValue();

					IFile newFile = getFile(currentProject, newName);
					if (newFile.exists()) {
						if (!askUserOverwrite(newFile.getName())) {

						}
						try {
							newFile.delete(true, null);
						} catch (CoreException ignored) {
						}

					}
					BuildConfigurationUtils.saveBuildConfiguration(newFile);
				}
			}
			try {
				currentProject.refreshLocal(1, null);
			} catch (CoreException ignored) {
			}
		}
	}

	private boolean askUserOverwrite(String fileName) {
		String[] options = {
				UIMessages.BCDialog_Overwrite_yes,
				UIMessages.BCDialog_Overwrite_no };
		String title = UIMessages.BCDialog_Overwrite_title;
		String msg = UIMessages.BCDialog_Overwrite_message.replaceAll("%fileName", fileName); //$NON-NLS-1$

		MessageDialog mdiag = new MessageDialog(null, title, null, msg,
				MessageDialog.QUESTION, options, 1);
		return (mdiag.open() == 0);
	}

	IFile getFile(IProject project, String name) {
		return project.getFile(
				name + "." + AJProperties.EXTENSION); //$NON-NLS-1$
	}


	/**
	 * Selection has changed - if we've selected a build file then remember it
	 * as the new project build config.
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		currentProject = null;
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection structuredSelection = (IStructuredSelection) selection;
			Object first = structuredSelection.getFirstElement();
			if (first instanceof IProject) {
				currentProject = (IProject) first;
			} else if (first instanceof IAdaptable) {
				currentProject = ((IAdaptable)first).getAdapter(IProject.class);
			}
		}
	}

	/**
	 * From IWorkbenchWindowActionDelegate
	 */
	public void dispose() {}

	/**
	 * From IWorkbenchWindowActionDelegate
	 */
	public void init(IWorkbenchWindow window) {
	}
}
