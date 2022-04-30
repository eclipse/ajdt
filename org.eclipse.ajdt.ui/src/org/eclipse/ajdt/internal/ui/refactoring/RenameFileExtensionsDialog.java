/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: Sian January - initial version
 * ...
 ******************************************************************************/

package org.eclipse.ajdt.internal.ui.refactoring;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.ajdt.core.BuildConfig;
import org.eclipse.ajdt.core.codeconversion.CodeChecker;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.actions.WorkspaceModifyDelegatingOperation;

/**
 * Dialog that renames file extensions for an entire project. Three options are
 * presented to the user - convert all extensions to .java, convert all
 * extensions to .aj and convert aspects to .aj and classes to .java.
 */
public class RenameFileExtensionsDialog extends Dialog {

	// The project
	private final IProject project;

	private Button convertAllToJavaButton;

	private Button convertAllToAJButton;

	private Button convertAspectsToAJButton;

	private Button includeFilesNotInBuildButton;

	private Button updateBuildConfigsButton;

	/**
	 * @param parentShell
	 */
	public RenameFileExtensionsDialog(Shell parentShell, IProject project) {
		super(parentShell);
		this.project = project;
	}

	/**
	 * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
	 */
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		shell.setText(UIMessages.Refactoring_ConvertFileExtensions);
	}

	protected Control createDialogArea(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.verticalSpacing = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_SPACING);
		layout.horizontalSpacing = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_SPACING);
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));

		convertAspectsToAJButton = new Button(composite, SWT.RADIO);
		convertAspectsToAJButton
				.setText(UIMessages.Refactoring_ConvertAspectsToAJAndClassesToJava);
		convertAspectsToAJButton.setSelection(true);

		convertAllToAJButton = new Button(composite, SWT.RADIO);
		convertAllToAJButton.setText(UIMessages.Refactoring_ConvertAllToAJ);

		convertAllToJavaButton = new Button(composite, SWT.RADIO);
		convertAllToJavaButton.setText(UIMessages.Refactoring_ConvertAllToJava);

		new Label(composite, SWT.NONE);

		includeFilesNotInBuildButton = new Button(composite, SWT.CHECK);
		includeFilesNotInBuildButton.setText(UIMessages.Refactoring_IncludeFilesNotInBuild);
		includeFilesNotInBuildButton.setSelection(true);
		updateBuildConfigsButton = new Button(composite, SWT.CHECK);
		updateBuildConfigsButton.setText(UIMessages.Refactoring_UpdateBuildConfigs);
		updateBuildConfigsButton.setSelection(true);
		applyDialogFont(composite);
		return composite;
	}

	protected void okPressed() {
		boolean updateBuildConfigs = updateBuildConfigsButton.getSelection();
		if (convertAllToAJButton.getSelection()) {
			convertAllExtensions(true, includeFilesNotInBuildButton
					.getSelection(), updateBuildConfigs);
		} else if (convertAllToJavaButton.getSelection()) {
			convertAllExtensions(false, includeFilesNotInBuildButton
					.getSelection(), updateBuildConfigs);
		} else if (convertAspectsToAJButton.getSelection()) {
			convertAspectsToAJAndOthersToJava(includeFilesNotInBuildButton
					.getSelection(), updateBuildConfigs);
		}
		super.okPressed();
	}

	/**
	 * Convert aspects' file extensions to .aj, and classes and interfaces to
	 * .java. Also converts files containing any inner aspects or pointcuts to
	 * .aj.
	 *
	 * @param includeNotBuiltFiles -
	 *            include files not included in the active build configuration.
	 * @param monitor -
	 *            progress monitor
	 * @param updateBuildConfigs -
	 *            update build configurations
	 */
	private void convertAspectsToAJAndOthersToJava(
			final boolean includeNonBuiltFiles, final boolean updateBuildConfigs) {
		IRunnableWithProgress runnable = monitor -> {
      IJavaProject jp = JavaCore.create(project);
      try {
        IPackageFragment[] packages = jp.getPackageFragments();
        monitor
            .beginTask(UIMessages.Refactoring_ConvertingFileExtensions,
                packages.length);
        // map of old to new names - needed to update build config
        // files.
        Map oldToNewNames = new HashMap();
for (IPackageFragment aPackage : packages) {
if (!(aPackage.isReadOnly())) {
try {
ICompilationUnit[] files = aPackage
.getCompilationUnits();
for (ICompilationUnit file : files) {

IResource resource = file.getResource();

if (!includeNonBuiltFiles
&& !(BuildConfig
.isIncluded(resource)))
{
// do not rename this file if it is not
// active
continue;
}

boolean isAspect = CodeChecker.containsAspectJConstructs((IFile) resource);
if (!isAspect
&& resource.getFileExtension()
.equals("aj"))
{ //$NON-NLS-1$
RenamingUtils.renameFile(false, resource, monitor,
oldToNewNames);
}
else if (isAspect
&& resource.getFileExtension()
.equals("java"))
{ //$NON-NLS-1$
RenamingUtils.renameFile(true, resource, monitor,
oldToNewNames);
}
}
}
catch (JavaModelException ignored) {
}
}
monitor.worked(1);
}
        if (updateBuildConfigs) {
            RenamingUtils.updateBuildConfigurations(oldToNewNames, project,
              monitor);
        }
      } catch (JavaModelException ignored) {
      }
    };

		IRunnableWithProgress op = new WorkspaceModifyDelegatingOperation(
				runnable);
		try {
			new ProgressMonitorDialog(getShell()).run(true, true, op);
		} catch (InvocationTargetException | InterruptedException ignored) {
		}
  }


	/**
	 * Convert all the extensions for files in a project
	 *
	 * @param convertToAJ -
	 *            if true convert to .aj, otherwise convert to .java
	 * @param includeNotBuiltFiles -
	 *            include files not included in the active build configuration.
	 * @param updateBuildConfigs -
	 *            update build configuration files if true.
	 */
	private void convertAllExtensions(final boolean convertToAJ,
			final boolean includeNotBuiltFiles, final boolean updateBuildConfigs) {

		IRunnableWithProgress runnable = monitor -> {
      IJavaProject jp = JavaCore.create(project);
      try {
        IPackageFragment[] packages = jp.getPackageFragments();
        monitor
            .beginTask(UIMessages.Refactoring_ConvertingFileExtensions,
                packages.length);

        // Map of old to new names - needed to update build config
        // files.
        Map oldNamesToNewNames = new HashMap();
for (IPackageFragment aPackage : packages) {
if (!(aPackage.isReadOnly())) {
try {
ICompilationUnit[] files = aPackage
.getCompilationUnits();
for (ICompilationUnit file : files) {
IResource resource = file.getResource();
if (!includeNotBuiltFiles
&& !(BuildConfig.isIncluded(resource)))
{
// do not rename this file if it is not
// active
continue;
}
if ((!convertToAJ && resource
.getFileExtension().equals("aj")) //$NON-NLS-1$
|| (convertToAJ && resource
.getFileExtension().equals(
"java")))
{ //$NON-NLS-1$
RenamingUtils.renameFile(convertToAJ, resource,
monitor, oldNamesToNewNames);
}
}
}
catch (JavaModelException ignored) {
}
}
monitor.worked(1);
}
        if (updateBuildConfigs) {
            RenamingUtils.updateBuildConfigurations(oldNamesToNewNames, project,
              monitor);
        }
      } catch (JavaModelException ignored) {
      }
    };

		IRunnableWithProgress op = new WorkspaceModifyDelegatingOperation(
				runnable);
		try {
			new ProgressMonitorDialog(getShell()).run(true, true, op);
		} catch (InvocationTargetException | InterruptedException ignored) {
		}

  }

}
