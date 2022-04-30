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
package org.eclipse.ajdt.internal.ui.help;

import org.eclipse.ajdt.internal.ui.editor.AspectJEditor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.actions.ActionUtil;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.util.JavadocHelpContext;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.HelpEvent;
import org.eclipse.swt.events.HelpListener;
import org.eclipse.ui.IWorkbenchPart;

// Partly copied from JavaUIHelp
public class AspectJUIHelp {

	public static void setHelp(StructuredViewer viewer, String contextId) {
		AspectJUIHelpListener listener= new AspectJUIHelpListener(viewer, contextId);
		viewer.getControl().addHelpListener(listener);
	}

	public static void setHelp(AspectJEditor editor, StyledText text, String contextId) {
		AspectJUIHelpListener listener= new AspectJUIHelpListener(editor, contextId);
		text.addHelpListener(listener);
	}

	/**
	 * Creates and returns a help context provider for the given part.
	 *
	 * @param part the part for which to create the help context provider
	 * @param contextId	the optional context ID used to retrieve static help
	 * @return the help context provider
	 */
	public static IContextProvider getHelpContextProvider(IWorkbenchPart part, String contextId) {
		IStructuredSelection selection;
		try {
			selection= SelectionConverter.getStructuredSelection(part);
		} catch (JavaModelException ex) {
			selection= StructuredSelection.EMPTY;
		}
		Object[] elements= selection.toArray();
		return new AspectJUIHelpContextProvider(contextId, elements);
	}

	private static class AspectJUIHelpListener implements HelpListener {

		private StructuredViewer fViewer;
		private final String fContextId;
		private AspectJEditor fEditor;

		public AspectJUIHelpListener(StructuredViewer viewer, String contextId) {
			fViewer= viewer;
			fContextId= contextId;
		}

		public AspectJUIHelpListener(AspectJEditor editor, String contextId) {
			fContextId= contextId;
			fEditor= editor;
		}

		/*
		 * @see HelpListener#helpRequested(HelpEvent)
		 *
		 */
		public void helpRequested(HelpEvent e) {
			try {
				Object[] selected= null;
				if (fViewer != null) {
					ISelection selection= fViewer.getSelection();
					if (selection instanceof IStructuredSelection) {
						selected= ((IStructuredSelection)selection).toArray();
					}
				} else if (fEditor != null) {
					IJavaElement input= SelectionConverter.getInput(fEditor);
					if (ActionUtil.isOnBuildPath(input)) {
						selected= SelectionConverter.codeResolve(fEditor);
					}
				}
				JavadocHelpContext.displayHelp(fContextId, selected);
			} catch (CoreException ignored) {
			}
		}
	}

	private static class AspectJUIHelpContextProvider implements IContextProvider {
		private final String fId;
		private final Object[] fSelected;
		public AspectJUIHelpContextProvider(String id, Object[] selected) {
			fId= id;
			fSelected= selected;
		}
		public int getContextChangeMask() {
			return SELECTION;
		}
		public IContext getContext(Object target) {
			IContext context= HelpSystem.getContext(fId);
			if (fSelected != null && fSelected.length > 0) {
				try {
					context= new JavadocHelpContext(context, fSelected);
				} catch (JavaModelException ignored) {
				}
			}
			return context;
		}
		public String getSearchExpression(Object target) {
			return null;
		}
	}
}
