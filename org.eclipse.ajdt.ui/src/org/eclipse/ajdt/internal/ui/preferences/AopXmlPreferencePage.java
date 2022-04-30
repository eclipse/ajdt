package org.eclipse.ajdt.internal.ui.preferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.ajdt.core.AopXmlPreferences;
import org.eclipse.ajdt.core.builder.AJBuildJob;
import org.eclipse.ajdt.internal.ui.resources.AspectJImages;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.internal.ui.viewsupport.FilteredElementTreeSelectionDialog;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.dialogs.PropertyPage;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceComparator;

public class AopXmlPreferencePage extends PropertyPage implements
        IWorkbenchPropertyPage {

    private final int IDX_ADD = 0, IDX_REMOVE = 1, IDX_EDIT = 2;
    private final String[] buttonLabels = { "Add...", "Remove", "Edit..." };

    protected class AopXmlAdapter implements IDialogFieldListener, IListAdapter {
        public void dialogFieldChanged(DialogField field) {
            isDirty = true;
        }

        public void customButtonPressed(ListDialogField field, int index) {
            switch (index) {
                case IDX_ADD:
                    addNew();
                    break;
                case IDX_REMOVE:
                    removeSelected();
                    break;
                case IDX_EDIT:
                    editSelected();
                    break;
            }
        }

        public void doubleClicked(ListDialogField field) {
            editSelected();
        }

        public void selectionChanged(ListDialogField field) {
          field.enableButton(IDX_EDIT, field.getSelectedElements().size() == 1);
        }
    }

    private static class AopXmlLabelProvider implements ILabelProvider {

        private final Image image = AspectJImages.AOP_XML.getImageDescriptor().createImage();

        public Image getImage(Object element) {
            return image;
        }

        public String getText(Object element) {
            return ((IPath) element).toPortableString();
        }

        public void addListener(ILabelProviderListener listener) { }

        public void dispose() {
            image.dispose();
        }

        public boolean isLabelProperty(Object element, String property) {
            return false;
        }

        public void removeListener(ILabelProviderListener listener) { }
    }

    private ListDialogField control;
    private AopXmlPreferences preferenceStore;
    private boolean isDirty = false;

    public AopXmlPreferencePage() { }

    public void editSelected() {
        IPath path = (IPath) control.getSelectedElements().get(0);
        IPath[] newPaths = chooseAopXmlEntries(getShell(), path, getElementsAsArray());
        if (newPaths != null) {
            int index = control.getIndexOfElement(path);
            control.removeElement(path);
            if (newPaths != null) {
              for (IPath newPath : newPaths) {
                control.addElement(newPath, index++);
              }
            }

        }
    }

    public void removeSelected() {
        control.removeElements(control.getSelectedElements());
    }

    public void addNew() {
        IPath[] newPaths = chooseAopXmlEntries(getShell(), null, getElementsAsArray());
        if (newPaths != null) {
          for (IPath newPath : newPaths) {
            control.addElement(newPath);
          }
        }
    }

    public void storePreferences() {
        List elements = control.getElements();
        preferenceStore.setAopXmlFiles(elements != null ? (IPath[]) elements.toArray(new IPath[0]) : null);

    }

    private void initializeContents() {
        IPath[] paths = preferenceStore.getAopXmlFiles();
        control.setElements(Arrays.asList(paths));
    }


    /**
     * Shows the UI to select new aop.xml entries located in the workspace.
     * The dialog returns the selected entries or <code>null</code> if the dialog has
     * been canceled. The dialog does not apply any changes.
     *
     * @param shell The parent shell for the dialog.
     * @param initialSelection The path of the element (container or archive) to initially select or <code>null</code> to not select an entry.
     * @param usedEntries An array of paths that are already on the classpath and therefore should not be
     * selected again.
     * @return Returns the new JAR paths or <code>null</code> if the dialog has
     * been canceled by the user.
     */
    // copied from BuildPathDialogAccess
    public IPath[] chooseAopXmlEntries(Shell shell, IPath initialSelection, IPath[] usedEntries) {
        if (usedEntries == null) {
            throw new IllegalArgumentException();
        }

        Class[] acceptedClasses= new Class[] { IFile.class };
        TypedElementSelectionValidator validator= new TypedElementSelectionValidator(acceptedClasses, true);
        ArrayList usedJars= new ArrayList(usedEntries.length);
        IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
      for (IPath usedEntry : usedEntries) {
        IResource resource = root.findMember(usedEntry);
        if (resource instanceof IFile) {
          usedJars.add(resource);
        }
      }
        IResource focus= initialSelection != null ? root.findMember(initialSelection) : null;

        FilteredElementTreeSelectionDialog dialog = new FilteredElementTreeSelectionDialog(shell, new WorkbenchLabelProvider(), new WorkbenchContentProvider());
        dialog.setInitialFilter("*.xml");
        dialog.setHelpAvailable(false);
        dialog.setValidator(validator);
        dialog.setTitle("aop.xml selection");
        dialog.setMessage("Choose the aop.xml files to be added to the build");
        dialog.setInput(getProject());
        dialog.setComparator(new ResourceComparator(ResourceComparator.NAME));
        dialog.setInitialSelection(focus);
        if (dialog.open() == Window.OK) {
            Object[] elements= dialog.getResult();
            IPath[] res= new IPath[elements.length];
            for (int i= 0; i < res.length; i++) {
                IResource elem= (IResource)elements[i];
                res[i]= elem.getFullPath();
            }
            return res;
        }
        return null;
    }

    private IProject getProject() {
        return getElement().getAdapter(IProject.class);
    }


    protected Control createContents(Composite parent) {
        preferenceStore = new AopXmlPreferences(getProject());
        AopXmlAdapter adapter = new AopXmlAdapter();
        control = new ListDialogField(adapter, buttonLabels, new AopXmlLabelProvider());
        PixelConverter converter= new PixelConverter(parent);

        Composite composite= new Composite(parent, SWT.NONE);
        composite.setFont(parent.getFont());

        LayoutUtil.doDefaultLayout(composite, new DialogField[] { control }, true, SWT.DEFAULT, SWT.DEFAULT);
        LayoutUtil.setHorizontalGrabbing(control.getListControl(parent));

        int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
        control.setButtonsMinWidth(buttonBarWidth);
        control.setDialogFieldListener(adapter);
        control.enableButton(IDX_EDIT, false);

        initializeContents();

        return composite;
    }

    private IPath[] getElementsAsArray() {
        List paths = control.getElements();
        return (IPath[]) paths.toArray(new IPath[0]);
    }

    public boolean performOk() {
        if (isDirty) {
            storePreferences();
            int res = askToBuild();
            if (res == 2) {
                return false;
            }
            isDirty = false;
            if (res == 0) {
                AJBuildJob job = new AJBuildJob(getProject(), IncrementalProjectBuilder.FULL_BUILD);
                job.schedule();
            }
        }
        return true;
    }

    private int askToBuild() {
        MessageDialog dialog = new MessageDialog(getShell(),
                UIMessages.CompilerConfigurationBlock_needsbuild_title, null,
                UIMessages.CompilerConfigurationBlock_needsfullbuild_message +
                "\nAlso, be sure to add the -xmlConfigured option to your project's extra compiler options.",
                MessageDialog.QUESTION, new String[] {
                        IDialogConstants.YES_LABEL,
                        IDialogConstants.NO_LABEL,
                        IDialogConstants.CANCEL_LABEL }, 0);
        return dialog.open();
    }
}
