/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     SpringSource - (Andrew Eisenberg) converted for use with AJDT
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.wizards;

import org.eclipse.ajdt.internal.ui.wizards.exports.AJJarPackageData;
import org.eclipse.ajdt.internal.ui.wizards.exports.AJJarPackageWizard;
import org.eclipse.ajdt.internal.ui.wizards.exports.AJJarPackagerUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.internal.ui.jarpackager.JarPackagerMessages;
import org.eclipse.jdt.ui.jarpackager.IJarDescriptionReader;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Shell;

/**
 * This action delegate opens the JAR Package Wizard and initializes
 * it with the selected JAR package description.
 *
 * AspectJ Changes marked
 */
public class OpenAJJarPackageWizardActionDelegate extends AJJarPackageActionDelegate { // AspectJ Change

    private IJarDescriptionReader fReader;

    /*
     * @see IActionDelegate
     */
    public void run(IAction action) {
        Shell parent= getShell();
        AJJarPackageData jarPackage;  // AspectJ Change
        String errorDetail;
        try {
            jarPackage= readJarPackage(getDescriptionFile(getSelection()));
        } catch (CoreException ex) {
            errorDetail= ex.getLocalizedMessage();
            MessageDialog.openError(parent, JarPackagerMessages.OpenJarPackageWizardDelegate_error_openJarPackager_title, JarPackagerMessages.OpenJarPackageWizardDelegate_error_openJarPackager_message + errorDetail);
            return;
        }

      if (fReader != null && !fReader.getStatus().isOK())
            ErrorDialog.openError(parent, JarPackagerMessages.OpenJarPackageWizardDelegate_jarDescriptionReaderWarnings_title, null, fReader.getStatus());
        AJJarPackageWizard wizard= new AJJarPackageWizard();  // AspectJ Change
        wizard.init(getWorkbench(), jarPackage);
        WizardDialog dialog= new WizardDialog(parent, wizard);
        dialog.create();
        dialog.open();
    }

    /**
     * Reads the JAR package spec from file.
     * @param description
     * @return the JAR package spec
     * @throws CoreException
     */
    private AJJarPackageData readJarPackage(IFile description) throws CoreException { // AspectJ Change
        Assert.isLegal(description.isAccessible());
        Assert.isNotNull(description.getFileExtension());
        Assert.isLegal(description.getFileExtension().equals(AJJarPackagerUtil.DESCRIPTION_EXTENSION));  // AspectJ Change
        AJJarPackageData jarPackage= new AJJarPackageData(); // AspectJ Change
        try {
            fReader= jarPackage.createJarDescriptionReader(description.getContents());
            fReader.read(jarPackage);
        } finally {
            if (fReader != null)
                fReader.close();
        }
        return jarPackage;
    }
}
