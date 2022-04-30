/*******************************************************************************
 * Copyright (c) 2004, 2005, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Andrew Eisenberg - factored out common code with AspectPathBlock
 *                        into PathBlok
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.wizards;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.FolderSelectionDialog;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;


/**
 * @author gharley
 *
 */
public class InPathBlock extends PathBlock {
    private final StatusInfo fOutputFolderStatus;
    private IPath fOutputLocationPath;
    private StringButtonDialogField fInpathOutputField;
    private ModifyListener FOutputPathModifyListener;
    private Text fOutputPathTextBox;

    public InPathBlock(IStatusChangeListener context, int pageToShow) {
        super(context, pageToShow);
        fPathList.setLabelText(UIMessages.InPathBlock_inpath_label);
        fOutputFolderStatus= new StatusInfo();
    }

    public void init(IJavaProject jproject, IClasspathEntry[] inpathEntries) {
        setJavaProject(jproject);

        List<CPListElement> newInPath = null;
        if (inpathEntries != null)
            newInPath = getExistingEntries(inpathEntries);
        if (newInPath == null)
            newInPath = new ArrayList<>();

        IProject project = jproject.getProject();
        String outFolderStr = AspectJCorePreferences.getProjectInpathOutFolder(project);
        IPath outputPath;

        if (outFolderStr == null || outFolderStr.equals("")) //$NON-NLS-1$
            outputPath = null;
        else
            outputPath = new Path(outFolderStr);
        changeInpathOutputText(outputPath);

        fPathList.setElements(newInPath);
        super.init();
    }


    protected void updateJavaBuildPathStatus() {
        super.updateJavaBuildPathStatus();
        updateOutputFolderStatus();
    }


    private void updateOutputFolderStatus() {
        if (fOutputLocationPath == null) {
            // use default
            fOutputFolderStatus.setOK();
            return;
        }

        // return OK if output folder exists, or ERROR if folder is invalid.
        // path is relative to the workspace.
        if (fOutputLocationPath.segmentCount() < 2 ||
                fOutputLocationPath.matchingFirstSegments(getJavaProject().getPath()) == 0) {
            fOutputFolderStatus.setError(UIMessages.InPathBlock_outFolder_1 +
                    fOutputLocationPath.toPortableString() + UIMessages.InPathBlock_outFolder_2);
            return;

        }


        IFolder outFolder = fWorkspaceRoot.getFolder(fOutputLocationPath);
        if (outFolder.exists()) {
            fOutputFolderStatus.setOK();
        } else {
            fOutputFolderStatus.setError(UIMessages.InPathBlock_outFolder_3 +
                    fOutputLocationPath.toPortableString() + UIMessages.InPathBlock_outFolder_4);
        }
    }




    protected void internalSetProjectPath(List<CPListElement> pathEntries,
            StringBuffer pathBuffer, StringBuffer contentKindBuffer,
            StringBuffer entryKindBuffer) {
        AspectJCorePreferences.setProjectInPath(getJavaProject().getProject(),pathBuffer.toString(),
                contentKindBuffer.toString(), entryKindBuffer.toString());
    }



    public TabItem tabContent(TabFolder folder){
        TabItem item = super.tabContent(folder);

        // create the inpath outfolder
        Control control = item.getControl();
        if (control instanceof Composite) {
            Composite parent = (Composite) control;

            // dummy label as a place holder in grid layout
            new Label(parent,SWT.LEFT | SWT.WRAP);
            Label label = new Label(parent,SWT.LEFT | SWT.WRAP);
            label.setText(UIMessages.InPathBlock_6);

            fInpathOutputField = new StringButtonDialogField(field -> {
                IContainer container= chooseContainer(fOutputLocationPath);
                if (container != null) {
                    changeInpathOutputText(container.getFullPath());
                }
            });
            if (fOutputLocationPath != null) {
                fInpathOutputField.setTextWithoutUpdate(fOutputLocationPath.toPortableString());
            }
            fInpathOutputField.setButtonLabel(UIMessages.InPathBlock_Browse);
            fInpathOutputField.doFillIntoGrid(parent, 3);
            FOutputPathModifyListener = e -> changeInpathOutputFolder();
            fOutputPathTextBox = fInpathOutputField.getTextControl(parent);
            fOutputPathTextBox.addModifyListener(FOutputPathModifyListener);

        }
        return item;
    }

    private void changeInpathOutputFolder() {
        String newText = fInpathOutputField.getText();
        if (newText.equals("")) { //$NON-NLS-1$
            fOutputLocationPath = null;
        } else {
            fOutputLocationPath = new Path(newText);
        }
        updateOutputFolderStatus();
        updateJavaBuildPathStatus();
        doStatusLineUpdate();
    }

    private void changeInpathOutputText(IPath newPath) {
        String newText;
        if (newPath == null) {
            fOutputLocationPath = null;  // use default
            newText = ""; //$NON-NLS-1$
        } else {
            fOutputLocationPath = newPath.makeRelative();
            newText = fOutputLocationPath.toPortableString();
        }
        fOutputPathTextBox.removeModifyListener(FOutputPathModifyListener);
        fOutputPathTextBox.setText(newText);
        fOutputPathTextBox.addModifyListener(FOutputPathModifyListener);

        updateOutputFolderStatus();
        updateJavaBuildPathStatus();
        doStatusLineUpdate();
    }


    // Borrowed from org.eclpse.jdt.internal.ui.wiaards.BuildPathsBlock
    private IContainer chooseContainer(IPath initPath) {
        Class[] acceptedClasses= new Class[] { IProject.class, IFolder.class };
        ISelectionStatusValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);
        IProject[] allProjects= fWorkspaceRoot.getProjects();
        ArrayList rejectedElements= new ArrayList(allProjects.length);
        IProject currProject= getJavaProject().getProject();
      for (IProject allProject : allProjects) {
        if (!allProject.equals(currProject)) {
          rejectedElements.add(allProject);
        }
      }
        ViewerFilter filter= new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());

        ILabelProvider lp= new WorkbenchLabelProvider();
        ITreeContentProvider cp= new WorkbenchContentProvider();

        IResource initSelection= null;
        if (initPath != null) {
            initSelection= fWorkspaceRoot.findMember(initPath);
        }

        FolderSelectionDialog dialog= new FolderSelectionDialog(getShell(), lp, cp);
        dialog.setTitle(UIMessages.BuildPathsBlock_ChooseOutputFolderDialog_title);
        dialog.setValidator(validator);
        dialog.setMessage(UIMessages.BuildPathsBlock_ChooseOutputFolderDialog_description);
        dialog.addFilter(filter);
        dialog.setInput(fWorkspaceRoot);
        dialog.setInitialSelection(initSelection);
        dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));

        if (dialog.open() == Window.OK) {
            return (IContainer)dialog.getFirstResult();
        }
        return null;
    }

    protected String getBlockNote() {
        return UIMessages.InPathBlock_note;
    }

    public String getBlockTitle() {
        return UIMessages.InPathBlock_tab_inpath_order;
    }


    protected IStatus findMostSevereStatus() {
        return StatusUtil.getMostSevere(new IStatus[] { fPathStatus, fOutputFolderStatus, fJavaBuildPathStatus });
    }


    public String getOutputFolder() {
        return fOutputLocationPath == null ? null : fOutputLocationPath.toPortableString();
    }

    protected String getEncodedSettings() {
        StringBuffer settings =  new StringBuffer(super.getEncodedSettings());
        CPListElement.appendEncodePath(fOutputLocationPath, settings).append(';');
        return settings.toString();
    }

    protected String getRestrictionPathAttrName() {
        return AspectJCorePreferences.INPATH_RESTRICTION_ATTRIBUTE_NAME;
    }

    protected String getPathAttributeName() {
        return AspectJCorePreferences.INPATH_ATTRIBUTE_NAME;
    }

}
