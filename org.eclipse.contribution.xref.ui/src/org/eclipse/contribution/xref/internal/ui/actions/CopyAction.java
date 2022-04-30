/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Matt Chapman   - initial version
 *******************************************************************************/
package org.eclipse.contribution.xref.internal.ui.actions;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.WorkbenchMessages;

/**
 * Retargetable action for Copy - copies tree viewer contents to clipboard as text
 *
 */
public class CopyAction extends Action {

	private TreeViewer fViewer;

	private Clipboard fClipboard;

	// maximum amount of whitespace to be used for indentation
	private static final String INDENT_SPACES = "                                                                      "; //$NON-NLS-1$

	public CopyAction(TreeViewer viewer, Clipboard clipboard) {
		super();
		fClipboard = clipboard;
		fViewer = viewer;

		setText(WorkbenchMessages.Workbench_copy);
		setToolTipText(WorkbenchMessages.Workbench_copyToolTip);
		ISharedImages workbenchImages = PlatformUI.getWorkbench()
				.getSharedImages();
		setImageDescriptor(workbenchImages.getImageDescriptor(ISharedImages.IMG_TOOL_COPY));

	}

	public void run() {
		fClipboard.setContents(new Object[] { getContentsAsText() },
				new Transfer[] { TextTransfer.getInstance() });
	}

	/**
	 * Convert the selected items to a textual representation
	 * @return
	 */
	public String getContentsAsText() {
		TreeItem[] items = fViewer.getTree().getSelection();
		StringBuilder buf = new StringBuilder();
		for (int i = 0; i < items.length; i++) {
			buf.append(nestingLevel(items[i]));
			buf.append(items[i].getText());
			if (i < items.length - 1) {
				buf.append(System.getProperty("line.separator")); //$NON-NLS-1$
			}
		}
		return buf.toString();
	}

	/**
	 * Returns whitespace to indent tree item appropriately
	 * (4 spaces per level)
	 * @param item
	 * @return
	 */
	private String nestingLevel(TreeItem item) {
		int level = 0;
		while ((item != null) && (level < 100)) {
			level++;
			item = item.getParentItem();
		}
		int indent = (level - 1) * 4;
		if (indent < INDENT_SPACES.length()) {
			return INDENT_SPACES.substring(0, indent);
		}
		return INDENT_SPACES;
	}

}
