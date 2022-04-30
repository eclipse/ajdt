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
package org.eclipse.contribution.xref.internal.ui.providers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.contribution.xref.core.IXReference;
import org.eclipse.contribution.xref.core.IXReferenceAdapter;
import org.eclipse.contribution.xref.internal.ui.text.XRefMessages;
import org.eclipse.contribution.xref.ui.IDeferredXReference;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaElement;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;

/**
 * The content provider for the tree of cross references
 */
public class XReferenceContentProvider
	implements IStructuredContentProvider, ITreeContentProvider {

	private TreeParent invisibleRoot;
	private Object input = null;

	public void inputChanged(Viewer v, Object oldInput, Object newInput) {
		if (newInput != null) {
			input = newInput;
			initialize();
			if (v != null) { // guard to make testing easier
				v.refresh();
			}
		}
	}

	public void refresh() {
		// due to an underlying model change (evaluation of a deferred
		// cross reference) the content needs to be reevaluated.
		initialize();
	}

	public void dispose() {
		input = null;
		invisibleRoot = null;
	}

	public Object[] getElements(Object parent) {
		if (parent == null)
			return new Object[0];
		if (parent.equals(input)) {
			return getChildren(invisibleRoot);
		}
		return getChildren(parent);
	}

	public Object getParent(Object child) {
		if (child instanceof TreeObject) {
			return ((TreeObject) child).getParent();
		}
		return null;
	}

	public Object[] getChildren(Object parent) {
		if (parent instanceof TreeParent) {
			return ((TreeParent) parent).getChildren();
		}
		return new Object[0];
	}

	public boolean hasChildren(Object parent) {
		if (parent instanceof TreeParent)
			return ((TreeParent) parent).hasChildren();
		return false;
	}

	private void initialize() {
		invisibleRoot = new TreeParent(""); //$NON-NLS-1$
		if ((input != null) && (input instanceof IXReferenceAdapter)) {
			IXReferenceAdapter xreferenceAdapter = (IXReferenceAdapter) input;
			createXRefTree(xreferenceAdapter);
		} else if (input != null && (input instanceof List)) {
      for (Object o : (List) input) {
        if (o instanceof IXReferenceAdapter) {
          createXRefTree((IXReferenceAdapter) o);
        }
      }
		} else if (input != null) {
			TreeParent root = new TreeParent(input.getClass().getName());
			root.setData(input);
			invisibleRoot.addChild(root);
		}
	}

	private void createXRefTree(IXReferenceAdapter xreferenceAdapter) {
		TreeParent root = new TreeParent(xreferenceAdapter.toString());
		root.setData(xreferenceAdapter.getReferenceSource());
		invisibleRoot.addChild(root);

		addXReferencesToTree(root, xreferenceAdapter.getXReferences());

		// If there are cross references for the children of the
		// currently selected XReferenceAdapter, then want to also
		// include these in the view.
		Object o = xreferenceAdapter.getReferenceSource();
		JavaElement je = null;
		if (o instanceof IJavaElement) {
			je = (JavaElement) o;
		}
		if (je != null) {
			addChildren(root,je,xreferenceAdapter);
		}
	}

	private boolean addChildren(TreeParent parent, JavaElement je, IXReferenceAdapter xreferenceAdapter) {
		boolean hasChildren = false;
		try {
			IJavaElement[] extra = xreferenceAdapter.getExtraChildren(je);
			List l = new ArrayList();
			IJavaElement[] children = je.getChildren();
      Collections.addAll(l, children);
			if (extra!=null) {
        Collections.addAll(l, extra);
			}
			children = (IJavaElement[])l.toArray(new IJavaElement[]{});
      for (IJavaElement child : children) {
        IAdaptable a = child;
        IXReferenceAdapter xrefAdapterChild = null;
        if (a != null) {
          xrefAdapterChild =
            a.getAdapter(
              IXReferenceAdapter.class);
        }
        if (xrefAdapterChild != null) {
          TreeParent childNode =
            new TreeParent(xrefAdapterChild.toString());
          childNode.setData(
            xrefAdapterChild.getReferenceSource());
          Collection xrc = xrefAdapterChild.getXReferences();
          if (!xrc.isEmpty()) {
            parent.addChild(childNode);
            addXReferencesToTree(childNode, xrc);
            JavaElement subJe = (JavaElement) child;
            hasChildren = true;

            if ((xreferenceAdapter.getExtraChildren(subJe) != null
                 && xreferenceAdapter.getExtraChildren(subJe).length > 0)
                || subJe.getChildren().length > 0)
            {
              addChildren(childNode, subJe, xreferenceAdapter);
            }

          }
          else {
            JavaElement subJe = (JavaElement) child;
            if (addChildren(childNode, subJe, xreferenceAdapter)) {
              parent.addChild(childNode);
              hasChildren = true;
            }
          }
        }
      }
		} catch (JavaModelException e) {
			// don't care about this exception
		}
		return hasChildren;
	}

	private void addXReferencesToTree(
		TreeParent parent,
		Collection xreferences) {
    for (Object xreference : xreferences) {
      IXReference xr = (IXReference) xreference;
      TreeParent relName = new TreeParent(xr.getName());
      if (xr instanceof IDeferredXReference) {
        addEvaluateChild(relName, (IDeferredXReference) xr);
      }
      Iterator li = xr.getAssociates();
      while (li.hasNext()) {
        Object associate = li.next();
        TreeObject leaf = new TreeObject(associate.toString());
        leaf.setData(associate);
        relName.addChild(leaf);
      }
      parent.addChild(relName);
    }
	}

	private void addEvaluateChild(TreeParent parent, IDeferredXReference r) {
		TreeObject t =
			new TreeObject(XRefMessages.XReferenceContentProvider_evaluate);
		t.setData(r);
		parent.addChild(t);
	}

}
