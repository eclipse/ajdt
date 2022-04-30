package org.eclipse.ajdt.internal.ui.refactoring;

import java.util.Iterator;
import java.util.List;

import org.aspectj.asm.IProgramElement;
import org.aspectj.asm.IProgramElement.Accessibility;
import org.aspectj.asm.IProgramElement.Kind;
import org.eclipse.ajdt.core.javaelements.DeclareElement;
import org.eclipse.ajdt.core.javaelements.IAspectJElement;
import org.eclipse.ajdt.core.javaelements.IntertypeElement;
import org.eclipse.ajdt.core.model.AJProjectModelFacade;
import org.eclipse.ajdt.core.model.AJProjectModelFactory;
import org.eclipse.ajdt.core.model.AJRelationshipManager;
import org.eclipse.ajdt.internal.ui.resources.AJDTIcon;
import org.eclipse.ajdt.internal.ui.resources.AspectJImages;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.util.SWTUtil;
import org.eclipse.jdt.internal.ui.util.TableLayoutComposite;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.UserInputWizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

public class PushInRefactoringInputPage extends UserInputWizardPage {
    class SortListener implements Listener {

        private final TableViewer tableViewer;
        private final Table table;

        public SortListener(TableViewer tableViewer) {
            this.tableViewer = tableViewer;
            this.table = tableViewer.getTable();
        }

        public void handleEvent(Event e) {
            // determine new sort column and direction

            TableColumn sortColumn = table.getSortColumn();
            TableColumn currentColumn = (TableColumn) e.widget;
            int dir = table.getSortDirection();

            if (sortColumn == currentColumn) {
                dir = dir == SWT.UP ? SWT.DOWN : SWT.UP;
            } else {
                table.setSortColumn(currentColumn);
                dir = SWT.UP;
            }

            // sort the data based on column and direction
            String sortIdentifier = null;
            if (currentColumn == iconColumn.getColumn()) {
                sortIdentifier = Sorter.ICON_SORT;
            }
            if (currentColumn == aspectColumn.getColumn()) {
                sortIdentifier = Sorter.ASPECT_SORT;
            }
            if (currentColumn == targetColumn.getColumn()) {
                sortIdentifier = Sorter.ITD_SORT;
            }
            if (currentColumn == itdColumn.getColumn()) {
                sortIdentifier = Sorter.TARGET_SORT;
            }

            tableViewer.setSorter(new Sorter(sortIdentifier, dir));
            table.setSortDirection(dir);
        }
    }

    static class Sorter extends ViewerSorter {
        final static String ICON_SORT = "icon.sort";
        final static String ASPECT_SORT = "aspect.sort";
        final static String ITD_SORT = "itd.sort";
        final static String TARGET_SORT = "target.sort";

        private final int dir;
        private final String column;

        Sorter(String column, int dir) {
            super();
            this.column = column;
            this.dir = dir;
        }

        public int compare(Viewer viewer, Object e1, Object e2) {
            if (column.equals(ASPECT_SORT)) {
                if (e1 instanceof IJavaElement) {
                    if (! (e1 instanceof IType)) {
                        e1 = ((IJavaElement) e1).getParent();
                    }
                    e1 = ((IJavaElement) e1).getElementName();
                }
                if (e2 instanceof IJavaElement) {
                    if (e2 instanceof IType) {
                        e2 = ((IJavaElement) e2).getParent();
                    }
                    e2 = ((IJavaElement) e2).getElementName();
                }
            } else if (column.equals(ITD_SORT) || column.equals(TARGET_SORT)) {
                // This isn't quite right because what shows up in
                // the column isn't what gets sorted on.
                if (e1 instanceof IJavaElement) {
                    e1 = ((IJavaElement) e1).getElementName();
                }
                if (e2 instanceof IJavaElement) {
                    e2 = ((IJavaElement) e2).getElementName();
                }
            }



            if (dir == SWT.DOWN) {
                return e1.toString().compareTo(e2.toString());
            } else {
                return e2.toString().compareTo(e1.toString());
            }
        }
    }

    private Listener sortListener;

    TableViewerColumn iconColumn;
    TableViewerColumn aspectColumn;
    TableViewerColumn targetColumn;
    TableViewerColumn itdColumn;

    private final PushInRefactoringDescriptor descriptor;

    public PushInRefactoringInputPage(String name, PushInRefactoringDescriptor descriptor) {
        super(name);
        Assert.isNotNull(descriptor.getITDs(), "Cannot perform refactoring with no ITDs Selected");
        this.descriptor = descriptor;
    }

    public void createControl(Composite parent) {
        Composite result= new Composite(parent, SWT.NONE);

        setControl(result);

        GridLayout layout= new GridLayout(2, false);
        result.setLayout(layout);

        Label label = new Label(result, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
        label.setText("The following intertype declarations will be pushed into their target types:");
        GridData gridData= new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan= 2;
        gridData.verticalIndent= 5;
        label.setLayoutData(gridData);

        createTable(result);

        label = new Label(result, SWT.SINGLE | SWT.LEFT | SWT.BORDER);
        label.setText("To change the set of intertype declarations to be pushed in, click cancel and reselect only the desired AspectJ elements.");
        label.setLayoutData(gridData);

        handleInputChanged();
    }



    private PushInRefactoring getPushInRefactoring() {
        return (PushInRefactoring) getRefactoring();
    }

    private void handleInputChanged() {
        RefactoringStatus status= new RefactoringStatus();
        PushInRefactoring refactoring= getPushInRefactoring();
        try {
            status.merge(refactoring.checkInitialConditions(new NullProgressMonitor()));
        } catch (OperationCanceledException ignored) {
        } catch (CoreException e) {
            status.merge(RefactoringStatus.createFatalErrorStatus(e.getMessage()));
        }
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
    private void createTable(Composite parent) {
        GridData gridData;

        TableLayoutComposite layoutComposite= new TableLayoutComposite(parent, SWT.NONE);
        layoutComposite.addColumnData(new ColumnWeightData(5, convertWidthInCharsToPixels(3), true));
        layoutComposite.addColumnData(new ColumnWeightData(40, convertWidthInCharsToPixels(20), true));
        layoutComposite.addColumnData(new ColumnWeightData(40, convertWidthInCharsToPixels(20), true));
        layoutComposite.addColumnData(new ColumnWeightData(40, convertWidthInCharsToPixels(20), true));

        // use this instead if we want to implement check boxes
//        final CheckboxTableViewer tv = CheckboxTableViewer.newCheckList(layoutComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        final TableViewer tv = new TableViewer(layoutComposite, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI);
        tv.setContentProvider(new FieldContentProvider());
        createColumns(tv);

        Table table= tv.getTable();
        table.setLinesVisible(true);
        table.setHeaderVisible(true);
        gridData= new GridData(GridData.FILL_BOTH);
        table.setLayoutData(gridData);
        tv.setInput(descriptor);
        final Object[] fields= descriptor.getITDs();
        // only if using checkbox viewer
//        for (int i= 0; i < fields.length; i++) {
//            IAspectJElement field= (IAspectJElement) fields[i];
//            tv.setChecked(field, true);
//        }
        tv.refresh(true);
        gridData= new GridData(GridData.FILL_BOTH);
        gridData.heightHint= SWTUtil.getTableHeightHint(table, Math.max(fields.length,5));
        layoutComposite.setLayoutData(gridData);

        sortListener = new SortListener(tv);
        iconColumn.getColumn().addListener(SWT.Selection, sortListener);
        aspectColumn.getColumn().addListener(SWT.Selection, sortListener);
        targetColumn.getColumn().addListener(SWT.Selection, sortListener);
        itdColumn.getColumn().addListener(SWT.Selection, sortListener);
    }

    private void createColumns(final TableViewer tv) {

        // four columns: icon, Aspect name, ITD name, target type name(s)


        iconColumn = new TableViewerColumn(tv, SWT.LEAD);
        iconColumn.setLabelProvider(new CellLabelProvider() {
            public void update(ViewerCell cell) {
                Object elt = cell.getElement();
                if (elt instanceof IntertypeElement || elt instanceof DeclareElement) {
                    IAspectJElement ajElem = (IAspectJElement) elt;
                    AJDTIcon icon;
                    try {
                        icon = AspectJImages.instance()
                                .getStructureIcon(ajElem.getAJKind(), ajElem.getAJAccessibility());
                        cell.setImage(icon.getImageDescriptor().createImage());
                    } catch (JavaModelException ignored) {
                    }
                } else if (elt instanceof IType) {
                    AJDTIcon icon = AspectJImages.instance()
                            .getStructureIcon(Kind.CLASS, Accessibility.PUBLIC);
                    cell.setImage(icon.getImageDescriptor().createImage());
                }
            }
        });


        aspectColumn = new TableViewerColumn(tv, SWT.LEAD);
        aspectColumn.setLabelProvider(new CellLabelProvider() {
            public void update(ViewerCell cell) {
                Object elt = cell.getElement();
                if (elt instanceof IntertypeElement || elt instanceof DeclareElement || elt instanceof IType) {
                    IMember itd = (IMember) elt;
                    cell.setText(itd.getParent().getElementName());
                }
            }
        });
        TableColumn column= aspectColumn.getColumn();
        column.setText("Declaring aspect");

        itdColumn = new TableViewerColumn(tv, SWT.LEAD);
        itdColumn.setLabelProvider(new CellLabelProvider() {
            public void update(ViewerCell cell) {
                Object elt = cell.getElement();
                if (elt instanceof IntertypeElement || elt instanceof IType) {
                    IMember itd = (IMember) elt;
                    cell.setText(itd.getElementName());
                } else if (elt instanceof DeclareElement) {
                    DeclareElement de = (DeclareElement) elt;
                    IProgramElement ipe = AJProjectModelFactory.getInstance().getModelForJavaElement(de).javaElementToProgramElement(de);
                    String details = ipe.getDetails();
                    details = ipe.getDetails();
                    if (ipe.getKind().isDeclareAnnotation()) {
                        // don't want fully qualified names in window
                        String annotationName = details;
                        String[] split = details.split(":");
                        if (split.length == 2) {
                            int secondPart = Math.max(split[1].lastIndexOf('.')+1, 1);
                            split[1] = split[1].substring(secondPart).trim();
                            annotationName = split[1];
                        }
                        cell.setText(de.getElementName() + " " + annotationName);
                    } else {
                        // declare parents
                        List<String> parents = ipe.getParentTypes();
                        StringBuilder sb = new StringBuilder();
                        for (Iterator<String> parentIter = parents.iterator(); parentIter
                                .hasNext();) {
                            String parent = parentIter.next();
                            String[] splits = parent.split("\\.");
                            parent = splits[splits.length-1];
                            sb.append(parent);
                            if (parentIter.hasNext()) {
                                sb.append(", ");
                            }
                        }
                        cell.setText(sb.toString());
                    }
                }
            }
        });
        column = itdColumn.getColumn();
        column.setText("Intertype Name");

        targetColumn = new TableViewerColumn(tv, SWT.LEAD);
        targetColumn.setLabelProvider(new CellLabelProvider() {
            public void update(ViewerCell cell) {
                Object elt = cell.getElement();
                if (elt instanceof IntertypeElement) {
                    IntertypeElement itd = (IntertypeElement) elt;
                    try {
                        cell.setText(new String(itd.getTargetType()));
                    } catch (JavaModelException ignored) {
                    }
                } else if (elt instanceof DeclareElement) {
                    DeclareElement de = (DeclareElement) elt;
                    AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(de);
                    IProgramElement ipe = model.javaElementToProgramElement(de);
                    String targetName;
                    if (ipe.getKind().isDeclareAnnotation()) {
                        String details = ipe.getDetails();
                        targetName = details;
                        String[] split = details.split(":");
                        if (split.length == 2) {
                            int firstPart = Math.max(split[0].lastIndexOf('.')+1, 0);
                            split[0] = split[0].substring(firstPart).trim();
                            targetName = split[0];
                        }
                    } else {
                        List<IJavaElement> elts = model.getRelationshipsForElement(de, AJRelationshipManager.DECLARED_ON);
                        StringBuilder sb = new StringBuilder();
                        for (Iterator<IJavaElement> eltIter = elts.iterator(); eltIter.hasNext(); ) {
                            IJavaElement target = eltIter.next();
                            sb.append(target.getElementName());
                            if (eltIter.hasNext()) {
                                sb.append(", ");
                            }
                        }
                        targetName = sb.toString();
                    }
                    cell.setText(targetName);
                } else if (elt instanceof IType) {
                    IType type = (IType) elt;
                    AJProjectModelFacade model = AJProjectModelFactory.getInstance().getModelForJavaElement(type);
                    List<IJavaElement> elts = model.getRelationshipsForElement(type, AJRelationshipManager.DECLARED_ON);
                    StringBuilder sb = new StringBuilder();
                    for (Iterator<IJavaElement> eltIter = elts.iterator(); eltIter.hasNext(); ) {
                        IJavaElement target = eltIter.next();
                        sb.append(target.getElementName());
                        if (eltIter.hasNext()) {
                            sb.append(", ");
                        }
                    }
                    String targetName = sb.toString();
                    cell.setText(targetName);
                }
            }
        });
        column = targetColumn.getColumn();
        column.setText("Target type");
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
            if (inputElement instanceof PushInRefactoringDescriptor) {
                PushInRefactoringDescriptor descriptor= (PushInRefactoringDescriptor) inputElement;
                return descriptor.getITDs();
            }
            return new IAspectJElement[0];
        }

    }

    public void dispose() {
        super.dispose();
        iconColumn.getColumn().removeListener(SWT.Selection, sortListener);
        aspectColumn.getColumn().removeListener(SWT.Selection, sortListener);
        targetColumn.getColumn().removeListener(SWT.Selection, sortListener);
        itdColumn.getColumn().removeListener(SWT.Selection, sortListener);
    }
}
