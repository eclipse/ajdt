/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved. This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: IBM Corporation - initial API and implementation
 * 				 Helen Hawkins   - iniital version
 *               Neal Upstone    - Patch for bug 271605
 ******************************************************************************/
package org.eclipse.contribution.xref.ui.views;

import org.eclipse.contribution.xref.internal.ui.utils.XRefUIUtils;
import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.contentoutline.ContentOutline;

/**
 * Class to activate the Cross References View via the context menu
 * in the outline view and the Navigate/Show In workbench menu
 */
public class OpenXReferenceViewAction implements IObjectActionDelegate, IWorkbenchWindowActionDelegate {

    /* (non-Javadoc)
     * @see org.eclipse.ui.IObjectActionDelegate#setActivePart(org.eclipse.jface.action.IAction, org.eclipse.ui.IWorkbenchPart)
     */
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IActionDelegate#run(org.eclipse.jface.action.IAction)
     */
    public void run(IAction action) {
 		try {
 			IWorkbenchPage page = getActiveWorkbenchPage();
 			if (page == null) return;
			page.showView(XReferenceView.ID);

 			ISelection currentSelection = XRefUIUtils.getCurrentSelection();
		    IWorkbenchPart workbenchPart = getActiveWorkbenchPart();
			XReferenceView xrefView = XReferenceUIPlugin.xrefView;
			xrefView.selectionChanged(workbenchPart,currentSelection);
		} catch (PartInitException ignored) {
		}
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IActionDelegate#selectionChanged(org.eclipse.jface.action.IAction, org.eclipse.jface.viewers.ISelection)
     */
    public void selectionChanged(IAction action, ISelection selection) {
    	IWorkbenchPart activePart = getActiveWorkbenchPart();
		action.setEnabled(activePart instanceof ContentOutline || activePart instanceof IEditorPart);
	}

	private static IWorkbenchPart getActiveWorkbenchPart() {
		IWorkbenchPage page = getActiveWorkbenchPage();
		if (page == null) {
		    return null;
		}
		IWorkbenchPart activePart = page.getActivePart();
		return activePart;
	}

	private static IWorkbenchPage getActiveWorkbenchPage() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null) {
		    return null;
		}
		IWorkbenchPage page = window.getActivePage();
		return page;
	}

    /* (non-Javadoc)
     * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#dispose()
     */
    public void dispose() {
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.IWorkbenchWindowActionDelegate#init(org.eclipse.ui.IWorkbenchWindow)
     */
    public void init(IWorkbenchWindow window) {
    }
}
