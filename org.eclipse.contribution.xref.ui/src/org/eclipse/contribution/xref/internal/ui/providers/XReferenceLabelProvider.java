/*******************************************************************************
 * Copyright (c) 2004, 2006 IBM Corporation and others.
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

import org.eclipse.contribution.xref.core.IXReferenceNode;
import org.eclipse.contribution.xref.ui.IDeferredXReference;
import org.eclipse.contribution.xref.ui.XReferenceUIPlugin;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.viewers.DecoratingLabelProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.swt.graphics.Image;

/**
 * Label provider for the tree of cross references
 */
public class XReferenceLabelProvider extends LabelProvider {

	// any element can be put into the data field of
	// a TreeObject. If it implements IWorkbenchAdapter,
	// it will be displayed correctly in the tree.

	public ILabelProvider labelProvider;

	public XReferenceLabelProvider() {
		labelProvider = new DecoratingLabelProvider(
			new JavaElementLabelProvider(),
			XReferenceUIPlugin.getDefault().getWorkbench().getDecoratorManager().getLabelDecorator()
		);
	}

	private boolean addedListener = false;

	private ListenerList<ILabelProviderListener> fListeners;

	public void addListener(ILabelProviderListener listener) {
		super.addListener(listener);
		if (fListeners == null)
			fListeners = new ListenerList<>();
		fListeners.add(listener);
		if (!addedListener) {
			addedListener = true;
			// as we are only retrieving images from labelProvider not using it
			// directly, we need to update this label provider whenever that one
			// updates
			labelProvider.addListener(event -> fireLabelChanged());
		}
	}

	private void fireLabelChanged() {
		if (fListeners != null && !fListeners.isEmpty()) {
			LabelProviderChangedEvent event = new LabelProviderChangedEvent(this);
			Object[] listeners = fListeners.getListeners();
			for (Object listener : listeners)
				((ILabelProviderListener) listener).labelProviderChanged(event);
		}
	}

	public String getText(Object obj) {
		String ret = obj.toString();
		Object data = ((TreeObject) obj).getData();
		if ((data != null) && !(data instanceof IDeferredXReference))
			ret = labelProvider.getText(data);
		return ret;
	}

	public Image getImage(Object obj) {
		Object data = ((TreeObject) obj).getData();
		if (data != null)
			if (data instanceof IDeferredXReference)
				return XReferenceUIPlugin.getDefault().getEvaluateImage();
			else if (data instanceof IXReferenceNode)
				return labelProvider.getImage(((IXReferenceNode) data).getJavaElement());
			else
				return (labelProvider.getImage(data));
		return XReferenceUIPlugin.getDefault().getXReferenceImage();
	}

	public void dispose() {
		fListeners = null;
		if (labelProvider != null) {
			labelProvider.dispose();
			labelProvider = null;
		}
	}
}
