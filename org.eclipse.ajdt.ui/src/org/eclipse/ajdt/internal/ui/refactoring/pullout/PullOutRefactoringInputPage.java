/*******************************************************************************
 * Copyright (c) 2010 SpringSource and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Kris De Volder - initial API and implementation
 *******************************************************************************/
package org.eclipse.ajdt.internal.ui.refactoring.pullout;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.internal.ui.refactoring.contentassist.ControlContentAssistHelper;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.util.TableLayoutComposite;
import org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider;
import org.eclipse.jdt.ui.IJavaElementSearchConstants;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.window.Window;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.dialogs.SelectionStatusDialog;

public class PullOutRefactoringInputPage extends UserInputWizardPage {

	private Text targetAspectInput;

	private TableViewerColumn typeColumn;
	private TableViewerColumn memberColumn;

	private final JavaUILabelProvider labelProvider = new JavaUILabelProvider();
	private TableViewerColumn packageColumn;

	public PullOutRefactoringInputPage(String name) {
		super(name);
	}

	private Text createAspectInput(Composite composite) {
		Text textBox= new Text(composite, SWT.SINGLE+SWT.BORDER);
		GridDataFactory.fillDefaults().align(SWT.FILL, SWT.FILL).grab(true, false).applyTo(textBox);

		return textBox;
	}

	public void createControl(Composite parent) {
		final PullOutRefactoring refactoring = getRefactoring();
		parent = new Composite(parent, SWT.FILL);
		GridLayout layout= new GridLayout(1,true);
		parent.setLayout(layout);
		setControl(parent);

		//Element to be pulled out

		Label label = new Label(parent, SWT.NONE);
		label.setText("The following element will be pulled out:");
		createTable(parent);

		createAspectGroup(parent);
		createITDGroup(parent);

		handleInputChanged();
	}

	private void createAspectGroup(Composite parent) {
		final PullOutRefactoring refactoring = getRefactoring();

		Group group = new Group(parent, SWT.DEFAULT);
		group.setText("Target Aspect");
		group.setLayout(new GridLayout());
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		createTargetAspectLine(group);

		final Button makePrivilegeCheckBox = new Button(group, SWT.CHECK);
		makePrivilegeCheckBox.setSelection(refactoring.isMakePrivileged());
		makePrivilegeCheckBox.setText("&Make the Aspect Privileged");
		makePrivilegeCheckBox.setToolTipText(
				"The aspect will be made privileged\n" +
				"ITDs in privileged aspects can access private and protected members in the" +
				"woven context without errors.");
		makePrivilegeCheckBox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refactoring.setMakePrivileged(makePrivilegeCheckBox.getSelection());
			}
		});
	}


	private void createITDGroup(Composite _parent) {
		final PullOutRefactoring refactoring = getRefactoring();

		Group group = new Group(_parent, SWT.DEFAULT);
		group.setText("Intertype Declaration Options");
		group.setLayout(new GridLayout(2, true));
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);

		final Button allowDropProtected = new Button(group, SWT.CHECK);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(allowDropProtected);
		allowDropProtected.setSelection(refactoring.isAllowDeleteProtected());
		allowDropProtected.setText("&Remove 'protected' keyword from ITDs ");
		allowDropProtected.setToolTipText(
				"The AspectJ language does not allow the 'protected' keyword on ITDs.\n" +
				"Check this option to automatically remove it.");
		allowDropProtected.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refactoring.setAllowDeleteProtected(allowDropProtected.getSelection());
			}
		});

		final Button allowMakePublicCheckbox = new Button(group, SWT.CHECK);
		allowMakePublicCheckbox.setSelection(refactoring.isAllowMakePublic());
		allowMakePublicCheckbox.setText("&Make ITDs public as needed");
		allowMakePublicCheckbox.setToolTipText(
				"If an ITD is private, it will be private to the aspect.\n" +
				"Pulled private members will no longer be accessible from the woven class." +
				"Check this option to allow the refactoring to fix broken references to" +
				"ITDs by making them public.");
		allowMakePublicCheckbox.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refactoring.setAllowMakePublic(allowMakePublicCheckbox.getSelection());
			}
		});

		final Button generateAbstractMethodStubs = new Button(group, SWT.CHECK);
		generateAbstractMethodStubs.setSelection(refactoring.isGenerateAbstractMethodStubs());
		generateAbstractMethodStubs.setText("&Generate stubs for abstract methods");
		generateAbstractMethodStubs.setToolTipText("Abstract ITDs are not supported by AspectJ.\n" +
				"Enable this option to remove the abstract keyword and add stub method bodies for " +
				"pulled out abstract methods.");
		generateAbstractMethodStubs.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refactoring.setGenerateAbstractMethodStubs(generateAbstractMethodStubs.getSelection());
			}
		});
	}

	private void createTargetAspectLine(Composite parent) {
		PullOutRefactoring refactoring = getRefactoring();

		parent = new Composite(parent, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(parent);
		GridLayout layout = new GridLayout(3,false);
		parent.setLayout(layout);

		Label label;
		label = new Label(parent, SWT.NONE);
		label.setText("&Target Aspect");

		targetAspectInput = createAspectInput(parent);
		final Button browseButton= new Button(parent, SWT.PUSH);
		browseButton.setText("&Browse...");
		targetAspectInput.setText(refactoring.getAspectName());
		targetAspectInput.addModifyListener(event -> handleInputChanged());

		browseButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent event) {
				IType type= selectAspect();
				if (type == null)
					return;

				targetAspectInput.setText(type.getFullyQualifiedName());
			}
		});

		try {
			AspectInputContentAssistProcessor processor = new AspectInputContentAssistProcessor(getRefactoring().getJavaProject());
			ControlContentAssistHelper.createTextContentAssistant(targetAspectInput,
					processor);
		} catch (JavaModelException ignored) {
		}
	}

	void handleInputChanged() {
		RefactoringStatus status= new RefactoringStatus();
		PullOutRefactoring refactoring= getRefactoring();
		status.merge(refactoring.setAspect(targetAspectInput.getText()));

		setPageComplete(!status.hasError());
		int severity= status.getSeverity();
		String message= status.getMessageMatchingSeverity(severity);
		if (severity >= RefactoringStatus.INFO) {
			setMessage(message, severity);
		} else {
			setMessage("", NONE); //$NON-NLS-1$
		}
	}

	/*
	 * from @link ExtractClassUserInputWizardPage
	 */
    private TableLayoutComposite createTable(Composite parent) {
        GridData gridData;
        initializeDialogUnits(parent);
        TableLayoutComposite layoutComposite= new TableLayoutComposite(parent, SWT.FILL);
        layoutComposite.addColumnData(new ColumnWeightData(20, convertWidthInCharsToPixels(20), true));
        layoutComposite.addColumnData(new ColumnWeightData(20, convertWidthInCharsToPixels(20), true));
        layoutComposite.addColumnData(new ColumnWeightData(40, convertWidthInCharsToPixels(40), true));

        // use this instead if we want to implement check boxes
//        final CheckboxTableViewer tv = CheckboxTableViewer.newCheckList(layoutComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        final TableViewer tv = new TableViewer(layoutComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        tv.setContentProvider(new FieldContentProvider());
        createColumns(tv);

        Table table= tv.getTable();
        table.setLinesVisible(true);
        table.setHeaderVisible(true);
        gridData= new GridData(SWT.FILL, SWT.FILL, true, true);
        table.setLayoutData(gridData);
        tv.setInput(getRefactoring());
        final Object[] fields= getRefactoring().getMembers();
        // only if using checkbox viewer
//        for (int i= 0; i < fields.length; i++) {
//            IAspectJElement field= (IAspectJElement) fields[i];
//            tv.setChecked(field, true);
//        }
        tv.refresh(true);
        gridData= new GridData(GridData.FILL_BOTH);
        gridData.widthHint= convertWidthInCharsToPixels(100);
        gridData.heightHint= SWTUtil.getTableHeightHint(table, Math.max(fields.length+1,2));
        layoutComposite.setLayoutData(gridData);

//        sortListener = new SortListener(tv);
//        iconColumn.getColumn().addListener(SWT.Selection, sortListener);
//        aspectColumn.getColumn().addListener(SWT.Selection, sortListener);
//        targetColumn.getColumn().addListener(SWT.Selection, sortListener);
//        itdColumn.getColumn().addListener(SWT.Selection, sortListener);

        return layoutComposite;
    }

    private void createColumns(final TableViewer tv) {

        packageColumn = new TableViewerColumn(tv, SWT.LEAD);
        packageColumn.setLabelProvider(new JavaCellLabelProvider(labelProvider) {
			@Override
			public Object getColumnData(Object element) {
				if (element instanceof IMember) {
					IMember method = (IMember) element;
					return method.getDeclaringType().getPackageFragment();
				}
				else
					return "???";
			}
        });
        TableColumn column= packageColumn.getColumn();
        column.setText("Package");

        typeColumn = new TableViewerColumn(tv, SWT.LEAD);
        typeColumn.setLabelProvider(new JavaCellLabelProvider(labelProvider) {
			@Override
			public Object getColumnData(Object element) {
				if (element instanceof IMember) {
					IMember method = (IMember) element;
					return method.getDeclaringType();
				}
				else
					return "???";
			}
        });
        column= typeColumn.getColumn();
        column.setText("Type");

        memberColumn = new TableViewerColumn(tv, SWT.LEAD);
        memberColumn.setLabelProvider(new JavaCellLabelProvider(labelProvider) {
			public Object getColumnData(Object elt) {
				return elt;
			}
        });
        column = memberColumn.getColumn();
        column.setText("Member Name");

    }

    /*
     * from @link ExtractClassUserInputWizardPage
     */
    public static class FieldContentProvider implements IStructuredContentProvider {

        public void dispose() {
        }

        public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
        }

        public Object[] getElements(Object inputElement) {
            if (inputElement instanceof PullOutRefactoring) {
            	PullOutRefactoring refactoring= (PullOutRefactoring) inputElement;
                return refactoring.getMembers();
            }
            return null;
        }

    }


	IType selectAspect() {
		IJavaProject project= getRefactoring().getJavaProject();

		IJavaElement[] elements= new IJavaElement[] { project};
		IJavaSearchScope scope= SearchEngine.createJavaSearchScope(elements);

		try {
			SelectionStatusDialog dialog= (SelectionStatusDialog)
				JavaUI.createTypeDialog(getShell(), getContainer(),
						scope, IJavaElementSearchConstants.CONSIDER_CLASSES, false, "",
						new AspectSelectionFilter(project));

			dialog.setTitle("Choose target Aspect");
			dialog.setMessage("Choose the Aspect where to create the intertype declaration(s)");

			if (dialog.open() == Window.OK)
				return (IType) dialog.getFirstResult();

		} catch (org.eclipse.jdt.core.JavaModelException exception) {
			//Count on aspect to catch/log this.
		}
		return null;
	}

	@Override
	public PullOutRefactoring getRefactoring() {
		return (PullOutRefactoring) super.getRefactoring();
	}

}
