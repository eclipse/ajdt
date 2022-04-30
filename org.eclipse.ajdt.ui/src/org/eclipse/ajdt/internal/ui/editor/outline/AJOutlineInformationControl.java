/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ajdt.internal.ui.editor.outline;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ajdt.core.javaelements.AJCompilationUnitManager;
import org.eclipse.ajdt.ui.AspectJUIPlugin;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.MethodOverrideTester;
import org.eclipse.jdt.internal.corext.util.SuperTypeHierarchyCache;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.JavaUIMessages;
import org.eclipse.jdt.internal.ui.text.AbstractInformationControl;
import org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter;
import org.eclipse.jdt.internal.ui.viewsupport.AppearanceAwareLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.DecoratingJavaLabelProvider;
import org.eclipse.jdt.internal.ui.viewsupport.MemberFilter;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.OverrideIndicatorLabelDecorator;
import org.eclipse.jdt.ui.ProblemsLabelDecorator;
import org.eclipse.jdt.ui.StandardJavaElementContentProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.bindings.TriggerSequence;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.bindings.keys.SWTKeySupport;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.Widget;
import org.eclipse.ui.IDecoratorManager;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

// Copied from org.eclipse.jdt.internal.ui.text.JavaOutlineInformationControl
// to allow use of a decorating label provider which will make use of our registered
// decorator - so that we get the right icons in the in-place outline view.
// not needed if/when eclipse bug 79489 is fixed
// Note also that changes have been made to enable us to test this
// programatically
// Changes marked with // AspectJ Change
/**
 * Show outline in light-weight control.
 *
 * @since 2.1
 */
public class AJOutlineInformationControl extends AbstractInformationControl {

	private KeyAdapter fKeyAdapter;
	private OutlineContentProvider fOutlineContentProvider;
	private IJavaElement fInput= null;

	private OutlineSorter fOutlineSorter;

	private OutlineLabelProvider fInnerLabelProvider;
	protected Color fForegroundColor;

	private boolean fShowOnlyMainType;
	private LexicalSortingAction fLexicalSortingAction;
	private SortByDefiningTypeAction fSortByDefiningTypeAction;
	private ShowOnlyMainTypeAction fShowOnlyMainTypeAction;
	private final Map<IType, ITypeHierarchy> fTypeHierarchies= new HashMap<>();

	static class TextMessages {
		public static String getString(String key) {
			return "TextMessage"; //$NON-NLS-1$
		}
	}

	private class OutlineLabelProvider extends AppearanceAwareLabelProvider {

		private boolean fShowDefiningType;

		private OutlineLabelProvider() {
			super(
				AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS | JavaElementLabels.F_APP_TYPE_SIGNATURE,
				AppearanceAwareLabelProvider.DEFAULT_IMAGEFLAGS
			);
		}

		/*
		 * @see ILabelProvider#getText
		 */
		@Override
		public String getText(Object element) {
			String text = super.getText(element);
			if (fShowDefiningType) {
				try {
					IType type = getDefiningType(element);
					if (type != null)
						return super.getText(type) + JavaElementLabels.CONCAT_STRING + text;
				}
				catch (JavaModelException ignored) { }
			}
			return text;
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.viewsupport.JavaUILabelProvider#getForeground(java.lang.Object)
		 */
		@Override
		public Color getForeground(Object element) {
			if (fOutlineContentProvider.isShowingInheritedMembers()) {
				if (element instanceof IJavaElement) {
					IJavaElement je= (IJavaElement)element;
					if (fInput.getElementType() == IJavaElement.CLASS_FILE)
						je= je.getAncestor(IJavaElement.CLASS_FILE);
					else
						je= je.getAncestor(IJavaElement.COMPILATION_UNIT);
					if (fInput.equals(je)) {
						return null;
					}
				}
				return fForegroundColor;
			}
			return null;
		}

		public void setShowDefiningType(boolean showDefiningType) {
			fShowDefiningType= showDefiningType;
		}

		public boolean isShowDefiningType() {
			return fShowDefiningType;
		}

		private IType getDefiningType(Object element) throws JavaModelException {
			int kind= ((IJavaElement) element).getElementType();

			if (kind != IJavaElement.METHOD && kind != IJavaElement.FIELD && kind != IJavaElement.INITIALIZER) {
				return null;
			}
			IType declaringType= ((IMember) element).getDeclaringType();
			if (kind != IJavaElement.METHOD) {
				return declaringType;
			}
			ITypeHierarchy hierarchy= getSuperTypeHierarchy(declaringType);
			if (hierarchy == null) {
				return declaringType;
			}
			IMethod method= (IMethod) element;
			MethodOverrideTester tester= new MethodOverrideTester(declaringType, hierarchy);
			IMethod res= tester.findDeclaringMethod(method, true);
			if (res == null || method.equals(res)) {
				return declaringType;
			}
			return res.getDeclaringType();
		}
	}

	// copied from JavaModelUtil in 3.2M2
	/**
	 * Finds a method declaration in a type's hierarchy. The search is top down, so this
	 * returns the first declaration of the method in the hierarchy.
	 * This searches for a method with a name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param type Searches in this type's supertypes.
	 * @param name The name of the method to find
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor If the method is a constructor
	 * @return The first method found or null, if nothing found
	 */
	private static IMethod findMethodDeclarationInHierarchy(ITypeHierarchy hierarchy, IType type, String name, String[] paramTypes, boolean isConstructor) throws JavaModelException {
		IType[] superTypes= hierarchy.getAllSupertypes(type);
		for (int i= superTypes.length - 1; i >= 0; i--) {
			IMethod first= JavaModelUtil.findMethod(name, paramTypes, isConstructor, superTypes[i]);
			if (first != null && !Flags.isPrivate(first.getFlags())) {
				// the order getAllSupertypes does make assumptions of the order of inner elements -> search recursively
				IMethod res= findMethodDeclarationInHierarchy(hierarchy, first.getDeclaringType(), name, paramTypes, isConstructor);
				if (res != null) {
					return res;
				}
				return first;
			}
		}
		return null;
	}

	private static class OutlineTreeViewer extends TreeViewer {

		private boolean fIsFiltering= false;

		private OutlineTreeViewer(Tree tree) {
			super(tree);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected Object[] getFilteredChildren(Object parent) {
			Object[] result = getRawChildren(parent);
			int unfilteredChildren= result.length;
			ViewerFilter[] filters = getFilters();
			if (filters != null) {
        for (ViewerFilter filter : filters)
          result = filter.filter(this, parent, result);
			}
			fIsFiltering= unfilteredChildren != result.length;
			return result;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		protected void internalExpandToLevel(Widget node, int level) {
			if (!fIsFiltering && node instanceof Item) {
				Item i= (Item) node;
				if (i.getData() instanceof IJavaElement) {
					IJavaElement je= (IJavaElement) i.getData();
					if (je.getElementType() == IJavaElement.IMPORT_CONTAINER || isInnerType(je)) {
						setExpanded(i, false);
						return;
					}
				}
			}
			super.internalExpandToLevel(node, level);
		}

		private boolean isInnerType(IJavaElement element) {
			if (element != null && element.getElementType() == IJavaElement.TYPE) {
				IType type= (IType)element;
				try {
					return type.isMember();
				} catch (JavaModelException e) {
					IJavaElement parent= type.getParent();
					if (parent != null) {
						int parentElementType= parent.getElementType();
						return (parentElementType != IJavaElement.COMPILATION_UNIT && parentElementType != IJavaElement.CLASS_FILE);
					}
				}
			}
			return false;
		}
	}


	private class OutlineContentProvider extends StandardJavaElementContentProvider {

		private boolean fShowInheritedMembers;

		/**
		 * Creates a new Outline content provider.
		 *
		 * @param showInheritedMembers <code>true</code> iff inherited members are shown
		 */
		private OutlineContentProvider(boolean showInheritedMembers) {
			super(true);
			fShowInheritedMembers= showInheritedMembers;
		}

		public boolean isShowingInheritedMembers() {
			return fShowInheritedMembers;
		}

		public void toggleShowInheritedMembers() {
			Tree tree= getTreeViewer().getTree();

			tree.setRedraw(false);
			fShowInheritedMembers= !fShowInheritedMembers;
			getTreeViewer().refresh();
			getTreeViewer().expandToLevel(2);

			// reveal selection
			Object selectedElement= getSelectedElement();
			if (selectedElement != null)
				getTreeViewer().reveal(selectedElement);

			tree.setRedraw(true);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object[] getChildren(Object element) {
			// AspectJ Change begin
			if (element instanceof ICompilationUnit)
				element = AJCompilationUnitManager.mapToAJCompilationUnit((ICompilationUnit) element);
			// AspectJ Change end

			if (fShowOnlyMainType) {
				if (element instanceof ICompilationUnit)
					element = getMainType((ICompilationUnit) element);
				else if (element instanceof IClassFile)
					element = getMainType((IClassFile) element);
				if (element == null)
					return NO_CHILDREN;
			}

			if (fShowInheritedMembers && element instanceof IType) {
				IType type = (IType) element;
				if (type.getDeclaringType() == null) {
					ITypeHierarchy typeHierarchy = getSuperTypeHierarchy(type);
					if (typeHierarchy != null) {
						IType[] supertypes = typeHierarchy.getAllSupertypes(type);
						// StandardJavaElementContentProvider.getChildren returns Object[], so we cannot be more specific
						List<Object> children = Arrays.asList(super.getChildren(type));
						for (IType supertype : supertypes)
							children.addAll(Arrays.asList(super.getChildren(supertype)));
						return children.toArray();
					}
				}
			}
			return super.getChildren(element);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			super.inputChanged(viewer, oldInput, newInput);
			fTypeHierarchies.clear();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void dispose() {
			super.dispose();
			fTypeHierarchies.clear();
		}

		/**
		 * Returns the primary type of a compilation unit (has the same
		 * name as the compilation unit).
		 *
		 * @param compilationUnit the compilation unit
		 * @return returns the primary type of the compilation unit, or
		 * <code>null</code> if is does not have one
		 */
		private IType getMainType(ICompilationUnit compilationUnit) {

			if (compilationUnit == null)
				return null;

			return compilationUnit.findPrimaryType();
		}

		/**
		 * Returns the primary type of a class file.
		 *
		 * @param classFile the class file
		 * @return returns the primary type of the class file, or <code>null</code>
		 * if is does not have one
		 */
		private IType getMainType(IClassFile classFile) {
			IType type= classFile.getType();
			return type != null && type.exists() ? type : null;
		}
	}


	private class ShowOnlyMainTypeAction extends Action {

		private static final String STORE_GO_INTO_TOP_LEVEL_TYPE_CHECKED= "GoIntoTopLevelTypeAction.isChecked"; //$NON-NLS-1$

		private final TreeViewer fOutlineViewer;

		private ShowOnlyMainTypeAction(TreeViewer outlineViewer) {
			super(TextMessages.getString("JavaOutlineInformationControl.GoIntoTopLevelType.label"), IAction.AS_CHECK_BOX); //$NON-NLS-1$
			setToolTipText(TextMessages.getString("JavaOutlineInformationControl.GoIntoTopLevelType.tooltip")); //$NON-NLS-1$
			setDescription(TextMessages.getString("JavaOutlineInformationControl.GoIntoTopLevelType.description")); //$NON-NLS-1$

			JavaPluginImages.setLocalImageDescriptors(this, "gointo_toplevel_type.gif"); //$NON-NLS-1$

			AspectJUIPlugin.getDefault().getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GO_INTO_TOP_LEVEL_TYPE_ACTION);

			fOutlineViewer= outlineViewer;

			boolean showclass= getDialogSettings().getBoolean(STORE_GO_INTO_TOP_LEVEL_TYPE_CHECKED);
			setTopLevelTypeOnly(showclass);
		}

		/*
		 * @see org.eclipse.jface.action.Action#run()
		 */
		@Override
		public void run() {
			setTopLevelTypeOnly(!fShowOnlyMainType);
		}

		private void setTopLevelTypeOnly(boolean show) {
			fShowOnlyMainType= show;
			setChecked(show);

			Tree tree= fOutlineViewer.getTree();
			tree.setRedraw(false);

			fOutlineViewer.refresh(false);
			if (!fShowOnlyMainType)
				fOutlineViewer.expandToLevel(2);


			// reveal selection
			Object selectedElement= getSelectedElement();
			if (selectedElement != null)
				fOutlineViewer.reveal(selectedElement);

			tree.setRedraw(true);

			getDialogSettings().put(STORE_GO_INTO_TOP_LEVEL_TYPE_CHECKED, show);
		}
	}

	private class OutlineSorter extends AbstractHierarchyViewerSorter {

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter#getHierarchy(org.eclipse.jdt.core.IType)
		 */
		@Override
		protected ITypeHierarchy getHierarchy(IType type) {
			return getSuperTypeHierarchy(type);
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter#isSortByDefiningType()
		 */
		@Override
		public boolean isSortByDefiningType() {
			return fSortByDefiningTypeAction.isChecked();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.typehierarchy.AbstractHierarchyViewerSorter#isSortAlphabetically()
		 */
		@Override
		public boolean isSortAlphabetically() {
			return fLexicalSortingAction.isChecked();
		}
	}


	private class LexicalSortingAction extends Action {

		private static final String STORE_LEXICAL_SORTING_CHECKED= "LexicalSortingAction.isChecked"; //$NON-NLS-1$

		private final TreeViewer fOutlineViewer;

		private LexicalSortingAction(TreeViewer outlineViewer) {
			super(TextMessages.getString("JavaOutlineInformationControl.LexicalSortingAction.label"), IAction.AS_CHECK_BOX); //$NON-NLS-1$
			setToolTipText(TextMessages.getString("JavaOutlineInformationControl.LexicalSortingAction.tooltip")); //$NON-NLS-1$
			setDescription(TextMessages.getString("JavaOutlineInformationControl.LexicalSortingAction.description")); //$NON-NLS-1$

			JavaPluginImages.setLocalImageDescriptors(this, "alphab_sort_co.gif"); //$NON-NLS-1$

			fOutlineViewer= outlineViewer;

			boolean checked=getDialogSettings().getBoolean(STORE_LEXICAL_SORTING_CHECKED);
			setChecked(checked);
			AspectJUIPlugin.getDefault().getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.LEXICAL_SORTING_BROWSING_ACTION);
		}

		@Override
		public void run() {
			valueChanged(isChecked(), true);
		}

		private void valueChanged(final boolean on, boolean store) {
			setChecked(on);
			BusyIndicator.showWhile(fOutlineViewer.getControl().getDisplay(), () -> fOutlineViewer.refresh(false));

			if (store)
				getDialogSettings().put(STORE_LEXICAL_SORTING_CHECKED, on);
		}
	}


	private class SortByDefiningTypeAction extends Action {

		private static final String STORE_SORT_BY_DEFINING_TYPE_CHECKED= "SortByDefiningType.isChecked"; //$NON-NLS-1$

		private final TreeViewer fOutlineViewer;

		/**
		 * Creates the action.
		 *
		 * @param outlineViewer the outline viewer
		 */
		private SortByDefiningTypeAction(TreeViewer outlineViewer) {
			super(TextMessages.getString("JavaOutlineInformationControl.SortByDefiningTypeAction.label")); //$NON-NLS-1$
			setDescription(TextMessages.getString("JavaOutlineInformationControl.SortByDefiningTypeAction.description")); //$NON-NLS-1$
			setToolTipText(TextMessages.getString("JavaOutlineInformationControl.SortByDefiningTypeAction.tooltip")); //$NON-NLS-1$

			JavaPluginImages.setLocalImageDescriptors(this, "definingtype_sort_co.gif"); //$NON-NLS-1$

			fOutlineViewer= outlineViewer;

			AspectJUIPlugin.getDefault().getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.SORT_BY_DEFINING_TYPE_ACTION);

			boolean state= getDialogSettings().getBoolean(STORE_SORT_BY_DEFINING_TYPE_CHECKED);
			setChecked(state);
			fInnerLabelProvider.setShowDefiningType(state);
		}

		/*
		 * @see Action#actionPerformed
		 */
		@Override
		public void run() {
			BusyIndicator.showWhile(fOutlineViewer.getControl().getDisplay(), () -> {
        fInnerLabelProvider.setShowDefiningType(isChecked());
        getDialogSettings().put(STORE_SORT_BY_DEFINING_TYPE_CHECKED, isChecked());

        fOutlineViewer.refresh(true);

        // reveal selection
        Object selectedElement= getSelectedElement();
        if (selectedElement != null)
          fOutlineViewer.reveal(selectedElement);
      });
		}
	}


	/**
	 * Creates a new Java outline information control.
	 *
	 * @param parent
	 * @param shellStyle
	 * @param treeStyle
	 * @param commandId
	 */
	public AJOutlineInformationControl(Shell parent, int shellStyle, int treeStyle, String commandId) {
		super(parent, shellStyle, treeStyle, commandId, true);
		// AspectJ Change begin
		// adding this for testing purposes (bug 80239)
		AJOutlineInformationControl.infoControl = this;
		// AspectJ Change end
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected Text createFilterText(Composite parent) {
		Text text= super.createFilterText(parent);
		text.addKeyListener(getKeyAdapter());
		return text;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected TreeViewer createTreeViewer(Composite parent, int style) {
		Tree tree= new Tree(parent, SWT.SINGLE | (style & ~SWT.MULTI));
		GridData gd= new GridData(GridData.FILL_BOTH);
		gd.heightHint= tree.getItemHeight() * 12;
		tree.setLayoutData(gd);

		final TreeViewer treeViewer= new OutlineTreeViewer(tree);

		// Hard-coded filters
		treeViewer.addFilter(new NamePatternFilter());
		treeViewer.addFilter(new MemberFilter());

		fForegroundColor= parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);

		fInnerLabelProvider= new OutlineLabelProvider();
		fInnerLabelProvider.addLabelDecorator(new ProblemsLabelDecorator(null));
		IDecoratorManager decoratorMgr= PlatformUI.getWorkbench().getDecoratorManager();
		if (decoratorMgr.getEnabled("org.eclipse.jdt.ui.override.decorator")) //$NON-NLS-1$
			fInnerLabelProvider.addLabelDecorator(new OverrideIndicatorLabelDecorator(null));

		// AspectJ Change begin
		//treeViewer.setLabelProvider(fInnerLabelProvider);
		treeViewer.setLabelProvider(new DecoratingJavaLabelProvider(fInnerLabelProvider));
		// AspectJ Change end

		fLexicalSortingAction= new LexicalSortingAction(treeViewer);
		fSortByDefiningTypeAction= new SortByDefiningTypeAction(treeViewer);
		fShowOnlyMainTypeAction= new ShowOnlyMainTypeAction(treeViewer);

		fOutlineContentProvider= new OutlineContentProvider(false);
		treeViewer.setContentProvider(fOutlineContentProvider);
		fOutlineSorter= new OutlineSorter();
		treeViewer.setComparator(fOutlineSorter);
		treeViewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS);


		treeViewer.getTree().addKeyListener(getKeyAdapter());

		return treeViewer;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected String getStatusFieldText() {
	    TriggerSequence[] sequences= getInvokingCommandKeySequences();
		if (sequences == null || sequences.length == 0)
			return ""; //$NON-NLS-1$

		String keySequence= sequences[0].format();

		if (fOutlineContentProvider.isShowingInheritedMembers())
			return Messages.format(JavaUIMessages.JavaOutlineControl_statusFieldText_hideInheritedMembers, keySequence);
		else
			return Messages.format(JavaUIMessages.JavaOutlineControl_statusFieldText_showInheritedMembers, keySequence);
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractInformationControl#getId()
	 * @since 3.0
	 */
	@Override
	protected String getId() {
		return "org.eclipse.jdt.internal.ui.text.QuickOutline"; //$NON-NLS-1$
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void setInput(Object information) {
		if (information == null || information instanceof String) {
			inputChanged(null, null);
			return;
		}
		IJavaElement je= (IJavaElement)information;
		ICompilationUnit cu= (ICompilationUnit)je.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (cu != null)
			fInput= cu;
		else
			fInput= je.getAncestor(IJavaElement.CLASS_FILE);

		inputChanged(fInput, information);
	}

	private KeyAdapter getKeyAdapter() {
		if (fKeyAdapter == null) {
			fKeyAdapter= new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					int accelerator = SWTKeySupport.convertEventToUnmodifiedAccelerator(e);
					KeySequence keySequence = KeySequence.getInstance(SWTKeySupport.convertAcceleratorToKeyStroke(accelerator));
					TriggerSequence[] sequences = getInvokingCommandKeySequences();
//					int accelerator = SWTKeySupport.convertEventToUnmodifiedAccelerator(e);
//					KeySequence keySequence = KeySequence.getInstance(SWTKeySupport.convertAcceleratorToKeyStroke(accelerator));
//					KeySequence[] sequences= getInvokingCommandKeySequences();
					if (sequences == null)
						return;
          for (TriggerSequence sequence : sequences) {
            if (sequence.equals(keySequence)) {
              e.doit = false;
              toggleShowInheritedMembers();
              return;
            }
          }
				}
			};
		}
		return fKeyAdapter;
	}

	/**
	 * {@inheritDoc}
	 */
	protected void handleStatusFieldClicked() {
		toggleShowInheritedMembers();
	}

	protected void toggleShowInheritedMembers() {
		// AspectJ Change begin - cast needed because of Eclipse 3.1 changes
		int flags= (int)(AppearanceAwareLabelProvider.DEFAULT_TEXTFLAGS |  JavaElementLabels.F_APP_TYPE_SIGNATURE);
		// AspectJ Change end
		if (!fOutlineContentProvider.isShowingInheritedMembers())
			flags |= JavaElementLabels.ALL_POST_QUALIFIED;
		fInnerLabelProvider.setTextFlags(flags);
		fOutlineContentProvider.toggleShowInheritedMembers();
		updateStatusFieldText();
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.AbstractInformationControl#fillViewMenu(org.eclipse.jface.action.IMenuManager)
	 */
	@Override
	protected void fillViewMenu(IMenuManager viewMenu) {
		super.fillViewMenu(viewMenu);
		viewMenu.add(fShowOnlyMainTypeAction);

		viewMenu.add(new Separator("Sorters")); //$NON-NLS-1$
		viewMenu.add(fLexicalSortingAction);

		viewMenu.add(fSortByDefiningTypeAction);
	}

	private ITypeHierarchy getSuperTypeHierarchy(IType type) {
		ITypeHierarchy th= fTypeHierarchies.get(type);
		if (th == null) {
			try {
				th= SuperTypeHierarchyCache.getTypeHierarchy(type, getProgressMonitor());
			} catch (JavaModelException e) {
				return null;
			}
			fTypeHierarchies.put(type, th);
		}
		return th;
	}

	private IProgressMonitor getProgressMonitor() {
		IWorkbenchPage wbPage= JavaPlugin.getActivePage();
		if (wbPage == null)
			return null;

		IEditorPart editor= wbPage.getActiveEditor();
		if (editor == null)
			return null;

		return editor.getEditorSite().getActionBars().getStatusLineManager().getProgressMonitor();
	}

	// AspectJ Change begin
	// adding this for testing purposes (bug 80239)
	private static AJOutlineInformationControl infoControl;

	/**
	 * Returns the instance of this class - this method
	 * is for testing purposes and is not part of the published
	 * API.
	 */
	public static AJOutlineInformationControl getInfoControl() {
		return infoControl;
	}
	// AspectJ Change end
}
