/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Common Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors: IBM Corporation - initial API and implementation 
 * 				 Helen Hawkins   - iniital version
 ******************************************************************************/
package org.eclipse.ajdt.internal.ui.wizards.migration;

import java.util.Iterator;
import java.util.List;

import org.eclipse.ajdt.internal.core.AJLog;
import org.eclipse.ajdt.internal.ui.AspectJProjectNature;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;


/**
 * This page deals with updating the builder id and removing the 
 * old builder id. The user is given the option as to which projects
 * they wish to remove the old builder id from. 
 */
public class BuilderMigrationPage1 extends WizardPage {

	private CheckedListDialogField checkedListDialogField;
	
	private List ajProjects;

	private BuilderMigrationPage1() {
		super(AspectJUIPlugin.getResourceString("BuilderMigrationPage1.name")); //$NON-NLS-1$
		this.setTitle(AspectJUIPlugin.getResourceString("BuilderMigrationPage1.title")); //$NON-NLS-1$		
		this.setDescription( AspectJUIPlugin.
				getResourceString("BuilderMigrationPage1.description")); //$NON-NLS-1$
	}
	
	protected BuilderMigrationPage1(List projects) {
	    this();
	    ajProjects = projects;	    
	}
	
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);
		composite.setLayoutData(new GridData(GridData.FILL_BOTH));
		setControl(composite);

		String[] buttonLabels= new String[] {
		        /* 0 */ NewWizardMessages.getString("ProjectsWorkbookPage.projects.checkall.button"), //$NON-NLS-1$
		        /* 1 */ NewWizardMessages.getString("ProjectsWorkbookPage.projects.uncheckall.button") //$NON-NLS-1$
		};

		checkedListDialogField = new CheckedListDialogField(null, buttonLabels, new AJProjectListLabelProvider());
		checkedListDialogField.setLabelText(AspectJUIPlugin
				.getResourceString("BuilderMigrationPage1.message")); //$NON-NLS-1$
		checkedListDialogField.setCheckAllButtonIndex(0);
		checkedListDialogField.setUncheckAllButtonIndex(1);
		checkedListDialogField.setElements(ajProjects);
		checkedListDialogField.setCheckedElements(ajProjects);
		checkedListDialogField.setViewerSorter(new ViewerSorter());
		
		LayoutUtil.doDefaultLayout(composite, new DialogField[] { checkedListDialogField }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(checkedListDialogField.getListControl(null));
		
		PixelConverter converter= new PixelConverter(parent);
		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		checkedListDialogField.setButtonsMinWidth(buttonBarWidth);
			
	}
	
	public void finishPressed() {
		updateBuilder(ajProjects,checkedListDialogField.getCheckedElements());
	}

	private void updateBuilder(List ajProjects, List projectsToRemoveBuilderFrom) {
		for (Iterator iter = ajProjects.iterator(); iter.hasNext();) {
			final IProject project = (IProject) iter.next();
			try {
				// add new builder id
				AspectJProjectNature.addNewBuilder(project);

				if (!AspectJProjectNature.hasNewBuilder(project)) {
					// addition of new builder failed for some reason
					AJLog.log("AJDT migration builder: addition of new builder failed!");
					Display.getDefault().syncExec(new Runnable() {
						public void run() {
							MessageDialog.openError(
								null,
								AspectJUIPlugin
									.getResourceString("Builder.migration.failed.title"), //$NON-NLS-1$
								AspectJUIPlugin.getFormattedResourceString(
									"Builder.migration.failed.message", //$NON-NLS-1$
									project.getName()));
						}
					});
				}
			} catch (CoreException e) {
			}
		}
		
		for (Iterator iter = projectsToRemoveBuilderFrom.iterator(); iter
                .hasNext();) {
            IProject project = (IProject) iter.next();
            try {
                AspectJProjectNature.removeOldBuilder(project);
            } catch (CoreException e) {
            }
        }
	}
	
	private class AJProjectListLabelProvider extends LabelProvider {
	    public String getText(Object element) {
	        if (element instanceof IProject) {
                return ((IProject)element).getName();
            }
	    	return element.toString();
	    }
	}
}
