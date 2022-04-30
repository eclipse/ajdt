/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman - initial version
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.wizards;

import org.eclipse.ajdt.core.AspectJPlugin;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnit;
import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.core.javaelements.AspectElement;
import org.eclipse.ajdt.core.javaelements.PointcutElement;
import org.eclipse.ajdt.internal.ui.text.UIMessages;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.SelectionButtonDialogFieldGroup;
import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.PlatformUI;

public class NewAspectWizardPage extends NewTypeWizardPage {

    private final static String PAGE_NAME= "NewAspectWizardPage"; //$NON-NLS-1$
    private final static String SETTINGS_CREATEMAIN= "create_main"; //$NON-NLS-1$
    private final static String SETTINGS_CREATEUNIMPLEMENTED_PC = "create_unimplemented_pc"; //$NON-NLS-1$
    private final static String SETTINGS_CREATEUNIMPLEMENTED_METH = "create_unimplemented_meth"; //$NON-NLS-1$

    private final int ISSINGLETON_INDEX= 0, PERTHIS_INDEX= 1, PERTARGET_INDEX= 2;
    private final int PERCFLOW_INDEX= 3, PERCFLOWBELOW_INDEX= 4, PERTYPEWITHIN_INDEX= 5;

    private final SelectionButtonDialogFieldGroup fStubsButtons;

    private final SelectionButtonDialogFieldGroup fPerClauseButtons;

    private final SelectionButtonDialogField fPerClauseSelection;


    public NewAspectWizardPage() {
        super(CLASS_TYPE, PAGE_NAME);

        setTitle(UIMessages.NewAspectCreationWizardPage_title);
        setDescription(UIMessages.NewAspectCreationWizardPage_description);

        String[] buttonNames3= new String[] {
                NewWizardMessages.NewClassWizardPage_methods_main,
                UIMessages.NewAspectCreationWizardPage_pointcuts_inherited,
                NewWizardMessages.NewClassWizardPage_methods_inherited
            };
        fStubsButtons= new SelectionButtonDialogFieldGroup(SWT.CHECK, buttonNames3, 1);
        fStubsButtons.setLabelText(UIMessages.NewAspectCreationWizardPage_stubs_label);

        fPerClauseSelection= new SelectionButtonDialogField(SWT.CHECK);
        fPerClauseSelection.setLabelText(UIMessages.NewAspectCreationWizardPage_instantiation_label);

        String[] perClauseButtonNames= new String[] {
                "i&ssingleton", //$NON-NLS-1$
                "p&erthis", //$NON-NLS-1$
                "pertar&get", //$NON-NLS-1$
                "per&cflow", //$NON-NLS-1$
                "pe&rcflowbelow", //$NON-NLS-1$
                "pertypewithi&n" //$NON-NLS-1$
        };
        fPerClauseButtons= new SelectionButtonDialogFieldGroup(SWT.RADIO, perClauseButtonNames, 3);
        fPerClauseSelection.attachDialogField(fPerClauseButtons);
    }

    /**
     * The wizard owning this page is responsible for calling this method with the
     * current selection. The selection is used to initialize the fields of the wizard
     * page.
     *
     * @param selection used to initialize the fields
     */
    public void init(IStructuredSelection selection) {
        IJavaElement jelem= getInitialJavaElement(selection);
        initContainerPage(jelem);
        initTypePage(jelem);
        doStatusUpdate();

        boolean createMain= false;
        boolean createUnimplementedPointcuts = true;
        boolean createUnimplementedMethods = true;
        IDialogSettings section= getDialogSettings().getSection(PAGE_NAME);
        if (section != null) {
            createMain= section.getBoolean(SETTINGS_CREATEMAIN);
            createUnimplementedPointcuts = section.getBoolean(SETTINGS_CREATEUNIMPLEMENTED_PC);
            createUnimplementedMethods = section.getBoolean(SETTINGS_CREATEUNIMPLEMENTED_PC);
        }

        setMethodStubSelection(createMain, createUnimplementedPointcuts, createUnimplementedMethods, true);
    }

    // ------ validation --------
    private void doStatusUpdate() {
        // status of all used components
        IStatus[] status= new IStatus[] {
            fContainerStatus,
            isEnclosingTypeSelected() ? fEnclosingTypeStatus : fPackageStatus,
            fTypeNameStatus,
            fModifierStatus,
            fSuperClassStatus,
            fSuperInterfacesStatus
        };

        // the mode severe status will be displayed and the OK button enabled/disabled.
        updateStatus(status);
    }

    /**
     * Hook method that gets called when the type name has changed. The method validates the
     * type name and returns the status of the validation.
     * <p>
     * We first delegate the validation to the superclass, then perform our own additional
     * validation.
     * </p>
     *
     * @return the status of the validation
     */
    protected IStatus typeNameChanged() {
        StatusInfo status = (StatusInfo) super.typeNameChanged();
        IPackageFragment pack = getPackageFragment();
        if (pack == null) {
            return status;
        }
        IProject project = pack.getJavaProject().getProject();
        if (!AspectJPlugin.isAJProject(project)) {
            status.setError(NLS.bind(
                    UIMessages.NewAspectCreationWizardPage_must_be_AJ_project,
                    project.getName()));
        }
        // must not exist as a .aj file
        if (!isEnclosingTypeSelected()
                && (status.getSeverity() < IStatus.ERROR)) {
            if (pack != null) {
                String typeName = getTypeNameWithoutParameters();
                IContainer folder = (IContainer) pack.getResource();
                if (folder != null) {
                    IResource res = folder.findMember(typeName + ".aj"); //$NON-NLS-1$
                    if (res != null) {
                        status.setError(NewWizardMessages.NewTypeWizardPage_error_TypeNameExists);
                        return status;
                    }
                }
            }
        }
        return status;
    }

    /*
     * @see NewContainerWizardPage#handleFieldChanged
     */
    protected void handleFieldChanged(String fieldName) {
        super.handleFieldChanged(fieldName);

        doStatusUpdate();
    }

    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        Composite composite= new Composite(parent, SWT.NONE);
        composite.setFont(parent.getFont());

        int nColumns= 4;

        GridLayout layout= new GridLayout();
        layout.numColumns= nColumns;
        composite.setLayout(layout);

        // pick & choose the wanted UI components

        createContainerControls(composite, nColumns);
        createPackageControls(composite, nColumns);

        // allowing creation of an inner aspect is of low
        // value and is not currently working, so disable
//      createEnclosingTypeControls(composite, nColumns);

        createSeparator(composite, nColumns);

        createTypeNameControls(composite, nColumns);
        createModifierControls(composite, nColumns);

        createInstantiationControls(composite, nColumns);

        createSuperClassControls(composite, nColumns);
        createSuperInterfacesControls(composite, nColumns);

        createStubSelectionControls(composite, nColumns);

        createCommentControls(composite, nColumns);
        enableCommentControl(true);

        setControl(composite);

        Dialog.applyDialogFont(composite);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.NEW_CLASS_WIZARD_PAGE);
    }

    /*
     * @see WizardPage#becomesVisible
     */
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            setFocus();
        }
    }

    /**
     * Returns the current selection state of the 'Create Main' checkbox.
     *
     * @return the selection state of the 'Create Main' checkbox
     */
    public boolean isCreateMain() {
        return fStubsButtons.isSelected(0);
    }

    /**
     * Returns the current selection state of the 'Create inherited abstract pointcuts'
     * checkbox.
     *
     * @return the selection state of the 'Create inherited abstract pointcuts' checkbox
     */
    public boolean isCreateInheritedPointcuts() {
        return fStubsButtons.isSelected(1);
    }

    /**
     * Returns the current selection state of the 'Create inherited abstract methods'
     * checkbox.
     *
     * @return the selection state of the 'Create inherited abstract methods' checkbox
     */
    public boolean isCreateInheritedMethods() {
        return fStubsButtons.isSelected(2);
    }

    /**
     * Sets the selection state of the method stub checkboxes.
     *
     * @param createMain initial selection state of the 'Create Main' checkbox.
     * @param createInherited initial selection state of the 'Create inherited abstract pointcuts' checkbox.
     * @param canBeModified if <code>true</code> the method stub checkboxes can be changed by
     * the user. If <code>false</code> the buttons are "read-only"
     */
    public void setMethodStubSelection(boolean createMain, boolean createInheritedPointcuts, boolean createInheritedMethods, boolean canBeModified) {
        fStubsButtons.setSelection(0, createMain);
        fStubsButtons.setSelection(1, createInheritedPointcuts);
        fStubsButtons.setSelection(2, createInheritedMethods);

        fStubsButtons.setEnabled(canBeModified);
    }

    private String getTypeNameWithoutParameters() {
        String typeNameWithParameters= getTypeName();
        int angleBracketOffset= typeNameWithParameters.indexOf('<');
        if (angleBracketOffset == -1) {
            return typeNameWithParameters;
        } else {
            return typeNameWithParameters.substring(0, angleBracketOffset);
        }
    }

    private void createInstantiationControls(Composite composite, int nColumns) {
        fPerClauseSelection.doFillIntoGrid(composite, 1);

        Control buttonGroup= fPerClauseButtons.getSelectionButtonsGroup(composite);
        GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_FILL);
        gd.horizontalSpan= nColumns - 2;
        buttonGroup.setLayoutData(gd);

        DialogField.createEmptySpace(composite);
    }

    private void createStubSelectionControls(Composite composite, int nColumns) {
        Control labelControl = fStubsButtons.getLabelControl(composite);
        LayoutUtil.setHorizontalSpan(labelControl, nColumns);

        DialogField.createEmptySpace(composite);

        Control buttonGroup = fStubsButtons.getSelectionButtonsGroup(composite);
        LayoutUtil.setHorizontalSpan(buttonGroup, nColumns - 1);
    }

    private void createInheritedPointcuts(IType type, ImportsManager imports,
            IProgressMonitor monitor) {
        String supertype = getSuperClass();
        if (supertype.length() == 0) {
            return;
        }
        try {
            IType stype = type.getJavaProject().findType(supertype,
                    AJCompilationUnitManager.defaultAJWorkingCopyOwner());
            if (stype == null) {
                // might be an aspect in same package
                String packageName = type.getPackageFragment().getElementName();
                supertype = packageName + "." + supertype;
                stype = type.getJavaProject().findType(supertype,
                        AJCompilationUnitManager.defaultAJWorkingCopyOwner());
                if (stype == null) {
                    return;
                }
            }
            String simpleName = stype.getElementName();
            ICompilationUnit cu = stype.getCompilationUnit();
            cu = AJCompilationUnitManager.mapToAJCompilationUnit(cu);
            if (cu instanceof AJCompilationUnit) {
                AJCompilationUnit ajcu = (AJCompilationUnit) cu;
                IType[] stypes = ajcu.getTypes();
              for (IType iType : stypes) {
                if (iType.getElementName().equals(simpleName)) {
                  if (iType instanceof AspectElement) {
                    AspectElement aspect = (AspectElement) iType;
                    PointcutElement[] pointcuts = aspect.getPointcuts();
                    for (PointcutElement pointcut : pointcuts) {
                      if (Flags.isAbstract(pointcut.getFlags())) {
                        String str = "pointcut " //$NON-NLS-1$
                                     + pointcut.getElementName()
                                     + "();"; //$NON-NLS-1$
                        if (Flags.isPublic(pointcut.getFlags())) {
                          str = "public " + str; //$NON-NLS-1$
                        }
                        else if (Flags.isProtected(pointcut
                          .getFlags()))
                        {
                          str = "protected " + str; //$NON-NLS-1$
                        }
                        type.createMethod(str, null, false, null);
                      }
                    }
                  }
                }
              }
            }
        } catch (JavaModelException ignored) {
        }
    }

    /*
     * @see NewTypeWizardPage#createTypeMembers
     */
    protected void createTypeMembers(IType type, ImportsManager imports, IProgressMonitor monitor) throws CoreException {
        boolean doInheritedPointcuts = isCreateInheritedPointcuts();
        if (doInheritedPointcuts) {
            createInheritedPointcuts(type, imports, new SubProgressMonitor(monitor, 1));
        }
        boolean doInheritedMethods = isCreateInheritedMethods();
        if (doInheritedMethods) {
            super.createInheritedMethods(type, false,
                    doInheritedMethods, imports, new SubProgressMonitor(monitor, 1));
        }
        boolean doMain = isCreateMain();
        if (doMain) {
            StringBuilder buf= new StringBuilder();
            final String lineDelim= "\n"; // OK, since content is formatted afterwards //$NON-NLS-1$
            String comment= CodeGeneration.getMethodComment(type.getCompilationUnit(), type.getTypeQualifiedName('.'), "main", new String[] {"args"}, new String[0], Signature.createTypeSignature("void", true), null, lineDelim); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            if (comment != null) {
                buf.append(comment);
                buf.append(lineDelim);
            }
            buf.append("public static void main("); //$NON-NLS-1$
            buf.append(imports.addImport("java.lang.String")); //$NON-NLS-1$
            buf.append("[] args) {"); //$NON-NLS-1$
            buf.append(lineDelim);
            final String content= CodeGeneration.getMethodBodyContent(type.getCompilationUnit(), type.getTypeQualifiedName('.'), "main", false, "", lineDelim); //$NON-NLS-1$ //$NON-NLS-2$
            if (content != null && content.length() != 0)
                buf.append(content);
            buf.append(lineDelim);
            buf.append("}"); //$NON-NLS-1$
            type.createMethod(buf.toString(), null, false, null);
        }

        IDialogSettings section= getDialogSettings().getSection(PAGE_NAME);
        if (section == null) {
            section= getDialogSettings().addNewSection(PAGE_NAME);
        }
        section.put(SETTINGS_CREATEMAIN, doMain);
        section.put(SETTINGS_CREATEUNIMPLEMENTED_PC, doInheritedPointcuts);
        section.put(SETTINGS_CREATEUNIMPLEMENTED_METH, doInheritedMethods);

        if (monitor != null) {
            monitor.done();
        }
    }

    protected void writePerClause(StringBuffer buf) {
        String s = ""; //$NON-NLS-1$
        if (fPerClauseSelection.isSelected()) {
            if (fPerClauseButtons.isSelected(ISSINGLETON_INDEX)) {
                s = " issingleton()"; //$NON-NLS-1$
            } else if (fPerClauseButtons.isSelected(PERTHIS_INDEX)) {
                s = " perthis(pointcut_name())"; //$NON-NLS-1$
            } else if (fPerClauseButtons.isSelected(PERTARGET_INDEX)) {
                s = " pertarget(pointcut_name())"; //$NON-NLS-1$
            } else if (fPerClauseButtons.isSelected(PERCFLOW_INDEX)) {
                s = " percflow(pointcut_name())"; //$NON-NLS-1$
            } else if (fPerClauseButtons.isSelected(PERCFLOWBELOW_INDEX)) {
                s = " percflowbelow(pointcut_name())"; //$NON-NLS-1$
            } else if (fPerClauseButtons.isSelected(PERTYPEWITHIN_INDEX)) {
                s = " pertypewithin(type_pattern)"; //$NON-NLS-1$
            }
        }
        buf.append(s);
    }

}
