/*******************************************************************************
 * Copyright (c) 2008 SpringSource Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     SpringSource - initial API and implementation
 *     Andrew Eisenberg
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.wizards;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.eclipse.ajdt.core.AspectJCorePreferences;
import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.internal.core.JavaModelStatus;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElementAttribute;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListLabelProvider;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.TreeListDialogField;
import org.eclipse.jdt.ui.JavaElementComparator;
import org.eclipse.jdt.ui.wizards.BuildPathDialogAccess;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.ui.dialogs.ListSelectionDialog;

/**
 *
 * @author andrew
 * @created Jun 26, 2008
 *
 * Abstract class that defines a block in a build paths dialog
 *
 */
public abstract class PathBlock {

    /* constants representing button locations */
    protected static final int IDX_ADDJAR = 3;
    protected static final int IDX_ADDEXT = 4;
    protected static final int IDX_ADDVAR = 5;
    protected static final int IDX_ADDFOL = 6;
    protected static final int IDX_ADDCON = 7;
    protected static final int IDX_ADDPRJ = 8;
    protected static final int IDX_EDIT = 9;
    protected static final int IDX_REMOVE = 10;


    static final String RESTRICTED_TO = "Restricted to";
    static final String NO_RESTRICTIONS = "<no restrictions>";

    protected class LibrariesAdapter implements IDialogFieldListener, ITreeListAdapter<CPListElement> {

      // ---------- IDialogFieldListener --------

      @Override
      public void dialogFieldChanged(DialogField field) {
        libaryPageDialogFieldChanged(field);
      }

      // -------- ITreeListAdapter --------

      @Override
      public void customButtonPressed(TreeListDialogField<CPListElement> field, int index) {
        libaryPageCustomButtonPressed(field, index);
      }

      @Override
      public void doubleClicked(TreeListDialogField<CPListElement> field) {
        // do nothing
      }

      @Override
      public Object[] getChildren(TreeListDialogField<CPListElement> field, Object element) {
        if (element instanceof CPListElement) {
          CPListElement listElement = (CPListElement) element;
          IClasspathEntry entry = listElement.getClasspathEntry();

          // Bug 243356 : Check if entry is in a classpath container
          IClasspathContainer container = getClasspathContainer(entry);
          if (container != null)
            return new Object[] { "From: " + container.getDescription() };

          // Bug 273770 : Check if entry is a classpath container that has been restricted
          if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
            Object[] children = listElement.getChildren(true);
            for (Object child : children) {
              if (child instanceof CPListElementAttribute &&
                  !((CPListElementAttribute) child).isBuiltIn() &&
                  ((CPListElementAttribute) child).getClasspathAttribute().getName().equals(getRestrictionPathAttrName())
              )
              {
                return new Object[] { child };
              }
            }
          }
        }
        return null;
      }

      @Override
      public Object getParent(TreeListDialogField<CPListElement> field, Object element) {
        if (element instanceof CPListElementAttribute) {
          return ((CPListElementAttribute) element).getParent();
        }
        else {
          return null;
        }
      }

      @Override
      public boolean hasChildren(TreeListDialogField<CPListElement> field, Object element) {
        Object[] children = getChildren(field, element);
        return children != null && children.length > 0;
      }

      @Override
      public void keyPressed(TreeListDialogField<CPListElement> field, KeyEvent event) { }

      @Override
      public void selectionChanged(TreeListDialogField<CPListElement> field) {
        libaryPageSelectionChanged(field);
      }

    }

    private final int fPageIndex;
    private IJavaProject fCurrJProject;
    private String fUserSettingsTimeStamp;

    protected final IWorkspaceRoot fWorkspaceRoot;
    protected TreeListDialogField<CPListElement> fPathList;
    protected IStatusChangeListener fContext;
    protected StatusInfo fPathStatus;  /* status for path list being self-consistent */
    protected StatusInfo fJavaBuildPathStatus; /* status for path list being consistent with Java build path */
    private final PathBlockWorkbookPage workbookPage;

    protected PathBlock(IStatusChangeListener context, int pageToShow) {
        fWorkspaceRoot = AspectJPlugin.getWorkspace().getRoot();
        fContext= context;
        fPageIndex= pageToShow;

        LibrariesAdapter adapter= new LibrariesAdapter();
        String[] buttonLabels= new String[] {
                null,
                null,
                null,
                /* IDX_ADDJAR */ UIMessages.PathLibrariesWorkbookPage_libraries_addjar_button,
                /* IDX_ADDEXT */ UIMessages.PathLibrariesWorkbookPage_libraries_addextjar_button,
                /* IDX_ADDVAR */ UIMessages.PathLibrariesWorkbookPage_libraries_addvariable_button,
                /* IDX_ADDFOL */ UIMessages.PathLibrariesWorkbookPage_libraries_addclassfolder_button,
                /* IDX_ADDCON */ UIMessages.PathLibrariesWorkbookPage_libraries_addlibrary_button,
                /* IDX_ADDPRJ */ UIMessages.PathLibrariesWorkbookPage_libraries_addproject_button,
                /* IDX_EDIT */   UIMessages.PathLibrariesWorkbookPage_libraries_edit_button,
                /* IDX_REMOVE */ UIMessages.PathLibrariesWorkbookPage_libraries_remove_button
        };
        fPathList = new TreeListDialogField<>(adapter, buttonLabels, new CPListLabelProvider());
        fPathList.setDialogFieldListener(adapter);
        fPathList.setRemoveButtonIndex(IDX_REMOVE);
        fPathList.enableButton(IDX_REMOVE, false);
        fPathList.enableButton(IDX_EDIT, false);

        fCurrJProject = null;
        fJavaBuildPathStatus= new StatusInfo();
        fPathStatus= new StatusInfo();
        workbookPage = new PathBlockWorkbookPage(fPathList);
    }

    protected abstract void internalSetProjectPath(
        List<CPListElement> pathEntries,
        StringBuffer pathBuffer, StringBuffer contentKindBuffer,
        StringBuffer entryKindBuffer
    );


    protected abstract String getBlockNote();

    public abstract String getBlockTitle();

    protected abstract String getPathAttributeName();
    protected abstract String getRestrictionPathAttrName();

    public void init() {
        initializeTimeStamp();
        updatePathStatus();
    }


    private void libaryPageSelectionChanged(DialogField field) {
        List<Object> selElements = fPathList.getSelectedElements();
        fPathList.enableButton(IDX_REMOVE, canRemove(selElements));
        fPathList.enableButton(IDX_EDIT, canEdit(selElements));
    }

    private void libaryPageDialogFieldChanged(DialogField field) {
        if (fCurrJProject != null) {
            // already initialized
            updatePathStatus();
            doStatusLineUpdate();
        }
    }

  private void libaryPageCustomButtonPressed(DialogField field, int index) {
        CPListElement[] libentries = null;
        switch (index) {
            case IDX_ADDJAR: /* add jar */
                libentries = openJarFileDialog();
                break;
            case IDX_ADDEXT: /* add external jar */
                libentries = openExtJarFileDialog();
                break;
            case IDX_ADDVAR: /* add variable */
                libentries = openVariableSelectionDialog();
                break;
            case IDX_ADDFOL: /* add class folder */
                libentries = openClassFolderDialog();
                break;
            case IDX_ADDCON: /* add container */
                libentries = openContainerSelectionDialog();
                break;
            case IDX_ADDPRJ: /* add project reference */
                libentries = openProjectSelectionDialog();
                break;
            case IDX_EDIT: /* edit the restrictions of a classpath container */
                editRestictions(fPathList.getSelectedElements());
                return;
            case IDX_REMOVE: /* remove */
                removeEntry();
                return;
        }
        if (libentries != null) {
            int nElementsChosen = libentries.length;
            // remove duplicates, but ignore
            // elements with classpath containers
            // since there is no direct control over
            // them.
            List<?> cplist = fPathList.getElements();
            List<CPListElement> elementsToAdd = new ArrayList<>(nElementsChosen);

            for (CPListElement curr : libentries) {
                if (!cplist.contains(curr) && !elementsToAdd.contains(curr)) {
                    elementsToAdd.add(curr);
                    curr.setAttribute(CPListElement.SOURCEATTACHMENT,
                        BuildPathSupport.guessSourceAttachment(curr));
                }
            }
            if (!elementsToAdd.isEmpty() && (index == IDX_ADDFOL)) {
                askForAddingExclusionPatternsDialog(elementsToAdd);
            }

            fPathList.addElements(elementsToAdd);
            fPathList.postSetSelection(new StructuredSelection(libentries));

            updatePathStatus();
            doStatusLineUpdate();
        }
    }

    void editRestictions(List<?> selectedElements) {

        if (selectedElements == null || selectedElements.size() != 1)
            return;
        Object o = selectedElements.get(0);
        if (o instanceof CPListElementAttribute) {
            CPListElementAttribute attr = (CPListElementAttribute) o;
            boolean success = workbookPage.editCustomEntry(attr);
            if (success)
                fPathList.refresh(o);
        }
    }

    protected void doStatusLineUpdate() {
        if (fContext != null) {
            IStatus res = findMostSevereStatus();
            fContext.statusChanged(res);
        }
    }

    /**
     * checks for missing entries in the path
     */
    private void updatePathStatus() {
        fPathStatus.setOK();

        List<CPListElement> elements = fPathList.getElements();

        CPListElement entryMissing = null;
        int nEntriesMissing = 0;
        // TODO: 'entries' is only written to, never read -> remove?
        IClasspathEntry[] entries = new IClasspathEntry[elements.size()];

        for (int i = elements.size() - 1; i >= 0; i--) {
            CPListElement currElement = elements.get(i);
            entries[i] = currElement.getClasspathEntry();
            if (currElement.isMissing()) {
                nEntriesMissing++;
                if (entryMissing == null)
                    entryMissing = currElement;
            }
        }

        if (nEntriesMissing > 0) {
            if (nEntriesMissing == 1)
                fPathStatus.setWarning(UIMessages.InPathBlock_warning_EntryMissing);
            else
                fPathStatus.setWarning(UIMessages.InPathBlock_warning_EntriesMissing);
        }
        updateJavaBuildPathStatus();
    }


    /**
     * checks to see if the path is well-formed,
     * duplicates, etc.
     */
    protected void updateJavaBuildPathStatus() {
        List<CPListElement> elements = fPathList.getElements();
        List<IClasspathEntry> entries = new ArrayList<>();

        for (CPListElement currElement : elements) {
            // ignore elements that are part of a container
            // since user does not have direct control over removing them
            if (!inClasspathContainer(currElement))
                entries.add(currElement.getClasspathEntry());
        }

        IPath outPath;
        try {
            outPath = fCurrJProject.getOutputLocation();
        } catch (JavaModelException e) {
            outPath = fCurrJProject.getPath();
        }

        IClasspathEntry[] entriesArr = entries.toArray(new IClasspathEntry[0]);
        IJavaModelStatus status = JavaConventions.validateClasspath(fCurrJProject, entriesArr, outPath);

        if (!status.isOK()) {
            fJavaBuildPathStatus.setError(status.getMessage());
            return;
        }

        IJavaModelStatus dupStatus = checkForDuplicates(fCurrJProject, entriesArr);
        if (!dupStatus.isOK()) {
            fJavaBuildPathStatus.setError(dupStatus.getMessage());
            return;
        }

        fJavaBuildPathStatus.setOK();
    }


    /**
     * Checks for duplicate entries on the inpath compared to the Java build
     * path
     *
     * This checks to make sure that duplicate entries are not being referred
     * to. For example, it is possible for the JUnit jar to be referred to
     * through a classpath variable as well as a classpath container. This
     * method checks for such duplicates
     *
     * 1. if an inpath entry is on the build path, then remove it from checking
     * 2. resolve the remaining inpath entries 3. resolve the build path 4.
     * there should be no overlap
     */
    private IJavaModelStatus checkForDuplicates(IJavaProject currJProject,
            IClasspathEntry[] entries) {
        try {
            Map<String, IClasspathEntry> allEntries = new HashMap<>(entries.length, 1.0f);
          for (IClasspathEntry value : entries) {
            // ignore entries that are inside of a container
            if (getClasspathContainer(value) == null) {
              allEntries.put(value.getPath().toPortableString(),
                value);
            }
          }

            IClasspathEntry[] rawProjectClasspath = currJProject
                    .getRawClasspath();
          for (IClasspathEntry classpathEntry : rawProjectClasspath) {
            allEntries.remove(classpathEntry.getPath()
              .toPortableString());
          }

            IClasspathEntry[] resolvedProjectClasspath = currJProject
                    .getResolvedClasspath(true);
            Map<String, IClasspathEntry> resolvedEntries = new HashMap<>();
          for (IClasspathEntry classpathEntry : allEntries.values()) {
            ClasspathEntry rawEntry = (ClasspathEntry) classpathEntry;
            switch (rawEntry.entryKind) {
              case IClasspathEntry.CPE_SOURCE:
              case IClasspathEntry.CPE_LIBRARY:
              case IClasspathEntry.CPE_VARIABLE:
                IClasspathEntry resolvedEntry = JavaCore
                  .getResolvedClasspathEntry(rawEntry);
                resolvedEntries.put(resolvedEntry.getPath()
                  .toPortableString(), resolvedEntry);
                break;
              case IClasspathEntry.CPE_CONTAINER:
                List<IClasspathEntry> containerEntries = AspectJCorePreferences.resolveClasspathContainer(
                  rawEntry, currJProject.getProject());

                for (IClasspathEntry entry : containerEntries) {
                  IClasspathEntry containerEntry = entry;
                  resolvedEntries.put(containerEntry.getPath()
                    .toPortableString(), containerEntry);
                }
                break;

              case IClasspathEntry.CPE_PROJECT:
                IProject thisProject = currJProject.getProject();
                IProject requiredProj = thisProject.getWorkspace().getRoot().getProject(
                  rawEntry.getPath().makeRelative().toPortableString());
                if (!requiredProj.getName().equals(thisProject.getName())
                    && requiredProj.exists())
                {
                  List<IClasspathEntry> containerEntries2 = AspectJCorePreferences.resolveDependentProjectClasspath(rawEntry, requiredProj);
                  for (IClasspathEntry o : containerEntries2) {
                    IClasspathEntry containerEntry = o;
                    resolvedEntries.put(containerEntry.getPath()
                      .toPortableString(), containerEntry);
                  }

                }
                break;
            }
          }

          for (IClasspathEntry iClasspathEntry : resolvedProjectClasspath) {
            if (resolvedEntries.containsKey(iClasspathEntry
              .getPath().toPortableString()))
            {
              // duplicate found.
              return new JavaModelStatus(IStatus.WARNING,
                IStatus.WARNING, currJProject, currJProject
                .getPath(),
                UIMessages.InPathBlock_DuplicateBuildEntry
                + iClasspathEntry.getPath());
            }
          }

            return JavaModelStatus.VERIFIED_OK;
        } catch (JavaModelException e) {
            return new JavaModelStatus(e);
        }

    }



    private void removeEntry() {
        List<Object> selElements = fPathList.getSelectedElements();
        for (int i = selElements.size() - 1; i >= 0; i--) {
            Object elem = selElements.get(i);
            if (elem instanceof CPListElementAttribute) {
                CPListElementAttribute attrib = (CPListElementAttribute) elem;
                attrib.getParent().setAttribute(attrib.getKey(), null);
                selElements.remove(i);
            }
        }
        if (selElements.isEmpty()) {
            fPathList.refresh();
            fPathList.dialogFieldChanged(); // validate
        } else {
            fPathList.removeElements(selElements);
        }
    }

    /**
     * only able to edit the restrictions child of a classpath container entry
     */
    private boolean canEdit(List<Object> selElements) {
        if (selElements.size() != 1)
            return false;
        Object elem = selElements.get(0);
        if (elem instanceof CPListElementAttribute) {
            Object attributeValue = ((CPListElementAttribute) elem).getValue();
            return attributeValue instanceof String;
        }
        return false;
    }


    /**
     * Determines whether or not the remove button is active.
     *
     * @param selElements Selected elements
     * @return true if all elements are CPListElements that are not attributes
     * and are not contained in classpath containers
     */
    private boolean canRemove(List<?> selElements) {
        if (selElements.size() == 0) {
            return false;
        }
      for (Object elem : selElements) {
        if (elem instanceof CPListElementAttribute) {
          if (((CPListElementAttribute) elem).getValue() == null) {
            return false;
          }
        }
        else if (elem instanceof CPListElement) {
          // Bug 243356
          // can't remove elements that are contained in a container
          CPListElement curr = (CPListElement) elem;
          if (inClasspathContainer(curr)) {
            return false;
          }
        }
      }
        return true;
    }

    private boolean inClasspathContainer(CPListElement element) {
        IClasspathAttribute[] attributes = element.getClasspathEntry().getExtraAttributes();
      for (IClasspathAttribute attribute : attributes) {
        if (AspectJCorePreferences.isAspectPathAttribute(attribute) ||
            AspectJCorePreferences.isInPathAttribute(attribute))
        {
          if (!(attribute.getValue().equals(attribute.getName()))) {
            return true;
          }
        }
      }
        return false;
    }

    private IClasspathContainer getClasspathContainer(IClasspathEntry classpathEntry) {
        IClasspathAttribute[] attributes = classpathEntry.getExtraAttributes();
      for (IClasspathAttribute attribute : attributes) {
        if (AspectJCorePreferences.isAspectPathAttribute(attribute) ||
            AspectJCorePreferences.isInPathAttribute(attribute))
        {
          // check to make sure this isn't the standard attribute, but one that is
          // enhanced with the container it came from.
          // When these attributes were created, the value was set to be the classpath container it came from
          if (attribute.getValue() != null && !attribute.getValue().equals(attribute.getName())) {
            try {
              return JavaCore.getClasspathContainer(new Path(attribute.getValue()), fCurrJProject);
            }
            catch (JavaModelException ignored) {
            }
          }
        }
      }
        return null;
    }

    // Don't think this is used.
    private void askForAddingExclusionPatternsDialog(List<CPListElement> newEntries) {
        HashSet modified = new HashSet();
        if (!modified.isEmpty()) {
            String title = UIMessages.InPathLibrariesWorkbookPage_exclusion_added_title;
            String message = UIMessages.InPathLibrariesWorkbookPage_exclusion_added_message;
            MessageDialog.openInformation(AspectJUIPlugin.getDefault()
                    .getActiveWorkbenchWindow().getShell(), title, message);
        }
    }

    private CPListElement[] openClassFolderDialog() {
        IPath[] selected = BuildPathDialogAccess.chooseClassFolderEntries(
                getShell(), fCurrJProject.getPath(),
                getUsedContainers());
        if (selected != null) {
            ArrayList<CPListElement> res = new ArrayList<>();
          for (IPath curr : selected) {
            IResource resource = fWorkspaceRoot.findMember(curr);
            if (resource instanceof IContainer) {
              res.add(newCPLibraryElement(resource));
            }
          }
            return res.toArray(new CPListElement[0]);
        }
        return null;
    }

    private CPListElement[] openJarFileDialog() {
        IPath[] selected = BuildPathDialogAccess.chooseJAREntries(
                getShell(), fCurrJProject.getPath(),
                getUsedContainers());
        if (selected != null) {
            ArrayList<CPListElement> res = new ArrayList<>();

          for (IPath curr : selected) {
            IResource resource = fWorkspaceRoot.findMember(curr);

            String outJar = AspectJCorePreferences
              .getProjectOutJar(fCurrJProject.getProject());
            StringBuilder projectOutJar = new StringBuilder();
            projectOutJar.append(fCurrJProject.getPath().toString());
            projectOutJar.append("/").append(outJar); //$NON-NLS-1$

            if (resource.getFullPath().toString().equals(
              projectOutJar.toString()))
            {
              MessageDialog.openInformation(getShell(),
                UIMessages.buildpathwarning_title,
                UIMessages.addtoinpathwarning);
            }
            else if (resource instanceof IFile) {
              res.add(newCPLibraryElement(resource));
            }
          }
            return res.toArray(new CPListElement[0]);
        }
        return null;
    }

    private CPListElement[] openExtJarFileDialog() {
        IPath[] selected = BuildPathDialogAccess
                .chooseExternalJAREntries(getShell());
        if (selected != null) {
            ArrayList<CPListElement> res = new ArrayList<>();
          for (IPath iPath : selected) {
            res.add(new CPListElement(fCurrJProject,
              IClasspathEntry.CPE_LIBRARY, iPath, null));
          }
            return res.toArray(new CPListElement[0]);
        }
        return null;
    }



    private CPListElement[] openVariableSelectionDialog() {
        List<CPListElement> existingElements = fPathList.getElements();
        ArrayList<IPath> existingPaths = new ArrayList<>(existingElements.size());
      for (Object existingElement : existingElements) {
        CPListElement elem = (CPListElement) existingElement;
        if (elem.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
          existingPaths.add(elem.getPath());
        }
      }
        IPath[] existingPathsArray = existingPaths
                .toArray(new IPath[0]);

        IPath[] paths = BuildPathDialogAccess.chooseVariableEntries(
                getShell(), existingPathsArray);
        if (paths != null) {
            ArrayList<CPListElement> result = new ArrayList<>();
          for (IPath path : paths) {
            CPListElement elem = new CPListElement(fCurrJProject,
              IClasspathEntry.CPE_VARIABLE, path, null);
            IPath resolvedPath = JavaCore
              .getResolvedVariablePath(path);
            elem.setIsMissing((resolvedPath == null)
                              || !resolvedPath.toFile().exists());
            if (!existingElements.contains(elem)) {
              result.add(elem);
            }
          }
            return result.toArray(new CPListElement[0]);
        }
        return null;
    }

    private CPListElement[] openContainerSelectionDialog() {
        IClasspathEntry[] created = BuildPathDialogAccess
                .chooseContainerEntries(getShell(), fCurrJProject,
                        getRawClasspath());
        if (created != null) {
            // check for existing restrictions
            try {
                IClasspathEntry[] existing = getJavaProject().getRawClasspath();
                for (int i = 0; i < created.length; i++) {
                  for (IClasspathEntry iClasspathEntry : existing) {
                    if (created[i].getPath().equals(iClasspathEntry.getPath())) {
                      created[i] = iClasspathEntry;
                    }
                  }
                    created[i] = AspectJCorePreferences.ensureHasAttribute(created[i], getRestrictionPathAttrName(), "");
                }
            } catch (JavaModelException ignored) {
            }

            CPListElement[] res = new CPListElement[created.length];
            for (int i = 0; i < res.length; i++) {
                res[i] = CPListElement.createFromExisting(created[i], getJavaProject());
            }
            return res;
        }
        return null;
    }

    private CPListElement[] openProjectSelectionDialog() {

        try {
          ArrayList<IJavaProject> selectable = new ArrayList<>(Arrays.asList(fCurrJProject.getJavaModel().getJavaProjects()));
            selectable.remove(fCurrJProject);

            List<CPListElement> elements= fPathList.getElements();
            for (int i= 0; i < elements.size(); i++) {
                CPListElement curr= (CPListElement) elements.get(0);
                if (curr.getEntryKind() == IClasspathEntry.CPE_PROJECT) {
                    IJavaProject proj= (IJavaProject) JavaCore.create(curr.getResource());
                    selectable.remove(proj);
                }
            }
            Object[] selectArr= selectable.toArray();
            new JavaElementComparator().sort(null, selectArr);

            ListSelectionDialog dialog= new ListSelectionDialog(getShell(), Arrays.asList(selectArr), new ArrayContentProvider(), new JavaUILabelProvider(), NewWizardMessages.ProjectsWorkbookPage_chooseProjects_message);
            dialog.setTitle("Select Projects");
            dialog.setHelpAvailable(false);
            if (dialog.open() == Window.OK) {
                Object[] result= dialog.getResult();
                CPListElement[] cpElements= new CPListElement[result.length];
                for (int i= 0; i < result.length; i++) {
                    IJavaProject curr= (IJavaProject) result[i];
                    cpElements[i]= new CPListElement(fCurrJProject, IClasspathEntry.CPE_PROJECT, curr.getPath(), curr.getResource());
                }
                return cpElements;
            }
        } catch (JavaModelException ignored) {
        }
        return null;
    }

    private IClasspathEntry[] getRawClasspath() {
        IClasspathEntry[] currEntries = new IClasspathEntry[fPathList.getSize()];
        for (int i = 0; i < currEntries.length; i++) {
            CPListElement curr = fPathList.getElement(i);
            currEntries[i] = curr.getClasspathEntry();
        }
        return currEntries;
    }



    private CPListElement newCPLibraryElement(IResource res) {
        return new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY,
                res.getFullPath(), res);
    }


  private IPath[] getUsedContainers() {
        ArrayList<IPath> res = new ArrayList<>();
        if (fCurrJProject.exists()) {
            try {
                IPath outputLocation = fCurrJProject.getOutputLocation();
                if (outputLocation != null && outputLocation.segmentCount() > 1) { // !=
                                                                                    // Project
                    res.add(outputLocation);
                }
            } catch (JavaModelException ignored) {}
        }

        List<CPListElement> cplist = fPathList.getElements();
    for (Object o : cplist) {
      CPListElement elem = (CPListElement) o;
      if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
        IResource resource = elem.getResource();
        if (resource instanceof IContainer) {
          res.add(resource.getFullPath());
        }
      }
    }
        return res.toArray(new IPath[0]);
    }

    public TabItem tabContent(TabFolder folder) {

        TabItem item;

        // TODO : Move over to the AJDT image registry
        ImageRegistry imageRegistry = JavaPlugin.getDefault()
                .getImageRegistry();

        item = new TabItem(folder, SWT.NONE);
        item.setText(getBlockTitle());
        item.setImage(imageRegistry.get(JavaPluginImages.IMG_OBJS_LIBRARY));
        item.setData(workbookPage);
        item.setControl(workbookPage.getControl(folder));

        Control control = item.getControl();
        if (control instanceof Composite) {
            Label label = new Label((Composite) control, SWT.LEFT | SWT.WRAP);
            label.setText(getBlockNote());
        }
        return item;
    }

    public void configureJavaProject(IProgressMonitor monitor)
            throws CoreException, InterruptedException {
        if (monitor == null) {
            monitor = new NullProgressMonitor();
        }
        monitor.setTaskName(NLS.bind(UIMessages.PathBlock_operationdesc_java,
                getBlockTitle()));
        monitor.beginTask("", 10); //$NON-NLS-1$

        try {
            internalConfigureJavaProject(fPathList.getElements(), monitor);
            initializeTimeStamp();
        } finally {
            monitor.done();
        }
    }

    /**
     * @param buffer
     * @return
     */
    protected StringBuffer removeFinalPathSeparatorChar(StringBuffer buffer) {
        // Chop off extra path separator from end of the string.
        if ((buffer.length() > 0)
                && (buffer.charAt(buffer.length() - 1) == File.pathSeparatorChar)) {
            buffer = buffer.deleteCharAt(buffer.length() - 1);
        }
        return buffer;
    }

    protected void internalConfigureJavaProject(List pathElements,
            IProgressMonitor monitor) throws CoreException
    {
        int nEntries = pathElements.size();
        List /* IClasspathEntry */<IClasspathEntry> pathEntries = new ArrayList<>();

      for (Object pathElement : pathElements) {
        // Bug 243356
        // remove from the list if this is an entry contained in a container
        // because entries in containers are computed separately
        CPListElement element = ((CPListElement) pathElement);
        if (!inClasspathContainer(element)) {
          pathEntries.add(element.getClasspathEntry());
        }
      }

        monitor.worked(2);


        Map<IPath, String> inpathRestrictions = new HashMap<>();
        Map<IPath, String> aspectpathRestrictions = new HashMap<>();
        StringBuffer pathBuffer = new StringBuffer();
        StringBuffer contentKindBuffer = new StringBuffer();
        StringBuffer entryKindBuffer = new StringBuffer();
      for (IClasspathEntry pathEntry : pathEntries) {
        pathBuffer.append(pathEntry.getPath());
        pathBuffer.append(File.pathSeparator);
        contentKindBuffer.append(pathEntry.getContentKind());
        contentKindBuffer.append(File.pathSeparator);
        entryKindBuffer.append(pathEntry.getEntryKind());
        entryKindBuffer.append(File.pathSeparator);
        String inpathRestriction = AspectJCorePreferences.getRestriction(pathEntry,
          AspectJCorePreferences.INPATH_RESTRICTION_ATTRIBUTE_NAME);
        if (inpathRestriction != null && inpathRestriction.length() > 0) {
          inpathRestrictions.put(pathEntry.getPath(), inpathRestriction);
        }
        String aspectpathRestriction = AspectJCorePreferences.getRestriction(pathEntry,
          AspectJCorePreferences.ASPECTPATH_RESTRICTION_ATTRIBUTE_NAME);
        if (aspectpathRestriction != null && aspectpathRestriction.length() > 0) {
          aspectpathRestrictions.put(pathEntry.getPath(), aspectpathRestriction);
        }
      }

        pathBuffer = removeFinalPathSeparatorChar(pathBuffer);
        contentKindBuffer = removeFinalPathSeparatorChar(contentKindBuffer);
        entryKindBuffer = removeFinalPathSeparatorChar(entryKindBuffer);

        internalSetProjectPath(pathElements, pathBuffer,
                contentKindBuffer, entryKindBuffer);

        // update the classpath with the restrictions
        if (inpathRestrictions.size() > 0 || aspectpathRestrictions.size() > 0) {
            IClasspathEntry[] entries = getJavaProject().getRawClasspath();
            boolean hasChanges = false;
            if (inpathRestrictions.size() > 0) {
                hasChanges |= updatePathRestrictions(entries, inpathRestrictions, false);
            }
            if (aspectpathRestrictions.size() > 0) {
                hasChanges |= updatePathRestrictions(entries, aspectpathRestrictions, true);
            }
            if (hasChanges) {
                getJavaProject().setRawClasspath(entries, new NullProgressMonitor());
            }
        }
    }

    // ensure that all the entries have the expected restrictions
    private boolean updatePathRestrictions(IClasspathEntry[] entries,
            Map<IPath, String> restrictions, boolean isAspectPath) {
        String restrictionKind = isAspectPath ? AspectJCorePreferences.ASPECTPATH_RESTRICTION_ATTRIBUTE_NAME
                : AspectJCorePreferences.INPATH_RESTRICTION_ATTRIBUTE_NAME;

        boolean hasChanges = false;
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
                // restrictions only available on container entries

                if (restrictions.containsKey(entries[i].getPath())) {
                    String restrictionStr = restrictions.get(entries[i].getPath());
                    entries[i] = AspectJCorePreferences.updatePathRestrictions(entries[i], restrictionStr,
                            restrictionKind);
                    hasChanges = true;
                }
            }
        }
        return hasChanges;
    }


    protected List<CPListElement> getExistingEntries(IClasspathEntry[] pathEntries) {
        List<CPListElement> newPath = new ArrayList<>();
      for (IClasspathEntry pathEntry : pathEntries) {
        IClasspathEntry curr = pathEntry;
        if (curr.getEntryKind() == IClasspathEntry.CPE_CONTAINER) {
          curr = AspectJCorePreferences.ensureHasAttribute(curr, getRestrictionPathAttrName(), "");
        }
        newPath.add(CPListElement.createFromExisting(curr,
          fCurrJProject));
      }
        return newPath;
    }


    protected Shell getShell() {
        return AspectJUIPlugin.getDefault().getActiveWorkbenchWindow()
                .getShell();
    }

    protected IJavaProject getJavaProject() {
        return fCurrJProject;
    }

    protected void setJavaProject(IJavaProject project) {
        this.fCurrJProject = project;
    }


    protected IStatus findMostSevereStatus() {
        return StatusUtil.getMostSevere(new IStatus[] { fPathStatus,
                fJavaBuildPathStatus });
    }

    public int getPageIndex() {
        return fPageIndex;
    }

    protected String getEncodedSettings() {
        StringBuffer buf= new StringBuffer();

        int nElements= fPathList.getSize();
        buf.append('[').append(nElements).append(']');
        for (int i= 0; i < nElements; i++) {
            CPListElement elem= fPathList.getElement(i);
            elem.appendEncodedSettings(buf);
        }
        return buf.toString();
    }

    public boolean hasChangesInDialog() {
        String currSettings= getEncodedSettings();
        return !currSettings.equals(fUserSettingsTimeStamp);
    }

    public void initializeTimeStamp() {
        fUserSettingsTimeStamp= getEncodedSettings();
    }
}
