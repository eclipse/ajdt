/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Helen Hawkins   - iniital version
 *******************************************************************************/
package org.eclipse.contribution.xref.ui.views;

import java.util.List;

import org.eclipse.contribution.xref.core.IXReferenceAdapter;
import org.eclipse.contribution.xref.core.XReferenceProviderManager;
import org.eclipse.contribution.xref.internal.ui.actions.CollapseAllAction;
import org.eclipse.contribution.xref.internal.ui.actions.CopyAction;
import org.eclipse.contribution.xref.internal.ui.actions.DoubleClickAction;
import org.eclipse.contribution.xref.internal.ui.actions.SelectAllAction;
import org.eclipse.contribution.xref.internal.ui.actions.ToggleLinkingAction;
import org.eclipse.contribution.xref.internal.ui.actions.ToggleShowXRefsForFileAction;
import org.eclipse.contribution.xref.internal.ui.actions.XReferenceCustomFilterAction;
import org.eclipse.contribution.xref.internal.ui.help.IXRefHelpContextIds;
import org.eclipse.contribution.xref.internal.ui.help.XRefUIHelp;
import org.eclipse.contribution.xref.internal.ui.providers.XReferenceContentProvider;
import org.eclipse.contribution.xref.internal.ui.providers.XReferenceLabelProvider;
import org.eclipse.contribution.xref.internal.ui.utils.XRefUIUtils;
import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;
import org.eclipse.help.IContextProvider;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.views.contentoutline.ContentOutline;

/**
 * This class represents the Cross Reference View
 */
public class XReferenceView extends ViewPart implements ISelectionListener,
		IPartListener {

	public static final String ID = "org.eclipse.contribution.xref.ui.views.XReferenceView"; //$NON-NLS-1$
	public static final String LINK_ID = ID + ".link"; //$NON-NLS-1$
	public static final String XREFS_FOR_FILE_ID = ID + ".xrefsForFile"; //$NON-NLS-1$

	private Action doubleClickAction;
	private Action collapseAllAction;
	private Action toggleLinkingAction;
	private Action toggleShowXRefsForFileAction;
	private Action xRefCustomFilterAction;
	private Action copyAction;
	private Action selectAllAction;

	private boolean linkingEnabled = true; // following selection?
	private boolean showXRefsForFileEnabled = false;
	private boolean changeDrivenByBuild = false;

	private List /* IXReferenceAdapter */ lastXRefAdapterList;
	private List /* IXReferenceAdapter */ lastLinkedXRefAdapterList;

	private ISelection lastSelection, lastLinkedSelection;
	private IWorkbenchPart lastWorkbenchPart, lastLinkedWorkbenchPart;
	private IJavaElement lastJavaElement, lastLinkedJavaElement;

	private TreeViewer viewer;
	private XReferenceContentProvider contentProvider;

	private Clipboard fClipboard;

	public XReferenceView() {
		XReferenceUIPlugin.xrefView = this;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IWorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createPartControl(Composite parent) {
		viewer = new TreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
		contentProvider = new XReferenceContentProvider();
		viewer.setContentProvider(contentProvider);
		viewer.setLabelProvider(new XReferenceLabelProvider());
		viewer.setAutoExpandLevel(AbstractTreeViewer.ALL_LEVELS);

		fClipboard = new Clipboard(viewer.getTree().getDisplay());

		restorePersistedSettings();
		makeActions();
		hookDoubleClickAction();
		contributeToActionBars();

		IWorkbenchWindow window = XRefUIUtils.getActiveWorkbenchWindow();

		if (window != null) {
			window.getSelectionService().addPostSelectionListener(this);
		}
		getSite().setSelectionProvider(viewer);

		// context menu
		MenuManager mgr = new MenuManager();
		mgr.add(copyAction);
		mgr.add(selectAllAction);
		// add the standard extension group
		mgr.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		Menu menu = mgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(mgr, viewer);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.ISelectionListener#selectionChanged(org.eclipse.ui.IWorkbenchPart,
	 *      org.eclipse.jface.viewers.ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		// enhancement 95724 - adding filter to xref view
		XReferenceProviderManager.getManager().setIsInplace(false);

		addListenersToOpenEditors();

		if (!(part instanceof AbstractTextEditor)
				&& !(part instanceof ContentOutline)) {
			// only want to respond to changes in selection
			// in editors and outline view
			return;
		}

		// if linking is enabled we want to work with the current
		// selection in the workbench, otherwise we want to work
		// with the last linked selection (since we're not linked with
		// the editor, we really don't care what was selected and are
		// really responding to changes due to a build)
		List xraList = null;
		lastJavaElement = XRefUIUtils.getSelectedJavaElement(part,selection);
		if (linkingEnabled) {
			// calculate the xrefs for the current selection
			if (showXRefsForFileEnabled) {
				xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastJavaElement,true);
			} else {
				xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastJavaElement,false);
			}

			if (xraList == null || xraList.isEmpty() || !lastJavaElement.exists() ) {
				// if there are compilation errors then we want to clear
				// the view (in this case, xraList is empty or null)
				clearView();
			} else if (lastXRefAdapterList == null || lastXRefAdapterList.isEmpty()) {
				//if this is the first selection (i.e. lastXRefAdapterList == null)
				// then just set the input to be the just calculated xrefs
				// OR
				// if compilatin errors occurred last time (lastXRefAdapterList is empty)
				// and this time they're fixed then just set the input to be the xrefs
				viewer.setInput(xraList);
				XRefUIUtils.setSelection(part, selection, viewer);
			} else {
				if (!sameXRefAdapter(lastXRefAdapterList,xraList)) {
					// if now selecting a new element in the editor then
					// need to update the view to contain this
					viewer.setInput(xraList);
					XRefUIUtils.setSelection(part, selection, viewer);
				} else if (changeDrivenByBuild){
					// if the change has been driven by a build then
					// need to update view since xrefs may have changed
					viewer.setInput(xraList);
					XRefUIUtils.setSelection(part, selection, viewer);
				} else {
					XRefUIUtils.setSelection(part, selection, viewer);
				}
			}
			// record what was last selected and last calculated
			lastWorkbenchPart = part;
			lastSelection = selection;
			lastXRefAdapterList = xraList;

			lastLinkedSelection = selection;
			lastLinkedWorkbenchPart = part;
			lastLinkedXRefAdapterList = xraList;
			lastLinkedJavaElement = lastJavaElement;

		} else {

			// calculate the xrefs for the last linked selection - these may have
			// changed due to a build
			if (showXRefsForFileEnabled) {
				xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastLinkedJavaElement,true);
			} else {
				xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastLinkedJavaElement,false);
			}

			if (xraList == null || xraList.isEmpty() || !lastLinkedJavaElement.exists()) {
				// if there are compilation errors then we want to clear
				// the view (in this case, xraList is empty or null)
				clearView();
			} else if (lastXRefAdapterList == null || lastXRefAdapterList.isEmpty()) {
				// if compilation errors occurred last time (lastXRefAdapterList is empty)
				// and this time they're fixed then we need to update the xref view. If
				// we've selected the same thing
				viewer.setInput(xraList);
				XRefUIUtils.setSelection(lastLinkedWorkbenchPart, lastLinkedSelection, viewer);
			} else if (sameXRefAdapter(lastLinkedXRefAdapterList,xraList) && changeDrivenByBuild) {
				// if anything has changed about the contents, then need to refresh
				// (this will come from a build)
				viewer.setInput(xraList);
				XRefUIUtils.setSelection(lastLinkedWorkbenchPart, lastLinkedSelection, viewer);
			}

			// record what was last selected and last calculated
			lastWorkbenchPart = part;
			lastSelection = selection;
			lastXRefAdapterList = xraList;
		}
	}

	private boolean sameXRefAdapter(List previousXRefAdapterList, List currentXRefAdapterList) {
		boolean sameXRefAdapter = true;
    for (Object o : currentXRefAdapterList) {
      boolean foundMatch = false;
      if (o instanceof IXReferenceAdapter) {
        IXReferenceAdapter currentXra = (IXReferenceAdapter) o;

        for (Object o2 : previousXRefAdapterList) {
          if (o2 instanceof IXReferenceAdapter) {
            IXReferenceAdapter lastXra = (IXReferenceAdapter) o2;
            if (currentXra.getReferenceSource().equals(
              lastXra.getReferenceSource()))
            {
              foundMatch = true;
            }
          }
        }
      }
      if (!foundMatch) {
        sameXRefAdapter = false;
      }
    }
		return sameXRefAdapter;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IWorkbenchPart#dispose()
	 */
	public void dispose() {
		super.dispose();
		if (fClipboard != null) {
			fClipboard.dispose();
			fClipboard = null;
		}
		IWorkbenchWindow window = XRefUIUtils.getActiveWorkbenchWindow();

		if (window != null) {
			window.getSelectionService().removePostSelectionListener(this);
		}
		IWorkbenchWindow activeWindow = JavaPlugin.getActiveWorkbenchWindow();
		if (activeWindow != null) {
			IWorkbenchPage activePage = activeWindow.getActivePage();
			if (activePage != null) {
				IEditorReference[] openEditors = activePage
						.getEditorReferences();
				if (openEditors.length != 0) {
					// remove the part listener for each open editor
          for (IEditorReference openEditor : openEditors) {
            openEditor.getPage().removePartListener(this);
          }
				}
			}
		}
		persistSettings();
		XReferenceUIPlugin.xrefView = null;
		viewer.getTree().dispose();
		if(viewer.getContentProvider() != null) {
			viewer.getContentProvider().dispose();
		}
		viewer.getLabelProvider().dispose();
	}

	public boolean isLinkingEnabled() {
		return linkingEnabled;
	}

	public void setLinkingEnabled(boolean isOn) {
		linkingEnabled = isOn;
		if (linkingEnabled) {
			// calculate the xrefs for the last selected JavaElement
			List xraList = null;
			if (showXRefsForFileEnabled) {
				xraList = XRefUIUtils.getXRefAdapterListForJavaElement(
						lastJavaElement,true);
			} else {
				xraList = XRefUIUtils.getXRefAdapterListForJavaElement(
						lastJavaElement,false);
			}
			viewer.setInput(xraList);
		}
	}

	public boolean isShowXRefsForFileEnabled() {
		return showXRefsForFileEnabled;
	}

	public void setShowXRefsForFileEnabled(boolean isOn) {
		showXRefsForFileEnabled = isOn;

		List xraList = null;
		IWorkbenchPart part = null;
		if (!linkingEnabled) {
			// if linking is not enabled then just want to show/hide the cross
			// references
			// for the file of the contents of the xref view
			if (lastLinkedSelection != null && lastLinkedWorkbenchPart != null) {
				part = lastLinkedWorkbenchPart;
				if (showXRefsForFileEnabled) {
					xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastLinkedJavaElement,true);
				} else {
					xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastLinkedJavaElement,false);
				}
			}
		} else {
			// if linking is enabled, then want to show/hide the cross
			// references
			// for the file which is open in the active editor
			if (lastSelection != null && lastWorkbenchPart != null) {
				part = lastWorkbenchPart;
				if (showXRefsForFileEnabled) {
					xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastJavaElement,true);
				} else {
					xraList = XRefUIUtils.getXRefAdapterListForJavaElement(lastJavaElement,false);
				}
			}
		}
		if (xraList != null) {
			ISelection sel = viewer.getSelection();
			viewer.setInput(xraList);
			XRefUIUtils.setSelection(part, sel, viewer);
		}
	}

	public void collapseAll() {
		viewer.collapseAll();
	}

	public ISelection getLastSelection() {
		return lastSelection;
	}

	public IWorkbenchPart getLastSelectedWorkbenchPart() {
		return lastWorkbenchPart;
	}

	private void persistSettings() {
		IPreferenceStore pstore = XReferenceUIPlugin.getDefault()
				.getPreferenceStore();
		if (!pstore.contains(LINK_ID)) {
			pstore.setDefault(LINK_ID, true);
		}
		pstore.setValue(LINK_ID, linkingEnabled);

		if (!pstore.contains(XREFS_FOR_FILE_ID)) {
			pstore.setDefault(XREFS_FOR_FILE_ID, false);
		}
		pstore.setValue(XREFS_FOR_FILE_ID, showXRefsForFileEnabled);
		XReferenceUIPlugin.getDefault().savePluginPreferences();
	}

	private void restorePersistedSettings() {
		IPreferenceStore pstore = XReferenceUIPlugin.getDefault()
				.getPreferenceStore();
		if (pstore.contains(LINK_ID)) {
			linkingEnabled = pstore.getBoolean(LINK_ID);
		}

		if (pstore.contains(XREFS_FOR_FILE_ID)) {
			showXRefsForFileEnabled = pstore.getBoolean(XREFS_FOR_FILE_ID);
		}
	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());

		// Add global action handlers.
		bars.setGlobalActionHandler(ActionFactory.COPY.getId(), copyAction);
		bars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(),
				selectAllAction);
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(new Separator());
		manager.add(toggleLinkingAction);
		manager.add(toggleShowXRefsForFileAction);
		manager.add(new Separator("filters")); //$NON-NLS-1$
		manager.add(xRefCustomFilterAction);
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(new Separator());
		manager.add(collapseAllAction);
		manager.add(toggleLinkingAction);
		manager.add(toggleShowXRefsForFileAction);
	}

	private void hookDoubleClickAction() {
		viewer.addDoubleClickListener(event -> doubleClickAction.run());
	}

	private void makeActions() {
		toggleLinkingAction = new ToggleLinkingAction(this);
		collapseAllAction = new CollapseAllAction(this);
		doubleClickAction = new DoubleClickAction(getViewSite().getShell(),
				viewer);
		toggleShowXRefsForFileAction = new ToggleShowXRefsForFileAction(this);
		xRefCustomFilterAction = new XReferenceCustomFilterAction(getSite()
				.getShell());
		copyAction = new CopyAction(viewer, fClipboard);
		selectAllAction = new SelectAllAction(viewer);
	}


	// fix for bug (no number) raised which said that xref view didn't
	// clear when there were no open editors
	private void addListenersToOpenEditors() {
		IWorkbenchWindow activeWindow = JavaPlugin.getActiveWorkbenchWindow();
		if (activeWindow != null) {
			IWorkbenchPage activePage = activeWindow.getActivePage();
			if (activePage != null) {
				IEditorReference[] openEditors = activePage
						.getEditorReferences();
				if (openEditors.length != 0) {
					// add a part listener to each open editor
          for (IEditorReference openEditor : openEditors) {
            openEditor.getPage().addPartListener(this);
          }
				}
			}
		}
	}

	/**
	 * @param changeDrivenByBuild
	 *            The changeDrivenByBuild to set.
	 */
	public void setChangeDrivenByBuild(boolean changeDrivenByBuild) {
		this.changeDrivenByBuild = changeDrivenByBuild;
	}

	// ----------------- IPartLisenter implementation ----------------------

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IPartListener#partActivated(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partActivated(IWorkbenchPart part) {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IPartListener#partBroughtToTop(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partBroughtToTop(IWorkbenchPart part) {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IPartListener#partClosed(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partClosed(IWorkbenchPart part) {
		if (part instanceof IEditorPart) {
			IWorkbenchWindow activeWindow = JavaPlugin
					.getActiveWorkbenchWindow();
			if (activeWindow != null) {
				IWorkbenchPage activePage = activeWindow.getActivePage();
				if (activePage != null) {
					IEditorReference[] openEditors = activePage
							.getEditorReferences();
					if (openEditors.length == 0) {
						// if there are no editors open, then want to clear the
						// contents of the xref view and all the records
						clearView();
						if (linkingEnabled) {
							lastXRefAdapterList = null;
							lastSelection = null;
							lastWorkbenchPart = null;
							lastJavaElement = null;
						}

					}
				}
			}
		}

	}

	private void clearView() {
		if (viewer != null && viewer.getContentProvider() != null) {
			viewer.setInput(null);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IPartListener#partDeactivated(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partDeactivated(IWorkbenchPart part) {
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.IPartListener#partOpened(org.eclipse.ui.IWorkbenchPart)
	 */
	public void partOpened(IWorkbenchPart part) {
	}


	public Object getAdapter(Class key) {
		if (key.equals(IContextProvider.class)) {
			return XRefUIHelp.getHelpContextProvider(this, IXRefHelpContextIds.XREF_VIEW);
		}
		return super.getAdapter(key);
	}


	// ----------------- This is for testing ----------------------

	/**
	 * Returns the tree viewer for the xref view - this method is for testing
	 * purposes and not part of the published API.
	 */
	public TreeViewer getTreeViewer() {
		return viewer;
	}

	/**
	 * Returns the action for the xref view - this method is for testing
	 * purposes and not part of the published API.
	 */
	public Action getCustomFilterAction() {
		return xRefCustomFilterAction;
	}

	/**
	 * Returns the action for the xref view - this method is for testing
	 * purposes and not part of the published API.
	 */
	public Action getToggleShowXRefsForEntireFileAction() {
		return toggleShowXRefsForFileAction;
	}

	/**
	 * Returns the action for the xref view - this method is for testing
	 * purposes and not part of the published API.
	 */
	public Action getToggleLinkWithEditorAction() {
		return toggleLinkingAction;
	}

}
