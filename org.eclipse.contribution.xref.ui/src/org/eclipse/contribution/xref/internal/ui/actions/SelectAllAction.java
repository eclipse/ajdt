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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.internal.WorkbenchMessages;

/**
 * Retargetable action for Select All - selects all visible items in the tree viewer
 *
 */
public class SelectAllAction extends Action {

	private TreeViewer fViewer;

	public SelectAllAction(TreeViewer viewer) {
		super();
		fViewer = viewer;
		setText(WorkbenchMessages.Workbench_selectAll);
		setToolTipText(WorkbenchMessages.Workbench_selectAllToolTip);
	}

	public void run() {
		ArrayList allVisible = new ArrayList();
		Tree tree = fViewer.getTree();
		collectExpandedAndVisible(tree.getItems(), allVisible);
		tree.setSelection((TreeItem[]) allVisible
				.toArray(new TreeItem[0]));
	}

	private void collectExpandedAndVisible(TreeItem[] items, List result) {
    for (TreeItem item : items) {
      result.add(item);
      if (item.getExpanded()) {
        collectExpandedAndVisible(item.getItems(), result);
      }
    }
	}

}
