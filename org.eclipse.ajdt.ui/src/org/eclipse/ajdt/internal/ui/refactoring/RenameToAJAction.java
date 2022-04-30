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

package org.eclipse.ajdt.internal.ui.refactoring;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.ajdt.core.AJProperties;
import org.eclipse.ajdt.internal.ui.ajde.AJDTErrorHandler;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;
import org.eclipse.ui.IActionDelegate;
import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;

/**
 * Rename the file extension of a file to .aj.
 */
public class RenameToAJAction implements IActionDelegate {

	private ISelection selection;

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
	 */
	public void run(IAction action) {
		if (selection instanceof StructuredSelection) {
			IRunnableWithProgress runnable = monitor -> {
        StructuredSelection sel = (StructuredSelection) selection;
for (Object object : sel) {
if (object instanceof IAdaptable) {

IResource file = ((IAdaptable) object)
.getAdapter(IResource.class);
if (file != null) {
IProject project = file.getProject();
String name = file.getName();
name = name.substring(0, name.indexOf('.'));
RenameResourceChange change = new RenameResourceChange(
file.getFullPath(), name + ".aj"); //$NON-NLS-1$
try {
change.perform(monitor);
}
catch (CoreException e) {
AJDTErrorHandler.handleAJDTError(UIMessages.Refactoring_ErrorRenamingResource, e);
}
if (project != null) {
updateBuildConfigs(monitor, project, name);
}
}
}
}
      };

			IRunnableWithProgress op = new WorkspaceModifyDelegatingOperation(
					runnable);
			try {
				new ProgressMonitorDialog(AspectJUIPlugin.getDefault()
						.getDisplay().getActiveShell()).run(true, true, op);
			} catch (InvocationTargetException | InterruptedException ignored) {
			}
    }
	}

	/**
	 * @param monitor
	 * @param project
	 * @param name
	 */
	private void updateBuildConfigs(IProgressMonitor monitor, IProject project,
			String name) {
		List<IFile> buildConfigs = AJProperties.getAJPropertiesFiles(project);
    for (IFile config : buildConfigs) {
      IFile buildConfig = config;
      BufferedReader br;
      try {
        br = new BufferedReader(new InputStreamReader(buildConfig
          .getContents()));
      }
      catch (CoreException e) {
        continue;
      }
      StringBuilder sb = new StringBuilder();
      try {
        String line = br.readLine();
        while (line != null) {
          line = line.replaceAll(name + ".java", name + ".aj"); //$NON-NLS-1$ //$NON-NLS-2$
          sb.append(line);
          sb.append(System.getProperty("line.separator")); //$NON-NLS-1$
          line = br.readLine();
        }
        StringReader reader = new StringReader(sb.toString());
        buildConfig.setContents(new ReaderInputStream(reader), true,
          true, monitor);
      }
      catch (IOException | CoreException ignored) {
      }
      finally {
        try {
          br.close();
        }
        catch (IOException ignored) {
        }
      }
    }
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction,
	 *      org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IAction action, ISelection selection) {
		this.selection = selection;
	}

}
